# Continuum AAA+ → Opus 5.5 Übergabe (22.10.2026)

## Einordnung und exakt reproduzierbarer Stand

- Repository: `Ullmann27/device-ai`. Produkt **Device AI Continuum**, nicht andere Projekte.
- Letzte bestätigte, normal signierte Nutzer-App: `at.heinz.deviceai.mentorpaired`, v0.3.12/Code 15, Release-Quell-SHA `619ebeb2831c8184a91e3c08e165ed4f4794f969`. Niemals mit einer Debug-Preview aktualisieren, deinstallieren oder die lokalen Gewichte überschreiben.
- Funktionsbasis: PR #78, Branch `integration/continuum-full-feature-candidate-20261022`, SHA `29f70b9729060609b385aae35a1e39a12d98eae0`, zusammengeführte QF2+ Quellenprüfung, Android/Termux Coding Agent, CDX1 read-only 25-case lab und native Opus-Premium-Home. Volles Render/JUnit/Lint/Assemble an dieser Basis grün.
- Aktueller Visual-/Cockpit-Branch: PR #79, `integration/continuum-aaa-bento-cockpits-20261022`; auf PR #78 aufgesetzt und für alle berechtigten GitHub-Agenten offen. Der Head muss vor jeder Änderung live gelesen werden (dieses Dokument kann älter als HEAD sein).
- Build: eigener Render static service `srv-dbd5nb8473hc73dgl5tg`, Buildskript `scripts/render-continuum-aaa-preview.sh`, Output `continuum-aaa-output`. Test-Paket `at.heinz.deviceai.featurepreview`, **keine vierte App**. Debugsignatur in getrennten Builds möglicherweise wechselnd: eine bereits installierte **Funktions-Preview** bei Zertifikatskonflikt nur nach expliziter Prüfung ihres Paketnamens ersetzen; niemals Haupt-App antasten.

## Was konkret fertig implementiert wurde

1. `ui/ContinuumBentoCockpit.kt`: Wiederverwendbare native, in dp skalierte Glasflächen, Cyan-Blau-Violett-Rahmen, responsive 2-/3-Spalten-Aktionskacheln, Statuszellen, Anzeige roher Audit-Timestamps als lesbare lokale Uhrzeit, bestehende Action-Callbacks.
2. `AgentActivity.kt`: vier bisherige Zentren (Lernen, Entwicklung, Speicher, System) auf `showCenter`-Bento, Learning-Core-v2-Statusdialog und Web-Recherche QUICK/STANDARD/DEEP ebenfalls Bento, Modell-Cockpit mit SWB1/Basismodell-Ladezustand und owner-gated Aktion; alte Modellladen-Schaltfläche von `Button?` auf `TextView?` kompatibilisiert.
3. `ui/ContinuumSciFiEffects.kt`: günstige native statische CRT-Linien (ca. 3.5% Deckkraft) und gecachte Radiallichtfelder. `ContinuumHomeView` zeichnet Licht hinter und Linien über dem Dashboard; `ContinuumBentoCockpit` zeichnet Scanlines über dem Inhalt ohne Eingabefilter. Keine Vollbild-Weichzeichnung, keine zusätzliche Animation/Abhängigkeit, kein Fake-Status.
4. `ui/ContinuumSystemGaugesView.kt`: vier Touch-/TalkBack-freie Statusringe aus `ContinuumDashboardState.SystemStatus`: tatsächlicher **App-CPU**-Prozessanteil, GPU **unbekannt (—)**, Geräte-RAM, Gerätespeicher; Labels und TalkBack. In Systemcockpit als zuletzt gemessener Dashboard-Snapshot.
5. `ContinuumDashboardCollector.kt`: Geräte-StatFs misst Geräteanteil; die Klassifizierung nur des App-`filesDir` wird sauber als `App: eigene Dateien`, `App: Wissensquellen`, `App: Projektdateien` und `Übrige Gerätebelegung` gezeigt (nicht mehr fälschlich `System` für Differenz).
6. `ui/ContinuumHomeElements.kt` und `ContinuumWideElements.kt`: positive Veränderungen realer Lernereignisse grün, negative rot, neutral grau. `SegmentBar`, `WaveChart`, `Donut`, `BarChart`, `SystemActivityElement` und 3D-Energiekern existierten schon aus PR #74 und wurden bewusst nicht durch Flutter-Code neu implementiert.
7. `.github/continuum/aaa-cockpit-and-effects.test.mjs`, bestehende `public-curriculum-lab.test.mjs` angepasst, um die erhaltene owner-gated Gewichte/Labor-Kachel statt eines entfernten alten AlertDialog-Knopfes zu prüfen.

## Bekannte Abnahmegrenzen

- **Neuester vollständiger erfolgreicher Build:** Render-Deploy `dep-dbd64jkipgvc73e6u6e0`, APK-Quellcommit `737725d00876d9321f3d187985582f482d6f461f`, Status **LIVE**, Gradle `BUILD SUCCESSFUL` mit 54 ausgeführten Tasks (inkl. `testDebugUnitTest`, `lintDebug`, `assembleDebug`), Node-/Backend-Suite grün. APK SHA-256 `7f9832d758fad0e07ff4953975b2a2af2c226fa4f89f58b97a1f24e8796c23a8`. APK-URL: `https://device-ai-continuum-aaa-cockpit-preview.onrender.com/DeviceAI-Continuum-AAACockpit-7f9832d758fad0e07ff4953975b2a2af2c226fa4f89f58b97a1f24e8796c23a8.apk`. Der spätere Commit an diesem Dokument ändert nur Dokumentation; kein neues Build für reine Doku nötig. Vor Geräteinstallation Zertifikatskonflikt der bisherigen Feature-Preview berücksichtigen.
- **Samsung Fold Außen-/Innendisplay** bislang nicht mit realen Screenshots des neuen Cockpits abgenommen. Responsives `ContinuumHomeLayout.isWide()` schaltet aktuell erst ab **700dp Breite UND 500dp Höhe** statt der jüngst gewünschten 600dp; vor Änderung echte verfügbare Fold-Fenstermaße, Insets und lesbare Schriften gegen Screenshot prüfen. Keine blinde Absenkung auf 600dp bei weiter linear skalierten 840dp-Karten. Früherer Funktions-Preview zeigte leeren Startscreen/Navigation oben; dessen FrameLayout-Fix liegt in PR #78, aber ein tatsächlicher Fold-Gerätetest nachher fehlt weiterhin. Screenshot erst nach bestätigtem Build.
- `showCenter` besitzt sieben modernisierte Einstiege; verschachtelte ältere AlertDialogs/Schalter und Details sind nicht automatisch vollständig ersetzt. Der Owner verlangt durchgehende AAA+-Qualität in **allen Unterdialogen**, echte Daten, keine Action-Verluste.
- Keine neuen Eingabe-/Sensorberechtigungen, keine Hintergrundta​sks, keine ungesicherten Code-Merges. Kein `main`-Push ohne explizite Freigabe.
- Flutter-Code des Owners ist **nur eine Designvorlage** für native Kotlin Canvas/View, kein neuer Flutter-/Dart-Stack.
- Öffentliches Android-Studium zeigt `FAILED_SAVE_CYCLE_IllegalStateException`: Ursache/Datenerhalt in Issue #76 separat dokumentiert, noch nicht im Gerät repariert.
- LM1/CCC1: Opus PR #75 Head `4691b03b` zuletzt veröffentlicht, `ccc1-journal.mjs`, `ccc1-interpreter.mjs` und vollständige pre-registrierte VALIDATION-Run-Artefakte waren zu diesem Zeitpunkt nur im lokalen Opus-Worktree / temporären Prozess. Nach Wiederfreigabe bei 21:40 **als Erstes Status, Exit-Code, `select.log`, Checkpoints und lokale CCC1-Quellen sichern**, genaue Hashes/Test-Receipts nach GitHub pushen und nicht neu starten/überschreiben, bevor der alte Lauf ausgewertet wurde.

## Prüfung vor produktiver Integration

- Diff PR #79 gegen PR #78 (beide ab v0.3.12 signierter Basis). Null Verlust von SWB1/CDX1/CRE1/CSX1/CCC1-Modellen/Dateien, owner facts und Backup-Rollback.
- Run node/backend suite + `:app:testDebugUnitTest` + `:app:lintDebug` + `:app:assembleDebug`; klassifiziere reale Warnungen. Zusätzlich Geräteprüfung Fold offen/geschlossen, große Schrift, Touchziel ≥48 dp, IME, Karten-Scroll, Modals, alles ohne transparente Blocker.
- Screenshots beider Geräteformen mit Referenzbild abgleichen; Performance/Batterie und GPU-/HW-Rendering messen statt „ohne Leistungsverlust“ zu behaupten.
- Keine Demo-/Beispielwerte (98%, 432 GB, 92% GPU). `null` -> „—“. Identität des beantwortenden Modells und SWB1-Holdout-Güte niemals als generelle Sprachintelligenz ausgeben.
- Nach erfolgreicher Freigabe kann ein **eigener gleicher Produktpaketname + identisches signiertes Zertifikat + monoton erhöhter versionCode** gebaut werden; die Preview alleine ist kein Nutzer-Daten-Update.

**Zusammenarbeitsregel:** Gib Opus den direkten Zugriff auf PR #79 und alle anderen Branches. Alle Änderungen in offenem GitHub-PR mit Commit, kein Agenten-exklusiver Block, keine Arbeit nur im flüchtigen Terminal, keine alten funktionsfähigeren Kernklassen durch ältere Datei kopieren. Bei Nutzungslimit zuerst Git commit/push und Prüfbelege sichern.
