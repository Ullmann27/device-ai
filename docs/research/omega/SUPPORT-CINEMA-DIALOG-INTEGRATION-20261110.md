# Cinema/dialog integration support

Owner: Continuum support root. Canonical development target: `cinema/fluid-light-stage-20261110` / PR #715. This isolated candidate integrates the current #720 source; the target branch remains untouched.

## Frozen inputs

- Cinema remote `5d36c5a57c031308c8de91049e19e9e8cb8365d5`, tree `970445b70dcd57d344f43ff95bfd58fd505f42ab`.
- Local Cinema equivalent `47a86df300210de7298e7768959cd5bf0c96fb4f`, exactly the same complete Git tree.
- Dialog remote `e99530750f4b471d54dc397b7a7b7f8b2977f6d1`, tree `d2380771bffe595f7371963a283b0a882c8974e3`.
- Local dialog equivalent `a2c8e7cf827bb2f9651520b20d49980f8ffdbcac`, exactly the same complete Git tree.

Both remote PRs were read before integration. Compare showed diverged histories: three dialog commits and eight later Cinema commits. No newer equivalent integration was present among the latest open PRs. A fresh isolated checkout merges both pinned source trees; `AgentActivity.kt` merges without conflict. Other Cinema changes, original avatar and existing STOP/return methods remain intact.

## Verification checkpoint

PORTIERT: combined source prepared. VERIFIZIERT: NOT_RUN on this combined tree at this checkpoint. GERÄTEBESTÄTIGT: NOT_RUN.

Next check: full main/test Android compilation and the 13 existing dialog/consent/context suites plus `CinemaStopReturnUiTest`, using a dedicated copied Gradle cache and an exclusive lease for this checkout. Prior #720 results are retained as upstream evidence, not claimed as combined-source results.

Relevant boundaries: EXP-07 source-bound regression evidence and EXP-12 session invalidation. Existing ApprovedWebToolGate remains the sole permission authority; chat wording does not grant generic or destructive tool actions. No new store, scheduler, model loop or training.

No main/RC/signer modification, new signing run, APK publication or installation. Rollback is to discard this integration candidate or revert its integration commit before promotion. Final exact-source receipt follows in this same branch.
