# Experiment C – WSC1: geschützte Wake-Sleep-Konsolidierung – Vorregistrierung (23.10.2026)

Festgelegt **vor** dem ersten Messlauf. Baut auf Experiment B (LCT1, Schlussfolgerer L + unabhängiger Prüfer V) auf.
Reines Forschungsmodul im Test-Quellset: keine Gewichte, kein Besitzermodell, kein Gedächtnis, keine APK.

## Frage

Kann Continuum einen zuvor drei Schritte langen, **geprüften** Schlussfolgerungsweg später schneller beantworten,
ohne an Zuverlässigkeit zu verlieren – und ist dafür eine Konsolidierung mit Abhängigkeitsprüfung („Gewohnheit“)
besser als ein gewöhnlicher geprüfter Cache?

## Ablauf (identische Ausgangsbedingungen für alle Systeme)

1. **Welt:** LCT1-Generator (`Lct1Experiment.Builder`), eigene Seeds, 300 Ketten + 200 Ablenkerketten, Stichtag
   23.10.2026.
2. **Wach-Phase (TRAIN):** Strom von 600 Fragen über 200 der Ketten (Drei-Hop-Fragetypen `REGION_OF_DEVICE`,
   `VOLTAGE_AT_BUILDING`, `CEO_FOR_DEVICE`, `CAPITAL_FOR_BUILDING`, `CHECK_VOLTAGE_AT_BUILDING`), jede Frage mehrmals
   mit Stichtagen bis 31.10.2026. Antwortet L und bestätigt V, entsteht ein **Kandidat**.
3. **Schlaf-Phase:** Kandidaten werden nur übernommen, wenn sie das Tor bestehen (siehe unten); jeder übernommene
   Kandidat bekommt eine **Quittung** (SHA-256 über Frage, Antwort, Ableitung, Abhängigkeiten, Gültigkeitsfenster,
   Probenergebnisse).
4. **Ereignisse** (feste Liste, je 20 zufällige Ketten, nicht überlappend): Geschäftsführerwechsel zum 01.12.2026
   (alter Fakt endet, neuer beginnt); widersprechender `IN_REGION`-Fakt; Verneinung `IN_BUILDING`; entfernter
   `OPERATED_BY`-Fakt; neues gleichnamiges Gerät. Dazu Widerruf der Quelle `src-netz-2` und rund 2 000 unbeteiligte
   neue Fakten (400 Ablenkerketten).
5. **HOLDOUT:** 1 200 Fragen, je Kette 4 (zufälliger Drei-Hop-Fragetyp, Stichtag 24.10.2026–01.03.2027): Ketten der
   Wach-Phase (gesehen) und 100 ungesehene. **Unabhängige Negativfälle** entstehen aus den Ereignissen (Widerspruch,
   Verneinung, fehlendes Glied, mehrdeutiger Name, widerrufene Quelle). Gold je Frage = frisches L+V auf der
   Wissensbasis nach den Ereignissen.

## Systeme

- **FULL** – L+V bei jeder Frage.
- **CACHE** – gewöhnlicher geprüfter Cache: exakter Schlüssel (Fragetyp, Erwähnung, Stichtag, Behauptung) → von V
  bestätigte Antwort; **jede** Änderung der Wissensbasis leert ihn. Fehlschlag → FULL.
- **WSC1** – Gewohnheit je (Relationsfolge, Erwähnung) mit Abhängigkeiten: Namensgruppe der Erwähnung, jede
  (Subjekt, Relation)-Gruppe entlang der Ableitung (Version = Hash der Fakten), Status aller Quellen dieser Gruppen,
  Gültigkeitsfenster = Zeitraum, in dem sich die Zulassung keines Fakts dieser Gruppen ändert. Treffer nur, wenn alle
  Versionen gleich sind und der Stichtag im Fenster liegt; Prüffragen werten die Behauptung neu aus. Sonst FULL.
- **NAIVE** – Gewohnheit (Relationsfolge, Erwähnung) → von V bestätigte Antwort, aber ohne Abhängigkeiten und ohne
  Fenster (Negativkontrolle).
- CACHE und WSC1 lernen in der Wach-Phase; CACHE speichert auch im HOLDOUT neue bestätigte Antworten (wie ein üblicher
  Cache), WSC1 übernimmt nur im Schlaf. Zeitmessung: der ganze Ablauf zweimal mit frischen Instanzen, gemessen wird der
  zweite Durchlauf.

## Tor der Schlaf-Phase (für WSC1)

1. Das Tor prüft jeden Kandidaten selbst mit V. Als unbestätigte Modellbehauptungen werden zusätzlich verfälschte
   Antworten angeboten (anderer Wert, fremder Fakt, fehlender Schritt) – alle müssen verworfen werden.
2. **Kalibrierung:** je Kandidat eine positive Probe (anderer Stichtag im Fenster: frisches L+V = Gewohnheit) und
   Negativproben (Stichtag außerhalb eines begrenzten Fensters; anderer Fragetyp derselben Entität; kontrafaktische
   Wissensbasis mit geänderter Abhängigkeit, deren frisches L+V nachweislich anders antwortet) – die Gewohnheit darf
   dort nicht greifen.
3. **Regression:** alle früher übernommenen Proben müssen unverändert bestehen; sonst wird der Kandidat verworfen.
   Prüfung des Tors selbst: absichtlich fehlerhafte Kandidaten (unbegrenztes Fenster, fehlende Abhängigkeit) müssen
   verworfen werden.

## Messgrößen (HOLDOUT)

Falsche Antworten gegen Gold (veraltet, widersprüchlich, unbelegt), verpasste, Trefferquote (aus Cache/Gewohnheit),
Laufzeit je Frage (Median, p95; JVM, kein Gerät) auf den **zuvor gelösten Fragen** (gleiche Relationsfolge und
Erwähnung wie eine Frage der Wach-Phase) und auf allen HOLDOUT-Fragen, Kosten der Schlaf-Phase, Zahl und Gründe
verworfener Kandidaten.

*Präzisierung vor dem ersten Lauf:* Die Fragestellung betrifft zuvor gelöste Wege; der Median über alle Fragen
würde bei einer Trefferquote unter 50 % nur die Laufzeit ungelöster Fragen messen. Die Laufzeitregeln gelten daher
für die Teilmenge der zuvor gelösten Fragen; die Laufzeit über alle Fragen wird zusätzlich berichtet.

## Entscheidungsregeln

1. **Schneller ohne Verlust:** WSC1 hat 0 falsche Antworten (alle HOLDOUT-Fragen) **und** Median-Laufzeit auf den
   zuvor gelösten Fragen ≤ 0,5 × FULL auf denselben Fragen.
2. **WSC1 statt Cache** nur, wenn zusätzlich Trefferquote (alle Fragen) ≥ CACHE + 0,10 **oder** Median auf den zuvor
   gelösten Fragen ≤ 0,5 × CACHE bei ebenfalls
   0 falschen Antworten. Sonst ist der gewöhnliche geprüfte Cache vorzuziehen.
3. Das Tor muss alle unbestätigten und alle absichtlich fehlerhaften Kandidaten verwerfen; sonst gilt WSC1 als nicht
   sicher, unabhängig von 1 und 2.
4. NAIVE dient nur als Negativkontrolle (erwartet: falsche Antworten nach Ereignissen).
