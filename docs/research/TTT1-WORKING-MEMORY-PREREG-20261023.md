# Experiment A – TTT1: adaptives Arbeitsgedächtnis – Vorregistrierung (23.10.2026)

Festgelegt **vor** dem ersten Messlauf. Reines Forschungsmodul im Test-Quellset: nichts in der APK, nichts im Chat,
nichts im dauerhaften Gedächtnis; alle Zustände leben nur im RAM einer Sitzung.

## Frage

Verbessert ein adaptiver 16×16-Fast-Weight-Arbeitsspeicher (Lernregel von `EphemeralFastWeightCore`) die Rückbezüge in
Gesprächen gegenüber (1) einem begrenzten Gesprächskontext und (2) einem expliziten Themen-/Entitätsspeicher – auf
denselben Testdialogen?

## Testdialoge (deutsch, deterministisch)

Generator mit festem Seed (DEV `20261027`, TEST `20261028`, Namen disjunkt). Entitäten: Geräte und Gebäude mit echtem
grammatischem Geschlecht (z. B. *der* Zähler, *die* Pumpe, *das* Lager, *die* Halle). Jeder Zug hat ein Gold: die
gemeinte Entität oder **RÜCKFRAGE** (wenn ein Mensch nachfragen müsste). Prädikate tragen Typvorgaben (*Wo steht …?* →
Gerät, *Wer betreibt …?* → Gebäude, *Wie alt ist …?* → beliebig). Varianten (DEV je 10, TEST je 40 Dialoge):

| Variante | Prüfzug (Gold) |
|---|---|
| `CONTINUE` | mehrere Fürwörter auf dieselbe Entität |
| `SWITCH` | ausdrücklicher Wechsel, danach Fürwort auf die neue Entität |
| `RETURN` | zwei Entitäten verschiedenen Geschlechts; Fürwort des älteren → ältere |
| `LONG_DISTANCE` | Entität vor mehr als drei Zügen, dazwischen kein passender Kandidat → diese |
| `TYPE_CONSTRAINT` | gleiches Geschlecht, Prädikat verlangt Gebäude; jüngere Entität ist ein Gerät → das Gebäude |
| `AMBIGUOUS` | zwei passende Entitäten im selben Zug genannt → RÜCKFRAGE |
| `TYPE_DESCRIPTION` | „Und die Kamera?“: genau eine Kamera genannt → diese; zwei → RÜCKFRAGE |
| `NEW_SESSION` | Fürwort im ersten Zug einer neuen Sitzung → RÜCKFRAGE (kein Übertrag) |
| `PATTERN` | Wechsel A, B, A, B (gleiches Geschlecht, je eigener Zug), dann Fürwort → B (jüngste passende) |

## Systeme

- **K – begrenzter Kontext:** die letzten 3 Züge; Fürwort → jüngste Entität im Fenster mit passendem Geschlecht;
  Typbeschreibung → jüngste passende im Fenster; sonst Rückfrage.
- **S – Entitätsspeicher:** bis 16 Einträge (Geschlecht, Typ, Nomen, letzter Zug); Fürwort → Kandidaten mit passendem
  Geschlecht **und** Typvorgabe des Prädikats, jüngster gewinnt; teilen sich die zwei jüngsten denselben Zug →
  Rückfrage; Typbeschreibung nur bei genau einem Treffer.
- **S+FW – Speicher + Fast-Weights:** wie S; nach jedem aufgelösten Zug lernt `W` den Übergang vorige → aktuelle
  Entität (seeded Einheitsvektoren in R¹⁶, Lernrate 0,08, Norm ≤ 2,5). `W` entscheidet (a) Gleichstände, bei denen S
  nachfragt, und (b) überstimmt die Jüngstenregel, wenn ein anderer zulässiger Kandidat um mindestens 0,2 höhere
  Kosinus-Ähnlichkeit zur Vorhersage hat. Typ- und Geschlechtsfilter bleiben.
- Jede Sitzung beginnt leer (Neustart setzt Fenster, Speicher und `W` zurück).

## Messgrößen (TEST)

Je Variante und System: **richtige Rückbezüge**, **falsche Rückbezüge** (andere Entität oder Entität statt
Rückfrage), **unnötige Rückfragen**, richtig verfolgte Entitätswechsel; Speicher je Sitzung (Bytes, gezählt);
Laufzeit je Zug (Median, p95; JVM, kein Gerät). Zusätzlich als Unit-Test: Gradient der Fast-Weight-Regel gegen
finite Differenzen, Normgrenze, Ablehnung nicht endlicher Eingaben, Neustart ohne Restzustand.

## Entscheidungsregel

Fast-Weights kommen nur dann in einen optionalen produktiven Pfad, wenn S+FW auf TEST **weniger falsche Rückbezüge**
als S hat **oder** bei gleich vielen falschen mindestens 5 Prozentpunkte mehr richtige. Sonst bleibt der explizite
Speicher die Baseline und `W` ein isoliertes Forschungsmodul. K gegen S wird beschrieben.
