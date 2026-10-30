# M7 · Was kosten die Shadow-Pfade pro Gesprächszug? (Host-JVM, nicht Fold7)

Programm: `app/src/test/java/at/heinz/deviceai/research/m7/M7ShadowCostBench.kt` (kein Teil der Unit-Suite).
Messaufbau: 40-Zug-Dialog (`inspectRequest` + `record` + `render`) mit `Argon4ShadowMode.OFF` gegen `SHADOW`. 60 verschränkte Läufe nach 30 Aufwärmläufen. Dazu die neue Gemma-Aussagenprüfung (`LocalModelClaimAudit`, 1168 Zeichen Antwort gegen 1704 Zeichen Beleg), 400 Messungen nach 2000 Aufwärmläufen. Drei unabhängige Prozessläufe.

| Lauf | Kontext OFF µs/Zug (Median / p95) | Kontext SHADOW µs/Zug | Shadow-Mehrkosten (Median) | Aussagenprüfung µs (Median / p95) |
|---|---|---|---|---|
| 1 | 15,2 / 24,5 | 75,0 / 110,7 | +59,7 | 715 / 1041 |
| 2 | 14,5 / 18,8 | 78,7 / 99,7 | +64,2 | 631 / 876 |
| 3 | 14,8 / 19,5 | 79,9 / 127,5 | +65,1 | 691 / 1000 |

Host: Linux amd64, JVM 21. **Kein Fold7-Wert.** ART auf dem Fold7 kann um ein Mehrfaches abweichen.

## Lesart
- Der F1-Shadow-Kern (#242) kostet auf dem Host rund 60 µs pro Zug, etwa das Fünffache des bisherigen Kontextpfads, absolut aber unter 0,1 ms.
- Die Aussagenprüfung läuft **einmal pro belegter Gemma-Antwort**, nicht pro Token. Sie kostet unter 1 ms, gegenüber mehreren Sekunden Generierung auf dem Gerät.
- Beide Pfade machen **keinen** zusätzlichen Modellaufruf und speichern keinen Antworttext.
- Entscheidung: Beide bleiben SHADOW. Für eine Aktivierung fehlen Fold7-Werte (ART-Latenz, Akku) **und** der Qualitätsnachweis (Aussagenprüfung gegen Besitzer-Feedback).
