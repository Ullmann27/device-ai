# Independent service review handoff

Baseline `527d43f93f6b1837bd8da38cfefcfc84a84fd464`. Acceptance is frozen in
`FROZEN-CRITERIA.md` before candidate/API inspection. This branch owns only review
tools, synthetic fixture tests and evidence. Production service and MCP code are
owned by other sessions.

Current: criteria frozen; candidate/service execution `NOT_RUN`.

Next: share the frozen contract, inspect the submitted API/source, implement a
transparent executable adapter and run real-process start/stop/queue/crash cases.
Record all failures without changing the frozen expectations. Then independently
check the exact final integrated source and report remaining gaps to Chief.

No new runtime/model downloads, cloud calls, installation, signing, main merge,
release or real-owner-data mutation are permitted by this reviewer task.
