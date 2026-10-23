# CCC1 auf dem Fold7 zum Laufen bringen (Termux + App)

Die App (ab 0.3.17) schickt CCC1-Aufträge über Termux an `~/device-ai/scripts/continuum-mobile-builder.mjs`.
CCC1 läuft also **im Termux-Checkout `~/device-ai`** – die App allein reicht nicht. Ist der Checkout älter als die
App, kennt er die CCC1-Befehle nicht, und die App meldet nur „Keine verlässliche CCC1-Rückmeldung“.

Nichts in dieser Anleitung löscht, setzt zurück oder überschreibt etwas. `main` bleibt unverändert.

## 1. Einmalig: Termux-Checkout auf den CCC1-Stand bringen

In Termux:

```bash
cd ~/device-ai
git status --short                      # muss leer sein – sonst eigene Änderungen zuerst committen oder sichern
git fetch origin opus/ccc1-e2e-20261023
git switch -c ccc1-arbeit FETCH_HEAD    # eigener Arbeitsbranch; der bisherige Branch bleibt, wie er ist
bash scripts/continuum-teamleader-termux.sh ccc1-check
```

Gibt es `ccc1-arbeit` schon: `git switch ccc1-arbeit && git merge --ff-only FETCH_HEAD`. Der Branch enthält
denselben CCC1-Stand wie die APK 0.3.17 (Quelle `285639ba`; Brückenformat `continuum-ccc1-bridge` Version 1,
per Vertragstest gesichert) plus die Termux-Befehle unten. Voraussetzung: Node.js 20 oder neuer (`node --version`).

## 2. Bereitschaft prüfen

```bash
bash scripts/continuum-teamleader-termux.sh ccc1-check          # Vorprüfung wie in der App (Sekunden)
bash scripts/continuum-teamleader-termux.sh ccc1-check --full   # zusätzlich die ganze Testkette einmal (Minuten)
```

`BEREIT` heißt: eigener Arbeitsbranch, sauberer Checkout, Zustand außerhalb, Node passt. `--full` zeigt, ob die
Testkette **auf diesem Gerät** ohne Regression grün ist – nur dann sind Reparaturen aussagekräftig. Steht der
Checkout auf `main`, `master`, `release` oder auf keinem Branch: `bash scripts/continuum-teamleader-termux.sh ccc1-prepare`
legt einen neuen Arbeitsbranch auf demselben Commit an (Dateien bleiben gleich; bei ungesicherten Änderungen bricht
es ab, ohne etwas zu verändern).

## 3. Benutzen

**In der App:** Android Builder → „CCC1 · Reparaturauftrag“ → Testkette wählen → *CCC1-Vorprüfung* →
*Reparaturauftrag einreichen* → *einen Schritt ausführen* (kann mehrere Minuten dauern) → *CCC1-Status abrufen*.
Erst der Status zeigt einen geprüften Patch (Hash, Dateien, „NICHT übernommen“). *Abbrechen* stoppt einen laufenden
Auftrag; ein Abbruch zählt nie als Erfolg.

**In Termux** (gleiche Brücke, gleicher Zustand):

```bash
bash scripts/continuum-teamleader-termux.sh ccc1-once     # einen CCC1-Schritt ausführen
bash scripts/continuum-teamleader-termux.sh ccc1-status   # alle Aufträge
bash scripts/continuum-teamleader-termux.sh ccc1-review   # neuester geprüfter Patch: Pfad, Hash, Vorschau
```

CCC1 repariert **fehlschlagende Tests** der gewählten Kette am aktuellen Commit. Ist die Kette grün, lautet das
ehrliche Ergebnis „nichts zu reparieren“.

## 4. Patch prüfen und übernehmen – nur durch dich

CCC1 übernimmt, merged und installiert nie etwas. Übernehmen, wenn du den Patch gelesen hast:

```bash
git -C ~/device-ai apply --check <Pfad aus ccc1-review>
git -C ~/device-ai apply <Pfad aus ccc1-review>
```

## 5. Was die App meldet und was zu tun ist

| Meldung | Ursache | Lösung |
|---|---|---|
| „Termux-Checkout … älter als die App“ / „Keine verlässliche CCC1-Rückmeldung“ (App 0.3.17) | Checkout ohne CCC1-Befehle | Abschnitt 1 |
| `CCC1_BRANCH_NOT_ISOLATED` | Checkout auf `main`/`release`/ohne Branch | `ccc1-prepare` |
| `CCC1_WORKTREE_NOT_CLEAN` | ungesicherte Änderungen oder neue Dateien | `git -C ~/device-ai status --short`, sichern |
| `CCC1_REQUIRED_FILE_MISSING` | Checkout zu alt | Abschnitt 1 |
| `CCC1_NODE_VERSION_UNSUPPORTED` | Node.js < 20 | `pkg upgrade nodejs` |
| „Termux konnte den Befehl nicht starten“ | Node fehlt oder `allow-external-apps` nicht gesetzt | `pkg install nodejs`; in `~/.termux/termux.properties` `allow-external-apps=true` |
| Schritt endet mit „nichts bestätigt“ | Lauf unterbrochen, abgebrochen oder Kette auf dem Gerät schon rot | `ccc1-check --full`, dann `ccc1-status` |

## 6. Gefahrlos ausprobieren (ohne `~/device-ai` zu berühren)

`docs/evidence/ccc1-e2e-20261023/README.md`, Abschnitt „Nachvollziehen“: eigener Klon `~/ccc1-proof`, eigener
Zustandsordner, absichtlich eingebaute Regression, voller CCC1-Lauf bis zum geprüften Patch.

## Stand der Prüfung

Auf Linux (Node 22) mit genau diesen Befehlen geprüft (`teamleader-termux.test.mjs`, `ccc1-bridge.test.mjs`).
**Auf dem Fold7 noch nicht ausgeführt** – das erste echte Geräteergebnis von `ccc1-check --full` bitte festhalten.
