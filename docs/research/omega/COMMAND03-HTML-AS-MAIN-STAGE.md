# Kommando 03 – HTML-Original als Android-Startfläche

**Status: WIP / UNCOMPILED / NOT DEVICE-TESTED / KEIN RELEASE.**

Die bisherige native Code60-Ansicht ist gestalterisch **nicht** die freigegebene HTML-App. Die zuvor erfolgreichen Hosttests belegen diese Übereinstimmung nicht. Der Besitzer hat die Abweichung ausdrücklich zurückgewiesen, weshalb die Signierung blockiert bleibt.

## Unveränderte Designquelle

PR #850, Commit `0876aa0accdf8154e06e53da42aa5c9492892066`, `design/continuum-web/continuum-preview/`.

Die 35 Originalblobs wurden mit identischen Git-BLOB-SHAs unter `app/src/main/assets/continuum-command03/` übernommen. Der prüfbare Katalog ist `docs/research/omega/COMMAND03-HTML-VISUAL-PARITY-LOCK.json`. Die Android-spezifischen Dateien `native-bridge.js` und `native-layer.css` sind **separat**; sie verändern die Originalblobs nicht.

## Jetzt direkt mit der vorhandenen Activity verbunden

- `AgentActivity.buildUi` legt die Original-HTML-Bühne als Hauptansicht auf die bestehende native Activity. Die bisherigen Android-Tool- und Datenpfade bleiben vorhanden. Der alte native Renderer einschließlich seines HUD-Animationspfads wird für die überlagerte HTML-Darstellung angehalten; die WebGL-Darstellung bleibt der visuelle Kopf/Kugel-Anker.
- Aktivierung im WIP-Quellstand: `continuum-command03/original_html_main=true` (Standard). Technischer Rückfall: `false` stellt beim nächsten Aufbau die klassische Ansicht wieder her. Während des Hauptbetriebs zeigt die Android-Zurück-Taste einen Bestätigungsdialog zur Rückkehr, ohne gespeicherte Chats zu löschen oder den aktuellen Eingabeentwurf absichtlich zu verwerfen.
- `ContinuumCommand03OriginalStage` lädt ausschließlich die lokalen App-Assets, blockiert externe Netzaufrufe und Dateizugriffe, setzt **kein** `addJavascriptInterface` ein und nimmt lediglich allowlist-basierte Aktionen entgegen.
- `native-bridge.js` zeigt echte Gesprächstitel aus dem existierenden Store, aktiven Chat, echte Benutzer- und Assistentennachrichten, gebundene Computer-Planungs-/Ergebniszeilen, Busy-/Zuhörstatus sowie verfügbare APP-CPU- und Geräte-RAM-Prozentwerte. Wo keine Messung existiert, bleibt der Wert unbekannt; die HTML-Simulationsanzeigen werden nicht als echte Telemetrie dargestellt.
- Die HTML-Eingabe verwendet den bestehenden `sendButton`-Listener. Chatwechsel gehen durch `selectStoredConversation`; Werkzeugnavigation verwendet die bisherigen vom Besitzer kontrollierten Android-Funktionen.
- HTML-„Recherche erleben“, Dateien/Argon4 und ähnliche Demoknöpfe lösen unter Android die echten nativen Besitzerwerkzeuge aus, **keine gespeicherte simulierte Recherche**. Die App stellt der Webansicht weder Modellzugriff noch Dateirechte direkt bereit.

## Strikte offene P0-Abnahme

1. Native PDF-Dokumente, Recherchebelege und Computer-Liveframes tatsächlich in der Original-HTML-Projektionsfläche anzeigen; vorhandene native Modale alleine reichen nicht.
2. Erhalt des Avatar-Rigs, echter Viseme/Blicksignale, Arbeitsereignisse und flüssiger Fensterbewegungen bei echten Tasks; derzeit nicht nachgewiesen.
3. Renderlast/GPU/WebGL auf dem Fold messen, thermisches Budget und doppelte Renderaktivität prüfen.
4. Android-Ansichten 392dp und 904dp sowie Größen-/Tastaturwechsel **visuell gegen HTML-Referenz** prüfen; kein erneutes Redesign zulassen.
5. Kotlin-Compile, gezielte und vollständige Android-Tests, Source-bound GitHub-QA, originale Signierung und echte Geräteannahme. Vorher **keine** Code60-Fertigmeldung, kein Main-Merge und kein installierbarer Release.

Reproduzierbare Quellprüfung: `node tools/command03-native-qa/check-visual-source.mjs`. Sie prüft die Quellen und Adapterverträge, **nicht** Android-Renderqualität oder Installation. Die 112 älteren grünen Hosttests gehören zu einem früheren SHA; sie wurden auf diesem neuen SHA nicht wiederholt.

## Frühzeitige Demo-Blockierung

Die lokale Android-Startdatei `index.android.html` ist die exakte HTML-Originaldatei plus **zwei** zusätzliche Asset-Links: `native-layer.css` im Kopf und `native-bridge.js` vor den originalen Seiten-Skripten. Die Original-`index.html` selbst bleibt byteidentisch. Dadurch sind die Navigations- und Formular-Interceptoren bereits registriert, bevor die Beispiel-Eventhandler der unveränderten Webdemo laufen. Die vorherige späte `onPageFinished`-Einfügung wird nicht mehr verwendet. Der Node-Quelltest verlangt, dass nach Entfernen genau dieser beiden Include-Zeilen die bytegenaue Originaldatei zurückbleibt. Das ist ein Quellvertrag, noch kein Geräte-Nachweis.
