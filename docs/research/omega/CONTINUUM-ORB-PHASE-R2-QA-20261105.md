# Continuum Bot 3 – Phase-R2, unabhängige Quellen-QA (05.11.2026)

**STATUS:** TEST_SOURCE_ADDED; numeric formula RED/GREEN ausgeführt; vollständiger Android-/AGP-/Robolectric-/Lint-/GPU-/Fold7-Lauf **NOT_RUN**. Kein Original-APK-Release.

## Eingänge

- C1 #519 `13c37f55789bf67b07beeb37cc1a5035526e623e`; gemeinsamer #527 `19039e5b822310cd5ab1ce5cc5cb854822a1bcd3`.
- Original-Kugel #539 `766a1953fe1b72a890448e38178448d50140fa40` mit Renderer-Blob `8f6862667abc1f5fcd7678c3d885f2ac8b522333`, sechs Pfade.
- Isolierte bisherige Grenzwertreparatur #542 `3357c6ac9ad347f93b4aa80690ce8a00df5e2a1c`, Renderer-Blob `761f4bd24b96e8989803a9540fee9152efbcf36f`.
- #535 `4bbf2863dc3c7debe74ac81f3d16521926c8ae0a` bleibt eigenständig WIP; keine blinde Übernahme.

## Reproduzierter Restfehler

#542 filtert nur `!phase.isFinite()`. `Float.MAX_VALUE` und `-Float.MAX_VALUE` sind endlich, aber `phase * 2f * PI.toFloat() * .35f` läuft über und `sin(±Infinity)` liefert `NaN`. Resultat: nicht-endlicher `fillCircle`-Alpha-Wert, der auch nach `coerceIn(0f, 1f)` nicht repariert wird. Kein bekannter Fold7-Feldfehler.

**Minimaler Fix:** Vor Weitergabe an dieselbe Originalkugel nur Phasen innerhalb `[-1_000_000f, 1_000_000f]` akzeptieren; außerhalb auf neutrale Phase 0, ebenso NaN/∞. Im normalen Zeitbereich unverändertes `Signals`-Objekt; `pulse`, `recall`, `processing`, Fills, Canvas, GLB, API, Statusquellen unverändert. Die Grenze ist ein defensiver Werte-/Trig-Präzisionsschutz und **keine** neue Zeitquelle.

## Tests, ausgeführt versus offen

Reproduzierbarer reiner Kotlin-Formeltest: `kotlinc -script docs/evidence/orb-phase-qa-20261105/orb_phase_numeric_smoke.kts`.

- Vorher #542-Formel: RED bei `±Float.MAX_VALUE`; beide ergeben NaN.
- Nachher: 60/60 Grenzwert- und Verarbeitungskombinationen endliche Alpha-Werte in [0,1].
- Idle: vier Phasen, Spannweite >0,02; jede Leuchtstärke mindestens 0,15 unter dem aktiven Vergleich. Ist eine **Formelprobe**, nicht die Ausführung der Projekttests.
- Die vorhandene negative JUnit `ContinuumOrbNonFiniteSignalsTest` behält ihre 3 ursprünglichen Szenarien und erhält zusätzliche Werte `±Float.MAX_VALUE` und `1_000_000f`. Der bestehende `ContinuumOrbCoreIntegrationTest` behält beide ursprünglichen Testmethoden und ergänzt einen expliziten Idle-vs-Aktiv-Test.
- Die JUnit-Tests selbst sind **NOT_RUN**: kein vollständiger isolierter Checkout, kein Android SDK / Gradle in der lokalen Umgebung; `git ls-remote` über GitHub scheiterte mit DNS-Auflösungsfehler. Host-JVM-Formeltest darf nicht als Android-/Java2D-/GPU-Abnahme ausgegeben werden.

**Pfadprüfung:** Drei vorhandene Pfade in eigenem QA-Zweig ergänzt; keine weiteren App-/Avatar-/Neon-/Handoff-Quellpfade geändert. Quelle des PRODUCTION-Fix: derselbe Original-`ContinuumBrainOrbRenderer`.

## C1-Übergabe

PR #539 → PR #542 → **diesen separaten R2-PR** einzeln auf exakt denselben geprüften Integrationskopf bringen, nicht #535 pauschal mergen. Erst `ContinuumOrbCoreIntegrationTest` und `ContinuumOrbNonFiniteSignalsTest` als echte Projekt-JUnit, dann vorhandene negativen/positiven Orb-/UI-Fälle, danach vollständige Android-/Node-/Backend-/Lint-/Release-Gates der eingefrorenen **kombinierten SHA**. Host-/Java2D-/Android-GPU-/Fold7-Pässe strikt getrennt. Kein `main`, GitHub Actions, Render-Deploy, Signieren oder APK-Button ohne vollständigen Release-Nachweis. Rücknahme: diesen zusätzlichen R2-Zweig auslassen.
