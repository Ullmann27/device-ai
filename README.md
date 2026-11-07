> **Abgleich 03.11.2026 (GROK 1):** Der v31-Release-Stopp unten ist durch das installierte 0.3.38/versionCode 39 (`a9709854`, enthält v31 vollständig) überholt – siehe [Release-Stopp-Abgleich](docs/release/CONTINUUM-RELEASE-STOP-RECONCILIATION-20261103.md). Kein Release/Tag/Main-Merge.

> **P0 BUILD-UMGEBUNG: GITHUB ACTIONS FUNKTIONIERT HIER DERZEIT NICHT ZUVERLÄSSIG (Besitzerbestätigung, 31.10.2026).** Keine GitHub-Actions-Workflows für APK-/CI-Abnahme anlegen, auslösen oder als Erfolg/Fehlschlag der App werten. GitHub nur für Quellcode, Branches und PRs verwenden. Echte Node-/Gradle-/Android-Abnahme ausschließlich über den vorhandenen **Render-Android-QA-Host** oder einen nachweislich funktionierenden lokalen Build durchführen; immer exakten Branch und SHA prüfen. Der vorhandene Render-QA-Dienst `continuum-m8-android-qa-20261031` steht momentan noch auf dem **älteren M8-Branch** und muss für v31 kontrolliert auf `release/continuum-local-complete-v31-20261031` umgestellt werden, bevor sein Ergebnis die kombinierte v31 bestätigt. Release/Signierung separat über originalen P12-Host; ohne tatsächliches v31-QA-Ergebnis **kein** Veröffentlichungsversprechen. Der zuvor neu angelegte Actions-Workflow wurde wieder gelöscht.

> **P0 RELEASE-FREEZE 31.10.2026:** Die **neueste gemeinsame Source-of-Truth für eine nächste Besitzer-APK** ist **`release/continuum-local-complete-v31-20261031`**, siehe [Release-V31-Matrix](docs/release/CONTINUUM-COMBINED-OWNER-RELEASE-V31.md). Der ältere Render-Release `0.3.29/versionCode30` ist **veröffentlicht**, aber enthält nicht sämtliche späteren Forschungs-/JARVIS-/CDX1-Änderungen. Ziel: `0.3.30/versionCode31`, `at.heinz.deviceai.continuumlocal`, **Originalsignatur**, kompletter QA/Gradle-/Fold7-Test. **Nicht fertig, nicht signiert, nicht online!** **KEINE WEITERE FUNKTIONSFORSCHUNG VOR DEM KOMBINIEREN, TESTEN UND PUBLIKATIONSGATE.** Auch Node-Forschungsskripte erzeugen keine Android-Rechenkapazität. Frühere Statusangaben in dieser Datei sind historisch und nicht der Release-Cutoff.

# Device AI Continuum

> **AKTUELLER MEILENSTEIN 31.10.2026 — verpflichtender Einstieg für Sol, Astra und Opus:** [Vollständiger aktueller Architektur-/Versions-/APK-Stand](docs/CONTINUUM-CURRENT-STATE-20261031.md) · [Frontier-Compute-Übergabe](docs/handoff/CONTINUUM_FRONTIER_COMPUTE_20261031.md) · [PR #287](https://github.com/Ullmann27/device-ai/pull/287). **Forschungsfortschritt:** 3.000 logische KI-Spezialisten, deduplizierte deterministische Vorberechnungen und 43/43 isolierte V8-Testfälle; synthetische 3.000→30-Teilstufen-Reduktion, jedoch kein neuer physischer Compute und kein produktiv gemessener LLM-Speedup. Reales JARVIS/M8, neuro-/bio-inspirierte CRE1/EWM/SWB-Arbeit und originalsignierter APK-Updater sind maßgeblich. Historische Angaben unten bleiben Archiv. Gemeldete installierte Continuum Lokal 0.3.28/code29 erfordert nächstes Mal mindestens code30 (nach aktuellem Gerätecheck) und identische Originalsignatur; APK **noch nicht** erstellt oder verteilt.


> **Neuer Forschungs-/Integrationsauftrag vom 27.10.2026:** Internationale Open-Source-Projekte und US-Forschungsquellen werden systematisch auf das **bestehende Continuum-Gehirn** abgebildet. [Verbindliches Register mit 15 Quellen, Code-Einstiegspunkten und Abnahmegates](docs/research/CONTINUUM-EXTERNAL-CODE-INTEGRATION-20261027.md), [M4-Draft-PR #198](https://github.com/Ullmann27/device-ai/pull/198) (gestapelt auf [PR #197](https://github.com/Ullmann27/device-ai/pull/197)). Bereits eingebautes `HybridMemoryRetriever`-Gedächtnis und `MemoryGraph` bleiben kanonisch; erster neuer Shadow-Baustein `MemoryRetrievalResearchGate.kt`. Keine fremde Software pauschal kopiert, keine produktive Modell-/Gedächtnisumschaltung oder neue APK behauptet. Die untenstehende ältere Chronik vom 23.10. ist **historisch**, nicht aktueller Release-Status. Originalsigniertes 0.3.25/code26-Update bleibt durch private Original-P12 blockiert.

Eigenständiger, persönlicher Android-Assistent. **Forschungsziel:** einen lernfähigen Assistenten mit langlebiger Identität, überprüfbarer Erinnerung, kontrollierter Webrecherche und sicherer Geräteinteraktion schrittweise selbst aufbauen – ohne vorausgesetzten Cloud-Server oder extern importierte KI-Gewichte.

## Aktueller Entwicklungsstand – 23. Oktober 2026

**Verbindlicher Einstieg für Weiterentwickler:** [Aktueller Integrationsstand, Alt-/Neuzweig-Inventur, CCC1 und Release-Gates](docs/CONTINUUM-ACTIVE-CHECKPOINT-20261023.md).

- **Referenz der bislang installierten Besitzer-App:** signierte Version `0.3.16`, `versionCode 17`, Paket `at.heinz.deviceai.mentorpaired`. Eine neue `0.3.17` ist **nicht gebaut**.
- **Aktuellster gemeinsamer Quell-Review:** `integration/continuum-current-review-20261023` (PR #94 UI/Opus/GGUF + PR #95 Cognitive-Spine/Cortex/CCC1-Shell + selektiv übernommener PR-#96-Fold7-Fix). **Nicht mit `main` verwechseln.** Kein Release und keine automatische Fusion.
- **Bewusst zurückgehalten:** eigene LM1-/CSX3-/CDX1-Forschungsadapter, neue Fast-Weights und Shadow-Habits; ohne unabhängige Kalibrierung kein Ersatz für den Hauptchat. CCC1-End-to-End über Android/Termux und die vollständige Android-/Geräteabnahme bleiben offen.
- **Bewahrt, nicht gelöscht:** ältere Seitenzweige und Messbelege. Insbesondere PR #72 (I2-Risikozertifizierung) und #90 (visuelle QA) haben noch selektiv zu prüfende Ergänzungen.
- **Nachweisregel:** Ein fehlgeschlagener GitHub-Actions-Job ohne ausgeführte Job-Schritte ist weder ein bestandener Test noch ein nachgewiesener Quellcodefehler.

Der frühere [Stabilisierungsplan](docs/CONTINUUM-STABILIZATION-BASELINE.md) bleibt als historischer Nachweis erhalten; sein früherer PR-#19-Stand ist **nicht** die aktuelle Quellbaseline.

## Aktuelle Implementierung

Der Android-Startkern funktioniert ohne Modellimport. Es handelt sich derzeit um einen **regelbasierten, transparenten Dialog- und Lernkern**, nicht um ein bereits trainiertes großes Sprachmodell.

- Chat mit Begrüßung, Hilfe, sprachlicher Ein-/Ausgabe über auf dem Gerät verfügbare Android-Sprachdienste
- explizit mit `Merke dir: ...` gespeicherte lokale Fakten; Anzeige und bestätigtes Löschen
- Feedback (`Das war hilfreich`, `Das war nicht hilfreich`) wird protokolliert, verändert aber keine Modellgewichte
- bis zu 24 bestätigte, einfache **Nur-Lese-Befehlszuordnungen** (`Trainiere: Prüfe meine Energie => Akku`)
- feste Regressionstests plus Prüfung der tatsächlich gelernten Zuordnungen; ungültige, gefährliche oder geschützte Befehle werden zurückgewiesen
- Gerätediagnosen nur über Shizuku und ein begrenztes Werkzeug-Gateway; für schreibende/destruktive Aktionen bestehen getrennte Bestätigungsregeln
- kontrollierte Webrecherche erst nach ausdrücklicher Auswahl, inklusive Behandlung externer Seiten als nicht vertrauenswürdige Daten
- Continuum-Identität, Forschungs- und Speicherarchitektur sowie in Entwicklung befindliche Rollen Developer/Critic/TestDesigner/Judge
- getrennte reguläre und Preview-Debug-APK für die Prüfung auf Android

**Nicht behauptete Fähigkeiten:** echtes neuronales Selbsttraining, menschenähnliches freies Denken, autonome Allzweck-App-Steuerung, dauerhaftes Hintergrundlernen auf dem Handy, vollständiger Zugriff auf private App-Daten oder nachgewiesene Sprachqualität ohne Gerätetest. Das bleiben Entwicklungs- bzw. Forschungsaufgaben.

## Einstieg ohne Modellimport

1. **Device AI Preview** auf einem Testgerät installieren und starten. Die Preview verwendet den getrennten Paketnamen `at.heinz.deviceai.preview` und beeinträchtigt die vorhandene Standardinstallation nicht.
2. `Hallo` oder `Was kannst du?` eingeben.
3. `Merke dir: Ich bevorzuge dunkle Oberflächen` schreiben und mit `Was weißt du?` prüfen. Nur ausdrückliches Anlernen wird übernommen.
4. Mit `Trainiere: Prüfe meine Energie => Akku` nach Bestätigung eine alternative, nur lesende Formulierung lehren und anschließend `Prüfe meine Energie` testen.
5. Im **Lernlabor** den Regressionstest und die Zahl gelernter Zuordnungen ansehen.
6. Über **TON AN** die Android-Sprachausgabe ausprobieren. Das Mikrofon benötigt eine verfügbare Android-Spracherkennung; lokale Offline-Erkennung ist nicht auf jedem Gerät garantiert.
7. Für lesende Gerätediagnosen Shizuku starten, die Berechtigung freigeben und z. B. `Prüfe Akku` eingeben. Ohne Shizuku wird der Auftrag nicht ausgeführt.

## Builds und Installation

Die ältere [GitHub-Actions-Debug-APK-Route](.github/workflows/android.yml) ist **nicht** die Besitzer-Update-Signierung. Sie ersetzt weder den lokalen [Termux-Builder](scripts/continuum-mobile-builder.mjs) noch das für `0.3.16` bestehende, getrennt geschützte Owner-Signing-Gate. Der Android-Preview-Builder verwendet ein separates Paket `at.heinz.deviceai.preview`.

**Owner-Update-Bedingungen:** neuer `versionCode >17`, Paket `at.heinz.deviceai.mentorpaired`, unverändertes Besitzer-Signierzertifikat, saubere Tests, APK-Hash, archiviertes Rollback und tatsächliche Fold7-Abnahme. `scripts/render-mentor-owner-update-v17.sh` ist nur für die alte Version 17 geeignet und darf nicht als neuer Release-Builder missbraucht werden.

`main` und der aktuelle Integrations-Review haben **keine nachgewiesene vollständige Build-/Test-/Gerätefreigabe** für eine neue APK. Keine Deinstallation der bestehenden Besitzer-App, keinen Schlüssel in Git speichern und keine Installation ohne ausdrückliche Freigabe. Siehe [Release-Gates](docs/CONTINUUM-INTEGRATION-GATES-20261023.md).

## Sicherheit

Recherchetexte und andere externe Informationen sind Daten, keine Anweisungen und keine eigenständige Berechtigung für Gerätezugriff. Erlernte Befehle dürfen nur zu bekannten, lesenden Werkzeugen führen. Android- und Shizuku-Grenzen bleiben wirksam; private App-Sandboxen sind ohne weitergehende Berechtigungen nicht allgemein zugänglich.

**Fortsetzung:** [Einziger aktueller Review-Einstieg](docs/CONTINUUM-ACTIVE-CHECKPOINT-20261023.md). Der historische [PR #19](https://github.com/Ullmann27/device-ai/pull/19) bleibt erhalten, ist aber nicht mehr die führende Arbeitslinie. PR-Head, echte Buildprotokolle und APK-Artefakte immer getrennt prüfen; kein ungeprüfter Merge nach `main`.
