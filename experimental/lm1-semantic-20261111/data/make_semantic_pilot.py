#!/usr/bin/env python3
"""Author a bounded open semantic pilot without model access or holdout input."""
from __future__ import annotations

import argparse
from collections import Counter
import hashlib
from itertools import combinations
import json
from pathlib import Path
import re
import unicodedata

from frozen_tokenizer import load_tokenizer, TOKENIZER_SHA256

VERSION = "lm1-semantic-pilot-20261111-v1"
SEED = 20261111
BASE = "1d9eab1275a7fcc92ad4c5e2d1dd52d1db411667"
LABELS = {"0": "no listed action", "1": "open named file", "2": "search named term", "3": "store named note"}
OBJECTS = {
    "train": (("train-amber", "nicht Bernstein"), ("train-linen", "kein Leinen"),
              ("train-willow", "Weide"), ("train-clay", "Ton am Ufer")),
    "dev": (("dev-coral", "nicht Koralle"), ("dev-maple", "Ahorn am Teich")),
}
FAMILIES = {
    "train": ("quotation_scope", "corrective_alternative", "double_negation", "changed_request"),
    "dev": ("conditional_branch", "ordinal_reference", "authorization_scope", "description_control"),
}


def sha(data):
    return hashlib.sha256(data).hexdigest()


def normalize(text):
    return " ".join(unicodedata.normalize("NFKC", text).casefold().split())


def bag(text):
    return Counter(re.findall(r"\w+", normalize(text)))


def actions(name):
    return {
        "0": "nichts tun",
        "1": f"die Datei „{name}“ öffnen",
        "2": f"nach „{name}“ suchen",
        "3": f"„{name}“ als Notiz speichern",
    }


def render(family, chosen, ignored, position):
    if family == "quotation_scope":
        clauses = [f"Führe aus: «{chosen}».", f"Nur ein Zitat, kein Auftrag: «{ignored}»."]
    elif family == "corrective_alternative":
        clauses = [f"Gewünscht ist «{chosen}».", f"Nicht «{ignored}», sondern der gewünschte Auftrag gilt."]
    elif family == "double_negation":
        clauses = [f"Es ist nicht erlaubt, den Auftrag «{chosen}» nicht auszuführen.", f"«{ignored}» ist nur ein Beispiel."]
    elif family == "changed_request":
        clauses = [f"Mein neuer Auftrag: «{chosen}».", f"Mein alter Auftrag «{ignored}» ist aufgehoben."]
    elif family == "conditional_branch":
        if position == "first":
            return f"Die Bedingung gilt. Falls sie gilt: «{chosen}»; sonst: «{ignored}»."
        return f"Die Bedingung gilt nicht. Falls sie gilt: «{ignored}»; sonst: «{chosen}»."
    elif family == "ordinal_reference":
        if position == "first":
            return f"Zur Auswahl stehen zuerst «{chosen}», danach «{ignored}». Führe nur Ersteres aus."
        return f"Zur Auswahl stehen zuerst «{ignored}», danach «{chosen}». Führe nur Letzteres aus."
    elif family == "authorization_scope":
        clauses = [f"Zugelassen ist «{chosen}».", f"Verboten ist «{ignored}»."]
        if position == "last":
            clauses.reverse()
        return " ".join(clauses) + " Erledige genau das Zugelassene."
    elif family == "description_control":
        clauses = [f"Ich beauftrage dich damit: «{chosen}».", f"Ich beschreibe lediglich die Möglichkeit: «{ignored}»."]
    else:
        raise ValueError("unknown semantic family")
    if position == "last":
        clauses.reverse()
    return " ".join(clauses)


def build(repo, seed=SEED):
    tok = load_tokenizer(repo)
    rows_by_split, payloads, summaries = {}, {}, {}
    seen_prompts, seen_families, seen_objects = set(), set(), set()
    for split in ("train", "dev"):
        if seen_families.intersection(FAMILIES[split]):
            raise ValueError("shared family")
        seen_families.update(FAMILIES[split])
        object_values = {normalize(name) for _, name in OBJECTS[split]}
        if seen_objects.intersection(object_values):
            raise ValueError("shared object")
        seen_objects.update(object_values)
        rows = []
        for family_index, family in enumerate(FAMILIES[split]):
            for edge_index, (a, b) in enumerate(combinations(LABELS, 2)):
                for object_index, (object_id, name) in enumerate(OBJECTS[split]):
                    action = actions(name)
                    pair_id = f"{split}-{family}-{a}{b}-{object_id}"
                    position = "first" if (object_index + edge_index + family_index) % 2 == 0 else "last"
                    pair = []
                    for label, other in ((a, b), (b, a)):
                        prompt = unicodedata.normalize("NFC", render(family, action[label], action[other], position))
                        canonical = normalize(prompt)
                        if canonical in seen_prompts or any(c in prompt for c in "\t\r\n"):
                            raise ValueError("duplicate or malformed prompt")
                        seen_prompts.add(canonical)
                        token_ids = [tok.bos] + tok.encode("Auftrag: " + prompt + "\n")
                        if len(token_ids) + 4 > 256:
                            raise ValueError("context budget exceeded")
                        identity = f"{VERSION}\0{seed}\0{pair_id}\0{label}\0{prompt}"
                        row = {"label": label, "prompt": prompt, "pairId": pair_id,
                               "familyId": family, "objectId": object_id,
                               "chosenPosition": position, "promptSha256": sha(prompt.encode()),
                               "wrappedInputSha256": sha(("Auftrag: " + prompt + "\n").encode()),
                               "promptUtf8Bytes": len(prompt.encode()), "prefixTokens": len(token_ids),
                               "prefixTokenIds": token_ids, "order": sha(identity.encode())}
                        rows.append(row)
                        pair.append(row)
                    if bag(pair[0]["prompt"]) != bag(pair[1]["prompt"]):
                        raise ValueError("lexical contrast failed")
                    if pair[0]["prefixTokens"] != pair[1]["prefixTokens"]:
                        raise ValueError("paired token length differs")
                    if pair[0]["promptUtf8Bytes"] != pair[1]["promptUtf8Bytes"]:
                        raise ValueError("paired byte length differs")
        rows.sort(key=lambda row: row["order"])
        for index, row in enumerate(rows, 1):
            row["id"] = f"semantic-{split}-{index:04d}"
        expected = 192 if split == "train" else 96
        if len(rows) != expected or Counter(r["label"] for r in rows) != {x: expected // 4 for x in LABELS}:
            raise ValueError("row or class imbalance")
        rows_by_split[split] = rows
        tsv = "id\tlabel\tprompt\n" + "".join(f"{r['id']}\t{r['label']}\t{r['prompt']}\n" for r in rows)
        payloads[f"{split}.tsv"] = tsv.encode()
        metadata = [{k: v for k, v in r.items() if k not in {"prompt", "order"}} for r in rows]
        payloads[f"{split}-metadata.json"] = json_bytes(metadata)
        summaries[split] = {
            "rows": expected, "pairs": expected // 2, "labelCounts": dict(Counter(r["label"] for r in rows)),
            "familyIds": list(FAMILIES[split]), "objectIds": [x[0] for x in OBJECTS[split]],
            "classPrefixTokens": {x: sorted(r["prefixTokens"] for r in rows if r["label"] == x) for x in LABELS},
            "allPairsSameWordBag": True, "allPairsSameBpeLength": True,
            "allPairsSameByteLength": True, "maxPrefixTokens": max(r["prefixTokens"] for r in rows),
            "sha256": sha(payloads[f"{split}.tsv"]),
        }
    manifest = {
        "schema": VERSION, "seed": seed, "baseCommit": BASE, "status": "OPEN_DEVELOPMENT_PILOT",
        "origin": "New authored synthetic German instructions; no user data, old DEV or holdout copied",
        "labelMap": LABELS, "generatorSha256": sha(Path(__file__).read_bytes()),
        "tokenizerSha256": TOKENIZER_SHA256, "tokenizerPortSha256": sha(Path(__file__).with_name("frozen_tokenizer.py").read_bytes()),
        "nativeParity": "REQUIRED_BEFORE_TRAINING", "format": {"header": "id\tlabel\tprompt", "encoding": "UTF-8", "modelInput": "BOS + encode('Auftrag: ' + prompt + '\n')", "target": "single label token followed by actual EOS"},
        "splits": summaries, "fileHashes": {k: sha(v) for k, v in payloads.items()},
        "limitations": [
            "Open TRAIN/DEV, not independent confirmation; author sees all examples and labels.",
            "Families differ in construction; all share the same four simulated intent labels.",
            "Every pair has identical word counts, bytes and BPE length; this does not exclude order-sensitive shortcuts.",
            "Only six training/dev object names; repeated templates create correlated observations.",
            "No action execution, permission change, owner memory or production integration.",
            "No claim of natural-language coverage beyond these bounded authored constructions.",
        ],
    }
    payloads["manifest.json"] = json_bytes(manifest)
    return payloads, rows_by_split, manifest


def json_bytes(value):
    return (json.dumps(value, ensure_ascii=False, indent=2, sort_keys=True) + "\n").encode()


def write_dataset(repo, output, seed=SEED):
    payloads, rows, manifest = build(repo, seed)
    output.mkdir(parents=True, exist_ok=False)
    for name, content in payloads.items():
        with (output / name).open("xb") as stream:
            stream.write(content)
    return manifest


def main():
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("--repo", type=Path, default=Path(__file__).resolve().parents[3])
    parser.add_argument("--output", type=Path)
    parser.add_argument("--check", action="store_true")
    args = parser.parse_args()
    if args.check:
        first = build(args.repo)
        if first != build(args.repo):
            raise ValueError("nondeterministic output")
        print(json.dumps({"status": "REPRODUCIBLE", "files": first[2]["fileHashes"]}, sort_keys=True))
    elif args.output:
        manifest = write_dataset(args.repo, args.output)
        print(json.dumps({"status": "CREATED", "files": manifest["fileHashes"]}, sort_keys=True))
    else:
        parser.error("--check or --output required")


if __name__ == "__main__":
    main()
