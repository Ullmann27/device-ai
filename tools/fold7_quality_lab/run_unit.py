#!/usr/bin/env python3
"""Execute isolated host unittest suite and persist only aggregate evidence."""
import argparse
import datetime as dt
import hashlib
import io
import json
import sys
import unittest
from pathlib import Path


def main(argv=None):
    p = argparse.ArgumentParser(description=__doc__)
    p.add_argument("--output", required=True)
    p.add_argument("--source-sha", default="LOCAL_UNCOMMITTED")
    args = p.parse_args(argv)
    log = io.StringIO()
    suite = unittest.defaultTestLoader.discover(str(Path(__file__).parent / "tests"))
    result = unittest.TextTestRunner(stream=log, verbosity=2).run(suite)
    total = result.testsRun
    failed = len(result.failures) + len(result.errors) + len(result.unexpectedSuccesses)
    payload = {"schema": "continuum.lab.unit.v1", "provenance": "host_python_unittest",
               "source_sha": args.source_sha,
               "timestamp_utc": dt.datetime.now(dt.timezone.utc).isoformat(),
               "total": total, "passed": total - failed - len(result.skipped) - len(result.expectedFailures),
               "failed": failed, "skipped": len(result.skipped),
               "expected_failures": len(result.expectedFailures),
               "status": "PASS" if result.wasSuccessful() else "FAIL",
               "test_log_sha256": hashlib.sha256(log.getvalue().encode()).hexdigest(),
               "test_category": "HOST_SOFTWARE_ONLY"}
    path = Path(args.output)
    path.parent.mkdir(parents=True, exist_ok=True)
    if path.exists():
        raise FileExistsError(f"Refusing overwrite: {path}")
    path.write_text(json.dumps(payload, indent=2) + "\n", encoding="utf-8")
    print(json.dumps(payload, indent=2))
    return 0 if result.wasSuccessful() else 1


if __name__ == "__main__":
    sys.exit(main())