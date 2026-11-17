# Read-only serial Node triage

All 634 compared Node source/test/package files match c362 and 5b93 exactly. Both full runs use Node24.22.1 and the same ordered354files. This does not establish identical runtime conditions. Complete serial result remains2455PASS/6FAIL/37SKIP.

- Typed observations: `.map` fails before type/printed-output safety assertions. Normal outputs are arrays; missing child responses return a skipped object. Actual skipped reason/process receipt was not captured, so timeout versus probe failure is unknown.
- CLI index: retained matching fixture records canonLoose/canonStrict as non-runnable with reason timeout, and caches those results; no near pair can then be printed. External-path/export-count/cluster assertions had already passed. Retained path is matched by content and execution time, not a unique path emitted by JUnit; preserve this limit of attribution. Why the child reached its budget is unproven.
- MCP overlap: fixture sleeps10ms while the first reader has a40ms timeout. Entry and continued overlap are not guaranteed. After timeout cleanup, a later request may correctly receive200; the actual interleaving/body/timing is absent. Production budget bypass is not proved. Separate barrier-based concurrency fixture is a follow-up; current candidate unchanged.
- TIMEOUT descendant: five-second marker wait fails before process-live, termination and restart assertions. No failed termination or already-reported worker TIMEOUT is proved by that failure.
- Detached child: output did not parse as a valid PID; output and process receipt are absent. A surviving child is not proved.
- Older unreadable process: first assertion proves r.ok=false. Inner terminationConfirmed assertion is not reached. Outer complete-run terminationConfirmed=true is a separate fact.

The original two process files passed37/37 unchanged in isolation, but both full variants fail. Four old process failures pass in the newer full run, one repeats and two other process cases fail. No common load, permission or cleanup cause is inferred. No timeout, production guard or expected result was relaxed.
