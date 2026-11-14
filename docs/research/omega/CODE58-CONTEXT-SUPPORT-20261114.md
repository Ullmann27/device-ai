# Code58 – höfliche Anschlussfragen

Basis: PR #836, `1656443d137bb6787c2eabe69a3e91ff1b2275b2`.
Ziel: gemeinsame Code58-Supportintegration des Integrators, anschließend PR #836.
Experimente: EXP-14 (bestehender Kontextpfad), EXP-13 (flüchtiger Aufgabenbezug).

## Änderung

`LocalConversationFollowUpPolicy` erkennt vollständig passende höfliche Rückbezüge,
zum Beispiel „Kannst du sie kurz zusammenfassen?“ und „Was kannst du mir darüber sagen?“.
Der vorhandene `LocalFollowUpTaskFrame` übernimmt dadurch die früheren vollständigen
Nutzernachrichten in chronologischer Reihenfolge. Spätere Korrekturen behalten Vorrang.
Der bestehende AgentActivity-Verlaufsgate verlangt bei diesen Rückbezügen Kontext;
fehlender Kontext wird weder erfunden noch aus anderen Sitzungen übernommen.

Die Ergänzung erkennt ausschließlich Sprachfragen mit begrenzten Formulierungen.
Explizite neue Themen, Zitate, formelle Sie-Anrede, Geräteaktionen und eingebettete
Zusatzbefehle werden durch die neue Erkennung nicht als höfliche Rückbezüge eingestuft.
Bestehende Grenzen für Secrets, alte Gerätebefehle, Nachrichtengröße und Toolrechte bleiben erhalten.
Keine neue Gedächtnisablage, Modelländerung oder Rechteausweitung.

## Tatsächlich ausgeführt

- Basis plus neue Tests: **37 JUnit-Tests, 5 Fehler**. Alle fünf Fehler betreffen die fehlenden
  höflichen Rückbezüge einschließlich Aufgabenframe und Korrekturreihenfolge.
- Nach Reparatur: **37 JUnit-Tests, 0 Fehler**.
- Testklassen: `LocalPoliteFollowUpTest`, `LocalConversationFollowUpPolicyTest`,
  `LocalFollowUpTaskFrameTest`.
- Neue Abdeckung: 17 höfliche Formulierungen, 21 unabhängige/negative Formulierungen,
  Größen-/Steuerzeichenabwehr, vollständiger Frame, Korrekturreihenfolge, Geheimnisfilter,
  fehlender/unvollständiger Verlauf und unveränderte Toolfreigabegrenzen.
- `git diff --check`: PASS.

Host: Kotlin 2.2.0, JUnit 4.13.2 und Hamcrest 1.3. Die vollständigen Produktionsdateien
`LocalConversationFollowUpPolicy.kt`, `LocalFollowUpTaskFrame.kt`, `LocalChatPromptBudget.kt`,
`LocalDialogueContextLimits.kt` und `Deliberation.kt` wurden kompiliert. Als reine
Kompilierabhängigkeiten dienten wortgetreue Quellabschnitte für Klassifikation/Assessment
aus `ContinuumIntelligence.kt` (vor `ReasoningPassRole`) und das vollständige Enum
`ModelCapability` aus `MemoryEcology.kt`. Keine Ersatzimplementierungen oder Teststubs.
Der Hosttest ersetzt keinen vollständigen Android-Gradle-Build.

Für die komplette Android-Quellkette auf dem integrierten SHA:

```sh
./gradlew :app:testDebugUnitTest --tests 'at.heinz.deviceai.cognitive.LocalPoliteFollowUpTest' --tests 'at.heinz.deviceai.cognitive.LocalConversationFollowUpPolicyTest' --tests 'at.heinz.deviceai.cognitive.LocalFollowUpTaskFrameTest'
```

## Status und Rückfall

PORTIERT: vorhandene Policy und echte Frame-Aufrufer bleiben kanonisch.
VERIFIZIERT: die genannten Host-JUnit-Tests, kein vollständiger Android-Build auf diesem Teilzweig.
GERÄTEBESTÄTIGT: NOT_RUN; kein Fold7-Zugriff und kein lokaler Modelllauf in dieser Unterstützung.
Nächster Schritt: zusammengeführten SHA im bestehenden Code58-QA prüfen; danach auf dem
Gerät eine Erklärung, Korrektur und höfliche Anschlussfrage in derselben Sitzung prüfen.
Rollback: nur diesen Support-Commit revertieren; Datenformat und persistierte Zustände sind unverändert.
