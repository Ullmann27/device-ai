# Continuum MASTER – Sol an GPT-6, 23. Oktober 2026

**A und B sind mit echten Host-SDK-Belegen abgeschlossen. Zwei getrennte M4-APKs sind gebaut und unabhängig geprüft.**
Aktueller Fold7-/Termux-Gesamtlauf, Installation, allgemeines Dialoglernen und der neue 30-Sekunden-Modus sind offen.
Keine Besitzer-APK gebaut, kein Main-Merge, keine Besitzergewichte verändert. Der PR bleibt Draft.

## 1. Tatsächliche Git-Herkunft und gesicherter Endstand

- Repository ausschließlich `Ullmann27/device-ai`.
- Start- und erneut bestätigter Remote-M4-HEAD: **`b842d471183fcf181f392d522a0d0a8f5936a649`**.
- Eigener isolierter Zweig: **`sol/continuum-master-m4-research-integration-20261023`**.
- Eingefrorener Implementierungs-Endcommit: **`c787c0986a2dd944e41cfa08bf4ceba226148a07`**.
- Entwicklungsabschluss einschließlich beider APKs und aller Belege: **`3d7fdb03eafb71c41bc8fa05d252ddc43b83d9a2`**.
- Diese Übergabe folgt als Dokumentationscommit auf diesen Abschluss; dessen eigene Identität ist nach dem Checkout mit
  `git log -1 --format=%H -- docs/CONTINUUM-SOL-TO-GPT6-HANDOFF-20261023.md` reproduzierbar.
- Eigener Draft-PR: **[PR #108](https://github.com/Ullmann27/device-ai/pull/108)**, Basis bleibt GPT-6-M4 / PR #107.
- PR #107, #106 und #99 wurden nicht gemergt, geschlossen, überschrieben oder force-gepusht. `main` blieb unverändert.
- Die Remote-Identität wurde vor den Arbeitsschritten erneut geprüft; die Übergabeanker ersetzten keine Git-Prüfung.

Commitfolge: `2ef7136` Design/Baseline → `ba6aa26` Sicherheits-/Regressionskorrekturen → `3f9ce26`
zwei Projektpläne/Host-Doctor → `6ff6d8f` Belege/Wiederaufnahme/Prozessläufe → `c787c09`
Android-Integration und Reviewkorrekturen → `3d7fdb0` tatsächliche APK-/Testarchivierung.
Jeder dieser Schritte wurde auf den eigenen Zweig gepusht. Kein Arbeitsstand ist ausschließlich in dieser Sitzung vorhanden.

## 2. Implementierte Funktionen und Architektur

### Gemeinsamer typisierter Projektplan

`app/src/main/assets/continuum-owned-project-plans-v1.json` ist der gemeinsame versionierte Katalog.
`scripts/continuum-project-plan.mjs` validiert ihn in Node; `ContinuumOwnedProjectPlan.kt` liest ihn in Android
mit dem vorhandenen StrictJson-/Canonical-JSON-/SHA-Verfahren. Kotlin enthält die beiden geprüften Planhashes.
Unbekannte Versionen, zusätzliche Felder, Rechte, Pfade, Aufgaben und gelockerte Grenzen werden abgelehnt.

Der Plan beschreibt Namen/Paket/versionCode, minSdk26/target36, Screens, Navigation, private Speicherung,
Berechtigungen, Ressourcen, Unit-Testklasse/Fälle, Akzeptanzkriterien, Gradle-Aufgaben, APK-Pfad und Grenzen.
Das ist bewusst ein **begrenzter Katalog zweier geprüfter Projekte**, keine beliebige App-Beschreibung und kein LLM-Quellgenerator.
Die Akzeptanzkriterien sind Anforderungen; ihre bloße Aufnahme ist keine ausgeführte Geräteprüfung.

- `m4-notes`, `at.heinz.continuum.m4notes`: vorhandene Notizen-/Über-App, Entwurfserhaltung und private Preferences.
- `m4-counter`, `at.heinz.continuum.m4counter`: neuer Zähler/Über, Bereich 0…9999, Erhöhen/Zurücksetzen,
  private Preferences, sichere Rückmeldung bei fehlgeschlagenem Speichern.
- Beide ohne deklarierte Berechtigung, insbesondere ohne Netzwerkberechtigung; getrennte Pakete und Projektordner.

### Bestehender Generator und CCC1 bleiben die gemeinsame Kette

`scripts/continuum-owned-app.mjs` unterstützt `create`, `verify`, `build`, neues lesendes `status` und nur die
beiden festen `--recipe`-IDs. Ohne Argument bleibt Notizen der Standard. Standardtoolchain bleibt `termux-arm64`;
`--host-sdk` ist ein ausdrücklicher, getrennter Linux-x64-Prüfpfad. Keine automatische Installation.

Quellenmanifest v2 bindet den vollständigen Plan und alle zehn generierten Dateien. Zusätzliche Build-Eingaben
wie `buildSrc`, Gradle-Inits, Android-Overlays oder weitere Kotlin-Dateien blockieren. Quellen werden vor dem Lesen
größenbegrenzt. Quelle, Elternpfade, Cache-/Outputbäume, Reports und APKs dürfen nicht auf Symlinks/Spezialdateien
umgeleitet werden; die Outputprüfung weist auch Hardlinks zurück. Vorhandene Projekte werden nicht überschrieben.

Vor jedem echten Build werden die konkreten alten JUnit-/Lint-/APK-Ausgaben privat archiviert. Feste Offline-Aufgaben
laufen mit `--rerun-tasks`, maximal zwei Workern, 1536 MiB Gradle-Heap und 35 Minuten Zeitgrenze.
Ein Exitcode 0 allein reicht nicht: exakt alle drei geplanten Testfälle, keine Fehler/Skips und ein gültiger Lintreport
müssen neu vorliegen. APKs sind auf 64 MiB begrenzt.

Die APK wird in eine private exklusive Prüfkopie übernommen. Echte `apksigner`- und `aapt2`-Aufrufe prüfen dieselben
Bytes; Dateihash und Größe werden zwischen den Prüfungen und bei der exklusiven Veröffentlichung kontrolliert.
Fremde bestehende Dateien werden nicht überschrieben. Temporäre Prüfkopien werden bereinigt.

`.github/continuum/owned-project-evidence.mjs` ist ein **zusätzliches Belegprofil des bestehenden CCC1-Verifiers**,
aus `ccc1-verifier.mjs` exportiert. Es ist kein zweiter Reparaturworker und erteilt keine Ausführungsrechte.
Das Profil prüft Plan/Quellenmanifest/Dateihashes, JUnit-Struktur, Lint, Toolausgaben, APK-Bytes, Profil/Host und die
expliziten Nicht-Geräte-Flags. Signierer und Paket müssen zu den gespeicherten gehashten Toolbelegen derselben APK passen.
Der Katalog wird lazy geladen, damit minimale vorhandene CCC1-Checkout-Fixtures ohne Android-Assets weiterhin funktionieren.

### Status, Abbruch und Wiederaufnahme

Private versionierte Receipts liegen in `~/.local/state/device-ai-continuum/owned-projects` mit separaten Laufverzeichnissen,
atomarem `latest-m4-*.json`, `BUILD_RUNNING` und eindeutigen Endzuständen. Lesendes `status` prüft Quellen, Reports und
APK erneut, startet keine Werkzeuge und wiederholt keinen Build. Veränderte Bytes verlieren ihre Bestätigung.
Ein historischer Host-Build wird ausdrücklich als solcher benannt; er wird kein frischer mobiler Build.

`scripts/continuum-owned-process.mjs` startet feste ausführbare Dateien ohne Shell, mit begrenzter Ausgabe,
Timeout und AbortSignal. Termux meldet `process.platform=android`; beide `/proc`-Plattformen werden berücksichtigt.
Eine geerbte zufällige Lauf-ID und PID-Startzeiten erkennen auch Gradles eigene Prozessgruppen und schnell verwaiste
Daemons. Auf Abbruch folgen TERM/KILL und die Prüfung des Endes. Unlesbare Prozessidentität ist kein Erfolgsbeleg.
Die direkte ChildProcess-Instanz wird trotzdem beendet; offene Pipes dürfen keine unbegrenzte Wartezeit verursachen.
Bei unbestätigtem Ende bleibt die vorhandene Build-Lease für die Besitzerprüfung bestehen – auch bei ENOSPC im Log
oder bei Signer-/aapt2-Läufen. Dies gilt für die festen vertrauenswürdigen Werkzeuge, **nicht** als Sandbox gegen
absichtlich ihre Lauf-ID entfernenden Code.

### Android-Verbindung

`ContinuumMobileBuilderActivity` bietet eine persistierte Notizen-/Zähler-Auswahl. Erzeugen und Bauen verlangen
eine auf das gewählte Projekt bezogene Bestätigung; Prüfen und Status sind lesend. Nur feste CLI-Argumente gehen
über die bestehende Termux-Brücke. Der bereits vorhandene Chat-Builder-Einstieg bleibt erhalten.

`ContinuumMobileBuildPlan`, `ContinuumMobileResultPolicy` und `ContinuumMobileBuildResultReceiver` binden das Ergebnis
an private Projektwahl, Rezept, Plan-/Quellhash, Paket/versionCode, Profil und CCC1-Urteil. Ein Host-Receipt darf
keinen frisch bestätigten Termux-Build darstellen. Die einmalige Callback-URI bindet weiterhin Operation/Request-ID.
Neue Recovery verwendet einen eigenen projektgebundenen einmaligen Status-Slot; ein ausstehender Build samt
Callback-Identität bleibt erhalten. Ein Robolectric-Test prüft diese gesamte simulierte IPC-Zuordnung.

Die Premium-Hauptoberfläche, Hologramm, Dashboard, Orbitalring, Anzeigen, Fold-Layout und Editor wurden nicht geändert.
`BootstrapBrain` erhielt lediglich die unten beschriebene Imperativ-Sicherheitskorrektur, keine neue Sprachlernarchitektur.

## 3. Gefundene und behobene Fehler

| Tatsächlicher Fehler | Reproduktion und Korrektur |
|---|---|
| Zusätzliche Quellen waren trotz korrekter Manifestdateien möglich | Neue Negativtests zunächst rot; Generator weist nicht erlaubte Quell-/Gradle-Eingaben zurück. |
| Gradle-Cache-/Outputumleitung und übergroße Quelldatei | Symlink-/Hardlink-/Pfadprüfungen, begrenztes Lesen vor Speicherallokation. |
| Veränderung der privaten Prüfkopie zwischen Signer und Veröffentlichung | Digest-/Größenkontrollen nach beiden Tools und beim Speichern, exklusive Kopie. |
| Fünf Linux-Sandbox-Tests scheiterten durch fremde gleich-UID-Prozesse | `nproc=32` erst innerhalb des neuen User-Namespace; RAM/CPU/Timeout bleiben ab Start begrenzt. |
| Fünf veraltete statische Android-Tests | Erwartungen an Bestätigungsrouting, zusätzliche M4-Operationen und Projektprovenienz aktualisiert. |
| SDK-Unit-Test nahm nur drei alte Buildoperationen an | Test prüft jetzt die passenden festen Skripte je Operationsfamilie. |
| „Installier das und zeig mir den Akku“ umging Schreibverb-Guard | Fehlender nackter Imperativ in `BootstrapBrain` ergänzt; historische Form „installierte Apps“ bleibt lesend. |
| CCC1-Minimalfixture scheiterte am eager Android-Katalogimport | Lazy-Laden nur für das tatsächlich benutzte neue Profil. |
| Android-Plattform und rasches Launcher-Ende ließen Daemons laufen | Echte harmlose Child-/Orphan-Repros, jetzt geerbte Lauf-ID plus Startidentität auf Linux/Android. |
| ENOSPC im Log konnte unbestätigte Lease freigeben | Retain-Flag vor jedem weiteren I/O; Negativtest bestätigt null Freigaben. |
| Widersprüchliches Host/Termux-Etikett und erfundener Signer waren verifizierbar | Profil/Host konsistent und Fingerprint an gehashte Toolausgabe derselben APK gebunden. |
| Offener Build blockierte seine Recovery-Abfrage | Getrennter Status-Slot; Widgettest zuerst rot und danach grün, MAIN bleibt unverändert. |
| EACCES bei proc/stat galt irrtümlich als Prozessende | Nur verschwundene PIDs werden so behandelt; verweigerte Sicht verhindert positive Bestätigung. |

Der frische Reviewer untersuchte den Gesamt-Diff und prüfte die Korrekturen nach. Abschluss am Implementierungscommit:
keine weiteren konkreten Befunde. Details und echte Rot-/Grün-Logs: [Reviewbericht](evidence/sol-m4-20261023/review.md).

## 4. Wirklich gebaute APKs und unabhängige Prüfung

| Projekt | Paket | Bytes | SHA-256 |
|---|---|---:|---|
| Notizen | `at.heinz.continuum.m4notes` | 902441 | `196f40c756cd67c85486bb7fd43e25769d86b6911cb7edeb19cb041163e35d22` |
| Zähler | `at.heinz.continuum.m4counter` | 901641 | `da3a998c09fe65a4d07adc8e68433c4cc44627d07d5f1e5e5fb3a329422be573` |

Beide Version 0.1.0/code1, minSdk26/target36, keine deklarierten Berechtigungen.
Signierer-SHA256 **`bc6c439abe04c0338fc7219cc90b1b0234750e086539606d7a20aa348af9a78e`**:
separater Android-Debug-Schlüssel, APK-Schema v2, **nicht** der Besitzer-Schlüssel.
Der private Debug-Schlüssel wurde nicht archiviert oder veröffentlicht.

Nach dem eingefrorenen Implementierungscommit wurden beide Projekte durch den kanonischen Worker offline mit
`testDebugUnitTest`, `lintDebug`, `assembleDebug` und `--rerun-tasks` gebaut. Je 3/3 Tests, keine Fehler/Skips;
Lint je 0 Fehler und zwei echte Warnungen (`DataExtractionRules`, `MissingApplicationIcon`).
Die erneuten warmen Host-Laufzeiten inklusive Doctor waren 30019 ms / 27679 ms. Quellen und APK-Hashes entsprachen
den vorherigen Trials mit gleichem Schlüssel/Cache. Das ist kein kalter Smartphone-Benchmark und kein Beweis
bitgleicher Builds mit anderer Umgebung oder Signatur.

Nach dem Export wurden gespeicherte Quellen/Belege erneut mit CCC1 und die APK nochmals mit echten Signer-/aapt2-Tools
auf einer frischen privaten Kopie geprüft. Beide Ergebnisse: `CONFIRMED`, Signatur/Paket/Hash/Berechtigungen passend.
Receipts sind lokale Prüfberichte, keine kryptographische Ausführungsattestierung. Die unabhängigen Toolaufrufe
bleiben zur Nachprüfung erforderlich.

**[Dauerhaftes APK-/Quellen-/Belegbundle mit Links und Prüfanleitung](evidence/sol-m4-20261023/README.md).**
Es enthält 69 gehashte Nutzdateien plus `files.sha256`, beide APKs, Originalreceipts, Quellen, Reports und Rohlogs.
Die bytegenauen Gradle-Rohlogs enthalten teilweise originale nachgestellte Leerzeichen; sie wurden wegen ihrer
Receipt-Hashes nicht redaktionell bereinigt. Keine Caches, Keystores oder privaten Besitzerdaten enthalten.

## 5. Testbefehle und wirkliche Ergebnisse

Toolchain: Linux x64, Node **24.19.0**, vollständiges Temurin **JDK17.0.21+9**, Gradle **8.11.1**, Kotlin **2.4.0**,
AGP **8.10.0**, SDK **36**, build-tools **35.0.0/36.0.0**; APK-Prüftools aus 36.0.0.
Die offiziellen Toolarchive wurden gehasht; Werte stehen in `toolchain.json`. Kein Android-/LiteRT-Compile-Stub im SDK-Lauf.

| Lauf | Tatsächliches Ergebnis | Archiv |
|---|---|---|
| Ursprüngliche M4-Suite am Start-HEAD | 29/29 bestanden | `tests/m4-baseline.tap` |
| Erste volle Node-Baseline, noch ohne Kotlin | 1041 gesamt, 1025 bestanden, 10 fehlgeschlagen, 6 übersprungen | `tests/node-baseline.tap` |
| Node nach ersten Sicherheitsfixes | 1049/1049, 0 Fehler/Skips | `tests/node-after-safety.tap` |
| Erste echte SDK-Hauptbaseline | 861 Tests, 2 Fehler, 3 Skips | `tests/android-trusted-sdk.log` |
| Abschließende komplette Node-Suite | **1074/1074**, 0 Fehler/Skips | `tests/node-final.tap` |
| Backend nach `npm ci --ignore-scripts --no-audit --no-fund` | **60/60**, 0 Fehler/Skips | `tests/backend-final.tap` |
| Haupt-App, echtes SDK/JUnit/Robolectric/Lint | **873/873**, 0 Fehler/Skips, **BUILD SUCCESSFUL in 2m 38s** | `tests/android-final.log`, 178 XML-Reports |
| Davon echte Robolectric-Widgetfälle | **8**, darunter zwei Builder-Fälle | im JUnit-Archiv |
| Zusätzliche JVM-Checks-Programme | **4/4 Exitcode 0** auf echtem Gradle-Testclasspath | `tests/kotlin-real-sdk-checks.log` |
| Prozess-Negativsuite einschließlich Reviewfälle | **10/10** | `tests/process-final.tap` |
| Getrennte generierte Apps | je 3/3 Unit-Tests, Lint, echte APK | je `state/run-*/` |

Die Android-Testzahl wurde aus allen JUnit-XML-Berichten aggregiert, nicht aus einem geschätzten Gradle-Taskzähler.
Haupt-App-Lint ist gemäß bestehender Gradle-Konfiguration auf `NewApi` begrenzt; es ersetzt kein vollständiges Audit.
Kein instrumentierter Test oder APK-Start auf einem physischen Telefon wurde in dieser Sitzung ausgeführt.

Ausgeführte Hauptbefehle (Environmentpfade in der Bundle-Anleitung):

```sh
env -u NODE_TEST_CONTEXT node --test scripts/continuum-owned-app.test.mjs
env -u NODE_TEST_CONTEXT node --test --test-reporter=tap --test-concurrency=2 .github/continuum/*.test.mjs scripts/*.test.mjs
npm --prefix backend ci --ignore-scripts --no-audit --no-fund
env -u NODE_TEST_CONTEXT node --test --test-reporter=tap --test-concurrency=2 backend/test/*.test.js
node .github/continuum/contrastive-parity.mjs prepare /workspace/scratch/contrastive-parity
node .github/continuum/cre-parity.mjs prepare /workspace/scratch/cre-parity
export CONTINUUM_CONTRASTIVE_PARITY_DIR=/workspace/scratch/contrastive-parity
export CONTINUUM_CRE1_PARITY_DIR=/workspace/scratch/cre-parity
gradle --offline --no-daemon --max-workers=2 --console=plain -PcontinuumUiWidgetTests=true -Pkotlin.compiler.execution.strategy=in-process :app:testDebugUnitTest :app:lintDebug
node .github/continuum/contrastive-parity.mjs verify /workspace/scratch/contrastive-parity
node .github/continuum/cre-parity.mjs verify /workspace/scratch/cre-parity
node docs/evidence/sol-m4-20261023/verify-bundle.mjs
sha256sum -c docs/evidence/sol-m4-20261023/files.sha256
```

Wirkliche Schlussausgaben der Paritätsprüfung:

```text
SWB2 cross-runtime parity verified: 350 rounds, 700 updates, 4113 weights within 1e-8
Cross-runtime parity verified: 4113 weights, max error 1.3877787807814457e-17
CRE1 Kotlin/Node inference parity verified on 7 cases
CRE1 Kotlin/Node SGD parity verified over 526336 weights (max error 3.469446951953614e-18)
```

Die vier zusätzlichen Programme waren `ContinuumCognitiveSpineChecks`, `ContinuumCortexShadowChecks`,
`ContinuumCortexPipelineChecks`, `ContinuumResidualSpanCoreChecks`. Ihr echter Testclasspath wurde aus Gradles
`testDebugUnitTest.classpath` gelesen und jeweils per JDK17 mit `-Xmx2g`, Test-/Main-Ressourcen und Arbeitsordner
`app/` ausgeführt. Das vorhandene Stub-Fallbackskript war wegen des funktionierenden echten SDK nicht nötig.

Aufgelöste Umgebungsblocker: System-Java21 war nur eine JRE ohne `javac`; vollständiges JDK17 installiert.
Der SDK-/Maven-Zugang benötigte den vorhandenen Proxy und dessen systemvertrauenswürdige CA. Die isolierte
Gradle-Konfiguration nutzte `/etc/ssl/certs/java/cacerts`; TLS wurde nicht abgeschaltet. Diese maschinenabhängige
Konfiguration und Toolcaches sind nicht im Repository. Erste Einrichtung hatte Netzwerkzugriff, finale M4-Builds waren offline.

## 6. Forschung, Daten, Holdouts und Entscheidungen

Die neuen überprüften Hypothesen betrafen konkrete M4-Engpässe: vollständige Quellidentität, frische Buildbelege,
Lebenszyklus/Lease und die Übertragung eines begrenzten Plans auf ein zweites Projekt. Für die Sicherheitsfehler
wurden zunächst fehlschlagende Fälle ausgeführt; der unabhängige Review lieferte zusätzliche Gegenbeispiele.
Die Implementierung wurde vor der finalen APK-Messung eingefroren. Wiederholte Regressionen dienten der Fehlersuche,
nicht der Auswahl eines angeblich auf blindem TEST überlegenen Sprachmodells.

**Keine neuen Trainingsdaten, Gewichte, Dialog-Holdouts oder neuronalen Qualitätsvergleiche in dieser Runde.**
Unit-/Negativfixtures und Robolectric-Fälle sind öffentlich einsehbare Entwicklungsprüfungen, kein verblindeter S1-Korpus.
Die bereits vorhandene Residual-Span-DEV-Demo (2/5 → 3/5) lief als Regression mit; sie bleibt ausdrücklich DEV,
einschließlich ihrer beiden falschen ABSTAIN-Fälle. Daraus folgt keine neue natürliche Dialogfähigkeit.

Opus' Vorregistrierungen und Ergebnisse unter `docs/research/` wurden gelesen und unverändert erhalten:
LCT1, WSC1, TTT1, CDX1 bleiben Forschungs-/Testmodule. Strukturierte Repräsentationen werden nicht durch
unbelegte Fast-Weight-Verfahren ersetzt. I1/I2 sind keine hier neu nachgewiesene produktive Dialogentscheidung.
EXP-00/08/09 wurden mit dem aktuellen Host-Nachweis ergänzt; EXP-13 und bestehende Forschung blieben erhalten.

S1–S5 wurden nicht als neue Experimente begonnen und erhalten keine erfundenen Messwerte:

- **S1:** Nächster echter Sprachvergleich muss den heutigen Kern gegen eine begrenzte Kontextroutine auf
  getrennten Train/DEV und vor dem Freeze versiegelten, ungesehenen Mehrturn-Dialogen vergleichen. Familien:
  Tippfehler/österreichische Formen, Referenzen, Zeit, Negation/Bedingung, Themenwechsel, Ambiguität, Korrektur.
  Vorab Intention/Entität/Rückfrage/Behauptung/Kontextverlust, Latenz und Ressourcen definieren; finale TEST-Messung einmalig.
- **S2:** `ContinuumMemoryDialogue` nutzt derzeit acht begrenzte Turns, Topic-Heuristik und aktuelle nicht widerrufene
  Ledgerbelege. TTT1 verarbeitet strukturierte Entitäten; das ist noch keine sichere Pronomenauflösung aus freien Texten.
  Neue Referenzen brauchen Entitäts-/Quellenversion und Widerrufstests, bevor WSC1 Ergebnisse wiederverwenden darf.
- **S3:** Reparaturerfahrung nur bei passendem Basiscommit, Dateihashes, Diagnose und Prüfprofil anbieten;
  alte Patches weiterhin durch denselben CCC1-Kritiker, echte Tests und unabhängige Prüfung schicken. Keine neue Pipeline.
- **S4:** LCT1-Codingplaner nur mit unabhängigen Fällen gegen die bestehende feste Aufgabenfolge untersuchen.
  Aktuell war Buildverifikation der belegte Engpass; kein künstlicher Planer-Gewinn behauptet.
- **S5:** Vorhanden sind `LocalModelHost`, `LiteRtLmLocalModelRuntime`, `AiContextCompiler` und
  `createLocalBrainCandidateProvider` im bestehenden eingeschränkten CCC1-Adapter. Zuerst diese Schnittstellen erweitern.
  Kein neues Modell heruntergeladen/ausgeführt, keine GGUF-/LiteRT-Latenz oder Hardwareeignung gemessen.
  Modellbytes plus KV-/Laufzeitspeicher müssen getrennt auf dem tatsächlichen Gerät gemessen werden.

## 7. Dialog und 30-Sekunden-Üben: abgegrenzte Anschlussarbeit

Meilenstein C ist **nicht** neu abgeschlossen. Die vorhandenen Fähigkeitsantworten, SWB1/CDX1/MemoryDialogue,
Kontextcompiler und Runtime wurden untersucht und nicht vorschnell ersetzt. „Was kannst du alles?“ hatte bereits
einen Fähigkeitsdialog; diese Runde verbessert dessen allgemeines Sprachverständnis nicht. „I hab den Text verlorn…“
hat weiterhin keinen hier unabhängig nachgewiesenen vollständigen Mehrturn-Reparaturdialog.
Der nackte Schreibimperativ wird jetzt zuverlässiger gesperrt; unklarer Übungstext darf daraus keine Geräteaktion erhalten.

Die vier Scheduler wurden untersucht: `NeuralPracticeScheduler` 15 Minuten, `ContinuumPublicStudyScheduler`
15 Minuten, `ContinuumDeviceTeamleaderScheduler` eine Stunde, `ContinuumAutonomousScheduler` 24 Stunden.
Das sind Betriebssystemgelegenheiten, keine garantierten 30-Sekunden-Takte. Der vorhandene Neural-Practice-Kern
trainiert den 273-Gewichte-Klassifikator, serialisiert mit AtomicBoolean, prüft Abbruch, speichert per compare-and-save
und wartet nach drei erfolglosen Zyklen auf neue bestätigte Beispiele. Mehr Übungen allein machen ihn nicht zu einem Kontextmodell.

**D ist nicht implementiert.** Konkreter nächster isolierter Entwurf: opt-in UI-Controller mit
`OFF/RUNNING/WAITING/PAUSED/COMPLETE`, Generations-ID, monotonem Takt, endlicher Maximalzahl von Fällen und
nur einem aktiven Runner. `onPause`/Stopp annulliert den Runner; Wiederaufnahme ist sichtbar. Zwischen Läufen
mindestens ungefähr 30 Sekunden, keine Nachholstürme. Bestehende Ressourcenbudgets/Temperatur-/Akkuprüfungen
vor jedem Fall verwenden; vorhandene periodische Jobs bleiben getrennt.

Versionierter privater Fortschritt muss Suite-/Split-ID, verbrauchte Fall-IDs, Ergebnis und Modellhash speichern.
Übungsevaluator hat keinerlei Gerätewerkzeuge, Mikrofon oder Netzwerk. Anfangs kann er Wissenslücken protokollieren;
Gewichtslernen erst bei einer geeigneten trainierbaren Komponente, unabhängigem Holdout und geprüfter
Snapshot-/Rollback-/compare-and-save-Promotion. Bereits trainierte Beispiele dürfen keine Lernfortschrittsmetrik sein.
Diese Skizze ist Anschlussarbeit, kein vorhandener Android-Button oder getesteter Zustandsautomat.

## 8. Offene Risiken und Geräteprüfungen

1. Kein aktueller Fold7-App→Termux→M4-Build→App-Abschluss. Historische Besitzerberichte bleiben historisch.
2. Keine APK dieser Runde auf einem Telefon gestartet/installiert: Navigation, echte Speicherung, Prozess-Tod,
   Ansichtwechsel/Falten/Rotation, IME, Warmstart und Hersteller-Backupverhalten sind offen.
3. Termux `/proc`/SELinux und echtes Gradle-Abbruchverhalten müssen am Gerät geprüft werden. Bei fehlender Sicht
   bleibt die Lease gesperrt; das ist sicher, kann aber mobile Builds blockieren. Vor manueller Freigabe Termux-Prozesse prüfen.
4. Verlorener/trunkierter Callback ist im Host-/UI-Test abgesichert, Android-Längen-/Transportgrenzen nicht real vermessen.
5. Heap-/Worker-/Zeit-/Dateigrenzen sind keine gesamte RAM-/CPU-/Akku-/Thermiksandbox. Keine Telefonverbrauchsmessung.
   Eine Worktree-Isolation ist keine OS-Sandbox. Freie untrusted Gradle-/LLM-Quellen bleiben deshalb gesperrt.
6. Lease serialisiert pro Projekt; zwei unterschiedliche Projektprozesse außerhalb der App sind kein globales Telefonbudget.
7. Bestehende v1-generierte Projektordner werden nie still migriert oder überschrieben. Bei anderem Quellenmanifest:
   erst Besitzerdateien sichern/prüfen, dann ausdrücklich getrennt neu erzeugen; keine automatische Löschanweisung.
8. Lokale Receipts sind nicht gegen einen vollständig kompromittierten gleichberechtigten Benutzer/Host attestiert.
   Rehashing und gespeicherte Toolbelege verhindern keine absichtliche Neufälschung aller Belege. Erneute echte Tools nötig.
9. Offizielle Buildabhängigkeiten müssen beim ersten Einsatz installiert/cached sein. Gradle8.11.1 funktioniert mit
   Kotlin2.4 hier; die ausgegebene zukünftige Kotlin2.5-Warnung erfordert später eine getrennt geprüfte Toolchain-Aktualisierung.
10. Issue #76 bleibt trotz vorhandener Reparatur auf dem Besitzergerät unbestätigt. Kein neuer Forschungscode wurde in den produktiven Chat promoviert.

## 9. Besitzer-App, nächster Kandidat und Rollback

Installierte Besitzer-App bleibt **0.3.17/code18**, `at.heinz.deviceai.mentorpaired`.
Vorgesehene nächste Besitzer-Version bleibt **0.3.18/code19**. Bestehender Fingerprint:
`2b05fa9de378fb6c6c5376143774a573897dc9c9151305f1c44010275c76a9cc`.
Der Schlüssel wurde weder gelesen noch ersetzt; der Signier-/Publikationsdienst wurde nicht aufgerufen.

Die bestehenden v19-Signier-/Rollback-Gates wurden nicht gelockert. Quellvergleich am Endstand:
**14/22 vorhandene Blob-Attestierungen passen, acht absichtlich geänderte Dateien unterscheiden sich**.
Die alte Releaseattestierung darf diesen neuen Stand deshalb noch nicht bauen. Neue Plan-/Verifier-/Prozessdateien
müssen in einen erneuten Review der Release-Quellmenge aufgenommen werden. Statische Release-Tests sind grün;
ein privater signierter Besitzer-Build oder das reale Rollback-Artefakt wurden hier nicht als geprüft ausgegeben.

Empfehlung: Die beiden beigefügten M4-Debug-APKs dienen als getrennte Prüfartefakte. Für den Besitzer-Nachfolger
erst einen geprüften 0.3.18/code19-Integrations-SHA mit unveränderter Premium-UI festlegen, dessen Pins prüfen,
die tatsächliche signierte 0.3.17 samt SHA/Paket/code18/Zertifikat sichern und die ausdrückliche Freigabe einholen.
Dann ausschließlich bestehenden vertrauenswürdigen Signierdienst/Schlüssel verwenden. Kein Deinstallieren;
In-place-Update und Datenerhalt anschließend auf Fold7 prüfen. Kein APK dieser Sitzung ist ein Besitzer-Update.

Code-Rollback: einzelne eigene Commits in umgekehrter Abhängigkeit mit `git revert` auf einem neuen Reviewzweig
zurücknehmen; kein Reset/Force-Push fremder Zweige. Oder vom unveränderten Startanker einen Vergleichsworktree
erstellen. Projekt-/Receiptdateien bleiben als Beweise erhalten; nicht automatisch bereinigen.
Eine Installation fand nicht statt, daher ist kein Geräte-Rollback dieser Sitzung erforderlich.

## 10. Exakte nächste Integration und größter nächster Durchbruch

1. Diese Übergabe und Bundle-Anleitung lesen; `git fetch`, tatsächliche Remote-HEADs und sauberen Worktree prüfen.
   PR #108 bleibt auf PR #107 aufgebaut; #106/#99 nicht blind erneut integrieren oder mergen.
2. `verify-bundle.mjs` mit echten SDK-Tools sowie `files.sha256` prüfen. Vor Integrations-/Releaseänderungen die
   echte Gesamtregression auf dem resultierenden SHA wiederholen; Bibliotheks-/Versions- oder UI-Änderungen nicht mit alten Zahlen freigeben.
3. Aktuellen Termux-Checkout und native Tools auf Fold7 prüfen. Besitzer-App mit neuer Builder-Auswahl erst nach
   eigenständigem Release-/Rollback-/Signierreview und Besitzerfreigabe bereitstellen.
4. Auf Fold7 Notizen wählen → bestätigen → erzeugen → Quellen prüfen → testen/bauen → Rückkanal abwarten →
   Paket/Signatur/SHA/Reports unabhängig prüfen. Mit Zähler wiederholen. Noch nicht automatisch installieren.
5. Timeout/Abbruch, fehlender Speicher, Symlink-/Quellenänderung und verlorenem Callback gezielt nachstellen.
   Recovery darf ausstehende MAIN-Identität nicht löschen; fehlende Prozesssicht darf keine Lease freigeben.
6. Nach gesonderter Installationsentscheidung die getrennte M4-App starten und Navigation/Speicherung/Lifecycle
   auf dem Gerät prüfen. Owner-UI/Daten/Fold/IME und Issue #76 separat abnehmen.
7. Erst danach S1/S2 vorregistrieren, Daten sauber trennen und eine prüfbare Dialogkomponente unabhängig messen.
   D als sichtbaren endlichen Vordergrundcontroller implementieren; S3/S5 an bestehende CCC1-/Modelladapter anbinden.

Der Coding-Agent kann jetzt **zwei unterschiedliche begrenzte Android-Projekte aus geprüften Plänen erzeugen,
real bauen und mit überprüfbaren Artefakten belegen**. Er kann noch keine beliebigen Apps aus natürlicher Sprache entwickeln.
Der nächste große Durchbruch ist die aktuelle vollständige Kette auf dem Besitzertelefon und anschließend ein
auf ungesehenen Dialogen gemessener Kontextkern. Weitere Übungszahlen oder isolierte neuronale Demos ersetzen das nicht.
