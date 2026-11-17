# Device AI Continuum 0.3.16 – signierter Fold-/Animations-Updatebericht
Datum: 2026-10-23

## Build-Identität
- **Signier-/Delivery-Branch:** `astra/render-mentor-apk-builder-20261019`.
- **Build-Commit:** `6706da14f6d713bf34799a5cac5b64cf0d4e638e`.
- **Render-Deploy:** `dep-dbdevhnb9u1s739fjlog`, Service `srv-dbaue53bc2fs73crkop0`, Status **live**.
- **GitHub-Code:** PR #86 (Fold live reflow und Motion), PR #87 (Regressiontests), PR #88 und #89 (Release-Archivprüfungen), nur auf bestehenden Signierzweig integriert, kein Main-Merge.
- **Gradle-Log:** `BUILD SUCCESSFUL in 4m 15s`, `54 actionable tasks: 54 executed`. Node-/Backend-/Security-Suites sowie `:app:testDebugUnitTest :app:lintDebug :app:assembleDebug` erfolgreich.
- **Paket:** `at.heinz.deviceai.mentorpaired` (kein neues appId-Suffix).
- **Version:** `0.3.16` / **versionCode 17**, vorher installiert `0.3.15` / code16.
- **Signer:** vorhandener persistenter PKCS12-Keystore, Zertifikat SHA256 `2b05fa9de378fb6c6c5376143774a573897dc9c9151305f1c44010275c76a9cc`; im Builder geprüft; APK v2 signiert, ein Signer.
- **APK SHA-256:** `531f4f8d5935f44e8e46ddce9cf3a1b05743e2d99e51855b40091b5c2afdf7ef`.
- **APK Datei:** `DeviceAI-Continuum-v0.3.16-531f4f8d5935f44e8e46ddce9cf3a1b05743e2d99e51855b40091b5c2afdf7ef.apk`.
- **Chrome-Direktlink:** `https://device-ai-mentor-apk-preview.onrender.com/DeviceAI-Continuum-v0.3.16-531f4f8d5935f44e8e46ddce9cf3a1b05743e2d99e51855b40091b5c2afdf7ef.apk`.
- **Verfügbarkeit:** Render-Deploy live; ein unabhängiger HTTP-GET/Hashcheck der veröffentlichten URL aus dem Chat ist nicht möglich gewesen. APK-Hash wurde am Buildort validiert. Android-Installation durch Besitzer ausstehend.

## Funktionsänderungen
- Reflow bei tatsächlichem onLayout-Fensterbreitenwechsel; nicht nur stale `screenWidthDp`.
- Fold rail / kompakte Bottom-Navigation entsprechend Breite/Höhe.
- Übernahme vorhandener Chat-Nachrichten, Eingabeentwurf und Cursor, Scrollposition, Aufgabenplan, Dashboardzustand; keine erneute Modelinitialisierung durch reine Fold-Umschaltung.
- 3D-Plexus dreht sichtbar bei konstantem Standbild-Baseline-Frame.
- Ständig sanfte Lichtakzente, Navigationspunkt/Lichtbalken, Lernkurvenmarker und gemessene CPU-Systemkurve; keine künstlichen Messwerte.
- Animationen nur bei sichtbarem, angehängtem Fenster, systemseitig aktivierter Animation und ohne Stromsparmodus.
- Kotlin/JVM-/Node-Tests (inkl. Fold-Rückklappen und Tastatur-Nicht-Neustart), Lint und Source-Gates erfolgreich auf signiertem Kandidaten.

## Ehrliche Grenzen / Daten
- Besitzerprüfung auf Samsung Fold, TalkBack, genaue FPS-/Energie-Messung und nachweisbare Überinstallation sind **noch offen**.
- Forschungs-LM1/CCC1-Quellkern bleibt vorhanden; nicht als voll aktiver, allgemein kompetenter LM1-Chat bewiesen.
- **Altes Rollback-Online-Archiv ist teilweise ausgefallen:** der Vorgänger-APK-HTTP-Download meldete 404. Das Skript hinterlegt eine öffentliche `ROLLBACK-ARCHIVE-STATUS.txt` und schreibt **niemals** „archiviert“, sofern kein alter Bytebestand heruntergeladen, SHA-256- und signaturgeprüft wurde. Source/Versionstags weiterhin auf GitHub; Besitzer sollte lokale Downloads/Exports zusätzlich behalten.
- Keine Besitzergewichte gelöscht/übertragen, kein neues packageId, keine Main-Änderung.
- Die älteren separat installierten Preview-Pakete müssen **individuell** exportiert und erst nach bestätigtem Update sicher deinstalliert werden. Ihre Daten werden **nicht** automatisch in `mentorpaired` migriert.

## Geräteabnahme
1. In Chrome APK 0.3.16 laden; Android muss für bestehende `Device AI Mentor Pair` **Aktualisieren** anbieten.
2. Öffnen, gespeicherte Lernstände und Gesprächsdaten prüfen.
3. Während die App offen bleibt Fold aufklappen: breite Rail-/Dashboardansicht muss automatisch füllen, ohne große leere rechte Fläche.
4. Fold schließen: Rückkehr zum kompakteren Phone-Layout, Texteingabe und Chat-/Aufgabenplan unverändert.
5. Hologramm sollte auch in READY ruhig kontinuierlich drehen; aktiver Navigationsglanz muss laufen. Für Energietest Stromsparmodus deaktivieren; bei systemweiten deaktivierten Animationen darf die App Motion reduzieren.
6. Reale Lern-/Systemaktivität muss sich nach neuen Ereignissen/Messintervallen verändern, aber keine erfundenen Zähler zeigen.
7. Danach separate Preview-Apps **erst nach nachweislichem Datenexport** und sicherer Paketzuordnung entfernen.