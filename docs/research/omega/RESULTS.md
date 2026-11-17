# Technischer Reparaturbericht, Messung und Übergabe

Diese Arbeit verbessert geprüfte Integrationszuverlässigkeit. Ein großer allgemeiner JARVIS-Intelligenzsprung oder neue neuronale Transferfähigkeit ist **noch nicht nachgewiesen**. Architektur: [ARCHITECTURE.md](ARCHITECTURE.md). Laufender Einstieg: [ASTRA-HANDOFF.md](ASTRA-HANDOFF.md). Forschungsarchitektur A–F: [PLAN.md](PLAN.md). Unabhängige Gegenprüfung: [REVIEW.md](REVIEW.md).

## Tatsächliche Reparaturen

| Problem / Datei | Änderung und Beleg | Codecommit |
|---|---|---|
| `.github/continuum/code-analyzer.mjs` ließ AgentActivity mit 401879 Bytes still weg | Limit explizit 1000000 UTF-8-Bytes, Überschreitung führt zu Fehler statt falscher Verwaist-Meldung; Regression großer Quelle/Mehrbytegrenze ROT→GRÜN | `4b9ead538514680427f3330ef6b6e1938f9123d5` |
| `typed-android-coding-wiring.test.mjs` verlangte veraltetes `verified`-Fragment | Tatsächliches Bridge-Verhalten und unvollständigen gefälschten Erfolgsbeleg prüfen | derselbe |
| `scripts/continuum-m6-review-qa.sh` rief fehlenden Wrapper auf; Backend-Abhängigkeiten verunreinigten Checkout | Verfügbare Gradle-CLI; exakte SHA/erlaubter Forschungsbranch; node_modules ignoriert; 32 fokussierte Regressionen bestanden | derselbe |
| Bestehende Tests erlauben Scheinreparaturen | Zusätzliches opt-in VH-SR-Veto samt unabhängiger Worktree-Wiederholung; 26 fokussierte Tests bestanden | `6345fab3fceaa2cd44ca5de30aa82c9fbe229feb` |
| Lokaler Dialog verlor Kontext vor dem letzten Paar, Fallback-Erinnerungen verloren Prüfstatus | Vier begrenzte Paare, Herkunft/Validierung im Fallback; bestehende Stores/Runtime/Tools bleiben verbunden | `c078396b87b671ee2fb6a727fc95d6503cd60815` |
| Formprüfung erzeugte Erfahrungserfolg ohne Sachbeweis | Aktiver Dialog deklariert `evaluationConfirmsOutcome=false`, speichert PARTIAL statt SUCCESS; sechs Integrationsverträge geprüft | derselbe |
| Native Conversation behielt alte Sitzungsdaten trotz geleertem UI-Kontext | Vollständiger Prompt erhält frische Conversation; Tool-Fortsetzung behält aktuelle. Zwei Lebenszyklustests und aktualisierter Node-Wiringtest ROT→GRÜN | `c721f785a5afc65cc05ec3415d9d0f7844d5ad88` |

Keine Signing-, Release-, Produktivdaten- oder Gewichtsänderung. Native Conversation-Neuerzeugung kann zusätzliche Latenz verursachen; reale Fold7-Messung steht aus. Es wird nur der Gesprächszustand, nicht die geladene Modell-Engine erneuert.

## Kontrollierte Reparaturmessung

Eingefrorener Benchmarkcommit: `da8196c9c1cdb46aa20d651b330d99e49f4cf7f3`. Rohbelege [controlled-replay.json](evidence/controlled-replay.json). Fünf synthetische numerische Aufgaben, zwei Wiederholungen, abwechselnde Variantenreihenfolge. Die bisherige Pipeline bleibt in der Baseline unverändert, VH-SR erhält zusätzlich eine eingefrorene kleine Prüfsuite. Gleiche Vorschlagsfolge und Obergrenze von zwei Vorschlägen; Bewertung mit anderen Eingabewerten nach Patchannahme. Aufgabentypen umfassen absichtliche Scheinreparatur, direkt richtigen Vorschlag und innerhalb des Budgets unlösbaren Vorschlagsstrom.

| Messgröße | Baseline | VH-SR |
|---|---:|---:|
| Läufe | 10 | 10 |
| Auf separaten Eingaben korrekte angenommene Patches | 2 | 8 |
| Falsch angenommene Patches | 8 | 0 |
| Keine Annahme bei Budgetende | 0 | 2 |
| Erkannte Online-Gegenbeispiele | 0 | 10 |
| Tatsächliche Vorschlagsaufrufe | 10 | 18 |
| Tatsächliche Modellaufrufe | 0 | 0 |
| Summe Reparatur-Wandzeit | 5183.222 ms | 7504.606 ms |

Das belegt das Auffinden dieser konstruierten Scheinreparaturen, keinen allgemeinen Modellgewinn. Zusätzliche Tests und mehr ausgeschöpfte Vorschläge verursachen Mehrkosten; keine Verbesserung bei gleicher tatsächlicher Rechenlast behauptet. Kein statistischer Signifikanzanspruch aus zehn abhängigen Wiederholungen. Die Aufgaben stammen vom selben Autor; getrennte Zahlenwerte sind kein blinder Aufgaben-Holdout. Daten enthalten Parent-CPU und kumulativen Parent-MaxRSS; Prozessbaum-CPU/Peak-RAM, GPU-Belastung und Androidwerte NOT_MEASURED. Transfer/Retention wurden nicht neu gemessen, kein Modell trainiert. Negative Ergebnisse bleiben enthalten.

## Fünf gewünschte JARVIS-Szenarien

| Szenario | Heute geprüft | Noch fehlender Nachweis |
|---|---|---|
| Mehrnachrichtengespräch | Frühere Budget-/Terminangabe bleibt über drei Paare im begrenzten Kontext; Sitzungsgrenze geprüft | Reales Modell verwendet die Information korrekt im Gespräch |
| Bestätigte Erinnerung | Bestehender Retriever liefert Besitzerangabe, Provenienz bleibt sichtbar, Antwort wird der Angabe zugeschrieben | Reale natürliche Antwort, Widerruf während laufender Inferenz, keine Gewichtsrestwirkung |
| Komplexe Anfrage/Teilschritte | Bestehender Klassifikator/Orchestrator verbunden, synthetische Antwort gegen inhaltliche Schritt-Kriterien geprüft | Natürlich erzeugte sinnvolle Pläne und Auswahl besserer Strategien |
| Sicheres Werkzeug | Echte ToolGate-Logik + simulierte Gerätefunktion; Antwort muss zur erfolgreichen Beobachtung passen | Tatsächliche Geräteabfrage und Rechtepfad auf Fold7 |
| Korrektur | Eigener Evaluator verwirft 41 und bestätigt 42; bestehendes Retrybudget und Reflection benutzt | Echtes Modell korrigiert aufgrund der Evidenz, nicht nur Testadapter |

Die sechs Dialog- und zwei Lebenszyklustests sind ausführbare Integrationsverträge. Sie sind **keine acht nativen Modelltests**. Ein Absturz-freier Aufruf allein ist kein Erfolgskriterium. Für natürliche Antwortqualität, Latenz, Speicherbedarf und Fold7-Auswirkungen fehlen echte Messungen; keine Zahlen ersatzweise aus Hosttestzeiten ableiten.

## Reproduktion

```sh
# Checkout des offiziellen privaten Repositories; keine Tokens in Dateien/Logs.
git fetch origin
git switch research/continuum-omega-vhsr-20261025
git rev-parse HEAD

env -u NODE_TEST_CONTEXT node --test --test-concurrency=2 .github/continuum/*.test.mjs scripts/*.test.mjs
npm --prefix backend ci --ignore-scripts --no-audit --no-fund
env -u NODE_TEST_CONTEXT node --test --test-concurrency=2 backend/test/*.test.js

# Gradle 8.11.1, vollständiges JDK 17, Android SDK36 + Build Tools35.0.0.
# JAVA_HOME und ANDROID_HOME auf die eigene Installation setzen.
gradle --no-daemon --max-workers=2 --console=plain -Dorg.gradle.jvmargs=-Xmx2g \
  -Pkotlin.compiler.execution.strategy=in-process \
  :app:testDebugUnitTest :app:lintDebug :app:compileDebugKotlin

# Für exakte ursprüngliche Vergleichszahlen den oben genannten eingefrorenen
# Benchmarkcommit in separatem Checkout verwenden; sonst neuen SHA berichten.
env -u NODE_TEST_CONTEXT node experimental/omega/benchmarks/controlled-repair.mjs > replay.json
(cd docs/research/omega/evidence && sha256sum -c SHA256SUMS)
```

Node 24.19.0/Linux x64; kein SDK-Wrapper im Repository. `--offline` wurde für abschließende Gradle-Läufe nach Installation/Caching verwendet. Netzwerkproxy-/Cachepfade sind Umgebungsdetails, keine erforderlichen Projektsecrets. Android-Lint ist per bestehender Konfiguration auf `NewApi` beschränkt. UI-Widget-Opt-in, Instrumentation, Geräteinstall und native Modelltests NOT_RUN. GitHub Actions durch Account-Billing blockiert; keine Actions-Erfolge behauptet.

Historische Fehlversuche bleiben als Rohbelege erhalten: Baseline Node 1181 PASS/3 FAIL/6 SKIP; Stabilisierungslauf 1186 PASS/1 FAIL/6 SKIP unter zusätzlicher Gradle-Last (Watchdog-Bestätigung), isolierte Wiederholung 7/7 PASS; erste Android-Ausführung mangels JAVA_COMPILER im JRE21 fehlgeschlagen. Finale Prüfwerte werden im Handoff und `evidence/final-validation.json` festgehalten.

## Priorisierter Übernahmeauftrag

1. Gepushten HEAD und `ASTRA-HANDOFF.md` lesen; PR #134/#135-Dateidiffs mit diesem Branch vergleichen. Keine fremden Änderungen ersetzen oder alte Branches löschen. Owner-Review bleibt notwendig vor produktiver Integration.
2. Größter Erkenntnisgewinn: vorhandenes geladenes LiteRT-LM im echten App-Pfad mit fünf neuen, fest eingefrorenen Szenarien gegen `821df5d1...` messen. Kontext, Memory-Attribution, Toolbeleg und Korrektur getrennt werten; Sitzungsreset ausdrücklich testen. Vor APK-Installation ist gesonderte Besitzerfreigabe erforderlich.
3. Danach LM1 in denselben Modelladaptervertrag im Shadow-Modus aufnehmen; Tokenizer-/Checkpointidentität und freie Generierung kennzeichnen. LM2 erst nach Vergleich natürlicher Aufgaben, Corpus-/Holdout-Trennung und Retention entwerfen. EWC als separates messbares Experiment, nicht als bereits vorhandene Fähigkeit behandeln.
4. H-MAC: bestehender ExperienceCompiler, bestätigte Belege und Widerrufskette; Retrieval-baseline gegen Übertragung auf neue Familien. Vorher alte formbasierte SUCCESS-Artefakte auditieren.
5. VH-SR mit eingefrorenem echten Qwen-Modell, unveröffentlichten Aufgaben und gleichen Zeit-/Aufrufgrenzen wiederholen. Negative/No-op-Antworten zählen; Codec-Hypothese nur bei belegtem Nutzen weiterführen. Kein Vorteil des Zeilen-Codecs bisher belegt.
6. EIG-SC/CEFR, atomare Restore-Tests, AEGIS-Datenflussprüfungen und begrenzte ARCHETYP-Evolution auf dieser gemeinsamen Architektur aufbauen. Wenn Mehrkosten ohne Transfergewinn entstehen, Ansatz verkleinern/verwerfen; keine automatische Selbstpromotion.
