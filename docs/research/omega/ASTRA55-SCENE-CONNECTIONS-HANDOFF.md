# Astra 55 – vorhandene Szenenbausteine anschließen

## Gemeinsame Linie

Basis `d7a59c6f6855dab3884e44725d6a2c3a55634806`, Anschlussquelle
`df43ec3580b7939462619505a1e196cf0373879e` aus der vorhandenen Holographic-Arbeit.
Integrationsverantwortlicher ist der bestehende Hauptintegrator; dieser Support
arbeitet auf `work/astra55-support-scene-connections-20261112`.

## Bewegungsblock

`CockpitMonitorMotion` steuert dieselbe Monitorfläche im bestehenden
`ContinuumHomeView`. Der native Bildinhalt und Filament verwenden dieselben
Yaw-/Pitch-/Z-Parameter aus `CockpitStageFrame`. Verdeckte, abgehängte und
inaktive Ansichten beenden die Fokusanimation. Die native Beschriftung liegt
wieder vor der Monitorfläche. Der vorhandene Lifecycle-Test wurde übernommen.

**PORTIERT:** Motion, Matrix, Foreground und vorhandene UI-/Lifecycle-Tests.
**HOST-VERIFIZIERT:** direkte Kotlin-2.0.20-Kompilation der Motion-/Stage-Klassen
gegen echte Android-36-/Filament-1.56.0-/gltfio-1.56.0-APIs; 14 JUnit-Tests PASS.
Quellhashes und Ausgaben stehen unter
`docs/evidence/astra55-scene-connections-20261112/motion/`.
Der Nachweis umfasst noch keine vollständige App-Kompilation und keine
Ausführung der Android-UI-Tests.
**GERÄTEBESTÄTIGT:** `NOT_RUN`; Filament-GPU, Fold7, Screenshot und Bedienung offen.

## Material-/Sichtbarkeitsblock

Die vorhandenen neun Materialien und das indirekte Licht aus `df43ec3580`
sind angeschlossen. Der kombinierte Stand hat 77 Szenenknoten: Die sechs
`arch-cap`-Kappen aus dem Holographic-Zweig bleiben entsprechend dem Hauptstand
entfernt. Generator und GLB behalten die Hauptlinien-Deckenstreben aus
`84acc08cb4`. Der korrigierte kombinierte Assetpin lautet
`8dfe8e9ae86969c53f5a4c60474cae2cb72f23c517bca6a9134edd8d4c98db9c`.
Das GLB hat 14.084 Byte. Nachweise der fokussierten Wiederholung stehen unter
`docs/evidence/astra55-scene-connections-20261112/canopy-repair/`.
Der vorherige Pin `f7a2931d...` aus `1d1ff43d5f` ist überholt: Dort waren die
oberen Kappen irrtümlich aus dem Holographic-Zweig wieder eingefügt worden.

Aus dem Layoutkandidaten werden nur Schriftgrößenreserve und Monitorunterkante
übernommen. Die Hauptlinie behält Kopfrahmen `.70`, `AVATAR_SCALE=.76`, die
Originalkugel und ihre Schutzbox. Die übernommenen Visibility-Tests verwenden
den echten `pullBack`- und `CockpitMonitorMotion.pose`-Pfad. So bleibt das
bereits integrierte Avatarframing erhalten. Kein weiterer Renderer entsteht.

**HOST-VERIFIZIERT:** 24 bestehende Motion-/Matrix-/Layout-/Visibility-Tests PASS;
direkte Android-36-/Filament-1.56.0-API-Kompilation PASS. Zusätzlich kompilieren
`ContinuumHomeView`, der unveränderte Hauptmonitor und beide UI-Testklassen mit
Kotlin 2.4.0 gegen echte Android-APIs und vorhandene lokale Appklassen.
Diese Klassen wurden nicht als vollständige App neu gebaut; Herkunft und
Stabilitätsdigest stehen im Receipt. Kotlin 2.0.20 konnte die 2.4-Metadaten
dieser Bestandsklassen nicht lesen; der passende vorhandene Compiler löste
diesen Werkzeugkonflikt ohne Produktionsänderung.
Dieser zusätzliche 2.4-Nachweis gehört zum Anschlussstand vor der
Kappen-/Pinreparatur; die Reparatur wiederholt die 24 Hosttests und die
Filament-API-Kompilation mit dem korrigierten Assetpin.

Vorhandene Hostrender-Werkzeuge sind übernommen, mit dem Hauptlinien-Framing
verbunden und syntaktisch beziehungsweise per Kotlin kompiliert. Kein Bild-
oder Videolauf wurde ausgeführt. Repro, Hashes und Grenzen:
`docs/evidence/astra55-scene-connections-20261112/REPORT.md`.

## Grenzen und Fortsetzung

`VirtualComputerMonitorView` bleibt auf der Hauptlinie mit `sourceViewport` und
Klickabsicherung. Originalavatar und Kugel bleiben erhalten. Keine Paket-,
Signatur-, Release- oder Installationsänderung. Rückfall: jeweiligen
Anschlusscommit zurücknehmen.

Nächster Einstieg: Anschlusscommits samt anschließender Kappenreparatur in den
vom Hauptintegrator geführten Stand übernehmen. Beim Assetpin-Konflikt mit
`84acc08cb4` den oben genannten
kombinierten Pin und dessen Generator/GLB als Einheit behalten. Danach voller
App-/UI-Lauf auf dem gemeinsamen SHA, anschließend echte Geräteabnahme.
Lokale Sicherung `LOCAL_ONLY / NOT_PUSHED`; externes Schreiben bleibt der
übergeordneten Koordination vorbehalten.
