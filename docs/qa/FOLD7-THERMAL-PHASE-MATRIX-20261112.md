# Fold7 – Hitze-Abnahmematrix (Code55; Gerätetest NOT_RUN)

Ziel: tatsächliche Wärmequellen getrennt messen. Bisherige Reparaturen vermeiden belegte Doppelarbeit und sichern die thermische Inferenzsperre; ein Temperaturgewinn auf dem Fold7 wurde hier nicht gemessen.

## Tatsächlich angeschlossen

Systemdiagnose → **NEUE MESSREIHE STARTEN** → **JETZT ANALYSIEREN** → vorhandener ToolGate/DeviceToolExecutor → frisches, erfolgreiches Diagnoseergebnis → vorhandener ThermalPhaseRecorder.

Ohne ausdrücklichen Start wird nichts aufgezeichnet. Start übernimmt keinen alten Messwert. Nur aktuelle, sichere, nicht wiederholte SUCCEEDED-Ergebnisse derselben Sitzung/Generation mit passendem und aufsteigendem Zeitstempel dürfen einen Punkt liefern. Erfasst werden ausschließlich bereits gemeldeter Android-Thermalstatus und Akkutemperatur. Fehlende/ungültige Werte bleiben unbekannt. GPU-Last/-Zeit, CPU-Temperatur, Thermal Headroom und Modelllaufzeiten werden durch diese Verbindung **nicht** gemessen.

MESSREIHE STOPPEN, Abbruch und onPause beenden die Aufzeichnung; Resume startet sie nicht erneut. Bei 2.000 Punkten stoppt sie. Ein erneuter ausdrücklicher Start beginnt leer. Die Messreihe ist prozesslokal; Bericht speichern bleibt die Speicherung der einzelnen Diagnose. Kein zusätzlicher Sampler, Timer, Modellaufruf oder Scheduler wurde eingeführt.

Verifiziert: neun ThermalPhaseRecorder- und zwölf SystemIntelligence-UI-Fälle im tatsächlichen Lauf43354998293 auf bfef9db3 (gesamt344 bestanden, keine Fehler/Skips). Der vollständige Lauf43358315168 auf058d754b meldete keine Fehler in diesen Klassen, blieb wegen drei gesonderten Grafik-Testannahmen insgesamt fehlgeschlagen. Details: [Verdrahtung und Nachweise](../research/omega/BLACKSITE-MANUAL-THERMAL-DIAGNOSIS-20261113.md).

## Noch auf dem Gerät auszuführen

Die übrigen benannten Recorder-Phasen sind vorbereitet. Capture und kombinierte Computerarbeit sind zusätzliche Abnahmeprotokolle, keine vorhandenen Recorder-Phasentypen. Die Tabelle behauptet keine automatisch angeschlossene Laststeuerung oder bereits erfolgte Messung.

| Phase | Kontrollierte Bedingung | Zeitraum | Status |
| --- | --- | --- | --- |
| DIAGNOSTIC_SNAPSHOT | Expliziter Start, frische Besitzer-Diagnosen | Manuelle Folge | UI verbunden; Fold7 NOT_RUN |
| IDLE_APP_OPEN | Display an, keine Inferenz/Capture/Sprache | 3 min | Messung NOT_RUN |
| MODEL_LOAD | Gleiches Modell-SHA, vorher entladen | Bis bereit | Zeitmessung NOT_RUN |
| SHORT_INFERENCE | Gleicher Kurzauftrag und Kontext | Eine Antwort | Messung NOT_RUN |
| LONG_INFERENCE | Nur zulässiges thermisches Budget | Eine lange Antwort | Messung NOT_RUN |
| MAYA_SPEECH | Gleicher Text, freigegebener Sprachmodus | Eine Ausgabe | Messung NOT_RUN |
| CINEMA_ONLY | Gleiche Kamera/Animation, keine Inferenz | 2 min | Messung NOT_RUN |
| Capture + 3D (Abnahmeprotokoll) | Gültige sichtbare App-/Capture-Sitzung | 2 min | Messung NOT_RUN |
| SPEECH_AND_CINEMA | Gleicher Text und Szene | Eine Ausgabe | Messung NOT_RUN |
| Kombinierte Arbeit (Abnahmeprotokoll) | Freigegebener identischer Computerauftrag | Bis geprüft beendet | Messung NOT_RUN |
| AFTER_STOP_REST | STOP, keine neue Last | 3 min | Messung NOT_RUN |

Gleiche Helligkeit, Modell-SHA/Quantisierung, Kontext, Ladezustand, Stromversorgung, Umgebung und abgekühlter Ausgangszustand dokumentieren. Monotone Zeitstempel und getrennte Phasen benutzen; keine Durchschnittswerte aus unterschiedlichen Lastarten vermischen.

Die bestehende Recorder-Zulassung für LONG_INFERENCE verweigert Status≥2 oder unbekannt. Das ist keine neue Modellsteuerung. Der bestehende LocalModelThermalGuard bleibt der tatsächliche Sicherheitsstopp; seine Schwellen wurden nicht verändert. Bei Status≥3 keine Lasttests fortsetzen.

Vorhandene Diagnosewerkzeuge wiederverwenden: fold7-diagnose.sh liest, außer die ausdrückliche Option --stop-continuum wird gesetzt. Android gfxinfo ist kein Filament-GPU-Zeitmesser. Rohdiagnosen können Geräteinformationen enthalten und werden nicht automatisch öffentlich hochgeladen.

Mediane und p95-Framezeiten, Jank, CPU/GPU, Speicher, Energie und Temperatur nur aus tatsächlich erhobenen Messreihen ausweisen. Nicht verfügbare Werte bleiben NOT_MEASURED. 60 FPS ist ein Ziel, kein Ergebnis.
