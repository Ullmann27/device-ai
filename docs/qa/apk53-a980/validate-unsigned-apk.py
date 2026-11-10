#!/usr/bin/env python3
"""Verify the unsigned APK53 candidate without modifying it."""
from __future__ import annotations

import argparse
import hashlib
import json
import os
from pathlib import Path, PurePosixPath
import re
import struct
import subprocess
import sys
import time
import zipfile

EXPECTED_SOURCE = "a980a19ed3ab44323009ae27eb299d322ff11679"
EXPECTED_TREE = "a9d3da18e774ccc1d3c7cd61c493ad65928c4d2e"
PROTECTED_SOURCE = "17491f4d8103f0e55836369333fab07e80e6e9c2"
EXPECTED_PACKAGE = "at.heinz.deviceai.continuumlocal"
EXPECTED_LIBRARIES = {
    "lib/arm64-v8a/libfilament-jni.so",
    "lib/arm64-v8a/libgltfio-jni.so",
    "lib/arm64-v8a/libopencv_java4.so",
    "lib/arm64-v8a/libc++_shared.so",
    "lib/arm64-v8a/liblitertlm_jni.so",
}
DEFAULT_REPO = Path("/workspace/scratch/5900afa6af29/device-ai")
DEFAULT_TOOLS = Path("/tmp/continuum-toolchain/android-sdk/build-tools/35.0.0")
DEFAULT_JAVA = Path("/tmp/continuum-toolchain/jdk")


class Rejected(ValueError):
    pass


def require(condition: bool, message: str) -> None:
    if not condition:
        raise Rejected(message)


def digest_stream(stream) -> str:
    digest = hashlib.sha256()
    for chunk in iter(lambda: stream.read(1024 * 1024), b""):
        digest.update(chunk)
    return digest.hexdigest()


def digest_file(path: Path) -> str:
    with path.open("rb") as stream:
        return digest_stream(stream)


def write_json(path: Path, value) -> None:
    path.write_text(json.dumps(value, indent=2, ensure_ascii=False) + "\n", encoding="utf-8")


def run(command: list[str], *, cwd: Path, env: dict, log: Path | None = None):
    result = subprocess.run(command, cwd=cwd, env=env, text=True,
                            stdout=subprocess.PIPE, stderr=subprocess.STDOUT,
                            timeout=60, check=False)
    if log is not None:
        log.write_text(result.stdout, encoding="utf-8")
    return result


def source_identity(repo: Path, env: dict) -> dict:
    def git(*arguments: str) -> str:
        result = run(["git", *arguments], cwd=repo, env=env)
        require(result.returncode == 0, "Git source identity unavailable")
        return result.stdout.strip()
    identity = {"commit": git("rev-parse", "HEAD"),
                "tree": git("rev-parse", "HEAD^{tree}"),
                "status": git("status", "--porcelain", "--untracked-files=normal")}
    require(identity == {"commit": EXPECTED_SOURCE, "tree": EXPECTED_TREE, "status": ""},
            "Expected exact clean APK53 source")
    asset = "app/src/main/assets/avatar3d/continuum-avatar.glb"
    original = git("rev-parse", PROTECTED_SOURCE + ":" + asset)
    require(git("rev-parse", "HEAD:" + asset) == original, "Original avatar blob changed")
    identity["original_avatar_git_blob"] = original
    return identity


def check_badging(text: str) -> dict:
    rows = re.findall(r"^package: name='([^']+)' versionCode='([^']+)' versionName='([^']*)'", text, re.M)
    require(rows == [(EXPECTED_PACKAGE, "53", "0.3.52")], "APK package/version mismatch")
    require(re.findall(r"^native-code: (.+)$", text, re.M) == ["'arm64-v8a'"],
            "APK native-code must be exactly arm64-v8a")
    require(re.findall(r"^sdkVersion:'([^']+)'$", text, re.M) == ["26"], "Unexpected minSdk")
    require(re.findall(r"^targetSdkVersion:'([^']+)'$", text, re.M) == ["36"], "Unexpected targetSdk")
    require(not re.search(r"^application-debuggable(?:\s|$)", text, re.M), "Release APK is debuggable")
    return {"package": rows[0][0], "versionCode": 53, "versionName": "0.3.52",
            "abi": "arm64-v8a", "minSdk": 26, "targetSdk": 36, "debuggable": False}


def check_elf(data: bytes, name: str) -> dict:
    require(len(data) >= 64 and data[:7] == b"\x7fELF\x02\x01\x01", "Invalid ELF64 LE: " + name)
    require(struct.unpack_from("<H", data, 16)[0] == 3, "Native library is not ET_DYN: " + name)
    require(struct.unpack_from("<H", data, 18)[0] == 183, "Native library is not AArch64: " + name)
    offset = struct.unpack_from("<Q", data, 32)[0]
    entry_size, count = struct.unpack_from("<HH", data, 54)
    require(entry_size >= 56 and 0 < count <= 128 and offset + entry_size * count <= len(data),
            "Invalid ELF program table: " + name)
    segments = []
    for index in range(count):
        header = offset + index * entry_size
        if struct.unpack_from("<I", data, header)[0] != 1:
            continue
        file_offset, address = struct.unpack_from("<QQ", data, header + 8)
        file_size, memory_size, alignment = struct.unpack_from("<QQQ", data, header + 32)
        require(alignment >= 16384 and alignment & (alignment - 1) == 0,
                "ELF load alignment below 16 KiB: " + name)
        require(file_offset % alignment == address % alignment,
                "ELF segment congruence invalid: " + name)
        require(file_size <= memory_size and file_offset + file_size <= len(data),
                "ELF segment exceeds file: " + name)
        segments.append({"offset": file_offset, "virtualAddress": address,
                         "fileSize": file_size, "memorySize": memory_size, "alignment": alignment})
    require(bool(segments), "ELF contains no load segments: " + name)
    return {"path": name, "bytes": len(data), "sha256": hashlib.sha256(data).hexdigest(),
            "machine": "AArch64", "class": "ELF64", "loadSegments": segments}


def check_unsigned_diagnostic(returncode: int, text: str) -> None:
    errors = [line.strip() for line in text.splitlines() if line.strip().startswith("ERROR:")]
    accepted = {"ERROR: Missing META-INF/MANIFEST.MF", "ERROR: No JAR signatures"}
    require(returncode == 1 and "DOES NOT VERIFY" in text and bool(errors)
            and set(errors) <= accepted, "apksigner did not prove unsigned status")
    require(not re.search(r"Exception|Traceback|Malformed|Failed to parse|Signer #\d+ certificate", text, re.I),
            "apksigner reported malformed APK or signer")
    require(not re.search(r"Verified using .*:\s*true", text), "APK contains a verified signature")


def check_archive(apk: Path, repo: Path, out: Path) -> dict:
    with zipfile.ZipFile(apk) as archive:
        entries = archive.infolist()
        names = [entry.filename for entry in entries]
        require(len(set(names)) == len(names), "Duplicate APK ZIP entries")
        for entry in entries:
            parts = PurePosixPath(entry.filename).parts
            require(bool(parts) and not entry.filename.startswith("/") and ".." not in parts
                    and "\\" not in entry.filename and "\x00" not in entry.filename,
                    "Unsafe APK ZIP entry")
            require((entry.external_attr >> 16) & 0o170000 != 0o120000, "APK ZIP contains symlink")
        require(archive.testzip() is None, "APK ZIP CRC verification failed")
        files = {entry.filename: entry for entry in entries if not entry.is_dir()}
        for name in ["AndroidManifest.xml", "resources.arsc", "classes.dex"]:
            require(name in files and files[name].file_size > 0, "Missing APK core entry: " + name)
        with archive.open("classes.dex") as dex:
            require(dex.read(4) == b"dex\n", "Invalid primary DEX header")
        library_names = {name for name in files if name.startswith("lib/")}
        require(library_names == EXPECTED_LIBRARIES,
                "Native inventory mismatch: missing=" + repr(sorted(EXPECTED_LIBRARIES - library_names))
                + " extra=" + repr(sorted(library_names - EXPECTED_LIBRARIES)))
        libraries = [check_elf(archive.read(name), name) for name in sorted(library_names)]
        v1_signatures = [name for name in files if re.fullmatch(r"META-INF/[^/]+\.(?:SF|RSA|DSA|EC)", name, re.I)]
        require(not v1_signatures, "APK contains JAR signing material")
        with apk.open("rb") as stream:
            require(archive.start_dir >= 16, "Invalid APK central directory")
            stream.seek(archive.start_dir - 16)
            require(stream.read(16) != b"APK Sig Block 42", "APK contains a signing block")

        assets = []
        for source_root in [repo / "app/src/main/assets", repo / "app/build/generated/owner-face-model-assets"]:
            require(source_root.is_dir(), "Missing source asset directory: " + str(source_root))
            for source in sorted(source_root.rglob("*")):
                require(not source.is_symlink(), "Source asset is a symlink")
                if not source.is_file():
                    continue
                name = "assets/" + source.relative_to(source_root).as_posix()
                require(name in files, "Missing packaged asset: " + name)
                expected = digest_file(source)
                with archive.open(name) as stream:
                    actual = digest_stream(stream)
                require(source.stat().st_size == files[name].file_size and actual == expected,
                        "Packaged asset differs from source: " + name)
                assets.append({"path": name, "source": str(source), "bytes": files[name].file_size, "sha256": actual})

        lock = json.loads((repo / "app/owner-face-models.lock.json").read_text())
        require({model["id"] for model in lock["models"]} == {"yunet", "sface"}, "Unexpected face-model lock")
        for model in lock["models"]:
            name = "assets/faceid/" + model["filename"]
            require(name in files and files[name].file_size == model["byteLength"], "Locked model missing/wrong size: " + name)
            with archive.open(name) as stream:
                require(digest_stream(stream) == model["sha256"], "Locked model hash mismatch: " + name)
        require("assets/avatar3d/continuum-avatar.glb" in files and "assets/faceid/model-manifest.json" in files,
                "Original avatar or face-model manifest missing")
        write_json(out / "inventory.json", [
            {"path": entry.filename, "bytes": entry.file_size, "compressedBytes": entry.compress_size,
             "method": entry.compress_type, "crc32": f"{entry.CRC:08x}"} for entry in entries])
        write_json(out / "assets.json", assets)
        write_json(out / "native-libraries.json", libraries)
        return {"zipCrc": "PASS", "entryCount": len(entries), "assetFilesCompared": len(assets),
                "nativeLibraries": sorted(library_names), "jarSignatureFiles": [], "apkSigningBlock": False}


def main() -> int:
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("apk", type=Path)
    parser.add_argument("output", type=Path)
    parser.add_argument("--repo", type=Path, default=DEFAULT_REPO)
    parser.add_argument("--build-tools", type=Path, default=DEFAULT_TOOLS)
    parser.add_argument("--java-home", type=Path, default=DEFAULT_JAVA)
    args = parser.parse_args()
    apk, repo, out = args.apk.absolute(), args.repo.resolve(), args.output.absolute()
    if not apk.is_file() or apk.is_symlink():
        print("REJECTED: existing regular APK required; no waiting", file=sys.stderr)
        return 2
    out = out.resolve()
    if out.is_relative_to(repo) or out.exists():
        print("REJECTED: fresh output directory outside repository required", file=sys.stderr)
        return 2
    out.mkdir(mode=0o700)
    report = {"status": "FAIL", "started": time.time(), "apk": str(apk), "expectedSource": EXPECTED_SOURCE,
              "expectedTree": EXPECTED_TREE, "validatorSha256": digest_file(Path(__file__)),
              "signing": "NOT_USED", "installation": "NOT_RUN", "Fold7": "NOT_RUN", "GPU": "NOT_RUN"}
    env = os.environ.copy()
    env["JAVA_HOME"] = str(args.java_home.resolve())
    env["PATH"] = str(args.java_home / "bin") + os.pathsep + env.get("PATH", "")
    try:
        report["sourceBefore"] = source_identity(repo, env)
        before = apk.stat()
        report["bytes"] = before.st_size
        report["sha256"] = digest_file(apk)
        report["archive"] = check_archive(apk, repo, out)
        for name in ["aapt2", "zipalign", "apksigner"]:
            require((args.build_tools / name).is_file() and os.access(args.build_tools / name, os.X_OK),
                    "Required Android tool unavailable: " + name)
        badging = run([str(args.build_tools / "aapt2"), "dump", "badging", str(apk)],
                      cwd=repo, env=env, log=out / "aapt2-badging.txt")
        require(badging.returncode == 0, "aapt2 cannot parse APK")
        report["identity"] = check_badging(badging.stdout)
        alignment = run([str(args.build_tools / "zipalign"), "-c", "-P", "16", "-v", "4", str(apk)],
                        cwd=repo, env=env, log=out / "zipalign.txt")
        require(alignment.returncode == 0, "APK ZIP alignment failed")
        report["zipalign16KiB"] = "PASS"
        signature = run([str(args.build_tools / "apksigner"), "verify", "--verbose", "--print-certs", str(apk)],
                        cwd=repo, env=env, log=out / "apksigner.txt")
        check_unsigned_diagnostic(signature.returncode, signature.stdout)
        report["unsignedDiagnostic"] = "EXPECTED_MISSING_SIGNATURE"
        report["sourceAfter"] = source_identity(repo, env)
        after = apk.stat()
        require((before.st_dev, before.st_ino, before.st_size, before.st_mtime_ns) ==
                (after.st_dev, after.st_ino, after.st_size, after.st_mtime_ns)
                and digest_file(apk) == report["sha256"], "APK changed during verification")
        require(report["sourceBefore"] == report["sourceAfter"], "Source changed during verification")
        require("\n" not in apk.name and "\r" not in apk.name and "\\" not in apk.name, "Unsafe checksum filename")
        (out / "SHA256SUMS.txt").write_text(report["sha256"] + "  " + apk.name + "\n", encoding="utf-8")
        report["status"] = "PASS"
        report["artifactStatus"] = "UNSIGNED_NOT_INSTALLABLE"
    except Exception as error:
        report["error"] = type(error).__name__ + ": " + str(error)
    finally:
        report["finished"] = time.time()
        write_json(out / "RESULT.json", report)
    print(json.dumps({key: report.get(key) for key in ["status", "artifactStatus", "sha256", "error"]}))
    return 0 if report["status"] == "PASS" else 1


if __name__ == "__main__":
    raise SystemExit(main())
