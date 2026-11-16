# Command03: native Bedienung im Original-HTML vervollständigen

Status: **PORTIERT / gemeinsamer Prüfkandidat eingefroren / native Verhaltensprüfung und unsigned APK noch offen**.

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

### Entwicklungscheckpoint 16.11.2026, 06:10 UTC

Der erste kleine Patch erhält eine bereits aktive Computersitzung beim erneuten Auswählen von Computer. Der neue native Regressionstest führt die tatsächliche Stage-Route zweimal aus und prüft zusätzlich, dass Chat die Sitzung weiterhin beendet und ein anschließender inaktiver Computereinstieg nur das Center öffnet. **NOT_RUN**: Dieser neue Androidfall wurde noch nicht kompiliert oder ausgeführt.

Der begonnene Wire-v2-Vertrag ergänzt native Seiten- und Ansichtsinstanzen sowie eine geordnete Editbestätigung. Der reine DraftLedger vergleicht Revision, vollständigen Text und Auswahl zusammen. Activity, Stage und HTML werden im nächsten Schritt gemeinsam umgestellt; dieser Zwischenstand ist ausdrücklich **nicht als kompilierbar bestätigt**.

Ein gesonderter DOM-Rotlauf gegen den noch unveränderten bisherigen Adapter reproduziert sieben Fehler bei 34 Fällen (27 bestanden, 7 fehlgeschlagen, keine übersprungen). Das ist ein Fehlernachweis des alten Adapters und kein bestandener Test dieses Zwischenstands. Die endgültige grüne Prüfung muss von der später veröffentlichten vollständigen Quelle stammen.

### Entwicklungscheckpoint: Stage und HTML auf Wire v2

Stage und separater HTML-Adapter sind gemeinsam umgestellt. Alle Transportaktionen und Rückmeldungen tragen die native Seitenkennung, eine eigene Ansichtsinstanz mit monotoner Revision und die Gesprächskennung. Text und Auswahl werden als eine Editrevision übergeben. Die Stage verwirft alte Queues beim Seiten-/Ansichtswechsel; echte Stop-Aktionen bleiben auch bei einem nicht übertragbaren Entwurf möglich. Der HTML-Adapter ergänzt die vorhandenen Quellen- und PDF-Aktionen, den Mikrofon-Langdruck und gebundene Fokuswünsche.

Die sieben zuvor roten neuen HTML-Kontrollfälle bestehen im gezielten Entwicklungslauf jetzt **7/7**; die JavaScript-Syntaxprüfung besteht. Der unveränderte Rotlauf mitsamt damaligen Test-/Adapterhashes ist separat erhalten. Die bisherige DOM-Regressionsauswahl wird erst anschließend auf den strengeren Transportvertrag umgestellt. **Kein Gesamtgrün und kein Android-Kompiliernachweis**: Activity und native Testfixtures sind noch in Arbeit. Das nächste Abnahmekriterium ist weiterhin der gemeinsame saubere Quellfreeze mit vollständig ausgeführten gezielten Regressionen.

### Entwicklungscheckpoint: native Produktionsanbindung

Die Activity-Anbindung ist jetzt als zusammenhängender Patch geschrieben: echte Ansichtsinstanzen für Haupt-/Helferchat, exakt gebundene Entwürfe und Verlaufsladungen, native Mikrofon- und Fokuspfade, opake Quellen- und Dokumentkennungen sowie ein Produzenten-Guard für verspätete Cloudantworten und deren Werkzeugfortsetzung. Bei einer durch den angenommenen Auftrag selbst ausgelösten Botwahl darf nur dessen ausdrücklich zugeordneter neuerer Entwurf kontrolliert übernommen werden. Ein begrenzter Übergangshinweis erlaubt dem HTML, noch unbestätigte lokale Eingaben separat zur manuellen Wiederherstellung anzubieten; diese HTML-Ergänzung ist noch in Arbeit.

Beim erfolgreichen PDF-Entwurf schließt nur der Abschluss zum exakt aktuellen Dokument die HTML-PDF und zeigt das Gesprächsergebnis. Alte Dokumentantworten dürfen eine inzwischen andere PDF nicht schließen. Fehlermeldungen bleiben dem auslösenden Dokument zugeordnet. Automatische Ansichtswechsel vergeben keinen neuen Eingabefokus; dafür ist ein ausdrücklicher Fokusauftrag erforderlich.

Dieser veröffentlichte Zwischenstand dient einer **diagnostischen Hauptkompilation**, während native Testfixtures und letzte HTML-Negativfälle vervollständigt werden. Noch **kein Compilererfolg**, keine frische Android-Testwertung und keine neue APK bestätigt. Der finale saubere Quellfreeze und seine tatsächlichen Prüfbelege folgen gesondert.

## Gemeinsamer Prüfkandidat

Die diagnostische Hauptkompilation auf `5e7b9eddb5252d570e7a99eaaa845afb53e38485` bestand am 16.11.2026 in 34,275 Sekunden. Der reguläre Task `:app:compileDebugKotlin` lief tatsächlich, ohne Kotlin-Compilerfehler; der ursprüngliche Checkout wurde unverändert restauriert. Diese Diagnose ist kein Testergebnis der später ergänzten Quelle.

Der nun vollständige Kandidat ergänzt noch den Cause-Token im zweiten asynchronen Bot-Auswahlschritt, den effektiven nativen Fokusfilter und die Zurückweisung alter PDF-Abschlüsse vor Änderungen an der aktuell geöffneten Arbeit. Ein alter PDF-Erfolg oder -Fehler darf nur seinen eigenen Journaleintrag aktualisieren, nicht den heutigen Dokumentbezug, die Ansicht oder den Aktionszustand.

Vorbereitete gezielte Auswahl: 34 native HTML-/Bridge-/Medienfälle, 77 Unitfälle einschließlich sechs neuer DraftLedger-Fälle, 32 bisherige Klassikfälle und sechs bereits vorhandene Dokumentregressionen. Die Dokumentfixture wählt dafür jetzt ausdrücklich den klassischen Modus; ihre Assertions bleiben bestehen. Die tatsächlichen Ergebniszahlen werden erst aus frischen Laufbelegen übernommen.

Der letzte vollständige Entwicklungslauf des HTML-Adapters besteht mit **44/44** Fällen, ohne übersprungene Tests. Die finale Prüfung muss zusätzlich das frisch von der aktuellen Kotlin-Stage erzeugte JSON verwenden; dafür ist `COMMAND03_REQUIRE_NATIVE_PACKET=1` gesetzt. Die ersten Rotläufe, fokussierte Zwischenprüfungen und die Diagnosequelle werden getrennt vom finalen Nachweis aufbewahrt.

Die statische Quellenprüfung besteht weiterhin für **35 Originalassets / 15.864.219 Bytes**. Ihr veralteter wortwörtlicher Session-Guard wurde an den neuen vollständigen Seiten-/Ansichts-/Sitzungsguard angepasst; Asset-Pins, Blob-Hashes und der Vertrag der exakt zwei separaten Android-Includes bleiben unverändert. Die Verhaltenssicherheit wird zusätzlich von den echten negativen DOM-/Androidfällen geprüft, nicht aus dieser Zeichenfolgenprüfung abgeleitet.

Nächste tatsächliche Schritte: saubere veröffentlichte Quelle kompilieren, vorbereitete native Auswahl ausführen, frisches Kotlin-Paket exportieren und den vollständigen DOM-Lauf damit durchführen, bestehende Releaseverträge prüfen, unsigned APK regulär bauen und unabhängig auf Quellen-/Assetgleichheit prüfen. Bei Fehlern wird der konkrete Befund repariert und ein neuer vollständiger Prüfstand veröffentlicht. **GERÄTEBESTÄTIGT: NOT_RUN.**

## Arbeitsumgebung

Während der lesenden Bestandsprüfung wurde die lokale Umgebung vorübergehend unavailable. Nach Wiederanbindung sind Quelle, Toolchain, aktuelle Compilerzustände, b56-APK und das dauerhaft gesicherte Prüfpaket vorhanden. Frühere RAM-Auslagerungen fehlen nach dem Neustart. Diese werden ausdrücklich als fehlende historische Zwischenstände dokumentiert; keine alten Testergebnisse oder Dateien werden fingiert. Eine neue externe Orchestrierung wird auf den tatsächlich vorhandenen b56-Nachweis gebunden und erhält die bisherigen Quell-, Lock-, Platz- und Ergebnisgrenzen.

Originalsignierung und Veröffentlichung bleiben an die vorhandene vollständige quellgebundene Release-QA und geschützte Besitzerfreigabe gebunden. Der neue Arbeitszweig löst diese Schritte nicht aus.
