"""Build real arm64 injectors in isolated checkouts; requires the configured SDK/NDK."""
import argparse
import hashlib
import json
import os
from pathlib import Path
import re
import shutil
import subprocess
import tempfile

ROOT = Path(__file__).resolve().parents[1]
MARKER = re.compile(rb"TCQT_INJECTOR_ID_V1=([0-9a-f]{64})")


def main():
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("--sdk", type=Path)
    args = parser.parse_args()
    sdk = args.sdk
    if sdk is None:
        line = next(line for line in (ROOT / "local.properties").read_text().splitlines()
                    if line.startswith("sdk.dir="))
        sdk = Path(line[8:].replace(r"\:", ":").replace("\\\\", "\\"))
    gradle = (ROOT / "build.gradle.kts").read_text()
    ndk_version = re.search(r'val androidNdkVersion = "([^"]+)"', gradle)[1]
    cmake_version = re.search(r'val androidCmakeVersion = "([^"]+)"', gradle)[1]
    suffix = ".exe" if os.name == "nt" else ""
    cmake = sdk / "cmake" / cmake_version / "bin" / f"cmake{suffix}"
    ninja = cmake.with_name(f"ninja{suffix}")
    ndk = sdk / "ndk" / ndk_version
    output = ROOT / "build/native-identity-tests"
    output.mkdir(parents=True, exist_ok=True)
    checks = []

    def run(*command):
        process = subprocess.run([str(arg) for arg in command], capture_output=True, text=True)
        if process.returncode:
            raise RuntimeError(process.stdout + process.stderr)

    def passed(name):
        checks.append(name)
        print(f"PASS: {name}", flush=True)

    with tempfile.TemporaryDirectory(prefix="checkouts-", dir=output) as temp:
        work = Path(temp).resolve()
        assert work.is_relative_to(output.resolve())  # Bound recursive cleanup to test output.
        source_a, source_b = work / "first/cpp", work / "other-path/cpp"
        build_a, build_b = work / "first/build", work / "other-path/build"
        for source in (source_a, source_b):
            shutil.copytree(ROOT / "app/src/main/cpp", source)
        for path in source_b.rglob("*"):
            if path.is_file():
                data = path.read_bytes().replace(b"\r\n", b"\n")
                path.write_bytes(data.replace(b"\n", b"\r\n"))

        def configure(source, build, build_type="Debug", flags="", platform="android-27"):
            run(cmake, "-G", "Ninja", "-S", source, "-B", build,
                f"-DCMAKE_TOOLCHAIN_FILE={(ndk / 'build/cmake/android.toolchain.cmake').as_posix()}",
                f"-DCMAKE_MAKE_PROGRAM={ninja.as_posix()}", "-DANDROID_ABI=arm64-v8a",
                f"-DANDROID_PLATFORM={platform}", f"-DCMAKE_BUILD_TYPE={build_type}",
                f"-DCMAKE_CXX_FLAGS={flags}")

        def build(build_dir):
            run(cmake, "--build", build_dir, "--target", "tcqtzygisk", "--parallel", "2")

        def identity(build_dir):
            return (build_dir / "zygisk/injector-identity.txt").read_text().strip()

        def binary(build_dir):
            data = (build_dir / "zygisk/libtcqtzygisk.so").read_bytes()
            matches = MARKER.findall(data)
            assert matches == [identity(build_dir).encode()], "Embedded identity differs from compiled inputs"
            return data

        configure(source_a, build_a)
        build(build_a)
        baseline = identity(build_a)
        first_binary = binary(build_a)
        manifest = (build_a / "zygisk/injector-identity.inputs").read_text()
        assert ndk_version in manifest, "Full NDK revision missing from identity"
        assert "TCQT_INJECTOR_COMPAT_ID" not in manifest, "Self-referential identity"
        passed("compiled and stripped ELF contains exactly the generated identity")

        configure(source_b, build_b)
        build(build_b)
        assert identity(build_b) == baseline, "Checkout path/line endings changed identity"
        second_binary = binary(build_b)
        passed("clean builds in different paths and CRLF/LF sources share an identity")

        (source_a.parent / "Entry.kt").write_text("// Java/Kotlin-only update\n")
        (source_a.parent / "build.gradle.kts").write_text("val appVersionCode = 99999\n")
        build(build_a)
        assert identity(build_a) == baseline
        passed("Java/Kotlin and application version changes preserve identity")

        scanner = source_a / "source32_scanner.cpp"
        scanner.write_text(scanner.read_text() + "\n// independent libtcqtmem change\n")
        build(build_a)
        assert identity(build_a) == baseline
        passed("independent source32 library does not change injector identity")

        header = source_a / "zygisk/jni_bridge.h"
        header.write_text(header.read_text() + "\n// native header update\n")
        # Do not explicitly configure: Ninja must regenerate after a header edit.
        build(build_a)
        after_header = identity(build_a)
        assert after_header != baseline
        binary(build_a)
        passed("incremental header edits regenerate and embed a new identity")

        added = source_a / "zygisk/identity_test_dependency.h"
        added.write_text("// newly added native dependency\n")
        build(build_a)
        after_added = identity(build_a)
        assert after_added != after_header
        passed("new native dependencies invalidate the identity automatically")

        configure(source_a, build_a, flags="-DTCQT_ID_TEST=1")
        after_flag = identity(build_a)
        assert after_flag != after_added
        passed("native compiler options change identity")

        configure(source_a, build_a, platform="android-28")
        after_platform = identity(build_a)
        assert after_platform not in (after_added, after_flag)
        passed("native Android API target changes identity")

        configure(source_a, build_a, build_type="Release")
        assert identity(build_a) not in (after_added, after_flag, after_platform)
        passed("Debug/Release and optimization changes change identity")

        # Keep compact evidence outside the temporary checkouts.
        (output / "first.so").write_bytes(first_binary)
        (output / "other-path.so").write_bytes(second_binary)
        result = {
            "checks": checks,
            "identity": baseline,
            "first_sha256": hashlib.sha256(first_binary).hexdigest(),
            "other_path_sha256": hashlib.sha256(second_binary).hexdigest(),
        }
        (output / "results.json").write_text(json.dumps(result, indent=2) + "\n")
        print(json.dumps(result, indent=2))


if __name__ == "__main__":
    main()
