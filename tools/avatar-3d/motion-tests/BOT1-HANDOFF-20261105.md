# Bot 1 – Blinzelkinematik (05.11.2026)

**Nachtrag – Q1-Regression des ursprünglichen App-Vertrags behoben (aktueller HEAD):** Die erste Fassung `4fc3914...` erfüllte bei `4.525f` die **unveränderte** exakte `Avatar3dContractTest`-Erwartung nicht (`0.9336765` statt `1.0`). Daher `c48bf7b7e1c3a6f690d7bf8727b5906e1df9912e` (Produktionsblob `5b89c17443ca8e362f479816f77cda862150f887`) mit 55 ms Schließen / 20 ms Vollschluss / 75 ms Öffnen. Separater Host-Regressionstest: RED Exit 1 → GREEN Exit 0; neue direkte JUnit-Stub-Hostprüfung **5/5 PASS**; publizierter Standalone-Hosttest ebenfalls RED→GREEN. **Android-Originaltest, Projekt-JUnit, Filament und Fold7 weiterhin NOT_RUN.** [Nachtrag mit exakten SHAs, Negativlog und Reproduktionskommando](BOT1-BLINK-CONTRACT-FIX-20261105.md). Der untenstehende historische Ablauf vor diesem Nachtrag bleibt als Provenienz erhalten, wird **nicht** als aktueller Quellstand ausgegeben.

**Status: HOST_KOTLIN_RED_GREEN / FILAMENT_3D_NOT_RUN / FOLD7_NOT_RUN / NO_RELEASE.**

## Quelle, Umfang und Eigentum

Isolierter Zweig: `bot1/avatar-blink-kinematics-20261105`, Ausgangspunkt **#535** `4bbf2863dc3c7debe74ac81f3d16521926c8ae0a` (darunter #526 `5a18f74f18d9b7d927f8c622052369496153829f`). #539 `766a1953fe1b72a890448e38178448d50140fa40` hat einen **anderen** Original-Orb-Slice auf #527; er wurde ausdrücklich nicht übernommen. C1 #519 war beim Intake separat auf `13c37f55789bf67b07beeb37cc1a5035526e623e`. Keine fremden Integrationszweige/Worktrees oder `main` verändert.

Vorheriger Produktionsblob: `AvatarIdleMotion.kt` Git-Blob **`31801578f47f6a677c58283a408bc2840297de6d`** (lokale Bytekopie per `git hash-object` exakt bestätigt). Nachher: **`4fc3914cf161f371e6375a63c3fb1b7a8863ba0b`** (nach GitHub-Push erneut per `fetch_file` geprüft). Der JUnit-Test ist `9e37279d48da93e4c052ca30a687c88358e7dd9f`, der reine Host-Probe `612f19d560892ff18f7bd3ff6882cfbaa840c645`. Keine andere Produktivdatei berührt.

Das originale GLB liegt im Git-Baum von #535 unter `app/src/main/assets/avatar3d/continuum-avatar.glb`: **Git-Blob-SHA1 `3498c003a8bf186a8abb47048e2d36be4810b88c`**, Größe **12.038.416 Bytes** (Git-Baum-API). Das Git-Blob ist nicht die SHA-256-Prüfsumme. `NOTICE.json` (Blob `91f05ac554172da5b5c79a9e6b845731d27fb801`) und `AvatarGlbContract.ASSET_SHA256` deklarieren beide `c8b88385c73fa01596a5734d20859440b1ecaef06cb1abea6cd7a47e62b08b9a`; der Vertrag nennt 21 Morph-Ziele. **Die vollständigen GLB-Bytes konnten in dieser Umgebung nicht heruntergeladen/gehasht werden; SHA-256 und tatsächliche Morph-Payload sind hier nicht unabhängig verifiziert.** Das Paketasset, Rig, Atlas, Zähne und Filament-Renderer blieben unverändert.

## Reproduzierter Defekt

Die alte Blinzelfunktion `(1 - abs(t - center) / .075).coerceIn(0,1)` ist dreieckig. An Beginn, Scheitel und Ende springt die Geschwindigkeitsableitung abrupt. Gemessen bei t=4,450 s mit 1-ms-Abtastung: davor **0,0/s**, danach **13,328611/s**. Der neue Regressionstest bricht auf dem *exakten* alten Blob ab (Exit **1**) mit `sharp eyelid velocity jump at 4.45: 13.328611`.

## Kleinste Produktionskorrektur

Die bestehende 460-s-View-Clock, 100 Haupt- plus 20 Zusatzblinzler je Zyklus, 150-ms-Zeitfenster, Kopf-/Blick-Sakkaden und 21 Morph-Ziele bleiben erhalten. Nur die Blinzelkurve erhält eine asymmetrische 55-ms-Schließphase / 95-ms-Öffnungsphase, jeweils quintisch (`6u^5−15u^4+10u^3`) mit Nullgeschwindigkeit und Nullbeschleunigung an Beginn, Scheitel und Ende. Die ursprünglichen Fenster 4,450–4,600 s und 4,150–4,300 s innerhalb der berechtigten Gruppe bleiben erhalten; die Scheitel wandern nur um 20 ms vor. Keine neue Frameclock, kein Shader, kein View, keine Laufzeitberechtigung.

## Tatsächlich ausgeführte Host-Tests

Umgebung: Linux x86_64, OpenJDK 21.0.12.1, `kotlinc-jvm 1.9.0`. Für JUnit-Signaturen wurde ausschließlich **im temporären Hostverzeichnis** ein minimalistischer `org.junit`-API-Stub verwendet und die vier Funktionen direkt ausgeführt. **Kein Gradle-JUnit-Engine-Lauf**; der `app/src/test`-Test selbst wurde mit den tatsächlichen neuen Produktionsbytes kompiliert und ausgeführt.

- **RED:** vorheriger Originalblob `31801578…` + derselbe neue `AvatarBlinkEasingTest.kt` + Host-Stub: Kompilation Exit 0, Testlauf Exit **1** an nachgewiesenem Sprung.
- **GREEN:** neuer Blob `4fc3914…`, identischer Test und Stub: Kompilation Exit 0, **4/4** direkt ausgeführte Tests bestanden; Prozess Exit **0**.
- **Zusätzlicher eigenständiger Host-Check:** `AvatarBlinkKinematicsCheck.kt`, mit exakt denselben Produktivbytes kompiliert und ausgeführt: RED vorher Exit **1**, GREEN danach Exit **0**. Überprüft Übergänge, Vollverschluss, Zusatzzyklus, Uhrgrenzen, NaN/Inf, Idempotenz und 4.601 diskrete Zykluspositionen. Das ist ein **Host-Kinematiktest**, kein visueller 3D-Test.

Reproduktionskommando auf diesem Zweig mit lokalem Kotlin-JVM-Compiler:

```sh
kotlinc app/src/main/java/at/heinz/deviceai/avatar3d/AvatarIdleMotion.kt \
  tools/avatar-3d/motion-tests/AvatarBlinkKinematicsCheck.kt \
  -include-runtime -d /tmp/continuum-blink-check.jar
java -jar /tmp/continuum-blink-check.jar
```

Für die **noch ausstehende** Projekt-Abnahme ist der neue JUnit-Test mit der vorhandenen Android-Testkonfiguration und anschließend mit dem vollständigen Avatar-/Audio- und UI-Umfang auszuführen. Künstliches Test-PCM oder Host-Pose belegt weder echte Lippen-/Phonem-Synchronität noch native GPU-Pixel.

## BLOCKED / NOT_RUN und Abgrenzung

**Echte Filament-3D-Vergleichsrenderings: NOT_RUN/BLOCKED.** Diese Laufumgebung hat weder vollständigen Git-Checkout/GLB-Bytes noch Blender/GPU-Testgerät oder Fold7/ADB-Zugang. Deshalb **keine** als 3D-Beweis ausgegebenen 2D-/Bildgenerierungsbilder. Material/Fresnel-Kantenlicht, Atlas-Nähte an Schädeldecke, Nacken/Ohr, heller Lippenkeil sowie Mund/Gaze auf dem Fold7 bleiben offen. Die 59509-Bilder und fünf Jarvis-Videos sind Ziele, keine vorliegenden Laufzeitnachweise dieses Zweigs.

**NICHT ausgeführt:** vollständige Android-/Robolectric-/Lint-Suite, Filament-Rendering, 3D-Before/After, reale Kamera, TTS-Audition, APK-Bau, Installation und Signierung. Das kürzere Host-Prüfergebnis darf den vollständigen #535-/#526-/#519-Abnahmestand nicht überschreiben.

## Übergabe C1 / #519

Nur den hier geprüften Produktiv-Slice (Commit `cf8847d814c13cdc1c51be195f4d74216f690619`) und seinen Test (Commit `f906ee57db7ae5566a0d0f5b09ad9cb316662993`) anhand der **genannten Blob-SHAs** auf dem C1-eigenen Träger einzeln prüfen. Standalone-Host-Probe: Commit `edb05acfea2acdd9c665d295c84261f9f381e9d5`. Keine pauschale Übernahme von #535/#539. `AvatarMorphMapper` verwendet bereits `idleMotion.blinkWeight`; **kein neuer Anschluss nötig**. Danach echte Android-Tests, quellengebundene Renderings mit GLB-Hash und native Fold7-Abnahme vor jeder Freigabe. Rückfall: diesen isolierten Slice nicht übernehmen.

**Unverändert:** Besitzer-Signatur, Paketname, Updatekanal, Offline-Schalter, Ownerdateien, Main-Branch, Originalkugel und vorhandener Director/Live-View/Frameclock-Pfad. Keine Actions, bezahlten Runner, APIs, APKs, Signierung, Releases oder Installation.
