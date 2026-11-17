# Eingabe nur im dargestellten Ausschnitt

Produkt-/Testcommit: `b22e641bbe2e39eaec791b3498e70bd6bdbf899b`.
Integration: Root, bestehende gemeinsame Technikprüfung; kein neuer Ausführungspfad.
Abhängigkeit: Render-Submission `ab42bf6e7d03de1880c4b6bf37e558bdb8d910ca`.

## Belegter Fehler

Display 2000 × 1000, Ziel-App rechts `[1000,0,2000,1000]`, eigene App links
`[0,0,1000,1000]`, schwebende Tastatur `[1200,300,1800,600]`.
Die echte `ProjectionCropGeometry` wählt `[1000,600,2000,1000]`, also den
normierten Ziel-App-Ausschnitt `[0,.6,1,1]`. Android darf den oberen Knoten
`[1300,100,1500,200]` als sichtbar melden. Er gehört aber nicht zum Monitorbild.

Der neue Native-Test gegen unveränderte Produktion erreichte tatsächlich einen
`performAction`-Aufruf im Robolectric-Shadow: erwartet 0, beobachtet 1.
11 Tests liefen, genau dieser Test scheiterte.

## Begrenzte Korrektur

`AndroidLiveComputer.port` liest Sequenz und abschließenden Renderbeleg über
`monitorSubmission.submittedFrame`. Der Tracker prüft die Aktualität des
tatsächlich eingereichten Bildes einschließlich seines eigenen `sourceEpoch`.
Dieser Epoch wird nicht mit dem Accessibility-Freigabe-Epoch verglichen.

`AndroidLiveNodePort` prüft nach Knoten-Refresh, Fenster-, Revisions- und
Freigabeprüfungen unmittelbar vor `performAction` zusätzlich:

- Frischer Renderbeleg zur gebundenen Aufgabe, Capture-Session, App und Fenster.
- Bekanntes Zielrechteck und bekannter Bildausschnitt.
- Vollständiges Zielrechteck im eingereichten Bildausschnitt.

Bei fehlendem Beleg oder unpassendem Ausschnitt bleibt `inputAttempted` falsch.
Native Ports ohne Capture-Session behalten ihren bisherigen Vertrag. Bestehende
Sende-, Entwurfs-, Empfänger- und Freigabegrenzen bleiben bestehen.

## Gezielt verifiziert

Frische scoped Kotlin-Kompilation, anschließend Robolectric SDK28:
`AndroidLiveNodePortTest` 14 Tests und `AndroidLiveScrollTest` 7 Tests,
**21/21 PASS**. Die vier neuen Fälle prüfen ein vollständig außerhalb liegendes
Ziel, ein nur teilweise sichtbares Ziel trotz sichtbarem Zentrum, das weiterhin
zulässige untere Feld mit SET_TEXT und anschließendem exaktem Digest sowie den
Verlust des Renderbelegs während des finalen Refresh.

Die vorhandenen Native-Tests prüfen weiterhin Nicht-Capture-Eingaben,
Freigabeentzug, Fensterüberdeckung, Textnachbedingungen und echte gebundene
Scrollereignisse innerhalb des Testsystems. Es wurden keine HardwareBuffer
nachgebaut. Renderbelege sind Metadaten-Fixtures an der Portgrenze.

Logs, Quellenhashes und echter Altcode-Repro:
[`computer-rendered-viewport-20261112`](../evidence/computer-rendered-viewport-20261112/receipt.json).
`red-repro.patch` auf Baseline `7fec32a6f5b679597ff535b9b0566037a9b9f01f`
ergänzt nur den reproduzierenden Test und dessen Fenster-Fixture.

Der kombinierte Root-Gate kompiliert zusätzlich die vollständige
`AndroidLiveComputer.port`-Anbindung mit dem Submission-Commit. Der isolierte
21-Test-Nachweis behauptet diesen Integrationslauf nicht. Android-Gerät,
MediaProjection auf Hardware und tatsächliche GPU-Ausgabe bleiben getrennte
Abnahmegrenzen.
