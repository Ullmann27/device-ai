# Independent service review handoff

Baseline `527d43f93f6b1837bd8da38cfefcfc84a84fd464`. Acceptance is frozen in
`FROZEN-CRITERIA.md` before candidate/API inspection. This branch owns only review
tools, synthetic fixture tests and evidence. Production service and MCP code are
owned by other sessions.

Current: actual host lifecycle/admission accepted in the scope documented by
`RESULT.md`; no production implementation changes were made here.

Final critical source: `d84689868631466640893c9ee9a0374db857a8b8`. Unexpected
worker death now preserves a durable resource barrier instead of starting another
task while descendant teardown is unknown. Independently reproduced RED on
0c54a7bc and targeted GREEN on d8468986 are in `descendant-red/` and
`descendant-green/`; see `DESCENDANT-FINDING.md`.

Executed: baseline scheduler 22/22; fd51d648 CLI lifecycle 8/8; a588d361 full
lifecycle plus active faults 10/10; 0c54a7bc active/global admission 4/4. Two setup
errors from a concurrently dirty builder checkout are preserved explicitly as
BLOCKED. Native inference was impossible in fault fixtures because only a
private exact-hash copy of the runtime executable was removed before submission.

Final test harness `c1e99f2fcf1f29f46addf4465cd3e519b0ea7203` contains thirteen
current cases. Chief reported final integrated source
`8fd58d00c53c6c2257a102922243f413afb6bac1`; the separate P0 reviewer owns its
remaining bounded final gate. This reviewer is stopped after securing evidence.

Next authorized session: run both test files on the exact final
clean source (`13` cases), serialized against any other per-UID service. Use the
reduced real profile bridge classpath. Then retain separate genuine local model,
independent verifier/report/restart, full combined QA and ARM64/Fold7 evidence.
No successful local coding result may be inferred from these lifecycle tests.

The genuine neural service benchmark S01 remains `NOT_RUN` in closing mode.
Direct quota reading is `NOT_VISIBLE`; Chief relayed the owner's 20% notice.
No automatic resume is scheduled. Synthetic fixture barrier cleanup matched the
exact state/task/instance and dead processes; no arbitrary real barrier was
deleted. No reviewer service/native process remains from this work.

No new runtime/model downloads, cloud calls, installation, signing, main merge,
release or real-owner-data mutation are permitted by this reviewer task.
