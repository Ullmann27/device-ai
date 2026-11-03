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

After the QA lane releases a CPU slot:

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

## Pending

- Execute unchanged baseline RED and exact candidate GREEN when QA releases CPU.
- Review the narrow imported asset/runtime source and candidate motion changes.
- Capture/review same-backend design regression and time-sequenced head renders.
- Evaluate real work-effect event binding separately from cosmetic motion.
- Voice, guided first-run tests and actual Fold7 acceptance remain separate gates.

No raw reference media, private owner data, credentials or generated likeness is
committed. Main, signing, installed app and productive publishing remain untouched.
