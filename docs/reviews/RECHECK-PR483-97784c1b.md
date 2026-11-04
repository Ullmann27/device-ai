# Recheck PR #483 @97784c1b (Jarvis Sicherheit) — ZWISCHENSTAND

## (1) 60533c94 ownerNegated — Code-Review
- `refusal()`: READ_ONLY/OWN_CONSENT -> null (unverändert), dann `ownerNegated` -> NEGATED_REFUSAL, dann der bisherige Taint-Pfad. Die neue Zeile gibt nur ein zusätzliches Nicht-null zurück; jeder vorher erreichbare Pfad bleibt gleich. **Kann nur mehr blockieren.** Reihenfolge wie gefordert.
- NegatedMoveReviewTest vorhanden, prüft 0 Moves / 0 Rückfragen für Ja und Nein.
- Probe-Ergebnisse: folgen (Tests laufen).

## (2) 39cd66e5 Sprache — Code-Review
- SDK_INT-Gate korrekt: `createOnDeviceSpeechRecognizer` nur bei SDK>=31; unter 31 Legacy + EXTRA_PREFER_OFFLINE.
- Mikro startet nur aus Klick-Listenern (AgentActivity 3084/7814, Readiness 2603, nach Permission-Grant 9768, MainActivity 245).
- **Befund A (mittel):** `startVoiceInput` ruft `LocalSpeechInputPolicy.route(mode, offlineVeto=true, onDeviceAvailable=speech.available())`. `available()` ist unter 31 jetzt auch für den Legacy-Erkenner true -> Route ON_DEVICE, auch bei Internet AUS / Offline-Veto. Unter 31 kann Audio so an einen Cloud-Dienst gehen, obwohl der Owner Internet ausgeschaltet hat. Vorschlag: unter 31 nur mit expliziter Freigabe (Route EXTERNAL_WITH_EXPLICIT_GRANT) oder `onDeviceAvailable` nur für Engine ON_DEVICE.
- **Befund B (niedrig):** Veraltete Texte: `LocalSpeechInputPolicy.kt:4` „The actual UI uses on-device ASR only“; Fehlertexte „lokale Spracherkennung … Es wird kein Cloud-Ersatz gestartet“ (Z. 18/20) — unter 31 nicht garantiert. Nutzer sieht keinen Hinweis, dass unter 31 online erkannt werden kann.
- Fold7 (API 31+) nicht betroffen.
