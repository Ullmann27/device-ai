# Continuum infrastructure repair evidence

Tested source: [`31e26ca48d411a73af42ec7d752af9195fd4adc8`](https://github.com/Ullmann27/device-ai/commit/31e26ca48d411a73af42ec7d752af9195fd4adc8). Official branch `sol/continuum-infrastructure-repair-20261025`, Draft PR #150. Later evidence/comment-only commits do not change executable behavior.

## Measured results

| Check | Result | Evidence |
|---|---|---|
| Complete Node suite, real kotlinc | 1238 pass, 0 fail, 0 skip | `node-final-31e26ca4.txt` |
| Android Gradle Kotlin/Java compilation, unit/widget tests, configured NewApi lint | BUILD SUCCESSFUL; 1032 pass, 3 optional fixture cases skipped; 36 widget tests pass; 0 lint issues | `android-final-31e26ca4.txt`, `android-junit-summary.json`, `android-newapi-lint-31e26ca4.xml` |
| Explicit Node→Kotlin→Node parity | 4/4 Kotlin cases pass including all 3 optional skips; independent SWB1/SWB2/CRE1 verification passes | `parity-31e26ca4.txt` |
| Backend syntax + tests | 60/60 pass | `backend-526cd43f.txt`; backend source unchanged at final SHA |
| Original Gemma bytes | 3659530240 bytes, SHA-256 matches selected original | `gemma-original-byte-verification.json` |
| DeepSeek | 48 declared shards / 510296708312 declared bytes at pinned revision, 0 downloaded | `deepseek-live-metadata.json` |
| Existing published owner APK | actual SHA, package mentorpaired, code23/0.3.22 and persistent certificate verified | `published-v23-*` |
| Render | read-only, 24 visible services / 23 in project scope; no service mutation | `render-inventory.json` |

The default Gradle profile omits three parity tests unless their fixture directories are supplied. Those exact cases were subsequently run using compiled real SDK classes, independently generated Node fixtures and separate Node verification. No test-only Android or model-runtime stubs were used. Synthetic parity fixtures do not alter phone weights or establish general answer quality.

## Reproduction

Use the official checkout at the tested SHA with JDK17, Gradle8.11.1, SDK36/build-tools35.0.0, Node24 and Kotlin2.4.0 available. Keep configured proxy/CA trust; the session used the system Java CA store. Install backend dependencies using `npm --prefix backend ci --ignore-scripts --no-audit --no-fund`.

```bash
CONTINUUM_OWNER_V23_QA=1 node --test --test-concurrency=2 .github/continuum/*.test.mjs scripts/*.test.mjs
npm --prefix backend run check
node --test --test-concurrency=2 backend/test/*.test.js
gradle --no-daemon --continue --max-workers=2 --console=plain \
  -Dorg.gradle.jvmargs="-Xmx2200m -XX:MaxMetaspaceSize=650m" \
  -PdeviceAiMentorPreview=true -PcontinuumUiWidgetTests=true \
  -Pkotlin.compiler.execution.strategy=in-process \
  :app:testDebugUnitTest :app:lintDebug :app:compileDebugKotlin
```

For explicit parity, prepare separate scratch directories using existing helpers:

```bash
node .github/continuum/contrastive-parity.mjs prepare /absolute/scratch/contrastive
node .github/continuum/cre-parity.mjs prepare /absolute/scratch/cre1
```

Set `CONTINUUM_CONTRASTIVE_PARITY_DIR` / `CONTINUUM_CRE1_PARITY_DIR` to those directories when running `ContinuumContrastiveParityTest` and `Cre1InferenceEncoderTest`, through Gradle or JUnitCore on the compiled SDK main/test classes. The recorded session used JUnitCore with Kotlin stdlib, JUnit4.13.2 and Hamcrest1.3; both classes executed 4 cases with no skips. Then independently verify:

```bash
node .github/continuum/contrastive-parity.mjs verify /absolute/scratch/contrastive
node .github/continuum/cre-parity.mjs verify /absolute/scratch/cre1
```

The final Android log normalizes trailing whitespace only for Git checks; result lines and all diagnostic content are preserved.

## Failures retained and practical limits

The initial combined run at `526cd43f` recorded 1 new UI test setup failure (queued OnShow callback not drained) and 1 intermittent OMEGA ABSTAIN during concurrent compiler load. The test fixture now drains the real main looper; production dialog behavior is unchanged. The unmodified OMEGA suite passed in isolation and the subsequent full Node suite passed without concurrent Gradle. Its 25ms numeric VM limit remains; a future constrained host can still legitimately abstain. Initial logs remain next to the final logs.

The existing signed code23 APK comes from older source and does not include these repairs. New source targets code24/0.3.23, but no new APK is signed/published/installed. New complete source approval, original signer and physical Fold7 acceptance are still required. Gemma device inference, DeepSeek inference/GPU, current phone BUILD callback and free-language-to-new-APK remain NOT_RUN/BLOCKED. No main merge or paid resources.

Review: no confirmed Critical/Important issue through core source `526cd43f`; later version/config/test-fixture edits were author-reviewed. The minor misleading “verified catalog ID” comment was corrected; output budgets still use label heuristics and are not native family/context certification.
