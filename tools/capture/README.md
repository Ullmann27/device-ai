# Jarvis capture and reference comparison

This harness captures the **real AgentActivity view tree** with Robolectric 4.14.1,
Android API 28 and native Canvas. It explicitly selects the existing **2D head**.
It cannot accept Android Filament, Android 12+ shader/backdrop behavior, a physical
Fold7 or GPU animation. Those require a separately labelled device capture.

The fixture disables Android animation duration scale **before Activity startup**
and restores it after teardown. It also disables the shell's continuous ticker.
The normal application source is unchanged. This makes a still frame possible:
without this setup, `JarvisCameraDrift` starts a `TimeAnimator` on window attachment,
and Robolectric's paused-looper idle call repeatedly consumes frame callbacks.
The previously observed hang was at `JarvisCameraDrift.onTimeUpdate`, not evidence
that native Canvas itself was unavailable. This fixture must not replace
functional animation, lifecycle or event-order tests.

## Run a capture

Use the existing shared serial Gradle runner so only one worktree uses Gradle at a
time. Its environment owns the JDK, SDK, cache and proxy settings. This tool does
not inspect credentials, start CI, install an APK or change application source.

```sh
python3 tools/capture/capture_jarvis.py \
  --gradle-runner /absolute/path/to/run_gradle.py \
  --output-dir /absolute/path/to/new-capture-directory \
  --case expandedCanonical
```

`--matrix` runs the seven viewport cases and six font variants below, plus the
1536×1024dp technical raster probe. Tracked source must be committed, and the
output directory must be empty. Every PNG has a matching JSON containing its
actual hash, source commit, density, logical viewport, font scale and renderer
limitations. `capture-run.json` records the source tree, exit status and frames.
A timeout or test failure remains a failed run even when some frames exist.
The capture-only init script reruns the selected Test task even if Gradle would
otherwise reuse its result. It does not force recompilation of unchanged sources.

| Case | Viewport in dp | Font scale | Purpose |
| --- | --- | --- | --- |
| `compact390` | 390×844 | 1.0 | Compact cover/phone |
| `compact412` | 412×915 | 1.0 | Compact phone |
| `mediumShortLandscape` | 780×360 | 1.0 | Short landscape |
| `mediumMultiwindow` | 700×720 | 1.0 | Medium multiwindow |
| `mediumNearSquare` | 832×748 | 1.0 | Medium near-square window |
| `expandedCanonical` | 960×640 | 1.0 | Expanded 3:2 composition |
| `expandedTablet` | 1280×800 | 1.0 | Expanded tablet |
| `compactFont150`, `compactFont200` | 412×915 | 1.5, 2.0 | Large compact text |
| `mediumFont150`, `mediumFont200` | 832×748 | 1.5, 2.0 | Large medium text |
| `expandedFont150`, `expandedFont200` | 960×640 | 1.5, 2.0 | Large expanded text |
| `referenceRasterActivityCanvas` | 1536×1024 | 1.0 | Technical raster probe; different logical width |

The configuration is mdpi, so pixels equal dp. The canonical 960×640dp frame can
be compared to the supplied 1536×1024 reference only by an explicitly recorded
uniform 1.6× resampling. A 1536dp window is not interchangeable with a 960dp window.

## Compare a canonical capture

The original master is pinned by byte hash in `jarvis-master-regions.json`.
It is a JPEG even though the upload's filename ended in `.png`. Its expected
repository location is `docs/design-screenshots/jarvis-master-20261104.jpg`.

```sh
python3 tools/capture/compare_jarvis_reference.py \
  --reference docs/design-screenshots/jarvis-master-20261104.jpg \
  --capture /absolute/path/to/activity-expanded-960x640.png \
  --capture-manifest /absolute/path/to/activity-expanded-960x640.json \
  --output-dir /absolute/path/to/comparison \
  --allow-uniform-resize
```

The tool rejects unknown reference bytes, mismatching frame manifests, aspect
ratio stretching and unannounced resampling. Optional `--crop L T R B` is explicit
and recorded; no automatic crop, warp, padding or image registration occurs.

It reports raw RGB error, RMSE and PSNR for the whole raster and named regions.
**There is no aesthetic pass percentage.** These measurements do not establish
correct anatomy, usability or motion. Expected differences from honest runtime
values are declared in a separate mask, and both masked and unmasked metrics are
retained. The excluded pixel fraction is always visible. Frame borders and
materials remain compared; no fake healthy state is injected to match the image.

`absolute-difference.png` is a diagnostic heatmap, not a product screenshot.
`included-pixels.png` shows the declared mask. Source, image, reference and region
contract hashes are retained in `comparison.json`.

Verify the comparison arithmetic and alignment refusals locally:

```sh
python3 -m unittest discover -s tools/capture -p 'test_*.py'
```
