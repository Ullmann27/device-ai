# Autonomy: Quellenrunde nach Abbruch fortsetzen

Task: AUTONOMY-COGNITION-LOOP-20261104. Experimente: EXP-01, EXP-04, EXP-12.
Arbeitszweig: `research/continuum-cognition-learning-loop-20261104`, Draft #528.
Basis der ersten Hostmessung: `f2470049b691c825f4d77b1156325e41a51dbf0a`.
Status: SOURCE_INTEGRATED; 14 Hosttests auf dem früheren exakten Snapshot grün.
Aktueller eingefrorener Stand mit 15 Fällen: NOT_RUN. Root übernimmt die gemeinsame Abnahme.

## Belegter Fehler

`ContinuumPublicStudyScheduler.kt` archiviert die gewählte Quelle vor `learnFromSources`.
`ContinuumSourceNovelty.knownHashes` wertete bereits das Archiv beziehungsweise jeden nicht
widerrufenen SOURCE_TEXT-Eintrag als bekannt. Ein Abbruch vor dem Evaluationsabschluss konnte
die Quelle deshalb dauerhaft aus der nächsten Auswahl entfernen. OBSERVED und REPLAY_PENDING
belegen allein keinen abgeschlossenen Audit.

Die unveränderten Produktionsquellen kompilierten auf dem Host. Zwei Regressionen starteten
einen neuen JVM-Prozess nach Archivierung beziehungsweise Ledger-Beobachtung: beide meldeten
KNOWN statt PENDING. Die drei bisherigen Novelty-Tests bestanden. Rohdaten:
[`red-attempt-1`](../../evidence/cognition-learning-loop-20261104/autonomy/red-attempt-1/).
Der dort dokumentierte `LegacyRoundStart.kt` bildet ausschließlich die vormals fehlende
Startmarkierung als No-op ab; Archiv, Ledger und Novelty sind tatsächliche Produktionsklassen.

## Änderung

Genau zwei Produktionsdateien geändert:

- `app/src/main/java/at/heinz/deviceai/learning/ContinuumSourceNovelty.kt`
- `app/src/main/java/at/heinz/deviceai/learning/ContinuumPublicStudyScheduler.kt`

Das vorhandene `learning-core-v2/source-attempts-v1.txt` enthält weiterhin nackte SHA-Zeilen
für abgeschlossene Runden. Eine `?SHA`-Zeile kennzeichnet eine begonnene, unvollständige Runde.
`markStarted` schreibt sie mit Dateisynchronisation und dem bestehenden atomaren Move-Pfad,
bevor der Scheduler Archiv oder Notebook für diese Quelle verändert. Schreibfehler werden
weitergereicht; Archivierung und Training werden dann nicht erreicht.

`knownHashes` nimmt explizit offene Runden aus der Menge bekannter Quellen heraus.
`markCompleted` ersetzt den offenen Zustand atomar durch den Abschluss. Der bestehende
Aufrufer verlangt weiterhin `completedEvaluation == true` und `!cancelled()`.
Auch abgelehnte, vollständig evaluierte Runden bleiben dedupliziert. Der bisherige
JVM-Rückgabetyp von `markCompleted` bleibt erhalten.

Das Limit bleibt bei 120 Journalzeilen und höchstens 8192 eingelesenen Bytes. Offene Runden
reservieren Plätze: bei einer offenen Runde bleiben höchstens 119 Abschlusszeilen.
Offene Marker werden niemals durch Kompaktierung entfernt. Bei 120 offenen Runden schlägt
eine weitere Startmarkierung vor Nebenwirkungen fehl. Archiv und Ledger ergänzen weiterhin
kompaktierte Abschlüsse. Abschlüsse anderer Aufrufer erhalten fremde offene Marker.

## Prüfung

Toolchain: Kotlin 2.4.0, Temurin JDK 17.0.20.1, JUnit 4.13.2, vorhandenes Android-36-JAR.
Der echte geänderte Scheduler wurde mitkompiliert. Es gab keinen Android-Service-Lauf.
Unveränderte reale C1-Abhängigkeiten stammen aus dem eingefrorenen Klassen-JAR
`44e45d4c047a1cd65a4ca8b8faccae498a559b09cb4bd5ae99a04db4ac94048d`.
Quell- und Dependency-SHA256, Befehle, Exitcodes und Rohlogs stehen in den jeweiligen
`manifest.json`, `commands.json`, `status.json`, `compile.txt` und `junit.txt`.

- RED: 5 Tests, 2 fachliche Fehler, 3 bestehende Tests bestanden; Compile 0, JUnit 1.
- Erster GREEN: 14/14 bestanden; Compile 0, JUnit 0.
- Abschließender eigener Versuch: vor Compilerstart LOCK_BUSY nach 35 Sekunden.
- Aktuelle 15 Fälle: NOT_RUN. Keine weitere eigene JVM-Prüfung; Root prüft alle drei Slices gemeinsam.
- Quellenprüfung: Start vor Archiv/Notebook/Training; Abschlussgate und Besitzerschalter erhalten.
- `git diff --check`: Exit 0.

Der 14er-GREEN bindet `ContinuumSourceNovelty.kt` an SHA256
`bdd4b2f46576a2a858a781a83129717e8d61f64827548d5af56a602a066e5a05`.
Danach wurde der bisherige JVM-Rückgabetyp wiederhergestellt, die Test-Nullability korrigiert,
der erledigte Kaltstart ergänzt und ein Test umbenannt. Der aktuelle Novelty-Hash ist
`833a6b8fa31e11f49c54632231d6465b0166110f96b1597b2c302e1a2a90d0d6`.
Der Scheduler blieb seit dem GREEN bei
`f14706ac6d255467051d01c496c3b7168f111cefaa6d9151fd001a111ff3e093`.
Der frühere PASS wird ausdrücklich nicht auf diese spätere Änderung übertragen.

Die gezielten Tests verwenden nur synthetische öffentliche Quellen und temporäre Verzeichnisse.
Sie prüfen Prozessneustart, OBSERVED/REPLAY_PENDING, akzeptierte und abgelehnte Abschlusszustände,
Kompaktierung, volle offene Liste, ungültige SHA, erhaltene REVOKED-Zeilen sowie Start- und
Abschlussschreibfehler. Es läuft kein Training; ein erfolgreicher Modellaudit wird nicht behauptet.
Compiler, JUnit und Neustartproben sind zeitlich begrenzt. Beide gemeinsamen Build-Locks werden
eingehalten; kein Gradle, kein Download, keine dauerhafte Schleife.

Reproduktion mit vorhandenem eingefrorenem Abhängigkeits-JAR und neuem Ausgabeverzeichnis:

```bash
python3 tools/cognition-learning-loop/autonomy/run_host.py /tmp/autonomy-fresh \
  --base-jar /workspace/scratch/344de8bc2104/memory-jvm-base.jar
```

## Grenzen und Kompatibilität

Historische Archiv- oder Ledgerquellen ohne Startmarker bleiben bekannt. Ein früherer
Lernabschluss ist dort nicht sicher rekonstruierbar; diese Änderung erzeugt daraus keine
neuen Trainingsaufträge. Der Offline-Aufrufer kann bereits markierte Notebook-Quellen
fortsetzen und über denselben Abschlussweg beenden. Seine eigenen unmarkierten Erststarts
wurden nicht geändert.

Der Marker ist keine exklusive Scheduler-Lease. Bereits gleichzeitig ausgewählte Runden
werden weiterhin durch die bestehende CORE_LOCK beim Training serialisiert; eine zusätzliche
Auswahlkoordination ist nicht Teil dieses Slice. Prozessabbruch wurde tatsächlich geprüft,
Stromverlust/Android-Dateisystem, voller App-Build, Fold7 und Gerätefreigaben nicht.

Modellgewichte, Lernparameter, Internet-/Offline-Schalter, Rechte, Kosten und Budgets bleiben
unverändert. Keine neue Queue, kein neuer Scheduler, kein Main-Merge, Release oder Installation.

## Übergabe und Rücknahme

Root übernimmt Commit und Veröffentlichung. Danach den vollständig integrierten App-Baum
nach dem bestehenden C1-Vertrag prüfen. Dieser Hostnachweis ersetzt keine Geräteabnahme.

Rücknahme betrifft nur die zwei Produktionsdateien dieses Slice; Journale und Quellen nicht
löschen. Eine ältere Version stellt die ursprüngliche Resume-Lücke wieder her und berücksichtigt
`?SHA`-Zeilen bei eigenen Journal-Schreibvorgängen nicht. Offene Zustände vor einem Downgrade
deshalb als Fortschrittsbeleg erhalten; keine automatische Bereinigung oder Migration eingeführt.
