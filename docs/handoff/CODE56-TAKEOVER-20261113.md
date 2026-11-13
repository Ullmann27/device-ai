# Code56 — direkte Übernahme und Renderer-/QA-Reparatur, 13.11.2026

Der Besitzer meldete, dass Opus sein Nutzungslimit erreicht hat. ChatGPT übernimmt diese Arbeit selbst; keine neu gestarteten Opus-Agents werden behauptet.

## Geprüfte Ausgangspunkte

- Produktzweig PR #830: `974b04ac1f55ccd3d9f32bfd9495ed82a1e4bba7`.
- Bestehender QA-/APK-Zweig: `da429fa7fe6c822b8a71bf2a5cbcd82b839da4f2`, identische Produktquellen plus Code56-Workflow.
- Lauf 43413266202: ARM64 und Node/backend erfolgreich; 5413 Android-Fälle ausgeführt ohne Fehler, aber 18 SKIPs statt 17. Zusätzlicher Fall: `ContinuumVisualEvidenceUiTest.fold7InnerMotionClip`. Lint damals übersprungen. Keine vollständige Abnahme.

## Eigene Änderungen

1. Renderer-P0: `FilamentAvatarRenderer.updateWorld` übergab bei Flugbeginn eine Kopie des gesamten Bühnenframes mit ungefähr -103 Grad an `FilamentCockpitStage.update`. Der strikt gültige Bereich bleibt -8..8 Grad. Jetzt geht der unveränderte Frame plus ein eigener Monitorwinkel durch den vorhandenen Renderer. `CockpitStageFrame.monitorTransform(turn)` rotiert nur die Monitormatrix, vor ihrer ungleichmäßigen Skalierung. Monitorrückseite und Monitorlicht verwenden dieselbe Matrix. Raum, Seitenflächen, Kugel und native Ruheprojektion erhalten den unveränderten Frame. Kamerafahrt, Dolly-Zoom, Avatar und Effekte bleiben bestehen.
2. Der Motion-SKIP hatte eine konkrete Konfigurationsursache: `motionClip` verlangt `continuum.ui.evidenceDir`; `app/build.gradle.kts` setzt dieses Property nur bei `-PcontinuumUiEvidenceDir`. Der volle Code56-Lauf liefert jetzt dieses Verzeichnis und archiviert die tatsächlichen Host-PNGs. **Die 17er-Skip-Liste wird nicht erweitert.** Das ist Robolectric-NATIVE-Canvas mit 2D-Kopf, kein Filament-/Fold7-Nachweis.
3. Separater kleiner Renderer-Regressionstest im vorhandenen Workflow; vollständige Android-Suite weiterhin ungefiltert. Lint läuft auch nach einem Test-/SKIP-Fehler, solange der Lauf nicht abgebrochen wurde. Ein Fehler bleibt trotzdem ein Fehler.

## Tatsächlich lokal ausgeführt

Der Container hat Kotlin/JDK, aber kein nutzbares GitHub-DNS und keinen eingerichteten Android-/Filament-Gerätezugriff. Die kleine Produktionsquelle wurde daher über den GitHub-Connector bezogen und vor Ausführung per Git-Blob-ID auf Identität geprüft.

- Alter `CockpitStageFrame.kt`-Blob: `792f0aba1d74d1e06d8b30348bb57f49d23ec8da` — identisch zur GitHub-Quelle.
- Alte Aufrufkombination: gültiger Ruheframe, dann `copy(yawDegrees=-103f)` → ungültig, erwarteter roter Regressionslauf.
- Derselbe eigenständige Prüfer mit alter Quelle: EXIT 1, fehlende separate Präsentationstransformation.
- Reparierter Produktionsblob: `5d65ef7e7147ef18928947e826bf845796f961c0`.
- Eigenständiger Kotlin-Prüfer danach: EXIT 0, **3636 Matrix-Stichproben** über drei Bildschirmformate, vier Ruhewinkel, drei Neigungen und 101 Flugwinkel; sechs ungültige Eingaben abgewiesen. Keine JUnit-/Android-/GPU-Zahl, keine Leistungsbehauptung.

Reproduktion mit Kotlin/JDK:

```sh
kotlinc app/src/main/java/at/heinz/deviceai/avatar3d/CockpitStageFrame.kt \
  tools/code56-renderer-qa/MonitorPresentationProbe.kt -include-runtime -d /tmp/code56-monitor-probe.jar
java -jar /tmp/code56-monitor-probe.jar
```

Sechs neue reguläre JUnit-Fälle in `CockpitMonitorPresentationTest` ergänzen Flugmathematik und einen Quell-Aufrufpfadvertrag. Ihre tatsächliche Android-/Gradle-Ausführung muss aus dem neuen CI-Lauf abgelesen werden; sie ist durch den lokalen Kotlin-Prüfer nicht ersetzt.

## Integration und Grenzen

Änderungen gehen in die bestehende Code56-Linie #830 und denselben QA-/APK-Zweig, per Fast-forward mit SHA-Lease. Kein neuer Produktzweig, kein Main-Merge, kein neues Rendersystem, kein Signierschlüssel, keine Veröffentlichung. Alte Dokumente und alte APKs sind keine Belege des neuen SHA.

Native Filament-Frames, echte Android-Bildquelle/Eingaben, Fold7-Sprach-/Temperaturprüfung und das vollständige Altzweig-Integrationsregister #831 bleiben getrennte offene Arbeit. Ein grüner Hostlauf allein ist keine Geräteabnahme.
