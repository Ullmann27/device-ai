# Unabhängige Orb-QA: ungültige Ereigniswerte – RED/GREEN am mathematischen Kontrollpfad

**Quelle:** PR #539, Head `766a1953fe1b72a890448e38178448d50140fa40`, Original-`ContinuumBrainOrbRenderer.kt`-Blob `8f6862667abc1f5fcd7678c3d885f2ac8b522333`. Der bestehende draw-Pfad berechnet den Kern aus `sin(s.phase ...)` und `s.pulse.coerceIn(...)` ohne Schutz vor NaN / ±∞; Kotlin `coerceIn` macht NaN nicht endlich. Solche Werte können Zeichenparameter verfälschen. Ein solcher Eingang ist auf dem Fold7 nicht nachgewiesen; dies ist ein reproduzierbarer Robustheits-Grenzfall.

**Negativ/Positiv isolierter Kotlin/JVM-Formelprüfung:** Ursprüngliche K-Formel mit NaN-Phase ergibt NaN-Alpha (RED). Dasselbe mathematische Kernmodell nach Normalisierung (`pulse,recall` endlich auf [0,1] begrenzt; `phase` sonst 0) liefert in 343 Kombinationen aus NaN, ±∞ und Randwerten nur endliche und begrenzte Werte (GREEN). **Keine vollständige Kotlin-Projektkompilierung/Android-GPU-Abnahme damit behauptet.**

**Produktkorrektur:** Am Anfang des vorhandenen `draw` einmal neutralisierte Signals bilden. Im gültigen Fall dieselbe `Signals`-Instanz durchreichen. Im ungültigen Fall eine `copy`. Alle Orbit-/Netz-/Kern-/Gehirn-Aufrufe erhalten die gleichen stabilen Werte. Echte Arbeitssignale, Unterbrechung und Idle unverändert; keine Schein-Aktivität, keine zweite Kugel. Neue `ContinuumOrbNonFiniteSignalsTest` prüft die an `ContinuumPainter` übergebenen Float-Parameter für drei negative Szenarien.

**Status:** WIP, neue JUnit-Regression unter dem vollständigen Android-/Gradle-Projekt **NOT_RUN**; kein Fold7, keine APK/Signierung, kein main. Rücknahme: eigenen Zweig auslassen; #539 bleibt unverändert. C1 entscheidet gemeinsame Integration.
