# M9/M10 · Reparaturerfahrung auf echtem Code – Vorregistrierung

**Stand:** vor jedem Armlauf. Aufgaben erzeugt auf Commit `c1df0b95`.
**Siegel:** `7a160a0ec6d73ae65c3ac7ade7475e355c2d18a497c1fbd6ad5bae970adcf211`. Die Aufgaben stehen in `m9-tasks-sealed.json`. Nach dem Lauf wird weder eine Aufgabe noch ein Kriterium geändert.

## Warum M3 keinen Vorteil zeigte (Arbeitshypothese)

M3 hatte **zwei** Kandidaten pro Aufgabe und **keine** beobachtbaren Diagnosemerkmale. Erfahrung kann nur Reihenfolgen verbessern, und bei zwei Kandidaten mit billigem Test gibt es kaum etwas zu ordnen. M9 prüft deshalb drei Bedingungen, unter denen Erfahrung helfen *könnte*: viele Kandidaten (Median 38), beobachtbare Diagnose (Art des Testfehlers) und unbekannter Code (Holdout-Module) mit einer neuen Fehlerursache.

## Aufbau

- **Code und Tests:** 13 echte Projektmodule mit ihren echten Testdateien. Training (6 Module, 59 Aufgaben), Entwicklung (3 Module, 28), Holdout (4 Module, 44). Die Module überschneiden sich nicht.
- **Fehler:** Ein-Token-Änderungen aus sechs Ursachen (REL, EQ, LOGIC, CONST, NEG; ARITH **nur im Holdout**, 4 Aufgaben). Nur Fehler, die die sichtbaren Tests erkennen und deren exakte Umkehrung im Reparaturvokabular liegt.
- **Kandidaten:** alle Ein-Token-Änderungen desselben Vokabulars in ±5 Zeilen um die Fehlerstelle (simulierte Lokalisierung; Positionen gehen nicht in die Rangfolge ein).
- **Tests 2:1:1:** sichtbar (Agent), Prüfer (unabhängige Verifikation), verdeckt (nur Endbewertung). Endbewertung: ganze Testdatei plus exakte Wiederherstellung.
- **Erfahrung:** nur aus Trainingsaufgaben; jeder Kandidat wird gegen die ganze Testdatei geprüft.
- **Budget:** 10 sichtbare Testläufe pro Aufgabe. Prüferläufe werden gesondert gezählt.
- **Arme:** none · general · context · counterexample (Prüfung nur bei Overfitting-Risiko aus Trainings-Gegenbeispielen) · verified (immer Prüfer) · adaptive (Kontext ab 8 Beobachtungen, sonst allgemein; mit Prüfer) · **selfcorrect** (M10: wie adaptive, plus Neuordnung nach jedem Fehlschlag, je nachdem, ob die Änderung die Menge der roten Tests verändert hat).
- **Wiederholungen:** 3 vollständige Läufe mit leerem Cache. Die Ergebnisse müssen identisch sein.

## Hypothesen und Erfolgskriterien (Holdout)

| ID | Hypothese | Kriterium |
|---|---|---|
| H9a | Kontext-Erfahrung löst auf bekannten Ursachen mehr Aufgaben oder braucht weniger Läufe als keine Erfahrung | exakter McNemar (gelöst) oder Vorzeichentest (Läufe) zweiseitig p < 0,05 |
| H9b | Auf der neuen Ursache ARITH bringt Erfahrung keinen Vorteil | nur beschreibend (n = 4) |
| H9c | Unabhängige Verifikation senkt falsche Erfolge gegenüber context | Anzahl falscher Erfolge; Kosten in Prüferläufen |
| H9d | Kontext schlägt allgemeine Erfahrung | beschreibend und gepaarter Test |
| H10 | Selbstkorrektur braucht weniger Läufe als verified, ohne mehr falsche Erfolge und ohne Wiederholung eines Kandidaten | Vorzeichentest p < 0,05; Wiederholungen = 0 (Laufzeitprüfung) |

Ein nicht signifikantes Ergebnis wird als solches berichtet. Grenzen: Die Fehler sind eingesetzt, nicht historisch. Reparaturen stammen aus demselben Vokabular. Es gibt kein Sprachmodell. Die Messung läuft auf dem Cloud-Host, nicht auf dem Fold7. Die echten historischen Fehler (5 Fixes) werden getrennt berichtet.
