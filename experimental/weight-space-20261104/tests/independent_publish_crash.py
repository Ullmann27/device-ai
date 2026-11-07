#!/usr/bin/env python3
"""Kill a real JSON publisher mid-serialization; no model process is started."""
from __future__ import annotations

import hashlib
import json
from pathlib import Path
import select
import subprocess
import sys
import tempfile


CHILD = r'''
import importlib.util, json, sys
from pathlib import Path
spec = importlib.util.spec_from_file_location("crash_probe_driver", sys.argv[1])
driver = importlib.util.module_from_spec(spec)
spec.loader.exec_module(driver)
original = json.JSONEncoder.iterencode
def paused_encoder(self, value, *args, **kwargs):
    iterator = iter(original(self, value, *args, **kwargs))
    yield next(iterator)
    print("SERIALIZATION_PAUSED", flush=True)
    sys.stdin.read(1)
    yield from iterator
json.JSONEncoder.iterencode = paused_encoder
driver.write_new(Path(sys.argv[2]), {"stages": [], "lastPassed": None, "stop": "BASELINE_GATE"})
'''


def main() -> int:
    source = (Path(sys.argv[1]) if len(sys.argv) > 1 else
              Path(__file__).resolve().parents[1] / "run_experiment.py").resolve(strict=True)
    expected = {"stages": [], "lastPassed": None, "stop": "BASELINE_GATE"}
    with tempfile.TemporaryDirectory(prefix="continuum-publish-crash-") as directory:
        target = Path(directory) / "ladder.json"
        process = subprocess.Popen(
            [sys.executable, "-c", CHILD, str(source), str(target)],
            stdin=subprocess.PIPE, stdout=subprocess.PIPE, stderr=subprocess.PIPE, text=True,
        )
        try:
            ready, _, _ = select.select([process.stdout], [], [], 10)
            if not ready or process.stdout.readline().strip() != "SERIALIZATION_PAUSED":
                raise RuntimeError("child did not reach the controlled serialization boundary")
            process.kill()
            _, stderr = process.communicate(timeout=5)
            exists = target.exists() or target.is_symlink()
            complete = False
            parse_error = None
            if exists:
                try:
                    complete = json.loads(target.read_bytes()) == expected
                except (ValueError, OSError) as exc:
                    parse_error = type(exc).__name__
            passed = process.returncode == -9 and (not exists or complete)
            report = {
                "case": "real_process_kill_during_phase_serialization",
                "sourceSha256": hashlib.sha256(source.read_bytes()).hexdigest(),
                "childReturnCode": process.returncode,
                "finalMarkerPresent": exists,
                "finalMarkerComplete": complete,
                "parseError": parse_error,
                "passed": passed,
                "stderr": stderr,
                "modelProcesses": 0,
                "trainingRuns": 0,
                "scope": "local synthetic phase marker; Linux SIGKILL, not power-loss durability",
            }
            print(json.dumps(report, indent=2))
            return 0 if passed else 1
        finally:
            if process.poll() is None:
                process.kill()
                process.communicate(timeout=5)


if __name__ == "__main__":
    raise SystemExit(main())
