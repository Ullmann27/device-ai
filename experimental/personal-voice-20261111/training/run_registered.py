#!/usr/bin/env python3
"""Run one fixed personal-voice LM1 candidate; never read independent test data."""
from __future__ import annotations
import argparse
from datetime import datetime, timezone
import hashlib
import json
from pathlib import Path
import resource
import subprocess
import time

ARMS = ("balanced-replay", "balanced-preserve")
ENTRY = "at.heinz.deviceai.learning.lm.LmPersonalVoicePilot"
BASE = "7a0af1e99554f9e73fbe4212d0aa4bf8ec97745efca56998d9b42bac4fad44fd"
TOK = "94375c73d1f6e2d04b790738580143fa8c1b9b456d40fa7eb6b4009877bd892f"


def sha(path):
    return hashlib.sha256(Path(path).read_bytes()).hexdigest()


def preflight(registration: Path, arm: str):
    if arm not in ARMS:
        raise ValueError("only the two registered arms are permitted")
    spec = json.loads(registration.read_text())
    if spec.get("schema") != "continuum-personal-voice-training-v1":
        raise ValueError("unsupported registration")
    if spec.get("steps") != 128 or spec.get("timeoutSeconds") != 600:
        raise ValueError("registered step/time budget changed")
    if spec.get("seed") != 20261111 or spec.get("learningRate") != 0.00005:
        raise ValueError("registered optimizer controls changed")
    if not spec.get("dataQualityGatePassed") or not spec.get("independentEvaluationFrozen"):
        raise ValueError("data quality / independent evaluation gates absent")
    if not spec.get("researchLeadAuthorized"):
        raise ValueError("concrete research budget not authorized")
    if set(spec["commands"]) != set(ARMS):
        raise ValueError("registration must contain exactly two candidates")
    for path, expected in spec["fileHashes"].items():
        if sha(path) != expected:
            raise ValueError("registered input changed: " + path)
    command = spec["commands"][arm]
    if len(command) != 13 or Path(command[0]).name != "java":
        raise ValueError("unexpected native command shape")
    if command[1:4] != ["-Xmx768m", "-XX:ActiveProcessorCount=1", "-cp"] or command[5] != ENTRY or command[6] != arm:
        raise ValueError("native invocation controls changed")
    # Java, compiler output, every data file, original model and tokenizer must be bound.
    required_inputs = [*command[4].split(":"), *command[7:12]]
    if not all(path in spec["fileHashes"] for path in required_inputs):
        raise ValueError("unbound model / tokenizer / code / data input")
    if sha(command[7]) != BASE or sha(command[8]) != TOK:
        raise ValueError("candidate must begin from original model and tokenizer")
    output = Path(command[-1])
    evidence = Path(spec["evidenceDirectories"][arm])
    if output.exists() or evidence.exists():
        raise FileExistsError("registered candidate already attempted")
    if output.resolve() == evidence.resolve() or output.resolve() in evidence.resolve().parents or evidence.resolve() in output.resolve().parents:
        raise ValueError("artifact and evidence directories must be separate")
    return spec, command, output, evidence


def main():
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("registration", type=Path)
    parser.add_argument("arm", choices=ARMS)
    parser.add_argument("--check-only", action="store_true")
    args = parser.parse_args()
    spec, command, output, evidence = preflight(args.registration, args.arm)
    if args.check_only:
        print(json.dumps({"preflight": "PASS", "arm": args.arm, "updates": 0}))
        return
    evidence.mkdir(parents=True, exist_ok=False)
    output.parent.mkdir(parents=True, exist_ok=True)
    start_utc = datetime.now(timezone.utc).isoformat()
    started = time.monotonic()
    usage_before = resource.getrusage(resource.RUSAGE_CHILDREN)
    with (evidence / "stdout.tsv").open("xb") as stdout, (evidence / "stderr.txt").open("xb") as stderr:
        timed_out = False
        try:
            result = subprocess.run(command, stdout=stdout, stderr=stderr, stdin=subprocess.DEVNULL, timeout=600)
            code = result.returncode
        except subprocess.TimeoutExpired:
            timed_out, code = True, None
    usage_after = resource.getrusage(resource.RUSAGE_CHILDREN)
    log = (evidence / "stdout.tsv").read_text()
    update_count = sum(line.startswith("UPDATE\t") for line in log.splitlines())
    unchanged = all(sha(path) == expected for path, expected in spec["fileHashes"].items())
    complete = code == 0 and update_count == 128 and (output / "final.lm1").is_file() and unchanged
    receipt = {
        "schema": "continuum-personal-voice-run-v1", "arm": args.arm,
        "registrationSha256": sha(args.registration), "command": command,
        "startedUtc": start_utc, "finishedUtc": datetime.now(timezone.utc).isoformat(),
        "exitCode": code, "timedOut": timed_out, "elapsedSeconds": time.monotonic() - started,
        "childUserCpuSeconds": usage_after.ru_utime - usage_before.ru_utime,
        "childSystemCpuSeconds": usage_after.ru_stime - usage_before.ru_stime,
        "maxChildRssKiB": usage_after.ru_maxrss,
        "resourceScope": "Linux x86_64 JVM process, not Fold7; no GPU/thermal/energy measurement",
        "observedUpdates": update_count, "complete": complete, "inputsUnchanged": unchanged,
        "stdoutSha256": sha(evidence / "stdout.tsv"), "stderrSha256": sha(evidence / "stderr.txt"),
        "artifacts": {str(path.relative_to(output)): {"sha256": sha(path), "bytes": path.stat().st_size}
                      for path in sorted(output.rglob("*")) if path.is_file()},
        "qualityAcceptance": "REQUIRES_INDEPENDENT_EVALUATION", "productionPromotion": False,
        "gpuJobs": 0, "externalModelCalls": 0,
    }
    (evidence / "receipt.json").write_text(json.dumps(receipt, indent=2) + "\n")
    print(json.dumps({"arm": args.arm, "complete": complete, "updates": update_count,
                      "seconds": receipt["elapsedSeconds"], "receipt": str(evidence / "receipt.json")}))
    if not complete:
        raise SystemExit(1)


if __name__ == "__main__":
    main()
