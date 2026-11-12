# Code55: nativer Kontext und nutzbare Promptplanung

Status: SOURCE_INTEGRATED. Host- und Geräteprüfung separat belegen.
EXP-05/12. Keine Gewichte, ToolGate-Rechte oder Modellpromotion geändert.

## Verifizierte SDK-Grenzen

Die gebundene `litertlm-android:0.18.0`-AAR/API-JAR wurde mit `javap` geprüft.
Zusätzlich wurden die unten verlinkten Quellen des Tags `v0.18.0` gelesen.
Vorhanden sind `EngineConfig.maxNumTokens`, `ConversationConfig.initialMessages`,
`prefillPrefaceOnInit`, `Conversation.getTokenCount()`,
`renderMessageIntoString(Message)`, `renderPrefaceIntoString()` und der echte
`maxOutputToken`-Parameter von `sendMessageAsync`.

`getTokenCount()` zählt bestehenden KV-Cache, keinen noch ausstehenden Input.
Eine öffentliche `countTokens(text)`-Methode gibt es in dieser Kotlin-Version
nicht. Die C++-Option für getrenntes Anhängen/Vorbefüllen einer Conversation
ist hier ebenfalls nicht öffentlich durchgereicht. `maxOutputToken=0` bedeutet
keinen unterstützten Prefill-only-Modus. InitialMessages vorbefüllen wäre möglich,
aber ein zusätzliches Zählgespräch würde den gesamten Auftrag nochmals auf der
CPU vorbefüllen und könnte nicht transparent als dieselbe Conversation decodieren.
Dieser teure Hilfsweg und private JNI-Handles werden nicht verwendet.

## Fehler und korrigierte Umsetzung

Die frühere Schranke zählte System-Anweisung und Tools nicht. Eine erste Reparatur
mit UTF-8-Bytes gleich Tokens war wiederum zu streng: Bereits der feste Activity-
Body ohne Identität, Genome und Auftrag enthält 1.135 UTF-8-Bytes. Pflicht-Tail,
Identität und kompletter Auftrag kommen hinzu. 4.096 Kontexttokens minus 2.048
pauschaler Ausgabe und großem Toolschema-Prefix lassen dafür oft keinen Platz.
Außerdem enthält das native Renderresultat im frischen, nicht vorbefüllten
Gespräch bereits den Prefix; dessen blindes erneutes Addieren zählt ihn doppelt.

Die Engine fordert jetzt ausdrücklich höchstens 4.096 Kontexttokens an; eine
kleinere bekannte Modellgrenze hat Vorrang. Es wird kein 16K-Kontext aktiviert.
Beim asynchronen Laden wird ein Gespräch mit der tatsächlichen System-Anweisung
und allen Toolschemata vorbefüllt und sein echter Prefix-Zähler gemessen. Dieses
Messgespräch wird geschlossen. Das wiederverwendbare Startgespräch und spätere
frische Gespräche haben anschließend einen leeren KV-Cache.

**Frische Chats und isoliertes Denken:** Vor Übergabe muss der native KV-Zähler
null sein. Damit bekommt der native Prefill den vollständigen gerenderten Input
inklusive System-Anweisung/Tools in einem Schritt. In SDK 0.18 prüft dieser Schritt
die echte Zahl der tokenisierten Eingaben gegen das konfigurierte Maximum.
Die Runtime schneidet den Besitzerauftrag nicht ab und entscheidet den Fit nicht
anhand einer starren Byte=Token-Schätzung.

Für die Ausgabeplanung wird das Maximum aus vollständigem Rendertext/3 und
nativ gemessenem Prefix plus Nutztext/3 verwendet, mit 256 Tokens Planungsreserve.
**Drei UTF-8-Bytes je Token sind ausdrücklich eine Heuristik, keine Tokenmessung
und kein Beweis, dass ein Auftrag hineinpasst.** Die Heuristik darf frische,
vollständige Aufträge nicht pauschal blockieren. Bei engem geschätztem Budget
bleibt eine kleine Ausgabeplanung von 128 Tokens. Das Ergebnis wird dem SDK
wirklich als `maxOutputToken` übergeben; die Modellfamiliengrenze bleibt ein
zusätzliches Maximum. Beispiel im Hosttest: 3.300 Nutztextbytes, 9.000 vollständige
Templatebytes und 1.800 gemessene Prefixtokens ergeben 840 angeforderte
Ausgabetokens. Das ist keine Messung eines konkreten Fold7-Modells.

**Native Tool-Fortsetzungen:** Hier ist bereits KV belegt. Die allgemeine
0.18-Prefill-Prüfung betrachtet nur die neu eingehenden Tokens; eine allgemeine
Summenprüfung darf daraus nicht abgeleitet werden. Deshalb bleibt die Schranke
für Fortsetzungen konservativ: echter kumulierter KV-Zähler plus gesamte
UTF-8-/NFKC-Länge des gerenderten neuen Inputs, 256 Reserve und tatsächlich
begrenzte Ausgabe. Zu große Fortsetzungen werden vor JNI vollständig abgelehnt.
Diese Byte-Oberabschätzung ist weiterhin keine universelle exakte Tokenizer-
Messung und kann passende Fortsetzungen ablehnen. Ein neues vollständiges
Besitzerprompt darf im nächsten frischen Gespräch wieder normal starten.

Die Transportgrenze `maxPromptUtf8Bytes` bleibt 16.000 Bytes. Sie behauptet keine
exakte verbleibende Tokenkapazität und senkt die Activity-Pflichtpromptgrenze
nicht auf wenige hundert Bytes. Native Fehler für einen zu langen Input werden
auf `LOCAL_MODEL_CONTEXT_UNVERIFIED_OR_FULL` abgebildet. Ein terminaler nativer
KV-Zähler am oder über dem konfigurierten Maximum gilt ebenfalls als unvollständig,
auch wenn das SDK nur `onDone` meldet. Ein dadurch ungültiger Gesprächskontext
darf nicht weiter als Tool-Fortsetzung genutzt werden. Der nächste frische Chat
ersetzt ihn. Private Inhalte werden für diese Fehler nicht protokolliert.

## Was noch nicht bewiesen ist

- Exakte Vorab-Tokenisierung des dynamischen Inputs fehlt im öffentlichen SDK.
- Tatsächliche Prefixgröße, Standardauftrags-Nutzbarkeit, Antwortlänge, Load-/Prefill-
  Latenz und Thermik auf dem Fold7: NOT_RUN.
- Native Modellmetadaten können eine angeforderte Enginegrenze weiter begrenzen;
  bei unbekannten Metadaten ist 4096 die Anforderung, kein Gerätebefund.
- Die Ausgabe kann bei knappem Kontext kürzer werden. Kontext-Ende ist kein
  Beleg für eine vollständig bearbeitete Anfrage.
- Die öffentlichen Input-/Outputtoken-Metriken bleiben `null`, weil der KV-Zuwachs
  neuen Input und Ausgabe gemeinsam enthält. Der echte KV-Zähler dient intern
  weiterhin der Kontextkontrolle. Das gebundene SDK bietet zwar
  `BenchmarkInfo.lastPrefillTokenCount` und `lastDecodeTokenCount`, aber nur bei
  aktiviertem Benchmarkmodus. Dieser ist in der aktuellen App nicht aktiviert;
  der native Abruf meldet dann ausdrücklich einen Fehler. Für eine reine Anzeige
  wird der Ausführungsmodus nicht verändert. Die UI zeigt bereits den vorhandenen
  ehrlichen Ersatztext „Tokenzahl nicht gemeldet“.

Hosttests prüfen normale Pflichtpromptgrößen ohne Totalblockade, fehlende/native
volle Zähler, keine Prefix-Doppelzählung, echten kumulierten Kontext bei
Fortsetzungen, Outputlimits, Unicode-Expansion und Zahlenüberlauf. Diese Tests
ersetzen keine native Modellinferenz.

## Primärquellen der gebundenen Version

- https://github.com/google-ai-edge/LiteRT-LM/blob/v0.18.0/kotlin/java/com/google/ai/edge/litertlm/Conversation.kt
- https://github.com/google-ai-edge/LiteRT-LM/blob/v0.18.0/kotlin/java/com/google/ai/edge/litertlm/Config.kt
- https://github.com/google-ai-edge/LiteRT-LM/blob/v0.18.0/runtime/conversation/conversation.cc
- https://github.com/google-ai-edge/LiteRT-LM/blob/v0.18.0/runtime/conversation/conversation_test.cc
- https://github.com/google-ai-edge/LiteRT-LM/blob/v0.18.0/runtime/core/tasks.cc
- https://github.com/google-ai-edge/LiteRT-LM/blob/v0.18.0/runtime/executor/llm_litert_compiled_model_executor.cc
- https://github.com/google-ai-edge/LiteRT-LM/blob/v0.18.0/runtime/core/session_advanced.cc
- https://github.com/google-ai-edge/LiteRT-LM/blob/v0.18.0/kotlin/java/com/google/ai/edge/litertlm/jni/litertlm.cc

Quellstellen: `Conversation::Create` (Prefix-Prefill), `GetSingleTurnText` und
`RenderMessageIntoString` (vollständiger Prefix nur beim leeren, unbefüllten Start),
`Prefill` in `tasks.cc` (echte Input-Tokenzahl), `ShouldStop` (Decodegrenzen) sowie
`GetTokenCount` (KV-Zähler). Keine API aus einem neueren Main-Zweig wird behauptet.
