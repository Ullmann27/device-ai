# Lokaler Coding-Meilenstein – Wiederaufnahme

Basis: PR #48, `integration/continuum-astra-20261021`, `dfd876c23e2e88faa2ff7ee2f7f75138b416929a`.
Entwicklungsbranch: `astra/continuum-local-coding-milestone-20261021`. EXP-00/02/05/07/08/11/12.

Begrenzter Entwurf: vorhandenen authentifizierten llama.cpp-Patchclient als explizit aktivierbaren Kandidatenlieferanten an **dieselbe** Repair-Pipeline anschließen. Standard bleibt deterministisch. Diagnose und begrenzter Codeauszug stammen aus dem eigenen Testlauf. Ein Modellvorschlag erhält keine neuen Tool-/Git-/Gedächtnisrechte; nur exakte erlaubte Hunks, Kritiker, Syntax, echte feste Tests und frischer unabhängiger Worktree können ihn als reviewpflichtigen Kandidaten bestätigen. Lokaler Server fehlt: ehrliche UNAVAILABLE-Meldung, deterministischer Pfad bleibt verfügbar. Keine automatische Installation oder Main-Promotion.

Plan dieses Abschnitts: negative Pfad-/Ausgabe-/Timeouttests; realer Loopback-Transporttest mit kontrollierter Antwort; echte Mini-Repository-Reparatur samt unabhängigen Tests; vollständige Node-/Backend-Regressionen und aktuelle Android-Gates. Keine Leistungsbehauptung für ein nicht vorhandenes GGUF. Ein isolierter Kontrollversuch soll zeigen, welche zusätzliche Fehlerklasse der Anschluss bearbeiten kann, ohne aus Testfixtures Modellintelligenz abzuleiten.

Status: begonnen, noch keine neue Funktion freigegeben. Restquote unbekannt. Nächster Einstieg: `.github/continuum/repair-loop.mjs`, `.github/continuum/backlog-repair-worker.mjs`, `scripts/continuum-local-brain.mjs`. Vor neuen Arbeiten tatsächlichen Remote-HEAD prüfen. Rollback: ausschließlich Commits dieses Branches; bestehende Modell-/Besitzerdateien werden nicht geöffnet.

## Gesicherter Funktionsabschnitt

Der vorhandene REPAIR-Worker erhält einen optionalen lokalen Kandidatenlieferanten. Explizite Aktivierung über `owner-repair --local-brain --local-paths lib/example.mjs`; API-Schlüssel bleibt in der lokalen Umgebung. Ein fester Node-Unterprozess verwendet den vorhandenen authentifizierten 127.0.0.1-Client, JSON-Schema und begrenzte Quelle (höchstens zwei Dateien mit je 1400 Zeichen). Keine Shell aus Modelltext. Kritiker, eindeutiger alter Hunk, Syntax, unveränderte Tests und unabhängiger zweiter Worktree bleiben dieselben. Modellpatches und ihr späteres Erfahrungs-Replay behalten Reviewpflicht und konservative Rangklasse. Standardbetrieb bleibt modellfrei.

Tatsächlich geprüft: drei Integrationstests zuerst rot, danach grün. Gezielte Suite 44/44 grün, einschließlich echtem HTTP→CLI→Patch→Git-Worktree→Node-Test-Durchlauf mit kontrollierter Antwort und kanonischem Backlog-Abschluss erst nach unabhängiger Prüfung. Das ist ein Protokoll-/Pipelinebeleg, keine gemessene GGUF-Modellleistung. Überangepasster Konstantenpatch wird durch negative Testfälle verworfen. Report behauptet ausdrücklich keine unabhängig nachgewiesene Modellausführung.

Android-Baseline des PR48-Stands: 706 Tests, ein Fehler durch versehentlich wiederverwendete, bereits vom vorherigen Worktree veränderte Node↔Kotlin-Backlog-Fixture. Keine Produktreparatur daraus abgeleitet. Frische, für diesen Branch getrennte Paritäts-Fixtures erzeugt; vollständiger Lauf wiederholt sich. Diese Node-Erweiterung verändert keine APK-Signatur, Paketkennung oder neuronalen Gewichte.
