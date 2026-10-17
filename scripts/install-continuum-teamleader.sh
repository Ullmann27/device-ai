#!/usr/bin/env bash
# Opt-in user-owned, zero-cloud Teamleader timer.
# Run: bash scripts/install-continuum-teamleader.sh
set -euo pipefail
if ! command -v systemctl >/dev/null 2>&1; then
  echo "systemd is unavailable; schedule the --once CLI locally instead." >&2
  exit 1
fi
ROOT="$(cd "$(dirname "$0")/.." && pwd -P)"
NODE="$(command -v node || true)"
if [[ -z "$NODE" ]] || ! "$NODE" -e 'if(Number(process.versions.node.split(".")[0])<20)process.exit(1)'; then
  echo "Node.js 20+ is required; no downloads are attempted." >&2
  exit 1
fi
if [[ ! "$ROOT" =~ ^[a-zA-Z0-9_./-]+$ ]] || [[ ! "$NODE" =~ ^[a-zA-Z0-9_./-]+$ ]]; then
  echo "Paths with spaces or special characters need a manual systemd unit." >&2
  exit 1
fi
UNIT_DIR="$HOME/.config/systemd/user"
mkdir -p "$UNIT_DIR"
SERVICE="$UNIT_DIR/continuum-teamleader.service"
TIMER="$UNIT_DIR/continuum-teamleader.timer"
if [[ -e "$SERVICE" || -L "$SERVICE" || -e "$TIMER" || -L "$TIMER" ]]; then
  echo "Existing user service/timer is not overwritten." >&2
  exit 1
fi
umask 077
TMP_SERVICE=''
TMP_TIMER=''
cleanup(){
  [[ -z "$TMP_SERVICE" ]] || rm -f -- "$TMP_SERVICE"
  [[ -z "$TMP_TIMER" ]] || rm -f -- "$TMP_TIMER"
}
trap cleanup EXIT
TMP_SERVICE="$(mktemp "$UNIT_DIR/.continuum-teamleader-service.XXXXXX")"
TMP_TIMER="$(mktemp "$UNIT_DIR/.continuum-teamleader-timer.XXXXXX")"
cat > "$TMP_SERVICE" <<EOF
[Unit]
Description=Continuum offline autonomous software Teamleader
# A linked Git worktree uses a .git file, not a directory.
ConditionPathExists=$ROOT/.git

[Service]
Type=oneshot
WorkingDirectory=$ROOT
Environment=GIT_TERMINAL_PROMPT=0
ExecStart=$NODE $ROOT/.github/continuum/teamleader-cli.mjs --once --root $ROOT --budget-minutes 20
TimeoutStartSec=35min
NoNewPrivileges=true
EOF
cat > "$TMP_TIMER" <<EOF
[Unit]
Description=Run Continuum Teamleader hourly on this machine

[Timer]
OnCalendar=hourly
Persistent=true
RandomizedDelaySec=3min
Unit=continuum-teamleader.service

[Install]
WantedBy=timers.target
EOF
# Hard links prevent replacing a pre-existing unit or dangling symlink.
if ! ln -- "$TMP_SERVICE" "$SERVICE"; then
  echo "Service appeared during installation; refusing to overwrite" >&2
  exit 1
fi
if ! ln -- "$TMP_TIMER" "$TIMER"; then
  rm -f -- "$SERVICE"
  echo "Timer appeared during installation; rolling back service" >&2
  exit 1
fi
if ! systemctl --user daemon-reload ||
   ! systemctl --user enable --now continuum-teamleader.timer; then
  systemctl --user disable --now continuum-teamleader.timer >/dev/null 2>&1 || true
  rm -f -- "$SERVICE" "$TIMER"
  systemctl --user daemon-reload >/dev/null 2>&1 || true
  echo "Timer activation failed; newly created units rolled back" >&2
  exit 1
fi
echo "Installed user timer: $TIMER"
echo "Status: systemctl --user list-timers continuum-teamleader.timer"
echo "Review: journalctl --user -u continuum-teamleader.service"
echo "Disable: systemctl --user disable --now continuum-teamleader.timer"
echo "Note: automatic runs require an active local user session or enabled lingering."
