#!/usr/bin/env python3
"""Audit the open data package; token counts use an independent Python port."""
from __future__ import annotations

import argparse
import csv
import hashlib
import json
from pathlib import Path
import struct
import unicodedata

import regex

TOKENIZER_SHA256 = "94375c73d1f6e2d04b790738580143fa8c1b9b456d40fa7eb6b4009877bd892f"
PRETOKEN = regex.compile(r" ?\p{L}+| ?\p{N}| ?[^\s\p{L}\p{N}]+|\s+")


def sha(data):
    return hashlib.sha256(data).hexdigest()


def normalize(text):
    return " ".join(unicodedata.normalize("NFKC", text).casefold().split())


class Tokenizer:
    def __init__(self, data):
        if sha(data) != TOKENIZER_SHA256:
            raise ValueError("tokenizer identity mismatch")
        magic, count = struct.unpack_from(">ii", data)
        if magic != 0x4C4D4250 or not 0 <= count <= 8000 or len(data) != 8+8*count:
            raise ValueError("invalid tokenizer layout")
        self.merges = [struct.unpack_from(">ii", data, 8+8*i) for i in range(count)]
        if any(not (0 <= a < 256+i and 0 <= b < 256+i)
               for i, (a, b) in enumerate(self.merges)):
            raise ValueError("invalid merge ids")
        self.rank = {pair: i for i, pair in enumerate(self.merges)}
        self.bos, self.eos = 256+count, 257+count

    def encode(self, text):
        output = []
        for word in PRETOKEN.findall(text):
            symbols = list(word.encode())
            while len(symbols) > 1:
                candidates = [self.rank.get((a, b), 10**9)
                              for a, b in zip(symbols, symbols[1:])]
                best = min(candidates)
                if best == 10**9:
                    break
                pair, following, index = self.merges[best], [], 0
                while index < len(symbols):
                    if index+1 < len(symbols) and tuple(symbols[index:index+2]) == pair:
                        following.append(256+best)
                        index += 2
                    else:
                        following.append(symbols[index])
                        index += 1
                symbols = following
            output.extend(symbols)
        return output


def audit(root, tokenizer):
    tokenizer_bytes = tokenizer.read_bytes()
    encoder = Tokenizer(tokenizer_bytes)
    report = {"status": "OPEN_DEV_DATA_AUDIT_PASS", "modelUpdates": 0,
              "tokenizerSha256": sha(tokenizer_bytes),
              "tokenCountsMethod": "Independent Python port of frozen LmBpeTokenizer",
              "nativeTokenizerValidation": "REQUIRED", "bos": encoder.bos, "eos": encoder.eos,
              "labelTokens": {str(i): encoder.encode(str(i)) for i in range(4)}, "files": {}}
    if any(len(tokens) != 1 for tokens in report["labelTokens"].values()):
        raise ValueError("labels must be single tokens")
    seen = set()
    for name in ("replay.txt", "retention_text.txt"):
        path = root/name
        lines = path.read_text().splitlines()
        expected = 16 if name == "replay.txt" else 8
        if len(lines) != expected or any(not line.strip() for line in lines):
            raise ValueError("invalid text row count")
        canonical = {normalize(line) for line in lines}
        if len(canonical) != expected or canonical & seen:
            raise ValueError("duplicate or overlapping text rows")
        seen.update(canonical)
        lengths = [len(encoder.encode(line)) for line in lines]
        if not all(30 <= length <= 80 for length in lengths):
            raise ValueError("text token budget exceeded")
        report["files"][name] = {"sha256": sha(path.read_bytes()), "rows": len(lines),
                                  "tokens": lengths, "minTokens": min(lengths), "maxTokens": max(lengths)}
    seen = set()
    for name in ("note-contrasts-v1/train.tsv", "note-contrasts-v1/dev.tsv", "retention.tsv"):
        path = root/name
        rows = list(csv.DictReader(path.read_text().splitlines(), delimiter="\t"))
        expected = 128 if name.endswith("/train.tsv") else 64
        if len(rows) != expected or any(set(row) != {"id", "label", "prompt"} for row in rows):
            raise ValueError("invalid intent rows")
        labels = {row["label"] for row in rows}
        if labels != ({"1", "2"} if name == "retention.tsv" else {"0", "3"}):
            raise ValueError("invalid intent labels")
        canonical = {normalize(row["prompt"]) for row in rows}
        if len(canonical) != expected or canonical & seen:
            raise ValueError("duplicate or overlapping intent prompts")
        seen.update(canonical)
        lengths = [1+len(encoder.encode("Auftrag: "+row["prompt"]+"\n"))
                   +len(encoder.encode(row["label"])) for row in rows]
        if max(lengths) > 256:
            raise ValueError("context limit exceeded")
        report["files"][name] = {"sha256": sha(path.read_bytes()), "rows": len(rows),
                                  "bosPrefixAnswerMaxTokens": max(lengths), "allFit256": True}
    manifest = json.loads((root/"note-contrasts-v1/manifest.json").read_text())
    if manifest["generatorSha256"] != sha((root/"make_note_contrasts.py").read_bytes()):
        raise ValueError("generator identity mismatch")
    for split in ("train", "dev"):
        if manifest["files"][split]["sha256"] != report["files"][f"note-contrasts-v1/{split}.tsv"]["sha256"]:
            raise ValueError("dataset identity mismatch")
    metadata = (root/"note-contrasts-v1/row-metadata.json").read_bytes()
    if sha(metadata) != manifest["rowMetadataSha256"]:
        raise ValueError("row metadata identity mismatch")
    report["datasetManifestSha256"] = sha((root/"note-contrasts-v1/manifest.json").read_bytes())
    report["auditSourceSha256"] = sha(Path(__file__).read_bytes())
    return report


def main():
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("--data", type=Path, default=Path(__file__).parent)
    parser.add_argument("--tokenizer", type=Path, required=True)
    args = parser.parse_args()
    print(json.dumps(audit(args.data, args.tokenizer), indent=2, sort_keys=True))


if __name__ == "__main__":
    main()
