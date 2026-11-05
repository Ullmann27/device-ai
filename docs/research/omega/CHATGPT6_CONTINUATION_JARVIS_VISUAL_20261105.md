# Weiterbearbeitungsauftrag an ChatGPT 6 – Continuum JARVIS Visual (05.11.2026)

Repository: `Ullmann27/device-ai` · Branch: `claude/continuum-jarvis-cockpit-z7bo60` (Draft-PR, offen lassen)
Basis: PR #564 HEAD `c4cd3b58627713d839563046822521aac9caf0f1`. Paket bleibt `at.heinz.deviceai.continuumlocal`.
Kein Main-Merge, kein Signaturwechsel, keine Veröffentlichung, kein Löschen von Nutzerdateien, keine kostenpflichtigen Dienste.

## Bereits erledigt (Claude, dieser Branch – Host-Tests grün, NICHT auf dem Fold7 geprüft)

| Commit | Inhalt | Beleg |
|---|---|---|
| `6a6d626e` | Kopf am rechten Rand nicht mehr abgeschnitten (`avatar3d/AvatarFraming.kt`): Fenster hält die gemessene Posen-Reichweite; Fold7-Bühne 884×760 → Kopf 87,5 % bei Anker 0,60 statt abgeschnitten bei 0,70; 1536×1024 unverändert. Hals/Kiefer: `Neck` trägt 65 % der Drehung (Kantendehnung p99 129 % → 42 %). Lid: Minimum-Jerk-Kurve. | `AvatarFramingTest` 6/6, `AvatarNeckSplitTest` 2/2, `AvatarBlinkEasingTest` 2/2 (LBS der echten GLB) |
| `f06a20ba` | #564-Reviewbefund: Textfeld-Kontakt im Freisprechen leitet nächsten Sprachzug nicht mehr zu CHAT | `ContinuumCockpitHandsFreeUiTest`: RED (`expected START but was CHAT`) → GREEN 2/2 |
| letzter Commit | Glas/Glow: gemeinsames Kartenmaterial `JarvisReferenceHud.surface` – Neonränder +16 % (ausgewählt +15 %), weiter Tiefen-Halo, dunkleres Glas; Nachweis-Tests `AvatarMotionEvidenceTest`, `ContinuumVisualEvidenceUiTest` | siehe PR-Beschreibung |

Volle Android-Suite auf `f06a20ba` (Host, root-Container): 3.285 Tests, 3 umgebungsbedingte Fehlschläge (root umgeht Schreibschutz: `ContinuumBotRosterStorageUiTest`, `ChiefBotStorageBoundaryTest`; Shell-Locale nicht UTF-8: `ShellExecutionTest`), 17 übersprungen.

## Deine Aufgaben in dieser Reihenfolge

1. **Originale Continuum-Kugel transparent und animiert wie in der alten App** (Besitzerwunsch, höchste Priorität).
   Quelle: PR #563 (`research/continuum-cockpit-voice-original-orb-20261105`) und #539 (Orb-Donor). Nur die originale Kugel
   (`ContinuumHologramRenderer`, `drawOriginalCore`-Pfad, `transparentBackdrop = true`), **nicht** `ContinuumBrainOrbRenderer`.
   Keine Hintergrundfläche, Ringe unabhängig rotierend, Energiekern, Partikel. Echte Signale: `orbPulseLevel`, `recallBrightness`.
2. **Leuchteffekte/Tiefe exakt wie `docs/design-screenshots/ref5-fold7-offen.png`** (Fold7 offen) bzw. 59509 (1536×1024):
   - Messen statt schätzen: `tools/measure_ref5_colors.py` gegen Robolectric-Aufnahmen (`ContinuumVisualEvidenceUiTest`,
     `-PcontinuumUiWidgetTests=true -PcontinuumUiEvidenceDir=…`). Zielwerte aus `docs/JARVIS-REF5-FARBEN.md`.
   - Animierter Randfluss Cyan→Blau→Violett→Magenta auf echten Kacheln: PR #549 (`bot2/isolated-neon-tile-energy-20261105`)
     selektiv übernehmen; Leerlauf-Puls, Berührungs-Licht, Öffnen/Schließen über `JarvisMotion` (eine Frame-Uhr).
3. **Avatar-Gesten bei Auftragsausführung**: Blick zum geöffneten Fenster als eigener Director-Intent (nicht `GazeObserved`),
   Nicken bei Bestätigung, Denk-Mikrobewegung; Werte über `AvatarMorphMapper`, Zeitreihe prüfbar mit `AvatarMotionEvidenceTest`.
4. **Fold7-Geräteabnahme** (Besitzer): Kopf nicht abgeschnitten (Fold7 innen + außen), Kinn nicht über der Dialogkarte,
   Hals ohne Riss bei Kopfdrehung, Mund nur bei echter TTS, STOP schließt sofort. FPS/Framezeit mit `JarvisShellView.frameLogging`.
5. **Optional, parallel (Dialog-/Gedächtnisteam)**: `VoiceSessionCoordinator`, Gedächtnis R12/R13 (#548, #553), 16M-LM1 nur im
   SHADOW-Modus stückweise trainieren (Checkpoints 1/8/32/128, unabhängiges Holdout), Gemma bleibt primär.

## Offene Punkte (ehrlich)
- Referenzvideos (Drive „Jarvis Video“, privat) konnten nicht geladen werden (Connector-Limit). Videos per „Jeder mit Link“
  oder als Einzelbilder bereitstellen, dann Timecode-Abgleich nach `CONTINUUM_VIDEO_REFERENCES_20261105.md`.
- `59509.png` und `Continuum_Avatar_Alle_Ansichten.png` liegen nicht im Repo; nicht ungefragt veröffentlichen.
- Kein Filament-Render auf dem Host; alle 3D-Aussagen sind LBS-Geometrie der echten GLB, keine GPU-Pixel.

## Regeln
Quelle vor Änderung prüfen, RED→GREEN, Tests mit exakten Zahlen und Commit-SHA melden, Host- und Gerätebelege trennen,
keine erfundenen Daten/Erfolge, gemeinsame Dateien (`AgentActivity`, `JarvisCockpitGrid`) nur als kleinste Patches an C1 (#519).
