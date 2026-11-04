# Jarvis menu / avatar visual evidence — 2026-11-04

## Baseline

`jarvis-menu-avatar-baseline.png` is freshly rendered from application source `578bdfd12f896cc990bf1a043bc1b248ba3adb6d` (checkout `ad2fa70b`, documentation-only difference), not an old cached screenshot.

- Viewport: 884 × 760 dp, scale 2, output 1768 × 1520 pixels.
- Driver: `BaselineJarvisPreview.kt`; metadata and image SHA-256: `baseline-provenance.json`.
- Actual production Java2D-compatible menu, shell, avatar and orb painters; unknown/default data and static idle state.
- This is a source-derived component preview, **not a running Android Activity or a Fold7 capture**. It does not exercise hit testing, Activity lifecycle, Android Skia, AGSL or Filament.
- Baseline defects are intentionally visible: duplicate navigation, owner card covering the portrait, and the orb squeezed into HEAD rather than its own ORB area.

The native Robolectric Activity capture attempt did not finish; the bounded thread dump blocked inside AndroidTestEnvironment/PackageParser/AssetManager before Activity construction. That is not proof of an Activity layout loop. Functional View tests and Java2D previews are recorded separately; neither substitutes for the native capture gate.
