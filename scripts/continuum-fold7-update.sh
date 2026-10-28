#!/usr/bin/env bash
# Owner-confirmed Fold7 in-place update of the ALREADY installed Continuum Lokal.
# Single command; never installs, deletes, rekeys or touches any other app.
set -Eeuo pipefail
umask 077

blocked() { printf 'CONTINUUM_UPDATE_BLOCKED=%s\n' "$*" >&2; exit 69; }
usage() {
  printf 'Aufruf: bash scripts/continuum-fold7-update.sh [versionCode]\n'
  printf 'Ohne Zahl: naechsten VersionCode aus dem vorherigen signierten lokalen Build ermitteln.\n'
  exit 64
}
[[ $# -le 1 && ( $# -eq 0 || "$1" =~ ^[0-9]+$ ) ]] || usage
[[ -t 0 ]] || blocked 'Interaktive Besitzerfreigabe in Termux erforderlich'
[[ "${PREFIX:-}" == /data/data/com.termux/files/usr* ]] ||
  blocked 'Nur fuer die bestehende Fold7-Termux-Umgebung'
root="$(cd -- "$(dirname -- "${BASH_SOURCE[0]}")/.." && pwd -P)"
cd "$root"
[[ -f scripts/continuum-fold7-standalone-apk.sh && -f app/build.gradle.kts ]] ||
  blocked 'Vollstaendige Continuum-Quellen fehlen'
for bin in git node bash sha256sum; do
  command -v "$bin" >/dev/null 2>&1 || blocked "Programm fehlt: $bin"
done

expected_branch='chatgpt/continuum-german-research-contradiction-20261028'
branch="$(git symbolic-ref --quiet --short HEAD 2>/dev/null)" ||
  blocked 'Detached HEAD: nicht automatisch bauen'
[[ "$branch" == "$expected_branch" ]] ||
  blocked "Falscher Branch: $branch. Nur freigegebener Continuum-Lokal-Entwicklungsbranch erlaubt"
remote="$(git remote get-url origin 2>/dev/null)" || blocked 'GitHub-Origin fehlt'
case "$remote" in
  https://github.com/Ullmann27/device-ai|\
  https://github.com/Ullmann27/device-ai.git|\
  git@github.com:Ullmann27/device-ai.git|\
  ssh://git@github.com/Ullmann27/device-ai.git) ;;
  *) blocked 'Unbekannter Git-Server; kein automatisches Update' ;;
esac
[[ -z "$(git status --porcelain --untracked-files=normal)" ]] ||
  blocked 'Nicht gespeicherte Quellcodeaenderungen: zuerst sichern'
keydir="$HOME/.local/share/device-ai-continuum/signing"
keyfile="$keydir/continuum-local.p12"
fpfile="$keydir/continuum-local-cert-sha256.txt"
[[ -f "$keyfile" && -s "$keyfile" && ! -L "$keyfile" && -f "$fpfile" && ! -L "$fpfile" ]] ||
  blocked 'Bisherige Original-P12 fehlt. Niemals neue Signatur erzeugen; Backup wiederherstellen'
expected_fp='6354e91dc0ca8df9173885f32402fd817f46e798f369206d4fd710ff3d03ee23'
[[ "$(cat "$fpfile")" == "$expected_fp" ]] ||
  blocked 'Zertifikat entspricht nicht der installierten Continuum-Lokal-Signatur'
# Build report includes no secrets, and is written after APK signing/hash verification.
state="$HOME/.local/state/device-ai-continuum/standalone-release"
[[ -d "$state" ]] || blocked 'Lokale Build-Nachweise fehlen; Versionscode nicht raten'
shopt -s nullglob
reports=("$state"/BUILD-INFO-*.txt)
(( ${#reports[@]} > 0 )) ||
  blocked 'Kein erfolgreicher alter Release dokumentiert; Versionscode nicht raten'
value() { sed -n "s/^$1=//p" "$2" | head -n1; }
highest=0
last_sha=''
last_apk=''
last_hash=''
for file in "${reports[@]}"; do
  [[ -f "$file" && ! -L "$file" ]] || continue
  [[ "$(value package "$file")" == at.heinz.deviceai.continuumlocal ]] || continue
  [[ "$(value signer_sha256 "$file")" == "$expected_fp" ]] || continue
  old_version="$(value version_code "$file")"
  [[ "$old_version" =~ ^([2-9][0-9]|[1-9][0-9]{2,3})$ ]] || continue
  (( old_version >= 26 && old_version <= 9999 )) || continue
  if (( old_version > highest )); then
    highest="$old_version"
    last_sha="$(value source_sha "$file")"
    last_apk="$(value artifact "$file")"
    last_hash="$(value apk_sha256 "$file")"
  fi
done
(( highest >= 26 )) || blocked 'Kein verifizierbarer alter Continuum-Lokal-Release'
[[ "$last_sha" =~ ^[a-f0-9]{40}$ && "$last_hash" =~ ^[a-f0-9]{64}$ ]] ||
  blocked 'Alte lokale Build-Metadaten unvollstaendig'

printf 'Kontrollierter Git-Abgleich: %s\n' "$branch"
# --ff-only prevents unintended merges. Never pull, switch or merge "main".
git pull --ff-only origin "$expected_branch" ||
  blocked 'Git-Aktualisierung fehlgeschlagen. Keine alten Dateien/Schluessel geaendert'
[[ "$(git symbolic-ref --quiet --short HEAD)" == "$expected_branch" &&
   -z "$(git status --porcelain --untracked-files=normal)" ]] ||
  blocked 'Branch oder Arbeitsverzeichnis nach Pull unklar'
sha="$(git rev-parse --verify HEAD)"
[[ "$sha" =~ ^[a-f0-9]{40}$ ]] || blocked 'Git-Quellstand ungueltig'

if [[ "$sha" == "$last_sha" && $# -eq 0 ]]; then
  # Reuse only the exact previously verified APK; no pointless rebuild.
  if [[ -f "$last_apk" && ! -L "$last_apk" &&
        "$(sha256sum "$last_apk" | cut -d ' ' -f1)" == "$last_hash" ]]; then
    printf 'CONTINUUM_UPDATE_ALREADY_BUILT=%s\n' "$last_apk"
    printf 'Keine neue Codeaenderung: kein unnoetiger APK-Neubau.\n'
    exit 0
  fi
  blocked 'Frueheres APK-Artefakt fehlt oder stimmt nicht; kein blindes Rebuild'
fi

if (( $# == 1 )); then version="$1"; else version="$((highest + 1))"; fi
[[ "$version" =~ ^[0-9]+$ ]] && (( version > highest && version <= 9999 )) ||
  blocked "Version muss groesser als zuletzt gebauter Code $highest sein"
printf '\nCONTINUUM-LOKAL-UPDATE\n'
printf 'Paket: at.heinz.deviceai.continuumlocal\n'
printf 'Letzte Build-Version: %s | Neue Version: %s\n' "$highest" "$version"
printf 'Git-Commit: %s\n' "$sha"
printf 'Alter Version-25-Bestand und 532 importierte Quellen werden nicht angefasst.\n'
printf 'Signatur: vorhandene P12; KEINE neue Signatur. Keine automatische Installation.\n'
printf 'Vorbereitung: private P12 verschluesselt OFFLINE zweitgesichert? Unbedingt pruefen.\n'
IFS= read -r -p 'Freigabe fuer Tests und signierten APK-Build: BAUEN eingeben: ' approved
[[ "$approved" == 'BAUEN' ]] || blocked 'Keine Besitzerfreigabe; nichts gebaut'
unset approved

node --test scripts/continuum-fold7-standalone-apk.test.mjs \
  scripts/continuum-gemma-picker-lifecycle.test.mjs \
  scripts/continuum-fold7-update.test.mjs ||
  blocked 'Lokale Node-Sicherheitstests fehlgeschlagen; keine neue APK'
# Targeted CDX1 Kotlin regression before release; old checkpoint is never opened.
export JAVA_HOME="$PREFIX/lib/jvm/java-17-openjdk"
export PATH="$JAVA_HOME/bin:$PATH"
export ANDROID_HOME="$HOME/android-sdk"
export ANDROID_SDK_ROOT="$ANDROID_HOME"
gradle="$HOME/continuum-toolchains/gradle-8.11.1/bin/gradle"
[[ -x "$gradle" && -x "$PREFIX/bin/aapt2" ]] ||
  blocked 'Fold7-Buildwerkzeuge fehlen'
"$gradle" --offline --no-daemon --console=plain --max-workers=2 \
  -Dorg.gradle.jvmargs='-Xmx1600m -XX:MaxMetaspaceSize=512m' \
  -Pkotlin.compiler.execution.strategy=in-process \
  -PdeviceAiMentorPreview=true \
  -PdeviceAiMobileAidlFallback=true \
  "-Pandroid.aapt2FromMavenOverride=$PREFIX/bin/aapt2" \
  :app:testDebugUnitTest \
  --tests at.heinz.deviceai.learning.ContinuumCdxLargeCurriculumTest \
  --tests at.heinz.deviceai.learning.ContinuumDecoderBridgeTest ||
  blocked 'Gezielte CDX1-JUnit-Tests fehlgeschlagen; keine neue APK'
[[ "$sha" == "$(git rev-parse HEAD)" &&
   -z "$(git status --porcelain --untracked-files=normal)" ]] ||
  blocked 'Git-Quellcode wurde waehrend Tests veraendert'
bash scripts/continuum-fold7-standalone-apk.sh build "$version" ||
  blocked 'Android-Release fehlgeschlagen; installierte App unveraendert'
printf 'CONTINUUM_FOLD7_UPDATE_BUILD_COMPLETE=TRUE\n'
printf 'APK im Downloadordner nur nach eigener Freigabe als Update installieren.\n'
