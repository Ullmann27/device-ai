# Code55: vorhandene Bausteine angeschlossen

## Gemeinsamer Stand

Support-PR: [#802](https://github.com/Ullmann27/device-ai/pull/802), Ziel: Cockpit-Integration [#799](https://github.com/Ullmann27/device-ai/pull/799).
Veröffentlichte Integrationsbasis `c5a75198053325bb18264e09f4c3a22956d8bac9` ist im Supportzweig enthalten;
ihr Baum entspricht dem lokalen Hauptstand `2711ef9c6370a409d1b446b3434f3fce2de40619`.
Die Anschlussrunde führt die bestehende Opus-/Code55-Arbeit fort.

Die bisherigen Patchkandidaten sind jetzt in Produktionsdateien eingearbeitet. Die folgenden
Verbindungen sind im gemeinsamen Supportbaum implementiert und zur Übernahme durch den
Hauptintegrator vorbereitet. Die aktiven Arbeitsbäume der beiden anderen Teams wurden nicht verändert.

| Bereich | Tatsächlicher Anschluss |
|---|---|
| Live-Eingaben | Text- und Scrollereignisse gelangen über den vorhandenen ScreenObservationBus zum Android-Port und zum Aktionsprüfer. Ein Scrollbeleg gehört zum ausgewählten Knoten, Fenster, Auftrag, Beobachtungszeitraum und Dispatch. Ein geänderter Baum allein bestätigt keinen Scrollvorgang. |
| Aktionsschutz | Fensterwechsel bestätigt keine alte Aktion. Das begrenzte Journal vergisst bei Überlauf keine unsicheren Aktionen. Unbekannte Positionswerte ergeben keinen Bewegungsbeleg. |
| Workflow und Avatar | Veraltete Ausgangsrevisionen, rückläufige Beobachtungen und Pause/Rückfrage eines fremden Schritts werden abgefangen. Die Avatar-Aktionskennung enthält die Auftragskennung; gleiche Schrittnamen zweier Aufträge bleiben unterscheidbar. |
| 3D-Monitor | Vorhandene Glas-/Rahmenmaterialien, indirektes Licht samt Freigabe, Fokusfahrt und gemeinsame Pitch/Yaw/Z-Projektion sind an Home und Filament angeschlossen. Native Beschriftungen bleiben über der Szene. |
| Layout und Modell | Vorhandene Standfuß- und Schriftgrößenkorrekturen sind übernommen. Hauptlinien-Deckenparameter und die Entfernung der sechs arch-cap-Elemente bleiben erhalten. Generator, GLB und Asset-Pin stimmen überein. |
| Sitzungswechsel | newSession verwirft einen vorgemerkten Computerstart und stoppt den bisherigen ComputerController, bevor die Chatsitzung zurückgesetzt wird. |
| Aufnahme-Service | Die vorhandene Foreground-Promotion erfolgt vor der Ablehnung eines veralteten Starts. Der Stop-PendingIntent enthält bereits die angeforderte Capture-Sitzung; der Exceptionpfad räumt auf und verwendet stopSelf(startId). |

Der Hauptmonitor mit sourceViewport-/Klickschutz und ContinuumHologramView sind bytegleich zur
Integrationsbasis. Originalavatar, Originalkugel, AVATAR_SCALE=.76 und Kopfrahmen .70 bleiben erhalten.
Die aktualisierten nativen UI-Testfixtures aus Holographic-Commit `dfe8f08f646360c0290ef420b9e6b16cc90904a1`
sind ebenfalls übernommen.

## Geprüfter Umfang

Die Läufe überlappen; ihre Testzahlen werden nicht zu einer Gesamtsuite addiert.

| Lauf | Ergebnis | Aussage |
|---|---:|---|
| Gemeinsame Workflow-/Live-Quellen nach Zusammenführung | 75/75 PASS | Originalworkflow, Verifier, Adapter, Bus, Bridge, AvatarDirector/Pose und CuePlayer; synthetischer OS-Port. Alle 27 Quellhashes stimmen mit dem Integrationsbaum überein. |
| Live-Kotlin-Regressionslauf | 36/36 PASS | Einschließlich zuvor offenem Scroll-Badge-Fall, Window-ID und Journalüberlauf. |
| Direkter Android-Live-Lauf | 47/47 PASS | Echte Service-/Portquellen mit Robolectric; Text/Scroll, Suchrollen, Widerruf und ungültige Bewegungsbelege. |
| Kombinierte Szene | 24/24 PASS | Motion-, Matrix-, Layout- und Sichtbarkeitstests; korrigiertes GLB zweimal bitgleich reproduziert. |
| Foreground-Service-Regressionslauf | 6/6 PASS | Echte Start-Intent-/Servicepfade und Notification-Bindung. Derselbe Testbestand gegen die alte Servicequelle: 6/6 FAIL. |
| Service mit endgültigem Live-Anschluss | 6/6 PASS | Sechs Quellen einschließlich neuem Beobachtungsbus, LiveScrollEvidence und Node-Port frisch aus dem gemeinsamen Rootbaum kompiliert; Hashidentität bestätigt. |
| Direkte Android-/Filament-Kompilation | PASS | Veränderte Klassen gegen Android 36, Filament/gltfio 1.56.0 und bestehende Appkompilate als Umgebung. |
| Gemeinsamer Gradle-Lauf | NICHT GESTARTET | Gemeinsame agent-gradle.lock war belegt; zwei Warteversuche ohne Ausführung von Gradle. Zweiter Versuch: Exit 75, expliziter Lock-Timeout. |

Direkte Androidläufe verwenden vorhandene Appkompilate als eingefrorene Umgebung; sie ersetzen
keinen vollständigen Neubau des gemeinsamen Appbaums. Die zuletzt übernommenen UI-Testfixtures
sind für den gebündelten Lauf vorhanden. Android-Systemprozess, echte MediaProjection-Berechtigung,
Filament-GPU und Fold7 bleiben ohne Gerätebestätigung.

Aktueller GLB-Pin: `8dfe8e9ae86969c53f5a4c60474cae2cb72f23c517bca6a9134edd8d4c98db9c`.
14.084 Byte, 77 Knoten, 75 Renderobjekte. Frühere Holographic-Renderbilder zeigen einen anderen
Assetstand und werden nicht als Bildnachweis dieses kombinierten Modells ausgegeben.

## Nachweise und Übernahme

- Gemeinsamer Quell- und Prüfbezug: `docs/evidence/astra55-support-20261112/connections/integration.json`.
- Workflow: `docs/evidence/astra55-support-20261112/workflow-connections/` und `connections/workflow-combined/`.
- Live: `docs/evidence/astra55-support-20261112/live/connection-final/`.
- Szene: `docs/evidence/astra55-scene-connections-20261112/`, abschließend `canopy-repair/`.
- Aufnahme-Service: `docs/evidence/astra55-support-20261112/projection-service/`.
- Blockierter Gradle-Aufruf mit ausgewählten Testklassen: `connections/android-combined/receipt.json`.

Die ursprünglichen Prüfberichte der Helfer sind historische Laufbelege. Diese Übergabe beschreibt
ihre Zusammenführung. Für die Hauptlinie ist der vollständige Support-PR maßgeblich; keine alten
repair.patch-Dateien zusätzlich anwenden. Generator, GLB und Pin gemeinsam übernehmen.

Sobald der gemeinsame Build-Slot frei ist, den dokumentierten gebündelten Android-Lauf auf dem
übernommenen Quellbaum ausführen. Ein anderes Team behält seinen laufenden Build; der Support
hat die Sperre weder entfernt noch umgangen. Anschließend bleibt die vorhandene Geräteprüfung
beim Hauptintegrator.

Die Foreground-Reihenfolge wurde anhand der
[Android-Dokumentation](https://developer.android.com/develop/background-work/services/fgs/troubleshooting)
und von [AOSP ActiveServices](https://android.googlesource.com/platform/frameworks/base.git/+/master/services/core/java/com/android/server/am/ActiveServices.java)
geprüft. Robolectric bestätigt die App-Aufrufreihenfolge, nicht den Systemprozess.

## Koordination

Die drei Supporthelfer liefern an den Hauptintegrator. Öffentliche Übergaben an beide Teams:
[Cockpit #799](https://github.com/Ullmann27/device-ai/pull/799#issuecomment-6823585121) und
[Holographic #801](https://github.com/Ullmann27/device-ai/pull/801#issuecomment-6823585597).
Eine Übernahme durch die fremden Agenten wird erst nach sichtbarer Bestätigung als erfolgt gewertet.
