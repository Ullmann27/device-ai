# Code54-Kandidat `5dfe07e64` – volle, ungefilterte Android-Suite (HOST)

- Quelle `5dfe07e64` (Integrationszweig, enthält den Fix `M3EvalGapsTest`), sauber, keine Filter, `sourceUnchanged: true`, 51 min.
- **Ergebnis: 4857 Fälle – 4836 PASS, 4 FAIL, 17 SKIP.** Gegenüber `f0120e1b2` ist `M3EvalGapsTest` jetzt grün; kein neuer Fehler.

| FAIL | Einordnung | Nachprüfung |
|---|---|---|
| `PdfIntakeNativeDecodeProbeUiTest.actualProviderReadRendersAndExtractsSyntheticPdf` | Host-Grenze: `FileDescriptor.getOwnerId$()` fehlt im JDK | braucht Android-Laufzeit (Emulator/Fold7) – NOT_RUN |
| `ShellExecutionTest.successfulGermanOutputRoundTripsThroughStrictVersionedWire` | **Umgebung bestätigt**: keine UTF-8-Locale | mit `LANG=C.UTF-8`: **11/11 PASS** (`envcheck-utf8.log.gz`) |
| `ContinuumBotRosterStorageUiTest.realReadonlyDirectoryFailure…` | Umgebung vermutet: Ausführung als root (schreibgeschützte Verzeichnisse sind für root schreibbar) | auch mit UTF-8 rot; Lauf als Nicht-root hier nicht eingerichtet – offen |
| `ChiefBotStorageBoundaryTest.failedFilesystemWriteRetainsRegistryAndSafeDiagnostic` | dto. („Expected a real read-only-directory write failure“) | dto. – offen |

17 SKIP unverändert (`ChiefPatternE2ETest` 6, `FirstThinkingRunE2ETest` 6, `ChiefWorkspaceWiringHostTest` 2, `ContinuumContrastiveParityTest` 2, `Cre1InferenceEncoderTest` 1).

## Gesamtstand Kandidat `5dfe07e64`
UI-Pflichtlauf 25/25 · NewApi-Lint ohne Befund · volle Suite 4836/4/17 (alle 4 Host/Umgebung) · APK54 unsigniert PASS,
SHA-256 `7f463d4785466302aa188b8e81b5e75f32393654cf1ebde16c06c1845649127c`. **Nicht signiert, nicht installiert.**
Das Gate in `harness/run-android-phase.py --phase full` verlangt zusätzlich einen SUCCESS des strikten nativen PDF-Tests –
auf diesem Host unmöglich; daher ist das formale Full-Gate hier **nicht** erfüllt. Nächster Schritt: Besitzerentscheidung
(SHA-genaue Signierfreigabe für `5dfe07e64` bzw. nativer PDF-Test auf Emulator/Fold7), dann Fold7-Abnahme.
