# C1-MEMORY-017A — statischer Einbaubeleg

**STATIC_SOURCE_MATCH_RUNTIME_PENDING**. Seit `cdea744bc431d56cead71dc6dcbfa287d1903aeb` ändern sich genau die drei vorgesehenen App-/Testpfade. Keine weitere App-Änderung.

Lokaler Kandidat: `57656c6a5287c37a8f4357dabe6d066e63aec235`. Veröffentlichung: `f13578bdab05279720fe4a3d6ae0126a208c8465`. Vollständiger Tree: `440f395289756ac6ec4f6c26f661c38895e24e12`. Das öffentliche Git-Commitobjekt wurde unabhängig gelesen und bestätigt denselben Tree.

| Pfad | Tatsächlicher Zielblob | Ergebnis |
| --- | --- | --- |
| `app/src/main/java/at/heinz/deviceai/cognitive/AiContextCompiler.kt` | `beb0f37d8de8965984bf38846b6479e0137d2340` | Intake identisch |
| `app/src/test/java/at/heinz/deviceai/cognitive/ContradictionBoundaryRegressionTest.kt` | `0c798951d4f19d0ac873330caf2c5b6ce6a29d99` | Intake identisch |
| `app/src/test/java/at/heinz/deviceai/cognitive/ContradictionBoundaryHoldoutTest.kt` | `8e0d89fee6ed05af7b32e4ca589c83fb008c5f8d` | Intake identisch |

Basis-/Zielblobs, SHA256 und Modi stimmen mit dem eingefrorenen Compiler017-Intake überein. Die beiden vollständigen Testdateien bleiben damit auch zu ihrem vor der Reparatur eingefrorenen Original identisch.

`MemoryEventSource.kt` bleibt unverändert bei `98e19b678a48b448738009db1d1322efd1e49440`; `AgentActivity.kt` bleibt unverändert bei `bc1a08495f56696b2f0a6613260beb92bb3dbc62`.

**Android-Ergebnis offen.** Der C1-Lauf `android-memory017a-01` ist als laufend gemeldet. Noch keine Log-/XML-Auswertung und kein Runtime-Ergebnis durch diesen Beleg. Die unabhängige Quellen-/Klassen-/Log-/XML-Gegenprüfung folgt nach der Abschlussmeldung.

Keine Repoänderung, eigene Ausführung, Build oder Lockbenutzung. Kein übertragener Gesamt-PASS und kein externer C2-ACK.
