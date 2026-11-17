#!/data/data/com.termux/files/usr/bin/bash
# Opt-in only: no background daemon, no downloads and no Android permissions.
set -euo pipefail
umask 077
if [[ "${PREFIX:-}" != '/data/data/com.termux/files/usr' ]]; then
  echo 'Termux prefix required, refusing to run on a different machine.' >&2; exit 2
fi
if [[ "$(uname -m)" != 'aarch64' ]]; then
  echo 'ARM64 Termux required.' >&2; exit 2
fi
case "${1:-}" in
  start) ;;
  *) echo 'Usage: bash scripts/continuum-local-llama-termux.sh start' >&2; exit 2 ;;
esac
server="${CONTINUUM_LLAMA_SERVER_BIN:-$HOME/llama.cpp/build/bin/llama-server}"
model="${CONTINUUM_GGUF_PATH:-$HOME/.local/share/device-ai/models/local-instruct.gguf}"
keyfile="$HOME/.config/device-ai/local-llama.key"
if [[ ! -f "$server" || ! -x "$server" ]]; then
  echo 'Local llama-server binary absent; build/install from official ggml-org source first.' >&2; exit 1
fi
if [[ ! -f "$model" || ! -r "$model" || -L "$model" ]]; then
  echo 'Owner-selected local GGUF absent or unsafe. No model will be downloaded automatically.' >&2; exit 1
fi
# Verify syntax support BEFORE server startup; some Termux package versions lag.
flags="$("$server" --help 2>&1 || true)"
for flag in '--api-key-file' '--no-cache-idle-slots' '--cache-ram' '--no-cache-prompt'; do
  if [[ "$flags" != *"$flag"* ]]; then
    echo "This llama-server lacks required protection: $flag" >&2; exit 1
  fi
done
mkdir -p "$(dirname "$keyfile")"
if [[ -L "$keyfile" ]]; then echo 'Refusing a symlink API-key file.' >&2; exit 1; fi
if [[ ! -e "$keyfile" ]]; then
  key="$(od -An -N32 -tx1 /dev/urandom | tr -d ' \n')"
  ( set -C; printf '%s\n' "$key" > "$keyfile" )
fi
chmod 600 "$keyfile"
if [[ "$(wc -c < "$keyfile")" -ne 65 ]]; then
  echo 'Invalid API-key file (expected 64 hex digits and newline).' >&2; exit 1
fi
printf '%s\n' 'Starting owner-approved localhost model. Stop with Ctrl+C. No remote listener.'
exec "$server" -m "$model" --host 127.0.0.1 --port 8089 \
  --api-key-file "$keyfile" --parallel 1 -c 2048 -t 4 \
  --no-cache-idle-slots --cache-ram 0 --no-cache-prompt
