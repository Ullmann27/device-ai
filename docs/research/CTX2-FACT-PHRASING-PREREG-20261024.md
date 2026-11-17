# CTX2: Hält die Kontextroutine, wenn der Besitzer anders formuliert? – Vorregistrierung (24.10.2026)

Festgelegt **vor** dem einzigen TEST-Lauf. Reines Forschungsmodul im Test-Quellset, nichts in der APK.
Anlass: CTX1 (`CTX1-DIALOGUE-CONTEXT-RESULT-20261024.md`) maß nur feste Fakt-Formulierungen. Vor jeder Anbindung muss
geklärt sein, was passiert, wenn echte Erinnerungen anders formuliert sind („Mein Bruder heißt Max.“).

## Vermutung (vor der Messung notiert)

Die eingefrorene CTX1-Routine erkennt dann gespeicherte Personen nicht und meldet sie als **unbekannt** – sie würde
richtige Antworten des heutigen Kerns verhindern. Auf TRAIN2/DEV2 bestätigt (siehe unten); TEST2 prüft das auf
ungesehenen Formulierungen und misst, ob eine verallgemeinerte Fassung das Problem löst.

## Systeme

- **B0:** heutiger Kern, unverändert.
- **B1:** CTX1 genau wie gemessen (`Ctx1DialogueContext(facts)`; per Prüfung: TRAIN/DEV-Ergebnisse von CTX1 nach dem
  Umbau je Familie identisch).
- **B2:** `Ctx1DialogueContext(facts, generalized = true)`, nur an TRAIN2/DEV2 entwickelt, jetzt eingefroren:
  1. zusätzliche Registermuster: „Mein/Meine X heißt N.“, „Ich habe einen/eine X, er/sie heißt N.“,
     „… einen/eine/ein X namens N …“, „N ist unser/unsere X.“, „Der/Die/Das X ist/hat …“ (Geschlecht aus Artikel/Possessiv);
  2. jedes noch nicht erfasste großgeschriebene Subjekt am Faktanfang vor Komma oder Verb (ist, wohnt, lebt, arbeitet,
     spielt, frisst, heißt, hat) wird Entität mit **unbekanntem** Geschlecht (passt zu „er“ und „sie“);
  3. ein Name heißt nur dann **unbekannt**, wenn keine gespeicherte Erinnerung ihn enthält; sonst wird gefragt.

## Testdialoge

`Ctx2Corpus`: dieselben zwölf Familien und Nutzerzüge wie CTX1; jede Erinnerung, die eine Person, ein Tier oder einen
Gegenstand einführt, wird umformuliert, teils mit einem Merkmal zusammengelegt. Gold folgt den neuen Fakten.

| Split | Seed | Wortschatz der Welt | Formulierungen der einführenden Fakten | Dialoge (Züge) | SHA-256 |
|---|---|---|---|---|---|
| TRAIN2 | 20261105 | bekannt | bekannt: „Mein Bruder heißt Max.“, „Max ist mein Bruder und wohnt in Graz.“, „Ich habe einen Bruder, er heißt Max.“, „Wir haben einen Hund namens Bello.“, „Bello ist unser Hund.“, „Das Auto ist …“ | 180 (405) | `64453743e3eabb10897a2fec0ef0a4076f66e8daa60d9bf2cf9f5f62ff43a784` |
| DEV2 | 20261106 | bekannt | bekannt | 120 (270) | `4ccd6f23a7eb05c3b03fd01f26ab2b926ae3f8be9157aa0269016b8006913531` |
| **TEST2** | 20261104 | ungesehen (wie CTX1-TEST, neuer Seed) | **ungesehen**: Apposition „N, meine Nichte, wohnt in …“, „Meine Nichte N ist 34 Jahre alt.“, „Das ist N, mein Schwager.“, „Bei uns lebt ein Wellensittich namens N.“, „N ist das Haustier der Familie, eine Ziege.“, „Unser/Unsere X ist/hat …“ | 480 (1080) | **`2a7242bf6a93a2366fb36275f431cfeeba8ff7f651c3033e8617ca73c4c9222a`** |

Der Runner (`Ctx2Experiment`) verweigert TEST2 bei abweichendem Prüfwert.

## Messgrößen und Entscheidungsregel

Wie CTX1 (richtig, falsch, verpasst, unnötige Rückfrage, Rückfrage, Enthaltung, Laufzeit). B2 ersetzt B1 als Kandidat
für eine abschaltbare Anbindung (Besitzerprüfung, standardmäßig aus) nur, wenn auf TEST2 **alle** gelten:
(1) falsch(B2) ≤ ½ · falsch(B0); (2) richtige Erinnerungsantworten B2 ≥ B0 **und** ≥ B1; (3) verpasst(B2) ≤ verpasst(B0) + 2 % der
Erinnerungszüge; (4) unnötige Rückfragen B2 ≤ 5 % der Erinnerungszüge; (5) Routine Median ≤ 5 ms, p95 ≤ 20 ms.
Erfüllt B2 das nicht, wird **keine** Kontextroutine angebunden.

## Entwicklungsstand (nur TRAIN2/DEV2)

| | B0 richtig/falsch/verpasst | B1 richtig/falsch/verpasst/unnötig | B2 richtig/falsch/verpasst |
|---|---|---|---|
| TRAIN2 | 247 / 152 / 6 | 117 / 8 / 204 / 16 | 345 / 0 / 0 |
| DEV2 | 164 / 100 / 6 | 86 / 5 / 131 / 8 | 230 / 0 / 0 |

Unit-Tests `Ctx2FactPhrasingTest` 5/5 (zusammen mit CTX1 14/14); fünf Mutationen (Unbekannt-Regel, Subjekt-Fallback,
„heißt“-Muster, „namens“-Muster, Standard „aus“ für CTX1) werden je erkannt.

## Grenzen (vorab)

Weiterhin synthetisch und vom selben Autor; die TEST2-Formulierungen standen beim Entwickeln im Generator-Quelltext
(nie ausgeführt oder ausgegeben). Unbekanntes Geschlecht kann Rückbezüge mehrdeutig oder falsch machen – das misst TEST2.
