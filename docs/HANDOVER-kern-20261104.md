# Übergabe Jarvis Kern – 04.11.2026 (für ChatGPT Astra)

Branch `design/jarvis-cockpit-home-20261104`, Draft-PR #481. Bindend: /workspace/JARVIS-DESIGN-BILDBESCHRIEB.md §11 (ref5 pixelgenau) und §12 (Animation für jede Aktion). Architektur: docs/ARCHITEKTUR-JARVIS.md.

## Stand (Commits auf diesem Branch)
- afc660cc: Cherry-Pick c4071b7a leer, Inhalt bereits über 5f846eb7 enthalten. Laut Commit sind ContinuumAdaptiveCockpitLayoutTest und ContinuumCoreMirrorTest grün.
- 80af1c15: Merge der ref5-Tokens von #479 @69b76b2f (docs/JARVIS-REF5-FARBEN.md, JarvisNeon.tile/button/pill).
- dec2c706 / b131a267: ref5-Raster Fold7 offen (`ui/JarvisCockpitGrid.kt`, `ui/JarvisCockpitGridContent.kt`), nur mit #479-Tokens.
  - USER_CARD-Slot und Vertrag OwnerRecognition (`cockpit/CockpitContracts.kt`) mit Quelle `cockpit/KeyguardOwnerRecognitionSource.kt` (nur das echte Signal Geräte-Entsperrung).
  - Vergleichsbild-Werkzeug `scripts/kern-cockpit-grid-preview.sh`, Ausgabe außerhalb von git: /workspace/jarvis-design-referenz/kontrolle-3/kern-cockpit-raster.png.
  - Laut Commits Tests JarvisCockpitGridTest, JarvisShellLayoutTest und JarvisShellContentTest grün (20/20, 16/16).
- 6c77f39c: Kopf-Registrierung (JarvisHoloAvatar im HEAD-Bereich, onDestroy meldet ab). Laut Commit UI-Tests 4/4 grün.
- Volle Testsuite für den Head NICHT gelaufen.

## Offen (Reihenfolge)
1. Dashboard-State-Felder für das Panel „Local Model Status“ von Sicherheit: freeRamBytes (ActivityManager.MemoryInfo.availMem), thermalStatus (PowerManager.currentThermalStatus ab API 29, sonst null), Lade-Freigabe (sonst null, Anzeige „—“).
2. ActionScene-Vertrag (§12) in at.heinz.deviceai.cockpit:
   - `CockpitAnimationEvent.ActionScene(turnId, atMillis, kind, toolName, sourceLabel, appId?, phase, items, question?, options)`
   - `ActionKind { MESSAGE_APP, DOCUMENT, DEVICE_FILE, WEB, APP, OTHER }`
   - `ActionPhase { REQUESTED, APPROVED, RUNNING, DONE, DENIED, FAILED }`
   - `ActionSceneItem(id, title, subtitle?, iconRef?, excerpt?)`
   - `ActionOption(id, label, toolName?, enabled, disabledReason?)`
   - `ActionSceneRenderer { supports(kind, toolName); play(event, home): Boolean }`
   - `ActionSceneRegistry { register, unregister, setDefault, resolve }`
   - `ActionContentSource` und `ActionContentSources`
   - `ToolGateActionSceneBridge` an ToolGate (AgentToolLayer.kt:119)
   - `PillActionGate.requestOption`
   - eingebaute Minimal-Szene als Rückfall
   - Szenen-Besitzer: Sicherheit MESSAGE_APP, Gedächtnis DOCUMENT, Lückenfüller DEVICE_FILE und WEB, Integration Standard-Szene.
3. CalmNotice(turnId, atMillis, sourceLabel, message, options) für normale Fehler; AlarmRaised.options. Die große Szene H nur bei SECURITY_FINDING.
4. #489 (Mathematik, MotionLibrary-Defaults) in diesen Branch mergen. Echte AudioLevelSource-Instanz bauen und Wellenform anbinden (bis dahin audioLevel 0).
5. #484 anbinden:
   - `bus.subscribe(BrainOrbAnimationRenderer(home.jarvisSequenceEffects, SystemClock::uptimeMillis))`
   - `RetrievalCalculationSource.addListener { bus.emit(it) }`
   - Orb-Ziel ist die Mitte des Kopf-Slots.
6. REVIEW-3: Icons Sprechblase/Krone weichen von ref5 ab (Besitzer Kern bzw. Integration).
7. Überlagerung über fremde Apps (SYSTEM_ALERT_WINDOW) bleibt aus, bis Heinz sie freigibt.

## Regeln
Kein Merge nach main, kein Force-Push, nichts löschen, PR bleibt Draft, keine Actions auslösen, nur echte Daten („—“/„offline“ sonst), Paket at.heinz.deviceai.continuumlocal und Signatur unverändert.
