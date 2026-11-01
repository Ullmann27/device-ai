# Sol314 independent review evidence

Candidate:8c40d111c557e9ebb0d19f807a098c1f5d196966. Original source baseline:97b5b9006f8c60f1a6fc4fb10981894330c55fe4. No binaries are included. All facts and conversations in the corpora are fictional.

V1 was frozen before its initial independent execution:8/50 baseline ->33/50 uncommitted naive variant. Once disclosed for debugging it became a consumed regression set. Final8c40d111 replay:45/50. V2 was frozen before the final candidate and executed exactly once against immutable snapshots:6/40 ->37/40. Its expectations were never changed. Further replays are consumed validation, not a new independent TEST.

V1 SHA-256:e2f343c6771add3f71bc1a0fffa6e7bc0438b0ad24b6cff9412fc75b6c8ed2f2
V2 SHA-256:2bf6acb02afc33a92422994d590b1289c187ee85ffc55a3148e057b5571ad8c0

`results/final/final-metrics.json` contains all50/all40 and declared-query-grammar subsets, Recall@3, Precision@3 with fixed denominator3, MRR, exact evidence sets, separate abstention counts, paired wins/losses, relevant evidence losses, query latency and failure text. Citation precision on actually returned hits is also reported. Evidence retrieval metrics use answerable cases; missing cases use abstention accuracy.

Final v2:18/20 exact answerable evidence sets and19/20 abstentions; declared query grammar35/36 (18/19 answerable,17/17 abstentions). Reverse ownership wording is conservatively unsupported; historical and future forms remain outside the stated improvement. No scoped wrong-holder citation appeared. Exact-set wins31/losses0 do not mean perfect recall: reverse ownership loses previously retrievable supporting evidence. V1 replay also loses reverse-ownership and explicit-new-object ellipsis evidence by abstaining.

Raw rank returns candidates for two unsupported follow-ups where dialogue correctly abstains. Core reconstruction runs only when grounded; no leaked citation occurred in these cases. This bundle does not verify Android/UI/core integration.

Final host reply v2 P50/P95 baseline2.358/27.563ms ->candidate4.836/21.581ms; v1 consumed replay2.455/16.273ms ->3.1865/39.319ms. These single-pass small-sample JVM values include synthetic history I/O and are not device timings.

Resource output uses a real FileLearningExperienceStore append/read round-trip within its512KiB/256-record limits. FinalResourceProbe includes both numeric identifiers (outside bounded name grammar) and alphabetic names (inside grammar). At256 alphabetic short facts, baseline rankP50/P95 12.889624/28.864750ms ->13.234473/24.911399ms; final selects the target alone. At256 alphabetic long facts,92.698215/112.391519ms ->97.239686/113.031073ms; all facts exceed512 characters, so the scoped reader returns no evidence by design. Do not describe this latter case as successful long-fact retrieval. Each final resource scenario runs one correctness query plus two warmups and seven timed queries.

The rejected naive variant had a severe full-ledger parsing regression; legal-store results are retained in results/v1-initial. Its initial working tree was never committed. The recovered source/diff matches the source SHA recorded before repairs exactly:814d165141b17193c2f4859224a77771ca0ea1bd9ceddac0dd93dedf76c577d7. This is hash-verified reconstruction, not a historical Git commit. Binary hashes in the provenance JSON identify the measured snapshots but no class files are packaged.

To reproduce after compiling baseline and candidate production sources, provide Kotlin compiler/dependency classpath and stdlib path, then call tools/run-corpus.sh with corpus, expected hash, class directories and a fresh output directory. That script labels any replay as consumed. Build classes from the exact source SHAs; tools/compile-snapshot.sh exports only the needed production sources from an existing repository into a scratch output directory. Kotlin compiler dependencies and org.json are external requirements. No signing, owner files, release or source checkout is changed.

This evidence concerns pure host Kotlin retrieval/dialogue and bounded quotation selection only. It does not demonstrate better CDX1 neural generation, Android Gradle/Lint, APK publication, Fold7 behavior or independently verified factual answers.
