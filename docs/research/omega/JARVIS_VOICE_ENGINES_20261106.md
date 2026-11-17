# JARVIS – natürliche weibliche Stimme: Auswahlregel und Engine-Optionen (06.11.2026, Fable 5.1)

Stand: HOST. Nichts davon wurde auf dem Fold7 gehört (REAL_DEVICE_NOT_RUN). Keine Stimme wird geklont oder mitgeliefert.

## Nachgewiesene Schwachstelle (Quellstand 4d84caa8)

`AgentActivity.refreshOfflineVoice()` wählte `engine.voices.filter { offline && language == "de" }.maxByOrNull { quality }`:

| Problem | Wirkung |
|---|---|
| Nur `quality` zählt | Bei gleicher Qualität entschied die Listenreihenfolge der Engine – die Stimme konnte zwischen App-Starts wechseln |
| Kein Regionsvorzug | `de-AT` (österreichische Aussprache, Wunsch des Besitzers) wurde nicht vor `de-DE` bevorzugt |
| Keine Besitzerwahl | Android liefert **kein Geschlecht** für eine Stimme (`android.speech.tts.Voice` hat Name, Locale, Quality, Latency, Features). Eine weibliche Stimme kann nur der Besitzer hören und wählen – es gab aber keine Auswahl |
| Kein Englisch | Englisch war nicht auswählbar |
| `pitch = 0.86` | `setPitch` verändert die Engine-Ausgabe nachträglich; jede Verschiebung lässt eine natürliche Stimme bearbeitet klingen |

## Änderung (dieser Branch)

- `cognitive/JarvisVoiceSelection.kt` (rein, ohne Android-Typen): Besitzerwahl nach Stimmenname → sonst offline + installiert
  + Sprache → Region (`de`: AT > DE > CH > andere; `en`: GB > US > andere) → Qualität ↓ → Latenz ↑ → Name (stabil).
  Netz- oder nicht installierte Stimmen werden nie gesetzt, auch nicht auf Besitzerwunsch (dann automatische Wahl,
  `ownerChoiceDropped = true`). Test `JarvisVoiceSelectionTest` 7/7 (HOST).
- `AgentActivity`: Langdruck auf **TON** öffnet die Stimmenauswahl (nur Offline-Stimmen der gewählten Sprache, beste zuerst,
  Beschreibung Region · Qualität · Tempo · Name, aktuelle Stimme markiert), Umschalter Deutsch/English; Wahl und Sprache
  liegen in `BootstrapMemoryStore` (`voice_name`, `voice_language`). Nach der Wahl spricht JARVIS einen Satz zur Probe.
- `JarvisVoiceProfile`: `pitch 1.0` (Engine-natürlich), `speechRate 0.96`.
- Unverändert: Halbduplex-Regel (`LocalSpeechInputPolicy.maySpeak`), Äußerungs-IDs, `UtteranceProgressListener` →
  `AvatarDirector.speechStarted/wordRange/speechEnded`, Stückelung langer Antworten (#566).

## Engine-Optionen (recherchiert, nicht gemessen)

Die App spricht über die Android-`TextToSpeech`-Schnittstelle mit der **Standard-Engine des Systems**. Damit stehen dem
Besitzer ohne App-Änderung alle Engines offen, die sich systemweit registrieren und Offline-Stimmen melden:

| Engine | Lokal | Deutsch | Bemerkung |
|---|---|---|---|
| Samsung TTS (One UI) | ja | ja (de-DE) | auf dem Fold7 vorinstalliert; Stimmen/Geschlecht nur per Hören prüfbar |
| Google Speech Services | ja (Offline-Stimmen nachladen) | ja (de-DE) | mehrere Stimmen pro Sprache, Qualität bis `QUALITY_VERY_HIGH` |
| SherpaTTS / sherpa-onnx TTS-Engine (Piper/VITS, Kokoro) | ja, 100 % offline nach Modell-Download | ja (z. B. Piper `de_DE-thorsten`, weitere Piper-Stimmen) | Open Source (Apache-2.0), registriert sich als System-TTS-Engine; Modell-Download aus Hugging Face ist der einzige Netzzugriff ([sherpa-onnx](https://github.com/k2-fsa/sherpa-onnx), [SherpaTTS](https://github.com/woheller69/ttsEngine), [Piper-Modellseite](https://k2-fsa.github.io/sherpa/onnx/tts/all/German/vits-piper-de_DE-thorsten-high.html)) |

Offen und nur am Gerät messbar: Natürlichkeit/Betonung je Stimme (Hörprobe), Latenz bis `onStart` (`VoiceTurnLatencyTrace`),
RAM (`adb shell dumpsys meminfo <engine-paket>`), Akku (`batterystats`), **österreichische** Stimme: keine der genannten
Engines ist für `de-AT` belegt; die Auswahlregel bevorzugt `de-AT`, sobald eine Engine sie meldet.

Nicht getan: keine TTS-Engine in die APK gebündelt (Größe/RAM/Updatekanal), keine Cloud-Stimme, keine Behauptung
phonemgenauer Lippensynchronisation (Wortgrenzen aus `onRangeStart` bleiben die einzige Zeitquelle).

Nächster Schritt am Fold7: Standard-Engine in Android wählen (Samsung / Google / SherpaTTS mit Piper-Stimme), in der App
TON lang drücken, Stimmen anhören, weibliche Stimme wählen; danach Latenz/RAM wie oben messen und hier eintragen.
