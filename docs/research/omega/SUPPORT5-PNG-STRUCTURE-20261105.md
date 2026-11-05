# Support 5 – PNG-Struktur nach Eigentümerbindung (05.11.2026)

Status: SOURCE_PATCHED / LOCAL_NODE_PASS / C1_REVIEW_PENDING / ANDROID_NOT_RUN.

Basis: #557 `8313c61c14d06a6ee48b6a6132bd428fc6ce5b4c` auf #527, C1 #519 bleibt Integrator. Neuere gemeinsame APK-Kandidaten #543/#544 und Neon #546 sind eigenständig und bleiben unberührt. Das in der aktuellen Unterhaltung gezeigte Bild ist eine **Textanweisung**, nicht das angekündigte Cockpit-Original.

## Reproduzierter Fehler
Der bisherige Referenzbinder prüft PNG-Magic, IHDR-Typ, Abmessungen und den Eigentümer-SHA256. Er akzeptiert aber **strukturell beschädigte PNG-Bytes**, sofern sie selbst mit korrektem Hash im Vertrag hinterlegt wurden. Sechs unveränderte neue adversariale Tests auf Originalquelle: **9 PASS / 6 FAIL (RED)**. Fälle: defekter IEND-CRC, fehlendes IEND, nachgestellte Fremdbytes, defekter IDAT-CRC, fehlender IDAT, übergroße Chunklänge. Keine Bildidentität aus Dateinamen abgeleitet.

## Kleinste Reparatur
Nur der bestehende optionale Offline-Binder und seine bestehende Testdatei werden geändert. Nach der bisherigen Format-/Größen-/SHA-Prüfung wird die Chunkstruktur bounded geprüft: CRC32 über Chunktyp und Chunkdaten, IHDR-Metadaten, IDAT-Pflicht und zusammenhängende IDATs, eindeutiges finales IEND ohne nachfolgende Bytes. Bestehende Fehler für falsche Hashes/Größen bleiben unverändert. Keine neue Paket-, App-, Renderer-, Shader-, Frameclock-, Avatar- oder Orb-Abhängigkeit.

**Grenze:** Das ist keine vollständige PNG-ZLIB-Decodierung und keine optische Ähnlichkeitsprüfung. Selbst ein korrekt kodiertes Bild ist erst nach expliziter Besitzerfreigabe die richtige Referenz. Private Dateien `59509.png`/`59592.png` wurden nicht veröffentlicht.

## Tatsächliche Ausführung
Originaldateien über GitHub-Connector gelesen, im isolierten Node-v22.16.0-Verzeichnis **Git-Blob-identisch** rekonstruiert. Unveränderte neue Tests vor Produktreparatur ausgeführt: 9/15 PASS, 6 FAIL (Exit 1). Reparierter Stand: **15/15 PASS**, Exit 0, Node-Syntaxprüfungen beider Dateien Exit 0. Details und SHA256 der Roh-TAP-Ausgaben in `docs/evidence/support5-reference-20261105/png-structure-red-green.json`.

Weder Android/Robolectric/Filament/Fold7 noch eine APK wurden durch diese Änderung gebaut oder geprüft. Keine Übernahme nach C1/main. Rücknahme: die beiden Dateihunks der PNG-Strukturprüfung zurücknehmen; bereits vorhandener Referenz-SHA/Geometrievertrag bleibt erhalten.

## Nächster freier Abnahmepunkt
Nach Identitätsfreigabe des tatsächlichen 1536×1024- und 866×2076-Bildes: Bild-/SHA-Pins aus der bestätigten privaten Quelle lokal erstellen, reale App-Screenshots des aktuellen **einheitlichen** APK-Baums, Compact/Fold/Font/Scroll/Touch 48dp sowie Kopf/Kugel und echte Hitboxes unabhängig vergleichen. #544 besitzt vollständige kombinierte Host-/Androidbelege nur für seinen eigenen geprüften SHA; heutiger neuer Design-/Referenzslice wird dadurch nicht automatisch freigegeben.
