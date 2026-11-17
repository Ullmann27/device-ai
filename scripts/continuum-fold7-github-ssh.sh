#!/usr/bin/env bash
# One-time personal SSH setup for the Fold7's private Continuum Git repository.
# Generates a protected PRIVATE key only in ~/.ssh. Uploads NOTHING itself.
# Never changes Git remote until GitHub authentication and repo read have passed.
set -Eeuo pipefail
umask 077
blocked() { printf 'CONTINUUM_SSH_BLOCKED=%s\n' "$*" >&2; exit 69; }
[[ $# -eq 1 && ( "$1" == prepare || "$1" == activate || "$1" == status ) ]] || {
  printf 'Aufruf: bash scripts/continuum-fold7-github-ssh.sh prepare|activate|status\n'
  exit 64
}
[[ "${PREFIX:-}" == /data/data/com.termux/files/usr* ]] ||
  blocked 'Nur im vorhandenen Fold7-Termux ausfuehren'
root="$(cd -- "$(dirname -- "${BASH_SOURCE[0]}")/.." && pwd -P)"
cd "$root"
[[ "$(git remote get-url origin 2>/dev/null)" == https://github.com/Ullmann27/device-ai.git ||
   "$(git remote get-url origin 2>/dev/null)" == https://github.com/Ullmann27/device-ai ||
   "$(git remote get-url origin 2>/dev/null)" == git@github.com:Ullmann27/device-ai.git ]] ||
  blocked 'Unbekanntes oder geaendertes GitHub-Repository'
sshdir="$HOME/.ssh"
private="$sshdir/continuum-fold7-github"
public="$private.pub"
known="$sshdir/known_hosts"
mkdir -p -- "$sshdir"
[[ ! -L "$sshdir" ]] || blocked 'SSH-Verzeichnis darf kein symbolischer Link sein'
chmod 700 "$sshdir"
[[ ! -L "$private" && ! -L "$public" && ! -L "$known" ]] ||
  blocked 'SSH-Schluessel oder known_hosts duerfen keine symbolischen Links sein'

case "$1" in
  status)
    printf 'GIT_ORIGIN=%s\n' "$(git remote get-url origin)"
    if [[ -s "$private" && -s "$public" ]]; then
      printf 'SSH_PUBLIC_KEY_FINGERPRINT='
      ssh-keygen -lf "$public" -E sha256 || blocked 'Public Key unlesbar'
    else
      printf 'SSH_KEY_NOT_CONFIGURED=TRUE\n'
    fi
    exit 0
    ;;
  prepare)
    [[ -t 0 ]] || blocked 'Interaktives Termux fuer SSH-Schluessel erforderlich'
    command -v ssh-keygen >/dev/null 2>&1 ||
      blocked 'OpenSSH fehlt; Termux: pkg install openssh'
    if [[ -e "$private" || -e "$public" ]]; then
      [[ -s "$private" && -s "$public" ]] ||
        blocked 'Nur ein Teil des bestehenden SSH-Schluesselpaares vorhanden. Nicht ueberschreiben'
      printf 'Vorhandenes SSH-Schluesselpaar wird weiterverwendet.\n'
    else
      printf 'Ein persoenliches Fold7-Schluesselpaar wird erzeugt.\n'
      printf 'Bitte eine eigene SSH-Passphrase setzen und lokal sicher aufbewahren.\n'
      ssh-keygen -q -t ed25519 -a 64 -f "$private" -C 'continuum-fold7-device-ai' ||
        blocked 'SSH-Schluesselerzeugung gescheitert'
      chmod 600 "$private"
      chmod 644 "$public"
    fi
    printf '\nNUR DIESEN OEFFENTLICHEN KEY bei GitHub eintragen (niemals private Datei):\n'
    cat -- "$public"
    printf '\nGitHub: https://github.com/settings/ssh/new\n'
    printf 'Typ: Authentication Key. Key einfuegen und speichern.\n'
    printf 'Danach: bash scripts/continuum-fold7-github-ssh.sh activate\n'
    exit 0
    ;;
esac

[[ -t 0 ]] || blocked 'SSH-Aktivierung nur interaktiv'
for bin in ssh ssh-keygen ssh-keyscan git; do
  command -v "$bin" >/dev/null 2>&1 ||
    blocked "Programm fehlt: $bin (Termux: pkg install openssh)"
done
[[ -s "$private" && -s "$public" ]] ||
  blocked 'Zuerst prepare und den Public Key bei GitHub hinterlegen'
# Host-key verification against GitHub's officially published Ed25519 SHA256,
# never trust unauthenticated ssh-keyscan output on its own.
pinned='SHA256:+DiY3wvvV6TuJJhbpZisF/zLDA0zPMSvHdkr4UvCOqU'
tmp="$(mktemp "$sshdir/continuum-github-host-XXXXXXXX")" ||
  blocked 'Temporar-Datei nicht erstellbar'
trap 'rm -f -- "$tmp"' EXIT
ssh-keyscan -T 10 -t ed25519 github.com 2>/dev/null |
  awk '$1=="github.com" && $2=="ssh-ed25519" {print; exit}' > "$tmp" ||
  blocked 'GitHub SSH-Host nicht erreichbar'
[[ -s "$tmp" ]] || blocked 'Kein GitHub Ed25519 Hostschluessel gefunden'
actual="$(ssh-keygen -lf "$tmp" -E sha256 | awk '{print $2}')"
[[ "$actual" == "$pinned" ]] ||
  blocked 'GitHub-Hostschluessel stimmt NICHT mit dem offiziellen Fingerprint ueberein'
if [[ -e "$known" ]] && ssh-keygen -F github.com -f "$known" >/dev/null 2>&1; then
  grep -Fqx -- "$(cat "$tmp")" "$known" ||
    blocked 'Bestehender github.com known_hosts Eintrag weicht ab; manuell pruefen'
else
  cat -- "$tmp" >> "$known"
  chmod 600 "$known"
fi

# GitHub's ssh -T success greeting returns exit code 1 by design.
sshcmd="ssh -i $private -o IdentitiesOnly=yes -o StrictHostKeyChecking=yes -o UserKnownHostsFile=$known"
hello="$(ssh -i "$private" -o IdentitiesOnly=yes -o StrictHostKeyChecking=yes \
  -o UserKnownHostsFile="$known" -T git@github.com 2>&1)" || true
[[ "$hello" == *'Hi Ullmann27!'* && "$hello" == *'successfully authenticated'* ]] ||
  blocked 'GitHub hat den Schluessel noch nicht bestaetigt; Public Key unter SSH Keys eintragen'
GIT_SSH_COMMAND="$sshcmd" git ls-remote git@github.com:Ullmann27/device-ai.git HEAD >/dev/null ||
  blocked 'SSH-Key hat keinen Lesezugriff auf device-ai; origin bleibt unveraendert'

previous="$(git remote get-url origin)"
previous_config="$(git config --local --get core.sshCommand 2>/dev/null || true)"
git config --local core.sshCommand "$sshcmd"
git remote set-url origin git@github.com:Ullmann27/device-ai.git
if ! git ls-remote origin HEAD >/dev/null; then
  git remote set-url origin "$previous"
  if [[ -n "$previous_config" ]]; then
    git config --local core.sshCommand "$previous_config"
  else
    git config --local --unset core.sshCommand || true
  fi
  blocked 'SSH nach Umstellung fehlgeschlagen; alter Git-Ursprung wiederhergestellt'
fi
printf 'CONTINUUM_GITHUB_SSH_READY=TRUE\n'
printf 'HTTPS-Token ist fuer normale git pull Befehle jetzt nicht mehr erforderlich.\n'
printf 'Privaten SSH-Schluessel niemals in Chat, GitHub-Dateien oder Downloads teilen.\n'
