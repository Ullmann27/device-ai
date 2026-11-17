# CTX1 (S1/S2): Kontextroutine vor dem Gedächtnisdialog – Vorregistrierung (24.10.2026, 01:40 Wien)

Festgelegt **vor** dem einzigen TEST-Lauf. Reines Forschungsmodul im Test-Quellset
(`app/src/test/java/at/heinz/deviceai/intelligence/maxultra/Ctx1*.kt`): nichts in der APK, nichts im Chat, nichts im
dauerhaften Gedächtnis. Auftrag: Sols Übergabe §6, S1 (heutiger Kern gegen begrenzte Kontextroutine auf ungesehenen
Mehrturn-Dialogen) und S2 (Rückbezüge aus freiem Text statt vorgeparster Strukturen wie in TTT1).

## Frage

Senkt eine begrenzte Kontextroutine **vor dem unveränderten Dialogkern** die falschen Behauptungen in deutschen
Mehrturn-Gesprächen über gespeicherte Besitzer-Fakten, ohne richtige Antworten zu verlieren?

## Systeme

- **B0 – heutiger Kern:** `ContinuumMemoryDialogue.reply` (Anschlusswörter „darüber/dazu/damit/weiter/noch mehr/und
  warum/und wie“, Themenübernahme, TF-IDF-Zeichen-n-Gramme mit Wortbeleg-Schwelle, Rechtschreibhilfe ab 6 Buchstaben)
  mit dem echten SWB1-Testmodell (`continuum-neural-swb1.base64`) über bestätigte Besitzer-Fakten
  (`USER_FACT`, `USER_CONFIRMED`, `OWNER_CONFIRMED`, `CONFIRMED`). Zitiert bis zu drei Erinnerungen.
- **B1 – `Ctx1DialogueContext` vor demselben Kern** (Regeln nur an TRAIN/DEV entwickelt, jetzt eingefroren):
  1. Entitätsregister aus den Fakten: „X ist mein/meine …“ (Person, Geschlecht aus dem Possessiv), „Unser/Unsere … heißt X.“
     (Tier, Gattung als Alias), „Mein/Meine X ist/hat …“ (Gegenstand); Geschlecht aus „-e“-Possessiv, Verkleinerung
     „-chen/-lein“ = sächlich, sonst männlich oder sächlich.
  2. Fürwort (er/ihn/ihm/sie/es) → jüngste Erwähnung mit passendem Geschlecht (höchstens 16 Erwähnungen gemerkt);
     zwei passende aus demselben Zug oder keine → **Rückfrage**.
  3. Kurzer Folgezug ohne Fragezeichen mit Korrekturwort (nein, nicht, sondern, meinte, gemeint, falsch, eigentlich, doch)
     oder beginnend mit „Und“ → letzte Frage für die neue (nicht verneinte) Entität bzw. den neuen Wochentag.
  4. Großgeschriebenes Wort an Namensstelle (nicht am Satzanfang, nicht nach Artikel/Possessiv/Präposition, kein
     Wochentag), das nicht im Register steht → **unbekannt** (keine Antwort aus dem Gedächtnis); ein Fürwort darauf ebenso.
  5. „nicht in <Ort>“ wird aus der Suchanfrage entfernt.
  6. Ist die Entität bekannt, bekommt der Kern nur deren Fakten (bei „X und Y“ beider); ohne Entität alle.
  7. Rechtschreibreparatur nur gegen die Wörter dieser Fakten: genau ein Wort (3–12 Buchstaben), genau ein Kandidat mit
     einem Tastatur-Nachbarfehler oder einem vertauschten Nachbarpaar.
  8. Nach einer aufgelösten Entität verändert eine Zitierung des Kerns die Erwähnungsliste nicht.

## Testdialoge

Generator `Ctx1Corpus` (deterministisch), Besitzer-Fakten in festen Formulierungen (das sind gespeicherte Aussagen),
je Welt drei Personen, ein Tier, ein Gegenstand (bei TIME drei Wochentagstermine), also 17–20 Fakten.
Familien (je Dialog 1–3 Züge, Gold je Zug: Erinnerung(en), RÜCKFRAGE oder NICHTS):

| Familie | Inhalt |
|---|---|
| REF_SIMPLE | Einführung, dann zwei Fragen mit Fürwort |
| REF_TYPO | Fürwortfrage mit einem Tastatur-Nachbarfehler im Prädikatwort |
| REF_AUSTRIAN | Fürwortfrage in österreichischer Umgangsform |
| ELLIPSIS | Frage mit Name, dann „Und X?“ |
| CORRECTION | Frage mit Name, dann Korrektur auf eine andere Person |
| NEGATION | „Nicht X, sondern Y“, danach „Wohnt sie nicht in <Ort von X>?“ |
| SWITCH | Person, dann Tier oder Gegenstand, dann Fürwort auf das Neue |
| RETURN | männliche Person, dann weibliches Tier/Gerät, dann „er“ → Person |
| AMBIGUOUS | zwei gleichgeschlechtliche Personen in einem Zug, dann Fürwort → RÜCKFRAGE |
| NEW_SESSION | Fürwort im ersten Zug → RÜCKFRAGE |
| UNKNOWN | Frage nach nie gespeicherter Person, dann Fürwort darauf → NICHTS |
| TIME | „am Montag“, dann „Und am Mittwoch?“ |

| Split | Seed | Dialoge (Züge) | Wortschatz | SHA-256 der kanonischen Form |
|---|---|---|---|---|
| TRAIN | 20261101 | 180 (405) | bekannt | `bc839ddd584c28f0758afd81338ffc02b4a50ec483af12976f8f01dcc73aa1f2` |
| DEV | 20261102 | 120 (270) | bekannt | `9aa8fce04dfb7aadcbdffb6e3632bf8a553cbbc940dbbbda51f1908c78258eb3` |
| **TEST** | 20261103 | 480 (1080) | **ungesehen**: Namen, Verwandtschaftswörter, Tiere, Gegenstände, Städte, Berufe, Hobbys, Futter, Farben, Termine **und alle Formulierungen der Nutzerzüge** | **`6f2514f3842d9e929770a262ae4475c1822cc4a3e72c9a639d257672d23d97f0`** |

Der Runner verweigert TEST, wenn der Prüfwert abweicht.

## Messgrößen (je Zug, festgelegt)

- **RICHTIG:** Gold Erinnerung und die erste Zitierung gehört dazu; Gold RÜCKFRAGE und es wird nachgefragt;
  Gold NICHTS und nichts wird zitiert.
- **FALSCH (falsche Behauptung):** erste Zitierung außerhalb des Golds; **jede** Zitierung, wo RÜCKFRAGE oder NICHTS richtig ist.
- **VERPASST:** Gold Erinnerung, nichts zitiert. **UNNÖTIGE RÜCKFRAGE:** Gold Erinnerung, nachgefragt.
- Nebenwerte: zusätzliche Zitierungen außerhalb des Golds, Laufzeit je Zug und der Routine allein (Median, p95; JVM,
  kein Gerät), Größe der Dialogdatei des Kerns, Einträge im Routinezustand.

## Entscheidungsregel (TEST, einmal)

B1 wird nur dann für eine optionale, abschaltbare Anbindung vorgeschlagen (Besitzerprüfung, nie automatisch), wenn
**alle** gelten: (1) FALSCH(B1) ≤ ½ · FALSCH(B0); (2) richtige Erinnerungsantworten B1 ≥ B0; (3) unnötige Rückfragen
B1 ≤ 5 % der Erinnerungs-Züge; (4) Mehrzeit der Routine Median ≤ 5 ms und p95 ≤ 20 ms. Sonst bleibt B1 Forschung.
Ergebnisse je Familie werden vollständig berichtet, auch wenn die Regel erfüllt ist.

## Entwicklungsstand (nur TRAIN/DEV, vor dem Einfrieren)

| | TRAIN B0 | TRAIN B1 | DEV B0 | DEV B1 |
|---|---|---|---|---|
| richtig (Erinnerung) | 250 | 345 | 163 | 230 |
| falsch | 150 | 0 | 103 | 0 |
| verpasst | 5 | 0 | 4 | 0 |
| Rückfrage richtig / Enthaltung richtig | 0 / 0 | 30 / 30 | 0 / 0 | 20 / 20 |

Verlauf der B1-Entwicklung: erste Fassung TRAIN 11 falsch (Ursache fast nur die Suche des Kerns: lange Berufswörter
verdünnen den Kosinus, Rechtschreibhilfe greift nicht, sobald ein Name trifft) → Eingrenzung auf die Fakten der Entität
und Rückkopplungsschutz → TRAIN 0 falsch, DEV 1 falsch („akt“) → Rechtschreibreparatur gegen die Fakten → DEV 0 falsch.
Unit-Tests `Ctx1DialogueContextTest` 9/9; sechs gezielte Regel-Mutationen (Gleichstand-Rückfrage, Rückkopplungsschutz,
Unbekannt-Erkennung, Artikel/Präposition-Ausschluss, Eindeutigkeit der Reparatur, Eingrenzung) werden je erkannt.

## Bekannte Grenzen (vorab)

- Synthetische Daten vom selben Autor wie B1. Die TEST-Formulierungen standen beim Entwickeln im Generator-Quelltext;
  TEST wurde nie erzeugt, ausgegeben oder ausgewertet (nur sein Prüfwert berechnet) – die Kenntnis bleibt eine mögliche Verzerrung.
- Feste Fakt-Formulierungen: Erkennt das Register eine andere Formulierung nicht, fällt B1 auf den ganzen Pool zurück.
- Kein Plural-„sie“, keine Höflichkeitsform, kein expletives „es“ („Wie geht es dir?“), keine Mehrwort-Namen.
- Gemessen wird Erdung (welche Erinnerung zuerst zitiert wird), nicht die Formulierung der Antwort.
