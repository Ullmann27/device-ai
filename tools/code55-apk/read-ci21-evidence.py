#!/usr/bin/env python3
"""Export original CI21 evidence; never rerun tests."""
import base64
import collections
import hashlib
import io
import json
import os
import pathlib
import re
import stat
import struct
import sys
import time
import urllib.error
import urllib.parse
import urllib.request
import xml.etree.ElementTree as ET
import zipfile

REPO = "Ullmann27/device-ai"
REPO_ID = 1415878393
RUN = 43620726334
SOURCE = "9f73b2ed1d03e34392a40883660c00f79afc7222"
TREE = "6ab9ad8d52e302403eb4608b93235e96e859924f"
PINS = {
    "Code59-Android-full-QA": (14124696168, 117677, "f6fd59d32d4efaaf398b8b24719f94c84d201c9c2b96bf49630e112b56d434d7"),
    "Code59-native-runtime-QA": (14124133777, 7954, "a08a49cd27d03353399f40be42aa7a65cf0f1167a58d79245305de0eb085073a"),
}
JOBS = {"full-android": 132525581049, "native-runtime": 132525581319}
DEADLINE = time.monotonic() + 240
OUTPUT_BYTES = 0
TOKEN = os.environ.pop("GH_TOKEN", "")
API_ROOT = "https://api.github.com/repos/" + REPO

def require(ok, message):
    if not ok:
        raise RuntimeError(message)

def remaining():
    value = DEADLINE - time.monotonic()
    require(value > 0, "Reader deadline exceeded")
    return min(15.0, value)

def emit(kind, **fields):
    global OUTPUT_BYTES
    remaining()
    line = "CI21_EVIDENCE " + json.dumps({"kind": kind, **fields}, ensure_ascii=True, separators=(",", ":"))
    OUTPUT_BYTES += len(line.encode("utf-8")) + 1
    require(OUTPUT_BYTES <= 32 * 1024 * 1024, "Output limit exceeded")
    print(line, flush=True)

class NoRedirect(urllib.request.HTTPRedirectHandler):
    def redirect_request(self, req, fp, code, msg, headers, newurl):
        return None

OPENER = urllib.request.build_opener(NoRedirect)

def request(url, authenticated):
    headers = {"User-Agent": "continuum-ci21-evidence-reader", "Accept": "application/vnd.github+json"}
    if authenticated:
        require(url.startswith(API_ROOT + "/"), "Authentication host mismatch")
        headers.update({"Authorization": "Bearer " + TOKEN, "X-GitHub-Api-Version": "2022-11-28"})
    else:
        p = urllib.parse.urlsplit(url)
        host = p.hostname or ""
        require(p.scheme == "https" and p.port in (None, 443) and not p.username and not p.password and not p.fragment, "Unsafe artifact redirect")
        require(any(host.endswith(s) for s in (".blob.core.windows.net", ".githubusercontent.com", ".amazonaws.com")), "Unexpected artifact storage host")
        headers["Accept"] = "application/octet-stream"
    try:
        return OPENER.open(urllib.request.Request(url, headers=headers), timeout=remaining())
    except urllib.error.HTTPError as exc:
        if exc.code in (301, 302, 303, 307, 308):
            return exc
        raise RuntimeError("HTTP request failed: " + str(exc.code)) from None
    except Exception as exc:
        raise RuntimeError("HTTP transport failed: " + type(exc).__name__) from None

def read_bounded(response, limit):
    data = bytearray()
    while True:
        remaining()
        chunk = response.read(min(65536, limit + 1 - len(data)))
        if not chunk:
            break
        data.extend(chunk)
        require(len(data) <= limit, "Response size limit exceeded")
    return bytes(data)

def api(path):
    with request(API_ROOT + path, True) as response:
        require(response.status == 200, "Unexpected API redirect/status")
        return json.loads(read_bounded(response, 2 * 1024 * 1024))

def download(meta):
    artifact_id, size, digest = PINS[meta["name"]]
    with request(API_ROOT + "/actions/artifacts/" + str(artifact_id) + "/zip", True) as response:
        require(response.status in (301, 302, 303, 307, 308), "Artifact redirect missing")
        url = response.headers.get("Location", "")
    data = None
    for _ in range(4):
        with request(url, False) as response:
            if response.status in (301, 302, 303, 307, 308):
                url = urllib.parse.urljoin(url, response.headers.get("Location", ""))
                continue
            require(response.status == 200, "Artifact storage status mismatch")
            data = read_bounded(response, size)
            break
    require(data is not None, "Artifact redirect limit exceeded")
    require(len(data) == size and hashlib.sha256(data).hexdigest() == digest, "Archive size/digest mismatch")
    archive = zipfile.ZipFile(io.BytesIO(data))
    items = archive.infolist()
    require(0 < len(items) <= 4096, "Archive entry count invalid")
    require(sum(item.file_size for item in items) <= 512 * 1024 * 1024, "Expanded archive too large")
    seen = set()
    for item in items:
        name = item.filename
        parts = name.rstrip("/").split("/")
        require(name and len(name) <= 512 and not name.startswith("/") and "\\" not in name and ":" not in name and all(p not in ("", ".", "..") for p in parts), "Unsafe archive path")
        require(name not in seen, "Duplicate archive entry")
        seen.add(name)
        mode = stat.S_IFMT(item.external_attr >> 16)
        require(mode in (0, stat.S_IFREG, stat.S_IFDIR), "Archive special file rejected")
        require(not (item.flag_bits & 1) and item.compress_type in (zipfile.ZIP_STORED, zipfile.ZIP_DEFLATED), "Unsupported archive encoding")
        require(item.file_size <= 32 * 1024 * 1024, "Archive entry too large")
        with archive.open(item) as stream:
            total = 0
            while chunk := stream.read(65536):
                remaining()
                total += len(chunk)
                require(total <= item.file_size, "Archive expanded size mismatch")
            require(total == item.file_size, "Archive entry truncated")
    emit("ARCHIVE_VERIFIED", artifact=meta["name"], id=artifact_id, bytes=size, sha256=digest, entries=len(items), crc="VERIFIED")
    return archive

def xml_bytes(archive, name):
    raw = archive.read(name)
    require(len(raw) <= 4 * 1024 * 1024, "XML too large")
    require(b"<!DOCTYPE" not in raw.upper() and b"<!ENTITY" not in raw.upper(), "XML declarations rejected")
    return raw, ET.fromstring(raw)

def android(archive):
    names = sorted(name for name in archive.namelist() if re.fullmatch(r"test-results/testDebugUnitTest/TEST-[^/]+\.xml", name))
    require(names, "No original JUnit XML")
    totals = collections.Counter()
    seen = set()
    for name in names:
        raw, suite = xml_bytes(archive, name)
        require(suite.tag == "testsuite", "Unexpected JUnit root")
        cases = suite.findall("testcase")
        count = collections.Counter()
        for case in cases:
            identity = (case.get("classname"), case.get("name"))
            require(all(identity) and identity not in seen, "Missing/duplicate testcase identity")
            seen.add(identity)
            require(len(seen) <= 20000, "Testcase limit exceeded")
            failures, errors, skips = case.findall("failure"), case.findall("error"), case.findall("skipped")
            status = "ERROR" if errors else "FAILED" if failures else "SKIPPED" if skips else "PASSED"
            count["tests"] += 1
            count["failures"] += bool(failures)
            count["errors"] += bool(errors)
            count["skipped"] += bool(skips)
            count["passed"] += status == "PASSED"
            details = [{"kind": e.tag, "type": e.get("type"), "message": e.get("message"), "text": "".join(e.itertext())} for e in [*failures, *errors, *skips]]
            emit("TESTCASE", file=name, classname=identity[0], name=identity[1], time=case.get("time"), status=status, details=details)
        for key in ("tests", "failures", "errors", "skipped"):
            require(int(suite.get(key, "0")) == count[key], "JUnit count mismatch: " + name + "/" + key)
        totals.update(count)
        emit("JUNIT_SUITE", file=name, bytes=len(raw), sha256=hashlib.sha256(raw).hexdigest(), counts=dict(count))
    emit("JUNIT_TOTAL", suites=len(names), counts=dict(totals), original_run=RUN, original_source=SOURCE)
    name = "reports/lint-results-debug.xml"
    require(name in archive.namelist(), "Original lint XML missing")
    raw, root = xml_bytes(archive, name)
    issues = root.findall("issue")
    severity = collections.Counter(i.get("severity", "") for i in issues)
    emit("LINT_TOTAL", file=name, bytes=len(raw), sha256=hashlib.sha256(raw).hexdigest(), severities=dict(severity), issue_ids=dict(collections.Counter(i.get("id", "") for i in issues)))
    for issue in issues:
        if issue.get("severity") in ("Fatal", "Error"):
            emit("LINT_ERROR", attributes=issue.attrib, locations=[i.attrib for i in issue.findall("location")])

def export(archive, name, kind, limit):
    if name not in archive.namelist():
        emit("FILE_MISSING", path=name, expected_kind=kind)
        return
    raw = archive.read(name)
    require(len(raw) <= limit, "Export file limit exceeded")
    details = {}
    if kind == "PNG":
        require(raw.startswith(b"\x89PNG\r\n\x1a\n") and len(raw) >= 33 and raw[12:16] == b"IHDR", "Invalid original PNG: " + name)
        details["width"], details["height"] = struct.unpack(">II", raw[16:24])
        require(0 < details["width"] <= 8192 and 0 < details["height"] <= 8192, "PNG dimensions out of bounds")
    elif kind == "JSON":
        try:
            json.loads(raw)
            details["json_valid"] = True
        except (ValueError, UnicodeError):
            details["json_valid"] = False
    encoded = base64.b64encode(raw).decode("ascii")
    chunks = (len(encoded) + 8191) // 8192
    emit("FILE_BEGIN", path=name, format=kind, bytes=len(raw), sha256=hashlib.sha256(raw).hexdigest(), chunks=chunks, **details)
    for index, start in enumerate(range(0, len(encoded), 8192)):
        emit("FILE_CHUNK", path=name, index=index, base64=encoded[start:start + 8192])
    emit("FILE_END", path=name, chunks=chunks)

def native(archive):
    for info in sorted(archive.infolist(), key=lambda i: i.filename):
        emit("NATIVE_ENTRY", path=info.filename, bytes=info.file_size, crc32=format(info.CRC, "08x"))
    for name in (
        "probe-exits.json", "host-live-identity.json",
        "hologram/hologram-state.json", "hologram/surface-evidence.json",
        "hologram/host-stack/collection.json", "hologram/guest-stack/collection.json",
        "routines/RESULT.json", "app/RESULT.json",
    ):
        export(archive, name, "JSON", 2 * 1024 * 1024)
    for name in (
        "source-sha.txt", "source-tree.txt", "emulator-version.txt",
        "emulator-archive-sha256.txt", "emulator-gpu-help.txt", "emulator.log",
        "host-crash/emulator-pid.txt", "host-crash/emulator-uid.txt",
        "host-crash/emulator-launch-utc.txt", "host-crash/core-pattern.txt",
    ):
        export(archive, name, "TEXT", 2 * 1024 * 1024)
    for name in (
        "hologram/hologram-screen.png", "hologram/hologram-texture.png",
        "routines/after-start.png", "app/camera-declined.png",
    ):
        export(archive, name, "PNG", 4 * 1024 * 1024)

def main():
    require(TOKEN, "Read-only token missing")
    run = api("/actions/runs/" + str(RUN))
    require(run["id"] == RUN and run["head_sha"] == SOURCE and run["run_attempt"] == 1 and run["status"] == "completed", "Original run identity/status mismatch")
    require(run["repository"]["id"] == REPO_ID and run["head_repository"]["id"] == REPO_ID and run["repository"]["full_name"] == REPO, "Original repository mismatch")
    commit = api("/git/commits/" + SOURCE)
    require(commit["sha"] == SOURCE and commit["tree"]["sha"] == TREE, "Original source tree mismatch")
    jobs = api("/actions/runs/" + str(RUN) + "/jobs?filter=all&per_page=100")
    require(jobs["total_count"] == len(jobs["jobs"]), "Job pagination required")
    for name, job_id in JOBS.items():
        match = [j for j in jobs["jobs"] if j["name"] == name and j["id"] == job_id]
        require(len(match) == 1 and match[0]["run_id"] == RUN and match[0]["head_sha"] == SOURCE and match[0]["status"] == "completed", "Original job identity/status mismatch")
        emit("ORIGINAL_JOB", name=name, id=job_id, conclusion=match[0]["conclusion"])
    listing = api("/actions/runs/" + str(RUN) + "/artifacts?per_page=100")
    require(listing["total_count"] == len(listing["artifacts"]), "Artifact pagination required")
    emit("ORIGINAL_SOURCE", run=RUN, source=SOURCE, tree=TREE, conclusion=run["conclusion"])
    selected = []
    for name, (artifact_id, size, digest) in PINS.items():
        matches = [a for a in listing["artifacts"] if a["name"] == name]
        require(len(matches) == 1, "Original artifact not unique")
        meta = matches[0]
        origin = meta["workflow_run"]
        require(meta["id"] == artifact_id and meta["size_in_bytes"] == size and meta["digest"] == "sha256:" + digest and meta["expired"] is False, "Pinned artifact mismatch")
        require(origin["id"] == RUN and origin["head_sha"] == SOURCE and origin["repository_id"] == REPO_ID and origin["head_repository_id"] == REPO_ID, "Artifact source mismatch")
        selected.append(meta)
    for meta in selected:
        with download(meta) as archive:
            if meta["name"] == "Code59-Android-full-QA":
                android(archive)
            else:
                native(archive)
    emit("READER_COMPLETED", status="EXPORTED_ORIGINAL_EVIDENCE", tests_rerun=False, original_conclusion=run["conclusion"])

if __name__ == "__main__":
    try:
        main()
    except Exception as error:
        print("CI21_READER_ERROR " + json.dumps({"type": type(error).__name__, "message": str(error)}, ensure_ascii=True), file=sys.stderr, flush=True)
        raise SystemExit(1)
