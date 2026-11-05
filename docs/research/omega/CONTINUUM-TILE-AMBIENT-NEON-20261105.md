# Laufend animierte Neon-Kacheln (05.11.2026)

**Isolierter Branch-Baustein auf #535**, exakt `4bbf2863dc3c7debe74ac81f3d16521926c8ae0a`.

Die sichtbare `JarvisShellView` aktiviert jetzt die vorhandene `ambientMotionEnabled`-Schranke standardmäßig, aber nur bei sichtbarem/fokussiertem Fenster und erlaubt gesetzter Animationsdauer sowie ohne Energiesparmodus. Nach dem ursprünglichen Einflug nutzt sie eine gedrosselte 66-ms-Aktualisierung (ca. 15 Hz) statt dauerhaft 60 VSync-Invalidierungen. Bei unsichtbarer Oberfläche, reduzierter Animationsdauer oder Fokusverlust stoppt die Auffrischung. Es wird **kein neuer Timer, Thread oder eigenständiger Animationsclock** eingeführt.

`JarvisShellRenderer` leitet seine bereits vorhandene `timeMs`-Uhr an `JarvisTiles.drawSurface` weiter. Ein sanfter, 14-Sekunden-zyklischer, phasenversetzter Cyan→Violett→Magenta-Randglanz wird auf den vorhandenen Buttons dargestellt. `DISABLED` erhält keinen Glow, `LOADING` bleibt ausschließlich an echte Caller-States gebunden. Der Zeichenpfad verwendet zwei leichte Konturstriche, keine Per-Frame-Gradient-/Textur-Generierung; der vorhandene `JarvisNeon`-Materialpfad bleibt erhalten. Keine Backend-/Nutzer-/Fremd-App-Behauptung.

Eine neue `JarvisTileAmbientNeonTest` kontrolliert Farbwerte, Kontinuität, zyklischen Übergang, Mehrkosten von zwei primitiven Randstrichen und unterdrückte Animation deaktivierter Kacheln.

**Gates:** Neuer JUnit-Test noch NICHT mit Projekt-Gradle/Android ausgeführt. Für diesen neuen Quellstand keine vollständige Geräte-/GPU-/Performance-Abnahme und keine APK. C1 muss nach selektiver Integration die tatsächliche Animation und Energie-/Thermalschutz auf Fold7 prüfen. Frühere #535-Host-Tests sind historische Belege, nicht diesem Slice vererbt.

Bewahrt Originalsignatur, Paket-ID, GLB und Original-Gehirnkugel, 21 Morphs, Offline-Einstellung, Nutzerdateien, main und bestehende Release-Pipeline. Rücknahme: diesen Draft-Slice auslassen.
