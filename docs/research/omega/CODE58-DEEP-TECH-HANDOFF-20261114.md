# Code58 deep technology integration — 2026-11-14

## Source and delivery state

- Repository: `Ullmann27/device-ai`.
- Local branch: `work/code58-deep-tech-20261114`.
- Original Code58 base: PR #836 at `1656443d137bb6787c2eabe69a3e91ff1b2275b2`.
- Integrated support branch: PR #839 at `de186d9efecd9824aa4615affef616347433fb3d`.
- Integrated capture continuation: PR #840 at `10ce4b675aa53963740d3625848ac287af83650b`.
- Local production changes, regression tests and QA selection are complete.
- Owner authorization for publishing this branch and running CI was received on
  2026-11-14. The earlier publication block is resolved by that authorization.
- At commit creation, the combined Android/Robolectric and build run is **PENDING**.
  Record its exact SHA and completed results in the accompanying pull request.
- Fold7 hardware verification: **NOT_RUN**.

Six bounded work streams covered dialogue architecture, projection lifecycle,
avatar scheduling, action dispatch, memory provenance and independent integration
review. The final local tree includes the support branch's polite follow-ups,
gaze-clock wrap, capture geometry recovery and research-topic fixes. The test
results below concern the explicitly named local suites, not all support changes.

The current integration also retains PR #839's repeated-letter continuations and
research length correction, plus PR #840's short clarification questions and
duplicate-start protection. Both implementations of the capture guard were
unified: keep the broader valid-session guard and retain `stopSelf(startId)` for
rejected unbound starts. The notification-preservation fixture now establishes
its notification before binding the session; duplicate starts correctly do not
create another notification. The joint workflow selects the additional capture
isolation class and requires its five cases plus the clarification boundaries.

## Repairs

### Object corrections preserve established context

`LocalConversationContext` distinguishes an unambiguous correction to an existing
object from a topic replacement. `OwnerObjectStateLedger` validates a correction
on a deep copy before admitting its stripped prefix. Changing an object's color
therefore preserves its established count and location. Ambiguous referents,
questions, commands and hypothetical statements do not become facts.

Independent review exposed a second defect: a later retraction or an epistemic
phrase such as `ich weiß es nicht` could still enter the ledger, including a false
white color. Qualifier checks now reject those cases with Unicode-aware token
boundaries. Literal color corrections to white remain valid.

### Stale capture starts cannot stop a current session

`ContinuumLiveProjectionService.onStartCommand` ignores stale, null or duplicate starts when
this service already owns the currently authorized live session. The guard runs
before a stale notification can replace the current one. Rejected starts stop
only their own `startId`. Added Android tests exercise stale and null starts.

This repairs one concrete route to a lost display. It does not establish that
every black-display cause has been eliminated. The GPU fence wait and device
surface/producer behavior still require Android and hardware investigation.

### Avatar motion follows the display clock

The existing animation loop uses `Choreographer.FrameCallback` with
`AvatarFramePacing` admission instead of a fixed 16 ms scheduling assumption.
Motion uses the frame timestamp; pause/resume resets admission state. Wake-up
lead accounts for the reported refresh rate while the current thermal and
speech budgets remain authoritative.

Independent review found that audio samples use arrival time, which can be later
than the delivered frame timestamp. The envelope now advances using current
uptime, while motion retains its vsync clock. The Android regression
`lateVsyncCannotRewindNewerAudioSamples` covers that connection.

The deterministic scheduler model admits 240 updates in four seconds for a
16 ms budget on 60/120 Hz inputs, and 120 updates for a 33 ms budget on
60/90/120 Hz inputs. At 90 Hz, a strict 16 ms minimum admits 45 updates/second.
These are model results, not measured device frame rates.

### Action cancellation retains the correct dispatch boundary

`LiveComputerActions` records `NOT_DISPATCHED` until immediately before
`performOnce`. Cancellation after admission but before dispatch permits a fresh
observation to retry. Dispatch exceptions and cancellation after dispatch retain
the uncertain-dispatch block. Repeating the same observation remains prohibited.

### Calendar events match complete dates

`ProactiveSideQuestAndCountdownEngine` validates whole numeric calendar dates and
an explicit year when supplied. `14.11.` cannot be mistaken for `4.11.`;
`14.11.2027` cannot match 2026. Valid clock tails such as `14.11. 18:00` and
`14.11. 18 Uhr` remain accepted. Bare short years, invalid times, impossible dates
and identifier fragments remain rejected. Provenance, validation, privacy and
relative-day freshness gates remain in effect.

## Fresh local verification

The runs below compiled the checkpoint `b8915e641b64258146d5504afffb68855b166515`
on 2026-11-14, before the subsequent PR #839/#840 integration. They are historical
host evidence; the merged tree requires its own Android CI result.

| Scope | Result | Evidence boundary |
| --- | --- | --- |
| Context/object ledger, eight JUnit suites | 78/78 pass | Real context/ledger sources; unused research/document and memory type boundaries use declared host shims |
| Action dispatch, four JUnit suites | 27/27 pass | Real action, registry and receipt sources; two Android-independent enum declarations supplied for host compilation |
| Proactive memory/retrieval, five JUnit suites | 36/36 pass | Real engine/retriever and literal production memory declarations; unrelated store implementations excluded |
| Avatar pacing/audio, two JUnit suites | 18/18 pass | Real pure Kotlin sources; no Android rendering execution |
| Projection start-path probe | 8/8 pass | Actual extracted `onStartCommand` body compiled with declared platform fakes |

Total: **159 distinct JUnit tests**, plus **8 separate projection probes**.
The host shims and projection fakes are test scaffolding, not Android evidence.

Before the fixes, new regression cases reproduced failures: object scope 4/9,
dispatch boundary 1/3, calendar recognition 6/9 and capture start handling 4/8.
Independent-review additions also reproduced the retraction and time-tail
regressions before repair. Baseline runs are separate from the final total.

Checkpoint checks passed: `git diff --check`, workflow YAML parsing, embedded
Python parsing, shell syntax, existence of all 38 selected test suites and all
23 explicitly required regression methods. These static checks do not execute
GitHub Actions or resolve Gradle/Android dependencies.

## Prepared Android verification

The existing `.github/workflows/code58-fold7-routing-qa.yml` now includes this
branch. Its selector includes all relevant host suites and Android lifecycle/UI
regressions. The evidence check rejects missing required cases, failures, errors
or skipped tests. After authorized publication, record the new run's exact commit
and result; previous Code58/PR #839 CI results do not verify this local tree.

Equivalent test command in a configured Android environment:

```sh
gradle --no-daemon --no-build-cache --max-workers=1 --console=plain \
  -Dorg.gradle.jvmargs='-Xmx2200m -XX:MaxMetaspaceSize=768m -XX:+UseSerialGC -XX:ActiveProcessorCount=2' \
  -Pkotlin.compiler.execution.strategy=in-process -PdeviceAiMentorPreview=true \
  -PcontinuumOwnerArm64=true -PcontinuumStandaloneVersionCode=58 \
  -PcontinuumLocalStandaloneQa=true -PcontinuumUiWidgetTests=true \
  -I docs/evidence/omega-apk54-20261111/harness/test-memory.gradle \
  -I .github/continuum/code58-fold7-routing-tests.init.gradle :app:testDebugUnitTest
```

Remaining acceptance work: run this Android matrix on the exact published
commit, then exercise capture consent/replacement/fold-resize and avatar
speech/pause/resume on the Fold7. No device display, rendering smoothness or
installable APK outcome is asserted by this handoff.
