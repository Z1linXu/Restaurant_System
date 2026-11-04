#!/usr/bin/env python3
"""Unit protection checks plus opt-in tests against a dedicated synthetic PostgreSQL."""
import copy
import importlib.util
import json
import os
from pathlib import Path
import subprocess
import sys
import tempfile
import unittest

sys.dont_write_bytecode = True

HERE = Path(__file__).resolve().parent
spec = importlib.util.spec_from_file_location("lifecycle", HERE / "lifecycle.py")
life = importlib.util.module_from_spec(spec)
spec.loader.exec_module(life)


class PolicySafetyTest(unittest.TestCase):
    def test_automatic_cleanup_cannot_be_enabled(self):
        with tempfile.TemporaryDirectory() as directory:
            p = Path(directory) / "policy.json"
            value = json.loads((HERE / "policy.example.json").read_text())
            value["automatic_cleanup_enabled"] = True
            p.write_text(json.dumps(value))
            with self.assertRaisesRegex(ValueError, "must remain false"):
                life.load_policy(p)

    def test_every_reviewed_table_is_blocked_and_core_has_no_retention_age(self):
        self.assertEqual(len(life.TABLES), 22)
        self.assertTrue(all(category in life.BLOCK for category, refs in life.TABLES.values()))
        self.assertTrue(all(refs for category, refs in life.TABLES.values()))
        self.assertIsNone(life.load_policy(HERE / "policy.example.json")["retention_days"]["A"])

    def test_no_execute_or_delete_cli_mode_exists(self):
        result = subprocess.run(["python3", str(HERE / "lifecycle.py"), "execute"], capture_output=True, text=True)
        self.assertNotEqual(result.returncode, 0)
        self.assertIn("invalid choice", result.stderr)

    def test_private_output_never_overwrites_and_public_input_is_rejected(self):
        with tempfile.TemporaryDirectory() as directory:
            p = Path(directory) / "evidence.json"
            life.write_private(p, {"value": 1})
            self.assertEqual(p.stat().st_mode & 0o777, 0o600)
            with self.assertRaises(FileExistsError):
                life.write_private(p, {"value": 2})
            p.chmod(0o644)
            with self.assertRaises(ValueError):
                life.read_private(p)

    def test_non_loopback_restore_fails_before_database_use(self):
        class Fake:
            host, name = "restaurant-prod", "resource_lifecycle_test"
            def value(self, query):
                raise AssertionError("Must not connect")
        archive = {"format": "restaurant-audit-archive-copy-v1", "table": "public.audit_logs",
                   "store_ids": [1], "schema": [{"attname": "id"}, {"attname": "store_id"}],
                   "rows": [{"id": 1, "store_id": 1}], "row_count": 1}
        archive["sha256"] = life.digest(archive)
        with self.assertRaisesRegex(ValueError, "loopback"):
            life.restore_local(Fake(), archive, "/tmp")


@unittest.skipUnless(os.environ.get("LIFECYCLE_TEST_POSTGRES") == "1", "dedicated synthetic PostgreSQL only")
class PostgresLifecycleTest(unittest.TestCase):
    @classmethod
    def setUpClass(cls):
        cls.db = life.Database()
        cls.root = Path(os.environ["LIFECYCLE_SANDBOX_ROOT"])
        if cls.db.host != "127.0.0.1" or cls.db.port != "55487" or cls.db.name != "resource_lifecycle_test":
            raise RuntimeError("Refuse a non-test database")
        data_directory = Path(cls.db.value("SELECT to_json(current_setting('data_directory'))")).resolve()
        if not data_directory.is_relative_to(cls.root.resolve()) or (cls.root / ".resource-lifecycle-synthetic").read_text().strip() != life.SANDBOX_MARKER:
            raise RuntimeError("Refuse an unmarked or unrelated cluster")
        cls.policy = life.load_policy(HERE / "policy.example.json")

    def setUp(self):
        # This fixture is created by run-isolated-tests.sh in a disposable local cluster.
        self.db.run("""
DROP SCHEMA public CASCADE;
CREATE SCHEMA public;
CREATE TABLE resource_lifecycle_sandbox_guard(marker text NOT NULL);
INSERT INTO resource_lifecycle_sandbox_guard VALUES ('SYNTHETIC_ONLY_NO_PRODUCTION_DATA');
CREATE TABLE stores(id bigint PRIMARY KEY);
INSERT INTO stores VALUES (1),(2);
CREATE TABLE audit_logs(
 id bigserial PRIMARY KEY,store_id bigint REFERENCES stores(id),actor_user_id bigint,
 actor_name_snapshot varchar(255),actor_role_snapshot varchar(255),action varchar(255) NOT NULL,
 entity_type varchar(255),entity_id bigint,summary varchar(1000),metadata_json text,
 created_at timestamp,request_ip varchar(255),user_agent varchar(1000));
INSERT INTO audit_logs(id,store_id,action,entity_type,entity_id,summary,metadata_json,created_at) VALUES
 (1,1,'ORDER_FINANCIAL_CHANGE','ORDER',10,'Synthetic only','{"source":"synthetic"}','2024-01-01'),
 (2,1,'PRINT_REVIEW','PRINT_JOB',20,'Synthetic only','{"quote":"O''Brien"}','2024-01-02'),
 (3,1,'RECENT_ACTION','ORDER',10,'Recent synthetic',NULL,'2026-11-03'),
 (4,2,'FOREIGN_STORE','ORDER',11,'Foreign synthetic',NULL,'2024-01-01'),
 (5,NULL,'GLOBAL_ACTION','USER',12,'Global synthetic',NULL,'2024-01-01'),
 (6,1,'OLDER_ACTION','ORDER',10,'Third old synthetic',NULL,'2024-01-03');
CREATE TABLE orders(id bigint PRIMARY KEY,status text,total_amount numeric(12,2));
INSERT INTO orders VALUES(10,'completed',10.00),(11,'preparing',20.00);
CREATE TABLE print_jobs(id bigint PRIMARY KEY,order_id bigint REFERENCES orders(id),status text,dispatch_source_key text);
INSERT INTO print_jobs VALUES(20,10,'PRINTED','synthetic-dedup'),(21,11,'PRINTING','synthetic-active');
CREATE TABLE print_job_attempts(id bigint PRIMARY KEY,print_job_id bigint,token text);
INSERT INTO print_job_attempts VALUES(30,20,'synthetic-token');
CREATE TABLE order_dispatch_outbox(id bigint PRIMARY KEY,status text,source_key text);
INSERT INTO order_dispatch_outbox VALUES(40,'PENDING','synthetic-pending');
CREATE TABLE uber_eats_orders(id bigint PRIMARY KEY,status text,item_financial_snapshot_json text);
INSERT INTO uber_eats_orders VALUES(50,'RELEASED_TO_KITCHEN','synthetic-pending-evidence');
CREATE TABLE sales_daily_summary(summary_date date,net_sales numeric(12,2),order_count integer);
INSERT INTO sales_daily_summary VALUES('2026-11-03',10.00,1);
CREATE TABLE sales_hourly_summary(summary_date date,sales_amount numeric(12,2));
INSERT INTO sales_hourly_summary VALUES('2026-11-03',10.00);
CREATE TABLE menu_item_sales_summary(summary_date date,sales_amount numeric(12,2),quantity_sold integer);
INSERT INTO menu_item_sales_summary VALUES('2026-11-03',10.00,2);
""", read_only=False)

    def plan(self, **kwargs):
        return life.plan(self.db, self.policy, "2026-11-03", [1], kwargs.get("batch", 2), kwargs.get("after", 0))

    def protected_fingerprint(self):
        names = ["orders", "print_jobs", "print_job_attempts", "order_dispatch_outbox", "uber_eats_orders",
                 "sales_daily_summary", "sales_hourly_summary", "menu_item_sales_summary"]
        return {name: self.db.value(f"SELECT coalesce(json_agg(t),'[]'::json) FROM public.{name} t") for name in names}

    def test_plan_is_bounded_resumable_private_and_has_no_deletable_records(self):
        before = self.protected_fingerprint()
        plan = self.plan()
        self.assertEqual([x["id"] for x in plan["audit_archive_copy_candidates"]], [1, 2])
        self.assertTrue(plan["has_more"])
        self.assertEqual(plan["next_after_id"], 2)
        self.assertEqual([x["id"] for x in self.plan(after=2)["audit_archive_copy_candidates"]], [6])
        self.assertTrue(all(not x["delete_supported"] for x in plan["tables"]))
        self.assertNotIn("archive_row", life.canonical(plan))
        self.assertNotIn("O'Brien", life.canonical(plan))
        self.assertEqual(before, self.protected_fingerprint())
        self.assertEqual(life.digest(plan), life.digest(self.plan()))

    def test_actual_unknown_fk_and_application_references_are_reported(self):
        self.db.run("CREATE TABLE unknown_audit_reference(id bigint PRIMARY KEY,audit_id bigint REFERENCES audit_logs(id));", read_only=False)
        plan = self.plan()
        audit = next(x for x in plan["tables"] if x["table"] == "public.audit_logs")
        self.assertTrue(any(x["source"] == "unknown_audit_reference" for x in audit["foreign_keys"]))
        self.assertIn("unknown_audit_reference", plan["unknown_tables_protected"])
        self.assertTrue(audit["application_references"])
        self.assertFalse(audit["delete_supported"])

    def test_archive_copy_simulated_removal_and_restore_preserve_reports_printing_and_dedup(self):
        protected = self.protected_fingerprint()
        archive = life.archive_audit(self.db, self.plan())
        self.assertEqual(life.verify_archive(archive)["row_count"], 2)
        # Simulation only, never a tool cleanup path, never a shared/real database.
        self.db.run("DELETE FROM audit_logs WHERE id IN (1,2);", read_only=False)
        restored = life.restore_local(self.db, archive, self.root)
        self.assertEqual(restored["status"], "LOCAL_SYNTHETIC_RESTORE_VERIFIED")
        self.assertEqual(protected, self.protected_fingerprint())
        first = self.db.value("SELECT json_agg(to_jsonb(a) ORDER BY id) FROM audit_logs a")
        life.restore_local(self.db, archive, self.root)
        self.assertEqual(first, self.db.value("SELECT json_agg(to_jsonb(a) ORDER BY id) FROM audit_logs a"))
        next_id = int(self.db.run("SELECT nextval(pg_get_serial_sequence('public.audit_logs','id'))", read_only=False))
        self.assertGreater(next_id, 6)

    def test_conflicting_identity_rolls_back_entire_restore_without_overwrite(self):
        archive = life.archive_audit(self.db, self.plan())
        self.db.run("DELETE FROM audit_logs WHERE id IN (1,2); INSERT INTO audit_logs(id,store_id,action) VALUES(2,1,'NEW_RECORD');", read_only=False)
        before = self.db.value("SELECT json_agg(to_jsonb(a) ORDER BY id) FROM audit_logs a")
        with self.assertRaises(RuntimeError):
            life.restore_local(self.db, archive, self.root)
        self.assertEqual(before, self.db.value("SELECT json_agg(to_jsonb(a) ORDER BY id) FROM audit_logs a"))

    def test_restore_preserves_dollar_tags_quotes_multiline_and_sql_like_audit_text(self):
        protected = self.protected_fingerprint()
        metadata = json.dumps({
            "literal_delimiter": "$restore$",
            "overlapping_candidate_tags": "$restore_0$restore_1$ $restore_2$",
            "quotes": "O'Brien said \"keep this\"",
            "multiline": "first line\nsecond line\r\nthird line",
            "backslashes": "C:\\synthetic\\archive\\quote\\'",
            "sql_like": "'); DROP TABLE public.orders; --\nCOMMIT; UPDATE print_jobs SET status='PRINTED';",
        }, ensure_ascii=False, indent=2)
        summary = "Literal $restore$ and O'Brien\nsecond line with \"quotes\""
        self.db.run("UPDATE audit_logs SET metadata_json=" + life.sql_string(metadata)
                    + ",summary=" + life.sql_string(summary) + " WHERE id=1;", read_only=False)
        archive = life.archive_audit(self.db, self.plan())
        self.assertEqual(life.verify_archive(archive)["status"], "VERIFIED_COPY_ONLY")
        self.db.run("DELETE FROM audit_logs WHERE id IN (1,2);", read_only=False)
        result = life.restore_local(self.db, archive, self.root)
        self.assertEqual(result["status"], "LOCAL_SYNTHETIC_RESTORE_VERIFIED")
        restored = self.db.value("SELECT to_jsonb(a) FROM audit_logs a WHERE id=1")
        self.assertEqual(restored["metadata_json"], metadata)
        self.assertEqual(restored["summary"], summary)
        self.assertEqual(protected, self.protected_fingerprint())
        life.restore_local(self.db, archive, self.root)
        self.assertEqual(restored, self.db.value("SELECT to_jsonb(a) FROM audit_logs a WHERE id=1"))

    def test_changed_evidence_checksum_schema_and_sandbox_markers_fail_closed(self):
        plan = self.plan()
        self.db.run("UPDATE audit_logs SET summary='Changed evidence' WHERE id=1;", read_only=False)
        with self.assertRaisesRegex(ValueError, "changed"):
            life.archive_audit(self.db, plan)
        archive = life.archive_audit(self.db, self.plan())
        tampered = copy.deepcopy(archive); tampered["rows"][0]["summary"] = "Tampered"
        with self.assertRaisesRegex(ValueError, "checksum"):
            life.verify_archive(tampered)
        self.db.run("ALTER TABLE audit_logs ADD COLUMN future_evidence text;", read_only=False)
        with self.assertRaisesRegex(ValueError, "schema"):
            life.restore_local(self.db, archive, self.root)
        self.db.run("ALTER TABLE audit_logs DROP COLUMN future_evidence; UPDATE resource_lifecycle_sandbox_guard SET marker='NOT_APPROVED';", read_only=False)
        with self.assertRaisesRegex(ValueError, "guard"):
            life.restore_local(self.db, archive, self.root)

    def test_cli_plan_archive_and_verify_use_private_files_without_changing_database(self):
        before = self.protected_fingerprint()
        with tempfile.TemporaryDirectory() as directory:
            plan = Path(directory) / "plan.json"; archive = Path(directory) / "archive.json"
            def cli(*args):
                return json.loads(subprocess.check_output(["python3", str(HERE / "lifecycle.py"), *args], text=True))
            result = cli("plan", "--as-of", "2026-11-03", "--store-id", "1", "--batch-size", "2", "--output", str(plan))
            self.assertEqual(result["status"], "DRY_RUN_ONLY")
            self.assertEqual(plan.stat().st_mode & 0o777, 0o600)
            cli("archive-audit", "--plan", str(plan), "--output", str(archive))
            self.assertEqual(archive.stat().st_mode & 0o777, 0o600)
            self.assertEqual(cli("verify-archive", "--archive", str(archive))["status"], "VERIFIED_COPY_ONLY")
        self.assertEqual(before, self.protected_fingerprint())


if __name__ == "__main__":
    unittest.main(verbosity=2)
