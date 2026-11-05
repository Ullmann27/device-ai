#!/usr/bin/env python3
"""Source-bound replay checks for the completed gradient diagnostic."""
import argparse
import contextlib
import importlib.util
import io
import json
from pathlib import Path
import shutil
import tempfile
from unittest.mock import patch

HERE = Path(__file__).resolve().parent
SPEC = importlib.util.spec_from_file_location("gradient_replay_target", HERE / "run_gradient_access.py")
RUN = importlib.util.module_from_spec(SPEC)
SPEC.loader.exec_module(RUN)


def inventory(root):
    return {str(p.relative_to(root)): RUN.digest(p) for p in sorted(root.rglob("*")) if p.is_file()}


def change_log(root, profile, kind, key, value):
    target = root / (profile + ".stdout.tsv")
    lines = target.read_text().splitlines()
    for index, line in enumerate(lines):
        if line.startswith(kind + "\t"):
            fields = dict(part.split("=", 1) for part in line.split("\t")[1:])
            fields[key] = value
            lines[index] = kind + "\t" + "\t".join(k + "=" + v for k, v in fields.items())
            break
    else:
        raise ValueError("mutation target missing")
    target.write_text("\n".join(lines) + "\n")
    receipt = root / (profile + ".receipt.json")
    record = json.loads(receipt.read_text())
    record["stdoutSha256"] = RUN.digest(target)
    receipt.write_bytes(RUN.CORE.encoded(record))


def main():
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("--run", type=Path, required=True)
    parser.add_argument("--output", type=Path, required=True)
    args = parser.parse_args()
    original = args.run.resolve(strict=True)
    registration = HERE / "gradient-registration.json"
    registration_sha = RUN.digest(registration)
    first = json.loads((original / "S0.receipt.json").read_text())["command"]
    jars = first[first.index("-cp") + 1].split(":")
    paths = {"harness": Path(jars[0]), "runtime": Path(jars[1]), "stdlib": Path(jars[2]),
             "models": Path(first[5]), "tokenizer": Path(first[6]), "probes": Path(first[7])}
    result = []

    def check(label, root, success=False, source=HERE, reg=registration, expected=registration_sha):
        before = inventory(root)
        rejected = None
        with patch.object(RUN, "HERE", source), patch.object(RUN.CORE, "launch", side_effect=AssertionError("unexpected model execution")) as launcher:
            with contextlib.redirect_stdout(io.StringIO()):
                try:
                    RUN.execute(reg, expected, paths, root)
                except (ValueError, FileNotFoundError, FileExistsError, KeyError) as error:
                    rejected = type(error).__name__ + ": " + str(error)
        assert launcher.call_count == 0 and inventory(root) == before
        assert (rejected is None) == success, (label, rejected)
        result.append({"case": label, "status": "PASS", "jvmLaunches": 0, "persistentFilesUnchanged": True, "rejection": rejected})

    before = inventory(original)
    check("actual_complete_resume", original, success=True)
    mutations = (("missing_first_receipt", lambda root: (root / "S0.receipt.json").unlink()),
                 ("missing_last_receipt", lambda root: (root / "D0.receipt.json").unlink()),
                 ("rebound_bad_derivative", lambda root: change_log(root, "S0", "DIRECTION", "numericalDerivative", "999")),
                 ("rebound_unrestored_array", lambda root: change_log(root, "D0", "DONE", "finalParameterIdentity", "0" * 64)))
    for label, mutation in mutations:
        with tempfile.TemporaryDirectory(prefix="gradient-replay-") as temporary:
            root = Path(temporary) / "run"
            shutil.copytree(original, root)
            mutation(root)
            check(label, root)
    with tempfile.TemporaryDirectory(prefix="gradient-binding-") as temporary:
        folder = Path(temporary)
        root = folder / "run"
        shutil.copytree(original, root)
        check("valid_private_copy", root, success=True)
        sources = folder / "sources"
        sources.mkdir()
        for name in (*RUN.SOURCES, "run_diagnostics.py"):
            shutil.copyfile(HERE / name, sources / name)
        (sources / "LmGradientAccess.kt").write_text("changed source")
        check("changed_compiled_gradient_source", root, source=sources)
        changed = json.loads(registration.read_text())
        changed["limits"]["heap"] = "9g"
        reg = folder / "changed-registration.json"
        reg.write_bytes(RUN.CORE.encoded(changed))
        check("rehashing_cannot_increase_budget", root, reg=reg, expected=RUN.digest(reg))
    assert inventory(original) == before
    report = {"status": "GRADIENT_SOURCE_BOUND_REPLAY_PASS", "registrationSha256": registration_sha,
              "checkerSha256": RUN.digest(Path(__file__)), "controllerSha256": RUN.digest(HERE / "run_gradient_access.py"),
              "cases": result, "jvmLaunches": 0, "originalFilesUnchanged": True, "originalInventory": before,
              "transientExclusiveLockUsed": True}
    with args.output.open("x") as stream:
        json.dump(report, stream, indent=2)
        stream.write("\n")
    print(json.dumps({"status": report["status"], "cases": len(result), "jvmLaunches": 0}))


if __name__ == "__main__":
    main()
