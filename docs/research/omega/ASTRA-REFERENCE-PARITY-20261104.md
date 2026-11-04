# Astra — binding reference reconstruction, 4 November 2026

## Acceptance state

**VISUAL CHANGES REQUESTED.** Heinz rejected the previous Canvas/Java2D preview. Its
passing tests establish specific source behavior, not reference fidelity, native
Filament rendering, physical Fold7 results, or release readiness. That preview is
retained as historical evidence and is not an accepted design baseline.

The master implementation order and Zusatz III authorize continued implementation
against the latest owner-provided image. This supersedes the old eight-tile ref5
geometry. The current reference is `docs/design-screenshots/jarvis-master-20261104.jpg`,
1536 × 1024, SHA-256
`e0afbdd199f619e0e8ee4ed791c8f930d34dd7769c3f0a9b154ccbfbb80749de`.
It is an unmodified copy of the owner attachment; despite the attachment's `.png`
suffix its actual encoding is JPEG. It is a comparison source, never a full-screen
production bitmap. The requested static labels are not evidence of ready services.

## Source boundary

- Starting local source: `8bb6d8b0424e5c991c866536fa5e73daa08161f6`.
- Starting published source: `e10f4785284ab08f1d5d0a77b64ab8631ed5b9cb`, Draft PR #491.
- These have the identical complete tree `8bbb63663edaa44c85b6645e4151b5c2a3d2eb46`.
- PR base remains the #483 integration branch. No main merge, release, installation,
  force-push, package/signing/update-channel or S01 changes are authorized here.
- PR #495 contains independently tested avatar-mouth/audio/dispatch changes to
  review and integrate. PR #496's GLB pivot-validator finding needs comparison with
  the documented rig correction before changing the rig. Neither report proves a
  native-renderer or device result.
- PR #499 adds memory/Activity integration on a separate branch. Its current work
  must not be silently overwritten or represented as already combined/tested here.

## Verified implementation inventory

The application uses Android Views, custom Canvas rendering through
`ContinuumPainter`/`AndroidContinuumPainter`, and an optional Filament GLB renderer
in `ContinuumHologramView`. This is not a Compose dashboard. `AgentActivity` owns
navigation, the existing dialog/audio controller and lifecycle; the UI consumes
`ContinuumDashboardState` and `CockpitStatus`, not a new invented ViewModel layer.

The old wide layout is an eight-card 4 × 2 grid based on 735 × 538 reference
coordinates. The Activity also reserves a separate outer rail, preventing the
home header/footer from using the full window. The old Canvas head is a different
face. A real GLB with face mesh, neural threads, facial morphs and joints is present;
its materials, camera and shape require independent native/asset work.

## Visual component matrix

Reference rectangles are measured/estimated in the supplied design plane; they
are normalized by 1536 and 1024, then used as adaptive constraints, not a global
nonuniform drawing transform. Renderers keep equal x/y scale for faces, text and
icons. Touch bounds are separate from bloom bounds.

| Reference region | Initial bounds x0,y0,x1,y1 | Existing component / integration | Current gap and owner |
| --- | --- | --- | --- |
| Full frame / background | 0,0,1536,1024 | JarvisShellRenderer, JarvisNeon, JarvisDesign | Outer violet frame, directional glow and energy ribbons; root |
| Top system bar | 12,6,1524,95 | JarvisCockpitGridContent, Activity clock/navigation | Whole-window header, existing-logo reconstruction, truthful privacy; root + layout |
| Navigation | 10,96,245,895 | JarvisMenu, JarvisTiles, real Android click views | Eleven compact touching rows, exact order; layout |
| Avatar stage | 250,68,800,500 | Original ContinuumHologramView, FilamentAvatarRenderer | Preserve live identity; native camera/material/rig correction; avatar owner |
| Communication card | 256,300,650,492 | CockpitStatus dialog + existing voice controls | Separate dialog bubble, live state and real actions; root |
| Continuum title / strip | 805,82,1185,180 | JarvisCockpitGridContent | Match proportion; capabilities do not imply readiness; root |
| Brain sphere / pedestal | 805,180,1220,500 | ContinuumBrainOrbRenderer / event overlay | Three independent spatial ring planes, genuine event intensity; root |
| Camera-status card | 1200,83,1520,143 | Actual camera/session state or unavailable | No simulated tracking; root |
| Module status | 1218,156,1520,405 | Dashboard state and existing model/worker/memory sources | No false Logic/Memory/Agents ready states; root |
| Ten actions | 258,504,1520,687 | JarvisTiles + existing JarvisMenu routes | Five columns × two rows; real Tasks/Web; layout + root style |
| Four information panels | 256,698,1520,889 | Existing CockpitPanels/MemoryActivity/LocalModelCard | Correct separate state sources, proportion and readable type; root |
| Bottom status | 16,906,1520,978 | JarvisCockpitPanels + collector | Whole-window span, explicit app CPU/device RAM/battery temperature; root |

## Work allocation and integration

1. Layout agent: grid/layout, home/rail/tile placement, real hitboxes and accessibility.
2. Avatar agent: GLB/Filament, material/rig validation, reviewed #495 fixes.
3. QA agent: deterministic host Android capture, source-bound build/tests, regional
   image comparisons. Owns serialized Gradle execution.
4. Root: HUD material/light layers, contents/data truthfulness, full-width Activity
   integration, brain rendering and final reviewed integration.

Each agent uses an isolated worktree from the stated source. Root publishes reviewed
checkpoint trees in the existing official repository/Draft PR. Source changes are
not accepted just because a preview can be generated.

## Validation and remaining gates

At this start checkpoint **no new implementation test is claimed**. Previous
2416-test/21-focused-UI receipts remain bound to their old source, not to future
edits. The known Robolectric Activity capture timeout involves continuously
rescheduled animation; QA must solve deterministic test setup without weakening
functional assertions. Java2D component renders and Blender diagnostic renders
must be labelled as such and cannot become physical Android/Fold7 evidence.

Required follow-up: layout/route/inset/font-size/lifecycle tests; real captured
Android regions compared against the pinned image; native avatar and audio sync;
adaptive thermal/load/power behavior; folded/unfolded physical Fold7 results;
signature compatibility on the established release host. No physical Fold7 is
connected to this workspace, no owner APK has been installed or released.

Relevant existing experiment contracts remain EXP-05 (actual model runtime),
EXP-06 (memory provenance), EXP-11 (real workers) and EXP-12 (identity/lifecycle).
This is a presentation integration, not model training or productive promotion.

Next integration step: commit the adaptive full-window ten-tile layout, integrate
the reviewed native-avatar corrections, then capture and compare those exact
sources before declaring any visual milestone complete.
