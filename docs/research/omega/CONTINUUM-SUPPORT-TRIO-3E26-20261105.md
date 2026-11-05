# Continuum additional support trio — completed support handoff

Date: 2026-11-05. Status: **INDEPENDENT_BASELINE_EXECUTED / SOURCE_REVIEWS_DELIVERED / NOT_RELEASED**.

## Scope and ownership

- Repository: Ullmann27/device-ai; support PR [#547](https://github.com/Ullmann27/device-ai/pull/547).
- Support branch: support/continuum-dialog-layout-apk-20261105-3e26, based on #543 89ae402d88b80ca5c48ab241e20c0369b7db517d.
- Three actual delegated assistants completed avatar/dialogue, graphics/layout and build/update source audits. Root executed the independent Node/backend baseline.
- The concurrent primary team owns production integration in [#544](https://github.com/Ullmann27/device-ai/pull/544), latest source checkpoint reviewed here `08aa76e8393bfedfeb1274c4e292b7e2a3139d15`; avatar [#545](https://github.com/Ullmann27/device-ai/pull/545), graphics [#546](https://github.com/Ullmann27/device-ai/pull/546).
- This support branch contains evidence and handoffs only. No primary-team production file, release key, version, update host or main branch was changed by this team.

## Delivered evidence

| Contribution | Exact result and boundary | Artifact |
| --- | --- | --- |
| Node baseline, executed by root | 327 original files, 2397 cases: 2360 PASS / 0 FAIL / 37 SKIP, exit 0; exact #543 source unchanged. | [RESULT.json](../../evidence/continuum-support-trio-3e26-20261105/RESULT.json), [full original TAP gzip](../../evidence/continuum-support-trio-3e26-20261105/node-full.tap.gz) |
| Backend initial run | 57 PASS / 7 setup FAIL because Express was absent in the fresh checkout. | RESULT.json; tool-observed outcome, raw log not archived. |
| Backend runtime follow-up | 64 PASS / 0 FAIL / 0 SKIP; express-rate-limit 8.7.3 was available instead of locked 8.7.1. Not exact-lock release reproduction. | RESULT.json; tool-observed outcome, raw log not archived. |
| Avatar/dialogue | Four missing PCM-silence stages rechecked on 08aa76e; exact #526 donor hunks and existing tests identified. Camera position tracking does not establish personal identity. | [PCM and camera intake](SUPPORT-TRIO-PCM-INTAKE-3E26-20261105.md) |
| Graphics/layout | Baseline lifecycle gaps documented; new #546 observer/receiver/lifecycle fix and five host-test cases source-reviewed. Runtime, visual and Fold7 results not executed by this team. | [Ambient lifecycle matrix](SUPPORT-TRIO-AMBIENT-LIFECYCLE-MATRIX-20261105.md) |
| Build/update | Eight gate groups and ten exact source blobs; existing original-package/original-signer channel retained in code. Live Render deployment still belongs to older a8e58ae source. | [Update audit](../../evidence/continuum-support-trio-3e26-20261105/update-audit.json), [root Render readback](../../evidence/continuum-support-trio-3e26-20261105/render-readonly-observation.json) |

## Executed baseline

Source commit: 89ae402d88b80ca5c48ab241e20c0369b7db517d. Source tree: 70c3d700a36e4e88850843fa9b392db92d99a388. Node v24.22.1. Tracked source stayed clean and HEAD unchanged. Both existing machine locks were held for each execution.

```sh
env -u NODE_TEST_CONTEXT node --test --test-concurrency=2 --test-reporter=tap .github/continuum/*.test.mjs scripts/*.test.mjs
env -u NODE_TEST_CONTEXT node --test --test-concurrency=2 --test-reporter=tap backend/test/*.test.js
```

Full Node duration: 154.795 seconds. Original uncompressed TAP: 519854 bytes; SHA256 52a55be5b07852e983dbfd1d762a5dc8da8cd36c27e006dc1895d044f154f625. Lossless archived gzip: 102860 bytes, Git blob 11bb802c707db0b699715a2e4d619504e2a44be2.

Skipped Node cases include unavailable native/Docker/kernel/model and JVM/SDK profiles. They are not passes. Backend initially could not import Express. A follow-up used available shared packages through links only in this team's checkout, without changing shared dependencies or tracked lockfiles. Express 5.3.0 and Helmet 8.3.0 matched the lock; express-rate-limit 8.7.3 did not match 8.7.1.

These outcomes do not transfer to 7ecdafac, 08aa76e or any later combined head. No Android compile, Android host test, Filament screenshot, audible lip-sync, original APK signature, install or Fold7 result was produced by this support team.

## Source review at the latest checkpoint

The primary team removed the incompatible AvatarExpression.WARM arm in 7ecdafac. Its 08aa76e checkpoint includes conversation-camera consent/STOP, appearance migration, Opus-derived layout/color changes and ambient recovery hooks. Root inspected the new commit patch; the graphics assistant rechecked the actual shell and its five test cases. Their presence is a source fact, not a passing-build or device claim.

The four PCM-silence stages still need selective integration into the newer production files. Existing donor tests should be reused, with current-utterance onStop → late callback → next utterance checked explicitly. Personal recognition of Heinz remains unimplemented in the reviewed face-position path.

The ambient matrix distinguishes the original #543 source gaps from the new #546 candidate. New callback/observer/receiver code addresses those source connections; host tests and real Fold7 visuals still require actual execution. Debug frame logging or manual recovery redraws must not conceal failures.

## Build and publication boundary

- Expected package: at.heinz.deviceai.continuumlocal.
- Expected original certificate SHA256: 6354e91dc0ca8df9173885f32402fd817f46e798f369206d4fd710ff3d03ee23.
- Existing manifest: https://continuum-owner-v23-release-preflight.onrender.com/continuum-local/latest.json.
- Existing Render build entrypoint already dispatches versionCode 39+ to the continuous-release path. No new service or update channel is required by the inspected code.
- Root independently read Render service/deploy metadata: latest live publisher deploy dep-dblbct6gekts73fte14g uses a8e58ae09f6f1a8f43bd0a6f3b30b7e81bb79be6, finished 2026-11-04T04:51:07.038382Z. autoDeploy is off. No build or deployment was triggered.
- Exact approved/freeze SHA, final-source QA, original-key availability, version greater than published and installed values, signed APK bytes and HTTPS readback remain required. Live manifest bytes and installed Fold7 version were not obtained; no next version was invented.
- The ten audited release/update source paths are unchanged between audited 7ecdafac and candidate 08aa76e. This does not establish a build or transfer tests to the candidate.

## Execution workspace incident

The initial duplicate checkout encountered ENOSPC; only this session's failed duplicate was removed. A bounded isolated checkout was then created and used for the completed baseline. Later the previously accessible scratch checkout/evidence directories and former toolchain paths were observed absent; the cause was not established.

The Node archive had already been uploaded as a Git blob and was attached to this branch at 385d6e8aa912284f1a9781e5665a623df945d9c9. The backend raw logs and local launcher were not uploaded before the directories disappeared. Backend counts and hashes therefore remain tool-observed results, not recoverable raw-log evidence. Existing team branches were not reset or overwritten.

## Concrete integration handoff

1. In #545, integrate the four donor PCM hunks into the current combined source, retaining its camera changes; run the actual listener/silence/STOP regressions.
2. In #546/#544, run the existing native host motion cases against the exact final shell; use the additional matrix for uncovered parent/window visibility, combined gates, registration cleanup and compact/Fold acceptance as needed.
3. Finish the exact combined Android gates and APK proof through the existing channel, with original package/signer and a measured higher version. Our baseline is supporting evidence only.

No main merge, paid execution, key read, signing, APK publication or installation was performed by this team. Continue from the actual newest primary head and preserve each team's ownership.
