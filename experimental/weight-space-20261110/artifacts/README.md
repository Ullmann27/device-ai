# Persisted resume checkpoints

`resume-checkpoints.zip` contains the actual binary results of the registered
132,160-parameter continuity experiment and the 2,632-parameter control replay.
No additional model execution was used to assemble this archive.

Archive size: **4,535,845 bytes**.

Archive SHA-256:
`33ee25d9f5b8135c2279e27076499c46addf9a0d1c639ea6a9887e632875320f`.

`resume-checkpoints.manifest.json` binds every archive member's exact size and
SHA-256. The internal `manifest.json` records original execution filenames,
registration commits and duplicate aliases. Continuous/resumed final LM1C
and LM1T bytes occur once each and map to both original paths.

The 13 members are:

- Five distinct original-132k outputs: initial, intermediate and final LM1C;
  intermediate and final complete LM1T.
- All five tiny-control LM1T states, including the negative controls.
- Original 512-token tokenizer and the tiny fixture's zero-merge tokenizer.
- Internal manifest.

The tiny tokenizer was reconstructed deterministically as the eight-byte
zero-merge LMBP representation; its hash was checked against all five tiny
LM1T headers. The authentic original baseline is not duplicated: its exact
repository path, commit, size and SHA-256 remain in the internal manifest.
The separately encoded initial LM1C is included because it is a distinct
experiment output with new metadata and unchanged initial weight values.

ZIP uses DEFLATED level 9, lexicographic member order, timestamp
1980-01-01 00:00:00 and Unix mode 100644. Every source checkpoint matched
its previously published execution receipt. ZIP CRC validation and complete
uncompressed byte comparisons passed. Original files remain intact.

These are research artifacts, including deliberate negative controls.
No model is approved for application use, and no language-quality improvement
is established by successful checkpoint recovery.
