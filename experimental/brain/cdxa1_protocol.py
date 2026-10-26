"""Authored synthetic CDXA1 TRAIN/DEV generator; no model/evaluator access.

Templates generate TRAIN labels only. Inference never imports this file.
"""
import json
import random
import sys
from pathlib import Path

TRAIN_NAMES = ["Asta", "Bert", "Cora", "Dino", "Elva", "Finn", "Gero", "Hedi"]
DEV_NAMES = ["Ivo", "Jule", "Keno", "Lina", "Mona", "Niko", "Oda", "Pia"]
TRAIN_VALUES = [["Aachen", "Jena", "Kiel", "Potsdam", "Rostock", "Weimar", "Koblenz", "Halle"],
                ["Bäcker", "Tischler", "Gärtner", "Lehrer", "Sänger", "Pilot"],
                ["einen Hut", "eine Kappe", "einen Helm", "eine Mütze"]]
DEV_VALUES = [["Mainz", "Erfurt", "Fulda", "Lübeck", "Bamberg", "Siegen"],
              ["Koch", "Maler", "Geiger", "Bote", "Schneider", "Richter"],
              ["einen Kranz", "einen Turban", "ein Tuch", "eine Haube"]]


def fact(name, relation, value, variant, negated=False):
    neg = "nicht " if negated else ""
    return ([f"{name} wohnt {neg}in {value}.", f"{name} lebt {neg}in {value}."],
            [f"{name} arbeitet {neg}als {value}.", f"{name} ist {neg}von Beruf {value}."],
            [f"{name} trägt {neg}{value}.", f"{name} hat {neg}{value} auf."])[relation][variant % 2]


def question(name, relation, variant):
    return ([f"Wo wohnt {name}?", f"Wo lebt {name}?", f"In welcher Stadt wohnt {name}?"],
            [f"Was arbeitet {name}?", f"Welchen Beruf hat {name}?", f"Was ist {name} von Beruf?"],
            [f"Was trägt {name}?", f"Was hat {name} auf?", f"Welche Kleidung trägt {name}?"])[relation][variant % 3]


def build():
    rng = random.Random(20261026)
    def split(kind, names, values, positive_count, negative_count):
        cases = []
        for i in range(positive_count):
            relation = i % 3; name = names[i % len(names)]
            value = values[relation][(i // 3) % len(values[relation])]
            target = fact(name, relation, value, i // 3)
            sources = [target]
            if i % 3 >= 1:
                r2 = (relation + 1) % 3
                sources.append(fact(name, r2, values[r2][i % len(values[r2])], i))
            if i % 3 == 2:
                other = names[(i+1) % len(names)]
                sources.append(fact(other, relation, values[relation][(i+1) % len(values[relation])], i))
            rng.shuffle(sources)
            cases.append({"id": f"{kind}-positive-{i:02d}", "stratum": "literal_supported" if kind == "train" else "unseen_entity_new_fact",
                          "evidence": " ".join(sources), "question": question(name, relation, i // 6),
                          "answer": target, "goldSpan": value, "supported": True})
        for i in range(negative_count):
            relation = i % 3; name = names[i % len(names)]; mode = i % 3
            value = values[relation][i % len(values[relation])]
            if mode == 0:
                sources = [fact(names[(i+1) % len(names)], relation, value, i)]
                stratum = "absent_entity"
            elif mode == 1:
                r2 = (relation+1) % 3
                sources = [fact(name, r2, values[r2][i % len(values[r2])], i)]
                stratum = "absent_relation"
            else:
                sources = [fact(name, relation, value, i, negated=True),
                           fact(names[(i+1) % len(names)], relation, values[relation][(i+1) % len(values[relation])], i+1)]
                rng.shuffle(sources); stratum = "negated_only"
            cases.append({"id": f"{kind}-negative-{i:02d}", "stratum": stratum,
                          "evidence": " ".join(sources), "question": question(name, relation, i // 3),
                          "answer": "UNBEKANNT", "goldSpan": None, "supported": False})
        return cases
    train = split("train", TRAIN_NAMES, TRAIN_VALUES, 48, 16)
    dev = split("dev", DEV_NAMES, DEV_VALUES, 24, 18)
    for i in range(12):
        relation = i % 3; name = TRAIN_NAMES[i % len(TRAIN_NAMES)]
        value = DEV_VALUES[relation][(i//3) % len(DEV_VALUES[relation])]
        target = fact(name, relation, value, i)
        other = fact(TRAIN_NAMES[(i+1) % 8], relation, DEV_VALUES[relation][(i+1) % len(DEV_VALUES[relation])], i+1)
        sources = [target, other]; rng.shuffle(sources)
        dev.append({"id":f"dev-changed-{i:02d}", "stratum":"known_entity_changed_fact", "evidence":" ".join(sources),
                    "question":question(name, relation, i//3), "answer":target, "goldSpan":value, "supported":True})
    challenges = [
        ("Ivo wohnt in Mainz. Ivo wohnt in Erfurt.", "Wo wohnt Ivo?", "UNBEKANNT", "contradictory"),
        ("Ivo wohnt in Mainz. Jule wohnt in Erfurt.", "Wo wohnen Ivo und Jule?", "UNBEKANNT", "multiple_subjects"),
        ("Ivo wohnt nicht in Mainz, sondern in Erfurt.", "Wo wohnt Ivo?", "UNBEKANNT", "contrast_correction"),
        ("Ivo wohnt in Mainz.", "Wie alt ist Ivo?", "UNBEKANNT", "unseen_relation"),
        ("Ivo hat ein Rad.", "Was hat Ivo?", "Ivo hat ein Rad.", "unseen_relation"),
        ("Ivo wohnt in Mainz.", "Wo wohnt er?", "UNBEKANNT", "pronoun"),
        ("In Mainz wohnt Ivo.", "Wo wohnt Ivo?", "In Mainz wohnt Ivo.", "inverted_syntax"),
        ("Ivo wohnt in Mainz.", "Was ist Ivos Wohnort?", "Ivo wohnt in Mainz.", "unseen_question_wording")
    ]
    for i, (e, q, a, label) in enumerate(challenges):
        dev.append({"id":f"dev-challenge-{i:02d}","stratum":"out_of_family_"+label,
                    "evidence":e,"question":q,"answer":a,"goldSpan":None,"supported":False,"outOfFamily":True})
    assert len(train) == 64 and len(dev) == 62
    inputs = lambda xs: {(x["evidence"],x["question"]) for x in xs}
    assert len(inputs(train)) == len(train) and len(inputs(dev)) == len(dev)
    assert not inputs(train) & inputs(dev)
    return {
        "schema":"CDXA1_NEURAL_POINTER_PROTOCOL_V1", "status":"FROZEN_BEFORE_TRAINING_OR_PREDICTION", "experiment":"EXP-05",
        "task":"Return full unique supporting evidence sentence verbatim or UNBEKANNT; 3 bounded German factual relation families",
        "architecture":"Shared16hidden tanh neural pointer softmax over all source whitespace-token chunks, UNBEKANNT, EOS. Previous predicted pointer is state. No forced next-pointer or EOS, no answer/template supplied at inference.",
        "features":"CDXA1_FEATURES_V1; generic CSX-style overlap/binding/negation/boundary features,32FNV1a word/3-4gram buckets and learned MLP; see source, no semantic proof",
        "seed":91, "hidden":16, "epochs":[0,10,30,80], "rate":0.01, "batchSize":64, "optimizer":"Adam beta1=.9 beta2=.999 epsilon=1e-8; TRAIN teacher-forced pointer CE; no DEV gradients",
        "maxOutputTokens":128, "maxEvidenceBytes":1024, "maxQuestionBytes":256, "maxSourceTokens":128,
        "selection":"Highest in-family DEV exact+EOS, then fewer epochs. Every stage/stratum/challenge output retained. No finalTEST tuning.",
        "comparators":{"rule":"Separate deterministic lexical relation/name/negation extraction baseline, never neural fallback",
                       "csx":"Actual ContinuumResidualSpanCore multiTokenSpans/identifierPlaceholders/entityProximity/excludeCueWords, same TRAIN plus goldSpan annotations,30epochs,.025rate; expand selected literal span to source sentence for format fairness",
                       "cdx":"Actual TinyContextDecoder same64TRAIN,seed91,rate.035,120000updates, raw and train-mean-centered×32; unchanged16D frozenSWB1; no app integration"},
        "provenance":{"author":"CDX implementation agent, synthetic public-only fixtures before predictions", "generatorSeed":20261026,
                      "trainNames":TRAIN_NAMES,"unseenDevNames":DEV_NAMES,"trainValues":TRAIN_VALUES,"unseenDevValues":DEV_VALUES,
                      "independentTest":"Separate evaluator, withheld until source/checkpoint/tokenizer/data/inference freeze; not this author's dataset"},
        "limits":["Neural retrieval/copying primitive with engineered inputs, not general learned semantics or free paraphrase generation",
                  "Existing CSX already has related features; this model adds autoregressive learned pointer transitions/EOS, not a second runtime",
                  "Deterministic baseline may equal/beat model; no advantage over rule-based extraction presumed", "Out-of-family and contradictory challenges separately reported", "No runtime promotion or owner weights"],
        "train":train, "dev":dev}


if __name__ == "__main__":
    p = Path(sys.argv[1])
    with p.open("x", encoding="utf-8") as output:
        json.dump(build(), output, indent=2, ensure_ascii=False); output.write("\n")
