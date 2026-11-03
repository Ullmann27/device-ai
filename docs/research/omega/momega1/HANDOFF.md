# MΩ-1 / DAN Ω / JARVIS integration handoff

Checkpoint: 2026-11-03, final preservation after owner-reported 5% quota. **HOST_IMPLEMENTATION_IN_PROGRESS /
LOCAL_MODEL_HOST_OBSERVED / FIRST_10_GOAL_FAILED / FOLD7_NOT_RUN**.
No FIRST_COGNITIVE_LOOP_VERIFIED, MΩ-1A, MΩ-1B or RELEASE_READY claim.
The owner confirmed there is no device channel and authorized continued host work.
The newest instruction prioritizes committing and pushing the concrete layout, face,
animation and effects work before the reported limit. No direct quota API exists.
The agreed application design remains authoritative; visual equivalence is not accepted.

## Source and authority

Research branch `codex/momega1-chief-20261103`, [draft PR410](https://github.com/Ullmann27/device-ai/pull/410).
The complete production source freeze is `645b2215022008e2149da81b48683f15077b1fb0`.
Research preservation checkpoint `1862fd5c` adds only evidence, review tooling and
full-layout captures. Subsequent narrow layout fixes and checker repairs must be
retested at their own source freeze; they do not inherit this full-suite result.

Code40 `64ec06a73f50cae9ee49035c840710d87777d50f` remains the unchanged historical
rollback source. Initial current Chief was `786436f5`; latest fetched foreign
Chief `efe622dda00e620952483dedad19257efb3284e6` has the same application/runtime
source as its independently tested `7247e99d0b93b62dfb2b5eee22486d22911cb722`.
Foreign full-suite counts do not certify this branch. The user-supplied old
`30b70a0d` was never assumed current or accepted. Recheck remote heads on resume.

The narrow foreign JVM harness repair `8ced39b7` (84 + 59 actual selected cases),
ChiefWorkerPool completion-race repair `4a398aa8` (13 cases), and release inventory
metadata repair `95b6cb1f` (6 cases) were compared before integration. The optional
PR405 3D path was subsequently imported and independently extended as described
below; other foreign UI/layout/CDX2 experiments have not been blindly imported.
No main merge, force push, owner signing, installation or Render publication.

The owner explicitly approved two separate host GGUF models. The 1.5B source,
weights and native binary pins are in MODEL-ADMISSION.md. Additional 7B approval
and exact pins are in HOST-7B-ADMISSION.md. Both downloads are complete and their
actual bytes match the pinned SHA256. This approval is not Android Gemma sharing,
Fold7 inference, cloud fallback, paid compute or an unrestricted model download.

## Actual agents and isolated workplaces

Thirteen distinct specialists have worked in successive waves, at most seven
active sessions including Chief: the original runtime/challenger, memory/reviewer,
loop, Android/reviewer, workspace/reviewer and QA roles, plus reference UI author,
reference reviewer and readiness engineer. Each has an isolated Git worktree and
assigned source paths. These are development sessions, not thirteen runtime bots
or virtual machines. The completed roles are reused for further review waves.
Two actual resource-limited Docker workplaces and real canonical worker execution
were observed; worktrees alone are never described as OS isolation. The adapter
uses the existing ChiefWorkerPool, backlog claim and AgentMailbox. Canonical
cancellation remains CHECKPOINT_BOUNDARY_ONLY; no stronger instant-stop claim.

## Integrated call paths

The existing AgentActivity.runLocal adapter, AiContextCompiler, bounded
orchestrator, ToolGate, model binding, owner-memory and LEX3 paths remain central.
ContinuumLocalAgentModel is not mislabeled as the active main-chat adapter.

- The native CLI/provider observes a fixed, verified local model/runtime pair.
  Genuine producer provenance uses original private immutable observations bound
  to task, source, request, response, proposal and execution identity. Tokens or
  self-asserted booleans alone do not admit model execution. Unknown/process-only
  pairs remain HOST_INTEGRATION and fail strict coding admission.
- Existing CCC1 executes bounded candidates in real isolation and performs a
  fresh independent RED/GREEN comparison. Original test identities, source,
  scope and patch hashes are checked. PR400 receipt contradictions, self-closing
  XML ancestry, isolated-path aliases and correlated false green-SHA claims were
  repaired. PR407 retains its two historical parser-only diagnostics (44/46);
  they are not silently relabeled PASS.
- Owner-pinned local planning now enters the existing typed draft review. Source
  `4b98da9362f91ab9930a7f4b279737a9e80f94bd`, PR442 and independent PR435: 13 real
  baseline assertion failures, then 102/102 actual host tests, zero skips,
  including 30 independent cases. Owner supplies the full target SHA and 1–3
  paths; no APK SHA or model-invented scope substitutes for target metadata.
  One real runtime adapter call is wired; injected-runtime tests do not prove
  native Android inference. Existing full prepare/RUN approval remains required.
- Typed dispatch revokes stale prepared snapshots, consumes consent before RUN,
  rejects late/duplicate callbacks and never retries an uncertain tool action.
  Earlier 45 real host UI/contract tests and independent negative cases remain.
- Technical experiences use the existing CCC1 ExperienceLog, independently
  branded verifier/model evidence and durable authenticated proof. They are
  TECHNICAL_EXPERIENCE_REVIEW_ONLY, never OWNER_CONFIRMED or trained weights.
  Current grants, revocation, expiry and scope are rechecked at retrieval.
  Optional clean-Git ancestry recall and the explicit on/off ablation control
  were independently tested (27 + 6 cases); no second fact database exists.

## Real local-model results and repairs

The exact admitted 1.5B native implementation is based on source `9e28d24b`, the
independently rebuilt identical llama-completion executable, bwrap network
isolation, fixed descriptors, bounded native resources and before/after hashes.
Actual inference, cancellation, path-swap and forgery tests were independently
observed (runtime evidence PR427/PR430). Failed native responses retain genuine
execution accounting without becoming accepted patches. Source `19474261` binds
that accounting consistently to the canonical loop.

Independent QA froze twelve synthetic unknown tasks before model exposure:
manifest SHA256 `1055d66300b0f4664978f103d1e5adb73a9619a337539ad41e898b7217a92407`.
On unchanged `19474261c15a3503a9f041244b4b4c3eaed4636f`, the actual original run
solved **3/10 primary tasks, 4/12 total**. All four independently verified repairs
used one call. There were **zero successful failed-candidate → second-candidate
corrections**, eight abstentions, 20 reserved calls and 18 admitted native
executions. Two native processes exited nonzero without admitted observations.
No timeouts or invalid JSON were recorded. Six tested candidates failed, four
non-exact patches and three repeated patches were rejected, and one genuine
no-op was rejected. Summed task time was 570.542 seconds. Native peak sampled RSS
was approximately 2.08 GB; these are host observations, not device measurements.
All failures remain in [QA PR413](https://github.com/Ullmann27/device-ai/pull/413),
`docs/evidence/momega1/qa/real-model/`, including failure-aggregate.json.
The 8/10 requirement and real successful self-correction requirement are NOT MET.

Real successful runs exposed a durable-proof bug: signing an in-memory object
before JSON persistence lost undefined fields and invalidated the stored MAC.
Chief reproduced the actual RED (`844f94fd`), then source `6baec1bc` snapshots and
revalidates the exact persisted data before signing new writes. Old invalid
records are never re-signed. Independent 16/16 adversarial cases plus 51/51
regressions passed; forged brands, altered bindings and corrupted proofs remain
ineligible. A fresh **known-u03 diagnostic** on exact `6baec1bc` subsequently
performed real native inference, RED, candidate GREEN, independent GREEN, storage
and one authenticated EXACT_SOURCE recall in a fresh Node process. This is real
persistence evidence, excluded from the original blind score.

The separately preregistered related-task pair used real Git ancestry and
unchanged frozen tasks. Its parent failed with two non-exact model patches, so
both later with/without-memory arms are NOT_RUN under that protocol. No learning
benefit has been demonstrated. Post-GREEN model reflection also remains unrun;
recorded pre-verification hypotheses are labeled accordingly.

The approved 7B pair is now independently admitted and integrated. Exact runtime
source `fb0f8152cfe4e85b778c2f2ef17034b2ef6333be`, review
`ea3bb013997d8f208c0995a7e440feba0370b450`, certificate SHA256
`29a14463a22b722eb948bb1afaa4ba8683d40b929955b725fa8fe3837a5b60ec`.
A genuine canonical admission call completed 156 tokens in 104.746 seconds,
exit0; sampled total native/container/host-client RSS was 5.161 GiB, below the
approved 8 GiB job budget. Actual resident cancellation also passed. Source
`a135a9ae` fixes the 7B-only aggregate CPU budget to twice remaining wall seconds
for two threads; wall timeout and fixed confinement remain. This explicit CPU
profile difference is not an equal-budget model comparison. Independent 89 host
and 21 actual Docker tests passed. Detailed bounds and sampling limitations are
in HOST-7B-RUNTIME-HANDOFF.md and the pinned review certificate.

The original twelve unknown tasks, first-ten score, two calls, 8192 context and
60-second benchmark timeout remain unchanged and **7B benchmark NOT_RUN**.
A known admission patch is excluded from blind scores. Shared Android LiteRT IPC,
actual Termux native compatibility, device cancellation and device resources
remain separate open prerequisites. Neither host model is Android Gemma.

## Reference face, motion and spoken setup

All seven requested Drive videos were retrieved privately; actual file hashes,
stream metadata and time-spanning frame comparisons are retained outside Git.
The owner clarified that the existing layout, colors, typography and controls
remain authoritative. The films supply the face/motion/effects/voice reference.
This session cannot directly hear the audio: no voice identity or heard-video
claim is made. No raw private media is committed.

Optional native 3D plus idle/observed gaze source `26555aa6` is integrated.
The genuine GLB contains a humanoid mesh, separate eyes, skin/joints and morphs.
It uses the same Director/reducer and existing visible-view clock; the orb and
2D fallback/preferences remain. The independent future-timestamp lockout RED was
fixed; 26/26 unchanged motion/gaze tests pass, zero skips. All 34 protected
existing design/font/2D source hashes remained unchanged at that acceptance.

**The visual head is not accepted.** Actual fixed-camera GLB material variants
have been compared repeatedly against primary-video frames. Opaque/coarse wire,
staring eyes, excessive density around the eyes/mouth, and a reachable O+Jaw
teeth exposure are explicit findings. The author and independent reviewer are
iterating on genuine geometry/materials and reachable production mapper poses.
Experimental assets remain outside the shipped asset until reviewed. Host
Blender renders are not Android Filament/Fold7 captures. Naturalness needs the
full fixed-camera motion sequence; numeric pose tests alone do not satisfy it.

The latest narrow mouth candidate combines mapper `9a23e5d6` with a neutral-
preserving jaw-rig correction (asset prefix `6b199c59`); neither correction alone
solves the geometry defect. The independently frozen semantic V2 contract is
27/27 PASS after 25/27 baseline PASS; the old literal double-opening expectation
is explicitly superseded, not reported green. All 112 independently frozen
speech combinations plus neutral have zero visible tooth/face intersections in
the combined candidate. Six original maximum poses and fixed-camera AA/O/blink/
stopped images were also examined. Source `62122c55` packages that exact GLB and
pin; final independent production-contract/asset validation and 27/27 cases
passed. Complete correction plus review `0d53e401` was merged into research
candidate `7eefff23`, with no luminous material or default-mode promotion.

The legacy EPSILON Python geometry check still records an actual V1 failure:
old JawY maximum0.25m and eye/jaw separation0.04m conflict with the corrected hinge
at0.267m /0.0214m. The independent reviewer developed a separate semantic V2
using unchanged neutral mouth/eye landmarks, actual transformed tooth vertices,
neutral-preserving rebasing and all seven original bad-bone cases. Its separate
final evidence is being preserved; V1 is never relabeled PASS.

A complete 224-frame, 9.333-second fixed-camera idle sequence now exists in
`docs/evidence/momega1-avatar-look/3805ba43/idle-preview.mp4`, with hashed frames,
contact sheets and logs. Actual production mapper motion drives head/gaze and
two blinks; synthetic input and host Blender rendering are explicit. Sampling
noise and remaining face/material differences prevent final visual acceptance.
The HDR4 material is experimental, not promoted into the app. Native renderer
`0996b42e` enables bounded postprocessing/bloom and passed actual Filament1.56 API
compilation and independent source review; native GPU pixels/performance remain
NOT_RUN. See JARVIS-AVATAR-RESUME.md. Fresh app still uses its existing default orb;
the unaccepted 3D look has not silently become the default.

PR436 provides an optional local Camera2 face-position adapter and pure readiness
projection. Native camera metadata, explicit foreground opt-in, permission,
comparable actual sensor timestamps and bounded frame freshness are required.
No face identity, eye tracking, stored pixels, cloud detector or downloaded model.
The independent reviewer found missing policy timestamps incorrectly defaulted to
now; source `6a354bec` rejects them, while actual native caller already supplied
an invalid sentinel. Original independent 22 cases pass after that correction;
additional camera cases bring that independent camera set to 24/24 PASS.

[PR447](https://github.com/Ullmann27/device-ai/pull/447), accepted production source
`6c2a30955fee800d6ddeffde9206158ea43537e9`, connects
first-open guidance, actual offline TTS callbacks, a dedicated on-device speech
test and explicit camera-to-Director observations. Six actual baseline UI tests
failed before implementation. Actual TTS lifecycle/caller checks found and fixed
stale heard-confirmation buttons; first-open voice defaults on only when no saved
choice exists, while stored mute wins. Actual first-welcome RED was 1/2 PASS;
final combined welcome/caller checks are 28/28 PASS, including nine new independent
cases and 17 prior cases. They are genuine host callback tests, not audible device
tests or full Activity startup proof. The
compact test panel leaves the head visible. Permission callbacks do not start
sensors; pause, dismissal and display change clear camera consent. Existing mute
choice is respected. No account API is connected: Google/Gmail, Meta/Facebook and
TikTok require real registrations and reviewed owner-authorized adapters.
Readiness and spoken-guide source plus independent PR445 evidence are integrated
at `705a6b7c`; see SPOKEN-GUIDE-HANDOFF.md for exact fixtures and limits.

The actual chat previously hid the dashboard and avatar. Accepted `ab55e440`
moves the same HologramView between home/chat and protects its parent geometry.
Final actual native host tests: 10/10 PASS. Earlier LEGACY graphics fixtures
reported a GONE window and are retained as fixture failures, not production fixes.

Accepted helper source `ca429fd8` provides bounded real-event worker/tool/document
arrivals and quiet local click/effect PCM, with existing mute, ASR/TTS and lifecycle
gates. Final63/63 host cases include17 independent cases;36 actual Canvas frames
cover compact88dp through200dp/wide sizes. Actual caller source `b6f4b174` plus
fixture `ae221ecc` binds effects to real ToolGate runner and Chief WorkerJob entry/
finally, and nonempty web-read results. Genuine native baseline13 tests6PASS7FAIL;
identical candidate tests13/13 PASS, including8 independent. Independent final
review `8e0db093` retains31 unique cases across visibility/helpers/callers.
These are host behavior/render checks, not audible speaker or real account proof.

Six full native Activity screenshots now cover phone/Fold portrait/landscape,
home and chat. Full layout fixture3/3 PASS at645b2215; comparison and exact pixels
are in `docs/evidence/momega1/layout-final/645b2215/comparison.html`.
The independent visual audit found portrait orb halo clipping and an idle phone
diagnostic strip, now assigned narrow repairs. Numerical layout PASS does not
mean visual reference equality. Full head/look, actual audible onboarding and
physical tracking remain open. Camera tracking currently stops with guide dismissal;
ordinary-chat tracking is an explicit remaining requirement, not implemented.
No decorative effect claims a real Gmail/PDF operation or completed test.

## Combined exact-source QA

Frozen production `645b2215022008e2149da81b48683f15077b1fb0` completed real local
unfiltered Android JUnit:2424 total,2412 PASS,0 FAIL,12 SKIP, including151 external
independent cases. Configured NewApi lint:0 fatal/error/warning; CRE1 and contrastive
parity passed. Backend64/64 PASS. Unsigned existing-option APK build is in progress.
Final Node:2311 total,2292 PASS,15 FAIL,4 SKIP, exit1. Preserve this RED result.
Six failures are stale Android wiring checks, two are outdated fake-Docker inspect
fixtures, three are stale missing-model preflight expectations, four require the
explicit resource-bounded model test environment. Test-only fixes and independent
negative controls are under review. No blanket green/full-release claim is allowed.
The QA lane owns complete raw logs, skip identities and final candidate rerun.

## Full acceptance matrix (required device milestone)

| Property | Status | Actual host evidence / missing device proof |
|---|---|---|
| Local model | NOT_RUN | Genuine pinned 1.5B host inference; Fold7 absent |
| Independent planning | NOT_RUN | Real unknown host model proposals; Android caller contracts; no device run |
| Tool execution | NOT_RUN | Real host Docker worker/verifier; device app→Termux unavailable |
| RED | NOT_RUN | Twelve real frozen host baseline failures; device unrun |
| GREEN | NOT_RUN | Four independent host repairs; device unrun |
| Independent verification | NOT_RUN | Separate host verifier and raw test identities; device unrun |
| Self-correction | FAIL | No successful revision in original real host series; device unavailable |
| Persistence | NOT_RUN | Genuine known-task new-write restart recall passed; device unrun |
| Reuse | NOT_RUN | Related parent failed; no measured later-task benefit |
| Safety | NOT_RUN | Host negative tests; full device acceptance absent |
| Resources | NOT_RUN | Host samples only; no Fold7 thermal/energy/RAM figures |
| Integration | FAIL | Exact645 Android/backend/lint pass; Node15 failures under repair; final combined candidate pending |

The same words have separate tiers: CONTRACT_TEST, HOST_INTEGRATION,
LOCAL_MODEL_HOST, FOLD7_MODEL, FOLD7_END_TO_END, RELEASE_READY. Only the last
required device evidence can close the local smartphone milestone. Known fixture
and synthetic runtime tests are not reclassified as unknown native/model results.

## Resume, testing and rollback

Compare actual PR410/lane remote heads and source differences first. Secure final
layout fixes/checker repairs and their independent review, rerun combined exact
source suites and preserve all failed runs. Continue visible head/material work
from JARVIS-AVATAR-RESUME.md and only then review first-open default promotion.
Run unchanged frozen7B tasks only through the canonical provider/worker.
Required combined QA is full Node, backend, unfiltered Android JUnit, configured
NewApi lint, parity and unsigned build. Historical lane/Code40/foreign
counts do not certify that final SHA. List all skips; critical MΩ cases cannot skip.

QA coordinates the shared 4-CPU/16-GiB host. Native inference and heavy compilers
must not silently contend with measured model runs. Model download approval does
not authorize exceeding its 8-GiB job budget. No device channel currently exists.

Rollback omits/reverts isolated source increments against Code40; it never restores
old owner data or removes later revocations. Do not clear an uncertain lease or
repeat a possibly executed tool. Inspect authenticated journals and require fresh
valid authorization when necessary. No weaker isolation or cloud fallback.
