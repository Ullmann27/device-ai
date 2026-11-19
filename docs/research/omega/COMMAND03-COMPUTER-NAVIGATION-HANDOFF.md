# Command03: Computer-Navigation ohne Sitzungsabbruch

Stand: **16.11.2026**. [PR855](https://github.com/Ullmann27/device-ai/pull/855)
führt die bestehende Code-60-Linie aus [PR851](https://github.com/Ullmann27/device-ai/pull/851) fort.
Geprüfter Produkt- und Teststand: **`7e783f3ee8bc3e75f800b8249f5cef283187752d`**.

## Änderung und Ursache

Der originale HTML-Computer-Button sendet die Route `device`. Die Activity rief
bisher zuerst `hideCommand03Computer()` auf. Das stoppte den bestehenden
`OwnerComputerSessionController` und löschte seinen Bildstrom, bevor das native
Computerzentrum geöffnet wurde. Die Überlagerung verdeckte außerdem die HTML-Darstellung.

Die Korrektur ergänzt fünf Zeilen im vorhandenen `routeCommand03HtmlAction`:
Bei `device` und bereits aktiver Sitzung wird der bestehende HTML-Abschnitt
geöffnet und die vorhandene Bildaktualisierung eingeplant. Die Methode kehrt
anschließend zurück. Derselbe Controller, Bildstrom und dieselbe WebView bleiben
erhalten; es entstehen keine neuen Capture- oder Ausführungspfade.

Ohne aktive Sitzung öffnet sich weiterhin das vorhandene Computerzentrum mit
Auftragseingabe und Systemfreigaben. Der Wechsel zu Chat und der Stopp-Button
beenden die Sitzung weiterhin. Quellen-, Sichtbarkeits-, Alters-, Darstellungs-
und Lifecycle-Grenzen bleiben im bestehenden Medienpfad. Die **35 freigegebenen
Originalassets mit 15.864.219 Bytes sind unverändert**.

## Reproduzierter Fehler und frische Nachweise

Der Regressionstest benutzt die tatsächliche Activity/WebView-Routenzustellung.
Nur die bereits aktive Computersitzung ist eine synthetische Testvorbedingung;
der Test startet weder Bildschirmaufnahme noch externe App-Aktionen.

Auf `3cf133527a86bb1c227a3d732888574e4f0d0996` liefen 17 Bridge-Fälle:
**16 bestanden, genau der neue aktive Computerfall schlug fehl** mit
`Opening Computer must preserve the active session`. Der frühere Lauf auf
`0d825f96` hatte zusätzlich eine unvollständige Testvorbedingung im Leerlauffall:
Das adaptive Kontrollraster erzeugt seine Kinder erst beim Layout. Der ergänzte
Layoutdurchlauf behält sämtliche Inhaltsprüfungen bei; Produktcode blieb dabei unverändert.

Alle folgenden Ergebnisse stammen frisch von **`7e783f3e`**, mit sauberem und
vor/nach jedem Lauf unverändertem Quellstand:

| Prüfung | Ergebnis | Nachweis |
| --- | ---: | --- |
| Android-HTML, Bridge und Medien | **25/25 PASS** | `navigation-green-ui-01` |
| Computer-, Vertrags- und Sprachlogik | **71/71 PASS** | `navigation-green-unit-01` |
| Klassische UI und Rückweg | **32/32 PASS** | `navigation-green-classic-ui-01` |
| DOM und verpflichtendes natives JSON-Paket | **28/28 PASS** | `web/web-verification.json` |
| **Gesamt** | **156/156 PASS** | Keine Fehler, keine Skips |
| Originalasset-Abgleich und JS-Syntax | PASS | 35 Originalassets bytegleich |
| Regulärer unsigned Release-Bau | **PASS, 147,414 s** | `navigation-unsigned-01` |
| Unabhängiger APK-Audit | **PASS** | `apk/independent-apk-review-7e783f3e.json` |

Die [versionierten Belege](command03-navigation-qa-7e783f3e/README.md) enthalten
Befehle, Quellenhashes, vollständige Laufberichte, 18 JUnit-XML-Dateien, DOM-TAP,
das frisch serialisierte synthetische Bildpaket und das unabhängige APK-Audit.
Frühere Läufe werden nicht zu den 156 bestandenen Fällen addiert.

Die eigene Arbeitskopie und der neue Buildordner wurden getrennt von laufender
Arbeit anderer Chats angelegt. Der Buildordner begann leer; es wurden keine
Compiler-Ausgaben eines anderen Checkouts hineinkopiert. Der bestehende
verifizierte Gradle-Abhängigkeitscache und die installierte Toolchain wurden
offline weiterverwendet. Der Release-Lauf führt `compileReleaseKotlin` und den
regulären Paketbau aus. Das ist kein Nachweis eines vollständig leeren
Abhängigkeitscaches und keine vollständige Release-QA.

## Aktualisierte Prüf-APK

- Paket: `at.heinz.deviceai.continuumlocal`.
- Version: **Code60 / 0.3.59 / arm64-v8a**.
- Größe: **111.421.502 Bytes**.
- SHA-256: **`0cc23f3e340295b4d6567ed643e9e315cba35f1ce24becc4d9afbac613f8ffd6`**.
- Zustand: **UNSIGNED_NOT_INSTALLABLE**.
- Unabhängig bestätigt: alle **38** verpackten HTML-/Adapterdateien bytegleich
  zur Quelle, ZIP-CRC, sichere eindeutige Pfade, fünf ARM64-Bibliotheken,
  16-KiB-ELF- und ZIP-Ausrichtung, Paket/SDK sowie fehlende Signatur.

Der erste Audit-Aufruf verwendete versehentlich den nicht vorhandenen
Build-Tools-Pfad `36.0.0` und brach vor einem Audit-Ergebnis ab. Der anschließende
vollständige Audit verwendet die im Buildbeleg ausgewiesenen installierten
Build-Tools **35.0.0**. Es wurde weder eine Prüfbedingung geändert noch ein
fehlgeschlagener Signaturtest als erfolgreich bezeichnet.

## Grenzen, Fortsetzung und Rückfall

**PORTIERT:** ja. **VERIFIZIERT:** lokale Fokusprüfungen und unsigned APK.
**GERÄTEBESTÄTIGT:** weiterhin **NOT_RUN**. Die Robolectric- und DOM-Prüfungen
belegen keine realen Android-WebView-/GPU-Pixel oder hörbare Lippensynchronität.
Hier war kein Android-Gerät angeschlossen und kein Emulator installiert.
Ein zusätzlicher Browser-Sichtvergleich scheiterte an der lokalen
Browser-/Verbindungsumgebung; es gibt daraus kein Screenshot-Ergebnis.

Nächster Einstieg: aktuellen Remote-HEAD von PR851 lesen, diesen Quellstand und
APK-Hash in den bestehenden Besitzer-Releaseweg übernehmen und die tatsächliche
Phone/Fold-Darstellung, Computer-Navigation, Sichtbarkeitsgrenzen und Sprache
auf dem Gerät prüfen. Diese Arbeit verändert weder Signierschlüssel noch
Paketkennung und nimmt keinen Main-Merge oder Besitzerrelease vor.

Die nachfolgende Nachweissicherung darf den geprüften `app`-Baum
**`6f34fa6a25cf884bb7fe3d7cb708d18e4c0a7148`** nicht ändern.
Rückfall bei einem neuen Befund: die fünf Routingzeilen durch einen gezielten
Folgecommit zurücknehmen; keine fremden Zweige zurücksetzen und keine
Medien-/Berechtigungsprüfung abschwächen. Die drei neuen Regressionen und ihre
Belege bleiben als nachvollziehbare Anforderung erhalten.
