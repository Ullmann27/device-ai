#!/usr/bin/env python3
"""Read-only calculation of one direct minGRU state path in the original LM1.

Only the registered original checkpoint and tokenizer are accepted. This script
does not load task data, run training, write files, or measure a full-model
gradient. All arithmetic after decoding the float32 weights uses Python floats.
"""
from __future__ import annotations

import argparse
import hashlib
import json
import math
from pathlib import Path
import statistics
import struct


BASE_SHA = "7a0af1e99554f9e73fbe4212d0aa4bf8ec97745efca56998d9b42bac4fad44fd"
TOKENIZER_SHA = "94375c73d1f6e2d04b790738580143fa8c1b9b456d40fa7eb6b4009877bd892f"
SOURCE_COMMIT = "30cd3a89810ed14fa84eca95236d3177e016118d"


def sha(data: bytes) -> str:
    return hashlib.sha256(data).hexdigest()


def verified_read(path: Path, expected: str, limit: int) -> bytes:
    if not path.is_file() or not 0 < path.stat().st_size <= limit:
        raise ValueError(f"input is not a bounded regular file: {path}")
    with path.open("rb") as stream:
        data = stream.read(limit + 1)
    if len(data) > limit or sha(data) != expected:
        raise ValueError(f"registered original input identity mismatch: {path}")
    return data


class Reader:
    def __init__(self, data: bytes):
        self.data = data
        self.offset = 0

    def take(self, size: int) -> bytes:
        if size < 0 or self.offset + size > len(self.data):
            raise ValueError("truncated or invalid serialized field")
        value = self.data[self.offset:self.offset + size]
        self.offset += size
        return value

    def integer(self) -> int:
        return struct.unpack(">i", self.take(4))[0]

    def utf_bytes(self) -> bytes:
        # Java writeUTF stores an unsigned byte count. Metadata are opaque here;
        # only the ASCII tokenizer hash and matrix names are decoded as text.
        return self.take(struct.unpack(">H", self.take(2))[0])

    def finished(self) -> None:
        if self.offset != len(self.data):
            raise ValueError("unexpected trailing serialized bytes")


def checkpoint(data: bytes) -> tuple[dict[str, int], dict[str, tuple[float, ...]]]:
    body, checksum = data[:-32], data[-32:]
    if hashlib.sha256(body).digest() != checksum:
        raise ValueError("checkpoint body checksum mismatch")
    reader = Reader(body)
    if (reader.integer(), reader.integer()) != (0x4C4D3143, 1):
        raise ValueError("unsupported LM1 checkpoint format")
    names = ("arch", "vocab", "dim", "layers", "heads", "context", "mlp")
    config = {name: reader.integer() for name in names}
    if any(config[name] != value for name, value in
           {"arch": 1, "vocab": 512, "dim": 64, "layers": 2, "heads": 4,
            "context": 256, "mlp": 256}.items()):
        raise ValueError("not the registered original minGRU architecture")
    if reader.utf_bytes().decode("ascii") != TOKENIZER_SHA:
        raise ValueError("checkpoint tokenizer binding mismatch")
    metadata_count = reader.integer()
    if not 0 <= metadata_count <= 64:
        raise ValueError("invalid metadata count")
    for _ in range(metadata_count):
        reader.utf_bytes()
        reader.utf_bytes()
    dim, mlp = config["dim"], config["mlp"]
    layout = [("tok", config["vocab"], dim)]
    for layer in range(config["layers"]):
        prefix = f"l{layer}."
        layout.extend((prefix + name, rows, cols) for name, rows, cols in (
            ("n1", 1, dim), ("wz", dim, dim), ("bz", 1, dim),
            ("wc", dim, dim), ("wg", dim, dim), ("wo", dim, dim),
            ("n2", 1, dim), ("w1", dim, mlp), ("b1", 1, mlp),
            ("w2", mlp, dim), ("b2", 1, dim)))
    layout.append(("nf", 1, dim))
    matrices = {}
    for name, rows, cols in layout:
        actual = (reader.utf_bytes().decode("ascii"), reader.integer(), reader.integer())
        if actual != (name, rows, cols):
            raise ValueError(f"unexpected matrix layout: {actual}")
        values = struct.unpack(f">{rows * cols}f", reader.take(4 * rows * cols))
        if not all(math.isfinite(value) for value in values):
            raise ValueError(f"non-finite checkpoint matrix: {name}")
        matrices[name] = values
    reader.finished()
    return config, matrices


def tokenizer(data: bytes) -> tuple[list[tuple[int, int]], list[bytes]]:
    reader = Reader(data)
    if reader.integer() != 0x4C4D4250:
        raise ValueError("not an LM BPE tokenizer")
    count = reader.integer()
    if not 0 <= count <= 8000:
        raise ValueError("invalid tokenizer merge count")
    merges, pieces = [], [bytes([value]) for value in range(256)]
    for index in range(count):
        left, right = reader.integer(), reader.integer()
        if not 0 <= left < 256 + index or not 0 <= right < 256 + index:
            raise ValueError("tokenizer merge refers to an unknown piece")
        merges.append((left, right))
        pieces.append(pieces[left] + pieces[right])
    reader.finished()
    return merges, pieces


def encode_segment(segment: str, merges: list[tuple[int, int]]) -> list[int]:
    rank = {pair: index for index, pair in enumerate(merges)}
    symbols = list(segment.encode("utf-8"))
    while len(symbols) > 1:
        ranks = [rank[pair] for pair in zip(symbols, symbols[1:]) if pair in rank]
        if not ranks:
            break
        best = min(ranks)
        pair, result, index = merges[best], [], 0
        while index < len(symbols):
            if index + 1 < len(symbols) and (symbols[index], symbols[index + 1]) == pair:
                result.append(256 + best)
                index += 2
            else:
                result.append(symbols[index])
                index += 1
        symbols = result
    return symbols


def sigmoid(value: float) -> float:
    if value >= 0:
        return 1.0 / (1.0 + math.exp(-value))
    exponent = math.exp(value)
    return exponent / (1.0 + exponent)


def summaries(values: list[float]) -> dict[str, float | int]:
    if not values or not all(math.isfinite(value) for value in values):
        raise ValueError("invalid calculated state factors")
    return {"dimensions": len(values), "min": min(values), "median": statistics.median(values),
            "max": max(values), "below0_01": sum(value < 0.01 for value in values),
            "below0_1": sum(value < 0.1 for value in values)}


def probe(config: dict[str, int], matrices: dict[str, tuple[float, ...]],
          ids: list[int], pieces: list[bytes]) -> dict:
    dim = config["dim"]
    retention, state_zero, state_one = [1.0] * dim, [0.0] * dim, [1.0] * dim
    rows = []
    for token in ids:
        embedding = matrices["tok"][token * dim:(token + 1) * dim]
        scale = 1.0 / math.sqrt(sum(value * value for value in embedding) / dim + 1e-5)
        normalized = [embedding[i] * scale * matrices["l0.n1"][i] for i in range(dim)]
        gate = [sigmoid(sum(normalized[i] * matrices["l0.wz"][i * dim + j]
                            for i in range(dim)) + matrices["l0.bz"][j]) for j in range(dim)]
        candidate = [sum(normalized[i] * matrices["l0.wc"][i * dim + j]
                         for i in range(dim)) for j in range(dim)]
        for j in range(dim):
            keep = 1.0 - gate[j]
            retention[j] *= keep
            state_zero[j] = keep * state_zero[j] + gate[j] * candidate[j]
            state_one[j] = keep * state_one[j] + gate[j] * candidate[j]
        rows.append({"token": token, "piece": pieces[token].decode("utf-8"), "gateZ": summaries(gate)})
    # Independent starting states demonstrate the stated affine identity for this
    # local path; they are not task examples or whole-model hidden states.
    error = max(abs((state_one[j] - state_zero[j]) - retention[j]) for j in range(dim))
    if error > 1e-12:
        raise ArithmeticError("local affine state-difference identity failed")
    return {"tokens": ids, "tokenGates": rows, "directPathRetention": summaries(retention),
            "perDimensionRetention": retention, "affineIdentityMaxAbsError": error}


def main() -> None:
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("--baseline", type=Path, required=True)
    parser.add_argument("--tokenizer", type=Path, required=True)
    args = parser.parse_args()
    baseline = verified_read(args.baseline, BASE_SHA, 64 * 1024 * 1024)
    vocabulary = verified_read(args.tokenizer, TOKENIZER_SHA, 100_000)
    config, matrices = checkpoint(baseline)
    merges, pieces = tokenizer(vocabulary)
    # For this fixed literal, the original PRETOKEN regex yields exactly these
    # three segments. No general-purpose replacement tokenizer is introduced.
    suffix = [token for segment in ("\n", "Aktion", ":") for token in encode_segment(segment, merges)]
    newline = encode_segment("\n", merges)
    if suffix != [10, 65, 107, 289, 58] or b"".join(pieces[token] for token in suffix) != b"\nAktion:":
        raise ValueError("unexpected registered suffix tokenization")
    result = {
        "scope": "ORIGINAL_LAYER0_DIRECT_STATE_PATH_ONLY",
        "referenceSourceCommit": SOURCE_COMMIT,
        "baselineSha256": sha(baseline), "tokenizerSha256": sha(vocabulary),
        "scriptSha256": sha(Path(__file__).read_bytes()), "config": config,
        "arithmetic": "Python float64 after decoding original float32 weights; not bit-exact Kotlin execution",
        "formula": "delta_h_end[j] = delta_h_start[j] * product_t(1 - z_t[j]) for identical layer-0 suffix inputs",
        "v1TrainingCheckpointRead": False, "taskDataRead": False, "trainingPerformed": False,
        "suffix": probe(config, matrices, suffix, pieces),
        "singleNewlineReference": probe(config, matrices, newline, pieces),
        "limits": ["Not a full-network gradient or Jacobian measurement",
                   "Upper-layer state paths and learned v1/v2 updates are not evaluated",
                   "The single-newline reference is not a trained ablation or a change to any registered study"],
    }
    print(json.dumps(result, ensure_ascii=False, indent=2, allow_nan=False))


if __name__ == "__main__":
    main()
