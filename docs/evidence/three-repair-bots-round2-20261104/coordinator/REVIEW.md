# Unabhängige Prüfung des gemeinsamen Quellstands

Prüfer: Koordinator, 04.11.2026. Die folgenden Läufe wurden vom Koordinator selbst
auf dem gemeinsamen Quellstand ausgeführt. Sie übernehmen nicht lediglich die
Ergebnisse der drei Reparatur-Bots.

## Gebundene Quellen

- Lokaler Quellcommit: `d91fe8b1141d35341b67298a04f3913fd1bf0876`.
- Veröffentlichter, inhaltsgleicher Quellcommit: `b2bc510a59e29d4ece478109e12b9785be11ee7d`.
- Gemeinsamer Git-Baum: `7770e3ef628f301bf40161e4a99906836c63b296`.

Die nachfolgenden Dokumentationscommits ändern diese Produktionsquellen nicht.
Dateihashes, Teilbäume, Befehle und Rohloghashes stehen in `receipt.json`.

## Selbst ausgeführte Prüfungen

| Prüfung | Ergebnis | Aussagebereich |
| --- | --- | --- |
| Sechs bestehende Build-/Release-Vertragssuiten mit erweiterter Signaturmatrix | 81/81 bestanden | Node-Tests der lokalen Skriptverträge; kein echter Release und kein Netzwerkdownload. |
| Separate Signaturprobe | Alt 10/12, korrigiert 12/12 | Sechs Variablen in beiden QA-Modi; echte Skriptausführung mit harmlosen Fixture-Werten und Download-Canary. |
| Vollständige Retrieverquelle mit bestehenden und neuen Tests | 29/29 bestanden | Host-JVM; unbenutzter Event-Decorator ist ein ausdrücklich scheiternder Compile-Stand-in. |
| Separate Tagesgrenzenprobe mit festen UTC-Orakeln | Alt 2/8, korrigiert 8/8 | Sechs Santiago-Fälle sowie Wiener 23- und 25-Stunden-Kontrollen. |
| Sequence-/BrainOrb-Hosttests auf finalem Handler-Kandidaten | 25/25 bestanden | Originale Logikquellen mit dokumentierten Android-/Painter-Stand-ins; keine Android-Queue. |

Diese Zahlen überschneiden sich in ihren geprüften Verhaltensweisen und werden
nicht zu einer angeblich unabhängigen Gesamttestzahl addiert. Die separate
Signaturprobe lief bereits auf dem isolierten Signaturfix; dessen Produktionsblob
ist im gemeinsamen Quellstand identisch. Die vier abschließenden Suite-/Probe-
Läufe verwendeten den oben genannten gemeinsamen Quellstand.

## Quellprüfung und Gegenkontrollen

### Tagesgrenzen

Der Koordinator reproduzierte den Fehler vor der Reparatur mit festen UTC-Werten.
Beide Tagesgrenzen werden nun vom jeweiligen lokalen Kalenderdatum aus aufgelöst.
Damit wird die wegen einer Mitternachtslücke auf 01:00 aufgelöste Uhrzeit nicht
auf andere Tage übertragen. Die sechs Santiago-Fälle scheiterten im alten Code
und bestehen nach der Änderung; beide Wiener Kontrollen bestehen zuvor und danach.
Die vorhandenen Wochen-/Monatsfenster, Overflow-Grenzen, Vertrauensregeln und
Rankingdaten bleiben erhalten.

Die Corpusmessung bleibt bei **42/44 Recall@3**, gerundet 0,955; der bekannte
Theater-Treffer bleibt auf Rang 5. Das ist weiterhin eine offen ausgewiesene
Rankinggrenze. Es wurde keine Rangverbesserung durch diese Tageskorrektur behauptet.

### Abschlussereignisse

Die erste Bot-Korrektur behob verlorene Abschlüsse während einer visuellen Pause.
Bei der unabhängigen Prüfung fand der Koordinator zusätzlich den nicht angehängten
`View.post`-Pfad. Deshalb enthält der finale Kandidat einen expliziten Handler am
Main-Looper für Hintergrund-Abschlüsse. Normale visuelle Ereignisse verwenden
weiterhin `View.post` und die bestehende Generationsprüfung.

Der Memory-Bot prüfte den finalen Produktionsblob
`86d39aedf0fadb6e0633bec42112b36f3a6fea9c` ebenfalls lesend und meldete keinen
weiteren blockierenden Befund. Ende-Ereignisse behalten ihre Auftragsidentität,
visuelles Leeren und Detach erfinden keinen Abschluss, und die bestehende Historie
enthält höchstens 24 IDs. Das ist **keine Kapazitätsgrenze für die Android-
MessageQueue** und kein allgemeiner Last- oder Leak-Nachweis. Der Handler kann die
View bis zur Zustellung halten; es gibt hier keine wiederholte oder verzögerte
Callback-Schleife.

Der Host-Handler wirft bei jedem `post` und simuliert keine erfolgreiche
Zustellung. Deshalb bleibt der reale Queue-Nachweis von den 25 Hosttests getrennt.
Die dafür ergänzten Robolectric-Tests benutzen vor und nach dem Handlerfix den
identischen Testblob `51db7f4f6996b314ff715a7ea0978b9b744578dd`.

### QA-Signaturgrenze

Die Produktionsänderung ergänzt genau eine fehlende Variable,
`CONTINUUM_MENTOR_SIGNING_P12_BASE64`, in der vorhandenen Ausschlussliste.
Der Koordinator reproduzierte den alten Durchlauf bis zur Download-Canary in
beiden QA-Modi. Nach dem Fix werden alle sechs Schlüssel vor Vorbereitung oder
Download mit Exitcode 69 abgelehnt. Die Fixture-Werte werden nicht ausgegeben.
Der Build-Bot prüfte die Ein-Zeilen-Änderung unabhängig und erweiterte die
reguläre Matrix; der Koordinator führte abschließend alle 81 Vertragstests erneut aus.

## Grenzen der Aussage

Die Protokolle in diesem Verzeichnis belegen keinen vollständigen Android-Build,
keine GPU-Darstellung, keinen Gerätebetrieb und keine APK-Abnahme. Der gesonderte
Build-Bericht dokumentiert den tatsächlichen Android-/Robolectric-Status.
Die aus PR #512 geprüften historischen XMLs gelten für dessen gebundenen
Integrationsstand und die dortigen elf UI- beziehungsweise fünfzehn Kalendertests.
Sie sind kein Ersatz für die neuen Abschlusszustellungsfälle.

Rohlogs bleiben unverändert, einschließlich abschließender Leerzeilen und
hostabhängiger Zeitmessungen. Diese Messzeiten werden nicht als Leistungsgewinn
auf Android ausgelegt.
