# NFC1 – Rohprotokolle (gesichert 23.10.2026)

Alle Läufe endeten mit `exit=0`. Die Jar-Quellen sind Commits dieses Zweigs: `e6d33527` (Batch 1) und `4220c410` (Batch 2).
Die Auswertung nach den vorregistrierten Regeln steht in `docs/CONTINUUM-NEURAL-FRONTIER-LAB-20261022.md`, Abschnitt 10.

| Datei | Versuch | Argumente | Jar (sha256, Anfang) | Ende (UTC) |
|---|---|---|---|---|
| `corpus.txt` | Datensplits NFC1, Einfrierhashes | `corpus` | – | vor allen Modellläufen |
| `nf2-lm-s.txt` | NF-2 Sprachmodell, Größe S: G, T, D, L, Dna, Dnb | Batch 1, siehe Kopfzeile | `2f340bbe2ae12882` | 20:39:34 |
| `nf4-relu-l1-0.txt` | NF-4 ReLU-FFN ohne L1 (Gr-S) | `lm Gr-S seeds=1,2,3 epochs=6 l1=0` | `3a60683a6a225e6e` | 20:43:47 |
| `nf4-relu-l1-1e-3.txt` | NF-4 ReLU-FFN mit L1 = 1e-3 | `lm Gr-S seeds=1,2,3 epochs=6 l1=0.001` | `3a60683a6a225e6e` | 20:48:03 |
| `nf3-bpe-gm.txt` | NF-3 BPE-Referenz G-M | `lm G-M seeds=1,2,3 epochs=6` | `3a60683a6a225e6e` | 21:01:05 |
| `nf3-blt.txt` | NF-3 Byte-Latent: Entropie- gegen feste Patches | `blt seeds=1,2,3 epochs=6 meanPatch=3` | `3a60683a6a225e6e` | 21:24:49 |
| `nf1-stream.txt` | NF-1 Online-Lernen auf CODE, Vergessen auf DOC | `stream` mit den sechs Epoche-6-Checkpoints von G-S/D-S | `3a60683a6a225e6e` | 21:36:43 |
| `nf2-recall.txt` | NF-2 assoziatives Abrufen (MQAR) | `recall D-S G-S T-S L-S G-M G-X seeds=1,2,3 steps=3000` | `3a60683a6a225e6e` | 22:35:44 |
| `run-chain2.sh.txt` | Startskript von Batch 2 (Reihenfolge wie vorregistriert) | – | – | – |
| `nf4-speed-fair.txt` | NF-4/NF-2 faire Zeitmessung: 18 Epoche-6-Checkpoints, ein Prozess, ein Thread, 5 verschränkte Runden | `speed` @ `d340c60e` | `3bf1b6c5e41b278a` | 00:48:23 (23.10.) |
| `evaluate.py`, `evaluation.txt` | Auswertung aller Läufe nach den vorregistrierten Regeln (Abschnitt 10 des NF-Dokuments) | `python3 evaluate.py` | – | – |
| `nf5-study-run.txt`, `nf5-results.json` | NF-5/NF-6a: 14 Aufgaben + 3 Gegenproben, 5 Bedingungen, echte Coding-Jobs | `scripts/nf5-ranker-study.mjs` @ `3bde2844` | – | 20:53:01 |
| `nf5-gold-rank-posthoc.txt` | **Nachträglich, nicht vorregistriert:** Rang der gemeinten Funktion je Ranker | `scripts/nf5-gold-rank-posthoc.mjs` | – | – |
| `nf5b-study-run.txt`, `nf5b-results.json` | NF-5b: gleiche 17 Aufgaben, `lexical` gegen `probe` (Abschnitt 8d) | `scripts/nf5-ranker-study.mjs <out> lexical probe` @ `1bfb5d00` | – | 01:53:14 (23.10.) |
| `nf5b-ambiguity-posthoc.txt` | **Nachträglich, nicht vorregistriert:** Welche Funktionen erfüllen alle Beispiele je Aufgabe | `scripts/nf5b-ambiguity-posthoc.mjs` | – | – |

- Zeitmessungen in diesen Protokollen (`SPEED …`) liefen unter paralleler Last und sind **nicht fair vergleichbar**.
  Der faire Vergleich ist der Modus `speed` (ein Prozess, ein Thread, verschachtelt in 5 Runden).
- Die 162 Checkpoints (34 MB) liegen auf dem Datenzweig `opus/nf-checkpoints-20261022` mit `MANIFEST.tsv`.
- sha256: `nf5-results.json` d8c4f90855e539a23daaff17bcabea3585181f3bc79fc29fb7efaf1b76214da2,
  `nf5-study-run.txt` 2ee083b5a67054c6dd37bbd03f2ea711fb3cb95bda0d9701c7fc9b1e404dc4ed,
  `nf5b-results.json` fcd22c10811e1e25261689aa5763c88c38d9b8c99136e02581ffc967fbcd3a17,
  `nf5b-study-run.txt` 977da37b6c629dd0210de4884016b66babc64b8948b605ecb2e3b8b8959469bd.
