# C1 #519 – begrenzte Integration zur Prüfung

Quellstand: `7c3523cde9dae302a52d69818867a9abe8cf0fb7` auf Draft-PR #565.
C1 wurde durch diese Arbeit nicht verändert. Kein automatischer Merge.

## Gemeinsame Dateien

`AgentActivity` erhält gegenüber dem zuvor eingetroffenen Stand `a5723ac9`
**45 hinzugefügte und 16 entfernte Zeilen**. Der vollständige kleine Kontextpatch
liegt in [C1-AgentActivity-minimal.patch](C1-AgentActivity-minimal.patch).
Die gesamte Activity darf nicht durch eine fremde Branchversion ersetzt werden.
`JarvisCockpitGrid` ist gegenüber `b294fc91` byteidentisch; kein Grid-Patch nötig.

Die Anschlüsse betreffen nur die Darstellung des vorhandenen Systemstatus auf
START, dessen laufende Telemetrie, Zurück/Navigation/Pause und Freigabe der
Arbeitsfläche. STOP beendet Sprache und Fläche vor den bestehenden frühen
Rückgaben. Bestehende Werkzeugaktionen und Autorisierungen bleiben zuständig.
Bestätigungsnicken hängt an den letzten ausdrücklichen positiven Dialogbuttons;
ein automatisch erfolgreicher Web-Callback darf kein Nicken auslösen.

## Zusammengehörige Komponenten

- `JarvisWorkWindow`, `JarvisWorkWindowGeometry` und `JarvisWorkWindowHost`
  enthalten Glasfläche, geschützte Bereiche und Activity-Anbindung.
- `JarvisShellView.observeMotionFrames` benutzt bestehende Draws; die Diagnose
  beobachtet dieselben Draws passiv. Keine zusätzliche Animationsschleife.
- Der kompakte `JarvisShellLayout`-Zweig zeigt Kopf und Originalkugel nebeneinander
  und reserviert bei großer Schrift Platz inklusive nativer Pixelrundung.
- `AvatarDirector`, `AvatarPoseReducer` und `AvatarMorphMapper` verwenden eine
  gebundene Intent-Verarbeitung. Legacy-Gesten sind Adapter. Echte
  Kamera-Beobachtungen bleiben von Fensterblick und Zustimmung getrennt.
- `ContinuumHologramView` reicht die tatsächliche Bewegungspolicy an den Reducer
  weiter. Der Originalrenderer, GLB, Rig, Materialien und 21 Morphziele bleiben.

Die früheren Framing-/Neck-, Aura-, Fallback- und Randflussänderungen im Branch
sind eigene Voraussetzungen. Ihre Herkunft steht in der Fortsetzungsdokumentation;
dieser Activity-Patch ersetzt sie nicht. C1 muss die entsprechenden vorhandenen
APIs zuerst abgleichen und dann die kleinsten passenden Deltas übernehmen.

## Abnahmegrenzen

Exakte Laufbefehle, Quellenbindungen, Zwischenfehler und finale Ergebnisse stehen
im [README](README.md) und in den Roh-XMLs. Die Läufe überschneiden sich; ihre Zahlen werden nicht addiert. Die Referenz-/Bildprüfung zeigt Android-Host-Canvas; das
Original-GLB-Replay ist Blender-CPU. Beides ersetzt keinen Filament-/Fold7-Lauf.

Die [Besitzerabnahme](FOLD7-OWNER-ACCEPTANCE.md) bleibt offen. Kein Paket-/Signatur-
oder Versionswechsel, keine zweite App, keine neue Avatarfigur, keine Installation
und keine Veröffentlichung. PR #565 bleibt offen und Draft.
