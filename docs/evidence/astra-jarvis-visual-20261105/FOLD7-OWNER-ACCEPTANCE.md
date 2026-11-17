# Fold7 – Besitzerabnahme des zusammenhängenden Cockpits

Status: **NOT_RUN auf einem physischen Fold7**. Dieses Protokoll ist eine
Prüfanleitung, kein Gerätebeleg. Die geprüfte Quell-SHA steht im zugehörigen
README. Eine Installation oder Veröffentlichung wird damit nicht freigegeben.

## Quellen und Aufnahmen sauber zuordnen

Vor der Messung notieren: tatsächlich installierte Version, Quell-Commit,
APK-SHA-256 und Signaturfingerabdruck, Android-Version, Displaymodus,
Auflösung/Dichte, Schriftgröße, Animationsskala und Energiesparmodus.
Fehlende Angaben bleiben `UNAVAILABLE`; keine Ableitung aus einem Host-Build.

Robolectric zeigt den bestehenden Canvas-Ersatzkopf. Blender rendert das
unveränderte Original-GLB auf der CPU. Erst eine Aufnahme der Android-App mit
aktivem Filament-Renderer belegt deren tatsächliche GPU-Ausgabe. Alle drei
Belegarten getrennt ablegen und entsprechend beschriften.

## Prüffolge

| Schritt | Tatsächliche Bedienung | Erwartung | Gerät |
|---|---|---|---|
| 1 | Fold7 aufgeklappt und zugeklappt auf START zeigen | Ein Originalkopf; separate Originalkugel mit Kern, Partikeln und gegenläufigen Ringen. Krone vollständig, Kinn oberhalb der Dialogkarte. | NOT_RUN |
| 2 | Im Leerlauf mehrere Sekunden aufnehmen | Ruhige Bewegungen, Blinzeln, unabhängige Ringphasen; Mund geschlossen. | NOT_RUN |
| 3 | Freisprechen bewusst aktivieren, echte Frage stellen | Zuhören und Denken folgen realen Zuständen; START bleibt sichtbar. | NOT_RUN |
| 4 | Tatsächliche TTS-Antwort aufnehmen | Lippen folgen ausgegebener Sprache; nach Ende geschlossen. PCM und Wortbereich-Fallback im Beleg unterscheiden. | NOT_RUN |
| 5 | Während TTS STOP betätigen; neue Eingabe machen | TTS stoppt, Mund und Arbeitsfläche schließen sofort; späte alte Sprachereignisse öffnen sie nicht wieder. | NOT_RUN |
| 6 | Kachel Systemstatus antippen | Lichtbahn und Glasfläche entstehen aus der Kachel; Augen führen den kurzen Fensterblick an. Kopf und Kugel bleiben sichtbar. | NOT_RUN |
| 7 | Reale Statusabfrage abwarten und im Fenster scrollen | Tatsächlich gelesene Werte oder verständlicher Lesefehler. Kein erfundener Erfolgszustand. Inneres Scrollen hält das Fenster offen. | NOT_RUN |
| 8 | SCHLIESSEN und anschließend Android-Zurück prüfen | Fläche zieht sich zurück, Blick kehrt zurück; erneutes Öffnen erzeugt genau eine Fläche. | NOT_RUN |
| 9 | Telefon-Cockpit hinter dem Fenster scrollen | Fenster schließt sofort, bevor veraltete Schutzbereiche den Kopf überdecken könnten. | NOT_RUN |
| 10 | Hintergrund/Vordergrund, Falten/Entfalten und Rotation | Alte Fläche wird freigegeben; eine Avatarinstanz und eine Originalkugel bleiben. Keine schwarzen Zwischenbilder. | NOT_RUN |
| 11 | Reduzierte Bewegung und Energiesparen testen | Keine fortlaufenden dekorativen Übergänge; Bedienung, STOP und statische Darstellung funktionieren. | NOT_RUN |
| 12 | Echte Freigabefrage, zunächst ablehnen | Kein Bestätigungsnicken. Zwischenstufe „Weiter prüfen“ löst ebenfalls keine Freigabegeste aus. | NOT_RUN |
| 13 | Nur eine tatsächlich gewollte Aktion endgültig freigeben | Genau ein zurückhaltendes Nicken; es bedeutet Zustimmung, keinen behaupteten Werkzeugerfolg. | NOT_RUN |
| 14 | 100 %, 130 % und 200 % Schriftgröße prüfen | Beide Identitäten bleiben über der Dialogkarte; Bedienelemente bleiben erreichbar. | NOT_RUN |
| 15 | Sehr kurzes Multiwindow mit großer Schrift prüfen | Falls neben geschützten Bereichen keine Fläche von 200 × 160 dp verbleibt, bleibt der vorhandene native Systemstatus-Dialog erreichbar. Dieser Fall ist keine holografische Fensterabnahme. | NOT_RUN |

Keine destruktive Aktion allein für diese Abnahme freigeben. Die Host-Tests der
Bestätigung verwenden lediglich einen Boolean-Aufzeichner und führen kein
Werkzeug aus. Gmail-, PDF- oder Coding-Erfolg sind durch Systemstatus nicht
belegt; fehlende Dienstanschlüsse bleiben offen.

## Timing, Bild und Fehler

Je ein zusammenhängendes Video von geöffnetem und geschlossenem Fold7 erstellen:
START → Zuhören → echte TTS → Systemstatus öffnen → Daten lesen → schließen →
STOP. Die vorhandene Aktion „HOLOGRAMM · LIVE-FRAMES PRÜFEN“ liefert einen
separaten 1,6-s-Beleg für Canvas-Callbacks, Draws und Phasenfortschritt. Diese
Zähler sind keine Filament-FPS und keine Display-Present-Zeiten.

Öffnen/Schließen, Eingabe bis sichtbare Reaktion und STOP bis Audioende aus den
tatsächlichen Aufnahmen messen. Geräte-Frametiming separat mit Android-
Frame-/Perfetto-Werkzeugen erfassen; Host-Zeiten nicht übertragen. Animationen
erst anhand dieser Messung feinabstimmen. Die eingestellten Übergangszeiten
sind Entwurfswerte, keine behaupteten Fold7-Messergebnisse.

Farbvergleich mit dem unveränderten Referenzbild und semantisch passenden,
nativen Bildregionen durchführen. Auflösung, Dichte und Quelle jeder Region
angeben; keine gestreckten Referenzkoordinaten als Farbtreue ausgeben.

Für jeden Fehler: Zeitstempel im Video, Schritt, Zustand, tatsächliches und
erwartetes Verhalten, Quell-/APK-Identität und reproduzierbare Bedienfolge.
Freigabe bleibt beim Besitzer; PR #565 bleibt offen und Draft.
