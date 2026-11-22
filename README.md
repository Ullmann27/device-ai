# Device AI

> **AKTUELLER PROJEKTHINWEIS (31.10.2026):** Diese README beschreibt den alten Grundstand, nicht die neuesten Continuum-Lokal-Forschungszweige. Für Sol/Astra/Opus vor Änderungen die [Agenten-Startanleitung](AGENTS.md), die [aktuelle kanonische Projektübergabe](https://github.com/Ullmann27/device-ai/blob/research/continuum-nemo-virtual-fabric-20261031/docs/CONTINUUM-CURRENT-STATE-20261031.md) und [Forschungs-PR #287](https://github.com/Ullmann27/device-ai/pull/287) lesen. Ziel: echte Android-JARVIS-Funktion mit M8-Coding-Agent, CRE1/EWM/SWB, biologisch/zeitabhängiger Forschung und originalsigniertem In-App-Update. 3.000 logische Experten sind **keine** 3.000 physischen Prozessoren; der synthetische 3.000→30-Befund ist noch kein realer Modellbenchmark. Installiert war zuletzt laut Geräteverlauf Continuum Lokal 0.3.28/code29; neue Version nur mit höherem code und Originalzertifikat.


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
