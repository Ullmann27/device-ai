#!/usr/bin/env python3
"""Recreate the exact research inputs from pinned git objects; never train."""
from __future__ import annotations

import argparse
import hashlib
import json
from pathlib import Path
import subprocess


ROOT = Path(__file__).resolve().parents[2]
PROVENANCE = ROOT / "docs/evidence/lm1-training-recovery-20261104/preflight/input-provenance.json"
REGISTRATION = ROOT / "experimental/lm1-training-recovery-20261104/registration.json"


def digest(data: bytes) -> str:
    return hashlib.sha256(data).hexdigest()


def git_bytes(repo: Path, commit: str, path: str, expected: str) -> bytes:
    result = subprocess.run(["git", "-C", str(repo), "show", commit + ":" + path],
                            capture_output=True, timeout=120)
    if result.returncode:
        raise RuntimeError("git object unavailable: " + commit + ":" + path)
    if digest(result.stdout) != expected:
        raise RuntimeError("git object content mismatch: " + path)
    return result.stdout


def main() -> None:
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("--repo", type=Path, required=True, help="Existing git repository with the pinned objects")
    parser.add_argument("--classpath", required=True, help="Registered study JAR plus Kotlin standard library")
    parser.add_argument("--output", type=Path, required=True, help="New input directory; must not exist")
    args = parser.parse_args()
    source = json.loads(PROVENANCE.read_text())
    expected = json.loads(REGISTRATION.read_text())["inputs"]
    out = args.output.resolve()
    out.mkdir(parents=True, exist_ok=False)
    inputs = [("lm1-baseline.lm1", source["baselineSourcePath"]),
              ("lm1-tokenizer.lmbp", source["tokenizerSourcePath"])]
    for name, path in inputs:
        (out / name).write_bytes(git_bytes(args.repo, source["modelSourceCommit"], path, expected[name]))
    docs = out / "original-train-docs"
    docs.mkdir()
    rows = []
    for document in source["documents"]:
        name = document["local_name"]
        if Path(name).name != name or name in {"paths.tsv", "", ".", ".."}:
            raise ValueError("invalid corpus filename")
        path = docs / name
        with path.open("xb") as stream:
            stream.write(git_bytes(args.repo, source["trainingDocumentSourceCommit"],
                                   document["original_path"], document["sha256"]))
        rows.append(name + "\t" + document["original_path"])
    (docs / "paths.tsv").write_text("\n".join(rows) + "\n")
    command = ["java", "-Xmx1g", "-cp", args.classpath,
               "at.heinz.deviceai.learning.lm.LmWeightExperiment", "corpus", str(docs), str(out / "train.txt")]
    with (out / "corpus.log").open("xb") as log:
        result = subprocess.run(command, cwd=ROOT, stdout=log, stderr=subprocess.STDOUT, timeout=120)
    if result.returncode:
        raise RuntimeError("corpus reconstruction failed; inspect corpus.log")
    actual = {name: digest((out / name).read_bytes()) for name in expected}
    if actual != expected:
        raise RuntimeError("reconstructed input identities differ from registration")
    receipt = {"status": "INPUTS_RECONSTRUCTED", "inputs": actual,
               "modelSourceCommit": source["modelSourceCommit"],
               "trainingDocumentSourceCommit": source["trainingDocumentSourceCommit"],
               "documents": len(rows), "trainingPerformed": False}
    with (out / "inputs-prepared.json").open("x") as stream:
        json.dump(receipt, stream, indent=2)
        stream.write("\n")
    print(json.dumps(receipt))


if __name__ == "__main__":
    main()
