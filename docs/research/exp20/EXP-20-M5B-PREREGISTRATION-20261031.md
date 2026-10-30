# EXP-20 M5b · Semantischer Basisstrom mit disjunkten Paraphrasen-Familien · Vorregistrierung

**Stand:** 31.10.2026.

**Reihenfolge, in Git nachprüfbar:**
1. Dieser Commit enthält Vorregistrierung, Generator mit **nur TRAIN-Familien**, Normalisierer und Trainingsskript.
2. **Erst danach** werden die VAL-Familien geschrieben (`M5bValFamilies.kt`) und die Daten erzeugt.
3. Danach CRE1-Training, nur auf TRAIN.
4. Danach einmalige VAL-Auswertung.

SEALED schreibt ein anderer Autor; er wird hier weder erzeugt noch geöffnet.

## Anlass
M5-A3: Kein vorhandener Encoder besteht die Synonym-Vorbedingung (VAL-S ≤ 15/64; Zufall bei Gleichstand 16/64). DEV und VAL teilten dieselben Umschreibungen, deshalb war Training dort unzulässig.

## Daten (`M5bData`)
- **Aufbau:** 2×2×2-Faktoriell (Person × Tag × Ort), gleiche Tätigkeit, 8 Kandidaten je Gruppe.
- **Balance:** Ziel-Faktor und Listenposition sind getrennt exakt balanciert, je 8×.
- **Umfang:** je Split und Typ (L, S) 64 Gruppen.
- **Trennung:** Namen zwischen TRAIN und VAL disjunkt; neue Orte, Tage (Mo–Fr) und Tätigkeiten gegenüber M5.
- **TRAIN-Familien:**
  - Tag: „am N-ten Arbeitstag der Woche“ und „einen Tag vor dem X“. Letzteres enthält einen **irreführenden** Wochentag.
  - Ort: je 2 Funktionsbeschreibungen.
- **VAL-Familien** (nach diesem Commit geschrieben, keine Formulierung mit TRAIN geteilt):
  - **near:** gleicher Konstruktionstyp mit anderer Oberfläche.
  - **far:** neuer Konstruktionstyp.
  - S-Gruppen wechseln deterministisch über beide Klassen; berichtet wird getrennt nach near und far.

## Arme
| Arm | Beschreibung |
|---|---|
| JACCARD, HYBRID_LEX | lexikalische Referenzen |
| CRE1, HYBRID_CRE1 | unveränderter Parity-Checkpoint `2290194d…0db6` |
| **A: CRE1_TRAINED, HYBRID_CRE1_TRAINED** | Parity-Checkpoint plus Training **nur auf M5b-TRAIN** mit `scripts/exp20-m5b-train-cre1.mjs`, feste Hyperparameter: epochs 20, batch 8, lr 0,01, tau 0,1, size 8, hard 7, seed 20261031. Harte Negative = die 7 faktoriellen Ablenker. Checkpoint-SHA wird festgehalten. |
| **B: NORM_JACCARD, NORM_HYBRID** | `M5bNormalizer`, **in diesem Commit eingefroren**. Tagesregeln nur für die beiden TRAIN-Konstruktionstypen; Orts-Glossar automatisch aus TRAIN-Wörtern, die genau einem Ort gehören. Unbekanntes bleibt unverändert. Danach lexikalisch. |
| QUERY_BLIND_MEDOID | mit Parity- **und** trainiertem CRE1 |
| TIE_CHANCE | Erwartungswert, wenn unter den lexikalisch maximalen Kandidaten zufällig gewählt wird |

## Gates und Vorbedingung (unverändert aus M5)
- **Datengültigkeit:** Die abfrageblinde Medoid-Kontrolle muss auf VAL ≤ 17,5 % strikte Top-1 liegen, sowohl mit Parity- als auch mit trainiertem CRE1. Sonst ist der Datensatz ungültig.
- **Vorbedingung je Arm auf VAL:** L ≥ 80 % **und** S ≥ 90 % (Near-Miss-Verwechslung S ≤ 10 %). Streng: Gleichstand zählt als Fehler.
- **Statistik (VAL, Holm über 4):**
  - Vergleiche: bester A-Arm gegen HYBRID_CRE1; bester B-Arm gegen HYBRID_LEX; A gegen B; bester Arm gegen QUERY_BLIND_MEDOID.
  - Exakter McNemar, Bootstrap mit 10 000 Ziehungen, MDE +5 pp.
  - **Zusätzlich Pflicht:** Ein S-Wert gilt nur dann als semantisch, wenn er **über TIE_CHANCE** liegt.

## Entscheidungsregel
1. Kein Arm besteht auf VAL-S → **M5 NICHT ERREICHT**. Die Fehler werden nach near und far aufgeschlüsselt.
2. Besteht A oder B nur auf near, aber nicht auf far → kein Nachweis für **unbekannte** Formulierungen; M5 NICHT ERREICHT.
3. Besteht ein Arm auf VAL → Freeze (Checkpoint, Normalisierer, Hashes). Danach **einmalige** Prüfung auf dem **fremden** SEALED (`EXP-20-M5-FOREIGN-SEALED-HOLDOUT-SPEC`). Erst dann ist M5 eventuell ERREICHT.
4. Nicht erlaubt: kein Tuning auf VAL, kein nachträglicher Glossar-Eintrag, keine Produktivänderung.

## Prüfsummen bei Registrierung
- `app/src/test/java/at/heinz/deviceai/research/exp20/m5b/M5bData.kt` SHA-256 `e807247fc219c3b8ef530bb5db1d7f181243e40ff48d0cd642dae4403f63d285`, Blob `200da21aadb6a0831818e5002184b7eddd08e48a`
- `app/src/test/java/at/heinz/deviceai/research/exp20/m5b/M5bNormalizer.kt` SHA-256 `741352cc5b40e2cf8a16d3d94c6b8c31c34fc51e917b2209652e21b0c536318a`, Blob `e87058e8a261f61d983c6a51f33b75e0cfaa2f7d`
- `app/src/test/java/at/heinz/deviceai/research/exp20/m5b/M5bTrainCheck.kt` SHA-256 `b2b5cd6e5c4a60b3c496b3d5fc66033ad9065569a70360af757d720b83265941`, Blob `d8676510ec8b72aeb418e89b40de24c2f2e82625`
- `scripts/exp20-m5b-train-cre1.mjs` SHA-256 `ec2b3daabc831a72447cb557dc9af0110d437a4e3948552ac077ff3be60acbd3`, Blob `98e1338a6e2acc1240f0f6282539827ef29a462e`
- TRAIN-TSV (aus `M5bTrainCheck`): SHA-256 `8466dbccfec589fc1c54a0702c6a9e2f9241fbb78100fb415b5e4147a48f52e4`; Normalisierer auf TRAIN-S 64/64 kanonisch (nur Konstruktionsprüfung).
