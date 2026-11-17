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

## Kein zweiter 3D-Dialog

Der alte separate HTML-Designtest-Dialog wurde entfernt. Die Ansicht wird in der vorhandenen Activity an genau einer Stelle montiert. Im Originaldesign zeigt die Android-Zurück-Taste den Rückfall zur klassischen Oberfläche; von dort schaltet **Mehr → KOMMANDO 03 · ORIGINALDESIGN ALS STARTSEITE** wieder in die HTML-Hauptoberfläche. Dabei bleiben sichtbare Chatzeilen und der aktuelle Eingabeentwurf beim Activity-Neuaufbau erhalten. Ein Geräte- oder Langzeittest dieses Wechsels ist offen.

## P0-Fortsetzung: echte PDF im originalen projizierten Display (WIP)

Der bereits vorhandene `PdfDocumentIntake` öffnet ausschließlich vom Besitzer ausgewählte PDFs über Androids lesenden Dokumentpicker. Der neue Pfad `AgentActivity.projectCommand03Document(doc)` wird erst **nach einem erfolgreichen echten PDF-Read** aufgerufen; eine gespeicherte Argon4-Demo ist keine Datenquelle. Die PDF-Metadaten, die ersten maximal 12.000 wirklich extrahierten Zeichen und – soweit vorhanden – eine Kopie der bereits gerenderten ersten Seite werden an die lokale HTML-Projektion übergeben.

Die JPEG-Erzeugung erfolgt begrenzt auf **280.000 Bytes** im vorhandenen seriellen Dokument-I/O-Executor; das Bild wird nicht als freigegebene Datei/Content-URI an JavaScript übergeben. Eine echte Bitmap-Kopie wird nach der Kodierung recycelt. Vor Anzeige muss Chat-ID, aktuelle Dokument-Lesegeneration, noch aktive WebView und Activity übereinstimmen; alte Rückmeldungen werden verworfen. Wenn das Seitenbild nicht zur Verfügung steht oder zu groß ist, zeigt das projizierte Dokument nur die echten Metadaten/Textinformationen. Bei fehlender Textschicht/älterem Android meldet die Ansicht dies ausdrücklich, statt eine Zusammenfassung vorzutäuschen. Alle fremden Textdaten werden mit DOM-`textContent` eingefügt; externe Webzugriffe sind gesperrt.

Das zusätzliche HTML-Markup und die CSS-Zeichenwege stehen vollständig in **separaten Android-Dateien** (`native-bridge.js`, `native-layer.css`); die 35 freigegebenen Originalblobs sind weiterhin byteidentisch. Zwei native Schaltflächen erlauben ausschließlich „Andere PDF öffnen“ über den echten Picker oder das Schließen der lokalen Projektion.

**Grenzen:** Die erste Seite kann gezeigt werden, aber weitere Seiten werden noch nicht visuell gerendert; OCR ist nicht integriert. Ein lokaler JavaScript-Parser- und Quellvertrag ist nicht gleichbedeutend mit Kotlin-Build, GPU-Renderprüfung oder Fold7-Test. Der vollständige Animations- und Dateiarbeitsablauf muss auf Hardware noch geprüft werden. **Kein signierter Code60-Release.**

**Animationserhalt:** Die tatsächliche PDF-Projektion verwendet jetzt `ContinuumWorkspaceMotion.transition(set,{kind:'open',duration:950})` aus der freigegebenen HTML-Quelle. Der bestehende unterbrechbare Web-Animations-/RAF-Controller übernimmt Bewegung und Fenstergeometrie, statt dass eine zweite konkurrierende native Fluganimation gebaut wird. Dieser Quellanschluss ist noch kein gemessener Fold7-Animationslauf.
