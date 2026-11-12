# Cockpit: Korrektur nach Referenzvergleich

Status: WIP / UNVERIFIED. Ziel: vorhandene Cockpit-Integration #799 über #801.
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
