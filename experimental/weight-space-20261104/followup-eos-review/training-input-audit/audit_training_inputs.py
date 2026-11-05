#!/usr/bin/env python3
"""Replay the published TRAIN targets without loading or running an LM.

This independent Python reader reproduces the pinned Kotlin corpus/tokenizer
semantics. It accepts its result only if corpus bytes, shuffled indices, actual
token sequences, and all four historical per-step token totals match their
published hashes/records. It never imports the experiment driver, reads a model
checkpoint, invokes a process, trains, or evaluates development/holdout text.
"""

import argparse
from collections import Counter
from datetime import datetime, timezone
import hashlib
import json
from pathlib import Path
import re
import struct
import unicodedata


BASE_COMMIT = "30cd3a89810ed14fa84eca95236d3177e016118d"
EXPERIMENT = "experimental/weight-space-20261104"
CORPUS_SHA = "27e67455e23dcc410855b2fd0f0292f9a00556d72d62aefb018e9eab4be034c9"
TOKENIZER_SHA = "94375c73d1f6e2d04b790738580143fa8c1b9b456d40fa7eb6b4009877bd892f"
SCHEDULE_SHA = "346e93c510dafe5e12ee6a0e54ae94a0f0d099e7c0fc8c6f8973e5ff9a6b5066"
TOKEN_SCHEDULE_SHA = "8e0d6ef53de6c84e48c74ecc4dbd4b70e25632caadc624f31ad0ac4aad681fee"
PINNED_SOURCES = {
    "app/src/main/java/at/heinz/deviceai/learning/lm/LmBpeTokenizer.kt":
        "fcff707a5a1679244cffbb6583e1594b6084e941c7e918f834d71499029b628e",
    "app/src/main/java/at/heinz/deviceai/learning/lm/LmCorpus.kt":
        "f6b0d0bbb9ee0e082b82e60ab7a39113cbf7d996fe60b363ee25c39a1b4930b4",
    "app/src/main/java/at/heinz/deviceai/learning/lm/LmModel.kt":
        "abb4b50cc443344aefe720844aad4a9ca3e3d967973cc31a4e45ca07b732f099",
    "app/src/main/java/at/heinz/deviceai/learning/lm/LmTensor.kt":
        "a5497f18d09be50f7afbd201d54d97f012f7508f8c53c834ff6c10ef278290ab",
    f"{EXPERIMENT}/LmWeightExperiment.kt":
        "5af9ba78209873de3588a3f18ab0e2e6e5d76d8bc85652c1b7fc82651bb0ff68",
    f"{EXPERIMENT}/train-source-manifest.json":
        "af590c4a47bf553a5ac1c331c9235aad4cd8608dd349db9ea25e9b7a1544074a",
}
PINNED_TRAIN_LOGS = {
    "core-6562304/continued-small-train.tsv":
        "c8af895cb59d175ec2c8273bba9ee18b7a41e42ff861b6c89df6cb9f89489513",
    "core-6562304/continued-expanded-train.tsv":
        "545292e52ddda6d17213ed685fe189b6e4a091214b3d533fa29a0d68ff8dc5cd",
    "core-9712128/continued-small-train.tsv":
        "27e866d8f5a958c0e163884641641baad11678863905c809563e1011df8b606f",
    "core-9712128/continued-expanded-train.tsv":
        "d85bce3f6b6bad12907378dbc26a435d9009c36b21ae9a98b6b5abdbfdea9ce0",
}
ASCII_SPACE = " \t\n\x0b\f\r"


def require(condition, message):
    if not condition:
        raise ValueError(message)


def sha(data):
    return hashlib.sha256(data).hexdigest()


def read_bound(path, expected=None):
    require(not path.is_symlink() and path.is_file(), f"not a regular input: {path}")
    raw = path.read_bytes()
    require(0 < len(raw) <= 2_000_000, f"input outside the source size bound: {path}")
    if expected is not None:
        require(sha(raw) == expected, f"input SHA-256 mismatch: {path}")
    return raw


def kotlin_trim(text):
    # Kotlin Char.isWhitespace includes Java isSpaceChar; Python adds NEL.
    whitespace = lambda c: c.isspace() and c != "\x85"
    start, end = 0, len(text)
    while start < end and whitespace(text[start]):
        start += 1
    while end > start and whitespace(text[end - 1]):
        end -= 1
    return text[start:end]


def clean(markdown):
    text = markdown.replace("\r\n", "\n")
    rules = [
        (r"(?ms)^\s*(\x60{3}|~~~).*?^\s*(\x60{3}|~~~)[^\n]*$", "\n"),
        (r"(?s)<!--.*?-->", ""),
        (r"!?\[([^\]]*)\]\([^)]*\)", lambda m: m.group(1)),
        (r"\b(?:https?|ftp)://\S+|\bwww\.\S+", "<link>"),
        (r"\b[A-Za-z0-9._%+-]+@[A-Za-z0-9.-]+\.[A-Za-z]{2,}\b", "<e-mail>"),
        (r"\b[0-9a-fA-F]{10,}\b", "<hash>"),
        (r"\d{7,}", "<zahl>"),
        (r"(?m)^\s*\|?\s*:?-{3,}:?\s*(\|\s*:?-{3,}:?\s*)*\|?\s*$\n?", ""),
        (r"Heinz\s+Ullmann", "der Besitzer"),
        (r"(?i)ullmann\w*", "<konto>"),
        (r"Heinz'", "des Besitzers"),
        (r"Heinz", "der Besitzer"),
        (r"(?i)heinz", "besitzer"),
        (r"[ \t]+\n", "\n"),
        (r"\n{3,}", "\n\n"),
    ]
    for pattern, replacement in rules:
        text = re.sub(pattern, replacement, text, flags=re.ASCII)
    return kotlin_trim(text)


def segments(text):
    result = []
    for raw in re.split(r"\n\s*\n", text, flags=re.ASCII):
        rest = kotlin_trim(raw)
        while rest:
            if len(rest.encode()) <= 255:
                result.append(rest)
                break
            cut, length, last_space = 0, 0, -1
            for ch in rest:
                size = len(ch.encode())
                if length + size > 255:
                    break
                # Java Character.isWhitespace excludes nonbreaking spaces.
                if ch.isspace() and ch not in "\x85\u00a0\u2007\u202f":
                    last_space = cut
                cut += 1
                length += size
            end = last_space if last_space > 0 else cut
            require(end > 0, "invalid UTF-8 segment boundary")
            result.append(kotlin_trim(rest[:end]))
            rest = kotlin_trim(rest[end:])
    return [
        kotlin_trim(re.sub(r"\s+", " ", s, flags=re.ASCII))
        for s in result
        if sum(not (ch.isspace() and ch != "\x85") for ch in s) >= 2
    ]


def pretokenize(text):
    """Independent scanner for the pinned JVM PRETOKEN regular expression."""
    at = 0
    while at < len(text):
        start = at
        prefix = at + 1 if text[at] == " " and at + 1 < len(text) else at
        category = unicodedata.category(text[prefix])[0]
        if category == "L":
            at = prefix + 1
            while at < len(text) and unicodedata.category(text[at])[0] == "L":
                at += 1
        elif category == "N":
            at = prefix + 1
        elif text[prefix] not in ASCII_SPACE:
            at = prefix + 1
            while (at < len(text) and text[at] not in ASCII_SPACE
                   and unicodedata.category(text[at])[0] not in "LN"):
                at += 1
        else:
            at = start + 1
            while at < len(text) and text[at] in ASCII_SPACE:
                at += 1
        yield text[start:at]


class Bpe:
    def __init__(self, raw):
        magic, count = struct.unpack(">ii", raw[:8])
        require(magic == 0x4C4D4250 and 0 <= count <= 8000, "invalid LMBP header")
        require(len(raw) == 8 + count * 8, "invalid LMBP size")
        self.merges = list(struct.iter_unpack(">ii", raw[8:]))
        self.pieces = [bytes([i]) for i in range(256)]
        for i, (a, b) in enumerate(self.merges):
            require(0 <= a < 256 + i and 0 <= b < 256 + i, "invalid BPE reference")
            self.pieces.append(self.pieces[a] + self.pieces[b])
        self.ranks = {pair: rank for rank, pair in enumerate(self.merges)}
        self.bos, self.eos = 256 + count, 257 + count
        self.cache = {}

    def encode(self, text):
        result = []
        for word in pretokenize(text):
            if word not in self.cache:
                ids = list(word.encode())
                while len(ids) > 1:
                    ranks = [self.ranks.get(pair) for pair in zip(ids, ids[1:])]
                    ranked = [r for r in ranks if r is not None]
                    if not ranked:
                        break
                    best = min(ranked)
                    a, b = self.merges[best]
                    merged, i = [], 0
                    while i < len(ids):
                        if i + 1 < len(ids) and ids[i] == a and ids[i + 1] == b:
                            merged.append(256 + best)
                            i += 2
                        else:
                            merged.append(ids[i])
                            i += 1
                    ids = merged
                self.cache[word] = ids
            result.extend(self.cache[word])
        require(b"".join(self.pieces[i] for i in result) == text.encode(),
                "BPE roundtrip failed")
        return result


class JavaRandom:
    def __init__(self, seed):
        self.state = (seed ^ 0x5DEECE66D) & ((1 << 48) - 1)

    def next31(self):
        self.state = (self.state * 0x5DEECE66D + 0xB) & ((1 << 48) - 1)
        return self.state >> 17

    def next_int(self, bound):
        if bound & (bound - 1) == 0:
            return (bound * self.next31()) >> 31
        while True:
            bits = self.next31()
            value = bits % bound
            if bits - value + bound - 1 < (1 << 31):
                return value


def parse_rows(raw):
    rows = []
    for line in raw.decode().splitlines():
        fields = line.split("\t")
        rows.append({"kind": fields[0], **dict(v.split("=", 1) for v in fields[1:])})
    return rows


def audit(repo, inputs):
    source_hashes = {}
    for path, expected in PINNED_SOURCES.items():
        source_hashes[path] = sha(read_bound(repo / path, expected))
    records = json.loads(read_bound(repo / EXPERIMENT / "train-source-manifest.json"))
    require(len(records) == 77, "expected 77 original TRAIN documents")
    expected_paths = "".join(f"{r['local_name']}\t{r['original_path']}\n" for r in records).encode()
    corpus_dir = inputs / "original-train-docs"
    require(read_bound(corpus_dir / "paths.tsv") == expected_paths, "corpus order changed")
    texts, names, input_hashes = [], set(), {}
    for record in records:
        local, original = record["local_name"], record["original_path"]
        path_digest = hashlib.sha256(original.encode()).digest()
        require(path_digest[0] % 10 not in {0, 1}, "non-TRAIN document")
        require(local == path_digest.hex() + ".md" and local not in names, "invalid corpus name")
        names.add(local)
        raw = read_bound(corpus_dir / local, record["sha256"])
        input_hashes[local] = sha(raw)
        texts.extend(segments(clean(raw.decode("utf-8"))))
    corpus = ("\n".join(texts) + "\n").encode()
    require(len(texts) == 2520 and len(corpus) == 429290 and sha(corpus) == CORPUS_SHA,
            "independent corpus replay differs from the historical Kotlin output")
    tok = Bpe(read_bound(inputs / "lm1-tokenizer.lmbp", TOKENIZER_SHA))
    encoded = [tok.encode(text) for text in texts]
    sequences = [
        [tok.bos] + ids[:63] + ([tok.eos] if len(ids) <= 63 else [])
        for ids in encoded
    ]
    order = list(range(len(sequences)))
    rng = JavaRandom(20261104)
    for size in range(len(order), 1, -1):
        other = rng.next_int(size)
        order[size - 1], order[other] = order[other], order[size - 1]
    selected = order[:64]
    schedule_hash = sha(",".join(map(str, selected)).encode())
    token_hash = sha("\n".join(",".join(map(str, sequences[i])) for i in selected).encode())
    require(schedule_hash == SCHEDULE_SHA, "independent shuffle differs")
    require(token_hash == TOKEN_SCHEDULE_SHA, "independent actual token schedule differs")
    require(len(selected) == 64 and len(set(selected)) == 64, "wrong schedule shape")
    require(sum(len(sequences[i]) - 1 for i in selected) == 3255, "wrong target count")
    per_batch, cumulative = [], 0
    for step in range(16):
        indices = selected[step * 4:(step + 1) * 4]
        target_count = sum(len(sequences[i]) - 1 for i in indices)
        eos_count = sum(sequences[i][-1] == tok.eos for i in indices)
        cumulative += target_count
        per_batch.append({
            "step": step + 1, "segmentIndices": indices, "targets": target_count,
            "contentTargets": target_count - eos_count, "eosTargets": eos_count,
            "cumulativeTargets": cumulative,
            "fullContentTokenLengths": [len(encoded[i]) for i in indices],
        })
    log_hashes, output_checkpoints = {}, {}
    for key, expected in PINNED_TRAIN_LOGS.items():
        profile, name = key.split("/")
        path = f"{EXPERIMENT}/core-6-10m-evidence/run/{profile}/logs/{name}"
        raw = read_bound(repo / path, expected)
        log_hashes[path] = sha(raw)
        rows = parse_rows(raw)
        identity = [r for r in rows if r["kind"] == "TRAIN_INPUT"]
        require(len(identity) == 1, "missing historical TRAIN_INPUT")
        require(identity[0]["datasetSha256"] == CORPUS_SHA
                and identity[0]["tokenizerSha256"] == TOKENIZER_SHA
                and identity[0]["scheduleSha256"] == schedule_hash
                and identity[0]["tokenScheduleSha256"] == token_hash, "historical identity differs")
        steps = [r for r in rows if r["kind"] == "TRAIN_STEP"]
        require(len(steps) == 16, "historical step count differs")
        require(all(int(r["step"]) == b["step"] and int(r["tokens"]) == b["cumulativeTargets"]
                    for r, b in zip(steps, per_batch)), "historical per-step target count differs")
        checkpoint = [r for r in rows if r["kind"] == "CHECKPOINT"]
        require(len(checkpoint) == 1, "missing historical CHECKPOINT")
        output_checkpoints[key] = checkpoint[0]["sha256"]
    eos_indices = [i for i in selected if sequences[i][-1] == tok.eos]
    bos_labels = sum(seq[1:].count(tok.bos) for seq in (sequences[i] for i in selected))
    eos_targets = sum(seq[1:].count(tok.eos) for seq in (sequences[i] for i in selected))
    require(bos_labels == 0 and eos_targets == len(eos_indices), "misplaced special-token target")
    return {
        "status": "PASS", "kind": "MODEL_FREE_TRAIN_TARGET_REPLAY",
        "sourceBaseCommit": BASE_COMMIT, "observedUtc": datetime.now(timezone.utc).isoformat(),
        "auditorSha256": sha(Path(__file__).read_bytes()),
        "modelLoaded": False, "modelProcessesStarted": 0, "trainingExecuted": False,
        "developmentRead": False, "holdoutRead": False, "weightsChanged": False,
        "sourceSha256": source_hashes, "trainingLogSha256": log_hashes,
        "inputDocumentSha256": input_hashes,
        "tokenizer": {"sha256": TOKENIZER_SHA, "vocab": tok.eos + 1,
                      "bos": tok.bos, "eos": tok.eos, "roundtripAllSegments": True},
        "corpus": {"sha256": sha(corpus), "documents": 77, "segments": len(texts),
                   "bytes": len(corpus), "uniqueSegmentTexts": len(set(texts)),
                   "completeSegmentsAtMost63Tokens": sum(len(t) <= 63 for t in encoded),
                   "longerSegmentsTruncatedForTraining": sum(len(t) > 63 for t in encoded)},
        "schedule": {
            "sha256": schedule_hash, "tokenSha256": token_hash, "segments": 64,
            "uniqueIndices": len(set(selected)), "uniqueTexts": len({texts[i] for i in selected}),
            "targets": cumulative, "contentTargets": cumulative - eos_targets,
            "eosTargets": eos_targets, "eosFraction": eos_targets / cumulative,
            "bosTargets": bos_labels, "completeSegments": len(eos_indices),
            "truncatedSegments": 64 - len(eos_indices),
            "omittedSuffixContentTokens": sum(max(0, len(encoded[i]) - 63) for i in selected),
            "maximumModelInputPositions": max(len(sequences[i]) - 1 for i in selected),
            "eosAtContentLengths": dict(sorted(Counter(len(encoded[i]) for i in eos_indices).items())),
            "eosSegmentLastCharacters": dict(sorted(Counter(texts[i][-1] for i in eos_indices).items())),
            "batches": per_batch,
        },
        "historicalOutputCheckpointSha256": output_checkpoints,
        "conclusion": "All four arms have identical reconstructed targets. EOS-only or BOS-label corruption is not observed. This replay does not establish the cause of a model's EOS preference.",
    }


def main():
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("--repo", type=Path, required=True)
    parser.add_argument("--inputs", type=Path, required=True)
    parser.add_argument("--output", type=Path, required=True)
    args = parser.parse_args()
    require(not args.output.exists() and not args.output.is_symlink(), "refusing to overwrite evidence")
    report = audit(args.repo.resolve(strict=True), args.inputs.resolve(strict=True))
    with args.output.open("x", encoding="utf-8") as target:
        json.dump(report, target, indent=2, ensure_ascii=False)
        target.write("\n")
    print(json.dumps({"status": report["status"], "output": str(args.output),
                      "targets": report["schedule"]["targets"],
                      "eosTargets": report["schedule"]["eosTargets"],
                      "contentTargets": report["schedule"]["contentTargets"],
                      "modelProcessesStarted": 0}))


if __name__ == "__main__":
    main()
