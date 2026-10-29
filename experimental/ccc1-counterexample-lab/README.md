# Continuum – acht sichere Meilensteine nach PR #242

**Startbasis:** PR #242, finaler HEAD `7924ba0793963dd84eef61517359a743071829bf`; ursprünglicher geprüfter App-Code `e06e17a4e8ba12a1dce3d5308e5eb455cda30b6d`.

Diese Fortsetzung ist ein **isolierter Forschungs- und Abnahmezweig**, kein Ersatz für das produktive Gehirn. Kein Merge nach `main`, kein neuer Worker, keine Besitzer-APK, keine Signatur, keine Veränderung von Gemma-Gewichten, Gerätedaten oder CCC1-Lernberechtigungen.

## Acht Meilensteine mit echten Freigabegrenzen

| Stufe | Ziel | Stand in diesem Zweig | Abschlussbedingung |
|---|---|---|---|
| 1 | PR-#242-Quelle einfrieren | Erfüllt: Branch auf exaktem finalen HEAD erzeugt | Keine Produktionsänderung |
| 2 | Host-QA-Belege maschinenlesbar prüfen | Implementiert: `scripts/continuum-host-evidence-gate.mjs` und Negativtests | Exakte SHA, 1.268 Android-, 390 Worker-, 226 Node- und 10 Python-PASS, offene Geräteschritte ausdrücklich erhalten |
| 3 | Fold7-Quellcode- und Lebenszyklusprüfung | **BLOCKIERT:** kein Fold7-Zugriff | Originales gerätespezifisches QA-Skript auf sauberem Checkout und 30-minütige Geräteabnahme |
| 4 | Native Gemma- und Besitzer-APK-Abnahme | **BLOCKIERT:** kein Geräte-/Signaturbeweis | Echte Offline-Antworten, Faltzustände, Abbruch, Neustart, Besitzerfreigabe, keine Datenverluste |
| 5 | Deterministisches Gegenbeispiel-Labor | Implementiert: reine JS-Fixsturen mit zwei konkurrierenden Reparaturen | Getrennte Trainings-/Holdout-Dateien, vorab fixierte Aufgabenidentität |
| 6 | Reale Gegenprüfungen in getrennten Prozessen | Experimenteller Runner implementiert; Repo-Gesamtausführung bleibt gesondert zu bestätigen | Fester SHA, vollständige Rohlogs, keine unbekannten Exitcodes als Erfolg |
| 7 | CCC1-Ranking gegen sichere Abstinenz vergleichen | Runner **liest** bestehende `rankCandidates`/`repairExperienceKey`; Forschungsregel ist SHADOW | Nur Aufgaben-Paare vergleichen; falsche Anwendung und Abstinenzen getrennt ausweisen |
| 8 | Unabhängiger Review und kontrollierter Folgeschritt | **OFFEN** | Ergebnis auf bisher unbekannten echten Fehlern, unabhängige Tests, Eigentümerabnahme vor jeder Aktivierung |

## Ausführung – ausschließlich Forschungsdaten

```sh
node --test experimental/ccc1-counterexample-lab/policy.test.mjs scripts/continuum-host-evidence-gate.test.mjs
node experimental/ccc1-counterexample-lab/run.mjs /tmp/continuum-counterexample-run-01
node scripts/continuum-host-evidence-gate.mjs e06e17a4e8ba12a1dce3d5308e5eb455cda30b6d docs/evidence/system-integration-20261029
```

Der Forschungsrunner verlangt einen sauberen, commitfesten Checkout und einen **noch nicht vorhandenen, absoluten Ausgabeordner außerhalb des Repositorys**. Jede synthetische Kandidatenprüfung startet einen frischen Node-Kindprozess in einem temporären Verzeichnis. **Das ist keine OS-Sandbox und keine extern authentifizierte unabhängige Verifikation.** Nur fest vorgegebene, harmlose Testdateien werden ausgeführt; es gibt keine Modell- oder Netzwerkaufrufe, keine Dateiedits im Produktquellbaum und keine Berechtigungsänderung.

Die Trainingsgruppe umfasst 18 ausgewählte Fälle, der fest versiegelte Holdout 24 Fälle. Die Quellenkontexte VALUE/CALL plus exportierte Hilfsfunktion werden beobachtet. Unter gleichartigen Fällen mit widersprüchlichen richtigen Strategien muss die Forschungsregel **ABSTAIN** zurückgeben. Eine geringere Fehlerquote durch mehr Abstinenz ist ausdrücklich kein allgemeiner Reparatur- oder Intelligenzgewinn.

Die SHA-256-Versiegelung bindet **nur diesen eingecheckten synthetischen Aufgabensatz**, nicht eine externe Blindstudie. Die Varianten teilen sich Strukturvorlagen; die Fälle sind keine neuen realen Softwareprojekte. Gemessen wird nur die Resultatquote der festgelegten Einzeltests sowie die Abstinenzquote, nicht Geschwindigkeit oder autonome generative Fähigkeiten.

Das Host-QA-Gate prüft die Konsistenz der in PR #242 abgelegten **berichteten** Rohdaten. Sein Erfolg macht weder eine neue Branch-Kompilierung noch einen Geräte-/Release-Erfolg wahr. Bei inhaltlich konsistenten Host-Belegen liefert die CLI absichtlich **Exitcode 2** (Gerätefreigabe ausstehend), bei Inkonsistenzen Exitcode 1.

## Unverhandelbare Sicherheitsregeln

- `main` und der Quellstand von PR #242 bleiben unverändert.
- Keine Änderungen an `recordSession`, produktiver Rangfolge, CC1/CCC1-Worker, Release-Signierung, Installations- oder Updatepfaden.
- Ein Gegenbeispiel ist ein Forschungsbefund, nie automatisch eine positive Reparaturerfahrung.
- Fehlende, widersprüchliche oder nicht unabhängig geprüfte Belege ergeben **ABSTAIN/NOT_RUN** und niemals Erfolg.
- Keine stillschweigenden neuen Prozente oder behaupteten Fold7-/Gemma-Gewinne.

## Nächste reale Lücke

Der nächste aktive Schritt benötigt einen bereits eingerichteten Fold7-Checkout und den in PR #242 dokumentierten unsignierten QA-Befehl. Erst dessen Rohbelege und eine gesonderte Besitzerfreigabe ermöglichen die weitere Geräte-/APK-Abnahme. Bis dahin bleibt dieses Labor unabhängig von der Produktions-App.
