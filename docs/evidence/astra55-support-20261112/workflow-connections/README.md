# Workflow→Avatar/Audio · Verbindungsnachweis

Quellcommit: `c19e7ea837c7a3c9e5d7404906102c335ae2a374`. Vertragsbasis: `627f2b3b593737b3f6659524baaa1b7ba29329ea`.
`source-binding.json` bestätigt alle 27 eingefrorenen Quell-/Test-/Deklarationsdateien identisch zum Commit.

| Prüfstufe | Nachbartests | Regressionen | Compile |
|---|---:|---:|---|
| baseline | 49 PASS | 8 PASS / 4 FAIL | PASS |
| repaired | 49 PASS | 12 PASS | PASS |
| current-contract | 60 PASS | 12 PASS | PASS |

Die Ausgangsfehler sind alte Beobachtungsrevision, geerbte Schrittbestätigung,
verspätete Pause eines anderen Schritts und fehlender Avatarimpuls des zweiten Auftrags.
Die Baseline verwendet Produktionsquellen aus `d7a59c6f68` plus neue Testfälle; deren exakte Hashes stehen
in ihrem Manifest. Der erste Reparaturcheckpoint ist `dc59e4b791`.
Für eine Baseline-Reproduktion in einer isolierten Kopie dieses Checkpoints nur CockpitWorkEvent.kt und
ComputerWorkAvatarBridge.kt auf ihren d7a59c6f68-Inhalt zurücksetzen; der damalige Runner ist im Checkpoint enthalten.

Die neue Pipelineprüfung führt OwnerComputerWorkflow, VerifiedNodeAction, LiveActionCockpitAdapter,
CockpitAnimationBus, ComputerWorkAvatarBridge, AvatarDirector/AvatarPoseReducer und ComputerWorkCuePlayer aus.
Der Geräteport liefert synthetische NodeActionFrames; Android-Capture und echte Hardware werden nicht simuliert bestätigt.
Sie prüft VERIFIED-Ankunft im gebundenen Reducer, reale Bewegung aus einem Dispatch, Endzustand,
Stop während einer noch ausstehenden Nachbeobachtung, ausbleibenden verspäteten Erfolg und Ton,
sowie Neu-Bindung mit verspäteten alten Ereignissen.

Kotlin 2.0.20 / JUnit 4.13.2, 512 MB, ein Prozessor, kein Gradle und kein Netzwerk.
Originalquellen; sechs reine Typdeklarationen unverändert aus den Android-gekoppelten Originaldateien extrahiert.
Ein anfänglich unvollständiger Compiler-Dateisatz ist als compile-setup-attempt erhalten;
er wurde um die vorhandene AvatarSentenceAccentPolicy ergänzt, bevor die oben aufgeführten Tests liefen.

`tools/astra55-workflow-connections/run.py --help` beschreibt die drei lokalen Pfadargumente.
Die Textdateien sind unveränderte Compiler-/JUnit-Ausgaben. Keine Assertions entfernt oder abgeschwächt.
Android/Gradle/GPU/APK: NOT_RUN. Fold7: NOT_RUN. LOCAL_ONLY / NOT_PUSHED.
