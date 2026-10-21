# Begrenzter persistenter Teamleiter – 21.10.2026

EXP-01 / EXP-12, kompatibel mit EXP-00/02/08/11. Ausgangspunkt:
`377bc05b8a8d96494f4cee7be8665fde3317f1e7`, isolierter Experimentbranch.
Status: `SOURCE_INTEGRATED`, lokale Node-Regressionsbelege; keine Samsung-Abnahme.

Der vorhandene `runTeamleaderLoop` unterstützt optional `stateFile` außerhalb des
Checkouts und `sourceRoot`. Dafür ist ein endliches `maxCycles` erforderlich.
Ohne diese Optionen bleibt der bisherige flüchtige Aufruf kompatibel. Die
Checkpointdatei ist ausschließlich der Zeitplan desselben kanonischen
Teamleiters; Backlog, Job-/Repair-Entscheidungen und Build-Lease bleiben erhalten.

Der Checkpoint speichert Quell-HEAD/-Pfad, unveränderlichen Zyklusrahmen,
Fehlerzähler, Backoff und Sitzung. Er wird vor Arbeit und nach jedem Ergebnis
mit exklusiver temporärer Datei, `fsync`, atomarem Rename und Prüfsumme
geschrieben. Die Prüfsumme erkennt Beschädigung, ist keine Authentifizierung.
Ein Neustart wartet den verbleibenden Backoff ab und setzt das Gesamtlimit
fort. `RUNNING` ohne Abschluss verbraucht bei Wiederaufnahme einen Versuch mit
`INTERRUPTED`; es erzeugt keinen Erfolg und spielt die Entscheidung nicht erneut
ab. `STOPPED`, `LIMIT_REACHED` und `ERROR` starten keine weitere Sitzung.
Ein neuer, ausdrücklich gewählter Checkpointpfad ist ein neuer Lauf.

Ein Scheduler-Lock verhindert konkurrierende Schleifen derselben Sitzung.
Automatische Wiederaufnahme ist nur für einen nachweislich toten PID auf
demselben Host erlaubt. Ein exklusiver Marker im alten Lockverzeichnis schützt
konkurrierende Recovery-Versuche. Unvollständige/korrupt gewordene Lockmetadaten,
fremde Hosts und zurückgebliebene Recovery-Marker benötigen manuelle Prüfung;
bloßes Alter erteilt keine Erlaubnis.

HEAD und sauberer Arbeitsstand werden vor und nach jedem Zyklus geprüft.
Änderungen während der Arbeit verwerfen einen gemeldeten Verifikationserfolg
und stoppen den Lauf. Ein Abort vor Arbeit startet keinen Zyklus; ein Abort
während Arbeit wird `INTERRUPTED`. Der Callback erhält optional
`{signal, cycleId, cycleNumber, sourceHead}`. Synchrone Compilerprozesse bleiben
durch ihre vorhandenen eigenen Laufzeitgrenzen begrenzt; Abort ist kein Beleg,
dass ein Betriebssystemprozess bereits beendet ist.

Tatsächlich ausgeführt: `node --test .github/continuum/local-teamleader-loop.test.mjs`
mit **22/22** bestanden. Darunter echte Git-Fixtures, gespeicherter Neustart,
Gesamtlimit/Backoff, Abbruch, korrupte und manipulierte Zustände, HEAD-/Dirty-
Wechsel, konkurrierende Scheduler sowie ein mit `SIGKILL` beendeter Node-Prozess
und dessen Recovery. Keine Android-Builds, externen APIs oder Gewichtsänderungen.

Nächster Schritt: bestehende CLI-/Werkstatt-Aufrufe mit denselben Opt-in-Feldern
verbinden und auf Samsung Wiederaufnahme, Doze, Akku-/Temperaturgrenzen und
Abbruch beobachten. Die bestehenden Termux-Ressourcenregeln bleiben maßgeblich.
Kein automatischer Main-Merge, Build-Erfolg oder Installationsnachweis.
Rollback: diese optionale Erweiterung und ihre Tests/Dokumentation zurücknehmen;
kanonische Backlog-/Repair-Daten werden weder migriert noch gelöscht.

CLI-Abschluss: `teamleader-cli.mjs --loop --durable-loop` verbindet jetzt dieselbe
persistente Schleife mit dem bestehenden Teamleiter. Ohne explizites
`--max-cycles` gilt für dieses Opt-in ein Gesamtlimit von 24; Restart setzt es
nicht zurück. Checkpoint: `<stateDir>/worker-loop-checkpoint.json` außerhalb des
Quellbaums. Die alten CLI-Modi bleiben kompatibel. CLI-Grenzen und die vollständige
Node-Suite wurden geprüft. Samsung-Prozesswiederaufnahme bleibt offen.
