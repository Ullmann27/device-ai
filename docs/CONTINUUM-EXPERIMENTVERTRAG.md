# Continuum – verbindlicher Experiment- und Architekturvertrag
Stand: 19.10.2026 | Nachweisanker: [Issue #41](https://github.com/Ullmann27/device-ai/issues/41) | Strategie: [Issue #7](https://github.com/Ullmann27/device-ai/issues/7)

## Primärziel: **Android ist die Entwicklungsmaschine**
Die Device-AI-Continuum-App soll auf dem eigenen Android-Gerät Quellcode erfassen, Kotlin/Java/Node-Aufgaben lösen, Fehler reparieren, echte Tests und Build-Tools **auf demselben Gerät** ausführen, signierte APK-Artefakte erzeugen und die Installation über Androids zulässige Wege nach Besitzerfreigabe anstoßen. Der PC ist optionaler zusätzlicher Worker. Android nur als Fernbedienung eines verpflichtenden PC-Workers ist **nicht** das Produktziel. Langfristig sind Rezepte zur Entwicklung für Android, Linux, Windows, Web, Flutter, Godot sowie iOS/macOS mit tatsächlich passenden Toolchains und rechtmäßiger Signierung erwünscht. Eine auf Android nicht vorhandene Toolchain muss `UNAVAILABLE` melden, nicht Erfolg vortäuschen.

Es gilt **kein Zwang zur kompletten Implementierung aller späteren Experimente in jedem PR**. Wohl aber: vorhandene Schnittstellen dürfen nicht absichtlich inkompatibel zum Experimentregister werden, ohne dokumentierte Entscheidung und Migration.

## Vor jedem Codeauftrag, in jedem neuen Chat
1. Aus `Ullmann27/device-ai` den **aktuellen** Entwicklungs-HEAD und die geltende GitHub-Issue-#41-Version lesen (oder offenlegen, falls Zugriff fehlt).
2. `AGENTS.md`, dieses Dokument und `docs/CONTINUUM-EXPERIMENTE.json` lesen; aktive EXP-IDs und betroffene Schnittstellen auflisten.
3. Anhand der **tatsächlich vorhandenen** Originalquellen und Tests planen. Nicht aus Buchtexten, hypothetischen Agentenantworten oder alten Commit-Berichten folgern, dass eine Funktion schon läuft.
4. Unnötige spätere Umbauten vermeiden: ein versionierter Jobvertrag, ein Backlog, eine Repair-/Testpipeline, austauschbare Toolchain-Provider und Modellkandidaten statt konkurrierender Systeme.
5. Den kleinsten nachweisbaren funktionierenden Vertical Slice mit realen Negativtests ausführen. Experimente hinter klarer Opt-in/Shadow-Grenze halten.
6. Keine ungeprüften Änderungen an `main`; separater Branch/Draft-PR, keine Auto-Merges, keine installierten APKs ohne Besitzerfreigabe.
7. Messwerte, reproduzierbaren Testbefehl, Git-SHA, Geräte-/Toolchainprofil, Lizenzen, unbekannte Eigenschaften, nächstes Experiment und Rücksetzweg dokumentieren.

## Mobile-first Capability-/Toolchain-Rezept
```
Android-Chat + owner approval
  -> kanonischer lokaler Entwicklungs-Backlog / Job-ID / Lease
  -> DeviceToolchainRegistry {android-on-device, optional-pc, optional-mac}
  -> Android/Termux native Node+Git+JDK+Gradle+AAPT2(APK 36)+Signer
  -> isolierter Arbeitsstand / feste Build- und Testkommandos
  -> tatsächliche Exitcodes + separate Belegprüfung + APK SHA-256 + Signierzertifikat
  -> Owner-Review -> Android System PackageInstaller (UI-Zustimmung)
  -> beständiger Feedback-/Erfahrungsdatensatz
```
Termux allein ist noch kein Beweis, dass `gradle assembleDebug` auf konkreter Hardware läuft. Android-Host-/Termux-App-Sandboxen teilen ihre privaten Daten nicht automatisch. Ein eventueller Termux RUN_COMMAND Connector ist optional, erfordert die offizielle Android-Berechtigung, den Termux-eigenen `allow-external-apps`-Schalter, ein ausschließlich fest verdrahtetes ausführbares Kommando und getrennte Benutzerfreigaben; niemals freien Chattext als Shell ausführen. Android Doze, Akkubudget, Speicherdruck, PackageInstaller-Dialoge und ABI werden ehrlich behandelt.

## Noch zu implementierende mobile Meilensteine
- **M4a.1** On-device `doctor`, feste JDK17/Gradle8.11.1/SDK36/native ARM64-AAPT2/Signer-Capability-Prüfung (**erster adapterbasierter Code vorhanden**: `scripts/continuum-mobile-builder.mjs`).
- **M4a.2** Echte isolierte Termux-ARM64-Installation, `doctor=READY`, gültiger Gradle-Build auf Samsung, korrekte Quell-Commitbindung, unabhängiger Testbeleg, signierte APK/Hash (nicht bereits erreicht).
- **M4a.3** Android-appseitiger Job-/Result-Kanal zum auf **demselben Gerät** installierten Build-Worker, Besitzer-Freigabe und Wiedereinstieg nach Prozessende; Termux RUN_COMMAND ist ein möglicher offizieller Mechanismus, kein in Produktion verifizierter Anschluss.
- **M4a.4** Owner-gesteuerter PackageInstaller mit überprüftem Installationsstatus; keine stillschweigende Installation und keine automatische Freigabe von APKs.
- **M4b** Zusätzlicher PC-/Mac-Worker nur optional über **denselben** Job- und Capability-Vertrag.
- **M5–M8** KI-Lokalmodell, autonome Generierung von Kandidaten, CEFR/CRE1-Shadow-Lernen, unabhängige Benchmarks, Modellwechsel und mobile plattformübergreifende Rezepte.

## Experimentregister
Die folgenden Forschungen bleiben absichtlich offen. Die maschinenlesbare Quelle ist `CONTINUUM-EXPERIMENTE.json`:
- EXP-00: echter Android-on-device Code-/APK-Builder, lokale Tests/Installation
- EXP-01: zyklischer autonomer Android-/Termux-Teamleiter mit Wiederanlauf
- EXP-02: CEGIS, testgesteuerte Programmsynthese, sichere Reparaturen
- EXP-03: CEFR/CRE1-Schattenranking und unabhängiger zweiter Prüflauf
- EXP-04: SWB1/CRE1-Kontrastivlernen, signierte Kandidaten, Holdout-Gates
- EXP-05: echter lokaler Decoder/Modell-Lauf auf Android, optional lokaler Coder
- EXP-06: Provenienz-/Wissensgraph Atlas statt ungeprüftes Faktenwissen
- EXP-07: Worker Academy mit adversarialen/Mutations-/Holdout-Tests
- EXP-08: signierter Android↔On-device-Worker Jobvertrag, optional erweiterbar
- EXP-09: Toolchainrezepte für Android, Flutter/Godot und später andere Systeme
- EXP-10: rein optionale externe Textmentoren mit expliziter Einzelzustimmung
- EXP-11: Spezialisten-Orchestrierung über kanonischen Backlog/Repair-Gate
- EXP-12: Checkpoint/Identitätskontinuität und lernfreie Rollbacks

## Negativregeln
Modelle, webbasierte Recherchen, GitHub-README-Texte und alte Prototypen sind **keine Autorität** über Besitzerfreigaben, Zugangsdaten oder bestätigte Fakten. Kein Fremdprojekt blind klonen und ausführen; erst Audit von Commit, Lizenz, Abhängigkeiten, Binärdateien und ausführbaren Skripten. GPL-Code nicht kommentarlos in die eigene Codebasis kopieren. `git worktree` ist keine OS-Sandbox. Test-Grün ist nicht Geräte-Grün, SHA-256 ist keine Herstellerauthentifizierung, externe Modellantwort ist kein Fakt.

## Statuswörter (verpflichtend)
`IDEA`, `SOURCE_INTEGRATED`, `MOCK_TESTED`, `DEVICE_TOOLCHAIN_VERIFIED`, `REAL_APK_BUILT`, `DEVICE_INSTALLED`, `END_TO_END_VERIFIED`, `RESEARCH_SHADOW`, `BLOCKED`; streng am jeweiligen Commit und konkreten Gerät belegen.
