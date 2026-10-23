# Experiment C – WSC1: geschützte Wake-Sleep-Konsolidierung – Ergebnis (23.10.2026)

Vorregistrierung: `WSC1-CONSOLIDATION-PREREG-20261023.md` (`6f1fc9ae`, Präzisierung der Laufzeitregel `cb2611d1`, beide
vor jedem Lauf). Code eingefroren in `677e2d9f` (`app/src/test/java/at/heinz/deviceai/intelligence/maxultra/Wsc1*.kt`,
baut auf LCT1 auf). Zwei identische Durchläufe mit frischen Instanzen (Ergebnisse gleich), gemessen der zweite.
Bericht: `wsc1-consolidation-report-20261023.json`, Konsole: `wsc1-consolidation-stdout-20261023.txt`.
JVM 21.0.12, x86_64 – **keine Android- oder Fold7-Aussage**. Nur Test-Quellset: keine Gewichte, kein Besitzermodell,
kein Gedächtnis, nichts in der APK.

## Ablauf

- **Welt:** 300 Ketten + 200 Ablenkerketten, 4 140 Fakten. **Wach-Phase:** 600 Fragen (200 Ketten × 3 Stichtage),
  alle von L beantwortet und von V bestätigt → 600 Kandidaten.
- **Schlaf:** 200 Gewohnheiten übernommen, 400 Dubletten (Frage schon abgedeckt), **0** echte Kandidaten verworfen,
  0 uninformative Kontrafakten, alle 200 Quittungen nachrechenbar. Dauer 19,7 s (die vollständige Regression wächst
  quadratisch).
- **Ereignisse:** je 20 Ketten Geschäftsführerwechsel zum 01.12.2026, widersprechender `IN_REGION`-Fakt, Verneinung
  `IN_BUILDING`, entferntes `OPERATED_BY`, gleichnamiges Gerät; Widerruf `src-netz-2`; 400 Ablenkerketten
  → 6 480 Fakten.
- **HOLDOUT:** 1 200 Fragen bis 01.03.2027, davon 299 mit Gold-Enthaltung (Negativfälle aus den Ereignissen) und
  492 **zuvor gelöste** (gleiche Relationsfolge und Erwähnung wie eine Wach-Frage).

## Ergebnis HOLDOUT

| System | richtig | falsch | Trefferquote | Median / p95 zuvor gelöste | Median / p95 alle |
|---|---|---|---|---|---|
| FULL (L+V jedes Mal) | 1 200 | 0 | – | 571 / 903 µs | 635 / 1 005 µs |
| CACHE (gewöhnlich, geprüft) | 1 200 | 0 | 0,003 (4) | 588 / 856 µs | 639 / 898 µs |
| **WSC1** | **1 200** | **0** | **0,303** (363) | **44 / 122 µs** | 103 / 1 106 µs |
| NAIVE (Negativkontrolle) | 1 071 | **129** | 0,410 (492) | 0,9 / 5 µs | 83 / 1 262 µs |

**Tor-Selbsttest** (leeres Ziel, je 20 Fälle): verfälschter Wert, fremder Fakt, fehlender Schritt → 60/60
`NOT_VERIFIED`; unbegrenztes Fenster → 20/20 `DATE_PROBE`; fehlende Abhängigkeit → 20/20 `COUNTERFACTUAL`.

## Entscheidungen nach Vorregistrierung

1. **Schneller ohne Verlust – erfüllt:** WSC1 0 falsche Antworten; Median auf zuvor gelösten Fragen 44 µs ≤ 0,5 × 571 µs
   (FULL), rund 13× schneller.
2. **WSC1 statt gewöhnlichem Cache – erfüllt:** Trefferquote 0,303 ≥ 0,003 + 0,10 und Median 44 µs ≤ 0,5 × 588 µs, bei
   beiden 0 falsche Antworten.
3. **Tor sicher – erfüllt:** 100/100 unbestätigte bzw. absichtlich fehlerhafte Kandidaten verworfen.
4. **NAIVE:** 129 falsche Antworten, alle auf zuvor gelösten Fragen nach den Ereignissen (veraltete, widerrufene,
   widersprochene oder mehrdeutig gewordene Antworten). Verifikation beim Lernen allein reicht nicht.

## Einordnung

- **Der Gewinn ist deterministisch, nicht neuronal.** WSC1 ist ein geprüfter Cache mit Abhängigkeiten und
  Gültigkeitsfenster. Gegenüber dem gewöhnlichen Cache bringen genau diese beiden Dinge den Vorteil: Das Fenster
  trägt die Antwort über wechselnde Stichtage, die feine Invalidierung übersteht 2 280 unbeteiligte neue Fakten.
  Kein Gewicht wurde trainiert oder verändert.
- **Woher die Zeit kommt:** Bei einem Treffer laufen weder die Suche noch der Prüfer V (V liest absichtlich Rohdaten
  ohne Index und kostet den Großteil von FULL). Das ist nur zulässig, weil eine Gewohnheit lediglich greift, solange
  alles unverändert ist, was L und V für sie gelesen haben. Das Tor prüft das mit Probe-Stichtagen und validierten
  Kontrafakten; die Mutationsprüfung zeigt, dass beide Prüfungen nötig sind (ohne Gruppenversionen übernimmt das Tor
  nichts, ohne Quellenstatus gibt WSC1 im kleinen Testlauf 8 falsche Antworten).
- **Kosten:** Ein Treffer (44 µs) enthält die erstmalige SHA-256-Berechnung der Gruppenversionen; beim Laden
  vorberechnet wären es wenige µs. Ein Fehlschlag kostet FULL plus die Prüfung (p95 alle: 1,1 ms). Die Schlafphase
  wächst quadratisch, weil jede Übernahme alle früheren Proben neu laufen lässt; ändern kann eine neue Gewohnheit
  aber nur Proben mit demselben Schlüssel.

## Grenzen

- Synthetische Welt und feste Ereignisliste, vom selben Autor wie L, V und WSC1.
- Die Abhängigkeitsmenge ist für L und V **in dieser Form** vollständig (Namensgruppe, jede Faktengruppe der
  Ableitung, Quellenstatus, Gültigkeitsgrenzen). Liest eine künftige Fassung von L mehr, etwa neue Regeln oder
  transitive Relationen, muss die Abhängigkeitsmenge mitwachsen. Die Kontrafakten des Tors ändern nur Fakten
  entlang der Ableitung und finden eine solche Lücke nicht.
- Enthaltungen werden nicht konsolidiert; nur Drei-Hop-Fragetypen; JVM-Zeiten, kein Gerät.

## Empfehlung

Wiederholte Mehr-Hop-Antworten nach dem WSC1-Muster beschleunigen, nicht mit neuronaler Konsolidierung:
Quelle nur LCT1 (L+V), Gewohnheit mit Abhängigkeiten, Fenster und Quittung, Tor mit Proben, Kontrafakten und
Regression. Vor einer Anbindung: Gruppenversionen beim Laden berechnen, Regression nach Schlüssel indexieren,
Gewohnheiten flüchtig halten (beim Neustart verwerfen) oder mit ihren Quittungen prüfbar speichern.

**Nachvollziehen:** `ContinuumCortexShadow.kt` + `Lct1*.kt` + `Wsc1*.kt` kompilieren (Klassenpfad wie
`scripts/android-kotlin-unit-check.sh`), dann `java … at.heinz.deviceai.intelligence.maxultra.Wsc1Experiment <bericht.json> <commit>`.
