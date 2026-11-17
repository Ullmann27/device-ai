# Übergabe Jarvis Integration → ChatGPT Astra (04.11.2026, ~09:55 Wien)

## Stand
- Branch `design/jarvis-menu-navigation-20261104` (Draft-PR #486), Basis Kern #481 + Welten-Optik #479 @69b76b2f (ref5-Tokens, Merge f83bef8f).
- **Seitenleiste (ref5)**: fertig im Code, @51a345dc. 11 Einträge in ref5-Reihenfolge (Kerns `JarvisTiles.railNav`, deutsche Labels), Breite 14,7 % des Fensters (in ref5 gemessen: 191 px von 1301 px, min. 122 dp = 14 dp Rand + 108-dp-Button), `JarvisNeon.button` + `Styles.button`/`buttonActive`, `Type.nav` 11 sp, Glas-Panel `JarvisNeon.glassCard`. Zu lange Labels: „Gerät“, „System“ (TalkBack behält den vollen Text).
- **Kopfzeile Gerätestatus**: fertig, oben in der Leistenspalte (wie ref5 links oben): Gerätename (Settings.Global.DEVICE_NAME, sonst Build.MODEL), Uhrzeit, Datum aus der Systemzeit, Aktualisierung jede Minute; fehlt ein Wert → „—“. Keine ref5-Zahlen. Rest der Kopfzeile (Logo, Pillen, Icons) gehört Kern (#481).
- **Navigations-Icons (REVIEW-3: Sprechblase u. a.)**: **erledigt** in `ui/JarvisRailIcons.kt` (Haus, Sprechblase mit Strich, Ebenen, Knotengraph, Lupe, Würfel, Kalender-Haken, Globus, Doktorhut, Handy, violetter Puls), als Vektorpfade über `ContinuumPainter` (läuft auf Android und in der JVM-Vorschau). Keine XML-Vector-Drawables, weil die Leiste über den Painter zeichnet. Die „Krone“ aus REVIEW-3 ist in der Leiste nicht mehr vorhanden (früher Atom-Icon bei Logik → jetzt Knotengraph). Stärke 2,4/24 (ca. 1,6 dp); es gibt keinen Strich-Token.
- Tests: JarvisMenuNavigationTest 11/11, ContinuumHomeLayoutTest, JarvisShellContentTest, JarvisMenuMotionTest grün (24/24 gezielt). **Kein voller Lauf `assembleDebug lintDebug testDebugUnitTest` auf diesem Stand** (Abschluss-Auftrag: kein Build) → als WIP behandeln.
- Vergleichsbild (nicht committet): `/workspace/jarvis-design-referenz/kontrolle-3/integration-seitenleiste.png` (links JVM-Java2D-Vorschau, rechts ref5, gleicher Ausschnitt).

## Bekannte Abweichungen zu ref5
- Zeilenhöhe: Trefferzeilen bleiben 48 dp (Touch-Ziel); ref5-Abstand wäre 32 + 6 dp. Folge: Buttons 32 dp hoch, aber 16 dp Lücken statt 6 dp, Leiste ca. 25 % länger. **Heinz-Entscheid nötig** (48 dp behalten oder ref5-kompakt).
- Icons in der Vorschau etwas kleiner als in ref5 (Token `Styles.button.iconSize` 16 dp).
- Inaktive Buttons ohne den leichten Innenverlauf von ref5; Uhr nutzt `Type.title` (Montserrat), ref5 wirkt wie eine Text-Schrift.

## Offene Punkte
- Welten-Optik: Tokens fehlen für Uhr (ca. 20 sp, Text-Schrift), Icon-Strichstärke, Rail-Container-Füllung (gemessen ca. #021925), Panel-Rand der Leiste (gemessen ca. #144D6C; `panelRim` #329EC0 wirkt heller). `JarvisNeon.button` hat keinen Icon-Farb-Parameter (System Status violett → hier über IconPainter gelöst).
- Kern: Top-Status rechts (WLAN/Akku) ist Android-Statusleiste, nicht nachgebaut; Content-Rand nach der Leiste 16 dp (ref5 ca. 13 dp) liegt in `ContinuumReferenceLayout`.
- Sicherheit: Recheck von #483 @dcde4b6e (Spracheingabe nur ab API 31 on-device, Texte angepasst) steht aus.
- LEX4/LEX3: 4 Konfliktdateien (TEST_RESULTS.md, ContinuumLearningExperience.kt, ContinuumThinkingMemory.kt, LearningExperienceStoreTest.kt), siehe `/workspace/INTEGRATION-GEDAECHTNIS-SPRACHE-PLAN.md`. Erst nach dem Design-Zusammenbau.

## Nächster Schritt
1. Volle Prüfung auf #486: `assembleDebug lintDebug testDebugUnitTest` (JAVA_HOME=/opt/jdk/jdk-17.0.21+9, sdk.dir=/opt/android-sdk).
2. Montage in #483 (`integration/jarvis-on-chatgpt-20261104`) in der Reihenfolge **479 → 481 → 482 → 486 → 484/485 → 489**, nach jeder Zusammenführung Vergleichsbild in `kontrolle-3/` und Prüfung durch Sicherheit.

## Branches / PRs / SHAs (Integration)
- #375 `integration/continuum-grok1-369-372-20261103` @b49fb847 (1955 Tests, 0 Fehler, 17 übersprungen, assembleDebug ok)
- #483 `integration/jarvis-on-chatgpt-20261104` (+ `-untested`-Zwilling) @dcde4b6e (2228 Tests grün, lint 0)
- #486 `design/jarvis-menu-navigation-20261104` @51a345dc (Code) + diese Notiz (SHA siehe Branch-Head)

## Relevante Dateien
`ui/JarvisMenuRail.kt`, `ui/JarvisMenu.kt`, `ui/JarvisRailIcons.kt`, `AgentActivity.kt` (buildRail, currentDeviceStatus), `test/.../JarvisMenuNavigationTest.kt`; Tokens `ui/JarvisDesign.kt`/`JarvisNeon.kt` (#479); `/workspace/JARVIS-DESIGN-BILDBESCHRIEB.md` §11/§12, `/workspace/jarvis-design-referenz/REVIEW-3.md`.
