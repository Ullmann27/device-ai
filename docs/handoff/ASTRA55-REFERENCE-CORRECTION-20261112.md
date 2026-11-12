# Cockpit: Korrektur nach Referenzvergleich

Status: PORTIERT / HOST-VERIFIZIERT; finale Renderfolge ausstehend.
Ziel: vorhandene Cockpit-Integration #799 über #801.
Basis: a99eac98cf, einschließlich aktueller Bildbindung, Eingabeprüfung,
Pixelbereinigung und Avatar-Kamerarückzug des Hauptteams.

Der Besitzer bewertet den bisherigen Hostrender als zu transparent und noch
nicht referenzgerecht. Ein Livebild allein löst die Kompositionsabweichung nicht.

## Verbindliche Korrektur

- Originalavatar deutlich dichter, Gesicht klar vor der Kugel.
- Originalkugel größer hinter Kopf und Schultern; äußere Ringe bleiben sichtbar.
- Monitor, seitliche Arbeitsfenster und Tisch als zusammenhängender Arbeitsplatz.
- Ruhige dunkle Raumarchitektur, ausgeprägte Glas- und Metallkanten; keine
  dominanten Gerüststützen oder durch den Bildschirm laufenden Glasflügel.
- Eine klar gekennzeichnete Vorschau mit Beispielinhalt zeigt die Bildwirkung;
  Beispielinhalt bleibt ausschließlich im Hostwerkzeug, niemals im Livefeed.

## Drei abgegrenzte Zuständigkeiten

1. Avatar/Kugel: Opazität, Kamerarahmung und responsive Komposition.
2. Architektur: vorhandener Workstation-Generator, tatsächlicher GLB, Materialpin.
3. Rendernachweis: produktive Kotlin-Geometrie, Beispielinhalt, sichtbare Ringbewegung.

Der Koordinator erhält die neueste Hauptteam-Logik und führt die Ergebnisse
zusammen. Die bewiesene Monitorbewegung wird auf diese Basis portiert.
Keine zweite Engine, kein zweiter Android-Clock, keine neue Bildquelle.
Die Originalavatar- und Kugelassets bleiben erhalten. Andere aktive Worktrees
werden nicht verändert. Freigaben für App-Aktionen bleiben im vorhandenen Pfad.

## Abnahme

Quellgebundene Fold-/Phone-Vergleichsbilder zuerst visuell mit den gelieferten
Referenzen abgleichen. Gesicht, Ringraum, Tischkontakt, Seitenfenster, lesbare
Inhaltsfläche und erreichbare native Schaltflächen prüfen. Anschließend passende
bestehende Layout-/Projektions-/UI-Tests; tatsächliche Ergebnisse hier festhalten.
Hostdarstellung und tatsächliche Android-Gerätedarstellung getrennt ausweisen.

## Gemeinsame 3D-Szene

Der lokale Hauptstand `9535b83a07` ist als nächste Integrationsbasis übernommen.
Seine eine Filament-Szene, perspektivische Kamera, echte GPU-Bildtextur und
aktualisierten Live-/Sicherheitspfade bleiben maßgeblich. Der alte separate
Stage wird nicht wiedereingeführt. Die Referenzgeometrie wird als Eingabe des
vorhandenen Unified-Generators portiert; ein früher Render von `6c951be2a1`
ist ausschließlich ein Kompositionsvergleich der vorherigen Implementierung.
Gemeinsame Welttransformationen werden aus dem Produktpfad exportiert, bevor
ein finaler Render dieses kombinierten Standes als Nachweis bezeichnet wird.

## Geprüfter Produktstand

`3a952f9d8f64e99a9a851b964b5392d8d39084fb` enthält den gesamten Main-Stand
`f16e96f183`. Dessen öffentlicher Tree ist `a45e874b4420598b7c549d5bb7176956951f02d9`.
Die konkrete Korrektur ist in dieselbe Engine/Kamera angeschlossen. Originalavatar
und bestehendes Unified-Kugelpaket sind byteidentisch; bei Deckkraft 1 bleiben
die ursprünglichen OPAQUE-/MASK-Materialien erhalten. Der abgelöste 2D-Overlay-
Vorläufer ist entfernt. Die Smartphone- und Fold-Rahmung gibt Schulterraum frei.

Scoped Produktkompilation PASS; 33 Geometrie-/Material-/Framingprüfungen und
7 native Feed-/Work-Event-Motionprüfungen PASS. Der ältere Canvas-Helpertest
ist separat, kein GPU-Beleg. Aktuelle Quelle und Grenzen stehen in
`docs/evidence/cockpit-reference-qa-20261112/README.md`.

Der gebundene Eventpfad des Hauptteams bleibt erhalten. Ergänzt ist das Beenden
der Monitorfahrt bei Verbergen/Hintergrund/Detach, die saubere Endpose ohne
Restreferenz und das Unterdrücken eines Neustarts bei gleichem Bewegungsziel.
Native Statusdetails bleiben über die kompakten Ziele und TalkBack erreichbar.

Die finale Renderfolge wartet auf die gemeinsame QA-Sperre des Hauptteams.
Die ersten Unified-Hostansichten `4b5663b8` sind gesichert und tatsächlich
visuell geprüft. Der letzte Fold-Framing-Schritt `6f069fe5` ist geometrisch
geprüft; dessen neue Sichtprobe wird vor der endgültigen Folge ausgewertet.
Gerätebestätigung bleibt NOT_RUN.
