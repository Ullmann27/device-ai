# Begrenzter OS-Ausführer im kanonischen Repair-Worker

21.10.2026 · EXP-00/01/02/03/07/08/11/12 · `SOURCE_INTEGRATED`, Linux-Testbelege; Samsung `UNAVAILABLE`.

Basis PR #49 / `377bc05b8a8d96494f4cee7be8665fde3317f1e7`. Keine Änderungen an Main, Android-Paket, Signatur, aktiven Gewichten oder Besitzerdateien.

## Tatsächlich implementiert

Der vorhandene Owner-REPAIR-Worker akzeptiert optional einen echten `createIsolatedExecutor` aus der lokalen Vertrauensgrenze. Ein gleich aussehendes JSON-/Modellobjekt erhält keine Ausführungsrechte. CLI: `owner-repair --file <externer-backlog> --out <externe-belege> --local-brain --local-paths <explizite-dateien> --isolation-config <externe-json>`. Ohne diese explizite Kapazität bleiben die bisherigen begrenzten numerischen Kandidaten unverändert. Wenn die gewählte Isolation fehlt, gibt es keinen Host-Fallback.

Die unveränderliche Quellkopie enthält ausschließlich reguläre Git-Index-Dateien und keine `.git`-Daten. Symlinks, Submodule, Konflikte, zu große Dateien und ausbrechende Pfade werden abgewiesen. Der Aufrufer muss den öffentlichen Toolchain-/Dependencybestand ausdrücklich auswählen. Kein Besitzer-HOME, Credentialverzeichnis, Docker-Socket oder schreibbarer Host-Bind wird in den Container gegeben. Feste Profile bestimmen sämtliche Kommandos; Modelltext bestimmt weder Shell noch Toolchain.

Ein lokaler Docker-Daemon ist hier eine **optionale Linux-Kapazität**. Das aus `scripts/continuum-isolation.Dockerfile` gebaute leere Image wird nur über seine unveränderliche SHA-256-ID zugelassen. Der Prozess hat einen unprivilegierten Benutzer, getrennte PID-/Netzwerk-/IPC-Namensräume, `network=none`, keine Linux-Capabilities, `no-new-privileges`, schreibgeschützte Wurzel und Quellen sowie begrenzte CPU-, RAM-, PID-, FD- und Laufzeitbudgets. Flüchtige tmpfs-Verzeichnisse sind die einzigen Schreibbereiche. Toolchains und öffentliche Offline-Abhängigkeiten werden read-only eingebunden. `env -i` entfernt Hostvariablen.

Baseline, Kandidat und unabhängige zweite Worktree-Prüfung verwenden denselben Adapter. Der vorhandene Diagnostikparser übersetzt Containerquellpfade kontrolliert auf die Worktree-Pfade. Vor dem Testlauf bleiben der bestehende Critic und die nicht ausführende Syntaxprüfung aktiv. Breite JS-/Kotlin-Hunks bleiben auf zwei explizite Quellen, 1.600 Zeichen/Hunk und die vorhandenen Criticgrenzen beschränkt. Tests, Buildspezifikation und Sicherheitssteuerung bleiben geschützt.

`LOCAL_ISOLATED_PATCH` ist stets reviewpflichtig und `learningEligible:false`. Weder Replay noch Reparaturgedächtnis lernen einen Erfolg daraus, auch wenn ein Aufrufer `independentPass:true` übergibt. Das ist notwendig: **OS-Isolation schützt den Host; Kandidatencode könnte seine Laufzeit und Testberichte beeinflussen.** Eine zweite Worktree-Prüfung und unveränderte Tests sind nützlich, aber kein manipulationssicherer semantischer Korrektheitsbeweis. Belege markieren Berichte deshalb als `untrustedReports:true`. Kein automatischer Merge, Gewichtsupdate oder APK-Installationsschritt folgt daraus.

## Echte Prüfungen und kleine Reparaturen

- Vier tatsächliche Kernel-/Negativtests: Besitzerdateien/Quellen unveränderbar, keine Hostcredentials/-Sockets, keine Privilegienerhöhung, wirksame cgroup-RAM-/PID-Grenzen, gescheiterte zusätzliche Mount-Namespace-Erzeugung, kein Hostnetzwerk/-PID-Zugriff und tatsächliche Zurückweisung überzähliger Kindprozesse.
- Echter Kotlin-2.0.20/JDK17-Compilerlauf und JVM-Ausführung im selben eingeschränkten Profil; Fallnormalisierung/Listenverarbeitung und fehlender Zugriff auf externe Besitzerdateien geprüft.
- Endloser Kandidat: Timeout einschließlich Containerentfernung zuletzt **1.221 ms**. Beim ersten Versuch konnte Docker das Timeout-SIGTERM weiterleiten und weiterwarten; der Client wird jetzt mit SIGKILL beendet, danach der Container separat beendet/entfernt. Der manuell unterbrochene erste Versuch gilt ausdrücklich nicht als Timeoutbeleg.
- Ein kleiner mehrfunktionaler JS-Listenverarbeitungsfall lief durch den echten kanonischen Worker: Baseline 1/3 korrekt, Kandidat und unabhängiger zweiter Worktree 3/3, keine Tests übersprungen, Besitzerquelle unverändert, null gelernte Erfahrungen. Der Kandidat war fest injiziert: **kein Modell-Kompetenznachweis**.
- Fünf neue Negativtests prüfen gefälschte Executorobjekte, Ressourcen-/Imagegrenzen, manipulierte Receipts, Konfigurations-/Symlinkgrenzen und das Verbot automatischer Lernlabels. Die gesamte Node-/Werkzeugsuite bestand 828/828, Backend 60/60.
- Der unabhängige Read-only-Review fand keinen konkreten P0/P1-Bypass. Sein Hinweis auf eine nicht bestätigte Beendigung bei Docker-Daemon-Ausfall wurde geschlossen: solche Fehler ergeben nun `ISOLATION_TEARDOWN_UNCONFIRMED`, niemals einen bestätigten Erfolg.

Reproduktion mit bereits geprüften lokalen Werkzeugen, ohne Download:

```sh
docker --host=unix:///var/run/docker.sock build --network=none -f scripts/continuum-isolation.Dockerfile -t continuum-isolation:local .
node --test .github/continuum/isolated-executor.test.mjs
node scripts/continuum-isolation-proof.mjs /absoluter/externer/toolchain.json
node scripts/continuum-isolated-repair-proof.mjs /absoluter/externer/toolchain.json
```

Externe JSON, ausschließlich konkrete öffentliche Werkzeugverzeichnisse, keine Shellargumente:

```json
{"image":"sha256:<tatsaechliche-64-hex-image-id>","nodeHome":"/werkzeuge/node","jdkHome":"/werkzeuge/jdk17","kotlinHome":"/werkzeuge/kotlinc","gradleHome":"/werkzeuge/gradle","androidHome":"/werkzeuge/android-sdk","gradleModules":"/oeffentlicher-cache/caches/modules-2","timeoutMs":240000,"memoryMb":4096,"pids":128,"cpus":2}
```

Node allein benötigt nur Image und Node-Verzeichnis. Toolchainprovenienz und Linux-Kernel/Docker-Daemon gehören zur Vertrauensbasis. Der Adapter installiert diese Kapazität nicht. Bei Daemonverlust kann die physische Containerbeendigung unbekannt bleiben; der Lauf stoppt mit Fehler und benötigt Administratorprüfung. Keine Sicherheitsgarantie gegen Kernel-/Daemonlücken.

## Offene Nachweise und Rücksetzung

Die festen Android-Compile-/Unit-/Lint-/Buildprofile sind implementiert, **in dieser neuen OS-Isolation noch nicht ausgeführt**. Keine Behauptung eines aktuellen vollständigen Android-Builds, signierten APKs oder Samsung-Isolationsnachweises. Docker ist keine vorausgesetzte Android-Fähigkeit; auf dem Samsung muss zuerst eine tatsächlich verfügbare gleichwertige native Grenze nachgewiesen werden. `proot` oder Worktree allein gelten weiterhin nicht als solche Grenze. Bis dahin keine breiten Modellpatches auf dem Telefon ausführen. Die vorhandene sichere Builder-/Termux-Pipeline bleibt erhalten.

Nächster klarer Schritt: auf einem geeigneten Linux-Worker genau das feste Androidprofil mit vorbereiteten öffentlichen Offline-Caches prüfen; danach separat neue unbekannte Coding-Aufgaben mit unabhängigen Orakeln. Keine Verlängerung dieses Meilensteins nach der Besitzerangabe von 5 % Restkontingent. Rollback: Isolationopt-in nicht wählen bzw. den isolierten Integrationscommit zurücknehmen; keine Datenmigration.
