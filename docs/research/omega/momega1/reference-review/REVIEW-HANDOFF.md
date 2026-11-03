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

No raw reference media, private owner data, credentials or generated likeness is
committed. Main, signing, installed app and productive publishing remain untouched.
