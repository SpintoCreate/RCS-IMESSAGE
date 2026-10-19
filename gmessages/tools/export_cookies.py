"""Make a local AES-256 ZIP for the personal Google Messages Android spike.

This script never opens a network connection, records no plaintext file, and only
keeps the cookie names libgm's current Google pairing flow requires. Install its
dependency before entering any cookies. Run from a private Windows terminal.
"""

import getpass
import json
import os
import sys
from pathlib import Path

import pyzipper

REQUIRED = ("SID", "HSID", "SSID", "OSID", "APISID", "SAPISID")
OPTIONAL = ("__Secure-1PSIDTS",)
ALLOWED = set(REQUIRED + OPTIONAL)
OUTPUT = Path("gmessages-cookie-export.zip")


def select_cookies(header: str) -> dict[str, str]:
    found: dict[str, str] = {}
    for field in header.split(";"):
        if "=" not in field:
            continue
        name, value = field.strip().split("=", 1)
        if name in ALLOWED:
            if not value or name in found:
                raise ValueError("A required cookie is empty or duplicated")
            found[name] = value
    missing = [name for name in REQUIRED if name not in found]
    if missing:
        raise ValueError("Missing cookies: " + ", ".join(missing))
    return found


def main() -> int:
    print("Use the SAME Google Account selected in Google Messages > Device Pairing.")
    print("In a private desktop browser window, open the documented /web/config URL.")
    print("In DevTools > Network, select /web/config and copy only its Cookie request-header value.")
    print("The header is hidden while you paste it. Never paste it in chat or a shell command.")
    header = getpass.getpass("Cookie header: ")
    try:
        cookies = select_cookies(header)
    except ValueError as exc:
        print(str(exc), file=sys.stderr)
        return 2
    header = ""

    first = getpass.getpass("New export passphrase (at least 16 characters): ")
    second = getpass.getpass("Repeat export passphrase: ")
    if len(first) < 16 or first != second:
        print("Passphrase is too short or does not match.", file=sys.stderr)
        return 2
    if OUTPUT.exists():
        print(f"{OUTPUT} already exists. Move or securely remove it first.", file=sys.stderr)
        return 2

    # pyzipper implements the WinZip AES format; Android reads it with zip4j.
    # All cookie bytes go straight from memory into the encrypted ZIP entry.
    descriptor = os.open(OUTPUT, os.O_WRONLY | os.O_CREAT | os.O_EXCL, 0o600)
    try:
        with os.fdopen(descriptor, "wb") as target:
            with pyzipper.AESZipFile(target, "w", compression=pyzipper.ZIP_DEFLATED,
                                      encryption=pyzipper.WZ_AES) as archive:
                archive.setpassword(first.encode("utf-8"))
                archive.setencryption(pyzipper.WZ_AES, nbits=256)
                archive.writestr("cookies.json", json.dumps(cookies, separators=(",", ":")).encode("utf-8"))
    except Exception:
        OUTPUT.unlink(missing_ok=True)
        raise
    print(f"Created {OUTPUT}. Copy it to your phone over USB, then import in the debug app.")
    print("Delete the PC copy and empty the Recycle Bin after the phone confirms import.")
    return 0


if __name__ == "__main__":
    raise SystemExit(main())
