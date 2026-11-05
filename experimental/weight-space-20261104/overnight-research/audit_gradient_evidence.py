#!/usr/bin/env python3
"""Independently recount frozen gradient evidence; no model execution."""
import argparse
import hashlib
import json
import math
from pathlib import Path
import struct

HERE = Path(__file__).resolve().parent
REGISTRATION_SHA = "3ee5dc40b67fad6c86c0c0b8487740834526530637e5ee9a9f992691d26f4f88"


def require(condition, message):
    if not condition:
        raise ValueError(message)


def digest(path):
    return hashlib.sha256(Path(path).read_bytes()).hexdigest()


def rows(path):
    result = []
    for line in path.read_text().splitlines():
        fields = line.split("\t")
        entries = [field.split("=", 1) for field in fields[1:]]
        require(all(len(v) == 2 for v in entries) and len({v[0] for v in entries}) == len(entries), "invalid record")
        result.append({"record": fields[0], **dict(entries)})
    return result


def audit(directory):
    require(digest(directory / "registration.json") == REGISTRATION_SHA, "registration identity")
    reg = json.loads((directory / "registration.json").read_text())
    for name, expected in reg["sourceHashes"].items():
        require(digest(HERE / name) == expected, "source changed")
    require(reg["buildReceipt"]["sourceSha256"] == digest(HERE / "LmGradientAccess.kt")
            and reg["buildReceipt"]["jarSha256"] == reg["harnessSha256"], "build binding")
    summary = json.loads((directory / "summary.json").read_text())
    result = {"profiles": {}, "nfRelativeGradientRatioWideOverSmall": {}}
    all_rows = {}
    for profile, dim, layers in (("S0", 64, 2), ("W0", 512, 2), ("D0", 512, 3)):
        mlp = dim * 4
        expected_count = 512 * dim + layers * (4 * dim * dim + 2 * dim * mlp + mlp + 4 * dim) + dim
        receipt = json.loads((directory / (profile + ".receipt.json")).read_text())
        require(receipt["status"] == "PASS" and receipt["exitCode"] == 0 and receipt["timedOut"] is False
                and receipt["registrationSha256"] == REGISTRATION_SHA, "process not successful")
        require(receipt["stdoutSha256"] == digest(directory / (profile + ".stdout.tsv"))
                and receipt["stderrSha256"] == digest(directory / (profile + ".stderr.txt")), "receipt mismatch")
        require(summary["receiptHashes"][profile] == digest(directory / (profile + ".receipt.json")), "summary receipt mismatch")
        data = rows(directory / (profile + ".stdout.tsv"))
        all_rows[profile] = data
        require(data[0]["record"] == "START" and data[-1]["record"] == "DONE", "incomplete profile")
        require(int(data[0]["parameters"]) == expected_count and int(data[0]["parameterBytes"]) == expected_count * 4, "parameter recount")
        require(data[0]["optimizerConstructed"] == "false" and data[-1]["optimizerUpdates"] == data[-1]["serializedCheckpoints"] == "0", "optimizer scope")
        require(data[0]["initialParameterIdentity"] == data[-1]["finalParameterIdentity"]
                and data[-1]["allArraysRestored"] == data[-1]["inputsUnchanged"] == "true", "array restoration")
        parity = [r for r in data if r["record"] == "INITIAL_PARITY"]
        gradients = [r for r in data if r["record"] == "GRADIENT"]
        directions = [r for r in data if r["record"] == "DIRECTION"]
        losses = [r for r in data if r["record"] == "LOSS"]
        require(len(parity) == 12 and all(r["pass"] == r["deepWideBitwiseParity"] == "true" and float(r["maximumProbabilityDelta"]) <= 1e-5 for r in parity), "initial parity")
        require(len(losses) == 4 and len(gradients) == 4 * (layers * 11 + 2) and len(directions) == (12 if profile == "D0" else 4), "record coverage")
        cases = []
        for loss in losses:
            probe = loss["probe"]
            group = [r for r in gradients if r["probe"] == probe]
            count = sum(int(r["parameters"]) for r in group)
            active = sum(int(r["nonzeroGradients"]) for r in group)
            norm = math.sqrt(sum(float(r["gradientL2"]) ** 2 for r in group))
            require(count == expected_count and active == int(loss["nonzeroGradients"]), "gradient coordinate accounting")
            require(math.isclose(norm, float(loss["totalGradientL2"]), rel_tol=1e-12), "gradient norm recount")
            require(abs(float(loss["lossF32"]) - float(loss["meanNll"])) <= 1e-5, "loss consistency")
            if profile == "D0":
                upstream = {"l2." + n for n in ("n1", "wz", "bz", "wc", "wg", "n2", "w1", "b1")}
                expected_inactive = 3 * dim * dim + dim * mlp + mlp + 3 * dim
                inactive = sum(int(r["parameters"]) for r in group if r["tensor"] in upstream)
                require(inactive == expected_inactive == 1838592
                        and all(int(r["nonzeroGradients"]) == 0 for r in group if r["tensor"] in upstream)
                        and active == expected_count - inactive == 7873536, "identity-layer access recount")
            else:
                require(active == expected_count, "initial gradient coordinates")
            cases.append({"probe": probe, "parameters": count, "nonzeroGradientCoordinates": active,
                          "totalGradientL2": norm, "meanNll": float(loss["meanNll"])})
        fd_errors = []
        for direction in directions:
            epsilon = struct.unpack(">f", struct.pack(">f", float(direction["epsilon"])))[0]
            derivative = (float(direction["positiveNll"]) - float(direction["negativeNll"])) / (2 * epsilon)
            analytic = float(direction["analyticDerivative"])
            error = abs(derivative - analytic)
            bound = .0005 + .02 * abs(analytic)
            require(math.isclose(error, float(direction["absoluteError"]), abs_tol=1e-12)
                    and error <= bound and direction["restored"] == direction["pass"] == "true", "directional difference gate")
            fd_errors.append({"probe": direction["probe"], "tensor": direction["tensor"], "absoluteError": error, "limit": bound, "fractionOfLimit": error / bound})
        result["profiles"][profile] = {"parameters": expected_count, "parameterBytes": expected_count * 4,
                                      "gradientTensorRecords": len(gradients), "cases": cases,
                                      "directionalChecks": fd_errors, "receiptSha256": digest(directory / (profile + ".receipt.json")),
                                      "processSeconds": receipt["elapsedSeconds"], "maxRssKiB": receipt["maxRssKiB"]}
    chain = [r for r in all_rows["W0"] if r["record"] == "CHAIN"]
    require(len(chain) == 4 and all(r["pass"] == "true" and int(r["coordinates"]) == 132160
            and float(r["maximumAbsoluteError"]) <= 2e-4 and float(r["relativeL2Error"]) <= 2e-4 for r in chain), "widening pullback gate")
    depth = [r for r in all_rows["D0"] if r["record"] == "DEPTH_ACCESS"]
    require(len(depth) == 4 and all(r["commonGradientBitwiseParity"] == r["inactiveUpstreamAllZero"] == r["allThreeOutputPathsNonzero"] == "true" for r in depth), "depth gradient gate")
    for probe in (r["probe"] for r in all_rows["S0"] if r["record"] == "LOSS"):
        small = next(r for r in all_rows["S0"] if r["record"] == "GRADIENT" and r["probe"] == probe and r["tensor"] == "nf")
        wide = next(r for r in all_rows["W0"] if r["record"] == "GRADIENT" and r["probe"] == probe and r["tensor"] == "nf")
        result["nfRelativeGradientRatioWideOverSmall"][probe] = float(wide["relativeGradientL2"]) / float(small["relativeGradientL2"])
    result.update(status="GRADIENT_EVIDENCE_RECOUNT_PASS", registrationSha256=REGISTRATION_SHA,
                  checkerSha256=digest(Path(__file__)), gradientTensorRecords=sum(r["gradientTensorRecords"] for r in result["profiles"].values()),
                  directionalChecks=20, wideningChainChecks=4, depthCommonGradientChecks=4,
                  maximumWideningPullbackAbsoluteError=max(float(r["maximumAbsoluteError"]) for r in chain),
                  maximumWideningPullbackRelativeL2Error=max(float(r["relativeL2Error"]) for r in chain),
                  optimizerUpdates=0, serializedCheckpoints=0,
                  limitation="Independent evidence arithmetic; backward arrays are checked by the pinned JVM source and finite differences, not recomputed by this Python auditor.")
    return result


if __name__ == "__main__":
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("--run", type=Path, required=True)
    parser.add_argument("--output", type=Path, required=True)
    args = parser.parse_args()
    result = audit(args.run)
    with args.output.open("x") as stream:
        json.dump(result, stream, indent=2)
        stream.write("\n")
    print(json.dumps({k: result[k] for k in ("status", "gradientTensorRecords", "directionalChecks", "maximumWideningPullbackAbsoluteError")}))
