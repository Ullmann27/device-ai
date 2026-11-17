# Code 49 – Originalstimme »Continuum / JARVIS« – Voice Design und Integration

Status: VOICE_DESIGN_PROMPT_PREPARED / ELEVENLABS_ACCOUNT_NOT_ACCESSIBLE_IN_CURRENT_CHAT / IMPLEMENTATION_NOT_STARTED.
Basis: 88dfd45139471463618673a1f827ee062e9bf6d5 (isolierter Zweig von Code49 PR #594). Der laufende Code49-QA-Run 42370446205 und dessen SHA bleiben unverändert.
Kein Schlüssel, Zugangstoken, privates Konto, E-Mail-Adresse oder Nutzer-Audio wird hier abgelegt. Keine API-Kosten, keine Audiogenerierung ausgelöst.

## Zielstimme

Arbeitstitel: **Continuum Original Voice V1**. Eigene männliche Stimme für die lokale Android-KI, nicht die Stimme eines Schauspielers oder einer bekannten Filmfigur.

### ElevenLabs Voice Design – Feld »Voice description«

An original male German-speaking AI companion voice, age 35 to 45, a low, velvety baritone with rich but controlled resonance. Native Standard German with an authentic, very subtle Austrian cadence, especially natural Viennese/Lower Austrian sentence melody, never a caricature. Intimate studio clarity, excellent diction for technical terms, smooth transitions, human micro-pauses and gentle breaths. Calm, quietly confident, attentive and intelligent. Warm and reassuring in a warning, quick and precise when confirming an action, subtly playful in relaxed conversation. Moderately paced, expressive without melodrama. Avoid metallic processing, exaggerated robotic effects, artificial bass, sales-announcer energy, movie-character impersonation and celebrity resemblance. Distinctive, modern, trustworthy, like a skilled personal copilot.

Zeichenanzahl: 841 (zulässiger Bereich laut Voice Design 20–1000).

### Feld »Text zur Vorschau«

Servus. Ich bin Continuum. Schön, dass du da bist. Ich habe deinen Auftrag verstanden. Jetzt überprüfe ich zuerst, welche Schritte tatsächlich möglich und erlaubt sind. Dein Dokument ist ausgewählt; ich kann dir den Inhalt in einer holografischen Arbeitsansicht zeigen. Eine wichtige Information: Das Gerät wird gerade warm. Deshalb reduziere ich die grafische Belastung, während wir weiterarbeiten. Wenn du Stopp sagst, halte ich sofort an. Alles erledigt? Dann zeige ich dir jetzt die Ergebnisse – klar, nachvollziehbar und ohne leere Versprechen.

Zeichenanzahl: 549 (zulässiger Bereich laut Voice Design 100–1000).

## Vorläufige Integration – bewusst nicht implementiert

1. Den Besitzer selbst in ElevenLabs aus drei Voice-Design-Vorschauen wählen lassen, dann nur die freigegebene `voice_id` übernehmen. Kein Account- oder Credits-Zugriff aus ChatGPT nachgewiesen.
2. **Optionaler, ausdrücklich bestätigter Online-TTS-Kanal.** Lokales Gemma-Gehirn bleibt lokal. Ohne Netz, Einwilligung oder Guthaben verwendet Continuum unverändert Android-TTS bzw. die bestehende offline installierte Stimme.
3. Eine sichere BYOK-Eingabemaske in der Android-App vorsehen; Zugangsschlüssel niemals in Git, APK, Release-Manifest, GitHub Actions, Logcat oder Chat. Vertrauliche Antworttexte dürfen nicht standardmäßig an ElevenLabs gesendet werden. Benötigt separate Freigabe und transparente Kennzeichnung, dass Antworttext den Anbieter erreicht.
4. Für reaktionsschnelle Sprachantworten `eleven_v4_turbo` in Betracht ziehen und nur freigegebene Satzsegmente an die API geben; `eleven_v4` nur für längere hochwertige Voiceovers. Aktuelle Modelle und Kompatibilität bei der tatsächlichen Implementierung erneut prüfen. Das beschleunigt nicht die lokale Modellinferenz.
5. Eingangs-/Ausgangstext streng begrenzen. Separate Abrechnungsgenehmigung, Ausgabelimit und STOP/Session-Gate. Bei Fehlermeldung/Timeout keine verborgenen automatischen Credit-Retries.
6. Für den Avatar liefert der offizielle Endpoint `POST /v1/text-to-speech/{voice_id}/with-timestamps` Audio plus **Zeichen-Zeitstempel**. Diese sind keine garantierten Phonem-Timings: Viseme M/B/P und Vokale müssen gesondert geprüft und mit dem tatsächlichen Audio-Playback-Takt synchronisiert werden. PCM-RMS kann den vorhandenen `TtsHologramSignal`/Avatar-Pfad ergänzen.
7. Ein einziger Audio-Player pro Session; akustisches STOP und Lippen-STOP in derselben Generation. Keine Audioausgabe, die nach Sprecherwechsel/STOP/Background oder Prozessneustart wieder startet.
8. Keine ungefragten Background-Aufnahmen. **Scribe v2 Realtime** für Spracherkennung erst optional nach ausdrücklicher Mikrofonfreigabe und Datenschutzhinweis; bestehende lokale Spracherkennung weiterhin verfügbar.
9. Ergänzende synthetische Sounds für bestätigte Tool-Ereignisse möglich: kleine Hologramm-Whooshes, erfolgreiche Übergabe, Warnhinweis – sparsam und nur bei wirklicher Aktion, nicht dauernd als Fake-Aktivität. Nicht über Speech-Credits-Stimme hinweg unnötig Credit-Verbrauch produzieren.
10. Getrennte **nachgewiesene** Host-/Android- und Fold7-Abnahmen: 5 gesprochene Dialogwechsel, Stop innerhalb laufender Wiedergabe, falscher/zurückgezogener API-Schlüssel, Netzverlust, Kein-Guthaben-Antwort, deutsche Aussprache, Stimmenwahl, offene/geschlossene Klappe, CPU/GPU/Temperatur, Kosten- und Datengrenzen. Kein "fertig" ohne Gerätetest.

## Quellen und technische Grenzen

- https://elevenlabs.io/docs/de/eleven-creative/voices/voice-design
- https://elevenlabs.io/docs/eleven-api/choosing-the-right-model
- https://elevenlabs.io/docs/api-reference/text-to-speech/convert-with-timestamps
- https://elevenlabs.io/docs/api-reference/text-to-speech/stream-with-timestamps
- https://elevenlabs.io/docs/help-center/product/core-capabilities/text-to-speech/do-you-offer-an-ai-model-for-conversational-purposes-or-for-chatbots

**Wichtig:** Dies ist eine nachvollziehbare Spezifikation, kein erstelltes ElevenLabs-Voice-Asset, kein API-Adapter, keine gesetzten Credits, keine APK. Die Veröffentlichung der Code49-PR benötigt weiterhin ihren eigenen vollständigen QA- und Geräte-Nachweis. 
