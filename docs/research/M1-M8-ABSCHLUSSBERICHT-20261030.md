# Continuum M1–M8 · Technischer Abschlussbericht (30.10.2026)

**Zweig:** `opus/continuum-m1-m8-20261030` · **Draft-PR:** [#244](https://github.com/Ullmann27/device-ai/pull/244) auf [#243](https://github.com/Ullmann27/device-ai/pull/243) (`ebba7e16`) → [#242](https://github.com/Ullmann27/device-ai/pull/242) (`7924ba07`).
**Getesteter Code:** [`c0a28141`](https://github.com/Ullmann27/device-ai/commit/c0a28141). Spätere Commits ändern nur Dokumente und Belege. #242 und #243 sind unverändert, `main` ist unberührt.

## Statustrennung

| | analysiert | implementiert | getestet (Host) | Fold7 bestätigt |
|---|---|---|---|---|
| M1 Integrationsmatrix | ja | Dokument | — | nein |
| M2 #243-Härtung | ja | ja | ja | nein |
| M3 Fähigkeitstransfer | ja | ja | ja (1 vorregistrierter Lauf) | nein |
| M4 Gemma-Aussagenprüfung (SHADOW) | ja | ja | ja (JVM) | nein |
| M5 Host-gegen-Code-Diagnose | ja | ja | ja | nein |
| M6 Abnahmepfad / #236-Prüfung | ja | Dokument | nein (kein Gerät) | **blockiert** |
| M7 Shadow-Kosten | ja | Messprogramm | Host-JVM gemessen | nein |
| M8 Regression | ja | — | ja | nein |

## Umgesetzte Änderungen

| # | Datei(en) | Ursprüngliches Problem | Ursache | Änderung | Test / Ergebnis | Commit |
|---|---|---|---|---|---|---|
| 1 | `scripts/continuum-host-evidence-gate.mjs`, `continuum-evidence-integrity.mjs` | CLI-Gate hing unbegrenzt bei FIFO, folgte Symlinks | Zusammenfassungen wurden vor der No-Follow-Prüfung mit `readFileSync` gelesen | Erst alle Bytes prüfen, dann nur die geprüften Bytes parsen | 3 neue Tests (2 vorher rot), 10/10; echtes CLI auf 517 Belegen Exit 2 | [`1df9e61c`](https://github.com/Ullmann27/device-ai/commit/1df9e61c) |
| 2 | `experimental/ccc1-counterexample-lab/policy.mjs`, `run.mjs` | Orakel nahm das Ergebnis nur aus dem Exit-Code | TAP-Zähler und Exit wurden nicht abgeglichen | `tapOracle`: Exit 0 ⇔ pass 1/fail 0, Exit 1 ⇔ pass 0/fail 1 | +1 Test; #243-Versuch unverändert 16/16/16/8 | [`1df9e61c`](https://github.com/Ullmann27/device-ai/commit/1df9e61c) |
| 3 | `ResearchFeedbackMultiprocessTest.kt`, `android-kotlin-unit-check.sh` | 2 rote Tests am #243-Kopf | `JAVA_TOOL_OPTIONS` auf Kind-stderr; POSIX-Locale (ASCII-Prozessargumente) | stderr getrennt; JUnit unter UTF-8-Locale | 2 rot → 13/13 | [`f12b1c9c`](https://github.com/Ullmann27/device-ai/commit/f12b1c9c) |
| 4 | `experimental/ccc1-counterexample-lab/{novel,transfer,transfer-policy,oracle}.mjs` | Transfer war nur auf Trainingsvorlagen geprüft | — | versiegelter NOVEL-Holdout (18), 6 Arme, Negativkontrollen | Vorregistrierung vor dem Lauf, 14/14 Labortests | [`61b5c59c`](https://github.com/Ullmann27/device-ai/commit/61b5c59c), Ergebnis [`1682ce30`](https://github.com/Ullmann27/device-ai/commit/1682ce30) |
| 5 | `cognitive/LocalModelClaimAudit.kt`, `CognitiveTurnTrace.kt`, `AgentActivity.kt` | Gemma-Antworten wurden nie gegen den Beleg geprüft | `Ewm1ClaimCheck` hatte 0 Aufrufer außerhalb von EWM | SHADOW-Audit → eine metadaten-only Trace-Stufe; Polarität satzweise | 6 neue Tests; ein roter Test deckte die globale Polaritätsgrenze auf | [`d10b8332`](https://github.com/Ullmann27/device-ai/commit/d10b8332) |
| 6 | `.github/continuum/test-failure-environment.mjs`, Harness | Host-Artefakte sahen wie Code-Fehler aus | keine Klassifikation vorhanden | konservative Signaturen (Kodierung, Netz, Platte, Speicher, Timeout), nie ein Pass | 4/4; reale Baseline: 1 von 2 erkannt (der zweite war wirklich ein fragiler Test) | [`a9721f97`](https://github.com/Ullmann27/device-ai/commit/a9721f97) |
| 7 | `research/m7/M7ShadowCostBench.kt` | Shadow-Kosten nur geschätzt | — | Messprogramm OFF vs SHADOW | 3 Läufe | [`c0a28141`](https://github.com/Ullmann27/device-ai/commit/c0a28141) |

## Vorher / nachher (nur gemessene Werte)

| Messgröße | vorher | nachher |
|---|---|---|
| Kotlin-Harness | 1265 Tests, **2 rot** (#243-Kopf) | **1271/1271** |
| Host-Gate bei FIFO-Beleg | hängt (Timeout) | sofort REJECTED |
| belegte Gemma-Antworten mit Aussagenprüfung | 0 % (nicht verbunden) | 100 % als Trace-Stufe (SHADOW) |
| Node kanonisch / Worker-CCC1-F3 | 226 / 390 (#242) | 226 / 390 (unverändert grün) |
| #243-Versuch | 16/16/16 · Guard 8/8 (16 Enthaltungen) | identisch |
| Transfer auf neue Formen | nicht gemessen | Erfahrung 6/18 = ohne 6/18; Verifikation 12/18 mit und ohne Erfahrung (je 30 Läufe) |
| Shadow-Kosten (Host) | geschätzt | F1 +60–65 µs/Zug; Aussagenprüfung 0,63–0,72 ms/Antwort |

Nicht verbessert, und das ist ehrlich: **Gespeicherte Reparaturerfahrung hat auf neuen Fehlerformen nichts gebracht** (M3). Kein Intelligenz- oder Qualitätsgewinn ist auf dem Gerät nachgewiesen.

## M8-Pflichtprüfungen

| Prüfpunkt | Beleg |
|---|---|
| keine Verschlechterung bestehender Gehirnfunktionen | 1271/1271 inkl. EWM2-, Brain-Bridge-, Widerrufs- und Dialogtests; Node kanonisch 226/226 |
| keine unberechtigten Werkzeugaktionen | ToolGate-Tests grün; das Audit ruft kein Werkzeug und kein Modell auf |
| keine doppelte Reparatur nach Neustart | Worker-Satz 390/390 inkl. „unexpected stop … no duplicate patch attempt“ |
| keine erfundenen Lern-/Erfolgsnachweise | M3: 0/6 Negativkontrollen als Erfolg; `tapOracle`; Diagnose vergibt nie einen Pass |
| keine Beschädigung von Ledger/Besitzerfakten/Modelldaten | Ledger, `recordSession`, Ranking und Gewichte unverändert; Audit schreibt nur eine Trace-Stufe |
| kein Schreibzugriff auf geschützte Zweige | nur `opus/continuum-m1-m8-20261030` gepusht; kein Force-Push, kein Merge |

Rohbelege: [`docs/evidence/m18-20261030/`](../evidence/m18-20261030/) (Logs, `qa-summary.json`, SHA-256-Manifest, Git-Blob-Hashes von 1219 getesteten Quelldateien).

## Nicht gelaufen

Gradle-Build, Android-Lint, APK, Signatur, Installation, native Gemma-Inferenz und jede Fold7-Messung fehlen. Grund: In dieser Umgebung ist `dl.google.com` gesperrt (HTTP 403), und es gibt keinen Gerätezugang. Der Pfad dafür ist in [M6](m6/M6-ABNAHMEPFAD-20261030.md) vorbereitet.

## Weitere Möglichkeiten (bewertet, nicht umgesetzt)

| Idee | Erfolgsaussicht | Potenzial | Risiko | Ressourcen | Fold7 | PC nötig |
|---|---|---|---|---|---|---|
| Aussagenprüfung → sichtbarer Hinweis bei UNSUPPORTED | mittel | hoch (Belegtreue) | Fehlalarme | gering | ja | nein |
| F1-Zustand in den Gemma-Prompt (A/B) | mittel–hoch | hoch (Bezüge) | Promptlänge | gering | ja | nein |
| Transfer auf echten Ledger-Fehlern mit teurem Orakel (Gradle) | unklar | mittel (Testläufe sparen) | gering | mittel | Termux | hilfreich |
| CRE1-Semantik im Abruf (E1.5) | mittel | mittel | gering | gering | ja | nein |
| GPU-Backend (K2) | unbekannt | mittel | Treiber | gering | ja | nein |
| Split-MoE / Großmodelle (F2) | gering | hoch für seltene Fälle | Datenschutz/Latenz | hoch | nein | ja |

## Der eine nächste Meilenstein

**Fold7-Abnahme genau dieses Commits nach dem M6-Pfad, inklusive der Flag-Rate der neuen Aussagenprüfung gegen das Besitzer-Feedback.**
Ohne diese Gerätemessung bleibt jede weitere Aktivierung (EWM2 ON, F1 im Prompt, sichtbare Prüfhinweise) eine unbewiesene Behauptung. Mit ihr lässt sich zum ersten Mal messen, ob Continuum belegtreuer antwortet.
