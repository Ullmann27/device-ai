#!/usr/bin/env python3
"""Read-only LM1C/LMBP preflight; Python standard library, no model execution.

Format source: LmCheckpointStore.kt, LmModel.kt and LmBpeTokenizer.kt at
30cd3a89810ed14fa84eca95236d3177e016118d. PASS covers checkpoint structure only.
Exit codes: 0 PASS, 2 FAIL, 3 UNAVAILABLE. Missing files never become a PASS.
"""
from __future__ import annotations

import argparse
import hashlib
import json
import math
import os
from pathlib import Path
import struct

CHECKPOINT_LIMIT = 64 * 1024 * 1024
FLOAT_BLOCK = 16_384


class InvalidCheckpoint(ValueError):
    """The supplied bytes violate the LM1 format or requested identity."""


def require(condition: bool, reason: str) -> None:
    if not condition:
        raise InvalidCheckpoint(reason)


def modified_utf8(raw: bytes) -> str:
    """Decode Java DataInputStream.readUTF code units, including surrogate pairs."""
    units = bytearray()
    offset = 0
    while offset < len(raw):
        first = raw[offset]
        offset += 1
        if first < 0x80:
            value = first
        elif first >> 5 == 0b110:
            require(offset < len(raw) and raw[offset] & 0xC0 == 0x80, "invalid modified UTF-8")
            value = ((first & 0x1F) << 6) | (raw[offset] & 0x3F)
            offset += 1
        elif first >> 4 == 0b1110:
            require(offset + 1 < len(raw) and raw[offset] & 0xC0 == 0x80
                    and raw[offset + 1] & 0xC0 == 0x80, "invalid modified UTF-8")
            value = ((first & 0x0F) << 12) | ((raw[offset] & 0x3F) << 6) | (raw[offset + 1] & 0x3F)
            offset += 2
        else:
            raise InvalidCheckpoint("invalid modified UTF-8")
        units.extend(struct.pack(">H", value))
    return units.decode("utf-16-be", errors="surrogatepass")


class BodyReader:
    def __init__(self, stream, size: int):
        self.stream = stream
        self.remaining = size
        self.digest = hashlib.sha256()

    def take(self, size: int) -> bytes:
        require(0 <= size <= self.remaining, "truncated checkpoint body")
        data = self.stream.read(size)
        require(len(data) == size, "truncated checkpoint body")
        self.remaining -= size
        self.digest.update(data)
        return data

    def integer(self) -> int:
        return struct.unpack(">i", self.take(4))[0]

    def utf(self) -> str:
        return modified_utf8(self.take(struct.unpack(">H", self.take(2))[0]))


def tensor_layout(config: dict) -> list[tuple[str, int, int]]:
    arch, vocab, dim, layers, heads, context, mlp = (
        config[key] for key in ("arch", "vocab", "dim", "layers", "heads", "context", "mlp"))
    require(arch in (0, 1), "unsupported architecture")
    require(258 <= vocab <= 8192 and 8 <= dim <= 512 and 1 <= layers <= 12
            and heads >= 1 and dim % heads == 0 and 8 <= context <= 2048
            and 8 <= mlp <= 4096, "invalid model configuration")
    result = [("tok", vocab, dim)]
    if arch == 0:
        result.append(("pos", context, dim))
    for layer in range(layers):
        prefix = f"l{layer}."
        result.append((prefix + "n1", 1, dim))
        if arch == 0:
            result.extend((prefix + name, dim, dim) for name in ("wq", "wk", "wv", "wo"))
        else:
            result.extend([(prefix + "wz", dim, dim), (prefix + "bz", 1, dim)])
            result.extend((prefix + name, dim, dim) for name in ("wc", "wg", "wo"))
        result.extend([(prefix + "n2", 1, dim), (prefix + "w1", dim, mlp),
                       (prefix + "b1", 1, mlp), (prefix + "w2", mlp, dim),
                       (prefix + "b2", 1, dim)])
    return result + [("nf", 1, dim)]


def stat_identity(stat) -> tuple:
    return stat.st_dev, stat.st_ino, stat.st_size, stat.st_mtime_ns, stat.st_ctime_ns


def read_tokenizer(path: Path, expected_sha: str | None = None) -> dict:
    with path.open("rb") as stream:
        before = os.fstat(stream.fileno())
        require(8 <= before.st_size <= 64_008, "tokenizer size outside LMBP limits")
        raw = stream.read(64_009)
        require(stat_identity(before) == stat_identity(os.fstat(stream.fileno()))
                and len(raw) == before.st_size, "tokenizer changed while reading")
    magic, merges = struct.unpack(">2i", raw[:8])
    require(magic == 0x4C4D4250 and 0 <= merges <= 8000, "invalid LMBP header")
    require(len(raw) == 8 + 8 * merges, "truncated or trailing tokenizer bytes")
    for index, (left, right) in enumerate(struct.iter_unpack(">2i", raw[8:])):
        require(0 <= left < 256 + index and 0 <= right < 256 + index,
                f"invalid tokenizer merge reference at {index}")
    sha = hashlib.sha256(raw).hexdigest()
    require(expected_sha is None or sha == expected_sha, "tokenizer SHA-256 mismatch")
    return {"sha256": sha, "bytes": len(raw), "merges": merges, "vocab": 258 + merges}


def validate(checkpoint: Path, tokenizer: Path, expected_parameters: int | None = None,
             expected_checkpoint_sha: str | None = None,
             expected_tokenizer_sha: str | None = None) -> dict:
    token = read_tokenizer(tokenizer, expected_tokenizer_sha)
    with checkpoint.open("rb") as stream:
        before = os.fstat(stream.fileno())
        require(64 < before.st_size <= CHECKPOINT_LIMIT, "checkpoint exceeds size contract or is truncated")
        reader = BodyReader(stream, before.st_size - 32)
        require(reader.integer() == 0x4C4D3143, "invalid LM1C magic")
        require(reader.integer() == 1, "unsupported LM1C version")
        config = {key: reader.integer() for key in
                  ("arch", "vocab", "dim", "layers", "heads", "context", "mlp")}
        layout = tensor_layout(config)
        parameters = sum(rows * columns for _, rows, columns in layout)
        require(parameters * 4 <= reader.remaining, "declared tensors do not fit checkpoint")
        require(expected_parameters is None or parameters == expected_parameters,
                "parameter count differs from requested target")
        require(config["vocab"] == token["vocab"], "tokenizer vocabulary mismatch")
        require(reader.utf() == token["sha256"], "checkpoint tokenizer binding mismatch")
        metadata_count = reader.integer()
        require(0 <= metadata_count <= 64, "invalid metadata count")
        metadata = {}
        for _ in range(metadata_count):
            key, value = reader.utf(), reader.utf()
            metadata[key] = value
        tensors = []
        checked_values = 0
        for name, rows, columns in layout:
            actual = reader.utf(), reader.integer(), reader.integer()
            require(actual == (name, rows, columns), f"tensor name/order/shape mismatch: {name}")
            count = rows * columns
            pending = count
            while pending:
                block_count = min(pending, FLOAT_BLOCK)
                data = reader.take(block_count * 4)
                require(all(math.isfinite(value) for (value,) in struct.iter_unpack(">f", data)),
                        f"nonfinite weights: {name}")
                checked_values += block_count
                pending -= block_count
            tensors.append({"name": name, "rows": rows, "columns": columns, "parameters": count})
        require(reader.remaining == 0, "trailing checkpoint body bytes")
        trailer = stream.read(32)
        require(trailer == reader.digest.digest(), "checkpoint body SHA-256 mismatch")
        whole_digest = reader.digest.copy()
        whole_digest.update(trailer)
        sha = whole_digest.hexdigest()
        require(expected_checkpoint_sha is None or sha == expected_checkpoint_sha,
                "checkpoint SHA-256 mismatch")
        require(not stream.read(1) and stat_identity(before) == stat_identity(os.fstat(stream.fileno())),
                "checkpoint changed while reading")
    return {"checkpointSha256": sha, "checkpointBytes": before.st_size,
            "checkpointBodySha256": trailer.hex(), "checkpointLimitBytes": CHECKPOINT_LIMIT,
            "checkpointHeadroomBytes": CHECKPOINT_LIMIT - before.st_size,
            "architecture": ("TRANSFORMER", "MINGRU")[config["arch"]], "config": config,
            "parameters": checked_values, "parameterBytes": checked_values * 4,
            "tensorCount": len(tensors), "tensors": tensors, "finiteWeights": True,
            "tokenizer": token, "metadata": metadata,
            "optimizerState": "ABSENT_FROM_LM1C_V1",
            "exactOptimizerResume": "UNAVAILABLE",
            "continuation": "WEIGHTS_LOADABLE_FRESH_OPTIMIZER_REQUIRED",
            "trainingMemory": "UNMEASURED", "trainingRuns": 0,
            "quality": "NOT_EVALUATED", "inferenceCapacity": "NOT_MEASURED_BY_PREFLIGHT",
            "androidValidation": "NOT_RUN"}


def main(argv=None) -> int:
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("--checkpoint", required=True, type=Path)
    parser.add_argument("--tokenizer", required=True, type=Path)
    parser.add_argument("--expect-parameters", type=int, default=16_011_776)
    parser.add_argument("--expect-checkpoint-sha256")
    parser.add_argument("--expect-tokenizer-sha256")
    args = parser.parse_args(argv)
    result = {"scope": "CHECKPOINT_PREFLIGHT_ONLY", "status": "FAIL"}
    try:
        result.update(validate(args.checkpoint, args.tokenizer, args.expect_parameters,
                               args.expect_checkpoint_sha256, args.expect_tokenizer_sha256))
        result["status"] = "PASS"
        code = 0
    except (FileNotFoundError, PermissionError) as error:
        result.update(status="UNAVAILABLE", reason=str(error))
        code = 3
    except (InvalidCheckpoint, OSError) as error:
        result["reason"] = str(error)
        code = 2
    print(json.dumps(result, indent=2, ensure_ascii=True, allow_nan=False))
    return code


if __name__ == "__main__":
    raise SystemExit(main())
