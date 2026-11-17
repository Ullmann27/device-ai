# Continuum – rekonstruierte Weiterarbeitsübergabe für ChatGPT Plus
Stand: 21.10.2026. Dies ist eine **rekonstruierte Übergabe aus dem gesicherten GitHub-Stand und den vom Besitzer gezeigten Screenshots**, nicht eine behauptete wortgetreue Wiederherstellung der lokalen Cloud-Datei von Sol 6.1.

## Unveränderliche Anker

Repository `Ullmann27/device-ai`. Unberührter Quell-/Experimentbranch von Astra: `astra/continuum-experiment-charter-20261019`, PR #42, abgeleiteter Head `1c255d25c1597b925ddeb14e5acad717134eaa33`. Qualitätsbranch von Sol: `sol/continuum-quality-20261021`, Draft-PR #43, Zielbranch PR #42. `main` darf nicht beschrieben oder automatisch gemergt werden. Aktuellen PR-Head vor jeder Folgearbeit neu aus GitHub lesen.

Primäres Produktziel: **Auf dem Smartphone selbst** Code untersuchen, lokal reparieren, mit Android/Termux kompilieren, signieren und **erst nach Eigentümerfreigabe** installieren. Weder PC-Pflicht noch Beweisbehauptungen über ungetestetes Android-Verhalten. Keine kostenpflichtige fremde LLM-API als Kernvoraussetzung.

Geltende Regeln: `AGENTS.md`, `docs/CONTINUUM-EXPERIMENTVERTRAG.md`, `docs/CONTINUUM-EXPERIMENTE.json`, GitHub-Issue #41. Besonders betroffen: EXP-00, EXP-01, EXP-02, EXP-04, EXP-06, EXP-08, EXP-12.

## Gesicherte Sol-Ergebnisse auf PR #43

### Lokaler Worker

Siehe `docs/CONTINUUM-WORKER-AUDIT-20261021.md`.

1. Untersuchungsergebnisse werden atomar zusammen mit dem Prüfentscheid persistiert; ein harter Abbruch nach der Submission kann den Auftrag nicht mehr dauerhaft in `VERIFYING` festsetzen.
2. Abgelaufene `INVESTIGATE`-Claims werden vom Scheduler und direkten Investigator über die kanonische `expire`-Operation und unter Beachtung der Versuchslimits wieder auswählbar.
3. Investigator-Backlog und Ergebnisberichte dürfen weder direkt noch über bestehende Symlink-Aliase in den Quellcheckout geschrieben werden.

Beleg nach dem Bericht: 97/97 fokussierte Node-22-/Node-24-Prüfungen bestanden, davon neun neu; diese Prüfungen sind noch kein echter Samsung-/Doze-/Termux-Ausführungstest. Keine automatische Merge-Berechtigung.

### Forschungs-/CRE1-Baseline

Siehe `docs/CONTINUUM-RESEARCH-BASELINE-AUDIT-20261021.md`.

Vier konkrete Baselinefehler wurden anhand roter Tests lokalisiert und repariert: near-duplicate Negativfixtures; zu früher Audit-/Train-Split-Zuschnitt; Rechteschema der Originalquelle; EC-P-256-KeyObject-Normalisierung. Bytegleicher Checkpoint-Rollback wurde mit einer tatsächlich abgelehnten Regression verpflichtend getestet. 25/25 fokussierte Tests auf Node 22 nach Sols Audit. Bestehende produktive SWB1-/CDX1-Modelle und Owner-Fakten wurden nicht überschrieben.

## Ursprüngliche Ausgangslage / keine beschönigten Lernwerte

Sol meldete auf dem unveränderten Ausgangs-HEAD für Node 22: 717 Tests, 705 grün, sechs rot, sechs übersprungen. Nach seinen ersten beiden Commits waren auch die beiden übrigen Baselinebereiche `docs/retrieval-benchmark.json` und `.github/continuum/m3-preview-release.test.mjs` bearbeitet, aber ein vollständiger grüner Lauf und ein Qualitätsabschlussbericht fehlten.

Der auf dem Samsung am 21.10.2026 um 03:37 fotografierte Learning Core zeigte 14.400 Gewichtsupdates, 39 Audits, 63 Experiences und 24 unbestätigte Quellen. Holdout: Intent 4/8, Semantik 3/6; Semantikverlust 0,7271. Dies ist **Trainingsaktivität, kein Beweis höherer Modellqualität**. Der separate historische Offline-Jobstatus `DEFERRED_BATTERY_LOW` belegt ohne neuen Lauf keine gegenwärtige Akkusperre.

## Wiederaufnahme in dieser Plus-Sitzung

1. GitHub PR #43 und `AGENTS.md`/Experimentvertrag/Issue #41 geprüft. Die in Sols Cloud-Screenshot benannte Original-Übergabedatei war nicht gepusht (GitHub 404). Dieser rekonstruierte Auftrag ersetzt keine lokalen, nicht erhaltenen Testartefakte.
2. Auf Sols unverändertem Branch ein **vollständiges** Node-/Backend-Gate vor den vorhandenen, echten Android-Gradle-Build in `scripts/render-mentor-preview.sh` eingefügt. Render nutzt denselben bereits autorisierten isolierten signierten Preview-Buildservice, ohne `main` zu verändern.
3. Im ersten vollständigen Lauf auf Render / Node 26: 728 Tests, 721 bestanden, 1 fehlgeschlagen, 6 bewusst übersprungen. Der einzige rote Test verlangte `# pass 2` und erkannte den neueren offiziellen Node-Test-Reporter `ℹ pass 2` nicht. Die Änderung in `.github/continuum/node-contract-synthesizer.test.mjs` akzeptiert ausschließlich diese beiden festen Zusammenfassungsformate, verlangt aber weiterhin **exakt zwei grüne und null rote** Tests. Kein Test wurde übersprungen oder deaktiviert.
4. Ein erneuter kompletter Render-Node-/Backend-/Android-Build wurde gestartet; **den endgültigen Status ausschließlich aus dessen Abschlusslogs entnehmen**. Dies ist bis zum expliziten Erfolgsbeleg kein bestandenes Gate.

## Noch zu schließende Risiken / konkrete Weiterarbeit

- Einen **vollständig grünen** Gesamt-Node-Testlauf mit genauer Testzahl, Node-Version, Übersprüngen und Commitbezug sichern; danach Backend-Tests sowie `:app:testDebugUnitTest`, `:app:lintDebug`, `:app:assembleDebug` und `apksigner verify` für **denselben Quellbaum** mit Exitcode belegen.
- Zusätzliche Sol-Arbeiten aus seiner nicht erreichbaren Cloud-Sitzung (Android-Scheduler-/Learning-Core-/Monitor-Audits und `CONTINUUM-QUALITY-MILESTONE-20261021.md`) dürfen nicht als implementiert gelten. Aus dem GitHub-PR sind derzeit ausschließlich die eingecheckten Änderungen nachgewiesen.
- Den aktiven Monitorzustand gegen einen realen Android-Hintergrundlauf, Prozessabbruch/Neustart und wiederkehrendes `BATTERY_LOW` auf dem Samsung überprüfen. Eine registrierte 15-Minuten-Jobperiode garantiert keine solche tatsächliche Startfrequenz.
- Für den lokalen SWB1-Lernkern zuerst reproduzierbare, **vorher versiegelte** Holdouts, unabhängige neue Aufgaben und Rollbackverhalten prüfen. Erst positive unabhängige Verbesserungen rechtfertigen eine Änderung der Trainingskonfiguration.
- Bei Änderungen an der APK dieselbe Paketkennung, denselben Signaturschlüssel und höhere Preview-versionCodes verwenden. Kein stiller Install, kein Deinstallieren der alten App oder Löschen des privaten Lernstands.
- `main` und PR #42 nicht verändern, PR #43 Draft halten, unabhängige Gesamtprüfung und Samsung-Abnahme getrennt kennzeichnen.

## Rücksetzweg

Sols Qualitätsbranch bleibt von PR #42 isoliert. Ein misslungener Qualitätstest/Render-Build darf die zuletzt live veröffentlichte, korrekt signierte Preview-APK nicht ersetzen. Änderungen an `scripts/render-mentor-preview.sh` (vollständiges Gesamt-Gate) und `node-contract-synthesizer.test.mjs` (Runnerformat) können im Qualitätsbranch jeweils als einzelne Commits zurückgenommen werden, ohne historische Worker- oder CRE1-Änderungen zu vernichten.
