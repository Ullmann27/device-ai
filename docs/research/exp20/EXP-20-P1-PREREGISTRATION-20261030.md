# EXP-20 · Pilot P1 · Vorregistrierung (vor dem Lauf committet)

**Frage:** Lässt sich ein CTRNN so integrieren, dass es für **jede** Schrittweite beschränkt bleibt? Kontrahiert es unter einer prüfbaren Bedingung? Ist es deterministisch? Und was kostet ein Schritt auf dem Host?
**Nicht gefragt:** ob das Signal das Routing verbessert. Dafür braucht es einen externen Holdout, und der folgt erst später (P2).
**Status:** NUR TEST/LABOR (`app/src/test/.../research/exp20/`). Es gibt keinen Aufrufer im Hauptcode, `ChatReasoningRouter` und ToolGate sind unverändert.

## Modell und einzige Änderung gegenüber der Baseline

- **Modell:** τᵢ·dhᵢ/dt = −hᵢ + Σⱼ Wᵢⱼ·tanh(hⱼ) + xᵢ + bᵢ. Die Eingaben x werden auf [−1, 1] geklemmt.
- **Integrator:** Exponential-Euler mit aᵢ = e^(−Δt/τᵢ), hᵢ ← aᵢ·hᵢ + (1−aᵢ)·driveᵢ. Für den linearen Teil ist das exakt. tanh(h) wird pro Schritt einmal berechnet (Jacobi).
- **Analytische Aussagen, die getestet werden:**
  1. **Schranke:** Mit Rᵢ = Σⱼ|Wᵢⱼ| + |bᵢ| + 1 gilt |hᵢ| ≤ max(|hᵢ(0)|, Rᵢ) für jedes Δt > 0. Der neue Wert ist eine Konvexkombination.
  2. **Kontraktion:** Gilt ρ = maxᵢ Σⱼ|Wᵢⱼ| < 1, dann schrumpft ‖h−g‖∞ pro Schritt mindestens um den Faktor q = maxᵢ(aᵢ + (1−aᵢ)·ρ) < 1.
- **Fail-closed:** Bei nicht endlichem Δt oder nicht endlicher Eingabe sowie bei Δt ≤ 0 bleibt der Zustand unverändert, und die Funktion gibt `false` zurück.
- **Baseline für die Kosten:** gleich viele unabhängige Leckintegratoren (EMA, hᵢ ← a·hᵢ + (1−a)·xᵢ). Das ist das einfachste Verfahren mit zeitlichem Kontext.

## H20a · Numerik (JUnit, Seed fest)

- **Lauf:** 10 000 Versuche mit diesen Parametern:
  - n ∈ [2, 32],
  - W zufällig und zeilenweise auf ρ ∈ [0, 0,95] skaliert,
  - τ ∈ [0,05; 20], Δt ∈ [10⁻⁴; 50],
  - Eingaben ∈ [−3; 3] (danach geklemmt),
  - je 200 Schritte, zwei Startzustände.
- **Bestanden nur bei 0 Verletzungen:**
  - Schranke (Toleranz 1e−12),
  - kein NaN oder ∞,
  - Kontraktion je Schritt ≤ q·vorher + 1e−12,
  - zwei identische Läufe sind bitgleich,
  - fail-closed in allen Fehlerfällen.
- **Negativkontrolle** (prüft, ob der Test unterscheiden kann): W = 3·I, x = 0, Start +2 gegen −2, 1000 Schritte. Der Abstand muss > 1 bleiben, denn es gibt zwei stabile Fixpunkte. Ohne die Bedingung ρ < 1 gilt also keine Kontraktion.

## H20b · Kosten (Host-JVM, **nicht** Fold7)

- **Lauf:** n = 16, 200 000 Aufwärmschritte, danach 1000 Blöcke à 1000 Schritte. Gemessen werden ns pro Schritt (p50/p95 über die Blöcke) und die Allokation über `ThreadMXBean.getThreadAllocatedBytes` im Messlauf.
- **Vergleich:** CTRNN gegen EMA unter gleichen Bedingungen.
- **Bestanden:**
  - Allokation im Messlauf ≤ 1024 Byte (keine Allokation pro Schritt),
  - CTRNN p95 ≤ 10 µs pro Schritt.
- **Berichtet:** das Verhältnis CTRNN zu EMA und der Speicherbedarf des Zustands.

## Ressourcen, Abbruch, Entscheidung

- **Ressourcen:** ein Host-Lauf mit Wanduhr ≤ 5 min. Bei Abbruch oder Timeout gilt UNBEKANNT, es wird kein Ergebnis erfunden.
- **Entscheidung:** Auch wenn alles besteht, bleibt EXP-20 Laborstatus. Bestanden heißt nur: Der Baustein ist numerisch sicher und billig genug für P2.
- **P2 (nicht in dieser Runde):** CTRNN-Vorkontext gegen EMA und gegen „ohne“ als SHADOW-Signal für eine Routing-Entscheidung, auf einem fremd versiegelten Holdout, mit Messung auf dem Fold7.
- **Lernen:** Es werden keine Gewichte gelernt. EWC und STDP sind deshalb hier nicht anwendbar.
