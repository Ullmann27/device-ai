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

## Zweiter Bot-2-Checkpoint — gerundete Randbahn (05.11.2026)

**Neuer echter visueller Defekt:** Der erste Slice #549 bewegte den Leuchtpunkt
auf einem *eckigen* Innenrechteck, während `JarvisNeon.component` gerundete
Glaskacheln mit 8-dp-Radius zeichnet. Beim Kurvenwechsel schnitt der Lichtpunkt
sichtbar die Rundung ab, statt der Kante mit stetiger Tangente zu folgen.

**RED (Kotlin 1.9 / JDK 21, lokale Host-Reproduktion der vorherigen
Helper-Quelle `c718a6ae4af679d2c0cab92fecbe99ab69e0f406`):**
Von 1.858 Zeitpunkten im 13-s-Zyklus weichen **144** um mehr als 0,07 dp
vom echten 5-dp-Innenradius ab; maximale Abweichung **2,0478 dp**.
Die negative Property-Prüfung brach mit Exitcode 1 ab. Berechnung:
außen 8 dp, Einrückung 3 dp, innen 5 dp.

**Fix und GREEN (separat eingecheckt):**

- `JarvisTileEnergy.kt`: Geraden mit vier Viertelkreisbögen, über die
  *echte Bogenlänge* parametrisiert (konstante Geschwindigkeit, stetiger
  Tangentenverlauf, geschlossener 13-s-Zyklus); scharfer Fallback nur bei
  Radius null. Keine neue Uhr oder Render-Schleife. Sicherer Umgang mit
  Long-Grenzen, ungültigen/extremen Größen und deaktivierten kleinen Controls.
  Git-Blob **`43dd016796976e66547ae55ecc359e5e140f4941`**,
  Commit **`58927d42db5f1b13ecaf7f11f565a7c9373acfc2`**.
- `JarvisTileEnergyTest.kt`: bestehende eckige Rand-Assertion durch
  Geometrie-Vertrag ersetzt; fünf Testmethoden für verschiedene Größen,
  alle Bögen, 13.000 Ein-Millisekunden-Schritte einschließlich Zyklusübergang,
  extreme Zeitwerte, 48-dp-Grenze und wiederholbare Ziel-Versetzung.
  Git-Blob **`575cd431a6bdb975548a59b0b218a882e90c4cbe`**,
  Commit **`2f0df62bf347336548dd3ae6b5894ed4d54c20c2`**.
- **Host GREEN:** identischer Kotlin-Produkt- und JUnit-Testquelltext
  kompiliert, 5/5 Testmethoden bestanden, ohne echte JUnit-/AGP-Laufzeit
  (eine lokale Minimal-Annotation/-Assertion-Brücke führte die Methoden aus).
  Unabhängige Rundungs-Property danach 0/1.858 Abweichungen
  über 0,07 dp; Maximum **0,00000763 dp**.
- **Sichtnachweis:** Zwei neue Java2D-Hostbilder
  `jarvis-rounded-vorher-java2d.png` und
  `jarvis-rounded-nachher-java2d.png` mit mathematisch gebundenen
  Vorher-/Nachher-Koordinaten. Als Laufartefakte außerhalb des
  Repositorys gespeichert; **keine** nativen Android-/GPU-Bilder.

**SOURCE_STAGED / HOST_KOTLIN_TESTS_PASS / ANDROID_NOT_RUN.**
Die älteren 205 Host-Assertions beziehen sich auf den *ersten* Slice/Blob,
nicht auf diese neue Revision. Auch für diesen zweiten Checkpoint sind
Android-Kompilierung, echtes JUnit/Robolectric, Lint, APK, Filament,
Fold7, Temperatur/FPS/Jank und C1-Gesamtintegration **NOT_RUN**.

**Bewusst offen:** `JarvisShellView.ambientMotionEnabled` bleibt default
`false`. Die Kachel folgt vorhandener Framezeit, aber im ruhenden Bildschirm
ist ohne spätere, thermisch überprüfte Aktivierung keine Dauerbewegung
zugesichert. Nichts an Haupt-Activity, Navigation, Sprache, Eigentümer-Signer,
Offline-Funktion, APK oder `main` geändert. C1 #519 bitte nur diesen
isolierten Diff auf dem aktuellen gemeinsamen Kandidaten prüfen, die
vollen Android-Gates erneut ausführen und den Design-Donor nicht
pauschal zusammenmergen.
