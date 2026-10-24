# CTX2 – Taxonomie der bekannten offenen TEST2-Fehler

Stand: 24.10.2026. EXP-14 · `RESEARCH_SHADOW` · reine Entwicklungsdiagnose; keine Produktänderung.

Die **27 falschen Antworten sind vollständig erfasst**. Sie verteilen sich auf zwei Mechanismen: **13 fehlende Gegenstandsreferenten bei Themenwechseln** und **14 falsche Pronomenauflösungen durch zu breite Geschlechtskandidaten bei der Rückkehr zur Person**. Alle Fehler treten im dritten Dialogzug auf. Es gibt hier keinen Fehler der Zeitauflösung.

## Datengrundlage und Abdeckung

Quelle ist der bereits geöffnete, reparierte B2-Bericht [docs/research/ctx2-open-b2-report-20261024.json](ctx2-open-b2-report-20261024.json) mit SHA-256 `be7abf235f5f9539dfbb3c5aa5183c1503faae7fab942f90b74ad4829250ae31`. Seine Summen sind 885 `CORRECT`, 27 `WRONG`, 0 `MISSED`, 8 `UNNEEDED_CLARIFY`, 80 `CLARIFIED`, 80 `ABSTAINED` bei 1080 Zügen. `failures[]` enthält alle 35 Fehlerdatensätze: genau 27 `WRONG` und 8 unnötige Rückfragen. Eine Rekonstruktion fehlender Fehler war daher nicht nötig. Die acht unnötigen Rückfragen gehören nicht zu den nachfolgenden 27 Fällen.

Der Eingangsbericht besitzt kein Commitfeld; seine ursprüngliche Codezuordnung lässt sich deshalb nicht allein aus dieser Datei beweisen. Zur mechanistischen Kontrolle wurden ausschließlich die 27 öffentlichen Fehlerdialoge aus `Ctx2Corpus` diagnostisch nachvollzogen, mit dem unveränderten reparierten B2-Kontext aus Commit `44c4d743e64f41cde2f6489e2e34ee7ba03ea012` (`generalized=true`) und demselben Gedächtniskern/SWB1-Testartefakt. Bei **27/27** stimmen umgeschriebene Frage und erster Beleg exakt mit dem Eingangsbericht überein. Dies ist eine Wiederholung bekannter Fehler, kein neuer TEST-Lauf mit Blindcharakter.

- TEST2-Generator: Seed `20261104`, 40 Dialoge je Familie; kanonischer Korpus-SHA-256 `2a7242bf6a93a2366fb36275f431cfeeba8ff7f651c3033e8617ca73c4c9222a` wurde geprüft.
- Eingefrorene Kontextdatei: SHA-256 `84008954049406050b31b05afca4abea38ba1a3fddda0bc7a31f45a1dfc61a34`; am untersuchten Arbeitsstand `5bf8a6d4d1f954de97a4faec296eae3f529201fb` bytegleich.
- Eigenständiger Kotlin-Diagnoselauf, keine Gradle-/SDK-Ausführung. Die vollständigen ursprünglichen Ergebnisberichte wurden nicht verändert.
- Versiegelter TEST3 wurde weder gelesen noch ausgeführt. Diese Datei enthält ausschließlich offene TEST2-Beispiele.

## Reproduktion

Der ausführbare [Diagnosepfad samt Anleitung](../evidence/sol-m5-20261024/diagnostics/README.md) extrahiert nur die benötigten reinen JVM-Quellen und das öffentliche Modellfixture aus dem fest gebundenen B2-Commit. Er benötigt Git, Python3, JDK17+ und `kotlinc`; Gradle, Android-SDK, vorgebaute Appklassen und Netzwerk sind nicht erforderlich. Er prüft Rohbericht-/Korpus-/Quellhash und reproduziert alle 27 offenen Fehler mit anschließendem Abgleich der Taxonomie.

## Zählweise

Die zwei primären Ursachenklassen schließen einander aus und summieren sich zu 27. Die gewünschten sechs Analyseachsen überlappen; ihre Zahlen dürfen nicht addiert werden.

| Primäre Ursache | Familie | Anzahl | Anteil der 27 WRONG |
|---|---|---:|---:|
| Gegenstand fehlt im Entitätsregister; früherer Personenfokus bleibt bestehen | SWITCH | 13 | 48,1 % |
| Weibliches Haustier als M/F registriert; jüngste Erwähnung gewinnt bei „er“ | RETURN | 14 | 51,9 % |

| Analyseachse | Betroffene WRONG | Bedeutung |
|---|---:|---|
| entity | 27 | Der ausgewählte Referent ist falsch: 13 fehlende Gegenstände, 14 zu breit typisierte Haustiere. |
| pronoun | 27 | 20 Fragen mit „er“, 7 mit „sie“; alle falsch aufgelöst. |
| time | 0 | Keine Kalender-/Zeitauflösungsursache. Altersfragen und das Wort „jetzt“ allein sind keine solche Ursache. |
| source | 27 als Folge; 0 eigenständige Autorisierungsverstöße beobachtet | Jeder erste Beleg ist außerhalb Gold, aber innerhalb der aktuell bereitgestellten bestätigten Dialogfakten. |
| rewriting | 27 als Folge | Das Pronomen wird jeweils durch den falschen Namen ersetzt; kein zusätzlicher Tippfehler-/Ersetzungsdefekt nachgewiesen. |
| stale | 13 | Bei SWITCH überlebt der alte Personenfokus den tatsächlichen Themenwechsel. Die 14 RETURN-Fälle bevorzugen dagegen einen frischen, falsch typisierten Kandidaten. |

## Mechanismus und Belege

**SWITCH (13):** Die Objektfakten lauten „Unser/Unsere …“, etwa „Unsere Kaffeemaschine hat die Farbe Silber.“ Der Gegenstand fehlt im B2-Register. Der zweite Dialogzug erhält trotzdem einen korrekten Beleg vom vorhandenen Retrieval. `observe()` kann diesen Beleg keinem registrierten Gegenstand zuordnen; der frühere Personenfokus bleibt erhalten. Der dritte Zug ersetzt das Pronomen durch diese Person. In allen 13 Fällen liegt die richtige Objektantwort danach außerhalb des gewählten Quellenscopes.

Beispiel `ctx2-test-switch-0`: „Sag mir etwas über Konrad.“ → „Sag mir etwas über meine Kaffeemaschine.“ → „Welche Farbe hat sie eigentlich?“ wird zu „Welche Farbe hat Konrad eigentlich?“ Erster Beleg ist „Konrad arbeitet als Tischler.“ statt „Unsere Kaffeemaschine hat die Farbe Silber.“ Das ist ein Referenz-/Zustandsfehler vor der anschließenden Belegauswahl.

**RETURN (14):** Die Dialoge führen erst einen Mann und dann ein weibliches Haustier ein. Das gespeicherte Haustierfakt nennt „eine Ziege“ oder „eine Ratte“, der allgemeine Subjektfallback registriert den Namen jedoch mit `{M,F}`. „er“ passt dadurch auch zum zuletzt erwähnten Haustier; die Routine bevorzugt dessen jüngere Erwähnung gegenüber der gemeinten Person. Alle 14 fehlgewählten Tiere sind im Trace M/F-Kandidaten. Der richtige Personenbeleg liegt anschließend außerhalb des Haustierscopes.

Beispiel `ctx2-test-return-8`: „Kennst du Bernhard?“ → „Kennst du Strolchi?“ bei gespeichertem „Strolchi ist das Haustier der Familie, eine Ziege.“ → „Als was arbeitet er denn?“ wird zu „Als was arbeitet Strolchi denn?“ Erster Beleg ist „Strolchi ist 35 Jahre alt.“ statt „Bernhard arbeitet als Bäcker.“

**Quellenabgrenzung:** In 27/27 diagnostischen Traces enthält der gewählte Scope keinen Goldbeleg. Alle tatsächlich zitierten Fakten gehören trotzdem zum aktuellen bestätigten Faktenpool des jeweiligen Dialogs. Die Beobachtung belegt falsche Zuordnung von Quellen zum Referenten, keine Verwendung widerrufener oder fremder Quellen. Der statische TEST2-Pool prüft keinen laufenden Quellenwiderruf. Zehn RETURN-Fälle zitieren zusätzlich ein Fakt zu einem anderen Merkmal (Beruf/Hobby/Wohnort gefragt, Alter/Futter zitiert); vier Altersfragen zitieren zwar das gefragte Merkmal, aber vom falschen Tier. Daraus wird keine unabhängige neue Fehlerursache abgeleitet.

## Vollständige Zuordnung der 27 WRONG

In den Tabellen bezeichnet „Soll → gewählt“ den Referenten. Alle aufgeführten Fälle betreffen Zug 3. Die maschinenlesbare [Begleitdatei](CTX2-OPEN-ERROR-TAXONOMY-20261024.json) hält Eingabe, Rewrite, Gold, Erstbeleg, Scope und Registry-Eintrag je Fall fest.

| SWITCH-Fall | Pronomen | Soll → gewählt | Erstbeleg |
|---|---|---|---|
| `ctx2-test-switch-0` | sie | Kaffeemaschine → Konrad | Konrad arbeitet als Tischler. |
| `ctx2-test-switch-2` | er | Kühlschrank → Clemens | Clemens arbeitet als Gärtner. |
| `ctx2-test-switch-8` | er | Kühlschrank → Gregor | Gregor arbeitet als Elektriker. |
| `ctx2-test-switch-9` | sie | Kaffeemaschine → Clemens | Clemens arbeitet als Gärtner. |
| `ctx2-test-switch-17` | sie | Kaffeemaschine → Theresa | Theresa arbeitet als Bäckerin. |
| `ctx2-test-switch-19` | er | Kühlschrank → Antonia | Antonia arbeitet als Gärtnerin. |
| `ctx2-test-switch-20` | er | Kühlschrank → Valentin | Valentin arbeitet als Apotheker. |
| `ctx2-test-switch-21` | er | Kühlschrank → Clemens | Clemens arbeitet als Bäcker. |
| `ctx2-test-switch-22` | sie | Nähmaschine → Magdalena | Magdalena arbeitet als Friseurin. |
| `ctx2-test-switch-23` | sie | Kaffeemaschine → Raphael | Raphael arbeitet als Tischler. |
| `ctx2-test-switch-24` | er | Kühlschrank → Ferdinand | Ferdinand arbeitet als Elektriker. |
| `ctx2-test-switch-31` | sie | Nähmaschine → Valerie | Valerie arbeitet als Tischlerin. |
| `ctx2-test-switch-32` | sie | Kaffeemaschine → Magdalena | Magdalena arbeitet als Gärtnerin. |

| RETURN-Fall | Pronomen | Soll → gewählt | Erstbeleg |
|---|---|---|---|
| `ctx2-test-return-8` | er | Bernhard → Strolchi | Strolchi ist 35 Jahre alt. |
| `ctx2-test-return-10` | er | Konrad → Strolchi | Strolchi frisst am liebsten Bananen. |
| `ctx2-test-return-11` | er | Matthias → Kiki | Kiki ist 68 Jahre alt. |
| `ctx2-test-return-16` | er | Clemens → Kiki | Kiki ist 45 Jahre alt. |
| `ctx2-test-return-17` | er | Konrad → Strolchi | Strolchi ist 36 Jahre alt. |
| `ctx2-test-return-19` | er | Gregor → Nala | Nala ist 11 Jahre alt. |
| `ctx2-test-return-20` | er | Bernhard → Flocki | Flocki ist 12 Jahre alt. |
| `ctx2-test-return-22` | er | Konrad → Brutus | Brutus ist 42 Jahre alt. |
| `ctx2-test-return-23` | er | Clemens → Brutus | Brutus ist 19 Jahre alt. |
| `ctx2-test-return-26` | er | Bernhard → Moritz | Moritz frisst am liebsten Löwenzahn. |
| `ctx2-test-return-27` | er | Gregor → Flocki | Flocki ist 15 Jahre alt. |
| `ctx2-test-return-36` | er | Clemens → Kiki | Kiki ist 8 Jahre alt. |
| `ctx2-test-return-37` | er | Severin → Nala | Nala ist 54 Jahre alt. |
| `ctx2-test-return-39` | er | Ferdinand → Strolchi | Strolchi ist 60 Jahre alt. |

## Grenzen und Einordnung

Die übrigen zehn TEST2-Familien enthalten laut Eingangsbericht keine WRONG-Fälle; das ist keine Vollständigkeitsbehauptung für deutsche Sprache. Der bekannte offene Datensatz bleibt synthetische Entwicklungs-/Regressionsdaten. Diese Taxonomie verändert keine Regeln, keine Produktlogik und keine Gewichte; sie bewertet weder einen neuen Kandidaten noch CTX3 und begründet keine automatische Übernahme. Laufzeiten und globale Zusatzbelege werden hier nicht neu gemessen oder aus den 27 Erstbelegen hochgerechnet.

Nächster Schritt: diesen Fehlerkatalog als Befund zur vorhandenen Forschung archivieren; neue Aussagen müssen anhand eines gesondert definierten Prüfgegenstands belegt werden. Rücksetzen dieser Dokumentationsänderung entfernt nur die beiden Taxonomiedateien. Produktverhalten, CTX1-Standard und eingefrorene B2-Semantik bleiben unverändert.
