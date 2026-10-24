# CTX1 (S1/S2): Kontextroutine vor dem Gedächtnisdialog – Ergebnis (24.10.2026)

Vorregistrierung: `CTX1-DIALOGUE-CONTEXT-PREREG-20261024.md` (Commit `8026fb5c`). TEST **einmal** ausgeführt mit genau
diesem Commit; Prüfwert des TEST-Korpus `6f2514f3842d9e929770a262ae4475c1822cc4a3e72c9a639d257672d23d97f0` stimmte.
Rohdaten: `ctx1/ctx1-test-report.json`, Ausgabe `ctx1/ctx1-test-stdout.txt`. JVM im Linux-Container, kein Gerät.

## Ergebnis TEST (480 Dialoge, 1080 Züge, ungesehener Wortschatz und ungesehene Formulierungen)

| | B0 heutiger Kern | B1 Kontextroutine + derselbe Kern |
|---|---:|---:|
| richtige Erinnerungsantworten (von 920) | 631 | **920** |
| **falsche Behauptungen** | **433** | **0** |
| verpasst (nichts zitiert, obwohl gespeichert) | 16 | 0 |
| unnötige Rückfragen | – | 0 |
| richtige Rückfragen (von 80) | 0 | 80 |
| richtige Enthaltungen bei Unbekannten (von 80) | 0 | 80 |
| Laufzeit je Zug Median / p95 | 1,74 / 3,17 ms | 1,42 / 2,11 ms |
| davon Routine allein Median / p95 | – | 0,10 / 0,26 ms |
| Zustand | Dialogdatei ≤ 1 533 B | Dialogdatei ≤ 1 521 B, Routine ≤ 8 Einträge |

Je Familie (richtig / falsch / verpasst bzw. Rückfrage oder Enthaltung):

| Familie | B0 | B1 |
|---|---|---|
| REF_SIMPLE (120) | 63 / 57 / 0 | 120 / 0 / 0 |
| REF_TYPO (80) | 48 / 16 / 16 | 80 / 0 / 0 |
| REF_AUSTRIAN (80) | 51 / 29 / 0 | 80 / 0 / 0 |
| ELLIPSIS (80) | 43 / 37 / 0 | 80 / 0 / 0 |
| CORRECTION (80) | 48 / 32 / 0 | 80 / 0 / 0 |
| NEGATION (120) | 64 / 56 / 0 | 120 / 0 / 0 |
| SWITCH (120) | 120 / 0 / 0 | 120 / 0 / 0 |
| RETURN (120) | 93 / 27 / 0 | 120 / 0 / 0 |
| AMBIGUOUS (80; 40 Einführungen, 40 Rückfragen) | 40 / 40 / 0 | 40 + 40 Rückfragen / 0 / 0 |
| NEW_SESSION (40 Rückfragen) | 0 / 40 / 0 | 40 Rückfragen / 0 / 0 |
| UNKNOWN (80 Enthaltungen) | 0 / 80 / 0 | 80 Enthaltungen / 0 / 0 |
| TIME (80) | 61 / 19 / 0 | 80 / 0 / 0 |

## Entscheidung nach der vorab festgelegten Regel

(1) falsch 0 ≤ ½ · 433 ✔ · (2) 920 ≥ 631 ✔ · (3) unnötige Rückfragen 0 ≤ 5 % ✔ · (4) Routine Median 0,10 ms ≤ 5 ms,
p95 0,26 ms ≤ 20 ms ✔. **B1 wird für eine optionale, abschaltbare Anbindung vorgeschlagen** – nur nach Besitzerprüfung,
standardmäßig aus, nie automatisch.

## Was die Zahlen zeigen (und was nicht)

- **Fehlerbild des heutigen Kerns:** Fürwörter sind Stoppwörter. „Weißt du, wie alt sie ist?“ sucht nur nach „alt“ und
  zitiert das Alter *irgendeiner* Person oder eines Tiers – bei neuer Sitzung, Mehrdeutigkeit und unbekannten Namen
  immer eine falsche Behauptung statt einer Rückfrage.
- **TIME, Ursache am eigenen Beispiel nachgeprüft:** „Und wie schaut's am Freitag aus?“ löst die Anschlussregel „und
  wie“ aus; der Kern hängt die vorige Frage an („Was steht am Dienstag an? Und wie …“) und zitiert den Dienstag.
  „Und am Freitag?“ löst sie nicht aus. Die Anschlussregel selbst erzeugt also falsche Antworten.
- **Tippfehler:** Die Rechtschreibhilfe des Kerns behandelt nur Wörter ab 6 Buchstaben und nur, wenn gar nichts
  gefunden wird; „Wo wognt er?“ bleibt deshalb unbeantwortet (16 verpasst).
- **Nebenwert zusätzliche Zitierungen:** B1 1 019, B0 752. Der Kern zeigt bis zu drei Erinnerungen; bei B1 sind es durch
  die Eingrenzung weitere Fakten **derselben** Person (außer TIME), bei B0 auch Fakten anderer Personen.
- **Warum B1 schneller ist:** Die Eingrenzung verkleinert den Pool, den der Kern bewertet.

## Grenzen (unverändert ernst zu nehmen)

- Synthetische Dialoge, geschrieben vom selben Autor wie B1; die TEST-Formulierungen standen beim Entwickeln im
  Generator-Quelltext (nie ausgeführt oder ausgegeben). 0 Fehler heißt: Die zwölf vorab beschriebenen Muster werden auch
  mit ungesehenem Wortschatz und ungesehenen Formulierungen beherrscht – **nicht**, dass beliebige echte Gespräche fehlerfrei sind.
- Feste Fakt-Formulierungen („X ist mein …“, „Unser … heißt X.“, „Mein … ist/hat …“); andere Formulierungen echter
  Besitzer-Erinnerungen sind ungeprüft (B1 fällt dann auf den ganzen Pool zurück, also auf B0-Verhalten).
- Kein Plural-„sie“, keine Höflichkeitsform, kein expletives „es“, keine Mehrwort-Namen, keine echten Gerätelaufzeiten.

## Nächster Schritt (Vorschlag, nicht ausgeführt)

Anbindung im Haupt-Quellset hinter einem standardmäßig ausgeschalteten Schalter in `ContinuumLearningCore.replyFromMemory`
(Register aus den aktuellen, nicht widerrufenen Besitzer-Fakten, Zustand nur im Arbeitsspeicher der Sitzung), mit
denselben Tests plus Widerruf-Test (S2: ein widerrufener Fakt darf weder Register noch Eingrenzung speisen).
Davor: Messung mit **echten** Formulierungen von Besitzer-Erinnerungen (anonymisiert, mit Einverständnis) als neuer
TEST-Split; erst danach Schalter für den Besitzer.
