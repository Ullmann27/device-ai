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
    *)
        printf '%s\n' 'Usage: bash scripts/continuum-teamleader-termux.sh [once|status|loop]' >&2
        exit 2
        ;;
esac
