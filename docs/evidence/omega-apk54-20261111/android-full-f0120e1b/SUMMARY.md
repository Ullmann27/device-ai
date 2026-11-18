# Code54 – volle, ungefilterte Android-Suite auf `f0120e1b2` (HOST)

- Quelle `f0120e1b231efc31237a0a13aa59f9cbcc3c490a`, sauber; keine Include-/Exclude-Filter; `sourceUnchanged: true`.
- Kommando wie `harness/run-android-phase.py --phase full` (Init `full-journal.init.gradle`, `test-memory.gradle`, versionCode 54), Laufzeit 52 min, JDK 21, als **root**, **ohne Locale** (`LANG` leer).
- **Ergebnis: 4857 Fälle – 4835 PASS, 5 FAIL, 17 SKIP.**

| FAIL | Einordnung | Beleg/Begründung |
|---|---|---|
| `M3EvalGapsTest.naturalReadOnlyDevicePhrasingsRoute` (Z. 23) | **Echte Code54-Regression** aus `e20ca3a56` | „Prüf nicht das WLAN, sondern den Akku“ → null. Ursache: `OwnerDeviceRequestSafety.readOnlyText` verbindet Teilsätze mit `". "`, `carryContrastVerb` kreuzt keinen Punkt. **Behoben in `5dfe07e64`** (Routing-/Sicherheitsumfeld 14 Klassen / 85 Tests / 0 FAIL). |
| `PdfIntakeNativeDecodeProbeUiTest.actualProviderReadRendersAndExtractsSyntheticPdf` | Host-Grenze (bekannt) | `NoSuchMethodError FileDescriptor.getOwnerId$()` – braucht Android-Laufzeit; bleibt streng FAIL. |
| `ContinuumBotRosterStorageUiTest.realReadonlyDirectoryFailure…` | Umgebung (vermutet: uid 0) | Test erwartet einen echten Schreibfehler in einem schreibgeschützten Verzeichnis; root darf schreiben. Nicht als Nicht-root nachgeprüft. |
| `ChiefBotStorageBoundaryTest.failedFilesystemWriteRetainsRegistryAndSafeDiagnostic` | Umgebung (vermutet: uid 0) | Meldung „Expected a real read-only-directory write failure“. Nicht als Nicht-root nachgeprüft. |
| `ShellExecutionTest.successfulGermanOutputRoundTripsThroughStrictVersionedWire` | Umgebung (vermutet: keine UTF-8-Locale) | `Grüße vom Gerät` → `Gr??e vom Ger?t`; `LANG` leer. Nachprüfung mit `LANG=C.UTF-8` ausstehend. |

17 SKIP: `ChiefPatternE2ETest` 6, `FirstThinkingRunE2ETest` 6, `ChiefWorkspaceWiringHostTest` 2, `ContinuumContrastiveParityTest` 2, `Cre1InferenceEncoderTest` 1 (`skips.json`).

Folge: Der Code54-Kandidat wird auf `5dfe07e64` neu gebaut und neu geprüft; der APK `ae99a1db…` aus `f0120e1b2` ist damit überholt.
