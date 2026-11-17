# CODE 51 – Voice prosody session recovery (2026-11-09)

## Scope and origin
Continuation of the user's interrupted GitHub Copilot graphics/voice session, deliberately on an isolated branch rather than overwriting #659 or #663.
- Repository: `Ullmann27/device-ai`
- Base Code51 integration: `60549c74cfaf437472ab2b02b86066c3ec23cba9` (#663)
- Branch: `chatgpt/code51-voice-prosody-recovery-20261109`
- Draft PR: #664, targets `copilot/code51-master-integrator-20261108`
- Production file: `app/src/main/java/at/heinz/deviceai/cognitive/JarvisVoiceProfile.kt`
- Regression file: `app/src/test/java/at/heinz/deviceai/cognitive/JarvisVoiceProfileTest.kt`

## Reproduced failure and minimal change
Existing `boundary()` took the last `". "` marker without distinguishing a title abbreviation from an actual sentence end. Given the synthetic long utterance `Die erste Kontrolle ist abgeschlossen. Jetzt meldet sich Dr. Sommer ...` and an 80-character chunk cap, the old implementation emitted `Die erste Kontrolle ist abgeschlossen. Jetzt meldet sich Dr.` as the first chunk: it separated the title from the surname and lost the earlier natural sentence break. In another synthetic long sentence it ignored a comma pause and broke much later at an arbitrary word.

The new boundary preference is **sentence > clause > word > hard limit**; explicit common German titles and one-letter initials do not end a sentence when followed by a space. No speech model, network TTS, prompt policy, audio engine, profile persistence or model permissions changed. Existing markup/URL/code sanitization and bounded chunk count stay intact.

## Validation
- **RED, local source-extracted Kotlin reproduction:** `kotlinc-jvm 1.9.0` with JDK21. Previous function threw `IllegalStateException` at the `Dr.` split assertion.
- **GREEN, local source-extracted Kotlin reproduction:** the two regression sentences pass, combined chunks reconstruct the original clean spoken text, plus existing long-answer chunk-count/bounded-size and inline-code suppression assertions pass.
- **Checked-in JUnit tests:** two focused new tests with the exact same sentence and clause cases. Actual repository JUnit run **NOT_RUN** (no full repository toolchain present in this environment).
- **Android/Gradle/Filament/real TTS/Fold7/audio perception:** **NOT_RUN**. No claim that the voice now sounds human or that full integration QA passed.
- **CI/Code51:** existing #663 exact-source GitHub QA/unsigned APK and generic Android runs are independent. This PR must not inherit their PASS counts as proof for these edits.

## Next executor
1. Review exact two file diff and reproduce the new tests with a supported JDK/Kotlin toolchain, e.g. `./gradlew :app:testDebugUnitTest` on a provisioned authorized Android host.
2. Run the long-speech/TTS lifecycle suite and extended text/ASR negative cases.
3. If green, selectively integrate #664 into #663 **only after current Code51 QA has one frozen source SHA**. Do not merge into main.
4. Measure real de-AT TTS, speaking cadence, interruption/STOP, long answers and avatar lip synchronization on the Fold7. Synthetic boundary tests do not establish acoustic quality.

## Rollback and safety
Revert only this PR's production change and coupled regression tests. No personal files, keys, existing bot data or installed app changes. No APK, signing, workflow dispatch, release or device claim. Preserve original Code50 signature and source-bound owner approval for eventual Code51 release.
