#!/usr/bin/env python3
"""Independently audit completed registered LM1 profiles without running models.

This reader follows the versioned LM1C checkpoint layout and raw TSV records.
It deliberately does not import the experiment driver or use its gate helpers.
Both final results must already exist before any evaluation data is inspected.
"""
from __future__ import annotations

import argparse
import hashlib
import json
import math
from pathlib import Path
import struct

import numpy as np


TOKENIZER_SHA = "94375c73d1f6e2d04b790738580143fa8c1b9b456d40fa7eb6b4009877bd892f"
PROFILES = {"core-6562304": 6_562_304, "core-9712128": 9_712_128}
PROFILE_FIELDS = ("profileId", "profileSha256", "studyPlanSha256", "studyRegistrationSha256")


def check(condition: bool, message: str) -> None:
    if not condition:
        raise ValueError(message)


def digest(path: Path) -> str:
    with path.open("rb") as stream:
        return hashlib.file_digest(stream, "sha256").hexdigest()


def read_json(path: Path) -> dict:
    return json.loads(path.read_text(encoding="utf-8"))


def read_rows(path: Path) -> list[dict]:
    result = []
    for line in path.read_text(encoding="utf-8").splitlines():
        fields = line.split("\t")
        if len(fields) > 1 and all("=" in item for item in fields[1:]):
            result.append({"kind": fields[0], **dict(item.split("=", 1) for item in fields[1:])})
    return result


def one(rows: list[dict], kind: str) -> dict:
    selected = [row for row in rows if row["kind"] == kind]
    check(len(selected) == 1, f"expected exactly one {kind}")
    return selected[0]


def load_checkpoint(path: Path) -> dict:
    raw = path.read_bytes()
    check(64 < len(raw) <= 64 * 1024 * 1024, f"checkpoint size: {path}")
    body = raw[:-32]
    check(hashlib.sha256(body).digest() == raw[-32:], f"body checksum: {path}")
    magic, version, arch, vocab, dim, layers, heads, context, mlp = struct.unpack_from(">9i", body)
    check((magic, version, arch) == (0x4C4D3143, 1, 1), f"LM1C minGRU header: {path}")
    check(258 <= vocab <= 8192 and 8 <= dim <= 512 and 1 <= layers <= 12
          and heads >= 1 and dim % heads == 0 and 8 <= context <= 2048
          and 8 <= mlp <= 4096, f"configuration: {path}")
    offset = 36

    def utf_bytes() -> bytes:
        nonlocal offset
        size = struct.unpack_from(">H", body, offset)[0]
        offset += 2
        value = body[offset:offset + size]
        check(len(value) == size, "truncated checkpoint string")
        offset += size
        return value

    tokenizer = utf_bytes().decode("ascii")
    check(tokenizer == TOKENIZER_SHA, f"tokenizer binding: {path}")
    meta_count = struct.unpack_from(">i", body, offset)[0]
    offset += 4
    check(0 <= meta_count <= 64, "metadata count")
    metadata = {}
    for _ in range(meta_count):
        key, value = utf_bytes(), utf_bytes()
        # Only protocol-owned ASCII metadata is used below. Other values are
        # skipped as opaque Java modified-UTF bytes rather than mis-decoded.
        if key.isascii() and value.isascii():
            metadata[key.decode("ascii")] = value.decode("ascii")
    layout = [("tok", vocab, dim)]
    for layer in range(layers):
        prefix = f"l{layer}."
        layout.extend([(prefix + "n1", 1, dim), (prefix + "wz", dim, dim),
                       (prefix + "bz", 1, dim)])
        layout.extend((prefix + name, dim, dim) for name in ("wc", "wg", "wo"))
        layout.extend([(prefix + "n2", 1, dim), (prefix + "w1", dim, mlp),
                       (prefix + "b1", 1, mlp), (prefix + "w2", mlp, dim),
                       (prefix + "b2", 1, dim)])
    layout.append(("nf", 1, dim))
    matrices = {}
    for name, rows, columns in layout:
        actual_name = utf_bytes().decode("ascii")
        actual_rows, actual_columns = struct.unpack_from(">2i", body, offset)
        offset += 8
        check((actual_name, actual_rows, actual_columns) == (name, rows, columns),
              f"matrix layout {name}: {path}")
        values = np.frombuffer(body, dtype=">f4", count=rows * columns, offset=offset)
        check(bool(np.isfinite(values).all()), f"nonfinite weights {name}: {path}")
        matrices[name] = values.reshape(rows, columns)
        offset += rows * columns * 4
    check(offset == len(body), f"trailing checkpoint bytes: {path}")
    count = sum(matrix.size for matrix in matrices.values())
    return {"sha256": hashlib.sha256(raw).hexdigest(), "bytes": len(raw), "parameters": count,
            "dim": dim, "layers": layers, "tokenizerSha256": tokenizer,
            "metadata": metadata, "matrices": matrices}


def audit_score(logs: Path, candidate: dict, suffix: str, expected_dataset: str) -> dict:
    path = logs / f"{candidate['label']}-{suffix}.tsv"
    receipt = read_json(logs / f"{candidate['label']}-{suffix}.receipt.json")
    check(receipt["returncode"] == 0 and receipt["timeout"] is False
          and receipt["logSha256"] == digest(path), "completed source-bound evaluation receipt")
    rows = read_rows(path)
    source, result = one(rows, "SOURCE"), one(rows, "EVALUATION")
    check(source["sha256"] == candidate["sha256"], "evaluation checkpoint mismatch")
    check(result["datasetSha256"] == expected_dataset and result["tokenizerSha256"] == TOKENIZER_SHA,
          "evaluation data mismatch")
    items = [row for row in rows if row["kind"] == "ITEM"]
    check([int(row["index"]) for row in items] == list(range(len(items))), "evaluation item order")
    check(len(items) == int(result["segments"]), "evaluation segment count")
    bits, size = sum(float(row["bits"]) for row in items), sum(int(row["bytes"]) for row in items)
    check(size > 0 and size == int(result["bytes"]) and math.isfinite(bits)
          and math.isclose(bits, float(result["bits"]), rel_tol=0, abs_tol=1e-8), "evaluation sum")
    check(math.isclose(bits / size, float(result["bpb"]), rel_tol=0, abs_tol=1e-10), "evaluation BPB")
    check(int(result["parameters"]) == candidate["parameters"], "evaluation parameter count")
    return {"bpb": float(result["bpb"]), "bytes": size, "segments": len(items),
            "tokens": int(result["tokens"])}


def audit_capacity(logs: Path, candidate: dict) -> dict:
    rows = read_rows(logs / f"{candidate['label']}-capacity.tsv")
    receipt = read_json(logs / f"{candidate['label']}-capacity.receipt.json")
    result, protocol, model = one(rows, "CAPACITY_RESULT"), one(rows, "CAPACITY_PROTOCOL"), one(rows, "CAPACITY_MODEL")
    check(result["checkpointSha256"] == candidate["sha256"]
          and model["checkpointSha256"] == candidate["sha256"], "capacity checkpoint mismatch")
    check(model["tokenizerSha256"] == TOKENIZER_SHA and int(model["parameters"]) == candidate["parameters"]
          and int(model["parameterBytes"]) == candidate["parameters"] * 4
          and int(model["checkpointBytes"]) == candidate["bytes"], "capacity model layout mismatch")
    for key, expected in {"firstTokenLimitMs": 1500, "generation32LimitMs": 5000,
                          "vmHwmLimitKiB": 786432, "requestMaxMillis": 30000,
                          "warmups": 1, "measuredRepeats": 3}.items():
        check(float(protocol[key]) == expected, f"capacity protocol {key}")
    check(protocol["promptSha256"] == "9c7bfc2a17f395c0219273f50652181d825c4712715339083daa71717e6ff495",
          "capacity prompt mismatch")
    trials = [row for row in rows if row["kind"] == "CAPACITY_TRIAL"]
    measured = [row for row in trials if row["phase"] == "measured"]
    expected_keys = {(phase, index, n) for phase, repeats in (("warmup", 1), ("measured", 3))
                     for index in range(1, repeats + 1) for n in (1, 32)}
    actual_keys = [(row["phase"], int(row["trial"]), int(row["requestedNewTokens"])) for row in trials]
    workload = len(actual_keys) == 8 and set(actual_keys) == expected_keys
    workload = workload and all(row["stop"] in {"MAX_TOKENS", "EOS"}
                               and row["promptTruncated"] == "false" and int(row["promptTokens"]) == 64
                               and math.isfinite(float(row["elapsedMs"])) and float(row["elapsedMs"]) >= 0
                               and 0 <= int(row["actualNewTokens"]) <= int(row["requestedNewTokens"])
                               and (row["stop"] != "MAX_TOKENS"
                                    or row["actualNewTokens"] == row["requestedNewTokens"])
                               for row in trials)
    workload = workload and len(measured) == 6 and all(
        int(row["actualNewTokens"]) == int(row["requestedNewTokens"])
        and float(row["elapsedMs"]) <= (1500 if row["requestedNewTokens"] == "1" else 5000)
        for row in measured)
    for requested, key in ((1, "maxFirstTokenCallMs"), (32, "maxGeneration32CallMs")):
        observed = [float(row["elapsedMs"]) for row in measured if int(row["requestedNewTokens"]) == requested]
        reported = float(result[key])
        check(bool(observed) and math.isfinite(reported) and reported == max(observed),
              f"capacity latency summary differs from measured trials: {key}")
    memory = [int(row["vmHwmKiB"]) for row in rows if row["kind"] == "CAPACITY_MEMORY"]
    check(bool(memory), "missing memory observations")
    peak = max(*memory, int(result["vmHwmKiB"]))
    eligible = (receipt["returncode"] == 0 and receipt["timeout"] is False
                and math.isfinite(float(receipt["wallSeconds"])) and 0 <= receipt["wallSeconds"] <= 120
                and result["pass"] == "true" and model["fullLayoutLoaded"] == "true"
                and model["finiteWeights"] == "true" and workload and min(memory) >= 0 and peak <= 786432)
    check(eligible is candidate["capacityEligible"], "saved capacity eligibility differs from raw workload")
    return {"eligible": eligible, "peakVmHwmKiB": peak, "reasons": result["reasons"],
            "maxFirstTokenCallMs": float(result["maxFirstTokenCallMs"]),
            "maxGeneration32CallMs": float(result["maxGeneration32CallMs"]),
            "actualMeasuredTokenCounts": [int(row["actualNewTokens"]) for row in measured]}


def audit_profile(directory: Path, expected_parameters: int, driver_sha: str) -> dict:
    environment = read_json(directory / "environment.json")
    frozen = read_json(directory / "frozen-selection.json")
    final = read_json(directory / "final.json")
    check(environment["orchestratorSha256"] == driver_sha, "study source changed")
    check(environment["profileId"] == directory.name, "profile directory identity")
    check(final["selectionSha256"] == digest(directory / "frozen-selection.json"), "final selection digest")
    check(final["studySelectionSha256"] == digest(directory.parent / "study-frozen-selection.json"),
          "final shared selection digest")
    check(final["holdoutSha256"] == frozen["holdoutSha256"] == environment["holdoutSha256"], "final holdout identity")
    check(frozen["lastCapacityPassed"]["parameters"] == expected_parameters, "registered target not reached")
    check(frozen["holdoutOpened"] is False, "selection was not frozen before holdout")
    logs = directory / "logs"
    receipt_count = 0
    for path in sorted(logs.glob("*.receipt.json")):
        receipt = read_json(path)
        log = path.with_name(path.name.removesuffix(".receipt.json") + ".tsv")
        check(receipt["logSha256"] == digest(log), f"receipt log checksum: {path.name}")
        check(receipt["orchestratorSha256"] == driver_sha, f"receipt source: {path.name}")
        check(not receipt.get("identityValidationFailure"), f"receipt input failure: {path.name}")
        check(receipt["runtimeIdentity"]["jarSha256"] == environment["jarSha256"], "runtime JAR identity")
        check(receipt["runtimeIdentity"]["protocolSha256"] == environment["protocolSha256"], "runtime protocol identity")
        check(all(receipt["runtimeIdentity"][key] == environment[key] for key in PROFILE_FIELDS),
              "runtime profile/plan/study identity")
        command, arguments = receipt["command"], receipt["arguments"]
        benchmark = command[5].endswith(".LmCapacityBenchmark")
        check(command[5] == "at.heinz.deviceai.learning.lm." +
              ("LmCapacityBenchmark" if benchmark else "LmWeightExperiment"), "exact JVM entrypoint")
        check(command[:4] == ["java", "-Xms64m", "-Xmx1g" if benchmark else "-Xmx3g", "-cp"]
              and command[6:] == arguments and digest(Path(command[4])) == environment["jarSha256"],
              "invocation program/heap/arguments")
        check(receipt["timeoutSeconds"] == (120 if benchmark else 600 if arguments[0] == "train" else 180),
              "invocation timeout contract")
        if not benchmark and arguments[0] == "corpus":
            documents = Path(arguments[1])
            corpus_digest = hashlib.sha256()
            for document in sorted(documents.glob("*.md")):
                corpus_digest.update(document.name.encode("utf-8") + b"\0")
                corpus_digest.update(digest(document).encode("ascii") + b"\0")
            identities = {"corpusManifestSha256": digest(documents / "paths.tsv"),
                          "corpusDocumentsSha256": corpus_digest.hexdigest()}
        else:
            offset = 0 if benchmark else 1
            identities = {"checkpointSha256": digest(Path(arguments[offset])),
                          "tokenizerSha256": digest(Path(arguments[offset + 1]))}
            if not benchmark and arguments[0] in {"train", "evaluate"}:
                identities["datasetSha256"] = digest(Path(arguments[3]))
            if not benchmark and arguments[0] == "parity":
                identities["comparisonCheckpointSha256"] = digest(Path(arguments[3]))
        check(receipt["inputHashes"] == identities, f"actual invocation input identities: {path.name}")
        receipt_count += 1
    loaded = {}

    def model(path: str) -> dict:
        if path not in loaded:
            loaded[path] = load_checkpoint(Path(path))
        return loaded[path]

    checkpoint_count = 0
    for path in sorted((directory / "checkpoints").glob("*.lm1")):
        item = load_checkpoint(path)
        check(item["sha256"] == path.stem, f"content addressed checkpoint: {path}")
        checkpoint_count += 1
    candidates = []
    final_by_sha = {candidate["sha256"]: candidate for candidate in final["results"]}
    check(len(final_by_sha) == len(final["results"]) == len(frozen["candidates"]), "final candidate set")
    for candidate in frozen["candidates"]:
        item = model(candidate["path"])
        check(all(item[key] == candidate[key] for key in ("sha256", "parameters", "bytes")), "candidate checkpoint")
        development = audit_score(logs, candidate, "dev", environment["developmentSha256"])
        holdout = audit_score(logs, candidate, "holdout", environment["holdoutSha256"])
        check(development["bpb"] == candidate["devBpb"], "frozen development score")
        reported = final_by_sha[candidate["sha256"]]
        check(reported["holdoutBpb"] == holdout["bpb"]
              and float(reported["holdout"]["bpb"]) == holdout["bpb"], "final holdout score")
        entry = {key: candidate[key] for key in ("label", "sha256", "parameters", "bytes")}
        entry.update(development=development, holdout=holdout, capacity=audit_capacity(logs, candidate))
        candidates.append(entry)
    eligible = [candidate for candidate in candidates if candidate["capacity"]["eligible"]]
    check(bool(eligible), "no eligible candidate")
    minimum = min(candidate["development"]["bpb"] for candidate in eligible)
    preferred = min((candidate for candidate in eligible if candidate["development"]["bpb"] <= minimum + 0.00001),
                    key=lambda candidate: (candidate["parameters"], candidate["development"]["bpb"]))
    check(preferred["sha256"] == frozen["developmentPreferred"]["sha256"], "independent selection rule")
    training = []
    selected_small = read_json(directory / "calibration.json")["selected"]
    frozen_by_label = {candidate["label"]: candidate for candidate in frozen["candidates"]}
    for label in ("continued-small", "continued-expanded"):
        rows = read_rows(logs / f"{label}-train.tsv")
        receipt = read_json(logs / f"{label}-train.receipt.json")
        check(receipt["returncode"] == 0 and receipt["timeout"] is False, "training did not complete")
        check(receipt["timeoutSeconds"] == 600 and receipt["wallSeconds"] <= 600, "training resource contract")
        source, target, result, inputs = (one(rows, kind) for kind in ("SOURCE", "CHECKPOINT", "TRAIN_RESULT", "TRAIN_INPUT"))
        before, after = model(receipt["arguments"][1]), model(target["path"])
        check(before["sha256"] == source["sha256"] and after["sha256"] == target["sha256"], "training checkpoint lineage")
        expected_parent = selected_small if label == "continued-small" else frozen["lastCapacityPassed"]
        expected_child = frozen_by_label[label]
        check(before["sha256"] == expected_parent["sha256"]
              and Path(receipt["arguments"][1]).resolve() == Path(expected_parent["path"]).resolve()
              and before["parameters"] == (132160 if label == "continued-small" else expected_parameters),
              "training source is not its registered small/expanded target")
        check(all(after[key] == expected_child[key] for key in ("sha256", "parameters", "bytes"))
              and Path(target["path"]).resolve() == Path(expected_child["path"]).resolve(),
              "training output is not its frozen candidate")
        check(before["parameters"] == after["parameters"], "training changed parameter count")
        changed = sum(int(np.count_nonzero(before["matrices"][name].view(">u4") != matrix.view(">u4")))
                      for name, matrix in after["matrices"].items())
        check(changed > 0 and changed == int(result["changedWeights"]), "training weight-change count")
        steps = [row for row in rows if row["kind"] == "TRAIN_STEP"]
        check([int(row["step"]) for row in steps] == list(range(1, 17))
              and all(math.isfinite(float(row["nll"])) for row in steps), "training step sequence")
        check(int(result["steps"]) == 16 and int(result["tokens"]) == 3255
              and int(steps[-1]["tokens"]) == 3255, "matched training token budget")
        check(after["metadata"]["adaptSteps"] == "16" and after["metadata"]["adaptTokens"] == "3255"
              and after["metadata"]["adaptData"] == inputs["datasetSha256"]
              and after["metadata"]["schedule"] == inputs["tokenScheduleSha256"], "checkpoint training metadata")
        training.append({"label": label, "parameters": after["parameters"], "changedWeights": changed,
                         "checkpointSha256": after["sha256"], "steps": 16, "tokens": 3255,
                         "millis": int(result["millis"]), "wallSeconds": receipt["wallSeconds"],
                         "inputIdentity": {key: inputs[key] for key in
                                           ("datasetSha256", "tokenScheduleSha256", "tokenizerSha256")}})
    check(training[0]["inputIdentity"] == training[1]["inputIdentity"], "training pair input mismatch")
    return {"profile": directory.name, "registeredParameters": expected_parameters,
            "receiptCount": receipt_count, "checkpointsChecked": checkpoint_count,
            "preferredCandidate": preferred["label"], "candidates": candidates, "training": training,
            "frozenSelectionSha256": digest(directory / "frozen-selection.json"),
            "finalSha256": digest(directory / "final.json")}


def main() -> None:
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("--study", type=Path, required=True)
    parser.add_argument("--historical-root", type=Path, required=True,
                        help="Workspace root containing the unchanged historical results and inputs")
    parser.add_argument("--output", type=Path, required=True)
    args = parser.parse_args()
    source = Path(__file__).resolve().parent.parent
    check(all((args.study / profile / "final.json").is_file() for profile in PROFILES),
          "both profile final results must exist before this audit")
    check((args.study / "study-frozen-selection.json").is_file(), "missing shared study freeze")
    driver_sha = digest(source / "run_experiment.py")
    registration = read_json(args.study / "study-registration.json")
    shared_freeze = read_json(args.study / "study-frozen-selection.json")
    check(registration["orchestratorSha256"] == shared_freeze["orchestratorSha256"] == driver_sha,
          "shared source identity")
    check(registration["jarSha256"] == "1b6d22ee57977a6e27ec2d0b72e57fab3a35fb8e694905c1270a4ffe87a2f9ba",
          "approved experiment JAR")
    check(registration["studyPlanSha256"] == digest(source / "CORE_6_10M_PLAN.md"), "registered plan")
    check(registration["holdoutSha256"] == shared_freeze["holdoutSha256"]
          == "a910f25ed4a54742e87c3e75c105472f0c882df2d4026e15a62ee3d48c90bbbd", "registered fresh holdout")
    check(shared_freeze["holdoutOpenedAtFreeze"] is False
          and shared_freeze["studyRegistrationSha256"] == digest(args.study / "study-registration.json"),
          "shared freeze registration")
    check(set(shared_freeze["profiles"]) == set(registration["profiles"]) == set(PROFILES), "complete shared profile set")
    for profile, parameters in PROFILES.items():
        descriptor = {"profileId": profile, "parameterBudget": parameters if parameters == 6_562_304 else 10_000_000,
                      "targetLabel": "width-8" if parameters == 6_562_304 else "depth-3",
                      "targetParameters": parameters,
                      "nextLabel": "depth-3" if parameters == 6_562_304 else "depth-4",
                      "nextParameters": 9_712_128 if parameters == 6_562_304 else 12_861_952,
                      "studyPlanSha256": registration["studyPlanSha256"]}
        profile_sha = hashlib.sha256(json.dumps(descriptor, sort_keys=True, separators=(",", ":")).encode()).hexdigest()
        registered = registration["profiles"][profile]
        check(registered == {**descriptor, "profileSha256": profile_sha, "output": str((args.study / profile).resolve())},
              "fixed profile registration")
        environment = read_json(args.study / profile / "environment.json")
        expected_binding = {"profileId": profile, "profileSha256": profile_sha,
                            "studyPlanSha256": registration["studyPlanSha256"],
                            "studyRegistrationSha256": digest(args.study / "study-registration.json")}
        check(all(environment[key] == value for key, value in expected_binding.items()), "profile environment binding")
        check(shared_freeze["profiles"][profile] == {**expected_binding,
              "selectionSha256": digest(args.study / profile / "frozen-selection.json")}, "joint child freeze")
    profiles = [audit_profile(args.study / profile, count, driver_sha) for profile, count in PROFILES.items()]
    check(profiles[0]["training"][0]["inputIdentity"] == profiles[1]["training"][0]["inputIdentity"],
          "cross-profile training schedule mismatch")
    pooled = [{**candidate, "studyProfileId": profile["profile"]}
              for profile in profiles for candidate in profile["candidates"] if candidate["capacity"]["eligible"]]
    minimum = min(candidate["development"]["bpb"] for candidate in pooled)
    preferred = min((candidate for candidate in pooled if candidate["development"]["bpb"] <= minimum + 0.00001),
                    key=lambda candidate: (candidate["parameters"], candidate["development"]["bpb"]))
    check(all(preferred[key] == shared_freeze["developmentPreferred"][key]
              for key in ("studyProfileId", "label", "sha256", "parameters", "bytes")), "joint quality selection")
    manifest = read_json(source / "g1-evidence/historical-artifacts-manifest.json")
    for item in manifest["files"]:
        path = args.historical_root / item["path"]
        check(path.stat().st_size == item["bytes"] and digest(path) == item["sha256"],
              f"historical artifact changed: {item['path']}")
    original_sources = read_json(source / "g1-evidence/integration-start.json")["sourceSha256"]
    repository = source.parent.parent
    for path, expected in original_sources.items():
        check(digest(repository / path) == expected, f"original runtime/protocol source changed: {path}")
    report = {"task": "G1-INTEGRATE-WEIGHT-002", "status": "PASS", "driverSha256": driver_sha,
              "auditorSha256": digest(Path(__file__)), "driverImported": False, "modelProcessesStarted": 0,
              "profiles": profiles, "historicalFilesUnchanged": len(manifest["files"]),
              "originalSourceHashesUnchanged": len(original_sources),
              "studyFreezeSha256": digest(args.study / "study-frozen-selection.json")}
    args.output.parent.mkdir(parents=True, exist_ok=True)
    with args.output.open("x", encoding="utf-8") as stream:
        json.dump(report, stream, indent=2, ensure_ascii=False)
        stream.write("\n")
    print(json.dumps({"status": "PASS", "report": str(args.output), "sha256": digest(args.output),
                      "profiles": len(profiles), "historicalFilesUnchanged": len(manifest["files"])}))


if __name__ == "__main__":
    main()
