# Continuum – Übergabe Opus, 22.10.2026 (Android-Coding-Durchstich)

Dieses Protokoll ist für **jede** nachfolgende KI oder jeden Entwickler gedacht. Nichts hier hängt an einer Opus-Sitzung.
Immer zuerst den tatsächlichen Remote-HEAD lesen; die SHAs unten sind Momentaufnahmen.

- Branch: `opus/continuum-mobile-coding-e2e-20261022` (gestapelt auf PR #53 `sol/continuum-isolated-research-20261021`, Basis-SHA `8897fd6630800b297fbda8f8638c7b615cf2b172`)
- Main unverändert `190f5eabaa60fc59292b2b80d3086aafd7743a80`. Kein Merge, keine APK, keine Signierung, keine Installation.
- Umgebung: Linux x86_64-Container, Node v22.22.0, git, JDK 21, Gradle 8.14.3. **Kein Android-SDK** (Host `dl.google.com` ist in dieser Umgebung per Netzwerkrichtlinie gesperrt, HTTP 403) → Android-Gradle-Build/Lint/APK: `UNAVAILABLE`. Kein Samsung-Zugriff.

## Was neu funktioniert (Experiment EXP-00 / M4.2)

Lücke vorher: Der Android→Termux-Kanal (`ContinuumMobileBuildPlan`, `RUN_COMMAND`) kannte nur `doctor|build|status` des APK-Builders. Der vorhandene Coding-Worker (Teamleiter → Besitzer-`TEST`-Auftrag → Vertragssynthese → Worktree-Tests → zweiter unabhängiger Worktree → Patch → Backlog `DONE`) war von dort nicht erreichbar, und es gab keine prüfbare Quittung.

Jetzt (keine zweite Pipeline, nur Anbindung):
- `scripts/continuum-mobile-builder.mjs code-test --project P --job <UUIDv4> --module .github/continuum/<m>.mjs --export <name> --cases-json '<≤300 Zeichen>' [--budget-minutes 1..30]`
  legt den Auftrag als Besitzer-`TEST` im **kanonischen** Backlog an (`backlog-cli propose-test` + `accept`, Task-ID deterministisch `mob-<sha256(job)[0:16]>`), führt **genau einen** `runTeamleaderCycle` aus und gibt eine Quittung `continuum-mobile-coding-receipt` v1 aus: Job-ID, Task-ID, Commit, Toolchain, Status, Teamleiter-Ergebnis, Patch-SHA-256 + Pfad + Bytes, Verifikationsbeleg-SHA, **echte Exitcodes** der drei festen Testläufe (baseline/candidate/independent), Fehlerursache. `approvedForMerge:false`, `installed:false`, `apk:null`, `deviceVerified:false`.
- `code-cancel --job <UUIDv4>`: bricht den Backlog-Auftrag ab (`abort`), überschreibt keine Quittung.
- Schutz: Replay derselben Job-ID führt nichts erneut aus (`replayed:true`); unsauberer Checkout → `BLOCKED_UNCLEAN_SOURCE`; anderer Commit als beim Anlegen → `BLOCKED_STALE_BASE`; belegter Worker/Lock → `BLOCKED_JOB_RUNNING` (Auftrag bleibt READY); vorrangige andere Arbeit → `BLOCKED_OTHER_WORK_FIRST`; ungültige Werte → `BLOCKED_INVALID_ORDER` ohne Backlog-Eintrag.
- `owner-contract-worker.mjs`: Verifikationsbeleg enthält zusätzlich `runs` (Phase, Profil, Exitcode, Timeout, Dauer, Zählwerte) – additiv.
- `teamleader-cli.mjs`: `defaultTeamleaderStateDir(root)` exportiert, damit Termux-Job und Teamleiter denselben Zustand nutzen.

## Tatsächlich ausgeführte Tests (dieser Container)

- `node --test --test-concurrency=2 .github/continuum/*.test.mjs scripts/*.test.mjs` → **832 Tests, 826 bestanden, 0 fehlgeschlagen, 6 übersprungen**. Die 6 übersprungenen Tests benötigen ein echtes `kotlinc` im PATH (kotlin-delegate-acceptance/-synthesizer, owner-kotlin-preview); sie wurden hier **nicht ausgeführt**.
- Neu `scripts/continuum-mobile-coding.test.mjs` 4/4: echter Lauf über exakt die Termux-Argumentliste in einem temporären Git-Repo (3 echte `node --test`-Läufe, Patch-Bytes = SHA, Backlog DONE, Checkout unverändert, Replay ohne Zweitlauf), Negativfall falscher Erwartungswert → `FAILED_CODING_CHECKS` ohne Patch, Fail-closed-Fälle (ungültig, unsauber, belegt, Basis verschoben, Abbruch).

## Statuskategorien

- Node-Termux-Jobkanal → Coding-Worker → Quittung: `SOURCE_INTEGRATED`, real auf Linux ausgeführt; **nicht** `DEVICE_TOOLCHAIN_VERIFIED`.
- Android-Seite (Auftrag erzeugen, Quittung anzeigen): siehe Abschnitt Android unten; ohne APK-Build und Gerät höchstens `SOURCE_INTEGRATED`/`MOCK_TESTED`.
- `END_TO_END_VERIFIED`, `REAL_APK_BUILT`, `DEVICE_INSTALLED`: **nein**.

## Android
Siehe Folgecommit/PR-Beschreibung: reines Kotlin-Objekt `ContinuumMobileCodingJob` (Auftragsvalidierung, feste Befehlszeile, Quittungsprüfung). Die bestehende Builder-Activity wurde bewusst **nicht** verändert (jeder `Operation`-Enumwert erzeugt dort automatisch einen Button; ohne Kompilierung keine ungeprüften UI-Änderungen).

## Nächster konkreter Schritt
1. Auf Samsung/Termux im sauberen Checkout: `node --test scripts/continuum-mobile-coding.test.mjs`, dann
   `node scripts/continuum-mobile-builder.mjs code-test --project ~/device-ai --job $(cat /proc/sys/kernel/random/uuid) --module .github/continuum/<vorhandenes Modul>.mjs --export <export> --cases-json '[{"args":[..],"expected":..}]'` und Quittung prüfen.
2. Danach UI-Anbindung in `ContinuumMobileBuilderActivity`/`ContinuumMobileBuildResultReceiver` (Eingabe, Bestätigungsdialog, Job-ID in PendingIntent, Quittung über `ContinuumMobileCodingJob.evaluate` anzeigen) mit echtem Gradle-Build auf Render oder Termux.

## Rollback
Branch ist eigenständig; Rücknahme = Commits dieses Branches nicht übernehmen bzw. `git revert`. Keine Datenmigration, kein Besitzerzustand gelöscht; Backlog-Einträge `mob-*` können mit `backlog abort` beendet werden.
