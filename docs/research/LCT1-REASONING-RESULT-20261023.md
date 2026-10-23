# Experiment B – LCT1: nachvollziehbares mehrstufiges Schlussfolgern – Ergebnis (23.10.2026)

Vorregistrierung: `LCT1-REASONING-PREREG-20261023.md` (Commit `2f4bf587`, vor jedem Lauf). Code eingefroren in
`50e5bcde` (`app/src/test/java/at/heinz/deviceai/intelligence/maxultra/Lct1*.kt`), TEST **einmal** ausgewertet.
Bericht: `lct1-reasoning-report-20261023.json`, Konsolenausgabe: `lct1-reasoning-test-stdout-20261023.txt`.
JVM 21.0.12, x86_64 (Intel Xeon 2,8 GHz, 4 Kerne) – **keine Android- oder Fold7-Aussage**. Reines Forschungsmodul
im Test-Quellset: nicht in der APK, nicht mit Chat, CCC1, Gedächtnis oder Gewichten verbunden.

Daten: DEV 144 Fälle / 2 760 Fakten (SHA-256 `8ad64168…5d8b08`), TEST 600 Fälle / 7 890 Fakten
(`dd6fda28…3913be`), Namen DEV/TEST disjunkt, Stichtag 23.10.2026.

## Ergebnis TEST (richtig / falsch / verpasst je 25 Fälle)

| Klasse | Variante | L | L+V | B0 |
|---|---|---|---|---|
| Zwei-/Drei-Hop | `HOP2_CITY`, `HOP2_MAYOR`, `HOP3_REGION`, `HOP3_VOLTAGE`, `HOP3_CEO` | je 25/0/0 | je 25/0/0 | je 25/0/0 |
| Widerspruch | `CONTRA_STEP` | 25/0/0 | 25/0/0 | 25/0/0 |
| Mehrdeutigkeit | `AMBIG_MENTION` | 25/0/0 | 25/0/0 | 25/0/0 |
| | `AMBIG_INTERMEDIATE_GAP` | 25/0/0 | 25/0/0 | **0/25/0** |
| | `AMBIG_INTERMEDIATE_OK` | 25/0/0 | 25/0/0 | 0/0/25 |
| falsche Relation | `WRONG_REL_CITY`, `WRONG_REL_CAPITAL` | je 25/0/0 | je 25/0/0 | je 25/0/0 |
| Zeit | `OUTDATED_WITH_CURRENT` | 25/0/0 | 25/0/0 | 0/0/25 |
| | `OUTDATED_ONLY`, `FUTURE_ONLY` | je 25/0/0 | je 25/0/0 | **je 0/25/0** |
| Quellen | `SRC_REVOKED_ONLY`, `SRC_UNKNOWN` | je 25/0/0 | je 25/0/0 | **je 0/25/0** |
| | `SRC_TAMPERED` | 25/0/0 | 25/0/0 | 25/0/0 |
| | `SRC_REVOKED_CONFLICT` | 25/0/0 | 25/0/0 | 0/0/25 |
| fehlendes Glied | `MISSING_LINK` | 25/0/0 | 25/0/0 | 25/0/0 |
| Zahlen/Negation | `NUM_CHECK_TRUE`, `NUM_CHECK_CHANGED`, `NEG_QUESTION`, `NEG_FACT_ONLY` | je 25/0/0 | je 25/0/0 | je 25/0/0 |
| | `NEG_FACT_CONFLICT` | 25/0/0 | 25/0/0 | **0/25/0** |
| **Summe** | falsche Antworten · Abdeckung | **0 · 1,000** | **0 · 1,000** | **150 · 0,727** |

**B0** (der Algorithmus der vorhandenen `EvidenceBoundedThoughtSearch`, verallgemeinert) stimmt in **118/118**
`CITY_OF_DEVICE`-Fällen exakt mit der echten Klasse überein. Seine 150 falschen Antworten folgen aus dem Datenmodell
(Namen als Knoten; keine Zeit, kein Quellstatus, keine Verneinung): Ein gleichnamiger Zwischenort liefert die Region
einer anderen Stadt, abgelaufene/künftige Amtsinhaber, widerrufene oder unbekannte Quellen und verneinte Fakten werden
als gültig genommen. Dazu 75 verpasste beantwortbare Fälle (geteilter Name, alter neben aktuellem Fakt, widerrufener
Gegenfakt).

**Unabhängiger Prüfer V:** 275 richtige L-Antworten, **0 fälschlich abgelehnt**; Mutationen abgelehnt: anderer Wert
bzw. vertauschtes Ja/Nein 275/275, fremder Fakt 275/275, Schritt entfernt 275/275, Reihenfolge vertauscht 275/275,
anderer Stichtag 75/75 – **1 175/1 175**. Zusätzlich (nicht vorregistriert): V verwirft **alle** falschen Antworten der
latenten Varianten (Z-rel 12/12, Z-beam 104/104 über alle Seeds).

## Latenter Teil (5 Seeds)

| Seed | Übergangsgenauigkeit (DEV-Gold) | Z-rel falsch · Abdeckung | Z-beam falsch · Abdeckung |
|---|---|---|---|
| 1 | 0,84 | 5 · 0,680 | 21 · 1,000 |
| 2 | 0,80 | 4 · 0,502 | 22 · 1,000 |
| 3 | 0,88 | 0 · 0,775 | 19 · 1,000 |
| 4 | 0,68 | 0 · 0,545 | 21 · 1,000 |
| 5 | 0,84 | 3 · 0,636 | 21 · 1,000 |

- **Z-beam** (Kompositionsregeln wie L, aber nur der latent beste Kandidat je Schritt) sieht Widersprüche nicht mehr:
  19–22 von 25 `CONTRA_STEP`-Fällen werden falsch beantwortet. Ein Kandidat weniger heißt: der Gegenbeleg wird nie
  angesehen.
- **Z-rel** (Relationsfolge aus dem 16×16-Fast-Weight statt aus der Tabelle) lernt die 25 Übergänge der sieben
  trainierten Fragetypen nur zu 68–88 % (17–22 von 25); ganze Fragetypen fallen je nach Seed aus (z. B. `HOP2_CITY`,
  `NEG_QUESTION`). Nachträgliche Durchsicht der falschen
  Antworten (eingefrorener Lauf, keine Auswirkung auf die Entscheidung): Fehlt ein Glied, nimmt Z-rel einen fachlich
  falschen Umweg mit passendem Zieltyp – „Bürgermeister am Gebäude“ → `OPERATED_BY`→`CEO` (Geschäftsführer der
  Betreiberfirma), „Hauptstadt“ → eigene Stadt oder `OPERATED_BY`→`HQ_CITY` (Firmensitz).
- Laufzeit: Z-rel ist 3× langsamer als L, Z-beam gleich schnell (Tabelle unten).

## Laufzeit je Frage (µs, Median / p95; JVM, kein Gerät)

| Ablenker | Fakten | L | L+V | B0 | Z-rel | Z-beam |
|---|---|---|---|---|---|---|
| 1× | 7 890 | 39 / 67 | 43 / 1 136 | 3,3 / 6,0 | 133 / 263 | 40 / 55 |
| 10× | 18 150 | 57 / 106 | 43 / 2 009 | 2,0 / 2,8 | 133 / 278 | 40 / 55 |
| 50× | 63 750 | 39 / 54 | 44 / 19 329 | 1,5 / 2,2 | 115 / 154 | 40 / 54 |

L ist unabhängig von der Größe der Wissensbasis (Index nach Subjekt). Seine 40 µs stammen fast ganz aus der
SHA-256-Siegelprüfung **bei jeder Frage**; B0 prüft Siegel einmal beim Import. Das p95 von L+V wächst mit der Basis,
weil V absichtlich ohne Index über die Rohdaten läuft (Unabhängigkeit vor Tempo). Die Heap-Zahl im Bericht
(`approxHeapBytesForSolvers`) ist **nicht aussagekräftig** (enthält Müll aus den Zeitmessungen).

## Entscheidungen nach Vorregistrierung

1. **LCT1 taugt als Forschungskern – erfüllt:** L+V 0 falsche Antworten in allen 24 Varianten, Abdeckung 1,000
   (≥ 0,95), V lehnt 1 175/1 175 Mutationen und 0 richtige Antworten ab.
2. **Kein latenter Vorteil:** Z-rel hat in Seeds 1, 2, 5 mehr falsche Antworten als L und in allen Seeds weniger
   Abdeckung; Z-beam hat in allen Seeds 19–22 falsche Antworten; keine Variante ist schneller als 0,5 × L. Die
   deterministische Graphsuche bleibt der Hauptmechanismus, der latente Teil ein isoliertes Forschungsmodul.
3. **B0 gegen L:** siehe Tabelle – der Zugewinn von L kommt aus IDs statt Namen, Zeit, Quellstatus, Verneinung und der
   Eindeutigkeitsprüfung je Schritt, nicht aus zusätzlicher Rechenleistung.

## Grenzen

- Die Daten sind synthetisch und vom selben Autor wie L entworfen: Gemessen ist, dass L die acht Problemklassen des
  Auftrags so behandelt wie spezifiziert und die alte Suche nicht – **nicht** allgemeines Weltwissen oder Verstehen.
- Fragen sind typisiert (kein Sprachverstehen), Relationen funktional, Tiefe ≤ 3.
- Datenlücke: `CAPITAL_FOR_BUILDING` kommt nur in Enthaltungsfällen vor (`WRONG_REL_CAPITAL`, `MISSING_LINK`); für die
  Regelsuche L ohne Belang, für Z-rel war dieser Fragetyp untrainiert (4 der 12 falschen Z-rel-Antworten betreffen ihn).
- Ein gültiges Siegel beweist Datenidentität, keine Wahrheit; „vertrauenswürdige Quelle“ ist ein Registereintrag.
- V ist eigener Code, aber vom selben Autor: Er schützt gegen Fehler in L und gegen fremde Ableitungen, nicht gegen
  einen gemeinsamen Spezifikationsfehler.

## Empfehlung / nächste Schritte

- L+V ersetzt im **Forschungspfad** die `EvidenceBoundedThoughtSearch` (gleiche Abstinenzhaltung, aber IDs, Zeit,
  Quellen, Verneinung, Prüfer). Vor jeder Anbindung an echte Besitzerfakten: authentisiertes Besitzersiegel statt
  `ownerReviewed`-Flag, Siegelprüfung beim Laden zwischenspeichern.
- Experiment C (WSC1) baut darauf auf: nur von V bestätigte Drei-Hop-Ableitungen werden Konsolidierungskandidaten;
  Vergleich gegen einen geprüften deterministischen Cache.
- Fast-Weights nicht für Relationswahl oder Kandidatenschnitt verwenden.

**Nachvollziehen:** Haupt- und Testquellen wie in `scripts/android-kotlin-unit-check.sh` kompilieren (oder nur
`ContinuumCortexShadow.kt` + `Lct1*.kt`), dann
`java … at.heinz.deviceai.intelligence.maxultra.Lct1Experiment test <bericht.json> <commit>`; DEV-Entwicklung mit `dev`.
