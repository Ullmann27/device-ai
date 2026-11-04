# Pinned #512 -> #513 application-source delta

Base f9267a3743a87a77bf6421011c014b2ca2d6b0d6; head 28b228698aef8c52e46c72b71fdd5e1915d6e76c. GitHub compare: ahead 4, behind 0, merge base equals base. This is a complete app-source change list (5 production, 13 tests), not a list of every historical evidence document.

| Repository path | Added | Deleted |
| --- | ---: | ---: |
| app/src/main/java/at/heinz/deviceai/AgentActivity.kt | 4 | 3 |
| app/src/main/java/at/heinz/deviceai/avatar/AvatarContract.kt | 20 | 2 |
| app/src/main/java/at/heinz/deviceai/avatar/AvatarDirector.kt | 58 | 9 |
| app/src/main/java/at/heinz/deviceai/avatar3d/AvatarIdleMotion.kt | 79 | 0 |
| app/src/main/java/at/heinz/deviceai/avatar3d/AvatarMorphMapper.kt | 19 | 17 |
| app/src/test/java/at/heinz/deviceai/avatar/AvatarDirectorTest.kt | 4 | 4 |
| app/src/test/java/at/heinz/deviceai/avatar/AvatarSpeechAdmissionTest.kt | 77 | 0 |
| app/src/test/java/at/heinz/deviceai/avatar/AvatarSpeechBoundaryTest.kt | 122 | 0 |
| app/src/test/java/at/heinz/deviceai/avatar/AvatarSpeechClockTest.kt | 58 | 0 |
| app/src/test/java/at/heinz/deviceai/avatar/AvatarSpeechOrderingRegressionTest.kt | 3 | 2 |
| app/src/test/java/at/heinz/deviceai/avatar/AvatarSpeechReattachmentTest.kt | 105 | 0 |
| app/src/test/java/at/heinz/deviceai/avatar3d/Avatar3dContractTest.kt | 4 | 1 |
| app/src/test/java/at/heinz/deviceai/avatar3d/AvatarArticulationBoundaryTest.kt | 73 | 0 |
| app/src/test/java/at/heinz/deviceai/avatar3d/AvatarMorphTransitionTest.kt | 15 | 19 |
| app/src/test/java/at/heinz/deviceai/motion/LipSyncFaceSourceTest.kt | 13 | 3 |
| app/src/uiTest/java/at/heinz/deviceai/AvatarRangeFrameConnectionUiTest.kt | 190 | 0 |
| app/src/uiTest/java/at/heinz/deviceai/AvatarSpeechReattachmentUiTest.kt | 248 | 0 |
| app/src/uiTest/java/at/heinz/deviceai/AvatarTtsLiveConnectionUiTest.kt | 7 | 7 |

Non-app tool additions: tools/fable-avatar-motion-check/Main.kt (213 lines), README.md (54), run.sh (33). The remaining comparison entries are inherited documentation and execution receipts under docs/. The full pinned comparison remains reproducible at https://github.com/Ullmann27/device-ai/compare/f9267a3743a87a77bf6421011c014b2ca2d6b0d6...28b228698aef8c52e46c72b71fdd5e1915d6e76c .

The supplied historical #513 dda8a3cf5b07cf32d878f9b3309490fdd892091d is not substituted for the newly completed integration.

## Our additional delta against selected #513

- Production: only avatar3d/AvatarIdleMotion.kt, exact blob 31801578f47f6a677c58283a408bc2840297de6d from #495 9ef9bd16848fea1fcc6f9c9832ad65c5fc05fb7f; five net added lines normalize signed zero.
- New tests: AvatarCycleBoundaryCases.kt (host executed), AvatarCycleBoundaryTest.kt (JUnit wrapper NOT_RUN), ContinuumBotDraftPreservationUiTest.kt (two Android acceptance cases NOT_RUN).
- Own handoff and evidence only otherwise. AgentActivity and all packaged models/assets remain exactly as #513; #518 is not imported.
