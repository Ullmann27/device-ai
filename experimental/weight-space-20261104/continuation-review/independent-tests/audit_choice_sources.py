#!/usr/bin/env python3
"""Independently compare the reviewed combined sources with the earlier repair.

This parses text only; it does not import either controller or invoke a process.
"""
from __future__ import annotations

import argparse
import ast
import hashlib
import json
from pathlib import Path


HERE = Path(__file__).resolve().parent
OLD = HERE.parents[1] / "recovery-gate-repair"
NEW = HERE.parent / "source-audit"
EXPECTED = {
    "v1": ("b1bed4338bb187ae6d835a46368cc05773ae96143559af368361103b64a7b327",
           "b1bed4338bb187ae6d835a46368cc05773ae96143559af368361103b64a7b327"),
    "long": ("638d02eaded31c3d7be7d978d6bcf08319dda9db35e948f1e0142008c2d5d387",
             "e1fa9104ec41c926423b8b7d011c05b9e6a298ee794972bc8ff7d0013bfa27b4"),
    "suffix": ("907d1c9635dc0e375c8d0ef68afa6f85f6c27655758a1895281f74ae0cfc0067",
               "67da16654388d322f3735a67f3fbf1d42635ffccf6ac62668ff46b80b47f1942"),
}


def sha(raw: bytes) -> str:
    return hashlib.sha256(raw).hexdigest()


def methods(module: ast.Module) -> dict:
    study = [node for node in module.body if isinstance(node, ast.ClassDef) and node.name == "Study"]
    assert len(study) == 1
    values = {node.name: node for node in study[0].body if isinstance(node, ast.FunctionDef)}
    assert len(values) == len(study[0].body)
    return values


def main() -> None:
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("--report", type=Path, required=True)
    args = parser.parse_args()
    compared = {}
    for kind, expected in EXPECTED.items():
        old_raw = (OLD / "fixtures" / kind / "run_registered_study.py.txt").read_bytes()
        new_raw = (NEW / "fixtures" / kind / "run_registered_study.py.txt").read_bytes()
        assert (sha(old_raw), sha(new_raw)) == expected, "unreviewed source: " + kind
        before, after = ast.parse(old_raw), ast.parse(new_raw)
        if kind == "v1":
            assert old_raw == new_raw
            compared[kind] = {"beforeSha256": expected[0], "afterSha256": expected[1], "byteIdentical": True}
            continue
        assert len(before.body) == len(after.body)
        for left, right in zip(before.body, after.body):
            if isinstance(left, ast.ClassDef) and left.name == "Study":
                continue
            if isinstance(left, ast.FunctionDef) and left.name == "main":
                continue
            assert ast.dump(left) == ast.dump(right), "unexpected top-level change"
        before_methods, after_methods = methods(before), methods(after)
        added = sorted(set(after_methods) - set(before_methods))
        changed = sorted(name for name in before_methods
                         if ast.dump(before_methods[name]) != ast.dump(after_methods[name]))
        assert added == ["study_choice_authorization"] and changed == ["test"]
        assert set(before_methods) <= set(after_methods)
        before_validator = ast.get_source_segment(old_raw.decode(), before_methods["post_capacity_evidence"])
        after_validator = ast.get_source_segment(new_raw.decode(), after_methods["post_capacity_evidence"])
        assert before_validator == after_validator
        cli = next(node for node in after.body if isinstance(node, ast.FunctionDef) and node.name == "main")
        options = {node.args[0].value for node in ast.walk(cli)
                   if isinstance(node, ast.Call) and isinstance(node.func, ast.Attribute)
                   and node.func.attr == "add_argument" and node.args
                   and isinstance(node.args[0], ast.Constant) and isinstance(node.args[0].value, str)}
        required = {"--development-choice", "--before-test-verification", "--before-test-verification-sha256"}
        assert required <= options
        compared[kind] = {"beforeSha256": expected[0], "afterSha256": expected[1],
                          "addedMethods": added, "changedMethods": changed,
                          "allOtherStudyMethodsAstIdentical": True,
                          "capacityValidatorSourceTextIdentical": True,
                          "capacityValidatorSha256": sha(after_validator.encode()),
                          "newCliArguments": sorted(required)}
    report = {"status": "INDEPENDENT_SOURCE_COMPARISON_PASS",
              "probeSha256": sha(Path(__file__).read_bytes()),
              "comparedAgainst": "previously verified c69cb427 capacity repair",
              "combinedPatchSha256": sha((NEW / "pr508-capacity-and-choice-gates.patch").read_bytes()),
              "controllers": compared, "actualModelProcesses": 0, "realHoldoutRead": False,
              "scope": "Read-only AST/text comparison; not a repeated execution of the old 165-case suite."}
    assert report["combinedPatchSha256"] == "87d54102113944c0884081b44cbb05eb6d253d56f8df0c84f9c31bc4f7039d05"
    args.report.write_text(json.dumps(report, indent=2) + "\n")
    print(report["status"])


if __name__ == "__main__":
    main()
