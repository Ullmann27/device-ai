# Human Dialogue Benchmark – Experiment E3 (19.11.2026)

**Zweck.** Der Besitzer nennt es „Gesetz Nummer eins – Persönlichkeit“: Das Gespräch soll vertraut, aufmerksam, ruhig,
intelligent und angenehm menschlich wirken – nie förmlich, nie zweimal dieselbe Begrüßung und **nie mit erfundenen
Erinnerungen oder behaupteten Gefühlen**. Dieser Benchmark misst das an ganzen Gesprächslagen statt an einzelnen
Sätzen.

**Was gemessen wurde und was nicht.** Alles hier lief auf dem Entwicklungsrechner (JVM-Unit-Tests) gegen den echten,
deterministischen Programmcode mit gestellter Uhr und gestellten Speichern. **Kein Modell, kein Gerät, keine Sprache:
Auf dem Fold7 ist nichts davon gelaufen (NOT_RUN).** Der Benchmark sagt, ob die App in einer Lage den richtigen Weg
nimmt und nichts Unbelegtes sagt – er sagt nicht, wie es sich anhört. Dafür gibt es
`docs/handover/GESPRAECH-GERAETECHECKLISTE-20261119.md`.

## Datei, Schema, Läufer

`docs/evaluation/continuum-eval-v1/dialogue-benchmark.jsonl` – 262 Zeilen, je eine Lage:

```json
{"id": "H-UNF-01", "category": "unfinished", "set": "heldout",
 "setup": {"nowIso": "2026-11-19T08:10:00", "lastSeenIso": "2026-11-18T21:30:00", "tone": "PERSONAL", "level": "NORMAL",
           "sharedWork": [{"kind": "DOCUMENT", "action": "OPENED", "title": "Argon4.pdf", "state": "PAUSED",
                           "endedIso": "2026-11-18T21:28:00", "resume": "Öffne das PDF Argon4"}],
           "openMessages": 0},
 "event": "return",
 "expect": {"route": "GREETING", "offer": "RESUME_WORK", "must": ["Argon4", "gestern Abend"], "maxQuestions": 1}}
```

| Feld | Bedeutung |
|---|---|
| `setup.nowIso`, `lastSeenIso`, `lastTalkIso` | Uhr des Geräts (Europe/Vienna), letztes Lebenszeichen, letzter eigener Satz (`null` = nicht gespeichert; fehlt `lastTalkIso`, gilt `lastSeenIso`) |
| `setup.tone`, `level`, `name`, `greeting` | Gesprächston, Initiativstufe, Anrede (`null` = keine), Begrüßungsschalter |
| `setup.sharedWork[]` | Einträge des Arbeitsstands: Art, Tätigkeit, Titel, Stand, Zeit, Fortsetzungs-Auftrag, Unterbrechungsstelle |
| `setup.openMessages`, `briefingOffer`, `appointment`, `busy`, `openQuestion`, `declined[]`, `greetingsBefore` | offene Nachrichten (fehlt = keine Liste angeschlossen), Tagesangebot der Briefing-Lenkung, Termin, läuft gerade etwas, wartet eine Lenkung auf eine Antwort, schon abgelehnte Vorschlagsarten, Zählerstand der Begrüßungen |
| `setup.fakeSuggestion` | ein Vorschlag, für den die App heute **keinen** Anbieter hat (Karten, Kalendereintrag) – nur um die Regel zu prüfen |
| `utterance` / `event` / `steps[]` | ein Satz, ein Ereignis (`return`, `leave`, `result`, `order`, `ack`) oder eine Folge mit `afterMinutes` |
| `expect.route` | was die App damit tut (siehe unten) |
| `expect.offer`, `order`, `level` | welches Angebot die Äußerung trägt, welcher Auftrag an eine Lenkung geht (`null` = keiner), welche Stufe danach gilt |
| `expect.must[]`, `mustNot[]` | Muster (ohne Groß-/Kleinschreibung), die im Gesagten vorkommen müssen bzw. nicht dürfen |
| `expect.maxQuestions` | höchstens so viele Fragen (ein Satz mit „?“ und mindestens drei Wörtern; „Na, Heinz?“ ist eine Anrede) |
| `expect.said`, `noImmediateRepeat`, `maxSameShape`, `minDistinct` | Folgen: Anzahl der Äußerungen, nie zweimal hintereinander derselbe Satz, derselbe Satzbau höchstens so oft hintereinander, Mindestzahl verschiedener Sätze |

Wege (`route`): `GREETING` · `NO_GREETING` · `CLAUSE` / `NO_CLAUSE` (kurzer Gruß vor der Antwort auf einen Auftrag) ·
`RECALL` · `RECALL_EMPTY` · `RESUME` · `RESUME_UNAVAILABLE` · `FORGET_WORK` · `SMALLTALK` · `LEVEL_SET` · `TONE_SET` ·
`NAME_SET` · `OFFER_ACCEPTED` · `OFFER_DECLINED` · `SUGGESTION` / `NO_SUGGESTION` · `ACK` · `LANE_QUESTION` (eine
Lenkung hatte gefragt und bekommt die Antwort) · `JOURNAL_RECALL` (die bisherige Rückschau des Arbeitsverlaufs
antwortet) · `NOT_TAKEN` (kein fester Weg des Gesprächs: der Satz geht an die übrigen Lenkungen oder das Modell).

**Läufer:** `app/src/test/java/at/heinz/deviceai/DialogueBenchmarkTest.kt`. Er baut je Lage den echten `OwnerTalk`
mit echtem Arbeitsstand (`OwnerSharedWorkRecord`), echter Initiativregel (`OwnerInitiative`), echten Parsern und
Wortlauten auf und führt die Schritte in der Reihenfolge der Activity aus: offene Frage einer Lenkung → Gesprächsart
→ Rückschau des Arbeitsverlaufs. Zusätzlich zu den Erwartungen der Zeile wird **jeder** gesagte Satz jeder Zeile
auf ein behauptetes Gefühl geprüft (`OwnerSmallTalk.claimsFeeling`). Der Läufer schreibt die Tabellen nach
`app/build/reports/dialogue-benchmark/`.

Ausführen (Host):

```
./gradlew :app:testDebugUnitTest --tests "*DialogueBenchmarkTest*"
```

## In welcher Reihenfolge gearbeitet wurde (ehrlich)

1. **Held-out zuerst.** Die 78 Zeilen mit `"set": "heldout"` wurden aus den Wünschen des Besitzers geschrieben und
   eingecheckt (Commit `a3dd77f2a`), **bevor eine Zeile der Umsetzung existierte**. Festgelegt waren zu dem Zeitpunkt
   nur das Schema, die Namen der Wege und die Regeln für Zeitangaben („heute früh“, „gestern Abend“, „am Montag“).
2. Danach die Umsetzung.
3. **Dann die 184 Entwicklungszeilen** (`"set": "dev"`) – geschrieben, als der Code stand, aber **bevor irgendeine Zeile
   ausgeführt worden war** (der erste Build wartete noch in der Warteschlange). Beim Schreiben fielen drei Lücken der
   Parser auf, die vor dem ersten Lauf geschlossen wurden (Füllwörter nach „wir“ – „wo waren wir denn …“, „mach **mal**
   da weiter“, ein abschließendes „bitte“ beim Stufenbefehl). Das ist Arbeit am Entwicklungsteil, nicht am Held-out.
4. **Erster Lauf** (nichts nachgezogen): Entwicklung 182/184, Held-out 76/78.
5. Die zwei Entwicklungs-Fehlschläge und einer der beiden Held-out-Fehlschläge hatten dieselbe Ursache **im Läufer**,
   nicht im Programm: Folgen, die mit `leave` enden (der Besitzer verlässt die App), wurden an diesem letzten Schritt
   gemessen, auf den die App gar nicht reagieren kann. Der Läufer misst seither die letzte **Reaktion**. Das ist eine
   Korrektur am Messgerät, die eine Held-out-Zeile (H-REP-06) erst messbar macht – deshalb steht sie hier ausdrücklich.
   Die Entwicklungszeile D-REP-02 war zusätzlich falsch aufgebaut (die Folge endete mit einer Frage des Besitzers statt
   mit der Rückkehr, die gemessen werden soll) und wurde korrigiert.
6. Der zweite Held-out-Fehlschlag (H-COL-09) bleibt stehen, siehe unten. Am Programm wurde wegen keiner Held-out-Zeile
   etwas geändert.
7. Danach wurde am Programm noch einmal gearbeitet – nicht wegen einer Benchmark-Zeile, sondern weil zwei vorhandene
   Oberflächentests es zeigten: Die Antwort auf „Wo waren wir?“ wird erst gebildet, wenn alle noch laufenden
   Schreibvorgänge des Arbeitsverlaufs im Arbeitsstand angekommen sind (ein STOP von eben muss in der Antwort stehen),
   und im Cockpit bleibt die VERLAUF-Fläche neben dem Satz erhalten. Beide Teile wurden danach erneut gemessen: die
   Zahlen unten.

## Ergebnis (Host, 19.11.2026)

### Entwicklungsteil – 184 von 184

| Kategorie | bestanden | gesamt |
|---|---:|---:|
| Begrüßung nach Lage (`greeting`) | 28 | 28 |
| Rückkehr mit offener Arbeit (`unfinished`) | 16 | 16 |
| „Wo waren wir?“ (`recall`) | 20 | 20 |
| Smalltalk ohne behauptete Gefühle (`smalltalk`) | 17 | 17 |
| Korrekturen und Unterbrechungen (`correction`) | 22 | 22 |
| Unvollständige und umgangssprachliche Sätze (`colloquial`) | 28 | 28 |
| Initiative je Stufe (`proactivity`) | 23 | 23 |
| Nichts erfinden (`never_invent`) | 15 | 15 |
| Wiederholungen über Folgen (`repetition`) | 15 | 15 |
| **Summe** | **184** | **184** |

Erster Lauf: 182/184 (D-REP-02 und D-REP-07, Ursache im Läufer, siehe oben).

### Held-out-Teil – 77 von 78

| Kategorie | bestanden | gesamt |
|---|---:|---:|
| Begrüßung nach Lage (`greeting`) | 10 | 10 |
| Rückkehr mit offener Arbeit (`unfinished`) | 7 | 7 |
| „Wo waren wir?“ (`recall`) | 9 | 9 |
| Smalltalk ohne behauptete Gefühle (`smalltalk`) | 7 | 7 |
| Korrekturen und Unterbrechungen (`correction`) | 9 | 9 |
| Unvollständige und umgangssprachliche Sätze (`colloquial`) | 9 | 10 |
| Initiative je Stufe (`proactivity`) | 11 | 11 |
| Nichts erfinden (`never_invent`) | 8 | 8 |
| Wiederholungen über Folgen (`repetition`) | 7 | 7 |
| **Summe** | **77** | **78** |

Erster Lauf: 76/78 (zusätzlich H-REP-06, Ursache im Läufer, siehe oben).

### Was nicht besteht und warum

| Zeile | Lage | Erwartet | Tatsächlich | Einordnung |
|---|---|---|---|---|
| H-COL-09 | „wo waren wir, öffne dann das pdf“ | `NOT_TAKEN` | `JOURNAL_RECALL` | **Die Zeile ist ungenau geschrieben, das Verhalten ist gewollt.** Die Gesprächsart nimmt den zusammengesetzten Satz wie verlangt nicht an; die schon vorhandene Rückschau des Arbeitsverlaufs erkennt darin aber „wo waren wir“ plus einen Dokumentauftrag und antwortet – so wie vor dieser Arbeit. Beim Schreiben der Zeile war diese zweite Lenkung nicht bedacht. Die Zeile wird nicht umgeschrieben und der Code nicht verbogen; sie ist in `KNOWN_HELDOUT_MISSES` festgehalten. |

Wer eine Held-out-Zeile behebt oder eine neue bricht, lässt den Test fehlschlagen – die Zahl oben kann also nicht
still veralten.

## Was der Benchmark nicht abdeckt

- **Kein Modell.** Alles Gemessene ist fester Programmtext. Wie das Cloud-Gehirn mit dem neuen Block „Gesprächslage“
  klingt, ob es sich an „höchstens eine Frage“ und „kurz antworten“ hält, ist nicht gemessen (kein Modelllauf).
- **Keine Stimme, kein Gerät.** Ob die Begrüßung beim Zurückkommen auf dem Fold7 genau einmal gesprochen wird, ob
  Drehen und Falten wirklich keine auslösen, ob der Kalender gelesen wird: NOT_RUN.
- **Die Regeln sind eng.** Ein Satz, den die Muster nicht kennen, geht wie bisher an die übrigen Lenkungen oder das
  Modell (`NOT_TAKEN`). Das ist Absicht (lieber nicht annehmen als falsch verstehen), heißt aber: Die Trefferquote auf
  frei gesprochene Sätze des Besitzers ist mit 262 selbst geschriebenen Lagen nicht belegt.
- **Selbst geschriebene Lagen.** Entwicklungs- und Held-out-Zeilen stammen vom selben Autor; der Held-out-Teil schützt
  vor Nachziehen, nicht vor einem gemeinsamen blinden Fleck.
- **Smalltalk-Prüfung auf Gefühle** ist eine Wortliste (`claimsFeeling`). Sie fängt die bekannten Formen
  („Ich freue mich“, „Mir geht es gut“, „Ich bin auch müde“), nicht jede denkbare.

## Zusätzliche Eigenschaftstests (nicht Teil der 262 Zeilen)

Zwei Tests würfeln Zustände und prüfen, dass kein Wort über Zeit oder Arbeit ohne gespeicherte Tatsache gesagt wird:

- `OwnerSharedWorkTest.everyTimeAndEveryTitleInAnAnswerComesFromAnEntry` – 600 zufällige Arbeitsstände (auch mit
  Zeitstempeln in der Zukunft und Lücken über 45 Tagen): jede Zeitangabe und jeder Titel in einer Antwort muss aus
  einem Eintrag kommen; ohne Eintrag kein „haben wir …“.
- `OwnerTalkSituationTest.everyWordAboutTimeWorkOrMessagesInAGreetingHasAStoredFactBehindIt` – 1 500 zufällige
  Begrüßungslagen: „gestern“, ein Wochentag, eine Dauer, „zuletzt gesprochen“, „aufgehört“, ein Dateiname,
  „Nachricht“, „Briefing“ dürfen nur erscheinen, wenn der passende Zeitstempel, Eintrag, Zähler oder Anbieter da ist;
  höchstens eine Frage; in „Zurückhaltend“ kein Angebot.
