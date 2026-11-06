# Arbeitsfenster-Kamerafahrt – 06.11.2026

## Ausgangspunkt

Repository `Ullmann27/device-ai`, Branch
`codex/jarvis-camera-flight-20261106`, offener Draft-PR #571 nach #565.
Gemeinsamer Zielbranch: `claude/continuum-jarvis-cockpit-z7bo60`.
Geprüfter Ausgangsstand: `2546cb4ac4f1cf5ef8f2e5632c57d0d68caab422`.
Der Bericht in Kommentar `6675257713` ist nachgelesen.

Status: **WIP / RED_EXPECTED**. Der Host-Activity-Test öffnet das echte
Arbeitsfenster und scheitert an der unveränderten Kopfprojektion: 1 Test,
1 erwarteter Assertion-Fehler, keine Skips. Die GLB-Projektionsfixture ersetzt
keine Filament-Ausführung. Beleg: `docs/evidence/astra-camera-flight-20261106/red-receipt.json`.

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

## Erster Implementierungsnachweis

Hostlauf `camera-green`: **19/19, 0 Fehler, 0 Skips**, Exit 0, Quellen während
des Laufs unverändert. Der zuvor rote Activity-Test ist grün. Enthalten sind
acht Uhr-/Abbruchtests, neun Framing-/Original-GLB-Geometrietests sowie
je ein Activity- und Aura-Lifecycle-Test. Prüfquellen und XML-Hashes stehen
in `first-green-receipt.json`. Weiter offen: vollständige betroffene Regression,
STOP/Pause/Reduktionsanbindung in der Activity, bestehendes Schweben, NewApi
und physische Fold7-/Filament-Abnahme.
