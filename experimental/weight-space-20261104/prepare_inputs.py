#!/usr/bin/env python3
"""Materialize the exact historical LM1 inputs from a local Git object database."""
import argparse
import hashlib
import json
from pathlib import Path
import re
import subprocess

DATA_COMMIT = "d2304a7658e1256b160c8012e1bac28268ff64a5"
CORPUS_COMMIT = "1d92db03b87558c0b330dc584fd1f4c025a93c82"
BASE_SHA = "7a0af1e99554f9e73fbe4212d0aa4bf8ec97745efca56998d9b42bac4fad44fd"
TOKENIZER_SHA = "94375c73d1f6e2d04b790738580143fa8c1b9b456d40fa7eb6b4009877bd892f"


def digest(data: bytes) -> str:
    return hashlib.sha256(data).hexdigest()


def main() -> None:
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("--repo", type=Path, default=Path.cwd())
    parser.add_argument("--output", type=Path, required=True)
    args = parser.parse_args()
    repository = args.repo.resolve(strict=True)
    destination = args.output.absolute()
    if destination.exists() or destination.is_symlink():
        raise FileExistsError("use a new input directory; existing data is never overwritten")
    records = json.loads(Path(__file__).with_name("train-source-manifest.json").read_text())
    if len(records) != 77:
        raise ValueError("expected the original 77 training documents")

    def blob(commit: str, path: str) -> bytes:
        return subprocess.check_output(["git", "-C", str(repository), "show", f"{commit}:{path}"], timeout=30)

    baseline = blob(DATA_COMMIT, f"checkpoints/{BASE_SHA}.lm1")
    tokenizer = blob(DATA_COMMIT, f"tokenizer/tokenizer-v512-{TOKENIZER_SHA}.lmbp")
    if digest(baseline) != BASE_SHA or digest(tokenizer) != TOKENIZER_SHA:
        raise ValueError("historical model/tokenizer identity mismatch")
    documents = []
    names = set()
    for record in records:
        original, local = record["original_path"], record["local_name"]
        if not re.fullmatch(r"[0-9a-f]{64}\.md", local) or local in names:
            raise ValueError("invalid or duplicate corpus filename")
        if hashlib.sha256(original.encode()).digest()[0] % 10 in {0, 1}:
            raise ValueError("validation or test document in training manifest")
        if local != digest(original.encode()) + ".md":
            raise ValueError("corpus filename does not bind its original path")
        data = blob(CORPUS_COMMIT, original)
        if digest(data) != record["sha256"]:
            raise ValueError(f"historical corpus identity mismatch: {original}")
        names.add(local)
        documents.append((local, original, data))
    destination.mkdir(parents=True, exist_ok=False)
    (destination / "lm1-baseline.lm1").write_bytes(baseline)
    (destination / "lm1-tokenizer.lmbp").write_bytes(tokenizer)
    corpus = destination / "original-train-docs"
    corpus.mkdir()
    for local, _, data in documents:
        (corpus / local).write_bytes(data)
    (corpus / "paths.tsv").write_text("".join(f"{local}\t{original}\n" for local, original, _ in documents))
    (corpus / "manifest.json").write_text(json.dumps(records, indent=2) + "\n")
    print(json.dumps({"output": str(destination), "trainingDocuments": len(documents),
                      "checkpointSha256": digest(baseline), "tokenizerSha256": digest(tokenizer),
                      "pathsSha256": digest((corpus / "paths.tsv").read_bytes())}, indent=2))


if __name__ == "__main__":
    main()
