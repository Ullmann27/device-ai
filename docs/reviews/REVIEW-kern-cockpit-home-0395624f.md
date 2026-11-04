# Review design/jarvis-cockpit-home-20261104 @0395624f (PR #481, Basis d8fd7f7c) – Prüfer, read-only

**Urteil: OK mit Auflagen.** Keine Sicherheitsbefunde, keine erfundenen Zahlen, alle Knöpfe nur Navigation. Aber: Live-Karten werden nie mit Daten gefüllt, und Basis-Rotfehler (Lint/Node/3 JUnit) bleiben bestehen.

## (1) Knöpfe/Pillen → nur über ToolGate/Freigabe
- Aktionsleiste/Tabs lösen nur Navigation aus (`AgentActivity.kt:1118–1141`: Chat, Gedächtnis, Bots, System, Lernen, Recherche-Dialog, Entwicklung, Coding-Task-Entwurf, Diagnose). Keine direkte Werkzeugausführung. ✔
- **Mittel (Spec-Lücke):** Antwort-Pillen aus VIDEO-SPEC 13–15 s („Direkt antworten?“ …) gibt es nicht. Beim Bau Pflicht: Pille → ToolProposal → ToolGate/Owner-Ja, nie direkt.

## (2) Keine erfundenen Zahlen
- `ui/ContinuumCockpitPresentation.kt:60–147`: alle Werte gemessen oder „nicht verfügbar“; Agenten-Balken = verbrauchte Frist (ehrlich beschriftet), kein erfundener Fortschritt. Akku-Temp. aus `ACTION_BATTERY_CHANGED` mit Plausibilitätsprüfung (`ContinuumDashboardCollector.kt:250–258`, Z. 41), ausdrücklich nicht CPU. ✔
- **Mittel:** `CockpitStatus` (Turn-Phase, Worker, Schritte, Dialog) wird nirgends befüllt – `ContinuumDashboardState.kt:19` nur Default; kein `CockpitStatus(`/`CockpitAnimationTriggers(` in `app/src/main` außerhalb der Definition. Prozesse/Agenten/Dialog zeigen daher immer „Noch kein Auftrag/noch nicht gestartet/Noch kein Gespräch“. Ehrlich, aber nicht live; Commit cf0651d5 behauptet „aus echten Quellen“.
- **Niedrig:** `CockpitAnimationTriggers` ohne Aufrufer – VIDEO-SPEC-Animationen (Einflug, Laser, Pillen) nicht sichtbar.

## Design gegen ref1/ref3/VIDEO-SPEC
- Querformat-Cockpit (3 Spalten, Aktionsleiste, Tabs) passt grob zu ref1/ref3; Gehirn-/Agenten-/System-Karten vorhanden. Kein Rendering möglich (kein Emulator/Paparazzi) → nicht visuell verifiziert.
- **Niedrig:** Video-Muster A–H (Z-Tiefe, Pillen, Umschlag, Alarm) nicht umgesetzt; Sidebar-Einträge der Spec („Live-Status“, „Forschung“) nur teilweise.

## Build/Tests (Box, JDK 17)
- `./gradlew assembleDebug lintDebug`: APK gebaut; **lintDebug FAILED** – 1 Error `AndroidOnDeviceSpeechInput.kt:31` NewApi (createOnDeviceSpeechRecognizer API 31, minSdk 26). Datei von ChatGPT-Basis, nicht vom Kern-Bot geändert → vorbestehend.
- Betroffene Tests (aus voller Suite): CockpitAnimationTriggersTest 7/7, ContinuumCockpitPresentationTest 5/5, ContinuumLandscapeCockpitLayoutTest 4/4, JarvisShellLayoutTest 5/5, ContinuumHomeLayoutTest 6/6.
- `./gradlew testDebugUnitTest`: 2234 Tests, **3 failed**, 19 skipped (ContinuumAdaptiveCockpitLayoutTest.everyHeaderAndActionHasUnscaledFortyEightDpTouchArea, ContinuumCoreMirrorTest ×2). Laut Branch-Doku `docs/evidence/jarvis-cockpit-20261104/preexisting-failures.txt` vorbestehend auf d8fd7f7c – **von mir nicht auf der Basis nachgemessen**.
- `node --test .github/continuum/*.test.mjs`: 1516 Tests, 1483 pass, **14 fail**, 19 skip. Auf Basis d8fd7f7c identisch 14 fail (gleiche Testnamen) → vorbestehend.
- **Niedrig:** Tests angepasst: `ContinuumCoreMirrorTest` filtert jetzt `landscapeCockpit` heraus, `ContinuumHomeLayoutTest` alte Querformat-Asserts ersetzt – begründet, aber schwächt die alten Verträge für Querformat.

## Sicherheit / fremde Arbeit
- Keine Manifest-/Gradle-/Abhängigkeitsänderung, keine Berechtigungen, keine exportierten Komponenten, kein Netzwerk. `registerReceiver(null, ACTION_BATTERY_CHANGED)` = nur Sticky-Abfrage, kein Receiver-Export.
- `ChiefWorkerPool.kt:+18`: nur lesender `snapshot()`/`capacity` (fremde Datei von HAUPT, additiv – mit HAUPT abstimmen).
- AgentActivity: nur 6 Zeilen Slot-Routing; Fold-Behandlung und STOP-Knopf nicht angefasst (deren Entfernung stammt aus der ChatGPT-Basis d8fd7f7c, siehe #477-Review).

## Auflagen
1. `CockpitStatus` aus TurnLifecycle/ChiefWorkerPool/Chat in AgentActivity befüllen, Trigger anbinden – sonst Karten dauerhaft leer.
2. Antwort-Pillen nur über ToolGate/Owner-Freigabe.
3. Lint-NewApi (`AndroidOnDeviceSpeechInput.kt:31`) und Basis-Rotfehler vor Integration beheben (Besitzer ChatGPT-Basis).
4. `ChiefWorkerPool`-Änderung mit HAUPT abstimmen.
