# Bot 2 — isolierter Cyan→Blau→Violett→Magenta-Randfluss für echte Jarvis-Kacheln

Stand: 05.11.2026 · **SOURCE_STAGED / KOTLIN_HOST_MATH_PASS / ANDROID_NOT_RUN**.

## Quellen und Abgrenzung

Basis: #539 `766a1953fe1b72a890448e38178448d50140fa40` (Orb-Slice auf #527);
gelesen: #535 `4bbf2863dc3c7debe74ac81f3d16521926c8ae0a`,
#527 `19039e5b822310cd5ab1ce5cc5cb854822a1bcd3` und
`CONTINUUM-UNIVERSAL-NEON-DESIGN-20261105.md`, die bestehenden
`JarvisDesign`, `JarvisNeon`, `JarvisReferenceHud`, `JarvisShellRenderer`,
`JarvisSceneFills`, `JarvisEnergyFlows` und `JarvisHeadAura`.

Reproduzierter Ausgangszustand: `JarvisTiles.drawSurface` malte unveränderliches Glas,
Rand, Glanz und bei *explizitem* LOADING nur das statische Zeichen „…“; der
`JarvisShellRenderer.draw` besaß bereits `timeMs`, leitete ihn jedoch nicht
an die Kacheln weiter. Es gab daher keinen zeitgebundenen Kachel-Randfluss.

## Exakte Änderungen

- `JarvisTileEnergy.kt` (neu): rein dekorativer, 13-s-periodischer und geschlossener
  Verlauf **innerhalb** des Kachelrandes; deterministisches Ziel-Offset, auch für
  negative/extreme Zeiten endliche Koordinaten, keine eigens laufende Uhr.
- `JarvisTiles.kt`: 64 vorab gebildete Farbstufen aus bestehenden
  Design-Tokens Cyan, `cardRimEnd` (Blau), Violett, Magenta. Ein kleiner
  transparenter Halo und ein maximal 1,35-dp-Hotspot auf dem Kachelrand;
  kein Leuchten auf Text, keine neue Hitbox, keine Berührung des Avatars.
  Deaktivierte sowie unter 48dp kleine Kacheln erhalten keinen Fluss.
  PRESS/SELECTED erhöhen nur den Glanz; weder Arbeitsstatus noch Erfolg
  werden erfunden. LOADING bleibt ausschließlich tatsächlicher Callerzustand.
- `JarvisShellRenderer.kt`: reicht **bestehende** `timeMs` und
  `animatorScale > 0` an Kacheln weiter, kein Frameclock / Choreographer.
- `JarvisTileEnergyTest.kt`: JUnit-Regression für Rand, 48dp,
  Grenzzeiten, geschlossenen Zyklus und deterministisches Offset.

## Durchführung und Prüfstufen

- **Host Kotlin 1.9/JDK 21:** exakte neue `JarvisTileEnergy.kt` kompiliert;
  unabhängiges Spiegel-Harness mit **205 erfüllten Assertions** (0 Fehler).
  Git-Blob-ID des Host-Mirrors und Repository-Blobs:
  `c718a6ae4af679d2c0cab92fecbe99ab69e0f406` (identisch).
- **Java2D**: separate Vorher-/Nachher-Illustrationen aus derselben Kotlin-
  Zeit-/Positionsfunktion erstellt. Diese zeigen die **geplante Wirkung des
  Randflusses**, nicht die produktive Android-Canvas-/AGSL-/Filament-Szene.
  Die Bilder befinden sich als Begleitdateien der Bot-Ausführung, nicht im Repo.
- **NOT_RUN:** vollständiger Kotlin/Android-Source-Compile (Abhängigkeiten im
  Ausführungshost nicht verfügbar), JUnit über AGP, Robolectric, Android-UI,
  vollständige App-Lint, APK, Filament, echtes Fold7, GPU/FPS/Jank/Temperatur.
  Der Kotlin-Host-Harness ist kein Ersatz für diese Gates.
- **Wichtiges Rest-Gate:** `JarvisShellView.ambientMotionEnabled` ist auf
  dem Träger standardmäßig `false`. Die neue Logik folgt der schon vorhandenen
  Shell-Zeituhr **bei einem Draw** und der Eintrittsanimation; ohne
  performance-/thermisch geprüfte Aktivierung des bestehenden Ambient-Refresh
  ist **kein kontinuierlicher Idle-Framefluss** zugesichert. Die Aktivierung
  der vorhandenen Uhr gehört ausdrücklich in einen nachfolgenden,
  gemessenen Lifecycle-/Power-Slice, nicht in diesen Mini-PR.

## C1-Übernahme

Eigentümer C1 (#519) und Integrationsbasis #527: nur die vier genannten
Dateien prüfen/cherry-picken, zuerst gegen #539-Fill/Orb-Stand und #535-
Designziele vergleichen; kein kompletter Import des #535-WIP. Exaktes
Quellmanifest fixieren, Android-/UI-/Lint-Gates auf dem kombinierten Baum
ausführen. Bei Abnahmegegensatz diesen Slice isoliert zurücknehmen.

Originalpaket, Signatur, Offline-/Owner-Daten, GLB, Sprach- und Navigationspfade,
`AgentActivity`, `ContinuumHologramView`, `main`, Release, Actions und
Installationskanal bleiben unverändert. Der Slice begründet **keine**
Live-Status- oder fremde-App-Datenansprüche.
