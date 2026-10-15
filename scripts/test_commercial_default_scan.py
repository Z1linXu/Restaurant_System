import importlib.util
from pathlib import Path
import unittest

spec = importlib.util.spec_from_file_location("scanner", Path(__file__).with_name("scan-commercial-defaults.py"))
scanner = importlib.util.module_from_spec(spec)
spec.loader.exec_module(scanner)


class CommercialDefaultScanTest(unittest.TestCase):
    def test_safe_synthetic_values_and_environment_placeholders(self):
        self.assertEqual([], scanner.violations("https://example.invalid 127.0.0.1 192.0.2.12\npassword: ${TEST_PASSWORD}\njwt-secret: <provided separately>"))

    def test_real_hosts_and_printers_are_rejected(self):
        for value in ("https://pos.lanzhounoodlesmtl.com", "192.168.4.23", "170.106.13.34", "https://merchant.example.com", "HTTP://merchant.example.com", "https://[fd00::1]"):
            with self.subTest(value=value):
                self.assertTrue(scanner.violations(value))

    def test_filled_credentials_and_private_keys_are_rejected(self):
        for value in ("device_token: synthetic-but-filled", "jwt-secret: unsafe-default", "-----BEGIN PRIVATE KEY-----", '{"device_token":"synthetic-test-value"}', '{"jwt-secret": "unsafe-default"}'):
            with self.subTest(kind=value.split(':')[0]):
                self.assertTrue(scanner.violations(value))


if __name__ == "__main__":
    unittest.main()
