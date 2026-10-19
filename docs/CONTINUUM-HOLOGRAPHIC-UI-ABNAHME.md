# Device AI Continuum – Hologramm-UI und Android-Abnahme

Der Android-Startbildschirm verwendet dieselbe native Kotlin/Canvas-Grafik wie
bisher, jetzt mit einem **einmalig gerenderten 340×234-Bitmap-Backdrop**,
transparentem Hintergrund und hardwarefreundlichen cyan-/violettfarbenen
Status-Orbits. Es gibt keinen Bildserver, keinen Remote-Asset-Download und
keinen simulierten Modellfortschritt.

**Sechs echte Oberflächenzustände:** BEREIT, SPRACHEINGABE,
DENKT, SPRICHT, FORSCHT und LERNT. Sie hängen an den vorhandenen
`AgentActivity`-Aufrufen für `setBusy`, `runResearch`, `runNeuralCommand`,
`startVoiceInput` und der Android-TTS-Utterance-Rückmeldung.

Die neun Balken im Hologramm zeigen bei SPRICHT **nur dann einen dynamischen
Ausschlag**, wenn `UtteranceProgressListener.onAudioAvailable` tatsächlich
16-Bit-PCM liefert. Kann die gewählte Android-TTS-Engine keine PCM-Pakete
bereitstellen, bleibt die Amplitude bewusst flach; nur die allgemeinen
Status-Orbits bewegen sich. Die Darstellung behauptet weder
Mikrofon-Audiomessungen noch neuronale Gewichtsänderungen.

Animationen werden bei ausgeschalteten Systemanimationen unterdrückt, beim
Abhängen des Views gestoppt und ohne verbleibende Handler-Callbacks freigegeben.
Die aufwendigen Schatten/Gradienten werden nicht in jedem Animationsframe
neu berechnet. Wichtige Composer-Schaltflächen haben mindestens 48 dp Höhe
oder Breite. Der dekorative Canvas ist für Screenreader nicht fokussierbar;
der danebenliegende lesbare Status ist zugänglich.

**Nachvollziehbare Prüfungen:**

- `node --test .github/continuum/android-hologram-ui-wiring.test.mjs` –
  statischer Vertragstest für Activity-/View-Verkabelung, Audio-Callback,
  Lifecycle und Touch-Größen
- `./gradlew :app:testDebugUnitTest --tests at.heinz.deviceai.ContinuumPcmLevelTest`
  – JVM-Prüfung für Stille, PCM16-Samples, ungerade Pakete und Sample-Budget
- `./gradlew :app:assembleDebug` – vollständiger Android-Compiler- und APK-Test
- Android-Emulator/Samsung: Screen vollständig aufnehmen und prüfen,
  dass Hologramm, Begrüßungszeile und sechs Aktionsbereiche weder bei
  normaler noch bei 150-%-Schrift abgeschnitten sind; Chat-Komponist muss
  mit offener Tastatur sichtbar und bedienbar bleiben; Sprachein-/ausgabe
  sowie Rückkehr aus der externen Recognizer-Activity testen
- UI-Leistung: Frame-Drops/Batterieverbrauch bei 10 Minuten idle/sprechen
  mit Android-Profiler messen; insbesondere auf dem Foldable-Layout testen

Die reine Kotlin-PCM-Berechnung wurde gesondert mit `kotlinc` und vier
synthetischen Prüfpaketen erfolgreich durchgerechnet. **Ein echter
Emulator-/Geräte-Screenshot, die vollständige Gradle-Kompilierung und
die visuelle Design-Abnahme liegen für diesen Branch noch nicht vor.**

PR bleibt bis zu diesem Nachweis DRAFT. Besonders zu prüfen ist die
Integration mit Sols noch nicht veröffentlichten UI-Änderungen: Beim
Übernehmen beide Zustands-/Audiopipelines vergleichen, keine unkontrollierte
Überschreibung von bereits implementierter Funktionalität und kein Merge in
`main` ohne Review.
