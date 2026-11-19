# Sprechen während der Erzeugung – Vorschlag (18.11.2026)

Status: **Vorschlag und Vorarbeit. Nichts davon spricht in der App.** Die Entscheidung liegt beim Besitzer.
Gerätemessungen: keine (NOT_RUN).

## 1. Ausgangslage im Code

- Gesprochen wird nur die vollständige, geprüfte Endantwort: `speakReply` → `ApprovedSpeechChunkQueue`
  mit dem Beleg `visible-final-reply`.
- Die Vorarbeit existiert bereits: `ApprovedStreamingSpeechAssembler` zerlegt den Textstrom in Sätze.
  `generateLocal(streamCandidateObserver = …)` reicht sie an `StreamingSpeechShadowTrace` weiter. Dieser
  zählt nur (erste Satzzeit, Anzahl, Zeichen) und schreibt „SPEECH SHADOW … noch NICHT gesprochen“ ins
  Protokoll. Der Beobachter ist nur bei reinen Besitzer-Dialogrunden ohne Werkzeugbeobachtung verbunden.
- Warum trotzdem gewartet wird: Die Prüfungen laufen erst nach dem Ende der Erzeugung
  (`LocalDialogueQualityGate`, Selbstkorrektur, Erinnerungs-/Dokumentbeleg, Darstellung am Ausgabelimit).
  Der sichtbare Text kann sich danach noch ändern. Ein früh gesprochener Satz wäre dann falsch.

## 2. Was in dieser Runde ergänzt wurde

| Baustein | Zweck |
|---|---|
| `ApprovedStreamingSpeechAssembler` (erweitert, weiterhin nur Schattenmessung) | Ordnungszahlen und Daten („am 3. Mai“, „17.11.“), Aufzählungen („1. …“, „- …“), Anführungszeichen und Klammern, mehr Abkürzungen als ganze Wörter, kein Verlust langer Sätze, fortlaufende Nummern nach Überlauf |
| `StreamingSpeechSegmenter` (neu, **ohne Aufrufer**) | Freigaberegeln, Protokoll des bereits Freigegebenen, `finish()`, `retract()` |

Vorher endete ein „Satz“ nach „am 3.“ und nach „1.“; ein Satz mit `„Stopp! Halt!“` wurde mitten im Zitat
getrennt; Listen ohne Satzzeichen blieben ein einziger Block; Sätze über 960 Zeichen gingen verloren.

## 3. Was früh gesprochen werden darf

Nur ganze Sätze einer Runde, die **alle** Bedingungen erfüllt:

1. **Runde geeignet** (`Turn.eligible`): reine Textrunde ohne angebotenes Werkzeug (`streamTextOnly`), keine
   Werkzeugbeobachtung im Auftrag, keine Erinnerungsantwort aus gespeicherten Besitzerfakten, keine
   Dokumentantwort. Diese drei Arten werden erst am Ende gegen ihren Beleg geprüft.
2. **Satz geschlossen:** Satzzeichen plus Folgetext; nie innerhalb von Code, Zitat oder Klammer.
3. **Grenze bestätigt:** mindestens *N* weitere Zeichen sind eingetroffen (Vorschlag 12) und die
   Fortsetzung beginnt nicht mit einem Kleinbuchstaben. Sonst wird der Satz mit dem nächsten verbunden.
4. **Stabil:** der Satz ist seit *T* ms geschlossen (Vorschlag 250 ms).
5. **Strom offen:** kein Werkzeugvorschlag, kein Pufferüberlauf, keine frühere Ablehnung.
6. **Satzprüfung bestanden:** dieselben lexikalischen Prüfungen des Qualitätstors, die pro Satz Sinn
   ergeben (`lexicalSentenceGate`): durchgesickerte Prompt-Rahmen, Leugnen des mitgelieferten Kontexts,
   ungefragte Mundart, wörtlich wiederholter Satz der vorigen Antwort.
7. **Obergrenze:** höchstens 8 Sätze vor dem Ende; der Rest wartet auf `finish()`.

Scheitert eine Bedingung, wird nichts mehr früh freigegeben. Die Endantwort läuft dann über den heutigen Weg.

## 4. Wenn die Endprüfung später scheitert

Ablauf am Ende der Erzeugung:

1. Die volle Endprüfung läuft wie heute.
2. Bestanden **und** `releasedIsPrefixOf(sichtbare Antwort)`: `finish()` gibt den Rest frei. Nichts wird
   doppelt gesprochen.
3. Sonst `retract()`: Es meldet genau die bereits freigegebenen Sätze. Die Sprachausgabe wird gestoppt,
   dann folgt einmal der gesprochene Hinweis „Korrektur: Was ich gerade gesagt habe, war noch nicht
   geprüft. Hier ist die geprüfte Antwort.“ und danach die geprüfte Antwort über den heutigen Weg.
   Im Chat steht ohnehin nur die geprüfte Antwort.

## 5. Was nicht garantiert werden kann

- **Wahrheit.** Auch das heutige Tor prüft nur Form und Wiederholung. Früh gesprochene Sätze sind nicht
  schlechter belegt als die Endantwort, aber sie sind **vor** einer möglichen Selbstkorrektur hörbar.
- **Ganze-Antwort-Prüfungen** (Echo der Frage, Gesamtlänge, Ausgabelimit) greifen erst am Ende. Ihr
  Scheitern führt zur gesprochenen Korrektur, nicht zum Verhindern.
- **Satzgrenzen.** Unbekannte Abkürzungen vor einem Großbuchstaben („Hr. Müller“ steht in der Liste,
  „Fam. Müller“ nicht) erzeugen eine falsche Pause, keinen falschen Inhalt.
- **Bereits Gehörtes** lässt sich nicht zurücknehmen, nur berichtigen.

## 6. Verbleibende Lücke bis zur Verdrahtung

1. `streamCandidateObserver` liefert Kandidaten auf dem Modell-Thread. Die Sprachwarteschlange gehört dem
   UI-Thread und kennt nur „alle Stücke auf einmal“; `ApprovedSpeechChunkQueue` kann bereits nachliefern
   (`WAIT_FOR_CHUNKS`), `speakReply` nutzt das nicht.
2. `speakReply` stoppt laufende Sprache. Für Fall 2 muss es an eine laufende Runde anhängen können.
3. Das Lebenszeichen („Mhm, Moment.“) und frühes Sprechen dürfen nicht gleichzeitig laufen.
4. Unterbrechen per Stimme und Gesprächsmodus müssen die frühe Runde als dieselbe Antwort binden.
5. Maya (Netzstimme) bleibt ausgeschlossen: früher Text ginge vor der Endprüfung an einen Dienst.

## 7. Messungen am Gerät vor einer Entscheidung

| Messung | Quelle |
|---|---|
| Zeit bis zum ersten bestätigten Satz gegenüber Zeit bis zur Endantwort | `StreamingSpeechShadowTrace` (vorhanden), getrennt nach kurzer und langer Antwort |
| Anteil geeigneter Runden | Zähler `Turn.eligible` im Schattenbetrieb |
| Anteil der Runden, in denen `retract()` nötig gewesen wäre | Schattenbetrieb über `model-dev.jsonl` und Alltagsgespräche |
| Falsch gesetzte Satzgrenzen | Hörprobe und Protokoll der Kandidaten |
| Pause zwischen frühem Satz und Rest | Tokenrate des Modells gegen Sprechtempo |
| Wärme und Akku bei gleichzeitiger Erzeugung und Sprachausgabe | Gerät |

Empfehlung: zuerst den Schattenbetrieb um die Zähler für Eignung und Rücknahme erweitern und messen.
Erst bei einer Rücknahmequote, die der Besitzer akzeptiert, verdrahten – dann nur mit der Offline-Stimme.

## 8. Prüfstand

Host, reine JVM: `ApprovedStreamingSpeechAssemblerTest`, `StreamingSpeechSegmenterTest`,
`StreamingSpeechShadowTraceTest`. Keine Aussage über Gerät, Stimme oder Modell.
