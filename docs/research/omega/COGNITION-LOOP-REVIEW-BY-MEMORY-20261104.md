# Unabhängiger Review: Autonomie-Slice

Reviewer: Bot GEDÄCHTNIS, 04.11.2026. **Nur Quellen-/Evidenzreview; kein eigener JVM-Lauf und keine Produktionsänderung für diesen Review.**

Ergebnis: **Kein materieller Blocker im abgegrenzten PublicStudy-Slice gefunden.** Der gespeicherte GREEN mit 14 Tests gilt für den ausdrücklich dokumentierten Zwischenstand. Für den finalen Stand mit 15 Tests ist der frische gemeinsame Lauf des Koordinators erforderlich; dessen Ergebnis wird hier nicht vorweggenommen.

## Geprüfter Stand

| Datei | SHA256 des aktuellen Reviewstands |
| --- | --- |
| `learning/ContinuumSourceNovelty.kt` | `833a6b8fa31e11f49c54632231d6465b0166110f96b1597b2c302e1a2a90d0d6` |
| `learning/ContinuumPublicStudyScheduler.kt` | `f14706ac6d255467051d01c496c3b7168f111cefaa6d9151fd001a111ff3e093` |
| `learning/ContinuumSourceNoveltyTest.kt` | `452320b6d4de316fcffec034fcb3d6aa3ad74950366f1da46f6e7cec146e71d7` |
| `learning/ContinuumSourceResumeTest.kt` | `f532c17ef3af40daa2189793d1d6f4504148e9cfdd5c32f75a31eac6c0591660` |
| `learning/ContinuumSourceRoundJournalTest.kt` | `376d6a40245a65ccd7ec82bb087a71512566f34dc39c758d13175b5b077267c8` |

Produktionsdateien unter `app/src/main/java/at/heinz/deviceai/`, Tests unter `app/src/test/java/at/heinz/deviceai/`. Zusätzlich gelesen: vollständiger `ContinuumKnowledgeArchive`, tatsächlicher Offline-Aufrufer in `ContinuumAutonomousScheduler`, Service-Deklarationen im Manifest, Autonomie-Handoff, Hostrunner und eingefrorene RED-/GREEN-Quellen samt Befehlen, Digests und Rohlogs.

## Fachliche Prüfung

**Start vor Nebenwirkungen:** Die beiden Schedulerzeilen setzen die Operation auf `START_SOURCE_ROUND` und rufen `markStarted` auf, bevor die ausgewählte Quelle archiviert, in das Notebook geschrieben oder an `learnFromSources` übergeben wird. Der Aufruf wird nicht in ein Fehler verschluckendes `runCatching` eingeschlossen. Ein Schreibfehler erreicht den vorhandenen äußeren Fehlerpfad. Frühere Themenauswahl/Netzabfrage/Entdeckung finden weiterhin davor statt; der Fix beansprucht keine Transaktion über diese bereits vorhandenen Schritte.

**Abschluss:** `markCompleted` bleibt hinter `outcome?.completedEvaluation == true && !cancelled()`. Der Journalwriter erzeugt den vollständigen neuen Zustand, synchronisiert die temporäre Datei und benutzt den vorhandenen Move-Pfad. Ein offener Marker wird zusammen mit dem Abschluss in derselben Datei ersetzt. Die gelesenen Tests erzwingen Start- und Abschluss-Schreibfehler vor Veröffentlichung und prüfen den erhaltenen alten Zustand. Atomare Sichtbarkeit bei unterstütztem `ATOMIC_MOVE` ist von Stromverlust-Dauerhaftigkeit zu unterscheiden; ein Geräte-/Stromausfallnachweis liegt nicht vor, und der bestehende Fallback ohne Atomizitätsgarantie bleibt eine Grenze.

**Historische Deduplizierung:** Nackte SHA-Zeilen bleiben lesbare Abschlüsse. `knownHashes` vereinigt diese weiterhin mit verifizierbaren Archivhashes und vorhandenen Source-Ledgereinträgen; nur ausdrücklich offene `?SHA`-Runden werden davon abgezogen. Historische unmarkierte Daten lösen dadurch keine ungefragte globale Wiederholung aus. Bereits vollständig evaluierte, auch abgelehnte Runden bleiben bekannt. Der zusätzliche Restart-Positivfall ist fachlich sinnvoll, war beim 14-Test-Zwischenstand aber noch nicht enthalten.

**Kapazität:** Offene Marker werden nicht durch `takeLast` entfernt. Sie reservieren Plätze innerhalb der 120 Zeilen; nur abgeschlossene Zeilen werden auf die Restkapazität begrenzt. Bei 120 offenen Runden wird der nächste Start vor dem Schreiben abgewiesen. Maximal 120 erzeugte Markerzeilen benötigen 7.920 Bytes einschließlich Zeilenenden und liegen damit innerhalb der bestehenden 8.192-Byte-Lesegrenze. Die Tests prüfen erhaltene offene Marker unter Kompaktierung und unveränderte Bytes bei voller Kapazität.

**Rücknahme:** Ein alter Writer kennt `?SHA` nicht und kann offene Zustände beim nächsten Schreiben verwerfen. Ein bloßer Downgrade ist deshalb keine fortschrittserhaltende Rücknahme. Der Handoff benennt dieses Risiko und verlangt, Journale/Quellen zu erhalten. Keine automatische Löschung oder Migration ist Bestandteil des Patches.

## Rohbelege und Abgrenzung

Gelesen in `docs/evidence/cognition-learning-loop-20261104/autonomy/`:

- `red-attempt-1`: Compiler 0, JUnit 1; 5 Tests, 2 fachliche Fehler. Beide Restart-Regressionen erhalten `KNOWN` statt `PENDING`; drei bestehende Tests bestehen. Der dokumentierte No-op `LegacyRoundStart.kt` stellt ausschließlich die vormals fehlende Startoperation dar; Archiv, Ledger und bekannte Hashes werden durch echte Produktionsklassen erzeugt.
- `green-attempt-1`: Compiler 0, JUnit 0; unveränderte Raw-Ausgabe `OK (14 tests)`. Produktions-Novelty-SHA `bdd4b2f46576a2a858a781a83129717e8d61f64827548d5af56a602a066e5a05`. Der vollständige PublicStudy-Scheduler wurde mitkompiliert, aber nicht als Android-Service ausgeführt.
- Der aktuelle Novelty-Stand stellt den bisherigen JVM-Rückgabetyp `Path` von `markCompleted` wieder her. Die Teständerungen ergänzen einen positiven Prozessneustartfall, sichern den Classpath-Nullfall und präzisieren einen Testnamen. Die aktuelle Fassung umfasst 15 Tests. `green-final/status.json` belegt lediglich `LOCK_BUSY` vor Compilerstart. Autor-Handoff und `summary.json` grenzen dies ausdrücklich ab.

Alle gelesenen ausführbaren Tests sind synthetische Hosttests ohne Lernlauf. Der gemeinsame eingefrorene C1-Abhängigkeitsbestand ist per SHA gebunden. Keinen Kollegenlauf als eigenen Test gezählt; keine Summe über überlappende Gruppen gebildet.

## Verbleibende, korrekt benannte Grenzen

Der Offline-Aufrufer kann durch PublicStudy markierte Notebook-Quellen fortsetzen und abschließen. Seine eigenen unmarkierten Erststarts bleiben unverändert; dieser Slice repariert daher nicht sämtliche Aufrufwege von `learnFromSources`. Der Marker ist außerdem keine exklusive Scheduler-Lease. Auswahlkonkurrenz, Stromverlust, tatsächlicher Android-Lifecycle und Gerätefunktion sind keine nachgewiesenen Eigenschaften dieses Hosttests.

Diese Grenzen sind im Autor-Handoff offengelegt und begründen keine zusätzliche Ausweitung dieses begrenzten Patches. Vor einer Aussage über den finalen integrierten Quellstand muss der Koordinator seine eigene aktuelle Abnahme und deren Hashbindung ergänzen.
