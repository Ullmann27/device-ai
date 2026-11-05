# SUPPORT5 — Referenzbindung vor visueller Abnahme (05.11.2026)

Status: ISOLATED_QA_TOOL / OWN_NODE_PASS / IMAGE_IDENTITY_UNRESOLVED / ANDROID_NOT_RUN.

## Quelle und Zuständigkeit

- Ausgangspunkt: gemeinsamer #527-Head 19039e5b822310cd5ab1ce5cc5cb854822a1bcd3; C1 #519 bleibt Integrator.
- Der laufende gemeinsame #544-Head de207b34a437e45c0cebf0295a9e0e171fc999f5 und Neon-#546 cc4075f2e6f9831ddbea0bba5fb518a1c9c3a373 wurden gelesen. Alle produktiven UI-/Activity-/Shaderdateien bleiben unberührt.
- Ebenso gelesen: #535 4bbf2863dc3c7debe74ac81f3d16521926c8ae0a, #539 766a1953fe1b72a890448e38178448d50140fa40, #549 f8fac7e5cf32b775f3422fc9497a1bf91b2799be.
- Verbindliche Konzepte: DESIGN-AVATAR-DE-HANDOFF-20261105.md (L2 offen) und CONTINUUM-UNIVERSAL-NEON-DESIGN-20261105.md. Bestehendes scripts/continuum-design-diff.py bleibt unverändert.

## Konkrete gefundene Verwechslung

Die privat zugängliche Library-Datei namens 59509.png ist 1386 × 1536 Pixel und hat tatsächliche JPEG-Magic-Bytes (FF D8 FF E0), obwohl die Endung .png lautet. Die im #535-Handoff so bezeichnete Querformatvorlage 59509 ist dagegen ausdrücklich 1536 × 1024 Pixel. Die in der Library ebenfalls vorhandene 59592.png ist ein 755 × 1536 JPEG-kodierter Vergleichsmontage-Screenshot, keine rohe Zielreferenz. **Eine Übereinstimmung der Dateinamen ist keine Bildidentität.** Die zugänglichen Dateien werden weder ins öffentliche Repository hochgeladen noch als die behauptete Owner-Originalvorlage umetikettiert. Die später angekündigte neue Vorlage lag zum Zeitpunkt dieses Checks nicht vor.

Die neue reine QA-Vorprüfung scripts/reference-contract.mjs verlangt deshalb pro tatsächlichem freigegebenem PNG eine explizite, vertrauenswürdige v1-Bindung aus SHA256 und exakten Pixelabmessungen. Es gibt keine implizite Auswahl per Dateiname und keine automatische Konvertierung. JPEG-Bytes unter .png werden bewusst zurückgewiesen; ein autorisierter Konvertierungs-/Bindeschritt wäre separat zu prüfen. Der SHA schützt vor still ausgetauschten Bildern, die Dimensionen vor Layoutvergleichen mit falscher Auflösung.

## Referenz-/Abnahmematrix

| Quelle | Tatsächlich zugänglich | Format/Größe | Verwendungsgrenze |
| --- | --- | --- | --- |
| Repository-Handoff #535 / 59509 | Text, keine nachgewiesene identische Bilddatei | Erwartet: 1536×1024 | Querformat-Soll, Vergleich blockiert bis korrekter Bild-Pin |
| Private Library-Datei 59509.png | Ja, Bild inspiziert | JPEG-Bytes, 1386×1536 | Nicht als 1536×1024-Querformatvorlage verwenden |
| Private Library-Datei 59592.png | Ja, Bild inspiziert | JPEG-Bytes, 755×1536 | Montage aus vorher/nachher/Soll, nicht Rohreferenz |
| #535 Hochformat-Soll | Nur Handoff-Text für 866×2076 | Nicht als Byte-Pin gelesen | Kompakt/Fold-zu noch offen |
| #544/#546 Produkt-Screenshots auf Fold7 | Keine Geräteaufnahmen in diesem Lauf | NOT_RUN | Keine 48dp-/Robolectric-/GPU-/Visem-Abnahme aus Bildnamen |

## Eigene Ausführung

- Exakte zwei neuen Node-Dateien mit Node 22.16.0 in isoliertem temporären Arbeitsordner tatsächlich ausgeführt: node --check; node --test --test-reporter=tap reference-contract.test.mjs; Exit 0, 9 Tests PASS, 0 FAIL/SKIP. Roh-TAP in docs/evidence/support5-reference-20261105/node-tests.tap.
- Git-Blob SHA des getesteten Produktwerkzeugs: 3971464f97d07ca65ca91b6843c58058d9535c40. Testblob: a951ba6036d00b809c54625dc59908dfa034746a. Beide vom GitHub-Commit unabhängig nachlesen.
- Tatsächliche private Eingangsdatei 59509.png wurde lokal mit Pillow inspiziert: 1386×1536 und JPEG-Magic. Die neue **PNG**-Vorprüfung verweigert diese Datei mit INVALID_PNG_HEADER. Keine privaten Bildbytes oder Dateihashes ins Repo kopiert.
- Der alte visuelle Vergleich ist SHA-gebunden, sobald ein Baseline-Report existiert, validiert aber beim ERSTEN Lauf keine freigegebene Referenzidentität. Dieses Werkzeug wird vor dem ersten Vergleich explizit vorgeschaltet, nicht als Ersatz für den bestehenden ROI-/Screenshotvergleich.

## Folgen und Rücknahme

Vor Referenz-Score und Übernahme von L2/L3: Besitzerfreigabe und tatsächliche PNG-Identität der Sollvorlage klären, SHA/Dims aus der echten Vorlage in lokalem Manifest pinnen; danach mit bestehendem Design-Diff echte Vorher/Nachher **derselben Quelle** ausführen, App-Captures vom tatsächlichen Android-Renderer getrennt von Java2D/Host festhalten. Fold offen/geschlossen, Schrift 1/1.5/2, Scrollen, zehn Kacheln, 48dp-Hitboxes, Kompakt/Kopf/Kugel, Dialog-/Avatar-Überdeckung und Gesten bleiben offene Produktgates.

Rücknahme: die beiden neuen Node-Dateien und diesen rein dokumentarischen Handoff auslassen. Kein produktiver UI-/Kotlin-/GLB-/Orb-/Activity-/APK-/Release-/Offline-/Signatur-/Versions-/Modell-/Besitzerdatenpfad geändert. Kein Main-Merge, keine Actions, keine Installation. Host-Node-Ergebnis ist kein neuer gemeinsamer Android-PASS.
