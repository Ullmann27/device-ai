# Final QA gVisor fixture repair

Base Chief87839335, branch codex/momega1-gvisor-fixture-20261103.
Only `.github/continuum/isolated-executor-gvisor.test.mjs` changes outside this
new evidence directory. Production executor/schema is unchanged.

The fake Docker CLI previously returned exit0 and empty stdout for unformatted
container inspect. The executor now consumes the complete inspect JSON and
validates observed confinement, so two old adapter tests failed with
ISOLATION_OBSERVATION_FAILED. The old runtime-only error assertion also needed
the current ISOLATION_OBSERVED_POLICY_MISMATCH code.

The fake now records actual run arguments and derives Config.User, image,
HostConfig runtime/network/read-only/resources/capabilities/security and bind
mount RW status from those arguments. Synthetic malformed JSON, wrong network
and writable-bind observations all fail closed while container/staging cleanup
remains asserted. Existing hardening/no-fallback/runtime/teardown checks remain;
new success checks also pin the complete observed policy and post-removal inspect.

`original-two-failures-645b2215.tap` preserves both exact failing blocks from
QA's original full Node log. `manifest.json` pins that whole raw log and shows
the test blob was identical between645b2215 and87839335. `focused-red.tap` records
an unchanged-base reproduction: 4tests,2PASS/2FAIL/0SKIP. `focused-green.tap`
records the fixed focused suite: 5tests,5PASS/0FAIL/0SKIP.

Command: `node --test .github/continuum/isolated-executor-gvisor.test.mjs`.
No Docker daemon, actual container, runsc, model or Android execution occurs in
this fake-CLI suite. Evidence class SYNTHETIC_ADAPTER_CONTRACT; it is not gVisor
runtime proof. No heavy QA was started. Root/QA own independent patch review
and the existing combined QA run. Rollback: revert this test-only patch.
