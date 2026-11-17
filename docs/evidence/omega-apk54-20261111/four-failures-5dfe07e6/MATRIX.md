# Code54 `5dfe07e64` – Vier-Fehler-Matrix (Stand 12.11.2026)

Ausgangspunkt: volle, ungefilterte Android-Suite auf `5dfe07e64` als **root**, **ohne Locale**: 4857 / 4836 PASS / 4 FAIL / 17 SKIP
(`../android-full-5dfe07e6/`). Nachprüfung jeder FAIL-Klasse **vollständig** (ganze Klasse, gleiche Assertions, keine Teständerung).

| # | Test | Ursache (bestätigt?) | Nachprüfung | Ergebnis |
|---|---|---|---|---|
| A1 | `PdfIntakeNativeDecodeProbeUiTest.actualProviderReadRendersAndExtractsSyntheticPdf` | Host-JDK ohne `FileDescriptor.getOwnerId$()` (Android-libcore) | Android-Laufzeit nötig. Host hat **kein `/dev/kvm`**, APK ist nur `arm64-v8a` → kein brauchbarer Emulator | **BLOCKIERT / NOT_RUN** – Gate nicht erfüllt |
| A2 | `ShellExecutionTest.successfulGermanOutputRoundTripsThroughStrictVersionedWire` | **bestätigt: Host ohne UTF-8-Locale** | `LANG=LC_ALL=C.UTF-8`, als root und als Nicht-root | **11/11 PASS** (beide Läufe) |
| A3 | `ContinuumBotRosterStorageUiTest.realReadonlyDirectoryFailure…` | **bestätigt: Ausführung als root** (schreibgeschützt gilt nicht für uid 0) | Benutzer `qa`, uid 30033, eigener Worktree `5dfe07e64`, eigene Gradle-Home-Kopie, `C.UTF-8` | **7/7 PASS** |
| A4 | `ChiefBotStorageBoundaryTest.failedFilesystemWriteRetainsRegistryAndSafeDiagnostic` | **bestätigt: Ausführung als root** | wie A3 | **11/11 PASS** |

Umgebung Nicht-root-Lauf: `setpriv --reuid=qa --regid=qa --init-groups`, `GRADLE_USER_HOME=/home/qa/.gradle` (Kopie inkl. `init.d/central-mirror.gradle`),
`./gradlew --no-daemon -q -Pkotlin.compiler.execution.strategy=in-process -PcontinuumUiWidgetTests=true :app:testDebugUnitTest --tests …`. JUnit-XML: `nonroot-utf8-junit.tar.gz`.

**Folge:** Von den 4 FAIL sind 3 als Umgebungsfehler bestätigt und unter geeigneter Umgebung grün. Offen bleibt nur A1 (nativer PDF-Test auf Android).
Das formale Full-Gate (`run-android-phase.py --phase full` verlangt genau einen SUCCESS von A1) ist **nicht erfüllt**.
