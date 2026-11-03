# Independent reference review handoff

Research only; no production edits. Draft PR434 targets the current MΩ Chief.
The initial acceptance contract and design/source metadata were frozen in
`ae17e3f508dd8890a4c2bcc35664d7d108ac02d8`; twelve independent native mapper/reducer
checks were frozen in `8c43f017575faec126c141e1f0377976d74c60d9`.

## Current finding

The existing real 3D head is a viable integration starting point, but it has not
met the reference appearance requirement. Exact-GLB Blender anchor renders show
coherent anatomy/depth. They also show a dim opaque face, coarse angular wires
and weaker eye/lip/contour definition than the primary reference. The mapper's
neutral head and eyes stay fixed; its only idle action is blinking. These are
source/render observations, not device or voice results.

The origin correctly discloses the 24-AA anchor as a stress pose combining
independent controls. It is not faithful runtime mapper replay. Do not report
its lower-teeth protrusion as a confirmed reachable Android defect. Existing
reachable-family checks are distinct from all-pose collision acceptance.

`independent-observations.json` binds the inspected foreign source/artifact
hashes. The primary ten-second video additionally received a twenty-sample,
2Hz visual review. All five videos received time-spanning visual sampling.
Audio listening and complete audiovisual playback remain NOT_RUN.

## Design authority clarification

The latest owner instruction preserves our agreed design. The video's layouts,
palette, phone mockup and fabricated dashboard values are not replacement
specifications. Existing theme/font/layout hashes form the comparison baseline.
Effects and natural head behavior are adapted within that design.

D02's prohibition of a forced renderer means an existing owner's saved choice
must survive and the orb/2D fallback must work. It does not add an approval gate
against a fresh-install/onboarding default already authorized by the owner.
Any such default must be explicit, tested and separate from preference migration.

## Independent native tests

Clean unchanged baseline checkout:
`/workspace/jarvis-reference-3d-baseline` at
`7caef9b624beebdb78c8c081a17f4978461ae496`.

Executed in a QA-authorized single-CPU gap:

```bash
bash tools/jarvis-reference-review/run-mapper-challenge.sh \
  /workspace/jarvis-reference-3d-baseline \
  7caef9b624beebdb78c8c081a17f4978461ae496 \
  /workspace/jarvis-reference-review-results/baseline
```

The runner compiles actual production AvatarContract, AvatarGlbContract and
AvatarMorphMapper with actual local Kotlin/JUnit dependencies. No stubs, model,
camera, voice generation or Android/Filament execution is used. It constrains
compiler CPU/heap and records compile output, JUnit output and exit status.
The two natural-idle requirements and ten regression/boundary requirements are
frozen before feature implementation. Degree bounds are conservative engineering
limits, not a claim that exact video motion was recovered.

Candidate acceptance requires those unchanged tests against an exact clean source,
source comparison and actual visual evidence. A passing mapper suite cannot
promote appearance, work-event effects, audio, onboarding or Fold7 criteria.

Actual unchanged baseline: **12 JUnit cases, 10 PASS, 2 FAIL, 0 SKIP**, exit 1.
Both failures are the preregistered missing idle head/gaze behavior, not fixture
or compiler failures. Raw logs and command/source hashes are retained in
`mapper-baseline-receipt.json`. No compiler ran alongside the benchmark model.

## Additional independent memory proof review

Chief requested a bounded independent review of an observed real-loop persistence
failure. The source repair is separate from this UI lane: technical proof MACs
were computed before JSON persistence removed optional undefined fields.

Sixteen cases were frozen at `462e43fd` before candidate results were supplied.
Unchanged helper at `19474261` (byte-identical to `d8a927dd`) produced **7 PASS,
9 FAIL**. Exact candidate `6baec1bcd6585d2d04688a7d15651c830b4886d7` produced
**16/16 PASS**, followed by **51/51** actual canonical technical-recall/durability
checks; zero skips. Cases include nested undefined, sparse arrays, fresh Node
restart, original-object preservation, post-serialization binding substitution,
copied brands, circular/BigInt refusal, and tampering with an outer hash rewrite.

**ACCEPT_DURABLE_PROOF_JSON_SNAPSHOT_FIX**, limited to that exact source. Original
live producer/verifier brands are checked before serialization; the persisted
snapshot is revalidated before MAC. Existing unauthenticated records are never
re-signed. Tests use explicit disposable private-brand fixtures, not real model
observations. Raw commands, seams and hashes: `memory-roundtrip-receipt.json`.

## Pending

- Execute unchanged native tests against the exact new motion candidate when QA releases CPU.
- Review the narrow imported asset/runtime source and candidate motion changes.
- Capture/review same-backend design regression and time-sequenced head renders.
- Evaluate real work-effect event binding separately from cosmetic motion.
- Voice, guided first-run tests and actual Fold7 acceptance remain separate gates.

## Observed gaze and additional video references

`REFERENCE-ADDENDUM.md` freezes eight real-event/effect requirements for the two
new 20-second videos, independently hashed and visually sampled. It also freezes
thirteen additional gaze/lifetime checks at `000c48b7`. After source inspection,
one separately labeled future-timestamp recovery challenge was added at `12586912`.

Combined actual baseline `7caef9b6`: **26 run, 12 PASS, 14 FAIL, 0 SKIP**.
Exact candidate `1649a04346631ca4389159c03e0ab6c7333f4e1c`: **26 run, 25 PASS,
1 FAIL, 0 SKIP**. All original 25 frozen requirements pass. The additional
challenge reproduces a lockout: future observation time 2000 is discarded at
render time 1000, but retaining its ordering timestamp blocks a valid observation
at 1001. A fix and unchanged rerun are required before contract acceptance.
Raw output, exact commands and source hashes are in
`gaze-motion-candidate-1649-receipt.json`.

All 34 protected theme/font/layout/2D-painter file hashes remain unchanged.
The separately authorized HologramView import preserves stored owner preferences,
orb/2D fallback and shared frame/pose state. The narrow runtime/asset import adds
no brain scheduler, TTS implementation, camera permission or Activity change.
Source comparison does not replace a native-render/visual/lifecycle check.

Follow-up exact source `26555aa6c59fdd4dd2e190defcdf49b983305555` passes the
**same unchanged 26 cases, 26 PASS, 0 FAIL, 0 SKIP**, exit 0. The fix restores the
last actually fresh timestamp after discarding a future sample, while preserving
the separate clear watermark. **ACCEPT_MOTION_GAZE_CONTRACT** for that exact source;
this does not accept head appearance, real camera integration, Filament rendering,
first-run guidance, voice identity or Fold7 execution. Raw output and exact source
hashes: `gaze-motion-fixed-2655-receipt.json`.

## Independent material/appearance comparison

Six actual-GLB A/B frames were inspected: front, slight yaw/pitch and full profile,
with identical camera/lighting/exposure/seed, Blender 4.3.2 Cycles CPU, 384×448,
eight samples and one CPU thread. Candidate material asset `15573963f9c3f862...`
is brighter and easier to read, but **H01 remains FAIL / NOT_ACCEPTED**. The face
is still opaque, the wire paths coarse/angular and the pale bright irises create
a fixed stare. Neutral pose/camera-angle frames do not demonstrate natural motion.

The reviewer independently verified all six frame hashes, byte-identical geometry
BIN chunks and unchanged glTF semantics outside four material objects. The
production asset remains `f39a89fc...`. `material-15573963-review.json` records
exact hashes, render parameters, observations and limitations. Eight-sample grain
limits fine appearance assessment; higher samples alone cannot fix the identified
material/eye differences. These are host asset renders, not Filament/device pixels.

## Second visual iteration and actual reachable-mouth finding

`VISUAL-ITERATION-CONSTRAINTS.md` records actionable eye, contour-flow, volume,
silhouette and mouth constraints. Private reviewer contact sheets retain original
color/aspect ratio and disclose manual alignment. The first chin crop was corrected
explicitly in receipt version 2; its earlier width estimate must not guide geometry.

An actual Kotlin exporter at source `26555aa6` supplies 260 reachable mapper poses,
including a complete 224-frame idle sequence, exact full-blink anchors, task/look
states and utterance-bound AA/PP/O/I/U followed by stop. The 26 accepted contracts
remain unchanged. These are synthetic inputs, not actual audio or camera evidence.

Surface `fe76e85d` retains original geometry but looks like a cylindrical cage.
Topology/eye variant `bee53236` follows anatomy more closely but saturates eye and
mouth regions. Its neutral and speech-stopped renders are pixel-identical, a useful
actual reset check. Density-adjusted `03caa24f` restores readable facial volume and
relaxed closed lids; it still needs contour-light/style and full-motion review.

Actual reachable O replay showed teeth outside the displayed opening, including
the unchanged original asset. The separately frozen canonical glTF intersection
check `d0c2ca85` reproduces **exit 1** on the original. Frontmost tooth-edge/face
crossings for neutral/AA/PP/O/I/U are **0/16/152/65/36/28**. Hidden internal contacts
and ordinary teeth visible within the aperture are explicitly separated.

Isolated Jaw+40 mm candidate `6b199c59` preserves all BIN bytes, materials and
neutral rigid-child positions, but remains **REJECTED / exit 1** with counts
**0/36/0/48/0/20**. It fixes sampled PP/I crossings and leaves AA/O/U unresolved.
Counts are not a severity scale. Full raw geometry witnesses, exact commands and
source hashes are in `mouth-geometry-red-first-candidate-receipt.json`.

The original Face skin binds only Root/Neck/Head. Jaw rotates inner mouth/teeth;
it does not add another outer-skin deformation. Original builder recipes confirm
that AA and JawOpen separately contain the same mouth-open basis. Component
ablation and any justified semantic mapping repair remain separate from the
neutral-preserving rig hypothesis; no material darkening may conceal geometry.

## Explicit composition V2 RED

Root approved the evidence-based contract version change; original V1 and all
CCC1 benchmark tests remain immutable. `MOUTH-COMPOSITION-V2-FREEZE.md` and new
independent tests were frozen at `cd975890` before implementation. V2 replaces the
obsolete exact double-mapping expectation with two stronger anatomy requirements.

Actual unchanged `26555aa6` result: **27 run, 25 PASS, 2 FAIL, 0 SKIP**, exit 1.
The only failures are the two new requirements: shared opening is added twice,
and inner-jaw movement does not follow cue-produced outer opening. Raw output and
hashes are retained in `gaze-v2-red-2655-receipt.json`. A new candidate must pass
V2, the original max-pose geometry checks, the frozen additional 112 speech poses
plus neutral, and actual render review before promotion. No claim of V1 PASS on
a future changed mapper is allowed without its actual result.

Five component-ablation frames independently show the doubled outer opening.
The ablation deliberately modifies pose components and is labeled accordingly.
An independently repeated canonical-to-Blender jaw-matrix comparison differs by
only `1.8453e-7` per element, consistent with Float32. This rejects a large replay
angle discrepancy as the cause of the observed artifact. It is not native-render
or device certification. Receipt: `aa-ablation-transform-review.json`.

No raw reference media, private owner data, credentials or generated likeness is
committed. Main, signing, installed app and productive publishing remain untouched.

## Independent composition V2 GREEN and factorial geometry

Exact source `9a23e5d6a9dc96d15525347c99ff682d632d7277` passes the unchanged
frozen V2 suite: **27 PASS, 0 FAIL, 0 SKIP**. The unchanged original V1 suite was
also rerun: **25 PASS, 1 FAIL, 0 SKIP**. Its sole failure is the explicitly
superseded exact independent-opening arithmetic. V1 has not been silently edited,
skipped or claimed to pass on this new mapping.

The actual source exporter was rerun, producing 260 poses and the separately
frozen 113-pose family. The factorial geometry result is decisive:

| Actual mapper | Actual rig | Neutral/AA/PP/O/I/U frontmost crossings | Exit |
| --- | --- | --- | --- |
| `26555aa6` | Original `f39a89fc` | 0/16/152/65/36/28 | 1 |
| `26555aa6` | Neutral-preserving `6b199c59` | 0/36/0/48/0/20 | 1 |
| `9a23e5d6` | Original `f39a89fc` | 0/159/152/48/102/40 | 1 |
| `9a23e5d6` | Neutral-preserving `6b199c59` | 0/0/0/0/0/0 | 0 |

Neither isolated change passes. The combination also executes **all 113 frozen
holdout poses with zero frontmost crossings and exit 0**. Hidden internal contacts
remain recorded; this is a sampled front-view result, not universal mesh collision
freedom. All raw JSON is retained losslessly in gzip files with both compressed
and uncompressed hashes, alongside stdout and exit codes. The receipt is
`mouth-composition-v2-green-9a23-receipt.json`.

All five actual combined render anchors were independently viewed and their
hashes and exact pose membership verified. AA/O now show teeth inside the visible
mouth aperture instead of the previous side rows. Both lids close in the full-blink
anchor. Neutral and stopped RGB pixels are exactly equal. Canonical-to-Blender
jaw matrices agree within `1e-6` per element. Details are in
`mouth-combined-render-9a23-6b199-review.json`.

Decision: **accept the sampled mouth-composition/rig correction as a combination**.
The source candidate still packages the original rig, so this result is not an
acceptance of that packaged asset. Before promotion, the final styled asset must
pass the same geometry cases and actual render comparison. The full 224-frame
idle sequence, native Filament appearance, real speech synchronization and device
acceptance remain separate and unproven. No reference video or private frames are
included in this evidence commit.

The exact packaged follow-up `62122c55077644de5bb1993347b7749c7e53610b` now
contains that same `6b199c59` rig and updates the production pin and provenance.
An independent final-source rerun passes **27/27 V2, zero skips**. The compiled
production preflight accepts the real packaged bytes and its hash pin; all 21
targets are present. Mapper, reducer and pose sources are byte-identical to the
tested `9a23e5d6` composition, and the validator's sole change is the pinned hash.

**ACCEPT_PACKAGED_MOUTH_COMPOSITION_AND_NEUTRAL_PRESERVING_RIG_FIX** applies only
to this anatomy correction. Identical-input geometry is referenced, not relabeled
as another measurement. The original material is still not the accepted reference
look. `packaged-rig-62122-acceptance.json` records commands, actual final-source
output, hashes and the separate visual/device limitations.

## Final visual checkpoint and newly disclosed legacy gate

Experimental styled asset `3805ba43261784993d3f4c65bb24df2a0ee306b89962167d81763c12c9d81deb`
also passes all 113 unchanged actual-pose frontmost-intersection checks, exit 0.
It remains a research asset: HDR material portability and final appearance are
not accepted. Its two 112-frame render batches were independently checked against
every actual exported pose and image hash. All 224 frames were viewed in seven
complete contact sheets. The fixed camera, contiguous 24 Hz timing, two blink
events and absence of idle mouth motion are confirmed. Maximum per-frame changes
are 0.0314 degrees head yaw and 0.0882 degrees eye yaw; the batch join is smaller.

The two-sample, 192x224 CPU renders are very noisy. They do not establish final
humanlike naturalness, fine eyelid/material quality, real-time playback, native
Filament or device performance. H01 remains NOT_ACCEPTED. Full author clip and
manifests are retained under `docs/evidence/momega1-avatar-look/3805ba43/` on
author source `60be37f98eb5b8d741c92ab2305d3f34cc7c57c8`; private reference media
remains outside Git. Independent receipt: `idle224-3805-independent-review.json`.

Renderer `0996b42e` separately receives bounded source-review acceptance: existing
View postprocessing and low-budget bloom, no new engine/entity/callback and the
existing cleanup path. This is not native pixel/GPU evidence and does not approve
the experimental HDR material. Receipt: `renderer-bloom-0996-source-review.json`.

An additional old EPSILON validator rejects the corrected rig because its Jaw
position also defines the morph-localization boundary. This actual V1 FAIL is
preserved and must not be reported as a full regression PASS. The explicit
supplemental contract `JAW-HINGE-V2-FREEZE.md` separates the actual control axis
from the pinned anatomical mouth-region landmark. No original V1 boundary is
widened. Its diagnostic coordinate view is never presented as the actual asset.

The first supplemental checker confused rigid node origins with baked tooth
vertices; its 15/16 result is retained, and the implementation now checks actual
transformed vertices without changing the requirement. The next full batch
completed cases 1–13, then hit its 60-second process budget during case 14. A
following batch was interrupted when all four CPUs were reserved for final QA.
Both timeout and resource interruption remain recorded. Uncompleted cases are
not counted as PASS. Final supplemental outcome is recorded separately after the
remaining unchanged cases finish.

The remaining cases are now complete: **16 distinct cases PASS**, with exact
per-case/process accounting in `hinge-v2-final-receipt.json`. The real original
control passes unchanged V1; the real corrected rig still fails actual V1 with
CLI exit 1; the explicit actual-hinge/fixed-region V2 passes. This is a disclosed
semantic resolution, not a relabeled V1 success. `HINGE-V2-RESULT.md` explains the
actual geometry, preserved protections, reviewer-checker correction, raw timeouts
and reproducible commands. No native/appearance/device gate is implied.
