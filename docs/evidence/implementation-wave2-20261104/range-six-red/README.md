# Actual callback regression: negative control

Production baseline: local 2558c029021af6ad399910478c56c0c75ec506c5, identical app tree to PR #506. Six newly added tests compile against the original production implementation and fail by assertion: 6 tests, 6 failures, 0 errors, 0 skipped. This is an intentional negative control, not a passing acceptance result.

The real Activity-created TextToSpeech engine and installed UtteranceProgressListener are used under host Robolectric. Accepted request fields and PCM/callback input are synthetic. PCM has two different measured levels (.5 then .25) so a replayed anchor is observable. No audible output, physical Android device or native GPU acceptance is claimed. The exact test source, test XML, source hash manifest, Gradle output and invocation are preserved alongside this note.
