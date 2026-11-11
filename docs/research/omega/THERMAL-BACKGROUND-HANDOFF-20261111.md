# Thermik – Hintergrundarbeit und Telemetrie

Datum: 11.11.2026. Auftrag ausschließlich vermeidbare Wärme untersuchen und beheben;
keine Reduktion von Modell, Antwortleistung, Kontext, Funktionen oder Grafik.

## Integration

- Verantwortlich: Root der Thermik-Sitzung.
- Basis: PR #769, `integrate/omega-apk54-20261111`,
  `f88d7d1936b9c0d53c176a0af31108fb863199e6`.
- Ziel: Root-Thermik-PR #773; anschließend dieselbe gemeinsame APK-Integrationslinie.
- Worker: `fix/thermal-background-20261111`.
- Code: `1785a9a1b169e3f49a725f5cb75bc9c0759f4842`.
- Betroffene vorhandene Schnittstelle: `ContinuumLearningCore.status()` (EXP-04/06).
- Status: Quellen repariert und fokussiert hostgeprüft; Gerätebestätigung `NOT_RUN`.

## Nachgewiesene zusätzliche Arbeit

`AgentActivity.dashboardRefresh` fordert bei sichtbarem Cockpit alle fünf Sekunden
Telemetrie an. Der vorhandene Aufruf ist bereits gegen parallele Sammlung und
unsichtbare Verbraucher geschützt. `ContinuumDashboardCollector.collect()` erzeugt
dabei eine neue `ContinuumLearningCore`-Instanz und ruft `status()` auf.

Auf der Basis dekodiert jeder Statusaufruf denselben unveränderten SWB1-Checkpoint
und berechnet erneut beide festen Prüfsätze: acht Intent- und sechs Semantikfälle.
Das ist zusätzlicher CPU-/Allokationsaufwand; es handelt sich nicht um Gemma-Training.
Eine Temperaturwirkung oder der Anteil am gemeldeten extremen Hitzeproblem ist
damit **nicht** am Fold7 belegt.

## Reparatur

Ein einzelner Prozesscache hält ausschließlich unveränderliche Messwerte, deren
Modellmetadaten und den exakten geprüften Checkpoint. Jeder Statusaufruf liest die
aktuellen Datei-Bytes; Wiederverwendung setzt vollständige Bytegleichheit und
gleiche Intent-/Semantik-Prüfsätze voraus. Zeitstempel, Dateigröße und Updatezähler
sind keine Ersatzschlüssel. Ein fehlender Checkpoint bezeichnet den vorhandenen
deterministischen Startencoder; ein neu erstellter Checkpoint invalidiert diesen Fall.

Bei Abweichung läuft weiterhin die bestehende Validierung samt unveränderter
Wiederherstellung des letzten guten Zustands. Eine erfolgreiche Wiederherstellung
wird unter den validierten reparierten Bytes gespeichert. Beschädigte Daten ohne
gültige Wiederherstellung können keine alten Messergebnisse übernehmen.

Experience-Statistik und letzter Audit werden bei **jedem** Aufruf frisch gelesen.
Der bestehende `CORE_LOCK` serialisiert Cachezugriff und Lernkernänderungen.
Es gibt keine Cache-Datei, keinen Timer, keine zusätzliche Datenbank, keine
Änderung der Lern-/Antwortpfade und kein verlängertes Telemetrieintervall.

## Tatsächliche Prüfungen

| Prüfung | Ergebnis |
| --- | --- |
| Unveränderte Produktion + fünf neue Regressionen | 3 PASS / 2 FAIL / 0 SKIP |
| Erste Cachefassung, sieben Regressionen | 7 PASS / 0 FAIL |
| Finale Fassung plus bestehende Core-/Encoder-Nachbarn | **27 PASS / 0 FAIL / 0 SKIP** |
| Vollständiger Gradle-/Android-Build | NOT_RUN durch diesen Worker |
| Unabhängiger Inferenz-Worker: fünf zusätzliche Randfalltests | **5 PASS / 0 FAIL / 0 SKIP** |
| Physisches Fold7, Gemma, Temperatur/Energie, Tokens/s | NOT_RUN |

Die Prüfungen kompilieren die aktuelle `ContinuumLearningCore.kt` und Tests mit
Kotlin 2.4.0/JDK21 gegen die vorhandenen echten kompilierten App-Abhängigkeiten.
Dies ist kein neuer vollständiger Android-Build. Quellhashes, Befehle und Rohlogs:
`docs/evidence/thermal-background-20261111/`.

Die sieben neuen Fälle prüfen Wiederverwendung über neue Instanzen,
24 parallele Leser, frische Besitzerstatistik, Austausch bei identischer
Dateigröße/Zeit/Updatezahl, beschädigte Bytes, Rückkehr zum alten Checkpoint und
geänderte Prüfsatz-Eingaben. Bestehende Coretests decken auch Modell-Recovery ab. Die unabhängigen Zusatztests
prüfen Recovery nach warmem Cache einschließlich unveränderter Schadensdatei und
einmaligem Audit, getrennte Besitzerstatistiken bei gleichen Modellbytes,
gleichzeitigen Kaltstart, gelöschten Checkpoint und beschädigte Backupbytes.
Diese fünf Fälle liefen separat gegen exakt denselben Produktionshash; sie sind
kein zusätzlicher vollständiger Android-Lauf.

## Begrenzte Arbeitsmessung

Ein synthetischer Hostlauf ruft den tatsächlichen Produktions-Statuspfad 1.000-mal
über neue Kerninstanzen auf; beide Varianten liefern exakt dieselbe Prüfsummen-Summe
der ausgegebenen Messwerte. Die Anzahl verschiedener Intent-Messobjekte sinkt von
1.000 auf 1; bei unverändertem Modell läuft die Prüfsatzberechnung einmal.

Die erste Variante serialisierte den Checkpoint bei jeder Abfrage erneut. Ihre
Zeitmessung lieferte keinen verlässlichen Vorteil; sie wurde ersetzt. Rohlogs ohne
`exact-` dokumentieren diese **nicht übernommene** Variante.

Die finale Fassung verglich die bereits gelesenen Bytes. Zwei Vergleiche ergaben
je 1.000 Statusaufrufen insgesamt 2,295 s → 1,529 s und 1,626 s → 1,108 s gemessene
CPU-Zeit des ausführenden Hostthreads. JIT-Aufwärmung und gleichzeitige Hostlast
machen diese Zahlen variabel; sie sind keine garantierte Beschleunigung und kein
Beleg für die Gesamt-App, Akkuenergie oder Fold7-Temperatur. Die Reduktion
identischer Berechnungen und unveränderte Ausgaben sind separat überprüft.

Wiederholung der fokussierten Tests:

```bash
CONTINUUM_QA_OUT=/tmp/continuum-thermal-status \
  python docs/evidence/thermal-background-20261111/run_pure.py verification
```

Der Harness erlaubt `THERMAL_REPO`, `CONTINUUM_KOTLIN_CACHE`,
`CONTINUUM_COMPILED_APP`, `CONTINUUM_JAVA` für vorhandene lokale Toolchains.
Er startet keinen Netzwerkdownload oder Gradle-Lauf. Die konkreten tatsächlich
benutzten Klassenpfade stehen in den Manifesten. `StatusBenchmark.kt` enthält die
synthetische Messschleife.

## Weitere untersuchte Pfade

- Offline-/Public-Study-Jobs: endliche Zyklen mit Zeit-/Ressourcengrenzen;
  kein nachgewiesener heißer Dauerloop. Zustimmung/Internet-Gates bleiben erhalten.
- Gedächtnis-/Rechenereignisse: wartende Dispatch-Threads ohne aktives Polling.
- Trainingswidget: ereignisgesteuert, kein dauernder Pollingdienst.
- Gesichtsabgleich: explizite Sitzung mit Einzelarbeit und Stichprobenabstand;
  kein Beleg für automatische parallele Dauerauswertung bei Modellaufruf.
- `NeuralPracticeJobService` benutzt ein wiederverwendetes Abbruchflag ohne
  Worker-Guard. Als separater möglicher Lifecycle-Mangel notiert; keine belegte
  Verbindung zur extremen Hitze, kein spekulativer Eingriff in diesem Patch.

## Unabhängige Prüfung des Modelllade-Fixes

Der vollständige Diff der Inferenzarbeit bis `e92265fdbd866e56efb83f0c2b7dc69bc256734a`
wurde gelesen: `sourceForLoad()` behält `get(..., verifyBytes=true)`, Identität,
Dateigröße und vollständigen SHA-256 innerhalb desselben Katalog-Monitors.
Native Capability-Prüfung und alle Modellparameter bleiben bestehen. Der zweite
identische Dateiscan entfällt. Kein Blocker in dieser Quellprüfung gefunden.
Dateiveränderung nach Hashprüfung vor nativem Öffnen bleibt eine schon bestehende
Grenze; dieser Patch behauptet keine neue dateideskriptorgebundene Integrität.

## Nächster Schritt und Rücknahme

Root übernimmt den kleinen Patch in PR #773 und führt den gemeinsamen Compile-/
Regressionslauf aus. Inferenz-Worker hat die Bytecache-Fassung unabhängig ohne Blocker geprüft.
Gerätemessung: dieselbe lokale Frage mit identischem Modell, Prompt, Renderzustand,
Starttemperatur und Ladezustand vergleichen; Temperatur, Android-Thermalstufe,
Antwortzeit und Hintergrund-CPU getrennt festhalten.

Rücknahme: diesen Quellencommit revertieren; keine Nutzerdaten, Dateiformate,
Gewichte, Signatur, Paketkennung oder Einstellungen wurden geändert.
