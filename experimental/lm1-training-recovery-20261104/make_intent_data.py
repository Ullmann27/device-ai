#!/usr/bin/env python3
"""Generate the preregistered, self-authored German LM1 intent study splits.

This program never reads model weights, existing corpora, or an old holdout.
It prints counts and hashes only. Test prompts must remain unopened until the
experiment's candidates and selection have been frozen.
"""
from __future__ import annotations

import argparse
from collections import Counter
import hashlib
import json
from pathlib import Path
import unicodedata


VERSION = "lm1-intent-study-v1"
DEFAULT_SEED = 20261104
LABELS = {"0": "keine Handlung oder Abbruch", "1": "Datei öffnen",
          "2": "Informationen suchen", "3": "Notiz merken"}
EXPECTED_ROWS = {"train": 128, "dev": 24, "test": 48}
FILENAMES = {"train": "train.tsv", "dev": "dev.tsv", "test": "test.sealed.tsv"}
MAX_PROMPT_UTF8_BYTES = 200

# Objects are intentionally disjoint across splits. Every object group is used
# for every label, so a file name or topic does not determine the answer.
OBJECTS = {
    "train": (
        {"file": "Testplan.txt", "topic": "lokale Datensicherung", "note": "Der Prüflauf beginnt am Dienstag"},
        {"file": "Arbeitsliste.txt", "topic": "automatische Tests", "note": "Die Sitzung endet am Freitag"},
        {"file": "Fehlerbericht.txt", "topic": "Versionskontrolle", "note": "Der Schlüssel liegt im Schrank"},
        {"file": "Projektplan.txt", "topic": "Dateiverschlüsselung", "note": "Die Aufgaben werden abends geprüft"},
    ),
    "dev": (
        {"file": "Ablauf.txt", "topic": "Arbeitspausen", "note": "Die Zeichnung braucht eine zweite Prüfung"},
        {"file": "Pruefbogen.txt", "topic": "Dateiformate", "note": "Der Entwurf bleibt vorerst unverändert"},
    ),
    "test": (
        {"file": "Uebergabe.txt", "topic": "Speicherverwaltung", "note": "Der Werkzeugkasten steht im Nebenraum"},
        {"file": "Messbericht.txt", "topic": "rekursive Funktionen", "note": "Die Abnahme findet am Mittwoch statt"},
        {"file": "Bauteilliste.txt", "topic": "lokale Suchindizes", "note": "Der nächste Prüfschritt betrifft die Oberfläche"},
    ),
}

# A family is a complete sentence template, not merely its punctuation.
# Different splits share the four intended meanings, never a template string.
# Corrections explicitly replace an earlier, still unexecuted instruction.
TEMPLATES = {
    "train": {
        "0": (
            ("negation", "Öffne die Datei {file} nicht."),
            ("negation", "Suche keine Informationen über {topic}."),
            ("negation", "Speichere diese Notiz nicht: {note}."),
            ("cancellation", "Brich den Auftrag zur Datei {file} ab."),
            ("cancellation", "Stopp. Führe die Suche über {topic} nicht aus."),
            ("cancellation", "Lass die Datei {file} geschlossen und tue nichts."),
            ("correction", "Öffne {file}. Korrektur: Führe den Auftrag nicht aus."),
            ("correction", "Merke dir: {note}. Nein, vergiss diesen Auftrag."),
        ),
        "1": (
            ("direct", "Öffne die Datei {file}."),
            ("direct", "Mach bitte {file} auf."),
            ("direct", "Zeige mir die Datei {file}."),
            ("request", "Kannst du die Datei {file} öffnen?"),
            ("negated_distractor", "Suche nicht nach {topic}; öffne {file}."),
            ("negated_distractor", "Notiere jetzt nichts. Öffne stattdessen {file}."),
            ("correction", "Suche nach {topic}. Korrektur: Öffne nur {file}."),
            ("correction", "Tu nichts. Korrektur: Öffne doch die Datei {file}."),
        ),
        "2": (
            ("direct", "Suche Informationen über {topic}."),
            ("direct", "Recherchiere bitte zum Thema {topic}."),
            ("direct", "Finde Wissen zum Thema {topic}."),
            ("request", "Kannst du Informationen über {topic} suchen?"),
            ("negated_distractor", "Öffne {file} nicht; suche Informationen über {topic}."),
            ("negated_distractor", "Merke dir nichts. Recherchiere zum Thema {topic}."),
            ("correction", "Öffne {file}. Korrektur: Suche nur Informationen über {topic}."),
            ("correction", "Tu nichts. Korrektur: Suche doch nach Informationen über {topic}."),
        ),
        "3": (
            ("direct", "Merke dir: {note}."),
            ("direct", "Notiere bitte: {note}."),
            ("direct", "Speichere als Notiz: {note}."),
            ("request", "Kannst du dir das merken: {note}?"),
            ("negated_distractor", "Öffne {file} nicht. Merke dir: {note}."),
            ("negated_distractor", "Suche keine Informationen. Notiere nur: {note}."),
            ("correction", "Öffne {file}. Korrektur: Notiere nur: {note}."),
            ("correction", "Tu nichts. Korrektur: Merke dir doch: {note}."),
        ),
    },
    "dev": {
        "0": (
            ("negation", "Bitte unterlasse das Öffnen von {file}."),
            ("cancellation", "Der Auftrag zum Thema {topic} ist gestrichen. Keine Aktion."),
            ("correction", "Notiere: {note}. Widerruf: nichts ausführen."),
        ),
        "1": (
            ("direct", "Ich möchte {file} sehen. Öffne sie bitte."),
            ("negated_distractor", "Keine Recherche zum Thema {topic}. Mach nur {file} auf."),
            ("correction", "Notiere nichts. Änderung des Auftrags: {file} öffnen."),
        ),
        "2": (
            ("direct", "Sammle Informationen zum Thema {topic}."),
            ("negated_distractor", "Lass {file} zu und recherchiere über {topic}."),
            ("correction", "Öffne {file}. Änderung: Stattdessen über {topic} recherchieren."),
        ),
        "3": (
            ("direct", "Halte fest: {note}."),
            ("negated_distractor", "Lass die Dateien geschlossen. Speichere als Gedankenstütze: {note}."),
            ("correction", "Recherchiere nicht. Neuer Auftrag: {note}. Bitte als Notiz sichern."),
        ),
    },
    "test": {
        "0": (
            ("negation", "Die Datei {file} soll ungeöffnet bleiben. Sonst bitte nichts tun."),
            ("negation", "Über {topic} sollst du gerade keine Informationen beschaffen."),
            ("negation", "Diese Angabe bitte nicht behalten: {note}."),
            ("correction", "Recherchiere über {topic}. Ich nehme den Auftrag zurück; führe nichts aus."),
        ),
        "1": (
            ("direct", "Ich will {file} lesen; rufe die Datei bitte auf."),
            ("direct", "Zur Ansicht brauche ich jetzt die Datei {file}."),
            ("negated_distractor", "Die Recherche über {topic} entfällt; rufe lediglich {file} auf."),
            ("correction", "Merke dir: {note}. Ich ändere den Auftrag: Nur {file} öffnen."),
        ),
        "2": (
            ("direct", "Beschaffe mir Auskünfte zum Thema {topic}."),
            ("direct", "Ich brauche Hintergründe über {topic}; finde etwas dazu heraus."),
            ("negated_distractor", "Die Datei {file} bleibt zu. Besorge nur Wissen über {topic}."),
            ("correction", "Öffne {file}. Diesen Auftrag ersetze ich: Bitte über {topic} nachforschen."),
        ),
        "3": (
            ("direct", "Halte diese Angabe für später fest: {note}."),
            ("direct", "Ich möchte diese Information behalten: {note}. Lege eine Notiz an."),
            ("negated_distractor", "Die Datei {file} bleibt geschlossen. Behalte stattdessen diese Angabe: {note}."),
            ("correction", "Recherchiere über {topic}. Das ersetze ich durch: Notiz anlegen: {note}."),
        ),
    },
}


def digest(data: bytes) -> str:
    return hashlib.sha256(data).hexdigest()


def normalized(text: str) -> str:
    return " ".join(unicodedata.normalize("NFKC", text).casefold().split())


def build(seed: int) -> tuple[dict[str, bytes], dict]:
    """Build and validate in memory; never open a previously generated test."""
    rows_by_split = {}
    seen_prompts = set()
    seen_templates = set()
    seen_objects = {key: set() for key in ("file", "topic", "note")}
    for split in EXPECTED_ROWS:
        groups = OBJECTS[split]
        for key, previous in seen_objects.items():
            current = {normalized(group[key]) for group in groups}
            if len(current) != len(groups) or current & previous:
                raise ValueError(f"non-disjoint {key} objects in {split}")
            previous.update(current)
        rows = []
        for label, templates in TEMPLATES[split].items():
            if label not in LABELS:
                raise ValueError(f"unknown label in {split}")
            for index, (kind, template) in enumerate(templates, start=1):
                canonical_template = normalized(template)
                if canonical_template in seen_templates:
                    raise ValueError("duplicate sentence template")
                seen_templates.add(canonical_template)
                family = f"{split}-{label}-{index:02d}"
                for object_index, values in enumerate(groups, start=1):
                    prompt = unicodedata.normalize("NFC", template.format(**values))
                    if (not prompt.strip() or any(character in prompt for character in "\t\r\n")
                            or len(prompt.encode("utf-8")) > MAX_PROMPT_UTF8_BYTES):
                        raise ValueError(f"invalid prompt shape in family {family}")
                    canonical_prompt = normalized(prompt)
                    if canonical_prompt in seen_prompts:
                        raise ValueError(f"duplicate or potentially contradictory prompt in {family}")
                    seen_prompts.add(canonical_prompt)
                    identity = f"{VERSION}\0{seed}\0{split}\0{family}\0{object_index}\0{prompt}"
                    rows.append({"label": label, "prompt": prompt, "family": family, "kind": kind,
                                 "order": digest(identity.encode("utf-8"))})
        counts = Counter(row["label"] for row in rows)
        if len(rows) != EXPECTED_ROWS[split] or counts != Counter({key: len(rows) // 4 for key in LABELS}):
            raise ValueError(f"incorrect split size or class balance in {split}")
        rows_by_split[split] = sorted(rows, key=lambda row: row["order"])

    payloads = {}
    file_metadata = {}
    for split, rows in rows_by_split.items():
        filename = FILENAMES[split]
        lines = ["id\tlabel\tprompt"]
        for index, row in enumerate(rows, start=1):
            lines.append(f"{split}-{index:04d}\t{row['label']}\t{row['prompt']}")
        payload = ("\n".join(lines) + "\n").encode("utf-8")
        payloads[filename] = payload
        lengths = [len(row["prompt"].encode("utf-8")) for row in rows]
        file_metadata[filename] = {
            "split": split, "rows": len(rows), "sha256": digest(payload),
            "labelCounts": dict(sorted(Counter(row["label"] for row in rows).items())),
            "patternCounts": dict(sorted(Counter(row["kind"] for row in rows).items())),
            "templateFamilyIds": sorted({row["family"] for row in rows}),
            "objectGroups": len(OBJECTS[split]),
            "promptUtf8BytesMin": min(lengths), "promptUtf8BytesMax": max(lengths),
        }
    manifest = {
        "version": VERSION, "seed": seed, "generatorSha256": digest(Path(__file__).read_bytes()),
        "origin": "self-authored German sentences; no downloaded corpus or model-generated examples",
        "labelMap": LABELS, "files": file_metadata,
        "format": {"encoding": "UTF-8", "header": "id\tlabel\tprompt", "lineEnding": "LF",
                   "modelInput": "Auftrag: {prompt}\nAktion:", "target": "one label digit followed by EOS"},
        "validation": {"balancedLabels": True, "normalizedPromptsDisjoint": True,
                       "sentenceTemplatesDisjoint": True, "objectValuesDisjoint": True,
                       "testAccessBeforeFreeze": "hashes and metadata only"},
        "limitations": ["Only four predefined intents; no tool execution or authorization.",
                        "Template and object separation is not proof of open-domain generalization.",
                        "Sealing is a preregistered no-peeking rule, not cryptographic secrecy."],
    }
    return payloads, manifest


def main() -> None:
    parser = argparse.ArgumentParser(description=__doc__)
    mode = parser.add_mutually_exclusive_group(required=True)
    mode.add_argument("--output", type=Path, help="new output directory; existing paths are never overwritten")
    mode.add_argument("--check", action="store_true", help="validate reproducibility in memory without creating files")
    parser.add_argument("--seed", type=int, default=DEFAULT_SEED)
    args = parser.parse_args()
    payloads, manifest = build(args.seed)
    if args.check:
        repeated_payloads, repeated_manifest = build(args.seed)
        if payloads != repeated_payloads or manifest != repeated_manifest:
            raise RuntimeError("dataset generation is not deterministic")
    else:
        output = args.output.resolve()
        output.mkdir(parents=True, exist_ok=False)
        for filename, payload in payloads.items():
            with (output / filename).open("xb") as stream:
                stream.write(payload)
        # The manifest is the completion marker. An interrupted partial directory
        # has no valid manifest and must not be used by the experiment.
        with (output / "manifest.json").open("x", encoding="utf-8", newline="\n") as stream:
            json.dump(manifest, stream, ensure_ascii=False, indent=2, sort_keys=True)
            stream.write("\n")
        (output / FILENAMES["test"]).chmod(0o444)
    print(json.dumps({"status": "VALIDATED" if args.check else "CREATED", "version": VERSION,
                      "seed": args.seed, "files": {name: {key: metadata[key] for key in
                       ("rows", "labelCounts", "sha256", "promptUtf8BytesMax")}
                       for name, metadata in manifest["files"].items()}}, ensure_ascii=False, sort_keys=True))


if __name__ == "__main__":
    main()
