# Critical worker-descendant finding and correction

## Reproduced gap

At Chief's requested final counterexample review, source
`0c54a7bc33bc3979f59a1b4d1bcd9e5b4e39a115` exposed an incomplete resource boundary:

1. `coding-provider.mjs` invokes a separate local-brain CLI with `spawnSync`.
2. That CLI calls `launchLocalModel`, which launches detached `prlimit`/`bwrap`
   with `--die-with-parent`.
3. Killing the outer service worker does not kill that still-live intermediate
   CLI. The sandbox's parent-death protection is tied to the immediate CLI.
4. The service cleared its worker identity after the outer process exited and
   could dispatch a different queued task. JavaScript `finally` cleanup in the
   OCI executor likewise cannot run after a worker receives SIGKILL.

The actual launcher was exercised with a harmless compiled C program that only
printed a readiness marker and slept. A transparent spawn observer retained the
real launcher arguments and process behavior. **This is explicitly
`CONTRACT_TEST_PROCESS_TOPOLOGY`, not an admitted model or neural result.**
After the outer worker died, the intermediate CLI and sandbox were both still
alive. Killing the immediate CLI then killed the sandbox. All fixture processes
were cleaned up. See `descendant-red/topology.log` and `topology.exit`.

A separate actual service test used the genuine approved runtime's private
exact-hash executable copy, removed before task submission. Native inference
was therefore impossible. After SIGKILL of its first dispatched worker, the
service dispatched the second queued task as well. That task acquired its own
worker identity and changed to `UNKNOWN` instead of remaining `PENDING`.

**RED: 1 failed test, 0 passed, 0 skipped; 8.335 seconds.** Raw process observations
and the assertion are retained in `descendant-red/`. The topology fixture and
service regression are separate proofs; they are not represented as one genuine
model job or as evidence that a real model was deliberately orphaned.

## Minimal correction and independent check

Builder source `d84689868631466640893c9ee9a0374db857a8b8` includes the durable
per-UID execution-uncertainty barrier introduced by `07ed91e5` and corrected by
`3b3f51d8`. An intent is persisted before worker GO. An unexpected worker exit
retains the barrier, records the task as `UNKNOWN`, and ends that scheduler
session. A later service cannot claim that process death proves descendant or
container teardown. Cancellation does not silently remove this uncertainty.

The independent regression required the next task to remain `PENDING`, with no
new worker or dispatch timestamp. It then restored the private runtime bytes
and required restart to refuse with reconciliation required.

**GREEN on exact `d84689868631466640893c9ee9a0374db857a8b8`: 1 passed test,
0 failed, 0 skipped; 9.314 seconds.** Tested harness: `23d14102`. Raw logs are
in `descendant-green/`. No model ran. The subsequent harness commit `c1e99f2f`
adapts older orphan scenarios to this stricter contract; it is supplied to the
separate final integrated gate rather than claimed executed here.

## Explicit fixture cleanup and remaining boundary

The test operator removed **only this synthetic fixture's** barrier after matching
its state directory, task, instance, PID and process-start identity; observing
both worker and daemon dead; confirming that the second task never dispatched;
and retaining the proof that the private executable was absent before submission.
The `TEST_OPERATOR_RECONCILED_ZERO_EXECUTION_FIXTURE` event records this action.
This was not a product recovery path and does not justify clearing a real unknown
execution. At cleanup, no service, worker, harmless-payload process or global
barrier from this reviewer remained.

Real uncertain native/container work still requires separate owner/operator
reconciliation with actual teardown evidence. No automatic force-cleanup or
unchecked replay feature was introduced. Genuine neural service benchmark S01,
the complete integrated suite, ARM64 and Fold7 are not certified by this check.

Closing mode: Chief relayed the owner's 20% remaining-budget notice. No further
model experiment or service run was started by this reviewer after the targeted
GREEN. Direct quota visibility remains `NOT_VISIBLE`.
