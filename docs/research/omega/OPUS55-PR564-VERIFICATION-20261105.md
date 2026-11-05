# PR #564 – Verifikation (05.11.2026, Claude Opus 5.5)

## Hänger im ersten Android-Lauf (reproduziert, Ursache eingegrenzt)
- `:app:testDebugUnitTest` lief 22 min ohne eine einzige Ergebnisdatei; Test-JVM 150 % CPU.
- Thread-Dump (`OPUS55-PR564-HANG-THREADDUMP-20261105.txt`): `ContinuumCockpitHandsFreeUiTest.kt:44`
  → `ActivityController.setup()` → `ShadowPausedLooper.idle()` → Haupt-Looper wird nie idle.
- Unterschied zu den funktionierenden Cockpit-Tests: Startzustand `native-head-3d=false` **ohne** `native-head`
  ⇒ Kopf aus. Mit Kopf an (wie `ContinuumReferenceCockpitUiTest`) startet dieselbe Activity normal.
- Testkorrektur: gleicher Startzustand wie die bestehenden Cockpit-Tests.
- **Offener Produktbefund (nicht behoben, nicht auf Gerät geprüft):** App-Start mit ausgeschaltetem Kopf
  erzeugt im Robolectric-Looper eine endlose Folge sofortiger Nachrichten. Kandidat:
  `ContinuumHomeView.layoutAvatarSlot` reiht bei jedem Layout `avatarSlotUpdate` ein, solange kein Renderer
  andockt. Reproduktion: Prefs `continuum-avatar-appearance` = {native-head-3d:false}, Activity `setup()`.
  Auf dem Fold7 prüfen: CPU/Akku bei „Kopf aus“.
