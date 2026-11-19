# Stimme und Prosodie – Stimmprofile, Sprechtext, Weitersprechen (19.11.2026)

Status: Code und JVM/Robolectric-Tests. **Wie irgendeine Stimme oder ein Preset klingt, ist NOT_RUN** – niemand
in dieser Entwicklung kann Audio hören. Nichts hier ist „am Gerät bestätigt“. Keine neue Bezahl-API, kein neuer
Dienst, kein API-Schlüssel im Repository, in Logs oder Tests (Tests verwenden `synthetic_test_key` und Fake-HTTP).

## 1. Architektur, wie vorgefunden (Basis c508b5589)

| Teil | Ort | Verhalten |
|---|---|---|
| Ein Sprach-Queue für alle Antworten | `AgentActivity.speakReply` → `JarvisVoiceProfile.speakableChunks` → `ApprovedSpeechChunkQueue` → `speakNextChunk` | Antwort wird in ≤480-Zeichen-Stücke an Satzenden geteilt; jedes Stück hat eine eigene Utterance-ID; zwischen Stücken Pause aus `HumanSpeechTiming.pauseBetweenMs` (200–950 ms). |
| Gerätestimme | `TextToSpeech voiceEngine`, Auswahl `JarvisVoiceSelection` (nur offline installierte Stimmen), Rate `JarvisVoiceProfile.speechRate`=0.96 × Register-Faktor (±6 %), Pitch 1.0 | Pitch bleibt 1.0, weil `setPitch` neu abtastet (frühere Befundnotiz in `JarvisVoiceProfile`). |
| ElevenLabs („Maya“) | `MayaSpeech` (Worker, Job-Abbruch), `ElevenLabsPcmRequest` (POST `/v1/text-to-speech/ii0s2u4R3UFnxKL6DOrz/with-timestamps?output_format=pcm_24000`, Modell `eleven_multilingual_v2`, stability 0.5, similarity_boost 0.75, style 0, use_speaker_boost true), `MayaPcmPlayer` (AudioTrack, Audio-Fokus) | Nur wenn `maya_enabled`, Schlüssel im Keystore und Internetmodus „Immer an“ (`mayaAvailable`). Fällt ein Stück aus, spricht die bereite Offline-Stimme weiter (`onMayaPieceFailed`). |
| Lippen-/Kiefer-Sync | `TtsHologramSignal` (RMS aus PCM, verankert an Wiedergabe-Start) → `voiceFrame` (33 ms, nur während `voiceSpeaking`) → `AvatarDirector.audioLevel` → `Command03SpeechProjection` → `presentSpeech` → `native-bridge.js speech()` → `ContinuumAvatar.setSpeechFrame` | Maya: echtes PCM + **Zeichen-Timing aus `/with-timestamps`** (`MayaSpeechAlignment` → Wort-Bereiche → `wordRange`). Gerätestimme: PCM nur wenn die Engine `onAudioAvailable` liefert; sonst `onRangeStart`-Wörter, sonst Text-Tempo (`AvatarDirector` FALLBACK). Ende/Abbruch: `stopVoiceAnimation` → `avatarDirector.speechEnded` → Paket `active=false` → `clearNativeSpeech`; zusätzlich verfällt jedes Web-Paket nach `durationMs` (100 ms). |
| Freihand-Gespräch | `ContinuousConversationMode` | Nach der gebundenen Antwort `LISTEN` → Mikrofon öffnet ohne Tippen (`CONVERSATION_RESUME_DELAY_MS`); „Stopp“ beendet, „Warte/Pause“ pausiert (ganze Äußerung). |
| Barge-in | `AndroidBargeInMonitor` + `VoiceBargeInDetector` | Opt-in (`voice_barge_in`, Standard aus, „experimentell“). Mikrofon `VOICE_COMMUNICATION` + `AcousticEchoCanceler`/`NoiseSuppressor` falls vorhanden; Detektor vergleicht Mikrofonpegel mit dem gerade hörbaren eigenen Pegel. Treffer → `onChatMicrophoneTap` → Rede stoppt, Mikrofon öffnet. Antippen unterbricht immer. |
| Verworfener Rest | `stopVoiceAnimation` | Bei jedem Ende wurde der ungesprochene Rest verworfen; „weiter“ war nur die Fortsetzung einer abgeschnittenen **Modell**-Antwort (`AnswerContinuationController`). |

## 2. Dokumentationsfakten (offiziell, abgerufen 19.11.2026)

- Stimmenliste: `GET https://api.elevenlabs.io/v2/voices`, `page_size` Standard 10, höchstens 100; Felder je Stimme
  `voice_id`, `name`, `labels`, `description`, `preview_url` („The preview URL of the voice“), `category`,
  `verified_languages` (je `language`, `model_id`, `accent`, `locale`, `preview_url`).
  <https://elevenlabs.io/docs/api-reference/voices/search>
  **Kosten der Liste/der Proben: in der offiziellen Doku nicht angegeben.** Abrechnung laut Preisseite: „Text to
  Speech is billed per character“ – die Liste und eine Probe erzeugen keine Sprache. Continuum behauptet deshalb
  **nicht** „kostenlos“, sondern „keine Sprachsynthese; Abrufpreis in der Doku nicht genannt“.
- Synthese mit Zeitmarken: `POST /v1/text-to-speech/{voice_id}/with-timestamps`; `voice_settings`: `stability`
  (Standard 0.5; „Lower values introduce broader emotional range“, höhere „can sound monotonous“),
  `similarity_boost` (0.75), `style` (0; „uses extra compute and may increase latency“), `use_speaker_boost`
  (true), `speed` (1.0); `language_code` „not supported for multilingual_v2“; `apply_text_normalization`
  auto/on/off; Antwort `audio_base64`, `alignment` mit `characters`, `character_start_times_seconds`,
  `character_end_times_seconds`. <https://elevenlabs.io/docs/api-reference/text-to-speech/convert-with-timestamps>
  Für `pcm_24000` ist kein Tarif-Mindestniveau genannt (PCM 44,1 kHz braucht Pro).
- Regler-Guide: Speed „minimum of 0.7“, „maximum of 1.2“; Stabilität höher „for a more serious, steadier
  delivery“; Style: „we recommend keeping this setting at 0 at all times“; „most common setting is stability around
  50, similarity around 75, and keeping style at 0“. Eleven v3: kein Speed/Similarity/Speaker Boost; Eleven v4:
  nur Stability und Similarity, kein SSML. <https://elevenlabs.io/docs/eleven-creative/playground/text-to-speech>
- Modelle: `eleven_v4`/`eleven_v4_turbo` (~100 ms), `eleven_v3`, `eleven_v3_conversational`,
  `eleven_multilingual_v2` (29 Sprachen inkl. Deutsch, „most stable on long-form“), `eleven_flash_v2_5` (~100 ms,
  32 Sprachen inkl. Deutsch, günstiger); `eleven_turbo_v2_5` veraltet. <https://elevenlabs.io/docs/overview/models>
- Pausen/Normalisierung: `<break time="x.xs" />` bis 3 s; „Eleven v4 and Eleven v3 do not support SSML break
  tags“; „too many break tags … can cause instability“. Normalisierung standardmäßig an; Multilingual v2 liest
  Zahlen korrekt, Flash v2.5 nicht immer.
  <https://elevenlabs.io/docs/overview/capabilities/text-to-speech/best-practices>
- Preise API (USD je 1.000 Zeichen): v2 Multilingual 0,08; Flash/Turbo 0,04; v3 0,08; v4 0,08; v4 Turbo 0,04.
  <https://elevenlabs.io/pricing/api>
- Android: `setSpeechRate` „1.0 is the normal speech rate … 0.5 is half“; `setPitch` „1.0 is the normal pitch,
  lower values lower the tone“. <https://developer.android.com/reference/android/speech/tts/TextToSpeech>
  Deutsche Offline-Stimmen hängen von der installierten Engine ab (Samsung TTS, Google Sprachdienste); Android
  liefert kein Geschlecht, nur Name/Locale/Qualität/Latenz/Netzbedarf (`getVoices`) – Auswahl nach Gehör.

## 3. Presets (Werte und Begründung; Klang NOT_RUN)

| Preset | stability | similarity | style | speed (EL) | Gerät Rate | Pitch | Pausen |
|---|---|---|---|---|---|---|---|
| Bisherige Stimme | 0.50 | 0.75 | 0 | *nicht gesendet* | 0.96 | 1.0 | 100 % |
| Souverän | 0.70 | 0.80 | 0 | 0.94 | 0.90 | 1.0 | 115 % |
| Sachlich | 0.60 | 0.75 | 0 | 1.00 | 0.98 | 1.0 | 100 % |
| Warm | 0.40 | 0.75 | 0 | 0.97 | 0.94 | 1.0 | 108 % |

- **Bisherige Stimme** ist Standard und sendet byte-gleich den bisherigen Request (Test
  `VoiceProfilesTest.formerVoiceIsTheDefaultAndSendsExactlyTheFormerRequest`).
- **Souverän**: Stabilität in Richtung „serious, steadier“, deutlich unter 1.0 wegen „monotonous“; Similarity
  etwas höher, damit eine gewählte tiefe Stimme ihr Timbre behält; Style 0 laut Doku; ~6 % langsamer innerhalb
  0.7–1.2; längere Pausen bleiben im 200–950-ms-Band (`HumanSpeechTiming`). Tiefe kommt aus der **Stimmwahl**,
  nicht aus Pitch-Verschiebung.
- Sprachbefehle verschieben in Stufen: Tempo ±0.04 (EL) bzw. ±5 % (Gerät), höchstens ±3; „ruhiger“ +0.08
  Stabilität (max. 0.9), −0.02 Tempo, +6 % Pausen, höchstens 2 Stufen.
- Modell bleibt `eleven_multilingual_v2` (bisheriges Modell, bisherige Kosten, korrektes Zahlenlesen). Flash v2.5
  wäre halb so teuer und schneller – Entscheidung des Besitzers, nicht umgesetzt.

## 4. Was geändert wurde

1. `voice/VoiceProfiles.kt`, `VoiceProfileStore.kt`: Profil = Engine (`maya_enabled`) + ElevenLabs-Stimme +
   Preset + Stufen. Ohne neue Schlüssel liest eine Installation genau wie vorher.
2. `voice/ElevenLabsVoiceCatalog.kt`, `VoiceProfileDialogs.kt`, `VoicePreviewPlayer.kt`: Menü „Stimmprofil und
   Sprechweise“ (TON lange drücken → Stimme). Liste lädt nur beim Öffnen, nur mit Schlüssel und „Immer an“;
   deutsch geprüfte Stimmen zuerst, Labels wie geliefert. „Probe hören“ spielt `preview_url` (nur https, ohne
   Schlüssel; deutsche Probe bevorzugt) mit Hinweis „ElevenLabs-Beispieltext, nicht Continuums Satz“.
   „Mit Continuum-Satz testen“ nennt vorher die Zeichenzahl (heute 91) und läuft nur nach „Ja, … Zeichen
   verwenden“. Sprachbefehle: „Wechsle die Stimme“, „Nimm wieder die bisherige Stimme“, „Sprich
   langsamer/schneller/ruhiger/normal“ – Bestätigung in einem Satz.
3. `voice/SpeechTextShaper.kt`: Sprechtext (Uhrzeiten, Datum, Dezimalkomma, Tausenderpunkte, Einheiten,
   Abkürzungen, Listen → Satzenden, Ordnungswörter, Links → Domain, Emoji weg, „!!!“ → „!“). **Keine SSML**
   (v3/v4 ohne Break-Tags, Instabilitätswarnung, Alignment muss exakt den gesendeten Text abbilden). Sichtbarer
   Text bleibt unverändert; danach wie bisher `JarvisVoiceProfile` (Code, Rest-Markup).
4. `voice/ResumableReplySpeech.kt`, `voice/VoiceCommands.kt`: Unterbrochener Rest (ab dem unterbrochenen Stück)
   bleibt 10 min in derselben Chat-Sitzung; „weiter“ spricht ihn (nur dann – sonst behält „weiter“ seine
   Modell-Fortsetzungsbedeutung). Im Freihandmodus hört Continuum danach wie gewohnt weiter zu.
5. Lippen-Sync: unverändert gelassen, weil bereits vorhanden: ElevenLabs-Zeitmarken kommen schon aus dem
   bisherigen `/with-timestamps`-Aufruf (gleiche Abrechnung pro Zeichen, keine Zusatzkosten in der Doku);
   der Mund schließt bei Ende/Abbruch über `speechEnded` + `active=false`, Web-Pakete verfallen nach 100 ms.
   Keine neue Animationsschleife.
6. Ausweichstimme: `VoiceFallbackNotice` sagt **einmal** den Grund (kein Schlüssel / nicht „Immer an“ / Antwort
   bleibt lokal / ElevenLabs antwortet nicht); Gerätestimme spricht denselben geformten Text mit Rate/Pitch des
   gewählten Presets. Kann gar nichts sprechen, wird das immer gesagt.

## 5. Kosten

| Aktion | Credits |
|---|---|
| Stimmenliste öffnen | keine Sprachsynthese; Preis in Doku nicht genannt |
| „Probe hören“ | keine Synthese, kein Text gesendet, kein Schlüssel; Preis in Doku nicht genannt |
| „Mit Continuum-Satz testen“ (ElevenLabs) | 91 Zeichen, nur nach Bestätigung |
| Normale Antworten mit ElevenLabs | wie bisher pro gesprochenem Zeichen; der Sprechtext kann durch ausgeschriebene Zahlen/Abkürzungen etwas länger sein (z. B. „GB“ → „Gigabyte“) |
| Gerätestimme | lokal |

## 6. Grenzen und Gerätearbeit

- Klang aller Presets/Stimmen: NOT_RUN. Deutsche Aussprache englischer Stimmen: NOT_RUN.
- Barge-in bleibt opt-in; Echo-Unterdrückung nur so gut wie der Geräte-AEC (Fold7 nicht geprüft). Ohne Barge-in
  ist im Freihandmodus während der Antwort kein Mikrofon offen – dann unterbricht nur Antippen.
- „usw.“ vor einem großgeschriebenen Nomen bekommt keine Satzpause (bewusst; Test dokumentiert).
- Gerätestimmen ohne `onAudioAvailable` bewegen den Mund nur nach Wort-Callbacks/Tempo, nicht nach Pegel.
- Abspielen einer Probe nutzt `MediaPlayer` mit fremder URL (ElevenLabs-Speicher): nur https, nur bei „Immer an“.
