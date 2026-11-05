#!/usr/bin/env python3
"""Exercise real controller methods at a blocked JVM-launch boundary."""
import argparse
import base64
import contextlib
import hashlib
import importlib.util
import io
import json
from pathlib import Path
import shutil
import struct
import tempfile
from unittest.mock import patch

HERE = Path(__file__).resolve().parent
SPEC = importlib.util.spec_from_file_location("frozen_controller", HERE / "run_diagnostics.py")
RUN = importlib.util.module_from_spec(SPEC)
SPEC.loader.exec_module(RUN)


def inventory(root):
    return {str(p.relative_to(root)): RUN.digest(p) for p in sorted(root.rglob("*")) if p.is_file()}


def clone(source, target):
    shutil.copytree(source, target)
    for arm in RUN.arms():
        for suffix in (".started.json", ".receipt.json"):
            path = target / (arm["id"] + suffix)
            value = json.loads(path.read_text())
            value["command"] = [item.replace(str(source), str(target)) for item in value["command"]]
            path.write_bytes(RUN.encoded(value))
    summary = json.loads((target / "summary.json").read_text())
    summary["receiptHashes"] = {a["id"]: RUN.digest(target / (a["id"] + ".receipt.json")) for a in RUN.arms()}
    (target / "summary.json").write_bytes(RUN.encoded(summary))


def rewrite_log(root, arm, transform):
    log = root / (arm + ".stdout.tsv")
    log.write_bytes(transform(log.read_bytes()))
    receipt = root / (arm + ".receipt.json")
    value = json.loads(receipt.read_text())
    value["stdoutSha256"] = RUN.digest(log)
    receipt.write_bytes(RUN.encoded(value))


def alter_parity(raw):
    lines = raw.decode().splitlines()
    for index, line in enumerate(lines):
        if not line.startswith("PROBE\t"):
            continue
        fields = dict(piece.split("=", 1) for piece in line.split("\t")[1:])
        p = list(struct.unpack(">512f", base64.b64decode(fields["probabilitiesF32BE"])))
        allowed = [i for i in range(512) if i not in (510, 511)]
        high = max(allowed, key=lambda i: p[i])
        low = min(allowed, key=lambda i: p[i])
        amount = min(.001, p[high] / 4)
        assert amount > 1e-5
        p[high] -= amount
        p[low] += amount
        encoded = struct.pack(">512f", *p)
        p = struct.unpack(">512f", encoded)
        fields["probabilitiesF32BE"] = base64.b64encode(encoded).decode()
        fields["topAllowedId"] = str(max([i for i in range(512) if i != 510], key=lambda i: p[i]))
        fields["eosRank"] = str(1 + sum(p[i] > p[511] for i in range(512) if i != 510))
        lines[index] = "PROBE\t" + "\t".join(k + "=" + v for k, v in fields.items())
        break
    return ("\n".join(lines) + "\n").encode()


def main():
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("--run", type=Path, required=True)
    parser.add_argument("--archive", type=Path, required=True)
    parser.add_argument("--output", type=Path, required=True)
    args = parser.parse_args()
    original = args.run.resolve(strict=True)
    frozen = HERE / "inference-registration.json"
    reg_sha = RUN.digest(frozen)
    reg = json.loads(frozen.read_text())
    first = json.loads((original / "S0.receipt.json").read_text())["command"]
    classpath_index = first.index("-cp") + 1
    jars = first[classpath_index].split(":")
    assert first[classpath_index + 1] == "at.heinz.deviceai.learning.lm.LmChannelDiagnostic"
    paths = {"archive": args.archive.resolve(strict=True), "tokenizer": Path(first[classpath_index + 3]),
             "harness": Path(jars[0]), "runtime": Path(jars[1]), "stdlib": Path(jars[2])}
    results = []

    def check(label, root, registration=frozen, expected_sha=reg_sha, source=HERE, success=False):
        before = inventory(root)
        rejected = None
        with patch.object(RUN, "HERE", source), patch.object(RUN, "launch", side_effect=AssertionError("unexpected JVM launch")) as launch:
            with contextlib.redirect_stdout(io.StringIO()):
                try:
                    RUN.execute(registration, expected_sha, paths, root)
                except (ValueError, FileNotFoundError, FileExistsError, KeyError) as error:
                    rejected = type(error).__name__ + ": " + str(error)
        after = inventory(root)
        assert launch.call_count == 0 and after == before, label
        assert (rejected is None) == success, (label, rejected)
        results.append({"case": label, "status": "PASS", "jvmLaunches": 0,
                        "persistentFilesUnchanged": True, "rejection": rejected})

    original_before = inventory(original)
    check("actual_complete_resume", original, success=True)
    mutations = [
        ("missing_first_receipt", lambda r: (r / "S0.receipt.json").unlink()),
        ("missing_last_receipt", lambda r: (r / "W0_from_W1_l1.receipt.json").unlink()),
        ("changed_stdout", lambda r: (r / "S0.stdout.tsv").write_text("changed\n")),
        ("changed_stderr", lambda r: (r / "S0.stderr.txt").write_text("changed\n")),
        ("missing_checkpoint", lambda r: (r / "inputs/W1.lm1").unlink()),
        ("changed_checkpoint", lambda r: (r / "inputs/S0.lm1").write_bytes(b"changed")),
        ("active_lock", lambda r: (r / "ACTIVE.lock").write_text("active owner\n")),
        ("unregistered_artifact", lambda r: (r / "unregistered").write_text("unexpected\n")),
        ("changed_summary", lambda r: (r / "summary.json").write_text("{}\n")),
        ("rebound_log_forward_parity_failure", lambda r: rewrite_log(r, "S0", lambda b: b.replace(b"instrumentationBitwiseParity=true", b"instrumentationBitwiseParity=false", 1))),
        ("rebound_log_input_mutation", lambda r: rewrite_log(r, "S0", lambda b: b.replace(b"inputsUnchanged=true", b"inputsUnchanged=false", 1))),
        ("rebound_log_initial_parity_failure", lambda r: rewrite_log(r, "W0", alter_parity)),
    ]
    for label, mutation in mutations:
        with tempfile.TemporaryDirectory(prefix="lm1-resume-check-") as temporary:
            root = Path(temporary) / "run"
            clone(original, root)
            mutation(root)
            check(label, root)
    with tempfile.TemporaryDirectory(prefix="lm1-binding-check-") as temporary:
        folder = Path(temporary)
        root = folder / "run"
        clone(original, root)
        check("valid_relocated_synthetic_fixture", root, success=True)
        sources = folder / "sources"
        sources.mkdir()
        for name in RUN.SOURCES:
            shutil.copyfile(HERE / name, sources / name)
        (sources / "LmChannelDiagnostic.kt").write_text("changed")
        check("changed_compiled_source", root, source=sources)
        changed = {**reg, "limits": {**reg["limits"], "processSeconds": 999}}
        bad = folder / "changed-registration.json"
        bad.write_bytes(RUN.encoded(changed))
        check("rehashing_does_not_relax_limits", root, registration=bad, expected_sha=RUN.digest(bad))
    assert inventory(original) == original_before
    result = {"status": "SOURCE_BOUND_RESUME_VERIFICATION_PASS", "registrationSha256": reg_sha,
              "controllerSha256": RUN.digest(HERE / "run_diagnostics.py"),
              "verificationSha256": RUN.digest(Path(__file__)), "cases": results,
              "actualRunFilesBefore": original_before, "actualRunFilesUnchanged": True,
              "jvmLaunches": 0, "transientExclusiveLockUsed": True,
              "fixtureNote": "Negative cases use private copies; relocated command paths and receipt hashes are synthetic test fixtures, not additional historical executions."}
    with args.output.open("x") as stream:
        json.dump(result, stream, indent=2)
        stream.write("\n")
    print(json.dumps({"status": result["status"], "cases": len(results), "jvmLaunches": 0}))


if __name__ == "__main__":
    main()
