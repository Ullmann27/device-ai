# Reviewer harness freeze

Driver/materializer/sampler adapted from existing PR413 QA at
10f4339a760f8a531993598200b1c3299d3b2b97, preserving actual canonical
prepareCodingTask → runCodingTask/default provider/ownerCodingExecutor calls.
Changes: new immutable g01–g03 fixtures; imports already canonical; initial recall
always off; inherited state rejected; production SHA pinned to fd743adc; per-case
exclusive reservation files, maximum two calls each/six total. Reservation files
are experiment bookkeeping, not production scheduling or authorization.

Initial g01/g02 preflight: assertion RED, zero calls/no grant. Initial g03 rejected
before grant because its frozen requirement exceeded existing acceptance-item size.
See setup-failure.json. Reviewer materializer now losslessly partitions unchanged
text at spaces; exact rejoin is asserted. Frozen bytes/tests/source are unchanged.
This adjustment occurred before any model invocation or generated candidate.

Native model and runtime independently rehashed; native-prerequisite.json.
Actual inspected Docker image: sha256:4838093b14e654fcf3b69decfe8b8a1fe75ef6c288ea5dadc4e169131716494c.
External synthetic-test config: concrete Node toolchain, 256 MiB, 64 PIDs, 1 CPU,
10 s, runc. Canonical executor validated that config and enforces network none,
read-only root/tools, no new privileges and no writable host mount/socket in guest.
Native process retains existing separate caps. Sampler matches actual binary inode
at 200 ms; sampled peaks can miss transients. No Fold7 or energy claim.
Quota NOT_VISIBLE. Inference NOT_RUN at this checkpoint.
