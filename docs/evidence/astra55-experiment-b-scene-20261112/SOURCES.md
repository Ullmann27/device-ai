# Experiment B – Primärquellen

Recherche vor Produktionsänderung: **12.11.2026, ab 14:40 UTC**.
Offizielle Inhalte wurden über die Websuche gelesen. Direkte GitHub- und
v1.56.0-Rohdateiabrufe wurden vom Webdienst mit `DisabledError` abgewiesen;
deshalb wird kein gelesener v1.56.0-Volltext behauptet. Der verwendete
Filament-1.56.0-Anschluss wird zusätzlich gegen echte lokale API-Klassen
kompiliert. Auch der aktuelle Inhalt von Repository-Issue #41 war über diesen
Webzugang nicht lesbar. Lokale AGENTS-, Experimentvertrag- und Registertexte
wurden geprüft; aktive Arbeitsgrenzen: EXP-07/11/12.

| Offizielle Quelle | Gelesene Aussage | Konsequenz für B |
|---|---|---|
| [Khronos glTF 2.0, §3.5.3 / §5.25](https://registry.khronos.org/glTF/specs/2.0/glTF-2.0.html) | Lokale Transformation ist `T * R * S`; Weltmatrix ist Elternweltmatrix mal lokale Matrix. | Ein unverändertes Kind muss durch eine neue Elternmatrix weiterhin bewegt werden. Alle 16 Weltkomponenten vergleichen. |
| [Khronos glTF 2.0, §3.11](https://registry.khronos.org/glTF/specs/2.0/glTF-2.0.html#animations) | Animationen adressieren Node-TRS oder Morphgewichte; nicht animierte Eigenschaften behalten ihre Werte. Die Spezifikation bestimmt keine Runtime-Abspielpolitik. | Keine neue Clock oder Interpretation der bestehenden Animation einführen. |
| [Filament TransformManager](https://github.com/google/filament/blob/main/android/filament-android/src/main/java/com/google/android/filament/TransformManager.java) | `setTransform` setzt eine lokale, zum Elternknoten relative Matrix. Viele Änderungen in tiefen Hierarchien können aufwendig sein. | Identische lokale Matrizen müssen nicht erneut geschrieben werden; tatsächlicher GPU-/Zeitgewinn folgt daraus nicht. |
| [Filament Camera](https://github.com/google/filament/blob/main/android/filament-android/src/main/java/com/google/android/filament/Camera.java) | Kamerapose ist ein starrer Welttransform; Kamera blickt entlang ihrer negativen Z-Achse. Elterntransformationen werden berücksichtigt. | Vorhandene gemeinsame Kamera und Projektion erhalten. |
| [Filament Materialien](https://google.github.io/filament/main/materials.html) | Deklarierte Materialparameter lassen sich über die Material-API zur Laufzeit setzen. Materialfunktionen unterscheiden Welt-, View- und Clipraum. | Cache ausschließlich lokale Transformaufrufe; Material- und Live-Texturupdates bleiben unverändert. |
| [Filament gltfio Animator](https://github.com/google/filament/blob/main/android/gltfio-android/src/main/java/com/google/android/filament/gltfio/Animator.java) | Dokumentierte Reihenfolge: Animationsanwendung, gegebenenfalls Crossfade, danach Knochenmatrizen aktualisieren. | Bestehenden Avatar-/Bone-Pfad nicht verändern. |
| [Android Choreographer](https://developer.android.com/reference/android/view/Choreographer) | Choreographer koordiniert Animation, Eingabe und Zeichnen anhand von Displayimpulsen; View-/Animations-APIs nutzen diesen Mechanismus. | Keine zweite Taktquelle oder zusätzliche Render-Schleife. |
| [Android Frame Pacing](https://developer.android.com/games/sdk/frame-pacing) | Unregelmäßige Präsentationsintervalle können trotz VSync-Ticks ruckeln; Präsentationszeitpunkte sind eine eigene Aufgabe. | Weniger Transform-Aufrufe sind kein Nachweis besserer Bildzeiten oder Geräte-FPS. |

Ältere Android-Game-Loop-Texte empfehlen VSync-Zeitwerte zur Animation. Die
aktuelle [FrameCallback-Referenz](https://developer.android.com/reference/android/view/Choreographer.FrameCallback)
verweist für Animationen inzwischen auf `VsyncCallback` und Animationszeiten.
B verändert diese APIs nicht und leitet aus widersprüchlichen Zeit-Empfehlungen
keinen ungeprüften SDK-Wechsel ab.

## Quellenbindung der Anwendung

Kanonische Basis: `5f87bdc610944b93d3173a83c5beeea315abeb66`.
Der erste gelesene Stand `140c59ee` ist durch die Referenzkorrektur überholt.
Sein zwölfphasiger Blender-Nachweis bleibt historischer Hostnachweis, keine
Baseline-GPU-Messung für B. B verwendet aktuelle Produktionsklassen und Assets.
Die aktuelle Stage hat **neun** lokale Uploads pro Aufruf: Raum, Seitenmonitore,
Hauptmonitor, Kugel und fünf Orbits. Der alte Acht-Aufruf-Stand wird nicht kopiert.
