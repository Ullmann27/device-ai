#!/usr/bin/env python3
"""Compile the unchanged native tokenizer and compare every pilot input."""
from __future__ import annotations

import argparse
import base64
import csv
import hashlib
import json
from pathlib import Path
import subprocess
import time


def digest(path):
    return hashlib.sha256(path.read_bytes()).hexdigest()


def run(repo, data, tokenizer, compiler, build, output):
    build.mkdir(parents=True, exist_ok=False)
    output.mkdir(parents=True, exist_ok=False)
    source = repo / "app/src/main/java/at/heinz/deviceai/learning/lm/LmBpeTokenizer.kt"
    checker = Path(__file__).with_name("LmSemanticTokenizerAudit.kt")
    rows = []
    for split in ("train", "dev"):
        meta = {r["id"]: r for r in json.loads((data / f"{split}-metadata.json").read_text())}
        for row in csv.DictReader((data / f"{split}.tsv").read_text().splitlines(), delimiter="\t"):
            encoded = base64.b64encode(row["prompt"].encode()).decode()
            ids = ",".join(map(str, meta[row["id"]]["prefixTokenIds"]))
            rows.append(f"{row['id']}\t{encoded}\t{ids}")
    inputs = output / "inputs.tsv"
    inputs.write_text("id\tpromptBase64\tprefixTokens\n" + "\n".join(rows) + "\n")
    jar = build / "tokenizer-audit.jar"
    compile_command = [str(compiler), str(source), str(checker), "-include-runtime", "-d", str(jar)]
    native_command = ["java", "-Xmx256m", "-cp", str(jar), "at.heinz.deviceai.learning.lm.LmSemanticTokenizerAudit", str(tokenizer), str(inputs)]
    inputs_hashes = {str(p): digest(p) for p in (source, checker, inputs, tokenizer, Path(__file__))}
    started = time.perf_counter()
    version = subprocess.run([str(compiler), "-version"], capture_output=True, text=True, timeout=60)
    compiled = subprocess.run(compile_command, capture_output=True, timeout=120)
    (output / "compile.stdout.txt").write_bytes(compiled.stdout)
    (output / "compile.stderr.txt").write_bytes(compiled.stderr)
    if compiled.returncode != 0:
        raise RuntimeError("native tokenizer compile failed")
    checked = subprocess.run(native_command, capture_output=True, timeout=60)
    (output / "native.stdout.tsv").write_bytes(checked.stdout)
    (output / "native.stderr.txt").write_bytes(checked.stderr)
    report = {
        "schema": "lm1-semantic-native-tokenizer-parity-v1",
        "status": "PASS" if checked.returncode == 0 else "FAIL",
        "nativeRows": len(rows), "modelUpdates": 0, "modelInference": 0,
        "compileExit": compiled.returncode, "nativeExit": checked.returncode,
        "compilerVersion": version.stdout + version.stderr,
        "compileCommand": compile_command, "nativeCommand": native_command,
        "inputHashes": inputs_hashes, "jarSha256": digest(jar),
        "stdoutSha256": digest(output / "native.stdout.tsv"),
        "allInputsUnchanged": all(digest(Path(p)) == value for p, value in inputs_hashes.items()),
        "elapsedSeconds": time.perf_counter() - started,
    }
    if not report["allInputsUnchanged"]:
        report["status"] = "FAIL"
    (output / "receipt.json").write_text(json.dumps(report, indent=2, sort_keys=True) + "\n")
    if report["status"] != "PASS":
        raise RuntimeError("native parity failed")
    return report


def main():
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("--repo", type=Path, default=Path(__file__).resolve().parents[3])
    for name in ("data", "tokenizer", "compiler", "build", "output"):
        parser.add_argument("--" + name, type=Path, required=True)
    args = parser.parse_args()
    report = run(**vars(args))
    print(json.dumps({k: report[k] for k in ("status", "nativeRows", "modelUpdates", "elapsedSeconds")}, sort_keys=True))


if __name__ == "__main__":
    main()
