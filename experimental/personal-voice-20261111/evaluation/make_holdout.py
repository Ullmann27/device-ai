#!/usr/bin/env python3
"""Authoring source for independent synthetic TEST; never training input."""
import collections
import json
import re
from pathlib import Path


def wordbag(text):
    return collections.Counter(re.findall(r"\w+", text.casefold(), flags=re.UNICODE))


PAIRS = [
    (0, 1, "quoted_negation", "Nur lesen darfst du „die Galerie öffnen“; wirklich öffnen darfst du sie nicht. Tu genau das Erlaubte.", "Nur lesen darfst du „die Galerie nicht öffnen“; wirklich öffnen darfst du sie. Tu genau das Erlaubte."),
    (0, 1, "permission_scope", "Untersagt ist das Öffnen von Kiesel; erlaubt ist das Lesen des Namens. Erledige jetzt die erlaubte Handlung.", "Erlaubt ist das Öffnen von Kiesel; untersagt ist das Lesen des Namens. Erledige jetzt die erlaubte Handlung."),
    (0, 1, "quoted_instruction", "„Öffne Mohn“ ist der zitierte Vorschlag; „Tu nichts“ ist mein jetziger Auftrag.", "„Tu nichts“ ist der zitierte Vorschlag; „Öffne Mohn“ ist mein jetziger Auftrag."),
    (0, 1, "late_correction", "Öffne den Kalender. Halt, korrigiere das: Lass den Kalender zu.", "Lass den Kalender zu. Halt, korrigiere das: Öffne den Kalender."),
    (0, 1, "speech_act", "Würde sich die Galerie öffnen, wenn ich den Knopf drücke? Ich möchte das nur wissen.", "Die Galerie soll sich jetzt öffnen. Drücke bitte den passenden Knopf."),
    (0, 1, "austrian_colloquial", "Geh, lass den Rechner noch zu, i frag nur, ob der überhaupt da is.", "Geh, mach ma den Rechner auf, den brauch i jetzt zum Zusammenzählen."),
    (0, 1, "ambiguous_reference", "Du kennst zwei Ordner. Öffne den anderen, aber ich sage noch nicht, welchen ich meine.", "Du kennst zwei Ordner. Mit dem anderen meine ich den zweiten: Öffne Ordner Flieder."),
    (0, 1, "repair_retraction", "Mein Satz war „Öffne Notizen“, aber das war ein Versprecher. Bitte warte.", "Mein Satz war „Bitte warte“, aber das war ein Versprecher. Öffne Notizen."),
    (1, 2, "negation_scope", "Öffne Kiesel, suche nicht nach Mohn.", "Suche nach Mohn, öffne Kiesel nicht."),
    (1, 2, "alternative_clause", "Statt nach Karten zu suchen, öffne den Kalender.", "Statt den Kalender zu öffnen, suche nach Karten."),
    (1, 2, "quoted_instruction", "„Suche Atlas“ war nur ein Beispiel; „Öffne Atlas“ ist der Auftrag.", "„Öffne Atlas“ war nur ein Beispiel; „Suche Atlas“ ist der Auftrag."),
    (1, 2, "late_correction", "Suche nach den Zeichnungen. Nein, ändere den Auftrag: Öffne die Galerie.", "Öffne die Galerie. Nein, ändere den Auftrag: Suche nach den Zeichnungen."),
    (1, 2, "reference_state", "Geplant war Öffnen von Mohn, verworfen wurde Suchen nach Mohn. Führe die geplante Handlung aus.", "Geplant war Suchen nach Mohn, verworfen wurde Öffnen von Mohn. Führe die geplante Handlung aus."),
    (1, 2, "austrian_colloquial", "Na geh, nix nachschauen im Netz; mach ma bitte die Karten-App auf.", "Na geh, die Karten-App lass zu; schau ma bitte nach dem Wanderweg im Netz."),
    (1, 2, "transcript_repair", "Erkannt wurde „suche Wolke“. Meine Wortlautkorrektur lautet: „Öffne Wolke“.", "Erkannt wurde „Öffne Wolke“. Meine Wortlautkorrektur lautet: „suche Wolke“."),
    (1, 2, "spoken_disfluency", "Also, äh, die Suche lassen wir. Was ich brauche, ist die offene Galerie, jetzt bitte.", "Also, äh, die Galerie lassen wir. Was ich brauche, ist eine Suche nach Kiesel, jetzt bitte."),
    (2, 3, "negation_scope", "Suche nach der Nummer sieben, merke sie dir nicht.", "Merke dir die Nummer sieben, suche nicht nach ihr."),
    (2, 3, "quoted_instruction", "„Merke den Namen Mohn“ ist nur das Beispiel. Mein Auftrag heißt „Suche den Namen Mohn“.", "„Suche den Namen Mohn“ ist nur das Beispiel. Mein Auftrag heißt „Merke den Namen Mohn“."),
    (2, 3, "late_correction", "Merke dir: Testfarbe Ocker. Nein, zurück: Suche nach dem Wort Ocker.", "Suche nach dem Wort Ocker. Nein, zurück: Merke dir: Testfarbe Ocker."),
    (2, 3, "permission_scope", "Erlaubt ist die Suche nach Mohn; untersagt ist das Speichern des Namens. Erledige jetzt die erlaubte Handlung.", "Untersagt ist die Suche nach Mohn; erlaubt ist das Speichern des Namens. Erledige jetzt die erlaubte Handlung."),
    (2, 3, "restricted_alternative", "Suche den Begriff Kranich; speichere keine Notiz dazu.", "Speichere eine Notiz zum Begriff Kranich; führe keine Suche dazu aus."),
    (2, 3, "austrian_colloquial", "Schau ma nach, was das Wort Zunder bedeutet; ins Gedächtnis brauchst das ned nehmen.", "Nimm ma ins Gedächtnis: Das Testwort ist Nebelrose. Nachschauen brauchst ned."),
    (2, 3, "reference_state", "Die Suche nach Zirbe ist bestätigt, die Notiz „Testwort Zirbe“ ist zurückgezogen. Erledige das Bestätigte.", "Die Notiz „Testwort Zirbe“ ist bestätigt, die Suche nach Zirbe ist zurückgezogen. Erledige das Bestätigte."),
    (2, 3, "spoken_disfluency", "Ich wollte das merken, also nein, ich fang noch mal an: Such nach der Bedeutung von Kobalt.", "Ich wollte das suchen, also nein, ich fang noch mal an: Merk dir das Testwort Kobalt."),
    (3, 0, "quoted_instruction", "„Speichere Testwort Hasel“ ist mein Auftrag. „Bitte nichts tun“ war nur das Zitat.", "„Bitte nichts tun“ ist mein Auftrag. „Speichere Testwort Hasel“ war nur das Zitat."),
    (3, 0, "permission_scope", "Erlaubt ist das Speichern der Testfarbe Bernstein; untersagt ist das bloße Erwähnen. Erledige jetzt die erlaubte Handlung.", "Untersagt ist das Speichern der Testfarbe Bernstein; erlaubt ist das bloße Erwähnen. Erledige jetzt die erlaubte Handlung."),
    (3, 0, "late_correction", "Speichere nichts. Ich korrigiere mich: Merke dir das Wort Zeder.", "Merke dir das Wort Zeder. Ich korrigiere mich: Speichere nichts."),
    (3, 0, "quotation_scope", "Merke dir „nicht speichern“ als Testwort. Das ist mein Auftrag.", "„Merke dir das Testwort“ ist nicht mein Auftrag. Speichern sollst du nichts."),
    (3, 0, "transcript_uncertainty", "Die Erkennung schrieb „nicht merken“. Ich bestätige die Korrektur: Merke dir Testzahl sieben.", "Die Erkennung schrieb „merken“ oder „nicht merken“. Ohne geklärten Wortlaut bitte erst nachfragen."),
    (3, 0, "austrian_colloquial", "Das merkst da bitte: Unser erfundenes Testwort is Gänseblume.", "Das merkst da bitte ned; Gänseblume war bloß a Beispiel, koa Auftrag."),
    (3, 0, "speech_act", "Ich gebe dir jetzt den Auftrag, die Testfarbe Aprikose zu notieren.", "Ich erzähle dir nur, dass ich die Testfarbe Aprikose gestern notiert habe."),
    (3, 0, "ambiguous_reference", "Mit dem zweiten Wort meine ich ausdrücklich Zimt. Merke dir dieses Wort.", "Merke dir das zweite Wort, aber weder die Liste noch der Bezug sind hier bekannt."),
]


def build(root):
    root.mkdir(parents=True, exist_ok=True)
    rows, metadata = [], []
    for n, (left, right, category, a, b) in enumerate(PAIRS, 1):
        same = wordbag(a) == wordbag(b)
        for variant, label, prompt in (("a", left, a), ("b", right, b)):
            ident = f"voice-test-{n:02d}-{variant}"
            rows.append((ident, str(label), prompt))
            metadata.append({"id": ident, "pair_id": f"voice-test-{n:02d}", "label": label,
                             "category": category, "wordbag_identical_pair": same,
                             "provenance": "independently_authored_synthetic_public_research",
                             "audio": None, "owner_specific": False,
                             "requires_clarification": category in ("ambiguous_reference", "transcript_uncertainty") and label == 0,
                             "not_measured_by_label": ["parameters", "actual_action", "ASR_accuracy", "natural_response_quality"]})
    assert len(rows) == 64
    assert collections.Counter(x[1] for x in rows) == {str(x): 16 for x in range(4)}
    assert len({x[2] for x in rows}) == 64
    assert max(len(x[2].encode()) for x in rows) <= 220
    tsv = "id\tlabel\tprompt\n" + "".join("\t".join(row) + "\n" for row in rows)
    (root / "test.tsv").write_text(tsv, encoding="utf-8")
    (root / "test-metadata.json").write_text(json.dumps(metadata, ensure_ascii=False, indent=2) + "\n", encoding="utf-8")
    return {"cases": 64, "pairs": 32, "wordbag_identical_pairs": sum(wordbag(x[3]) == wordbag(x[4]) for x in PAIRS),
            "labels": dict(collections.Counter(x[1] for x in rows)), "max_prompt_utf8_bytes": max(len(x[2].encode()) for x in rows)}


if __name__ == "__main__":
    print(json.dumps(build(Path(__file__).parent / "holdout"), indent=2))
