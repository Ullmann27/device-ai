# ARGON F2 — offline research, never a runtime capacity switch

Editable Python sources and published reports were taken from PR240 snapshot
`67888e3323f5f30964202e901034cdf80dddb456` against common base PR238 `a3fdc179`.
No bytecode files were imported. Existing F1 holdout seals/results are preserved,
not retuned. Proposed patch files are reference material, not applied changes.

Run the bounded analytical/simulation regressions with the existing Python:

```sh
python3 -m unittest discover -s scripts/argon4 -p 'test_*.py'
```

Integration checkpoint: 10 tests PASS, exit 0 on the Linux host. These test
analytical formulas and deterministic cache simulation; neither those results
nor the published host read benchmark measure Fold7 RAM, GPU/NPU, thermal,
energy or native-model performance. No runtime code imports these scripts or
uses their projections to enable a backend/cache/large-model configuration.
`stream_bench.py` requires an explicit operator-selected existing public file;
it was not run during this integration and must not be pointed at private data.

Hardware acceptance and a fixed candidate/control comparison on the Fold7 are
required before any F2 proposal can change the app's resource policy. Existing
CPU/Gemma path and model identity stay unchanged.
