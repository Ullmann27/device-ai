# Continuum · Weiterarbeitsplan, Forschung, Experimente und die strategische Jarvis-Herausforderung

**Stand 30.10.2026, Übergabe von Opus 5.5. Jede KI und jeder Mensch kann hier übernehmen.**

## 1. Wo alles liegt (Stapel, keiner gemergt)

`main` (`190f5eab`) ← #237 ← #238 `a3fdc179` ← #241 `39772c9b` ← #242 `7924ba07` ← #243 `ebba7e16` ← **#244** `0e672333` (M1–M8) ← **#245** `opus/continuum-m9-m16-20261030` (M9–M16, Abschluss `732cb740` und später)
Separat: #236 (Updater, eigene Sicherheitsabnahme), #240 (ARGON-4-Gutachten, PDF), #239 (Alternative zu #238, **nicht mergen**).

Berichte: `docs/research/M1-M8-ABSCHLUSSBERICHT-20261030.md`, `docs/research/M9-M16-ABSCHLUSSBERICHT-20261030.md`.

## 2. Unverhandelbare Regeln

- Neue Fähigkeiten laufen erst **SHADOW**. Eine Aktivierung braucht eine Fold7-Messung **und** die Freigabe des Besitzers.
- Ein Experiment braucht: Vorregistrierung → Siegel (SHA-256) → Commit **vor** dem Lauf → genau **ein** Holdout-Lauf → das Ergebnis wird unverändert berichtet, auch wenn es negativ ist.
- Kein Merge nach `main`, kein Force-Push, keine Schlüssel/P12, keine Modellgewichte, keine privaten Daten im Repo, keine kostenpflichtigen KI-APIs.
- Nach jedem geprüften Schritt: committen, pushen, SHA zurückprüfen.

## 3. Angestoßene und vorbereitete Experimente (fortsetzbar ohne Vorwissen)

| ID | Status | So fortsetzen | Erfolgskriterium |
|---|---|---|---|
| **X1 · M13 Gegenbeispiel-Mining** | **erledigt** (1 Lauf auf `732cb740`, [`m13/M13-RESULT-20261030.md`](m13/M13-RESULT-20261030.md)): 17 Hypothesen, 1 CONFIRMED (nicht Bonferroni-fest), 1 REFUTED, 15 INCONCLUSIVE | nicht wiederholen. Nächster Schritt ist eine Nachmessung von `H-PREFER-LOAD_FAILURE-NEG:REMOVE` auf dem X4-Datensatz, vorab versiegelt | wie vorher; zusätzlich Bonferroni über alle Hypothesen eines Laufs vorregistrieren |
| **X2 · Fold7-Abnahme** | vorbereitet (`docs/research/m6/M6-ABNAHMEPFAD-20261030.md`) | auf dem Fold7: unsigniertes QA-Skript auf exaktem Commit, dann das 12-Fälle-Protokoll | 12/12 erledigt, Flag-Rate von Aussagenprüfer und Zeitlinie gegen Besitzer-Feedback (`CONFIRMED/CORRECTED`) gemessen |
| **X3 · Generative Reparatur** | offen. Grenze gemessen: 0/4 echte historische Fixes im Vorlagen-Vokabular | auf dem PC ein lokales Code-Modell als zusätzlichen Kandidatengenerator an `experimental/repair-transfer-lab` anschließen (neuer Arm `llm`). Gleiche versiegelte Aufgaben **plus** echte Fixes. | mehr exakt gelöste Aufgaben als `adaptive` (20/44) bei ≤ 1 falschem Erfolg. Historische Fixes > 0/4. |
| **X4 · Echte Fehlersammlung** | offen | aus dem CCC1-Ledger und der Git-Historie ≥ 30 reproduzierbare echte Fehler mit eigenem Test sammeln (`historical.mjs` erweitern), **vor** dem Lauf versiegeln | Datensatz versiegelt und von einer zweiten KI gegengeprüft |
| **X5 · Router-Nachprüfung** | offen (Rest aus M12) | Bei `CLAIM_AUDIT_UNSUPPORTED/CONFLICT` schlägt `ComputeAllocationPolicy` einen einzigen Verifikationspass vor, nur als Trace-Signal | nur bei Fold7-Messung: weniger falsche Details pro Antwort, ≤ 1 Zusatzaufruf |
| **X6 · Fremd geschriebener Test** | offen | eine **andere** KI schreibt TEST6/7 (Dialog + Gedächtnis, ≥ 150 Fälle, deutsch) und versiegelt ihn, bevor Opus-Code darauf läuft | F1-Kern und EWM2+ halten ihre Holdout-Werte (≥ 80 %, 0 falsche Bindungen) |
| **X7 · Teure Verifikation** | offen | M9-Arme mit Gradle/Kotlin-Tests statt Node (auf dem PC) | Erfahrung spart dort messbar Minuten, nicht nur Läufe |
| **X8 · Abhängigkeitsplanung** | offen (Rest aus M14) | im bestehenden Teamleader: Aufträge mit `dependsOn`, Fehlerbudget pro Auftrag, Wiederanlauf-Test mit `SIGKILL` | keine Doppelarbeit, keine verletzte Abhängigkeit in 100 Chaos-Läufen |

## 4. Offene Forschungsfragen (nach erwartetem Nutzen sortiert)

1. Wie oft schlägt der Aussagenprüfer bei **echten** Gemma-Antworten fehl alarmierend an? (Bisher sind nur konstruierte Fälle bekannt.)
2. Warum hat die Selbstkorrektur-Heuristik geschadet (16 statt 19)? Erste Vermutung: Eine Änderung, die die Menge roter Tests verändert, ist ein schwaches Signal. Prüfen: Abstand zum erwarteten Wert als Signal.
3. Lohnt Erfahrung erst ab einer bestimmten Kandidatenzahl (Schwelle zwischen 2 und 38)? Dosis-Wirkungs-Kurve messen.
4. Kann die Zeitlinie (bis/seit/Korrektur) Besitzerfakten vorschlagen, ohne sie automatisch zu ändern? Nur als Prüfvorschlag in der Besitzer-Inbox.

## 5. Die ultimative strategische Jarvis-Herausforderung

> **„Der verifizierte Selbstverbesserungs-Kreislauf auf dem eigenen Gerät“**
> Jarvis soll innerhalb von 30 Tagen **allein auf dem Fold7 (plus optional dem eigenen PC), ohne Cloud-KI**, nachweisbar besser werden. Ein Mensch schreibt keinen einzigen Patch.

Der Kreislauf, der dafür geschlossen werden muss:
1. Ein Gespräch des Besitzers erzeugt Signale: Aussagenprüfer, Zeitlinie, F1-Rückfragen, Besitzer-Korrekturen.
2. Diese werden zu **anonymisierten Fehlerfällen** (keine Inhalte, nur Struktur und Testfall).
3. Daraus entsteht ein CCC1-Auftrag.
4. Ein lokales Modell oder ein Vorlagen-Generator liefert Kandidaten.
5. Unabhängige Verifikation erfolgt im Zweit-Worktree mit verdeckten Tests.
6. Ergebnis ist ein Draft-PR, den der Besitzer freigibt.
7. Danach folgt die Messung auf einem **fremd geschriebenen, versiegelten** Test.

**Bestanden** ist die Herausforderung nur, wenn alle vier Punkte erfüllt sind:
- ≥ 3 verifizierte, vom Besitzer angenommene Verbesserungen,
- signifikant weniger falsche Details pro Antwort auf dem versiegelten Test (McNemar, p < 0,05),
- 0 Besitzerfakten ohne Zustimmung verändert,
- 0 Rechte-Erweiterungen.

**Kill-Switch:** Jede falsche Erfolgsmeldung, jeder Datenexport ohne Freigabe und jeder Schreibzugriff auf `main` beendet den Versuch sofort.

**Warum das die richtige Herausforderung ist:** Alle Bausteine existieren inzwischen einzeln (Gehirn, Prüfer, Teamleader, CCC1, Verifikation, Erfahrung). Gemessen ist, dass Erfahrung hilft (M9) und dass die Prüfer greifen (M11/M12). Es fehlt das **eine durchgehende, gemessene Kreislauf-Experiment**.

## 6. Übernahme in 5 Befehlen

```sh
git fetch origin opus/continuum-m9-m16-20261030 && git switch -c <dein-zweig> origin/opus/continuum-m9-m16-20261030
cat docs/research/CONTINUUM-WEITERARBEIT-PLAN-20261030.md docs/research/M9-M16-ABSCHLUSSBERICHT-20261030.md
node --test .github/continuum/m9-m16-wiring.test.mjs experimental/repair-transfer-lab/*.test.mjs   # Schnellprüfung
# Kotlin ohne Gradle: scripts/android-kotlin-unit-check.sh (Erwartung 1282/1282)
# dann das erste offene X aus Abschnitt 3 nehmen, Draft-PR auf #245 eröffnen
```
