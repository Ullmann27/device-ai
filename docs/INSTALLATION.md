# Android installation and signing (Device AI 0.3.1)

The regular debug APK uses `applicationId=at.heinz.deviceai`. The GitHub Actions runner may create a fresh debug signing key; Android then refuses an in-place update of an already installed APK signed by a different key (`INSTALL_FAILED_UPDATE_INCOMPATIBLE`). Increasing versionCode cannot bypass a signature mismatch.

For testing without removing the installed app or losing its data, the same CI workflow also builds an isolated **Device AI Preview** APK with package `at.heinz.deviceai.preview` and a different visible app label. Install the preview alongside the existing app. **Preview app data and permissions are independent** (including Shizuku authorization, imported models and settings). Do not assume settings are migrated.

Both APK variants are debug-signed, not distribution releases. An update of either package in later workflow runs will likewise require a stable signing key. Production upgrades must use a securely stored signing keystore provided through CI secrets; do not commit private signing keys or use a publicly derivable signing key. Until stable signing is configured, prefer preview for initial side-by-side testing, and retain old installations when their data matters.

GitHub Actions runs actual `gradle :app:testDebugUnitTest`, `gradle :app:assembleDebug`, and `gradle :app:clean :app:assembleDebug -PdeviceAiPreview=true`. The preview and regular APKs must each be downloaded from their corresponding artifact. Successful builds do not establish installation success on a physical Samsung Fold.
