# CCC1 End-to-End-Beleg – echter Reparaturlauf über die Termux-Brücke (23.10.2026)

**Was bewiesen ist:** Ein Besitzerauftrag läuft über genau die Aufrufe, die die Android-App an Termux sendet
(`node ~/device-ai/scripts/continuum-mobile-builder.mjs ccc1-* --project ~/device-ai …`, Arbeitsverzeichnis =
Projekt, Zustand im Standardordner `~/.local/state/device-ai-continuum/ccc1`), durch CCC1 (Vorprüfung, Journal,
Gate, bestehende Reparatur-Pipeline, unabhängiger zweiter Arbeitsstand, CCC1-Verifier) und endet als
**geprüfter Patch zur Besitzerprüfung** – nichts übernommen, gemergt oder installiert.

**Was NICHT bewiesen ist:** Lauf auf dem Fold7/Termux selbst (hier: Linux-Container, Node 22, gleiche Skripte),
Android-Rückkanal auf einem Gerät, APK-Build. Die Android-Auswertung wurde mit dem echten Kotlin-Code
(`ContinuumCcc1Bridge.evaluate`) außerhalb von Gradle gegen genau diese Antworten geprüft (`proof/android-evaluation.txt`).

## Aufbau

- Quelle: Branch `opus/ccc1-e2e-20261023` @ `687b3a56c4c4b15802033d72d3704c3aab51a595`, frisch geklont,
  `origin` = `https://github.com/Ullmann27/device-ai.git` (Bindung), lokaler Branch `ccc1-proof-20261023`.
- Eingeschleuste Regression (nur lokal, nie gepusht), Commit `98f1408248e4…`: `isOwnerComment` in
  `.github/continuum/console-policy.mjs` prüft `startsWith` statt Gleichheit – ein Konto `Ullmann27-similar`
  hätte Besitzerrechte für Lern- und Speicheränderungen. Bestätigt rot: `proof/regression-confirmed.txt`.
- Das Modul liegt außerhalb der Importkette der CCC1-Laufzeit (CCC1 lief also nicht mit dem Fehler selbst).

## Ergebnis (proof/)

| Schritt | Aufruf | Ergebnis |
|---|---|---|
| 01 | `ccc1-compile-repair --chain node` | READY, Branch `ccc1-proof-20261023`, keine Befunde |
| 02 | `ccc1-submit-repair --chain node --order <uuid>` | Auftrag `t-a1bf9c70fe199bb7`, `duplicate:false` |
| 03 | gleicher Aufruf, gleiche Auftrags-ID (verlorener Rückkanal) | derselbe Auftrag, `duplicate:true` |
| 05 | `ccc1-run-once` (70 s) | `VERIFIED_PATCH_PENDING_REVIEW` / CCC1 `COMPLETED` |
| 06 | `ccc1-status` | `COMPLETED`, `repair:node@d4caa68debf9`, Patch unversehrt, Strategie `REVERT_FILE_TO_PARENT` |
| 07 | erledigten Auftrag erneut senden | derselbe Auftrag, `duplicate:true`, nichts ausgeführt |
| 08 | `ccc1-run-once` erneut | `IDLE` |
| 09 | `ccc1-abort` auf erledigtem Auftrag | `ALREADY_ENDED` (`COMPLETED` bleibt) |

Quittung (`proof/coding-job-receipt.json`): Baseline **792 Tests ausgeführt, 1 rot** (Exit 1) → Kandidat
**792 / 0 rot** → unabhängiger zweiter Arbeitsstand **792 / 0 rot**; `evidenceSufficient: true`;
`approvedForMerge:false`, `installed:false`, `deviceVerified:false`.
Patch `proof/repair.patch`, SHA-256 `d4caa68debf9ba72f048301c1a3e093e97809f51336936f307a63ab8d3086b33`
(stellt die exakte Besitzerprüfung wieder her). Checkout danach unverändert (`proof/git-after.txt`).

**Eigene Gegenprobe:** Patch auf frischem Klon mit `git apply --check` + `git apply`, danach volle Suite
`.github/continuum/*.test.mjs`: **880 Tests, 874 bestanden, 0 fehlgeschlagen, 6 übersprungen**
(`proof/independent-full-suite.summary`) – identisch zur sauberen Basis.

## Abbruch während des Laufs (cancel/)

Zweiter Klon, gleiche Regression, Auftrag `t-744099441e5b87bc`: `ccc1-run-once` gestartet; sobald der Worker den
Job übernommen hatte, `ccc1-abort`. Antwort `WRITER_ACTIVE`, Job `CANCELLED`. Der laufende Schritt endete als
`DONE_INVESTIGATION` / CCC1 `ABSTAINED` („Die Quittung entscheidet nichts (job ended CANCELLED)“), kein Patch, kein
`confirmed`; zweiter Abbruch `ALREADY_ENDED`; danach `IDLE`. Der Lauf rechnete hier noch zu Ende (70 s) –
seit `f6a23243` stoppt die Reparatur nach einem Abbruch vor dem unabhängigen Lauf (`TASK_ABORTED_BY_OWNER`).

**Wiederholung mit Schrittgrenzen-Abbruch (`cancel-step-boundary/`, Code `c397ff1b`):** gleicher Ablauf und gleiche
Regression auf frischem Klon, Abbruch 5 s nach Übernahme des Jobs. `ccc1-run-once` endet nach **38,7 s statt 70,6 s**
mit demselben Ergebnis (`DONE_INVESTIGATION` / `ABSTAINED`, kein Patch, zweiter Abbruch `ALREADY_ENDED`, danach
`IDLE`). Im Berichtsordner liegt nur die JUnit-Datei der Baseline (886 Testfälle, 1 rot): kein Kandidatenlauf, kein
Bericht, kein unabhängiger Lauf; Checkout sauber, temporäre Arbeitsbäume entfernt. Der gerade laufende Prüfschritt
(hier die Baseline) läuft immer zu Ende – sein Prozess wird nicht beendet.

## Ablehnungen am echten Repository (refusals/)

`main`-Branch → `BLOCKED_PREFLIGHT` (`CCC1_BRANCH_NOT_ISOLATED`), ungesicherte Datei → `CCC1_WORKTREE_NOT_CLEAN`,
Zustandsordner im Checkout → `BLOCKED_STATE_PATH`; jeweils Exit 2, kein Auftrag angelegt.

## Integrität

`proof/task-journal.jsonl` und `proof/experience.jsonl` sind byte-identisch zum Lauf (HMAC-Kette mit dem
Laufzeitschlüssel, der **nicht** veröffentlicht ist). In `coding-job-receipt.json`, `task-result.json` und
`refusals/calls.jsonl` sind lokale Pfade durch `<TERMUX_HOME>` ersetzt; ihr Byte-Hash weicht daher vom
journalisierten `receiptSha256` ab.

## Nachvollziehen (Termux oder Linux, Node ≥ 20)

In einem eigenen Ordner und mit eigenem Zustandsordner, damit weder der Besitzer-Checkout `~/device-ai` noch der
echte CCC1-Zustand berührt wird:

```bash
git clone --branch opus/ccc1-e2e-20261023 https://github.com/Ullmann27/device-ai.git ~/ccc1-proof && cd ~/ccc1-proof
git checkout -b ccc1-proof && sed -i 's/name.toLowerCase()===parts\[0\].toLowerCase()/name.toLowerCase().startsWith(parts[0].toLowerCase())/' .github/continuum/console-policy.mjs
git -c user.name=proof -c user.email=proof@example.invalid commit -qam "injected regression (local only)"
B=~/ccc1-proof/scripts/continuum-mobile-builder.mjs; P=~/ccc1-proof; Z=~/ccc1-proof-state
node $B ccc1-compile-repair --chain node --project $P --state-dir $Z
node $B ccc1-submit-repair --chain node --order "$(cat /proc/sys/kernel/random/uuid)" --project $P --state-dir $Z
node $B ccc1-run-once --project $P --state-dir $Z
node $B ccc1-status --project $P --state-dir $Z
node .github/continuum/ccc1-cli.mjs status --state $Z --format json   # mit Patch-Vorschau
```
