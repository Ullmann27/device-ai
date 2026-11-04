# Handover Gedächtnis (Kugel + Memory Activity), 2026-11-04

## Stand
- Branch `continuum/m2-calc-events`, PR #484 (Draft). Letzter Code-SHA `79977eb2a6165dbab32e71016b906ae86a7caf75`.
- Gemergt: #481 @efac2b54, #479 @69b76b2f (ref5-Tokens), motion-math #489 @e600add5 (Feder; vor dem STOPP bereits erledigt).
- Tests auf 79977eb2: BrainOrbSignalsTest 5/5, JarvisMemoryActivityPanelTest 3/3, JarvisMotionTest 14/14 grün.
  Auf d2a15247: RetrievalCalculationSourceTest 4/4, ContinuumMemoryEventQueueTest 3/3, ContinuumRetrievalExperimentTest 2/2.
- Vergleichsbilder (nicht im Git): /workspace/jarvis-design-referenz/kontrolle-3/gedaechtnis-kugel.png,
  gedaechtnis-memory-activity.png, gedaechtnis-memory-activity-live.png (Testdaten aus echtem Temp-Gedächtnis).

## Erledigt (Runde 2)
- Kugel: Gehirn mit Windungen/hellem Kern, violett/cyan Rand-Bloom, Glas-Glanz, feste Partikel, Sockelstufen. Nur #479-Tokens.
- Panel: englische Labels (New memories (today), Context updates, Hypotheses statt "Insights extracted", Total memories),
  Wert links, Label linksbündig; Balken aus echten Ereignissen pro Stunde, ohne Daten gedimmt leer.
- dataFlight: t³ ersetzt durch Easings.easeInCubic (#489).

## Offen
- Kugel: noch weniger volumig als ref5 (Gehirn flacher, Windungen schematisch), Bloom/Staub auf Welten-Optik-Tokens umstellen, sobald vorhanden.
- Panel: Einbau in Slot/Grid durch Kern (siehe unten). Befund: ContinuumLearningCore sendet kein MemoryStored → "Context updates" bleibt 0, bis ein Store-Hook emittiert.
- Widget-Federn (widgetSpring/MotionTimelines.widgets) noch nicht verwendet.
- §12 PDF-/Dokument-Szene auf Kerns ActionScene-Vertrag. Widget-Einflug §6.
- Konflikt LEX4 (#371) vs. LEX3 (Integration) in 5 Dateien.
- 3 rote Layout-Tests aus der Basis: ContinuumAdaptiveCockpitLayoutTest ×2, ContinuumCoreMirrorTest ×1.

## Einbau für Kern
- Kugel: `ContinuumBrainOrbRenderer().draw(p, cx, cy, r, Signals(pulse, recall, hypothesis, phase))`; Signals aus JarvisSequenceEffects.
- Panel: `val tr = MemoryActivityTracker(); ContinuumMemoryEvents.forApp(dir).addListener(tr)` (AutoCloseable schließen);
  `JarvisMemoryActivityPanel.draw(p, l, t, r, b, MemoryActivitySnapshot.from(ContinuumMemoryStatsReader(dir).read(), tr))`, Stats gedrosselt lesen. Referenz 160 × 105 dp.

## Relevante Dateien
- app/src/main/java/at/heinz/deviceai/ui/ContinuumBrainOrbRenderer.kt
- app/src/main/java/at/heinz/deviceai/ui/JarvisMemoryActivityPanel.kt (+ Test JarvisMemoryActivityPanelTest.kt)
- app/src/main/java/at/heinz/deviceai/ui/JarvisSequenceEffects.kt, BrainOrbAnimationRenderer.kt
- app/src/main/java/at/heinz/deviceai/cockpit/RetrievalCalculationSource.kt
- tools/continuum-jvm-preview/src/at/heinz/deviceai/ui/GedaechtnisPreview.kt (JVM-Vorschau)
- Gedächtnis-Kern: #371 `continuum/m2-cognitive-memory` @de333693.

## Nachtrag Sprint 04.11.2026 09:57 Wien
- 5e171d8b3f6dd7019437ce625f7b6c3922c56691: Merge #481-Head 5a1076ca (enthält c4071b7a), konfliktfrei.
- Tests auf 5e171d8b: ContinuumAdaptiveCockpitLayoutTest 3/3, ContinuumCoreMirrorTest 6/6, JarvisMemoryActivityPanelTest 3/3, BrainOrbSignalsTest 5/5 grün.
- ROT: RetrievalCalculationSourceTest.nothingAfterCloseAndThrowingListenerIsIsolated (3/4), zweimal reproduziert: „expected:<null> but was:<Thread[continuum-calc-events-…]>“ – Event-Thread lebt nach close() noch. Calc-Source-Code durch den Merge unverändert; Ursache ungeklärt (Verdacht: Test wartet nicht auf Thread-Ende bzw. Timing). Nächster Schritt: Thread nach letztem close() joinen oder Test mit Zeitlimit auf Thread-Ende warten lassen.
