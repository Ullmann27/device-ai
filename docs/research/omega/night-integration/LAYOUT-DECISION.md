# Night integration: layout reconciliation

The app source from PR #410 is the reference for ordinary font settings and the already verified portrait clipping fix. PR #456 contributes persistent bot profiles and an adaptive cockpit with larger touch targets. Its automatic merge would otherwise replace the complete reference composition.

## Implementation decision

- Preserve the reviewed phone, wide and portrait composition for font scales through 1.3.
- Select the adaptive cockpit for larger fonts and windows below 320 dp, with a minimum supported adaptive width of 240 dp.
- Keep all destinations, real dashboard state and the one shared hologram; retain the portrait renderer geometry only for the reference portrait composition.
- Keep separate behavior coverage for the reference layout and adaptive layout. Render both through the real Android Canvas tests, including long values and large fonts.
- Preserve original avatar assets, source hashes and the native-model crash barrier. This is host UI evidence, never a physical Fold7 result.

## Verification plan

Run the original reference geometry tests against the automatic merge first, then reconcile the layout and run reference plus adaptive policies. Run the native graphics layout/ink/click suites with final source, then the full Android and Node gates on an immutable integration commit. Record exact outcomes and skipped or blocked capabilities.
