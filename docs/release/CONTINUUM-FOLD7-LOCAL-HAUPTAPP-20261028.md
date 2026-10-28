# Continuum Lokal – Fold7 als eigener Android-Entwicklungsrechner

Stand: 28.10.2026. Ausschließlich **Device AI Continuum**, bestehende Haupt-App-Quellen, kein zweites Demo-/Spielprojekt.

## Technischer Ausgangspunkt

- Die bereits installierte Haupt-App ist `at.heinz.deviceai.mentorpaired` mit `versionCode 25`. Die vorherige Render-P12 ist derzeit nicht verfügbar. **Eine APK mit anderer Signatur darf diese Installation nicht überschreiben.**
- Der Fold7 hat bereits JDK17, SDK36, Gradle8.11.1, natives ARM64-aapt2 und `apksigner` sowie 1125/1125 lokale Android-JUnit-Prüfungen des aktuellen Kerncodes erfolgreich ausgeführt.
- Das neue `continuumLocalStandalone`-Buildziel verwendet dieselben produktiven Kotlin- und Ressourcenquellen mit `deviceAiMentorPreview=true`, aktiviert den bisherigen integrierten Handy-Builder, ändert aber ausschließlich die Android-Paketidentität auf `at.heinz.deviceai.continuumlocal`, den sichtbaren Namen auf `Continuum Lokal`, VersionCode ab 26, und nutzt eine **eigene dauerhafte PKCS12-Signatur**.
- Der Build ist Android-**Release**, nicht die nichtinstallierbare QA-/Debug-Signatur. Es erfolgt keine automatische Installation.

## Erster Build direkt auf Fold7 in Termux

Voraussetzung: bestehender Git-Checkout `~/device-ai-fold7-candidate` und laufende Termux-Werkzeuge von den bereits erfolgreichen lokalen QA-Builds. Quellcode bleibt auf dem isolierten PR-Branch, `main` bleibt unberührt.

```bash
cd "$HOME/device-ai-fold7-candidate" &&
git pull --ff-only &&
node --test scripts/continuum-fold7-standalone-apk.test.mjs &&
bash scripts/continuum-fold7-standalone-apk.sh build 26
```

Bei der **ersten** Ausführung fragt das Skript am Terminal nach einem neuen privaten Passwort von mindestens zwölf Zeichen und einer Wiederholung. Die Eingabe erscheint absichtlich **nicht** auf dem Bildschirm. Das Passwort niemals an ChatGPT, GitHub, Render, in Logs oder Screenshots weitergeben. Danach wird die eigene lokale private P12 erzeugt unter:

```text
$HOME/.local/share/device-ai-continuum/signing/continuum-local.p12
```

Die Datei hat eingeschränkte lokale Zugriffsrechte, liegt **außerhalb** von GitHub/Projekt/Downloadordner und bleibt für künftige Builds erhalten. Das zugehörige Zertifikats-SHA256 wird lokal fixiert. Bei späteren Builds wird das gleiche Passwort abgefragt und das ursprüngliche Zertifikat nachgeprüft. **Keine automatische Schlüsselrotation.**

Das Skript prüft `:app:testDebugUnitTest --tests at.heinz.deviceai.ContinuumMobileBuilderRouteTest`, `:app:lintRelease`, `:app:assembleRelease` und verifiziert Signatur, Paketname, Versionscode und APK-SHA256. Die vollständige Suite ist unabhängig davon nach Quellcodeänderungen im normalen QA-Gate erneut zu verwenden; nicht behaupten, dass sämtliche 1125 Tests bei diesem zielgerichteten Release-Durchlauf erneut gestartet wurden.

Bei Erfolg endet der Lauf mit `CONTINUUM_LOCAL_APK_READY=...`. Die verifizierte APK wird nach `Downloads/Continuum_back_up/Continuum-Lokal-v0.3.25-<sha256>.apk` kopiert; schon gesicherte Gewichte, Quellen und Backlog werden nicht verändert.

## Installieren und eigene Daten

APK im Samsung-Dateimanager öffnen, nach eigener Bestätigung installieren. Android sollte **eine zusätzliche App** `Continuum Lokal` anbieten, nicht ein Update von `at.heinz.deviceai.mentorpaired`. Falls Android eine vorhandene App zu ersetzen versucht, abbrechen. Die bisherige App **niemals** löschen oder ihre Daten zurücksetzen.

Nach Start in `Continuum Lokal` können die in v25 manuell gesicherten Daten einzeln über die bestehenden UI-Funktionen importiert werden:

1. `continuum-swb1-backup.bin` – Lernzentrum → Gewichte / Lernlabor → Gewichte importieren.
2. `continuum-wissensarchiv.zip` – Lernzentrum → Wissensarchiv → Backup / Wiederherstellen → importieren.
3. `device-ai-backlog.json` – Entwicklung → Aufgaben/Backlog → Import.

Diese Exporte sichern **nicht nachweislich** sämtliche Chats, persönlichen Erinnerungen, Einstellungen, Gemma-Modellkopien oder sämtliche Lernstände. Beide Apps verfügen über getrennte Android-Speicherbereiche. Die alte Version 25 bleibt verfügbar, bis der Besitzer die erforderlichen Daten überprüft hat.

## Künftige vollständig lokale Versionen

Sobald ein neuer Quellstand geprüft wurde, kann dasselbe Skript mit erhöhtem Versionscode arbeiten, z. B. `bash scripts/continuum-fold7-standalone-apk.sh build 27`. Gleiche Android-Paket-ID und **gleiche lokale P12** ermöglichen reguläre Updates der `Continuum Lokal`-App ohne GitHub Actions, Render oder bezahlte KI-APIs. Nie einen kleineren Versionscode oder eine andere Signatur verwenden.

**Wichtig:** Eine P12 nur auf dem Fold7 ist kein ausreichendes Backup. Sie muss nach dem ersten erfolgreichen Build samt getrennt aufbewahrtem Passwort auf ein **zweites, verschlüsselt gesichertes Medium** kopiert werden. Ein Verlust der Termux-App oder des Telefons ohne dieses Backup würde erneut die Updatefähigkeit zerstören. Die P12 niemals in GitHub, einem Chat, in der öffentlich geteilten Downloadstruktur oder in einem Render-Artefakt publizieren.

## Statusgrenzen

Der Quellcode für diesen Releasepfad liegt im Draft-PR #205. Vor tatsächlichem Fold7-Lauf ist **keine** lokal signierte `Continuum Lokal`-Release-APK als gebaut oder installiert zu betrachten. Die alte `Continuum`-Besitzerinstallation wird durch diesen Pfad weder signiert noch ersetzt.
