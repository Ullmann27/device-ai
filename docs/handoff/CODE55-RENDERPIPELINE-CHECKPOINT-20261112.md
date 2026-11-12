# Code55 Renderpipeline – Integrationscheckpoint

Verantwortlich: Astra, PR #799 → #791. Besitzerauftrag: vorhandene gemeinsame 3D-Implementierung fertigstellen, kompilieren, gezielt prüfen, unsignierte APK bauen, anschließend kontrolliert in #791 übernehmen. Kein Main-Merge, keine Signierung/Installation.

## Quellstand und echte Ergebnisse

- Code vor dieser Dokumentation: `8a0ec80dcd`.
- Gemeinsame Filament-Engine/Scene/Kamera für Originalavatar, räumliche Kugel und Arbeitsplatz; externer HardwareBuffer-Monitor ist angeschlossen.
- `:app:compileDebugKotlin` auf `98385b0277b84f8725066b93f3a50e1c7a06318f`: PASS, 32 s.
- Produktions- und vollständige Testquellen auf `8a0ec80dcd`: Gradle-Vorbereitung PASS, 44 s. Die Testausführung war in diesem Vorbereitungsschritt bewusst SKIPPED; dies ist kein Test-PASS.
- Fokuslauf auf `bef87216f9`: BUILD_DAEMON_TERMINATED vor Abschluss, kein vollständiges Testergebnis. Kotlin-Komplilation und Testprozess werden deshalb in getrennten Prozessen ausgeführt.
- GLB-/Material-/Originalavatar-Strukturprüfung auf `8a0ec80dcd`: PASS. Fünf Original-Orbitgruppen/Tiefenplatzierung werden anhand des tatsächlich aktiven Original-Renderers noch korrigiert; die aktuelle alte Vier-Orbit-Geometrie ist kein fertiger Abnahmebeleg.
- APK: NOT_BUILT. Fold7 / Filament-GPU / echte App-Pixel / Thermik: NOT_RUN.

## Bereits integriert

Reale Bildschirmbilder sind an Task, Capture-Sitzung, Fenster, Beobachtungsrevision und Aufnahmealter gebunden. Verspätete Callbacks und unbekannte Farbprofile werden abgelehnt; bekannte RGB-Profile werden tatsächlich nach sRGB konvertiert. Pause/Stop gibt eigene GPU-Bildreferenzen frei. Scroll-Erfolg benötigt ein passendes natives Scrollereignis und tatsächliche Containerbewegung, keine bloße Änderung im Ansichtsbaum.

## Aktueller Quellenabgleich

- #801 zuletzt frisch gelesen: `871a25cd5de72917cbe3fba54bb8a0d0fab7b103`. Prüffähige GLBs/Materialien übernommen bzw. auf den gemeinsamen Renderer portiert. Neuer Referenzkorrekturstand wird gegen den neuen gemeinsamen Pfad geprüft; sein älterer separater Renderer wird nicht zusätzlich aktiviert.
- #802 zuletzt frisch gelesen: `c1d41bcdce73b42cbce3b77920ecd86360b379fe`. Sicherheits-/Ereigniskorrekturen portiert. Vorhandener Foreground-Service-Fix und Sitzungswechsel werden gerade gegen die bereits integrierten Capture-Korrekturen abgeglichen.

## Nächster zwingender Schritt

Konkrete Orb-/Service-Korrekturen integrieren → vollständige Produktions-/Testkompilation → fokussierte und vollständige verfügbare Host-/UI-Tests → unsignierte Code55-APK → SHA/Artefakt/Restfehler dokumentieren → geprüften Stand nach #791 übernehmen. Blender-Belege werden eindeutig als Hostrender bezeichnet; sie ersetzen keinen Android-GPU-Gerätetest.
