"""Synthetic-only tests; no browser session or network access is used."""

import base64
import hashlib
import json
import unittest
from unittest.mock import Mock, patch

from cryptography.exceptions import InvalidTag
from cryptography.hazmat.primitives.ciphers.aead import AESGCM

from export_cookies import AAD, PBKDF2_ITERATIONS, encrypt_payload, generate_passphrase, select_cookies, send_over_usb


class ExportCookiesTest(unittest.TestCase):
    def setUp(self):
        self.cookies = {name: "synthetic_" + name for name in
                        ("SID", "HSID", "SSID", "OSID", "APISID", "SAPISID")}

    def test_encrypted_transfer_round_trip(self):
        phrase = "apple-bridge-candle-dragon-ember-forest-garden-harbor-island"
        payload = encrypt_payload(self.cookies, phrase, 1_700_000_000_000)
        self.assertTrue(payload.startswith("UMGM2:"))
        self.assertNotIn("synthetic_SID", payload)
        raw = base64.urlsafe_b64decode(payload.removeprefix("UMGM2:") + "===")
        key = hashlib.pbkdf2_hmac("sha256", phrase.encode(), raw[:16], PBKDF2_ITERATIONS, dklen=32)
        data = json.loads(AESGCM(key).decrypt(raw[16:28], raw[28:], AAD))
        self.assertEqual(data["SID"], "synthetic_SID")
        self.assertEqual(data["__createdAtMs"], 1_700_000_000_000)

    def test_wrong_phrase_and_corruption_rejected(self):
        phrase = "apple-bridge-candle-dragon-ember-forest-garden-harbor-island"
        payload = encrypt_payload(self.cookies, phrase, 1_700_000_000_000)
        raw = bytearray(base64.urlsafe_b64decode(payload.removeprefix("UMGM2:") + "==="))
        wrong_key = hashlib.pbkdf2_hmac(
            "sha256", b"wrong-phrase", raw[:16], PBKDF2_ITERATIONS, dklen=32,
        )
        with self.assertRaises(InvalidTag):
            AESGCM(wrong_key).decrypt(raw[16:28], raw[28:], AAD)
        right_key = hashlib.pbkdf2_hmac(
            "sha256", phrase.encode(), raw[:16], PBKDF2_ITERATIONS, dklen=32,
        )
        raw[-1] ^= 1
        with self.assertRaises(InvalidTag):
            AESGCM(right_key).decrypt(raw[16:28], raw[28:], AAD)

    def test_generated_phrase_has_nine_wordlist_words(self):
        self.assertEqual(len(generate_passphrase().split("-")), 9)

    def test_missing_cookie_rejected(self):
        with self.assertRaisesRegex(ValueError, "HSID"):
            select_cookies("SID=synthetic-only")

    @patch("export_cookies.subprocess.run")
    def test_usb_requires_exactly_one_authorized_phone(self, run):
        run.return_value.stdout = "List of devices attached\nphone1\tdevice usb:1-1\nphone2\tdevice usb:1-2\n"
        self.assertFalse(send_over_usb("UMGM2:synthetic", adb="adb"))
        run.assert_called_once()

    @patch("export_cookies.subprocess.run")
    def test_usb_sends_ciphertext_to_debug_activity(self, run):
        run.side_effect = [
            Mock(stdout="List of devices attached\nphone1 device usb:1-1\n"),
            Mock(stdout="Starting: Intent", stderr="", returncode=0),
        ]
        payload = "UMGM2:synthetic-ciphertext"
        self.assertTrue(send_over_usb(payload, adb="adb"))
        self.assertEqual(run.call_args.args[0][-1], payload)
        self.assertIn("encrypted_transfer", run.call_args.args[0])


if __name__ == "__main__":
    unittest.main()
