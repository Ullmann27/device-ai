#!/usr/bin/env python3
"""Source-bound, model-free probe of PR508's intent freeze after interruption.

All checkpoint, runtime, dataset and receipt bytes are synthetic. The real Study
constructor, verify, selection, freeze_evidence, freeze and test methods execute.
Only subprocess.Popen is trapped, before any process exists. No real model or
held-out example is read. The bundled reviewed source is hashed before compile.
Exit 1 records a reproduced gate defect; it is not a training result.
"""
from __future__ import annotations

import argparse
from contextlib import redirect_stdout
from datetime import datetime, timezone
import hashlib
import io
import json
from pathlib import Path
import tempfile
import types
from unittest.mock import patch


REVIEWED_COMMIT = "def5acf9cbba82ee51a53cc06fecc52e9cd0324d"
REVIEWED_SHA256 = "409f87f5a174804d7290c29972d6b4e2bbb19ad293ac1da027122c3f28d14d4f"
REVIEWED_PATH = "experimental/lm1-training-recovery-20261104/run_registered_study.py"


def digest(raw: bytes) -> str:
    return hashlib.sha256(raw).hexdigest()


def load_reviewed(path: Path) -> types.ModuleType:
    raw = path.read_bytes()
    if digest(raw) != REVIEWED_SHA256:
        raise RuntimeError("UNREVIEWED_SOURCE_REJECTED_BEFORE_COMPILE")
    module = types.ModuleType("reviewed_pr508_post_capacity_probe_target")
    module.__file__ = str(path)
    exec(compile(raw, str(path), "exec"), module.__dict__)
    return module


def put(path: Path, raw: bytes) -> str:
    path.parent.mkdir(parents=True, exist_ok=True)
    with path.open("xb") as stream:
        stream.write(raw)
    return digest(raw)


def put_json(path: Path, value: dict) -> None:
    put(path, (json.dumps(value, sort_keys=True, indent=2) + "\n").encode())


def row(kind: str, **fields: object) -> str:
    return "\t".join([kind, *(f"{key}={value}" for key, value in fields.items())]) + "\n"


def fixture(module: types.ModuleType, root: Path, source: Path,
            after_capacity: str, tamper_training_receipt: bool) -> object:
    module.ROOT = root
    module.EXPERIMENT = root / "experimental/lm1-training-recovery-20261104"
    inputs = root / "synthetic-inputs"
    output = root / "synthetic-output"
    jar = root / "synthetic.jar"
    stdlib = root / "synthetic-stdlib.jar"
    jar_hash = put(jar, b"SYNTHETIC_NONEXECUTABLE_JAR")
    stdlib_hash = put(stdlib, b"SYNTHETIC_NONEXECUTABLE_STDLIB")
    input_hashes = {name: put(inputs / name, ("SYNTHETIC " + name).encode())
                    for name in ("lm1-baseline.lm1", "lm1-tokenizer.lmbp", "train.txt")}
    source_hash = put(root / REVIEWED_PATH, source.read_bytes())
    sealed = "experimental/lm1-training-recovery-20261104/intent-data/test.sealed.tsv"
    sealed_hash = put(root / sealed, b"SYNTHETIC TEST SENTINEL; NO OWNER DATA\n")
    registration = {"schemaVersion": 1, "files": {REVIEWED_PATH: source_hash, sealed: sealed_hash},
                    "inputs": input_hashes, "jarSha256": jar_hash, "stdlibSha256": stdlib_hash}
    put_json(module.EXPERIMENT / "registration.json", registration)
    args = argparse.Namespace(output=output, jar=jar, stdlib=stdlib, inputs=inputs,
                              source_commit=REVIEWED_COMMIT)
    study = module.Study(args)
    for variant, successes in (("small", (0, 4, 10, 14)), ("wide", (0, 5, 11, 16))):
        directory = output / ("intent-" + variant)
        checkpoints = []
        for step in (0, 32, 64, 128):
            raw = f"SYNTHETIC_CHECKPOINT {variant} {step}\n".encode()
            checkpoint_sha = digest(raw)
            path = directory / "checkpoints" / (checkpoint_sha + ".lm1")
            put(path, raw)
            checkpoints.append({"step": step, "sha256": checkpoint_sha, "path": str(path)})
        before_log = row("CAPACITY_RESULT", checkpointSha256=checkpoints[0]["sha256"],
                         **{"pass": "true"}).encode()
        before_hash = put(directory / "capacity-before.tsv", before_log)
        log = row("MANIFEST", variant=variant,
                  tokenScheduleSha256=digest(b"SYNTHETIC_MATCHED_TOKEN_SCHEDULE"))
        log += "".join(row("CHECKPOINT", **checkpoint) for checkpoint in checkpoints)
        for step, correct, nll in zip((0, 32, 64, 128), successes, (3.0, 2.0, 1.5, 1.0)):
            log += row("SCORE", split="dev", step=step, correct=correct, total=24, answerNll=nll)
        log += row("INITIAL_CAPACITY_GATE", checkpointSha256=checkpoints[0]["sha256"],
                   logSha256=before_hash, **{"pass": "true"})
        log += row("SELECTION", checkpointSha256=checkpoints[-1]["sha256"], step=128,
                   devCorrect=successes[-1], devTotal=24, devAnswerNll=1.0,
                   testRead="false", inputsUnchanged="true")
        log_hash = put(study.logs / ("intent-" + variant + ".tsv"), log.encode())
        command = ["java", "-Xms64m", "-Xmx3g", "-cp", str(jar) + module.os.pathsep + str(stdlib),
                   module.PACKAGE + "LmIntentStudy", "train", str(inputs / "lm1-baseline.lm1"),
                   str(inputs / "lm1-tokenizer.lmbp"), str(module.EXPERIMENT / "intent-data"),
                   str(directory), variant]
        receipt = {"label": "intent-" + variant, "command": command, "returncode": 0,
                   "timeout": False, "timeoutSeconds": 600, "wallSeconds": 1.0,
                   "registeredBytesUnchanged": True, "logSha256": log_hash,
                   "registrationSha256": study.registration_sha, "sourceCommit": REVIEWED_COMMIT,
                   "jarSha256": jar_hash}
        if tamper_training_receipt and variant == "wide":
            receipt["logSha256"] = "0" * 64
        put_json(study.logs / ("intent-" + variant + ".receipt.json"), receipt)
        if after_capacity == "absent":
            continue
        label = "intent-" + variant + "-capacity-after"
        if after_capacity == "malformed-wide" and variant == "wide":
            put(study.logs / (label + ".tsv"), b"INTERRUPTED_SYNTHETIC_CAPACITY\n")
            put(study.logs / (label + ".receipt.json"), b'{"returncode":')
            continue
        failed_capacity = after_capacity == "failed-wide" and variant == "wide"
        after_log = row("CAPACITY_RESULT", checkpointSha256=checkpoints[-1]["sha256"],
                        **{"pass": "false" if failed_capacity else "true"}).encode()
        after_hash = put(study.logs / (label + ".tsv"), after_log)
        put_json(study.logs / (label + ".receipt.json"), {
            "label": label, "returncode": 1 if failed_capacity else 0, "timeout": False, "timeoutSeconds": 120,
            "registeredBytesUnchanged": True, "registrationSha256": study.registration_sha,
            "sourceCommit": REVIEWED_COMMIT, "jarSha256": jar_hash, "logSha256": after_hash})
    return study


class ProcessBoundaryReached(RuntimeError):
    pass


def exercise(source: Path, name: str, after_capacity: str,
             tamper_training: bool, expected_rejection: bool) -> dict:
    module = load_reviewed(source)
    attempts = []

    def block_process(command: list[str], *args: object, **kwargs: object) -> None:
        attempts.append(command)
        raise ProcessBoundaryReached("MODEL_PROCESS_BLOCKED_BY_TEST")

    with tempfile.TemporaryDirectory(prefix="continuum-pr508-capacity-") as temporary:
        root = Path(temporary)
        with patch.object(module.subprocess, "Popen", side_effect=block_process):
            study = fixture(module, root, source, after_capacity, tamper_training)
            training_before = {p.name: digest(p.read_bytes()) for p in study.logs.glob("intent-*.receipt.json")}
            captured = io.StringIO()
            error = None
            freeze_accepted = False
            with redirect_stdout(captured):
                try:
                    study.freeze()
                    freeze_accepted = (study.out / "selection-freeze.json").is_file()
                    study.test()
                except ProcessBoundaryReached as exc:
                    error = str(exc)
                except (OSError, RuntimeError, ValueError, KeyError) as exc:
                    error = type(exc).__name__ + ": " + str(exc)
            after_receipts = sorted(p.name for p in study.logs.glob("*-capacity-after.receipt.json"))
            unchanged = all(digest((study.logs / n).read_bytes()) == value
                            for n, value in training_before.items())
            actual_rejection = not freeze_accepted and not attempts and error is not None
            authorized_first_test = (freeze_accepted and len(attempts) == 1
                                     and attempts[0][-4] == "test"
                                     and attempts[0][-5] == module.PACKAGE + "LmIntentStudy")
            passed = unchanged and (actual_rejection if expected_rejection else authorized_first_test)
            return {"case": name, "expected": "REJECT_BEFORE_FREEZE_OR_TEST_PROCESS" if expected_rejection
                    else "AUTHORIZE_FIXED_TEST_AFTER_FREEZE", "passed": passed,
                    "freezeAccepted": freeze_accepted,
                    "testProcessStartAttempts": len(attempts), "actualProcessesStarted": 0,
                    "postCapacityReceiptsPresent": after_receipts,
                    "postCapacityFixture": after_capacity,
                    "existingSyntheticReceiptBytesUnchanged": unchanged,
                    "errorOrBoundary": error,
                    "stdout": captured.getvalue().replace(str(root), "<SYNTHETIC_ROOT>"),
                    "firstAttemptedMainAndMode": attempts[0][5:7] if attempts else []}


def main() -> int:
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("--runner", type=Path, default=Path(__file__).parent / "fixtures" /
                        "pr508-def5acf9-run_registered_study.py.txt")
    parser.add_argument("--report", type=Path)
    args = parser.parse_args()
    source = args.runner.resolve()
    load_reviewed(source)
    cases = [exercise(source, "missing_post_capacity_blocks_freeze_and_test", "absent", False, True),
             exercise(source, "malformed_post_capacity_blocks_freeze_and_test", "malformed-wide", False, True),
             exercise(source, "positive_completed_metadata_control", "present", False, False),
             exercise(source, "recorded_capacity_fail_still_allows_limited_task_control", "failed-wide", False, False),
             exercise(source, "tampered_training_receipt_is_rejected", "present", True, True)]
    with tempfile.TemporaryDirectory(prefix="continuum-pr508-source-") as temporary:
        changed = Path(temporary) / "unreviewed.py"
        put(changed, source.read_bytes() + b"\n")
        try:
            load_reviewed(changed)
        except RuntimeError as exc:
            source_guard = str(exc) == "UNREVIEWED_SOURCE_REJECTED_BEFORE_COMPILE"
        else:
            source_guard = False
    cases.append({"case": "unknown_source_is_rejected_before_compile", "passed": source_guard})
    failed = sum(not result["passed"] for result in cases)
    report = {"schemaVersion": 1, "observedUtc": datetime.now(timezone.utc).isoformat(),
              "reviewedCommit": REVIEWED_COMMIT, "reviewedPath": REVIEWED_PATH,
              "reviewedSourceSha256": digest(source.read_bytes()),
              "probeSha256": digest(Path(__file__).read_bytes()),
              "status": "RED_REPRODUCED" if failed else "PASS",
              "scope": "SYNTHETIC_INTENT_FREEZE_DEPENDENCY_CHECK_ONLY",
              "mockedBoundary": "subprocess.Popen only; ROOT and EXPERIMENT relocated to synthetic fixture",
              "syntheticReceiptsAreModelEvidence": False, "actualModelProcesses": 0,
              "realHoldoutRead": False, "cases": cases,
              "passed": len(cases) - failed, "failed": failed, "exitCode": int(bool(failed))}
    if args.report is not None:
        put_json(args.report, report)
    print(json.dumps(report, indent=2))
    return int(bool(failed))


if __name__ == "__main__":
    raise SystemExit(main())
