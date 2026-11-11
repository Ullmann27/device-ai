#!/usr/bin/env python3
"""Execute a frozen, explicitly authorized native candidate once."""

import argparse
import datetime
import fcntl
import hashlib
import json
from pathlib import Path
import resource
import subprocess
import time


def sha(path):
    return hashlib.sha256(Path(path).read_bytes()).hexdigest()


def main():
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("--registration", type=Path, required=True)
    parser.add_argument("--arm", choices=("A", "B"), required=True)
    parser.add_argument("--gate", type=Path)
    parser.add_argument("--check-only", action="store_true")
    args = parser.parse_args()
    reg = json.loads(args.registration.read_text())
    assert reg["schema"] == "lm1-semantic-native-registration-v1"
    assert reg["maximumUpdatesPerCandidate"] == 128
    assert sorted(reg["arms"]) == ["A", "B"]
    assert reg["runnerSha256"] == sha(__file__)
    for path, expected in reg["inputSha256"].items():
        assert sha(path) == expected, "input changed: " + path
    arm = reg["arms"][args.arm]
    assert arm["command"][6] == args.arm
    assert arm["command"][0] == reg["javaPath"]
    assert arm["command"][1:4] == ["-Xmx768m", "-XX:ActiveProcessorCount=1", "-cp"]
    if args.check_only:
        print(json.dumps({"status": "PREFLIGHT_PASS", "arm": args.arm, "updates": 0,
                          "registrationSha256": sha(args.registration)}))
        return
    assert args.gate is not None, "root gate is required"
    gate = json.loads(args.gate.read_text())
    assert gate["registrationSha256"] == sha(args.registration)
    assert gate["authorizedCandidates"] == ["A", "B"]
    assert gate["maximumUpdatesPerCandidate"] == 128
    assert gate["dataPilotAccepted"] and gate["independentProtocolFrozen"]
    assert gate["sourcePublished"] and gate["rootAuthorizesTraining"]
    assert len(gate["publishedTrainingCommit"]) == 40
    receipt_dir = Path(arm["receiptDirectory"])
    output_dir = Path(arm["outputDirectory"])
    assert not receipt_dir.exists() and not output_dir.exists(), "refuse to overwrite a run"
    ledger = Path(reg["executionLedger"])
    ledger.mkdir(parents=True, exist_ok=True)
    with (ledger / "native.lock").open("a") as lock:
        fcntl.flock(lock, fcntl.LOCK_EX | fcntl.LOCK_NB)
        with (ledger / (args.arm + ".started.json")).open("x") as marker:
            json.dump({"arm": args.arm, "registrationSha256": sha(args.registration),
                       "gateSha256": sha(args.gate)}, marker)
        receipt_dir.mkdir(parents=True)
        started = datetime.datetime.now(datetime.timezone.utc).isoformat()
        before = resource.getrusage(resource.RUSAGE_CHILDREN)
        begin = time.perf_counter()
        timed_out = False
        try:
            result = subprocess.run(arm["command"], capture_output=True, timeout=660)
            stdout, stderr, code = result.stdout, result.stderr, result.returncode
        except subprocess.TimeoutExpired as error:
            stdout, stderr, code = error.stdout or b"", error.stderr or b"", None
            timed_out = True
        elapsed = time.perf_counter() - begin
        after = resource.getrusage(resource.RUSAGE_CHILDREN)
        (receipt_dir / "stdout.tsv").write_bytes(stdout)
        (receipt_dir / "stderr.txt").write_bytes(stderr)
        updates = [line for line in stdout.decode(errors="replace").splitlines()
                   if line.startswith("UPDATE\t")]
        final = [line for line in stdout.decode(errors="replace").splitlines()
                 if line.startswith("RESULT\t")]
        unchanged = {path: sha(path) == expected for path, expected in reg["inputSha256"].items()}
        receipt = {
            "schema": "lm1-semantic-execution-receipt-v1", "arm": args.arm,
            "startedUtc": started, "command": arm["command"], "exitCode": code,
            "timedOut": timed_out, "wallSeconds": elapsed,
            "cpuUserSeconds": after.ru_utime - before.ru_utime,
            "cpuSystemSeconds": after.ru_stime - before.ru_stime,
            "peakRssKiB": after.ru_maxrss, "updatesObserved": len(updates),
            "finalRecords": final, "inputsUnchanged": unchanged,
            "registrationSha256": sha(args.registration), "gateSha256": sha(args.gate),
            "runnerSha256": sha(__file__), "stdoutSha256": sha(receipt_dir / "stdout.tsv"),
            "stderrSha256": sha(receipt_dir / "stderr.txt"),
            "artifacts": {str(p.relative_to(output_dir)): {"sha256": sha(p), "bytes": p.stat().st_size}
                          for p in sorted(output_dir.rglob("*")) if p.is_file()},
            "additionalCloudGpuJobs": 0, "creditUsage": "UNAVAILABLE_NOT_ESTIMATED",
            "scope": "CPU research candidate only; not application or phone verification",
        }
        (receipt_dir / "receipt.json").write_text(json.dumps(receipt, indent=2) + "\n")
        print(json.dumps({key: receipt[key] for key in
                          ("arm", "exitCode", "timedOut", "updatesObserved", "wallSeconds",
                           "cpuUserSeconds", "cpuSystemSeconds", "peakRssKiB", "finalRecords")}))
        assert code == 0 and not timed_out and len(final) == 1
        assert 0 < len(updates) <= 128 and all(unchanged.values())


if __name__ == "__main__":
    main()
