# Operation Continuum Ω — laufender Meilenstein, 2026-11-03

Status: WIP / UNVERIFIED. Wochenkontingent: NOT_VISIBLE. Keine verfügbare unterstützte Quotenabfrage gefunden. Checkpoints nach bedeutenden Schritten, spätestens nach etwa 5–10 Minuten; bei wachsendem Unterbrechungsrisiko Abschluss statt neuer Großexperimente.

## Gepinnte Ausgangslage

- Repository: Ullmann27/device-ai; remote laut API derzeit public (Sichtbarkeit nicht verändert).
- main: 2949893a7c65bdc6a875bdcb28b7a550fd9d7c3a.
- Besitzer-Code39: a9709854f48d2fd6589f9ce5ea97f6ec3be0d233 (Nutzerangabe, kein neuer Gerätecheck).
- #375: 68a34bd9cb0660a97f7a4262b0a7612f31c929cc.
- #388: c986aa5ac771b13c886bfb880cf906454775d898.
- #390 / gemeinsame Entwicklungsbasis: b4f61ae9765e26dd401db928b76fa9f233c0742a.
- Chief-Branch: codex/team-chief/omega-20261103, lokaler Worktree /workspace/continuum-chief.
- #384–387 bleiben eigene Forschungszweige; kein blindes Zusammenführen. #378 enthält laut aktueller PR-Metadaten nur Vorarbeit. #389 ist Team-Bootstrap, kein Ausführungsnachweis.

## Autorisierter Umfang und Designentscheidung

Der aktuelle Masterauftrag autorisiert autonome isolierte Entwicklung, Tests, Commit/Push und Draft-PRs sowie echte Spezialisten. Historische v31-Forschungsstopps sind durch Code39 und den aktuellen Auftrag überholt (siehe vorhandene RELEASE-STOP-RECONCILIATION). Keine erneute Designfreigabe für diese Arbeit erforderlich. Keine Freigabe für main-Merge, produktiven Release, Signierung, Geräteinstallation oder Änderung realer Besitzerinformationen.

Vorhandene Pipeline erweitern: AgentActivity → AiContextCompiler / ConversationContext → BoundedAgentOrchestrator → ToolGate → verifizierter Nachzustand. Kein zweiter Denk-, Sprach-, Gedächtnis- oder Task-Kern. Gegenüber blindem PR-Gesamtmerge und kompletter Neuarchitektur werden eng abgegrenzte, reproduzierbare Reparaturen auf #390 bevorzugt.

## Aufgaben und exklusive Schreibzuständigkeit

1. Intelligence: Kontext-/Follow-up-/Retention-Code und zugehörige Tests nach konkreter Pfadmeldung. Keine Persistenz, Activity oder CCC1.
2. Memory: ContinuumLearningExperience.kt, neue LEX3-Speicherdateien und zugehörige Store-Tests. Sichere Migration nur nach reproduzierbarer Eignungsprüfung. Keine Activity ohne Chief-Freigabe.
3. CCC1: konkrete .github/continuum/ccc1-* Dateien und Tests nach Pfadmeldung. Kein Kotlin-Memory oder Scheduler-Neubau.
4. Android/Avatar: bestehende Avatar-Verträge, native Renderer/View, zugehörige Tests. AgentActivity nur nach exklusiver Chief-Freigabe. Keine neuen Modell-/TTS-Kerne oder erfundenen Assets.
5. Quality: unabhängige Baseline, Buildumgebung und Abnahme. Keine Featuredateien ändern; neue Prüffälle separat. Keine selbst attestierten Erfolge akzeptieren.
6. Chief: Inventur, wissenschaftlicher Abgleich, Abhängigkeiten, Integration geprüfter Commits, gemeinsame Dokumentation. Keine Konkurrenzschreibzugriffe.

Alle Lanes erhalten eigene codex/team-*/omega-20261103 Branches und /workspace/continuum-* Worktrees. Tatsächliche Agenten-IDs folgen nach Start; dies ist noch kein Startnachweis.

## Erfolgskriterien des ersten Meilensteins

- Ausgangs-SHAs und vorhandene Architektur mit tatsächlichen Aufrufpfaden erfassen.
- Mindestens einen relevanten Gehirn-/Gedächtnisfehler reproduzieren, minimal reparieren, unveränderte Regression erneut ausführen.
- LEX3 nur mit Legacy-Erhalt, Integritäts-/Crash-/Negativtests und dokumentierten Grenzen integrieren; keine automatische Besitzerfakten-Promotion.
- Avatar nur an echte vorhandene Events, ohne vorgetäuschte Lern-/Denkaktivität.
- Unabhängiger Reviewer prüft finalen Quellstand; Logs nennen SHA, Befehl, Umgebung, Resultat und Auslassungen.
- Hosttests, echtes lokales Gemma, APK-Build und Fold7-Abnahme getrennt ausweisen. Keine Intelligenzsteigerung aus reinen Vertrags-/Fixturetests ableiten.

## Vorläufiger Backlog

P0: ausführbare Baseline und Regressionen von #388/#390. P1: Kontextkorrektheit und 256-Erfahrungsgrenze. P1: CCC1 unabhängige Belege/Wiederanlauf. P2 parallel: natives ereignisgebundenes Avatar-Rendering. Danach Langzeit-/Zeitachsen-Evaluation und kontrollierte neuro-symbolische Experimente auf bestehender Infrastruktur.

## Rückfall / Wiederaufnahme

Kein bestehender Branch wird zurückgesetzt. Jede Lane bleibt reversibel isoliert; gemeinsame Integration erst nach Prüfung. Bei Abbruch zuerst remote HEADs, dieses Handoff und Lane-Berichte lesen. Offene Checks bleiben NOT_RUN. Kein APK- oder Releaseversprechen.
