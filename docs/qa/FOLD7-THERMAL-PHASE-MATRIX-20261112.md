# Fold7 – datensparsame Hitze-Abnahmematrix (Code54, vorbereitet, **NOT_RUN**)

Ziel: Messen, *wo* die Wärme entsteht – nicht die Leistung pauschal senken. Die drei Hitzereparaturen (#773, `1ba7b9458`)
beseitigen belegte Doppelarbeit; ihr realer Temperaturgewinn auf dem Fold7 ist **ungemessen**.

Werkzeug: `systemintelligence/ThermalPhaseRecorder.kt` (Zweig `claude/thermal-phase-recorder-20261112`, 4/4 Host-Tests).
Er speichert nur Zahlen, die das Gerät selbst meldet: Thermalstatus 0..6, Thermal-Headroom (falls verfügbar),
Akkutemperatur in Zehntel °C und Modelllaufzeit. **Keine** Prompts, Antworten, Dateinamen, Audio. GPU-Werte werden nicht erhoben
(`gpuMeasured: false`). Die lange Inferenz wird bei Status ≥ 2 oder unbekanntem Status **verweigert**. Der bestehende
`LocalModelThermalGuard` bleibt der einzige Sicherheitsstopp; nichts wird umgangen.

| Phase | Vorbedingung | Dauer | Erhobene Werte |
|---|---|---|---|
| IDLE_APP_OPEN | Status ≤ 1, Display an, keine Inferenz | 3 min | Status, Headroom, Akku |
| MODEL_LOAD | Modell entladen | bis bereit | + Ladezeit |
| SHORT_INFERENCE | Status ≤ 1 | 1 Kurzfrage | + Modelllaufzeit |
| LONG_INFERENCE | **nur Status ≤ 1** (sonst verweigert) | 1 lange Antwort | + Modelllaufzeit |
| MAYA_SPEECH | Maya eingerichtet, Internetmodus erlaubt | 1 Antwort vorlesen | Status, Akku |
| CINEMA_ONLY | keine Inferenz | 2 min Arbeitsfensterzyklen | Status, Headroom, Akku |
| SPEECH_AND_CINEMA | Status ≤ 1 | 1 gesprochene Antwort mit Szene | Status, Akku |
| AFTER_STOP_REST | nach STOP | 3 min | Rückgang ohne Last |

Abbruch: sofort bei Status ≥ 3. Vergleich nur unter gleichen Bedingungen (gleiche Helligkeit, Ladezustand, Umgebung, abgekühltes Gerät).
Keine Kühlpacks oder andere riskante Temperaturwechsel.

Offen (Umsetzung): owner-gesteuerte Bedienung im Diagnosezentrum, die die Phasen startet bzw. beendet und `summary()` als JSON
anzeigt bzw. teilt; Anbindung an `PowerManager.getCurrentThermalStatus()`/`getThermalHeadroom()` und an den Akku-Broadcast.
Erst danach Fold7-Messung.
