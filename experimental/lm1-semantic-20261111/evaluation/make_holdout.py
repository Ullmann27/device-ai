#!/usr/bin/env python3
"""Freeze independently authored evaluation cases."""

import argparse
import collections
import hashlib
import json
from pathlib import Path
import re
import unicodedata

SEED = 2026111103
BASE = "1d9eab1275a7fcc92ad4c5e2d1dd52d1db411667"
LABELS = {"0": "no_action", "1": "open", "2": "search", "3": "note"}
FAMILIES = (
    ("recipient", "Vera erhält „{a}“ als Auftrag; du erhältst „{b}“ als Auftrag.", 1),
    ("delegation", "Dir weise ich „{a}“ zu; an Vera delegiere ich „{b}“.", 0),
    ("parenthesis", "Dein Auftrag steht außerhalb der Klammern: „{a}“ („{b}“).", 0),
    ("insertion", "In Klammern steht bloß ein Einschub („{a}“); dein Auftrag lautet „{b}“.", 1),
    ("relative_request", "Was ich von dir verlange, ist „{a}“; was ich nur erwähne, ist „{b}“.", 0),
    ("relative_exclusion", "Was ich bloß erwähne, ist „{a}“; worum ich dich bitte, ist „{b}“.", 1),
    ("embedded_preference", "Mein Wunsch besteht darin, dass du „{a}“ umsetzt, während „{b}“ unterbleibt.", 0),
    ("embedded_refusal", "Mein Wunsch besteht darin, dass „{a}“ unterbleibt, während du „{b}“ umsetzt.", 1),
)
OBJECTS = (
    "Glasboot", "Morgenkiesel", "Kreidehafen", "Silberpfad",
    "Wolkenkarte", "Fichtenbogen", "Sternenschale", "Bernsteinspur",
    "Wiesenfenster", "Kupfergarten", "Blattbrücke", "Quarzspindel",
    "Nebelrad", "Sonnenkiste", "Eisfaden", "Mondsteg",
)


def digest(data):
    return hashlib.sha256(data).hexdigest()


def command(label, obj):
    return {
        "0": "Tu gar nichts",
        "1": f"Öffne die Datei {obj}.txt",
        "2": f"Suche Informationen über {obj}",
        "3": f"Erstelle eine Notiz über {obj}",
    }[str(label)]


def bag(text):
    return collections.Counter(re.findall(r"\w+", text.casefold()))


def normalized(text):
    return " ".join(unicodedata.normalize("NFKC", text).casefold().split())


def payload(rows):
    return ("id\tlabel\tprompt\n" + "".join(
        f"{r['id']}\t{r['label']}\t{r['prompt']}\n" for r in rows)).encode()


def build():
    rows = []
    for f, (family, template, selected) in enumerate(FAMILIES):
        for replica in range(2):
            obj = OBJECTS[f * 2 + replica]
            for edge in range(4):
                labels = (edge, (edge + 1) % 4)
                pair = f"h-{f:02d}-{replica}-{edge}"
                pair_rows = []
                for flip in range(2):
                    ordered = labels if flip == 0 else labels[::-1]
                    prompt = template.format(a=command(ordered[0], obj),
                                             b=command(ordered[1], obj))
                    label = str(ordered[selected])
                    row = {"id": pair + f"-{flip}", "label": label,
                           "prompt": prompt, "pairId": pair, "familyId": family,
                           "objectId": obj, "promptSha256": digest(prompt.encode())}
                    rows.append(row)
                    pair_rows.append(row)
                assert bag(pair_rows[0]["prompt"]) == bag(pair_rows[1]["prompt"])
                assert pair_rows[0]["label"] != pair_rows[1]["label"]
                assert len(pair_rows[0]["prompt"].encode()) == len(pair_rows[1]["prompt"].encode())
    assert len(rows) == 128
    assert collections.Counter(r["label"] for r in rows) == dict.fromkeys("0123", 32)
    assert len({normalized(r["prompt"]) for r in rows}) == 128
    rows.sort(key=lambda r: digest(f"{SEED}:{r['id']}".encode()))

    retention = []
    for i, obj in enumerate(("Küstenarchiv", "Tannenufer", "Seidenrampe", "Lehmstern",
                             "Wasserfibel", "Bucheninsel", "Kieselkranz", "Kupferbogen")):
        for form, text in enumerate((f"Bitte zeige den Inhalt der Datei {obj}.txt.",
                                    f"Ich möchte {obj}.txt als Dokument geöffnet haben.")):
            retention.append({"id": f"r-open-{i}-{form}", "label": "1", "prompt": text})
        for form, text in enumerate((f"Bitte ermittle Informationen zum Thema {obj}.",
                                    f"Ich möchte eine Recherche über {obj}.")):
            retention.append({"id": f"r-search-{i}-{form}", "label": "2", "prompt": text})

    texts = (
        "Eine Schublade lässt sich öffnen und schließen. Ihr Inhalt bleibt beim Schließen im Schrank.",
        "Ein Kompass zeigt eine Richtung an. Die Himmelsrichtungen helfen dabei, Wege zu beschreiben.",
        "Ein Kalender ordnet Tage zu Wochen und Monaten. Termine können einem bestimmten Tag zugeordnet werden.",
        "Eine Waage dient dazu, eine Masse zu bestimmen. Das Messergebnis wird mit einer Einheit angegeben.",
        "Ein Schlüssel passt zu einem bestimmten Schloss. Ein anderer Schlüssel muss dieses Schloss nicht öffnen.",
        "Eine Linie verbindet zwei Punkte. Mehrere Linien können zusammen eine geschlossene Figur bilden.",
        "Ein Inhaltsverzeichnis nennt die Abschnitte eines Textes. Die Seitenangaben erleichtern das Auffinden.",
        "Ein Echo ist ein zurückgeworfener Schall. Zwischen dem ursprünglichen Laut und dem Echo liegt Zeit.",
    )
    files = {"semantic.tsv": payload(rows), "novel-retention.tsv": payload(retention),
             "novel-text.txt": ("\n".join(texts) + "\n").encode(),
             "metadata.json": (json.dumps(rows, ensure_ascii=False, indent=2,
                                           sort_keys=True) + "\n").encode()}
    manifest = {
        "schema": "lm1-independent-holdout-v1", "seed": SEED, "basisCommit": BASE,
        "labelMap": LABELS, "semanticRows": 128, "contrastPairs": 64,
        "syntaxFamilies": 8, "classCounts": dict.fromkeys("0123", 32),
        "novelRetentionRows": 32, "novelTextRows": len(texts),
        "files": {name: {"sha256": digest(data), "bytes": len(data)}
                  for name, data in files.items()},
        "generatorSha256": digest(Path(__file__).read_bytes()),
        "origin": "Separately authored synthetic German instructions by evaluator agent",
        "status": "FROZEN_BEFORE_MODEL_EVALUATION",
        "blindness": "Organizational; shared filesystem and Git are not access-isolated",
        "pairedWordMultisetsEqual": True, "pairedUtf8ByteLengthsEqual": True,
        "scope": "Unseen syntactic templates and object names; shared semantic primitives",
        "limitations": [
            "Eight syntax families include paired variants of four broader constructions.",
            "These are controlled synthetic instructions, not population-sampled language.",
            "Quoted imperatives simplify label assignment and do not test free conversation.",
            "No held-out model result has been used to change these examples.",
            "Native token-length checks and train-overlap audit are still required.",
        ],
    }
    return files, manifest


def main():
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("--output", type=Path, required=True)
    args = parser.parse_args()
    files, manifest = build()
    assert (files, manifest) == build()
    args.output.mkdir(parents=True, exist_ok=False)
    for name, data in files.items():
        (args.output / name).write_bytes(data)
    (args.output / "manifest.json").write_text(json.dumps(manifest, indent=2,
                                                        ensure_ascii=False) + "\n")
    print(json.dumps({"rows": 128, "pairs": 64, "families": 8,
                      "manifestSha256": digest((args.output / "manifest.json").read_bytes())}))


if __name__ == "__main__":
    main()
