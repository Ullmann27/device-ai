# Independent quotation-selector review

Frozen12-case corpus SHA-256:2843d83946c9e2fec2fbb42826c410667ef77d72455b5d400c6569fc8406e40b. First execution against the supplied linear selector:11/12 pass. The source snapshot is uncommitted; its before/after SHA was84fca0adf6e4a30f4643b647967d5a8fb788eca0e3c0e7a13c8f4235aaef2498. Binary provenance is recorded but no binaries are included. The set is consumed after disclosure of the failure.

The failure returns a partial overlong token even though no complete matching anchor fits maxChars80. REVIEW.txt records the requirement and integration findings. Passed cases check exact source/snippet hashes, source substring and UTF8 offsets, NFC/NFD, emoji/surrogate boundaries, distinct-term coverage and input bounds. No quotation establishes a factual answer or owner authority.

Reproduce this historical reviewed source snapshot using an external Kotlin compiler/dependency classpath and stdlib:

    java -cp "$CONTINUUM_KOTLIN_COMPILER_CP" org.jetbrains.kotlin.cli.jvm.K2JVMCompiler -no-stdlib -no-reflect -jvm-target 17 -classpath "$CONTINUUM_KOTLIN_STDLIB" -d /path/to/fresh-output source-before.kt FrozenWindowDataset.kt IndependentWindowReview.kt
    java -cp "/path/to/fresh-output:$CONTINUUM_KOTLIN_STDLIB" at.heinz.deviceai.learning.IndependentWindowReviewKt

Replays are consumed validation, not fresh independent TEST. FrozenWindowDataset.kt is a mechanically encoded representation of corpus.json; freeze_corpus.py retains the original authorship generator and is not needed to replay.

The linear selector resource probe uses at most6000 UTF16 source chars and512 question chars.21 measured calls after4 warmups: dense-one P50/P95 2.928339/6.721287ms; dense24 terms2.224382/4.050083ms; sparse1.420917/1.793995ms; no overlap1.297023/2.097050ms. Host JVM only. Android/Gradle/Render integration remains unverified in this independent review.
