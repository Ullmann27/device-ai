# Cinema: Originalavatar und reales Fensterlicht

Basis: `17491f4d8103f0e55836369333fab07e80e6e9c2` / PR #706.
Ziel: gemeinsamer eigener Cinema-Integrationszweig des Hauptagenten; kein Main-Merge, kein Release.
Verantwortlich: Avatar/Filament-Teilauftrag. Forschungszweig `cinema/avatar-window-light-20261110` ist nur lokaler Zulieferzweig.

## Befund und Anschluss

Bestehender produktiver Weg: `JarvisShellView`/`JarvisWorkWindowHost` → `WindowProjectedMotionSampler` → `CockpitAnimationBus` → `CinemaWindowGazeBridge` → `AvatarDirector` und `CinemaWindowLightState` → `FilamentAvatarRenderer.applyCoreLight` → dieselbe Filament-Punktleuchte.

- Tatsächlich projizierte Fensterkoordinaten lagen bereits als `gazeX/gazeY` vor. Die Lichtanbindung verlor Y; der Renderer bevorzugte teilweise Augenblick statt Fensterposition. Jetzt bleibt die X/Y-Fensterposition maßgeblich, auch während des Sprechens/Blickkontakts. Die bestehende Leuchte bewegt sich begrenzt horizontal **und vertikal**; keine zusätzliche Leuchte oder Renderpass.
- Farbe/Position/Offenstatus werden in einem unveränderlichen Snapshot atomar übertragen und pro Frame zusammen gelesen.
- Geschlossene fixe IDs wie `work-document` wurden vom Director verworfen und konnten beim nächsten Auftrag keine neue Blickbewegung auslösen. Interne Blick-IDs sind jetzt an Turn und Fenster gebunden.
- Verspätete Bewegungen beendeter Turns können das Fensterlicht nicht wieder aktivieren. CLOSED eines alten Turns beendet nicht dessen Nachfolger. Bei Schließen der aktuell beleuchteten Fläche wird die zuletzt aktualisierte verbliebene Fläche wieder maßgeblich.
- Registries sind begrenzt: 16 offene Fenster, je 64 terminale Turn-/Fensteridentitäten. Keine zweite Uhr, kein Renderer-Neustart.

Die bestehende Idle-Animation, Augen-/Kopf-Feder, Halsaufteilung, Sakkaden, Blinkvariation und TTS-Kette wurden geprüft und **nicht neu erfunden**. Dies ist kein neues Audiophonem-Modell. Lippenbewegungen bleiben an vorhandene PCM-/Wortbereichs-/gekennzeichnete Texttempo-Rückfallquellen gebunden.

## Originalasset

`app/src/main/assets/avatar3d/continuum-avatar.glb` SHA-256:
`c8b88385c73fa01596a5734d20859440b1ecaef06cb1abea6cd7a47e62b08b9a`.
Unverändert. Head, Neck, EyeLeft, EyeRight, Jaw vorhanden; FaceMesh und NeuralThreadsMesh je 21 Morph Targets. Authored DeepIndigoSkin-Rauheit 0,52.

## Verifikation

Status: **PORTIERT** in den echten Aufrufpfad, **VERIFIZIERT** nur auf Host-Ebene; **GERÄTEBESTÄTIGT: NOT_RUN**.

- RED: unveränderte Basis-Bridge/-LightState gegen zwei synthetische Lebenszyklusproben: **2/2 FAIL** (zweiter Auftrag schaut nicht erneut; spätes Event beleuchtet beendeten Auftrag). Reproduktionsquelle und Ausgabe unter `docs/evidence/cinema-avatar-light-20261110/`.
- GREEN: aktuelle `CinemaWindowGazeBridgeTest` + unveränderte `AvatarDirectorTest`: **12/12 PASS** mit Kotlin 2.4.0, JUnit 4.13.2, JDK 21 und Android-36-API-JAR; kein Emulator/keine GPU.
- GREEN: `SpatialWindowLightTest`: **5/5 PASS**, einschließlich vertikaler Grenzen, NaN, Thermik-/Motion-Rückfall und Snapshot-Kohärenz.
- Node-Quellverträge: **18/18 PASS** (`avatar-surface-radiance-wiring`, `code52-final-swarm-avatar-cinema-merge`, `code52-graphics-branch-intake`, `code52-cinema-source-contract`).
- Gemeinsamer abschließender Hostlauf einschließlich exakt derselben zwei RED-Proben: **19/19 PASS**, Ausgabe `green.txt`.
- `git diff --check`: PASS.
- Filament-GPU, Fold7, echte Audioqualität, Framezeiten und Thermik: **NOT_RUN**. Android-Gesamtbuild/-tests werden vom Integrator geprüft; Hosttests belegen keine Filament-Bildwirkung.

## Grenzen / nächster Prüfschritt

Die einzelne bestehende Leuchte folgt dem zuletzt aktualisierten noch offenen Fenster. Das ist eine begrenzte Lichtapproximation, keine physikalische Summe mehrerer emissiver Fenster. Bestehende thermische Reduktion deaktiviert den dynamischen Lichtzusatz weiterhin. Keine Kameraaufnahme oder Berechtigungserweiterung.

Nach Integration: zwei Aufträge nacheinander, Fenster oberhalb/unterhalb/seitlich, Sprecherunterbrechung, verspätete CLOSED-/TurnEnded-Ereignisse, STOP, Fehler, Falten und Rückkehr prüfen. Auf dem Fold7 die Richtung des Gesichtslichts filmen und mit der projizierten Fläche abgleichen. Kein Signierauftrag.

Rückfall: diesen isolierten Commit revertieren; Originalasset, Daten, Paketkennung und Signatur bleiben unverändert. Bestehende Cinema-/Lighting-Schalter und Thermik-Gates bleiben erhalten. Betroffen: vorhandene visuelle Cinema-Experimente; kein Eingriff in das separate numerische EXP-15-Forschungsmodul des globalen Registers.
