#!/usr/bin/env bash
# Read-only Fold7 diagnosis for Continuum (adb from a PC, or Termux with wireless adb). Reads only system metrics:
# CPU, load, RAM, storage, thermal, battery, Continuum jobs/process/jank, ANR and crash headers. It never reads chat
# data, app files, preferences, keys or passwords, and changes nothing - except with the explicit --stop-continuum,
# which force-stops the app (no data is deleted) to test whether hangs persist without Continuum.
#   scripts/fold7-diagnose.sh [out-dir] [--stop-continuum] [--minutes N]
set -u
PKG=at.heinz.deviceai.continuumlocal
OUT=fold7-diag-$(date +%Y%m%d-%H%M%S); STOP=0; MIN=5
for x in "$@"; do case "$x" in --stop-continuum) STOP=1;; --minutes) :;; [0-9]*) MIN=$x;; *) OUT=$x;; esac; done
mkdir -p "$OUT"; a(){ adb shell "$@" 2>&1; }
adb get-state >/dev/null 2>&1 || { echo "Kein Gerät per adb verbunden."; exit 2; }
snap(){ d="$OUT/$1"; mkdir -p "$d"
  a dumpsys cpuinfo | head -40 > "$d/cpuinfo.txt"; a top -b -n 1 -m 25 > "$d/top.txt"
  a cat /proc/meminfo | head -8 > "$d/meminfo-system.txt"; a dumpsys meminfo "$PKG" | head -45 > "$d/meminfo-continuum.txt"
  a dumpsys thermalservice | grep -iE "status|temperature\{|throttl" | head -30 > "$d/thermal.txt"
  a dumpsys battery | grep -E "level|temperature|status|plugged" > "$d/battery.txt"; a df -h /data > "$d/storage.txt"
  a dumpsys jobscheduler "$PKG" | grep -E "JOB #|Running|Pending|enqueue|Ready|Satisfied|periodic" | head -60 > "$d/jobs-continuum.txt"
  a dumpsys activity processes "$PKG" | grep -E "ProcessRecord|oom|curAdj|procState" | head -20 > "$d/process.txt"
  a dumpsys gfxinfo "$PKG" | grep -E "Total frames|Janky|percentile|Slow" > "$d/jank.txt"
  # ANR and crash headers only (process, subject, time) - no stack contents, no message text.
  a dumpsys dropbox --print data_app_anr | grep -E "^Process:|^Subject:|^[0-9]{4}-[0-9]{2}-[0-9]{2} " | tail -40 > "$d/anr-headers.txt"
  a logcat -b crash -d -t 400 | grep -E "FATAL EXCEPTION|Process: |^[A-Za-z.]+(Exception|Error)\b" | cut -c1-160 | tail -40 > "$d/crash-headers.txt"; }
sample(){ f="$OUT/$1/load.tsv"; echo -e "time\tloadavg\tthermal" > "$f"
  for i in $(seq 1 $((MIN*6))); do echo -e "$(date +%T)\t$(a cat /proc/loadavg | cut -d' ' -f1-3)\t$(a dumpsys thermalservice | grep -m1 -i 'Thermal Status' | tr -dc '0-9')" >> "$f"; sleep 10; done; }
echo "Phase A: normaler Betrieb ($MIN min). Hänger bitte mit Uhrzeit notieren."; snap A; sample A; snap A-end
if [ "$STOP" = 1 ]; then
  echo "Phase B: Continuum gestoppt (force-stop, keine Daten gelöscht), $MIN min. Treten Hänger weiter auf?"
  a am force-stop "$PKG" >/dev/null; snap B; sample B; snap B-end
fi
echo "Fertig: $OUT (enthält nur Systemmetriken). Hänger-Zeiten notieren und mit load.tsv vergleichen."
