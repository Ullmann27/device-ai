# C1-UI-020 V1 — unabhängiger statischer Testvertragsreview

## Urteil und Grenze

Kein statischer Blocker für den eingefrorenen C1-RED-Lauf gefunden. Der Patch passt bytegenau von der unveränderlichen C1-Basis auf den gesicherten Test-only-Checkpoint. Er behält alle acht ursprünglichen Testmethoden, alle Qualifiers und die vier Profilprüfungen mit Fontfaktoren 1 / 1,5 / 2. Die Änderung aktualisiert die Selektoren auf die tatsächlich aktive, angehängte Shell und ergänzt konkrete Anforderungen an Kompaktmodus und sichtbaren Mehr-Rückweg.

Dies ist ein statischer Review, kein bestandener Testlauf und keine Freigabe des späteren Produktpatchs. Durch den Reviewer wurden weder Tests noch Builds gestartet, kein Checkout geändert und keine Prozesse oder Sperren beeinflusst. Der von C1 gemeldete Host-Gate- und UI-RED-Status wird hier nicht als eigener Laufnachweis übernommen.

## Unveränderliche Quelle

- Basis: `681bdec2527c6a5ef4c773e8211cf62e4ac4ad06`, Tree `1ab9ee719626c15a8510e10e478f5e7da4a41a2b`.
- Test-only-Ziel: `f31855138d77ecb5e05e7a17a620b1fd6e9d28c5`, Tree `323227c1c05017b0733509e24f0aebc2cc9faa25`.
- Genau ein veränderter App-Pfad: `app/src/uiTest/java/at/heinz/deviceai/ContinuumReferenceCockpitUiTest.kt`.
- Basisblob `4fa09ef1fe3a6fe88b707e8150611a066c01c72d`; Zielblob `315f42d32518f0c8de4a73cb46c349bd8dbb04d6`.
- Patch-SHA256 `fdd1d7bccba6c400ccaa2edc0fad8224b83da9dfcbf1be9488a07327829f9b7e`; Ziel-SHA256 `b162dda889669fb6818b9e35114a1874f7991d3df3d0d2f83da9b937e9748547`.
- Herkunft: eingefrorene Dateien in `apk-audit/ui020`; Sidecar bezeichnet die erste Freeze-Basis 59d8. Deren Basisblob entspricht dem späteren 681bdec. Der publizierte Commit c65604c wurde von C1 mitgeteilt, für diesen Review nicht separat remote abgerufen.

## Acht ursprüngliche Methoden

| Methode | Unveränderliches Profil | Erhaltene bzw. konkretisierte Aussage |
|---|---|---|
| `presentationControlsReuseTheLiveAvatarAndPersistCompactChoice` | 412×915 mdpi | Reale Appearance-Dialogbedienung; dieselbe Avatarinstanz innerhalb derselben Activity; Singleton. Die bisherigen drei Radiooptionen Kugel / Native 2D / Native 3D und die zwei Aktionen Bewegung prüfen / Schließen bleiben ausdrücklich verlangt. Kompakt wird als vierte Radiooption ergänzt und muss Avatarhöhe sowie Inhaltshöhe tatsächlich verkleinern, die Konversation nach oben verschieben, die Preference speichern und Activity-Recreation überstehen. Die Normalansicht muss exakt zur ursprünglichen Geometrie zurückkehren. |
| `composerDraftSurvivesActualWindowReflowAndActivityRecreation` | 412×915 mdpi | Einstieg über die echte sichtbare Konversationsaktion. Draft, Auswahlpositionen, tatsächliche Fensterverkleinerung auf 280×720, Eingabefokus, maxLines=2, Activity-Recreation, CHAT-Zustand und ausbleibende Ausführung bleiben verlangt. |
| `narrowLargeTypeSendTargetSubmitsReadOnlyCapabilitiesThroughDecor` | 280×720 mdpi | Reales Decor-Senden von „Was kannst du?“; Eingabe wird geleert, Fähigkeiten erscheinen, keine Busy-Ausführung. Die bereits vorher vorhandene synthetische Busy-Fixture prüft weiterhin einzeilige Stop-Darstellung und Abbruch-Beschreibung. |
| `narrowPhoneHasRealDestinationsAndReadableLargeType` | 280×720 mdpi | Gemeinsame Profilprüfung mit Font 1 / 1,5 / 2; reale Shellziele, mindestens 48 dp, Nichtüberlappung, sichtbare Dialogbedienung, erreichbarer Coding-Entwurf, Chat/Rückweg, Draft und Avataridentität. |
| `phoneHasRealDestinationsAndReadableLargeType` | 412×915 mdpi | Dieselben semantischen Zusagen für das normale Telefonprofil bei allen drei Fontfaktoren. |
| `foldLandscapeHasRealDestinationsAndReadableLargeType` | 840×649 land mdpi | Dieselben semantischen Zusagen für das breite Querformat bei allen drei Fontfaktoren. |
| `foldPortraitHasRealDestinationsAndReadableLargeType` | 840×1050 port mdpi | Dieselben semantischen Zusagen für das breite Hochformat bei allen drei Fontfaktoren. |
| `shortenedWideWindowKeepsEveryRailTargetScrollableAndClickable` | 840×649 land mdpi, echte Verkürzung auf 840×320 | Beide realen Rail-Ausprägungen werden mit Font 2 auf Scrollbarkeit, volle erreichbare Targets, mindestens 48 dp und Nichtüberlappung geprüft. Die exakten elf aktuellen Ziele werden verlangt, jedes Shell-Rail-Ziel wird über Decor bedient. Dialoggrenzen, erreichbarer Mehr-/Coding-Entwurf, kein Taskstart, gleicher Avatar sowie Chat-Draft und Rückweg bleiben erhalten. |

## Prüfung gegen tatsächliche Aufrufer

`AgentActivity` aktiviert bereits auf der Basis die Jarvis-Shell. `ContinuumHomeView` blendet dabei die alten ElementViews aus. Der neue Test verlangt eine angehängte, sichtbare HomeView innerhalb des tatsächlichen Decor-Baums und prüft den aktiven Shellzustand; er erzwingt ihn nicht. Getaggte Ziele müssen eindeutig, angehängt und sichtbar sein. Die elf Rail-Ziele stimmen exakt mit `JarvisTiles.railNav` und der daraus abgeleiteten `JarvisMenu.railEntries` überein: home, chat, memory, logic, research, agents, tasks, web, learning, device, system.

Interaktionen erfolgen über DOWN/UP-Ereignisse an der tatsächlichen Decor-View. Scrollen führt reale ScrollView-Vorfahren und die Zielrechtecke nach. ListView-Selektion dient dem Sichtbarmachen der vorhandenen Dialogzeile; aktiviert wird sie anschließend per Decor-Tap. Neue direkte performClick-/performItemClick-/callOnClick-Aufrufe, private Routenausführung, erzwungene Sichtbarkeit oder Shell-Umschaltung kommen nicht hinzu. Die bestehende private Busy-Fixture bleibt auf die Stop-Darstellung beschränkt.

Die strikten Breiten-/Höhenprüfungen `>= 48f` bleiben. Geschwisterrechtecke werden paarweise und tatsächlich sichtbare globale Rechtecke auf Überschneidung geprüft. Die schon vorher vorhandene 1-Pixel-Toleranz betrifft ausschließlich vollständige Sichtbarkeit/Clipping, nicht die 48-dp-Mindestgröße. Vier Profile und Fontmatrix sind unverändert. Der frühere Acht-Ziele-Legacy-Zähler wird durch die expliziten elf tatsächlich vorhandenen Shell-/Chat-Rail-Ziele ersetzt; dies ist keine Auslassung eines aktuellen Railziels.

Der bisher verlangte Coding-Entwurf bleibt als eigene reale Route über Mehr → Entwicklerwerkzeuge → CODING-AUFTRAG ENTWERFEN erhalten. Wo ein tatsächlicher Header-/Dock-Mehr-Einstieg sichtbar ist, wird er genutzt. Andernfalls verlangt V1 einen sichtbaren Mehr-Einstieg im Systemzentrum. Dieses letzte Verhalten ist eine bewusst neue Produktanforderung der RED-Hypothese und auf der unveränderten Basis noch nicht vorhanden; ein privater Navigations-Bypass ersetzt es nicht.

Der vorhandene Appearance-Dialog besitzt drei Radiooptionen und zwei Dialogaktionen. V1 verlangt diese fünf bestehenden Optionen/Aktionen ausdrücklich weiter und ergänzt Kompakt. Identität wird vor Recreation innerhalb der alten Activity und danach innerhalb der neuen Activity geprüft; eine unmögliche Instanzidentität über Activity-Neuerzeugung hinweg wird nicht behauptet.

## Offene Nachweise und spätere Produktprüfung

- Der C1-RED-Lauf und spätere GREEN-Lauf müssen diese eingefrorenen Quellstände jeweils selbst belegen. Statische Assertionzahlen sind keine Laufzeit- oder Abdeckungsnachweise.
- Der spätere Produktpatch muss Kompaktpräferenz, echte aktive Shellgeometrie, exakte Normalrückkehr und den sichtbaren Mehr-Rückweg implementieren, ohne die bestehenden Chooser-Handler zu verlieren. Er ist nicht Bestandteil dieses Reviews.
- Native 3D und Bewegung werden hier als bestehende Chooseroptionen/-aktionen auf Vorhandensein geprüft; der Test ist kein Filament-, GPU-, Geräte- oder Bewegungs-E2E-Nachweis.
- Stop bleibt wie vorher eine Darstellungsprüfung auf synthetischen Busy-Zustand. Tatsächlicher Inferenzabbruch wird damit nicht zusätzlich bewiesen.
- Die vorhandenen Privacy-/Research-Zustimmungsgrenzen und `busy == false`-Prüfungen bleiben bestehen. Der Review behauptet weder Modellinferenz noch externe Toolausführung.

## Durchgeführte statische Verifikation

Read-only `git show`, `git rev-parse`, `git diff --name-only`, `git ls-tree` und Python-Quell-/Hashinspektion: Exit 0. Der Patch wurde ausschließlich im Speicher auf den unveränderlichen Basistext übertragen; alle Hunk-Kontexte passten und das Ergebnis entsprach bytegenau dem Zielblob. Alle acht Methoden und Qualifiers wurden verglichen, die Fontmatrix und die tatsächlichen elf Railziele überprüft, neue Shortcut-Muster ausgeschlossen. Die fünf importierten JUnit-Assertionfamilien haben 73 → 125 direkte Aufrufstellen; diese Zählung umfasst weder Helperaufrufe noch dynamische Ausführungen und wird nicht als Testbeweis verwendet.

Eine erste eng begrenzte Inspektionsausführung meldete eine harmlose Python-`SyntaxWarning` für ein nicht-rohes Regex-Literal; sie endete mit Exit 0. Die nachfolgenden Prüfungen benutzen rohe Regex-Literale. Tests/Builds: NOT_RUN_BY_REVIEWER.
