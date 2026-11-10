# Integrated Presenter result

Root's actual Android/Robolectric run at `fac24859486959434529f4817c676dfdec9cfa00` passed **19/19** selected tests: all 11 Presenter event/ownership tests plus 8 existing window lifecycle tests. Tested application main subtree: `46e8fbd3cf913f11c2d150830e89502f41bc5528`.

The preceding integrated run at `bb4da2a128b2220d095625d49673032df1e1f6c1` passed 99 of 110; all 11 failures were at the fixture's active-window precondition, before event assertions. Settling/focusing the legacy window alone did not fix it. Root then enabled native graphics and a paused 16 ms Choreographer clock. The original active-window assertion remains, and every subsequent behavior assertion now passed. No production guards were weakened.

The application main subtree is identical across those runs. Together they cover **110 distinct tests**, not 118; the eight existing window tests were repeated. This is root's integrated source result, not a full Android result on this isolated review branch.

Root fixture commit `fac2485` is cherry-picked here as `f2a6ee4`. Both owned production files and both test files compare byte-for-byte identical with the actual tested integrated source; hashes are in `integrated-presenter/handoff.json`. The full branches differ because root contains the other workers' changes. `integrated-presenter/capture-run.json`, the two JUnit XML files and `gradle-run.txt` preserve the actual source-bound result; no new build was run to sync this handoff.

The initial `receipt.json` and sampler logs remain the historical 12-test controlled host regression. They do not include this later Android result. Root owns integration through PR715. Fold7/GPU/Filament, real model inference, signing and installation remain NOT_RUN.
