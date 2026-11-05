#!/usr/bin/env python3
"""One frozen counterfactual path-opening experiment, without optimization."""
import argparse
import hashlib
import importlib.util
import json
import math
from pathlib import Path

HERE = Path(__file__).resolve().parent
PRIOR_SHA = "3ee5dc40b67fad6c86c0c0b8487740834526530637e5ee9a9f992691d26f4f88"
INITIAL_IDENTITY = "d6d3d64b01a12fe8d0e8c81527aff276bfefb776f09c2ed37f7f6eb21fd24640"


def digest(path):
    path = Path(path)
    if not path.is_file() or path.is_symlink():
        raise ValueError("expected regular input")
    return hashlib.sha256(path.read_bytes()).hexdigest()


if digest(HERE / "gradient-registration.json") != PRIOR_SHA:
    raise ValueError("previous registration changed")
PRIOR = json.loads((HERE / "gradient-registration.json").read_text())
if digest(HERE / "run_gradient_access.py") != PRIOR["sourceHashes"]["run_gradient_access.py"]:
    raise ValueError("shared controller source changed")
SPEC = importlib.util.spec_from_file_location("gradient_contract", HERE / "run_gradient_access.py")
G = importlib.util.module_from_spec(SPEC)
SPEC.loader.exec_module(G)
C = G.CORE
SOURCES = ("OPEN-PATH-PROTOCOL.md", "LmDepthAccessProbe.kt", "run_open_path.py", "probes.json",
           "gradient-registration.json", "run_gradient_access.py", "inference-registration.json", "run_diagnostics.py")
LIMITS = {"processSeconds": 120, "heap": "3g", "maxRssKiB": 3145728}


def require(condition, message):
    if not condition:
        raise ValueError(message)


def registration(build, harness):
    receipt = json.loads(build.read_text())
    require(receipt["exitCode"] == 0 and not receipt["stderr"] and receipt["sourceSha256"] == digest(HERE / "LmDepthAccessProbe.kt")
            and receipt["jarSha256"] == digest(harness), "build binding failed")
    return {"schemaVersion": 1, "purpose": "FIXED_PATH_OPENING_NO_OPTIMIZER", "limits": LIMITS,
            "sourceHashes": {name: digest(HERE / name) for name in SOURCES}, "harnessSha256": digest(harness),
            "initialParameterIdentity": INITIAL_IDENTITY, "runtimeSha256": C.RUNTIME, "stdlibSha256": C.STDLIB,
            "tokenizerSha256": C.TOKENIZER, "probesSha256": C.sha(C.probe_bytes()[1]),
            "models": {name: C.MODELS[name] for name in ("S0", "W0")}, "java": PRIOR["java"],
            "buildReceipt": receipt, "buildReceiptSha256": digest(build)}


def verify_inputs(reg, paths):
    require(reg["schemaVersion"] == 1 and reg["purpose"] == "FIXED_PATH_OPENING_NO_OPTIMIZER"
            and reg["limits"] == LIMITS and reg["initialParameterIdentity"] == INITIAL_IDENTITY, "scope or budget changed")
    require(reg["sourceHashes"] == {name: digest(HERE / name) for name in SOURCES}, "source binding failed")
    for key, expected in (("harness", reg["harnessSha256"]), ("runtime", C.RUNTIME), ("stdlib", C.STDLIB), ("tokenizer", C.TOKENIZER)):
        require(reg[key + "Sha256"] == digest(paths[key]) == expected, "input binding failed")
    for name in ("S0", "W0"):
        require(reg["models"][name] == digest(paths["models"] / (name + ".lm1")) == C.MODELS[name], "checkpoint changed")
    require(digest(paths["probes"]) == reg["probesSha256"] == C.sha(C.probe_bytes()[1]), "probes changed")
    require(reg["java"] == PRIOR["java"] and digest(reg["java"]["path"]) == reg["java"]["sha256"], "Java changed")
    require(reg["buildReceipt"]["sourceSha256"] == reg["sourceHashes"]["LmDepthAccessProbe.kt"]
            and reg["buildReceipt"]["jarSha256"] == reg["harnessSha256"] and reg["buildReceipt"]["exitCode"] == 0, "compiled source changed")


def validate(raw, reg):
    rows = []
    for line in raw.decode().splitlines():
        fields = line.split("\t")
        pairs = [f.split("=", 1) for f in fields[1:]]
        require(all(len(p) == 2 for p in pairs) and len({p[0] for p in pairs}) == len(pairs), "invalid record")
        rows.append({"kind": fields[0], **dict(pairs)})
    index = 0

    def take(kind, probe=None):
        nonlocal index
        require(index < len(rows) and rows[index]["kind"] == kind, "missing or unordered " + kind)
        row = rows[index]
        index += 1
        require(probe is None or row["probe"] == probe, "probe mismatch")
        return row

    first = take("START")
    require(first["optimizerConstructed"] == "false" and first["initialParameterIdentity"] == INITIAL_IDENTITY
            and int(first["parameters"]) == 9712128 and first["wideCheckpointSha256"] == C.MODELS["W0"]
            and first["smallCheckpointSha256"] == C.MODELS["S0"] and first["tokenizerSha256"] == C.TOKENIZER
            and first["probesSha256"] == reg["probesSha256"], "starting identity failed")
    probes = C.probe_bytes()[0]
    for probe in probes:
        row = take("BASE_PARITY", probe["id"])
        require(row["pass"] == row["deepWideBitwiseParity"] == "true"
                and 0 <= float(row["maximumProbabilityDelta"]) <= 1e-5, "baseline parity failed")
    opened = take("OPENED")
    require(int(opened["changedCoordinates"]) == 2560 and float(opened["woDiagonal"]) == .0001
            and float(opened["w2MappedEntry"]) == .000025 and opened["openedParameterIdentity"] != INITIAL_IDENTITY, "perturbation differs from protocol")
    deltas = []
    for probe in probes:
        row = take("OPEN_PARITY", probe["id"])
        value = float(row["maximumProbabilityDelta"])
        require(row["pass"] == "true" and 0 <= value <= 1e-5, "opened-path parity failed")
        deltas.append(value)
    counts = G.tensor_counts("D0")
    cases = []
    for probe in probes[:4]:
        minimum, maximum = math.inf, 0.
        newly_reached = 0
        norms = {}
        for name, count in counts.items():
            row = take("GRADIENT", probe["id"])
            require(row["tensor"] == name and int(row["parameters"]) == int(row["nonzeroGradients"]) == count
                    and row["allFinite"] == "true", "gradient coordinate not reached")
            low, high, norm = (float(row[k]) for k in ("minimumNonzeroAbs", "maximumAbs", "gradientL2"))
            require(all(math.isfinite(v) and v > 0 for v in (low, high, norm)) and low <= high
                    and low * math.sqrt(count) <= norm * (1 + 1e-6) and norm <= high * math.sqrt(count) * (1 + 1e-6), "gradient magnitude invalid")
            minimum, maximum = min(minimum, low), max(maximum, high)
            norms[name] = norm
            if name in {"l2." + n for n in G.INACTIVE}:
                newly_reached += count
        row = take("LOSS", probe["id"])
        require(row["allParametersReached"] == "true" and int(row["nonzeroGradientCoordinates"]) == 9712128
                and int(row["targetCount"]) > 0 and math.isfinite(float(row["meanLoss"]))
                and newly_reached == 1838592, "loss or access gate failed")
        cases.append({"probe": probe["id"], "nonzeroGradientCoordinates": 9712128, "newlyReachedCoordinates": newly_reached,
                      "minimumNonzeroGradientAbs": minimum, "maximumGradientAbs": maximum,
                      "meanLoss": float(row["meanLoss"]), "tensorGradientL2": norms})
    restored = take("RESTORED")
    require(restored["pass"] == "true" and restored["initialParameterIdentity"] == restored["finalParameterIdentity"] == INITIAL_IDENTITY
            and restored["completedLossProbes"] == "4", "restoration failed")
    last = take("DONE")
    require(index == len(rows) and last["lossProbes"] == "4" and last["inputsUnchanged"] == "true"
            and last["optimizerUpdates"] == last["serializedCheckpoints"] == "0" and last["modelPromotion"] == "false", "completion failed")
    return {"maximumOpenedProbabilityDelta": max(deltas), "changedParameterCoordinates": 2560,
            "cases": cases, "allArraysRestored": True, "optimizerUpdates": 0, "serializedCheckpoints": 0}


def execute(reg_path, reg_sha, paths, output):
    require(digest(reg_path) == reg_sha, "registration digest mismatch")
    reg_raw = reg_path.read_bytes()
    reg = json.loads(reg_raw)
    verify_inputs(reg, paths)
    require(not output.is_symlink(), "output symlink")
    if output.exists():
        require((output / "registration.json").read_bytes() == reg_raw, "foreign registration")
    else:
        output.mkdir(parents=True, exist_ok=False)
        C.write_new(output / "registration.json", reg_raw)
    lock = output / "ACTIVE.lock"
    C.write_new(lock, C.encoded({"registrationSha256": reg_sha}))
    try:
        require({p.name for p in output.iterdir()} <= {"registration.json", "ACTIVE.lock", "started.json", "stdout.tsv", "stderr.txt", "receipt.json", "summary.json"}, "foreign output artifact")
        command = [reg["java"]["path"], "-Xmx3g", "-cp", ":".join(str(paths[k]) for k in ("harness", "runtime", "stdlib")),
                   "at.heinz.deviceai.learning.lm.LmDepthAccessProbe", str(paths["models"]), str(paths["tokenizer"]), str(paths["probes"])]
        start, stdout, stderr, receipt_path = (output / name for name in ("started.json", "stdout.tsv", "stderr.txt", "receipt.json"))
        intent = {"registrationSha256": reg_sha, "command": command}
        if receipt_path.exists():
            require(start.read_bytes() == C.encoded(intent), "process intent changed")
            receipt = json.loads(receipt_path.read_text())
        else:
            require(not any(p.exists() or p.is_symlink() for p in (start, stdout, stderr, receipt_path)), "partial attempt blocks rerun")
            C.write_new(start, C.encoded(intent))
            outcome = C.launch(command, stdout, stderr, 120)
            receipt = {**intent, **outcome, "status": "FAIL", "stdoutSha256": digest(stdout), "stderrSha256": digest(stderr)}
            if outcome["exitCode"] == 0 and not outcome["timedOut"] and outcome["maxRssKiB"] <= LIMITS["maxRssKiB"]:
                try:
                    validate(stdout.read_bytes(), reg)
                    receipt["status"] = "PASS"
                except (ValueError, KeyError, IndexError) as error:
                    receipt["validationError"] = str(error)
            C.write_new(receipt_path, C.encoded(receipt))
        require(all(receipt[k] == v for k, v in intent.items()) and receipt["status"] == "PASS" and receipt["exitCode"] == 0
                and receipt["timedOut"] is False and 0 <= receipt["elapsedSeconds"] <= 121
                and 0 < receipt["maxRssKiB"] <= LIMITS["maxRssKiB"], "attempt failed or budget exceeded")
        require(receipt["stdoutSha256"] == digest(stdout) and receipt["stderrSha256"] == digest(stderr), "receipt logs changed")
        result = validate(stdout.read_bytes(), reg)
        verify_inputs(reg, paths)
        result.update(status="ALL_9712128_COORDINATES_REACHED_RESTORED_NO_OPTIMIZER", registrationSha256=reg_sha,
                      receiptSha256=digest(receipt_path), processSeconds=receipt["elapsedSeconds"], maxRssKiB=receipt["maxRssKiB"])
        summary = output / "summary.json"
        if summary.exists():
            require(summary.read_bytes() == C.encoded(result), "summary changed")
        else:
            C.write_new(summary, C.encoded(result))
        return result
    finally:
        lock.unlink()


def main():
    parser = argparse.ArgumentParser(description=__doc__)
    sub = parser.add_subparsers(dest="action", required=True)
    register = sub.add_parser("register")
    for name in ("build", "harness", "output"):
        register.add_argument("--" + name, type=Path, required=True)
    run = sub.add_parser("run")
    for name in ("registration", "harness", "runtime", "stdlib", "tokenizer", "models", "probes", "output"):
        run.add_argument("--" + name, type=Path, required=True)
    run.add_argument("--registration-sha256", required=True)
    args = parser.parse_args()
    if args.action == "register":
        C.write_new(args.output, C.encoded(registration(args.build, args.harness)))
        print(json.dumps({"registrationSha256": digest(args.output)}))
    else:
        paths = {k: getattr(args, k).resolve(strict=True) for k in ("harness", "runtime", "stdlib", "tokenizer", "models", "probes")}
        print(json.dumps(execute(args.registration, args.registration_sha256, paths, args.output), indent=2))


if __name__ == "__main__":
    main()
