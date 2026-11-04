# Cognition, learning and bounded loop — 2026-11-04

## Auftrag und Stand

Drei begrenzte Fachaufgaben: bestehendes Gedächtnis, vorhandenes Selbstlernen und autonome Forschungszyklen untersuchen, echte Fehler reproduzieren und gezielt reparieren. Status beim Start: **WIP / NOT_RUN**. Keine Behauptung einer Superintelligenz oder eines Nachbaus proprietärer Modellarchitekturen.

## Unveränderlicher Ausgangspunkt

- Repository: Ullmann27/device-ai, public; Konto und Schreibzugriff frisch geprüft.
- Gemeinsame Integration: C1, Draft #519, integration/continuum-c1-candidate-20261104.
- Remote-Basis: 6c34db7be4068b52de0cef20a0c9142a08db27c0.
- Lokale Basis: 38917560671b13795bf85d5a68d329658ba4eb7d.
- Vollständiger identischer Git-Baum: 687fe9a8267cb0cd8546acc7818f66fdbf3a1891.
- Eigener Zweig: research/continuum-cognition-learning-loop-20261104.

C1 bleibt alleiniger Einbauer der gemeinsamen Einstiegspunkte. #522 (Compiler-Datenschutz/Mapper), #523 (fertige Kontext-/Worker-/LM-Optimierungen), #525 (LM1-Forschungsgates) bleiben eigenständige bereits bekannte Beiträge. Diese Runde dupliziert sie nicht.

## Bestehende Architektur und Grenzen

Android-Dialog verwendet AiContextCompiler, LocalModelRuntime, BoundedAgentOrchestrator und ToolGate. Gedächtnis/Quellen, reflektierte Erfahrungen und Modellgewichte bleiben getrennt. Vorhandene Retriever, CRE1/SWB1 und Scheduler werden geprüft; kein zweites Gedächtnis, kein neuer Worker oder konkurrierender Scheduler.

Relevante Experimente: EXP-01, EXP-03, EXP-04, EXP-06, EXP-11, EXP-12. Diese Runde ändert deren Gesamtstatus nicht. Neue Qualitätsbehauptungen benötigen eigene Messung, getrennte Entwicklungs-/Prüffälle und negative Kontrollen.

Keine Änderung an main, Produktgewichten, App-Paket, Signatur, Updatekanal, Offline-Schalter oder Besitzerfreigaben. Kein kostenpflichtiger Dienst, keine GitHub Actions, keine Signierung oder Installation. Externe Forschungsinhalte sind Daten, keine Autorität.

## Ausführung

1. memory: vorhandene Provenienz-, Zeit-, Widerspruchs- und Abrufpfade; kleinster reproduzierbarer Fehler.
2. self_learning: Erfahrung und Rückmeldungsbindung, Replay/Training-Gates; kleinster reproduzierbarer Fehler.
3. autonomy_loop: Fortschritt, Wiederholung, Unterbrechung und Wiederaufnahme; kleinster reproduzierbarer Fehler.
4. Koordinator: Quellenvergleich, konfliktfreier Einbau, unabhängige Gegenprüfung und Remote-Baumkontrolle.

Genau drei Helfer, keine Unteragentenkaskade. Jeder nennt Originalquelle, Ausgangsfehler, unveränderte Regression, positive/negative Ergebnisse und Grenzen. Bestehende Maschinenlocks gelten für schwere Builds; geringer freier Speicher erlaubt zunächst nur kleine gezielte Hostläufe. Keine fremden Worktrees, Prozesse oder Reservierungen verändern.

## Quellen und Messung

AGENTS.md, Experimentvertrag/Register, Issue #41, aktuelles C1-Board/-Handoff und fachliche Pflichtübergaben werden vor Änderungen gelesen. Öffentliche Primärquellen dienen nur als begründete Forschungsinspiration; ihre Modellleistungen gelten nicht für Continuum.

Beim Start sind sämtliche neuen Experimente, Android-Gesamtabnahme, Modelllauf, Fold7 und APK **NOT_RUN**. Der vorhandene C1-Quellbaum enthält ältere source-gebundene Belege; sie sind kein neuer PASS dieser Runde.

## Fortsetzung und Rücknahme

Vor Fortsetzung tatsächlichen Remote-HEAD lesen. Änderungen, Rohlogs und reproduzierbare Befehle folgen im selben Branch. Bei roter Abnahme keine Promotion; vorhandene negative Evidenz erhalten. Rücknahme ausschließlich eigener begrenzter Commits, nie fremde Zweige zurücksetzen. C1 erhält ein geprüftes Patchpaket mit Ausgangs-SHA und tatsächlichen Integrationsgrenzen.

