# Code55 PDF harness and signing audit

Source: `e84816ec80cdc605325d8ea4983c2ac98102d557`, tree `c9e30889ce21aad4484b3d5a7db54510551e0899`.
Scope: source review only. No signing, secret reads, environment changes,
service changes, deployment or installation.

## Reusable PDF harness

The migrated native test was still listed in
`tools/pdf-scene-review/focused.init.gradle`. The harness now retains the
24 remaining host classes and its all/core/visual scopes. Its removed decode
scope fails explicitly with the mandatory Android probe path. Supplying the
obsolete `continuumPdfAllowMissingHostNative` option also fails; no assumption
or silent omission replaces native verification. JUnit journaling and
`failOnNoMatchingTests = true` remain.

Read-only scan at the source above: 292 top-level script files, 441 GitHub
automation/configuration files and 225 tool scripts. No old class or former
runtime bypass remains in active Node contracts or Code55 workflows.
The other literal class reference is
`.github/workflows/continuum-code54-qa.yml:118`, which is gated exclusively to
`integrate/omega-apk54-20261111`; that historical workflow is unchanged.
Historical receipts and frozen source records are unchanged.

This edit has source review only. Gradle execution: NOT_RUN. The existing
Code55 complete-qa gate still requires full-android, native-pdf and
node-backend success on the same source. Earlier native success applies only
to its recorded source; it does not validate this new integration commit.

## Existing original signing routes

| Route | Existing source | Exact constraint |
| --- | --- | --- |
| GitHub | `.github/workflows/continuum-code50-sign-release.yml` and `scripts/continuum-code50-github-signer.sh` | Fixed Code51 branch, Code51 QA workflow/event/jobs, Code51 artifact and receipt, Code50 release baseline. It cannot accept Code55 unchanged. |
| Local Fold7 | `scripts/continuum-fold7-standalone-apk.sh build 55` | Termux, local JDK17/Gradle8.11.1/SDK36 toolchain, persistent original Continuum Lokal P12 and local interactive password. Verifies package, original certificate and version. Produces a local APK without installing it. |
| Existing Render publisher | `scripts/render-continuum-m8-owner-release.sh` dispatches code39+ to `scripts/render-continuum-owner-continuous-release.sh` | Exact-SHA freeze release and approval, pinned service/host, original signer, higher live version, full existing unsigned QA, signed verification and staged output. Publication is separate. |

The Render entry requires service
`continuum-owner-v23-release-preflight` and host
`continuum-owner-v23-release-preflight.onrender.com`.
Its release fields are `CONTINUUM_RELEASE_VERSION_CODE=55`,
`CONTINUUM_RELEASE_APPROVED_SHA=<accepted SHA>` and
`CONTINUUM_RELEASE_FREEZE_LIFTED_BY_OWNER=<same SHA>`.
No field was set or inspected on a live service. Existing private signing
values must remain confined to their protected host.

Both Continuum Lokal routes preserve package
`at.heinz.deviceai.continuumlocal` and certificate SHA-256
`6354e91dc0ca8df9173885f32402fd817f46e798f369206d4fd710ff3d03ee23`.
`scripts/continuum-local-owner-apk.sh` instead targets the legacy
`at.heinz.deviceai.mentorpaired` package and another certificate; it is not
the route for this update.

Important source limits: the local fusion pre-sign test helper still fixes
QA versionCode 27, and both old local/Render QA helpers use JDK17 and do not
invoke the new native PDF lane. They therefore do not replace current
Code55 source-bound full QA. Live host/toolchain/key availability, installed
version, SHA-specific owner approval and signed artifact verification were
not examined or claimed.

Smallest next action: finish the current exact-source Code55 gates, freeze
the accepted source and artifact receipt, then hand that source to one
existing owner-approved original-key route. Local signing can use the
existing `build 55` entry after its local prerequisites are verified.
The unchanged GitHub Code51 signer must not be dispatched for Code55.
This audit adds no signer or workflow and does not authorize signing,
publication or device installation.
