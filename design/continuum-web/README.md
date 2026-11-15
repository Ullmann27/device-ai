# Continuum · räumliche Webgestaltung

Arbeitsstand: 15. November 2026. Verantwortlich für diesen abgegrenzten Gestaltungsschritt: ChatGPT / Astra.

## Auftrag und Grundlage

Die vorhandene Continuum-Identität bleibt erhalten: Originalkopf und Originalkugel aus dem bestehenden Repository. Das neue Raster umfasst Chats links, Werkzeuge oben, Avatar und Arbeitsfläche in der Mitte, Diagramme und Quellen rechts sowie eine erreichbare Eingabe.

Ausgangsbranch: `claude/code55-cinematic-evolution-20261112`.
Quell-Pin: `1b90611388cc2b1978ddfe32064a25dd3a08cee1`.
Ziel dieser Übernahme: `design/continuum-fui-web-20261115`, als überprüfbarer Vorschlag gegen den Code55-Gestaltungsstand.

## Bewahrte Originale

- `app/src/main/assets/avatar3d/continuum-avatar.glb`: 12.038.416 Byte; SHA-256 `c8b88385c73fa01596a5734d20859440b1ecaef06cb1abea6cd7a47e62b08b9a`. Bestehende Geometrie, Materialien, Rig und Morph-Ziele werden nicht ersetzt.
- `ContinuumHologramRenderer.kt`, `ContinuumOrbitalRing.kt`, `ContinuumHologramPlexus.kt`: vorhandene Kugel mit fünf äußeren Ringen, Kernringen, Netz, Partikeln und Unendlichkeitszeichen.
- Bestehende Android-Aufrufwege `AvatarDirector`, `AvatarMorphMapper`, `FilamentAvatarRenderer` und `HollywoodFui*` bleiben Bezugspunkte der Gestaltung.

## Aktueller Integrationsstatus

Dieser erste Checkpoint sichert Auftrag und Herkunft. Die Webdateien und Portierungen folgen in derselben Linie. Der vollständige neue Kopf- und Kugelverbund ist hier noch **WIP / UNVERIFIED**.

Die bereits lokal ausführbare Oberfläche hat 21 DOM-Integrationsprüfungen bestanden: Navigation, isolierte Chatverläufe, Phasen-/Quellenverbindung, Unterbrechung, Negation und Einstellungen. Das belegt die geprüfte Weblogik; Browserlayout, WebGL und Fold7 sind dadurch nicht freigegeben.

## Abnahme und Anschluss

- PORTIERT: erst nach tatsächlich abgelegtem Original-Renderer-Port und angebundenem GLB.
- VERIFIZIERT: getrennte Belege für DOM, Originaldatei-Hashes, Canvas-Render, Blender-Rig-Erhaltung und Paketintegrität.
- GERÄTEBESTÄTIGT: offen, bis der konkrete Fold7-Lauf beobachtet wurde.
- Backendaktionen bleiben in der Gestaltungsfassung als Demonstration beschriftet. Keine erfundenen Downloads, echten Agentenaufträge oder KI-Antworten.
- Keine Änderung an Android-Paketidentität, Signierung, Modellgewichten, ToolGate oder produktiven Nutzerdaten. Kein Main-Merge und keine APK-Veröffentlichung.

Der aktuelle Nutzerauftrag betrifft die tatsächlich bedienbare visuelle Ausarbeitung und den Austausch zwischen geeigneten Designwerkzeugen. Es wird kein Fortschritt bei EXP-00 bis EXP-16 behauptet; bestehende Auftrags-, Speicher- und Modellverträge bleiben kompatibel und unangetastet.
