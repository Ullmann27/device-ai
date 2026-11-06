# Arbeitsfenster-Kamerafahrt – 06.11.2026

## Ausgangspunkt

Repository `Ullmann27/device-ai`, Branch
`codex/jarvis-camera-flight-20261106`, offener Draft-PR #571 nach #565.
Gemeinsamer Zielbranch: `claude/continuum-jarvis-cockpit-z7bo60`.
Geprüfter Ausgangsstand: `2546cb4ac4f1cf5ef8f2e5632c57d0d68caab422`.
Der Bericht in Kommentar `6675257713` ist nachgelesen.

Status: **HOST_TESTS_AND_NEWAPI_PASS / DEVICE_NOT_RUN**. Kamerafahrt implementiert;
Abschlusslauf auf `c9984b3faeaa14a9a2fb06614cfc3d3159b18107`:
**120/120 bestanden, 0 Fehler, 0 Skips**, NewApi ohne Befund. App-Quellen während
des Laufs unverändert; nachfolgende Änderungen betreffen nur Belege/Übergabe.
Ausgangsnachweis: Der Host-Activity-Test
öffnete das echte Arbeitsfenster und scheiterte an der unveränderten
Kopfprojektion: 1 Test, 1 erwarteter Assertion-Fehler, keine Skips. Die
GLB-Projektionsfixture ersetzt keine Filament-Ausführung.
Beleg: `docs/evidence/astra-camera-flight-20261106/red-receipt.json`.

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
in `first-green-receipt.json`. Damals noch offen: vollständige betroffene Regression,
STOP/Pause/Reduktionsanbindung in der Activity, bestehendes Schweben, NewApi
und physische Fold7-/Filament-Abnahme.

## Erweiterte Prüfung und Testadapter

Der Stand von #565 bis `61ab95e1263c57cb3f68daef2766ef5e2c1e94a9` ist
konfliktfrei integriert; neue Header-, Touch- und TalkBack-Tests bleiben erhalten.
Original-GLB mit bestehendem Schweben und ein synthetischer Fall mit sicheren
Endpunkten, aber gefährlicher Zwischenstrecke: **5/5 grün**.

Erster vollständiger Lauf: **113/120 bestanden, 7 Fehler, 0 Skips**.
Sechs Fehler stammen vom alten reflektierten `open`-Testaufruf mit überholter
Signatur. Er nutzt jetzt den typisierten Aufruf; seine Prüfungen bleiben gleich.
Der neue Fokusverlust-Test benötigte zusätzlich den echten View-Dispatch in
der pausierten Robolectric-Fixture. Die Activity-Steuerung allein lieferte
dort den View-Callback nicht rechtzeitig. Nach diesen Fixture-Korrekturen:
**8/8 grün**, einschließlich STOP, reduzierter Bewegung, Fokus und Pause/Resume.
NewApi war durch den abgebrochenen Gesamtlauf noch nicht belegt.

Quellbindung, Fehler und Ergebnisse: `checkpoint-receipt.json` im Belegordner.

## Abschluss und nächste Übernahme

Die korrigierten Quellen sind als `c9984b3faeaa14a9a2fb06614cfc3d3159b18107`
gesichert. Vollständiger betroffener Abschlusslauf `camera-final-verified`:
**120/120 bestanden, keine Fehler/Skips, Exit 0**; `:app:lintDebug` mit dem
vorhandenen NewApi-Gate **PASS / 0 Befunde**. Laufzeit 6 min 21 s. Quellen vor
und nach dem Lauf identisch, Checkout beim Start sauber.

Belege und portabler Testbefehl:
[`README`](../../evidence/astra-camera-flight-20261106/README.md),
[`final-receipt.json`](../../evidence/astra-camera-flight-20261106/final-receipt.json),
[`final-gradle.log`](../../evidence/astra-camera-flight-20261106/final-gradle.log),
[`newapi-results.xml`](../../evidence/astra-camera-flight-20261106/newapi-results.xml).
Die früheren Fehlversuche bleiben dokumentiert. Diese Tests sind eine begrenzte
betroffene Suite, keine neue Ausführung der gesamten App-Testsammlung.

Verbindungen: angenommenes `JarvisWorkWindowHost`-Fenster →
`ContinuumHologramView` → bestehende Uhr / `AvatarCameraMotion` → gemeinsame
`AvatarCameraFraming`-Projektion für Aura und Filament. Dauer 520 ms bei
Animationsfaktor 1, höchstens 8 % Rückzug und 2,5 % seitlicher Bühnenweg;
Geometrie begrenzt bei Bedarf weiter. STOP und Lifecycle verwerfen die Fahrt.

Nächster Einstiegspunkt: **physischer Fold7 mit nativer Filament-Ansicht**;
innen/außen, Öffnen/Schließen, STOP, Pause, Falten/Drehen, reduzierte Bewegung
und große Schrift. Kein ADB-Gerät verfügbar, daher **NOT_RUN**. Keine neue
APK oder Aufnahme erstellt. PR #571 bleibt Draft zu #565; Main bleibt unberührt.

EXP-00…12, Modell, Gedächtnis und ToolGate erhalten keine neuen Verhaltenspfade.
Rückfall: ausschließlich die fünf Produktionsdatei-Deltas von #571 gegen #565
zurücknehmen; keine fremden Commits zurücksetzen. Original-GLB-SHA256:
`c8b88385c73fa01596a5734d20859440b1ecaef06cb1abea6cd7a47e62b08b9a`.
