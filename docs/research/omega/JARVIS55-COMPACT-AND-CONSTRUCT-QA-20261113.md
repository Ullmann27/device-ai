# Compact workstation and construction-phase QA

Patch source: `c83faa2639f1521948b46a10c2438bc4281c93da`.

## Actual failure receipts

Full Android run [43344572298](https://github.com/Ullmann27/device-ai/actions/runs/43344572298) failed six relevant UI cases. The artifact-only diagnostic [43350986680](https://github.com/Ullmann27/device-ai/actions/runs/43350986680), job 131641725448, verified and printed the XML failure receipts for source `41306257ce03c1e22e998877bf769ce42b3a0486`, tree `3a16a64f4d831ba51d54f24cbb952d850e8d433e`:

- Both 840dp compact/restoration tests fail at font scale 1.5: compact content is not shorter.
- All four visual-evidence profiles fail with opening progress 0.0 at 80 ms.

These are prior failure receipts, not passing results for this patch.

## Compact layout

The default Home uses `CinematicWorkstationLayout`; compact previously left that layout and entered the legacy shell. At large type this reintroduced the old feature tiles and information rows, producing a page longer than the current full workstation.

Compact now remains a variant of the existing workstation layout: the original orb occupies a smaller live slot, badges remain readable, and the current header, conversation, input, eight-route dock and status remain. More retains the extra destinations. Only the full workstation monitor planes are omitted while compact is selected. No new render path or duplicate avatar is added.

Default full geometry follows the same calculations, insertion order and bounds as before. Selecting the full appearance reconstructs those original bounds. The existing Activity compact/restore tests are retained unchanged, including exact restoration, one live view, minimum touch sizes and actual navigation.

The added pure layout regression covers widths 240–1536dp, short/landscape/portrait viewports and font scales 1, 1.5 and 2: shorter compact height, actual orb slot, positive contained bounds, no overlapping slots or controls, eight native routes, More, unchanged control sizes and deterministic full restoration.

## Construction phases

The existing `HollywoodFuiConstruct.flight` intentionally holds the panel at its source until the 140 ms light-anchor phase ends. The 80 ms test was checking motion inside this hold.

The visual test now separately requires:

1. At 80 ms: active anchor, zero flight, real source geometry, no released data or brackets.
2. Mid-bracket phase: strictly intermediate flight and bracket visibility, exact source/destination scale, zero data, existing avatar gaze and protected-region checks.
3. Mid-data phase: partially released data and advancing flight.
4. Settled/closing phases: all existing content, geometry, scrolling, lifecycle, actual status and closing assertions remain.

Phase samples use the existing timing contract. The production animation timeline is unchanged; no effect is disabled and no assertion is replaced with a permissive endpoint check. Anchor and data-release captures are added to the host evidence.

## Verification status

Source and actual diagnostic logs reviewed. Changed Kotlin/unit/UI tests: NOT_RUN in this connector-only worker. Integration must run `CinematicWorkstationLayoutTest`, `JarvisShellLayoutTest`, `HollywoodFuiConstructTest`, `ContinuumReferenceCockpitUiTest` and `ContinuumVisualEvidenceUiTest` plus the full QA gates. Host captures remain Robolectric evidence, not Filament/device/performance proof.
