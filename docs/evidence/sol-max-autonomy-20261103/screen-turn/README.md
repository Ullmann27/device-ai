# Bildschirm-Turn – RED → GREEN

Beide letzten Läufe: echte Android-Gradle-Kompilierung/JUnit auf Linux x86_64,
JDK17, Android SDK36, Gradle8.11.1, Kotlin2.4, reale LiteRT-API-Abhängigkeit.
Die HostChannelAdapter simulieren **nur** die native Enginegrenze; keine Gewichte,
JNI-Inferenz oder Android-Fenster wurden ausgeführt.

Ausgangs-SHA steht in `screen-red-base.sha`. `screen-red.patch` ist der exakte
unverhaltensändernd extrahierte Produktionszweig plus die neuen Tests; auf diesen
SHA anwenden, um die Reproduktion wiederherzustellen. Das ist keine Behauptung,
dass im alten Commit schon ein testbarer submit-Helfer existierte.

Befehl: `bash run-focused-gradle.sh` (enthaltene absolute SDK-/Workspacepfade an
den eigenen Host anpassen; Quellcode/Parameter/Testfilter unverändert). Der
Init-Filter wählt ausschließlich LocalToolContinuationTest, re-executed die
Suite und ändert keine QA-Produktionsschranke.

Der erste CLI-Versuch mit `--tests` wurde vom vorhandenen taskNames-QA-Gate schon
in der Konfiguration abgewiesen (`screen-command-rejected.log`). Keine Tests
liefen; das ist **kein** RED-Beleg. Danach wurde der erlaubte Init-Filter benutzt.

`screen-red.log`/XML: 12 ausgeführt, 1 Fehler im neuen Bildschirmtest
`freshScreenTurnDoesNotEncodeAnUnusedOversizedToolResponse`,
IllegalStateException LOCAL_TOOL_CONTEXT_EXCEEDS_BUDGET; Exit1.
Der Test verwendet einen zulässigen CONTINUUM_SCREEN_DATA_V1-Snapshot,
ScreenObservationPrompt (SHA/Grant/Ablauf), vollständiges JSON und
LocalPromptFrame, bevor der tatsächliche Produktionshelfer aufgerufen wird.

GREEN: nur den forceFresh-Zweig vor das lazy Tool-Encoding legen. Echte
Fortsetzungen bleiben größenbegrenzt und verwenden den nativen Toolkanal.
`screen-green.log`/XML: 12 ausgeführt, 12 PASS, 0 Fehler/Skips; Exit0.
Vier neue Kontrollen: validierter Bildschirmframe, übergroße echte Fortsetzung,
kleine mehrsprachige Toolantwort, leere Beobachtungsliste. Acht alte Fälle bleiben.

Vollständige Gesamtsuiten sind durch diesen fokussierten Lauf **nicht** belegt;
werden getrennt nach Source-Freeze protokolliert. Keine Gerätefreigabe.
