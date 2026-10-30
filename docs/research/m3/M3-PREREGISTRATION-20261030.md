# M3 · Fähigkeitstransfer auf neue Fehlerformen – Vorregistrierung

**Stand:** 30.10.2026, vor dem ersten Lauf. Autor: Opus 5.5. Basis: PR #243 (`ebba7e16`) + M2-Fixes (`1df9e61c`).
**Siegel des NOVEL-Holdouts:** `5a306c87c02be2fe9fe2bfe8da9beb0e8711c9de1d339e5a280f2f281adcfe68` (18 Aufgaben).
Nach dem ersten Lauf wird weder das Siegel noch eine Fixture geändert. Ergebnisse werden unverändert berichtet, auch wenn sie den Vorhersagen widersprechen.

## Frage

Hilft gespeicherte Reparaturerfahrung aus PR #243 bei Fehlerformen, die im Training nie vorkamen? Und was leistet unabhängige Verifikation mit und ohne Erfahrung?

## Neue Fehlerfamilien (je 3 Aufgaben, eigener Namensraum, andere Syntax als Training/Holdout)

| Familie | Form | Merkmal | richtig ist |
|---|---|---|---|
| member-local | `score.v+5`, lokal `scores={v:n}` | NO_HELPER_EXPORT | Tippfehler-Fix |
| member-import | wie oben, Helfer exportiert `score` | BOTH_AVAILABLE | Import |
| arity-local | `score(8,2)`, lokal `scores(x,y)` | NO_HELPER_EXPORT | Tippfehler-Fix |
| arity-import | wie oben, Helfer exportiert `score(x,y)` | BOTH_AVAILABLE | Import |
| negative-local | Member-Form, Sollwert passt zu keinem Fix | NO_HELPER_EXPORT | **keiner** |
| negative-import | Aritäts-Form, Sollwert passt zu keinem Fix | BOTH_AVAILABLE | **keiner** |

Die Negativkontrollen prüfen, ob irgendein Arm einen Erfolg ohne gültigen Kandidaten meldet. Der Test `transfer.test.mjs` prüft durch Ausführen beider Kandidaten, dass in jeder Aufgabe genau einer (bei negative-*: keiner) den Sollwert liefert.

## Arme

1. **baseline**: CCC1-`rankCandidates` ohne Erfahrung, Budget 1 Test
2. **broad**: mit allgemeiner Erfahrung aus 18 Trainingsaufgaben, Budget 1
3. **context**: mit kontextbezogener Erfahrung (`node.js.value|call`), Budget 1
4. **guarded**: konservativer Guard aus PR #243 (enthält sich bei Widerspruch), Budget 1
5. **guardedVerify**: Guard-Wahl zuerst, sonst Kontext-Rangfolge, unabhängiger Test, Budget 2 (korrigierte Anwendungsbedingung: Enthaltung → prüfen statt raten)
6. **verifyOnly**: keine Erfahrung, Rangfolge ohne Statistik, Budget 2

## Vorhersagen (vor dem Lauf)

- **P1:** baseline = broad = context = **6/18** (alle wählen den Tippfehler-Fix; richtig nur in member-local und arity-local). Erfahrung bringt auf neuen Formen keinen Vorteil.
- **P2:** guarded = **6 richtig / 3 falsch / 9 Enthaltungen**. Die 3 Fehler sind negative-local: Die gelernte Bedingung „kein Export ⇒ Tippfehler“ verallgemeinert über, wenn kein Kandidat stimmt.
- **P3:** guardedVerify = verifyOnly = **12/18** (das Orakel-Maximum) bei gleich vielen Testläufen (je **30**). Erfahrung spart hier keine Verifikation.
- **P4:** **0 von 6** Negativkontrollen werden in irgendeinem Arm als Erfolg gemeldet (sonst bricht die Auswertung ab).

## Stop- und Berichtsregel

Ein Lauf auf sauberem, gepushtem Commit. Abweichungen von P1–P4 werden als Befund berichtet und nicht wegrepariert. Kein Ergebnis darf CCC1-Lernen, Ranking oder Rechte ändern (`productionActivationAllowed:false`).

## Grenzen

Es sind synthetische JS-Fixtures von einem Autor mit zwei festen Strategien, ohne generierten Code. Die Familien sind neue Formen, keine neuen echten Projekte. Die Kindprozesse laufen in keiner OS-Sandbox. Es gibt keine externe Beglaubigung und keine Fold7-Ausführung.
