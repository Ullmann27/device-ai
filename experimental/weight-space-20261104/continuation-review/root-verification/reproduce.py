#!/usr/bin/env python3
"""Repeat the reviewed text-only audits, exact gate probes and patch application.

No model or real holdout data is loaded. Outputs remain beside this verifier.
The two expected failing subjects are deliberate regression controls.
"""
from __future__ import annotations

import hashlib
import json
from pathlib import Path
import subprocess
import sys
import tempfile
import types


HERE = Path(__file__).resolve().parent
REVIEW = HERE.parent
REPO = REVIEW.parents[2]
PREVIOUS = REVIEW.parent / "recovery-gate-repair"
PIN = "9ceffc26a2159c8dc726c1f13d2882119159c130"
PINS = {
    "source-audit/audit_current_sources.py": "2382bd3c6a1076c0e4d91d16979ab7bf2781bf12d98035962b69573c10b82332",
    "results/audit_published_v3.py": "31af55ca4ce3c4d282a8269c9f9b0d287f92a295ce9493a0441903d633c63c1e",
    "source-audit/build_choice_guard_patch.py": "a6d3d9f847dbc665b2491a916eaefd83e4ed9cf0e0246a3c6a0b904cff4ea303",
    "independent-tests/probe_choice_binding.py": "5954cf5cfd6effe29738f9dae5213287b73430b85f9f076d5d6e3481670cd4a9",
    "independent-tests/probe_prospective_registration.py": "03102af2856607b88d607c0c84caa6647b3fba445485242c625e4a6937ad183b",
    "source-audit/pr508-capacity-and-choice-gates.patch": "87d54102113944c0884081b44cbb05eb6d253d56f8df0c84f9c31bc4f7039d05",
    "source-audit/choice-guard-on-capacity-repair.patch": "7ad80b4bb76aaab3daef41bce585b913de4d0422a3679fb6aa1a86ee7c5a663f",
}


def digest(raw: bytes) -> str:
    return hashlib.sha256(raw).hexdigest()


def require(condition: bool, message: str) -> None:
    if not condition:
        raise RuntimeError(message)


def run(command: list[str], cwd: Path = REPO, expected: int = 0) -> subprocess.CompletedProcess:
    result = subprocess.run(command, cwd=cwd, stdout=subprocess.PIPE, stderr=subprocess.PIPE,
                            timeout=180, check=False)
    require(result.returncode == expected,
            "unexpected exit: " + repr(command) + "\n" + result.stderr.decode(errors="replace")[-4000:])
    return result


def write_json(path: Path, data: dict) -> bytes:
    raw = (json.dumps(data, indent=2, ensure_ascii=False, allow_nan=False) + "\n").encode()
    path.write_bytes(raw)
    return raw


def audit_repeat(relative: str, expected_report: str, output: str) -> dict:
    path = REVIEW / relative
    module = types.ModuleType("root_repeated_" + path.stem)
    module.__file__ = str(path)
    sys.modules[module.__name__] = module
    exec(compile(path.read_bytes(), str(path), "exec"), module.__dict__)
    report = module.audit(REPO)
    raw = write_json(HERE / output, report)
    require(raw == (REVIEW / expected_report).read_bytes(), "independent audit result differs: " + relative)
    return {"auditor": relative, "output": output, "sha256": digest(raw),
            "byteIdenticalToHelperReport": True, "status": report["status"]}


def main() -> None:
    for path, expected in PINS.items():
        require(digest((REVIEW / path).read_bytes()) == expected, "unreviewed input: " + path)
    audits = [
        audit_repeat("source-audit/audit_current_sources.py", "source-audit/source-review.json", "source-review.json"),
        audit_repeat("results/audit_published_v3.py", "results/published-v3-results.json", "published-v3-results.json"),
    ]
    builder = json.loads(run([sys.executable, "-B", str(REVIEW / "source-audit/build_choice_guard_patch.py")]).stdout)
    require(builder["status"] == "SOURCE_BOUND_ARTIFACTS_MATCH", "patch regeneration mismatch")
    static_path = REVIEW / "independent-tests/audit_choice_sources.py"
    run([sys.executable, "-B", str(static_path), "--report", str(HERE / "independent-source-comparison.json")])
    require((HERE / "independent-source-comparison.json").read_bytes()
            == (REVIEW / "independent-tests/independent-source-comparison.json").read_bytes(),
            "independent AST comparison differs")
    manifest = json.loads((REVIEW / "source-audit/choice-guard-manifest.json").read_bytes())
    sources = manifest["sources"]
    original_bindings = {}
    for variant, item in sources.items():
        raw = run(["git", "show", PIN + ":" + item["sourcePath"]]).stdout
        blob = run(["git", "rev-parse", PIN + ":" + item["sourcePath"]]).stdout.decode().strip()
        require(blob == item["sourceGitBlob"] and digest(raw) == item["sourceSha256"], "original Git identity mismatch")
        require(raw == (REVIEW / "source-audit" / item["originalFixture"]).read_bytes(), "original fixture mismatch")
        original_bindings[variant] = {"blob": blob, "sha256": digest(raw), "fixtureByteIdentical": True}
    patch_checks = []
    for mode, filename in (("original", "pr508-capacity-and-choice-gates.patch"),
                           ("capacity-repair", "choice-guard-on-capacity-repair.patch")):
        with tempfile.TemporaryDirectory(prefix="patch-application-", dir=HERE) as temporary:
            root = Path(temporary)
            run(["git", "init", "--quiet"], root)
            for variant, item in sources.items():
                baseline = ((REVIEW / "source-audit" / item["originalFixture"]) if mode == "original"
                            else PREVIOUS / "fixtures" / variant / "run_registered_study.py.txt")
                target = root / item["sourcePath"]
                target.parent.mkdir(parents=True, exist_ok=True)
                target.write_bytes(baseline.read_bytes())
            patch = REVIEW / "source-audit" / filename
            run(["git", "apply", "--check", str(patch)], root)
            run(["git", "apply", str(patch)], root)
            for item in sources.values():
                actual = (root / item["sourcePath"]).read_bytes()
                require(actual == (REVIEW / "source-audit" / item["patchedFixture"]).read_bytes(),
                        "applied patch differs from reviewed fixture")
            patch_checks.append({"baseline": mode, "patch": filename, "sha256": digest(patch.read_bytes()),
                                 "appliedToFreshTemporaryTree": True, "allThreeFinalFixturesByteIdentical": True})
    fixed_arguments = []
    for variant in ("long", "suffix"):
        item = sources[variant]
        fixed_arguments.extend(["--" + variant + "-runner", str(REVIEW / "source-audit" / item["patchedFixture"]),
                                "--" + variant + "-sha256", item["patchedSourceSha256"]])
    probes = []
    cases = [("probe_choice_binding.py", "original", 3, 28),
             ("probe_choice_binding.py", "capacity-repair", 3, 28),
             ("probe_choice_binding.py", "choice-repair", 31, 0),
             ("probe_prospective_registration.py", "choice-repair", 6, 0)]
    for script, phase, passed, failed in cases:
        suffix = "registration" if "registration" in script else "choice-binding"
        output = phase + "-" + suffix + ".json"
        command = [sys.executable, "-B", str(REVIEW / "independent-tests" / script),
                   "--phase", phase, "--report", str(HERE / output)]
        if phase == "choice-repair":
            command.extend(fixed_arguments)
        process = run(command, expected=int(bool(failed)))
        report = json.loads((HERE / output).read_bytes())
        require((report["passed"], report["failed"]) == (passed, failed), "unexpected regression counts")
        require(report["actualModelProcesses"] == 0 and report["realHoldoutRead"] is False, "model/holdout boundary violated")
        probes.append({"script": script, "phase": phase, "report": output,
                       "passed": passed, "failed": failed, "exitCode": process.returncode,
                       "expectedOutcomeMatched": True})
    cli_checks = []
    for variant in ("long", "suffix"):
        source = REVIEW / "source-audit" / sources[variant]["patchedFixture"]
        result = run([sys.executable, "-B", str(source), "test", "--jar", "/nonexistent/synthetic.jar",
                      "--stdlib", "/nonexistent/synthetic-stdlib.jar", "--inputs", "/nonexistent/synthetic-inputs",
                      "--output", "/nonexistent/synthetic-output", "--source-commit", PIN], expected=2)
        message = result.stderr.decode()
        require("test requires --development-choice, --before-test-verification, and --before-test-verification-sha256" in message,
                "CLI did not reject absent choice authorization before source/input access")
        require("Traceback" not in message, "CLI reached constructor unexpectedly")
        cli_checks.append({"runner": variant, "missingChoiceArgumentsRejectedByParser": True, "exitCode": 2})
    report = {"schemaVersion": 1, "status": "ROOT_REPEAT_PASS", "reviewedCommit": PIN,
              "verifierSha256": digest(Path(__file__).read_bytes()), "pinnedInputs": PINS,
              "auditRepeats": audits, "sourceBoundGeneratorResult": builder,
              "independentAstReportByteIdentical": True, "originalGitSourceBindings": original_bindings,
              "patchApplications": patch_checks, "gateProbeRepeats": probes, "cliChecks": cli_checks,
              "newChoiceAndRegistrationCases": 37, "newChoiceAndRegistrationPassed": 37,
              "historical165CaseSuiteRepeated": False, "actualModelProcesses": 0, "realHoldoutRead": False,
              "historicalRegistrationsOrReceiptsModified": False, "activeForeignRunnersModified": False,
              "modelPromotion": False}
    write_json(HERE / "verification.json", report)
    print(json.dumps({key: report[key] for key in ("status", "reviewedCommit", "newChoiceAndRegistrationPassed",
                                                  "actualModelProcesses", "realHoldoutRead")}, indent=2))


if __name__ == "__main__":
    main()
