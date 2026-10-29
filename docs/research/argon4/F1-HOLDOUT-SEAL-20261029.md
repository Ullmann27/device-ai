# ARGON4 F1 · Holdout-Siegel (vor der Namen/Nomen-Korrektur)

Lauf 1 auf DEV (Datei `f1-dialog-benchmark-run1-20261029.json`) zeigte: LONG_RANGE 0 richtig / 4 falsch / 8 Rückfragen,
Ursache großgeschriebene Nomen und Imperative in Füllsätzen als Schein-Personen. Bevor der Kernel korrigiert wird, ist
hier der Holdout festgelegt: andere Namen, Rollen, Orte und Füllsätze (`Argon4DialogBenchmark.dialogs(holdout = true)`).

- SHA-256 des kanonischen Holdout-Textes (`holdoutText()`): `82ad1bbfa8262a568ab21b962ae52b7ceee5a75d4a03fc4f5f193f49bae0d466`
- Regel: Holdout genau einmal nach der Korrektur laufen lassen; Ergebnis unverändert berichten, auch wenn schlechter.
- Grenze: ein Autor schreibt Prototyp und beide Sätze; synthetisch; kein Gemma, kein Gerät.
