# EXP-20 M5-A3/A4 · Encoder-Vorbedingung und Armvergleich auf DEV/VAL · Vorregistrierung (vor dem Lauf)

**Stand:** 30.10.2026.

- **Voraussetzung:** Das echte CRE1-Blind-Gate hat bestanden (VAL strikt 15/128, Commit `e698162e`).
- **Daten:** M5-DEV/VAL `7d70d20f…7dd2`.
- **Checkpoint:** CRE1-Parity `2290194d…0db6`, unverändert und **nicht** auf M5 trainiert.
- **Nicht geöffnet:** SEALED. Das hier ist **interne Entwicklungsevidenz**, keine unabhängige Bestätigung.

## Arme (alle parameterfrei, kein Tuning, Implementierung wiederverwendet aus `P4Associative.scores` / `P3Probe.jaccard`)
- `JACCARD`, `HYBRID_LEX` (`HybridMemoryRetriever` ohne semantischen Scorer), `CRE1` (`score(q, m)`), `HYBRID_CRE1` (`semanticScorer = CRE1.score`).
- `QUERY_BLIND_MEDOID`: Σᵢ⟨mᵢ, mⱼ⟩ auf der m-Seite, ohne Abfrage.
- `HOPFIELD2_B4`: nur als Artefaktkontrolle.

## Metrik
- **Streng Top-1** je Gruppe, Gleichstand = Fehler.
- Getrennt nach Split (DEV/VAL) und Typ (L/S): je 64 Gruppen.
- In M5 sind alle 7 Ablenker faktorielle Near Misses (gleiche Tätigkeit, ≥ 1 gemeinsamer Slot). Die Near-Miss-Verwechslung je Gruppe ist deshalb definiert als „ein Ablenker ≥ TRUE“ = 1 − strikt Top-1.

## Registrierte Vorbedingung (aus P3 / Auftrag)
Ein Arm **besteht** auf VAL nur, wenn gleichzeitig gilt:
- Top-1 ≥ 80 % bei L und bei S;
- Near-Miss-Verwechslung ≤ 10 % bei S, also Top-1 S ≥ 90 %.

## Statistik (VAL, beschreibend)
- Exakter McNemar mit Holm über drei Vergleiche: HYBRID_CRE1–HYBRID_LEX, HYBRID_CRE1–JACCARD, HYBRID_CRE1–QUERY_BLIND_MEDOID.
- Bootstrap mit 10 000 Ziehungen, MDE +5 pp; Urteile nach `P2Stats.verdict`.

## Entscheidungsregel
1. **Besteht kein Arm** die Vorbedingung auf VAL-S: Der semantische Basisstrom fehlt weiterhin. WP2 (Curriculum) ist dann gerechtfertigt, **aber nur mit einem neu registrierten Datensatz mit getrennten Paraphrasen-Familien für Training und Prüfung**. Begründung: DEV und VAL teilen hier dieselben 10 Umschreibungen, ein auf DEV trainierter Encoder würde sie auf VAL nur auswendig wiedererkennen. M5 = NICHT ERREICHT; SEALED bleibt geschlossen.
2. **Besteht HYBRID_CRE1** und ist gegenüber allen drei Referenzen `SUPERIOR`: Kandidaten-Freeze. Die WP3-Bestätigung erfolgt erst auf dem fremden SEALED-Holdout. Ohne diesen gilt M5 weiter als NICHT ERREICHT.

## Prüfsumme bei Registrierung

- `M5ArmProbe.kt` SHA-256 `5c7abe5f28bc22f6557dce90d8d94bcee395512a7e77ec107bc2036fe36b66a8`, Blob `fc9a01c0d4baa047b736a8b30aca1222e2214eab`
