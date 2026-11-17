# Kamerablick im Hauptdialog · 05.11.2026

Basis: `7ecdafac328700295da393ce44bbe22eb8b21aee` (#544, WARM-Kompilierfix enthalten).
Status: `SOURCE_INTEGRATED`, neue Android-/Robolectric-Tests zunächst `NOT_RUN`.

## Verhalten

- Erste Frage nach dem nächsten Vordergrundstart, sobald die Startbegleitung geschlossen ist: „Darf ich dich beim Gespräch anschauen?“.
- „Nein“, „Später“ und Zurück lassen die Kamera aus. Das gespeicherte Flag `camera-question-shown-v1` verhindert erneutes ungefragtes Anzeigen; es speichert keine Kamerazustimmung.
- Kameraknopf im Composer und bestehender Dashboard-Kameraeintrag öffnen dieselbe Freigabefrage jederzeit erneut.
- Android-Berechtigung startet keine Sitzung. Nach der Berechtigung erfolgt ein neuer ausdrücklicher Start über den Kameraknopf.
- Eine gestartete Sitzung bleibt beim Schließen der Startbegleitung im Hauptdialog bestehen. Die sichtbare Leiste „KAMERA AN · STOPPEN“ beendet sie unmittelbar.
- App-Pause, Destroy, Konfigurationswechsel und Ablösen des gesamten Hauptfensters stoppen die Kamera. Es gibt keinen automatischen Wiederanlauf.
- Nur lokale Gesichtspositionen erreichen den bestehenden AvatarDirector. Es gibt keine Bildspeicherung, Übertragung oder persönliche Gesichtserkennung; keine neue Face-ID-Einrichtung wird behauptet.

## Prüfung

`git diff --check`: bestanden vor Commit. Acht neue Host-UI-Tests in `ContinuumConversationCameraUiTest` prüfen Frage/Nein/Später, Permission ohne Start, sichtbaren Stop, Pause mit späterem Button-Callback, Konfigurationswechsel, Gesprächs-Gaze und Hintergrundschutz. Der bestehende Setup-Dismiss-Test prüft den ausdrücklich veränderten Sitzungsumfang.

Root führt die gemeinsame Android-Suite aus. Zu prüfen: `ContinuumConversationCameraUiTest`, `ContinuumReadinessGuideUiTest`, `AndroidAvatarCameraInputUiTest`, `ContinuumReadinessGuideTest`, `AvatarCameraPolicyTest`, `AvatarObservedGazeTest`, Composer-/Fold-Layoutregressionen. Die Tests emulieren weder Kamera-Hardware noch echte Gesichtserfassung. Fold7-/Android17-Kamera, GPU, Audio und End-to-End bleiben bis physischer Prüfung `NOT_RUN`.

## Kompatibilität und Rückweg

EXP-05/06/11/12 bleiben unverändert kompatibel. Keine Änderung an Gedächtnis, Modell, Owner-Toolgates, Spracheingabefreigabe oder Paket/Signatur. TTS ist bereits über echte `onStart`-/`onRangeStart`-Callbacks mit AvatarDirector und PCM-Anker verbunden; Tempo `0.93f` bleibt unverändert. Rückweg: ausschließlich diesen Kameracommit revertieren, keine Einstellungen oder Gedächtnisdateien löschen.
