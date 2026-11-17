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
ACTIVATION_PID=''
INSTALLED=false
# Determine ownership by inode even if interrupted between hard-link creation
# and the next shell instruction. Never remove a different owner's unit.
our_unit(){
  local temporary="$1" destination="$2"
  [[ -n "$temporary" && -f "$temporary" && -f "$destination" &&
     ! -L "$destination" && "$temporary" -ef "$destination" ]]
}
cleanup(){
  local status=$?
  trap - EXIT INT TERM
  if [[ -n "$ACTIVATION_PID" ]]; then
    kill "$ACTIVATION_PID" 2>/dev/null || true
    wait "$ACTIVATION_PID" 2>/dev/null || true
  fi
  if [[ "$INSTALLED" != true ]]; then
    if our_unit "$TMP_TIMER" "$TIMER"; then
      systemctl --user disable --now continuum-teamleader.timer >/dev/null 2>&1 || true
      rm -f -- "$TIMER"
    fi
    if our_unit "$TMP_SERVICE" "$SERVICE"; then
      rm -f -- "$SERVICE"
    fi
    systemctl --user daemon-reload >/dev/null 2>&1 || true
  fi
  [[ -z "$TMP_SERVICE" ]] || rm -f -- "$TMP_SERVICE"
  [[ -z "$TMP_TIMER" ]] || rm -f -- "$TMP_TIMER"
  return "$status"
}
trap cleanup EXIT
trap 'exit 130' INT
trap 'exit 143' TERM
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
  echo "Timer appeared during installation; rolling back service" >&2
  exit 1
fi
if ! systemctl --user daemon-reload; then
  echo "Timer installation failed during daemon reload" >&2
  exit 1
fi
# An asynchronous activation lets Bash handle SIGTERM while waiting.
# The EXIT trap terminates activation and rolls back only our own units.
systemctl --user enable --now continuum-teamleader.timer &
ACTIVATION_PID=$!
if ! wait "$ACTIVATION_PID"; then
  ACTIVATION_PID=''
  echo "Timer activation failed; newly created units rolled back" >&2
  exit 1
fi
ACTIVATION_PID=''
INSTALLED=true
echo "Installed user timer: $TIMER"
echo "Status: systemctl --user list-timers continuum-teamleader.timer"
echo "Review: journalctl --user -u continuum-teamleader.service"
echo "Disable: systemctl --user disable --now continuum-teamleader.timer"
echo "Note: automatic runs require an active local user session or enabled lingering."
