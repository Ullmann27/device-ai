# Samsung-LiteRT-LM-Diagnose · 20.10.2026

Am Samsung wurde Qwen3-0.6B mit der LiteRT-LM-Engine geladen (UI zeigt
`MODELL ENTLADEN`), aber zwei anschließende Chatversuche endeten in
`Lokaler Modellfehler: Local generation cancelled.`. Dieser Text ist ein
vom Runtime-Event `LocalInferenceEvent.Cancelled` abgeleiteter Fehler;
er erklärt nicht, **wer** abgebrochen hat.

Um diesen Fehler reproduzierbar einzugrenzen, ist in der bestehenden
Experiment-APK-Quellcodebasis der manuelle Chatbefehl
`Continuum Modelltest` ergänzt. Er benötigt ein bereits geladenes
`.litertlm`-Modell, startet genau eine isolierte `streamThinking`-
Generierung ohne Tool-Definitionen und zeigt entweder generierten Text
oder einen begrenzten Fehlerbericht mit Laufzeit. Er schreibt weder
persönliches Gedächtnis noch Trainingsgewichte und autorisiert keine
Geräteaktionen. Der normale Chat bleibt unverändert.

## Interpretation der Ergebnisse

- **Modelltest erfolgreich, normaler Chat scheitert:** native
  Textgenerierung ist grundsätzlich funktional; anschließend gezielt
  ChatReasoningRouter, Tool-Conversation, Zeitlimits und Cancel-Races
  untersuchen.
- **Modelltest ebenfalls `Cancelled`:** Native LiteRT-LM-Inferenz,
  Modellausführung, Gerätelaufzeit, Thread-/Callback-Rennen und
  Paketversion prüfen; ohne Logcat kein eindeutiger Grund.
- **Modelltest meldet kein geladenes Modell:** Modellstatus und
  Modell-Lifecycle kontrollieren.

Dieser neue Befehl steht **erst nach einem neuen APK-Build und der
Installation dieser Codeversion** zur Verfügung. Er beweist allein
noch keine generative Gerätefunktion. Alle übrigen Sicherheitsschranken
und der Draft-PR-Isolationsvertrag bleiben unverändert.
