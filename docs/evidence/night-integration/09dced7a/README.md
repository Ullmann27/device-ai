# Initial combined-source QA: 09dced7a

These are actual initial results, retained even after correcting the test harness.

- Full Node: **2305 total, 2267 PASS, 15 FAIL, 23 SKIP**, source unchanged, process exit1 and termination confirmed. The raw JUnit contains every case. One source assertion expected a bare break after a 250ms wait; actual code now checks the output budget first. Fourteen OS-success proofs could not run on this host: trusted-tool ownership and the actual bwrap network namespace are unavailable. These original failures are not relabeled as successful native proofs.
- Android: **2501 total, 2484 PASS, 0 FAIL, 17 SKIP**. Product/test sources under app/ remained byte-identical to this commit. Subsequent working changes during the run were confined to Node test corrections. Three skipped deterministic parity cases will be enabled with prepared Node/Kotlin fixtures for the final run; the other skips remain explicit.
- Configured Android NewApi lint: **0 issues**.
- Backend: **64 PASS, 0 FAIL, 0 SKIP**, complete JUnit and process exit0.

The shared app directory identity and added regression case results are recorded in android-summary.json. These records do not prove physical Fold7, native Filament appearance, an accepted S01 run or a working ChatGPT connection. No native model was run.
