#!/usr/bin/env python3
"""Read only the published capacity/probe logs; never load or execute a model.

This is a bounded historical diagnosis, not a new quality test or gate approval.
It reads no training text, development text, holdout text, or checkpoint binary.
The report goes to stdout so the caller chooses a new evidence destination.
"""
from __future__ import annotations

import hashlib
import json
from pathlib import Path


ROOT = Path(__file__).resolve().parents[3]
EXPERIMENT = Path("experimental/weight-space-20261104")
SOURCE_COMMIT = "30cd3a89810ed14fa84eca95236d3177e016118d"
TOKENIZER = "94375c73d1f6e2d04b790738580143fa8c1b9b456d40fa7eb6b4009877bd892f"
DRIVER = "8f2f49bda56ce1f63e86fecf262229acff3d6f5d1ada1d97270b6c1731cda9be"
JAR = "1b6d22ee57977a6e27ec2d0b72e57fab3a35fb8e694905c1270a4ffe87a2f9ba"
PROMPT = "9c7bfc2a17f395c0219273f50652181d825c4712715339083daa71717e6ff495"
SOURCE_HASHES = {
    "app/src/main/java/at/heinz/deviceai/learning/lm/LmRuntime.kt":
        "210606a253650d2268e642ab0edc876aedc90eba2adddc1ac7b8c35eb013e0c2",
    "app/src/main/java/at/heinz/deviceai/learning/lm/LmModel.kt":
        "abb4b50cc443344aefe720844aad4a9ca3e3d967973cc31a4e45ca07b732f099",
    "app/src/main/java/at/heinz/deviceai/learning/lm/LmTensor.kt":
        "a5497f18d09be50f7afbd201d54d97f012f7508f8c53c834ff6c10ef278290ab",
    "experimental/weight-space-20261104/LmWeightExperiment.kt":
        "5af9ba78209873de3588a3f18ab0e2e6e5d76d8bc85652c1b7fc82651bb0ff68",
    "experimental/weight-space-20261104/LmCapacityBenchmark.kt":
        "d88541b436089b6df04f50035cffa79378b939dabbf03ef77a9ca1acd52bbd9c",
}


def digest(data: bytes) -> str:
    return hashlib.sha256(data).hexdigest()


def require(condition: bool, message: str) -> None:
    if not condition:
        raise ValueError(message)


def analyze() -> dict:
    evidence: dict[str, str] = {}

    def read(path: Path) -> bytes:
        absolute = ROOT / path
        require(not absolute.is_symlink() and absolute.is_file(), f"regular file required: {path}")
        raw = absolute.read_bytes()
        require(0 < len(raw) <= 1_000_000, f"bounded nonempty evidence required: {path}")
        evidence[str(path)] = digest(raw)
        return raw

    def rows(path: Path, profile: str) -> tuple[list[tuple[str, dict[str, str]]], dict]:
        raw = read(path)
        receipt = json.loads(read(path.with_suffix(".receipt.json")))
        require(receipt["logSha256"] == digest(raw), f"log hash mismatch: {path}")
        require(receipt["timeout"] is False and receipt["identityValidationFailure"] is None,
                f"invalid execution identity: {path}")
        require(receipt["orchestratorSha256"] == DRIVER, f"wrong driver: {path}")
        require(receipt["runtimeIdentity"]["jarSha256"] == JAR, f"wrong JAR: {path}")
        require(receipt["runtimeIdentity"]["profileId"] == profile, f"wrong profile: {path}")
        require(receipt["inputHashes"]["tokenizerSha256"] == TOKENIZER, f"wrong tokenizer: {path}")
        parsed = []
        for line in raw.decode("utf-8").splitlines():
            kind, *fields = line.split("\t")
            pairs = [field.split("=", 1) for field in fields]
            require(all(len(pair) == 2 for pair in pairs), f"malformed row: {path}")
            require(len({pair[0] for pair in pairs}) == len(pairs), f"duplicate field: {path}")
            parsed.append((kind, dict(pairs)))
        return parsed, receipt

    def select(parsed: list, kind: str) -> list[dict[str, str]]:
        return [fields for name, fields in parsed if name == kind]

    def one(parsed: list, kind: str) -> dict[str, str]:
        selected = select(parsed, kind)
        require(len(selected) == 1, f"exactly one {kind} row required")
        return selected[0]

    for path, expected in SOURCE_HASHES.items():
        require(digest(read(Path(path))) == expected, f"reviewed source changed: {path}")

    profiles = {}
    transitions = []
    for profile, initial in (("core-6562304", "width-8"), ("core-9712128", "depth-3")):
        folder = EXPERIMENT / "core-6-10m-evidence/run" / profile / "logs"
        candidates = {}
        for label in ("baseline", initial, "continued-small", "continued-expanded"):
            capacity, cap_receipt = rows(folder / f"{label}-capacity.tsv", profile)
            probes, probe_receipt = rows(folder / f"{label}-probes.tsv", profile)
            model = one(capacity, "CAPACITY_MODEL")
            result = one(capacity, "CAPACITY_RESULT")
            checkpoint = cap_receipt["inputHashes"]["checkpointSha256"]
            require(checkpoint == probe_receipt["inputHashes"]["checkpointSha256"] ==
                    model["checkpointSha256"] == result["checkpointSha256"] == one(probes, "SOURCE")["sha256"],
                    f"checkpoint mismatch: {profile}/{label}")
            require(one(capacity, "CAPACITY_PROTOCOL")["promptSha256"] == PROMPT, "capacity prompt changed")
            require(probe_receipt["returncode"] == 0, "instruction probe process failed")
            require(cap_receipt["returncode"] == (0 if result["pass"] == "true" else 1), "capacity exit mismatch")
            trials = select(capacity, "CAPACITY_TRIAL")
            require(len(trials) == 8, "missing capacity trials")
            require({(r["phase"], int(r["trial"]), int(r["requestedNewTokens"])) for r in trials} ==
                    {(phase, trial, requested) for phase, nums in (("warmup", [1]), ("measured", [1, 2, 3]))
                     for trial in nums for requested in (1, 32)}, "wrong capacity trial identities")
            require(all(r["promptTokens"] == "64" and r["promptTruncated"] == "false" for r in trials),
                    "changed or truncated prompt")
            instructions = select(probes, "PROBE")
            require(len(instructions) == 6 and sorted(int(r["index"]) for r in instructions) == list(range(6)),
                    "missing or duplicated instruction probes")
            instructions.sort(key=lambda r: int(r["index"]))
            summarized = []
            for r in instructions:
                require(r["correct"] in ("true", "false"), "invalid exact-match flag")
                require((r["text"].strip() == r["expected"]) == (r["correct"] == "true"), "score mismatch")
                summarized.append({"index": int(r["index"]), "prompt": r["prompt"],
                                   "expected": r["expected"], "stop": r["stop"], "tokens": int(r["tokens"]),
                                   "correct": r["correct"] == "true", "loggedTextSha256": digest(r["text"].encode())})
            require(int(one(probes, "PROBE_RESULT")["correct"]) == sum(r["correct"] for r in summarized),
                    "probe summary mismatch")
            candidates[label] = {
                "checkpointSha256": checkpoint, "parameters": int(model["parameters"]),
                "capacity": {"pass": result["pass"] == "true", "reasons": result["reasons"],
                             "trials": len(trials), "immediateEos": sum(r["stop"] == "EOS" and r["actualNewTokens"] == "0" for r in trials),
                             "completeWorkload": sum(r["stop"] == "MAX_TOKENS" and r["actualNewTokens"] == r["requestedNewTokens"] for r in trials)},
                "instructionProbes": summarized,
                "immediateEosProbeIndices": [r["index"] for r in summarized if r["stop"] == "EOS" and r["tokens"] == 0],
            }
        for before, after in (("baseline", "continued-small"), (initial, "continued-expanded")):
            old, new = candidates[before], candidates[after]
            require(old["capacity"]["pass"] and old["capacity"]["completeWorkload"] == 8,
                    "historical initial capacity observation differs")
            require(not new["capacity"]["pass"] and new["capacity"]["immediateEos"] == 8,
                    "historical trained capacity observation differs")
            require(old["immediateEosProbeIndices"] == [0, 1, 4, 5] and
                    new["immediateEosProbeIndices"] == list(range(6)),
                    "historical instruction transition differs")
            require([(r["prompt"], r["expected"]) for r in old["instructionProbes"]] ==
                    [(r["prompt"], r["expected"]) for r in new["instructionProbes"]], "unmatched instruction prompts")
            transitions.append({"profile": profile, "before": before, "after": after,
                                "alreadyImmediateEos": old["immediateEosProbeIndices"],
                                "afterImmediateEos": new["immediateEosProbeIndices"],
                                "newImmediateEos": sorted(set(new["immediateEosProbeIndices"]) - set(old["immediateEosProbeIndices"]))})
        profiles[profile] = candidates
    return {
        "schemaVersion": 1, "scope": "RESEARCH_SHADOW", "status": "VERIFIED_ARCHIVED_LOG_FINDINGS",
        "sourceCommit": SOURCE_COMMIT, "analyzerSha256": digest(Path(__file__).read_bytes()),
        "newModelProcesses": 0, "newTrainingProcesses": 0, "holdoutFilesRead": 0,
        "checkpointBinariesRead": 0, "archivedExecutionPairsChecked": 16,
        "profiles": profiles, "instructionTransitions": transitions,
        "established": [
            "The original and expanded initial checkpoints already stop immediately on four of six instruction prompts.",
            "All four trained candidates stop immediately on the fixed capacity prompt and on all six instruction prompts.",
            "The small unexpanded control also changes the two previously nonempty instruction outputs to immediate EOS.",
            "The observed failure therefore does not require width expansion.",
        ],
        "notEstablished": ["The training loss or EOS-target fraction caused the observed change.",
                           "Any particular optimizer or runtime defect caused the observed change.",
                           "The models stop immediately on every possible prompt.",
                           "EOS margins or probabilities before and after training; these are absent from the archived logs."],
        "evidenceSha256": evidence,
    }


if __name__ == "__main__":
    print(json.dumps(analyze(), ensure_ascii=False, indent=2))
