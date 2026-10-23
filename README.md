# NFC1 checkpoints (Neural Frontier Lab, 22.10.2026)

Data branch only - never merge. 198 content-addressed checkpoints (162 from batches 1-2, 36 byte models from NF-6b-lite) (file name = sha256 of the content) written by the
NFC1 runs whose logs are committed on `opus/continuum-neural-frontier-lab-20261022` under
`docs/evidence/nf-20261022/`. `MANIFEST.tsv` lists hash, size and the producing log line of every file.
Code that reads them: `app/src/test/java/at/heinz/deviceai/learning/nf/` on that branch (commits e6d33527, 4220c410; NF-6b-lite: 8f175ab1).
Research checkpoints only; none of them replaces or modifies a productive model weight (SWB1, CDX1, LM1 release).
