# Experiment B – LCT1: nachvollziehbares mehrstufiges Schlussfolgern – Vorregistrierung (23.10.2026)

Festgelegt **vor** dem ersten Messlauf. Reines Forschungsmodul im Test-Quellset (`app/src/test/...`): nichts davon
liegt in der APK, ist mit dem Chat verbunden oder verändert Gewichte, Gedächtnis oder CCC1.

## Fragen

1. Beantwortet ein typisierter, quellgebundener, begrenzter Graph-Schlussfolgerer (**LCT1**) Zwei- und Drei-Hop-Fragen
   richtig und enthält er sich in den acht Problemklassen des Auftrags, ohne falsche Antworten zu geben – und wie
   schneidet dagegen der Algorithmus der vorhandenen `EvidenceBoundedThoughtSearch` (**B0**) ab?
2. Erkennt ein **unabhängiger Prüfer** (eigener Code, liest die Rohdaten selbst) falsche Ableitungen, ohne richtige
   abzulehnen?
3. Bringt ein **latenter Zustand** (16-D-Fast-Weights mit der Lernregel von `EphemeralFastWeightCore`) bei der Suche
   einen reproduzierbaren Vorteil gegenüber der deterministischen Graphsuche?

## Wissensmodell

- Entitäten mit ID, Typ (`DEVICE`, `BUILDING`, `CITY`, `REGION`, `COMPANY`, `PERSON`) und Namen; eine Erwähnung in der
  Frage wird über Namen aufgelöst (mehr als eine Entität = mehrdeutig).
- Fakten: ID, Quelle, Subjekt-ID, Relation, Objekt (Entitäts-ID oder Zahl mit Einheit), Polarität (`ASSERTED`/`NEGATED`),
  Gültigkeit `validFrom`/`validTo` (optional), SHA-256-Siegel über die exakten Faktfelder.
- Quellenregister: `TRUSTED` oder `REVOKED`; nicht registrierte Quellen sind ungültig. Ein gültiges Siegel beweist nur
  Datenidentität, keine Wahrheit.
- Relationen (alle funktional): `IN_BUILDING` (Gerät→Gebäude), `IN_CITY` (Gebäude→Stadt), `IN_REGION` (Stadt→Region),
  `GRID_VOLTAGE` (Region→V), `MAYOR` (Stadt→Person, zeitabhängig), `OPERATED_BY` (Gebäude→Firma), `HQ_CITY`
  (Firma→Stadt), `CEO` (Firma→Person, zeitabhängig), `CAPITAL` (Region→Stadt), `LARGEST_CITY` (Region→Stadt).
- Fragetypen mit fester Kompositionsregel: `CITY_OF_DEVICE` [IN_BUILDING, IN_CITY], `MAYOR_AT_BUILDING` [IN_CITY, MAYOR],
  `REGION_OF_DEVICE` [IN_BUILDING, IN_CITY, IN_REGION], `VOLTAGE_AT_BUILDING` [IN_CITY, IN_REGION, GRID_VOLTAGE],
  `CEO_FOR_DEVICE` [IN_BUILDING, OPERATED_BY, CEO], `CAPITAL_FOR_BUILDING` [IN_CITY, IN_REGION, CAPITAL];
  Prüffragen `CHECK_VOLTAGE_AT_BUILDING` (Zahl) und `CHECK_CITY_OF_DEVICE` (Stadt, optional verneint). Jede Frage trägt
  ein Stichtagsdatum.

## Daten (deterministisch)

Generator mit festem Seed: DEV `20261023`, TEST `20261024`; Namen aus Silben, DEV- und TEST-Namen disjunkt, jeder Fall
mit eigenen Entitäten (Ausnahme: absichtlich geteilte Namen in den Mehrdeutigkeitsfällen). Alle Fälle eines Teils und
zusätzlich 200 vollständige Ablenkerketten bilden **eine** Wissensbasis. Je Variante DEV 6, TEST 25 Fälle:

| Klasse | Varianten (Gold) |
|---|---|
| Zwei-/Drei-Hop | `HOP2_CITY`, `HOP2_MAYOR`, `HOP3_REGION`, `HOP3_VOLTAGE`, `HOP3_CEO` (Antwort + Ableitung) |
| widersprüchliche Quellen | `CONTRA_STEP`: zwei gültige, vertrauenswürdige, verschiedene Fakten auf einem Schritt (Enthaltung) |
| mehrdeutige Entitäten | `AMBIG_MENTION` (Enthaltung); `AMBIG_INTERMEDIATE_GAP`: Zwischenstadt teilt den Namen mit einer anderen, der echten fehlt der nächste Fakt (Enthaltung); `AMBIG_INTERMEDIATE_OK`: beide vollständig (Antwort über IDs) |
| falsche Relationstypen | `WRONG_REL_CITY`: kein `IN_CITY`, aber `OPERATED_BY`→`HQ_CITY` (Enthaltung); `WRONG_REL_CAPITAL`: kein `CAPITAL`, aber `LARGEST_CITY` (Enthaltung) |
| zeitlich überholt | `OUTDATED_WITH_CURRENT` (Antwort mit aktuellem Fakt); `OUTDATED_ONLY`, `FUTURE_ONLY` (Enthaltung) |
| ungültige Quellen | `SRC_REVOKED_ONLY`, `SRC_TAMPERED`, `SRC_UNKNOWN` (Enthaltung); `SRC_REVOKED_CONFLICT`: vertrauenswürdiger Fakt + widersprechender aus widerrufener Quelle (Antwort) |
| fehlende Schlussglieder | `MISSING_LINK` (Enthaltung) |
| Zahlen und Negation | `NUM_CHECK_TRUE` (Ja); `NUM_CHECK_CHANGED`: veränderte Zahl (Nein + Wert); `NEG_QUESTION`: verneinte Prüffrage (Ja/Nein); `NEG_FACT_CONFLICT`: Fakt und gleichrangige Verneinung (Enthaltung); `NEG_FACT_ONLY`: nur die Verneinung (Enthaltung) |

24 Varianten: DEV 144, TEST 600 Fälle. DEV dient der Entwicklung; TEST wird **genau einmal** mit dem eingefrorenen Code
ausgewertet (Commit und SHA-256 des Datensatzes im Bericht).

## Systeme

- **B0** – Algorithmus der vorhandenen `EvidenceBoundedThoughtSearch`, auf dieselben Kompositionsregeln verallgemeinert:
  Knoten = Namen, feste Muster, Enthaltung bei mehreren Endwerten. Sein Datenmodell kennt weder Zeit, Quellstatus noch
  Verneinung: Fakten mit ungültigem Siegel und verneinte Fakten werden beim Import ausgelassen, Zeit und Quellstatus
  ignoriert. Treuetest: Auf allen `CITY_OF_DEVICE`-Fällen muss B0 dasselbe liefern wie die echte Klasse.
- **L** – LCT1, deterministisch: Auflösung über IDs, nur gültige (Siegel, Quelle `TRUSTED`, Stichtag) und bejahte
  Fakten; jeder Schritt muss eindeutig sein – verschiedene gültige Objekte oder eine gültige Verneinung desselben Fakts
  = Widerspruch → Enthaltung; Tiefe ≤ 3; Antwort mit Ableitung (Fakt-IDs) und Quellen.
- **L+V** – Antwort von L nur, wenn der unabhängige Prüfer sie bestätigt, sonst Enthaltung.
- **Z-rel** – wie L, aber die Relationsfolge wählt der latente Zustand statt der Kompositionstabelle: Zustand
  `normalize(e(Frage) + e(vorige Relation))`, Vorhersage `W·Zustand`, gewählt wird die verfügbare Relation mit der
  höchsten Kosinus-Ähnlichkeit, Stopp, wenn `STOP` am ähnlichsten ist; am Ende muss der Zieltyp stimmen.
- **Z-beam** – Kompositionsregeln wie L, aber je Schritt wird nur der beste Kandidat (k = 1, latente Bewertung)
  weiterverfolgt.
- Latenter Teil: Einbettungen = zufällige Einheitsvektoren in R¹⁶; `W` (16×16) mit der Lernregel von
  `EphemeralFastWeightCore` (Verlust ½‖W·x − y‖² + 0,001‖W‖², Lernrate 0,08, Fehlerbegrenzung ±2, Frobenius-Norm ≤ 2,5),
  50 Durchläufe über die Übergänge der DEV-Goldableitungen. **5 Seeds** (1–5).

## Messgrößen (TEST)

Je Variante und System: **richtig** (gleicher Wert bzw. Ja/Nein **und** gleiche Ableitung wie Gold; bei Gold-Enthaltung:
Enthaltung), **falsch** (Antwort, wo Enthaltung richtig wäre, oder anderer Wert/andere Ableitung), **verpasst**
(Enthaltung, wo eine Antwort belegt ist). Abdeckung = richtig / beantwortbar. Laufzeit je Frage (Median, p95; JVM
x86_64, keine Android-Aussage) bei 1×, 10× und 50× Ablenkern. Prüfer: Ablehnungen richtiger L-Antworten; Erkennung
von fünf Mutationen je richtiger L-Antwort (anderer Wert gleichen Typs, fremder Fakt in der Ableitung, Schritt
entfernt, Reihenfolge vertauscht, anderer Stichtag außerhalb der Gültigkeit, soweit anwendbar).

## Entscheidungsregeln

1. **LCT1 taugt als Forschungskern**, wenn auf TEST: 0 falsche Antworten für L+V in allen Varianten, Abdeckung ≥ 0,95,
   Prüfer lehnt 100 % der Mutationen ab und 0 richtige Antworten.
2. **Latenter Vorteil** nur, wenn eine Z-Variante in **allen 5 Seeds** nicht mehr falsche Antworten als L hat **und**
   entweder Abdeckung ≥ L + 0,05 oder Median-Laufzeit ≤ 0,5 × L bei 50× Ablenkern. Sonst bleibt die Graphsuche der
   Hauptmechanismus und der latente Teil ein isoliertes Forschungsmodul.
3. B0 gegen L wird je Variante beschrieben (keine Freigaberegel).

Gilt Regel 1 nicht, wird LCT1 nicht weiterempfohlen, sondern der Fehler je Variante dokumentiert.
