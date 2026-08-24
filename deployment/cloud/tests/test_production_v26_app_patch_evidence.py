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
            return MODULE.validate(handle.name, MODULE.BUSINESS_PROFILE, self.sha, self.backend, self.frontend, self.env, self.preflight, self.approval, self.run_id)

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

    def small_evidence(self):
        return "\n".join((
            "HOTFIX_CLASS|SMALL_FRONTEND_DISPLAY_ONLY",
            "ENVIRONMENT|restaurant-pos-staging",
            f"ACCEPTED_SHA|{self.sha}",
            f"BACKEND_IMAGE_ID|{self.backend}",
            f"FRONTEND_IMAGE_ID|{self.frontend}",
            "FLYWAY|26|26|true",
            "VISUAL|Capillary|毛细（1）|PASS",
            "VISUAL|Thin|细（2）|PASS",
            "VISUAL|Sanxi|三细（3）|PASS",
            "VISUAL|Erxi|二细（4）|PASS",
            "VISUAL|Leek Leaf|韭叶（5）|PASS",
            "VISUAL|Wide|宽（6）|PASS",
            "VISUAL|Extra Wide|大宽（7）|PASS",
            "SELECTION|Thin_active_after_click|PASS",
            "ADD_TO_ORDER|PASS",
            "ORDER_SUMMARY_SEMANTIC|Thin / 细|PASS",
            "MODAL_RUNTIME_ERRORS|0|PASS",
            "HEALTH|PASS",
            "PRODUCTION_MUTATION|NONE",
            "RESULT|PASS",
        )) + "\n"

    def validate_small(self, content, **overrides):
        values = {"source_sha": self.sha, "backend_image_id": self.backend, "frontend_image_id": self.frontend}
        values.update(overrides)
        with tempfile.NamedTemporaryFile("w", encoding="utf-8") as handle:
            handle.write(content)
            handle.flush()
            return MODULE.validate(
                handle.name, MODULE.SMALL_FRONTEND_PROFILE, values["source_sha"], values["backend_image_id"],
                values["frontend_image_id"], self.env, self.preflight, self.approval, self.run_id,
            )

    def test_accepts_small_frontend_display_evidence(self):
        self.assertEqual(self.validate_small(self.small_evidence())["result"], "PASS")

    def test_small_frontend_display_rejects_wrong_bindings_and_failed_visual_gate(self):
        mutations = (
            self.small_evidence().replace(self.sha, "9" * 40),
            self.small_evidence().replace(self.backend, "sha256:" + "c" * 64),
            self.small_evidence().replace(self.frontend, "sha256:" + "d" * 64),
            self.small_evidence().replace("SMALL_FRONTEND_DISPLAY_ONLY", "SMALL_FRONTEND_DISPLAY_ONLY_EXTRA"),
            self.small_evidence().replace("ADD_TO_ORDER|PASS", "ADD_TO_ORDER|FAIL"),
            self.small_evidence().replace("VISUAL|Thin|细（2）|PASS", "VISUAL|Thin|细（2）|FAIL"),
        )
        for content in mutations:
            with self.subTest(content=content[:80]), self.assertRaises(ValueError):
                self.validate_small(content)

    def test_rejects_unknown_profile(self):
        with tempfile.NamedTemporaryFile("w", encoding="utf-8") as handle:
            handle.write(self.small_evidence())
            handle.flush()
            with self.assertRaises(ValueError):
                MODULE.validate(handle.name, "SMALL_FRONTEND_DISPLAY_ONLY_EXTRA", self.sha, self.backend, self.frontend,
                                self.env, self.preflight, self.approval, self.run_id)


if __name__ == "__main__":
    unittest.main()
