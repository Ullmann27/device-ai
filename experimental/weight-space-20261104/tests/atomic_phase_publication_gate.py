#!/usr/bin/env python3
"""Synthetic failure, visibility, race and constructor-resume publication probes.

Uses the actual driver write_new and constructor with synthetic inputs. No model,
JVM, training or holdout evaluation is launched. Every generated fixture remains
under --workspace (a new temporary directory by default).
"""
from __future__ import annotations

import argparse
from concurrent.futures import ThreadPoolExecutor
import hashlib
import json
from pathlib import Path
import tempfile
import threading
import types
import unittest
from unittest import mock


def digest(path: Path) -> str:
    return hashlib.sha256(path.read_bytes()).hexdigest()


class PublicationGate(unittest.TestCase):
    source: Path
    workspace: Path

    def setUp(self) -> None:
        self.root = self.workspace / self._testMethodName
        self.root.mkdir()
        script = self.root / "run_experiment.py"
        script.write_bytes(self.source.read_bytes())
        self.driver = types.ModuleType("publication_probe")
        self.driver.__file__ = str(script)
        exec(compile(script.read_bytes(), str(script), "exec"), self.driver.__dict__)
        self.target = self.root / "results" / "ladder.json"
        self.payload = {"phase": "ladder", "candidate": "synthetisch – geprüft", "steps": [1, 2, 3]}
        self.process_guard = mock.patch.object(self.driver.subprocess, "Popen",
            side_effect=AssertionError("external processes forbidden in publication probes"))
        self.process_guard.start()
        self.addCleanup(self.process_guard.stop)

    def assert_published(self) -> None:
        self.assertEqual(json.loads(self.target.read_text(encoding="utf-8")), self.payload)
        self.assertTrue(self.target.read_bytes().endswith(b"\n"))

    @staticmethod
    def interrupt_dump(value, stream, **options) -> None:
        stream.write('{"phase": "l')
        stream.flush()
        raise OSError("injected interruption after partial JSON write")

    def test_complete_unicode_publication(self) -> None:
        self.driver.write_new(self.target, self.payload)
        self.assert_published()

    def test_serialization_failure_leaves_no_marker_and_allows_retry(self) -> None:
        with self.assertRaises(TypeError):
            self.driver.write_new(self.target, {"phase": "ladder", "unsupported": object()})
        self.assertFalse(self.driver.path_present(self.target), "failed serialization published a marker")
        self.driver.write_new(self.target, self.payload)
        self.assert_published()

    def test_partial_write_failure_leaves_no_marker_and_allows_retry(self) -> None:
        with mock.patch.object(self.driver.json, "dump", self.interrupt_dump):
            with self.assertRaisesRegex(OSError, "injected interruption"):
                self.driver.write_new(self.target, self.payload)
        self.assertFalse(self.driver.path_present(self.target), "failed write published a marker")
        self.driver.write_new(self.target, self.payload)
        self.assert_published()

    def test_in_progress_json_is_not_visible_under_final_name(self) -> None:
        partial_written = threading.Event()
        finish_write = threading.Event()

        def paused_dump(value, stream, **options):
            serialized = json.dumps(value, **options)
            stream.write(serialized[:12])
            stream.flush()
            partial_written.set()
            if not finish_write.wait(5):
                raise TimeoutError("reader did not release synthetic writer")
            stream.write(serialized[12:])

        with mock.patch.object(self.driver.json, "dump", paused_dump), ThreadPoolExecutor(max_workers=1) as pool:
            writing = pool.submit(self.driver.write_new, self.target, self.payload)
            try:
                self.assertTrue(partial_written.wait(5), "writer did not reach fault boundary")
                visible_while_incomplete = self.driver.path_present(self.target)
            finally:
                finish_write.set()
            writing.result(timeout=5)
        self.assertFalse(visible_while_incomplete, "reader could observe incomplete final JSON")
        self.assert_published()

    def test_existing_marker_is_not_overwritten(self) -> None:
        self.target.parent.mkdir()
        original = b'{"phase": "already committed"}\n'
        self.target.write_bytes(original)
        with self.assertRaises(FileExistsError):
            self.driver.write_new(self.target, self.payload)
        self.assertEqual(self.target.read_bytes(), original)

    def test_existing_invalid_marker_is_not_silently_repaired(self) -> None:
        self.target.parent.mkdir()
        original = b'{"old interrupted marker":'
        self.target.write_bytes(original)
        with self.assertRaises(FileExistsError):
            self.driver.write_new(self.target, self.payload)
        self.assertEqual(self.target.read_bytes(), original)

    def test_existing_symlink_and_referent_are_unchanged(self) -> None:
        self.target.parent.mkdir()
        referent = self.root / "existing.json"
        original = b'{"phase": "original evidence"}\n'
        referent.write_bytes(original)
        self.target.symlink_to(referent)
        with self.assertRaises(FileExistsError):
            self.driver.write_new(self.target, self.payload)
        self.assertTrue(self.target.is_symlink())
        self.assertEqual(self.target.readlink(), referent)
        self.assertEqual(referent.read_bytes(), original)

    def test_dangling_symlink_is_not_replaced_or_followed(self) -> None:
        self.target.parent.mkdir()
        referent = self.root / "missing.json"
        self.target.symlink_to(referent)
        with self.assertRaises(FileExistsError):
            self.driver.write_new(self.target, self.payload)
        self.assertTrue(self.target.is_symlink())
        self.assertEqual(self.target.readlink(), referent)
        self.assertFalse(referent.exists())

    def test_competing_writers_have_exactly_one_complete_winner(self) -> None:
        start = threading.Barrier(2)
        payloads = [{"writer": index, "data": str(index) * 32768} for index in range(2)]

        def publish(index):
            start.wait(timeout=5)
            try:
                self.driver.write_new(self.target, payloads[index])
                return "published", index
            except FileExistsError:
                return "exists", index

        with ThreadPoolExecutor(max_workers=2) as pool:
            outcomes = list(pool.map(publish, range(2)))
        self.assertCountEqual([status for status, _ in outcomes], ["published", "exists"])
        winner = next(index for status, index in outcomes if status == "published")
        self.assertEqual(json.loads(self.target.read_text()), payloads[winner])

    def test_constructor_restarts_after_interrupted_environment_write(self) -> None:
        inputs = self.root / "inputs"
        inputs.mkdir()
        base = inputs / "lm1-baseline.lm1"
        tokenizer = inputs / "lm1-tokenizer.lmbp"
        holdout = inputs / "holdout.txt"
        jar = inputs / "synthetic-never-executed.jar"
        for path in (base, tokenizer, holdout, jar):
            path.write_text("SYNTHETIC NONEXECUTABLE " + path.name + "\n")
        (self.root / "PROTOCOL.md").write_text("SYNTHETIC PROTOCOL\n")
        (self.root / "development.txt").write_text("SYNTHETIC DEVELOPMENT\n")
        self.driver.BASE_SHA = digest(base)
        self.driver.TOKENIZER_SHA = digest(tokenizer)
        self.driver.HOLDOUT_SHA = digest(holdout)
        args = argparse.Namespace(inputs=inputs, output=self.target.parent, jar=jar, holdout=holdout)
        java_queries = []

        def fake_java_version(command, **options):
            self.assertEqual(command, ["java", "-version"])
            java_queries.append(command)
            return types.SimpleNamespace(stderr="SYNTHETIC: Java was not launched")

        with mock.patch.object(self.driver.subprocess, "run", fake_java_version):
            with mock.patch.object(self.driver.json, "dump", self.interrupt_dump):
                with self.assertRaisesRegex(OSError, "injected interruption"):
                    self.driver.Experiment(args)
            resumed = self.driver.Experiment(args)
            environment_path = self.target.parent / "environment.json"
            original = environment_path.read_bytes()
            resumed_again = self.driver.Experiment(args)
        self.assertEqual(len(java_queries), 2, "cached environment should not query Java again")
        self.assertEqual(resumed.environment["baseSha256"], digest(base))
        self.assertEqual(resumed_again.environment, resumed.environment)
        self.assertEqual(environment_path.read_bytes(), original)


class RecordedResult(unittest.TextTestResult):
    def __init__(self, *args, **kwargs):
        super().__init__(*args, **kwargs)
        self.cases = []

    def addSuccess(self, test):
        super().addSuccess(test)
        self.cases.append({"case": test._testMethodName, "pass": True})

    def addFailure(self, test, err):
        super().addFailure(test, err)
        self.cases.append({"case": test._testMethodName, "pass": False, "error": str(err[1])})

    def addError(self, test, err):
        super().addError(test, err)
        self.cases.append({"case": test._testMethodName, "pass": False, "error": str(err[1])})


def main() -> None:
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("source", type=Path, nargs="?", default=Path(__file__).resolve().parent.parent / "run_experiment.py")
    parser.add_argument("--workspace", type=Path)
    args = parser.parse_args()
    source = args.source.resolve()
    workspace = args.workspace or Path(tempfile.mkdtemp(prefix="continuum-publication-gate-"))
    workspace.mkdir(parents=True, exist_ok=True)
    run = Path(tempfile.mkdtemp(prefix="publication-" + digest(source)[:12] + "-", dir=workspace))
    PublicationGate.source, PublicationGate.workspace = source, run
    before = digest(source)
    suite = unittest.defaultTestLoader.loadTestsFromTestCase(PublicationGate)
    result = unittest.TextTestRunner(verbosity=2, resultclass=RecordedResult).run(suite)
    report = {"task": "ATOMIC-PHASE-PUBLICATION-20261104", "sourcePath": str(source),
              "sourceSha256": before, "sourceHashAfterRun": digest(source), "harnessSha256": digest(Path(__file__)),
              "passed": sum(case["pass"] for case in result.cases),
              "failed": sum(not case["pass"] for case in result.cases), "cases": result.cases,
              "realModelProcesses": 0, "realTrainingRuns": 0, "realHoldoutEvaluations": 0}
    report_path = run / "report.json"
    report_path.write_text(json.dumps(report, indent=2) + "\n")
    print(json.dumps({"report": str(report_path), "sha256": digest(report_path),
                      "passed": report["passed"], "failed": report["failed"]}))
    raise SystemExit(0 if result.wasSuccessful() else 1)


if __name__ == "__main__":
    main()
