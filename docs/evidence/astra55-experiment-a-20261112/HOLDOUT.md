# Separater Review-Holdout: Teilwirkung vor Materialfehler

Nach dem abgeschlossenen Primärvergleich unabhängig vom Integrator hinzugefügt. Ausgangskandidat `af4ce72efea2ac09f5c2ef231d70a98948ec2715`. Primärfolgen,50%-Gate und ursprüngliche14 Szenarien bleiben unverändert.

Hypothese des Gegenbefunds: `setParameter` kann den Materialzustand ändern und danach werfen. Ein dabei bestätigter alter Cachewert lässt eine anschließende Rückkehr zu diesem alten Wert fälschlich aus. Der Host-Spy protokolliert deshalb die Wirkung und wirft erst danach.

Drei Fälle an der vollständigen Produktionsklasse:

1. `frameAvailable`: fehlgeschlagene Ausblendung bei ausstehenden GPU-Referenzen, anschließend Freigabe und neuer Frame. Der Materialzustand muss wieder sichtbar werden. Bei vorhandenem #803-Protokoll wird dieselbe Ausblendung über dessen echtes `frameFailed` ausgelöst.
2. `pointerAvailable`: fehlgeschlagene Pointer-Aktivierung, anschließend vorhandenes STOPPED-Ereignis. Der Materialzustand muss den Pointer entfernen.
3. `contentScale`: fehlgeschlagener Resize-Schreibaufruf, anschließend Rückkehr zur ursprünglichen Monitorgeometrie. Beide Skalierungswerte müssen wiederhergestellt werden.

Erwarteter Reparaturpfad: den jeweiligen Cache vor jedem tatsächlichen Schreibversuch unbekannt setzen, erst nach erfolgreicher Rückkehr bestätigen. Host-Spies, keine Behauptung eines auf Filament/Gerät beobachteten nativen Fehlers. Ausführen mit demselben Runner und zusätzlichem `--holdout`; RED und GREEN getrennt erhalten.

## Ergebnis

- Referenz5f87:3/3 PASS.
- Ausgangskandidat af4ce72:3/3 FAIL; genau die drei erwarteten Stale-Cache-Meldungen.
- Reparatur3e2bc6d30e: pro Arm3/3 PASS, insgesamt9/9; Zustands-/Import-/Freigabefolgen gleich.
- Ursprüngliche14 Szenarien auf denselben finalen Quellen erneut je3 Arme:42/42 PASS.180→2 bleibt unverändert.
- Vier zusätzliche Invalidierungszuweisungen, keine Änderung von Import, Lease oder Capture.
