# Native context/correction result

The unchanged preregistered 24 cases ran against the existing actual
`LocalConversationContext` and the new research bridge on JVM. Forty original
source files were compiled, with the app-pinned OkHttp and official Android API
implementation dependencies. No API stand-ins or rewritten context engine were
used. The Android encrypted-store factory compiled too, but Android Keystore
execution, UI behavior and Fold7 were not run.

| Separate scope | Existing context API A | Context adapter B |
| --- | ---: | ---: |
| Automatic correction/context handling | 4/10 | 8/10 |
| Human-confirmed transcript/meaning replacement | 0/3 | 3/3 |
| Review integrity checks | 0/5 | 5/5 |
| Input bounds/integrity checks | 0/2 | 2/2 |
| Text preservation controls | 4/4 | 4/4 |
| Total component checks | 8/24 | 22/24 |

There is no regression among the eight cases the original API passes. The
aggregate 22/24 is **not** an 91.7% speech-understanding or real-app accuracy
claim: it includes review and bounds features absent from A. The improvement
uses deterministic canonicalization, an owner-context snapshot and explicit
human confirmation. No LM1 or Gemma weight change contributes to these results.

## Concrete outcomes

- `Nein, so habe ich das nicht gemeint. Ich meinte den Zweig Sand.`:
  A leaves the text unchanged without a scope directive; B returns corrected
  scope `Zweig Sand` and retains the previous owner-only context in its result.
- The same longer correction with `den anderen Entwicklungszweig`:
  B returns the clarification decision `CLARIFY`, without selecting a target.
- Confirmed `Öffne Bilder.` -> `Öffne Downloads.` preserves the review's explicit
  distinction between transcription and meaning correction. This is a supplied
  human repair, not independently learned speech recognition.
- Stale/wrong-source/wrong-session/unconfirmed repairs are rejected. Negative
  and multipart instructions in preservation controls remain verbatim; their
  subsequent semantic execution is not measured by this test.

Two expected scope corrections still fail and were not patched after evaluation:

1. `Nein, das war anders gemeint. Ich bezog mich auf den Zweig Sand.`
2. `Na, so hab i des ned gmeint. I man den Zweig Sand.`

Both return `PASS_THROUGH` rather than the expected updated scope. The present
canonicalizer recognizes a narrow discourse pattern. General paraphrase or
Austrian-language understanding is not established by this adapter.

For all 24 cases, each possible advisory model label 0/1/2/3 leaves the
non-advisory result identical: 96 bounded invariance probes, no tool grant, no
training start. These are policy tests, not a C pipeline with actual new model
inference. Full app A/B/C, microphone/ASR, Gemma and Fold7 remain NOT_RUN.

## Actual execution and source integrity

Run exit 0; wall 0.5164 s; CPU user 0.6492 s, system 0.1245 s; child peak RSS
52,088 KiB. JVM parallel housekeeping can make aggregate CPU exceed wall time.
Per-case A/B timings use different setup scopes and are not a speed comparison.
All compiled source snapshots and frozen tests remained unchanged.

Initial compile attempts are retained: missing real dependencies/transitive
sources, then one command construction error; attempt 4 compiled successfully.
The pre-execution hash guard then detected a concurrent change to the unused
store source and refused evaluation. Attempt 5 compiled immutable copies of
all actual sources including the corrected store, then ran the original cases
once. No component outcome was viewed before this successful execution, and no
case/gold label was changed. `component-results-v1/` contains source/dependency
and class hashes, exact commands, all compile logs and raw case results.

The bridge/adapter identity is from root remote commit
`d0e5c192035ae35bf499ce605ce788e218c8c65f`; the separately refined store identity
is bound by its full source hash in the compilation manifest. Do not infer
acceptance of future source edits from this receipt.

## Reproduce after research integration

Use `run_context.py --help`. Supply the integrated research checkout, Kotlin
2.4.0 home, and a dependency directory containing the exact jars named and
hashed in the compile registration. The script snapshots actual source bytes,
refuses changed frozen tests, preserves failures and uses a fresh output
directory. It does not collect audio, contact a cloud model, invoke tools or
train weights. A repeat uses consumed tests and is replication, not a new blind
generalization experiment.
