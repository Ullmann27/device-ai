# JARVIS – Avatar/Glow-Integration und drei unabhängige Prüfschienen (05.11.2026)

## Verbindlicher Kontext
- Repo: `Ullmann27/device-ai`, Arbeitszweig: `claude/continuum-jarvis-cockpit-z7bo60`, Draft-PR #565. **Kein Merge auf main.**
- Bestehende Avatarkopf-GLB bleibt; bestehende Continuum-Kugel bleibt im START-Cockpit.
- Keine Signatur-, Paket-, Versions-, Publisher- oder Modelländerung. Keine APK-Veröffentlichung.
- Parallele Teamarbeit auf dem Zweig läuft. Neue Commits nur auf jeweils frisch gelesener HEAD-SHA; keine fremden Änderungen überschreiben.
- Quelle: `docs/research/omega/CHATGPT6_CONTINUATION_JARVIS_VISUAL_20261105.md` und `docs/evidence/claude-jarvis-visual-20261105/README.md`.

## Tatsächlich implementierte Änderungen

| Aufgabe | Code-Commit | Pfade |
|---|---|---|
| Original-Kugel (bereits auf HEAD vorhanden) | vor diesem Paket | `ContinuumHologramView` nutzt `ContinuumHologramRenderer.draw(..., transparentBackdrop = true)`; `orbPulseLevel` / `recallBrightness` eingebunden |
| Bot-2-Randlauf übernommen | `228be254`, `a0dc9541`, `15b4b4a5` | `ui/JarvisTileEnergy.kt`, `ui/JarvisTiles.kt`, `test/ui/JarvisTileEnergyTest.kt` |
| Aura-Wiederaufnahme korrigiert | `97c74b93`, `dda39575` | `ContinuumHologramView.updateAuraFraming()`, `uiTest/ContinuumAuraLifecycleUiTest.kt` |
| Intentgeste der echten Bedienung | `7fd6e179` | `avatar/AvatarContract.kt`, `avatar/AvatarDirector.kt`, `avatar3d/AvatarMorphMapper.kt`, schmaler `AgentActivity`-Adapter, `test/avatar/AvatarTaskGestureTest.kt` |

### Bot-/Prüfschiene A: Avatar/GLB und lebendige Bewegungen
- `AvatarTaskGestureTest` durchführen: Fensterblick, echte Beobachtung hat Vorrang, Bestätigungsnicken, Denkbewegung, STOP, reduzierte Animation.
- `AvatarMotionEvidenceTest` und bestehende Lippen-/Blinzel-/Neck-Tests gegen HEAD laufen lassen.
- 3D-Filament und echte GLB-Pixel *nur* anhand nativer GPU-Ausgabe abnehmen; Robolectric-2D-Ersatzkopf ist **keine** 3D-Abnahme.
- Aktion/Tool-Erfolg niemals aus dem dekorativen Nicken ableiten; Kamera nur bei Einwilligung.

### Bot-/Prüfschiene B: Glow, Tiefe, Randlauf und Referenz
- Donor #549 wird **selektiv** umgesetzt: 13-s-Rundweg, cyan → blau → violett → magenta, Glow-Tail, keine zusätzliche Uhr.
- Prüfen, dass die Animation nur läuft, wenn Shell-Zeit und Sichtbarkeit es erlauben. Bei reduziertem Motion-Modus, Disabled und zu kleinen Kacheln kein Bead.
- Bestehende Farben mit `tools/measure_ref5_colors.py` und `docs/JARVIS-REF5-FARBEN.md` abgleichen. Keine ungeprüften %- oder Fold7-Farbtreue-Behauptungen.
- Referenzvideos sind privat in Google Drive; keine privaten Medien in einen öffentlichen GitHub-PR kopieren.

### Bot-/Prüfschiene C: Integration, Konfliktprüfung und Geräteabnahme
- Gleichzeitige Änderungen der anderen Bot-Zweige gegen aktuelle PR-HEAD prüfen; C1 (#519) erhält nur differenzierte, einzeln begründete Cherry-picks.
- Zielprüfungen (je nach Gradle-Source-Set-Konfiguration):
  `./gradlew :app:testDebugUnitTest --tests at.heinz.deviceai.avatar.AvatarTaskGestureTest --tests at.heinz.deviceai.ui.JarvisTileEnergyTest`
  und die vorhandenen UI/Robolectric-Tests inkl. `ContinuumAuraLifecycleUiTest`, `ContinuumVisualEvidenceUiTest`, `ContinuumCockpitHandsFreeUiTest`.
- Lifecycle prüfen: START → Dialog → START, Cover/Fold7 offen, Pause/Resume, STOP nach TTS, reale Berechtigungsabfrage.
- Vollständiges Android Build/AGP/Lint/Tests und echte Filament-GPU-Kamera- und TTS-Abnahme stehen bis zu einem protokollierten Lauf auf HEAD auf **NOT_RUN**.

## Offene Gates / Grenzen
- Die gerade genannten neuen Tests wurden auf diesem Gesamt-HEAD **noch nicht als GREEN nachgewiesen**.
- 3D-Gerätebild, reale Blick-/Gesichtserkennung, Fold7-FPS und Bildvergleich zu privaten Video-Referenzen: **NOT_RUN**.
- Kein Bot startet durch dieses Dokument automatisch. Es beschreibt abgegrenzte Arbeitspakete für bereits angeschlossene Entwickler/Prüfer.

## Fortsetzung 05.11.2026 – zusätzliche Produktänderungen

| HEAD/Commit | Abgrenzung |
|---|---|
| `49574620` + `b8e3c29e` | Denkgestik endet beim Zustandswechsel; langes tatsächliches Denken/Lernen/Forschen behält minimale Kopf- und Augenmikrobewegungen ohne zweite Frame-Uhr oder Frame-Collections. Zwei zusätzliche Tests in `AvatarTaskGestureTest`. |
| `c7819628` | Bestehende beiden Neon-Kachelrandstrokes wiederhergestellt, **zusätzlich** abgerundeter 13-s-Lichtpunkt mit Nachleuchten. Neue `JarvisTileAmbientNeonTest`-Proxyprobe; ursprünglicher Zwei-Striche-Vertrag bleibt erhalten. |
| `c744a3b8` + `13141935` | Bei 3D-Laden oder Filament-Fehler bleibt der vorhandene native 2D-Hologrammkopf sichtbar. Die TextureView startet transparent und wird erst nach gültigem 3D-Frame gezeigt. Zwei neue Robolectric-Pixelvergleichstests. |
| `29673102` | `JarvisHeadAura` erhält ausschließlich echte `energy` und `audioEnergy` aus `ContinuumHologramView`; Ready-Basisglow bleibt unverändert, gebundene Verstärkung bei Arbeit/Sprache. Drei neue Signalgating-Tests. |

**Verifizierte Code-Lesegates**: Auf HEAD `29673102378f4f6da910719cb78564e69b7a2b5c` wurden 18 von 18 gezielten Quellcode-Verträgen über GitHub-Readback positiv geprüft. Dies ist **kein** Gradle-/JUnit-/Robolectric-/Filament-/Fold7-Test. Zusammengefasste frühere Tests nicht als neue Ergebnisse ausgeben.

**Neue notwendige Abnahme in den drei bestehenden Prüfschienen**:
- A: `AvatarTaskGestureTest` inkl. Zustandswechsel/Long-Think sowie TTS-STOP, reale GLB, `ContinuumAvatarFallbackUiTest` bis erster GPU-Frame und nach 3D-Absturz.
- B: `JarvisTileAmbientNeonTest` (alter Zwei-Stroke-Vertrag und neue Beads), `JarvisTileEnergyTest` (Rundweg), `JarvisHeadAuraSignalTest` (keine falsche Aktivität), Farben vor/nach nach Ref5.
- C: kombinierter Kotlin/Gradle-/UI-Test auf exakt gleichem Commit, danach echte Fold7-Filament-Aufnahmen inkl. Pause/Resume, Fold-wechsel, STOP und FPS. Separater GPU-Nachweis verpflichtend.

Mögliche begrenzte Host-Befehle (nur in vorhandener Android-SDK-Toolchain, keine GitHub-Actions-Neustarts):
```bash
./gradlew :app:testDebugUnitTest --tests 'at.heinz.deviceai.avatar.AvatarTaskGestureTest' --tests 'at.heinz.deviceai.ui.JarvisTileEnergyTest' --tests 'at.heinz.deviceai.ui.JarvisTileAmbientNeonTest' --tests 'at.heinz.deviceai.ui.JarvisHeadAuraSignalTest'
./gradlew -PcontinuumUiWidgetTests=true :app:testDebugUnitTest --tests 'at.heinz.deviceai.ContinuumAvatarFallbackUiTest' --tests 'at.heinz.deviceai.ContinuumAuraLifecycleUiTest'
```

Die obigen Befehle sind **noch nicht auf diesem HEAD ausgeführt**. Kein APK-Build, kein Release, kein Main-Merge, keine Originalsignaturänderung; privat gespeicherte Referenzvideos werden nicht publiziert.
