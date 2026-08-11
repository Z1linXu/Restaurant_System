import importlib.util
import json
import os
import tempfile
import time
import unittest
from pathlib import Path


ROOT = Path(__file__).resolve().parents[1]
MODULE_PATH = ROOT / "tools" / "twin_staging_staff_reconcile.py"
SPEC = importlib.util.spec_from_file_location("twin_staging_staff_reconcile", MODULE_PATH)
MODULE = importlib.util.module_from_spec(SPEC)
SPEC.loader.exec_module(MODULE)


class TwinStagingStaffReconcileTest(unittest.TestCase):
    def test_secret_contract(self):
        value = {
            "owner_login_identifier": "STG005_OWNER_20260808_R01",
            "owner_target_identifier": "owner",
            "owner_login_password": "owner-password-value",
            "staff": [
                {"username": "manager", "role_code": "MANAGER", "password": "M" * 20},
                {"username": "staffA", "role_code": "FRONTDESK", "password": "A" * 20},
                {"username": "staffB", "role_code": "FRONTDESK", "password": "B" * 20},
            ],
        }
        with tempfile.NamedTemporaryFile(mode="w", encoding="utf-8", delete=False) as handle:
            json.dump(value, handle)
            path = handle.name
        try:
            os.chmod(path, 0o600)
            with open(path, "r", encoding="utf-8") as handle:
                parsed = MODULE.read_secrets(handle.fileno())
            self.assertEqual("owner", parsed["owner_target_identifier"])
        finally:
            os.unlink(path)

    def test_source_has_no_secret_output(self):
        source = MODULE_PATH.read_text(encoding="utf-8")
        self.assertNotIn("print(secret", source)
        self.assertNotIn("print(session", source)
        self.assertIn("credential_parity=NO", source)
        self.assertIn("/me/workspaces", source)
        self.assertIn("/reset-password", source)

    def test_runtime_evidence_binding(self):
        sha = "a" * 40
        payload = (
            f"OPS001_RUNTIME|BEFORE|APPROVED_SHA|{sha}\n"
            "OPS001_RUNTIME|BEFORE|FLYWAY|count=10|max_version=10|digest=abc\n"
            "OPS001_RUNTIME|BEFORE|STATUS|PASS\n"
        ).encode()
        with tempfile.NamedTemporaryFile(delete=False) as handle:
            handle.write(payload)
            path = Path(handle.name)
        try:
            os.chmod(path, 0o600)
            digest = __import__("hashlib").sha256(payload).hexdigest()
            MODULE.validate_runtime_evidence(path, digest, sha)
            os.utime(path, (time.time() - 901, time.time() - 901))
            with self.assertRaisesRegex(ValueError, "older than 15 minutes"):
                MODULE.validate_runtime_evidence(path, digest, sha)
        finally:
            os.unlink(path)


if __name__ == "__main__":
    unittest.main()
