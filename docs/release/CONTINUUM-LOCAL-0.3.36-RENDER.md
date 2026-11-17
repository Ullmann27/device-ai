# Continuum Lokal 0.3.36 (code 37) – Original-Update über Render

Status: **RELEASE_PREPARED, NOT SIGNED, NOT PUBLISHED, DEVICE_NOT_RUN**.

## Herkunft und unveränderliche Regeln
- Fable/Sol-Quelle: PR #335, verifizierter Integrationscommit `b06889f586ed54f074d2b2d36535a0b5a820ed61`.
- Arbeitsbranch: `release/continuum-local-0.3.36-render-20261101`, nur vorbereitende Buildskripte und Prüfungen. Kein Merge nach `main`.
- Gerät zeigt derzeit Continuum `0.3.35 (36)`; das geplante nächste Update heißt `0.3.36 (37)`. Installierten APK-Signer und aktuelle Installation zusätzlich vor Installation überprüfen.
- Originalpaket: `at.heinz.deviceai.continuumlocal`.
- Originalzertifikat SHA-256: `6354e91dc0ca8df9173885f32402fd817f46e798f369206d4fd710ff3d03ee23`. **Kein neues Keystore erzeugen**.
- Fest gepinnter Updatehost: `continuum-owner-v23-release-preflight.onrender.com`.
- Render-Publisher: `srv-dbf1tr2vcj2c73ehpfog`; Buildkommando bleibt `bash scripts/render-continuum-m8-owner-release.sh`.
- Der ältere Code-36-Signierungsvertrag bleibt erhalten. Code 37 wird nur bei exakt gesetztem `CONTINUUM_RELEASE_VERSION_CODE=37` an `scripts/render-continuum-v37-owner-release.sh` delegiert.
- Sicherheits- und Benutzerregeln aus `AGENTS.md` gelten uneingeschränkt.

## Werkzeuge für den Releasekandidaten
- `scripts/render-continuum-v37-qa.sh`: unabhängige vollständige Render-Android-QA mit echter LiteRT-Abhängigkeit, 37er Paket-/Versionsprüfung, Veröffentlichung nur von Testprotokollen (keine installierbare APK).
- `scripts/render-continuum-v37-owner-release.sh`: Signaturprüfung, SHA- und Quellenfreigabe, kompletter QA-Durchlauf für exakt denselben Commit, erneutes Bauen, Prüfung des bestehenden originalen Zertifikats, Signieren und Staging auf dem bisherigen Host.
- `scripts/continuum-v37-owner-render-release.test.mjs`: positive Source-/Versionsprüfungen und negative Freigabe-/Signierprüfungen.
- `scripts/render-continuum-m8-owner-release.sh`: ursprünglicher Render-Entrypoint; die Version-36-Freigabesperren bleiben erhalten, neue Version 37 nur über eigenen strengen Vertrag.

## Reihenfolge der Abnahme

1. Exakten Releasebranch-SHA prüfen, neue v37-Node-Tests und die bisherigen 36er Contract-Tests ausführen, fehlerfreien Arbeitsbaum nachweisen.
2. Auf Render **v37-QA** bauen und vollständige Statusdatei, `ARCHITECTURE-PROOF.json`, alle zehn Logdateien und SHA/Version kontrollieren; `apk_signed=FALSE` und `apk_published=FALSE` müssen bestehen. Kein QA-Artifact als installierbares Besitzerupdate verteilen.
3. Original-Installationspaket, tatsächlich vorhandenen Zertifikatsfingerprint, Version 36 und sichere Backups/Rollback prüfen. Der Gerät-Screenshot zeigt nur Version 36, nicht den Signaturfingerprint.
4. Separat für **exakten** überprüften Commit den originalen Owner-Release autorisieren und nur die schon vorhandenen sicheren Render-Umgebungsvariablen nutzen: `CONTINUUM_RELEASE_APPROVED_SHA`, `CONTINUUM_RELEASE_HOST`, `CONTINUUM_RELEASE_VERSION_CODE=37`, `CONTINUUM_LOCAL_SIGNING_P12_BASE64` und `CONTINUUM_LOCAL_SIGNING_PASSWORD`. Geheime Werte nicht im Repo, in Logs, in Chat oder in Commitnachrichten veröffentlichen. Wenn Originalsigierschlüssel fehlen, STOP.
5. Erst nach vollständiger QA/Ownerfreigabe den bestehenden fest gepinnten Render-Publisher mit dem exakt geprüften Release-Commit (nur Fast-forward) aktualisieren und dessen Deployment auslösen. Bei falschem SHA, Signatur, Host oder Versionscode muss die Pipeline hart scheitern.
6. Nach einem `live`-Deploy über HTTPS `/continuum-local/latest.json` und die dort genannte APK bytegleich zurücklesen, SHA-256, Paketname, Versionscode 37, Version 0.3.36, APK-Signer und Quell-SHA prüfen. Vorher ist nichts veröffentlicht.
7. Die APK ausschließlich als bestätigtes In-place-Update auf dem Fold7 installieren, keine Appdaten löschen, anschließend OFF/ASK/ALWAYS_ON, Gemma, EWM2, RIS, Sprachausgabe und Backups am Gerät abnehmen. Ein erfolgreich gebautes Android-Artefakt ersetzt keinen Fold7-Gerätetest.

## Harte Stopps
Keine Veröffentlichung, falls Quell-SHA ungeprüft, neuere installierte Version, anderes Signierzertifikat, fehlender originaler Keystore, abweichender Host, unvollständige Render-QA, fehlender Backup-/Rollback-Nachweis oder Geräte-Ownerfreigabe. Der bestehende Render-Publisher, `main`, produktive Gewichte und das Besitzerwissen dürfen nicht durch eine ungetestete Rechercheänderung ersetzt werden.

**Hinweis:** Dieser Branch ist ein Releasekandidat. Die Quellcodeänderungen von Fable/Sol bleiben unverändert; ein neues Android-Gesamt-QA für den tatsächlichen Release-HEAD ist verpflichtend.
