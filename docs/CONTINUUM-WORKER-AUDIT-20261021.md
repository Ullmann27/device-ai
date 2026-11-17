# Lokaler Worker-/Teamleiter-Audit, 21.10.2026

Geprüfte Basis: `1c255d25c1597b925ddeb14e5acad717134eaa33` (PR #42), Arbeitsbranch `sol/continuum-quality-20261021`. `AGENTS.md`, Experimentvertrag und Experimentregister wurden gelesen; der Hauptaudit hat die aktuelle Issue-#41-Fassung und den Remote-HEAD abgeglichen. Dieser Teil betrifft ausschließlich die bestehende lokale Node-Backlog-/Repair-Pipeline. Keine neue Anwendung, keine neue Architektur, keine externen Dienste oder bezahlten Modellaufrufe.

Status der Änderungen: `SOURCE_INTEGRATED`, durch lokale Linux-/Git-/Node-Integrationstests belegt. Kein Android-/Termux-Gerätetest, APK-Build oder Installationsnachweis dieses Audits.

## Reproduzierte Fehler und Korrekturen

1. **INVESTIGATE konnte nach einem Prozessabbruch dauerhaft in VERIFYING hängen bleiben.** Der Worker persistierte `submit` und anschließend `complete` beziehungsweise `fail` in getrennten Backlog-Transaktionen. Ein echter Kindprozess wurde direkt nach der ersten persistierten Submission mit Exitcode 78 beendet. Sowohl bei einem tatsächlich grünen als auch bei einem tatsächlich roten Node-Test blieb vor der Korrektur `VERIFYING` zurück. Dieser Zustand besitzt keine Claim-Lease und wird vom kanonischen `expire` nicht zurückgesetzt. Jetzt werden Submission und tatsächlicher Prüfentscheid wie beim vorhandenen Owner-Repair-Worker in einer atomaren `BacklogStore.apply`-Transaktion geschrieben. Der grüne Fall bleibt mit passender Commit-/Report-SHA-Evidenz `DONE`, der rote wird mit negativer Evidenz wieder `READY` innerhalb seines Versuchsbudgets.

2. **Abgelaufene INVESTIGATE-Claims wurden bei unverändertem HEAD weder vom Scheduler noch vom direkten Worker wieder aufgenommen.** `nextEligible` ignoriert `IN_PROGRESS`; im Gegensatz zu den Owner-REPAIR-/TEST-Pfaden fehlte davor die kanonische Lease-Ablaufbehandlung. Beide Einstiegspunkte prüfen nun abgelaufene Investigation-Claims und verwenden die vorhandene `expire`-Operation. Nach einem abgelaufenen ersten Versuch wird die Aufgabe erneut auswählbar; nach dem zweiten bei `maxAttempts=2` bleibt sie `FAILED`. Ein noch gültiger Claim samt Token und Backlog-Dateibytes bleibt unverändert. Kein zusätzlicher Versuch und keine zusätzliche Autorität wird vergeben.

3. **Der direkte Investigator konnte Berichte und Backlog-Zustand in den Quellcheckout schreiben.** Der bisherige Schutz verwendete nur `path.resolve`; ein Bericht direkt im Repository oder über einen auf das Repository zeigenden Symlink wurde tatsächlich geschrieben, und die Aufgabe konnte trotzdem fertig werden. Der Worker verwendet jetzt denselben bestehenden `resolveOutputDestination` wie die Owner-Worker. Backlog und Bericht müssen nach Auflösung vorhandener Symlink-Vorfahren außerhalb des Quellcheckouts liegen. Vier Negativfälle prüfen direkte und über Symlinks adressierte Backlog-/Berichtsziele, ohne Claim oder Quellcheckout zu verändern.

Alle neuen Regressionen standen vor ihrer jeweiligen Korrektur reproduzierbar auf Rot. Ein anfänglicher Fehler im Testprozess-Aufruf (`argv[1]` war JSON statt Dateipfad) wurde ausschließlich im Testaufbau korrigiert; danach zeigten die Crash-Regressionen den tatsächlichen Zustand `VERIFYING` statt des erwarteten `DONE`/`READY`.

## Validierung

Linux x86_64, Git 2.52.0. Der folgende fokussierte Lauf bestand sowohl auf dem unterstützten **Node v22.23.3** (`/workspace/toolchain/node22/bin/node`) als auch zusätzlich auf Node v24.19.0. Der Node-Gesamtlauf und dessen Basisbefunde werden im übergeordneten Audit getrennt berichtet.

```sh
node --test \
  .github/continuum/backlog-investigator-recovery.test.mjs \
  .github/continuum/backlog-investigator.test.mjs \
  .github/continuum/autonomous-teamleader.test.mjs \
  .github/continuum/teamleader-end-to-end.test.mjs \
  .github/continuum/development-backlog.test.mjs \
  .github/continuum/backlog-repair-worker.test.mjs \
  .github/continuum/owner-contract-worker.test.mjs \
  .github/continuum/shared-build-lease.test.mjs \
  .github/continuum/local-teamleader-loop.test.mjs
git diff --check
```

Ergebnis auf beiden Node-Versionen: **97/97 bestanden, 0 fehlgeschlagen, 0 übersprungen**; `git diff --check` ohne Befund. Davon neun neue Regressionen. Die Tests verwenden temporäre echte Git-Repositories, feste Node-Testprofile und echte Kindprozesse. Geprüft werden auch reale Red→Repair→Green-Patches in zwei Worktrees, Owner-TEST-Evidenz, unveränderte Originalquellen, gemeinsame Build-Lease, abgelehnte manipulierte/lebende Locks, konkurrierende Backlog-Schreiber sowie Loop-Backoff und Wiederholungsgrenzen. In diesen fokussierten vorhandenen Suiten trat kein zusätzlicher Basisfehler auf.

## Architektur, Experimente und Grenzen

- **EXP-01 / EXP-11:** vorhandener Teamleiter und gemeinsamer Backlog bleiben die einzigen Scheduler-/Aufgabenpfade. Lease-Erholung nutzt ausschließlich die existierende Zustandsmaschine und deren Versuchsbudget.
- **EXP-02:** feste Prüfkommandos, tatsächliche JUnit-Testzahlen, unabhängiger zweiter Repair-Worktree und Owner-Review bleiben erhalten. Investigator-Findings autorisieren weiterhin keine Codeänderung oder automatischen Merge.
- **EXP-12:** persistierter Prüfentscheid ist crashkonsistent; gültige Claims und bestehende Evidenz werden nicht überschrieben oder als Erfolg umgedeutet.
- **EXP-00 / EXP-08:** keine Änderung am Android-first-Ziel, Android-/Termux-Transport, nativen Toolchainvertrag oder Installationsfreigaben. Der PC bleibt optional.

Die gemeinsame Build-Lease bleibt absichtlich nach einem harten Prozessabbruch erhalten und verlangt Prüfung statt altersbasierter Übernahme. Die neuen Crash-Tests betreffen den direkten Investigator an der Backlog-Persistenzgrenze; sie belegen keinen unbeaufsichtigten Neustart eines vollständig abgebrochenen Teamleiters trotz zurückgelassener Build-Lease. Bereits aus älteren Läufen stammende `VERIFYING`-Aufgaben werden nicht automatisch umgeschrieben; sie benötigen eine kontrollierte Prüfung ihrer Submission und Evidenz. Ein während eines harten Abbruchs zurückgelassener Git-Worktree muss gegebenenfalls nach Prüfung entfernt werden; der Regressionstest räumt seine eigenen Worktrees auf.

Git-Worktrees sind keine OS-Sandbox. Die vorhandenen Prozess-/OS-Grenzen wurden nicht gelockert; keine Modelltexte werden als Shell übernommen, keine geschützten Kernpfade für autonome Änderungen freigegeben. Die Pfadprüfung schützt gegen vorhandene Symlink-Aliase, ist jedoch keine Absicherung gegen einen gleichzeitig böswillig umbenennenden Prozess derselben OS-Identität. Physisches Android-Prozessende, Doze, thermische Grenzen, plötzlicher Stromverlust und Netzwerkdateisystem-Races wurden hier nicht simuliert.

Nächster Schritt: unterstützten Node-Gesamtlauf des Hauptaudits abschließen, anschließend dieselben vorhandenen Teamleiter-/Backlog-Wege auf dem verbundenen Samsung/Termux mit explizit beobachteter Wiederaufnahme und Abbruch prüfen. Keine neue Termux-Einrichtung erforderlich.

Rollback: die Änderungen in `.github/continuum/backlog-investigator.mjs` und `.github/continuum/autonomous-teamleader.mjs` zurücknehmen und `.github/continuum/backlog-investigator-recovery.test.mjs` entfernen. Keine Schemaänderung oder Datenmigration; bestehende Backlogs bleiben lesbar. Dabei kehren die oben reproduzierten Fehler zurück. In diesem Teil wurden keine Dienste veröffentlicht, Änderungen gemergt oder APKs installiert; die vom Besitzer angeforderte Versionssicherung wird im Hauptaudit separat dokumentiert.
