# Focused source geometry execution

Not an Android/JUnit/GPU test. ConstantsOnly.kt substitutes only three theme constants and three rail constants read from the pinned production definitions. All six complete geometry classes and the check methods are production/test sources.

Local executed commands (baseline and candidate directories contain the six pinned source files):

```
kotlinc baseline/*.kt ConstantsOnly.kt GeometryProbe.kt -include-runtime -d baseline.jar
java -jar baseline.jar --dump
kotlinc candidate/*.kt ConstantsOnly.kt GeometryProbe.kt -include-runtime -d candidate.jar
java -jar candidate.jar --dump
kotlinc baseline/*.kt ConstantsOnly.kt ContinuumLandscapeTargetPrecisionChecks.kt PrecisionRunner.kt -include-runtime -d baseline-precision.jar
java -jar baseline-precision.jar
kotlinc candidate/*.kt ConstantsOnly.kt ContinuumLandscapeTargetPrecisionChecks.kt PrecisionRunner.kt -include-runtime -d candidate-precision.jar
java -jar candidate-precision.jar
```

All four compiles exit0. Baseline runs exit1; candidate runs exit0. Repeated precision runs give the committed raw logs. Tests overlap; do not add counts.

Reproduction from a checkout:

```
U=app/src/main/java/at/heinz/deviceai/ui
T=app/src/test/java/at/heinz/deviceai/ui
E=docs/evidence/g1-r6-landscape-20261104
kotlinc "$U/ContinuumHomeLayout.kt" "$U/ContinuumLandscapeCockpitLayout.kt" "$U/ContinuumReferenceLayout.kt" "$U/JarvisWindowProfile.kt" "$U/JarvisCockpitGrid.kt" "$U/JarvisShellLayout.kt" "$E/ConstantsOnly.kt" "$T/ContinuumLandscapeTargetPrecisionChecks.kt" "$E/PrecisionRunner.kt" -include-runtime -d /tmp/continuum-r6-precision.jar
java -jar /tmp/continuum-r6-precision.jar
```

The standalone source fixture must never be placed on the application's production source path. The original JUnit suite and full Android/lint checks remain unexecuted here.
