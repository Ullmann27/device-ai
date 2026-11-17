# Fold7 – falsche Chat-Routen in Continuum 0.3.37

## Gerätebeobachtung, 2026-11-03

Auf dem Fold7 wurden folgende Fehlleitungen in der originalsignierten Version 0.3.37 / Code 38 sichtbar: `Was kannst du` und `Wie viel Speicher habe ich?` endeten in öffentlichen Recherche-/Quellenpfaden statt in lokaler Selbsthilfe beziehungsweise Android-Speicherabfrage. `Hallo ?` wurde mit einer unpassenden Quellen-/Gedächtnisbeobachtung beantwortet. Die Meldung `keine bestätigte Gemma-Antwort` ist korrekt als fehlender echter Generierungsnachweis zu behandeln. Die Anzeige `Gemma READY` beweist nur Lade-/Laufzeitbereitschaft, nicht erfolgreiche Textgenerierung. Das Shizuku-Symbol meldete nicht verbunden; die lokale `get_storage_info`-Probe braucht Shizuku nicht.

## Ursache im geprüften Quellcode

`AgentActivity.sendCurrentInput()` rief in AUTO / bei ALWAYS_ON `PublicKnowledgeTopicRouter` und anschließend den semantischen Quellenabruf auf, bevor `BootstrapBrain.decide()` harmlose lokale Begrüßungs-, Selbsthilfe- und Geräteintents klassifizieren durfte. Die Routerprüfung ignorierte außerdem den ausdrücklich gewählten LOCAL-Modus bei automatischen Webfragen. Dies konnte externe Recherche ohne inhaltlichen Bedarf auslösen und Speicherfragen von `DeviceStorageProbe` weglenken.

## Eng begrenzte Korrektur

`OwnerChatIntentRouter` klassifiziert ausschließlich an beiden Enden verankerte, eindeutige Begrüßungen, Selbstfähigkeitsfragen und Gerätespeicherfragen. Diese werden in der Android-Chatsteuerung **vor** Web, Quellenarchiv oder semantischem Gedächtnis behandelt; `get_storage_info` bleibt mit `ReadOnlyToolPlan`, `ToolGate` und `DeviceStorageProbe` verknüpft. Antworten des einfachen Startkerns werden niemals als generative Gemma-Antwort ausgegeben. `PublicKnowledgeTopicRouter` verweigert für dieselben drei Intenttypen automatische Suche. Im LOCAL-Modus wird die automatische Public-Topic/ASK-Websuche nicht angeboten; ausdrücklich vom Besitzer angeforderte Webaktionen behalten ihre separaten Freigaberegeln.

## Nachweise und Grenzen

Der neue reine Kotlin-Router wurde separat mit dem tatsächlich verfügbaren `kotlinc` und `java` kompiliert und mit 17 positiven und negativen Fällen ausgeführt: **17/17 erfolgreich**. Eine neue Android-JUnit-Regressionsdatei testet die gemeinsame Route zum Public-Topic-Router. Der komplette Android-/Gradle-Build auf diesem **neuen Branch** ist noch nicht durchgeführt; die früheren grünen 1.687/1.969 Tests gelten für den vorherigen Release-Code und dürfen nicht als Prüfung dieses Patches ausgegeben werden. Ebenso ist das reale Gemma-/Shizuku-/Geräteverhalten auf dem Fold7 weiterhin separat zu testen.

Der Branch darf nicht automatisch in `main`, den Originalsignierer oder den Live-Updatekanal übernommen werden. Vor Freigabe der nächsten ursprünglichen Update-Version: echte Gradle-/Node-QA, Originalsignierung auf dem geprüften Code, versionCode 39 statt 38, Besitzerfreigabe, Manifest-12-Feld-Validierung und In-App-Geräteabnahme. Bereits installierte Version 0.3.37 und alle lokalen Daten bleiben unverändert.

## Nächste messbare Gerätetests

1. Lokal ohne Internet: `Hallo ?` → passende lokale Begrüßung; `Was kannst du?` → tatsächliche Funktionen mit richtigen Grenzen.
2. Ohne Shizuku: `Wie viel Speicher habe ich?` → frischer Android-`DeviceStorageProbe`-Wert, nicht Web.
3. `Continuum Modelltest Direkt` → echte lokale LiteRT-Inferenz mit Timing/Fehlerlog. Erst eine nachgewiesene Textantwort zählt als Gemma-Funktionsbeleg.
4. Shizuku-App laufend/berechtigt? Dann Shell-Bridge-Bindung und UserService getrennt prüfen; keine ungeprüften Gerätebefehle ausführen.

Keine kosmetischen READY-/Lernzahlen dürfen diese Nachweise ersetzen.
