"""Verify dual APK contents and optional raw APK/flash ZIP identity (no SDK needed)."""
import argparse
import hashlib
import re
import struct
from pathlib import Path
from zipfile import ZIP_STORED, ZipFile


def verify_local_extra(extra: bytes, name: str):
    offset = 0
    while offset < len(extra):
        # zipalign may leave a zero-filled padding tail without a field header.
        if not any(extra[offset:]):
            return
        assert offset + 4 <= len(extra), f"Truncated local ZIP extra field: {name}"
        _, length = struct.unpack_from('<HH', extra, offset)
        offset += 4
        assert offset + length <= len(extra), f"Invalid local ZIP extra field length: {name}"
        offset += length


def verify(apk: Path, raw: Path | None = None, flash_zip: Path | None = None):
    with ZipFile(apk) as package:
        assert package.testzip() is None, "CRC mismatch"
        names = package.namelist()
        assert len(names) == len(set(names)), "Duplicate ZIP entries"
        required = {"AndroidManifest.xml", "classes.dex", "resources.arsc", "module.prop",
                    "customize.sh", "payload.sh", "post-fs-data.sh",
                    "zygisk/arm64-v8a.so", "webroot/index.html",
                    "META-INF/com/google/android/update-binary"}
        assert required <= set(names), f"Missing entries: {required - set(names)}"
        assert not any(n.startswith("payload/") or n.endswith("libtcqtzygisk.so") for n in names)
        assert not any(n.endswith(".apk") for n in names), "Nested APK"
        assert b"@VERSION" not in package.read("module.prop"), "Unresolved version"
        injector_ids = re.findall(rb"TCQT_INJECTOR_ID_V1=[0-9a-f]{64}",
                                  package.read("zygisk/arm64-v8a.so"))
        assert len(injector_ids) == 1, "Missing or duplicate injector implementation identity"
        with apk.open('rb') as source:
            for entry in package.infolist():
                source.seek(entry.header_offset)
                header = source.read(30)
                assert header[:4] == b'PK\x03\x04', f"Invalid local ZIP header: {entry.filename}"
                name_length, extra_length = struct.unpack_from('<HH', header, 26)
                source.seek(name_length, 1)
                extra = source.read(extra_length)
                assert len(extra) == extra_length, f"Truncated local ZIP extra data: {entry.filename}"
                verify_local_extra(extra, entry.filename)
                native_library = entry.filename.startswith("lib/") and entry.filename.endswith(".so")
                if entry.filename == "resources.arsc" or native_library:
                    assert entry.compress_type == ZIP_STORED, entry.filename
                if native_library:
                    assert source.tell() % 16384 == 0, f"Native library is not 16 KiB aligned: {entry.filename}"
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
