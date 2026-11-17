# Continuum Lokal – nächste Geräteprüfung und sicherer Zugang

## Kurzer lokaler Prüfweg (keine Installation)

Im vorhandenen `$HOME/device-ai-fold7-candidate` zuerst den Arbeitsbaum kontrollieren. Lokale Änderungen vor einem Wechsel separat sichern; kein Reset. Den Forschungszweig holen und den im PR #215 genannten exakten geprüften Quellcommit auswählen:

```bash
cd "$HOME/device-ai-fold7-candidate"
git status --short
git fetch origin sol/continuum-oneapp-brain-recovery-20261028
# Nur bei sauberem Arbeitsbaum:
git switch --detach 414ce4a2d4643727757f16b4dac9624a8ff8801d
bash scripts/continuum-fusion-full-regression.sh "$(git rev-parse HEAD)"
```

Der Runner benutzt vorhandenes JDK17/SDK36/Gradle8.11.1/native AAPT2. Ergebnisse liegen außerhalb Git. Keine Signierpasswörter vorher exportieren. Der unsignierte QA-Modus erlaubt keine APK-Erzeugung; keine QA-App installieren.

Nach bestandener Prüfung und Besitzerfreigabe kann derselbe bestehende Releaseweg benutzt werden:

```bash
bash scripts/continuum-fold7-standalone-apk.sh build 27
```

Das Skript verlangt die vorhandene Original-P12 und den Zertifikatsfingerabdruck `6354e91dc0ca8df9173885f32402fd817f46e798f369206d4fd710ff3d03ee23`; es erzeugt keinen Ersatzschlüssel. Es führt zuerst die vollständige schlüsselfreie QA aus und fragt erst danach privat nach dem Kennwort. Der Besitzer prüft, dass 27 größer als der installierte Code ist. Es installiert nichts.

Danach muss am Gerät separat nachgewiesen werden: Modellverwaltung → Original `.litertlm` wählen (3659530240 Bytes, SHA256 `0b2a8980ce155fd97673d8e820b4d29d9c7d99b8fa6806f425d969b145bd52e0`), Import während Fold/Activity-Neuerstellung fortsetzen, Hash/Inspektion/READY prüfen, ECHTEN nativen Direkt-Test ausführen und nach Neustart wiederholen. Neue Lifecycle-Unit-Tests ersetzen diese Geräteprüfung nicht. Persistierte Teilkopien erfordern nach vollständigem Prozessende eine bewusste erneute Dateiauswahl; der neue Koordinator garantiert keinen automatischen Byte-Resume nach Prozessverlust.

## Persönliche Daten

Lernzentrum → EINE HAUPT-APP → AKTIONEN bietet Lernprotokoll-Export und eine schreibfreie Übernahmeprüfung. Diese Datei enthält nur `experiences-v2.bin`: keine komplette Gerätesicherung, keine Chats, Einstellungen, privaten Modellgewichte oder Schlüssel. Prüfsumme ist keine Herkunftsbestätigung. Bestehende Widerrufe werden niemals aus einem Backup reaktiviert. Die Übernahme persönlicher Aussagen braucht Einzelprüfung; bisher ist kein automatischer Import implementiert. Die bereits installierte alte APK kann neue Exportfunktionen nicht rückwirkend erhalten. Alle vier Apps behalten ihre Daten.

## Fernzugang – geprüfter Entwurf, noch nicht eingerichtet

Bevorzugt: Verbindung ausschließlich über ein privates lokales WLAN oder ein vorhandenes WireGuard-VPN. OpenSSH mit getrenntem öffentlichem Schlüssel je berechtigtem Entwickler, ohne Passwortanmeldung, ohne öffentlich erreichbaren Port und ohne öffentliches ADB. Kein neuer kostenpflichtiger Dienst nötig; entfernte Cloud-Sitzungen müssen erst eine tatsächlich erreichbare private Route erhalten. Wenn kein vorhandener VPN-Endpunkt existiert, ist ein LAN-Zugang nur für Geräte im gleichen Netz erreichbar. Es wird keine erreichbare Verbindung behauptet.

Wichtige Grenze: Termux-SSH, Gradle und die aktuelle P12 teilen dieselbe Android-UID. Ein allgemeiner SSH-Shellzugang oder frei veränderbarer Buildcode kann unter dieser UID den Signierschlüssel lesen. `proot`, ein anderes Unterverzeichnis oder ein Shell-Wrapper bildet dafür keine belastbare Sandbox. Deshalb derzeit keinen dauerhaften allgemeinen KI-Shellzugang zum schlüsseltragenden Termux freigeben.

Ein sinnvoller erster Zugang ist ein widerrufbarer SSH-Schlüssel mit `restrict` und einem separat geprüften erzwungenen Befehl, der ausschließlich begrenzte Statusdaten ausgibt. Keine Port-/Agentweiterleitung, keine freie Shell, keine beliebigen Pfade, kein Signieren. Für autonome Patch-/Testausführung ist anschließend eine echte getrennte Android-UID/Arbeitsprofil-Umgebung ohne Signierschlüssel oder ein optionaler lokaler Worker nötig. Signieren bleibt bei der Besitzerumgebung. Dieser eingeschränkte Remote-Dispatcher ist hier NICHT implementiert; vor Einrichtung müssen die tatsächlichen Fold7-Netzwerk-/Android-Möglichkeiten und die Widerrufsmöglichkeit lokal geprüft werden.

## Originalschlüssel sichern

Nur auf dem Gerät: zuerst ein verschlüsseltes Backup auf ein zweites physisches Medium, mit neuer unabhängiger Passphrase. Eine Probeentschlüsselung in einem privaten temporären Verzeichnis muss bytegleich zur Original-P12 sein; keine Uploads oder Schlüssel-/Passwortausgaben. Anschließend kann `keytool -importkeystore` die vorhandene Identität in einen neuen privaten PKCS12-Kandidaten mit neuem Kennwort übertragen (Alias `continuum-local`, kein `-genkeypair`). Zertifikat-Fingerprint und Zugriff auf denselben privaten Schlüssel lokal prüfen, bevor der Besitzer die Originaldatei ersetzt. Die Originaldatei bleibt bis zur vollständigen Probe erhalten. Keine Rotation wurde in dieser Sitzung ausgeführt.
