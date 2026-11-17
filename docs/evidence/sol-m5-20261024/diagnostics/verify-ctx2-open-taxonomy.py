#!/usr/bin/env python3
"""Prepare pinned pure-JVM sources and verify the archived, known-open TEST2 taxonomy."""
import collections
import hashlib
import json
from pathlib import Path
import re
import subprocess
import sys

FROZEN_COMMIT = "44c4d743e64f41cde2f6489e2e34ee7ba03ea012"
REPORT_PATH = "docs/research/ctx2-open-b2-report-20261024.json"
REPORT_SHA256 = "be7abf235f5f9539dfbb3c5aa5183c1503faae7fab942f90b74ad4829250ae31"
CONTEXT_SHA256 = "84008954049406050b31b05afca4abea38ba1a3fddda0bc7a31f45a1dfc61a34"
CORPUS_SHA256 = "2a7242bf6a93a2366fb36275f431cfeeba8ff7f651c3033e8617ca73c4c9222a"
TAXONOMY_PATH = "docs/research/CTX2-OPEN-ERROR-TAXONOMY-20261024.json"
SOURCES = [
    "app/src/main/java/at/heinz/deviceai/learning/ContinuumLearningExperience.kt",
    "app/src/main/java/at/heinz/deviceai/learning/ContinuumMemoryDialogue.kt",
    "app/src/main/java/at/heinz/deviceai/learning/ContinuumOrthographyProbe.kt",
    "app/src/main/java/at/heinz/deviceai/learning/LearningSnapshot.kt",
    "app/src/main/java/at/heinz/deviceai/learning/TrainableSubwordEncoder.kt",
    "app/src/test/java/at/heinz/deviceai/intelligence/maxultra/Ctx1DialogueContext.kt",
    "app/src/test/java/at/heinz/deviceai/intelligence/maxultra/Ctx1Corpus.kt",
    "app/src/test/java/at/heinz/deviceai/intelligence/maxultra/Ctx2Corpus.kt",
    "app/src/test/java/at/heinz/deviceai/intelligence/maxultra/Ctx1Experiment.kt",
    "app/src/test/resources/continuum-neural-swb1.base64",
]
EXPECTED_TOTALS = dict(CORRECT=885, WRONG=27, MISSED=0, UNNEEDED_CLARIFY=8, CLARIFIED=80, ABSTAINED=80)


def check(condition, message):
    if not condition:
        raise ValueError(message)


def read_report(root):
    raw = (root / REPORT_PATH).read_bytes()
    check(hashlib.sha256(raw).hexdigest() == REPORT_SHA256, "Original B2 report hash differs")
    report = json.loads(raw)
    check(report["system"] == "B2" and report["totals"] == EXPECTED_TOTALS, "B2 totals differ")
    failures = report["failures"]
    wrong = [record for record in failures if " WRONG:" in record]
    check(len(failures) == 35 and len(wrong) == 27, "Expected all 27 WRONG plus 8 unnecessary clarifications")
    records = {}
    pattern = re.compile(r'^(\S+) #(\d+) WRONG: "([^"]*)" -> "([^"]*)" cited=(.*?) gold=(.*)$')
    for record in wrong:
        match = pattern.fullmatch(record)
        check(match is not None, "Unexpected failure-record syntax")
        ident, turn, input_text, query, citation, gold = match.groups()
        check(ident not in records, "Duplicate WRONG dialog")
        check(turn == "3" and ident.startswith(("ctx2-test-switch-", "ctx2-test-return-")), "Unexpected WRONG case")
        records[ident] = dict(input=input_text, query=query, firstCited=citation, gold=gold)
    return report, records


def prepare(root, work, output):
    _, records = read_report(root)
    subprocess.run(["git", "-C", str(root), "cat-file", "-e", FROZEN_COMMIT + "^{commit}"], check=True)
    manifest = []
    for source in SOURCES:
        raw = subprocess.check_output(["git", "-C", str(root), "show", FROZEN_COMMIT + ":" + source])
        (work / Path(source).name).write_bytes(raw)
        digest = hashlib.sha256(raw).hexdigest()
        if source.endswith("/Ctx1DialogueContext.kt"):
            check(digest == CONTEXT_SHA256, "Frozen B2 context hash differs")
        manifest.append(dict(path=source, sha256=digest))
    (work / "wrong-ids.txt").write_text("\n".join(records) + "\n", encoding="utf-8")
    (output / "frozen-sources.json").write_text(json.dumps(
        dict(commit=FROZEN_COMMIT, files=manifest), ensure_ascii=False, indent=2) + "\n", encoding="utf-8")
    print("Prepared 9 pinned pure-JVM sources and the public SWB1 fixture; no Android build outputs used.")


def verify(root, work, output):
    _, expected = read_report(root)
    trace = json.loads((output / "traces.json").read_text(encoding="utf-8"))
    check(trace["corpusSha256"] == CORPUS_SHA256, "TEST2 corpus hash differs")
    dialogs = trace["dialogs"]
    check(len(dialogs) == 27 and {d["id"] for d in dialogs} == set(expected), "Wrong-dialog coverage differs")
    taxonomy = json.loads((root / TAXONOMY_PATH).read_text(encoding="utf-8"))
    archived = {case["dialogId"]: case for case in taxonomy["cases"]}
    check(len(archived) == 27 and set(archived) == set(expected), "Taxonomy coverage differs")
    counts = collections.Counter()
    for dialog in dialogs:
        last = dialog["turns"][2]
        prior = expected[dialog["id"]]
        case = archived[dialog["id"]]
        check(last["input"] == prior["input"] and last["query"] == prior["query"], "Query reproduction differs")
        check(last["cited"] and last["cited"][0] == prior["firstCited"], "First citation reproduction differs")
        check("Memory(facts=[" + ", ".join(last["gold"]) + "])" == prior["gold"], "Gold reproduction differs")
        check(not set(last["gold"]).intersection(last["scope"]), "Gold unexpectedly occurs in selected scope")
        check(set(last["cited"]).issubset(dialog["facts"]), "Citation outside current dialog facts")
        check(case["input"] == last["input"] and case["rewrittenQuery"] == last["query"]
              and case["firstCited"] == last["cited"][0] and case["gold"] == last["gold"]
              and case["scope"] == last["scope"], "Archived case differs from trace")
        if dialog["family"] == "SWITCH":
            check(not any(e["surface"] == case["intendedEntity"] for e in dialog["registry"]), "Object registration changed")
            check(case["primaryCategory"] == "ENTITY_REGISTRATION_MISSING", "SWITCH taxonomy differs")
        else:
            entity = next(e for e in dialog["registry"] if e["surface"] == case["selectedEntity"])
            check(entity["genders"] == ["M", "F"], "Pet-gender candidates changed")
            check(case["primaryCategory"] == "PRONOUN_GENDER_AND_RECENCY", "RETURN taxonomy differs")
        counts[dialog["family"]] += 1
    check(counts == {"SWITCH": 13, "RETURN": 14}, "Primary counts differ")
    check(taxonomy["overlappingCategoryCounts"] == dict(entity=27, pronoun=27, time=0, source=27, rewriting=27, stale=13),
          "Overlapping counts differ")
    result = dict(status="PASS", evaluationStatus="KNOWN_OPEN_TEST2_DIAGNOSIS",
                  reportPath=REPORT_PATH, reportSha256=REPORT_SHA256,
                  frozenB2Commit=FROZEN_COMMIT, corpusSha256=CORPUS_SHA256,
                  exactQueryAndFirstCitationReproductions=27,
                  goldExcludedFromSelectedScope=27, allCitationsInCurrentDialogFacts=True,
                  primaryCounts=dict(counts), noGradle=True, noNetwork=True)
    (output / "verification.json").write_text(json.dumps(result, ensure_ascii=False, indent=2) + "\n", encoding="utf-8")
    print("PASS: 27/27 exact rewrites+first citations; 13 SWITCH/14 RETURN; taxonomy and scopes verified.")


def main():
    check(len(sys.argv) == 5 and sys.argv[1] in {"prepare", "verify"}, "usage: prepare|verify repository scratch output")
    mode, root, work, output = sys.argv[1:]
    (prepare if mode == "prepare" else verify)(Path(root), Path(work), Path(output))


if __name__ == "__main__":
    main()
