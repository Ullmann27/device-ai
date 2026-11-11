#!/usr/bin/env python3
"""Hash and validate all preserved data evidence from the verified base."""
import argparse
import csv
import hashlib
import io
import json
from pathlib import Path
import subprocess

BASE = "1d9eab1275a7fcc92ad4c5e2d1dd52d1db411667"


def inventory(repo):
    root = repo / "experimental/weight-space-20261110/data"
    records = []
    for path in sorted(p for p in root.rglob("*") if p.is_file()):
        relative = path.relative_to(repo).as_posix()
        content = path.read_bytes()
        original = subprocess.check_output(["git", "show", f"{BASE}:{relative}"], cwd=repo)
        if original != content:
            raise ValueError("historical source changed: " + relative)
        text = content.decode("utf-8")
        record = {"path": relative, "bytes": len(content), "sha256": hashlib.sha256(content).hexdigest(),
                  "baseByteIdentical": True, "lines": len(text.splitlines())}
        if path.suffix == ".json":
            parsed = json.loads(text)
            record["parsedType"] = type(parsed).__name__
        if path.suffix == ".tsv":
            rows = list(csv.DictReader(io.StringIO(text), delimiter="\t"))
            record["rows"] = len(rows)
            if rows and "label" in rows[0]:
                record["labelCounts"] = {label: sum(r["label"] == label for r in rows) for label in sorted({r["label"] for r in rows})}
        records.append(record)
    return {"schema": "lm1-historical-data-source-inventory-v1", "baseCommit": BASE,
            "files": records, "allHistoricalSourcesUnchanged": True,
            "scope": "complete byte reads and structured parsing; source-level findings documented separately",
            "modelUpdates": 0, "modelInference": 0}


if __name__ == "__main__":
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("--repo", type=Path, default=Path(__file__).resolve().parents[3])
    parser.add_argument("--output", type=Path, required=True)
    args = parser.parse_args()
    result = inventory(args.repo)
    with args.output.open("x") as stream:
        json.dump(result, stream, indent=2, sort_keys=True)
        stream.write("\n")
    print(json.dumps({"files": len(result["files"]), "allHistoricalSourcesUnchanged": True}))
