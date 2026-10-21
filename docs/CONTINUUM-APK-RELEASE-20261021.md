# Device AI Continuum 0.3.9 – signierte Astra-Integrations-APK

Stand: 21.10.2026 · STATUS: **RENDER_APK_BUILT**, nicht DEVICE_INSTALLED/END_TO_END_VERIFIED.

## Quell- und Integrationsbelege

- Review-Branch: `integration/continuum-astra-20261021`; PR #48 Draft.
- Astra-Forschungsfixes PR #46, CDX1/SWB1-Versionbindung PR #44, atomare Paar-Checkpoints PR #45 sind unter einem Integrations-Merge im Entwicklungsbranch enthalten.
- Nach Integration korrigierter CDX1-Node-Test: Commit `e462dfcdede86930c8e339f3b1d8b8cd713ebb9c`.
- Quell-/Release-Skript-Härtung gegen CDN-Altdateien: Integrationscommit `452ba6e4da12ddb9ea5ac1aa9fe62c7f112ad285`.
- Über denselben Quellbaum gebauter Render-Build-Delivery-Commit: `524fff3643c3bec061962998c78eef3aad8c8f39` (ausliefernder Bridge-Merge `02929bbb492bab3204cbe774ce780861a86518d0`). Keine Änderung an `main`.
- Render Static Service `srv-dbaue53bc2fs73crkop0` (`device-ai-mentor-apk-preview`), erfolgreicher Deploy `dep-dbc74ee1egvs739nvurg`, Status `live`.
- Der produktive Mentor-Backend-Service `srv-dbat422jnfac7391t7sg` blieb auf seinem bisherigen Branch. Signiermaterial wurde nicht ausgelesen, nur innerhalb des autorisierten Render-Builds verwendet.

## Verifikation des Builds

- Vollständiger Node-Testlauf, Backendtests, Mentor-Kontrakt-/Worker-Tests: Build-Skript unter `set -Eeuo pipefail` lief bis zu allen späteren Gates durch.
- Gradle-Aufruf: `:app:testDebugUnitTest :app:lintDebug :app:assembleDebug -PdeviceAiMentorPreview=true`.
- Render-Protokoll: `BUILD SUCCESSFUL in 4m`; 54 Gradle-Tasks ausgeführt.
- `apksigner verify --verbose`: v2 true, 1 Signierer; erwartete Zertifikats-SHA-256-Identität durch Build-Skript geprüft.
- `aapt dump badging`: `at.heinz.deviceai.mentorpaired` / `versionCode='12'` / `versionName='0.3.9'` / SDK 36.
- SHA-256 `26753ef4e49f7b572926f84ae48c0f5ce394fed2f568e753d18549d218108ccf` entspricht dem in Render-Buildprotokoll erzeugten **inhaltsadressierten** APK-Namen.
- Öffentliche unverwechselbare `SHA256SUMS-26753ef4e49f7b572926f84ae48c0f5ce394fed2f568e753d18549d218108ccf.txt` und `BUILD-INFO-26753ef4e49f7b572926f84ae48c0f5ce394fed2f568e753d18549d218108ccf.txt` wurden zusätzlich über unabhängigen HTTPS-Inhaltsabruf rückgelesen; der Download-Endpunkt für die Binärdatei antwortete mit `content_too_large` (Binärdownload konnte hier nicht erneut vollständig gehasht werden).
- Früherer CDN-Effekt: generische `SHA256SUMS.txt` lieferte nach Live-Deployment noch die SHA des vorherigen Artefakts `fc5ded...`. Nur den **neuen Content-Hash-Dateinamen** verwenden, nie generische Namen als Beweis für aktuelle APK.

## APK-Download

https://device-ai-mentor-apk-preview.onrender.com/DeviceAI-Continuum-v0.3.9-26753ef4e49f7b572926f84ae48c0f5ce394fed2f568e753d18549d218108ccf.apk

SHA-256: `26753ef4e49f7b572926f84ae48c0f5ce394fed2f568e753d18549d218108ccf`

Prüfsummendatei:
https://device-ai-mentor-apk-preview.onrender.com/SHA256SUMS-26753ef4e49f7b572926f84ae48c0f5ce394fed2f568e753d18549d218108ccf.txt

Build-Info:
https://device-ai-mentor-apk-preview.onrender.com/BUILD-INFO-26753ef4e49f7b572926f84ae48c0f5ce394fed2f568e753d18549d218108ccf.txt

## Installation – Samsung Android 16

1. Bestehende `Device AI Mentor Pair` (`at.heinz.deviceai.mentorpaired`) **nicht deinstallieren**. Nicht `Daten löschen` oder den App-Speicher zurücksetzen.
2. APK über die **inhaltsadressierte URL oben** in Chrome herunterladen, nicht über den generischen älteren Dateinamen.
3. Prüfe, soweit möglich, Dateihash gegen den Wert oben. Optional Android-Installationsdialog: falls Signatur/Paketkonflikt oder ein anderes Paket als `at.heinz.deviceai.mentorpaired`, Abbruch statt Neuinstallation.
4. Die APK-Datei in „Downloads“ öffnen, erforderliche Android-Berechtigung für Installation aus dieser Quelle **nur für diesen Vorgang** erteilen; Android muss „Aktualisieren“ für dieselbe bestehende App anbieten.
5. Nach Update: App-Version 0.3.9 prüfen; unveränderte SWB1-Lernstände, bestehende Checkpoints und gespeicherte Forschung im Lerncenter kontrollieren.
6. CDX1-Status auf `PINNED_CURRENT`, `PINNED_PREVIOUS`, `RECOVERED_LKG`, `LEGACY_UNBOUND` oder `NO_CHECKPOINT` prüfen; keine Freigabe nur aus dem Status ableiten. Bei `RECOVERED_LKG`: Forschung nur lesend, CDX1-Training gesperrt.
7. Nur nach eigener Zustimmung CDX1-Hintergrundüben aktivieren; die lokale 20-%-Akku-/Temperatur-/Speichergrenze bleibt gültig. Warte nicht auf einen garantiert genauen 15-Minuten-Job.
8. Öffentliche Forschung mit einem existierenden deutschen Wikipedia-Artikel prüfen; fehlender Asilomar-Titel soll als `SOURCE_UNAVAILABLE_MISSING_PAGE` statt unklarer `IllegalStateException` gemeldet werden.
9. Das Coding-Zentrum kann vorhandene Builder-Funktionen öffnen; ein vollständig app-initiierter Termux-BUILD samt Rückkanal, unabhängiger Geräte-/APK-Installation und autonomer Programmierzyklus sind weiter separate, **nicht nachgewiesene** Gates.
10. Zwei echte Lernläufe, App-Neustart/Doze, Akku 19/20 %, Termux-Rückkanal und LKG-Recovery auf dem Samsung messen; erst nach dieser Abnahme `END_TO_END_VERIFIED` freigeben.

## Rollback und offene Schritte

- Bei nicht startender App, Paketkonflikt oder verlorenen Daten: **keine Deinstallation**; Testergebnis, Screenshots, Version und ggf. Logcat protokollieren. Alte private Checkpoints sichern und keinen nicht kompatibel signierten Downgrade erzwingen.
- GitHub-Backup: `backup/render-apk-before-astra-integration-20261021`.
- `main` und produktive Nutzerzustände unverändert.
- Lokale CDX1-Verbesserung gegenüber eingefrorenen Holdouts und vollständiger autonomer Android-Coding-Agent **nicht belegt**. Diese APK ist ein signierter Integrationskandidat, keine vollständig geräteabgenommene Produktfreigabe.


## Samsung-Forensik 21.10.2026 – CDX1-Lernstand nach v0.3.9 (offen)

**Besitzerbeleg v0.3.9:** SWB1 ist sichtbar mit 104 Experiences, 25.400 Gewichtsupdates, SHA-Präfix `95ee0ef36a56b1fa`, Intent-Holdout 4/8 und Semantik-Holdout 3/6. Öffentliche Forschung mit 43 neuen Quellen und 44 akzeptierten SWB1-Runden, JobScheduler registriert. CDX1-Labor dagegen `NO_CHECKPOINT`, 0 Updates, großes Archiv 0/12. Ein SWB1-Datenverlust ist daraus nicht erkennbar; CDX1-Verlust oder fehlendes Einlesen ist noch nicht bestimmt.

**Kontext aus den vorherigen Gesprächen:** Am 20.10. wurde für Mentor Pair v0.3.4 Code 7 / Quelle `a608490b6eba` ein CDX1-Laborstand von 120 Updates und Checkpoint-Hash-Präfix `17226b3d7a8ef698` beschrieben. Verschiedene ältere Zwischenberichte unterscheiden zwischen vorbereiteten 120 Schritten und bereits gespeicherten Gewichten: den ursprünglichen Screenshot/Status für einen definitiven Vergleich heranziehen; **nicht aus einem bloßen Auftrag einen persistierten Checkpoint folgern**. Keine eindeutige Besitzerbestätigung für Deinstallation oder Datenlöschung zwischen v0.3.4 und v0.3.9.

**Direkter Quellcodevergleich am 21.10.:**
- Android-Paket damals und jetzt `at.heinz.deviceai.mentorpaired`; daher nicht einfach die bekannte Trennung zur `.preview`-App als Erklärung annehmen.
- Beide Versionen verwenden `ContinuumLearningCore(File(filesDir,"learning-core-v2"))` und `ContinuumDecoderBridge(directory)` mit Legacy-Dateiname `context-decoder-paired.cdx`; keine geplante Pfadmigration.
- Die Decoder-Serialisierungsimplementierung `TinyContextDecoder.kt` ist vom damaligen Commit `a608490b6eba` bis zur neuen v0.3.9 byteidentisch (Git-Blob-SHA `d5866c39048b8c194bc96e979f62fb617e2c8e85`). Eine bewusste Modellformat-Inkompatibilität ist damit nicht erkennbar.
- v0.3.9 bevorzugt ein neues `context-decoder-paired.bundle` mit LKG, fällt bei Nichtvorhandensein aber auf die alte `context-decoder-paired.cdx` zurück. Falls Legacy-Datei vorhanden und intakt ist, sollte sie als `LEGACY_UNBOUND` oder gebundenes Modell erscheinen.
- Eine **schadhafte** Legacy-Datei wird derzeit in `loadPairedOrNull()` durch `runCatching...`.getOrNull() als fehlender Checkpoint dargestellt. Ein Checkpoint im falschen Dateiverzeichnis oder eine nach APK-Neuinstallation nicht mehr vorhandene Datei würde ebenso `NO_CHECKPOINT` erzeugen. Deshalb **keine automatische Reinitialisierung, kein stilles Training, keine Löschung** bevor der Dateizustand lesend geprüft wurde.

**Besitzerdiagnose (maximal zwei zusätzliche Bilder statt Wiederholung aller Screenshots):**
1. Lernzentrum → Experimente → `CDX1 · BELEG + FRAGE → ANTWORT (LABOR)`: den vollständigen Statusdialog mit Updates und SWB1-Verbindung fotografieren, KEIN Training starten.
2. Android Einstellungen → Apps → `Device AI Mentor Pair` → `Speicher`: Screenshot von App-/Datenspeichergröße und installierter Paketvariante/Version (ggf. App-Info-Bild zusätzlich, falls Version nicht auf derselben Seite sichtbar ist). Ein Android-Speicher-Screenshot allein beweist den Checkpointzustand nicht.

**Reparaturpfad:** App-interne, strikt lesende Dateidiagnose (Existenz, Länge, strukturierte Decoder-Validität, SHA-Präfix und Legacy-/Bundle-/LKG-Status) ergänzen, falls bestehende Laboranzeige keine genaue Ursache erkennen lässt. Bei vorhandener gültiger Legacy-Datei vor einer explizit zugestimmten Migration einen separaten gesicherten Snapshot erstellen. Fehler `CORRUPT_LEGACY` statt `NO_CHECKPOINT` darstellen. Keine Dateiveränderung allein durch Statusabfrage, keine automatische Checkpoint-Promotion, keine Produktions-Branchänderung. Eine Korrektur-APK braucht echten Build und erneute Geräteprüfung; die v0.3.9 darf nicht stillschweigend als CDX1-repariert gelten.


## Sicherheits- und UX-Nachbesserungen nach Besitzer-Screenshots, 21.10.2026

**Ausgangslage:** Die auf Samsung installierte APK ist v0.3.9 (versionCode 12), signiert und startet. SWB1 hat 25.400 Updates, 104 Experiences, 43 neue Quellen. Forschung am 21.10. um 10:23:51 `TRAINING_ACCEPTED`. Ein SWB1-Offlinepaket mit 1.100 Intent- und 900 Semantik-Updates wurde richtigerweise als `KANDIDAT VERWORFEN` abgelehnt, da bestätigte private Lektionen schlechter geworden wären. Separater NEURON/Tiny-Kern zeigte 5 Auto-Lernzyklen mit 2 akzeptierten Verbesserungen, Härtetest 6/12. Der ältere `Neuron Demo`-Befehl lieferte `IllegalArgumentException`, weil der Testlauf nur bei leerem Satzspeicher starten darf. Er darf NIEMALS Besitzer-Lektionen zurücksetzen. **Diese drei Lernwege sind unabhängig.**

**CDX1-Fall:** Ältere Dokumentation erwähnt 120 Paarlabor-Updates, während v0.3.9 `NO_CHECKPOINT`, 0 Updates anzeigt. Der vorläufige Quellcodebefund zeigte einen echten Fehler: `loadPairedOrNull()` verschluckte eine beschädigte alte CDX1-Datei zu null, sodass `pairedStatus()` fälschlich `NO_CHECKPOINT` meldet; ohne weiteren Schutz hätte eine neue Trainingsrunde bei bestehender beschädigter Legacy-Datei versuchen können, ein neues Paarbundle von Generation 0 anzulegen. Dies ist kein nachgewiesener Datenverlust, sondern ein Schutz- und Diagnoseproblem.

**Im bestehenden Draft-Integrationsbranch behoben (noch NICHT in einer neuen APK installiert):**
- `6197905c7f7bfc3dcf2614a7573e7b0f24095d52`: korrupte oder verwaiste Legacy-Dateien führen zu `CORRUPT`, nicht `NO_CHECKPOINT`; vorhandenes LKG-Backup ist nur lesbar (`RECOVERED_LKG`), Training über einer Wiederherstellung bleibt gesperrt. Symlinks werden abgewiesen. Echte Abwesenheit bleibt `NO_CHECKPOINT`. Bestehendes Bundle und SWB1 werden nicht überschrieben.
- `4a1d7832f98585cc69b8215164fe78fdcaaf481d` und `b4c9cec3f65e07d87dc99dfc88508036bf9b14da`: neue JVM-Regressionstests für korrupte Legacy-Dateien, Backup-Wiederherstellung nur lesend, verwaistes Binding, sowie strikt nichtschreibende Metadateninventur.
- `4e783143b3490f253e2adb30a764d36c9d96391e`: bei `Neuron Demo` mit vorhandenen Lektionen klare Sicherheitsmeldung statt irreführendem `IllegalArgumentException`; kein Reset, keine neuen Gewichte.
- `542187816a64df9bd2706bf56a26a2b146e7f81c`, `e44a27d90d07020c7aec29a950958ce427606cea`, `04d0b205f07a89736b61f376b5cd50624e958846`, `fbb0b1dbf1769e1ee9dea1c48f1bd92bdf2822c6`: CDX1-Labor zeigt im bestehenden Fenster die fünf erwarteten Dateieinträge nur als vorhanden/fehlend/Typ/Bytezahl an: Legacy-CDX1, Legacy-LKG, SWB1-Binding, Paarbundle, Paarbundle-LKG; niemals Dateiinhalt oder private Pfade. Der UI-Text unterscheidet `CORRUPT` explizit.

**Wichtig:** Code-Commits und neue Testdateien sind noch KEINE bestandenen Android-Tests; kein Gradle-/Render-Rebuild für diese Patches wurde ausgelöst, der auf dem Samsung laufende v0.3.9-Stand bleibt unberührt. Die Änderungen erfordern erst Gradle-JVM-, Lint- und signierten Versionsbuild, wenn die gefahrlose Reparatur im bestehenden Integrationsbranch fertig ist. Nicht erneut Version 0.3.9 über eine identische Version aktualisieren, sondern gegebenenfalls eine höhere, eindeutig signierte und paketkompatible Folgeversion. `main` und Live-Backend unberührt. Keine unkontrollierte Checkpoint-Migration und kein Auto-Reset.

**Nächster kontrollierter Schritt:** Neue Fotos des vollständigen CDX1-Laborstatus und des Android-App-Datenbereichs auswerten, sofern lesbar. Danach entscheiden, ob nur das Verzeichnis leer ist oder eine alte Datei vorhanden, aber beschädigt ist. Bis zur Diagnose kein experimentelles CDX1-Training, keine Datenlöschung und keine Deinstallation. Danach gezielte Release-Gates und Samsung-Geräteprüfung. Der Bericht enthält bewusst keine Behauptung, alte 120 CDX1-Updates seien bereits wiederhergestellt.
