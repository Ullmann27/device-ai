#!/usr/bin/env python3
"""Execute isolated host unittest suite and persist only aggregate evidence."""
import argparse
import datetime as dt
import hashlib
import io
import json
import re
import subprocess
import sys
import unittest
from pathlib import Path


def checkout_source(root):
    def git(*args):
        return subprocess.check_output(["git", "-C", str(root), *args],
                                       stderr=subprocess.DEVNULL, timeout=10)
    try:
        if Path(git("rev-parse", "--show-toplevel").decode().strip()).resolve() != root.resolve():
            return None, False
        sha = git("rev-parse", "HEAD").decode().strip()
        if not re.fullmatch(r"[0-9a-f]{40}", sha):
            return None, False
        clean = not git("status", "--porcelain", "--untracked-files=all").strip()
        tracked = {}
        for entry in git("ls-tree", "-r", "-z", "HEAD", "--", "tools/fold7_quality_lab").split(b"\0"):
            if entry:
                metadata, name = entry.split(b"\t", 1)
                tracked[name.decode()] = metadata.split()[2].decode()
        for file in (root / "tools/fold7_quality_lab").rglob("*.py"):
            data = file.read_bytes()
            blob = hashlib.sha1(b"blob " + str(len(data)).encode() + b"\0" + data).hexdigest()
            if tracked.get(file.relative_to(root).as_posix()) != blob:
                clean = False
        return sha, clean
    except (OSError, subprocess.SubprocessError, ValueError):
        return None, False


def main(argv=None):
    p = argparse.ArgumentParser(description=__doc__)
    p.add_argument("--output", required=True)
    p.add_argument("--source-sha", help="Expected checked-out commit; never overrides observed source")
    args = p.parse_args(argv)
    sys.dont_write_bytecode = True
    root = Path(__file__).resolve().parents[2]
    before_sha, before_clean = checkout_source(root)
    if args.source_sha is not None and (not re.fullmatch(r"[0-9a-f]{40}", args.source_sha)
                                        or args.source_sha != before_sha):
        p.error("--source-sha must match the observed full Git HEAD")
    log = io.StringIO()
    suite = unittest.defaultTestLoader.discover(str(Path(__file__).parent / "tests"))
    result = unittest.TextTestRunner(stream=log, verbosity=2).run(suite)
    after_sha, after_clean = checkout_source(root)
    verified = bool(before_sha and before_clean and after_clean and before_sha == after_sha)
    total = result.testsRun
    failed = len(result.failures) + len(result.errors) + len(result.unexpectedSuccesses)
    passed = total - failed - len(result.skipped) - len(result.expectedFailures)
    successful = result.wasSuccessful() and passed > 0
    status = "FAIL" if not successful else "PASS" if verified else "NOT_VERIFIED"
    payload = {"schema": "continuum.lab.unit.v1", "provenance": "host_python_unittest",
               "source_sha": before_sha if verified else "LOCAL_UNCOMMITTED" if before_sha else None,
               "checkout_sha": before_sha, "source_verified": verified, "source_unchanged": verified,
               "timestamp_utc": dt.datetime.now(dt.timezone.utc).isoformat(),
               "total": total, "passed": passed,
               "failed": failed, "skipped": len(result.skipped),
               "expected_failures": len(result.expectedFailures),
               "status": status,
               "test_log_sha256": hashlib.sha256(log.getvalue().encode()).hexdigest(),
               "test_category": "HOST_SOFTWARE_ONLY"}
    path = Path(args.output)
    path.parent.mkdir(parents=True, exist_ok=True)
    if path.exists():
        raise FileExistsError(f"Refusing overwrite: {path}")
    path.write_text(json.dumps(payload, indent=2) + "\n", encoding="utf-8")
    print(json.dumps(payload, indent=2))
    return 0 if status == "PASS" else 1


if __name__ == "__main__":
    sys.exit(main())
