#!/usr/bin/env python3
"""Detached model-quality benchmark with sealed local controls and exact scoring."""
import argparse
import hashlib
import json
import random
import secrets
import sys
from collections import Counter, defaultdict
from pathlib import Path

SCHEMA = "continuum.intelligence.v1"
DOMAINS = ("context", "logic", "memory", "planning", "truth", "tool_choice")


def digest(value):
    return hashlib.sha256(json.dumps(value, sort_keys=True, ensure_ascii=False,
                                   separators=(",", ":")).encode()).hexdigest()


def build_case(i, rng):
    name1, name2 = rng.sample(("Mira", "Lea", "Nora", "Lina", "Tessa", "Jana"), 2)
    color1, color2 = rng.sample(("rot", "blau", "gruen", "gelb", "violett", "orange"), 2)
    code1, code2 = rng.sample(range(100, 999), 2)
    domain = DOMAINS[i % len(DOMAINS)]
    if domain == "context":
        prompt = (f"Dialog A: {name1} legt den {color1}en Ordner ab. {name2} verwahrt den {color2}en Ordner. "
                  f"Dialog B: {name1} sagt: 'Mein Ordner soll jetzt abgeholt werden.' "
                  f"Anschlussfrage: Welche Farbe hat der Ordner, auf den 'mein' sich bezieht? "
                  'Antworte ausschließlich als JSON mit Feld "farbe".')
        expected = {"farbe": color1}
    elif domain == "logic":
        cost, balance = rng.randint(10, 90), rng.randint(25, 110)
        approved = rng.choice([True, False])
        prompt = (f"Ein Auftrag darf genau dann ausgeführt werden, wenn Guthaben >= Kosten UND Freigabe vorliegt. "
                  f"Guthaben={balance}, Kosten={cost}, Freigabe={'ja' if approved else 'nein'}. "
                  'Antworte ausschließlich als JSON mit booleschem Feld "erlaubt".')
        expected = {"erlaubt": balance >= cost and approved}
    elif domain == "memory":
        prompt = (f"Gespräch Montag: Für Projekt {name1} wurde Kennzahl {code1} bestätigt. "
                  f"Gespräch Dienstag: ausdrückliche Korrektur: {code1} ist veraltet, jetzt gilt {code2}. "
                  f"Gespräch Mittwoch: Was gilt aktuell für {name1}? "
                  'Antworte ausschließlich als JSON mit den Feldern "aktuell" (Zahl) und "alt_ungueltig" (bool).')
        expected = {"aktuell": code2, "alt_ungueltig": True}
    elif domain == "planning":
        validated, permission = rng.choice([True, False]), rng.choice([True, False])
        next_step = "permission_request" if not permission else ("execute" if validated else "validate")
        prompt = (f"Aufgabe: lokal vorhandenen Prüfbericht versenden. Erforderlich: zuerst Besitzerfreigabe, "
                  f"dann Inhaltsprüfung, dann Versand. Freigabe={'ja' if permission else 'nein'}, "
                  f"Inhaltsprüfung={'fertig' if validated else 'offen'}. Was ist der einzig nächste Schritt? "
                  'Antworte ausschließlich als JSON mit Feld "next_step"; Werte permission_request, validate, execute.')
        expected = {"next_step": next_step}
    elif domain == "truth":
        prompt = (f"Quelle A (geprüftes Testprotokoll): Kennzahl für {name1}={code1}. "
                  f"Quelle B (unbelegte Vermutung): Kennzahl für {name1}={code2}. "
                  f"Welche Zahl ist belegt, und ist die zweite belegt? "
                  'Antworte ausschließlich als JSON mit Zahl "belegt" und booleschem Feld "vermutung_belegt".')
        expected = {"belegt": code1, "vermutung_belegt": False}
    else:
        authorized = rng.choice([True, False])
        prompt = (f"Nutzerauftrag: Lies das lokale Diagnoseprotokoll {name1}. "
                  f"Berechtigung zum Lesen={'erteilt' if authorized else 'verweigert'}. "
                  'Welches Werkzeug darf jetzt gewählt werden? Kein Werkzeug ausführen. '
                  'Antworte ausschließlich als JSON mit Feld "tool"; Werte local_read oder none.')
        expected = {"tool": "local_read" if authorized else "none"}
    return {"id": f"S{i+1:04d}", "domain": domain, "prompt": prompt, "expected": expected}


def generate(count, split, seed_bytes=None):
    if not 1 <= count <= 500:
        raise ValueError("Count must be 1..500")
    if split not in ("development", "holdout"):
        raise ValueError("Invalid split")
    seed = seed_bytes if seed_bytes is not None else secrets.token_bytes(32)
    entropy = hashlib.sha256(seed + split.encode()).digest()
    rng = random.Random(int.from_bytes(entropy, "big"))
    cases = [build_case(i, rng) for i in range(count)]
    return {"schema": SCHEMA, "split": split, "suite_id": digest(cases), "cases": cases,
            "privacy": "synthetic; holdout cases and expected answers stay with evaluator"}


def export_prompts(suite):
    validate_suite(suite)
    return {"schema": SCHEMA, "suite_id": suite["suite_id"], "split": suite["split"],
            "cases": [{k: c[k] for k in ("id", "domain", "prompt")} for c in suite["cases"]]}


def validate_suite(suite):
    if suite.get("schema") != SCHEMA or suite.get("split") not in ("development", "holdout"):
        raise ValueError("Invalid suite schema/split")
    cases = suite.get("cases", [])
    if not cases or len(set(c["id"] for c in cases)) != len(cases):
        raise ValueError("Suite missing cases or duplicate IDs")
    if digest(cases) != suite.get("suite_id"):
        raise ValueError("Suite integrity mismatch")
    for c in cases:
        if c.get("domain") not in DOMAINS or not isinstance(c.get("expected"), dict):
            raise ValueError("Invalid suite case")


def grade(suite, responses, provenance, model_ref, app_ref):
    validate_suite(suite)
    if provenance not in ("synthetic_selftest", "device_model_declared"):
        raise ValueError("Invalid provenance")
    mapped = {}
    for record in responses:
        cid = record.get("id")
        if cid in mapped or cid is None:
            raise ValueError("Duplicate/missing response ID")
        mapped[cid] = record.get("answer")
    valid_ids = {c["id"] for c in suite["cases"]}
    if set(mapped) - valid_ids:
        raise ValueError("Unknown response IDs")
    per_case, domain_counts = [], defaultdict(Counter)
    for case in suite["cases"]:
        ans = mapped.get(case["id"])
        status = "NOT_RUN"
        if case["id"] in mapped:
            correct = isinstance(ans, dict) and set(ans) == set(case["expected"])
            if correct:
                correct = all(type(ans[key]) is type(value) and ans[key] == value
                              for key, value in case["expected"].items())
            status = "PASS" if correct else "FAIL"
        domain_counts[case["domain"]][status] += 1
        per_case.append({"id": case["id"], "domain": case["domain"], "status": status})
    summary = Counter(x["status"] for x in per_case)
    attempted = summary["PASS"] + summary["FAIL"]
    return {"schema": SCHEMA, "suite_id": suite["suite_id"], "split": suite["split"],
            "provenance": provenance, "inference_execution_verified": False,
            "model_ref": model_ref, "app_ref": app_ref,
            "totals": {"planned": len(per_case), "attempted": attempted,
                       "pass": summary["PASS"], "fail": summary["FAIL"],
                       "not_run": summary["NOT_RUN"],
                       "accuracy_attempted_pct": round(100 * summary["PASS"] / attempted, 2) if attempted else None},
            "domain_counts": dict(domain_counts), "per_case": per_case,
            "limits": ["Exact structured outputs only; no free-text semantic quality measurement",
                       "Tool selection does not show tool execution",
                       "Inferences and execution receipts are not verified by this offline scorer"]}


def compare_scores(left, right):
    if (left.get("suite_id") != right.get("suite_id") or left.get("split") != right.get("split")
            or left.get("provenance") != "device_model_declared"
            or right.get("provenance") != "device_model_declared"):
        return {"status": "NOT_COMPARABLE", "reason": "Suite/split/provenance differs"}
    x, y = left["totals"], right["totals"]
    if x["not_run"] or y["not_run"] or not x["attempted"]:
        return {"status": "NOT_COMPARABLE", "reason": "Incomplete benchmark"}
    return {"status": "DECLARED_MODEL_COMPARISON_UNVERIFIED", "suite_id": left["suite_id"],
            "baseline_pass": x["pass"], "candidate_pass": y["pass"],
            "delta_pass_cases": y["pass"] - x["pass"],
            "reason": "Execution provenance remains self-declared until device receipts are validated"}


def write_new(path, payload):
    dest = Path(path)
    dest.parent.mkdir(parents=True, exist_ok=True)
    if dest.exists():
        raise FileExistsError(f"Refusing overwrite: {dest}")
    dest.write_text(json.dumps(payload, ensure_ascii=False, indent=2) + "\n", encoding="utf-8")


def load(path):
    return json.loads(Path(path).read_text(encoding="utf-8"))


def main(argv=None):
    p = argparse.ArgumentParser(description=__doc__)
    sub = p.add_subparsers(dest="command", required=True)
    gen = sub.add_parser("generate")
    gen.add_argument("--split", choices=("development", "holdout"), required=True)
    gen.add_argument("--count", type=int, default=36)
    gen.add_argument("--seed-file", help="Optional local seed file; never committed or reported")
    gen.add_argument("--output", required=True)
    exp = sub.add_parser("export-prompts")
    exp.add_argument("suite"); exp.add_argument("--output", required=True)
    grade_p = sub.add_parser("grade")
    grade_p.add_argument("suite"); grade_p.add_argument("responses")
    grade_p.add_argument("--provenance", choices=("synthetic_selftest", "device_model_declared"), required=True)
    grade_p.add_argument("--model-ref", required=True); grade_p.add_argument("--app-ref", required=True)
    grade_p.add_argument("--output", required=True)
    cmp = sub.add_parser("compare")
    cmp.add_argument("baseline"); cmp.add_argument("candidate")
    cmp.add_argument("--output", required=True)
    demo = sub.add_parser("selftest-responses")
    demo.add_argument("suite"); demo.add_argument("--output", required=True)
    args = p.parse_args(argv)
    if args.command == "generate":
        seed = Path(args.seed_file).read_bytes() if args.seed_file else None
        result = generate(args.count, args.split, seed)
    elif args.command == "export-prompts":
        result = export_prompts(load(args.suite))
    elif args.command == "grade":
        result = grade(load(args.suite), load(args.responses), args.provenance, args.model_ref, args.app_ref)
    elif args.command == "compare":
        result = compare_scores(load(args.baseline), load(args.candidate))
    else:
        suite = load(args.suite)
        validate_suite(suite)
        result = [{"id": c["id"], "answer": c["expected"]} for c in suite["cases"]]
    write_new(args.output, result)
    print(json.dumps({"output": args.output, "status": "GENERATED", "suite_id":
                      result.get("suite_id") if isinstance(result, dict) else None}, ensure_ascii=False))
    return 0


if __name__ == "__main__":
    try:
        sys.exit(main())
    except (OSError, ValueError, KeyError, TypeError, json.JSONDecodeError) as exc:
        print(f"BENCH_ERROR: {exc}", file=sys.stderr)
        sys.exit(2)