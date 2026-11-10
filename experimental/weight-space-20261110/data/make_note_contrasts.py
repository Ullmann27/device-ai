#!/usr/bin/env python3
"""Create an open LM1 TRAIN/DEV contrast set; never access model weights."""
from __future__ import annotations

import argparse
from collections import Counter
import hashlib
import json
from pathlib import Path
import unicodedata


VERSION = "lm1-note-contrast-dev-20261110-v1"
SEED = 20261110
MAX_PROMPT_BYTES = 200
EXPECTED_ROWS = {"train": 128, "dev": 64}
FAMILIES = {
    "train": (
        ("remember", "Bitte merken", "Nicht merken"),
        ("record", "Jetzt notieren", "Nicht notieren"),
        ("secure", "Bitte als Notiz sichern", "Nicht als Notiz sichern"),
        ("store", "Jetzt als Notiz speichern", "Nicht als Notiz speichern"),
    ),
    "dev": (
        ("retain", "Bitte festhalten", "Nicht festhalten"),
        ("write", "Jetzt aufschreiben", "Nicht aufschreiben"),
        ("file", "Bitte als Notiz ablegen", "Nicht als Notiz ablegen"),
        ("keep", "Jetzt als Hinweis aufbewahren", "Nicht als Hinweis aufbewahren"),
    ),
}
OBJECTS = {
    "train": (
        ("cup", "Der Becher ist blau", "Der blaue Becher steht links neben dem leeren Teller"),
        ("cloth", "Der Stoff ist weich", "Der weiche Stoff liegt oben auf dem breiten Regal"),
        ("room", "Der Raum ist leer", "Der leere Raum hat zwei Fenster und eine schmale Tür"),
        ("board", "Das Brett ist glatt", "Das glatte Brett liegt quer neben der kleinen Kiste"),
    ),
    "dev": (
        ("lamp", "Die Lampe ist grün", "Die grüne Lampe steht hinter der flachen Schale am Fenster"),
        ("ribbon", "Das Band ist dünn", "Das dünne Band liegt zwischen dem Heft und der runden Dose"),
    ),
}


def digest(data: bytes) -> str:
    return hashlib.sha256(data).hexdigest()


def normalized(text: str) -> str:
    return " ".join(unicodedata.normalize("NFKC", text).casefold().split())


def build(seed: int = SEED, families=None, objects=None):
    families = FAMILIES if families is None else families
    objects = OBJECTS if objects is None else objects
    seen_templates, seen_objects, seen_prompts = set(), set(), set()
    payloads, split_metadata, row_metadata = {}, {}, {}
    for split in EXPECTED_ROWS:
        split_templates, split_objects = set(), set()
        for family, positive, negative in families[split]:
            if (not family or positive[:5] not in {"Bitte", "Jetzt"}
                    or not negative.startswith("Nicht ")
                    or positive[5:] != negative[5:]):
                raise ValueError("invalid positive/negative directive pair")
            for directive in (positive, negative):
                canonical = normalized(directive)
                if canonical in seen_templates or canonical in split_templates:
                    raise ValueError("non-disjoint directive template")
                split_templates.add(canonical)
        for object_id, short, long in objects[split]:
            if not object_id or len(long.encode()) <= len(short.encode()):
                raise ValueError("invalid object length levels")
            for content in (short, long):
                canonical = normalized(content)
                if canonical in seen_objects or canonical in split_objects:
                    raise ValueError("non-disjoint object content")
                split_objects.add(canonical)
        seen_templates.update(split_templates)
        seen_objects.update(split_objects)
        rows, pairs = [], {}
        for family, positive, negative in families[split]:
            family_id = f"note-v1-{split}-{family}"
            for object_id, short, long in objects[split]:
                for level, content in (("short", short), ("long", long)):
                    for position in ("first", "last"):
                        pair_id = f"{family_id}-{object_id}-{level}-{position}"
                        if pair_id in pairs:
                            raise ValueError("duplicate contrast pair id")
                        pair_rows = []
                        for label, directive in (("0", negative), ("3", positive)):
                            if position == "first":
                                prompt = f"{directive}: {content}."
                            else:
                                prompt = f"Angabe: {content}. {directive}."
                            prompt = unicodedata.normalize("NFC", prompt)
                            if (any(char in prompt for char in "\t\r\n")
                                    or len(prompt.encode()) > MAX_PROMPT_BYTES):
                                raise ValueError("invalid prompt shape")
                            canonical = normalized(prompt)
                            if canonical in seen_prompts:
                                raise ValueError("duplicate normalized prompt")
                            seen_prompts.add(canonical)
                            row = {"label": label, "prompt": prompt, "pairId": pair_id,
                                   "familyId": family_id, "objectId": f"{split}-{object_id}",
                                   "lengthLevel": level, "directivePosition": position,
                                   "promptUtf8Bytes": len(prompt.encode())}
                            identity = f"{VERSION}\0{seed}\0{split}\0{pair_id}\0{label}\0{prompt}"
                            row["order"] = digest(identity.encode())
                            rows.append(row)
                            pair_rows.append(row)
                        if pair_rows[0]["promptUtf8Bytes"] != pair_rows[1]["promptUtf8Bytes"]:
                            raise ValueError("unequal contrast-pair byte lengths")
                        pairs[pair_id] = pair_rows
        counts = Counter(row["label"] for row in rows)
        if len(rows) != EXPECTED_ROWS[split] or counts != {"0": len(rows)//2, "3": len(rows)//2}:
            raise ValueError("incorrect split size or label balance")
        if sorted(row["promptUtf8Bytes"] for row in rows if row["label"] == "0") != sorted(
                row["promptUtf8Bytes"] for row in rows if row["label"] == "3"):
            raise ValueError("unequal label length distributions")
        rows.sort(key=lambda row: row["order"])
        lines, metadata = ["id\tlabel\tprompt"], []
        for index, row in enumerate(rows, 1):
            row_id = f"note-{split}-{index:04d}"
            lines.append(f"{row_id}\t{row['label']}\t{row['prompt']}")
            metadata.append({"id": row_id, **{key: value for key, value in row.items()
                                             if key not in {"prompt", "order"}}})
        payload = ("\n".join(lines)+"\n").encode()
        payloads[f"{split}.tsv"] = payload
        row_metadata[split] = metadata
        split_metadata[split] = {
            "rows": len(rows), "contrastPairs": len(pairs), "labelCounts": dict(counts),
            "familyIds": sorted({row["familyId"] for row in rows}),
            "objectIds": sorted({row["objectId"] for row in rows}),
            "directivePositionCounts": dict(Counter(row["directivePosition"] for row in rows)),
            "lengthLevelCounts": dict(Counter(row["lengthLevel"] for row in rows)),
            "promptUtf8BytesMin": min(row["promptUtf8Bytes"] for row in rows),
            "promptUtf8BytesMax": max(row["promptUtf8Bytes"] for row in rows),
            "sha256": digest(payload),
        }
    metadata_bytes = (json.dumps(row_metadata, ensure_ascii=False, indent=2, sort_keys=True)+"\n").encode()
    payloads["row-metadata.json"] = metadata_bytes
    manifest = {
        "version": VERSION, "seed": seed, "generatorSha256": digest(Path(__file__).read_bytes()),
        "status": "OPEN_DEVELOPMENT_ONLY", "origin": "New authored synthetic German scenarios",
        "labelMap": {"0": "do not store the presented note", "3": "store the presented note"},
        "files": split_metadata, "rowMetadataSha256": digest(metadata_bytes),
        "format": {"encoding": "UTF-8", "header": "id\tlabel\tprompt", "lineEnding": "LF",
                   "modelInput": "Auftrag: {prompt}\n", "target": "one label token then actual EOS",
                   "maskedTargetsOnly": True, "eosIsNotLiteralText": True},
        "validation": {"balancedLabels": True, "equalPairedByteLengths": True,
                       "equalLabelByteLengthDistributions": True, "balancedDirectivePositions": True,
                       "balancedContentLengthLevels": True, "disjointDirectives": True,
                       "disjointObjectContent": True, "disjointNormalizedPrompts": True},
        "limitations": [
            "Developer-visible TRAIN/DEV only; no test or blind generalization claim.",
            "Same open authoring process; semantic families and markers are shared.",
            "Bitte/Jetzt versus Nicht can solve the bounded task; no general language claim.",
            "Matched UTF-8 byte length does not imply matched BPE token length.",
            "Native tokenizer must validate every full sequence against its context limit.",
            "Only labels 0 and 3; other intents and long generation require retention checks.",
            "Synthetic statements are task inputs, not facts or owner memory.",
        ],
    }
    return payloads, manifest


def write_dataset(output: Path, seed: int = SEED):
    payloads, manifest = build(seed)
    output.mkdir(parents=True, exist_ok=False)
    for name, payload in payloads.items():
        with (output/name).open("xb") as stream:
            stream.write(payload)
    with (output/"manifest.json").open("x", encoding="utf-8", newline="\n") as stream:
        json.dump(manifest, stream, ensure_ascii=False, indent=2, sort_keys=True)
        stream.write("\n")
    return manifest


def main():
    parser = argparse.ArgumentParser(description=__doc__)
    mode = parser.add_mutually_exclusive_group(required=True)
    mode.add_argument("--output", type=Path)
    mode.add_argument("--check", action="store_true")
    parser.add_argument("--seed", type=int, default=SEED)
    args = parser.parse_args()
    if args.check:
        payloads, manifest = build(args.seed)
        if (payloads, manifest) != build(args.seed):
            raise RuntimeError("nondeterministic dataset")
    else:
        manifest = write_dataset(args.output, args.seed)
    print(json.dumps({"status": "VALIDATED" if args.check else "CREATED",
                      "version": VERSION, "files": manifest["files"]}, sort_keys=True))


if __name__ == "__main__":
    main()
