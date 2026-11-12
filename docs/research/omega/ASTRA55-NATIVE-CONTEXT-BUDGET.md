# Code55: vollständiger nativer Kontextvertrag

Status: SOURCE_INTEGRATED, Host- und Geräteprüfung beim Integrator separat belegen.
Betroffen: EXP-05/12. Keine Gewichte, ToolGate-Rechte oder Modellpromotion geändert.

## Tatsächlich geprüfte SDK-Schnittstelle

Die im Projekt verwendete `litertlm-android:0.18.0`-AAR/API-JAR wurde lokal mit
`javap` gelesen. Öffentliche API vorhanden:

- `EngineConfig.maxNumTokens`
- `ConversationConfig.prefillPrefaceOnInit`
- `Conversation.getTokenCount()` für bereits vorhandenen KV-Cache
- `Conversation.renderMessageIntoString(Message)`
- `Conversation.renderPrefaceIntoString()`

Keine öffentliche `countTokens(text)`-Methode in dieser gebundenen Kotlin-Version.
Eine solche Methode wurde nicht erfunden oder per Reflection auf private Handles
aufgerufen. Neuere C++-Quellen mit `CountTokens` beweisen keine Verfügbarkeit im
verwendeten Android-SDK.

## Bisheriger Fehler

`getTokenCount()` vor dem ersten Prefill zählt System-Anweisung und Toolschemas
noch nicht. Die frühere `allowsSubmission`-Funktion übersprang die vollständige
Kapazitätsprüfung zusätzlich bei frischem Chat und isoliertem Denken. Das statische
15.500-Zeichen-Limit ist keine Tokenmessung. Modell-Metadaten zur maximalen
Kontextlänge sind außerdem nicht identisch mit der tatsächlich konfigurierten
KV-Kapazität des Engines.

## Reparatur

Die Engine wird ausdrücklich mit maximal 4.096 Tokens konfiguriert; eine kleinere
bekannte Modellgrenze hat Vorrang. Es wird kein größerer 16K-Kontext mit erhöhtem
Speicher-/Thermikbedarf still aktiviert. Bei unbekannten Modell-Metadaten ist 4096
die ausdrücklich angeforderte Konfiguration, deren Initialisierung gelingen muss.

Der ohnehin asynchrone Load erstellt die erste Konversation mit
`prefillPrefaceOnInit=true` und merkt sich deren echte native Prefix-Tokenzahl.
Das betrifft dieselbe feste System-Anweisung und denselben registrierten
Toolschema-Katalog wie spätere frische Chats. Deren Erstellung bleibt ohne
zusätzliches Prefill unter dem Runtime-Lock. Noch leere frische KV-Caches erhalten
die zuvor gemessene Prefixzahl; beim wiederverwendeten vorgefüllten Start-Cache
wird sie nicht doppelt addiert. Fortsetzungen müssen ihren tatsächlichen,
kumulierten KV-Zähler liefern.

Der neue Input wird über das echte SDK-Chattemplate gerendert. Seine UTF-8-Länge
und die UTF-8-Länge nach NFKC-Normalisierung bilden eine konservative Schätzung,
keinen behaupteten exakten Tokenizer. Ausgabe-Maximum und zusätzliche
Framingreserve zählen bei jedem Aufruf mit. Isoliertes Denken besitzt keine
Tools und budgetiert seinen tatsächlich gerenderten Prefix ebenfalls konservativ.
Unbekannte Render-/Count-Ergebnisse oder ein zu großes Gesamtbudget führen vor
`sendMessageAsync` zu `LOCAL_MODEL_CONTEXT_UNVERIFIED_OR_FULL`.

Die ursprüngliche Besitzeranfrage wird nicht abgeschnitten. Das optionale
`LocalModelRuntime.maxPromptUtf8Bytes` erlaubt dem bestehenden PromptFrame-Aufrufer,
vorher weniger optionale History/Advisory aufzunehmen. Die Runtime prüft trotzdem
den vollständigen tatsächlichen Input. Die Thermal-Guard-Delegation gibt diese
Eigenschaft unverändert weiter. Ein Budget von null ist ein echter Engpass zwischen
Prefix und reservierter Ausgabe; es rechtfertigt kein stilles Entfernen von
Sicherheitsvorgaben oder Schema-Teilen. Kleinere Promptpakete können konservativ
abgelehnt werden, obwohl sie bei exakter Tokenisierung passen würden.

## Grenzen und Prüfung

Neue Negativtests: fehlende Prefixzahl bei KV=0, keine Doppelzählung vorgefüllter
Konversationen, kumulierte Fortsetzungen, vollständige Templatebytes,
Ausgabereserve, Unicode-Expansion, unbekannte Kapazität und Zahlenüberlauf.
`LocalToolContinuationTest` bestätigt, dass frische Chats die Prüfung nicht
umgehen können. Native Modellinferenz, tatsächliche Prefix-Tokens, verbleibendes
Promptbudget und Load-/Antwortlatenz auf dem Fold7: NOT_RUN.

Die Prefill-Messung wird einmal beim Modellladen ausgeführt. Ein Fehler bei der
Prefixmessung schließt die erzeugte Konversation vor der Engine; nicht sicher
freigegebene Handles bleiben für den vorhandenen expliziten Unload erhalten.

Primärquellen:

- https://github.com/google-ai-edge/LiteRT-LM/blob/main/kotlin/java/com/google/ai/edge/litertlm/Conversation.kt
- https://github.com/google-ai-edge/LiteRT-LM/blob/main/kotlin/java/com/google/ai/edge/litertlm/Config.kt
- https://github.com/google-ai-edge/LiteRT-LM/blob/main/runtime/conversation/conversation_test.cc

Die API-Verfügbarkeit wurde am tatsächlich gebundenen 0.18.0-Artefakt geprüft;
die verlinkten aktuellen Quellen erklären die Semantik, ersetzen aber keinen
Gerätelauf dieser SDK-Version.
