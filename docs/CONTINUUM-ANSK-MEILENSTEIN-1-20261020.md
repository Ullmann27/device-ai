# Device AI Continuum – ANSK-Meilenstein 1

**Stand:** 20. Oktober 2026  
**Branch:** astra/continuum-experiment-charter-20261019 · Draft-PR #42  
**Status:** Forschungsimplementierung im GitHub-Branch; kein Android-Gerätenachweis der neuen Prüfkette und keine automatische Modellpromotion.

## Erster vertikaler Systemaufbau

1. Der bestehende reversible Kotlin-Import-Planer erzeugt eine geplante Ein-Fehler-Mutation. Die fünf ursprünglichen Planner-Tests wurden bereits auf Samsung bestanden.
2. Der neue einmalige ANSK-Labor-Runner (ansk-import-lab.mjs) arbeitet ausschließlich im vorhandenen experimentellen Branch. Er verweigert main und unsaubere Quell-Checkouts.
3. In einem verwalteten Git-Wegwerf-Worktree wird eine **echte Grün-0 → Rot-Mutation → Grün-Reparatur** ausgeführt. Das Labor benutzt nur die vorhandene feste Android-Prüfkette aus repair-loop.mjs, niemals generierte Shell-Befehle.
4. Alle vier Prüfphasen verwenden Gradle --rerun-tasks. Die vorhandene Java-17/Gradle-8.11.1/ARM64-aapt2-Konfiguration aus scripts/continuum-mobile-builder.mjs wird wiederverwendet. Fehlende gepinnte Termux-Werkzeuge blockieren die Prüfung.
5. Die mutierte Datei muss eine konkrete Kotlin-Compilerdiagnose UNRESOLVED_REFERENCE zum erwarteten Symbol liefern. Nur die bereits vorhandene, vom repair-critic akzeptierte Strategie KOTLIN_MISSING_IMPORT ist als experimenteller Reparaturgewinner zulässig.
6. Derselbe Patch wird auf einem **zweiten unabhängigen Worktree** vom ursprünglichen Basis-Commit erneut angewendet und mit vollständig frischer Kompilierung sowie JUnit getestet. Source-SHA-256 und pro Profil ausgeführte Testzahlen werden kontrolliert.
7. Nur bei bestandenen Prüfungen wird ein **deklaratives Rezept mit SHA-256** in privaten Termux-State-Daten außerhalb des Git-Checkouts gesichert. Es ist quarantänisiert und hat ausschließlich SHADOW_ONLY-Autorität. Weder repair-ledger.json noch CRE1-Gewichte werden verändert.
8. Mit planAnskShadowReplay kann dieser Rezeptbeleg einen neuen, bisher unbekannten Kotlin-Importfehler lediglich als **unverbindlichen Kandidaten** bedienen. Bestehender Kotlin-Lexer, insertImport, hunk, applyEdits und Critic bleiben zuständig. Keine automatische Aktivierung oder Tool-Registrierung.

## Bestehende Komponenten wiederverwendet

- planInverseKotlinImportMutation in repair-strategies.mjs
- createWorktree, resetWorktree, removeWorktree, boundedWorktreeFile, runChain, evidenceSufficient, rankCandidates und checkCandidateKotlinSyntax in repair-loop.mjs
- proposeCandidates und applyEdits in repair-strategies.mjs
- critique in repair-critic.mjs
- validateTriplePhaseSample in repair-cycle-safety.mjs als separates strukturelles Zusatz-Gate, niemals als Nachweis einer tatsächlichen Ausführung
- resolveTermuxEnvironment in scripts/continuum-mobile-builder.mjs

## Neue Dateien und Testfälle

- .github/continuum/ansk-lab-contract.mjs – Forschungs-Gates für grün/rot/grün und gehashte Rezepte
- .github/continuum/ansk-import-lab.mjs – zeitbegrenzter Einmalversuch in zwei isolierten Worktrees
- .github/continuum/ansk-recipe-replay.mjs – rein lesende Wiederverwendung eines quarantänisierten Rezepts
- .github/continuum/ansk-lab-contract.test.mjs – 11 neue Kontrakttests
- .github/continuum/ansk-import-lab.test.mjs – 6 neue Git-/Branch-/CLI-Sicherheitsprüfungen
- .github/continuum/ansk-recipe-replay.test.mjs – 7 neue Schatten-Replay- und Tampering-Tests

Damit sind **24 neue Testfälle** im Forschungsbereich definiert. Der vorherige Mutationsplaner hat weitere 5 Tests; der Dreiphasen-Validator hat eigene Regressionstests.

## Sicherheits- und Erkenntnisgrenzen

Der Prototyp ist offlinefähig, nutzt keine Groq-Anfrage und keine GitHub-Actions. Er kann keine APK installieren, nach main schreiben, Trainingsgewichte verändern oder generierten Code als Werkzeug registrieren.

Die lokale Schattenrezeptdatei ist nicht kryptografisch authentisiert: Der SHA-256-Hash schützt vor unbemerkter Inhaltsveränderung bei unverändertem Hash, nicht vor Angreifern, die den Inhalt und Hash zugleich ersetzen.

**Nachtrag Testbefehl für die lokale Buildsperre:**

~~~bash
node --test .github/continuum/shared-build-lease.test.mjs \
  .github/continuum/autonomous-teamleader.test.mjs \
  .github/continuum/ansk-import-lab.test.mjs \
  scripts/continuum-mobile-builder.test.mjs
~~~

Der isolierte Modul-Funktionstest der Buildsperre wurde außerhalb des Repositorys mit Node 22 durchgeführt (5/5 PASS). **Die vier obigen originalen Repository-Testdateien, die vollständige Gradle-Prüfkette und die echte Samsung-IPC wurden für diesen neuen Commit noch nicht ausgeführt.**


Der ANSK-Runner besitzt weiterhin einen eigenen exklusiven Forschungs-Lock und prüft zusätzlich den bisherigen Teamleiter-Lock. **Nachtrag 20.10.2026:** Drei lokale Node-Einstiegspunkte – ANSK-Labor, Offline-Teamleiter und Termux-APK-Builder – verwenden nun außerdem dieselbe atomare, checkoutgebundene `shared-build-lease.mjs`. Eine vorhandene Sperre führt zum sofortigen Abbruch vor Gradle; Prozessabstürze führen absichtlich nicht zu einer zeitgesteuerten Sperrenübernahme. Unsicher bereinigte ANSK-Worktrees halten die Sperren zur Prüfung zurück. Das ist eine lokale Kooperationssperre für Prozesse mit derselben Betriebssystemidentität, **noch keine verifizierte Android-App↔Termux-IPC-Lease**, kein OS-Sandbox-Ersatz und keine Freigabe für unbeaufsichtigte Geräteexperimente.

Der Erfolg einer vorhandenen Kotlin-Importregel beweist noch keine neu entdeckte generative Fähigkeit. Dies ist ein absichtlich einfacher Integrationsfall für überprüfbare Erkenntnis-Rezepte.

## Nachweis- und Abnahmeplan

Zuerst die neuen Node-Tests auf dem aktuellen Branch ausführen, nach Sync mit GitHub:

~~~bash
cd "$HOME/device-ai"
git status --short --branch
node --test .github/continuum/ansk-lab-contract.test.mjs \
  .github/continuum/ansk-import-lab.test.mjs \
  .github/continuum/ansk-recipe-replay.test.mjs \
  .github/continuum/inverse-mutation.test.mjs \
  .github/continuum/repair-cycle-safety.test.mjs
~~~

Der erste echte, ausdrücklich vom Besitzer zu startende Samsung-/Termux-Versuch ist:

~~~bash
node .github/continuum/ansk-import-lab.mjs --once \
  --file app/src/main/java/at/heinz/deviceai/learning/ModelCheckpointStore.kt \
  --import java.nio.file.Files
~~~

Diese tatsächliche Projektdatei importiert java.nio.file.Files und ruft Files.move auf. Der Versuch kann wegen Gradle auf Mobilhardware erheblich dauern. Das Lab läuft höchstens 45 Minuten und bricht ab, wenn die Baseline nicht grün, die Mutation nicht rot, die Reparatur nicht grün oder der unabhängige Worktree nicht grün wird. Bei Erfolg meldet es TRIPLE_PHASE_VERIFIED_SHADOW_RECIPE.

**Evidenzstatus:** Die neuen 11 Kontrakttests und 7 Replaytests wurden während der Entwicklung zusätzlich in einer isolierten V8-Umgebung gegen die tatsächlichen GitHub-Quellen geprüft (18 PASS). Dies ersetzt keinen echten Node-Testlauf mit Git-Fixture-Testfällen und keinen Samsung-Kotlin-/JUnit-Build.

## Meilensteinstatus

- M1 Code: kompletter einmaliger Forschungsablauf plus Schatten-Replay im isolierten Branch – **implementiert**.
- M1 Node: alle Tests nativ unter Node ausführen und 0 FAIL/0 SKIP belegen – **offen**.
- M1 Samsung: Grün-0 → Rot-Mutation → Grün-Reparatur + zweiter Worktree mit echter Kotlin-/JUnit-Prüfung – **offen**.
- M1 realer Lerngewinn: ein neues Problem wird durch die Rezeptbibliothek nach unabhängig geprüften Tests messbar besser gelöst – **offen**.
- M2 Android-Autonomie: lokale gemeinsame Build-Sperre für die drei zentralen Node-Worker **quellseitig integriert, fünf neue isolierte Node-Tests lokal erfolgreich**. Die zusätzlich definierten integrationsbezogenen Regressionen sind auf dem vollständigen Repository-HEAD noch nicht ausgeführt. Android↔Termux authentisierte Job-Lease, Gerätekonkurrenz, Recovery sowie Besitzer-/Ressourcengates bleiben **offen**.

Keine unbewiesenen 100-Prozent-Korrektheits-, Null-Watt- oder Submillisekunden-Versprechen.