import hashlib
import importlib.util
import json
import pathlib
import tempfile
import unittest

ROOT = pathlib.Path(__file__).resolve().parents[3]
PATH = ROOT / "deployment/cloud/production-v26-app-patch-evidence.py"
SPEC = importlib.util.spec_from_file_location("patch_evidence", PATH)
MODULE = importlib.util.module_from_spec(SPEC)
SPEC.loader.exec_module(MODULE)


class PatchEvidenceTest(unittest.TestCase):
    sha = "1" * 40
    env = "2" * 64
    preflight = "3" * 64
    approval = "4" * 64
    run_id = "5" * 32
    backend = "sha256:" + "a" * 64
    frontend = "sha256:" + "b" * 64

    def evidence(self):
        scope = (
            "organization_id=10;target_store_id=;source_store_id=1;"
            "profile_code=CHINATOWN_MENU_2026_02_02;preflight=" + self.preflight + ";"
            "acceptance_run_id=" + self.run_id
        )
        fingerprint = MODULE.sha256_lines((
            "environment=restaurant-pos-staging",
            "action=business-store-create-acceptance",
            "approved_sha=" + self.sha,
            "env_sha256=" + self.env,
            "scope=" + scope,
        ))
        data = {
            "schema": "V26_BUSINESS_STORE_CREATE_ACCEPTANCE_V1",
            "run_id": self.run_id,
            "source_sha": self.sha,
            "backend_image_id": self.backend,
            "frontend_image_id": self.frontend,
            "environment_sha256": self.env,
            "runtime_preflight_sha256": self.preflight,
            "owner_approval_sha256": self.approval,
            "request_fingerprint": fingerprint,
            "request_body_sha256": "6" * 64,
            "organization_id": 10,
            "foreign_organization_id": 20,
            "store_id": 44,
            **{key: "PASS" for key in MODULE.PASS_FIELDS},
        }
        return data

    def validate(self, content):
        with tempfile.NamedTemporaryFile("w", encoding="utf-8") as handle:
            handle.write(content)
            handle.flush()
            return MODULE.validate(handle.name, self.sha, self.backend, self.frontend, self.env, self.preflight, self.approval, self.run_id)

    def test_valid_strict_evidence(self):
        self.assertEqual(self.validate(json.dumps(self.evidence()))["store_id"], 44)

    def test_rejects_unknown_missing_or_failed_gate(self):
        for mutate in (
            lambda d: d.update(extra="value"),
            lambda d: d.pop("fresh_create"),
            lambda d: d.update(fresh_create="FAIL"),
            lambda d: d.update(request_fingerprint="0" * 64),
            lambda d: d.update(foreign_organization_id=10),
            lambda d: d.update(backend_image_id="sha256:" + "c" * 64),
            lambda d: d.update(frontend_image_id="sha256:" + "d" * 64),
        ):
            data = self.evidence()
            mutate(data)
            with self.assertRaises(ValueError):
                self.validate(json.dumps(data))

    def test_rejects_duplicate_key_and_spliced_binding(self):
        data = json.dumps(self.evidence())
        duplicate = data[:-1] + ',"final_result":"PASS"}'
        with self.assertRaises(ValueError):
            self.validate(duplicate)
        spliced = self.evidence()
        spliced["runtime_preflight_sha256"] = "9" * 64
        with self.assertRaises(ValueError):
            self.validate(json.dumps(spliced))


if __name__ == "__main__":
    unittest.main()
