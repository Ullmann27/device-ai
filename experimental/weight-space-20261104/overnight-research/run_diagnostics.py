#!/usr/bin/env python3
"""Bounded, immutable-receipt inference diagnostics; no training entry point."""
from __future__ import annotations

import argparse
import base64
import hashlib
import json
import math
import os
from pathlib import Path
import signal
import shutil
import struct
import subprocess
import sys
import time
import zipfile

HERE = Path(__file__).resolve().parent
ARCHIVE = "428d62b308e308afd9c971ff4974981e96b9dc8a783d80e3653c226983dcc016"
TOKENIZER = "94375c73d1f6e2d04b790738580143fa8c1b9b456d40fa7eb6b4009877bd892f"
RUNTIME = "8075d799b42f3b4f23f2f154af09685f52074503b0a4103ba336a618a3be1950"
STDLIB = "fb169596659a518357c4b2c16f43dc75ab1c4980565ed4b4a317a050e5e39006"
MODELS = {
    "S0": "70cb6ed4020042c4ced21bf0c58dab1b83ee2a51655fdbec41279cd961234435",
    "W0": "af617cf6d223a3e53b44bc3c0578b6924e9c73d8b92f558a981af22990cad04d",
    "S1": "90b58fb58e048bd4eac967f2b0b131373170201660de7a3d330b393a6c8d820f",
    "W1": "0f2b5481cf60d6c6021e9e24d1e5ba70d303da274c18933de29c48a47489cf8c",
}
SOURCES = ("PROTOCOL.md", "probes.json", "LmChannelDiagnostic.kt", "run_diagnostics.py")
STAGES = ("embedding", "l0.recurrent", "l0.mlp", "l1.recurrent", "l1.mlp", "finalNorm")
LIMITS = {"processSeconds": 120, "totalProcessSeconds": 1800, "maxRssKiB": 786432,
          "heap": "1g", "generationTokens": 32, "generationMillis": 5000}


def require(condition, message):
    if not condition:
        raise ValueError(message)


def sha(raw):
    return hashlib.sha256(raw).hexdigest()


def digest(path):
    path = Path(path)
    require(path.is_file() and not path.is_symlink(), "expected regular file: " + str(path))
    with path.open("rb") as stream:
        return hashlib.file_digest(stream, "sha256").hexdigest()


def encoded(value):
    return (json.dumps(value, sort_keys=True, indent=2, allow_nan=False) + "\n").encode()


def write_new(path, raw):
    with Path(path).open("xb") as stream:
        stream.write(raw)


def arms():
    result = [{"id": arm, "receiver": arm, "donor": arm, "block": "native"} for arm in MODELS]
    for receiver, donor in (("S1", "S0"), ("S0", "S1"), ("W1", "W0"), ("W0", "W1")):
        for block in ("tok", "nf", "l0", "l1"):
            result.append({"id": f"{receiver}_from_{donor}_{block}", "receiver": receiver, "donor": donor, "block": block})
    return result


def probe_bytes():
    probes = json.loads((HERE / "probes.json").read_text())["probes"]
    require(len(probes) == len({p["id"] for p in probes}) == 12, "probe identity invalid")
    raw = "".join(p["id"] + "\t" + base64.b64encode(p["text"].encode()).decode() + "\n" for p in probes).encode()
    return probes, raw


def registration(build_receipt, harness):
    build = json.loads(Path(build_receipt).read_text())
    require(build["exitCode"] == 0 and not build["stderr"], "diagnostic build failed")
    require(build["sourceSha256"] == digest(HERE / "LmChannelDiagnostic.kt"), "build source mismatch")
    require(build["jarSha256"] == digest(harness), "build JAR mismatch")
    java = Path(shutil.which("java")).resolve(strict=True)
    version = subprocess.run([str(java), "-version"], capture_output=True, text=True, timeout=10, check=True)
    return {"schemaVersion": 1, "purpose": "CHANNEL_AND_EOS_DIAGNOSTICS_ONLY", "trainingPermitted": False,
            "protocolCommit": "062256befb0bafc3da5adbf18d7f2e27182e5b48",
            "modelSourceCommit": "59891cfad6dd015f47c187cc688ea94b0026ad7d",
            "sourceHashes": {name: digest(HERE / name) for name in SOURCES},
            "archiveSha256": ARCHIVE, "tokenizerSha256": TOKENIZER, "runtimeJarSha256": RUNTIME,
            "stdlibSha256": STDLIB, "harnessJarSha256": digest(harness), "models": MODELS,
            "probesTsvSha256": sha(probe_bytes()[1]), "arms": arms(), "limits": LIMITS,
            "java": {"path": str(java), "sha256": digest(java), "version": version.stderr},
            "buildReceipt": build, "buildReceiptSha256": digest(build_receipt)}


def verify_registration(reg, paths):
    require(reg["schemaVersion"] == 1 and reg["purpose"] == "CHANNEL_AND_EOS_DIAGNOSTICS_ONLY"
            and reg["trainingPermitted"] is False, "registration purpose changed")
    require(reg["sourceHashes"] == {name: digest(HERE / name) for name in SOURCES}, "diagnostic sources changed")
    require(reg["arms"] == arms() and reg["models"] == MODELS and reg["limits"] == LIMITS, "arms or limits changed")
    expected = {"archive": ARCHIVE, "tokenizer": TOKENIZER, "runtime": RUNTIME, "stdlib": STDLIB,
                "harness": reg["harnessJarSha256"]}
    keys = {"archive": "archiveSha256", "tokenizer": "tokenizerSha256", "runtime": "runtimeJarSha256",
            "stdlib": "stdlibSha256", "harness": "harnessJarSha256"}
    for key, value in expected.items():
        require(reg[keys[key]] == value and digest(paths[key]) == value, "input identity changed: " + key)
    require(reg["probesTsvSha256"] == sha(probe_bytes()[1]), "probe materialization changed")
    require(digest(reg["java"]["path"]) == reg["java"]["sha256"], "Java launcher changed")
    build = reg["buildReceipt"]
    require(build["exitCode"] == 0 and build["sourceSha256"] == reg["sourceHashes"]["LmChannelDiagnostic.kt"]
            and build["jarSha256"] == reg["harnessJarSha256"], "compiled source binding invalid")


def probabilities(record):
    raw = base64.b64decode(record["probabilitiesF32BE"], validate=True)
    require(len(raw) == 2048, "probability vector size invalid")
    values = struct.unpack(">512f", raw)
    require(all(math.isfinite(x) and x >= 0 for x in values) and abs(sum(values) - 1) <= 3e-6,
            "invalid probability distribution")
    return values


def parse_log(raw, arm, reg):
    rows = []
    for line in raw.decode().splitlines():
        fields = line.split("\t")
        pairs = [field.split("=", 1) for field in fields[1:]]
        require(all(len(pair) == 2 for pair in pairs), "malformed output record")
        require(len({pair[0] for pair in pairs}) == len(pairs), "duplicate output key")
        rows.append({"kind": fields[0], **dict(pairs)})
    probes, _ = probe_bytes()
    expected_kinds = ["START"]
    for _ in probes:
        expected_kinds += ["TRACE"] * 6 + ["PROBE"] + (["GENERATION"] if arm["block"] == "native" else [])
    expected_kinds.append("DONE")
    require([row["kind"] for row in rows] == expected_kinds, "missing, extra or unordered output")
    require(all(row.get("arm") == arm["id"] for row in rows), "wrong output arm")
    first, last = rows[0], rows[-1]
    for key in ("receiver", "donor", "block"):
        require(first[key] == arm[key], "wrong intervention identity")
    require(first["receiverSha256"] == MODELS[arm["receiver"]] and first["donorSha256"] == MODELS[arm["donor"]], "wrong checkpoint output")
    require(first["tokenizerSha256"] == TOKENIZER and first["probesSha256"] == reg["probesTsvSha256"], "wrong input output")
    require(first["training"] == "false" and int(first["parameters"]) == (132160 if arm["receiver"][0] == "S" else 6562304), "wrong model scope")
    require(int(first["replacedTensors"]) == (0 if arm["block"] == "native" else 1 if arm["block"] in ("tok", "nf") else 11), "wrong replacement count")
    require(last["probes"] == "12" and last["inputsUnchanged"] == "true" and last["receiverArraysUnchanged"] == "true"
            and last["trainingUpdates"] == last["serializedCheckpoints"] == "0", "completion integrity failure")
    position = 1
    results = {}
    for probe in probes:
        trace_rows = rows[position:position + 6]
        require([row["stage"] for row in trace_rows] == list(STAGES), "wrong trace stages")
        for row in trace_rows:
            require(row["probe"] == probe["id"], "wrong trace probe")
            for key in ("valueRms", "spreadRms", "relativeSpreadRms", "maximumReplicaRange"):
                require(math.isfinite(float(row[key])) and float(row[key]) >= 0, "invalid trace metric")
            require(int(row["replicas"]) == (1 if arm["receiver"][0] == "S" else 8), "wrong replica count")
            require(0 <= int(row["exactlyTiedGroups"]) <= int(row["groups"]), "invalid tied group count")
        row = rows[position + 6]
        require(row["probe"] == probe["id"] and row["instrumentationBitwiseParity"] == "true", "forward parity failed")
        values = probabilities(row)
        require(row["eosId"] == "511" and row["bosId"] == "510", "special token mismatch")
        require(abs(float(row["eosProbability"]) - values[511]) <= 5e-8, "EOS probability mismatch")
        allowed = [i for i in range(512) if i != 510]
        require(int(row["topAllowedId"]) == max(allowed, key=lambda i: values[i]), "top token mismatch")
        require(int(row["eosRank"]) == 1 + sum(values[i] > values[511] for i in allowed), "EOS rank mismatch")
        for key in ("eosLogit", "bestOtherLogit", "eosMargin", "entropyNats"):
            require(math.isfinite(float(row[key])), "nonfinite logit metric")
        if arm["block"] == "native":
            generation = rows[position + 7]
            require(generation["probe"] == probe["id"] and generation["truncated"] == "false", "generation input mismatch")
            require(generation["stop"] in {"EOS", "MAX_TOKENS", "TIME", "CONTEXT"}, "invalid generation stop")
            require(0 <= int(generation["tokens"]) <= 32 and int(generation["millis"]) >= 0, "invalid generation limits")
            base64.b64decode(generation["textBase64"], validate=True)
        results[probe["id"]] = row
        position += 8 if arm["block"] == "native" else 7
    return results


def parity(results):
    maximum = max(abs(a - b) for probe in results["S0"]
                  for a, b in zip(probabilities(results["S0"][probe]), probabilities(results["W0"][probe])))
    require(maximum <= 1e-5, "original widening probability parity failed")
    return {"maximumAbsoluteProbabilityDelta": maximum, "limit": 1e-5, "pass": True}


def launch(command, stdout, stderr, seconds):
    require(sys.platform.startswith("linux") and hasattr(os, "wait4"), "Linux resource accounting required")
    started = time.monotonic()
    timed_out = False
    with stdout.open("xb") as out, stderr.open("xb") as err:
        process = subprocess.Popen(command, stdout=out, stderr=err, start_new_session=True)
        try:
            while True:
                pid, status, usage = os.wait4(process.pid, os.WNOHANG)
                if pid:
                    process.returncode = os.waitstatus_to_exitcode(status)
                    break
                if time.monotonic() - started >= seconds:
                    timed_out = True
                    os.killpg(process.pid, signal.SIGKILL)
                    _, status, usage = os.wait4(process.pid, 0)
                    process.returncode = os.waitstatus_to_exitcode(status)
                    break
                time.sleep(.025)
        except BaseException:
            if process.returncode is None:
                os.killpg(process.pid, signal.SIGKILL)
                _, status, _ = os.wait4(process.pid, 0)
                process.returncode = os.waitstatus_to_exitcode(status)
            raise
    return {"exitCode": process.returncode, "timedOut": timed_out, "elapsedSeconds": time.monotonic() - started,
            "maxRssKiB": usage.ru_maxrss, "userSeconds": usage.ru_utime, "systemSeconds": usage.ru_stime}


def verify_receipt(receipt, arm, reg_sha, command, stdout, stderr):
    require(receipt["schemaVersion"] == 1 and receipt["status"] == "PASS" and receipt["arm"] == arm,
            "failed or foreign receipt")
    require(receipt["registrationSha256"] == reg_sha and receipt["command"] == command, "receipt binding changed")
    require(receipt["stdoutSha256"] == digest(stdout) and receipt["stderrSha256"] == digest(stderr), "receipt log changed")
    require(receipt["exitCode"] == 0 and receipt["timedOut"] is False, "failed process receipt")
    require(math.isfinite(receipt["elapsedSeconds"]) and 0 <= receipt["elapsedSeconds"] <= LIMITS["processSeconds"] + 1,
            "process budget exceeded")
    require(0 < receipt["maxRssKiB"] <= LIMITS["maxRssKiB"], "memory budget exceeded")


def execute(reg_path, reg_sha, paths, output):
    require(digest(reg_path) == reg_sha, "registration digest mismatch")
    raw_registration = reg_path.read_bytes()
    reg = json.loads(raw_registration)
    verify_registration(reg, paths)
    output = Path(output).absolute()
    require(not output.is_symlink(), "output symlink rejected")
    if output.exists():
        require((output / "registration.json").read_bytes() == raw_registration, "output belongs to another registration")
    else:
        output.mkdir(parents=True, exist_ok=False)
        write_new(output / "registration.json", raw_registration)
    lock = output / "ACTIVE.lock"
    write_new(lock, encoded({"pid": os.getpid(), "registrationSha256": reg_sha}))
    try:
        known = {"registration.json", "ACTIVE.lock", "inputs", "summary.json"}
        known.update(a["id"] + suffix for a in arms() for suffix in (".started.json", ".stdout.tsv", ".stderr.txt", ".receipt.json"))
        require(set(p.name for p in output.iterdir()) <= known, "unexpected output artifact")
        inputs = output / "inputs"
        probes, raw_probes = probe_bytes()
        if not inputs.exists():
            inputs.mkdir()
            with zipfile.ZipFile(paths["archive"]) as package:
                for arm, expected in MODELS.items():
                    raw = package.read(f"LM1_Training_Research_20261104/models/{arm}.lm1")
                    require(sha(raw) == expected, "archive checkpoint mismatch")
                    write_new(inputs / (arm + ".lm1"), raw)
            write_new(inputs / "probes.tsv", raw_probes)
        require(not inputs.is_symlink() and set(p.name for p in inputs.iterdir()) == {a + ".lm1" for a in MODELS} | {"probes.tsv"}, "input materialization mismatch")
        for arm, expected in MODELS.items():
            require(digest(inputs / (arm + ".lm1")) == expected, "materialized checkpoint changed")
        require(digest(inputs / "probes.tsv") == reg["probesTsvSha256"], "materialized probes changed")
        receipts, results = {}, {}
        for arm in arms():
            verify_registration(reg, paths)
            spent = sum(r["elapsedSeconds"] for r in receipts.values())
            require(spent < LIMITS["totalProcessSeconds"], "total process budget exceeded")
            command = [reg["java"]["path"], "-Xmx1g", "-cp", ":".join(str(paths[k]) for k in ("harness", "runtime", "stdlib")),
                       "at.heinz.deviceai.learning.lm.LmChannelDiagnostic", str(inputs), str(paths["tokenizer"]),
                       str(inputs / "probes.tsv"), arm["id"], arm["receiver"], arm["donor"], arm["block"]]
            start = output / (arm["id"] + ".started.json")
            stdout = output / (arm["id"] + ".stdout.tsv")
            stderr = output / (arm["id"] + ".stderr.txt")
            receipt_path = output / (arm["id"] + ".receipt.json")
            intent = {"registrationSha256": reg_sha, "arm": arm, "command": command}
            if receipt_path.exists():
                require(start.read_bytes() == encoded(intent), "process intent changed")
                receipt = json.loads(receipt_path.read_bytes())
            else:
                require(not any(p.exists() or p.is_symlink() for p in (start, stdout, stderr, receipt_path)), "partial prior arm blocks resume")
                write_new(start, encoded(intent))
                outcome = launch(command, stdout, stderr, min(LIMITS["processSeconds"], LIMITS["totalProcessSeconds"] - spent))
                receipt = {"schemaVersion": 1, **intent, **outcome,
                           "stdoutSha256": digest(stdout), "stderrSha256": digest(stderr), "status": "FAIL"}
                if outcome["exitCode"] == 0 and not outcome["timedOut"] and outcome["maxRssKiB"] <= LIMITS["maxRssKiB"]:
                    try:
                        parse_log(stdout.read_bytes(), arm, reg)
                        receipt["status"] = "PASS"
                    except (ValueError, KeyError, IndexError) as error:
                        receipt["validationError"] = str(error)
                write_new(receipt_path, encoded(receipt))
            verify_receipt(receipt, arm, reg_sha, command, stdout, stderr)
            results[arm["id"]] = parse_log(stdout.read_bytes(), arm, reg)
            receipts[arm["id"]] = receipt
            if "S0" in results and "W0" in results:
                parity(results)
            print(json.dumps({"arm": arm["id"], "status": receipt["status"], "elapsedSeconds": receipt["elapsedSeconds"], "maxRssKiB": receipt["maxRssKiB"]}), flush=True)
        verify_registration(reg, paths)
        for arm, expected in MODELS.items():
            require(digest(inputs / (arm + ".lm1")) == expected, "checkpoint changed during study")
        require(digest(inputs / "probes.tsv") == reg["probesTsvSha256"], "probes changed during study")
        summary = {"status": "DIAGNOSTIC_EXECUTION_COMPLETE_NO_TRAINING", "registrationSha256": reg_sha,
                   "arms": len(receipts), "probes": len(probes) * len(receipts), "traceRows": len(probes) * len(receipts) * 6,
                   "nativeGenerations": 48, "initialParity": parity(results),
                   "totalProcessSeconds": sum(r["elapsedSeconds"] for r in receipts.values()),
                   "maximumProcessRssKiB": max(r["maxRssKiB"] for r in receipts.values()),
                   "receiptHashes": {a: digest(output / (a + ".receipt.json")) for a in receipts},
                   "inputsUnchanged": True, "trainingUpdates": 0, "modelPromotion": False}
        target = output / "summary.json"
        if target.exists():
            require(target.read_bytes() == encoded(summary), "completed summary changed")
        else:
            write_new(target, encoded(summary))
        return summary
    finally:
        lock.unlink()


def main():
    parser = argparse.ArgumentParser(description=__doc__)
    sub = parser.add_subparsers(dest="action", required=True)
    register = sub.add_parser("register")
    register.add_argument("--build-receipt", type=Path, required=True)
    register.add_argument("--harness", type=Path, required=True)
    register.add_argument("--output", type=Path, required=True)
    run = sub.add_parser("run")
    run.add_argument("--registration", type=Path, required=True)
    run.add_argument("--registration-sha256", required=True)
    for name in ("archive", "tokenizer", "runtime", "stdlib", "harness", "output"):
        run.add_argument("--" + name, type=Path, required=True)
    args = parser.parse_args()
    if args.action == "register":
        write_new(args.output, encoded(registration(args.build_receipt, args.harness)))
        print(json.dumps({"registrationSha256": digest(args.output)}))
    else:
        paths = {name: getattr(args, name).resolve(strict=True) for name in ("archive", "tokenizer", "runtime", "stdlib", "harness")}
        print(json.dumps(execute(args.registration, args.registration_sha256, paths, args.output), indent=2))


if __name__ == "__main__":
    main()
