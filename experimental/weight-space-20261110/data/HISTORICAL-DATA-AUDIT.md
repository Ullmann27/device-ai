# LM1 historical data audit

Source: PR #508, commit `59891cfad6dd015f47c187cc688ea94b0026ad7d`.
The exact registered generator was reconstructed and its `--check` executed:
PASS, all three generated TSV hashes match the archived manifest.
No model process, optimizer update or checkpoint mutation occurred.

The corpus has 128 TRAIN, 24 DEV and 48 already-consumed TEST cases, with
equal counts of four labels. Full templates, normalized full prompts and
object values are disjoint. That is exact-text separation, not proof of
semantic independence. Source templates contain the historical TEST; it is
not a fresh, secret or independent confirmation set.

| Label | TRAIN median bytes | DEV median bytes | Consumed TEST median bytes |
|---|---:|---:|---:|
| 0: no action | 58.5 | 64.5 | 75.0 |
| 1: open | 50.0 | 61.0 | 70.5 |
| 2: search | 59.0 | 57.5 | 78.5 |
| 3: remember | 64.5 | 100.5 | 113.0 |

This measured length shift can confound note-class transfer. It does not
prove causation, token distance, or a runtime defect. Source SHA256 and
reproducible measurements are in the adjacent JSON files.

Next: new openly authored TRAIN/DEV note-versus-no-action contrast pairs,
matched byte lengths and balanced directive positions. No TEST file or
independent-generalization claim. Other intents and long generation require
separate retention measurements.
