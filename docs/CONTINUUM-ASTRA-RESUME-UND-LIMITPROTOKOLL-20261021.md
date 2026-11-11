> **Fortsetzung 11.11.2026, PR #769:** Gemeinsamer Prüfstand `5b935b4feaf960bb6973dd4d630f02744e41913f`, Kandidat Code54/0.3.53. Aktueller Fortsetzungsbericht: [Omega APK54](research/omega/OMEGA-APK54-HANDOFF-20261111.md). Testmatrix/Register enthalten die neuen Ergebnisse; die nachfolgenden Ausgangsangaben bleiben historisch erhalten. Noch keine neue APK und keine Signier-/Gerätefreigabe.

# Continuum – Astra-Wiederaufnahme und Nutzungslimit-Protokoll
Stand: 21.10.2026 · Projekt: ausschließlich `Ullmann27/device-ai` · Relevanter Draft-PR: #42

## Neuer zusätzlicher Einstiegspunkt: lokaler Coding-Meilenstein

PR #49 auf `astra/continuum-local-coding-milestone-20261021`, ab PR48-HEAD `dfd876c23e2e88faa2ff7ee2f7f75138b416929a`. Funktionsstand `b464b3e` und Abschlussbelege: [CONTINUUM-LOCAL-CODING-MILESTONE-20261021.md](CONTINUUM-LOCAL-CODING-MILESTONE-20261021.md). Tatsächlichen GitHub-HEAD lesen; die nachstehenden älteren APK-/Prioritätsangaben sind historisch. Beschränkter lokaler Zahlenfunktions-Kandidatenpfad, kanonischer Repair-Worker und unabhängige Tests implementiert. Größere Modellpatches, echtes GGUF-/Samsung-Laufzeitverhalten und aktuelle App→Termux→App BUILD-Abnahme bleiben offen. Keine neuronalen Gewichte verändert und kein Main-Merge.


## Ausführungsanweisung für den nächsten Astra-Lauf

Vor jeder Änderung tatsächlichen HEAD des Experimentbranches `astra/continuum-experiment-charter-20261019` und abweichende Branch-/PR-Stände lesen. Zuletzt vor Anlage dieser Übergabe überprüfter PR-HEAD: `8dae174f8779e9aa5b66f3241cd0c28c24861000`; dieser Wert wird durch spätere Commits **historisch**, niemals blind auschecken oder als aktuelle Spitze annehmen. `AGENTS.md`, `docs/CONTINUUM-EXPERIMENTVERTRAG.md`, `docs/CONTINUUM-EXPERIMENTE.json` und Issue #41 erneut prüfen. `main` nicht ändern. Experiment-IDs: EXP-00/01/02/04/05/08/11/12.

Die zuletzt am Gerät gemessene App-Version mit geprüfter Veröffentlichung ist Continuum Preview **0.3.8 / Code 11** (`at.heinz.deviceai.mentorpaired`). Render-Auslieferung basierte auf Quellcommit `fda2339090266fd59f383fe98232f2958a3ba1dc`, Delivery-Commit `30d280fe4911e82b182c925c8c851751473cba85`, APK-SHA-256 `8aa1d102474555be18cc709969a9d7c7101c982735c67fb8a125a296f3e232f0`. Diese APK enthält neuere, seitdem eingecheckte Reparaturen **nicht automatisch**. Owner hat reale Android-Screenshots geliefert: laufendes Startbildschirm-Widget, gespeicherte Forschungs- und Offline-Abschlüsse, Coding-Teamleiter `READY`, aber noch keinen echten Ende-zu-Ende-Coding-Agenten und kein importiertes generatives LiteRT-LM-Modell.

## PRIORITÄT 0 – echter Gerätenachweis 21.10. um 08:21 Uhr

Im Trainings-Monitor: öffentliche Forschung / SWB1, Thema **Asilomar Conference on Beneficial AI**, Start und Abschluss 08:21:37, Phase `FAILED_IllegalStateException` und Telemetrie `FAILED`. Besitzerfreigabe und Jobregistrierung blieben bestehen; nächstes *ungefähres* Android-Planungsfenster 08:36:37. Parallel: Offline-SWB1-Job 07:36:27 `FINISHED` („kein Lerngewinn behauptet“). Dieser einzelne Fehler ist **nicht** durch einen vollständigen Stacktrace erklärt; eine fehlende/ungeeignete deutschsprachige Wikipedia-Seite ist nur eine plausible Hypothese.

Aktuelle relevante Stellen:
- `app/src/main/java/at/heinz/deviceai/learning/ContinuumPublicStudyScheduler.kt`: `ContinuumPublicStudyFetch.fetch()`, `discover()`, `ContinuumPublicStudyJobService.onStartJob()`, `recordCycle()`. Mehrere `check(...)` und `error(...)` werfen `IllegalStateException`, beispielsweise HTTP-Status, Content-Type, fehlender Auszug, Größen-/Zeitgrenze, Quellen-/Archivprüfung. Der Catch speichert derzeit nur `FAILED_` plus Exceptionklasse. Daher **keine** einzelne Ursache ohne Reproduktion behaupten.
- `ContinuumPublicStudyPolicy.kt` beschränkt externe Quellen auf `de.wikipedia.org`, öffentliche textliche Auszüge (96 KiB, 390 Zeichen); die erweiterte Themenwarteschlange stammt aus `ContinuumTopicFrontier.kt`.
- `ContinuumTrainingMonitor.kt` protokolliert LIVE-Ereignisse und zeigt echte `FAILED`-Abschlüsse. Widgets und Erinnerungen beruhen darauf.

Erste sichere Maßnahme: Fehler im Code nach Phase und *eng begrenzter diagnostischer Ursache* differenzieren, z. B. `FETCH_HTTP_STATUS`, `FETCH_MISSING_PAGE`, `FETCH_EMPTY_EXCERPT`, `FETCH_UNEXPECTED_CONTENT`, `ARCHIVE_FAILURE`, `LEARNING_AUDIT_FAILURE`, `CANCELLED`, ohne URLs mit Secrets, Auszugstexte oder ungesicherten Stacktraces in Widget/Benachrichtigung einzubauen. Für bekannte fehlende Artikel ein vorhersagbares, ehrliches `SOURCE_UNAVAILABLE`-Ergebnis prüfen, **ohne** Gewichte, gelernte Quellen, Versuchsstatistiken oder Fehlerursachen zu verfälschen. Kurzfristige Netzwerkfehler getrennt behandeln; Rate-Limits respektieren. Reproduzierbare negative Kotlin- und Node-Regressionsprüfungen für fehlende Seiten, HTTP 429/503, beschädigtes JSON, Zeitbudget/Cancel, 20%-Akku, kaputte Archive und unveränderte Checkpoints. Echte Gerätediagnose bleibt offen bis der Owner ein späteres Testresultat zeigt.

Keine unbegrenzten Retries und keine direkte Shellausführung aus fremden Quellen. Patch ausschließlich in Experimentbranch; Android-Kompilierung, Node-/Backend-Tests und Sicherheitsgates vor einem APK-Release.

## PRIORITÄT 1 – bereits gesicherte, aber noch NICHT mit neuer APK gebaute Änderungen

Nach 0.3.8 wurden auf dem Experimentbranch ergänzt:
1. `DeviceTeamleader.kt`: neuer konservativer Akku-Abbruchgrund `BATTERY_UNKNOWN` im persistenten Teamleiter-Status zugelassen; zugehöriger Android-JVM-Regressionstest. Dieses Update wurde **nicht** gegen Render/Android als Teil von 0.3.8 gebaut.
2. `AgentActivity.kt`: Entwicklerdialog zeigt **EIGENEN CODING-WORKER PRÜFEN** und führt zum vorhandenen `openMobileBuilder()`; Architektur-/Negativtests und `docs/CONTINUUM-EIGENER-CODING-AGENT-20261021.md` liegen bereit, vollständig zu bauen und die Tests zu korrigieren, wenn deren Annahmen vom realen Code abweichen.

Bei jeder Fehlerbehebung diese Querschnittsänderungen berücksichtigen; sie nicht still verwerfen und nicht als bereits gerätebestätigt ausgeben.

## PRIORITÄT 2 – eigene Coding-Agent-Integration, KEIN fremder Bot

Einmaliges kanonisches Backlog und Lease, eigene deterministische Reparaturpipe (`.github/continuum/repair-loop.mjs`), Builder auf Android/Termux, `DevelopmentCoordinator.kt` mit Rollen für Code-/Kritiker-/Testentwurf-/Richter-Kandidaten (noch **kein** echter Testexecutor) an **einen** authorisierten Jobvertrag koppeln. Zuerst kleiner echter Android-Durchstich aus Besitzer-Chat oder Sprache zu fixen Task-Spezifikationen, isoliertem Worktree, tatsächlichem Test-/APK-Exitcode, Signer/Hash, Besitzerprüfung und nachvollziehbarem Status. Keine Fremdagenten, kein automatisch ausgeführter Modelltext, keine Übernahme in `main`.

Ein optionales LiteRT-LM-Codingmodell ist nur austauschbare Kandidatenerzeugung; es ist derzeit nicht importiert und darf keine Autorität über Shell, Git, APK-Installation, private Fakten oder Modellpromotion erhalten. SWB1-/CDX1-Lernen und Holdouts separat messen. Nicht gleichzeitig großen alternativen Agenten oder zweiten Backlog bauen.

## Nutzungslimit: kontrollierte Sitzungsführung für Astra/Sol

WICHTIG: Das ChatGPT-/Codex-Work-Nutzungskontingent ist **keine** aus GitHub oder Android auslesbare Variable. Es gibt hier keine belegte offizielle API, mit der Continuum automatisch verbleibende Astra-Prozente oder die Zeit bis zur Kontingentrücksetzung erkennen kann. Angaben aus dem ChatGPT-UI sind Owner-Berichte. Der Code darf keine fiktiven Prozentwerte generieren, Quoten zurücksetzen, Konten rotieren oder ein API-Budget behaupten.

Die Angabe „in 23 Minuten wieder 100 % für fünf Stunden“ bedeutet **23 Minuten warten auf den nächsten Nutzungszeitraum**; sie bedeutet *nicht*, dass nur noch 23 Minuten Arbeit verbleiben. Nach Rücksetzung im UI unter Einstellungen → Nutzung tatsächliche verbleibende Kontingente, ggf. fünfstündige und wöchentliche Grenzen prüfen. Kein starkes Modell für triviale Textsortierung/Git-Status; High/Very High nur für komplizierte Codeentwurfs-/Diagnoseaufgaben. Eine globale Shared-Allowance ist nicht durch Wechsel zwischen Astra und Sol vermeidbar.

Konservatives Arbeitsschema je neu gestarteter Session:
1. Zu Beginn den aktuellen SHA, Status aller offenen Tests, aktuellen Worktree/PR und einen **einzigen** kritischen Fehler übernehmen. Explizites Ziel/Abbruchbedingung und maximal 15-Minuten-Sprint notieren.
2. Nach **jedem** funktionalen Patch oder spätestens **alle 15 Minuten** einen Git-Commit mit eng begrenzter Änderung und ein kurzes Text-Handoff: `branch`, vollständiger `HEAD`, aktuelle Testkommandos und Ergebnisse, Fehlercodes, offene Dateien, erster nächster Schritt, Rollback. Keine uncommitteten nennenswerten Änderungen bis zum Quotenende aufbauen. Bei ungeplantem Stopp reproduzierbares `INTERRUPTED` statt `DONE`.
3. Die Quotenschwellen beziehen sich **nur** auf den vom Besitzer tatsächlich im UI gemeldeten Rest: unter ca. 30 % keine neuen breiten Featurestränge; bei 15 % nur laufenden atomaren Teil abschließen, Report und Übergabe schreiben; bei 5 % keine weiteren risikoreichen Tools/Builds starten. Bei unbekanntem Rest vorbeugend ohnehin jede kleine Teilaufgabe sichern.
4. Für längere Builds Tests/Compiler extern auf bereits autorisierter lokaler Toolchain oder Render ablaufen lassen; bei Quotenstopp können unabhängige Worker nach ihren **eigenen** Berechtigungen weiterarbeiten. Ein ChatGPT-Limit darf niemals als Beweis für ein abgeschlossenes Build gelten; Zustand erst nach neuem Lesen der echten Job-/Render-Ergebnisse melden.
5. Letzte 15–30 Minuten des **geplanten** Arbeitstages nur Integration, Gesamtprüfung und Handoff; die „fünf Stunden“ sind keine garantierte Laufzeit. Vor jeder aufwendigen Aktion zuerst Nutzungsanzeige prüfen, wenn verfügbar.
6. Beim Wiedereinstieg nur `AGENTS.md`, dieses Handoff, neuestes Git-HEAD und betroffene Quelldateien lesen; bereits bestätigte Informationen nicht vollständig neu recherchieren. Historische SHA/Builds nie als aktuell ausgeben.

## Konkreter Fünf-Stunden-Plan nach Reset, unter Vorbehalt des wirklichen Budgets

Phase A: aktuellen Experiment-HEAD/Status prüfen und den öffentlichen Forschungsfehler mit reproduzierbarer Fehlertaxonomie isolieren, möglichst sicher reparieren.
Phase B: Regressionen gegen Source-Fetch/Jobplanung, Gewichtspersistenz, Holdouts, gespeicherte Lernen-/Archiv-Evidenz und Rate-Limit-Verhalten ausführen. `BATTERY_UNKNOWN`-Teamleiter-Patch mitprüfen.
Phase C: Vorhandene Coding-Agent-Komponenten zu EINEM kleinen auf Android ausführbaren Auftrag zusammenführen; nicht ohne Gerätebeleg eine vollständig autonome APK-Generierung behaupten.
Phase D: vollständige Tests, Kotlin/Gradle/Lint, signierte APK nur bei komplett grünen Gates und Quellen-/Signer-/Hashgleichheit; Nutzer erst bei wirklicher Geräteabnahme ein Update empfehlen.
Phase E: finalen Stand dokumentieren, Zwischenkommits und eventuelle verbleibende Blocker mit Belegen und nächstem exakten Schritt sichern.

## Übergabevertrag (in jedem Schlussreport ausfüllen)

`AKTUELLER_BRANCH`, `AKTUELLER_HEAD_SHA`, `PR`, `GEÄNDERTE_DATEIEN`, `TESTS_TATSÄCHLICH_AUSGEFÜHRT`, `TESTS_OFFEN`, `APK_VERSION_SHA_SIGNER_FALLS_GEBAUT`, `FEHLER_REPRO`, `GERÄTEABNAHME_FEHLT`, `NÄCHSTE_15_MINUTEN_AKTION`, `ROLLBACK`, `ERLAUBNISSTATUS`. Niemals Status `DONE` wenn nach Quotenstopp kein Verifikationsbeleg vorliegt.

Diese Datei ist ein Arbeitsprotokoll, kein automatischer Kontingentleser und kein Timer/Background-Agent.
