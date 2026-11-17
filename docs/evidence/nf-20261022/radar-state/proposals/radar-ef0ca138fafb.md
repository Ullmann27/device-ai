# Vorschlag radar-ef0ca138fafb: TurboSparse-LLM: Accelerating Mixtral and Mistral Inference via dReLU Sparsity

Quelle: https://arxiv.org/html/2406.05955v1 (Code-Lizenz: unknown; nur Idee als Anregung, kein Code übernommen)
Experiment: NF-4 · vorläufige Einstufung: FEASIBLE (local hardware named: "CPU")

1. **Mechanismus:** Argues that plain ReLU is not sparse enough and proposes dReLU plus data mixing; speed is evaluated for pure CPU, hybrid GPU-CPU and phone inference according to the authors.
2. **Bekannte Methode mit ähnlichem Zweck:** die Quelle selbst; Vergleich mit der Lab-Baseline des Experiments.
3. **Was wäre an unserer Variante anders:** vor dem Lauf auszufüllen (eigene Implementierung im LM1-Autodiff).
4. **Widerlegende Messung:** VALIDATION-BPB bzw. Aufgabenerfolg nicht besser als die unveränderte Baseline bei gleichem Budget (Seeds 1–3, vollständige Trennung).
5. **Kosten:** Parameter, ms/Token, Spitzen-RAM auf CPU messen; keine Annahmen aus dem Paper übernehmen.
6. **Abbruch:** NaN/Instabilität, Budgetüberschreitung oder kein Vorteil nach dem vorab festgelegten Budget.
