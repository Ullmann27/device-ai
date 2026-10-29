# Continuum — zentrale technische Übergabe an GPT-6

Stand 2026-10-29. **Host-verifiziert; keine neue APK oder Geräte-/Gemma-Inferenzprüfung.** Offizielles privates Repository: `Ullmann27/device-ai`.

## Installiert versus Draft

| Stand | Exakte Quelle / Branch | Einordnung |
|---|---|---|
| Besitzerangabe Fold7 | PR228 `b2b2277162e346688da3eef551414108615f3cad`, `review/ewm2-revocation-race-20261029` | Installiert: Continuum Lokal0.3.27/code28, `at.heinz.deviceai.continuumlocal`; Gemma E4B geladen, EWM2 AN, 548 Quellen. 1218 Android-/218 Node-Tests laut Besitzer. Kein Fernzugriff dieser Sitzung. |
| Gedächtnis-Vorläufer | PR229 `a16e45c8bb43a38143ed729b5e0501337adc8eb5`, `review/continuum-memory-attribute-guard-20261029` | Auf PR228; Geburtstagsschutz. Nicht als installiert belegt. |
| Diese Reparaturen | [Draft PR230](https://github.com/Ullmann27/device-ai/pull/230), `sol/continuum-gpt6-closeout-20261029`; geprüfter Code `07f3431309861562b53ca28a2ae1b94309d68739` | Auf PR229; vollständige Testergebnisse unten. Nachfolgender Abschlusscommit enthält nur Dokumentation/Rohbelege; endgültiger HEAD im PR bzw. `git rev-parse HEAD`. |
| Anschlussrunde | [Draft PR237](https://github.com/Ullmann27/device-ai/pull/237), `sol/continuum-dialog-integration-20261029` | Auf PR230; PR233/234 werden gesondert geprüft. Kein Ergebnis vorweggenommen. |
| Opus-Forschung | PR224 `d49fbfea587ac18bead8f5ad30f244ca4250d796`, `opus/continuum-brain-research-20261029` | Keine Forschungsänderung und kein Zugriff auf versiegelte TEST4/TEST5. |

PR228 basiert auf PR227 `f5881a7b5ba6894f36df38eb28f897cb4da1cda5` (`opus/continuum-ewm2-integration-20261029`). Laufzeitreparaturen aus PR223, Code `c8ea465f3ff6cd87feb763df7f83d8dfc8d70a6a`, sind bereits Vorfahren des installierten Codes. Deren Rohlog-Commit `de8f759b1be9f56d1c7d4efc3b52cf8a07ee82cb` bleibt separat zugänglich.

## Implementiert und gemessen

- **Gedächtnis:** `app/src/main/java/at/heinz/deviceai/learning/ContinuumMemoryDialogue.kt` und entsprechender Test. `Nela wohnt seit ihrer Geburt in Graz.` wurde vor der Reparatur fälschlich als Geburtstagsevidenz verwendet; `Wo wohnt Nela, unabhängig von ihrem Geburtstag?` verlor hingegen einen gültigen Wohnortbeleg. Zwei neue öffentliche synthetische Tests scheiterten real, bestehen jetzt. Bestehende Tests unverändert. Der kleine lexikalische Filter ist kein allgemeiner semantischer Beweis; zusammengesetzte Fragen/andere Attribute bleiben offen.
- **Release:** `scripts/continuum-source-state.sh`, `.test.mjs`, `continuum-fold7-standalone-apk.sh`, `continuum-fusion-full-regression.sh`. QA verwendet denselben ausgewählten Gradle-/AAPT2-Pfad wie der Releaseweg. Erneute HEAD-/Arbeitsbaumprüfung nach QA, vor Passwortabfrage und vor Schlüsselzugriff. Git-Fehler verweigern mit festem Fehlercode ohne Diff-/Geheimnisausgabe. Kontrollpunkte sind keine atomare Sperre gegen jeden möglichen parallelen Dateizugriff.
- **Worker:** `scripts/continuum-local-development-worker.mjs`, `.github/continuum/local-worker-process.test.mjs`. Erster fehlgeschlagener Prüfzyklus beendet jetzt die Schleife mit Fehler. Vorher reproduziert: FAILED → automatischer weiterer PASS; nachher nur FAILED. Expliziter Neustart nach Reparatur besteht. STOP, Lease, Prozessgruppen, Budgets, Zweigschutz und CCC1 bleiben erhalten. Der Health-Worker ist keine nachgewiesene generative Entwicklungs-KI.

Commits: `e90e1e6d52e0ea1522fbced23d5d83b60c15579a` (rote Gedächtnistests), `d086ad9d8cbd3cdec9feffce235888f70cbe6d04` (Gedächtnis), `1f4e117893b497f3f548483f483259fede8a337e` (Release), `07f3431309861562b53ca28a2ae1b94309d68739` (Worker). EXP00/01/06/12; bestehende Architektur, keine neuen Gewichte.

## Reale Tests und Rohbelege

[`../evidence/gpt6-closeout-20261029`](../evidence/gpt6-closeout-20261029) enthält Originalfehler, sämtliche JUnit-XML, Gradle-/Node-Logs, Befehle, Exitcodes und SHA-256-Manifest. Nur öffentliche Testfixtures, keine Besitzerdateien/Modelle/Schlüssel.

| Lauf | Status / Exit | Ergebnis |
|---|---|---|
| Ausgangsregression `e90e1e6d` | FAIL /1 | 1221 JUnit,232 Suiten, genau2 neue Gedächtnisfehler,0errors/0skips;218 Node PASS /0 |
| Worker-Negativtest gegen ursprünglichen Worker | FAIL /1 | Unerlaubter automatischer Folgezyklus; `red-worker.log` |
| Gezielte Release-/Worker-Prüfung | PASS /0 | 39 Node,0fail/0skip; reale Git-Fixtures/Kindprozesse plus strukturelle Aufrufprüfungen |
| Voller Schlusslauf `07f34313` | **PASS /0** | **1221 JUnit/232 Suiten,226 Node,0fail/0skip**, Kotlin/Java kompiliert, bestehendes NewApi-Lint und CRE1/SWB-Parität bestanden |
| Bash-Syntax / `git diff --check` | PASS /0 | Drei geänderte Shellskripte und Patch |
| Fold7 ARM64, native Gemma, Signierung/Installation | NOT_RUN | Gerät/Schlüssel hier nicht zugänglich; keine Ersatzsignatur/kein Ersatzmodell |

Vorhandene Toolchain: Linux x86_64, Temurin JDK17.0.21+9, Gradle8.11.1, Android SDK36, AAPT2 2.19-11948202 (build-tools35.0.0), Node24.19.0, Git2.52.0. Kein Versionswechsel/Download. **Lint ist gemäß bestehender Konfiguration NewApi-only**, kein umfassender zusätzlicher Lint-Nachweis.

Ausgeführt: `bash scripts/continuum-fusion-full-regression.sh <exakter-HEAD>`, mit entferntem Local-/Mentor-Signing-Environment und `CONTINUUM_FUSION_REGRESSION_OUTPUT` außerhalb Checkout. Vollständige Befehle stehen in `red-memory/command.txt` und `green-final/command.txt`. Der Runner führt `:app:testDebugUnitTest :app:lintDebug` ohne Testfilter aus, setzt QA-Modus/AAPT2/SDK und erzeugt lokale Paritätsfixtures; auf Termux zusätzlich dessen dokumentierte native Pfade und AIDL-Flag.

## Andere geprüfte PR-Stände

- PR232 `fe871cb685bd3cd42347ce80ca2373319c11da96`, `research/continuum-language-core-update-contract-20261029`: reine Auswahl-/Update-Metadatenverträge, keine neue Inferenz. Nicht übernommen.
- PR233 `316c55543f2e1e84a52c5ace437c572791e1936d`, `fix/continuum-self-awareness-neuron-command-20261029`: Dialogzuordnung, Tutor-Hinweise, bestätigte Neuron-Aliase. In237 geprüft.
- PR234 `d5af54e18a2694de78b0a125c5f83e73da930cdf`, `fix/continuum-dashboard-evidence-labels-20261029`: Statusprojektion ohne private Inhalte; echte Trainingsentscheidung und SWB1-Beschriftung. In237 geprüft.
- PR236 `6bc54545adcef83e801003f5559d377f3a20d9ba`, `feature/continuum-p0-inapp-owner-updater-20261029`: Update-UI/Downloader/PackageInstaller und unsigned Render-Pfad. Separat. Codeprüfung: **instanzlokale** Aktivsperre, aber gemeinsames Staging-Verzeichnis; Activity-Neustart kann mehrere Instanzen erzeugen. Dauerhafte Sessionzuordnung/Wiederanlauf und Zweitupdate nicht bewiesen.
- Issue235-Kommentare berichten erfolgreichen Render-QA-Build `e0f44a2b7f77d38f7a731ec4f45e8622db683578`, aber **Preview-Debug-Paket**, kein Haupt-App-Update. Die alte Actions-/Render-Blockade ist daher nicht pauschal aktuell. Hier kein Cloud-Build/Deploy oder Release ausgelöst.

## Bestehende Verbindungen und Grenzen

`AgentActivity.runContinuumDialogue` → `ContinuumCognitiveRuntime` / `CognitiveDecisionBridge` → `ContinuumBrainBridge` (EWM2) → `LocalFirstPolicy` → `runLocal` / `BoundedAgentOrchestrator` → `LiteRtLmLocalModelRuntime`. Exakter Gedächtnisabruf bleibt von unbestätigter Modellsynthese getrennt. CRE1/CDX/SWB-Forschung wird hier nicht aktiviert/trainiert.

Allgemeine Recherche besteht bereits: `cognitive/GeneralWebDiscoveryProvider.kt` (Bing-RSS, sichtbarer Wikipedia-Fallback), `ResearchEngine.kt`, `ApprovedWebResearchToolRunner.kt`, `ApprovedWebToolGate.kt`. `web_search`/`web_read` sind in AgentActivity angeschlossen, mit DNS/TLS-/Größen-/Widerrufsgrenzen. Keine Internetfreigabe gesetzt. TTS/RecognizerIntent bestehen; Offline-Spracherkennung hängt vom Android-Anbieter ab. Netz-/Sprach-/Hardwarequalität ist separat zu prüfen.

Gemma-Nachweis auf Fold7: öffentliche normale Frage, ausgewählter Gemma-Hash, CHAT-Profil mit FIRST_NATIVE_TEXT_CALLBACK, native/runtime COMPLETED, Textmenge und tatsächliche Antwort gemeinsam prüfen. `localmodel/LocalInferenceProfile.kt` und `cognitive/CognitiveTurnTrace.kt` sind bereits angeschlossen. Modell geladen oder Regeltext reicht nicht. Native Prefillzeit ist nicht exponiert; Heapwerte sind nur JVM. PR221-Callback-Umbau nicht übernommen. Hier kein erreichbares Fold7 und keine Gemma-Datei; keine Qwen-Ersatzmessung.

Generation16 `at.heinz.deviceai.mentorpaired` bleibt unverändert. Das persönliche256-Einträge-Protokoll und alte Identität sind weiterhin nicht vollständig als gerettet belegt. Keine Besitzerdateien, Quellen, Gewichte, Berechtigungen oder Schlüssel angefasst. Originale Lokal-Zertifikats-SHA256: `6354e91dc0ca8df9173885f32402fd817f46e798f369206d4fd710ff3d03ee23`; historisches Mentor-Zertifikat ist anders. Nur der vorhandene Fold7-P12 darf später dieselbe Haupt-App signieren. Keine neue APK in dieser Runde.

## Unmittelbarer nächster Schritt

Auf dem autorisierten Fold7-Checkout des geprüften Code-Commits `07f3431309861562b53ca28a2ae1b94309d68739` die vollständige QA ausführen, ohne Signierung/Installation:

```bash
bash scripts/continuum-fusion-full-regression.sh 07f3431309861562b53ca28a2ae1b94309d68739
```

Für237 dessen gesonderten final geprüften HEAD verwenden. Rückfall ausschließlich per neuem Revert-Commit im Entwicklungsbranch, keine Daten-/Gewichts-Rücksetzung, Branchlöschung oder Main-Merge. Bestandene Tests belegen diese Fehlerkorrekturen, keine allgemeine Intelligenzsteigerung oder Langzeitstabilität.
