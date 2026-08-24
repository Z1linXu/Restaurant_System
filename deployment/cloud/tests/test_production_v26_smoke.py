import base64
import hashlib
import hmac
import importlib.util
import json
import pathlib
import subprocess
import sys
import unittest
from unittest import mock


PATH = pathlib.Path(__file__).parents[1] / "production-v26-smoke.py"
SPEC = importlib.util.spec_from_file_location("production_v26_smoke", PATH)
MODULE = importlib.util.module_from_spec(SPEC)
assert SPEC.loader is not None
SPEC.loader.exec_module(MODULE)


class ProductionV26SmokeTest(unittest.TestCase):
    def test_inconsistent_organization_claim_keeps_database_authority(self):
        MODULE.validate_database_organization_authority(
            {"id": 1, "organization_id": 10},
            {
                "organizations": [{"id": 10}],
                "stores": [{"id": 1, "organization_id": 10}],
            },
            1,
            10,
            1000010,
        )

    def test_inconsistent_organization_claim_cannot_replace_context_or_workspace(self):
        valid_workspaces = {
            "organizations": [{"id": 10}],
            "stores": [{"id": 1, "organization_id": 10}],
        }
        with self.subTest("context"):
            with self.assertRaises(SystemExit):
                MODULE.validate_database_organization_authority(
                    {"id": 1, "organization_id": 1000010},
                    valid_workspaces,
                    1,
                    10,
                    1000010,
                )
        with self.subTest("workspace"):
            with self.assertRaises(SystemExit):
                MODULE.validate_database_organization_authority(
                    {"id": 1, "organization_id": 10},
                    {
                        "organizations": [{"id": 10}, {"id": 1000010}],
                        "stores": [{"id": 1, "organization_id": 10}],
                    },
                    1,
                    10,
                    1000010,
                )
        with self.subTest("workspace store"):
            with self.assertRaises(SystemExit):
                MODULE.validate_database_organization_authority(
                    {"id": 1, "organization_id": 10},
                    {
                        "organizations": [{"id": 10}],
                        "stores": [
                            {"id": 1, "organization_id": 10},
                            {"id": 2, "organization_id": 1000010},
                        ],
                    },
                    1,
                    10,
                    1000010,
                )

    def test_minted_token_has_valid_hs256_signature_and_short_expiry(self):
        secret = "s" * 48
        token = MODULE.mint_token(secret, 1, 2, 1, 1, "OWNER")
        header, payload, signature = token.split(".")
        expected = base64.urlsafe_b64encode(
            hmac.new(secret.encode(), f"{header}.{payload}".encode(), hashlib.sha256).digest()
        ).rstrip(b"=").decode()
        claims = json.loads(base64.urlsafe_b64decode(payload + "=" * (-len(payload) % 4)))
        self.assertEqual(signature, expected)
        self.assertEqual(claims["role_code"], "OWNER")
        self.assertLessEqual(claims["exp"] - claims["iat"], 600)

    def test_api_requires_loopback_or_prevalidated_internal_host(self):
        with self.assertRaises(SystemExit):
            MODULE.Api("https://production.example", "token")
        with self.assertRaises(SystemExit):
            MODULE.Api("http://192.0.2.10", "token")
        MODULE.Api("http://127.0.0.1:18080", "token")
        MODULE.Api("http://172.20.0.4", "token", "172.20.0.4")
        with self.assertRaises(SystemExit):
            MODULE.Api("http://172.20.0.4", "token", "172.20.0.5")

    def test_write_target_rejects_live_before_docker_access(self):
        with mock.patch.object(MODULE, "docker_json") as docker_json:
            with self.assertRaises(SystemExit):
                MODULE.validate_rehearsal_target("http://172.20.0.4", "cloud-db-1", "a" * 64, "b" * 64, "c" * 64, "run", "network")
            docker_json.assert_not_called()

    def test_write_target_accepts_only_exact_internal_run_owned_clone(self):
        container_id = "a" * 64
        backend_id = "b" * 64
        api_id = "c" * 64
        with mock.patch.object(
            MODULE,
            "docker_json",
            side_effect=[
                [{
                    "Id": container_id,
                    "Config": {"Labels": {"restaurant.production-v26-rehearsal": "run-1"}},
                    "NetworkSettings": {"Networks": {"isolated-1": {"IPAddress": "172.20.0.2"}}, "Ports": {"5432/tcp": None}},
                }],
                [{
                    "Id": backend_id,
                    "Config": {"Labels": {"restaurant.production-v26-rehearsal": "run-1"}},
                    "NetworkSettings": {"Networks": {"isolated-1": {"IPAddress": "172.20.0.3"}}, "Ports": {"8080/tcp": None}},
                }],
                [{
                    "Id": api_id,
                    "Config": {"Labels": {"restaurant.production-v26-rehearsal": "run-1"}},
                    "NetworkSettings": {
                        "Networks": {"isolated-1": {"IPAddress": "172.20.0.4"}},
                        "Ports": {"80/tcp": None},
                    },
                }],
                [{
                    "Internal": True,
                    "Labels": {"restaurant.production-v26-rehearsal": "run-1"},
                    "Containers": {container_id: {}, backend_id: {}, api_id: {}},
                }],
            ],
        ):
            self.assertEqual(
                MODULE.validate_rehearsal_target("http://172.20.0.4", "clone-db", container_id, backend_id, api_id, "run-1", "isolated-1"),
                "172.20.0.4",
            )

    def test_rehearsal_target_rejects_any_published_port(self):
        container_id = "a" * 64
        backend_id = "b" * 64
        api_id = "c" * 64
        with mock.patch.object(
            MODULE,
            "docker_json",
            side_effect=[
                [{
                    "Id": container_id,
                    "Config": {"Labels": {"restaurant.production-v26-rehearsal": "run-1"}},
                    "NetworkSettings": {"Networks": {"isolated-1": {"IPAddress": "172.20.0.2"}}, "Ports": {}},
                }],
                [{
                    "Id": backend_id,
                    "Config": {"Labels": {"restaurant.production-v26-rehearsal": "run-1"}},
                    "NetworkSettings": {"Networks": {"isolated-1": {"IPAddress": "172.20.0.3"}}, "Ports": {}},
                }],
                [{
                    "Id": api_id,
                    "Config": {"Labels": {"restaurant.production-v26-rehearsal": "run-1"}},
                    "HostConfig": {"PortBindings": {"80/tcp": [{"HostIp": "127.0.0.1", "HostPort": ""}]}},
                    "NetworkSettings": {
                        "Networks": {"isolated-1": {"IPAddress": "172.20.0.4"}},
                        "Ports": {"80/tcp": None},
                    },
                }],
            ],
        ):
            with self.assertRaises(SystemExit):
                MODULE.validate_rehearsal_target(
                    "http://172.20.0.4", "clone-db", container_id, backend_id, api_id, "run-1", "isolated-1"
                )

    def test_write_target_rejects_wrong_owner_label(self):
        container_id = "b" * 64
        with mock.patch.object(
            MODULE,
            "docker_json",
            return_value=[{
                "Id": container_id,
                "Config": {"Labels": {"restaurant.production-v26-rehearsal": "another-run"}},
                "NetworkSettings": {"Networks": {"isolated-1": {"IPAddress": "172.20.0.2"}}, "Ports": {}},
            }],
        ):
            with self.assertRaises(SystemExit):
                MODULE.validate_rehearsal_target("http://172.20.0.4", "clone-db", container_id, "c" * 64, "d" * 64, "run-1", "isolated-1")

    def test_write_target_rejects_default_production_port_before_docker_access(self):
        with mock.patch.object(MODULE, "docker_json") as docker_json:
            with self.assertRaises(SystemExit):
                MODULE.validate_rehearsal_target(
                    "http://127.0.0.1",
                    "clone-db",
                    "a" * 64,
                    "b" * 64,
                    "c" * 64,
                    "run-1",
                    "isolated-1",
                )
            docker_json.assert_not_called()

    def test_database_timeout_fails_closed(self):
        with mock.patch.object(
            MODULE.subprocess,
            "run",
            side_effect=subprocess.TimeoutExpired(cmd=["docker"], timeout=20),
        ):
            with self.assertRaises(SystemExit):
                MODULE.run_psql("clone-db", "select 1")

    def test_component_option_ids_match_legacy_and_are_deterministic(self):
        self.assertEqual(MODULE.component_option_id("COMBO_EGG", "combo_tea_egg"), -20101)
        first = MODULE.component_option_id("COMBO_SIDE", "new_component")
        self.assertEqual(first, MODULE.component_option_id("combo_side", "NEW_COMPONENT"))
        self.assertLess(first, 0)

    def test_main_write_guard_runs_before_identity_or_token_use(self):
        argv = [
            "production-v26-smoke.py",
            "--base-url", "http://127.0.0.1:18080",
            "--db-container", "cloud-db-1",
            "--mode", "write",
            "--expected-db-container-id", "c" * 64,
            "--expected-backend-container-id", "d" * 64,
            "--expected-api-container-id", "e" * 64,
            "--expected-run-id", "run-1",
            "--expected-network", "isolated-1",
        ]
        with mock.patch.object(sys, "argv", argv), mock.patch.object(MODULE, "numeric_identity") as identity:
            with self.assertRaises(SystemExit):
                MODULE.main()
            identity.assert_not_called()


if __name__ == "__main__":
    unittest.main()
