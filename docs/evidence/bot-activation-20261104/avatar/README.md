# Iris: bestehende Avatar-Regeln auf dem Host geprüft

## Ergebnis

57 vorhandene JUnit-Tests bestanden; Kotlin-Kompilierung und sämtliche sieben JUnit-Läufe endeten mit Exitcode 0. Kein Produktionsfehler reproduziert, keine Produktionsdatei verändert. Die vor und nach dem Lauf berechneten SHA-256-Werte sämtlicher ausgewählter Produktions- und Testquellen stimmen überein.

- Geprüfter Quellstand: `e101e393dd6c80803ac9d9ac91fccd9812c4a18d`.
- Ausführungscheckout: `000d413bd5745694d5ff6a8e9e6046af5b591db3`; ausgewählte Quellen sind zum geprüften Quellstand unverändert.
- Umgebung: OpenJDK 21.0.13, Kotlin 2.0.20, JUnit 4.13.2, vorhandenes JSON-JAR 20240303.
- Direkter Compiler-/JUnit-Aufruf mit echten Produktionsklassen und vorhandenen Tests. Kein Gradle-Build, Download oder Android-Stub.

| Vorhandene Testklasse | Bestanden |
| --- | ---: |
| AvatarDirectorTest | 4 |
| AvatarPortedPartsTest | 3 |
| AvatarSpeechOrderingRegressionTest | 10 |
| AvatarObservedGazeTest | 12 |
| AvatarCameraPolicyTest | 17 |
| LipSyncFaceSourceTest | 9 |
| MotionLibraryTest | 2 |
| Gesamt | 57 |

## Zuordnung zum vorbereiteten Auftrag

Auftrag `owner-bot-avatar-stop-20261104`, Domänenprofil `bot-iris`, Fingerprint `1c184881ab6b1e3f44f0ea61e5a65c801cef7a472a2b09ddba81860fa721f529`.

Status bleibt `VALIDATED_NOT_SUBMITTED`. Die Dienstrollen des vorgesehenen vorhandenen Ablaufs sind FORGE / LINT / ECHO. Dieses Dokument ist ein separater Host-Prüfbericht des Helferagenten; es ist keine Antwort von Iris, keine CCC1-Freigabe, kein Zustellbeleg und keine vom Botdienst ausgestellte Reparaturquittung.

| Annahmekriterium | Tatsächlich ausgeführte vorhandene Prüfung |
| --- | --- |
| Beendete oder fremde Sprachsignale werden verworfen. | `AvatarDirectorTest.speechProducesStartVisemesLevelsAndEndForTheCurrentUtteranceOnly`; `AvatarSpeechOrderingRegressionTest.endedSpeechCannotRestartFromQueuedAudioOrVisemes` |
| Verspätete Audiosignale dürfen einen geleerten Sprachzustand nicht wieder öffnen. | `AvatarSpeechOrderingRegressionTest.pauseClearsAudioOrderingWithoutAdmittingLateCallbacks` prüft Audio und Viseme nach `clearSpeech()` sowie einen anschließend erlaubten neuen Sprachvorgang. |
| Ungültige und veraltete Blicksignale werden abgewiesen. | `AvatarObservedGazeTest` prüft Alter, Konfidenz, NaN/Unendlich, Grenzen, Reihenfolge, Ablauf, Freigabe-/Aufgabenpriorität und alte Signale nach Löschung. `AvatarCameraPolicyTest` prüft vier Zulassungsbedingungen, Widerruf, alte Generationen, veraltete Aufnahmezeit und Ausfall des Frame-Heartbeats. |
| Arbeitssignale zeigen den vorhandenen Avatar-Zustand. | `AvatarDirectorTest.runtimeFlagsMapToOneStateWithPriorityAndNoDuplicates` prüft Busy → Thinking, Listening, Speaking, WaitingForApproval und Idle mit Priorität und ohne doppelte Zustandsevents. |

Präzise Abbruchgrenze: Der vorhandene SpeechOrdering-Endetest verwendet `SpeechEnded(..., interrupted = false)`; der Pausentest ruft den produktiven Abbruchbaustein `clearSpeech()` direkt auf. Die gelesene Produktionslogik behandelt beide Werte des `interrupted`-Flags gleich: `AvatarDirector.speechEnded` löscht die aktive Utterance und der Reducer ruft bei passender Utterance `clearSpeech()` auf. Dieser Lauf ist daher keine zusätzliche End-to-End-Prüfung eines Android-Abbruch-Callbacks mit `interrupted = true`.

## Belegte vorhandene Schnittstellen

- `ChiefBotProfileStore.kt`: Iris ist das Profil `bot-iris`, Rolle „Avatar und Animation“; Profile allein bestätigen keine Fähigkeiten und starten keine Ausführung.
- `ContinuumBotRosterDialog.kt:157` sowie `AgentActivity.kt:1867`: vorhandene Team-/Adressierungswege initialisieren die 20 Profile. Die UI kennzeichnet registrierte Profile ausdrücklich ohne laufenden Auftrag.
- `ChiefBotHandoff.kt`: Übergabe an einen Workspace-Job benötigt aktuelle kanonische Auftragsidentität, Ablaufzeit, Budget, zulässige Werkzeuge, Grant-/Replay-Prüfung und getrennten Builder/Verifier. Ein Aufgabenplan oder Profil ersetzt diese Autorität nicht.
- `AvatarDirector.kt:21`: vorhandene Laufzeitsignale; `:39`: Blickprüfung; `:52`, `:61`, `:73`, `:86`: bestehende Sprachereignisse.
- `AvatarContract.kt:91`, `:128`, `:145`: produktiver Reducer, Leeren von Sprache und zeitabhängige Pose.

## Grenzen

Kein Liveprofil, Modell, Worker-Service, Queue oder Reparaturauftrag wurde gestartet. Es gab null Modellaufrufe. Keine Kamera oder anderer Gerätesensor wurde verwendet. Keine Aussage über Android-UI, Filament/GPU, Hardware-Audio, Akkulast, thermisches Verhalten oder die sichtbare Qualität auf dem Fold7. Keine Videos, Installation, Signierung oder Main-Merge. Die alternative reine `AvatarPoseBridge.applyTo`-Funktion wurde durch den vorhandenen Motion-Test mitgeprüft, aber nicht neu angeschlossen; die parallel bearbeitete Sprachverdrahtung wird nicht dupliziert.

## Reproduktion und Rohbelege

`run_existing_avatar_tests.py` enthält den vollständigen lokalen Lauf. `compile.command.json` und die klassenweisen `*.command.json` halten die ausgeführten Argumente fest. `compile.log`, die sieben Klassenlogs und `result.json` enthalten die beobachteten Resultate, Quellhashes, Zeitstempel und Prüfumgebung. Das Unterverzeichnis `classes/` ist ein regenerierbares Compiler-Zwischenergebnis und muss nicht archiviert werden.
