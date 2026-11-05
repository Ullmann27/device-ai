#!/usr/bin/env python3
"""Verify the actual published archive without extracting or executing a model.

The binary reader follows the pinned Kotlin checkpoint/tokenizer format. All
metadata in this particular archive must be ASCII; this is not a general Java
modified-UTF decoder. No dataset file or generator is opened.
"""
from __future__ import annotations

import argparse
import hashlib
import json
from pathlib import Path
import struct
import subprocess
import zipfile


HERE = Path(__file__).resolve().parent
REPO = HERE.parents[3]
PIN = "59891cfad6dd015f47c187cc688ea94b0026ad7d"
FREEZE_PIN = "9ceffc26a2159c8dc726c1f13d2882119159c130"
EVIDENCE = "docs/evidence/lm1-training-recovery-20261104/"
EXPERIMENT = "experimental/lm1-training-recovery-20261104/"
PREFIX = "LM1_Training_Research_20261104/"
FORMAT_SOURCES = {
    "LmCheckpointStore.kt": "09bf68c1c765b6ed625d71672a2aa836fedc3f587546e9197d4b21ee0e446b17",
    "LmModel.kt": "abb4b50cc443344aefe720844aad4a9ca3e3d967973cc31a4e45ca07b732f099",
    "LmBpeTokenizer.kt": "fcff707a5a1679244cffbb6583e1594b6084e941c7e918f834d71499029b628e",
}


def require(condition: bool, message: str) -> None:
    if not condition:
        raise RuntimeError(message)


def sha(raw: bytes | memoryview) -> str:
    return hashlib.sha256(raw).hexdigest()


def file_sha(path: Path) -> str:
    value = hashlib.sha256()
    with path.open("rb") as stream:
        for block in iter(lambda: stream.read(1024 * 1024), b""):
            value.update(block)
    return value.hexdigest()


class Reader:
    def __init__(self, raw: bytes | memoryview):
        self.raw = memoryview(raw)
        self.offset = 0

    def take(self, count: int) -> memoryview:
        require(count >= 0 and self.offset + count <= len(self.raw), "truncated binary field")
        value = self.raw[self.offset:self.offset + count]
        self.offset += count
        return value

    def integer(self) -> int:
        return struct.unpack(">i", self.take(4))[0]

    def text(self) -> str:
        length = struct.unpack(">H", self.take(2))[0]
        return self.take(length).tobytes().decode("ascii")


def layout(config: dict) -> list[tuple[str, int, int]]:
    vocab, dim, layers = config["vocab"], config["dim"], config["layers"]
    mlp = config["mlp"]
    result = [("tok", vocab, dim)]
    if config["arch"] == 0:
        result.append(("pos", config["context"], dim))
    for layer in range(layers):
        name = "l" + str(layer)
        result.append((name + ".n1", 1, dim))
        if config["arch"] == 0:
            result.extend((name + "." + weight, dim, dim) for weight in ("wq", "wk", "wv", "wo"))
        else:
            result.extend(((name + ".wz", dim, dim), (name + ".bz", 1, dim)))
            result.extend((name + "." + weight, dim, dim) for weight in ("wc", "wg", "wo"))
        result.extend(((name + ".n2", 1, dim), (name + ".w1", dim, mlp),
                       (name + ".b1", 1, mlp), (name + ".w2", mlp, dim), (name + ".b2", 1, dim)))
    result.append(("nf", 1, dim))
    return result


def checkpoint(raw: bytes, tokenizer_sha: str, vocab_size: int) -> tuple[dict, dict]:
    require(len(raw) > 64, "short checkpoint")
    body = memoryview(raw)[:-32]
    require(hashlib.sha256(body).digest() == raw[-32:], "checkpoint trailer digest mismatch")
    reader = Reader(body)
    require(reader.integer() == 0x4C4D3143 and reader.integer() == 1, "unexpected checkpoint format")
    config = {key: reader.integer() for key in ("arch", "vocab", "dim", "layers", "heads", "context", "mlp")}
    require(config["arch"] in (0, 1) and 258 <= config["vocab"] <= 8192
            and 8 <= config["dim"] <= 512 and 1 <= config["layers"] <= 12
            and config["heads"] >= 1 and config["dim"] % config["heads"] == 0
            and 8 <= config["context"] <= 2048 and 8 <= config["mlp"] <= 4096, "invalid configuration")
    require(config["vocab"] == vocab_size and reader.text() == tokenizer_sha, "tokenizer mismatch")
    meta_count = reader.integer()
    require(0 <= meta_count <= 64, "invalid metadata count")
    meta = {}
    for _ in range(meta_count):
        key, value = reader.text(), reader.text()
        require(key not in meta and len(key) <= 64 and len(value) <= 512, "invalid metadata")
        meta[key] = value
    tensors, values = [], {}
    for name, rows, cols in layout(config):
        require((reader.text(), reader.integer(), reader.integer()) == (name, rows, cols), "parameter layout mismatch")
        data = reader.take(rows * cols * 4)
        require(all((bits & 0x7F800000) != 0x7F800000 for (bits,) in struct.iter_unpack(">I", data)),
                "nonfinite Float32 parameter")
        tensors.append({"name": name, "rows": rows, "cols": cols, "parameters": rows * cols, "sha256": sha(data)})
        values[name] = data
    require(reader.offset == len(body), "unexpected trailing checkpoint data")
    count = sum(value["parameters"] for value in tensors)
    return ({"sha256": sha(raw), "bytes": len(raw), "bodyChecksumVerified": True,
             "config": config, "metadata": meta, "tokenizerSha256": tokenizer_sha,
             "parameterCountRecomputed": count, "parameterBytes": count * 4,
             "allParametersFinite": True, "completeLayoutAndEndOfFileVerified": True, "tensors": tensors}, values)


def audit(repo: Path, archive: Path, tokenizer: Path) -> dict:
    inventory = {}

    def source(path: str, commit: str = PIN) -> bytes:
        raw = subprocess.check_output(["git", "show", commit + ":" + path], cwd=repo)
        inventory[commit + ":" + path] = {"sha256": sha(raw), "bytes": len(raw)}
        return raw

    for name, expected in FORMAT_SOURCES.items():
        path = "app/src/main/java/at/heinz/deviceai/learning/lm/" + name
        raw = source(path)
        require(sha(raw) == expected and raw == source(path, "be4e49600fed1db4c697230bb8e42c150dbfbdaf"),
                "unreviewed binary format source")
    public = json.loads(source(EVIDENCE + "archive-manifest.json"))
    require(archive.is_file() and not archive.is_symlink(), "archive must be a regular file")
    require(archive.stat().st_size == public["bytes"] == 51103436, "archive size mismatch")
    archive_sha = file_sha(archive)
    require(archive_sha == public["sha256"] == "428d62b308e308afd9c971ff4974981e96b9dc8a783d80e3653c226983dcc016",
            "archive digest mismatch")
    freeze_raw = source(EVIDENCE + "final-selection/selection-freeze.json")
    require(freeze_raw == source(EVIDENCE + "final-selection/selection-freeze.json", FREEZE_PIN), "freeze changed")
    freeze = json.loads(freeze_raw)
    registrations = {version: json.loads(source(EXPERIMENT + folder + "registration.json"))
                     for version, folder in (("v1", ""), ("v2", "long-study/"), ("v3", "suffix-study/"))}
    token_raw = tokenizer.read_bytes()
    token_sha = sha(token_raw)
    tokens = Reader(token_raw)
    require(tokens.integer() == 0x4C4D4250, "tokenizer format mismatch")
    merges = tokens.integer()
    require(0 <= merges <= 8000, "tokenizer merge count invalid")
    for index in range(merges):
        left, right = tokens.integer(), tokens.integer()
        require(0 <= left < 256 + index and 0 <= right < 256 + index, "invalid tokenizer merge reference")
    require(tokens.offset == len(token_raw), "tokenizer trailing bytes")
    models, tensors, members = {}, {}, {}
    with zipfile.ZipFile(archive) as package:
        names = package.namelist()
        require(len(names) == len(set(names)) == public["membersVerified"] == 10, "archive membership mismatch")
        manifest_raw = package.read(PREFIX + "MANIFEST.json")
        require(sha(manifest_raw) == public["manifestSha256"], "embedded manifest digest mismatch")
        manifest = json.loads(manifest_raw)
        require(set(names) == {PREFIX + "MANIFEST.json"} | {PREFIX + name for name in manifest["files"]},
                "unlisted archive member")
        require(manifest["scope"] == "RESEARCH_SHADOW_NOT_PROMOTED" and manifest["modelPromotion"] is False,
                "archive research identity changed")
        require(manifest["selectedStudy"] == "v3" and manifest["freezePublishedCommit"] == FREEZE_PIN
                and manifest["freezeSha256"] == sha(freeze_raw) and manifest["sourceCommits"]["v3"] == freeze["sourceCommit"],
                "archive freeze/source identity mismatch")
        require(token_sha == manifest["tokenizerSha256"] == "94375c73d1f6e2d04b790738580143fa8c1b9b456d40fa7eb6b4009877bd892f",
                "actual tokenizer digest mismatch")
        members["MANIFEST.json"] = {"sha256": sha(manifest_raw), "bytes": len(manifest_raw)}
        for name, expected in manifest["files"].items():
            raw = package.read(PREFIX + name)
            require({"sha256": sha(raw), "bytes": len(raw)} == expected, "member identity mismatch: " + name)
            members[name] = expected
            if name.startswith("models/"):
                arm = Path(name).stem
                require(arm in freeze["arms"] and sha(raw) == freeze["arms"][arm]["checkpointSha256"], "model differs from prior freeze")
                model, tensors[arm] = checkpoint(raw, token_sha, 258 + merges)
                metadata = manifest["models"][arm]
                require(metadata["path"] == name and metadata["sha256"] == sha(raw)
                        and metadata["bytes"] == len(raw) and metadata["parameters"] == model["parameterCountRecomputed"]
                        and metadata["selectedStep"] == int(freeze["arms"][arm]["step"]), "model manifest mismatch")
                embedded = model["metadata"]
                require(embedded["adaptSteps"] == str(metadata["selectedStep"])
                        and embedded["parameters"] == str(model["parameterCountRecomputed"])
                        and embedded["schedule"] == freeze["tokenScheduleSha256"]
                        and embedded["adaptData"] == registrations["v3"]["files"][EXPERIMENT + "intent-data/train.tsv"]
                        and embedded["scope"] == "RESEARCH_SHADOW"
                        and embedded["experiment"] == "lm1-intent-suffix-20261104"
                        and embedded["variant"] == ("small" if arm.startswith("S") else "wide"),
                        "embedded adaptation metadata mismatch")
                model["frozenStep"] = metadata["selectedStep"]
                model["adaptationMetadataMatchesFrozenStepScheduleAndTrainingIdentity"] = True
                models[arm] = model
        for version, registration in registrations.items():
            require(members["runtime/" + version + "-experiment.jar"]["sha256"] == registration["jarSha256"], "registered JAR mismatch")
            require(members["runtime/kotlin-stdlib-2.0.20.jar"]["sha256"] == registration["stdlibSha256"], "registered stdlib mismatch")
        require(manifest["finalTest"] == json.loads(source(EVIDENCE + "study-v3/intent-summary.json")), "archived final summary mismatch")
        require(manifest["knownTrainingFit"] == json.loads(source(EVIDENCE + "study-v3/training-fit-summary.json")), "archived train-fit mismatch")
    changes = {}
    for initial, trained in (("S0", "S1"), ("W0", "W1")):
        require(models[initial]["config"] == models[trained]["config"], "paired model config changed")
        changed_slots = sum(sum(a != b for a, b in zip(struct.iter_unpack(">I", tensors[initial][name]),
                                                      struct.iter_unpack(">I", tensors[trained][name]))) for name in tensors[initial])
        require(changed_slots > 0, "checkpoint difference is metadata-only")
        changes[initial + "->" + trained] = {"float32SlotsWithDifferentBits": changed_slots,
                                              "parameterCount": models[trained]["parameterCountRecomputed"],
                                              "metadataOnlyDifference": False}
    require(file_sha(archive) == archive_sha and tokenizer.read_bytes() == token_raw, "input bytes changed during audit")
    return {"schemaVersion": 1, "status": "ACTUAL_ARCHIVE_CHECKPOINT_AND_RUNTIME_IDENTITIES_PASS",
            "reviewedCommit": PIN, "verifierSha256": sha(Path(__file__).read_bytes()),
            "archive": {"path": str(archive), "sha256": archive_sha, "bytes": public["bytes"], "membersVerified": 10,
                        "bytesUnchangedDuringAudit": True, "extractedToDisk": False},
            "tokenizer": {"path": str(tokenizer), "sha256": token_sha, "bytes": len(token_raw), "merges": merges,
                          "vocabulary": 258 + merges, "formatAndReferencesVerified": True, "includedInArchive": False},
            "members": members, "models": models, "weightByteChanges": changes,
            "sourceInventory": inventory, "checkpointBinariesRead": 4, "jarBinariesHashed": 4,
            "tokenizerBinaryRead": True, "actualModelProcesses": 0, "modelInferenceExecuted": False,
            "trainingExecuted": False, "datasetFilesOrGeneratorsRead": False, "modelPromotion": False,
            "interpretation": ["Actual artifact integrity and frozen checkpoint identity verified independently of log claims.",
                               "This is not a new inference, training, quality, Android or model-promotion approval.",
                               "JAR hashes match registered binaries; this is not an independent reproducible-build attestation.",
                               "Tokenizer is present separately and matches every model; it is not included in this ZIP.",
                               "The format reader is restricted to the ASCII metadata in this hash-pinned archive."]}


def main() -> None:
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("--repo", type=Path, default=REPO)
    parser.add_argument("--archive", type=Path, required=True)
    parser.add_argument("--tokenizer", type=Path, required=True)
    parser.add_argument("--output", type=Path, default=HERE / "binary-integrity.json")
    args = parser.parse_args()
    require(args.output.resolve().is_relative_to(HERE), "output must remain in root-verification")
    report = audit(args.repo.resolve(), args.archive.resolve(), args.tokenizer.resolve())
    args.output.write_text(json.dumps(report, indent=2, ensure_ascii=False, allow_nan=False) + "\n")
    print(json.dumps({"status": report["status"], "checkpointBinariesRead": 4, "jarBinariesHashed": 4,
                      "actualModelProcesses": 0, "weightByteChanges": report["weightByteChanges"]}, indent=2))


if __name__ == "__main__":
    main()
