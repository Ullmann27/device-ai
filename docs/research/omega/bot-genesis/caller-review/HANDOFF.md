# Caller review handoff

- Branch: `codex/genesis-caller-review-20261103`
- Draft PR: https://github.com/Ullmann27/device-ai/pull/450
- Criteria: `c09ef5b721a3cbb66f962c232066680425ff1f88`
- Original source finding: `cb7ec5618532309743ab2565fe9151684650672a`
- Assigned source: `7473ffb6584b70b1023b1156fca823e2a76dff36`
- Composed source: `66fce742c89236e7d5b78ceb2ec29ed1f1cd7b44`
- Accepted repair source: `d112106b5ddad13c2f67faf8912ebbc4276d63fe`
- Disposition: `ACCEPTED_SOURCE_SLICE`, independent read-only inspection.

One source-demonstrated P1 was reported: delayed ASR/permission callbacks survived
private bot switching. Chief repaired it before review acceptance. Direct-address
history display and explicit model lifecycle invalidation were also inspected
after narrow follow-up changes. See `FINDINGS.md` and byte hashes in
`SOURCE-RECEIPT.json`.

Executed test count in this review: **0**. This is intentional source-audit scope;
the separate UI reviewer owns the existing widget/speech suite and additional
ASR shadow cases. No Gradle/native/model jobs or unknown MΩ task-body reads.
No production source edits by this reviewer. Quota: `NOT_VISIBLE`.

Remaining acceptance:

1. Run the separate ASR callback/permission cases on exact composed source.
2. Run final shared-source Android compilation/regressions with the Chief's CPU gate.
3. Exercise real on-device recognition/TTS/folding and model load/unload/recreation.
4. Measure native dispatch stalls and main-thread wait on controller/store fsync.
5. Keep persistence/dialogue evidence separate from model intelligence, coding
   execution, local repair and device-performance claims.

No release, signing, installation, Main merge or productive owner-data mutation
was performed. Findings concern only the private caller slice; other teams retain
their own evidence and responsibilities.
