# Kapitel VI/VII (Besitzer-Regieauftrag Teil II) – Abgleich mit Code54 `f0120e1b2`

Verbindliche Abnahmekriterien aus dem Besitzerauftrag „DAS CINEMATIC JARVIS UNIVERSE – Teil II“ (11.11.2026),
abgeglichen mit dem tatsächlichen Quellstand. Nichts davon ist am Fold7 geprüft (NOT_RUN).

## Bereits vorhanden (Code, Datei)
| Anforderung | Stand | Fundstelle |
|---|---|---|
| Nicht zugängliche Werte sichtbar als „nicht verfügbar“ | **vorhanden** | `systemintelligence/SystemDiagnosis.kt` `report()` (Akku, Akkutemperatur, Thermalstatus, App-CPU, RAM, Datenträger; `unavailable`-Menge) |
| Keine geschätzte GPU-/Fremd-App-Zahl | **vorhanden** (ausdrücklicher Hinweis) | `SystemDiagnosis.recommendations()` letzte Zeile |
| Thermalstatus ≥ 3 sachlich, Akkutemperatur ≠ CPU-Temperatur | **vorhanden** | `SystemDiagnosis.recommendations()` |
| Spracherkennung „analysiere/prüf mein Handy, ändere nichts“ ohne Fehlablehnung, ohne Modellpfad | **integriert über #772/#778** | `cognitive/OwnerDeviceRequestSafety.kt`, Tests `BootstrapBrainOwnerIntentSafetyTest`, `SpokenDiagnosisRoutingTest`, `OwnerDeviceRequestSafetyTest` |
| Reiniger: Vorschau → ausdrückliche Freigabe → entfernte Dateien/Bytes, verfügbarer-Speicher-Delta getrennt, Rücknahme | **vorhanden, Umfang begrenzt** (nur eigene Diagnoseberichte; Rücknahme 10 min im selben Prozess) | `OwnDiagnosticCache.kt`, `SystemStorageInspector`, UI `AgentActivity.showSystemIntelligence` |
| Analyse freigegebener Dateien ohne Löschplan | **vorhanden** | `StorageAnalysisPreview.kt`, `StorageContentAnalysis.kt`, `StorageScanLane.kt` |

## Fehlt (Umsetzungsaufträge, in dieser Reihenfolge)
1. **Messungen als Ereignisse statt Momentaufnahme.**
   - `SystemDiagnosis` wird heute in einem Stück erzeugt.
   - Neu: je Kategorie (Akku, Thermal, RAM, Datenträger, Modellzustand, eigene Lade-/Generierungszeiten, eigene Aufgaben) ein `DiagnosisMeasurement`-Ereignis mit Zeitstempel und Quelle. Es läuft über den vorhandenen `CockpitBus`/`cockpitTriggers`-Weg, keine neue Ereignisschleife.
   - Test: Eine Kategorie erscheint erst nach ihrem Ereignis. Fehlende Kategorien bleiben „nicht verfügbar“. Kein Diagramm ohne mindestens zwei echte Werte.
2. **Auswertung in vier getrennten Gruppen.**
   - `recommendations()` mischt heute Messwert, Auffälligkeit und Hypothese.
   - Neu: reines Modell `DiagnosisEvaluation(confirmed, anomalies, hypotheses, approvedImprovements)`. Jede Hypothese trägt die fehlende Gegenprüfung.
   - `approvedImprovements` ist leer, solange keine ausdrückliche Besitzerfreigabe vorliegt.
   - Tests: Thermalstufe 3 erzeugt „Auffälligkeit“, nie „Ursache gefunden“. „Ändere nichts“ führt nie zu einer Ausführung.
3. **Planungszustand ohne Ausführung.**
   - „Ja, aber zuerst nur den Plan“ erzeugt eine Vorschau (Reiniger: `OwnDiagnosticCache.preview`) und keinen `cleanup`-Aufruf.
   - Test über den vorhandenen ToolGate: Freigabe erst durch eine eigene Bestätigung.
4. **Szene (nach 1–3, bestehende Architektur).**
   - Diagnose-Hauptfläche links als `JarvisWorkWindow` (`Edge.LEFT`, wie das PDF-Dokumentfenster aus #761), Auswertung rechts (`Edge.RIGHT`, wie das Ergebnisfenster).
   - Kugelimpuls und Band nur bei angenommenem Auftrag bzw. eintreffender Messung: vorhandenes `JarvisFluidLight.workingSample`, Quittungspaket bei `verifiedStep`.
   - Blick: zuerst die Augen, dann der Kopf (vorhandener `CriticalDampedSpring`/`GazeBlinkPolicy`), Ziel = aktive Tafel.
   - Warnfarbe bei Thermalstufe ≥ 3 sachlich, Energiefluss langsamer (kein Alarm). Keine zweite Uhr, keine neue Engine.
5. **Speicherlandschaft des Reinigers.**
   - Nur Bereiche mit echter Messung: eigene Diagnoseablage (`OwnDiagnosticCache`) und vom Besitzer freigegebene Dateien (`StorageScanLane`).
   - Geschützte oder nicht gescannte Bereiche werden als „nicht gescannt“ gezeichnet, nie als belegt.
   - Größen ausschließlich aus Bytes der Vorschau. Kein Dritt-App-Cache ohne Android-Berechtigung.

## Abnahme
Host: Tests zu 1–3 (JVM) und Lebenszyklus/Szene (Robolectric, als Host-Canvas gekennzeichnet). Gerät: Besitzersatz
„mein Handy wird … warm … ändere noch nichts“ → gestaffelte echte Messungen, Auswertung in vier Gruppen, kein Eingriff;
„Ja, aber zuerst nur den Plan“ → nur Vorschau. Thermik/GPU/Bildzeiten: NOT_RUN bis Fold7-Messung.
