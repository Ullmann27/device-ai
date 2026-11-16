# Command03: native Bedienung im Original-HTML vervollständigen

Status: **WIP / neue Korrekturen noch nicht implementiert oder geprüft**.

## Quelle und Integration

Ausgangspunkt: `e92001ad6c28cdfd75e3fc29bb6efb65e832afa0`, kanonische Linie `integrate/continuum-command03-android-20261115`, PR851. Der vorige Medienstand wurde auf `b56a614b` mit153 gezielten Fällen und einer unabhängigen unsigned APK-Prüfung bestätigt. Diese Ergebnisse gelten nicht automatisch für die folgenden Korrekturen.

Integrationsverantwortlich: Root; genau drei bestehende unterstützende Agents für Android, Web und QA. Arbeitszweig: `fix/command03-native-controls-20261116`. Ziel ist die bestehende kanonische Linie, keine neue Produkt- oder Renderarchitektur.

## Belegte noch offene Anschlüsse

1. **Computer-Tab:** Bei bereits aktivem Computer ruft die HTML-Route device den Stop-Pfad auf und öffnet einen zusätzlichen nativen Dialog. Der aktive Computer soll stattdessen im vorhandenen HTML-Bereich ausgewählt werden; inaktive Einstiege und tatsächlich verdeckende Routen behalten ihre bisherigen Grenzen.
2. **Entwurf und Sprache:** dirtyDraft verhindert nach HTML-Bearbeitung spätere native Diktat-/Vorbelegungs-/Sendeaktualisierungen. Erforderlich ist eine bestätigte lokale Editrevision statt pauschalem Überschreiben.
3. **Fokus und Mikrofon:** Native Composer-Fokuswünsche erreichen das verdeckte EditText. Der vorhandene stille Diktier-/Gesprächsende-Langdruck ist im HTML nicht angeschlossen.
4. **Dialogansichten:** Hauptchat und Helferbot verwenden im HTML dieselbe Sitzungskennung; beim Wechsel werden Verläufe sichtbar angehängt. Die nativen Stores sind bereits getrennt. Eine zusätzliche revisionsgebundene Ansicht muss verspätete Verläufe, Antworten und Eingaben abweisen.
5. **Quellenkarten:** Die vorhandenen anklickbaren Quellen werden ausschließlich im verdeckten nativen Container ausgegeben. Der HTML-Adapter benötigt den bestehenden, an den aktuellen Dialog gebundenen Quellenzugriff.
6. **PDF:** Der vorhandene native Knopf zur Entwurfserstellung fehlt im HTML-Dokumentbereich; die Funktion ist per normalem Auftrag bereits erreichbar.

Die Befunde wurden im tatsächlichen Activity-/Stage-/JS-Aufrufpfad geprüft. Allgemeine Phone-/Fold-Geometriefehler werden daraus nicht abgeleitet.

## Umsetzung und Prüfungen

Die35 Originalassets und der vorhandene Renderer bleiben bytegleich. Native Capture-, ToolGate-, Composer-, Sprach-, Bot- und Dokumentpfade werden weiterverwendet. Gezielte negative Prüfungen müssen alte Dialog-/Seitenkennungen, neue lokale Eingaben während nativer Verarbeitung, späte Rückmeldungen und tatsächliche Verdeckung abdecken.

Noch offen: Implementierung, neue Rot-/Grün-Belege, kompilierter eingefrorener Quellstand, gezielte Android-/Web-Regressionen und aktualisierte unsigned Prüf-APK.

## Arbeitsumgebung

Während der lesenden Bestandsprüfung wurde die lokale Umgebung vorübergehend unavailable. Nach Wiederanbindung sind Quelle, Toolchain, aktuelle Compilerzustände, b56-APK und das dauerhaft gesicherte Prüfpaket vorhanden. Frühere RAM-Auslagerungen fehlen nach dem Neustart. Diese werden ausdrücklich als fehlende historische Zwischenstände dokumentiert; keine alten Testergebnisse oder Dateien werden fingiert. Eine neue externe Orchestrierung wird auf den tatsächlich vorhandenen b56-Nachweis gebunden und erhält die bisherigen Quell-, Lock-, Platz- und Ergebnisgrenzen.

Originalsignierung und Veröffentlichung bleiben an die vorhandene vollständige quellgebundene Release-QA und geschützte Besitzerfreigabe gebunden. Der neue Arbeitszweig löst diese Schritte nicht aus.
