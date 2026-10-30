# EXP-20 · P4 · Hopfield- und Quantisierungs-Pilot am vorhandenen Retriever · Vorregistrierung

**Stand:** 30.10.2026, vor dem Lauf committet.

**Einordnung:** **EXPLORATIV.** Verwendet wird der bereits ausgewertete P3-Datensatz (`p3-dataset.tsv`, SHA-256 `104cd929…2939`). Gemessen wird nur, was eine **Transformation gegenüber ihrer eigenen Basis** ändert, nicht die unabhängige Qualität. Es gibt keine Produktivänderung.

## Frage
Bringt Modern-Hopfield-Dynamik oder ein kompakter, quantisierter Schlüssel einen Vorteil gegenüber der einfachen Ähnlichkeitssuche auf denselben CRE1-Vektoren? Und hilft der vorhandene `HybridMemoryRetriever` mit CRE1 als semantischem Score gegenüber seinem lexikalischen Modus?

## Arme (CRE1 = Parity-Checkpoint SHA-256 `2290194d…0db6`, q-Seite für die Abfrage, m-Seite für Erinnerungen)

| Arm | Definition |
|---|---|
| `HYBRID_LEX` | `HybridMemoryRetriever.retrieve(query, records)`, unveränderter Produktivcode, ohne semantischen Scorer |
| `HYBRID_CRE1` | derselbe Aufruf mit `semanticScorer = cre.score(q, m)` |
| `CRE1` | Basis: Skalarprodukt ⟨q, m⟩ (normierte Vektoren) |
| `HOPFIELD1` | p = softmax(β·s), β = 8: Ranking nach p. **Behauptung:** rangidentisch zu CRE1, weil streng monoton (wird als Identität geprüft) |
| `HOPFIELD2_B4`, `HOPFIELD2_B16` | ein Abrufschritt ξ = Σᵢ pᵢ mᵢ mit β = 4 bzw. 16, dann Ranking nach ⟨ξ, mⱼ⟩; feste β, kein Tuning |
| `INT8` | je Vektor auf int8 skaliert (127/max), Ganzzahl-Skalarprodukt |
| `SIGN32` | 32 Vorzeichenbits, Ähnlichkeit = 32 − Hamming |

Alle Arme sehen dieselben 8 Kandidaten je Gruppe (wahr, 3 Near Misses, 4 Unverwandte). Für den Retriever haben die Datensätze identische Metadaten: EPISODIC, USER_PROVIDED, Konfidenz 0,9, gleicher Zeitstempel. Ein vom Retriever ausgefilterter Kandidat zählt als Score −∞.

## Metriken und Regeln
- **Metriken:** streng Top-1 (Gleichstand = Fehler), Near-Miss-Verwechslung, MRR. Außerdem Latenz je Abfrage ohne Cache (Host, Blockmittel) und Bytes je gespeichertem Schlüssel.
- **Vergleiche** (exakter McNemar, Holm über 5): HOPFIELD2_B4–CRE1, HOPFIELD2_B16–CRE1, INT8–CRE1, SIGN32–CRE1, HYBRID_CRE1–HYBRID_LEX.
- **Behalten** wird eine Transformation nur bei `SUPERIOR` gegenüber CRE1. Bei `EQUIVALENT` nur dann, wenn sie messbar billiger ist (Bytes oder Latenz). Sonst `REJECTED`.
- **HOPFIELD1:** Jede Rangabweichung von CRE1 gilt als Implementierungsfehler.

## Prüfsummen bei Registrierung

- `P4Associative.kt` SHA-256 `ac91ffb104cd02fa2fe42eed8a08d60597280ab8445c0b06b1480722c211e53f`, Blob `919e18ab1c46e562a5f628cd0506654e62732aad`
- `HybridMemoryRetriever.kt` (unverändert) Blob `e4c0f56207c24b3e691404029664788dc3da2c00`
- Daten: `p3-dataset.tsv` `104cd929e2b4f3ced6a96a7a5cac29a78f0c0366dcb4d10a73b4824abf2f2939`
