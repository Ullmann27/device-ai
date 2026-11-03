# Overnight release audit — 2026-11-03

## Scope and authority

Read-only inspection of the existing Render publisher, live catalog, release scripts and resource-test correction. The owner's current overnight request authorizes integration and an original-signed APK through the old app's existing Download button after the actual gates pass. It does not authorize a main merge, a signature change, new model runs or clearance of the S01 barrier. This audit performed no deployment, signing, secret access or model execution.

## Last observed live release

These are claims from the successfully retrieved catalog, **not independent verification of the downloaded APK bytes or certificate**. Re-read the live catalog immediately before publication.

| Field | Observed value |
| --- | --- |
| Catalog | `https://continuum-owner-v23-release-preflight.onrender.com/continuum-local/latest.json` |
| Channel / approval | `OWNER_RELEASE` / `approved: true` |
| Version | `0.3.38`, code `39` |
| Source commit | `a9709854f48d2fd6589f9ce5ea97f6ec3be0d233` |
| Package | `at.heinz.deviceai.continuumlocal` |
| APK SHA-256 | `b22ac165abb1532eb893afd3f8fbe4d135bacaf2d094de9cbe6d38b81a4dbad9` |
| APK bytes | `54673371` |
| Signer SHA-256 | `6354e91dc0ca8df9173885f32402fd817f46e798f369206d4fd710ff3d03ee23` |
| APK URL | `https://continuum-owner-v23-release-preflight.onrender.com/continuum-local/Continuum-Lokal-b22ac165abb1532eb893afd3f8fbe4d135bacaf2d094de9cbe6d38b81a4dbad9.apk` |

## Existing publisher and source route

- Service: `srv-dbf1tr2vcj2c73ehpfog`, `continuum-owner-v23-release-preflight`; static site, auto-deploy **off**, manually triggered.
- Repository/branch: `Ullmann27/device-ai`, `research/continuum-m8-render-signed-release-20261031`.
- Build command: `bash scripts/render-continuum-m8-owner-release.sh`; publish directory: `continuum-m8-owner-release`.
- Last observed live deploy: `dep-dbkokcdlmcpc73diq5c0`, source `a9709854f48d2fd6589f9ce5ea97f6ec3be0d233`, completed `2026-11-03T07:34:15.63722Z`.
- Root verified that this source is an ancestor of the integration branch. After final QA, fast-forward the configured publisher branch to the exact tested commit with an expected-old-SHA check and no force. Recheck ancestry and the branch head at that point. No main merge or service-setting change is needed.
- The wrapper already dispatches codes at least 39 to `scripts/render-continuum-owner-continuous-release.sh`. Approval/freeze-lift SHA guards must both equal the final tested commit; these guards do not check out a different source. Preserve the existing original-signing secret configuration without exposing values.
- Code **40** was the next permissible code at the observed snapshot; the live comparison must still prove strict monotonic increase when publishing.

The separate QA service `srv-dbkbtgqjnfac73a6incg` (`continuum-unified-research-apk-20261102`) currently serves a fixed unsigned code-38 receipt. It is not evidence of the final integration or of a usable bounded model host. GitHub Actions are not the acceptance route under `AGENTS.md`.

## Mandatory release gates retained

The existing continuous QA and release scripts remain authoritative and unchanged by this test correction. Retain exact source/host/approval checks, clean-source checks and strict live-version checks. Before materializing signing secrets, run the existing atomic regressions, critical and full Node suites, fresh exact-source receipts, Android compilation/unit/widget tests, required fresh Android JUnit counts without skips, lint, and unsigned APK/AAB checks. Full Node evidence must expose the resource skips described below.

Then retain the original PKCS12 certificate check, signed release lint/build, actual APK v2-signature verification, exact package/version checks, hash/size checks and exact update-catalog contract. No gate is waived by this audit. After deployment, fetch the immutable APK and verify its bytes and certificate independently. `continuum-owner-render-readback.mjs` reports `APK_HASH_VERIFIED_CERT_NOT_VERIFIED`; that result still requires actual `apksigner verify --verbose --print-certs` verification. Device/Fold7 acceptance remains separate and must not be inferred from a build or publication.

## Resource-test review

Reviewed `scripts/continuum-local-model-{cpu,7b}.test.mjs` and the new test-only `scripts/continuum-model-test-host.mjs`: **approved; no blocking finding in the corrected diff**. The helper independently inspects the real cgroup filesystem, membership, control ownership/type, positive memory limit at most 8 GiB and zero swap. A capable host whose production guard rejects fails; an incapable host must prove real rejection, including rejection before spawn. The heap check uses a real subprocess with production Node arguments and an empty environment. The Docker probe uses the fixed client/socket, empty configuration and exact pinned image, without pulling it. Unexpected execution failures still fail.

The helper is imported only by the two tests. No production launcher, resource limit, model pin, CLI, seccomp or release/QA-script change was present in this correction. Root reported the targeted result as **15 total / 11 PASS / 4 SKIP / 0 FAIL**; the reviewer inspected the code and did not independently rerun that execution.

Exactly four success proofs remain **NOT_RUN** on this host:

1. Exact 7B-profile launcher CPU/address-space arguments under a valid real job memory bound.
2. The two-thread profile's unchanged wall-clock cancellation under that bound.
3. Actual compiled synthetic ELF execution through gcc-14/prlimit/bwrap proving kernel CPU and address-space limits.
4. Fixed-image Docker CLI transport rejecting mismatched weights before native launch.

Keep their explicit skip reasons and counts in the final QA evidence and release notes. These changes establish **no S01 acceptance or barrier clearance, no native-model/inference acceptance, no bounded-host acceptance and no device acceptance**. This document is an audit record, not a publication receipt.

## Additional namespace proof review after the first combined run

The immutable 09dced7a full Node run remains FAIL: 2267 PASS, 15 FAIL, 23 SKIP. Its original JUnit and summary are preserved under docs/evidence/night-integration/09dced7a. One failure was a stale streaming source assertion: a completed 250ms wait now checks the output budget before leaving the loop. The corrected assertion retains cancellation, deadline and budget checks and passed5/5.

The other14 cases require successful host namespace execution. Independent inspection found that this host's fixed bwrap/prlimit files are uid65534 rather than the required root owner; separately, a fixed model-free bwrap /usr/bin/true probe was denied creation of its NETLINK_ROUTE socket. The test-only helper records those separate capabilities without invoking the production guard or borrowing its sandbox arguments. Unfamiliar errors remain failures.

The14 actual OS-success cases now remain explicitly NOT_RUN when these prerequisites are absent. All11 existing portable/negative cases still execute. A new always-on case exercises the real query and launcher rejection and requires absence of model observations/envelopes; on a capable host it requires actual synthetic success, so an always-reject regression cannot silently skip. This correction received independent source review.

Actual combined observation/CPU/7B verification: **41 total, 23 PASS, 0 FAIL, 18 explicit SKIP**. These18 missing proofs comprise the prior4 resource/Docker cases plus14 namespace/process cases. They are not native-model or host acceptance. Production tools, ownership, isolation policies, model pins, S01 barrier and release gates remain unchanged. The final full-node run must use the next immutable checkpoint.
