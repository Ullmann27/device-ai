#!/usr/bin/env python3
"""Execute one registered native arm."""
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
    parser.add_argument("arm", choices=("answer", "replay"))
    parser.add_argument("--check-only", action="store_true")
    args = parser.parse_args()
    spec = json.loads(args.registration.read_text())
    for path, expected in spec["fileHashes"].items():
        if sha(path) != expected:
            raise ValueError("registered input changed: " + path)
    command = spec["commands"][args.arm]
    output = Path(command[-1])
    evidence = Path(spec["evidenceDirectories"][args.arm])
    if output.exists() or evidence.exists():
        raise FileExistsError("registered output already exists")
    if args.check_only:
        print(json.dumps({"preflight": "PASS", "arm": args.arm, "updates": 0}))
        return
    evidence.mkdir(parents=True)
    started = time.monotonic()
    with (evidence / "stdout.tsv").open("wb") as stdout, (evidence / "stderr.txt").open("wb") as stderr:
        timed_out = False
        try:
            result = subprocess.run(command, stdout=stdout, stderr=stderr, timeout=spec["timeoutSeconds"])
            code = result.returncode
        except subprocess.TimeoutExpired:
            timed_out, code = True, None
    receipt = {
        "schema": "lm1-contrast-dev-run-v1", "arm": args.arm,
        "registrationSha256": sha(args.registration), "command": command,
        "exitCode": code, "timedOut": timed_out,
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
    print(json.dumps({"arm": args.arm, "exitCode": code, "seconds": receipt["elapsedSeconds"], "receipt": str(evidence / "receipt.json")}))
    if code != 0 or not receipt["inputsUnchanged"]:
        raise SystemExit(1)


if __name__ == "__main__":
    main()
