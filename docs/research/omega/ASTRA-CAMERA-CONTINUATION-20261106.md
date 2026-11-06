# Arbeitsfenster-Kamerafahrt – 06.11.2026

## Ausgangspunkt

Repository `Ullmann27/device-ai`, Branch
`claude/continuum-jarvis-cockpit-z7bo60`, offener Draft-PR #565.
Geprüfter Ausgangsstand: `2546cb4ac4f1cf5ef8f2e5632c57d0d68caab422`.
Der Bericht in Kommentar `6675257713` ist nachgelesen.

Status dieses Start-Checkpoints: **WIP / UNVERIFIED**.

## Kleinster Prüfgegenstand

Das tatsächliche Arbeitsfenster steuert einen begrenzten Rückzug der
orthografischen Kopfkamera; beim Schließen kehrt die Ansicht zurück.
Die vorhandene Projektion bleibt der Ausgangspunkt. Kopf und Aura müssen
dieselbe Zwischenprojektion verwenden. Die originale GLB, Kugel, Farben,
Halsaufteilung und maximalen Kopfwinkel bleiben erhalten.

Kein neuer Timer: vorhandene sichtbare Avatar-Frame-Uhr. STOP, sofortiges
Schließen, Pause, Abhängen und reduzierte Bewegung setzen die Fahrt zurück.
Die gesamte Strecke muss die gemessene Posenreichweite und Dialogkarte
berücksichtigen, nicht nur ihre Endpunkte.

## Abnahme

- Host-Activity-Test: fehlende Fahrt zuerst reproduzieren.
- Original-GLB: unabhängiges Skinning über Zwischenprojektionen und Layouts.
- Uhr/Lifecycle: Abbruch, Umkehr, Zeitsprünge, reduzierte Bewegung.
- Betroffene Android-/Cockpit-Regressionen, vorhandenes NewApi-Gate.
- Physischer Fold7: **UNAVAILABLE / NOT_RUN**, ADB-Geräteliste leer.

Keine Änderung an EXP-00…12, Modell-/Gedächtnis-/ToolGate-Verträgen.
Keine APK, Installation, Signierung, Veröffentlichung oder Main-Integration.
Sequentielle Arbeit ohne weitere Bots. Rückfall: Kamerafahrt-Delta revertieren,
Ausgangsstand und bestehende Arbeitsfenster behalten.
