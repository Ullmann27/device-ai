# Chief-Bot-Muster – Ende-zu-Ende-Testbericht (Lumo Welten-Optik, 3.11.2026)

Zweig `grok/chief-e2e-tests`, Basis PR #373 (`26162442`, `continuum/m3-grok-selbstrekonstruktion-20261103`).
Muster und Aufgabenteilung: `/workspace/JARVIS-GEMEINSAM.md` (Punkte 1–8). `docs/CHIEF-PATTERN.md` existiert auf #373 noch nicht,
HAUPTs Zweig `continuum/haupt-chief-pattern-20261103` ist zum Testzeitpunkt identisch mit #373 (keine eigenen Commits).

**Testdatei:** `app/src/test/java/at/heinz/deviceai/chief/ChiefPatternE2ETest.kt` (+ `ChiefPatternInternalBridge.kt`).
Getestet wird echter Produktivcode (`BoundedAgentOrchestrator`, `ToolGate`/`ToolRegistry`, `BoundedResearchTextSanitizer`,
`HybridMemoryRetriever`, `SkillGraph`, `ConstrainedResourceFabric`). Gefälscht sind nur Modell und Werkzeug-Backend (deterministisch, ohne Gerät und ohne Netz).
**Produktivcode wurde nicht verändert.**

Ausführen: `./gradlew :app:testDebugUnitTest --tests 'at.heinz.deviceai.chief.*'`

## Szenarien und Ergebnis (HOST_VERIFIED, JDK 17)
| # | Punkt | Szenario | Ergebnis |
|---|---|---|---|
| S1 | 1 | Anfrage → Quittung (UNDERSTANDING/PLANNING vor dem ersten Modellaufruf) → Werkzeug → Ergebnis → genau ein Terminalzustand als letztes Ereignis, lückenlose Sequenznummern | PASS |
| S1b | 1/8 | Reine Antwort ohne Werkzeug: Die Runde endet terminal, ohne Werkzeugaufruf und ohne „verifiziert“ zu behaupten | PASS (siehe Befund B2) |
| S2 | 2 | Arbeiter-Lauf im Hintergrund blockiert im Modell. Der Chief sieht den Zwischenstand, beantwortet parallel eine zweite Frage und bricht den Arbeiter ab (`cancelGeneration` 1×, 0 Werkzeugläufe, BLOCKED) | PASS |
| S2b | 2 | Arbeiter mit eigenem Auftrag, eigener Werkzeug-Whitelist und Bericht zurück | PENDING (keine API) |
| S3a | 3 | Register und Schema: unbekanntes Werkzeug, fehlendes Pflichtfeld, Zusatzargument, Roh-Shell, befehlsartiges Argument und falscher Typ werden REJECTED, das Backend läuft 0× | PASS |
| S3b | 3 | CONFIRM: Nein → 0 Ausführungen, Ja → genau 1. WAITING_FOR_APPROVAL kommt vor EXECUTING, READ_ONLY ohne Rückfrage | PASS |
| S3c | 3 | Eingeschleuster Befehl in einer Webseite (`system: … delete_file`). Ein naives Modell folgt ihm, die Löschung wird aber abgefragt, abgelehnt und läuft 0×. Der Sanitizer zitiert Rollenpräfix und `<<<`-Marker | PASS |
| S4 | 4 | Suche findet einen älteren datierten Eintrag („Zahnarzttermin“ über „Termin“). SENSITIVE wird per Datenschutzfilter ausgeschlossen, die relevante Erinnerung erreicht das Modell | PASS |
| S4b | 4 | Schichten Profil, Tagebuch und geteiltes Wissen für alle Agenten | PENDING |
| S5 | 5 | SkillGraph: Nur registrierte Werkzeuge erlaubt, standardmäßig deaktiviert, Abhängigkeiten zuerst | PASS |
| S5b | 5 | Fähigkeiten-Bibliothek als Textdatei, bei Bedarf geladen | PENDING |
| S6 | 6 | Routinen melden sich nur, wenn es Neues gibt | PENDING |
| S7 | 7 | Agent-Nachrichten: dringend weckt, Info wartet | PENDING |
| S8a | 8 | Löschen nur mit Policy, Ja und starkem Ja. Jede fehlende Bedingung → 0 Ausführungen | PASS |
| S8b | 8 | Limitwächter: Gate-Budget (2 von 3), Agent-Werkzeugbudget und Schrittgrenze begrenzen ein „gieriges“ Modell | PASS |
| S8c | 8 | RAM-/Temperaturwächter (`ConstrainedResourceFabric`): heiß, zu wenig RAM oder nicht gemessen → Ablehnung (fail-closed) | PASS |
| S8d | 8 | Wächter ist vor dem Modell- bzw. Arbeiterstart verdrahtet | PENDING (siehe B3) |

**Summe neu:** 17 Tests, 11 PASS, 0 FAIL, 6 PENDING (`@Ignore("PENDING …")` mit Begründung und „SCHARF:“-Anleitung im Testkörper).

**Gesamtsuite JVM** (`:app:testDebugUnitTest`, sauberer Lauf): **1.810 Tests, 0 FAIL, 0 Error, 11 skip** (#373: 1.793 Tests mit 5 skip, + 17 neue mit 6 pending).
Die Node-Suite (`.github/continuum/*.test.mjs`) habe ich nicht erneut ausgeführt, weil dieser Zweig dort nichts ändert.

## Befunde (dokumentiert, nicht behoben)
- **B1 – Quittung kommt erst nach dem Gedächtnisabruf (niedrig).** In `BoundedAgentOrchestrator.runFrom` laufen `memories()` und
  `HybridMemoryRetriever.retrieve` *vor* dem ersten `emit(UNDERSTANDING)`. Ist der Gedächtnis-Lieferant langsam (große Datenbank, IO),
  sieht der Nutzer bis dahin keine Quittung. Für Punkt 1 („sofort quittieren“) sollte `UNDERSTANDING` vor dem Abruf emittiert werden. Das ist aus dem Quellcode belegt, einen Test dafür gibt es nicht.
- **B2 – Normale Antworten enden als BLOCKED (mittel, UX).** Ohne frische Werkzeugbeobachtung meldet der Orchestrator immer
  `CognitiveWorkState.BLOCKED`, auch bei einer erfolgreichen Plauderantwort (`verifiedOutcome` setzt eine Werkzeugausgabe voraus). Das ist ehrlich
  („nicht verifiziert“), aber für das Chief-Muster fehlt ein Terminalzustand „beantwortet, nicht werkzeugverifiziert“. Avatar und UI
  zeigen sonst nach jeder Smalltalk-Runde „blockiert“. Empfehlung an HAUPT: eigener Zustand oder Flag, kein Umdeuten von COMPLETED.
- **B3 – Ressourcenwächter nicht verdrahtet (mittel).** `ConstrainedResourceFabric.plan()` wird im Produktivcode nirgends aufgerufen,
  nur in Tests. RAM- und Temperaturschutz greifen zur Laufzeit also nicht. Einen Nutzungslimit-Wächter (z. B. 90 %) gibt es nicht.
- **B4 – Nach erschöpftem Agent-Werkzeugbudget wird das Modell weiter gefragt (niedrig).** Bei `calls >= maxToolCalls` macht die Schleife
  `continue` und ruft das Modell erneut auf, bis es antwortet oder die Schrittgrenze greift. Das ist begrenzt (S8b), kostet aber unnötige
  Modellaufrufe. Empfehlung: Nach der Budgetmeldung höchstens noch einen Antwortversuch ohne Werkzeuge erlauben.
- **B5 – Lexikalischer Injektionsschutz.** `LocalConversationPromptFirewall` und `BoundedResearchTextSanitizer` sind eng und lexikalisch. Der
  eigentliche Schutz ist die Freigabegrenze (S3c), Text ist nie eine Freigabe. Das ist in Ordnung, sollte aber in CHIEF-PATTERN.md so festgehalten werden.

## Was für die anderen Bots fehlt (Tests liegen bereit)
- **HAUPT (1–3):** Chief/Arbeiter-API (`delegate(brief, allowedTools) → WorkerReport`), `docs/CHIEF-PATTERN.md`, B1/B2. → S2b scharf schalten.
- **Continuum Gedächtnis (4–5):** Schichten Profil, Tagebuch und geteiltes Wissen (agentenübergreifend), Text-Fähigkeiten-Bibliothek. → S4b, S5b.
- **Continuum 1 (6–7):** Routinen mit Zeitplan/Auslöser und „nur bei Neuem melden“, Agent-Nachrichtenbus mit URGENT/INFO. → S6, S7.
- **REPERATEUR (8):** Wächter vor Modell- und Arbeiterstart verdrahten (B3), Nutzungslimit-Wächter. → S8d.

Grenzen: reine Host-JVM-Tests, keine Fold7-Prüfung, keine echten Modell- oder TTS-Laufzeiten. „Ansprechbar“ ist auf Thread-Ebene belegt,
nicht auf UI-/Activity-Ebene (`DEVICE_REQUIRED`).
