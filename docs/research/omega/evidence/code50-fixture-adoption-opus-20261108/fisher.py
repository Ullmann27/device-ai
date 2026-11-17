"""Einseitiger exakter Fisher-Test (hypergeometrisch, nur Standardbibliothek) fuer die Code-50-Entscheidungsregel.

Aufruf: fisher.py <rounds.jsonl>
Vorhandene Lead-Daten (von mir per JUnit-Parser nachgeprueft): Stress A 2/6 + Harness A 1/5 = 3/11, B 0/6 + 0/5 = 0/11.
Eine Runde zaehlt als Fehlrunde, wenn mindestens ein Testfall fehlschlug oder der Exit-Code != 0 ist.
H1: Variante A (500 ms) hat die hoehere Fehlerwahrscheinlichkeit als B (1500 ms).
"""
import json
import sys
from math import comb

EXISTING = {"A": (3, 11), "B": (0, 11)}  # (Fehlrunden, Runden)


def fisher_one_sided(a_fail, a_n, b_fail, b_n):
    m, n = a_fail + b_fail, a_n + b_n
    return sum(comb(a_n, k) * comb(b_n, m - k) for k in range(a_fail, min(m, a_n) + 1)) / comb(n, m)


new = {"A": [0, 0], "B": [0, 0]}
with open(sys.argv[1], encoding="utf-8") as handle:
    for line in handle:
        row = json.loads(line)
        new[row["variant"]][0] += 1
        new[row["variant"]][1] += 1 if (row["failed"] or row["exit"] != "0") else 0
a_n, a_f, b_n, b_f = new["A"][0], new["A"][1], new["B"][0], new["B"][1]
print(f"neue Runden: A {a_f}/{a_n} Fehlrunden, B {b_f}/{b_n} Fehlrunden")
print(f"nur neue Runden:    einseitiges Fisher p = {fisher_one_sided(a_f, a_n, b_f, b_n):.5f}")
pa_f, pa_n = a_f + EXISTING["A"][0], a_n + EXISTING["A"][1]
pb_f, pb_n = b_f + EXISTING["B"][0], b_n + EXISTING["B"][1]
p = fisher_one_sided(pa_f, pa_n, pb_f, pb_n)
print(f"gepoolt (Regel 2):  A {pa_f}/{pa_n}, B {pb_f}/{pb_n}, einseitiges Fisher p = {p:.5f}")
print(f"Bedingung (a) B hat 0 Fehler in neuen Runden: {'ERFUELLT' if b_f == 0 else 'VERFEHLT'}")
print(f"Bedingung (b) gepooltes p <= 0,10:            {'ERFUELLT' if p <= 0.10 else 'VERFEHLT'}")
print(f"A in neuen Runden mit 0 Fehlern (=> 'unentschieden'): {'JA' if a_f == 0 else 'NEIN'}")
