# Worker descendant stop checkpoint (2026-10-29)

Status: WIP / UNVERIFIED. Runtime execution environment unavailable; every new process regression is NOT_RUN.

Baseline: PR #216, b47f4fac043b2124a9ef4dd1cfb469a3fde88bc6. Own branch: codex/worker-descendant-stop-20261029. Existing untracked-file refusal and failed-cycle exit behavior are retained. Relevant experiments: EXP-01 and EXP-08; no experiment completion or device result is claimed.

## Smallest review object

The native health worker is a read-only test runner, separate from the existing CCC1 task journal/backlog/repair path. Its execute() sends group SIGTERM and schedules group SIGKILL after 500 ms, but close currently cancels that timer. A test descendant with ignored stdio can survive SIGTERM while the Node runner closes, allowing release of the checkout lease with a running old descendant.

The two added process regressions start a real SIGTERM-resistant descendant, record its Linux/Termux PID/start/boot identity, request SIGTERM or wait for timeout, require it to have stopped and then run a fresh healthy cycle. Zombie states count as stopped because their code cannot execute. Tests clean up their own matching PID in finally. Test-first source is published before the proposed worker change; neither RED nor GREEN has been executed.

## Proposed minimum change

Keep and await the existing 500 ms group escalation after leader close before resolving execute(), writing the completion receipt and releasing the lease. This is the same existing worker and lease, with no second queue, daemon, remote shell, generative model, paid service or authority expansion.

## Required verification

In an owner-reviewed isolated clean Linux/Termux checkout, with Node/Git/flock and /proc identity support:

```sh
node --test .github/continuum/local-development-worker.test.mjs .github/continuum/local-worker-process.test.mjs
```

Run the new cases against the baseline worker to observe the expected retained running descendant, then against the patch. Follow with the current full Node regression. Capture exact source SHA, TAP, exit codes and process cleanup evidence outside the checkout. Node tests / SDK / Android / APK / device: NOT_RUN here.

## Safety and continuation

Test subprocesses are not an OS sandbox. An arbitrary Termux UID can reach secrets owned by that UID; process cleanup does not protect the signing P12. No main writes, owner signing, APK installation, model/weight changes or existing CCC1 receipt changes. Detached descendants that leave the process group remain outside this small fix.

Next: implement the minimum escalation wait, review the exact diff, preserve WIP/NOT_RUN status, then execute actual regressions when a supported environment exists. Rollback: drop this isolated branch's commits; baseline files remain available at b47f4fac043b2124a9ef4dd1cfb469a3fde88bc6.
