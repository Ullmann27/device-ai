# Astra Code55: frisches Live-Bild vor jeder Eingabe

Basis: `2711ef9c63`. Produktionsfix: `dc233c844533f193577dc9b11b8dac052904a994`.
Ziel: Code55-Integrationslinie, PR #799. Integrator: Astra.

`OwnerComputerSessionController.checkDispatchActive` prüft unmittelbar vor jeder
nativen Klick-, Text- oder Scroll-Eingabe die bestehende Aufgaben-/Freigabebindung
und zusätzlich den aktuell abgefragten Monitorstatus. Dieser muss LIVE sein und
zur gleichen taskId sowie captureSession gehören. Ein zuvor einmal frisches Bild
reicht nicht aus. Das anfängliche Warten auf das erste echte Bild bleibt bestehen.

Die vorhandenen nativen permissionCheck-Aufrufe verwenden diese Prüfung. Keine
zusätzlichen Freigaben, Wiederholungen, Eingabearten oder Hintergrundaufträge.

`LiveComputerDispatchGuardTest`: 7/7 echte JVM-Prüffälle bestanden. Produktionscode
und tatsächlicher `LatestLiveFrameHolder` wurden mit Kotlin 2.4.0 neu übersetzt;
unveränderte Abhängigkeiten stammen aus dem echten Integrationsbuild. Geprüft:
frisches Bild, später veraltetes Bild, kein Bild, erstes Bild noch ausstehend,
Stopp/Widerruf, verdeckte/pausierte Quelle sowie falsche Sitzung und falscher Auftrag.
Kein UI-Simulator, kein paralleler Gradle-Job, kein Gerätetest.

PORTIERT: Produktions- und Testfolgecommit. VERIFIZIERT: sieben JVM-Fälle.
GERÄTEBESTÄTIGT: nein. Die kombinierte Android-Suite muss beide Folgecommits
einschließen. Rücknahme bei Regression: beide zusammengehörigen Commits zurücknehmen.
