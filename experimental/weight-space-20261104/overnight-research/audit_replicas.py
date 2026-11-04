#!/usr/bin/env python3
"""Read-only, source-bound channel replica measurements."""
from __future__ import annotations

import argparse
import hashlib
import importlib.util
import json
from pathlib import Path
import subprocess
import sys
import zipfile

import numpy as np

HERE = Path(__file__).resolve().parent
REPO = HERE.parents[2]
PIN = "59891cfad6dd015f47c187cc688ea94b0026ad7d"
READER = HERE.parent / "continuation-review/root-verification/audit_binary_archive.py"
READER_SHA = "dca91cb311ac022a3f8f257f8ca61fffc9de41e33fae35a73b8412786b4fbc86"
ARCHIVE_SHA = "428d62b308e308afd9c971ff4974981e96b9dc8a783d80e3653c226983dcc016"
TOKENIZER_SHA = "94375c73d1f6e2d04b790738580143fa8c1b9b456d40fa7eb6b4009877bd892f"
MODEL_SHAS = {
    "S0": "70cb6ed4020042c4ced21bf0c58dab1b83ee2a51655fdbec41279cd961234435",
    "S1": "90b58fb58e048bd4eac967f2b0b131373170201660de7a3d330b393a6c8d820f",
    "W0": "af617cf6d223a3e53b44bc3c0578b6924e9c73d8b92f558a981af22990cad04d",
    "W1": "0f2b5481cf60d6c6021e9e24d1e5ba70d303da274c18933de29c48a47489cf8c",
}
SOURCES = {
    "experimental/weight-space-20261104/LmWeightSurgery.kt": "f1bc63a0b1cd65af53306b96317266347d7763aab858b3e87adb1fe37119c62e",
    "experimental/lm1-training-recovery-20261104/suffix-study/LmIntentSuffixStudy.kt": "2ca026da5ecebb79f9fcbb0d49d39cc6c1485b0d31b19fe10118cb4ff711ef89",
    "app/src/main/java/at/heinz/deviceai/learning/lm/LmModel.kt": "abb4b50cc443344aefe720844aad4a9ca3e3d967973cc31a4e45ca07b732f099",
    "app/src/main/java/at/heinz/deviceai/learning/lm/LmCheckpointStore.kt": "09bf68c1c765b6ed625d71672a2aa836fedc3f587546e9197d4b21ee0e446b17",
}


def sha(raw):
    return hashlib.sha256(raw).hexdigest()


def file_sha(path):
    with Path(path).open("rb") as stream:
        return hashlib.file_digest(stream, "sha256").hexdigest()


def replica_metrics(values, base_cols):
    values = np.asarray(values)
    if values.ndim != 2 or base_cols < 1 or values.shape[1] % base_cols or not np.isfinite(values).all():
        raise ValueError("invalid replica matrix")
    replicas = values.shape[1] // base_cols
    if replicas < 1 or values.shape[0] < 1:
        raise ValueError("empty replica matrix")
    groups = np.ascontiguousarray(values).reshape(values.shape[0], replicas, base_cols)
    bit_groups = groups.view(f"u{groups.dtype.itemsize}")
    tied = np.all(bit_groups == bit_groups[:, :1, :], axis=1)
    measured = groups.astype(np.float64)
    means = measured.mean(axis=1, keepdims=True)
    rms = float(np.sqrt(np.mean(measured ** 2)))
    spread = float(np.sqrt(np.mean((measured - means) ** 2)))
    return {
        "replicas": replicas,
        "groups": int(tied.size),
        "exactlyTiedGroups": int(tied.sum()),
        "groupsWithDifferentBits": int((~tied).sum()),
        "spreadRms": spread,
        "valueRms": rms,
        "relativeSpreadRms": spread / rms if rms else 0.,
        "maximumReplicaRange": float(np.max(np.ptp(measured, axis=1))),
    }


def collapse_input_replicas(values, base_rows, base_cols):
    values = np.asarray(values)
    if (values.ndim != 2 or min(base_rows, base_cols) < 1
            or values.shape[0] % base_rows or values.shape[1] % base_cols
            or values.shape[0] // base_rows != values.shape[1] // base_cols):
        raise ValueError("inconsistent input/output replication")
    factor = values.shape[0] // base_rows
    return values.reshape(factor, base_rows, factor, base_cols).sum(axis=0, dtype=np.float64).reshape(base_rows, -1)


def audit(archive, tokenizer):
    if file_sha(READER) != READER_SHA:
        raise ValueError("binary reader identity changed")
    spec = importlib.util.spec_from_file_location("pinned_binary_reader", READER)
    reader = importlib.util.module_from_spec(spec)
    spec.loader.exec_module(reader)
    for path, expected in SOURCES.items():
        raw = subprocess.check_output(["git", "show", f"{PIN}:{path}"], cwd=REPO, timeout=30)
        if sha(raw) != expected:
            raise ValueError("source identity changed: " + path)
    if file_sha(archive) != ARCHIVE_SHA or file_sha(tokenizer) != TOKENIZER_SHA:
        raise ValueError("archive/tokenizer identity changed")
    reports, arrays = {}, {}
    with zipfile.ZipFile(archive) as package:
        for arm, expected in MODEL_SHAS.items():
            raw = package.read(f"LM1_Training_Research_20261104/models/{arm}.lm1")
            if sha(raw) != expected:
                raise ValueError("checkpoint identity changed: " + arm)
            model, values = reader.checkpoint(raw, TOKENIZER_SHA, 512)
            reports[arm] = model
            arrays[arm] = {row["name"]: np.frombuffer(values[row["name"]], dtype=">f4").astype(np.float32).reshape(row["rows"], row["cols"])
                           for row in model["tensors"]}
    small = arrays["S0"]
    measurements = {}
    for arm in ("W0", "W1"):
        tensors = {}
        for name, values in arrays[arm].items():
            source = small[name]
            row = {"ordinaryReplicaSpread": replica_metrics(values, source.shape[1])}
            if name.split(".")[-1] in {"wz", "wc", "wg", "wo", "w1", "w2"}:
                collapsed = collapse_input_replicas(values, *source.shape)
                row["equalInputReplicaAction"] = replica_metrics(collapsed, source.shape[1])
                reference = np.tile(source.astype(np.float64), (1, 8))
                row["equalInputActionDeltaFromS0MaxAbs"] = float(np.max(np.abs(collapsed - reference)))
            else:
                reference = np.tile(source.astype(np.float64), (1, 8)) / (8 if name == "nf" else 1)
                row["tiledS0DeltaMaxAbs"] = float(np.max(np.abs(values.astype(np.float64) - reference)))
            tensors[name] = row
        measurements[arm] = tensors
    if file_sha(archive) != ARCHIVE_SHA or file_sha(tokenizer) != TOKENIZER_SHA:
        raise ValueError("input changed during audit")
    return {
        "schemaVersion": 1,
        "status": "MEASURED_NOT_A_QUALITY_GATE",
        "sourceCommit": PIN,
        "sourceHashes": SOURCES,
        "implementationSha256": file_sha(Path(__file__)),
        "readerSha256": READER_SHA,
        "protocolSha256": file_sha(HERE / "PROTOCOL.md"),
        "probesSha256": file_sha(HERE / "probes.json"),
        "archiveSha256": ARCHIVE_SHA,
        "tokenizerSha256": TOKENIZER_SHA,
        "pythonVersion": sys.version,
        "numpyVersion": np.__version__,
        "models": {arm: {key: row[key] for key in ("sha256", "bytes", "parameterCountRecomputed", "parameterBytes", "config", "allParametersFinite", "bodyChecksumVerified")}
                   for arm, row in reports.items()},
        "measurements": measurements,
        "inputsUnchanged": True,
        "trainingUpdates": 0,
        "datasetReads": 0,
        "effectiveParameterCountClaimed": False,
        "qualityImprovementClaimed": False,
    }


def main():
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("--archive", required=True, type=Path)
    parser.add_argument("--tokenizer", required=True, type=Path)
    parser.add_argument("--output", required=True, type=Path)
    args = parser.parse_args()
    if args.output.exists() or args.output.is_symlink():
        raise FileExistsError("output already exists")
    result = audit(args.archive, args.tokenizer)
    with args.output.open("x") as stream:
        json.dump(result, stream, indent=2, allow_nan=False)
        stream.write("\n")
    print(json.dumps({"output": str(args.output), "status": result["status"], "sha256": file_sha(args.output)}))


if __name__ == "__main__":
    main()
