#!/usr/bin/env python3
"""Independent NumPy forward and raw-evidence arithmetic verification."""
from __future__ import annotations

import argparse
import base64
import hashlib
import importlib.util
import json
import math
import os
from pathlib import Path
import struct
import subprocess
import time
import zipfile

import numpy as np

HERE = Path(__file__).resolve().parent
READER = HERE.parent / "continuation-review/root-verification/audit_binary_archive.py"
PINS = {
    "archive": "428d62b308e308afd9c971ff4974981e96b9dc8a783d80e3653c226983dcc016",
    "tokenizer": "94375c73d1f6e2d04b790738580143fa8c1b9b456d40fa7eb6b4009877bd892f",
    "registration": "9dd0171df1e8bf1adb0ed64cc3fa8bea1436d78773003341d75aa896492dc70e",
    "reader": "dca91cb311ac022a3f8f257f8ca61fffc9de41e33fae35a73b8412786b4fbc86",
}
EXPECTED = {
    "S0": "70cb6ed4020042c4ced21bf0c58dab1b83ee2a51655fdbec41279cd961234435",
    "S1": "90b58fb58e048bd4eac967f2b0b131373170201660de7a3d330b393a6c8d820f",
    "W0": "af617cf6d223a3e53b44bc3c0578b6924e9c73d8b92f558a981af22990cad04d",
    "W1": "0f2b5481cf60d6c6021e9e24d1e5ba70d303da274c18933de29c48a47489cf8c",
}


def require(condition, message):
    if not condition:
        raise ValueError(message)


def sha(raw):
    return hashlib.sha256(raw).hexdigest()


def file_sha(path):
    with Path(path).open("rb") as stream:
        return hashlib.file_digest(stream, "sha256").hexdigest()


def f32(value):
    return np.asarray(value, dtype=np.float32)


def sigmoid(values):
    values = f32(values)
    negative = values < 0
    exponent = np.exp(np.where(negative, values, -values).astype(np.float64)).astype(np.float32)
    return np.where(negative, exponent / (np.float32(1) + exponent), np.float32(1) / (np.float32(1) + exponent))


def norm(values, gain):
    squares = (values * values).sum(axis=1, dtype=np.float64)
    scale = (1 / np.sqrt(squares / values.shape[1] + float(np.float32(1e-5)))).astype(np.float32)
    return (values * scale[:, None]) * gain


def forward(weights, ids):
    states = {"embedding": weights["tok"][ids]}
    x = states["embedding"]
    for layer in (0, 1):
        key = "l" + str(layer) + "."
        h = norm(x, weights[key + "n1"])
        z = sigmoid(h @ weights[key + "wz"] + weights[key + "bz"])
        candidate = h @ weights[key + "wc"]
        state = np.zeros_like(candidate)
        previous = np.zeros(candidate.shape[1], dtype=np.float32)
        for position in range(len(ids)):
            previous = (np.float32(1) - z[position]) * previous + z[position] * candidate[position]
            state[position] = previous
        gate = h @ weights[key + "wg"]
        x = x + (state * (gate * sigmoid(gate))) @ weights[key + "wo"]
        states[key + "recurrent"] = x
        h = norm(x, weights[key + "n2"])
        value = h @ weights[key + "w1"] + weights[key + "b1"]
        u = np.float32(math.sqrt(2 / math.pi)) * (value + np.float32(.044715) * value * value * value)
        gelu = (np.float32(.5) * value) * (np.float32(1) + np.tanh(u.astype(np.float64)).astype(np.float32))
        x = x + (gelu @ weights[key + "w2"] + weights[key + "b2"])
        states[key + "mlp"] = x
    final = norm(x, weights["nf"])
    states["finalNorm"] = final
    logits = (final @ weights["tok"].T)[-1]
    exp64 = np.exp((logits - logits.max()).astype(np.float64))
    probabilities = (exp64.astype(np.float32) / exp64.sum()).astype(np.float32)
    return logits, probabilities, states


def spread(values):
    blocks = values.astype(np.float64).reshape(values.shape[0], values.shape[1] // 64, 64)
    rms = math.sqrt(float((blocks * blocks).mean()))
    residual = blocks - blocks.mean(axis=1)[:, None, :]
    variability = math.sqrt(float((residual * residual).mean()))
    return {"valueRms": rms, "relativeSpreadRms": variability / rms if rms else 0.,
            "maximumReplicaRange": float((blocks.max(axis=1) - blocks.min(axis=1)).max())}


def read_rows(path):
    result = []
    for line in path.read_text().splitlines():
        fields = line.split("\t")
        pairs = [field.split("=", 1) for field in fields[1:]]
        require(all(len(pair) == 2 for pair in pairs) and len({p[0] for p in pairs}) == len(pairs), "bad raw record")
        result.append({"record": fields[0], **dict(pairs)})
    return result


def verify(run, archive, tokenizer):
    started = time.monotonic()
    require(os.environ.get("OPENBLAS_NUM_THREADS") == "1", "single BLAS thread required")
    require(file_sha(archive) == PINS["archive"] and file_sha(tokenizer) == PINS["tokenizer"], "input identity failed")
    require(file_sha(READER) == PINS["reader"], "reader changed")
    reg_raw = (run / "registration.json").read_bytes()
    require(sha(reg_raw) == PINS["registration"], "registration changed")
    reg = json.loads(reg_raw)
    for name, expected in reg["sourceHashes"].items():
        require(file_sha(HERE / name) == expected, "registered source changed")
    tensor_source = subprocess.check_output(["git", "show", "59891cfad6dd015f47c187cc688ea94b0026ad7d:app/src/main/java/at/heinz/deviceai/learning/lm/LmTensor.kt"], cwd=HERE.parents[2])
    require(sha(tensor_source) == "a5497f18d09be50f7afbd201d54d97f012f7508f8c53c834ff6c10ef278290ab", "kernel source changed")
    spec = importlib.util.spec_from_file_location("verified_checkpoint_reader", READER)
    reader = importlib.util.module_from_spec(spec)
    spec.loader.exec_module(reader)
    arrays = {}
    with zipfile.ZipFile(archive) as package:
        for arm, expected in EXPECTED.items():
            raw = package.read("LM1_Training_Research_20261104/models/" + arm + ".lm1")
            require(sha(raw) == expected, "checkpoint changed")
            report, values = reader.checkpoint(raw, PINS["tokenizer"], 512)
            arrays[arm] = {t["name"]: np.frombuffer(values[t["name"]], dtype=">f4").astype(np.float32).reshape(t["rows"], t["cols"])
                           for t in report["tensors"]}
    token_raw = tokenizer.read_bytes()
    magic, count = struct.unpack_from(">ii", token_raw)
    require(magic == 0x4C4D4250 and count == 254 and len(token_raw) == 8 + count * 8, "tokenizer format")
    pieces = [bytes((i,)) for i in range(256)]
    for offset in range(8, len(token_raw), 8):
        a, b = struct.unpack_from(">ii", token_raw, offset)
        require(0 <= a < len(pieces) and 0 <= b < len(pieces), "token reference")
        pieces.append(pieces[a] + pieces[b])
    probes = json.loads((HERE / "probes.json").read_text())["probes"]
    texts = {p["id"]: p["text"] for p in probes}
    stats = {"maximumProbabilityDelta": 0., "maximumEosLogitDelta": 0., "maximumBestOtherLogitDelta": 0.,
             "maximumTraceValueRmsDelta": 0., "maximumTraceRelativeSpreadDelta": 0., "maximumTraceRangeDelta": 0.}
    arms, receipts, distributions = {}, {}, {}
    trace_count = 0
    generation_count = 0
    for arm in reg["arms"]:
        name = arm["id"]
        receipt_path = run / (name + ".receipt.json")
        receipt = json.loads(receipt_path.read_text())
        receipts[name] = file_sha(receipt_path)
        require(receipt["registrationSha256"] == PINS["registration"] and receipt["arm"] == arm and receipt["status"] == "PASS"
                and receipt["exitCode"] == 0 and not receipt["timedOut"], "process not valid")
        require(0 < receipt["maxRssKiB"] <= 786432 and 0 <= receipt["elapsedSeconds"] <= 121, "resource budget")
        for kind, suffix in (("stdout", ".stdout.tsv"), ("stderr", ".stderr.txt")):
            require(file_sha(run / (name + suffix)) == receipt[kind + "Sha256"], "raw log changed")
        rows = read_rows(run / (name + ".stdout.tsv"))
        require(rows[0]["record"] == "START" and rows[-1]["record"] == "DONE", "incomplete arm")
        require(all(row["arm"] == name for row in rows), "mixed arm output")
        p_rows = [r for r in rows if r["record"] == "PROBE"]
        t_rows = [r for r in rows if r["record"] == "TRACE"]
        g_rows = [r for r in rows if r["record"] == "GENERATION"]
        require([p["probe"] for p in p_rows] == list(texts) and len(t_rows) == 72, "probe coverage")
        require(len(g_rows) == (12 if arm["block"] == "native" else 0), "generation coverage")
        weights = {k: (arrays[arm["donor"]][k] if k == arm["block"] or k.startswith(arm["block"] + ".") else v)
                   for k, v in arrays[arm["receiver"]].items()}
        cases = []
        distributions[name] = {}
        for row in p_rows:
            probe = row["probe"]
            ids = [int(i) for i in row["inputIds"].split(",")]
            require(ids[0] == 510 and all(0 <= i < 510 for i in ids[1:]), "invalid input IDs")
            require(b"".join(pieces[i] for i in ids[1:]).decode() == texts[probe], "input text mismatch")
            raw_p = base64.b64decode(row["probabilitiesF32BE"], validate=True)
            require(len(raw_p) == 2048, "distribution length")
            observed = np.frombuffer(raw_p, dtype=">f4").astype(np.float64)
            require(np.isfinite(observed).all() and (observed >= 0).all() and abs(observed.sum() - 1) < 3e-6, "distribution values")
            require(row["instrumentationBitwiseParity"] == "true", "JVM forward parity")
            allowed = [i for i in range(512) if i != 510]
            require(int(row["topAllowedId"]) == max(allowed, key=lambda i: observed[i]), "top token")
            require(int(row["eosRank"]) == 1 + sum(observed[i] > observed[511] for i in allowed), "EOS rank")
            entropy = -sum(p * math.log(p) for p in observed if p > 0)
            require(abs(entropy - float(row["entropyNats"])) < 1e-10, "entropy arithmetic")
            require(abs(observed[511] - float(row["eosProbability"])) <= 5e-8, "EOS arithmetic")
            eos_logit, other_logit = map(lambda x: float(np.float32(x)), (row["eosLogit"], row["bestOtherLogit"]))
            require(abs(eos_logit - other_logit - float(row["eosMargin"])) < 1e-12, "logit margin arithmetic")
            logits, predicted, traces = forward(weights, ids)
            delta = float(np.max(np.abs(predicted.astype(np.float64) - observed)))
            ed = abs(float(logits[511]) - eos_logit)
            od = abs(float(max(logits[i] for i in allowed if i != 511)) - other_logit)
            stats["maximumProbabilityDelta"] = max(stats["maximumProbabilityDelta"], delta)
            stats["maximumEosLogitDelta"] = max(stats["maximumEosLogitDelta"], ed)
            stats["maximumBestOtherLogitDelta"] = max(stats["maximumBestOtherLogitDelta"], od)
            require(delta <= 1e-5 and ed <= 1e-4 and od <= 1e-4, "independent forward mismatch: " + name + "/" + probe)
            matching = [r for r in t_rows if r["probe"] == probe]
            require({r["stage"] for r in matching} == set(traces) and len(matching) == len(traces), "trace identity")
            for observed_trace in matching:
                actual = spread(traces[observed_trace["stage"]])
                for key, tolerance, report_key in (("valueRms", 1e-4, "maximumTraceValueRmsDelta"),
                                                  ("relativeSpreadRms", 2e-6, "maximumTraceRelativeSpreadDelta"),
                                                  ("maximumReplicaRange", 1e-4, "maximumTraceRangeDelta")):
                    difference = abs(actual[key] - float(observed_trace[key]))
                    stats[report_key] = max(stats[report_key], difference)
                    require(difference <= tolerance, "independent trace mismatch: " + name + "/" + probe + "/" + observed_trace["stage"])
                trace_count += 1
            distributions[name][probe] = observed.tolist()
            cases.append({"probe": probe, "group": next(p["group"] for p in probes if p["id"] == probe),
                          "topId": int(row["topAllowedId"]), "eosProbability": observed[511],
                          "eosMargin": float(row["eosMargin"]), "independentProbabilityDelta": delta})
        generation_count += len(g_rows)
        arms[name] = {"receiver": arm["receiver"], "donor": arm["donor"], "block": arm["block"],
                      "meanEosProbability": sum(c["eosProbability"] for c in cases) / 12,
                      "eosTopCount": sum(c["topId"] == 511 for c in cases), "cases": cases,
                      "generation": [{"probe": row["probe"], "tokens": int(row["tokens"]), "stop": row["stop"],
                                      "millis": int(row["millis"]), "text": base64.b64decode(row["textBase64"]).decode()} for row in g_rows]}
        print(json.dumps({"verifiedArm": name, "cases": len(cases)}), flush=True)
    native_delta = max(abs(a - b) for probe in texts for a, b in zip(distributions["S0"][probe], distributions["W0"][probe]))
    require(native_delta <= 1e-5, "initial parity gate")
    summary = json.loads((run / "summary.json").read_text())
    require(summary["receiptHashes"] == receipts and summary["probes"] == 240 and summary["traceRows"] == trace_count == 1440
            and summary["nativeGenerations"] == generation_count == 48, "summary mismatch")
    for name, arm in arms.items():
        reference = arms[arm["receiver"]]
        arm["deltaMeanEosFromReceiver"] = arm["meanEosProbability"] - reference["meanEosProbability"]
        arm["perProbeEosDeltaFromReceiver"] = {case["probe"]: case["eosProbability"] - next(c["eosProbability"] for c in reference["cases"] if c["probe"] == case["probe"]) for case in arm["cases"]}
    require(file_sha(archive) == PINS["archive"] and file_sha(tokenizer) == PINS["tokenizer"], "inputs changed")
    return {"status": "INDEPENDENT_NUMERICAL_VERIFICATION_PASS", "registrationSha256": PINS["registration"],
            "implementationSha256": file_sha(Path(__file__)), "verificationProtocolSha256": file_sha(HERE / "VERIFICATION-PROTOCOL.md"),
            "numpyVersion": np.__version__, "blasThreads": 1, "elapsedSeconds": time.monotonic() - started,
            "checkedForwardPasses": 240, "checkedTraceRows": trace_count, "checkedGenerations": generation_count,
            "initialProbabilityDelta": native_delta, "independentMaximumDeltas": stats, "arms": arms,
            "trainingUpdates": 0, "newPrompts": 0, "inputsUnchanged": True, "qualityImprovementClaimed": False}


def main():
    parser = argparse.ArgumentParser(description=__doc__)
    for name in ("run", "archive", "tokenizer", "output"):
        parser.add_argument("--" + name, type=Path, required=True)
    args = parser.parse_args()
    require(not args.output.exists() and not args.output.is_symlink(), "existing verification output")
    result = verify(args.run, args.archive, args.tokenizer)
    with args.output.open("x") as stream:
        json.dump(result, stream, indent=2, allow_nan=False)
        stream.write("\n")
    print(json.dumps({k: v for k, v in result.items() if k != "arms"}, indent=2))


if __name__ == "__main__":
    main()
