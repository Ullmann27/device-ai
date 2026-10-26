# Source-level unit replay against pinned original and candidate – 2026-10-26

Status: **EXECUTED / LIMITED_V8_HOST_ONLY / FULL_NODE_AND_ANDROID_NOT_RUN**.

## Exact GitHub objects
- Test file source: `.github/continuum/baby-core.test.mjs`, Git blob `3d73f58c39d7d88a667acfa761d164c3e3f800fc`. Same exact test source applied to both revisions.
- Baseline module: `.github/continuum/baby-core.mjs`, `main` blob `f10c8ed8a2a401fa7c7f4e8cb20c415c04978683`, head originally pinned at `190f5eabaa60fc59292b2b80d3086aafd7743a80`.
- Patched module: `.github/continuum/baby-core.mjs` in PR158, blob `df049d3f2faac38b7fb263a64ba89a51d22cf5fd`.

## Measured results

| Exact same isolated test functions | Baseline | Patched |
|---|---:|---:|
| Runnable tests | 12 | 12 |
| Passed | 10 | **12** |
| Failed | **2** | **0** |
| Skipped web-only tests | 3 | 3 |

The two baseline failures are:
1. `grounded duplicate cannot inherit confidence, identity or citations from provisional row`: baseline returns `provisional-high` in the GROUNDED result instead of the source-verified ID.
2. `evidence ingestion never grafts unverified citations onto verified same-claim support`: baseline retains unverified URLs in a supposedly grounded claim.

The repaired module passed all 12 runnable case callbacks. Both code revisions and the exact test source were loaded from the GitHub connector at their pinned Git blob versions.

## Environment and limitations
The original module body and test functions ran in a fresh JavaScript V8 isolate, with `import`/`export` headers removed for evaluation. Real test callback bodies and production helper bodies were preserved. Node `crypto` was replaced with an **isolate-only deterministic identifier hash** and `structuredClone` by a JSON round-trip clone; assertions were implemented with a small strict `equal`/`ok`/`deepEqual` harness. Three tests requiring the original `public-web.mjs` network/IP/HTML dependencies were excluded in both runs. Therefore:
- This is **not** `node --test` in an authorized checkout, not original Node crypto, not an Android integration run, and not a successful GitHub Actions workflow.
- No inference, neural training, state branch modification or real owner data used.

## GitHub Actions availability
Multiple GitHub Actions runs of this branch, including final run `40272725587`, failed within seconds with no steps run and no runner assignment. These failures cannot certify Android or Node source regressions. Independent runner report on related PR157: `docs/evidence/gpt6-brain-routing-20261026/GITHUB-ACTIONS-RUNNER-BLOCKER-20261026.md`.

## Next reproducibility gate
On authorized full checkout with Node22:
```sh
node --test .github/continuum/baby-core.test.mjs
node --test .github/continuum/*.test.mjs
```
Use an actual Node runtime and separately verify Android/Termux/Samsung source compatibility before merging to `main`. Do not rerun broken hosted workflows blindly. PR158 remains Draft / review-only.
