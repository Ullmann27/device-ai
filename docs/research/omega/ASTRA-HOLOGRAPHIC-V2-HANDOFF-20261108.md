# Astra: holografische Glaskonstruktion V2

Status: WIP / NOT_DEVICE_VERIFIED. Experiment: VIS-HOLO-V2-20261108.

## Integrationsvertrag

Eigener Branch `astra/continuum-holographic-v2-20261108`, Basis
`cb11db2f967110680da92db8c0b07f01f3d2b47d` aus #653. #651
`09722adf6c88453ac7607a2a484ae97f00a8fc00` ist nachweislich Vorfahr.
SOL-/Fable-Branches, main, Signierung, APKs und Modellgewichte bleiben unveraendert.
Kanon #642, Auftrag #638/#643/#645 und beide Review-Hinweise aus #651 gelesen.
EXP-00..12: keine Job-, Tool-, Memory- oder Modellvertraege geaendert; ausschliesslich
Praesentation bestehender Ereignisse. Kein neuer Avatar, Eventbus oder Renderer.

## Referenz- und Codeaudit / IST-SOLL

Tatsaechlich betrachtete Repository-Bilder: `ref5-fold7-offen.png`,
`jarvis-master-20261104.jpg`, `ref4-phone-hochformat.png`,
`ref2-fold7-tablet-phone.png`, `phone-einflug-250ms.png` in
`docs/design-screenshots/`. Das letzte Bild ist eine fruehe schematische Vorschau,
kein Nachweis des aktuellen Geraetestands. Die Referenzen sind Designziele,
ihre dargestellten Statuszahlen werden nicht als echte Daten uebernommen.
Nicht erreichbare Chat-Anhaenge/Gemini-Originalvideos werden nicht als gesehen behauptet.

| Bereich | Nachgewiesenes IST | SOLL / priorisierter Eingriff |
|---|---|---|
| Material / Licht (P1) | Cinematics verwendet absolute Gradienten 0..1, obwohl die Scheiben hunderte dp gross und versetzt sind; deshalb praktisch einfarbige Raender und fehlender Reflex | Lokale, scheibengrosse Cyan/Violett-Verlaeufe, dunkles Glas, gerichtete Reflexe, schmale scharfe Lichtkante |
| Tiefe / Schaerfe (P1) | Host fuegt Overlay nach dem nativen Arbeitsfenster ein: volle hintere Glasscheiben, Raster und Canvas-Profiltexte liegen ueber bedienbaren Texten | Rueckwaertige Konstruktion unter dem nativen Inhalt; Kopf/Orb/Eingabe geschuetzt; native sp-Texte bleiben vorne |
| Animation (P1) | Fenster: Shell-Uhr und 600-ms-Einflug; FUI: eigene ValueAnimator und 900-ms-Aufbau/420-ms-Abbau; Fenster schliesst in 300 ms | Eine Shell-Zeitbasis, abgestimmte Freigabe des Inhalts und vollstaendiger Rueckbau; keine zweite Animationsuhr |
| Dauerlicht / Budget | Ankerhalo und Glint bleiben nach Aufbau sichtbar (#651); Overlay zeichnet bei aktiver Arbeit unabhaengig von Sichtbarkeit/reduzierter Bewegung nach | Vollstaendiger Anker-Fade, kein eigener Live-Callback; bestehendes JarvisRenderQuality bleibt massgeblich |
| Avatar / Kugel | Original-GLB, MorphMapper, Neck/Head/Eye/Jaw, Blink/Viseme, PoseReducer und Director vorhanden; originale Kugel/Ringe laufen im HologramView | Assets/Rig/kanonische Pose unveraendert erhalten; keine unbelegten Behauptungen ueber neue Gesichtsqualitaet |
| Sieben Welten | DOCUMENT_BLUEPRINT, APP_CONDUIT, CODE_FORGE, SWARM_GRID, EVIDENCE_CONSTELLATION, CHRONO_GAUGE, SECURITY_XRAY vorhanden; typisierte LiveData und echte Timeline | Gemeinsames Material/Bewegungsgesetz fuer alle; keine erfundenen Agenten, Fortschritte oder Ergebnisse |
| Layout / Dialoge | Native WorkWindow-Geometrie schuetzt Gesicht, Kugel und Dialog; graue Lern-/Backlog-Dialoge und Navigationsduplikate sind separate Owner-Befunde | In diesem ersten Meilenstein keine riskante Neuverdrahtung der Bestaetigungs-/Berechtigungsdialoge; bleibt offen |

Untersucht: ContinuumHologramView, FilamentAvatarRenderer, AvatarMorphMapper,
HollywoodFuiConstruct/Cinematics/StageOverlay/LiveData, JarvisWorkWindow/Host,
JarvisMotion, JarvisRenderQuality, JarvisShellView, ContinuumPainter und
AndroidContinuumPainter. Filament nutzt feste Belichtung, drei existierende
Lichter und begrenztes Bloom; keine zusaetzlichen GPU-Paesse vorgesehen.

## Nachweise / Umgebung

Initial: JDK27, Gradle8.11.1; kein adb, Emulator, Android-SDK oder kotlinc auf PATH.
Vorschauversuch: FAIL (kotlinc fehlt). Android-Versuch: BLOCKED (JDK27 nicht
unterstuetzt); Google-SDK-Host `dl.google.com` nicht per DNS erreichbar.
Kotlin2.2.21 fuer vorhandene Host-Vorschau lokal wiederhergestellt.
Tests, Frames und konkrete Ressourcenmessungen folgen; bisher kein PASS behauptet.

## Rueckfall / naechster Einstieg

Geplant: ein zentraler reversibler Praesentationsschalter fuer Film-Layer und
Einflugneigung, ohne Verlust von Originalavatar, Sprache, Inhalt oder Tool-Freigaben.
Rollback nur eigener Commits, niemals Reset der SOL-/Fable-Branches.
Naechster Einstieg: RED-Tests fuer Anker-Fade, scheibenlokale Gradienten,
eine Zeitbasis, reduzierte Bewegung und statischen Rueckfall.
