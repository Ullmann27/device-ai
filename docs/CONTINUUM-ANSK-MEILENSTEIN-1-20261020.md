# Device AI Continuum – ANSK-Meilenstein 1

**Stand:** 20. Oktober 2026  
**Branch:** astra/continuum-experiment-charter-20261019 · Draft-PR #42  
**Status:** Forschungsimplementierung im GitHub-Branch; kein Android-Gerätenachweis der neuen Prüfkette und keine automatische Modellpromotion.

## Erster vertikaler Systemaufbau

1. Der bestehende reversible Kotlin-Import-Planer erzeugt eine geplante Ein-Fehler-Mutation. Die fünf ursprünglichen Planner-Tests wurden bereits auf Samsung bestanden.
2. Der neue einmalige ANSK-Labor-Runner (ansk-import-lab.mjs) arbeitet ausschließlich im vorhandenen experimentellen Branch. Er verweigert main und unsaubere Quell-Checkouts.
3. In einem verwalteten Git-Wegwerf-Worktree wird eine **echte Grün-0 → Rot-Mutation → Grün-Reparatur** ausgeführt. Das Labor benutzt nur die vorhandene feste Android-Prüfkette aus repair-loop.mjs, niemals generierte Shell-Befehle.
4. Alle vier Prüfphasen verwenden Gradle --rerun-tasks. Die vorhandene Java-17/Gradle-8.11.1/ARM64-aapt2-Konfiguration aus scripts/continuum-mobile-builder.mjs wird wiederverwendet. Fehlende gepinnte Termux-Werkzeuge blockieren die Prüfung.
5. Die mutierte Datei muss eine konkrete Kotlin-Compilerdiagnose UNRESOLVED_REFERENCE zum erwarteten Symbol liefern. Nur die bereits vorhandene, vom repair-critic akzeptierte Strategie KOTLIN_MISSING_IMPORT ist als experimenteller Reparaturgewinner zulässig.
6. Derselbe Patch wird auf einem **zweiten unabhängigen Worktree** vom ursprünglichen Basis-Commit erneut angewendet und mit vollständig frischer Kompilierung sowie JUnit getestet. Source-SHA-256 und pro Profil ausgeführte Testzahlen werden kontrolliert.
7. Nur bei bestandenen Prüfungen wird ein **deklaratives Rezept mit SHA-256** in privaten Termux-State-Daten außerhalb des Git-Checkouts gesichert. Es ist quarantänisiert und hat ausschließlich SHADOW_ONLY-Autorität. Weder repair-ledger.json noch CRE1-Gewichte werden verändert.
8. Mit planAnskShadowReplay kann dieser Rezeptbeleg einen neuen, bisher unbekannten Kotlin-Importfehler lediglich als **unverbindlichen Kandidaten** bedienen. Bestehender Kotlin-Lexer, insertImport, hunk, applyEdits und Critic bleiben zuständig. Keine automatische Aktivierung oder Tool-Registrierung.

## Bestehende Komponenten wiederverwendet

- planInverseKotlinImportMutation in repair-strategies.mjs
- createWorktree, resetWorktree, removeWorktree, boundedWorktreeFile, runChain, evidenceSufficient, rankCandidates und checkCandidateKotlinSyntax in repair-loop.mjs
- proposeCandidates und applyEdits in repair-strategies.mjs
- critique in repair-critic.mjs
- validateTriplePhaseSample in repair-cycle-safety.mjs als separates strukturelles Zusatz-Gate, niemals als Nachweis einer tatsächlichen Ausführung
- resolveTermuxEnvironment in scripts/continuum-mobile-builder.mjs

## Neue Dateien und Testfälle

- .github/continuum/ansk-lab-contract.mjs – Forschungs-Gates für grün/rot/grün und gehashte Rezepte
- .github/continuum/ansk-import-lab.mjs – zeitbegrenzter Einmalversuch in zwei isolierten Worktrees
- .github/continuum/ansk-recipe-replay.mjs – rein lesende Wiederverwendung eines quarantänisierten Rezepts
- .github/continuum/ansk-lab-contract.test.mjs – 11 neue Kontrakttests
- .github/continuum/ansk-import-lab.test.mjs – 6 neue Git-/Branch-/CLI-Sicherheitsprüfungen
- .github/continuum/ansk-recipe-replay.test.mjs – 7 neue Schatten-Replay- und Tampering-Tests

Damit sind **24 neue Testfälle** im Forschungsbereich definiert. Der vorherige Mutationsplaner hat weitere 5 Tests; der Dreiphasen-Validator hat eigene Regressionstests.

## Sicherheits- und Erkenntnisgrenzen

Der Prototyp ist offlinefähig, nutzt keine Groq-Anfrage und keine GitHub-Actions. Er kann keine APK installieren, nach main schreiben, Trainingsgewichte verändern oder generierten Code als Werkzeug registrieren.

Die lokale Schattenrezeptdatei ist nicht kryptografisch authentisiert: Der SHA-256-Hash schützt vor unbemerkter Inhaltsveränderung bei unverändertem Hash, nicht vor Angreifern, die den Inhalt und Hash zugleich ersetzen.

**Nachtrag Testbefehl für die lokale Buildsperre:**

~~~bash
node --test .github/continuum/shared-build-lease.test.mjs \
  .github/continuum/autonomous-teamleader.test.mjs \
  .github/continuum/ansk-import-lab.test.mjs \
  scripts/continuum-mobile-builder.test.mjs
~~~

Der isolierte Modul-Funktionstest der Buildsperre wurde außerhalb des Repositorys mit Node 22 durchgeführt (5/5 PASS). **Die vier obigen originalen Repository-Testdateien, die vollständige Gradle-Prüfkette und die echte Samsung-IPC wurden für diesen neuen Commit noch nicht ausgeführt.**


Der ANSK-Runner besitzt weiterhin einen eigenen exklusiven Forschungs-Lock und prüft zusätzlich den bisherigen Teamleiter-Lock. **Nachtrag 20.10.2026:** Drei lokale Node-Einstiegspunkte – ANSK-Labor, Offline-Teamleiter und Termux-APK-Builder – verwenden nun außerdem dieselbe atomare, checkoutgebundene `shared-build-lease.mjs`. Eine vorhandene Sperre führt zum sofortigen Abbruch vor Gradle; Prozessabstürze führen absichtlich nicht zu einer zeitgesteuerten Sperrenübernahme. Unsicher bereinigte ANSK-Worktrees halten die Sperren zur Prüfung zurück. Das ist eine lokale Kooperationssperre für Prozesse mit derselben Betriebssystemidentität, **noch keine verifizierte Android-App↔Termux-IPC-Lease**, kein OS-Sandbox-Ersatz und keine Freigabe für unbeaufsichtigte Geräteexperimente.

Der Erfolg einer vorhandenen Kotlin-Importregel beweist noch keine neu entdeckte generative Fähigkeit. Dies ist ein absichtlich einfacher Integrationsfall für überprüfbare Erkenntnis-Rezepte.

## Nachweis- und Abnahmeplan

Zuerst die neuen Node-Tests auf dem aktuellen Branch ausführen, nach Sync mit GitHub:

~~~bash
cd "$HOME/device-ai"
git status --short --branch
node --test .github/continuum/ansk-lab-contract.test.mjs \
  .github/continuum/ansk-import-lab.test.mjs \
  .github/continuum/ansk-recipe-replay.test.mjs \
  .github/continuum/inverse-mutation.test.mjs \
  .github/continuum/repair-cycle-safety.test.mjs
~~~

Der erste echte, ausdrücklich vom Besitzer zu startende Samsung-/Termux-Versuch ist:

~~~bash
node .github/continuum/ansk-import-lab.mjs --once \
  --file app/src/main/java/at/heinz/deviceai/learning/ModelCheckpointStore.kt \
  --import java.nio.file.Files
~~~

Diese tatsächliche Projektdatei importiert java.nio.file.Files und ruft Files.move auf. Der Versuch kann wegen Gradle auf Mobilhardware erheblich dauern. Das Lab läuft höchstens 45 Minuten und bricht ab, wenn die Baseline nicht grün, die Mutation nicht rot, die Reparatur nicht grün oder der unabhängige Worktree nicht grün wird. Bei Erfolg meldet es TRIPLE_PHASE_VERIFIED_SHADOW_RECIPE.

**Evidenzstatus:** Die neuen 11 Kontrakttests und 7 Replaytests wurden während der Entwicklung zusätzlich in einer isolierten V8-Umgebung gegen die tatsächlichen GitHub-Quellen geprüft (18 PASS). Dies ersetzt keinen echten Node-Testlauf mit Git-Fixture-Testfällen und keinen Samsung-Kotlin-/JUnit-Build.

## Meilensteinstatus

- M1 Code: kompletter einmaliger Forschungsablauf plus Schatten-Replay im isolierten Branch – **implementiert**.
- M1 Node: alle Tests nativ unter Node ausführen und 0 FAIL/0 SKIP belegen – **offen**.
- M1 Samsung: Grün-0 → Rot-Mutation → Grün-Reparatur + zweiter Worktree mit echter Kotlin-/JUnit-Prüfung – **offen**.
- M1 realer Lerngewinn: ein neues Problem wird durch die Rezeptbibliothek nach unabhängig geprüften Tests messbar besser gelöst – **offen**.
- M2 Android-Autonomie: lokale gemeinsame Build-Sperre für die drei zentralen Node-Worker **quellseitig integriert, fünf neue isolierte Node-Tests lokal erfolgreich**. Die zusätzlich definierten integrationsbezogenen Regressionen sind auf dem vollständigen Repository-HEAD noch nicht ausgeführt. Android↔Termux authentisierte Job-Lease, Gerätekonkurrenz, Recovery sowie Besitzer-/Ressourcengates bleiben **offen**.

Keine unbewiesenen 100-Prozent-Korrektheits-, Null-Watt- oder Submillisekunden-Versprechen.


## Nachtrag 20.10.2026: M1a – erklärbarer Baseline-Blocker (quellseitig integriert)

Der erste auf dem Samsung ausdrücklich gestartete ANSK-Lauf zu Commit
`126bd9905eb76559f23c76eeb9f36d27c45afa50` endete tatsächlich
mit `BLOCKED_BASELINE_NOT_GREEN`. Damit ist **keine** erfolgreiche
Compiler-Reparatur oder Lernrezept-Promotion belegt. Das Labor stoppte
vorschriftsmäßig vor der künstlichen Kotlin-Mutation.

Die bisherige Ausgabe enthielt keinen unterscheidbaren Auslöser.
`.github/continuum/ansk-baseline-triage.mjs` wertet jetzt nur die
bereits tatsächlich gelaufenen Gradle-/JUnit-Profile aus und liefert
stabile, begrenzte Kategorien: Kotlin-Kompilation, JUnit-Fehlschlag,
fehlender JUnit-Nachweis, Toolchain-/Dependency-Problem,
Ressourcenmangel, Timeout oder unklassifizierter Fehler.

- Aus Gradle-Ausgaben werden ausschließlich **fest definierte Kategorien**
  erzeugt. Rohe Fehlertexte, Quelldateipfade, Zugangsdaten, Terminal-
  Umgebungsvariablen und Modelltexte werden weder als Antwort noch
  im ANSK-Diagnosebeleg gespeichert.
- Bei blockierter Baseline schreibt das **bereits vorhandene**
  ANSK-Labor einen kleinen, atomar aktualisierten Diagnosebeleg nach
  `~/.local/state/device-ai-continuum/ansk-lab/last-baseline-failure.json`.
  Es werden weder Quellcode noch `main`, eine APK oder Modellgewichte geändert.
- Die **bestehende** `teamleader-cli.mjs --status` zeigt den Beleg unter
  `localIntegration.ansk.lastBaselineFailure` mit Zustand
  `HISTORICAL_BASELINE_FAILURE_REPORTED`, Commit, Kategorie und
  vorgeschlagener Diagnoseaktion. Der Beleg ist historisch,
  kein aktuell laufender Vorgang und keine verifizierte Reparatur.
- Manipulierte Belege, Symlinks und zu große Dateien werden nicht als
  valide Statusmeldungen übernommen.

**Native lokale Modultests:** 10/10 bestanden mit Node 22.16; die
verifizierten Git-Blob-Hashes der getesteten Quelldatei und Testdatei
stimmen bytegenau mit den GitHub-Blobs überein. Diese Modultests
ersetzen **keinen vollständigen Repository- oder Samsung-Test**.

Nach einem geprüften Fast-forward im bestehenden Experimentbranch:

~~~bash
node --test .github/continuum/ansk-baseline-triage.test.mjs \
  .github/continuum/ansk-import-lab.test.mjs \
  .github/continuum/local-integration-status.test.mjs
~~~

Den echten neuen Fehlergrund liefert erst ein erneuter, **ausdrücklich
besitzerautorisierter** ANSK-Einmalversuch unter unveränderter
`--once --file ... --import ...`-Befehlsgrenze und bei vorhandener
Termux-Toolchain. Danach kann der lokale Teamleiter-Status die
historische Kategorie anzeigen, auch wenn das Labor wieder vor der
Mutation abbrechen musste.

**Nächste Entwicklungsentscheidung:** Anhand des tatsächlich gemeldeten
Fehlertyps bestehende Reparatur-/Toolchainpfade gezielt verbessern;
keine künstliche Freigabe einer roten Baseline. Eine automatische
Baseline-Reparatur ist hier noch nicht behauptet.


## M1b – reale Samsung-Baseline mit drei übersprungenen Tests

Am 20.10.2026 wurde ein ANSK-`--once`-Lauf auf Samsung Android 16 ARM64
mit sauberem Experiment-HEAD `d86c7599771379070840f442fd84524915b8b19c`
beendet. Die tatsächlich angezeigten Daten:

- `android-compile`: `passed=true`, `exitCode=0`, 0 Compilerfehler;
- `android-unit`: `passed=true`, `exitCode=0`, **632 ausgeführte
  Tests**, `failed=0`, **3 übersprungene Tests**;
- ANSK-Antwort: `BLOCKED_BASELINE_NOT_GREEN`, `reason=INVALID_PROFILE`.

**Quellcode-Ursache:** Die erste Version von `measureAnskChain` lehnte
ausnahmslos jedes `p.tests.skipped>0` ab, auch wenn echte JUnit-Tests
erfolgreich waren. Das ist **kein** Nachweis eines Kotlin-Compilerfehlers
und keine erfolgreich durchgeführte Reparaturmutation.

**Gezielte Anpassung der vorhandenen Repair-Pipeline:**

1. `parseJUnit` und `readJUnitDirectory` erzeugen SHA-256-Fingerabdrücke
   der im echten Gradle-JUnit-XML als `skipped` markierten Testfallidentitäten
   (Klassenname plus Methodenname). Die Rohbezeichnungen erscheinen nicht
   im öffentlichen ANSK-Status.
2. Der bestehende `runProfile` fasst diese Fingerabdrücke deterministisch
   sortiert zu `tests.skippedCaseDigest` zusammen. Unvollständige,
   unidentifizierbare oder doppelte Testfallidentitäten erzeugen **keine**
   positiv akzeptierte Skip-Evidenz.
3. `measureAnskChain` akzeptiert bereits vorhandene Skips der
   Ausgangsbasis nur bei vollständig vorhandenem Digest. Reparatur und
   unabhängiger zweiter Worktree müssen **dieselbe Zahl und denselben
   Fingerabdruck** übersprungener Tests nachweisen und mindestens dieselbe
   Zahl wirklich ausgeführter Tests liefern.
4. Ein anderer übersprungener Test trotz gleicher Skip-Anzahl,
   zusätzlich übersprungene Tests, ausbleibende JUnit-Dokumentation,
   weniger ausgeführte Tests und fehlender/ungültiger Digest blockieren
   weiterhin mit `INVALID_PROFILE` oder `EVIDENCE_REGRESSION`.
5. Die bestehende ANSK-Triage erkennt fehlende Skip-Identitäten als
   `ANDROID_UNIT_SKIP_IDENTITIES_MISSING` und empfiehlt eine Überprüfung
   der JUnit-Dateien, nicht das Überspringen der Sicherheitsprüfung.

Dies ist eine **Belegnormalisierung**, kein allgemeines `--ignore-tests`
und keine künstliche grüne Baseline. Der automatische Installations- und
Produktionszugriff bleibt unverändert ausgeschaltet.

**Validierungsstatus:** Die neuen Kernregeln und XML-Erkennung wurden
in neun quellgebundenen isolierten JavaScript-Prüfungen nachvollzogen;
zusätzliche native Node-Tests sind im Experimentbranch definiert.
**Die vollständigen Node-/Gradle-Tests auf Samsung für diesen neuen
Commit stehen noch aus.** Eine tatsächlich unabhängig verifizierte
`TRIPLE_PHASE_VERIFIED_SHADOW_RECIPE` liegt weiterhin nicht vor.

Reproduzierbare, noch offene Geräteabnahme:

~~~bash
node --test .github/continuum/repair-diagnostics.test.mjs \
 .github/continuum/ansk-lab-contract.test.mjs \
 .github/continuum/ansk-baseline-triage.test.mjs \
 .github/continuum/ansk-import-lab.test.mjs \
 .github/continuum/local-integration-status.test.mjs
~~~

Erst bei vollständig grünem Lauf ist ein neuer, ausdrücklich
autorisierter ANSK-`--once`-Versuch sinnvoll. Das Origin-Git-SHA,
Testzahlen, Skip-Digest und eventuelle Fehler werden getrennt
aufbewahrt. Die lokale APK wird weder installiert noch freigegeben.

**Kompatibilität:** Bestehende `testProfile`-, `executed`-,
`failed`- und `skipped`-Felder bleiben unverändert;
`skippedCaseDigest` wird nur für tatsächliche Skips ergänzt.
Alte Belege ohne Digest können nach dieser Regel nicht als
erfolgreiche Baseline mit Skips gewertet werden.

**Rollback:** Die Änderungen an
`repair-diagnostics.mjs`, `repair-loop.mjs`,
`ansk-lab-contract.mjs`, `ansk-baseline-triage.mjs` und den
zugehörigen Tests isoliert revertieren; keine Datenbankmigration und
kein schreibender Eingriff in `main`.
