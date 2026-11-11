#!/usr/bin/env python3
"""Public synthetic speech-understanding fixtures and fail-closed data contract.

No microphone access, private-chat ingestion, network access or model training.
The native LM1 four-class target is deliberately narrower than the rich meaning.
"""
from __future__ import annotations

import argparse
import collections
import hashlib
import json
from pathlib import Path
import re
from typing import Any

VERSION = 1
SOURCE = "authored:continuum-personal-voice-20261111-v1"
LABELS = {"0": "no_side_effect", "1": "open", "2": "search", "3": "remember"}
# Template IDs are split units. Semantics are shared on purpose; wording is not.
TEMPLATES = {
    "train": [
        ("spoken_repair", "{a}. Äh nein, i mein: {b}. Genau so.", "b"),
        ("live_vs_quote", "„{b}“ ist mein Auftrag. „{a}“ war bloß ein Zitat.", "b"),
        ("rejected_tail", "Bitte {a}. Den Vorschlag „{b}“ lehne ich ab.", "a"),
        ("negated_alternative", "Nicht „{a}“, sondern „{b}“, bitte. Nichts zusätzlich.", "b"),
    ],
    "dev": [
        ("retrospective_revision", "{a} war vor der Korrektur. Gültig bleibt jetzt: {b}.", "b"),
        ("permission_and_discussion", "Freigegeben: {a}. Bei „{b}“ bleibt es beim Besprechen.", "a"),
    ],
}
TARGETS = {
    "train": ["Kamera", "Galerie", "Kalender", "Rechner"],
    "dev": ["Dateimanager", "Notizbuch"],
}
EDGES = [("0", "1"), ("1", "2"), ("2", "3"), ("3", "0")]
WORD = re.compile(r"[^\W_]+", re.UNICODE)


def canonical(value: Any) -> bytes:
    return json.dumps(value, ensure_ascii=False, sort_keys=True, separators=(",", ":")).encode("utf-8")


def sha(value: bytes) -> str:
    return hashlib.sha256(value).hexdigest()


def normalized(text: str) -> str:
    return " ".join(WORD.findall(text.casefold()))


def word_bag(text: str) -> tuple[tuple[str, int], ...]:
    return tuple(sorted(collections.Counter(WORD.findall(text.casefold())).items()))


def phrase(label: str, target: str) -> str:
    # App names are fictional test labels, never inferred installed applications.
    return {
        "0": f"erkläre nur das Wort {target}",
        "1": f"öffne die App {target}",
        "2": f"suche nach dem Wort {target}",
        "3": f"merke dir das Wort {target}",
    }[label]


def action(label: str, target: str) -> dict[str, Any] | None:
    if label == "0":
        return None
    key = {"1": "application", "2": "query", "3": "content"}[label]
    return {"intent": LABELS[label], "parameters": {key: target}}


def protected_content_digest(record: dict[str, Any]) -> str:
    """Bind consent to wording, meaning, audio, source and context, excluding consent."""
    return sha(canonical({key: record[key] for key in (
        "id", "origin", "recognized_transcript", "corrected_transcript", "context", "meaning", "audio"
    )}))


def make_record(identifier: str, split: str, family: str, prompt: str,
                label: str | None, target: str = "", rejected_label: str | None = None,
                pair: str | None = None, speech_act: str = "request") -> dict[str, Any]:
    chosen = action(label, target) if label is not None else None
    forbidden = action(rejected_label, target) if rejected_label is not None else None
    return {
        "schema_version": VERSION,
        "id": identifier.replace("_", "-"),
        "split": split,
        "family_id": family,
        "pair_id": pair,
        "origin": {"kind": "synthetic_authored", "source_ref": SOURCE,
                   "rights": "repository_authorized_synthetic", "uses_private_content": False},
        "consent": {"text_training": False, "audio_retention": False, "cloud_export": False,
                    "third_party_voice_authorized": False, "scope_digest": None},
        "storage": {"classification": "public_research_synthetic", "encryption_required": False},
        "audio": None,
        "recognized_transcript": prompt,
        "corrected_transcript": None,
        "transcript_correction_confirmed": False,
        "context": {"turns": [], "prior_task_refs": []},
        "meaning": {"confirmed": True, "intended_instruction": phrase(label, target) if label is not None else prompt,
                    "speech_act": speech_act, "actions": [chosen] if chosen else [],
                    "forbidden_actions": [forbidden] if forbidden else [],
                    "needs_clarification": False},
        "sensitivity": {"reviewed": True, "approved": True, "categories": []},
        "lm1": {"eligible": label is not None, "label": label, "prompt": prompt if label is not None else None},
        "review": {"status": "research_checked", "reviewer_kind": "synthetic_author"},
    }


def contrasts() -> list[dict[str, Any]]:
    records = []
    for split, templates in TEMPLATES.items():
        for family, template, selected in templates:
            for left, right in EDGES:
                for number, target in enumerate(TARGETS[split]):
                    pair = f"pv1-{split}-{family}-{left}{right}-{number:02d}"
                    for variant, (a, b) in enumerate(((left, right), (right, left))):
                        chosen = a if selected == "a" else b
                        rejected = b if selected == "a" else a
                        prompt = template.format(a=phrase(a, target), b=phrase(b, target))
                        records.append(make_record(f"{pair}-{variant}", split, family, prompt,
                                                   chosen, target, rejected, pair))
    return records


def supervised_replay() -> list[dict[str, Any]]:
    # Newly authored easy examples, never copied from historical retention.tsv.
    apps = ["Uhr", "Wetter", "Leseliste", "Skizzen", "Musik", "Wörterbuch", "Scanner", "Reisekarte"]
    queries = ["Sonnenblumen", "Bauplänen", "Wanderrouten", "Birkenblättern", "Papiergrößen", "Wolkenarten", "Fenstermaßen", "Zeichenstiften"]
    records = []
    for label, targets in (("1", apps), ("2", queries)):
        patterns = ("Bitte öffne {target}.", "Ich möchte {target} öffnen.") if label == "1" else (
            "Suche bitte nach {target}.", "Ich möchte nach {target} suchen.")
        for pattern_i, pattern in enumerate(patterns):
            for target_i, target in enumerate(targets):
                record = make_record(f"pv1-replay-{label}-{pattern_i}-{target_i}", "replay",
                                     f"simple-{label}-{pattern_i}", pattern.format(target=target), label, target)
                records.append(record)
    return records


def pipeline_examples() -> list[dict[str, Any]]:
    """Open diagnostics for richer contracts, never native single-label targets."""
    records = []

    def add(name: str, text: str, speech_act: str, actions: list[dict[str, Any]],
            *, forbidden: list[dict[str, Any]] | None = None, clarification: bool = False,
            context: list[dict[str, str]] | None = None, refs: list[str] | None = None,
            corrected: str | None = None, intended: str | None = None) -> None:
        item = make_record(f"pv1-pipeline-{name}", "pipeline_dev", name, text, None, speech_act=speech_act)
        item["meaning"].update(actions=actions, forbidden_actions=forbidden or [],
                               needs_clarification=clarification, intended_instruction=intended or corrected or text)
        item["context"] = {"turns": context or [], "prior_task_refs": refs or []}
        item["corrected_transcript"] = corrected
        item["transcript_correction_confirmed"] = corrected is not None
        records.append(item)

    add("asr_wording", "Öffne den Kalender statt den Galerien.", "request", [action("1", "Galerie")],
        corrected="Öffne die Galerie statt den Kalender.", forbidden=[action("1", "Kalender")],
        intended="Öffne nur die Galerie. Die synthetisch bestätigte Wortlautkorrektur hat Vorrang.")
    add("meaning_only", "Nein, so hab ich das nicht gemeint. Ich meine den anderen Zweig.", "correction", [],
        clarification=True, context=[{"turn_id": "t1", "role": "owner", "text": "Es gibt die Zweige blau, grün und gelb."},
                                     {"turn_id": "t2", "role": "assistant", "text": "Ich prüfe blau."}], refs=["t1", "t2"],
        intended="Nachfragen, ob grün oder gelb gemeint ist; keine automatische Zweigänderung.")
    add("referent_unique", "Na, den andern mein ich. Nur anschauen bitte.", "correction", [
        {"intent": "inspect_branch", "parameters": {"branch": "grün", "read_only": True}}],
        forbidden=[{"intent": "modify_branch", "parameters": {"branch": "grün"}}],
        context=[{"turn_id": "t1", "role": "owner", "text": "Vergleiche blau mit grün."},
                 {"turn_id": "t2", "role": "assistant", "text": "Soll ich blau ansehen?"}], refs=["t1", "t2"])
    add("multi_action", "Also bitte zuerst den Kalender aufmachen, dann die Notiz suchen, aber nix löschen.", "request", [
        {"intent": "open", "parameters": {"application": "Kalender", "order": 1}},
        {"intent": "search", "parameters": {"query": "Notiz", "order": 2}}],
        forbidden=[{"intent": "delete", "parameters": {"scope": "all"}}])
    add("multi_repair", "Such den Plan und öffne die Bilder. Nein, Bilder lassen wir, nur den Plan suchen.", "correction", [action("2", "Plan")],
        forbidden=[action("1", "Bilder")])
    add("question", "Kannst du mir sagen, ob die Kamera schon offen ist?", "question", [],
        forbidden=[action("1", "Kamera")])
    add("statement", "Die Kamera hab ich selber aufgemacht, das war nur zur Info.", "statement", [],
        forbidden=[action("1", "Kamera")])
    add("request", "Bitte mach jetzt die Kamera auf.", "request", [action("1", "Kamera")])
    add("offline_veto", "Schau bitte lokal nach der Notiz, ohne Internet und ohne irgendwas zu senden.", "request", [
        {"intent": "search", "parameters": {"query": "Notiz", "source": "local"}}],
        forbidden=[{"intent": "network", "parameters": {"scope": "all"}}, {"intent": "send", "parameters": {"scope": "all"}}])
    add("unknown_referent", "Mach das da bitte auf, du weißt eh.", "request", [], clarification=True,
        intended="Welches Objekt soll geöffnet werden?")
    add("quoted_negation", "Ich sage nicht: Öffne nichts. Ich sage: Öffne den Kalender.", "request", [action("1", "Kalender")])
    add("long_spontaneous", "Also äh ich wollte zuerst die Bilder suchen, das machen wir aber später, jetzt brauch ich den Kalender, "
        "nein nicht den öffnen, ich will nur wissen, ob morgen ein Termin drinsteht, und bitte nichts ändern.", "question", [],
        forbidden=[action("1", "Kalender"), {"intent": "modify_calendar", "parameters": {"scope": "all"}}],
        intended="Bestehende lesende Kalenderabfrage für morgen nur bei vorhandener Berechtigung; keine Änderung.")
    return records


def require(condition: bool, code: str) -> None:
    if not condition:
        raise ValueError(code)


def validate_record(record: dict[str, Any], *, public_export: bool = False) -> None:
    required = {"schema_version", "id", "split", "family_id", "pair_id", "origin", "consent", "storage", "audio",
                "recognized_transcript", "corrected_transcript", "transcript_correction_confirmed", "context", "meaning",
                "sensitivity", "lm1", "review"}
    require(isinstance(record, dict) and set(record) == required, "RECORD_KEYS")
    require(record["schema_version"] == VERSION, "SCHEMA_VERSION")
    require(isinstance(record["id"], str) and re.fullmatch(r"pv1-[a-z0-9-]+", record["id"]) is not None, "RECORD_ID")
    require(record["split"] in {"train", "dev", "replay", "pipeline_dev", "personal_pending"}, "SPLIT")
    require(isinstance(record["family_id"], str) and bool(record["family_id"]), "FAMILY")
    require(record["pair_id"] is None or isinstance(record["pair_id"], str) and bool(record["pair_id"]), "PAIR_ID")
    for field in ("origin", "consent", "storage", "context", "meaning", "sensitivity", "lm1", "review"):
        require(isinstance(record[field], dict), "OBJECT_REQUIRED_" + field.upper())
    origin, consent, meaning = record["origin"], record["consent"], record["meaning"]
    require(set(origin) == {"kind", "source_ref", "rights", "uses_private_content"}, "ORIGIN_KEYS")
    require(origin["kind"] in {"synthetic_authored", "owner_corrected"}, "ORIGIN_KIND")
    require(isinstance(origin["source_ref"], str) and bool(origin["source_ref"]), "SOURCE_REF")
    require(type(origin["uses_private_content"]) is bool, "PRIVATE_FLAG")
    require(set(consent) == {"text_training", "audio_retention", "cloud_export", "third_party_voice_authorized", "scope_digest"}, "CONSENT_KEYS")
    require(all(type(consent[key]) is bool for key in consent if key != "scope_digest"), "CONSENT_TYPES")
    require(consent["scope_digest"] is None or isinstance(consent["scope_digest"], str) and re.fullmatch(r"[a-f0-9]{64}", consent["scope_digest"]), "CONSENT_DIGEST")
    require(set(record["storage"]) == {"classification", "encryption_required"}, "STORAGE_KEYS")
    require(type(record["storage"]["encryption_required"]) is bool, "STORAGE_TYPE")
    text = record["recognized_transcript"]
    require(isinstance(text, str) and 0 < len(text) <= 8192 and not any(ord(c) < 32 and c not in "\n\t" for c in text), "TRANSCRIPT")
    corrected = record["corrected_transcript"]
    require(corrected is None or isinstance(corrected, str) and 0 < len(corrected) <= 8192, "CORRECTED_TRANSCRIPT")
    require(type(record["transcript_correction_confirmed"]) is bool, "WORDING_CONFIRM_TYPE")
    require((corrected is not None) == record["transcript_correction_confirmed"], "UNCONFIRMED_WORDING")
    require(set(record["context"]) == {"turns", "prior_task_refs"}, "CONTEXT_KEYS")
    turns = record["context"]["turns"]
    require(isinstance(turns, list) and len(turns) <= 16, "CONTEXT_TURNS")
    for turn in turns:
        require(set(turn) == {"turn_id", "role", "text"} and turn["role"] in {"owner", "assistant"}
                and all(isinstance(turn[k], str) and 0 < len(turn[k]) <= 8192 for k in turn), "CONTEXT_TURN")
    refs = record["context"]["prior_task_refs"]
    require(isinstance(refs, list) and all(isinstance(ref, str) and ref for ref in refs), "TASK_REFS")
    require(set(meaning) == {"confirmed", "intended_instruction", "speech_act", "actions", "forbidden_actions", "needs_clarification"}, "MEANING_KEYS")
    require(type(meaning["confirmed"]) is bool and type(meaning["needs_clarification"]) is bool, "MEANING_TYPES")
    require(isinstance(meaning["intended_instruction"], str) and 0 < len(meaning["intended_instruction"]) <= 8192, "INTENDED_INSTRUCTION")
    require(meaning["speech_act"] in {"question", "statement", "request", "correction"}, "SPEECH_ACT")
    for field in ("actions", "forbidden_actions"):
        require(isinstance(meaning[field], list) and len(meaning[field]) <= 8, "ACTION_LIST")
        for item in meaning[field]:
            require(isinstance(item, dict) and set(item) == {"intent", "parameters"}, "ACTION_KEYS")
            require(isinstance(item["intent"], str) and re.fullmatch(r"[a-z_]+", item["intent"]) and isinstance(item["parameters"], dict), "ACTION_TYPE")
            require(all(isinstance(k, str) and type(v) in {str, int, bool} for k, v in item["parameters"].items()), "ACTION_PARAMETERS")
    require(not (set(map(canonical, meaning["actions"])) & set(map(canonical, meaning["forbidden_actions"]))), "CONFLICTING_ACTIONS")
    require(not meaning["needs_clarification"] or not meaning["actions"], "AMBIGUOUS_EXECUTION")
    sensitivity = record["sensitivity"]
    require(set(sensitivity) == {"reviewed", "approved", "categories"} and type(sensitivity["reviewed"]) is bool
            and type(sensitivity["approved"]) is bool and isinstance(sensitivity["categories"], list)
            and all(isinstance(c, str) for c in sensitivity["categories"]), "SENSITIVITY")
    require(set(record["review"]) == {"status", "reviewer_kind"}, "REVIEW_KEYS")
    require(record["review"]["status"] in {"pending", "research_checked", "owner_confirmed", "rejected"}, "REVIEW_STATUS")
    require(record["review"]["reviewer_kind"] in {"synthetic_author", "owner", "none"}, "REVIEWER_KIND")
    lm1 = record["lm1"]
    require(set(lm1) == {"eligible", "label", "prompt"} and type(lm1["eligible"]) is bool, "LM1_KEYS")
    synthetic = origin["kind"] == "synthetic_authored"
    if synthetic:
        require(origin["rights"] == "repository_authorized_synthetic" and not origin["uses_private_content"], "SYNTHETIC_PROVENANCE")
        require(record["storage"] == {"classification": "public_research_synthetic", "encryption_required": False}, "SYNTHETIC_STORAGE")
        require(record["review"]["reviewer_kind"] == "synthetic_author", "SYNTHETIC_REVIEW")
        require(record["audio"] is None, "SYNTHETIC_AUDIO_NOT_COLLECTED")
    else:
        require(origin["rights"] == "owner_granted_local_training", "OWNER_RIGHTS")
        require(record["storage"] == {"classification": "private_local", "encryption_required": True}, "PRIVATE_STORAGE_REQUIRED")
        require(record["review"]["reviewer_kind"] in {"owner", "none"}, "OWNER_REVIEW")
        if consent["text_training"] or consent["audio_retention"]:
            require(consent["scope_digest"] == protected_content_digest(record), "STALE_CONTENT_CONSENT")
    if record["audio"] is not None:
        audio = record["audio"]
        require(isinstance(audio, dict) and set(audio) == {"local_ref", "sha256", "third_party_voice"}, "AUDIO_KEYS")
        require(isinstance(audio["local_ref"], str) and audio["local_ref"].startswith("private-audio:")
                and isinstance(audio["sha256"], str) and re.fullmatch(r"[a-f0-9]{64}", audio["sha256"])
                and type(audio["third_party_voice"]) is bool, "AUDIO_LOCAL_ONLY")
        require(consent["audio_retention"], "AUDIO_RETENTION_DENIED")
        require(not audio["third_party_voice"] or consent["third_party_voice_authorized"], "THIRD_PARTY_AUDIO_DENIED")
    if lm1["eligible"]:
        require(lm1["label"] in LABELS, "LM1_LABEL")
        require(meaning["confirmed"] and sensitivity["reviewed"] and sensitivity["approved"], "UNREVIEWED_TRAINING")
        require(not meaning["needs_clarification"] and len(meaning["actions"]) <= 1, "MULTI_OR_AMBIGUOUS_LM1")
        require(lm1["prompt"] == (corrected or text), "LM1_UNCONFIRMED_WORDING")
        require(not any(c in lm1["prompt"] for c in "\n\t"), "LM1_TSV_DELIMITER")
        intents = [item["intent"] for item in meaning["actions"]]
        require(intents == ([] if lm1["label"] == "0" else [LABELS[lm1["label"]]]), "LM1_MEANING_MISMATCH")
        if not synthetic:
            require(consent["text_training"] and record["review"]["status"] == "owner_confirmed", "OWNER_TRAINING_DENIED")
    else:
        require(lm1["label"] is None and lm1["prompt"] is None, "INELIGIBLE_LM1_PAYLOAD")
    if public_export:
        require(synthetic and not origin["uses_private_content"] and record["audio"] is None, "PRIVATE_PUBLICATION_DENIED")
        require(record["review"]["status"] == "research_checked" and sensitivity["reviewed"] and sensitivity["approved"]
                and not sensitivity["categories"], "PUBLIC_REVIEW_DENIED")


def majority_accuracy(records: list[dict[str, Any]], key) -> float:
    groups: dict[Any, collections.Counter[str]] = collections.defaultdict(collections.Counter)
    for record in records:
        groups[key(record["lm1"]["prompt"])][record["lm1"]["label"]] += 1
    return sum(max(labels.values()) for labels in groups.values()) / len(records)


def audit(records: list[dict[str, Any]], replay: list[dict[str, Any]], pipeline: list[dict[str, Any]]) -> dict[str, Any]:
    all_records = records + replay + pipeline
    for record in all_records:
        validate_record(record, public_export=True)
    require(len({record["id"] for record in all_records}) == len(all_records), "DUPLICATE_ID")
    eligible = records + replay
    require(len({normalized(record["lm1"]["prompt"]) for record in eligible}) == len(eligible), "DUPLICATE_PROMPT")
    pairs: dict[str, list[dict[str, Any]]] = collections.defaultdict(list)
    for record in records:
        pairs[record["pair_id"]].append(record)
    for pair in pairs.values():
        require(len(pair) == 2 and len({record["lm1"]["label"] for record in pair}) == 2, "INVALID_CONTRAST_PAIR")
        require(len({word_bag(record["lm1"]["prompt"]) for record in pair}) == 1, "BOW_PAIR_MISMATCH")
        require(len({record["split"] for record in pair}) == 1, "PAIR_SPLIT_LEAK")
    train, dev = ([record for record in records if record["split"] == split] for split in ("train", "dev"))
    require(not ({r["family_id"] for r in train} & {r["family_id"] for r in dev}), "FAMILY_SPLIT_LEAK")
    require(not ({word_bag(r["lm1"]["prompt"]) for r in train} & {word_bag(r["lm1"]["prompt"]) for r in dev}), "BOW_SPLIT_LEAK")
    split_metrics = {}
    for split, group in (("train", train), ("dev", dev), ("replay", replay)):
        histogram = dict(sorted(collections.Counter(r["lm1"]["label"] for r in group).items()))
        prompts = [r["lm1"]["prompt"] for r in group]
        metric = {"rows": len(group), "labels": histogram,
                  "max_characters": max(map(len, prompts)),
                  "max_utf8_bytes": max(len(p.encode("utf-8")) for p in prompts),
                  "families": sorted({r["family_id"] for r in group}),
                  "bow_oracle_accuracy_ceiling": majority_accuracy(group, word_bag),
                  "byte_length_oracle_accuracy": majority_accuracy(group, lambda s: len(s.encode("utf-8"))),
                  "character_length_oracle_accuracy": majority_accuracy(group, len)}
        if split != "replay":
            require(histogram == {label: len(group) // 4 for label in LABELS}, "LABEL_IMBALANCE")
            require(metric["bow_oracle_accuracy_ceiling"] <= 0.5, "BOW_SHORTCUT")
            require(metric["byte_length_oracle_accuracy"] <= 0.5, "LENGTH_SHORTCUT")
        split_metrics[split] = metric
    require(len(train) == 128 and len(dev) == 32 and len(replay) == 32, "DATA_BUDGET")
    return {"schema_version": VERSION, "status": "DATA_GATE_PASSED_PENDING_NATIVE_TOKEN_CHECK",
            "total_public_synthetic_records": len(all_records), "contrast_pairs": len(pairs),
            "metrics": split_metrics, "private_records": 0, "real_audio_records": 0,
            "asr_accuracy": "NOT_MEASURABLE_WITHOUT_AUDIO_AND_CONFIRMED_TRANSCRIPT",
            "shortcut_claim": "Full order-insensitive word-bag oracle is capped at 50% on paired contrasts. Position/template rules remain possible; no general-language claim.",
            "split_claim": "Full authored surface-template families and exact prompts are disjoint. Intended task semantics are shared; independent evaluator owns confirmation.",
            "native_token_budget": "PENDING_NATIVE_PREFLIGHT_PREFIX_PLUS_4_LE_CONTEXT256"}


def write_tsv(path: Path, records: list[dict[str, Any]]) -> None:
    data = "id\tlabel\tprompt\n" + "".join(f'{r["id"]}\t{r["lm1"]["label"]}\t{r["lm1"]["prompt"]}\n' for r in records)
    path.write_text(data, encoding="utf-8")


def generate(output: Path, historical_retention: Path | None = None) -> dict[str, Any]:
    require(not output.exists(), "DESTINATION_ALREADY_EXISTS")
    records, replay, pipeline = contrasts(), supervised_replay(), pipeline_examples()
    report = audit(records, replay, pipeline)
    if historical_retention is not None:
        lines = historical_retention.read_text(encoding="utf-8").splitlines()
        require(lines[0] == "id\tlabel\tprompt", "HISTORICAL_HEADER")
        historical = {normalized(line.split("\t", 2)[2]) for line in lines[1:] if line}
        require(not historical.intersection(normalized(r["lm1"]["prompt"]) for r in records + replay), "HISTORICAL_RETENTION_LEAK")
        report["historical_retention"] = {"sha256": sha(historical_retention.read_bytes()), "exact_normalized_overlap": 0,
                                          "role": "audit_only_not_training_input"}
    output.mkdir(parents=True)
    for split in ("train", "dev"):
        write_tsv(output / f"{split}.tsv", [r for r in records if r["split"] == split])
    write_tsv(output / "supervised_replay.tsv", replay)
    for filename, rows in (("examples.jsonl", records), ("replay-examples.jsonl", replay), ("pipeline-synthetic.jsonl", pipeline)):
        (output / filename).write_bytes(b"".join(canonical(row) + b"\n" for row in rows))
    (output / "data-audit.json").write_text(json.dumps(report, ensure_ascii=False, indent=2) + "\n", encoding="utf-8")
    manifest = {"schema_version": VERSION, "scope": "PUBLIC_SYNTHETIC_RESEARCH_ONLY", "source_ref": SOURCE,
                "baseline_pr": 728, "baseline_commit": "1d9eab1275a7fcc92ad4c5e2d1dd52d1db411667",
                "label_contract": LABELS, "input_wrapper": "Auftrag: {prompt}\n",
                "native_target": "one existing label token plus actual EOS; do not supervise prompt tokens",
                "lm1_training_inputs": ["train.tsv", "supervised_replay.tsv"],
                "never_training_inputs": ["dev.tsv", "pipeline-synthetic.jsonl", "historical retention.tsv", "independent evaluator holdouts"],
                "generator_sha256": sha(Path(__file__).read_bytes()),
                "files": {p.name: {"sha256": sha(p.read_bytes()), "bytes": p.stat().st_size} for p in sorted(output.iterdir())}}
    (output / "manifest.json").write_text(json.dumps(manifest, ensure_ascii=False, indent=2) + "\n", encoding="utf-8")
    return report


def main() -> None:
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("--output", type=Path, required=True)
    parser.add_argument("--historical-retention", type=Path)
    args = parser.parse_args()
    print(json.dumps(generate(args.output, args.historical_retention), ensure_ascii=False, indent=2))


if __name__ == "__main__":
    main()
