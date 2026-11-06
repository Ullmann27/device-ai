# Tatsächliche Prüfgeschichte

Die [Zählung](iteration-counts.json) stammt aus archiviertem `AUDIT_TESTS`
und frischem JUnit-XML. Jeder Unterordner enthält den tatsächlichen Befehl,
Basiscommit, Quellpatch, Prüfsummen und Exitcode. Läufe überschneiden sich;
ihre Zahlen dürfen nicht addiert werden. Ein Laufname mit `green` beschreibt
die Absicht beim Start, niemals ein vom Ergebnis unabhängiges Gütesiegel.

## Fehler und ihre Auflösung

- `work-window-red`: fehlende Fensteranbindung, 0/1 bestanden.
- `work-window-avatar-red`: fehlende gebundene Avatar-/Fenster-APIs, 0/14.
- `stop-early-return-red`: Kompilierung fehlgeschlagen, kein Test ausgeführt.
  Der Wiederholungslauf reproduziert danach den echten STOP-Fehler: 0/1.
  Die frühen Rückgaben dürfen Sprache und Mundzustand nicht offen lassen.
- `work-window-avatar-green-attempt1`: Kompilierung fehlgeschlagen,
  kein Test ausgeführt; alte XML-Dateien ausdrücklich ausgeschlossen.
- `work-window-avatar-green-attempt2`: 93/99, sechs Fixture-/Framepumpfehler.
- `phone-approval-red`: 12/16; ausdrückliche Zustimmung und Telefongeometrie.
- `work-window-avatar-green-attempt3`: 110/127. Sieben TTS-Fixture-Zeitfehler,
  sechs Draw-Hierarchiefehler und vier Pixel-/Skalierungsfehler. Der Quellenstand
  entspricht dem lokalen Checkpoint `cc2848bb`; dessen Inhalt ist aus der
  öffentlichen Basis `2859f18d` und diesem archivierten Quellpatch ableitbar.
- `intent-window-lifecycle-red`: 0/6 auf diesem lokalen Checkpoint plus
  Testpatch; wiederholtes Listening, reduzierte Bewegung, Entfernen im Draw
  und gebrochene Zielpixel bleiben als negative Gegenproben erhalten.
- `integrated-lifecycle-green-attempt1`: 196/196. Basis `7159aba8` plus
  archivierter Quellenpatch, anschließend identisch als `8c851a09` veröffentlicht.
  Freigabe außerhalb Draw, eine Intent-Verarbeitung und native Pixelrundung.
- `phone-density-check`: 0/2. Bei 420 dpi fehlte tatsächlich ein nativer
  Pixel in der Mindesthöhe; der zweite Fehler betraf die initiale Anheftung
  des Testfensters. `phone-density-green`: 23/23; 2 dp Rundungsreserve plus
  korrekt abgewartete Anheftung. Produktstand anschließend `9468eb87`.
- `final-cockpit-validation`: **274/275**, Quelle `9468eb87`, ein echter
  Hintergrundscrollfehler nach erfolgreichem Öffnen/Schließen im Cover-Profil.
  Die bis dahin aufgenommenen Bilder bleiben gültig. Android-Lint wurde in
  diesem fehlgeschlagenen Lauf nicht erreicht. Dieser Lauf wird nicht nachträglich
  in 275/275 umgedeutet.
- `real-scroll-red` (1/2) und `real-scroll-green` (2/3): beide scheitern
  bereits an der Blick-Vorbedingung. 65 aufwendige Host-Draws können die reale
  2500-ms-Lebensdauer des Blick-Intents überschreiten. Diese Läufe beweisen
  deshalb noch nichts über den Scroll-Callback.
- `real-scroll-red-corrected`: **1/2**, Quelle `9468eb87` plus Testpatch.
  Blick wird unmittelbar im Öffnen geprüft. Tatsächliches `scrollTo` verschiebt
  den Hintergrund von 0 auf 32; die Fläche bleibt fälschlich sichtbar.
  Kein künstlich ausgelöstes ViewTreeObserver-Ereignis.

## Finaler Anschluss

`7c3523cde9dae302a52d69818867a9abe8cf0fb7` bindet das Fenster direkt an den
Scroll-Callback seines tatsächlichen Cockpit-Vorfahren. Der Callback verwendet
die übergebenen neuen Koordinaten. Der eigene Inhalts-ScrollView bleibt davon
getrennt; Schließen entfernt die Bindung. Keine weitere Animationsuhr.

Der abschließende Lauf `final-scroll-cockpit-validation` führt die betroffenen
Fenster-, Shell-, TTS-, Handsfree-, Freigabe- und vier Bildtests sowie Lint aus.
Beobachtet: **39 PASS, 0 FAIL, 0 SKIP**, Exit 0; zehn XML-Suites.
NewApi-Lint: 0 gemeldete Issues unter der unveränderten Projektkonfiguration.
Receipt und Quellenmanifest bestätigen den unveränderten App-Quellstand. Der frühere breite Lauf bleibt als solcher mit einem Fehler bestehen.

Hostprüfung bedeutet weder Filament-GPU-Ausgabe noch physische Fold7-Abnahme.
Gmail-, Dokument- und Coding-Integrationen wurden durch diese Tests nicht ausgeführt.
