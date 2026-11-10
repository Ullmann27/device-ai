#!/usr/bin/env python3
"""Execute the single registered distillation arm."""
import argparse
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
    parser.add_argument("registration", type=Path)
    parser.add_argument("--check-only", action="store_true")
    args = parser.parse_args()
    spec = json.loads(args.registration.read_text())
    if spec["schema"] != "lm1-distill-dev-registration-v1":
        raise ValueError("unexpected registration schema")
    for path, expected in spec["fileHashes"].items():
        if sha(path) != expected:
            raise ValueError("registered input changed: " + path)
    command = spec["commands"]["distill"]
    if (command[1:3] != ["-Xmx768m", "-XX:ActiveProcessorCount=1"]
            or command[5:7] != ["at.heinz.deviceai.learning.lm.LmDistillPilot", "distill"]
            or spec["timeoutSeconds"] != 660):
        raise ValueError("registered resource controls changed")
    output = Path(command[-1])
    evidence = Path(spec["evidenceDirectories"]["distill"])
    if output.exists() or evidence.exists():
        raise FileExistsError("registered output already exists")
    if args.check_only:
        print(json.dumps({"preflight": "PASS", "arm": "distill", "updates": 0}))
        return
    if spec["prerequisites"]["independentAnalyzerPin"] == "PENDING_ROOT_FREEZE":
        raise ValueError("independent analyzer not frozen")
    evidence.mkdir(parents=True)
    started = time.monotonic()
    with (evidence / "stdout.tsv").open("wb") as stdout, (evidence / "stderr.txt").open("wb") as stderr:
        timed_out = False
        execution_error = None
        try:
            result = subprocess.run(command, stdout=stdout, stderr=stderr, timeout=spec["timeoutSeconds"])
            code = result.returncode
        except subprocess.TimeoutExpired:
            timed_out, code = True, None
        except OSError as error:
            execution_error, code = str(error), None
    receipt = {
        "schema": "lm1-distill-dev-run-v1", "arm": "distill",
        "registrationSha256": sha(args.registration), "command": command,
        "exitCode": code, "timedOut": timed_out, "executionError": execution_error,
        "elapsedSeconds": time.monotonic() - started,
        "maxChildRssKiB": resource.getrusage(resource.RUSAGE_CHILDREN).ru_maxrss,
        "rssMeaning": "Linux child high-water mark, not Android",
        "inputsUnchanged": all(sha(p) == h for p, h in spec["fileHashes"].items()),
        "stdoutSha256": sha(evidence / "stdout.tsv"), "stderrSha256": sha(evidence / "stderr.txt"),
        "artifacts": {str(p.relative_to(output)): {"sha256": sha(p), "bytes": p.stat().st_size}
                      for p in sorted(output.rglob("*")) if p.is_file()},
        "qualityAcceptance": "REQUIRES_INDEPENDENT_DEV_ANALYSIS", "productionPromotion": False,
    }
    (evidence / "receipt.json").write_text(json.dumps(receipt, indent=2) + "\n")
    print(json.dumps({"arm": "distill", "exitCode": code, "seconds": receipt["elapsedSeconds"],
                      "receipt": str(evidence / "receipt.json")}))
    if code != 0 or not receipt["inputsUnchanged"]:
        raise SystemExit(1)


if __name__ == "__main__":
    main()
