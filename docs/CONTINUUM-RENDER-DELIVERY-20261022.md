# Continuum – sichere Render-Auslieferung ohne GitHub-Actions-Abhängigkeit
Stand: 22.10.2026 · EXP-00 / EXP-08 / EXP-09 / EXP-12 · SOURCE_INTEGRATED in isoliertem Reviewbranch.

## Ausgangslage
GitHub-Actions-Schedule-Läufe 39712690921 und 39707029914 enden binnen Sekunden mit `failure`, `runner_name=""` und `steps=[]`. Deshalb hat kein Node-/Gradle-/APK-Test begonnen; die eigentliche Ursache der Runner-Nichtzuteilung ist ohne die GitHub-Konto-/Billingmeldung ungeklärt. Nicht blind erneut starten oder den Fehler als bewiesenen Quellcodefehler melden.

Bereits funktionierender Render-Buildpfad:
- `device-ai-mentor-apk-preview`, Branch `astra/render-mentor-apk-builder-20261019`, am 21.10.2026 um 18:21 UTC `live` für signierten Mentor-Pair-Build 0.3.11/code14.
- `device-ai-continuum-pr50-preview`, Branch `chatgpt/continuum-apk-preview-20261021`, am 21.10.2026 um 18:04 UTC `live`.
- `autoDeploy` ist für beide Render-Static-Sites deaktiviert. Die vorhandenen Render-Buildskripte mit Node-/Backend-/Android-/Lint-/Signatur-/Hash-Gates werden NICHT ersetzt.

## Ergänzung: unabhängiger Liefer-Hash-Check
`scripts/continuum-render-delivery-audit.mjs` prüft **nach** einem Render-Deploy:
1. Nur zwei ausdrücklich freigegebene HTTPS-Origins; keine Redirects.
2. Ein fest erwarteter APK-Name enthält Version und **unabhängig gelieferten** SHA-256.
3. Inhalt der öffentlich publizierten `SHA256SUMS-<SHA>.txt` und `BUILD-INFO-<SHA>.txt` gegen erwartetem Git-Commit, Branch und Paket.
4. Die tatsächlich ausgelieferten APK-Bytes streaming gegen den erwarteten SHA-256; keine lokale Schreiboperation, keine Speicherung von Besitzerdaten.
5. Ein Abbruch bei fehlendem/metadatenfremdem/veraltetem APK, unbekannter Origin, ungültigen Metadaten, Redirect, zu großer/trunkierter Antwort.

Manuell mit Node 22+ ausführbar, benötigt keine GitHub-Actions-Runtime, keine Secrets und keine weitere Abhängigkeit. Beispiel für den am 21.10.2026 dokumentierten **Mentor-Preview-Release**, SHA aus eigenständigem Releaseprotokoll:

```sh
node scripts/continuum-render-delivery-audit.mjs \
  --origin https://device-ai-mentor-apk-preview.onrender.com \
  --sha256 e529374f9e4eb4bde149c156e1f217405500c7c9c88711efa1b437d63bd01b72 \
  --version 0.3.11 \
  --packageName at.heinz.deviceai.mentorpaired \
  --commit 17e22bc2d9e1851fd634f44b8aadc04479556983 \
  --branch astra/render-mentor-apk-builder-20261019 \
  --artifact DeviceAI-Continuum-v0.3.11-e529374f9e4eb4bde149c156e1f217405500c7c9c88711efa1b437d63bd01b72.apk
```

Ausgang: nur `DELIVERY_SHA256_VERIFIED` oder Exitcode ungleich 0. **Keine Signatur-, Geräteinstallations-, Android/ARM64- oder Play-Store-Freigabebehauptung.** Der bisherige Build prüft die APK-Signatur selbst mit `apksigner` und festem Mentor-Zertifikat; diese externe Byteprobe ergänzt den Nachweis, ersetzt ihn nicht.

## Nachhaltiger Release-Pfad
1. GitHub zunächst für Code, Git-Branches und Draft-PRs; keine ungereviewten Main-Merges.
2. Render manuell für gebündelte Builds. Bestehenden `.mentorpaired`-Paketnamen und persistentes Zertifikat für Android-Updates erhalten. Keine App deinstallieren/privaten Speicher zurücksetzen, niemals alte Backups oder SWB1/CDX1-Checkpoints überschreiben.
3. Freigabe einer neuen Version nur nach Node-/Backend-/Kotlin-/Lint-/APK-/Signaturprüfung, anschließend unabhängiger Liefer-Hashprüfung und Besitzerbestätigung auf dem Gerät. Fehler blockiert Freigabe statt still auf einen anderen APK-Namen auszuweichen.
4. Google Play ist ein **gesondertes Vorhaben**: produktiver `release`-AAB-Build, separate Upload-Signatur/Play App Signing, stabile Anwendungspaketkennung, steigender `versionCode`, Store- und Datenschutzprüfungen und Play-Console-Freigabe. Das heutige signierte `debug`-Mentor-APK ist kein Play-Release. Ohne Play-Console-/Signaturfreigaben keine Upload- oder Veröffentlichungsversuche.
5. Samsung bleibt primäre Entwicklungsmaschine mit Termux-Worker (EXP-00/08). Render ist zusätzliche Kapazität und kein Pflichtdienst für offline funktionierende Gerätefunktionen.
6. GitHub Actions nicht blind erneut starten, wenn bereits vor Runner-Zuteilung gescheitert. Zuerst in GitHub-Kontoführung Actions-Budget, Usage und Repository Actions-Einstellungen kontrollieren.

## Geprüfte Tests / Grenzen
`node --test scripts/continuum-render-delivery-audit.test.mjs`: 8/8 bestanden mit Node 22.16.0 in isolierter, synthetischer Umgebung. Redirects, unzulässige Origins, falsche Datei-/Commit-/Paketkennungen, Metadatenverdopplung, veraltete APK-Bytes und Streaming-Limits getestet. Kein echter Render-Binärdownload im Test; externe, aktuelle CDN-Lieferung bleibt separat zu prüfen. Kein Gradle-/Android-Gesamtlauf für diesen reinen Node-Zusatz. Neues Skript und Tests werden im vorhandenen Render-Build durch das bestehende `scripts/*.test.mjs`-Gate erfasst, **sobald und nur wenn** es nach separatem Review integriert wird.

**Rollback:** Nur die zwei neuen Skripte und diese Dokumentation aus dem Reviewbranch zurücknehmen. Keine Daten-/Signer-/Gewichtsmigration, keine Änderung von `main`, APK, aktiven Render-Sites oder Store-Konten.
