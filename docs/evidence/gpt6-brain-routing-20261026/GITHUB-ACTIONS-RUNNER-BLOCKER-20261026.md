# GitHub Actions pre-run block audit – 26 October 2026

Status: **RUNS_FAILURE / RUNNER_NOT_ASSIGNED / ROOT_CAUSE_UNDETERMINED**. This is a read-only audit; no workflow configuration, billing or main-branch code changed.

GitHub REST actual workflow-run results:
- Latest recent successful run in the GitHub `status=success` search: `38841965441`, Build Device AI APK, 2026-10-16T02:44:23Z–02:50:56Z, source `004494068c9f3eda5965de6fdb8b7795dac1d279`. Do not infer no other successes through a different query; this was the latest returned success as of the check.
- Hourly Watch scheduled run `40275078208`, `main` SHA `190f5eabaa60fc59292b2b80d3086aafd7743a80`, created `2026-10-26T05:35:53Z`, ended `05:35:56Z`, conclusion `failure`.
- Hourly Watch job `121415997635` `watch` failed, **`runner_id:0`, `runner_name:""`, `steps:[]`**, and downstream `repair` and `publish` skipped.
- Zero-Cost Core scheduled run `40271323570`, created `2026-10-26T04:53:00Z`, ended `04:53:03Z`, conclusion `failure`. Job `121404563543` `live-core` failure, no returned steps.
- PR158 APK Build run `40272725587`, SHA `77b30efa874341f7cc65ccf40173329225f5ce37`, created `05:09:31Z`, ended `05:09:33Z`, failure. Job `121408852648` `build` failure with `steps:[]`. Commit check run shows **2 annotations**, but the current GitHub connector cannot read the check-run annotations endpoint.
- Many preceding pushes to the same PR158 branch also failed in 2–4 seconds with no steps; these are not separate source-level Android compilation regressions proved by workflow evidence.
- `fetch_workflow_job_logs` attempted for watch, zero-cost and APK jobs but the log redirect returned `BlobNotFound` 404; no actual failing command or stderr could be retrieved.

## Diagnosis boundary
The observed failed jobs did **not execute code steps on a GitHub runner**, so none of these recent failures proves a Kotlin, Gradle, Node, signing or model bug. A missing GitHub Actions billing authorization/spending budget, account policy or runner-allocation problem is **plausible but NOT confirmed**. The inaccessible check-run annotations and account-side Actions/Billing notifications are essential missing root-cause evidence. The `continuum-state` and `copilot/pr-12-working-first-vertical-slice` branches and pinned neural checkout SHA `a3bca34f4f2843875bd907954991ee9a5d9638ed` were explicitly verified to exist, but that does not fix runner allocation.

## Operational consequence
- Do not rerun or repeatedly push branches just to retry broken Actions; this creates more failed runs.
- Do not claim the scheduled hourly developer and four-hour neural worker are actually working until a job has a runner, nonempty executed steps, verified exit codes and durable new evidence.
- Prefer independently verified local Node/JVM, and existing authorized Android/Termux build path when available; no paid platform setup without the owner.
- Owner/provider-side next diagnosis: open GitHub Actions run annotations and account Actions/Billing/runner availability. Fix root cause **before** attempting any main/owner APK promotion.
- No workflow push, main merge or owner-data write was performed in this audit.
