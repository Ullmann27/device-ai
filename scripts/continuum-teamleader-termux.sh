#!/usr/bin/env bash
# Android / Termux entrypoint for the SAME Continuum Node Teamleader.
# No cloud model, GitHub Actions, root permissions or Termux:API required.
set -euo pipefail

ROOT="$(cd "$(dirname "$0")/.." && pwd -P)"
CLI="$ROOT/.github/continuum/teamleader-cli.mjs"
if ! command -v node >/dev/null 2>&1 || ! command -v git >/dev/null 2>&1; then
    printf '%s\n' 'Continuum needs the existing Termux packages nodejs and git.' >&2
    printf '%s\n' 'When connected to the internet, Termux can install them with: pkg install nodejs git' >&2
    exit 1
fi
if ! node -e 'if (Number(process.versions.node.split(".")[0]) < 20) process.exit(1)' ; then
    printf '%s\n' 'Node.js version 20 or later is required.' >&2
    exit 1
fi
if ! git -C "$ROOT" rev-parse --is-inside-work-tree >/dev/null 2>&1; then
    printf '%s\n' 'Copy/clone the device-ai repository into Termux first.' >&2
    printf '%s\n' 'The protected Android app data is not accessible through Termux.' >&2
    exit 1
fi
if [[ ! -f "$CLI" ]]; then
    printf '%s\n' 'Teamleader CLI not found in this checkout.' >&2
    exit 1
fi

# CCC1 through the SAME builder bridge the Android app calls (scripts/continuum-mobile-builder.mjs ccc1-*), with the
# same default state outside the checkout. Nothing here fetches, pushes, resets, deletes or applies a patch.
BUILDER="$ROOT/scripts/continuum-mobile-builder.mjs"
CCC1_CLI="$ROOT/.github/continuum/ccc1-cli.mjs"
CCC1_STATE="$HOME/.local/state/device-ai-continuum/ccc1"

ccc1_check() {
    printf 'Node.js %s · %s\n' "$(node --version)" "$(git --version)"
    printf 'Checkout %s: Branch "%s" @ %s\n' "$ROOT" "$(git -C "$ROOT" branch --show-current)" "$(git -C "$ROOT" rev-parse --short=12 HEAD)"
    if [[ ! -f "$BUILDER" || ! -f "$CCC1_CLI" ]] || ! grep -q 'ccc1-submit-repair' "$BUILDER"; then
        printf '%s\n' 'NICHT BEREIT: Dieser Checkout kennt die CCC1-Befehle der App noch nicht (zu alt).' >&2
        printf '%s\n' 'Anleitung: docs/CONTINUUM-CCC1-AUF-DEM-GERAET.md, Abschnitt 1.' >&2
        return 1
    fi
    local answer
    answer="$(node "$BUILDER" ccc1-compile-repair --chain node --project "$ROOT" 2>&1)" || true
    node --input-type=module -e '
        const [answer, root] = process.argv.slice(1);
        const {pathToFileURL} = await import("node:url");
        const {explainCcc1Findings} = await import(pathToFileURL(root + "/.github/continuum/ccc1-local-preflight.mjs").href);
        let r;
        try { r = JSON.parse(answer); } catch {
            console.log("NICHT BEREIT: keine auswertbare Antwort der CCC1-Brücke:\n" + answer.slice(0, 800)); process.exit(1);
        }
        const pf = r.result?.preflight;
        if (r.status === "OK" && r.result.status === "READY") {
            console.log("BEREIT: CCC1-Vorprüfung bestanden (Branch " + pf.checkedBranch + ", Commit " + pf.sourceCommit.slice(0, 12) + ").");
            process.exit(0);
        }
        const findings = pf?.findings ?? (r.failure?.message ?? "").match(/CCC1_[A-Z_]+(?::\S+)?/g) ?? [];
        console.log("NICHT BEREIT: " + (findings.join(", ") || r.status));
        for (const hint of explainCcc1Findings(findings)) console.log("  - " + hint);
        process.exit(1);
    ' "$answer" "$ROOT"
}

ccc1_full_chain() {
    # The node chain exactly as a repair worktree runs it (nested real repairs skipped), on this device and checkout.
    local log status
    log="$(mktemp "${TMPDIR:-/tmp}/ccc1-check.XXXXXX")"
    printf '%s\n' 'Volle Testkette node wie im Reparatur-Arbeitsstand; das dauert einige Minuten ...'
    status=0
    (cd "$ROOT" && env -u NODE_TEST_CONTEXT CONTINUUM_REPAIR_DEPTH=1 node --test .github/continuum/*.test.mjs) >"$log" 2>&1 || status=$?
    grep -E '^# (tests|pass|fail|skipped|cancelled) ' "$log" || true
    if [[ "$status" -ne 0 ]]; then
        printf '%s\n' 'NICHT BEREIT für verlässliche Reparaturen: Diese Tests scheitern auf dem Gerät schon ohne Regression:'
        grep -E '^not ok ' "$log" | head -n 20 || true
        printf 'Vollständiges Protokoll: %s\n' "$log"
        return 1
    fi
    printf '%s\n' 'BEREIT: Die Testkette läuft auf diesem Gerät grün.'
    rm -f -- "$log"
}

case "${1:-once}" in
    once)
        exec node "$CLI" --once --root "$ROOT" --budget-minutes 10
        ;;
    status)
        exec node "$CLI" --status --root "$ROOT"
        ;;
    loop)
        # Optional foreground loop while this Termux session and Android allow.
        # No self-persistence, wake-lock or hidden always-on process.
        printf '%s\n' 'Continuum runs at most one 10-minute local cycle per hour.'
        printf '%s\n' 'Android may suspend or terminate Termux; this is not a 24/7 guarantee.'
        while :; do
            if command -v termux-battery-status >/dev/null 2>&1; then
                observation="$(termux-battery-status 2>/dev/null || true)"
                if ! node -e '
                    try {
                        const status = JSON.parse(process.argv[1]);
                        if (!Number.isFinite(status.percentage) || status.percentage <= 25 ||
                            !Number.isFinite(status.temperature) || status.temperature >= 42)
                            process.exit(1);
                    } catch { process.exit(1); }
                ' "$observation"; then
                    printf '%s\n' 'Battery or temperature is unsuitable; skipping this cycle.'
                    sleep 3600
                    continue
                fi
            else
                printf '%s\n' 'Battery monitor unavailable; unattended loop is disabled.'
                printf '%s\n' 'Use "once", or set up the optional Termux:API battery-status tool.' >&2
                exit 1
            fi
            if node "$CLI" --once --root "$ROOT" --budget-minutes 10; then
                printf '%s\n' 'Cycle completed; waiting for next local interval.'
            else
                printf '%s\n' 'Cycle failed; status preserved for inspection.' >&2
            fi
            sleep 3600
        done
        ;;
    ccc1-check)
        # Read-only: preflight through the app's bridge; with --full also the whole node chain once.
        ready=0
        ccc1_check || ready=1
        if [[ "${2:-}" == --full ]]; then ccc1_full_chain || ready=1; fi
        exit "$ready"
        ;;
    ccc1-prepare)
        # A clean checkout on main/master/production/prod/release or a detached HEAD gets a NEW local work branch at
        # the same commit (files unchanged). An existing work branch, other branches and dirty trees stay untouched.
        if [[ -n "$(git -C "$ROOT" status --porcelain --untracked-files=normal)" ]]; then
            printf '%s\n' 'Abbruch, nichts verändert: Der Checkout hat ungesicherte Änderungen oder neue Dateien:' >&2
            git -C "$ROOT" status --short | head -n 20 >&2
            exit 1
        fi
        current="$(git -C "$ROOT" branch --show-current)"
        if [[ -z "$current" || "${current,,}" =~ ^(main|master|production|prod|release)($|[/-]) ]]; then
            work="ccc1-arbeit-$(date +%Y%m%d-%H%M%S)"
            git -C "$ROOT" switch -q -c "$work"
            printf 'Neuer Arbeitsbranch %s auf demselben Commit (Dateien unverändert).\n' "$work"
        else
            printf 'Branch "%s" ist schon ein eigener Arbeitsbranch; nichts verändert.\n' "$current"
        fi
        ccc1_check
        ;;
    ccc1-once)
        # One CCC1 step, exactly as the app's "einen Schritt ausführen" (journal, gate, verifier unchanged).
        exec node "$BUILDER" ccc1-run-once --project "$ROOT"
        ;;
    ccc1-status)
        exec node "$CCC1_CLI" status --state "$CCC1_STATE"
        ;;
    mainapp-qa)
        # Reuses the ONE existing local Android QA builder. Exact HEAD approval
        # and a clean worktree are enforced there; no owner P12, no install.
        if [[ $# -gt 2 || ( $# -eq 2 && "$2" != --offline ) ]]; then
            echo 'Usage: bash scripts/continuum-teamleader-termux.sh mainapp-qa [--offline]' >&2
            exit 2
        fi
        if [[ ! -f "$ROOT/scripts/continuum-local-mainapp-qa.sh" ]]; then
            echo 'Aktueller Haupt-App-QA-Builder fehlt; Checkout aktualisieren.' >&2
            exit 1
        fi
        printf '%s\\n' 'Haupt-App-Qualitätsbuild: separates Testzertifikat, NIEMALS über die installierte Continuum-App installieren.'
        if [[ $# -eq 2 ]]; then
            exec bash "$ROOT/scripts/continuum-local-mainapp-qa.sh" --offline
        fi
        exec bash "$ROOT/scripts/continuum-local-mainapp-qa.sh"
        ;;
    ccc1-review)
        # Shows the newest verified repair patch for the owner's review; applying it stays a separate owner decision.
        node "$CCC1_CLI" status --state "$CCC1_STATE" --format json | node -e '
            let s = ""; process.stdin.on("data", d => s += d).on("end", () => {
                const t = JSON.parse(s).tasks.filter(x => x.review).at(-1);
                if (!t) { console.log("Kein geprüfter Reparatur-Patch vorhanden."); return; }
                console.log("Auftrag " + t.taskId + " (" + t.state + "), Patch " + t.review.patchSha256 +
                    ", unversehrt: " + (t.review.intact ? "ja" : "NEIN - nicht verwenden"));
                console.log("Datei: " + t.review.path);
                console.log(t.review.preview ?? "(keine Vorschau)");
            });'
        ;;
    *)
        printf '%s\n' 'Usage: bash scripts/continuum-teamleader-termux.sh [once|status|loop|ccc1-check [--full]|ccc1-prepare|ccc1-once|ccc1-status|ccc1-review|mainapp-qa [--offline]]' >&2
        exit 2
        ;;
esac
