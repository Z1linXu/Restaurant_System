import importlib.util
import pathlib
import unittest


PATH = pathlib.Path(__file__).parents[1] / "production-v26-evidence.py"
SPEC = importlib.util.spec_from_file_location("production_v26_evidence", PATH)
MODULE = importlib.util.module_from_spec(SPEC)
assert SPEC.loader is not None
SPEC.loader.exec_module(MODULE)


SOURCE = "a" * 40
BACKEND = "sha256:" + "b" * 64
FRONTEND = "sha256:" + "c" * 64
BACKUP = "d" * 64
RECOVERY = "e" * 64
BUSINESS = "1" * 64
PRINTING = "2" * 64
RUN_ID = "0123456789abcdef0123456789abcdef"


def rehearsal_text() -> str:
    return "\n".join([
        f"ANDROID_COMPATIBILITY|run_id={RUN_ID}|app_version=0.2.0-offline-pr7|version_code=2|webview_entry=unchanged|auth_token=unchanged|register_heartbeat=additive|pad_direct_contract=unchanged|routes_headers=unchanged|min_version_guard=absent|result=PASS",
        f"RESTORE|run_id={RUN_ID}|backup_sha256={BACKUP}|flyway=V10-exact|network_internal=true|volume_isolated=true|result=PASS",
        f"DATA_BASELINE|run_id={RUN_ID}|business_fingerprint={BUSINESS}|printing_fingerprint={PRINTING}|result=PASS",
        f"MIGRATION|run_id={RUN_ID}|from=V10|to=V26|migrations=16|ledger=exact|business_fingerprint=unchanged|printing_fingerprint=unchanged|result=PASS",
        f"ADDITIVE_INVARIANTS|run_id={RUN_ID}|stores=1|violations=0|result=PASS",
        f"READ_SMOKE|run_id={RUN_ID}|store_id=1|historical_detail=PASS|organization_claim_db_authority=PASS|wrong_store=PASS|websocket=PASS|result=PASS",
        f"WRITE_SMOKE|run_id={RUN_ID}|order_id=2|options=PASS|combo=PASS|inventory=PASS|printing_roles=PASS|mock_endpoint_free=PASS|result=PASS",
        f"TARGET_STACK|run_id={RUN_ID}|backend_image_id={BACKEND}|frontend_image_id={FRONTEND}|restart=PASS|result=PASS",
        f"RECOVERY_RESTORE_FAILURE_PROOF|run_id={RUN_ID}|restore_failed=true|primary_untouched=true|result=PASS",
        f"LEGACY_READ_SMOKE|run_id={RUN_ID}|store_id=1|checks=11|isolation=PASS|result=PASS",
        f"RECOVERY_PROOF|run_id={RUN_ID}|mode=validated-temp-db-switch|recovery_helper_sha256={RECOVERY}|restore_failure_original_untouched=true|validated_temp=true|flyway=V10-exact|business_fingerprint=restored|result=PASS",
        f"RESOURCE_CLEANUP|run_id={RUN_ID}|containers=0|networks=0|volumes=0|children=0|result=PASS",
        f"REHEARSAL|run_id={RUN_ID}|source_sha={SOURCE}|backend_image_id={BACKEND}|frontend_image_id={FRONTEND}|backup_sha256={BACKUP}|production_clone=true|real_printer=false|real_pad=false|result=PASS",
    ]) + "\n"


class ProductionV26EvidenceTest(unittest.TestCase):
    def test_exact_evidence_contract_passes(self):
        MODULE.verify_staging(
            "PHASE_B_PART2_STAGING_AUTOMATED_ACCEPTANCE = PASS\n",
            f"Final exact Staging artifact: `{SOURCE}`\nFlyway: V26\nAgent 6 reviewed the implementation\n",
            SOURCE,
        )
        MODULE.verify_rehearsal(rehearsal_text(), SOURCE, BACKEND, FRONTEND, BACKUP, RECOVERY, BUSINESS, PRINTING)

    def test_staging_failure_marker_is_rejected(self):
        with self.assertRaises(ValueError):
            MODULE.verify_staging(
                "PHASE_B_PART2_STAGING_AUTOMATED_ACCEPTANCE = PASS\nNO_GO|old failure\n",
                f"Final exact Staging artifact: `{SOURCE}`\nFlyway: V26\nAgent 6 reviewed the implementation\n",
                SOURCE,
            )

    def test_staging_duplicate_pass_marker_is_rejected(self):
        with self.assertRaises(ValueError):
            MODULE.verify_staging(
                "PHASE_B_PART2_STAGING_AUTOMATED_ACCEPTANCE = PASS\n" * 2,
                f"Final exact Staging artifact: `{SOURCE}`\nFlyway: V26\nAgent 6 reviewed the implementation\n",
                SOURCE,
            )

    def test_pass_words_cannot_replace_exact_artifact_binding(self):
        forged = rehearsal_text().replace(f"source_sha={SOURCE}", f"source_sha={'f' * 40}")
        with self.assertRaises(ValueError):
            MODULE.verify_rehearsal(forged, SOURCE, BACKEND, FRONTEND, BACKUP, RECOVERY)

    def test_stale_backup_fingerprint_is_rejected(self):
        with self.assertRaises(ValueError):
            MODULE.verify_rehearsal(
                rehearsal_text(),
                SOURCE,
                BACKEND,
                FRONTEND,
                BACKUP,
                RECOVERY,
                "3" * 64,
                PRINTING,
            )

    def test_missing_cleanup_proof_is_rejected(self):
        forged = "\n".join(
            line for line in rehearsal_text().splitlines() if not line.startswith("RESOURCE_CLEANUP|")
        )
        with self.assertRaises(ValueError):
            MODULE.verify_rehearsal(forged, SOURCE, BACKEND, FRONTEND, BACKUP, RECOVERY)

    def test_embedded_failure_marker_is_rejected(self):
        with self.assertRaises(ValueError):
            MODULE.verify_rehearsal(
                rehearsal_text() + "RECOVERY_NO_GO|simulated failure\n",
                SOURCE,
                BACKEND,
                FRONTEND,
                BACKUP,
                RECOVERY,
            )

    def test_pass_and_fail_for_same_marker_is_rejected(self):
        forged = rehearsal_text() + "TARGET_STACK|result=FAIL\n"
        with self.assertRaises(ValueError):
            MODULE.verify_rehearsal(forged, SOURCE, BACKEND, FRONTEND, BACKUP, RECOVERY)

    def test_duplicate_marker_is_rejected(self):
        duplicate = next(line for line in rehearsal_text().splitlines() if line.startswith("MIGRATION|"))
        with self.assertRaises(ValueError):
            MODULE.verify_rehearsal(rehearsal_text() + duplicate + "\n", SOURCE, BACKEND, FRONTEND, BACKUP, RECOVERY)

    def test_wrong_marker_order_is_rejected(self):
        lines = rehearsal_text().splitlines()
        left = next(index for index, line in enumerate(lines) if line.startswith("MIGRATION|"))
        right = next(index for index, line in enumerate(lines) if line.startswith("DATA_BASELINE|"))
        lines[left], lines[right] = lines[right], lines[left]
        with self.assertRaises(ValueError):
            MODULE.verify_rehearsal("\n".join(lines) + "\n", SOURCE, BACKEND, FRONTEND, BACKUP, RECOVERY)

    def test_cleanup_and_rehearsal_run_ids_must_match(self):
        forged = rehearsal_text().replace(
            "REHEARSAL|run_id=0123456789abcdef0123456789abcdef",
            "REHEARSAL|run_id=fedcba9876543210fedcba9876543210",
        )
        with self.assertRaises(ValueError):
            MODULE.verify_rehearsal(forged, SOURCE, BACKEND, FRONTEND, BACKUP, RECOVERY)

    def test_cross_run_marker_splice_is_rejected(self):
        forged = rehearsal_text().replace(
            f"MIGRATION|run_id={RUN_ID}",
            "MIGRATION|run_id=fedcba9876543210fedcba9876543210",
        )
        with self.assertRaises(ValueError):
            MODULE.verify_rehearsal(forged, SOURCE, BACKEND, FRONTEND, BACKUP, RECOVERY)


if __name__ == "__main__":
    unittest.main()
