# CSX1 – ehrliche Enthaltung und Generalisierung (Opus, 22.10.2026)

Experiment-IDs EXP-04/05/06 (Shadow-Forschung). Branch `opus/continuum-csx1-abstention-20261022`, gestapelt auf PR #61
(`chatgpt/continuum-residual-pointer-built-20261022`, Basis `21365a4e2cf1e0a8fa101e069fa4e019d36143a6`). Kein Merge nach `main`,
keine Promotion, SWB1/CDX1 und Gerätecheckpoints unberührt. Dieses Dokument wird mit jedem Zyklus fortgeschrieben.

## Umgebung
Linux x86_64-Container, OpenJDK 21.0.12.1, `kotlinc-jvm 2.0.20` (offizielle Maven-Central-Artefakte, Aufbau siehe
`docs/CONTINUUM-OPUS-HANDOFF-20261022.md` auf PR #57), JUnit 4.13.2. **Android-SDK/Gradle-Android-Build: `UNAVAILABLE`**
(Host `dl.google.com` per Netzwerkrichtlinie gesperrt). Kein APK, kein Samsung-Test.

## Gate 1 – exakte Reproduktion von PR #61 (`21365a4`)

```sh
cd app/src
kotlinc main/java/at/heinz/deviceai/learning/span/ContinuumResidualSpanCore.kt \
        main/java/at/heinz/deviceai/learning/span/ContinuumCounterfactualCurriculum.kt \
        test/java/at/heinz/deviceai/learning/span/ContinuumResidualSpanCoreChecks.kt \
        test/java/at/heinz/deviceai/learning/span/ContinuumResidualSpanCoreTest.kt \
        -cp junit-4.13.2.jar:hamcrest-core-1.3.jar -jvm-target 17 -include-runtime -d csx1.jar     # exit 0
java -cp csx1.jar at.heinz.deviceai.learning.span.ContinuumResidualSpanCoreChecks                   # exit 0
java -cp csx1.jar:junit-4.13.2.jar:hamcrest-core-1.3.jar org.junit.runner.JUnitCore \
     at.heinz.deviceai.learning.span.ContinuumResidualSpanCoreTest                                  # OK (1 test), exit 0
```

Tatsächliche Ausgabe (identisch zur früheren Prototypmessung):
`PASS residual gradient checks=38 maximumError=1.5443709852624998E-10`, alle Schutzprüfungen PASS,
`300 steps, loss 1.2136851176488221 -> 0.6067638047117935`, DEV 2/5 → 3/5; `Salzburg`, `XZ-902`, `Zange` richtig;
**beide unbeantwortbaren Fragen falsch beantwortet** (`Salzburg`, `XZ-902` statt Enthaltung).
Gradle-JUnit über die Android-Testvariante: `UNAVAILABLE` (kein SDK).
