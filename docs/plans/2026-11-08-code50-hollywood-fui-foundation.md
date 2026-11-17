# Code 50 – Hollywood-FUI Foundation

Status: `SOURCE_INTEGRATION / DRAFT`

Basis: PR #612 (`d724af80939114fcd831e116030e4d10847f91fa`)
Arbeits-PR: #614

## Ziel dieses Vertical Slice

Die bestehende kanonische Arbeitsfenster-Pipeline erhält ein gemeinsames filmisches Konstruktionsgesetz, ohne einen zweiten Event-Bus, eine zweite Workscene oder erfundene Live-Daten einzuführen.

## Umgesetzt

- Reines, deterministisches 3-Phasen-Modell:
  - 0–120 ms: Eckklammern
  - 120–260 ms: Raster/Scan
  - 260–420 ms: Datenfreigabe
  - 320 ms kontrollierter Rückbau
- Reduced-Motion-Fallback ohne laufenden Scan.
- Szenenprofile für Dokument, App, Entwicklung, Schwarm, Recherche/Evidenz, Zahlen und Sicherheit.
- Transparenter, touch-durchlässiger Android-Overlay-Renderer im bestehenden `JarvisWorkWindowHost`.
- Einmalige Haptik an Phasenübergängen; keine Haptik oder Audioausgabe aus `onDraw`.
- Der laufende Scan wird nur während realer Arbeit weitergetaktet; nach der Konstruktion bleibt die Szene sonst statisch.
- Unit-Spezifikation für Phasengrenzen, Monotonie, Rückbau, Reduced Motion und Szenenzuordnung.

## Bewusst nicht als erledigt markiert

- Kein Nachweis für 120 FPS, 0 % CPU oder thermische Neutralität.
- Kein Android-Gesamtbuild in dieser Sitzung; der gestapelte Draft-PR löst die vorhandene `android.yml` nicht automatisch aus.
- Kein Fold7-Gerätevideo und keine Frame-/Thermal-Messung.
- AGSL/`RuntimeShader` noch nicht aktiviert. Zuerst muss der portable Canvas-Fallback gebaut und profiliert sein.
- Audio bleibt im vorhandenen policy-gesteuerten `ContinuumUiFeedback`; keine zweite Audio-Engine.
- Echte Kopfparallaxe benötigt eine explizite, lifecycle-sichere Pose-Quelle aus dem Avatarpfad.
- Kalender-Zwischenrufe und Memory-Hinweise werden erst mit Quell-ID, Zeitstempel, Zuständigkeit und Duplikat-/Ruhezeit-Gate angeschlossen.
- Die Arbeitswelt `CHRONO_GAUGE` ist vorhanden, aber noch nicht an einen realen Berechnungs-/Finanz-Eventtyp gebunden.

## Verbindliche Merge-Gates

```bash
./gradlew :app:testDebugUnitTest
./gradlew :app:compileDebugKotlin
bash tools/audit_cockpit_ui.sh
```

Danach auf dem Zielgerät:

1. PDF öffnen/analysieren: sichtbare 3-Phasen-Konstruktion, keine Berührungsblockade.
2. App starten: App-Conduit nur bei realem Tool-Start.
3. Lokales Denken/Entwicklung: Scan nur solange der Turn aktiv ist.
4. STOP: Fenster, Scan, Avatar-Arbeit und Haptik enden deterministisch.
5. Power-Save/Thermal ≥ 3: Animation reduzieren oder stillsetzen; Antwort- und Toolqualität unverändert.
6. Fold/Rotation/200 % Schrift: keine Überdeckung des Avatars, der Kugel oder bedienbarer Inhalte.
7. Perfetto/JankStats: Framezeiten, CPU/GPU, Akku und Thermalstatus dokumentieren; erst danach Ziel-FPS festlegen.

## Nächster Vertical Slice

1. `HollywoodFuiStageOverlay` an `JarvisRenderQuality.Budget` koppeln.
2. Bestehendes `ContinuumUiFeedback` um genau einen policy-gesteuerten Holo-Cue erweitern.
3. Reale Berechnungs-/Sicherheits-/Research-Ereignisse auf die fehlenden Profile routen.
4. Provenienz-Gate für Kalender- und Memory-Zwischenrufe implementieren.
5. Optionaler API-33+-AGSL-Pass hinter Capability- und Thermal-Gate; Canvas bleibt verbindlicher Fallback.
