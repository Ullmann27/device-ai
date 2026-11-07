# Jarvis-Sprachausgabe: Qualität und Synchronisation möglicher Offline-Engines (Zwischenstand 2026-11-06)

**Status: Zwischenstand – auf Anweisung des Besitzers vorzeitig abgeschlossen.** Alle Host-Messungen und Quellenprüfungen in diesem Dokument wurden tatsächlich durchgeführt; Abschnitte, die nicht mehr bearbeitet werden konnten, sind ausdrücklich als „nicht geprüft“ markiert. Es wurden keine Messwerte geschätzt oder ergänzt.

Vertieft die Recherche `JARVIS_VOICE_ENGINES_20261106.md` (dort: Android-Voice-API, bestehende Stimmenauswahl, Lippen-Fallbacks). Diese wird hier nicht wiederholt.

Kennzeichnung in allen Tabellen:
- **[H]** = am Host gemessen (x86_64, 4 vCPU Intel Xeon 2,90 GHz, 8 GB RAM, Python-Paket `sherpa-onnx` 1.13.8). **Kein Fold7-Wert.**
- **[D]** = aus Dokumentation/Quellcode gelesen (URL angegeben).
- **[C]** = Community-Bericht (schwache Evidenz).
- **[N]** = nicht geprüft.
- Fold7: **REAL_DEVICE_NOT_RUN** – kein Schritt wurde auf dem Gerät ausgeführt.

---

## 1. Warum die Engine die Lippen-Synchronisation bestimmt

Die App (`avatar/AvatarDirector.kt`) kennt drei Quellen für Mundbewegung, in dieser Reihenfolge: PCM-Hüllkurve aus `UtteranceProgressListener.onAudioAvailable`, Wortgrenzen aus `onRangeStart`, und zuletzt ein Text-Tempo (`TEXT_PACE_GRACE_MS = 250`, `TEXT_PACE_WORD_MS = 360`, `TEXT_PACE_TAIL_MS = 300`; Lesestand des Codes am Ausgangs-Commit `5e2e3aa8`). Was davon verfügbar ist, hängt allein von der System-Engine ab:

- `onAudioAvailable` wird vom Android-Framework für **jede** Engine ausgelöst, die über `TextToSpeechService` synthetisiert: `PlaybackSynthesisCallback.audioAvailable()` kopiert jeden Puffer und ruft `dispatchOnAudioAvailable(bufferCopy)` auf, sowohl bei `speak()` als auch bei `synthesizeToFile()` [D] ([AOSP PlaybackSynthesisCallback.java, Z. 168–201](https://raw.githubusercontent.com/aosp-mirror/platform_frameworks_base/android14-release/core/java/android/speech/tts/PlaybackSynthesisCallback.java); [UtteranceProgressListener.onAudioAvailable](https://developer.android.com/reference/android/speech/tts/UtteranceProgressListener)). Die Hüllkurve ist damit engine-unabhängig – aber sie kommt zum Synthesezeitpunkt, nicht zum Abspielzeitpunkt („may not be played for some time depending on buffer sizes“, ebd.).
- `onRangeStart` kommt **nur**, wenn die Engine selbst `SynthesisCallback.rangeStart(markerInFrames, start, end)` aufruft („Only called if the engine supplies timing information“) [D] ([UtteranceProgressListener.onRangeStart](https://developer.android.com/reference/android/speech/tts/UtteranceProgressListener)).
- Phonem-Zeitstempel kennt die Android-TTS-API überhaupt nicht; es gibt keinen Callback dafür [D] (ebd.). Eine „präzise Phonemsynchronisation“ ist daher über eine System-Engine prinzipiell nicht belegbar – bestenfalls Wortgrenzen plus Hüllkurve.

---

## 2. Kandidaten-Vergleich

| Engine (System-TTS) | Offline | Installation/Kosten | Lizenz | Weibliche de-DE-Stimme | Modellgröße | Sample-Rate | `onRangeStart` (Wortgrenzen) | PCM via `onAudioAvailable` | de-AT | Quelle |
|---|---|---|---|---|---|---|---|---|---|---|
| **Samsung TTS** (`com.samsung.SMT`) | ja, nach Download des Sprachpakets [D] | vorinstalliert auf Galaxy; Pakete gratis im Galaxy Store [D] | proprietär | ja: „German Default voice 1“ = `com.samsung.SMT.lang_de_de_f00` (Voice Type: Female) und HD-Stimme „Deutsch Stimme 1“ = `com.samsung.SMT.lang_de_de_l01` (Female); `…_m00` = Male [D] | HD-Paket 97,4 MB (Galaxy Store) bzw. 93–115 MB (APKMirror-Versionen) [D] | [N] | **nein** [C] – mehrere Entwicklerberichte: nur Google TTS liefert Wortgrenzen, Samsung/Pico nicht | ja (Framework) [D] | nein, nur de-DE [D] | [Galaxy Store de_de_l01](https://galaxystore.samsung.com/detail/com.samsung.SMT.lang_de_de_l01), [APKMirror f00](https://www.apkmirror.com/apk/samsung-electronics-co-ltd/samsungtts-german-female/samsungtts-german-female-3-1-23-51-release/samsung-tts-german-default-voice-1-3-1-23-51-android-apk-download/), [APKMirror m00](https://www.apkmirror.com/apk/samsung-electronics-co-ltd/samsungtts-german-male/samsungtts-german-male-312314000-release/samsungtts-german-male-312314000-android-apk-download/), [APKMirror l01 „Deutsch Stimme 1“](https://www.apkmirror.com/apk/samsung-electronics-co-ltd/samsungtts-hd-german/samsungtts-hd-german-312315000-release/), [Reddit r/speechtech](https://www.reddit.com/r/speechtech/comments/1r2bl6g/mobile_tts_reading_app_dev_what_i_learned_about/), [GitHub-Notiz zu Engines](https://github.com/Gazdal83/BookMotion/blob/main/tasks.md) |
| **Google Speech Services** (`com.google.android.tts`) | ja, nach Download der Offline-Stimme („Install voice data“) [D] | vorinstalliert; gratis (Play Store) [D] | proprietär | de-DE-Offline-Stimme `de-de-x-nfh` ist weiblich (XDA-Liste: „de-DE (female) … de-de-x-nfh-phone-hmm“; Google-Preinstall-Doku: Verzeichnis `/system/tts/google/de-de-x-nfh`) [D]; weitere `de-de-x-…-local`-Stimmen: Geschlecht [N] | [N] | [N] | **ja** [C]+[D] – Google TTS gilt als einzige verbreitete Engine mit `rangeStart`; die Voice-API selbst macht dazu keine Angabe | ja (Framework) [D] | nein, nur de-DE [D] | [Play: Speech Recognition & Synthesis](https://play.google.com/store/apps/details?id=com.google.android.tts), [XDA Stimmenliste](https://xdaforums.com/t/q-google-tts-voices-download-link.2969700/), [StackOverflow Preinstall](https://stackoverflow.com/questions/58109159/google-text-to-speech-googletts-language-packs-for-preinstall), [StackOverflow Namensformat](https://stackoverflow.com/questions/62883919/android-tts-voice-name-format) |
| **sherpa-onnx TTS Engine (offiziell, k2-fsa)** | ja, Modell im APK gebündelt [D] | APK-Download von GitHub Pages, gratis; **nicht** im Play Store/F-Droid [D] | Apache-2.0 (Engine); Piper-Stimmen je Datensatz | **Für Deutsch nur männliche/robotische Prebuilt-APKs** (thorsten, thorsten_emotional, glados, glados_turret); keine eva_k/kerstin/ramona-APK [D] | de_DE-Piper-APKs ~55–85 MB (laut APK-Liste) [D] | Piper low/x_low 16 kHz, medium 22,05 kHz [D] | **nein** – `TtsService.kt` ruft nur `callback.start/audioAvailable/done`, kein `rangeStart`; kein `onGetVoices` → Framework erzeugt eine Voice pro Sprache [D] | ja: streamt PCM-Blöcke per `callback.audioAvailable` während der Synthese [D] | nein (Modell-Locale `de_DE`) [D] | [APK-Liste](https://k2-fsa.github.io/sherpa/onnx/tts/apk-engine.html), [TtsService.kt](https://raw.githubusercontent.com/k2-fsa/sherpa-onnx/master/android/SherpaOnnxTtsEngine/app/src/main/java/com/k2fsa/sherpa/onnx/tts/engine/TtsService.kt), [TtsEngine.kt](https://raw.githubusercontent.com/k2-fsa/sherpa-onnx/master/android/SherpaOnnxTtsEngine/app/src/main/java/com/k2fsa/sherpa/onnx/tts/engine/TtsEngine.kt) |
| **SherpaTTS** (woheller69, Fork der obigen Engine) | ja; Modelle werden einmalig von Hugging Face geladen [D] | **F-Droid**, gratis [D] | Apache-2.0 [D] | ja: beliebige Piper-de_DE-Stimmen wählbar (eva_k, kerstin, ramona …), außerdem Kokoro/Coqui-Modelle [D] | wie Piper-Modell (siehe §3) | 16/22,05 kHz | **nein** – gleicher `TtsService`-Aufbau, kein `rangeStart` [D] | ja [D] | nein | [GitHub woheller69/ttsEngine](https://github.com/woheller69/ttsEngine), [TtsService.kt](https://raw.githubusercontent.com/woheller69/ttsEngine/master/app/src/main/java/com/k2fsa/sherpa/onnx/tts/engine/TtsService.kt) |
| **Supertonic 3 über sherpa-onnx-Engine** (neu, 2026) | ja [D] | Prebuilt-APK (sherpa-onnx v2.0.1) auf GitHub Pages, gratis [D] | Modell OpenRAIL-M, Engine Apache-2.0 [D] | ja: 10 Sprecher, `sid 0–4 = F1–F5` (weiblich), `5–9 = M1–M5`; 31 Sprachen inkl. `de` [D] | int8-Archiv 128,8 MB [D] | 24 kHz [D] | nein (gleiche Engine) [D] | ja [D] | nein | [sherpa-Doku supertonic-3-de](https://k2-fsa.github.io/sherpa/onnx/tts/all/German/supertonic-3-de.html), [HF Supertone/supertonic-3](https://huggingface.co/Supertone/supertonic-3), [Release-Asset](https://github.com/k2-fsa/sherpa-onnx/releases/tag/tts-models) |
| **eSpeak-NG** (Referenz, Formant-Synthese) | ja | F-Droid 1.52.0, gratis [D] | GPL-3.0 | kein natürliches Timbre; Varianten | sehr klein | 22,05 kHz [D] | **ja, aber nur in `master` seit 2026-08-01 (PR #2471)** – nicht in Release 1.52.0 / F-Droid 1.52.0 [D] | ja [D] | nein | [espeak-ng TtsService.java](https://raw.githubusercontent.com/espeak-ng/espeak-ng/master/android/src/com/reecedunn/espeak/TtsService.java), [Releases](https://github.com/espeak-ng/espeak-ng/releases), [F-Droid API](https://f-droid.org/api/v1/packages/com.reecedunn.espeak) |
| **RHVoice** | ja | F-Droid | LGPL | **kein Deutsch** (Sprachen: Russisch, Englisch, Portugiesisch BR, Esperanto, Georgisch, Ukrainisch, Kirgisisch, Tatarisch, Mazedonisch, Albanisch, Polnisch) [D] | – | – | – | – | – | [RHVoice README](https://raw.githubusercontent.com/RHVoice/RHVoice/master/README.md) |
| **Mimic3 / Coqui TTS** | – | keine Android-System-Engine; Mimic3-Repo zuletzt 2025-03 bearbeitet, Coqui TTS zuletzt 2024-08 [D] | – | Coqui-CSS10-de-Modell nur über SherpaTTS | – | – | – | – | – | [GitHub MycroftAI/mimic3](https://github.com/MycroftAI/mimic3), [GitHub coqui-ai/TTS](https://github.com/coqui-ai/TTS) |
| **ML Kit GenAI Text-to-Speech** (Google, on-device) | Modell wird aufs Gerät geladen [D] | SDK-Integration nötig (`genai-text-to-speech:1.0.0-alpha1`), **kein** System-TTS | proprietär | ja, Geschlecht dokumentiert (star02/08/12/16 weiblich); de-DE unterstützt [D] | [N] | [N] | [N] | Streaming-Audio-Chunks [D] | nein | **Fold7 nicht in der Geräteliste** (nur S25/S26/Fold8/Flip8, Pixel 8–11); Alpha ohne SLA [D] – [ML Kit Doku](https://developers.google.com/ml-kit/genai/text-to-speech/android) |

**de-AT:** Keine der geprüften Offline-Engines bietet eine österreichische Stimme; Piper-Voices enthalten nur `de_DE` (Modell-JSON: `"region": "DE"` [H]), Samsung/Google nur de-DE [D]. Österreichische Stimmen existieren nur bei Cloud-Diensten (nicht Gegenstand).

---

## 3. Piper-Stimmen: Geschlecht, Herkunft, Lizenz [D]

| Stimme (de_DE) | Geschlecht | Datensatz | Lizenz | Training | Quelle |
|---|---|---|---|---|---|
| eva_k (x_low) | **weiblich** | M-AILABS (Eva K.) | „See URL“ (M-AILABS, LibriVox-Basis) | from scratch, 16 kHz | [MODEL_CARD](https://huggingface.co/rhasspy/piper-voices/raw/main/de/de_DE/eva_k/x_low/MODEL_CARD), [Paper: „Eva K … female“](https://personales.upv.es/thinkmind/dl/conferences/centric/centric_2021/centric_2021_1_40_30010.pdf) |
| kerstin (low) | **weiblich** | rhasspy/dataset-voice-kerstin („native female German speaker“, 1 374 Äußerungen) | CC0 | Finetune von en-US „ryan“, 16 kHz | [MODEL_CARD](https://huggingface.co/rhasspy/piper-voices/raw/main/de/de_DE/kerstin/low/MODEL_CARD), [Datensatz](https://github.com/rhasspy/dataset-voice-kerstin) |
| ramona (low) | **weiblich** | M-AILABS (Ramona Deininger) | „See URL“ | from scratch, 16 kHz | [MODEL_CARD](https://huggingface.co/rhasspy/piper-voices/raw/main/de/de_DE/ramona/low/MODEL_CARD), [Paper: „Ramona … female“](https://personales.upv.es/thinkmind/dl/conferences/centric/centric_2021/centric_2021_1_40_30010.pdf) |
| karlsson (low) | männlich | M-AILABS | „See URL“ | Finetune ryan | [MODEL_CARD](https://huggingface.co/rhasspy/piper-voices/raw/main/de/de_DE/karlsson/low/MODEL_CARD) |
| pavoque (low) | männlich (PAVOQUE-Korpus, ein Sprecher) | marytts/pavoque-data | CC BY-NC-SA 4.0 (**nicht-kommerziell**) | Finetune ryan | [MODEL_CARD](https://huggingface.co/rhasspy/piper-voices/raw/main/de/de_DE/pavoque/low/MODEL_CARD) |
| thorsten (low/medium/high, emotional) | männlich | Thorsten-Voice | CC0 | medium: 22,05 kHz | [MODEL_CARD](https://huggingface.co/rhasspy/piper-voices/raw/main/de/de_DE/thorsten/medium/MODEL_CARD) |
| miro (high), dii (high), glados* | [N] – keine MODEL_CARD im rhasspy-Repo gefunden | | | | |

Hinweis: Das Geschlecht von pavoque ist aus dem Korpus (ein männlicher Sprecher) abgeleitet, die MODEL_CARD selbst nennt es nicht [D].

---

## 4. Host-Messung [H] – sherpa-onnx 1.13.8, Piper-VITS, x86_64 (KEIN Fold7-Wert)

Setup: `pip install sherpa-onnx==1.13.8` (Version 2.0.1 synthetisiert diese Modelle **nicht** mehr ohne externe Phonemisierung: „no lexicon, and no frontend available“ – espeak-ng-Frontend wurde aus 2.x entfernt [H]). Modelle aus dem Release `tts-models` ([k2-fsa/sherpa-onnx](https://github.com/k2-fsa/sherpa-onnx/releases/tag/tts-models)). Skript: `bench.py` (nicht im Repo; 3 Wiederholungen je Satz, bester Wert; Warm-up-Äußerung vorab). Sätze: S1 „Ich habe die PDF gelesen und einen Entwurf erstellt.“ – S2 „Gestern haben wir die zweite Möglichkeit gewählt, aber die Voraussetzung hat sich heute geändert.“ – S3 „Möchtest du, dass ich beim letzten offenen Punkt weitermache?“

### 4.1 Größe, Laden, Speicher

| Modell | Archiv (tar.bz2) | ONNX | espeak-ng-data | Ladezeit | RSS nach Laden | RSS nach Synthese |
|---|---|---|---|---|---|---|
| eva_k-x_low | 26,5 MB | 20,6 MB | ~ gleich in allen Archiven | 0,62 s | 91 MB | 191–194 MB |
| kerstin-low | 67,1 MB | 63,1 MB | | 0,69 s | 132 MB | 247–259 MB |
| ramona-low | 67,1 MB | 63,1 MB | | 0,72 s | 132 MB | 238–244 MB |

(RSS = `ru_maxrss` des Python-Prozesses inkl. Interpreter, ~41 MB Grundlast; ein Prozess je Modell.)

### 4.2 Echtzeitfaktor (RTF = Synthesezeit / Audiodauer; <1 = schneller als Echtzeit)

| Modell | Threads | S1 Audio / Synth / RTF | S2 Audio / Synth / RTF | S3 Audio / Synth / RTF |
|---|---|---|---|---|
| eva_k-x_low | 1 | 3,09 s / 0,180 s / **0,058** | 5,65 s / 0,296 s / **0,052** | 3,47 s / 0,168 s / **0,048** |
| eva_k-x_low | 4 | 3,54 s / 0,072 s / 0,020 | 6,03 s / 0,124 s / 0,021 | 3,43 s / 0,068 s / 0,020 |
| kerstin-low | 1 | 3,15 s / 0,204 s / **0,065** | 5,20 s / 0,356 s / **0,068** | 2,95 s / 0,199 s / **0,067** |
| kerstin-low | 4 | 2,97 s / 0,077 s / 0,026 | 5,12 s / 0,130 s / 0,025 | 2,99 s / 0,077 s / 0,026 |
| ramona-low | 1 | 2,67 s / 0,176 s / **0,066** | 4,93 s / 0,319 s / **0,065** | 2,86 s / 0,176 s / **0,061** |
| ramona-low | 4 | 2,95 s / 0,070 s / 0,024 | 4,54 s / 0,115 s / 0,025 | 2,91 s / 0,069 s / 0,024 |

Einordnung: Alle drei Modelle synthetisieren einen Satz in 0,07–0,36 s. Ein Fold7-Wert liegt nicht vor; als einziger mobiler Referenzpunkt aus der Literatur: Piper `de_DE-thorsten-medium` auf Pixel 10 Pro Fold (Tensor G5) lief laut einem Drittprojekt 6–9× schneller als Echtzeit, bei 3,8–4,0 s Zeit bis zum ersten Audio für einen 71-s-Text [D] ([mmaudet/piper-rn-poc](https://github.com/mmaudet/piper-rn-poc)) – anderes Gerät, andere Stimme, nicht übertragbar.

### 4.3 Messbare Audio-Eigenschaften der WAV-Proben (Lauf mit 4 Threads)

| Datei | Dauer | Sprachanteil | Wörter | ms/Wort | RMS | Peak | längste innere Pause | Clipping |
|---|---|---|---|---|---|---|---|---|
| eva_k-x_low_s1 | 3,54 s | 3,34 s | 9 | 371 | −21,0 dBFS | −2,0 dBFS | 100 ms | 0 |
| eva_k-x_low_s2 | 6,03 s | 5,80 s | 14 | 414 | −22,4 dBFS | −4,6 dBFS | 80 ms | 0 |
| eva_k-x_low_s3 | 3,43 s | 3,30 s | 9 | 367 | −21,2 dBFS | −2,1 dBFS | 40 ms | 0 |
| kerstin-low_s1 | 2,97 s | 2,72 s | 9 | 302 | −25,3 dBFS | −5,6 dBFS | 60 ms | 0 |
| kerstin-low_s2 | 5,12 s | 4,90 s | 14 | 350 | −25,7 dBFS | −4,2 dBFS | 200 ms (Komma) | 0 |
| kerstin-low_s3 | 2,99 s | 2,80 s | 9 | 311 | −24,5 dBFS | −4,6 dBFS | 60 ms | 0 |
| ramona-low_s1 | 2,95 s | 2,68 s | 9 | 298 | −27,9 dBFS | **−13,1 dBFS** | 80 ms | 0 |
| ramona-low_s2 | 4,54 s | 4,32 s | 14 | 309 | −26,5 dBFS | **−11,1 dBFS** | 120 ms | 0 |
| ramona-low_s3 | 2,91 s | 2,70 s | 9 | 300 | −22,6 dBFS | −9,9 dBFS | 60 ms | 0 |

Befunde (messbar, ohne Hörurteil):
1. Keine Abbrüche: Alle neun Proben enthalten den vollständigen Satz (Sprachanteil ≥ 2,7 s, keine inneren Pausen > 400 ms; die 400-ms-Pause von kerstin-S2 im 1-Thread-Lauf liegt am Komma). Kein Clipping.
2. **ramona-low ist ~6–9 dB leiser** als eva_k/kerstin (Peak −10 bis −13 dBFS). Für die PCM-Hüllkurve in `AvatarDirector` bedeutet das eine kleinere Amplitude – falls dort ein fester Schwellwert verwendet wird, muss er stimmenabhängig normalisiert werden (am Fold7 prüfen).
3. **Dauer ist nicht deterministisch:** Dieselbe Äußerung lieferte zwischen Läufen z. B. 5,30 / 5,65 / 6,03 s (eva_k S2) – VITS nutzt einen stochastischen Dauer-Prädiktor (`noise_w 0.8` im Modell-JSON). Das gemessene Sprechtempo liegt bei **298–414 ms/Wort**, der Text-Tempo-Fallback der App rechnet mit 360 ms/Wort; über 14 Wörter ergibt das bis zu ±0,75 s Drift – der Fallback ist also plausibel kalibriert, aber nicht satzgenau.
4. **Phonem-Warnung:** sherpa-onnx meldet bei allen drei Stimmen für Wörter mit ich-Laut („Ich“, „Möchtest“) `Skip unknown phonemes. Unicode codepoint: U+0327`. Ursache: piper-phonemize zerlegt espeak-Ausgabe absichtlich nach NFD („Decompose, e.g. ç -> c + ̧“), `tokens.txt` enthält aber nur das vorkomponierte `ç` [H]+[D] ([piper-phonemize phonemize.cpp Z. 48–49](https://raw.githubusercontent.com/rhasspy/piper-phonemize/master/src/phonemize.cpp)). Da Piper beim Training denselben Phonemizer verwendet hat, ist das Verhalten konsistent mit dem Original-Piper und vermutlich hörbar unauffällig – **am Fold7 explizit an „ich/nicht/möchtest“ prüfen**, nicht als korrekt annehmen.
5. **Zeitstempel: nein.** Die Python-API `OfflineTts` bietet nur `generate`, `num_speakers`, `sample_rate`; `generate` liefert ausschließlich Samples + Sample-Rate [H]. Die Android-Engines (k2-fsa, SherpaTTS) rufen entsprechend nie `rangeStart` [D]. Piper selbst hat seit 2025 **experimentelle Phonem-Alignments** (Samples pro Phonem-ID), die aber ein gepatchtes ONNX-Modell und die piper1-gpl-API erfordern und in keiner Android-Engine ankommen [D] ([piper1-gpl ALIGNMENTS.md](https://github.com/OHF-Voice/piper1-gpl/blob/main/docs/ALIGNMENTS.md), [Issue #22](https://github.com/OHF-Voice/piper1-gpl/issues/22)); das ursprüngliche rhasspy/piper ist seit 2025 archiviert, der Nachfolger OHF-Voice/piper1-gpl ist GPL-3.0 [D] ([GitHub rhasspy/piper](https://github.com/rhasspy/piper), [GitHub OHF-Voice/piper1-gpl](https://github.com/OHF-Voice/piper1-gpl)).

### 4.4 Nicht durchgeführt [N]
- ASR-Transkription der Proben (Umlaute/„PDF“) – optional, nicht begonnen.
- Supertonic-3-Proben (128 MB, außerhalb des Download-Budgets).
- thorsten/pavoque/karlsson (männlich, nicht Zielgruppe).
- Hörbewertung „Natürlichkeit“ – ausdrücklich dem Besitzer vorbehalten. WAV-Dateien liegen außerhalb des Repos (`/home/user/workspace/tts/out/*.wav`, 16 kHz PCM16 mono) und wurden dem Auftraggeber-Agenten als Anhang übergeben.

---

## 5. Wie die bestehende Stimmenauswahl die Engines sieht [D]

`JarvisVoiceSelection.kt` filtert `offline + installiert → Sprache de → Region AT>DE>CH>ohne → Qualität → Latenz`. Erwartete `Voice`-Objekte je Engine (Fold7 nicht geprüft):

| Engine | `Voice.name` | `Voice.locale` | Qualität/Latenz | Features | Folge für die Auswahl |
|---|---|---|---|---|---|
| sherpa-onnx-Engine / SherpaTTS | Standard-Implementierung des Frameworks: `onGetVoices()` iteriert `Locale.getAvailableLocales()`, akzeptiert nur Locales, deren Status dem erwarteten entspricht; da `onIsLanguageAvailable` für `deu` immer `LANG_AVAILABLE` liefert, passt nur das Locale **ohne Land** → eine Voice, Name = `onGetDefaultVoiceNameFor` = normalisiertes Locale, voraussichtlich `"de"` [D] ([AOSP TextToSpeechService.java](https://raw.githubusercontent.com/aosp-mirror/platform_frameworks_base/android14-release/core/java/android/speech/tts/TextToSpeechService.java), [sherpa TtsService.kt](https://raw.githubusercontent.com/k2-fsa/sherpa-onnx/master/android/SherpaOnnxTtsEngine/app/src/main/java/com/k2fsa/sherpa/onnx/tts/engine/TtsService.kt)) | `de` (country leer) | `QUALITY_NORMAL` (300) / `LATENCY_NORMAL` (300) | leer (kein `KEY_FEATURE_NETWORK_SYNTHESIS`) | Region „ohne“ → Rang 3, einzige Stimme der Engine; Qualität 300 wird im Picker als Stufe angezeigt, ist aber ein Framework-Default, **kein** Qualitätsurteil. Welche Piper-Stimme spricht, entscheidet die Engine-App, nicht die Voice. |
| Samsung TTS | Muster `<ll>-<CC>-SMT<typ><nn>`, z. B. `pt-BR-SMTf00`, Locale `por-x-lvariant-f00` [C] → für Deutsch voraussichtlich `de-DE-SMTf00` (weiblich), `de-DE-SMTm00` (männlich), `de-DE-SMTl01` (HD weiblich) [N] | `deu-DEU-…` | [N] | [N] | Region DE → Rang 1; mehrere Stimmen unterscheidbar nur am Namen (f/m/l). Quelle Namensmuster: [flutter_tts #516](https://github.com/dlutton/flutter_tts/issues/516), [samsung-tts Treiber-README](https://github.com/KamiKitsune420/samsung-tts/blob/main/driver/README.md) |
| Google TTS | `de-de-x-nfh-local` / `-network`, `de-DE-language` [C]+[D] | `deu-DEU` | [N] | `-network` trägt `networkConnectionRequired` | Region DE → Rang 1; Offline-Filter entfernt `-network`-Varianten. |
| eSpeak-NG | eigener Name, `QUALITY_NORMAL`, `LATENCY_VERY_LOW` [D] | je Variante | 300 / 100 | `embedded synthesis` | Latenzrang bevorzugt eSpeak vor allen anderen bei gleicher Qualität – unerwünscht, falls parallel installiert. |

---

## 6. Empfehlung (Reihenfolge)

1. **Samsung TTS „Deutsch Stimme 1“ (`lang_de_de_l01`, HD, weiblich)** als Erstkandidat: bereits auf dem Fold7 vorhanden, gratis, offline, weiblich dokumentiert; Nachteil: voraussichtlich keine Wortgrenzen → Lippen laufen über PCM-Hüllkurve [C]. Zu prüfen am Gerät (§7).
2. **Google TTS `de-de-x-nfh-local` (weiblich)** als zweiter Kandidat: einzige Engine mit belastbarer Aussicht auf `onRangeStart`-Wortgrenzen [C]; Stimmenqualität offline [N].
3. **SherpaTTS (F-Droid) mit Piper `kerstin-low` oder `eva_k-x_low`**: einzige Option mit frei lizenzierten (CC0 / M-AILABS) weiblichen Stimmen, am Host nachweislich schnell (RTF 0,05–0,07 bei 1 Thread) und fehlerfrei im Sinne von Abbrüchen; Nachteile: 16 kHz, keine Wortgrenzen, nicht-deterministische Dauer, Voice erscheint nur als `"de"` ohne Region, Modell-Download beim ersten Start. `ramona-low` nur mit Pegelanpassung.
4. **Supertonic 3 (sherpa-onnx-Engine-APK, F1–F5)**: potenziell natürlichste Offline-Option (24 kHz, 2026) – vollständig ungeprüft [N]; Lizenz OpenRAIL-M beachten.
5. Nicht geeignet: RHVoice (kein Deutsch), eSpeak-NG (Referenz; Wortgrenzen erst in künftigem Release), Mimic3/Coqui (keine System-Engine, Projekte ruhend), ML Kit GenAI TTS (Fold7 nicht unterstützt, Alpha, SDK-Pflicht).

Für die Lippen-Synchronisation gilt engine-übergreifend: **Keine der Optionen liefert Phonem-Zeitdaten.** Die ehrliche Aussage bleibt „Hüllkurve + (bei Google) Wortgrenzen“; eine Phonem-Synchronisation darf nicht behauptet werden.

---

## 7. Fold7-Prüfprotokoll (REAL_DEVICE_NOT_RUN)

Gerät: Galaxy Z Fold7, Snapdragon 8 Elite for Galaxy, 12/16 GB RAM, Android 16/One UI 8 (bzw. nach Update Android 17/One UI 9) [D] ([Samsung Newsroom](https://news.samsung.com/global/samsung-galaxy-z-fold7-raising-the-bar-for-smartphones), [GSMArena](https://www.gsmarena.com/samsung_galaxy_z_fold7-13826.php)).

**A. Vorbereitung (je Engine)**
1. Samsung: Einstellungen → Allgemeine Verwaltung → Text-zu-Sprache → Zahnrad bei „Samsung“ → „Sprachdaten installieren“ → Deutsch, Stimme 1 (HD) und ggf. Default voice 1/2 laden [D] ([Samsung Support](https://www.samsung.com/us/support/answer/ANS10003701/)).
2. Google: gleiche Stelle, Zahnrad bei „Google“ → Deutsch → Download der Offline-Stimme [D] (ebd.).
3. SherpaTTS: F-Droid → „SherpaTTS“ installieren → Sprache Deutsch → Modell `kerstin-low` (oder `eva_k-x_low`) laden [D] ([woheller69/ttsEngine](https://github.com/woheller69/ttsEngine)).
4. Bevorzugte Engine umstellen; Flugmodus **ein** (Offline-Nachweis).

**B. In der App (Langdruck TON → Stimmenliste)**
5. Notieren, welche Einträge erscheinen: `Voice.name`, Region, Qualitäts-/Latenzstufe, Offline-Markierung. Erwartung: Samsung `de-DE-SMT…`-Namen; Google `de-de-x-…-local`; SherpaTTS genau ein Eintrag `de` ohne Region (§5). Abweichung = Befund.
6. Automatische Vorauswahl prüfen: Wird bei Samsung/Google die DE-Stimme, bei SherpaTTS die „ohne Region“-Stimme gewählt? Ist eSpeak-NG installiert, prüfen, ob die Latenzregel sie fälschlich bevorzugt.

**C. Hören (Besitzerurteil) – die drei Testsätze S1–S3 sprechen lassen**
7. Je Satz: vollständig gesprochen? Abbruch? „PDF“ buchstabiert? Umlaute „Möglichkeit/gewählt/geändert/Möchtest“ korrekt? ich-Laut in „Ich/Möchtest“ (Piper: Hinweis §4.3 Punkt 4)? Zahlen/Satzzeichenpausen plausibel?
8. Subjektiv: weiblich? natürlich? Tempo bei `rate 0.96`, Tonhöhe bei `pitch 1.0` passend? (Nur der Besitzer urteilt.)

**D. Messen (Logcat / vorhandene Debug-Ausgaben der App)**
9. Zeit von `speak()` bis `onStart` (Latenz) und bis `onDone`; drei Wiederholungen je Satz, Engine-Cold-Start getrennt notieren.
10. Zählen, ob `onRangeStart` eintrifft (Anzahl Aufrufe je Satz; erwartet: Google > 0, Samsung/SherpaTTS = 0) und ob `onAudioAvailable`-Blöcke eintreffen (erwartet: bei allen). Festhalten, welchen Lippen-Modus `AvatarDirector` tatsächlich wählt (PCM / Wort / Text-Tempo).
11. Bei SherpaTTS: Pegel der Hüllkurve vergleichen (ramona ~ −12 dBFS Peak vs. kerstin ~ −5 dBFS am Host) – reagiert der Mund bei leiser Stimme noch?
12. RAM der Engine-App in Einstellungen → Entwickleroptionen → „Laufende Dienste“ ablesen (Host-Referenz Piper-low: ~130 MB nach Laden, ~250 MB nach Synthese, inkl. Python-Overhead – nicht übertragbar).
13. Ergebnis als Tabelle Engine × (Sätze ok / onRangeStart-Zahl / Latenz / Lippenmodus / Besitzerurteil) in dieses Dokument nachtragen und Status von REAL_DEVICE_NOT_RUN auf REAL_DEVICE_RUN setzen.

---

## 8. Offen / ungeprüft (Zusammenfassung)
- Fold7: alles (REAL_DEVICE_NOT_RUN).
- Samsung/Google: tatsächliche `Voice.name`-Werte, Qualitäts-/Latenzfelder, `onRangeStart`-Verhalten – nur Community-Evidenz.
- Google de-DE: Geschlecht weiterer Offline-Stimmen außer `nfh`.
- Supertonic 3: keine Probe erzeugt; Qualität und Android-Engine-Verhalten ungeprüft.
- ASR-Gegenprobe der WAVs nicht durchgeführt.
- Alle Host-Zahlen sind x86-Werte und kein Maß für das Gerät.
