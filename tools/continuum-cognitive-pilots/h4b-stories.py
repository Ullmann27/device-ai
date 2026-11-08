#!/usr/bin/env python3
"""Prospective German/de-AT return-reference stories; keys come from story structure only."""
import hashlib
import json
from pathlib import Path
import random
import sys

# family -> list of (noun, gender, dative-plural variant or None, ASR variant or None)
# A family groups nouns that legitimately refer to one topic (compound head, inflection, ASR).
# Plural variants keep the stem (regular inflection); umlaut plurals are deliberately excluded.
FAMILIES = {
    "treiber": [("Druckertreiber", "m", None, None), ("Grafiktreiber", "m", None, None), ("Drucker", "m", "Druckern", None)],
    "termin": [("Zahnarzttermin", "m", None, None), ("Friseurtermin", "m", None, None)],
    "versicherung": [("Autoversicherung", "f", None, None), ("Haushaltsversicherung", "f", None, None)],
    "rechnung": [("Stromrechnung", "f", None, None), ("Gasrechnung", "f", None, None), ("Rechnung", "f", "Rechnungen", None)],
    "vertrag": [("Handyvertrag", "m", None, None), ("Mietvertrag", "m", None, None)],
    "wohnung": [("Gemeindewohnung", "f", None, None), ("Eigentumswohnung", "f", None, None), ("Wohnungsübergabe", "f", None, "Wohnungsuebergabe")],
    "pickerl": [("Parkpickerl", "n", None, None), ("Jahrespickerl", "n", None, None)],
    "heizung": [("Heizung", "f", "Heizungen", None), ("Heizungsablesung", "f", None, None)],
    "garage": [("Garage", "f", "Garagen", None)],
    "fenster": [("Fenster", "n", "Fenstern", None)],
    "balkon": [("Balkon", "m", "Balkonen", None)],
    "keller": [("Keller", "m", "Kellern", None)],
    "router": [("Router", "m", "Routern", None)],
    "matura": [("Matura", "f", None, None)],
    "sackerl": [("Sackerl", "n", "Sackerln", None)],
    "spital": [("Spital", "n", None, None)],
    "polizze": [("Polizze", "f", "Polizzen", None)],
    "kueche": [("Küche", "f", None, "Kueche")],
    "schluessel": [("Schlüssel", "m", None, "Schluessel")],
    "buegeleisen": [("Bügeleisen", "n", None, "Buegeleisen")],
    "waesche": [("Wäsche", "f", None, "Waesche")],
    "kuehlschrank": [("Kühlschrank", "m", None, "Kuehlschrank")],
    "strassenbahn": [("Straßenbahn", "f", None, "Strassenbahn")],
    "fuehrerschein": [("Führerschein", "m", None, "Fuehrerschein")],
    "steuer": [("Steuererklärung", "f", None, "Steuererklaerung")],
    "kindergarten": [("Kindergartenplatz", "m", None, None)],
    "marille": [("Marillenmarmelade", "f", None, None)],
    "knoedel": [("Semmelknödel", "m", None, "Semmelknoedel")],
    "fahrrad": [("Fahrradservice", "n", None, None)],
    "eltern": [("Elternabend", "m", None, None)],
    "erdaepfel": [("Erdäpfelsalat", "m", None, "Erdaepfelsalat")],
}
HEADS = {"treiber": "Treiber", "termin": "Termin", "versicherung": "Versicherung", "rechnung": "Rechnung",
         "vertrag": "Vertrag", "wohnung": "Wohnung", "pickerl": "Pickerl", "heizung": "Heizung"}
HEAD_GENDER = {"Treiber": "m", "Termin": "m", "Versicherung": "f", "Rechnung": "f", "Vertrag": "m",
               "Wohnung": "f", "Pickerl": "n", "Heizung": "f"}
# Ambiguity stratum: one shared noun with two different qualifiers (both arms should abstain).
AMBIGUOUS_NOUNS = {"rechnung": ("Rechnung", "f"), "termin": ("Termin", "m"), "vertrag": ("Vertrag", "m")}
QUALIFIERS = ["vom Elektriker", "vom Installateur", "vom Zahnarzt", "von der Werkstatt", "vom Vermieter", "von der Bank"]
DATIVE = {"m": "dem", "f": "der", "n": "dem", "pl": "den"}
ACCUSATIVE = {"m": "den", "f": "die", "n": "das", "pl": "die"}
NOMINATIVE = {"m": "Der", "f": "Die", "n": "Das", "pl": "Die"}

# Content words are unique per template so that unrelated topic turns never merge.
TOPIC_TEMPLATES = [
    "Wegen {dat} {N} muss ich heuer noch was erledigen.",
    "Bei {dat} {N} hakt es gerade, das nervt ziemlich.",
    "Zu {dat} {N} hätte ich eine Frage, das pressiert aber nicht.",
    "Schau dir bitte {acc} {N} an, da stimmt irgendwas ned.",
    "{nom} {N} ist mir gestern eingefallen, Gschicht ohne Ende.",
    "Die Sache mit {dat} {N} sollten wir am Freitag klären.",
    "Bei {dat} {N} brauch ich deine Hilfe, ehrlich gsagt.",
    "Apropos {N}: da fehlt noch die Bestätigung vom Amt.",
]
# Every return template carries a cue the ORIGINAL tracker regex recognizes; only the noun differs.
RETURN_TEMPLATES = [
    "Zurück zu {dat} {N} von vorhin.",
    "Und jetzt bitte weiter bei {dat} {N} von davor.",
    "Fortsetzen bitte mit {dat} {N} von vorhin.",
]
DISTRACTORS = [
    "Passt, danke dir, bis später.",
    "Der Mitarbeiter vom Finanzamt hat wegen dem Beitrag angerufen.",
]
STRATA = ["long", "inflected", "asr", "head", "short", "ambiguous", "none"]


def fold(word):
    return word.lower().replace("ä", "ae").replace("ö", "oe").replace("ü", "ue").replace("ß", "ss")


def surfaces(entry):
    return [s for s in entry[:1] + entry[2:] if s]


def related(a, b):
    """Conservative independence check: no surface of a is a prefix/suffix of any surface of b."""
    for x in a:
        for y in b:
            fx, fy = fold(x), fold(y)
            short, long_ = (fx, fy) if len(fx) <= len(fy) else (fy, fx)
            if long_.startswith(short) or long_.endswith(short):
                return True
    return False


def pick_entry(rng, family, predicate=lambda e: True):
    options = [e for e in FAMILIES[family] if predicate(e)]
    return rng.choice(options) if options else None


def fill(template, noun, gender):
    return template.format(N=noun, dat=DATIVE[gender], acc=ACCUSATIVE[gender], nom=NOMINATIVE[gender]) \
        .replace("Zu dem", "Zum").replace("Bei dem", "Beim")


def generate(split):
    seed, count = {"dev": (50321, 60), "test": (77419, 180)}[split]
    rng = random.Random(seed)
    stories = []
    for number in range(count):
        stratum = STRATA[number % len(STRATA)]
        while True:
            story = build(rng, stratum, f"{split}-{number:03d}")
            if story:
                break
        stories.append(story)
    return {"schemaVersion": 1, "origin": "prospective self-authored; not blind", "split": split,
            "seed": seed, "stories": stories}


def build(rng, stratum, story_id):
    families = list(FAMILIES)
    rng.shuffle(families)
    topic_count = rng.randint(2, 4)
    chosen = []  # (family, entry)
    # Target topic by stratum
    if stratum == "long":
        fam = rng.choice([f for f in families if any(len(e[0]) > 10 and e[2] is None and e[3] is None for e in FAMILIES[f])])
        target = (fam, pick_entry(rng, fam, lambda e: len(e[0]) > 10 and e[2] is None and e[3] is None))
    elif stratum == "inflected":
        fam = rng.choice([f for f in families if any(e[2] for e in FAMILIES[f])])
        target = (fam, pick_entry(rng, fam, lambda e: e[2]))
    elif stratum == "asr":
        fam = rng.choice([f for f in families if any(e[3] for e in FAMILIES[f])])
        target = (fam, pick_entry(rng, fam, lambda e: e[3]))
    elif stratum == "head":
        fam = rng.choice(list(HEADS))
        target = (fam, pick_entry(rng, fam, lambda e: len(e[0]) > len(HEADS[fam])))
    elif stratum == "ambiguous":
        fam = rng.choice(list(AMBIGUOUS_NOUNS))
        noun, gender = AMBIGUOUS_NOUNS[fam]
        first, second = rng.sample(QUALIFIERS, 2)
        target = (fam, (f"{noun} {first}", gender, None, None))
    else:  # short, none
        fam = rng.choice([f for f in families if any(len(e[0]) <= 10 and e[3] is None for e in FAMILIES[f])])
        target = (fam, pick_entry(rng, fam, lambda e: len(e[0]) <= 10 and e[3] is None))
    chosen.append(target)
    if stratum == "ambiguous":
        chosen.append((fam, (f"{noun} {second}", gender, None, None)))
    for family in families:
        if len(chosen) >= topic_count + (1 if stratum == "ambiguous" else 0):
            break
        if family in {c[0] for c in chosen}:
            continue
        entry = pick_entry(rng, family)
        if any(related(surfaces(entry), surfaces(c[1])) for c in chosen):
            continue
        chosen.append((family, entry))
    if stratum == "none":
        # Target is never mentioned; the remaining topics form the dialogue.
        mentioned = chosen[1:]
        if len(mentioned) < 2:
            return None
    else:
        mentioned = chosen
    rng.shuffle(mentioned)
    # The referenced topic must be paused, so a non-anchor topic always closes the dialogue.
    if stratum != "none" and mentioned[-1][0] == target[0]:
        swap = next(i for i, c in enumerate(mentioned) if c[0] != target[0])
        mentioned[swap], mentioned[-1] = mentioned[-1], mentioned[swap]
    templates = TOPIC_TEMPLATES[:]
    rng.shuffle(templates)
    turns = []
    turn_topic = {}
    for index, (family, entry) in enumerate(mentioned):
        noun, gender = entry[0], entry[1]
        if stratum == "asr" and family == target[0] and rng.random() < .5:
            noun = entry[3]  # ASR variant spoken in the topic turn, canonical in the return
        turns.append({"text": fill(templates[index], noun, gender), "topic": index})
        turn_topic[len(turns) - 1] = index
    # One distractor somewhere before the final topic turn or at the end
    distractor = rng.choice(DISTRACTORS)
    position = rng.randrange(1, len(turns) + 1)
    turns.insert(position, {"text": distractor, "topic": None})
    topic_turns = {t["topic"]: i for i, t in enumerate(turns) if t["topic"] is not None}
    # Return noun by stratum
    entry = target[1]
    if stratum == "long" or stratum == "short":
        noun, gender = entry[0], entry[1]
    elif stratum == "inflected":
        noun, gender = entry[2], "pl"
    elif stratum == "asr":
        mentioned_noun = next(t["text"] for t in turns if t["topic"] is not None and
                              mentioned[t["topic"]][0] == target[0])
        noun = entry[0] if entry[3] in mentioned_noun else entry[3]
        gender = entry[1]
    elif stratum == "head":
        noun = HEADS[target[0]]
        gender = HEAD_GENDER[noun]
    elif stratum == "ambiguous":
        noun, gender = AMBIGUOUS_NOUNS[target[0]]
    else:
        noun, gender = entry[0], entry[1]
    return_text = fill(rng.choice(RETURN_TEMPLATES), noun, gender)
    if stratum == "none":
        key = {"kind": "NONE"}
    elif stratum == "ambiguous":
        indices = [i for i, c in enumerate(mentioned) if c[0] == target[0]]
        key = {"kind": "AMBIGUOUS", "turns": sorted(topic_turns[i] for i in indices)}
    else:
        index = next(i for i, c in enumerate(mentioned) if c[0] == target[0])
        key = {"kind": "TOPIC", "turn": topic_turns[index]}
    for text in [t["text"] for t in turns] + [return_text]:
        assert "\t" not in text and "|||" not in text
    return {"id": story_id, "stratum": stratum, "turns": turns, "returnText": return_text, "key": key,
            "topics": [{"family": f, "noun": e[0]} for f, e in mentioned]}


def main():
    if len(sys.argv) != 3 or sys.argv[1] not in ("dev", "test"):
        raise SystemExit("usage: h4b-stories.py dev|test OUT_DIR")
    document = generate(sys.argv[1])
    out = Path(sys.argv[2])
    out.mkdir(parents=True, exist_ok=True)
    path = out / "stories.json"
    path.write_text(json.dumps(document, indent=2, ensure_ascii=False) + "\n", encoding="utf-8")
    rows = ["id\tstratum\tturns\treturn"]
    for story in document["stories"]:
        rows.append("\t".join([story["id"], story["stratum"],
                               " ||| ".join(t["text"] for t in story["turns"]), story["returnText"]]))
    (out / "stories.tsv").write_text("\n".join(rows) + "\n", encoding="utf-8")
    counts = {}
    for story in document["stories"]:
        counts[story["stratum"]] = counts.get(story["stratum"], 0) + 1
    print(json.dumps({"split": document["split"], "stories": len(document["stories"]), "strata": counts,
                      "jsonSha256": hashlib.sha256(path.read_bytes()).hexdigest()}))


if __name__ == "__main__":
    main()
