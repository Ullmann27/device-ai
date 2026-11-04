#!/usr/bin/env python3
"""Probe phase-resume integrity without constructing or running an experiment."""
import argparse
import hashlib
import json
from pathlib import Path
import tempfile
import types
from unittest.mock import patch


def probe(source_path: Path) -> dict:
    source = source_path.read_bytes()
    module = types.ModuleType("support_weight_phase_probe")
    module.__file__ = str(source_path)
    # The reviewed runner defines main behind __name__ == '__main__'. Its
    # Experiment constructor, input checks and model process are never called.
    exec(compile(source, str(source_path), "exec"), module.__dict__)
    results = []
    with tempfile.TemporaryDirectory(prefix="continuum-resume-probe-") as temporary:
        root = Path(temporary)
        target = root / "interrupted.json"

        def interrupted_dump(value, stream, **kwargs):
            stream.write('{"interrupted":')
            raise RuntimeError("synthetic writer interruption")

        with patch.object(module.json, "dump", interrupted_dump):
            try:
                module.write_new(target, {"synthetic": True})
            except RuntimeError as error:
                if str(error) != "synthetic writer interruption":
                    raise
        incomplete_published = target.exists()
        if target.exists():
            try:
                json.loads(target.read_text())
            except json.JSONDecodeError:
                pass
            else:
                incomplete_published = False

        for phase, filename in (("ladder", "ladder.json"),
                                ("continuation", "frozen-selection.json"),
                                ("final", "final.json")):
            folder = root / phase
            folder.mkdir()
            (folder / filename).write_text('{"incomplete":', encoding="utf-8")
            experiment = object.__new__(module.Experiment)
            experiment.out = folder
            try:
                getattr(experiment, phase)()
            except Exception as error:
                results.append({"phase": phase, "silentSkip": False,
                                "rejectionType": type(error).__name__})
            else:
                results.append({"phase": phase, "silentSkip": True})

    return {"sourceSha256": hashlib.sha256(source).hexdigest(),
            "publishedIncompleteFileAfterInterruptedWrite": incomplete_published,
            "resumeCases": results,
            "experimentConstructorExecuted": False, "modelExecuted": False,
            "ownerInputRead": False,
            "scope": "Synthetic phase-resume control flow, not model or capacity acceptance"}


if __name__ == "__main__":
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("runner", type=Path)
    arguments = parser.parse_args()
    result = probe(arguments.runner.resolve())
    print(json.dumps(result, indent=2))
    raise SystemExit(1 if result["publishedIncompleteFileAfterInterruptedWrite"] or
                     any(case["silentSkip"] for case in result["resumeCases"]) else 0)
