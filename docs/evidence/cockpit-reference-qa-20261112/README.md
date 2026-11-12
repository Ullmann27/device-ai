# Referenzcockpit: geprüfter Sammelstand

Produktquelle: `3a952f9d8f64e99a9a851b964b5392d8d39084fb`.
Basis enthält den gesamten Hauptstand `f16e96f183`, dessen Tree dem öffentlichen
Checkpoint `a45e874b4420598b7c549d5bb7176956951f02d9` entspricht.
Spätere Dokumentationscommits ändern die aufgeführten Produktdateihashes nicht.

## Tatsächliche Ergebnisse

| Prüfung | Ergebnis | Aussage |
|---|---|---|
| Geänderte Kotlin-Produktpfade und Unified-Abhängigkeiten | PASS | Scoped Kompilation gegen eingefrorene Integrationsklassen |
| World/Layout/Framing/Projektion/Material/Motion | 33/33 PASS | Tatsächliche Originalgeometrie, perspektivische Transformationen, unveränderte opake Materialien |
| Native Feed-Übergabe und gebundene Monitorfahrt | 7/7 PASS | Robolectric 4.14.1, SDK 28, NATIVE; sichtbares Fenster, identischer Feed, Cleanup und Umkehr |
| Historischer Canvas-Pixelhelper | 1/1 PASS | Nur alter Helper; ausdrücklich kein Unified-GPU-Pixelbeleg |
| Seed und Unified-Assets | PASS | Separate Prüfreports unter `../cockpit-reference-materials-20261112/` |

Die UI-Fixtures berücksichtigen den aktuellen Vertrag: Filament besitzt die
Bildtextur; die native Statusfläche übergibt Feed-Referenzen. Bloßes Verbinden
eines Feeds startet keinen Arbeitsflug. Erst ein Ereignis mit passender
Aufgabe, Aufnahmesitzung und frischer Sequenz führt die Monitorfahrt aus.
Die unveränderten Prüfungen dieser Bindung bleiben im Hauptstand erhalten.

Ein vorheriger Runnerstart scheiterte an einem relativen Ressourcen-APK-Pfad.
Der finale Lauf verwendet eine explizite, kopierte Ressourcen-APK mit Hash
sowie eigene Testkonfiguration und aktuelle Produktklassen. Alte Fixtures
erwarteten Polling in der inzwischen abgelösten Canvas-Bildfläche; sie wurden
auf die tatsächlich angeschlossene Feed-Delegation umgestellt. Prüfungen auf
Sichtbarkeit, Identität, Abbruch und Rückkehr bleiben ausdrücklich enthalten.

## Umfang und Wiederholung

Die JSON-Belege enthalten tatsächliche Befehle, Test- und Produktdateihashes,
eingefrorene Klassen-/Ressourcenhashes und Exitcodes. Nur lokale Pfade wurden
durch benannte Variablen normalisiert. Für einen normalen vollständigen
Projektbuild lassen sich dieselben Klassen mit dem bestehenden Gradlepfad
ausführen: `-PcontinuumUiWidgetTests=true :app:testDebugUnitTest` und `--tests`
für `CinematicWorkstationLayoutTest`, `CockpitMonitorMotionTest`,
`CockpitWorldTransformsTest`, `CockpitStageFrameTest`,
`AvatarMaterialVariantTest`, `AvatarFramingTest`,
`CockpitMonitorLifecycleUiTest` und `CockpitMonitorMotionUiTest`.
Dieser Gradle-Gesamtlauf wurde hier nicht als bestanden ausgegeben.

Kein vollständiger APK-/Gerätebuild, keine Android-Filament-GPU-Aufnahme,
keine echten WhatsApp-/Office-Pixel, keine Fold7-FPS- oder Temperaturmessung.
Die getrennten Hostrender zeigen ausdrücklich Beispielinhalte.
