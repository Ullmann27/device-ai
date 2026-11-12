# Code55 – holografische Cockpit-Zulieferung

**Implementiert, hostgeprüft und gerendert. Geräteabnahme offen.**
Übergabe über [PR #801](https://github.com/Ullmann27/device-ai/pull/801)
an [#799](https://github.com/Ullmann27/device-ai/pull/799), anschließend Code55
#791. Der ursprüngliche Szenen-Checkpoint #800 wurde bereits in #799 übernommen.

## Drei Arbeitsbereiche

| Agent | Ergebnis |
| --- | --- |
| Materialien | Echte Bevel-Geometrie, Rauchglaslagen, Cyan-/Violettkanten, konstantes SH-Fülllicht, korrigierter Tischkontakt |
| Animation | Gemeinsame native/GPU-Pose, 420-ms-Fokusfahrt, stetige Umkehr, Sichtbarkeits-Lifecycle und gezielte UI-Prüfungen |
| Rendernachweis | Sechs Fold-/Phone-Bilder und Bewegungsclip aus Originalassets und produktiven Kotlin-Berechnungen |

Der Koordinator hat die Änderungen in `work/astra55-holographic-support-20261112`
zusammengeführt und die tatsächlichen Renderbilder geprüft. Originalavatar und
Originalkugel bleiben bytegleich. Lesbare App-Pixel behalten ihre native Ebene;
Foreground-Beschriftungen liegen vor Raum und Monitor. Keine zusätzliche
Android-Engine und kein zweiter Rendertakt.

## Änderungen

Der bestehende Workstation-GLB enthält plastische Kanten und getrennte Glaslagen.
Der flache Monitorfuß liegt auf der dünnen Tischglasauflage. Das Asset belegt
14.728 von höchstens 32.768 Bytes; Pin:
`3223168a58e5fa51c48bb56ba36aabbf0cdb936025a01d96d4b14e5e0fa023aa`.
81 Renderables ersetzen 46; Gerätekosten transparenter Flächen sind ungemessen.

Monitorrahmen und App-Pixel verwenden dieselbe Perspektive einschließlich
Tiefe, Yaw und Pitch. Filament verwendet `T * RY * RX * S`.
Richtungswechsel übernehmen die momentane Geschwindigkeit; Umkehrfahrten sind
auf 480 ms begrenzt. Hintergrund, ausgeblendete Oberfläche und deaktivierte
Systemanimation beenden die Bewegung geordnet.

Kopfregion, Gesichtsschutzfläche und Monitorposition halten Gesicht und Kinn
sichtbar. Acht native Schaltflächen bleiben auf Fold und Telefon erreichbar.
Die Entwicklungsbilder zeigen einen Monitor ohne Livebild.

## Nachweise

| Prüfung | Ergebnis |
| --- | --- |
| Android-Produktions- und Testkompilierung | PASS auf isolierter Integrationsbasis `a99eba365d` plus Support |
| JUnit: Projektion, Bewegung, Layout, Sichtbarkeit, Fußkontakt | 24/24 PASS |
| Robolectric: Lifecycle, Bewegungssteuerung, unverfälschte Pixel | 8/8 PASS; echte Android-Klassen, SDK 28, native Grafik |
| Renderprojektion | 55/55 PASS; maximale Eckabweichung 0,000380742 Pixel |
| Bilder und Video | Sechs PNG, 49-Frame-MP4; Quell-/Asset-/Ausgabehashes geprüft |
| Filament auf Android, Fold7-Framerate und Wärme | NOT_RUN |

Der Gradle-Testprozess blieb beim Workerwechsel stehen und wurde beendet;
dieser Task wird nicht als bestanden gewertet. Die kompilierten Produktionsklassen
wurden anschließend direkt mit JUnit geprüft: alle 32 gezielten Tests bestanden.
Das Produktions-JAR blieb dabei bytegleich. Die ursprünglichen UI-Fixtures
hatten ein unsichtbares Testfenster. Native Grafik und positive Sichtbarkeitsprüfungen
korrigieren den Testaufbau, ohne Produktionsschutz oder Assertions abzuschwächen.
Mit altem Produktcode scheitern weiterhin genau zwei Wiederanlaufprüfungen.

- [Gemeinsamer Prüfbeleg und Quellhashes](../evidence/astra55-hologram-final-20261112/verification.json)
- [Kompilierungsbeleg](../evidence/astra55-hologram-final-20261112/android-compile-receipt.txt)
- [Robolectric-Beleg](../evidence/astra55-hologram-motion-20261112/android-ui-proof.json)
- [Bilder, Video und Reproduktion](../evidence/cockpit-hologram-host-20261112/README.md)

## Quellenidentität und Übernahme

Öffentlich auscheckbarer visueller Freeze:
`097aace1db04d51c13c13be7a88985d9f34fcfcc`.
Sein Git-Tree `22c08f103e7a40a9d05cde4c285f4d4e14b35f20` stimmt exakt mit
dem lokalen Renderstand `df43ec3580b7939462619505a1e196cf0373879e` überein.
Die Manifeste behalten diese ursprüngliche lokale Quellkennung. Spätere
Support-Commits ergänzen Test-Fixtures, Belege und Dokumentation.

Die Zulieferung enthält die Szenenarbeit bis `feb285eef5`. Während der
Prüfung entwickelte das Hauptteam #799 weiter. Bei der Übernahme:

1. **Neuere `VirtualComputerMonitorView.updatePlayback`-/`sourceViewport`-Logik
   erhalten.** Sie enthält den Wiederanlauf bereits, ergänzt Pixelbereinigung
   und Ausschnittbindung. Die ältere Support-Implementierung nicht
   darüberkopieren; die korrigierten Tests sind unabhängig vom Helper.
2. **Neue Hauptteam-Kamera `AvatarFraming.pullBack` / `AVATAR_SCALE` mit der
   Support-Gesichtsschutzfläche abstimmen.** Diese spätere Kamera ist nicht
   im Render-Freeze enthalten. Beide Verkleinerungen nicht ungeprüft addieren;
   die kombinierte Geometrie anschließend erneut rendern.
3. GLB, Generator und Pin gemeinsam übernehmen. Motion-Datei, gemeinsame
   Projektion und Filament-Transform gemeinsam übernehmen. Aktuelle Capture-,
   Aktions- und Auftragslogik des Hauptteams erhalten.

Zwölf geprüfte Produktions-/Hosttest-Dateien sind zwischen Support und dem
kompilierten Prüfstand `e9523efd0b` bytegleich; der Beleg enthält die Hashes.
Spätere #799-Commits sind damit nicht als integriert oder gerendert bestätigt.
Aktive Worktrees anderer Teams wurden nicht verändert.

## Grenzen und Rücknahme

Die Bilder sind **HOST_COMPOSITE**: Blender und Java2D mit Originalassets,
produktiver Geometrie und Bewegung. Sie belegen keine identische Filament-
Beleuchtung oder Live-MediaProjection. Keine APK wurde hierfür signiert oder
installiert; kein Merge nach main. EXP-05/06/11/12 bleiben erhalten.

Rücknahme: zusammengehörige Material- und Bewegungsänderungen auf dem
Entwicklungszweig zurücknehmen; GLB und Pin stets gemeinsam. Preview-Werkzeuge
und Belege sind kein Bestandteil der APK.
