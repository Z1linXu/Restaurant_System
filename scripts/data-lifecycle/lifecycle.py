#!/usr/bin/env python3
"""Bounded database lifecycle plans and archive copies. There is no cleanup executor.

Connection settings use PGHOST/PGPORT/PGDATABASE/PGUSER/PGPASSWORD, never command
arguments containing passwords. plan/archive are read-only. restore-local is
restricted to a marked loopback synthetic sandbox and never overwrites a row.
"""
import argparse
import datetime as dt
import hashlib
import json
import os
from pathlib import Path
import re
import stat
import subprocess
import sys


# Application references are deliberately explicit: absence of a SQL FK is not proof of safety.
TABLES = {
    "print_jobs": ("D", ["dispatch_source_key/manual intent dedup", "reprint_source_job_id", "order_id/order_update_batch_id", "claim/lease/retry/uncertain"]),
    "print_job_attempts": ("D", ["print_job_id", "attempt token replay", "attempt numbering", "physical print evidence"]),
    "order_dispatch_outbox": ("D", ["source_key dedup", "print_jobs.dispatch_source_key", "order_id/order_update_batch_id", "active processing/retry"]),
    "order_item_options": ("B", ["order_items", "menu option identities", "historical printing/BOM", "order archive graph"]),
    "order_items": ("B", ["orders/options", "kitchen/beverage/production", "printing", "raw financial reports"]),
    "production_tasks": ("B", ["source_type/source_id kitchen/beverage", "order_id/order_item_id", "execution status"]),
    "order_submission_requests": ("D", ["submission idempotency key", "order_id replay response", "offline replay"]),
    "kitchen_tasks": ("B", ["order completion/KDS/Ready", "production_tasks.source_id", "active order protection"]),
    "audit_logs": ("C", ["entity_type/entity_id polymorphic references", "financial/security/correction evidence", "per-action retention unapproved"]),
    "orders": ("B", ["order_items/Uber FK", "printing/outbox/submission/update batches", "inventory source_id", "raw financial reports"]),
    "uber_eats_orders": ("D", ["environment/uber_order_id dedup", "financial_corrections FK", "original financial/source hashes", "pending money/cancel/edit"]),
    "frontdesk_beverage_items": ("B", ["order status/details", "production_tasks.source_id", "active beverage delivery"]),
    "sales_hourly_summary": ("B", ["historical reporting", "rebuild requires original orders", "not an archival substitute"]),
    "menu_item_sales_summary": ("B", ["historical reporting", "stable menu identity", "not complete transaction evidence"]),
    "uber_eats_events": ("D", ["environment/event_id/body_hash dedup", "cancel/edit existence", "pending/held/processing work"]),
    "menu_item_options": ("A", ["live menu/ordering/BOM", "historical identity", "add-on/remove/parent graph"]),
    "store_menu_master_mappings": ("A", ["provisioning", "polymorphic local_entity_id", "master version/store"]),
    "chain_master_menu_options": ("A", ["provisioning/master version", "product/parent keys"]),
    "uber_eats_menu_mappings": ("A", ["future import/remap", "local item/option identities", "store mapping"]),
    "order_update_batches": ("D", ["revision/idempotency key", "order items/print jobs/outbox", "old request replay"]),
    "sales_daily_summary": ("B", ["historical reporting", "rebuild requires original orders", "in-store legacy summary only"]),
    "uber_eats_financial_corrections": ("A", ["APPLY/REVOKE journal", "original/source hashes", "actual revenue evidence"]),
}
BLOCK = {"A": "CORE_DATA_NO_TIME_CLEANUP", "B": "ARCHIVE_READ_AND_REPORT_EQUIVALENCE_UNPROVEN",
         "C": "ACTION_RETENTION_AND_EVIDENCE_REQUIREMENTS_UNAPPROVED", "D": "REFERENCE_IDEMPOTENCY_OR_PRINT_UBER_RISK"}
SANDBOX_MARKER = "SYNTHETIC_ONLY_NO_PRODUCTION_DATA"


def canonical(value):
    return json.dumps(value, ensure_ascii=False, sort_keys=True, separators=(",", ":"))


def digest(value):
    return hashlib.sha256(canonical(value).encode()).hexdigest()


def sql_string(value):
    return "'" + str(value).replace("'", "''") + "'"


def write_private(path, value):
    path = Path(path)
    payload = json.dumps(value, ensure_ascii=False, indent=2, sort_keys=True) + "\n"
    if len(payload.encode()) > 16 * 1024 * 1024:
        raise ValueError("Output exceeds bounded archive size; use a smaller batch")
    fd = os.open(path, os.O_WRONLY | os.O_CREAT | os.O_EXCL, 0o600)
    with os.fdopen(fd, "w") as output:
        output.write(payload)


def read_private(path):
    path = Path(path)
    info = path.lstat()
    if not stat.S_ISREG(info.st_mode) or info.st_mode & 0o077:
        raise ValueError("Input must be a private regular file (mode 0600)")
    if info.st_size > 16 * 1024 * 1024:
        raise ValueError("Input exceeds bounded archive size")
    return json.loads(path.read_text())


class Database:
    def __init__(self):
        self.host = os.environ.get("PGHOST", "127.0.0.1")
        self.port = os.environ.get("PGPORT", "5432")
        self.name = os.environ.get("PGDATABASE", "")
        if not self.name:
            raise ValueError("PGDATABASE is required")
        self.psql = os.environ.get("PSQL", "psql")

    def run(self, query, read_only=True):
        env = os.environ.copy()
        # Do not inherit a service definition or PGOPTIONS capable of changing the explicit target.
        env.pop("PGSERVICE", None)
        env.pop("PGSERVICEFILE", None)
        env["PGOPTIONS"] = "-c statement_timeout=5000 -c lock_timeout=1000 -c default_transaction_read_only=" + ("on" if read_only else "off")
        args = [self.psql, "-X", "-qAt", "-v", "ON_ERROR_STOP=1", "-h", self.host,
                "-p", self.port, "-d", self.name]
        result = subprocess.run(args, input=query, text=True, capture_output=True, env=env, timeout=15)
        if result.returncode:
            # Errors can echo SQL containing archived personal data; never forward them to stdout.
            raise RuntimeError("PostgreSQL request failed; transaction rolled back or no write started")
        return result.stdout.strip()

    def value(self, query):
        return json.loads(self.run(query))

    def identity(self):
        return self.value("SELECT json_build_object('database',current_database(),'database_oid',"
                          "(SELECT oid FROM pg_database WHERE datname=current_database()),"
                          "'server_version',current_setting('server_version')); ")

    def audit_schema(self):
        return self.value("SELECT coalesce(json_agg(s ORDER BY s.attnum),'[]'::json) FROM "
                          "(SELECT a.attnum,a.attname,format_type(a.atttypid,a.atttypmod) AS data_type,"
                          "a.attnotnull,a.attidentity,a.attgenerated FROM pg_attribute a "
                          "WHERE a.attrelid='public.audit_logs'::regclass AND a.attnum>0 AND NOT a.attisdropped) s")


def load_policy(path):
    policy = json.loads(Path(path).read_text())
    if policy.get("automatic_cleanup_enabled") is not False:
        raise ValueError("automatic_cleanup_enabled must remain false; no executor is implemented")
    for category in "BCD":
        days = policy.get("retention_days", {}).get(category)
        if not isinstance(days, int) or isinstance(days, bool) or days < 1:
            raise ValueError("Review retention days must be positive integers")
    if policy.get("retention_days", {}).get("A") is not None:
        raise ValueError("Core data has no time-based retention")
    return policy


def checked_stores(values):
    stores = sorted(set(values or []))
    if not stores or any(not isinstance(x, int) or isinstance(x, bool) or x <= 0 for x in stores):
        raise ValueError("Explicit positive store IDs are required")
    return stores


def plan(db, policy, as_of, stores, batch_size, after_id):
    stores = checked_stores(stores)
    if not 1 <= batch_size <= 500 or after_id < 0:
        raise ValueError("batch_size must be 1..500 and after_id must be nonnegative")
    date = dt.date.fromisoformat(as_of)
    metadata = db.value("SELECT coalesce(json_agg(t),'[]'::json) FROM "
                        "(SELECT s.relname,s.n_live_tup,s.n_dead_tup,pg_total_relation_size(s.relid) AS total_bytes "
                        "FROM pg_stat_user_tables s WHERE s.schemaname='public' ORDER BY s.relname) t")
    fks = db.value("SELECT coalesce(json_agg(t),'[]'::json) FROM (SELECT n.nspname AS source_schema,"
                   "c.relname AS source,fn.nspname AS target_schema,fc.relname AS target,"
                   "pg_get_constraintdef(k.oid) AS definition FROM pg_constraint k "
                   "JOIN pg_class c ON c.oid=k.conrelid JOIN pg_namespace n ON n.oid=c.relnamespace "
                   "JOIN pg_class fc ON fc.oid=k.confrelid JOIN pg_namespace fn ON fn.oid=fc.relnamespace "
                   "WHERE k.contype='f' AND (n.nspname='public' OR fn.nspname='public') "
                   "ORDER BY n.nspname,c.relname,k.conname) t")
    relations = {row["relname"]: row for row in metadata}
    tables = []
    for name, (category, refs) in TABLES.items():
        tables.append({"table": "public." + name, "category": category,
                       "review_retention_days": policy["retention_days"].get(category),
                       "delete_supported": False, "block_reason": BLOCK[category],
                       "application_references": refs,
                       "foreign_keys": [fk for fk in fks if fk["source"] == name or fk["target"] == name],
                       "statistics": relations.get(name), "exists": name in relations})
    cutoff = date - dt.timedelta(days=policy["retention_days"]["C"])
    candidates = []
    if "audit_logs" in relations:
        store_sql = ",".join(map(str, stores))
        candidates = db.value("SELECT coalesce(json_agg(c),'[]'::json) FROM (SELECT a.id,a.store_id,a.action,"
                              "a.entity_type,a.created_at,to_jsonb(a) AS archive_row FROM public.audit_logs a "
                              f"WHERE a.id>{after_id} AND a.store_id IN ({store_sql}) "
                              f"AND a.created_at<{sql_string(cutoff.isoformat())}::timestamp "
                              f"ORDER BY a.id LIMIT {batch_size + 1}) c")
    has_more = len(candidates) > batch_size
    candidates = candidates[:batch_size]
    # The plan contains hashes, identities and review reasons, never customer/audit payload bodies.
    safe_candidates = [{key: row[key] for key in ("id", "store_id", "action", "entity_type", "created_at")}
                       | {"row_sha256": digest(row["archive_row"]), "delete_supported": False,
                          "block_reason": BLOCK["C"]} for row in candidates]
    return {"format": "restaurant-lifecycle-plan-v1", "source": db.identity(), "store_ids": stores,
            "as_of": date.isoformat(), "audit_cutoff_database_wall_time": cutoff.isoformat(),
            "policy_sha256": digest(policy), "automatic_cleanup_enabled": False, "delete_supported": False,
            "tables": tables, "audit_archive_copy_candidates": safe_candidates,
            "batch_size": batch_size, "after_id": after_id, "has_more": has_more,
            "next_after_id": candidates[-1]["id"] if candidates else after_id,
            "unknown_tables_protected": sorted(set(relations) - set(TABLES)),
            "note": "Age selects archive-copy review candidates only. No financial/security/action retention approval or deletion proof exists."}


def archive_audit(db, plan_value):
    if plan_value.get("format") != "restaurant-lifecycle-plan-v1" or plan_value.get("delete_supported") is not False:
        raise ValueError("Invalid read-only lifecycle plan")
    if db.identity() != plan_value["source"]:
        raise ValueError("Plan source no longer matches the selected database")
    candidates = plan_value["audit_archive_copy_candidates"]
    if not 1 <= len(candidates) <= 500:
        raise ValueError("An archive copy must contain 1..500 planned records")
    ids = [row["id"] for row in candidates]
    if any(not isinstance(x, int) or isinstance(x, bool) or x <= 0 for x in ids) or len(set(ids)) != len(ids):
        raise ValueError("Invalid planned IDs")
    rows = db.value("SELECT json_agg(to_jsonb(a) ORDER BY a.id) FROM public.audit_logs a WHERE a.id IN ("
                    + ",".join(map(str, ids)) + ")") or []
    expected = {row["id"]: row for row in candidates}
    if len(rows) != len(candidates) or any(digest(row) != expected[row["id"]]["row_sha256"] for row in rows):
        raise ValueError("Planned audit evidence changed; create a new plan")
    stores = checked_stores(plan_value["store_ids"])
    if any(row["store_id"] not in stores for row in rows):
        raise ValueError("Archive scope mismatch")
    value = {"format": "restaurant-audit-archive-copy-v1", "table": "public.audit_logs",
             "source": plan_value["source"], "store_ids": stores, "schema": db.audit_schema(),
             "plan_sha256": digest(plan_value), "rows": rows, "row_count": len(rows),
             "copy_only": True, "delete_supported": False}
    value["sha256"] = digest(value)
    return value


def verify_archive(value):
    if value.get("format") != "restaurant-audit-archive-copy-v1" or value.get("table") != "public.audit_logs":
        raise ValueError("Unsupported archive format/table")
    body = {key: item for key, item in value.items() if key != "sha256"}
    if value.get("sha256") != digest(body):
        raise ValueError("Archive checksum mismatch")
    rows = value.get("rows", [])
    if not 1 <= len(rows) <= 500 or value.get("row_count") != len(rows):
        raise ValueError("Archive size mismatch")
    stores = checked_stores(value.get("store_ids"))
    ids = [row.get("id") for row in rows]
    if len(set(ids)) != len(ids) or any(not isinstance(x, int) or isinstance(x, bool) or x <= 0 for x in ids):
        raise ValueError("Archive IDs must be unique positive integers")
    columns = {x["attname"] for x in value.get("schema", [])}
    if not columns or any(set(row) != columns or row.get("store_id") not in stores for row in rows):
        raise ValueError("Archive row schema/scope mismatch")
    return {"status": "VERIFIED_COPY_ONLY", "row_count": len(rows), "sha256": value["sha256"],
            "delete_supported": False}


def restore_local(db, archive, sandbox_root):
    verify_archive(archive)
    if db.host != "127.0.0.1" or not re.fullmatch(r"resource_lifecycle_[a-z0-9_]+", db.name):
        raise ValueError("Restore is restricted to a named loopback synthetic sandbox")
    root = Path(sandbox_root).resolve(strict=True)
    marker_file = root / ".resource-lifecycle-synthetic"
    if not marker_file.is_file() or marker_file.read_text().strip() != SANDBOX_MARKER:
        raise ValueError("Sandbox root must have the explicit synthetic-data marker")
    info = db.value("SELECT json_build_object('data_directory',current_setting('data_directory'),"
                    "'port',current_setting('port'),'marker',"
                    "(SELECT marker FROM public.resource_lifecycle_sandbox_guard LIMIT 1))")
    data_directory = Path(info["data_directory"]).resolve(strict=True)
    if info["marker"] != SANDBOX_MARKER or info["port"] != db.port or not data_directory.is_relative_to(root):
        raise ValueError("Synthetic sandbox guard/root/port mismatch")
    if db.audit_schema() != archive["schema"]:
        raise ValueError("Target schema differs from archive")
    data = sql_string(canonical(archive["rows"]))
    # All identity conflicts are checked before inserts; one transaction, no overwrite/upsert.
    body = """DECLARE entry jsonb; existing jsonb; sequence_name text;
BEGIN
  FOR entry IN SELECT value FROM jsonb_array_elements(""" + data + """::jsonb) LOOP
    SELECT to_jsonb(a) INTO existing FROM public.audit_logs a WHERE id=(entry->>'id')::bigint;
    IF FOUND AND existing IS DISTINCT FROM entry THEN
      RAISE EXCEPTION 'Archive identity conflict';
    END IF;
  END LOOP;
  FOR entry IN SELECT value FROM jsonb_array_elements(""" + data + """::jsonb) LOOP
    IF NOT EXISTS(SELECT 1 FROM public.audit_logs WHERE id=(entry->>'id')::bigint) THEN
      INSERT INTO public.audit_logs OVERRIDING SYSTEM VALUE
      SELECT * FROM jsonb_populate_record(NULL::public.audit_logs,entry);
    END IF;
  END LOOP;
  sequence_name := pg_get_serial_sequence('public.audit_logs','id');
  IF sequence_name IS NOT NULL THEN
    EXECUTE format('SELECT setval(%L,greatest((SELECT max(id) FROM public.audit_logs),(SELECT last_value FROM %s)),true)',sequence_name,sequence_name);
  END IF;
END;
"""
    # A dollar-quoted DO body terminates even when its tag occurs inside a SQL
    # string literal. Select a tag absent from the complete body, including data.
    # Lookahead also finds overlapping tags such as $restore_0$restore_1$.
    used_tags = set(re.findall(r"(?=(\$restore_[0-9]+\$))", body))
    number = 0
    while f"$restore_{number}$" in used_tags:
        number += 1
    delimiter = f"$restore_{number}$"
    sql = ("BEGIN ISOLATION LEVEL SERIALIZABLE;\n"
           "SET LOCAL standard_conforming_strings=on;\n"
           "LOCK TABLE public.audit_logs IN SHARE ROW EXCLUSIVE MODE;\n"
           "DO " + delimiter + "\n" + body + delimiter + ";\nCOMMIT;\n")
    db.run(sql, read_only=False)
    ids = ",".join(str(row["id"]) for row in archive["rows"])
    observed = db.value("SELECT json_agg(to_jsonb(a) ORDER BY a.id) FROM public.audit_logs a WHERE id IN (" + ids + ")")
    if observed != archive["rows"]:
        raise RuntimeError("Post-restore equality check failed")
    return {"status": "LOCAL_SYNTHETIC_RESTORE_VERIFIED", "row_count": len(observed), "sha256": archive["sha256"]}


def main():
    parser = argparse.ArgumentParser(description=__doc__)
    modes = parser.add_subparsers(dest="mode", required=True)
    p = modes.add_parser("plan")
    p.add_argument("--policy", default=str(Path(__file__).with_name("policy.example.json")))
    p.add_argument("--as-of", required=True)
    p.add_argument("--store-id", type=int, action="append", required=True)
    p.add_argument("--batch-size", type=int)
    p.add_argument("--after-id", type=int, default=0)
    p.add_argument("--output", required=True)
    p = modes.add_parser("archive-audit")
    p.add_argument("--plan", required=True)
    p.add_argument("--output", required=True)
    p = modes.add_parser("verify-archive")
    p.add_argument("--archive", required=True)
    p = modes.add_parser("restore-local")
    p.add_argument("--archive", required=True)
    p.add_argument("--sandbox-root", required=True)
    args = parser.parse_args()
    if args.mode == "plan":
        policy = load_policy(args.policy)
        value = plan(Database(), policy, args.as_of, args.store_id,
                     args.batch_size if args.batch_size is not None else policy.get("batch_size", 100), args.after_id)
        write_private(args.output, value)
        print(canonical({"status": "DRY_RUN_ONLY", "delete_supported": False,
                         "archive_copy_candidates": len(value["audit_archive_copy_candidates"]),
                         "has_more": value["has_more"], "next_after_id": value["next_after_id"], "plan_sha256": digest(value)}))
    elif args.mode == "archive-audit":
        value = archive_audit(Database(), read_private(args.plan))
        write_private(args.output, value)
        print(canonical(verify_archive(value)))
    elif args.mode == "verify-archive":
        print(canonical(verify_archive(read_private(args.archive))))
    else:
        print(canonical(restore_local(Database(), read_private(args.archive), args.sandbox_root)))


if __name__ == "__main__":
    try:
        main()
    except (ValueError, RuntimeError, OSError, subprocess.TimeoutExpired) as error:
        print(str(error), file=sys.stderr)
        sys.exit(1)
