# ATOMIC-PHASE-PUBLICATION-20261104

## Befund und Beitrag

Geprüfte Basis: `30cd3a89810ed14fa84eca95236d3177e016118d` (PR #492).
Der bisherige `write_new`-Pfad öffnete bereits den endgültigen Dateinamen mit
`open("x")`, bevor JSON vollständig geschrieben war. Ein echter
Serialisierungsfehler oder ein injizierter Schreibabbruch hinterließ damit einen
unvollständigen Phasenmarker. Beim real ausgeführten Konstruktor scheiterte der
erneute Start nach einem unterbrochenen `environment.json`-Schreibvorgang mit
`JSONDecodeError`. Dies ist ein Wiederaufnahmefehler; die Prüfung belegt keine
Trainingsfreigabe durch ungültige JSON.

Die neue isolierte Suite reproduzierte am unveränderten Treiber **vier Fehler bei
zehn Fällen**. Dieselbe Suite besteht mit dem Fix **10/10**. Alle bestehenden
Gate-Suiten bestehen am korrigierten Treiber zusätzlich **36 + 6 + 4 + 29 = 75/75**.
Zusammen: **85/85**. Ein AST-Vergleich bestätigt identischen Code außerhalb von
`write_new` und dem hinzugefügten `tempfile`-Import.

## Implementierung

[`run_experiment.py`](../run_experiment.py) schreibt JSON zunächst in eine eindeutig
benannte temporäre Datei im selben Zielordner. Nach vollständigem Schreiben,
`flush`, Datei-`fsync` und Schließen veröffentlicht `os.link` die fertige Datei
unter dem endgültigen Namen. Diese Operation überschreibt kein vorhandenes Ziel;
auch bestehende und dangling Symlinks bleiben bestehen. Konkurrierende Writer
können höchstens einen endgültigen Namen anlegen. Im regulären Fehler- und
Erfolgsfall wird nur die selbst erzeugte temporäre Datei entfernt.

Die neuen Prüfungen stehen in
[`atomic_phase_publication_gate.py`](../tests/atomic_phase_publication_gate.py).
Sie prüfen vollständige Unicode-JSON, Serialisierungsfehler, Teilwrite-Fehler,
Unsichtbarkeit unfertiger JSON unter dem endgültigen Namen, unveränderte gültige
und ungültige vorhandene Marker, bestehende und dangling Symlinks, konkurrierende
Writer sowie die tatsächliche Konstruktor-Wiederaufnahme. Ein zweiter normaler
Konstruktorstart prüft zusätzlich die Wiederverwendung des vollständigen
Umgebungsbelegs.

Die Fälle verwenden synthetische Eingaben und fangen Prozessstarts ab. Die
Java-Versionsabfrage des Konstruktors wird durch einen ausdrücklich synthetischen
Wert ersetzt. Kein Modell, keine JVM, kein Training und keine Holdout-Auswertung
wurde durch diese Prüfungen gestartet.

## Ausgeführte Prüfung

Python **3.12.14**. Kompakte Ergebnisse, Fallnamen und Hashes:
[`atomic-phase-publication-review.json`](atomic-phase-publication-review.json).
Die vollständigen lokalen Logs und Fixture-Berichte sind darin referenziert.
Fixtures und synthetische Modellbytes gehören nicht in den Commit.

Vom Repositoryverzeichnis aus, mit einem ausdrücklich gewählten Evidence-Pfad:

```bash
PYTHONDONTWRITEBYTECODE=1 python3 experimental/weight-space-20261104/tests/atomic_phase_publication_gate.py --workspace /tmp/continuum-publication-proof
PYTHONDONTWRITEBYTECODE=1 python3 experimental/weight-space-20261104/tests/adversarial_driver_gate.py --workspace /tmp/continuum-publication-proof
PYTHONDONTWRITEBYTECODE=1 python3 experimental/weight-space-20261104/tests/targeted_final_creation_gate.py --workspace /tmp/continuum-publication-proof
PYTHONDONTWRITEBYTECODE=1 python3 experimental/weight-space-20261104/tests/direct_dispatch_gate.py --workspace /tmp/continuum-publication-proof
PYTHONDONTWRITEBYTECODE=1 python3 experimental/weight-space-20261104/tests/registered_profiles_gate.py --workspace /tmp/continuum-publication-proof
```

Für die Red-Gegenprobe wird der neue Suite ein Pfad zum unveränderten Basistreiber
als Positionsargument gegeben. Erwartet sind **6 PASS / 4 FAIL** und Exitcode 1.
Beim lokalen Kopieren nur von Treiber und Tests fehlte zunächst die separate
`CORE_6_10M_PLAN.md`; dieser Profil-Suitenaufruf endete vor der Fallausführung.
Der korrigierte Aufruf mit `--plan` auf die unveränderte Basisdatei bestand 29/29.
Dies war ein Fixture-Aufbauproblem, kein bestandener oder fehlgeschlagener
Gate-Fall.

Treiber vor dem Fix:
`8f2f49bda56ce1f63e86fecf262229acff3d6f5d1ada1d97270b6c1731cda9be`.
Treiber mit Fix:
`e991683e6abf222ca0f213cc703ff0d23ea646efd2772204c069d89e8f542c8f`.
Neue Suite:
`d19eecec009a08417c117e97c14ee8744e6b29307567324ab8dfa286ae621cf5`.

## Kompatibilität und Grenzen

Alle Konstruktor-, Runtime-, Holdout-, Trainings- und Orchestrator-Hashregeln
bleiben unverändert. Der neue Treiber-Hash erhält insbesondere **keine neue
Freigabe für abgeschlossene `8f2f49…`-Studien**. Historische Ausnahmen bleiben exakt
die bereits vorhandenen. Der Fix darf nicht als Erlaubnis zum erneuten Trainieren
oder Auswerten abgeschlossener Studien interpretiert werden.

Das lokale Dateisystem muss Hardlinks innerhalb desselben Verzeichnisses
unterstützen. Andernfalls schlägt die Veröffentlichung ohne Zielmarker fehl.
Eine Zusicherung gegen Stromausfall oder für die Dauerhaftigkeit von
Verzeichniseinträgen erfolgt nicht. Bei einem harten Prozessabbruch kann eine
Stagingdatei übrig bleiben; sie ist kein Phasenmarker und wird nicht wiederverwendet.
Bereits beschädigte endgültige Marker werden weder gelöscht noch automatisch
repariert. Die Änderung bildet keine Transaktion über Modellprozess, Checkpoint,
Log und Receipt hinweg und gibt keine globale Sperre für parallele Experimentläufe.

Die Prüfungen liefern keinen Beleg für bessere Modellqualität, ein erfolgreiches
16M-Training oder Android-/Geräte-/Release-Freigaben. Die unabhängige echte
Prozessabbruch-Gegenprobe von Root wird getrennt geführt.

## Integration und Rollback

Zur Übernahme sind genau vier Dateien vorgesehen: der geänderte Treiber, die neue
Suite und die beiden hier benachbarten Evidence-/Handoff-Dateien. Alle älteren
Testdateien in der lokalen Helferkopie sind unveränderte Prüfabhängigkeiten.
Root integriert und veröffentlicht diesen Beitrag in PR #507; der Helfer hat
keinen eigenen Push, PR-Kommentar oder Merge durchgeführt.

Ein Rollback stellt ausschließlich den vorherigen Treiber aus dem Basiscommit
wieder her. Die neuen Belege bleiben erhalten. Der nächste Schritt ist Roots
unabhängige Integration mit Gegenprüfung am exakten veröffentlichten Quellstand.
