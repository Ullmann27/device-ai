# Sichtabnahme: Gedächtnisepisode gegenüber Referenzmontage

**Die sichtbare Kernfolge ist nachgebildet: helle Dokument-/Ordnerflächen im Tiefenflug → große bestehende Kugel → Rückkehr zu Kopf und kleiner Kugel im Cockpit. Die gesamte Referenzmontage ist damit nicht visuell gleichwertig nachgebildet.**

Read-only-Abnahme am 7. November 2026. Eingefrorene Quelle: `3ff83387af64bf9f99f4440f657f3d27937ca777`, Tree `5206abd91b3740838661aa0e27e6702b3891591b`.

## Gesichtet

- Private Kontaktübersicht der Referenzmontage, ausschließlich lokal angesehen.
- Zwölf gezielt ausgewählte Originalframes des tatsächlichen Activity-/Android-Native-Canvas-Exports: `00`, `03`, `06`, `09`, `12`, `15`, `18`, `19`, `20`, `21`, `22`, `23`.
- Die vorhandene Serie umfasst 24 PNGs mit 884 × 760 Pixeln, 220 ms Abtastabstand und 5.060 ms erfasster Zeit. Die Episode selbst ist auf 4,8 Sekunden begrenzt. Die Zeitangaben unten sind Sample-Abstände ab dem ersten exportierten Bild, keine sekundengenaue Zuordnung zur Referenzmontage.

Der Export entstand vor dem finalen Freeze aus dem dokumentierten Produktionsoverlay. Alle sechs im Exportreceipt gebundenen Produktionsdateien haben exakt dieselben SHA-256-Werte wie im jetzigen Freeze. Es wurden weder neue Frames noch neue Tests erzeugt. Einzelbild- und Quellhashes stehen in `source-and-observation-manifest.json`.

## Sichtbare Übereinstimmung und Restabstand

| Merkmal | Tatsächlich sichtbarer Hostbeleg | Verbleibender Abstand / Grenze |
| --- | --- | --- |
| Dokument-/Ordnerflug | Frames 03/06 zeigen helle Papierflächen und gelbe Ordner; unterschiedliche Größen und radiale Lichtlinien vermitteln Tiefe. Die Szene nimmt den großen zentralen Bereich ein. | Die Referenz zeigt einen deutlich dichteren Schwarm, stärkere Tiefenstaffelung und große, unterschiedlich gestaltete Seiten. Im Host sind die Flächen weniger zahlreich und überwiegend einheitliche Symbole mit kurzen Linien. |
| Annäherung und Übergang | Frame 09 zeigt Papierflug und eine sich aufbauende Kugel gleichzeitig; Frames 12/15 zeigen die große Kugel. Der Tiefen-/Annäherungseindruck ist sichtbar. | Das belegt die visuelle Größen-/Positionsfolge. Eine tatsächliche dreidimensionale Kamerafahrt oder gleiche Perspektive zur Referenz folgt daraus nicht. |
| Große Kugel | Frames 12/15/18 zeigen eine helle, zentral vergrößerte Kugel mit dem bestehenden Unendlichkeitszeichen, Netzwerkstruktur und Orbitalbögen. | Die Referenz zeigt eine violette, plasmatische Energiekugel mit anderem Kern, Lichtverlauf und anderer Zeichendichte. Der Host behält die vorhandene eigene Kugelgestaltung; keine Gleichheit der Oberflächen oder Lichtsimulation. |
| Rückkehr ins Cockpit | Frames 19–22 zeigen die Verkleinerung und Bewegung der Kugel nach rechts; der Kopf wird links wieder sichtbar. Frame 23 zeigt die wiederhergestellte Cockpitkomposition ohne Dokumentflug. | In der Referenz wird stärker zwischen bildfüllender Effektszene und Cockpit umgeschnitten. Im Host bleiben Navigation, Dialog, Kacheln und Statusbereiche durchgehend davor sichtbar; das wirkt weniger wie eine vollständig übernommene Filmfläche. |
| Kopf und Avatar | In Start-/Rückkehrframes ist links ein cyan-violetter Linienkopf sichtbar. | **Das ist die Aufnahmegrenze dieses Native-Canvas-Hosts.** Die Serie enthält keinen Filament-GPU-Nachweis. Daraus lässt sich kein fehlender 3D-Gerätepfad ableiten; Original-GLB und Texture-Pfad sind bewusst erhalten. Die Referenz zeigt einen großen, zentralen, plastisch wirkenden Kopf-/Brustkörper. |
| Gesamte Auftragsmontage | Diese Serie zeigt die begrenzte Gedächtnisepisode und ihre Rückkehr. | Die Referenzübersicht enthält zusätzlich Gmail-/Telefon-/Messenger-Kacheln, ein großes PDF-Motiv, Dokument-, Kalender- und Wetterfenster sowie Aktionen um den Kopf. Diese Elemente werden **in dieser einzelnen Episode bzw. Aufnahme** nicht dargestellt. Das ist keine Aussage, dass entsprechende Funktionen in der gesamten App fehlen. |
| Bedienelemente und Format | Navigation, Dialog, Aktionskacheln und Statuskarten bleiben im Host sichtbar vor den Effekten. | Bilder allein beweisen keine Tap-Zustellung. Die 884 × 760-Hostkomposition hat ein anderes Format als die breiten Referenzbilder; Größe, Rahmung und Freiflächen lassen sich deshalb nicht pixelgleich vergleichen. |
| Sprache und tatsächliche Arbeit | Die Oberfläche zeigt den Startdialog und synthetische Gedächtnismetadaten des Exports. | Die PNGs enthalten kein Audio und zeigen in dieser Serie keine laufende Gmail-/PDF-/Kalenderbearbeitung. Gesprochenes Arbeiten, Sprachsynchronität, echte Fremd-App-Steuerung und echte Dokumentinhalte sind damit nicht belegt. |

## Aussagegrenzen

- **220 ms ist der Abtastabstand des Exports, kein gemessener Render- oder Display-FPS-Wert.** Zwischen den Samples können Einzelbildfehler oder Ruckler liegen; die Bildserie misst keine Flüssigkeit.
- Keine Aussage zu Fold7-/Android17-Geräteverhalten, GPU-/Filament-Pixeln, Bildrate, Latenz, Temperatur oder Energieverbrauch.
- Die Kontaktübersicht ermöglicht Motiv- und Reihenfolgevergleich. Sie allein belegt weder Referenzschnittzeiten noch Referenzkamerapfade oder deren technische Umsetzung.
- Keine 1:1-, Pixel-, Asset- oder GPU-Parität zur gesamten Referenzmontage. Der belegte Produktfortschritt ist der sichtbare große Gedächtnisübergang mit anschließender Rückkehr.

Die private Referenz und Hostbilder wurden nicht in diesen Berichtsordner oder ins Repository kopiert. Dieser Bericht enthält ausschließlich technische Beobachtungen und Hashbelege. Candidate blieb sauber; keine Source-, Git-, Gradle- oder Teständerung.
