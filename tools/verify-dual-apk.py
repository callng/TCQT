"""Verify dual APK contents and optional raw APK/flash ZIP identity (no SDK needed)."""
import argparse
import hashlib
from pathlib import Path
from zipfile import ZIP_STORED, ZipFile


def verify(apk: Path, raw: Path | None = None, flash_zip: Path | None = None):
    with ZipFile(apk) as package:
        assert package.testzip() is None, "CRC mismatch"
        names = package.namelist()
        assert len(names) == len(set(names)), "Duplicate ZIP entries"
        required = {"AndroidManifest.xml", "classes.dex", "resources.arsc", "module.prop",
                    "customize.sh", "zygisk/arm64-v8a.so", "webroot/index.html",
                    "META-INF/com/google/android/update-binary"}
        assert required <= set(names), f"Missing entries: {required - set(names)}"
        assert not any(n.startswith("payload/") or n.endswith("libtcqtzygisk.so") for n in names)
        assert not any(n.endswith(".apk") for n in names), "Nested APK"
        assert b"@VERSION" not in package.read("module.prop"), "Unresolved version"
        for entry in package.infolist():
            if entry.filename == "resources.arsc" or entry.filename.startswith("lib/") and entry.filename.endswith(".so"):
                assert entry.compress_type == ZIP_STORED, entry.filename
            if entry.filename.endswith(".sh"):
                assert b"\r\n" not in package.read(entry), entry.filename
        if raw:
            with ZipFile(raw) as original:
                for name in original.namelist():
                    if name.startswith(("classes", "lib/", "res/", "assets/")) or name in ("AndroidManifest.xml", "resources.arsc"):
                        if not name.endswith("/"):
                            assert original.read(name) == package.read(name), f"Changed APK entry: {name}"
    if flash_zip:
        assert apk.read_bytes() == flash_zip.read_bytes(), "APK/ZIP must be byte-identical"
    print(f"PASS {apk}: sha256={hashlib.sha256(apk.read_bytes()).hexdigest()}")


if __name__ == "__main__":
    parser = argparse.ArgumentParser()
    parser.add_argument("apk", type=Path)
    parser.add_argument("--raw", type=Path)
    parser.add_argument("--zip", type=Path)
    args = parser.parse_args()
    verify(args.apk, args.raw, args.zip)
