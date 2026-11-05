# 3D-Avatar: natürliche Blinkintervalle – separater Test-Slice

Quellbasis: Design-PR #535, Head `4bbf2863dc3c7debe74ac81f3d16521926c8ae0a`. Ursprüngliches Produktionsblob `31801578f47f6a677c58283a408bc2840297de6d`.

Keine neuen Timer, Zufallsquellen, Sensoren oder Zustände. Innerhalb des vorhandenen 460-Sekunden-Clock werden die wiederholten 4,6-Sekunden-Blinzelzeitpunkte in der Nähe des ursprünglichen Endes durch eine deterministische, pro Slot begrenzte Vorverlagerung von 0–0,44 s aufgelockert. Erster Zyklusanker und 460s-Wrap bleiben unverändert. Der bestehende sekundäre Doppellidschlag folgt dem jeweiligen ersten mit 0,30 s Abstand; pro Zyklus bleiben 100 Primär- und 20 Zusatzblinzler.

**Tatsächlich isoliert geprüft:** `kotlinc` (Kotlin-Host, kein Android Gradle) kompiliert die Produktionsklasse zusammen mit einem eigenständigen Smoke-Test, der 92.001 Zeitpunkte über 460 s abtastet. Ergebnis: 120 Blinzler, Gewichte in [0,1], erster Blink bei 4,525 s, geschlossener 460s-Wrap, Hauptintervallbereich 3,965–5,255 s. **JUnit-Datei in diesem PR noch nicht über AGP ausgeführt; Android-/GPU-/Fold7-/Audio-Test NICHT AUSGEFÜHRT.**

Original-GLB und 21 Morphs, AvatarDirector, Audio/STOP, Originalpaket, Signatur, Besitzerdaten und Offline-Schalter unverändert. Rücknahme: diesen Zweig auslassen. C1 übernimmt erst nach eigener Projekt-Abnahme.
