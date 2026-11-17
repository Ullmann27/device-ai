# Vorschlag radar-8cd13dea7c0e: Optimised CPU inference of small language models on mobile devices

Quelle: https://arxiv.org/abs/2505.06461v1 (Code-Lizenz: unknown; nur Idee als Anregung, kein Code übernommen)
Experiment: all · vorläufige Einstufung: FEASIBLE (local hardware named: "CPU")

1. **Mechanismus:** Reports that GPU memory transfer overhead and CPU thread optimisation decide on-device speed and argues for the untapped potential of optimised CPU inference on mobile devices.
2. **Bekannte Methode mit ähnlichem Zweck:** die Quelle selbst; Vergleich mit der Lab-Baseline des Experiments.
3. **Was wäre an unserer Variante anders:** vor dem Lauf auszufüllen (eigene Implementierung im LM1-Autodiff).
4. **Widerlegende Messung:** VALIDATION-BPB bzw. Aufgabenerfolg nicht besser als die unveränderte Baseline bei gleichem Budget (Seeds 1–3, vollständige Trennung).
5. **Kosten:** Parameter, ms/Token, Spitzen-RAM auf CPU messen; keine Annahmen aus dem Paper übernehmen.
6. **Abbruch:** NaN/Instabilität, Budgetüberschreitung oder kein Vorteil nach dem vorab festgelegten Budget.
