# OSV inventory snapshot repair - 2026-10-25

Status: LOCAL_SCOPED_TESTS_PASSED / REPOSITORY_PUBLICATION_PENDING / NO_RELEASE.
Base: `5801d2d5650796ae01561e6a7e8e1f21b1567a63`.
Target: `research/continuum-omega-resume-chatgpt-20261025` (Draft PR #134).

## Defect and bounded fix

The query adapter validated inventory provenance, but copied only the components
array. Caller mutations while awaiting a response could relabel findings or
change later batch queries without changing the original inventory fingerprint.
Reuse the existing inventory validator to create detached normalized component
values. Freeze those values and use the same snapshot for report provenance,
budget checks and every request/response mapping. This grants no authority.

Files: `advisory-intake.mjs`, `osv-public-query.mjs`, and new
`osv-inventory-snapshot.test.mjs` under `experimental/omega/security/`.

## Checks actually performed locally

Node v20.20.1, fresh temporary directories, mock streaming HTTP only.
Original source bytes were checked against Git blob IDs:

- advisory-intake.mjs: 179d38372206798eede01d922c7f301e9972ded3
- osv-public-query.mjs: cf48f0c5696ba894f83910dd4218e4b0fc09ac50
- bounded-osv-response.mjs: 9cd990863b4b93044ff3399bcfec1a2359bda76e
- osv-public-query.test.mjs: e0855928acc281051ba35310cb7501b5727d1468

Command from that directory:
`node --test --test-concurrency=1 osv-public-query.test.mjs osv-inventory-snapshot.test.mjs`

- Unmodified baseline plus four new regressions: 13 tests, 11 pass, 2 fail.
- Patched modules, identical test files: 13 pass, 0 fail, 0 skipped.
- Fresh-directory repetition: 13 pass, 0 fail. This is a repeat, not 26 unique tests
  and not an independent security verifier or a sandbox-isolation proof.
- `node --check` passed for both edited modules and the new test file.

The two red-to-green cases mutate a pending query component and a later batch
component. Other regressions retain pre-transport rejection of invalid/over-budget
inventory and non-disclosure of caller-only fields. Existing nine adapter tests
are unchanged, including explicit consent, response bounds and fail-closed pagination.

## Invariants and limits

No live OSV request, raw shell, CCC1 grant, owner-key operation, model training,
main/release modification, signing, installation or deployment. Component limits,
HTTP limits, timeouts and pagination behavior remain unchanged.

Not verified: full security suite, repository/backend/Android CI, deployed SBOM,
Fold behavior, authenticated owner-consent chain, cumulative network budgets or
hostile in-process getters/proxies. Existing returned reports remain shallowly
frozen; this patch fixes input snapshot aliasing, not every object trust boundary.

Next: rerun exact published-head scoped and full security regression; finish the
Android/Termux/CCC1 authority call graph before enabling any preview dispatch.
