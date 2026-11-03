# SOL 6.1 MAX — CONTINUUM MASTER HANDOFF
**Datum:** 2026-11-03 · **Auftraggeber:** Besitzer von Device AI Continuum
**Repository:** https://github.com/Ullmann27/device-ai · ausschließlich dieses Projekt, nicht Lumo

## Direktive

Übernimm die technische Entwicklungsleitung und führe das bestehende, vollständig lokale Android-KI-Projekt **ohne erneuten Kontextverlust vom aktuellen Reparaturstand bis zur vereinbarten Android-first-Zielarchitektur** weiter. Das ist keine Einzelaufgabe und kein Auftrag zur bloßen Dokumentation. Implementiere, teste, repariere, integriere und sichere in GitHub. Arbeite mit eigenständiger Ursachenforschung, RED→GREEN, sachlichem Review, echten Belegen und wiederverwendbaren Modulen. Schreibe nie „fertig“, wenn nur Hosttests oder eine READY-Anzeige existieren.

**Führender Einstieg:** Draft PR [#364](https://github.com/Ullmann27/device-ai/pull/364), Branch `repair/continuum-shizuku-composer-20261103`, **vor Übergabe geprüfter HEAD `5ff4ad6a232faf6ae6420b58f96d6293bcc844d3`**. Vor jedem Write den tatsächlichen Remote-HEAD erneut lesen. Kein Force-Push auf gemeinsame Zweige. Für größere parallele Forschungsarbeit eigenen offen bearbeitbaren Branch und Draft-PR im selben Repository anlegen; keine konkurrierenden Implementierungen desselben Moduls.

### Quellenhierarchie und offene Parallelzweige

1. **Eigentümer-Gerätebefunde 2026-11-03:** installierte originalsignierte Continuum Lokal `0.3.37` / Code 38 auf Galaxy Z Fold7; In-App-Updatekanal nach Manifestkorrektur funktionsfähig. Echte UI-Fehler: `Hallo?` → irrelevante Erinnerung; `Was kannst du?` → Webrecherche; `Wie viel Speicher habe ich?` → Web statt Gerät; Shizuku „nicht verbunden“; bislang **keine nachgewiesene Gemma-Textgenerierung**. Diese realen Negativbefunde haben Vorrang vor kosmetischen Fortschrittszahlen.
2. **PR #364 (dieser Branch):** sieben Commits, 17 geänderte Dateien; Shizuku-Bind-/Berechtigungs-Timeouts und Recovery, lokale Chat-Intents, Neuron-Tutor-Grenze, Memory-Stopwords, Mehrzeilen-Editor und direkter Offline-LiteRT-Modelltest. Die Shizuku-Testsuite hat **11 definierte** Robolectric-Testfälle, nicht elf nachgewiesene erfolgreiche Läufe. `scripts/continuum-fold7-repair-qa.sh` ist ein neuer, SHA-gepinnter **noch nicht ausgeführter** Host-QA-Runner mit `-PcontinuumUiWidgetTests=true`.
3. **PR #363:** lokales Gesprächs-/Speicherrouting, Basis von PR #364, HEAD `7dacf0fb8f21e0dec9957fec23d8edb7feea3e77`.
4. **PR #361:** Sol61-JARVIS-Basis, HEAD `97a930f51b40b0d25571fa0068c4fe2eb6a35f1e`, getesteter Quellstand `48c0fb1f2b4db06619e170bf36f8d070e002eca4`. **Historische** 1.687 JVM + 1.969 Node PASS, Lint grün; diese Zahlen beweisen **nicht** den heutigen PR #364, nicht native Modellinferenz und nicht den Fold7-Zustand.
5. **PR #362:** paralleler Sol-Max-Autonomie-Forschungszweig, HEAD `df25b8f360c321054c3020949a219f90041f0d55`. Beinhaltet reparierte Bildschirm-/Toolfortsetzungen und RED→GREEN-Belege. Die vollständige erste Gesamtabnahme enthielt mindestens einen echten Android-Testfehler; keine pauschale GREEN-Behauptung. **Vor Integration zuerst Git-Diff, Abstammung, reale Tests, Überschneidungen mit PR #364 und Quell-Hashes prüfen; nicht blind überschreiben oder duplizieren.**
6. **PR #360:** große integrierte Architektur, noch Draft. Enthält frühere Gehirn-, GGUF-/Termux-, ToolGate-/Coding-/Forschungszweige. Änderungen daraus nicht nachträglich als bereits bewiesene Live-Funktionen umetikettieren.
7. **Original-Publisher:** bestehender Render-Service `srv-dbf1tr2vcj2c73ehpfog`, Host `continuum-owner-v23-release-preflight.onrender.com`; live 0.3.37/code38 mit zwölf Katalogfeldern, `channel:OWNER_RELEASE`, `approved:true`. Publisher-Reparatur-SHA `2eae6b53cbdd9b8b4d3c12f8d8a3c4ca770ea8f2`. Originalpaket `at.heinz.deviceai.continuumlocal`, Zertifikats-SHA-256 `6354e91dc0ca8df9173885f32402fd817f46e798f369206d4fd710ff3d03ee23`. Re-check vor jedem Release. **Code38-Release-Skripte nicht einfach als code39-fähig behaupten**.
8. **Dauerhafter Plan:** [Issue #41](https://github.com/Ullmann27/device-ai/issues/41), `AGENTS.md`, `docs/CONTINUUM-EXPERIMENTVERTRAG.md`, `docs/CONTINUUM-EXPERIMENTE.json`, `docs/research/omega/SOL-MAX-AUTONOMY-HANDOFF-20261103.md`, `docs/research/omega/SOL61-JARVIS-INTEGRATION-HANDOFF-20261102.md`, `docs/superpowers/plans/2026-11-03-fold7-shizuku-composer.md` und relevante Review-Threads. **Achtung:** In `AGENTS.md` stehen historisch ältere Release-Freeze- und Code31-Stände; nach Datum, Git-HEAD und aktuellen Gerätebelegen auflösen, nicht blind rückwärtsgehen. Nicht Lumo einbeziehen.

## Phasen und verbindliche Abschlussbedingungen

### P0 — Fehlerfreie Ausgangsbasis und echte QA (zuerst)
- Git-Stand/Dateien/Abhängigkeiten, offenen PR-Graph, diff-bezogene Reviews und Render-Zustand exakt prüfen; kein fremder Branch-Reset.
- Den bestehenden QA-Runner `scripts/continuum-fold7-repair-qa.sh` auf exakt neuestem Reparatur-HEAD in einem real ausgestatteten SDK36/JDK17/Gradle8.11.1-Workspace ausführen. Prüfe den Runner selbst auf Bash-/Gradle-Konfigurationsfehler. Android `:app:compileDebugKotlin`, `:app:testDebugUnitTest`, `:app:lintDebug`; **mit UI-SourceSet/Robolectric**, JUnit-XML und echter Ausführungszahl aller elf Shizuku-Fälle, Editor-, Speicher-, Router-Tests sowie Node. Nicht nur source-grep oder simulierte Testläufe. Bisherige JDK21-only LiteRT-Hosttests gelten unter JDK17 als **SKIP / NOT_VERIFIED**: gesondert auf echtem JDK21 verifizieren, sofern praktikabel.
- Jeder RED-Fund: kleinster reproduzierbarer Fehler, isolierte Korrektur, echter GREEN-Re-Run, Regression, Commit/Push + Rohlogs. QA-Erfolg an finaler, unveränderter SHA nachweisen.
- Bereits vorhandene Safety- und Regressionsgates nicht für eine grüne Zahl deaktivieren.

### P1 — Wirklich nutzbare Android-App und originaler Updatekanal
- Eigentümer-UI-Fehler `Hallo?`, `Was kannst du?`, `Wie viel Speicher habe ich?`, Editor-Scroll/Fold-Resize, unpassende Web-/Gedächtnisquellen und Shizuku-Bereitstellung im echten Ablauf gezielt abnehmen. `get_storage_info` muss **ohne Shizuku** lokale Werte liefern.
- Shizuku: App installiert und Dienst läuft? Berechtigung? Binder? tatsächlicher ShellUserService? Timeout/Stop/Retry? Fehler nach Zustand trennen, nicht alle Ursachen als „nicht verbunden“ verbergen. Keine Privilegienerschleichung, keine unbestätigte Shell-Wirkung.
- Für neue Owner-APK **höherer versionCode als 38**, korrekte Originalsignatur/Paketkennung, gleiche App-Daten, kompatibler zwölfteiliger Katalog, HTTPS/SHA256/Dateigröße, ausdrücklich freigegebener Publisher-Commit. Android-PackageInstaller darf Besitzerbestätigung verlangen. Originalsignier-P12 ausschließlich auf vorhandenem vertrauenswürdigen Publisher, niemals im Code/PR/QA. Bei fehlender QA/Geräteabnahme Kandidat klar als ungetestet behandeln und **nicht** freigeben.
- Ziel: zuverlässiger, wiederholbar verwendbarer „Updates prüfen und laden“-Kanal für die nächste Version und künftige Versionen, keine One-off-Manifests, keine Deinstallation.

### P2 — Echte lokale Modellintelligenz (vor Autonomieclaims)
- Bereits vorhandenes Gemma4/LiteRT-LM- und optionales GGUF-/Termux-System prüfen, **keine erfundenen API-/Modelleigenschaften oder bezahlte KI-Abhängigkeiten**. Die Aktion `GEMMA-DIREKTTEST · OFFLINE` aus PR364 soll einen tatsächlich erzeugten nichtleeren Antworttext, Modellidentität, Dauer, Fehler und STOP zuverlässig ausgeben – niemals nur READY.
- Native Kontext-/Tokenbegrenzung, tatsächlicher Modellpfad/Dateiintegrität, Geräte-RAM/Thermik, Engine-/Conversation-Lifecycle, Cancel/Reload, Tool-Response-Übergabe, Gedächtnis und Intent-Router untersuchen. Erst wenn echte direkte Inferenz belegt ist, behaupten, dass das Gehirn antworten kann.
- Mehrstufige Dialoge mit sauberen lokalen/externalen Vertrauensgrenzen; Webdaten nicht als Systembefehle; keine Zusammenfassung vollständiger Toolbelege zu irreführenden Textschnipseln; keine Gedächtnis-Promotion durch bloße Modellbehauptung.
- Fehlt Fold7-Zugriff: Host-Regressionen und klare, im UI erreichbare Gerätediagnose liefern; `DEVICE_NOT_RUN` statt Fake-GREEN.

### P3 — OPERATION FEUERPROBE: echter autonomer CCC1-Coding-Agent
- Bestehende CCC1/M8-Entwicklung und einen kanonischen Coding-/Repair-Worker verwenden, Android als **primären** Entwicklungs-/Build-Rechner (Issue #41: EXP-00/01/02/08/09/11); PC optionaler Worker, niemals Pflicht.
- Reale Arbeitsfolge: freigegebener Auftrag → lokales Modell erstellt/überprüft Plan → isolierter Git-Worktree/Sandbox → realer reproduzierbarer RED-Test → modellgenerierter gezielter Patch → tatsächlich unabhängiger GREEN-Test → Quittung, Diff, SHA, Owner-Review. Fake/Scripted Stub getrennt von Modellautonomie benennen. Keine Writes nach `main`/Production.
- Crash/Restart/Timeout/Cancel, UNKNOWN nach Dispatch und **ganze Aufgabe** gegen neue Call-ID-Replays absichern. ToolGate, Audit, Grenzen und unveränderten Kern-Reparaturmechanismus beachten. Kein autonomes Installieren/Signieren ohne Freigabe.

### P4 — Android-Werkzeuge, Sprache und Bedienung
- ToolRegistry → ToolGate → richtige Android-API/Shizuku-berechtigter UserService → verifizierter Toolbeleg; zuerst read-only Dateien/Speicher/Apps, dann owner-bestätigte Änderungen. Nachrichten nur als Entwurf ohne Versandfreigabe, Bildschirm-/Privatdaten geschützt.
- Sprache offline soweit echte Schnittstellen vorhanden; Dialogkorrekturen, Fokus, Fold7-Faltzustand, Stop und Langzeitnutzung.
- Eine konsistente UI mit Arbeitsstatus, Modellstatus, Besitzerfreigaben, Quittungen und fehlerfestem Chat. 3D-Avatar erst nach tatsächlich benutzbarem Kern priorisieren; spätere hochwertige Animation über vorhandenen Renderer, keine reine Scheinbewegung.

### P5 — Autonomer lokaler Worker, Gedächtnis und echtes Lernen
- Bereits vorhandene Scheduler/Backlog/Worker-, SWB1/CRE1/CDX/Neuron-Komponenten **in den bestehenden Gehirn-Aufrufpfad** integrieren; keine zweite Datenbank, zweite KI oder konkurrierende Worker.
- Energie-/RAM-/Thermal-/Doze-Grenzen, dauerhafte Checkpoints, stunden-/ereignisgebundene lokale Workerstufen, Leases/Idempotenz/Recovery. **Kein unverifizierter Anspruch auf 24/7 unter Android.**
- Erfolg nur durch unabhängige Tests, Gegenbeispiele, Holdouts, erlaubte Daten und reversible Lernkandidaten. Ungeprüfte Webquellen, private Besitzerfakten und trainierte Gewichte strikt trennen.

### P6 — Zurück zur langfristigen Gesamtarchitektur
- Nach stabilen P0–P5 sukzessive Issue #41 und EXP-00…12, Brain/Synapse/Quantumflow/Fusion/Atlas/Research-Verbindungen und wissenschaftliche Experimente nach Architekturverträglichkeit priorisieren. Neue Ideen nur als messbare, deaktivierbare Hypothesen mit realem Test. Externe Open-Source-Forschung nur lizenz- und SHA-geprüft integrieren.
- Größere Qualitätssprünge: reales On-Device-Build/Signing unter Android mit Ownerfreigabe, robuste Tool-/Gedächtnisverkabelung, Kompetenzmessung unbekannter Codingaufgaben, optionaler Host-/Plattformadapter, reaktive Avatar-/UI-Erfahrung. Kein „AGI-Prozent“ ohne messbaren Maßstab.

## Arbeits- und Reportingvertrag

- Arbeite pro Meilenstein eigenständig weiter, bis ein nachweisbarer Endpunkt oder ein **konkreter, externer Hardware-/Berechtigungsblocker** erreicht ist. Stelle keine unnötigen Rückfragen und erfinde keine Gerätetests.
- Schreibe nach jeder wesentlichen Änderung direkt ins offizielle Repository: offener Draft-PR, kleiner sauberer Commit/Push, Branch/Parent/HEAD, Testbefehl, Exit-Code, Logs, Screenshots/Quittungen, bewiesener Effekt, Grenzen. Für parallele Branches expliziten Integrationsplan samt Konfliktprüfung führen.
- Normierte Zustände: `SOURCE_ONLY`, `HOST_VERIFIED`, `DEVICE_VERIFIED`, `RELEASE_VERIFIED`, `BLOCKED`; nicht vermischen.
- Systematische unabhängige Gegenprüfung und RED→GREEN, inklusive Negativ- und Sicherheitsfällen. Keine Tests abschalten, um grün zu werden. Frühe Hosttests nicht als heutige Android-Abnahme zählen.
- Nie automatische PR-Merge nach `main`, Force-Push, Repo-Privatheit aufheben, Owner-Signaturschlüssel veröffentlichen, private Daten löschen, schreibende Android-/Shell-Aktionen ohne ToolGate-Freigabe, externe kostenpflichtige KI-API als Pflicht oder parallele autonome Kernarchitektur.
- GitHub Actions gelten gemäß aktueller `AGENTS.md`-Warnung nicht als zuverlässiger APK-/QA-Weg; verwende eine nachweislich funktionierende lokale oder isolierte Render-Android-QA-Umgebung. Render-**Originalsignierung** getrennt vom unsignierten QA-Host halten.
- Bei nicht erreichbarem Fold7 realistische Diagnoseanleitung im UI und exakte ausstehende Gerätetests liefern; nicht behaupten, man könne remote auf das Smartphone zugreifen.
- **Keine reine Text-Antwort als Arbeitsergebnis.** Wenigstens ein prüfbarer GitHub-Checkpoint mit tatsächlicher Reparatur/Tests oder offen ausgewiesenen, reproduzierten Blockern. Nächsten Agenten mit versioniertem Handoff abholen. Arbeit nicht nach einer grünen Hostzahl oder neuer APK abbrechen: langfristige autonome Fähigkeiten weiterentwickeln, ohne Freigabegrenzen zu brechen.

## Erstes Vorgehen beim Aufruf

1. Remote-HEAD dieses PR und PR #362/#363/#361 plus Publisher live lesen.
2. QA-Skript und tatsächliche Gradle-Optionen **prüfen und ausführen**; defekte Testharnesses zuerst minimal reparieren. Besonders auf die 11 UI/Shizuku-Tests achten.
3. Mögliche PR362-LiteRT-Screen-Fixes nicht verlieren: separat gegen PR364 vergleichen; nur kompatible, getestete Teile überführen.
4. Danach den zuverlässig funktionierenden Code39+-Updatekandidaten und reale Fold7-Geräteabnahme vorbereiten.
5. Dann lokale Gemma-Proof → echte CCC1-Feuerprobe → Android-Werkzeug- und Worker-/Gedächtnisintegration → Issue-#41-Gesamtplan.

**Abschlussmaßstab ist die reale benutzbare, vor Ort nachweisbare Continuum-KI – nicht eine Testanzahl oder PR-Beschreibung.**
