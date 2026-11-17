# Independent workspace review: frozen criteria

Frozen before candidate implementation/results inspection. Reviewer base ca93fb4f7a17ff7a2a13debd93197ba485594f06; target 4665302f6c967c1bfb1e13c9acf01fad4e7eec20. Experiments EXP-01/02/07/08/11/12. Research host review only, FOLD7_NOT_RUN; real model unavailable. No feature edits, owner data, paid resources, deployment, signatures, installation or main merge.

Acceptance criteria:
1. Labels, request descriptions, model prose and self-reported status cannot authorize execution. Canonical CCC1 approval MAC, journal, source SHA, job and request identity must bind execution.
2. Missing, invalid, unknown, stale, replayed, revoked and mismatched authorization fail closed before work. Crash/uncertain state cannot silently replay.
3. Real Docker execution uses immutable/readonly source snapshots, inaccessible peer workspaces and host files, no host/network access, and fixed allowlisted profiles with no arbitrary shell.
4. Runtime enforces CPU, RAM, PID and deadline bounds, drops privilege/capabilities, and limits output. Inspect actual container configuration and test observable confinement without image pulls.
5. Cancellation and timeout terminate actual work and remove containers; teardown failures cannot be reported as verified success. Concurrent executions have distinct identities and cannot cross-write.
6. Existing ChiefWorkerPool and AgentMailbox receive typed bounded results without a second scheduler or mailbox database. Mailbox full, duplicate result, unknown/stale status and wrong request/job/source bindings cannot produce positive completion.
7. Existing Kotlin pool/mailbox tests and focused adapter tests compile against real dependencies, under QA-owned build scheduling. Host Kotlin is not Android/Fold7 execution.
8. Documentation reports actual count of simultaneously observed containers (prior claim two), shared 4 CPU / 16 GiB host capacity, no ten VMs/Android instances or virtual GPU claim.

P0: authorization/host isolation escape. P1: execution replay, wrong task accepted, false success, missing resource/cleanup barrier. P2: bounded availability or evidence/documentation defect. Record raw logs and exact commands/exits; distinguish test harness/setup errors from product defects. Independent findings request changes before integration when P0/P1 remain.

Review currently NOT_RUN. Rollback: omit these review-only files; no productive state changes.

## Repair review supplement, frozen before repair inspection

The author proposes requiring an existing canonical owner REPAIR claim. Independently test: no direct/unclaimed execution; wrong backlog path/id/token/source/chain fail; valid current lease permits required fixed profiles; stale/expired/replaced claims fail; OWNER abort/MAC corruption/terminal journal after claim fail; expired goal bounds every profile; no task replay after terminal completion; fresh view never itself grants execution. Repeated profiles under the same current claim are necessary for canonical RED/GREEN/independent verification and are not a replay by themselves. Require actual canonical repair positive regression, not only mocks. Mailbox delivery must bind typed generation identity and cannot derive authority from summary prose.
