# Bot Genesis independent UI resumption

Baseline4b881f81ddfbe38729a551d9e460e818c6b3b715 (production c7ec853e2ffaa488b92826306f015bfaa67f5bb7). New review branch codex/genesis-ui-review-20261103. Original UI review de153623a and all raw compile/queue REDs retained by merge. Production unchanged by reviewer.

The host is now actually usable: pwd/git succeeded. First exclusive heavy window belongs to this reviewer; no simultaneous model build/inference. Goal is to execute the repaired407 roster against the unchanged acceptance intent before new Bot Genesis UI work.

Disclosed, planned adaptations (not blind tests): the former olderLoad case expected two admitted reads, and lateSave expected a replacement read to queue behind a write. The accepted407 implementation intentionally refuses excess work with a visible busy/retry message. Assert at most one admitted operation, no stale callback altering the replacement window, and successful fresh reload only after explicit retry. An already accepted save must finish on its selected stable ID despite navigation. Preserve the16-open/bound1 counterexample byte-for-byte. Original13-case result remains12/13RED; do not retroactively relabel it.

Add two independent controls for the new public close lifecycle: close during an accepted save must not cancel or lose the selected-ID write or revive a window, and show after Activity destruction must admit no work. These are disclosed source-informed regressions, not unseen intelligence tasks.

Run exact-source Gradle/Robolectric with before/after hashes and raw XML/logs; no production edits. Then release CPU for the native builder. Full final combined tests are a separate later freeze. Main/signature/release/owner data untouched; Fold7 and real GPU NOT_RUN.
