#!/usr/bin/env python3
"""Source-bound, bounded gradient diagnostics without optimizer updates."""
import argparse
import hashlib
import importlib.util
import json
import math
from pathlib import Path
import struct

HERE = Path(__file__).resolve().parent
PRIOR = HERE / "inference-registration.json"
PRIOR_SHA = "9dd0171df1e8bf1adb0ed64cc3fa8bea1436d78773003341d75aa896492dc70e"


def digest(path):
    path = Path(path)
    if path.is_symlink() or not path.is_file():
        raise ValueError("expected regular file")
    with path.open("rb") as stream:
        return hashlib.file_digest(stream, "sha256").hexdigest()


if digest(PRIOR) != PRIOR_SHA:
    raise ValueError("prior source registration changed")
PRIOR_RECORD = json.loads(PRIOR.read_text())
if digest(HERE / "run_diagnostics.py") != PRIOR_RECORD["sourceHashes"]["run_diagnostics.py"]:
    raise ValueError("shared bounded executor changed")
SPEC = importlib.util.spec_from_file_location("bounded_executor", HERE / "run_diagnostics.py")
CORE = importlib.util.module_from_spec(SPEC)
SPEC.loader.exec_module(CORE)

COUNTS = {"S0": 132160, "W0": 6562304, "D0": 9712128}
SOURCES = ("GRADIENT-PROTOCOL.md", "LmGradientAccess.kt", "run_gradient_access.py", "probes.json")
LIMITS = {"processSeconds": 120, "totalProcessSeconds": 360, "maxRssKiB": 3145728, "heap": "3g"}
INACTIVE = ("n1", "wz", "bz", "wc", "wg", "n2", "w1", "b1")
ACTIVE = ("wo", "w2", "b2")


def require(condition, message):
    if not condition:
        raise ValueError(message)


def tensor_counts(profile):
    dim = 64 if profile == "S0" else 512
    mlp = 4 * dim
    counts = {"tok": 512 * dim}
    for layer in range(3 if profile == "D0" else 2):
        counts.update({f"l{layer}.{name}": count for name, count in (
            ("n1", dim), ("wz", dim * dim), ("bz", dim), ("wc", dim * dim),
            ("wg", dim * dim), ("wo", dim * dim), ("n2", dim), ("w1", dim * mlp),
            ("b1", mlp), ("w2", mlp * dim), ("b2", dim))})
    counts["nf"] = dim
    require(sum(counts.values()) == COUNTS[profile], "parameter formula mismatch")
    return counts


def registration(build, harness):
    receipt = json.loads(build.read_text())
    require(receipt["exitCode"] == 0 and not receipt["stderr"], "build did not pass")
    require(receipt["sourceSha256"] == digest(HERE / "LmGradientAccess.kt") and receipt["jarSha256"] == digest(harness), "build binding mismatch")
    return {"schemaVersion": 1, "purpose": "GRADIENT_ACCESS_ONLY_NO_OPTIMIZER", "profiles": COUNTS,
            "sourceHashes": {name: digest(HERE / name) for name in SOURCES}, "priorRegistrationSha256": PRIOR_SHA,
            "harnessSha256": digest(harness), "runtimeSha256": CORE.RUNTIME, "stdlibSha256": CORE.STDLIB,
            "tokenizerSha256": CORE.TOKENIZER, "models": {name: CORE.MODELS[name] for name in ("S0", "W0")},
            "probesSha256": CORE.sha(CORE.probe_bytes()[1]), "java": PRIOR_RECORD["java"],
            "limits": LIMITS, "buildReceipt": receipt, "buildReceiptSha256": digest(build)}


def verify_registration(reg, paths):
    require(reg["purpose"] == "GRADIENT_ACCESS_ONLY_NO_OPTIMIZER" and reg["schemaVersion"] == 1, "wrong scope")
    require(reg["profiles"] == COUNTS and reg["limits"] == LIMITS, "changed profiles or budgets")
    require(reg["priorRegistrationSha256"] == PRIOR_SHA and digest(PRIOR) == PRIOR_SHA, "shared registration changed")
    require(digest(HERE / "run_diagnostics.py") == PRIOR_RECORD["sourceHashes"]["run_diagnostics.py"], "shared executor changed")
    require(reg["sourceHashes"] == {name: digest(HERE / name) for name in SOURCES}, "source binding failed")
    for key, expected in (("harness", reg["harnessSha256"]), ("runtime", CORE.RUNTIME), ("stdlib", CORE.STDLIB), ("tokenizer", CORE.TOKENIZER)):
        require(reg[key + "Sha256"] == expected and digest(paths[key]) == expected, "input binding failed: " + key)
    for name in ("S0", "W0"):
        require(reg["models"][name] == CORE.MODELS[name] and digest(paths["models"] / (name + ".lm1")) == CORE.MODELS[name], "initial checkpoint binding failed")
    require(digest(paths["probes"]) == reg["probesSha256"] == CORE.sha(CORE.probe_bytes()[1]), "probe binding failed")
    require(reg["java"] == PRIOR_RECORD["java"] and digest(reg["java"]["path"]) == reg["java"]["sha256"], "Java binding failed")
    build = reg["buildReceipt"]
    require(build["exitCode"] == 0 and build["sourceSha256"] == reg["sourceHashes"]["LmGradientAccess.kt"]
            and build["jarSha256"] == reg["harnessSha256"], "compiled source binding failed")


def validate(raw, profile, reg):
    rows = []
    for line in raw.decode().splitlines():
        parts = line.split("\t")
        pairs = [part.split("=", 1) for part in parts[1:]]
        require(all(len(p) == 2 for p in pairs) and len({p[0] for p in pairs}) == len(pairs), "invalid record")
        rows.append({"kind": parts[0], **dict(pairs)})
    require(rows and all(r["profile"] == profile for r in rows), "wrong profile output")
    probes = CORE.probe_bytes()[0]
    counts = tensor_counts(profile)
    index = 0

    def take(kind, probe=None):
        nonlocal index
        require(index < len(rows) and rows[index]["kind"] == kind, "missing or unordered " + kind)
        row = rows[index]
        index += 1
        if probe is not None:
            require(row["probe"] == probe, "wrong probe output")
        return row

    def number(row, key, nonnegative=True):
        value = float(row[key])
        require(math.isfinite(value) and (not nonnegative or value >= 0), "nonfinite or invalid " + key)
        return value

    first = take("START")
    require(first["optimizerConstructed"] == "false" and int(first["parameters"]) == COUNTS[profile]
            and int(first["parameterBytes"]) == 4 * COUNTS[profile], "wrong parameter scope")
    require(first["smallCheckpointSha256"] == CORE.MODELS["S0"] and first["wideCheckpointSha256"] == CORE.MODELS["W0"]
            and first["tokenizerSha256"] == CORE.TOKENIZER and first["probesSha256"] == reg["probesSha256"], "wrong input identity")
    for probe in probes:
        row = take("INITIAL_PARITY", probe["id"])
        require(row["pass"] == row["deepWideBitwiseParity"] == "true" and number(row, "maximumProbabilityDelta") <= 1e-5, "initial parity failed")
    cases = []
    directions = 0
    for probe in probes[:4]:
        tensors = {}
        for name, count in counts.items():
            row = take("GRADIENT", probe["id"])
            require(row["tensor"] == name and int(row["parameters"]) == count and row["allFinite"] == "true", "gradient tensor mismatch")
            gn, pn, maximum = (number(row, key) for key in ("gradientL2", "parameterL2", "gradientMaxAbs"))
            nonzero = int(row["nonzeroGradients"])
            require(0 <= nonzero <= count and gn >= maximum - 1e-10 and ((nonzero == 0) == (gn == 0)), "gradient accounting mismatch")
            if pn:
                require(math.isclose(number(row, "relativeGradientL2"), gn / pn, rel_tol=1e-12, abs_tol=1e-12), "relative gradient mismatch")
            else:
                require(row["relativeGradientL2"] == "UNDEFINED_ZERO_PARAMETER_NORM", "zero-norm gradient ratio")
            tensors[name] = row
        loss = take("LOSS", probe["id"])
        ids = [int(v) for v in loss["inputIds"].split(",")]
        targets = [int(v) for v in loss["targets"].split(",")]
        require(ids[0] == 510 and targets == ids[1:] + [511] and len(targets) == int(loss["targetCount"]), "loss targets changed")
        require(abs(number(loss, "lossF32") - number(loss, "meanNll")) <= 1e-5, "loss consistency failed")
        total_norm = math.sqrt(sum(float(r["gradientL2"]) ** 2 for r in tensors.values()))
        require(math.isclose(number(loss, "totalGradientL2"), total_norm, rel_tol=1e-12, abs_tol=1e-10)
                and int(loss["nonzeroGradients"]) == sum(int(r["nonzeroGradients"]) for r in tensors.values()), "gradient totals failed")
        if profile == "W0":
            errors = []
            for name, count in tensor_counts("S0").items():
                row = take("CHAIN_TENSOR", probe["id"])
                require(row["tensor"] == name and int(row["coordinates"]) == count, "chain tensor mismatch")
                errors.append(number(row, "maximumAbsoluteError"))
            row = take("CHAIN", probe["id"])
            require(row["pass"] == "true" and int(row["coordinates"]) == 132160
                    and number(row, "maximumAbsoluteError") == max(errors) <= 2e-4
                    and number(row, "relativeL2Error") <= 2e-4, "chain rule failed")
        if profile == "D0":
            row = take("DEPTH_ACCESS", probe["id"])
            require(row["commonGradientBitwiseParity"] == row["inactiveUpstreamAllZero"] == row["allThreeOutputPathsNonzero"] == "true"
                    and int(row["inactiveUpstreamParameters"]) == 1838592 and int(row["newLayerParameters"]) == 3149824, "depth access failed")
            require(all(int(tensors["l2." + name]["nonzeroGradients"]) == 0 for name in INACTIVE)
                    and all(int(tensors["l2." + name]["nonzeroGradients"]) > 0 for name in ACTIVE), "depth flags disagree with gradients")
        for name in (("nf", "l2.wo", "l2.w2") if profile == "D0" else ("nf",)):
            row = take("DIRECTION", probe["id"])
            require(row["tensor"] == name and row["restored"] == row["pass"] == "true" and number(row, "epsilon") == .001, "direction scope failed")
            analytic = number(row, "analyticDerivative", nonnegative=False)
            epsilon = struct.unpack(">f", struct.pack(">f", .001))[0]
            numerical = (number(row, "positiveNll") - number(row, "negativeNll")) / (2 * epsilon)
            error = abs(numerical - analytic)
            limit = .0005 + .02 * abs(analytic)
            require(math.isclose(number(row, "numericalDerivative", False), numerical, rel_tol=1e-12, abs_tol=1e-12)
                    and math.isclose(number(row, "absoluteError"), error, abs_tol=1e-12)
                    and math.isclose(number(row, "limit"), limit, abs_tol=1e-12) and error <= limit, "direction arithmetic failed")
            directions += 1
        cases.append({"probe": probe["id"], "meanNll": float(loss["meanNll"]), "totalGradientL2": total_norm,
                      "nonzeroGradientCoordinates": int(loss["nonzeroGradients"]), "tensorCount": len(tensors)})
    last = take("DONE")
    require(index == len(rows) and last["allArraysRestored"] == last["inputsUnchanged"] == "true"
            and last["optimizerUpdates"] == last["serializedCheckpoints"] == "0"
            and last["finalParameterIdentity"] == first["initialParameterIdentity"]
            and int(last["directionChecks"]) == directions and last["lossProbes"] == "4", "completion gate failed")
    return {"profile": profile, "parameters": COUNTS[profile], "parameterBytes": COUNTS[profile] * 4,
            "cases": cases, "directionChecks": directions, "initialParameterIdentity": first["initialParameterIdentity"]}


def execute(registration_path, registration_sha, paths, output):
    require(digest(registration_path) == registration_sha, "registration identity failed")
    raw = registration_path.read_bytes()
    reg = json.loads(raw)
    verify_registration(reg, paths)
    output = output.absolute()
    require(not output.is_symlink(), "output symlink")
    if output.exists():
        require((output / "registration.json").read_bytes() == raw, "foreign output registration")
    else:
        output.mkdir(parents=True, exist_ok=False)
        CORE.write_new(output / "registration.json", raw)
    lock = output / "ACTIVE.lock"
    CORE.write_new(lock, CORE.encoded({"registrationSha256": registration_sha}))
    try:
        expected = {"registration.json", "ACTIVE.lock", "summary.json"} | {name + suffix for name in COUNTS for suffix in (".started.json", ".stdout.tsv", ".stderr.txt", ".receipt.json")}
        require({p.name for p in output.iterdir()} <= expected, "foreign output artifact")
        reports, receipts = {}, {}
        for profile in COUNTS:
            verify_registration(reg, paths)
            spent = sum(r["elapsedSeconds"] for r in receipts.values())
            require(spent < LIMITS["totalProcessSeconds"], "total process budget")
            command = [reg["java"]["path"], "-Xmx3g", "-cp", ":".join(str(paths[k]) for k in ("harness", "runtime", "stdlib")),
                       "at.heinz.deviceai.learning.lm.LmGradientAccess", str(paths["models"]), str(paths["tokenizer"]), str(paths["probes"]), profile]
            start, stdout, stderr, receipt_path = (output / (profile + suffix) for suffix in (".started.json", ".stdout.tsv", ".stderr.txt", ".receipt.json"))
            intent = {"registrationSha256": registration_sha, "profile": profile, "command": command}
            if receipt_path.exists():
                require(start.read_bytes() == CORE.encoded(intent), "process intent changed")
                receipt = json.loads(receipt_path.read_text())
            else:
                require(not any(p.exists() or p.is_symlink() for p in (start, stdout, stderr, receipt_path)), "partial profile blocks resume")
                CORE.write_new(start, CORE.encoded(intent))
                outcome = CORE.launch(command, stdout, stderr, min(120, 360 - spent))
                receipt = {**intent, **outcome, "status": "FAIL", "stdoutSha256": digest(stdout), "stderrSha256": digest(stderr)}
                if outcome["exitCode"] == 0 and not outcome["timedOut"] and outcome["maxRssKiB"] <= LIMITS["maxRssKiB"]:
                    try:
                        validate(stdout.read_bytes(), profile, reg)
                        receipt["status"] = "PASS"
                    except (ValueError, KeyError, IndexError) as error:
                        receipt["validationError"] = str(error)
                CORE.write_new(receipt_path, CORE.encoded(receipt))
            require(all(receipt[key] == value for key, value in intent.items()) and receipt["status"] == "PASS"
                    and receipt["exitCode"] == 0 and receipt["timedOut"] is False, "failed profile receipt")
            require(digest(stdout) == receipt["stdoutSha256"] and digest(stderr) == receipt["stderrSha256"], "receipt logs changed")
            require(math.isfinite(receipt["elapsedSeconds"]) and 0 <= receipt["elapsedSeconds"] <= 121
                    and 0 < receipt["maxRssKiB"] <= LIMITS["maxRssKiB"], "profile budget failed")
            reports[profile] = validate(stdout.read_bytes(), profile, reg)
            receipts[profile] = receipt
            print(json.dumps({"profile": profile, "status": receipt["status"], "seconds": receipt["elapsedSeconds"], "maxRssKiB": receipt["maxRssKiB"]}), flush=True)
        verify_registration(reg, paths)
        summary = {"status": "GRADIENT_ACCESS_DIAGNOSTIC_PASS_NO_OPTIMIZER", "registrationSha256": registration_sha,
                   "profiles": reports, "totalProcessSeconds": sum(r["elapsedSeconds"] for r in receipts.values()),
                   "maximumProcessRssKiB": max(r["maxRssKiB"] for r in receipts.values()),
                   "receiptHashes": {name: digest(output / (name + ".receipt.json")) for name in receipts},
                   "optimizerUpdates": 0, "serializedCheckpoints": 0, "modelPromotion": False}
        if (output / "summary.json").exists():
            require((output / "summary.json").read_bytes() == CORE.encoded(summary), "completed summary changed")
        else:
            CORE.write_new(output / "summary.json", CORE.encoded(summary))
        return summary
    finally:
        lock.unlink()


def main():
    parser = argparse.ArgumentParser(description=__doc__)
    sub = parser.add_subparsers(dest="action", required=True)
    register = sub.add_parser("register")
    for name in ("build", "harness", "output"):
        register.add_argument("--" + name, required=True, type=Path)
    run = sub.add_parser("run")
    for name in ("registration", "harness", "runtime", "stdlib", "tokenizer", "models", "probes", "output"):
        run.add_argument("--" + name, required=True, type=Path)
    run.add_argument("--registration-sha256", required=True)
    args = parser.parse_args()
    if args.action == "register":
        CORE.write_new(args.output, CORE.encoded(registration(args.build, args.harness)))
        print(json.dumps({"registrationSha256": digest(args.output)}))
    else:
        paths = {key: getattr(args, key).resolve(strict=True) for key in ("harness", "runtime", "stdlib", "tokenizer", "models", "probes")}
        print(json.dumps(execute(args.registration, args.registration_sha256, paths, args.output), indent=2))


if __name__ == "__main__":
    main()
