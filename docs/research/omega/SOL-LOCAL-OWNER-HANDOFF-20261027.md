# Local main-app integration handoff

WIP / UNVERIFIED checkpoint. Owner final assignment, 2026-10-27.

Branch `sol/continuum-local-owner-integration-20261027`, based on PR #195 at `8b75080cbfb40e8e6a284ee95f65b8700d9105a8`. GitHub API verifies PR #187 now at `7665a3dcb02f9bd1fa79cb6d4b63e95b147eb9bb`; #195 already combines Gemma restore (#193) and memory evidence fixes/tests (#194). Compare exact files before any integration. Do not duplicate these paths.

Priority: HTTPS Git authentication and repeatable local owner APK build; bounded compatible integration only. No new inference, training, model download or second app. Existing owner certificate must remain `2b05fa9de378fb6c6c5376143774a573897dc9c9151305f1c44010275c76a9cc`. Target owner package `at.heinz.deviceai.mentorpaired`, next 0.3.25/code26. Original signing key availability is not implied by repository access.

Research PR #189 is safely preserved, including the unfinished status UI, at `9f2ebecc`. Last focused core checkpoint `aafc382e`; no autonomous formatter repair succeeded (9 model requests across retained rounds; ABSTAINED). Do not integrate unverified UI or label research as a confirmed capability. No branch deletion or forced rewrite.

Next: inspect/fix `scripts/continuum-local-owner-apk.sh`, add a credential-free HTTPS helper, run actual local Android compilation/unit checks on this checkout, publish exact results and unresolved signing/device blockers. APK is NOT_BUILT at this checkpoint. `main`, production, original signing keys, installed model/memories remain untouched. Rollback: discard this unmerged integration branch; all source branches retained.
