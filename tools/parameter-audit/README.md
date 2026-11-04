# Parameter-Audit (GAMMA, 04.11.2026) – ZWISCHENSTAND, abgebrochen durch Sitzungsende

`lm1_param_count.py` spiegelt `LmConfig.layout()` aus `app/src/main/java/at/heinz/deviceai/learning/lm/LmModel.kt`
analytisch (reine Arithmetik). Lauf vom 04.11.2026 (Host, Python 3):

| Profil | Arch | dim/L/mlp | Parameter | f32-Bytes | ≤ 64 MiB Checkpoint-Cap |
|---|---|---|---|---|---|
| LM1-GM-v512 Original 64/2 | MinGRU | 64/2/256 | 132.160 | 528.640 | ja |
| Ladder 512/2 (core-6562304) | MinGRU | 512/2/2048 | 6.562.304 | 26.249.216 | ja |
| Ladder 512/3 (core-9712128) | MinGRU | 512/3/2048 | 9.712.128 | 38.848.512 | ja |
| Ladder 512/5 „16M“ | MinGRU | 512/5/2048 | **16.011.776** | 64.047.104 | ja (knapp unter `LmRuntime.MAX_CHECKPOINT_BYTES` = 67.108.864) |
| Ladder 512/6 | MinGRU | 512/6/2048 | 19.161.600 | 76.646.400 | **nein** |

**Verifiziert:** nur die *definierte* Parameterzahl je Profil (Architekturarithmetik).
**Nicht verifiziert (offen):** ob für 512/5 ein gespeicherter Checkpoint existiert, ob Gewichte initialisiert oder trainiert sind
(Verteilungsstatistik), Checkpoint-Hashes, die Gate-Reparatur aus PR #497 (fail-closed vor Trainingsfortsetzung) und die
CI-/Scheduler-Pfade auf `main`. Die Zahl „~3.778 Parameter“ aus der Projektangabe wurde in keinem LM1-Profil wiedergefunden
(kleinstes gelistetes Profil: LM1-TS-v512 Transformer 32/2 = 49.632). Nächster Schritt: Checkpoint-Dateien unter
`docs/evidence/lm1-*/` und auf `origin/research/continuum-weight-space-audit-20261104` (PR #492/#497) mit `--json`-Ausgabe
abgleichen (Bytes = 4·Parameter + Header/Namen/SHA-Trailer).
