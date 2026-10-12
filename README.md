# Device AI

Persönlicher lokaler Android-Geräteassistent für ein eigenes Android-Gerät.

## Stand

Die erste funktionsfähige Prototyp-Version enthält:

- lokale Texteingabe statt Termux-Befehlen
- Shizuku-Anbindung mit Shell-Identität
- Geräteinformationen
- Liste installierter Benutzer-Apps
- Downloads anzeigen
- Speicherübersicht
- Screenshot nach Downloads speichern
- Apps per Namen oder Paketnamen starten
- Apps beenden und neu starten
- neueste APK aus Downloads installieren
- Expertenmodus mit `!` vor einem Shell-Befehl
- Bestätigungsdialog vor verändernden Aktionen
- automatischen APK-Build über GitHub Actions

## Voraussetzung auf dem Handy

1. Shizuku installieren.
2. In Android `Entwickleroptionen > Debugging über WLAN` aktivieren.
3. Shizuku über WLAN-Debugging starten.
4. Device AI installieren.
5. In Device AI auf **Shizuku verbinden** tippen und die Berechtigung erlauben.

Danach kann die App die unterstützten Befehle über eine normale Oberfläche ausführen. Termux ist für die tägliche Bedienung nicht mehr erforderlich.

## Beispiele

- `Geräteinfo`
- `Apps`
- `Downloads`
- `Speicher`
- `Screenshot`
- `Öffne WhatsApp`
- `Beende Lumo`
- `Neustart Lumo`
- `Installiere neueste APK`

Expertenmodus:

```
! pm list packages -3
```

Der Expertenmodus fragt vor der Ausführung nach einer Bestätigung.

## APK bauen

Jeder Push auf `main` startet den GitHub-Workflow **Build Device AI APK**.

Nach erfolgreichem Lauf:

1. GitHub > **Actions**
2. neuesten Lauf **Build Device AI APK** öffnen
3. unter **Artifacts** `DeviceAI-debug-apk` herunterladen
4. ZIP entpacken
5. `app-debug.apk` auf Android installieren

## Sicherheitsmodell

Lesende und harmlose Aktionen können sofort ausgeführt werden. Zustandsändernde Aktionen wie App-Stopp, Neustart, APK-Installation und freie Shell-Befehle verlangen eine Bestätigung.

Device AI erhält ohne Root keinen unbegrenzten Zugriff auf private App-Sandboxen wie `/data/data/<paket>`. Die Shizuku-Ebene arbeitet bei einem nicht gerooteten Gerät grundsätzlich mit der ADB-Shell-Identität.
