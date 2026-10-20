#!/usr/bin/env bash
set -Eeuo pipefail

# Device AI Continuum: safe Samsung preview refresh.
# Never deletes application data, installs packages, or changes Main.
PROJECT="$HOME/device-ai"
BRANCH="astra/continuum-experiment-charter-20261019"
KEY="$HOME/.ssh/continuum_device_ai_readonly"
cd "$PROJECT"
if [[ "$(git branch --show-current)" != "$BRANCH" ]]; then
  printf '%s\n' "ABBRUCH: Falscher Branch. Erwartet: $BRANCH" >&2
  exit 2
fi
if [[ -n "$(git status --porcelain)" ]]; then
  printf '%s\n' "ABBRUCH: Nicht gespeicherte lokale Änderungen. Nichts überschrieben." >&2
  exit 3
fi
if [[ ! -f "$KEY" ]]; then
  printf '%s\n' "ABBRUCH: Der bekannte read-only GitHub-Schlüssel ist nicht vorhanden." >&2
  exit 4
fi

export GIT_SSH_COMMAND="ssh -i $KEY -o IdentitiesOnly=yes -o StrictHostKeyChecking=yes -o BatchMode=yes"
printf '%s\n' "1/5: Experimentbranch sicher aktualisieren."
git pull --ff-only
printf 'Aktueller Commit: '
git log -1 --format='%h %s'
printf '%s\n' "2/5: Forschungs-, Archiv- und CDX1-Node-Tests."
node --test \
  .github/continuum/autonomous-topic-frontier.test.mjs \
  .github/continuum/continuum-growth-and-archive.test.mjs \
  .github/continuum/research-job-permission.test.mjs \
  .github/continuum/cdx-large-curriculum.test.mjs \
  .github/continuum/local-model-smoke.test.mjs
printf '%s\n' "3/5: Android-Werkzeuge prüfen."
node scripts/continuum-mobile-builder.mjs doctor
printf '%s\n' "4/5: Kotlin Unit-Tests, Android Lint, APK und Signaturprüfung."
node scripts/continuum-mobile-builder.mjs build
printf '%s\n' "5/5: Nur die gerade geprüfte Preview für Chrome bereitstellen."
printf '%s\n' "Termux offen lassen; die bestehende Android-App NICHT vorher löschen."
node scripts/continuum-preview-chrome-download.mjs
