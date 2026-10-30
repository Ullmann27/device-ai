#!/usr/bin/env bash
# Continuum neuro-research daemon installer (Ubuntu/Debian with systemd --user).
# Installs a user service; never takes secrets from this script or the repository.
set -euo pipefail
umask 077

REPO="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd -P)"
SERVICE_NAME="continuum-neuro-agent"
CONF_DIR="$HOME/.config/continuum-neuro-agent"
ENV_FILE="$CONF_DIR/agent.env"
UNIT_DIR="$HOME/.config/systemd/user"
UNIT="$UNIT_DIR/$SERVICE_NAME.service"

if [[ "${1:-}" == "--check" ]]; then
  command -v node >/dev/null || { echo "NODE_22_REQUIRED"; exit 2; }
  node -e 'process.exit(Number(process.versions.node.split(".")[0])>=22?0:2)' || { echo "NODE_22_REQUIRED";exit 2; }
  node "$REPO/scripts/continuum-neuro-agent.mjs" --check
  exit 0
fi
if [[ "${1:-}" != "--install" || $# -ne 1 ]]; then
  echo "Usage: bash scripts/setup-continuum-neuro-agent.sh --check|--install" >&2;exit 2
fi
command -v systemctl >/dev/null || { echo "SYSTEMD_USER_HOST_REQUIRED";exit 2; }
systemctl --user show-environment >/dev/null 2>&1 || { echo "SYSTEMD_USER_SESSION_REQUIRED";exit 2; }
command -v node >/dev/null || { echo "NODE_22_REQUIRED";exit 2; }
node -e 'process.exit(Number(process.versions.node.split(".")[0])>=22?0:2)' || { echo "NODE_22_REQUIRED";exit 2; }
if [[ "$(git -C "$REPO" symbolic-ref --quiet --short HEAD)" != "research/continuum-cloud-gpu-admission-20261030" ]]; then
  echo "REVIEWED_RESEARCH_BRANCH_REQUIRED";exit 2
fi
node --test "$REPO/scripts/continuum-neuro-agent.test.mjs" "$REPO/scripts/continuum-nvidia-ultra-review.test.mjs"
mkdir -p -m 700 "$CONF_DIR" "$UNIT_DIR"
chmod 700 "$CONF_DIR"
if [[ ! -f "$ENV_FILE" ]]; then
  echo "NVIDIA: revoke both API keys previously pasted in chat."
  echo "Enter only a newly rotated NVIDIA developer API key; it will not be echoed."
  read -rs -p "New NVIDIA API key: " NVIDIA_NEW_KEY
  echo
  if [[ ! "$NVIDIA_NEW_KEY" =~ ^nvapi-[A-Za-z0-9_-]{12,256}$ ]]; then
    unset NVIDIA_NEW_KEY;echo "INVALID_ROTATED_API_KEY";exit 2
  fi
  {
    printf 'NVIDIA_API_KEY=%s\n' "$NVIDIA_NEW_KEY"
    printf 'CONTINUUM_ULTRA_CONSENT=ONE_REMOTE_TECHNICAL_REVIEW\n'
    printf 'CONTINUUM_NEURO_DAILY=3\n'
    printf 'CONTINUUM_NEURO_TOTAL=24\n'
    printf 'CONTINUUM_NEURO_MIN_INTERVAL_MS=900000\n'
    printf 'CONTINUUM_NEURO_COMMIT_EVIDENCE=true\n'
  } > "$ENV_FILE"
  chmod 600 "$ENV_FILE"
  unset NVIDIA_NEW_KEY
else
  echo "EXISTING_SECRET_FILE_PRESERVED: $ENV_FILE"
fi
NODE="$(command -v node)"
if [[ "$NODE" == *$'\n'* || "$REPO" == *$'\n'* || "$NODE" == *" "* || "$REPO" == *" "* || "$NODE" == *"%"* || "$REPO" == *"%"* ]]; then
  echo "UNSUPPORTED_SERVICE_PATH";exit 2
fi
cat > "$UNIT" <<UNIT
[Unit]
Description=Continuum JARVIS owner-governed NVIDIA research worker
After=network-online.target

[Service]
Type=simple
WorkingDirectory=$REPO
EnvironmentFile=$ENV_FILE
ExecStart=$NODE $REPO/scripts/continuum-neuro-agent.mjs --loop
Restart=on-failure
RestartSec=300
UMask=0077
NoNewPrivileges=yes
PrivateTmp=yes
StandardOutput=journal
StandardError=journal

[Install]
WantedBy=default.target
UNIT
chmod 600 "$UNIT"
systemctl --user daemon-reload
systemctl --user enable --now "$SERVICE_NAME.service"
echo "USER_SERVICE_ENABLED: $SERVICE_NAME"
echo "Read status: systemctl --user status $SERVICE_NAME"
echo "Stop/revoke: systemctl --user stop $SERVICE_NAME"
echo "Review local private reports: $HOME/.local/state/continuum-neuro-agent"
echo "No GitHub push, executable model patches, app update or uncapped GPU usage enabled."
