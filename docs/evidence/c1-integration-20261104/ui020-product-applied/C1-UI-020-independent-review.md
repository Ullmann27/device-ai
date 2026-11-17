# C1-UI-020 — unabhängiger Produktreview V1

## Ergebnis und Quelle

**Kein materieller neu eingeführter Quellblocker im geprüften Produkt-v1; Laufabnahme bleibt separat.** Geprüft wurde ausschließlich der fünf Dateien umfassende Vorschlag mit SHA256 `fb3799bfc0b9045d72563dd03ac9e24c0ea262c9920b42bbd02af2f5965ff12d` gegen C1 `f31855138d77ecb5e05e7a17a620b1fd6e9d28c5`, Tree `323227c1c05017b0733509e24f0aebc2cc9faa25`.

Alle fünf Basisdateien sind bytegleich zu den eingefrorenen Vorschlägen sowie zu 59d8d7e/cdea744. Jede Hunkstelle wurde im Speicher auf die unveränderliche C1-Basis angewendet; alle vollständigen Zielblobs und SHA256 stimmen mit dem Produktmanifest überein. Es wurden keine Repositorydateien geändert und keine Tests, Builds oder Modelle ausgeführt.

Die Activity-Basis `bc1a08495f56696b2f0a6613260beb92bb3dbc62` wird im Vorschlag zu `26cbaa9362171b012f2285f85fc671f95c19510a`. C1 bleibt einziger Einbauer.

## Geprüfte Verbindungen

| Bereich | Eigener statischer Befund |
| --- | --- |
| Listener/Lebenszyklus | Genau eine Callback-Eigenschaft am vorhandenen Hologramm. buildConversationPane ersetzt sie nach Zuweisung von homeView; die Lambda liest das jeweils aktuelle Activity-Feld und prüft isFinishing/isDestroyed. onDestroy setzt sie vor weiterer Aufräumarbeit auf null. Kein alter Home-View wird in dieser Lambda eingefangen. |
| Avatarinstanz | Der bestehende hologramView wird beim Pane-Neubau wiederverwendet und aus seinem alten Parent entfernt. Der Patch erzeugt keinen zweiten Avatar, Renderer-Controller oder Scheduler. |
| Präferenz | Derselbe bestehende Store continuum-cockpit-presentation und Schlüssel compact; avatar-3d bewahrt den 2D-/3D-Rückweg der älteren Auswahl. Die nativen bestehenden Head-/3D-Setter behalten ihre bisherigen Appearance- und Rendererpfade. |
| Einstieg | Hologramm-Longpress, Systemzentrum-Avataraktion und Shell-Appearanceaktion rufen dieselbe showAppearanceChooser-Funktion auf. Der neue vierte Eintrag verwendet denselben Callback. |
| Layout/Cache | compactPresentation wird in configure an die Shell gegeben, bei der Home-Messung und bei shellGeometry übergeben und in JarvisShellView sowohl als Cachebedingung als auch bei der Geometrieerzeugung verwendet. refreshPresentation fordert danach ein Layout an. |
| Avatar-/Touchbereiche | Native Aktionen und Avatargrenzen werden aus derselben shellGeometry aufgebaut. Im neuen Kompaktzweig sind Kopf- und Orb-Rechteck horizontal durch 10 dp getrennt; beide liegen in einer einzigen 120 dp hohen Livefläche. Der vorhandene reine Orb-Renderer verwendet bei deaktiviertem Kopf die ganze Livefläche. |
| Normalmodus | Nach rein textueller Entfernung der bei compactPresentation=false unerreichbaren neuen Zweige/Parameter ist die gesamte JarvisShellLayout-Datei wieder bytegleich mit C1. reference() und normale Phone-/Wide-Reflowkörper bleiben erhalten. |
| More/Authority | Die neue CenterAction führt nur zu showMoreWorkAreas → showDashboardCategory. Der vorhandene makeAction-Adapter schließt den Systemdialog; eine weitere ausdrückliche Kategorieauswahl nutzt die bestehenden Routes. Kein Job-, Tool-, Netzwerk- oder Modellaufruf wird unmittelbar ergänzt. |

## Breite Kompaktansicht: zulässige Grenze, kein allgemeiner Flächengewinn

compactPresentation=true umgeht auch bei einem breiten Referenzprofil reference() und verwendet Reflow. Der Avatar wird 120 dp hoch; der übrige Inhalt wird als eigene vertikale Blöcke angeordnet. **Die Gesamthöhe kann dadurch wachsen und die Conversation kann tiefer liegen.**

Konkrete reine Quellableitung, kein ausgeführter Layouttest: Für Shell 840×649 dp, Font 1, ohne externe Reservierung liefert der Normalzweig 649 dp Inhalt. Der neue Kompaktzweig ergibt 858 dp:

64 + 10 + 70 + 10 + 120 + 10 + 128 + 10 + 52 + 10 + (2×64 + 10) + 10 + (148 + 10) + 56 + 12 = 858.

Das ist mit dem begrenzten V1-Vertrag vereinbar: Produktmanifest und eingefrorener Test fordern den Gesamthöhengewinn ausdrücklich nur beim 412×915-Phone. Die bestehende Layoutregel erlaubt vertikales Scrollen und priorisiert unverkleinerte Bedienelemente. Der Kommentar „later controls move upward“ darf daher nur für die schmale Zielansicht verstanden werden. Eine universell kürzere Seite ist nicht belegt und wird hier nicht behauptet.

Statisch bleiben Profilspalten und bisherige Text-/Controlhöhen erhalten. Tatsächliche Lesbarkeit, Zeilenumbrüche und Optik der breiten Kompaktansicht sind **nicht visuell abgenommen**. Die acht eingefrorenen Fälle erzeugen Compact-Aufnahmen nur am 412er Phone; die breiten Profile laufen darin im Normalmodus. Ein späterer 8/8-GREEN ist deshalb kein Bildbeleg für breite Compact-Ansichten.

## Eingefrorener Test separat geprüft

Testpatch SHA256: `fdd1d7bccba6c400ccaa2edc0fad8224b83da9dfcbf1be9488a07327829f9b7e`.

Der Patch verändert genau ContinuumReferenceCockpitUiTest.kt. Sein Ziel ist auf f318 bereits vollständig enthalten: Blob `315f42d32518f0c8de4a73cb46c349bd8dbb04d6`, SHA256 `b162dda889669fb6818b9e35114a1874f7991d3df3d0d2f83da9b937e9748547`. Alle acht Testmethodennamen bleiben identisch. Der lexikalische Assertion-Ausdruckzähler steigt von 77 auf 151; das ist weder eine Laufzählung noch allein ein Beweis semantischer Vollständigkeit.

Die neuen Navigationsprüfungen verwenden vorhandene native Controls, die angehängte sichtbare Hierarchie, echte Scrollvorfahren und DOWN/UP-Dispatch durch Decor. Es gibt kein performClick und kein Ignore-/Assumption-Ausweichen. Reflection liest weiterhin Activity-Felder; die bereits bestehende explizite Stop-Zustandsfixture benutzt setBusy. Die reale Navigation wird dadurch nicht ersetzt.

Nicht von den acht Fällen als eigene Laufsequenz abgedeckt sind: tatsächlicher Hologramm-Longpress, Systemzentrum-Einstieg in Appearance, aktive 3D-Auswahl/-Rendering, ausdrücklicher Callback-null-Beleg nach Destroy und erneute Auswahl unmittelbar nach Pane-Rebuild. Diese Verbindungen wurden hier nur im Quelltext gelesen. Die neben dem Paket vorhandene Scratch-Datei JarvisShellLayoutTest ist kein Bestandteil des eingefrorenen Acht-Fälle-Testpatches.

## Neu gemeldeter C1-RED und getrennte Folgearbeit

Während dieses Reviews meldete Root den tatsächlichen V1-RED: 8 Fälle, 1 PASS, 7 FAIL, 0 SKIP, Exit 1; Archiv-SHA256 `191c9330db35639c1bb9bfea11f6dfaec0adfd25f683bb4d4105865de9ecc966`. Dies ist an dieser Stelle eine klar bezeichnete Root-Mitteilung; das Archiv ist in diesem V1-Quellreview nicht unabhängig ausgewertet.

Die fehlende Kompaktwahl und der schon auf der Basis vorhandene 39-dp-Status-Touchbereich bedeuten, dass Produkt-v1 allein keine Gesamtbehebung der aktuellen REDs beweist. Der zusätzliche Status-Touchfehler wird auf ausdrücklichen Root-Auftrag als separater Vorschlag geprüft. Der eingefrorene V1-Test wird nicht geändert. Andere Fixture-Befunde bleiben bei repair_review.

Vollständige Hash-/Hunk-/Quellbelege stehen in C1-UI-020-independent-review.json und frozen/. **Kein neuer Runtime-PASS, keine Geräte-/GPU-/IME-Abnahme und kein externer C2-ACK.**

