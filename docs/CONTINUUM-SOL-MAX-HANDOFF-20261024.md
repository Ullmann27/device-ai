# Continuum Sol Max – laufende Fortsetzung, 24.10.2026

Status: IN_PROGRESS. Diese Datei wird vor Abschluss mit exakten Test- und End-SHAs ersetzt.

- Verbindliche Remote-Basis: `opus/continuum-sol-handoff-20261024`, tatsächlicher Remote-HEAD und lokaler Start `d0f0892e156b9bcfa6a9a80d6fcd515173a62565` (24.10.2026 erneut per ls-remote geprüft).
- Neuer dauerhaft zugänglicher Fortsetzungsbranch: `sol/continuum-max-20261024`. Neue isolierte Worktree; keine fremden Zweige überschrieben, kein main-Merge.
- Aktueller Dokument-Commit: `git log -1 --format=%H -- docs/CONTINUUM-SOL-MAX-HANDOFF-20261024.md`.
- AGENTS.md, Experimentvertrag/Register, Issue #41 und Opus-Übergabe gelesen. EXP-00/08/09 sichere Bestandsintegration; EXP-14 bleibt OFF/SHADOW. Kein neues Sprach-/Lernexperiment.
- Basis enthält die gesicherte Sol-Reparatur144ab574 sowie Opus01cf6f80/0e51aff0/4f6cc98f. Frühere lokale Weiterarbeit wird inhaltlich verglichen; keine bereits integrierte Entwicklung neu implementiert.
- Laufende unabhängige Prüfungen: M5-/Builder-Lifecycle mit echten Android-SDK/Robolectric-Widgets, expliziter Baseline-Sitzungsreset, Opus-Regressions-/Mutationsprüfung, Release-Quellreview. Alte Arbeitsstände/Testlogs sind keine Endbelege dieser neuen Basis.
- Toolchain lokal wirklich vorhanden: JDK17, Kotlin2.4, Gradle8.11.1, AGP8.10, Android SDK36/build-tools35+36. Kein SDK_UNAVAILABLE, keine Kompilierstubs nötig.
- APK0.3.18/code19 nicht signiert/gebaut. Owner-Signing BLOCKED: unveränderte22-Pin-Freigabeliste weicht in11 Dateien ab,10 weitere APK-Quellen müssen ausdrücklich geprüft/freigegeben werden. Keine Freigabevariable oder Fingerabdrücke geändert, keine Render-Deploymentmutation.
- Vorhandener Chat→M5-Entwurf→kanonischer M4-Plan→Kontrolldialog→Builder→Termux/CCC1-Rückkanal bleibt die einzige Kette. Fold7-Geräteabnahme fehlt.

Nächster Schritt bei Unterbrechung: tatsächlichen Remote-HEAD dieses neuen Branches lesen, Arbeitsbaum/Testprozesse prüfen, diese Datei und Opus-Übergabe lesen; offene Regressionen abschließen, vollständige Checks auf gemeinsamem Quell-SHA ausführen, technischen Quellenfreigabebericht und0.3.17-Rollback verifizieren. Keine Signierung ohne ausdrückliche Besitzerfreigabe der konkreten Quelländerungen.

Rollback ausschließlich eigene neue Commits revertieren. Besitzerfakten, Gewichte, installierte Pakete und Signierschlüssel bleiben unangetastet.
