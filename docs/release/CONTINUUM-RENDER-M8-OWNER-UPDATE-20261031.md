# Continuum Lokal: Render v29 original-signed update candidate

**Build source:** `research/continuum-m8-render-signed-release-20261031`.
**Target app:** `at.heinz.deviceai.continuumlocal`, versionCode 29,
versionName 0.3.28.
**Required installed-app certificate:** SHA-256
`6354e91dc0ca8df9173885f32402fd817f46e798f369206d4fd710ff3d03ee23`.

## Tested baseline and actual gap

The preceding M8 source commit
`861d2faed694fc1869050cf5ed81a593701801f5` passed
a real Render Android Gradle `testDebugUnitTest`, `lintDebug`,
unsigned `assembleRelease` and the selected Node contract tests.
This **does not** certify this changed release-script commit or provide
an original-signed APK.

The installed updater's fixed host is
`continuum-owner-v23-release-preflight.onrender.com`, its fixed
manifest path is `/continuum-local/latest.json`. A new Render
service under another hostname is **not** a substitute for that host.
The existing hosted v23 service still has an older, unrelated
release-preflight build configuration. Its Render settings must be
updated *only after* the original signing key and approved source are
available.

The historic Mentor Pair signing service is **not suitable**: its
package/certificate are different. Do not relax certificate matching
or uninstall the user's Continuum Lokal installation.

## Release script

`scripts/render-continuum-m8-owner-release.sh` is a separately
controlled, fail-closed release candidate. It uses the same Android
toolchain as the successful `render-continuum-m8-qa.sh`, reruns all
QA tasks on the exact release SHA, then requires a private P12 with
alias `continuum-local`. The exact certificate must match the installed
app pin **before** the Android release Gradle task is invoked.

These secrets must be provisioned **only in the authorized private
Render service** using Render's secure environment mechanism:

- `CONTINUUM_LOCAL_SIGNING_P12_BASE64`: original, existing owner
  PKCS#12 in base64, not a newly generated or debug key.
- `CONTINUUM_LOCAL_SIGNING_PASSWORD`: password for that same key.

Never paste these values into ChatGPT, GitHub, a URL, a repository,
a manifest or build logs. The script turns tracing off, uses private
temporary files, checks the X.509 certificate fingerprint and removes
the key file during cleanup.

Other required non-secret settings for the correct pinned service:

- `CONTINUUM_RELEASE_APPROVED_SHA`: full exact SHA of the
  source whose tests/compatibility and owner approval have completed.
- `CONTINUUM_RELEASE_VERSION_CODE=29`
- `CONTINUUM_RELEASE_HOST=continuum-owner-v23-release-preflight.onrender.com`
- Service name `continuum-owner-v23-release-preflight`; do not
  install the release command on a differently named service.
- Build command: `bash scripts/render-continuum-m8-owner-release.sh`
- Publish directory: `continuum-m8-owner-release`.

The output, **only on a successful signed build**, contains
`continuum-local/Continuum-Lokal-<sha256>.apk` and
`continuum-local/latest.json`. The APK byte length, checksum,
source SHA, certificate, application ID and version are verified
and all required catalog fields use the installed app's schema.

A built artifact does not prove that the pinned public hostname
is serving it. Verify the actual HTTPS response for the
`latest.json` and APK bytes, then test `Updates prüfen und laden`
followed by Android's user confirmation on the Fold7.

## Release security blockers

1. No verified proof that the original Continuum Lokal signing P12
   is available to a Render service. The Mentor Pair key is
   incompatible. **No signing fallback is permitted.**
2. The pinned v23 static site's existing build command/branch
   currently do not point to this release candidate. Editing a
   different Render site's files does not fix the installed app.
3. The **release-script commit itself** needs an independent
   signed-build test and ownership approval, even though its
   earlier source was QA-green.
4. The installed device's package/version/signer should be
   independently read back before actual installation.
5. A failed release must leave the old APK and owner data untouched.

The owner is not required to build anything on Termux. The only
intended device interaction once all gates are met is
`Updates prüfen und laden` and the OS installer confirmation.
