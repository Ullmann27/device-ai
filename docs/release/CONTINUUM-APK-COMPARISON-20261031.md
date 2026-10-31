# Continuum Lokal — Vergleich letzter gemeldeter Download mit kommender APK

**Erstellt:** 31.10.2026. **Das ist ein Release-Vergleichsplan, keine freigegebene oder herunterladbare APK.**

## Ausgangsstand (nach Benutzer-Geräteberichten, nicht live vom Fold7 gelesen)

- Letzte installierte Continuum-Lokal-App: `0.3.28`, `versionCode 29`, `applicationId=at.heinz.deviceai.continuumlocal`.
- Update-Schaltfläche **„Updates prüfen und laden“** laut bisherigem Bericht vorhanden. Keine vollständige Abnahme eines **zweiten** Downloads und unveränderten Nutzerzustands durch diesen Kanal aus diesem Forschungsturn belegt.
- Besitzerzertifikat vom Fold7 / lokaler Original-`continuum-local.p12`; nur Fingerabdruck für Abgleich, Passwort/Keystore niemals in Source oder Chat.
- Vor jeder Veröffentlichung **erneut** PackageManager auf dem realen Gerät lesen, denn diese Historie kann veraltet sein.
- **Nächste Version:** `versionCode > 29` (beispielsweise 30), für Kontinuität `0.3.29` möglich, aber vorab Android-Gradle-VersionName-Konfiguration und tatsächliche Paketmetadaten prüfen.
- **Sperre:** PR #286 enthält einen `code29`-Releasevertrag. Ein `code29`-Artefakt ist kein gültiges höheres Update gegenüber installiertem `code29`; vor Nutzung korrigieren und **alle** Signatur-/Host-/Release-Tests auf exakt dem resultierenden HEAD erneut durchführen.

## Änderungsverzeichnis seit gemeldeter Basis

| Bereich | Entwicklungszweig / Codeveränderung | Status für eine nächste APK |
| --- | --- | --- |
| Owner-Updater | PR #260/#265, Schaltfläche und Zertifikats-/Paket-Prüfungen | Bereits in der gemeldeten App vorhanden; zweiten realen Updatevorgang noch belegen |
| EWM-v2 | PR #265, EWM-Ersetzungsfix und neuro-inspiriertes Gedächtnis | Forschungsintegration; Übernahme, History/Owner-Fakten/Datenerhalt und Gerätetest prüfen |
| Temporal/CRE1/SWB | bestehende Forschung in PR #284 | Vorhandene Stränge müssen unverändert funktionsfähig bleiben; keine allein durch Integration bewiesene Intelligenzsteigerung |
| Owner-Kontextfilter | PR #284/#285: STALE, CONTRADICTED, SENSITIVE als ungeeigneter Live-Fallback zurückweisen | Quellcode-Fix vorhanden, Android-/Realgerät-Abnahme fehlt |
| M8 lokaler Agent | PR #285: `ContinuumLocalAgentModel`, Tool-Outcomes nur mit wirklichen Receipts | Geschriebene Tests; unabhängigen generativen Reparaturerfolg, Berechtigung, Cancellation und UI-Test nachweisen |
| Coding-Loop/Teamleader/MCP | PR #265: isolierter Worker und 30 Benchmarks | Tests als Quellstand berichtet, kein garantierter 24/7-Gerätebetrieb und kein unabhängiger unbekannter-Fix-Nachweis |
| Virtual Compute + Reuse | PR #287: 50–3.000 logische Experten, 3.000→30 synthetische Teilstufen, Kotlin `ContinuumComputeReuseResearch` | **Research-only**, neu hinzugefügter Adapter ist nicht an laufenden Chat oder reale LLM-Inferenz verdrahtet; keine App-Geschwindigkeitsverbesserung behaupten |
| NVIDIA NeMo | PR #287 Training-Dry-Run | Noch kein echtes GPU-Training oder Modell auf dem Gerät |
| JARVIS-/MCP-Video | PR #263 (separater Zweig) | Referenz/Bewertung, keine ungeprüfte Übernahme real laufender Dienste |
| Signing/Build-Vertrag | PR #286 (separater Zweig), Render-Prüfungen | Kein bestätigter Original-P12-Publisher für neue APK, kein gültiges höheres veröffentlichtes Artefakt |

**Fazit:** Gegenüber dem letzten Download sind die genannten Punkte **potenzielle Verbesserungen des Entwicklungsquellstands**, nicht nachgewiesene neue Produktfunktionen auf dem Fold7. Es existiert zum Erstellungszeitpunkt **kein bestätigter neuer, originalsignierter Continuum-Lokal-Download**, den man seriös mit der installierten APK als Fertigversion vergleichen könnte.

## Verbindliche Fold7-Abnahmematrix

1. **Identität:** `applicationId=at.heinz.deviceai.continuumlocal`, installierter Versionscode und neue `versionCode` über PackageManager und `apkanalyzer`/`apksigner` übereinstimmend prüfen; originaler X.509-Fingerabdruck identisch.
2. **Installationspfad:** kein Deinstallieren/Reset; nur `Updates prüfen und laden` → verifiziertes `latest.json` → originalsignierter HTTPS-Download → SHA-256, Zertifikat, Anti-Downgrade → Android PackageInstaller mit Benutzerbestätigung.
3. **Zweiter Update-Durchlauf:** Eine gültige nächste, höhere Version erneut über denselben Button laden, damit „Update funktioniert dauerhaft“ statt einmaligem manuellen APK-Import nachgewiesen ist.
4. **Persistenz:** vor/nach Hash-/Stichproben zu Besitzeridentität, SWB/EWM, MemoryGraph, modellbezogenen Dateien, App-Einstellungen, Wissensarchiv (historisch 548 Quellen berichtet), Chatkontext, Entwicklungs-Backlog und Updater-Preferences; keine private Datenausleitung.
5. **Gehirn:** reale lokale Gemma/gewählte Modellversion, Offline-Textchat, relevante Erinnerungen nach Zeitkorrektur, Widersprüche, keine SENSITIVE-Fallback-Promotion, ToolGate-Berechtigungen.
6. **M8:** realer Modellvorschlag → erlaubtes Tool in der Sandbox → unbekannte FAIL→PATCH→PASS-Aufgabe mit unabhängigen Folgetests; abgelehnte Toolfreigabe und Timeout müssen korrekt abbrechen.
7. **Biologisch-neuronal:** CRE1, EWM-v2, SWB und Zeitruntime unverändert, Shadow-Experimente keine beleglosen Qualitätssteigerungen.
8. **Compute-Reuse:** OFF vs. SHADOW vs. kontrolliertes ON, gleiche Modell-/Quellstände, Cache-Hit-Rate und Latenz/CPU/RAM/Temperatur tatsächlich messen; negative Testfälle immer korrekt.
9. **UI/Voice:** Hologramm-/Animationsstatus nur nach tatsächlichen Geräteeinstellungen messen; Sprache/Diktat, Hintergrund-Audio, Fokuswechsel und Fold7-Wechsel testen. Keine funktionierenden Animationen aus Screenshots ableiten.
10. **Releaseschutz:** kein Signier-P12 in Logs oder Repository; Rollback ohne Datenverlust, keine alte Paketkennung `at.heinz.deviceai.mentorpaired`, keine unbekannte Render-Veröffentlichung, keine automatische Freigabe.

## Nicht verhandelbare Abnahmeschranke

Eine bloß kompilierte, debug-signierte, nur auf Render erzeugte oder mit einem falschen Paket signierte APK ist **kein** aktueller Benutzer-Download. Die neue APK ist erst „fertig“, wenn Revisions-SHA, vollständige Prüfungen, Originalsignatur, Version, Host, Download-Manifest und Fold7-Installation nachgewiesen sind.
