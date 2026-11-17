# Continuum – Recovery-Meilenstein ab Sol 6.1 (21.10.2026)

**Basis:** Sol-Branch `sol/continuum-isolated-research-20261021`, zuletzt gesicherter HEAD `66e6b0d81a131925c1f9a3e2ccb47b938e6f05e7` (EXP-01/02/05/12). **Status:** `SOURCE_INTEGRATED + ISOLATED_LINUX_SMOKE_TESTED`; keine Android-Gerätefreigabe.

## Tatsächlich ergänzt

1. **llama.cpp-Patchprotokoll:** `scripts/continuum-local-brain.mjs` verwendet statt des flachen `response_format.schema` das verschachtelte `response_format.json_schema.{name,strict,schema}`. Diese Änderung adressiert die 2026 dokumentierte Gefahr, dass flache Schemata von manchen llama.cpp-Versionen ohne Grammatikbeschränkung ignoriert werden. Unabhängige Ausgabevalidierung, `trusted:false` und `autoApply:false` bleiben erhalten. HTTP-/Negativtests in `continuum-local-brain.test.mjs` ergänzt. Kein neuer Modell-/GGUF-Lauf in dieser Sitzung.

2. **Persistenz tatsächlich aufrufbar:** `teamleader-cli.mjs` bietet jetzt `--loop --durable --max-cycles N` an. Ohne `--durable` bleibt der bisherige Lauf unverändert. Die persistente Laufdatei liegt außerhalb des Git-Checkouts; HEAD- und Eigentümerbindung, Lock, Crash-Recovery und begrenzte Zyklusanzahl stammen aus Sols vorhandener `local-teamleader-loop.mjs`. Neue CLI-Negativtests prüfen erzwungenes endliches Limit und unzulässige Betriebsarten. Kein automatisches Aktivieren im Termux-Shell-Loop ohne zusätzliche Batterie-/Doze-Abnahme.

3. **Tatsächlich eingeschränkter Linux-Ausführungsnachweis:** `scripts/continuum-isolated-node.sh` führt nur **eine** kleine, ausdrücklich in einem besitzereigenen Modus-700-Verzeichnis bereitgestellte `.mjs`-Datei aus. Der Wrapper friert Quellbytes in einem eigenen Verzeichnis ein und startet Node über Linux-User-/Mount-/Network-/IPC-/PID-Namespaces, chroot, read-only Bind-Mount, `setpriv --no-new-privs` mit leerem Capability-Bounding-Set, `env -i`, `prlimit`, Deadline und kontrolliertem Prozessabbruch. Keine `.git`-Dateien, Projektgewichte, Besitzerverzeichnisse oder Zugangsdaten werden hineingemountet. Fehlende Linux-Namespaces, Tools oder Rechte bedeuten **UNAVAILABLE**, niemals unsicheren Fallback. Nicht auf Android als funktionierend ausgeben.

## Drei Forschungsprototypen (strikt RESEARCH_SHADOW)

`scripts/continuum-capability-research.mjs` vergleicht drei **kleine, synthetische** Verfahren gegen einfache Baselines:

- **Gegenbeispielgeleitete Auswahl** eines festen Pools numerischer Kandidaten: Baseline 0/4, nach unabhängigen Challenge-Proben 4/4 auf vorab getrennten Zahlen-Holdouts.
- **Provenienzgeprüfte Auswahl** gegen rein zeitliche Sortierung: Baseline 0/3, evidenzbasierter Filter 3/3 auf konstruierten Quellenbeispielen, einschließlich expliziter Abstinenz.
- **Budgetgebundene Kontextauswahl** gegen FIFO: Baseline 0/2, geprüfte Priorisierung 2/2 vollständig abgedeckte synthetische Anforderungen.

`node --test scripts/continuum-capability-research.test.mjs` wurde in einem getrennten lokalen Node-22-Prototyp mit **4/4 PASS** ausgeführt. Mit den oben genannten drei Sandbox-Tests: **7/7 lokale Prototypentests PASS**.

**Wissenschaftliche Grenze:** Die Testfälle und Referenzwerte sind bewusst einfach und von uns konstruiert; Ergebnisse dürfen **nicht** als Generalisierungs-, Modellqualitäts- oder Intelligenzgewinn interpretiert werden. Keine neuen neuronalen Gewichte, keine unbekannten realen Coding-Aufgaben, keine autonome Promotion. Die Module dienen nur als reproduzierbare Versuchsanordnung für spätere echte, versiegelte Datensätze.

## Unabhängige lokale Versuche

In einer Linux/Node-22-Umgebung wurde ein praktischer Namespace-/Chroot-Versuch erfolgreich durchgeführt. Ein Kandidat sah weder eine private Hostdatei noch `/etc/passwd` oder das übergebene Test-Secret; Schreibzugriff auf den staging-code wurde mit `EROFS` abgewiesen; die Loopback-Verbindung schlug fehl. Die lokale, separat erstellte Drei-Fälle-Testfolge für den gleichen Sandbox-Adapter bestand **3/3**, einschließlich Pfad-/Symlink-/Verzeichnisrechten und Kandidatenfehlern.

**Evidenzgrenze:** Die GitHub-Version der Node-Testdateien wurde aus diesem Chat nicht als Gesamtsuite ausgeführt. Die früheren 777 Node-/706 Android-/60 Backend-Pässe gehören zu PR #49 und sind **nicht** neue Prüfbelege für diese Commits. Die Linux-Sandbox ist ein eigenständiger Prüfadapter, **noch nicht** in die kanonische Reparaturpipeline als vertrauenswürdiger Executor eingebaut. Ein Worktree allein bleibt unzureichend. Vor einem produktiven Einsatz sind integrierte End-to-End-Tests, Host-/Kernel-Kompatibilität, Ressourcenmessungen und unabhängiges Review nötig.

## Reproduzierbare Prüfungen

```sh
node --test scripts/continuum-local-brain.test.mjs \
  scripts/continuum-isolated-node.test.mjs \
  scripts/continuum-capability-research.test.mjs \
  .github/continuum/local-teamleader-loop.test.mjs
node --check scripts/continuum-local-brain.mjs
node --check .github/continuum/teamleader-cli.mjs
bash -n scripts/continuum-isolated-node.sh
```

Für einen **ausdrücklich freigegebenen** Einzelversuch auf kompatiblem Linux:

```sh
CONTINUUM_ISOLATION_STAGE=/ABSOLUTER_EIGENER_STAGINGORDNER \
  bash scripts/continuum-isolated-node.sh /ABSOLUTER_EIGENER_STAGINGORDNER/candidate.mjs
```

Das sind Ausführungsanweisungen, keine in dieser Sitzung behauptete GitHub-CI-Abnahme.

## Offene Gates

- Sols nicht gepushte Dateien aus der abgebrochenen Cloud-Sitzung sind über GitHub nicht lesbar. Sie wurden nicht überschrieben; Sols Branch blieb unverändert.
- Unabhängige, aktuelle GitHub-Gesamtsuiten und unabhängiges Sandbox-Security-Review.
- Echte GGUF-Modellinferenz mit dem **neuen** Schema auf passender Hardware und messbarem Kontext-/Tokenprofil.
- Weiterentwicklung der drei synthetischen Shadow-Versuche auf echte neue unabhängige Aufgaben und vorab versiegelte Holdouts; keine Selbstverbesserungsbehauptung vor Nachweis.
- Linux-Ausführer sicher in die vorhandene Reviewpipeline integrieren; Android benötigt eine separate, tatsächlich unterstützte Sandbox-Strategie.
- Aktuelle Samsung-Android→Termux→Android-BUILD-Rückmeldung und Besitzerabnahme.

**Rollback:** Den gesamten separaten ChatGPT-Branch verwerfen. `main`, Sols Entwicklungsbranch, installierte APK, SWB1/CDX1/NEURON-Gewichte und Besitzerdateien wurden nicht angefasst.

## Zusatzmeilenstein: unabhängiger OS-Ausführungsbeleg, weiterhin ohne Autorität

`scripts/continuum-isolation-receipt.mjs` wurde ergänzt. Der vertrauenswürdige Aufrufer gibt einen absoluten, besitzereigenen Staging-Pfad und eine einzelne reguläre `.mjs`-Datei an. Der Adapter ruft den vorhandenen Linux-Ausführer ausschließlich über einen festen `/bin/bash`-Aufruf auf. PATH wird aus dem Node-Laufzeitpfad und festen Systempfaden aufgebaut, keine Shell aus Modelltext. Der Adapter ist nicht in den kanonischen Worker promoted und lädt keine Modelle.

Ein JSON-Receipt dokumentiert Zufalls-Laufkennung, SHA-256 des Kandidaten und Ausführers, Exitcode, Signal, Ausgabemengen/-Hashes, Laufzeit und Quelländerungserkennung. Ausgangswerte: `verified:false`, `reviewRequired:true`, `autoApply:false`, `independentAttestation:false`. Sogar Exitcode 0 bedeutet ausschließlich `EXECUTED_UNTRUSTED`, nie `VERIFIED`. Bei veränderter Quelle, Pfadverstoß, Prozessfehler oder Ausgabegrenze wird abgewiesen. Die Receipt-Prüfsumme ist nur ein Korruptionshinweis, **keine Signatur und kein authentischer Remote-Nachweis**. Es wird kein Kandidatenausgabetext in den Receipt übernommen, um gefälschte Tool- oder Testmeldungen von der evidenzführenden Schicht fernzuhalten.

Tatsächlich auf der vorhandenen Linux/Node-22-Laufzeit lokal neu ausgeführt: `node --test continuum-capability-research.test.mjs continuum-isolated-node.test.mjs continuum-isolation-receipt.test.mjs` **12/12 bestanden, 0 fehlgeschlagen, 0 Skips**. Die zusätzlichen fünf Receipt-Tests prüfen positiven OS-Ablauf ohne Freigabe, fehlgeschlagene Kandidaten, Symlinks/Pfadeskapaden, übergroße stdout-Antwort und absichtlich gefälschte `verified:true`-Modellmeldung. `bash -n` und `node --check` ebenfalls bestanden. Lokale Tests sind keine GitHub-CI-, Samsung- oder GGUF-Abnahme.

Nächster sicherer Schritt: Prüfbeleg mit unveränderlichem Quell-HEAD und unabhängigen **fest definierten Testprofilen** verknüpfen; Modelloutput hat keinen Einfluss auf Testkommando, Prüferentscheidung oder Rechte. Erst nach unabhängiger adversarialer Überprüfung darf eine isolierte Integration in den kanonischen Repair-Worker erwogen werden. Kein Main-Merge und keine automatische Besitzerfreigabe.
