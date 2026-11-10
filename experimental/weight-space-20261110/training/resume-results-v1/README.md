# Original 132k weight-state continuity

The single registered run completed with exit code 0 in 0.729305 seconds.
Registration was published before execution at
`af8f746ff717a67d6bd52f8848cfda94a413139c`.
No retry and no change to the registered settings occurred.

- Authentic original model: 132,160 parameters, original SHA-256
  `7a0af1e99554f9e73fbe4212d0aa4bf8ec97745efca56998d9b42bac4fad44fd`.
- Continuous arm: two actual optimizer updates.
- Recovery arm: one update, complete LM1T save/load, one further update.
- Four actual updates total. Tokenizer supplied BOS 510 / EOS 511.
- All 132,160 first and 132,160 second optimizer moments were nonzero
  after the first update and at both final states.
- Complete final LM1T files match byte for byte by SHA-256:
  `ef51b01fa6227c45808bb4df63227eff33b908c4db4789df6dc78f44c05f5923`.
- Both final LM1C files have SHA-256
  `a0c54008308f7c330ec22a432388004b907e89c9e6424ac3aa9816d76bf0aa88`.
- Original checkpoint, tokenizer and registered experiment sources remain
  byte-identical. Raw command, hashes and artifact manifest are in `receipt.json`.

The two logged losses concern different synthetic sequences. Their numerical
difference is not a measured improvement. No language-quality benchmark,
new final-test evaluation, 16M training, Android test or model promotion
occurred. The 768-MiB Java heap and 120-second deadline are configured bounds;
actual peak RSS was not measured.

The raw stdout contains a JVM performance-data-file locking warning. The
process nevertheless completed normally; stderr is empty. All raw output is
retained. Independent LM1C tensor comparison is performed by the separate
evaluation path and must be cited separately from this native result.

Binary research outputs remain under the execution path recorded in the
receipt. They must not replace the active application model. Preserve them
with their recorded hashes when archiving the full study.
