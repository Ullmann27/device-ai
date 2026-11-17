# Continuum – Meilenstein und geprüfte Open-Source-Muster, 20.10.2026

**Repository:** Ullmann27/device-ai
**Entwicklungszweig:** astra/continuum-experiment-charter-20261019
**Review:** Draft-PR #42
**Priorität:** Android 16, ARM64, Termux; keine verpflichtende PC-, Cloud- oder bezahlte LLM-API.
**Experiment-IDs:** EXP-01 (lokaler Teamleiter), EXP-02 (geprüfte Reparaturen),
EXP-06 (Belegprovenienz), EXP-07 (Spezialistenprüfungen),
EXP-08 (Android↔Termux), EXP-11 (Orchestrierung), EXP-12 (Kontinuität).

## Getrennte belegte Fortschritte

1. Samsung/Termux: JDK17, Gradle 8.11.1, SDK36, ARM64-AAPT2 und apksigner:
   `doctor=READY_FOR_EXPERIMENTAL_BUILD`. Werkzeuge verfügbar bedeutet
   nicht, dass jede Android-Kompilierung erfolgreich ist.
2. Samsung: Die ausgewählten Build-Lease-/Status-Tests (12/12) sowie
   Teamleiter-/ANSK-/Builder-Tests (72/72) bestanden. Das waren
   überwiegend deterministische lokale Node-Tests mit Test-Fixtures.
3. Samsung: ANSK-Baseline tatsächlich gestartet; Ergebnis
   `BLOCKED_BASELINE_NOT_GREEN`. Die erste vollständige
   Grün–Rot–Grün-Reparatur in echten Android-Worktrees wurde **nicht** erreicht.
4. Samsung: Ein späterer Triagediagnose-Integrationsfehler (26/28)
   wurde durch ein gezieltes Update behoben; 30/30 Tests bestanden.
   Der tatsächliche Android-Baseline-Fehler ist weiterhin zu klassifizieren.
5. Experimentbranch: gemeinsamer Build-Lease, ANSK-Status und historische
   Fehlerdiagnose an bestehende `teamleader-cli.mjs --status` gekoppelt.
6. Dieser Nachtrag: `teamleader-review-handoff.mjs` verdichtet die
   ohnehin vorhandenen Statusbelege zu einem typisierten, ausnahmslos
   lesenden Review-Übergabepaket; keine zweite Task-Datenbank,
   keine automatischen Freigaben, keine neuen Netzwerk-APIs.

## Open-Source-Recherche: Prinzip übernehmen statt SDK nachinstallieren

**Omnigent** (`https://github.com/omnigent-ai/omnigent`):
Eine gemeinsame Steuerung über mehrere Agenten, getrennte Arbeitskontexte
und definierte Richtlinien. **Übernommenes Prinzip:** eindeutige Rollen und
reviewpflichtige Übergaben aus echten Belegen. Continuum behält seinen eigenen
kanonischen Backlog, seine Repair-Pipeline und isolierte Git-Worktrees.
Kein Omnigent-SDK integriert; keine Abhängigkeit von Claude/Codex.

**ai-memory** (`https://github.com/akitaonrails/ai-memory`):
Git-/Markdown-basiertes dauerhaftes Projektwissen und konkrete
Agentenübergaben. **Übernommenes Prinzip:** eine aus bestehendem State
reproduzierbare, kompakte Übergabe statt langer, unstrukturierter Logs.
Der neue Review-Handoff schreibt absichtlich keinen zweiten Memory-Store.
Künftig könnten nur bestätigte Befunde als versionierte Besitzerentscheidungen
im bestehenden Backlog gesichert werden; nicht aus Fremdtext trainieren.

**Android Skills** (`https://github.com/android/skills` und
`https://developer.android.com/tools/agents/android-skills`):
Spezialisierte, dokumentierte Android-Entwicklungsanweisungen.
**Derzeit nur als Prüfreferenz**, nicht als ungeprüft heruntergeladene
ausführbare Skills. Der native Termux-Builder bleibt maßgeblich; Android-
Werkzeuge müssen auf der Zielhardware verfügbar und reproduzierbar sein.

**magpie** (`https://github.com/yetone/magpie`):
Lokaler Gateway/Router für viele LLM-Anbieter und lokale Modelle; laut
README auch unter Termux. **Noch nicht eingebaut**: Die Abhängigkeit ist für
den deterministischen Teamleiter unnötig. Ein optionales Modellprovider-
Interface kann später isoliert ergänzt werden, ohne externe Logins vorauszusetzen.

**Anatomy** (`https://github.com/wheresryan22/anatomy`):
Isometrische SVG-/WebGL-Erklärungen. Als visuelle Inspiration für das
Continuum-3D-Cockpit geeignet, aber keine Android-native Komplettlösung.
Keine Fremd-Grafikassets importiert.

Starzahlen, Bewährungsgrad und Demo-Benchmarks Dritter sind **kein**
Korrektheits- oder Gerätebeweis. Keine Lizenz oder Entwicklerfreigabe
aus bloßen README-Beschreibungen hergeleitet.

## Neuer Handoff-Vertrag

`.github/continuum/teamleader-review-handoff.mjs` produziert
`continuum-teamleader-review-handoff` Version 1 auf Basis von
`lastCompleted` und `localIntegration`. `reviewHandoff` erscheint
zusätzlich im **bestehenden** `teamleader-cli.mjs --status`-JSON.
Keine Hintergrundjobs, Dateiänderungen, Shellkommandos,
Signatur-/Installationsfreigaben oder automatische Reparatur werden durch
eine Statusabfrage ausgelöst.

Begrenzte Aktionen:
- `INSPECT_LOCAL_BUILD_LEASE` bei vorhandenem/fragwürdigem Lock;
- `INVESTIGATE_HISTORICAL_ANSK_BASELINE` bei altem Laborfehler;
- `INSPECT_LAST_UNVERIFIED_REPAIR` bei nicht bewiesener Reparatur;
- `REQUEST_PATCH_REVIEW` bei gemeldeter reparaturseitiger Verifikation;
- `VERIFY_APK_ON_DEVICE` für noch nicht gerätegeprüften APK-Bericht;
- andernfalls `REVIEW_EXISTING_BACKLOG`.

Jedes Paket ist `ADVISORY_ONLY`,
`approvedToExecute=false` und
`approvalRequiredForPublication=true`. Fremde Statusstrings
werden nicht in den Output übernommen; ein alter Beleg behauptet
niemals eine verifizierte Installation. Die ANSK-Kategorien stammen
aus dem bestehenden `ansk-baseline-triage.mjs`.

## Reproduzierbare Prüfungen auf Samsung

~~~bash
node --test .github/continuum/teamleader-review-handoff.test.mjs
node --test .github/continuum/ansk-baseline-triage.test.mjs \
  .github/continuum/ansk-import-lab.test.mjs \
  .github/continuum/local-integration-status.test.mjs
node .github/continuum/teamleader-cli.mjs --status --root .
~~~

Entwicklungsseitig liefen sieben pure Handoff-Verhaltensprüfungen
in einer isolierten V8-Prüfung grün. **Der echte Node-Prozesstest für die
CLI und sämtliche Samsung-Tests dieses neuen GitHub-Commits sind noch offen**,
bis sie auf dem Zielgerät ausgeführt werden.

## Nächster technischer Blocker

Vor weiteren ANSK-Mutationen den **realen Baseline-Fehler**
(`BLOCKED_BASELINE_NOT_GREEN`) anhand des neuen
`failureCategory`-Status eingrenzen. Keine künstliche grünerzeugende
Abkürzung und keine ungeprüfte Veröffentlichung.

**Rollback:** Der neue pure Handoff lässt sich durch Revert seiner
Import-/Statuszeile in `teamleader-cli.mjs` und der beiden neuen Dateien
rückgängig machen. Keiner der Änderungen migriert den bestehenden
Backlog-/Modell-/Gerätestatus.
