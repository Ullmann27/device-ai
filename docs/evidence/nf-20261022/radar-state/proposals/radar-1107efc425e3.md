# Vorschlag radar-1107efc425e3: ReLU Strikes Back: Exploiting Activation Sparsity in Large Language Models

Quelle: https://arxiv.org/abs/2310.04564 (Code-Lizenz: unknown; nur Idee als Anregung, kein Code übernommen)
Experiment: NF-4 · vorläufige Einstufung: INTERESTING (matches a lab topic; no size or hardware statement found)

1. **Mechanismus:** Argues for reinstating ReLU in language models: activation choice barely changes accuracy after fine-tuning, and the resulting sparsity cuts inference compute of OPT models by about 32 percent per token according to the authors.
2. **Bekannte Methode mit ähnlichem Zweck:** die Quelle selbst; Vergleich mit der Lab-Baseline des Experiments.
3. **Was wäre an unserer Variante anders:** vor dem Lauf auszufüllen (eigene Implementierung im LM1-Autodiff).
4. **Widerlegende Messung:** VALIDATION-BPB bzw. Aufgabenerfolg nicht besser als die unveränderte Baseline bei gleichem Budget (Seeds 1–3, vollständige Trennung).
5. **Kosten:** Parameter, ms/Token, Spitzen-RAM auf CPU messen; keine Annahmen aus dem Paper übernehmen.
6. **Abbruch:** NaN/Instabilität, Budgetüberschreitung oder kein Vorteil nach dem vorab festgelegten Budget.
