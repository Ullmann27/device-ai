# Auftrag an Opus 5.5 – Continuum: Jarvis am echten virtuellen Computer

Arbeite im Extra-Hochmodus. Setze den bestehenden Code55-Stand fort. Das ist ein Implementierungsauftrag mit echten App-Aktionen, anschließend überprüfbarer Darstellung und sauberen GitHub-Checkpoints.

Repository: `Ullmann27/device-ai`
Designordner: `docs/design/continuum-live-computer-20261112/`
Einstieg: `README.md`, `BAUANLEITUNG.md`, `manifest.json` und sämtliche Bilder unter `bilder/`.
Übergabe: `docs/research/omega/ASTRA-LIVE-COMPUTER-HANDOFF-20261112.md`.
Referenzbranch: `work/continuum-live-computer-design-20261112`.
Implementierungsziel: bestehende Linie `claude/code55-cinematic-evolution-20261112`, PR #791; zuletzt geprüft `caba9c83d13a9dc25b74da5ef9a4c0317ef5e7c8`. Ermittle zuerst den aktuellen HEAD und die neueren Arbeiten. Übernimm den Designordner kontrolliert, falls er in deinem Checkout fehlt. Überschreibe keine fremde Arbeit.

## 1. Das gewünschte Ergebnis

Jarvis sitzt an einem räumlichen Computer. Auf seinem Monitor sieht man exakt die echte App-/Computersitzung, die er gerade bedient. Das Arbeitsprinzip ist wie bei Computer-Use-Bots: beobachten, passende Eingabe ausführen, neuen Zustand prüfen, weiterarbeiten. Eine grafische Computerattrappe oder eine abgefilmte Musteranimation erfüllt den Auftrag nicht.

Wenn ich „Öffne WhatsApp“ sage, öffnet sich mein WhatsApp über den tatsächlich erlaubten Geräteweg. Auf Jarvis' Monitor erscheinen die aktuellen App-Pixel. Gleiches Ziel gilt für mein Facebook, Samsung „Eigene Dateien“, Downloads, Word, Excel und weitere unterstützte Apps. Welche App wirklich spiegelbar und bedienbar ist, wird am konkreten Gerät geprüft und korrekt angezeigt. Keine universelle Kompatibilität behaupten.

Die beigefügten Bilder zeigen Gestaltung und Beispielzustände. Sie sind keine privaten Screenshots. Ersetze ihre App-Inhalte in der Umsetzung durch echte Sitzungsbilder; keine Kontaktliste, Nachricht, Datei oder Erfolgsmeldung aus den Beispielbildern in den Produktzustand übernehmen.

## 2. Exakter Aufbau der Szene

- Behalte den ORIGINALEN Continuum-Avatar, sein Gesicht, seine Morphs und die ORIGINALKUGEL.
- Setze die Kugel DIREKT HINTER Jarvis.
- Jarvis wird leicht transparent. Das Infinity-Logo der Kugel scheint durch die Mitte seiner Stirn oberhalb der Augenbrauen, horizontal zwischen den Augen. Gesicht und Augen bleiben gut erkennbar.
- Vor ihm steht ein hochwertiger, aufrechter Computer mit geraden rechteckigen Monitorflächen. Leichte räumliche Drehung wie an einem Arbeitsplatz; keine liegenden Tablets und kein schräg gekippter Gesamtbildschirm.
- Hauptmonitor groß und lesbar, links bei genügend Breite Dateien/Navigation oder eine Auftragsansicht. Rauchglas, Tiefe, plastische Rahmen und dezente Lichtreflexe außen. Innerhalb des Monitors bleibt die echte App-Oberfläche scharf und unverändert.
- Inhalte richtig herum und im richtigen Seitenverhältnis anzeigen. Keine Hologramm-Effekte über Nachrichten, Zellen oder Schaltflächen legen.
- Kopf, Hals und Schultern sind oberhalb bzw. seitlich des Monitors sichtbar. Jarvis wirkt wie jemand, der wirklich am Computer arbeitet.

## 3. Vollständiger WhatsApp-Ablauf

Beispielauftrag: „Bereite eine Nachricht an Anna vor: Ich bin in 20 Minuten da.“

1. Jarvis nimmt den Auftrag an, schaut kurz zum Benutzer und anschließend zum Monitor.
2. Er öffnet die echte App. Die Kamera/Monitorfläche gleitet weich in die Arbeitsansicht. Der App-Inhalt erscheint erst, wenn echte frische Bilddaten vorliegen.
3. Er öffnet die tatsächliche Suche oder Kontaktauswahl. Sein Blick geht zur Schaltfläche, danach folgt die echte Eingabe mit einem kleinen Klicksignal.
4. Er fokussiert das Suchfeld und gibt den Namen ein. Die Zeichen und Suchergebnisse müssen in der tatsächlichen App erscheinen.
5. Falls nötig scrollt er durch die echte Kontaktliste. Die Liste bewegt sich kontinuierlich; Augen und Kopf verfolgen den relevanten Bereich. Kein rein dekoratives Scrollen, wenn der Treffer bereits eindeutig sichtbar ist.
6. Bei mehreren passenden Kontakten fragt er gezielt nach. Bei eindeutiger Zuordnung öffnet er den Chat und prüft den tatsächlichen Chatkopf.
7. Er gibt den beauftragten Text ein. Dezent bewegen sich die Schultern und gegebenenfalls die Hände. Das sichtbare Eingabefeld zeigt den wirklichen Entwurf.
8. Bei „Vorbereiten“ bleibt es ein Entwurf. Versand nur bei entsprechendem ausdrücklichem Auftrag und gemäß bestehender Freigaberegeln. Ein bloßes „WhatsApp öffnen“ erlaubt keine Nachricht.
9. Nach dem tatsächlichen Arbeitsschritt schaut er zum Benutzer zurück und nennt nur den verifizierten Stand.

Wenn Angaben schon im Auftrag enthalten sind, nicht erneut nach ihnen fragen. Fehlt wirklich eine notwendige Angabe, bleibt die relevante Ansicht sichtbar und Jarvis fragt nach. Er soll während des ganzen Vorgangs präsent bleiben.

## 4. Weitere sichtbare Arbeitsvorgänge

- **Facebook:** öffnen → Suchfeld → Suchbegriff eingeben → tatsächliche Ergebnisse → bei Bedarf Kategorien/Scrollen → gewünschten Beitrag öffnen. Öffnen/Lesen berechtigt nicht zum Liken, Beitreten oder Veröffentlichen.
- **Eigene Dateien:** echte App öffnen → Navigation → Download-Ordner wählen → reale Liste → suchen/sortieren/scrollen → richtige Datei auswählen → Vorschau bzw. Ziel-App öffnen. Auf Fold7 links Navigation, mittig Liste und gegebenenfalls rechts Vorschau, soweit die tatsächliche Quelle dies unterstützt. Keine erfundenen Spalten in den gespiegelten App-Inhalt malen.
- **Word:** reale Datei öffnen → Absatz aufsuchen → Editor fokussieren → gewünschten Text bearbeiten → Ergebnis prüfen → Speichern nur nach tatsächlichem Beleg melden.
- **Excel:** reale Arbeitsmappe und Blatt öffnen → Zeilen/Spalten bewegen → richtige Zelle selektieren → Wert/Formel eingeben → Ergebnis und Speichern prüfen.
- **App-Wechsel:** Arbeitsplatz bleibt stabil; alte Arbeitsfläche gleitet weich nach links, neue Fläche kommt in den Fokus. Neue App-Pixel erst anzeigen, wenn die richtige Quelle tatsächlich bereit ist. Quellumschaltung und erforderliche Android-Auswahl berücksichtigen.

## 5. Bewegung und kleine Effekte

Binde alle Arbeitsbewegungen an echte Ausführungsereignisse. Keine zufällige Dauerschleife, während intern etwas anderes geschieht.

- Blick führt zum tatsächlichen Ziel, danach folgen kleine Kopf-/Schulterbewegungen.
- Reales Klicken: dezenter Tap-Ring/Mauszeiger am korrekten Ort, ein leiser kurzer Klick und minimale Schulterbewegung.
- Reales Scrollen: kontinuierliche App-Bewegung und sanfte Blickverfolgung.
- Reale Texteingabe: kleine natürliche Tippbewegung. Wird Text technisch in einem Schritt eingesetzt, keine angeblich live getippten Einzelzeichen erfinden.
- Beim Laden: natürliche Atmung/Blinzeln und erkennbares Warten. Keine fortgesetzten Tippgeräusche.
- Bei Rückfragen: Arbeitsbewegung pausiert, Jarvis schaut zum Benutzer und spricht.
- Bei Stop, Fehler oder Freigabeentzug: keine weitere Eingabe, keine verspäteten Klickgeräusche, klarer sichtbarer Zustand.

Animationen weich beschleunigen/abbremsen, Position und Geschwindigkeit kontinuierlich halten. Monitorfokus etwa 250–420 ms und Andocken etwa 320–500 ms als erste Gestaltungswerte; echte App-Antwortzeiten bestimmen den Ablauf. Keine festen Gesamtzeiten vortäuschen und den Auftrag nicht für Filmbewegungen künstlich ausbremsen.

## 6. Farbige Buttons und beide Displayformen

Unten bleiben acht hochwertige plastische Glasbuttons:

Start Cyan; Gespräch Türkis; Gedächtnis Violett; Forschung Indigo; Aufgaben Bernstein; Internet Blau; Lernen Grün; Geräte Koralle.

Jeder Button hat Symbol, lesbare Beschriftung, räumliche Kanten, kontrollierte Reflexe und sichtbaren Aktivzustand. Darüber steht das Eingabefeld „Was soll ich für dich erledigen?“.

Fold7 offen: acht Buttons in einer Reihe, breiter Arbeitsbereich. Normales Smartphone: zwei Reihen mit je vier großen Buttons, ein gut lesbarer Hauptmonitor, Nebenspalten aufklappbar. Beide Varianten müssen vollständig umgesetzt und geprüft werden. Falten, Drehen, Bildschirmtastatur und Insets berücksichtigen; nicht die ganze Desktopansicht kleinquetschen.

## 7. Technische Umsetzung und Ehrlichkeit

Prüfe zuerst vorhandene Filament-, AvatarDirector-, ToolGate-, Auftrags-, ScreenObservation- und Gerätepfade. Verwende die vorhandene Engine und originale Assets. Keine zweite konkurrierende Aufgabenverwaltung, keinen zweiten Renderer und keine PC-Pflicht ohne belegten Grund.

Im geprüften Code55-Stand ist `chief/DeviceControl.kt` ein inaktiver Kandidat, kein angeschlossener Beweis für Scrollen/Tippen. Der Manifest enthält dort keinen MediaProjection-Foreground-Service. Der native Klickkandidat erwartet ein einzelnes Fenster. Diese Lücken sauber schließen; nicht Prüfungen pauschal entfernen.

Bildquelle und Bedienung getrennt implementieren: MediaProjection liefert nach Android-Freigabe echte Bilder, erteilt aber keine Steuerungsrechte. Ein MediaProjection-VirtualDisplay ist auch keine allgemeine virtuelle Maschine für fremde Apps. Eine bestehende virtuelle Bot-Sitzung nur nach geprüftem Anschluss verwenden und als solche kennzeichnen.

Auf demselben Handy ist das gleichzeitige Anzeigen und Bedienen einer fremden App im vollständigen Cockpit nicht pauschal möglich. Prüfe Mehrfenster-/Sichtbarkeits-/Fokusverhalten auf Fold7 und Smartphone. Wenn ein konkreter Weg nicht unterstützt wird, klar benennen und einen echten zulässigen Darstellungsweg nutzen. Keine simulierten Livebilder und keine Umgehung von geschützten Fenstern.

Kopple Auftrags-ID, Sitzungs-/Fensterbindung, Aktion, neue Beobachtung und Bildzeit. Veraltete Bilder als pausiert kennzeichnen. Erfolg erst nach bestätigter Wirkung. Keine doppelten Aktionen nach unklarem Ergebnis. Verhindere Capture des eigenen Spiegelbilds und eine Bildschirm-in-Bildschirm-Schleife.

Baue eine sparsame Surface-/GPU-Pipeline ohne dauernde PNG/JPEG-/Base64-Kopien, unbeschränkte Frame-Warteschlangen oder Modellabfragen pro Bild. Bestehende Cinematic-Effekte erhalten. Reale Wärme, Framerate und Latenz messen statt Verbesserungen zu behaupten.

## 8. Vorgehen und Ergebnisnachweise

Setze zuerst einen kleinen echten Durchstich um: freigegebene App → tatsächliches Bild auf dem Monitor → reale Suche/Scrollen/Auswahl → bestätigter Folgezustand. Danach Avatar/Ton/Übergänge anbinden und beide Displayformen prüfen. Anschließend weitere Apps über denselben allgemeinen Weg erweitern.

Liefer echte Vorher-/Nachher-Bilder und eine Geräteaufnahme des vollständigen Vorgangs. Prüfe Stop, fehlende Rechte, Capture-Abbruch, mehrdeutige Kontakte, App-Wechsel und Rotation. Hosttests, Emulator und Fold7-Belege getrennt benennen. Keine automatischen privaten Nachrichten als Test versenden.

Sichere sinnvolle Zwischenstände im bestehenden GitHub-Projekt. Nenne Branch, exakten Commit, echte Prüfungen und offene Grenzen. Halte `PORTIERT`, `VERIFIZIERT` und `GERÄTEBESTÄTIGT` auseinander. Keine Änderung an Paketidentität oder Signatur; kein Main-Merge ohne Freigabe.
