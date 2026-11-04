# Recheck PR #483 @97784c1b (Jarvis Sicherheit)

## (1) 60533c94 ownerNegated — Code-Review
- `refusal()`: READ_ONLY/OWN_CONSENT -> null (unverändert), dann `ownerNegated` -> NEGATED_REFUSAL, dann der bisherige Taint-Pfad. Die neue Zeile gibt nur ein zusätzliches Nicht-null zurück; jeder vorher erreichbare Pfad bleibt gleich. **Kann nur mehr blockieren.** Reihenfolge wie gefordert.
- NegatedMoveReviewTest vorhanden, prüft 0 Moves / 0 Rückfragen für Ja und Nein.
- Pflichtfälle (lokaler Test, evidence/RecheckNegationProbeTest.kt, grün): „lösche familie.jpg nicht“/delete_file und „verschieb das nicht“/move_file -> je 0 Ausführungen, 0 Rückfragen (Owner Ja und Nein).

| Eingabe | negiert erkannt | Ausführung (Owner Ja) | Rückfragen | Bewertung |
|---|---|---|---|---|
| nicht löschen | ja | 0 | 0 | ok |
| keinesfalls löschen | ja | 0 | 0 | ok |
| lösch bitte auf keinen Fall | **nein** | 0 | 1 | False Negative, akzeptabel (Owner-Freigabe greift weiter) |
| don't delete / do not delete | ja | 0 | 0 | ok |
| nicht die alten großen Urlaubsfotos löschen (>3 Wörter) | **nein** | 0 | 1 | False Negative, akzeptabel (Freigabe greift) |
| lösche die Datei, nicht die andere | nein | 0 | 1 | kein False Positive, ok |
| nicht vergessen: lösche x.tmp | nein | 0 | 1 | kein False Positive, ok |
| lösche familie.jpg (Kontrolle) | nein | 0 | 1 | ok, normale Freigabe |

Hinweis: In diesem Harness führt delete_file auch nach Owner-Ja nicht aus (Policy-Stub). Entscheidend sind die Rückfragen. Kein False Negative führt ohne Owner-Ja aus.

**Urteil (1): BESTÄTIGT.** Optional: „auf keinen Fall“ und „keinesfalls“ nachgestellt in die Liste aufnehmen.

## (2) 39cd66e5 Sprache — Code-Review
- SDK_INT-Gate korrekt: `createOnDeviceSpeechRecognizer` nur bei SDK>=31; unter 31 Legacy + EXTRA_PREFER_OFFLINE.
- Mikro startet nur aus Klick-Listenern (AgentActivity 3084/7814, Readiness 2603, nach Permission-Grant 9768, MainActivity 245).
- **Befund A (mittel):** `startVoiceInput` ruft `LocalSpeechInputPolicy.route(mode, offlineVeto=true, onDeviceAvailable=speech.available())`. `available()` ist unter 31 jetzt auch für den Legacy-Erkenner true -> Route ON_DEVICE, auch bei Internet AUS / Offline-Veto. Unter 31 kann Audio so an einen Cloud-Dienst gehen, obwohl der Owner Internet ausgeschaltet hat. Vorschlag: unter 31 nur mit expliziter Freigabe (Route EXTERNAL_WITH_EXPLICIT_GRANT) oder `onDeviceAvailable` nur für Engine ON_DEVICE.
- **Befund B (niedrig):** Veraltete Texte: `LocalSpeechInputPolicy.kt:4` „The actual UI uses on-device ASR only“; Fehlertexte „lokale Spracherkennung … Es wird kein Cloud-Ersatz gestartet“ (Z. 18/20) — unter 31 nicht garantiert. Nutzer sieht keinen Hinweis, dass unter 31 online erkannt werden kann.
- Fold7 (API 31+) nicht betroffen.

**Urteil (2): BESTÄTIGT mit Auflage A** (Route unter API 31 trotz Internet AUS; fürs Fold7 nicht relevant) und B (Texte).

## Läufe @97784c1b + lokaler Probe-Test
- Guard, BoundedAgent, Speech, Negated und Probe: 66 Tests, 0 Fehler.
- Volle testDebugUnitTest: 2227 Tests (2224 + 3 lokale), 0 Fehler, 19 skipped.
- lintDebug: 0 Issues, NewApi weg.
