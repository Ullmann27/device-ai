# Continuum Lokal Code48 – GitHub-Original-APK

Status 07.11.2026: GitHub Actions als Ausweichweg zum durch Pipeline-Minuten gesperrten Render-Original-Publisher. **Ein durch GitHub veröffentlichter Code48-Release ist erst nach allen Prüfschritten und Originalsignatur tatsächlich installierbar.** Unsignierte QA-APKs sind keine Updates.

## Unveränderliche Identität

- Repository: `Ullmann27/device-ai`
- Freigegebener APK-Quellcommit: `c9fffb7dda9c173b6c5ccd4104672b09fd94de15`
- Version: `0.3.47`, versionCode `48`
- Paket: `at.heinz.deviceai.continuumlocal`
- **Ursprünglicher Zertifikats-SHA256:** `6354e91dc0ca8df9173885f32402fd817f46e798f369206d4fd710ff3d03ee23`
- GitHub-Workflow: `.github/workflows/continuum-code48-github-owner.yml`
- Isolierter Branch: `codex/continuum-code48-github-owner-20261107`
- Koordination: PR #593; GitHub-Fallback: PR #595; Build49: PR #594 bleibt unangetastet.

Der Workflow testet immer den exakt gepinnten Quellcommit und prüft die Paketkennung/Version der unverändert unsignierten Release-APK, bevor ein eigener Signaturjob sie verwenden darf. Die GitHub-Workflow-Commits sind **nicht** die APK-Quellcommits.

## Originalsignaturschlüssel ausschließlich in einer geschützten GitHub-Umgebung

In GitHub → Settings → Environments eine Umgebung `continuum-original-signing` anlegen. Diese Umgebung auf den Branch `codex/continuum-code48-github-owner-20261107` beschränken und, wenn möglich, eine ausdrückliche Freigabe durch den Besitzer verlangen. Bei einem Einzelbenutzer-Repository ist die Konfiguration der Freigaberegeln bewusst zu prüfen.

In dieser **Umgebung**, nicht im öffentlichen Repository, zwei *Environment secrets* anlegen:

- `CONTINUUM_LOCAL_SIGNING_P12_BASE64`: Base64-Inhalt der **bestehenden Originaldatei** `continuum-local.p12`
- `CONTINUUM_LOCAL_SIGNING_PASSWORD`: Passwort dieser bestehenden P12

Keines dieser Geheimnisse darf in GitHub-Dateien, Issue-/PR-Kommentaren, Chat-Nachrichten, Artefakten oder Logs landen. Eine andere P12, neue Signatur oder ein Mentor-Pair-Key würde eine reguläre Installation über die bestehende App verhindern und wird deshalb durch die Fingerprint-Prüfung abgelehnt. Existiert keine zugängliche Sicherung des Originalschlüssels, muss die sichere Signatur beim bisherigen vertrauenswürdigen Original-Signer bleiben oder aus einem bestehenden autorisierten Backup wiederhergestellt werden. Der private Schlüssel kann nicht aus der installierten APK abgeleitet werden.

Der signierende Job verwendet die Geheimnisse nur in einem kurzlebigen privaten Verzeichnis. Nach Originalzertifikatsprüfung signiert und überprüft er die fertige Release-APK. Erst danach erstellt er einen unveränderlichen GitHub Release und liest die Binärdatei zum SHA-256-Abgleich erneut herunter. Ohne Originalschlüssel schlägt der Signierjob bewusst fehl; eine grüne Host-QA ist **kein** Beleg für eine signierte APK.

## Prüfstufen und Grenzen

1. Release-Sondertests, kritischer und vollständiger Node-Testlauf, Backend-Tests.
2. Vollständige Android-/Robolectric-/UI-/Lint-Tests auf demselben Originalquellstand.
3. Unsigned Release-APK/AAB, Paket/versionCode-Badging, Unsigniertheit und Hashprüfung.
4. Original-P12-Zertifikat, signierte APK-v2-Signatur, Paket/Version/Größe/SHA; GitHub Release HTTPS-Readback.
5. Fold7-Echtgerät, Temperatur, GPU/FPS, TTS-Multiturn und Referenzvideo-Abgleich bleiben **NOT_RUN**, bis sie auf dem Gerät durchgeführt wurden.

**Achtung Updatekanal:** Die bereits installierte APK liest weiterhin ausschließlich den ursprünglichen Render-Host `continuum-owner-v23-release-preflight.onrender.com/continuum-local/latest.json`. Eine mit Originalzertifikat signierte GitHub-Release-APK kann manuell als In-Place-Update installiert werden, erscheint aber noch nicht automatisch im bisherigen Update-Button. Ein künftiger Wechsel dieses Buttons auf GitHub muss als getrennte, wieder vollständig originalsignierte App-Änderung entwickelt, getestet und ausgeliefert werden. Niemals eine neue Paketkennung oder Signatur als vermeintliches In-Place-Update verwenden.

Kein automatischer Render-Deploy, kein Main-Merge, keine neuen kostenpflichtigen Dienste und keine Nutzerdateilöschung durch diesen Fallback.
