# Inworld Router integration — 2026-11-04

## Status after owner clarification — shelved prototype

The owner clarified that Inworld is wanted only if it materially helps build the
app's AI, avatar/visualizations or memory. A separate one-question text mentor
does not satisfy that goal, so this prototype is preserved for reference and is
not selected for merge, deployment, activation or phone installation. No live
provider call occurred. The earlier activation checklist below is historical,
not a current request to configure a key or deploy this prototype.

See `INWORLD-FIT-ASSESSMENT-20261104.md` for the official capability assessment.
Inworld's broader voice/lip-sync, image and Realtime memory features may be useful,
but they require a different integration and have no measured benefit here yet.

## Delivered source and authorization

- Repository: `Ullmann27/device-ai`; [Draft PR #504](https://github.com/Ullmann27/device-ai/pull/504).
- Branch: `feat/continuum-inworld-router-20261104`.
- Frozen base: `0e3685a3bc6cb3a4af61988450371f11e9cb0f3c` (PR #500).
- The owner requested server-only `INWORLD_API_KEY`, literal Basic authentication and implementation before authenticated testing.
- Existing EXP-10 optional text mentor is the applicable contract. Source integration is complete; live phone acceptance remains open.
- The isolated worktree preserved the sibling session's uncommitted avatar work.

## Feature and boundaries

Android's **INWORLD · EINZELFRAGE** entry is available in the learning center, model center and cloud settings. It reuses backend device pairing, shows the whole question for one-time confirmation, and displays the reply as external/unverified. Pairing returns to Inworld. Overlong input is rejected intact; late replies cannot mutate another session or Inworld request. Ordinary local conversation/model and OpenAI tool-agent contracts are not rerouted. No provider key or direct Inworld endpoint was added to Android.

`RemoteMentorProtocol` validates the shared contract: only provider, literal consent and a bounded question leave the client. Successful text requires matching provider, unverified trust, no tools and no memory write. Existing Groq/OpenRouter providers continue through this contract.

The shared backend adapter accepts INWORLD in addition to GROQ/OPENROUTER; its historic `GroqMentor` name is retained. Inworld uses a pinned HTTPS endpoint, `Authorization: Basic <key>` without re-encoding, default model `inworld/models/gemma-4-31b-it`, and `extra_body.reasoning.effort: medium`. Only the backend configures model/effort. A valid response model identifier is retained; separate reasoning, provider metadata and tool proposals are discarded. Upstream receives the approved question plus the existing static public project instruction, without chat history, private memory or device context.

Inworld has one upstream attempt per approved request, a 45-second deadline, redirect rejection, a 64 KiB response limit and a 4200-character presentation limit. A disconnected HTTP client aborts fetch and releases the in-flight slot. This is not proof of provider-side cancellation or refunded billing. Android's existing definite-401 pairing recovery is distinct from an upstream retry.

Daily quotas count UTC days per server process; restart resets them and multiple instances have separate counters. They are not an account-wide cost cap. The provider is disabled by default; no startup probe calls Inworld.

## Review findings resolved

The existing `addUserMessage` helper could discover a public research topic from the approved question when recurring research was already enabled. A real Robolectric regression reproduced a new `topic_pending=quantenmechanik` preference after confirmation. The Inworld path now passes `allowLearningHints=false`, suppressing both topic discovery and the curiosity radar. Existing callers retain their default behavior. The regression now passes.

Assistant rendering does not train, write durable answer memory, or dispatch tools. It retains a short activity-local cockpit preview. Existing local TTS may read displayed text when enabled; no Inworld audio capability is used. Independent source/security review found no confirmed remaining blocker. The reviewer independently ran the intermediate 84-test backend suite; the root ran the final combined tests below. No second independent final run is claimed.

## Observed verification

| Check | Observed result |
| --- | --- |
| Original backend suite | 64 passed, 0 failed, 0 skipped |
| Final complete backend suite | **93 passed, 0 failed, 0 skipped** |
| New real-Express/local-upstream Inworld HTTP tests | 9 passed; included in the 93 |
| Android shared protocol JUnit tests | **9 passed, 0 failed, 0 skipped** |
| Actual Android Robolectric UI tests | **7 passed, 0 failed, 0 skipped** |
| Existing mentor and Android integration Node wiring tests | **31 passed, 0 failed, 0 skipped** |
| Backend syntax and smoke-command syntax | Passed |
| Android main and test source compilation | Passed in the scoped Gradle run |
| Live Inworld / deployment / signed APK / physical phone | **NOT_RUN** |

Backend tests use synthetic credentials and local fixtures. HTTP child processes have an explicit environment, temporary working directory, and a test preload accepting only the pinned production Inworld URL before forwarding to loopback; unexpected fetches are blocked. No production endpoint override or extra dependency was introduced.

Observed RED evidence covers unsupported provider, transport cleanup, configuration factory, smoke gates, missing Android protocol, six missing UI behaviors, and question-topic isolation. Evidence is in `docs/evidence/inworld-router-20261104/`, including final Android JUnit XML. The complete Android test suite was not executed: all test sources compiled and the 16 relevant behavioral tests ran. Existing compiler/deprecation warnings remain visible in the captured output.

## Reproduction

Backend: Node 24.22.1 was used; the project declares Node >=20.

```bash
npm --prefix backend test
npm --prefix backend run check
node --check backend/scripts/inworld-smoke.mjs
node --test .github/continuum/groq-mentor-ui-wiring.test.mjs .github/continuum/groq-mentor-resilience-wiring.test.mjs .github/continuum/android-integration-wiring.test.mjs
```

Android: JDK 17 and the existing Android SDK/Gradle cache.

```bash
./gradlew --offline --no-daemon --max-workers=1 \
  -Pkotlin.compiler.execution.strategy=in-process \
  -Dorg.gradle.jvmargs='-Xmx1536m -XX:MaxMetaspaceSize=512m -Dfile.encoding=UTF-8' \
  -PcontinuumUiWidgetTests=true testDebugUnitTest \
  --tests at.heinz.deviceai.InworldMentorUiTest \
  --tests at.heinz.deviceai.RemoteMentorProtocolTest
```

No GitHub Actions were used for acceptance. No signing identity, version code, owner data, local model weights or release artifact was changed.

## Remaining activation steps

1. Configure `INWORLD_API_KEY` securely on the backend service used by the app and set `INWORLD_MENTOR_ENABLED=true` when activating the provider. The local execution environment had no key. No credential from chat was used.
2. Deploy the reviewed backend source and apply its environment configuration. No Render workspace was selected; no live service configuration was inspected or changed. Repository source is not a deployment.
3. Run `npm run smoke:inworld -- --confirm-live` in that configured backend environment. It sends a fixed synthetic question and can consume credits. It prints status flags/provider/model only, not prompts, answers or keys. Its process-local quota is separate from a running service.
4. Build/install the reviewed Android version through the existing authorized release process, then verify a paired one-question reply on the phone.

The supplied model's account availability and actual reasoning behavior remain unverified until live testing. Complete configuration and official documentation sources are in `backend/README.md`.

## Rollback

Set `INWORLD_MENTOR_ENABLED=false` and restart the backend. Reverting this scoped PR returns the previous provider set. No state migration or training occurred. Preserve sibling branches, release signatures and owner data.
