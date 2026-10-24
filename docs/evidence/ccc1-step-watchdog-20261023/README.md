# CCC1: Besitzer-Abbruch beendet auch den laufenden Prüfschritt (23.10.2026)

**Vorher** (`c397ff1b`, Beleg `../ccc1-e2e-20261023/cancel-step-boundary/`): Ein Abbruch wirkte erst an der nächsten
Schrittgrenze. Der gerade laufende Prüfschritt (Baseline-Testlauf, auf dem Telefon ein Gradle-Lauf von Minuten) lief
zu Ende; `ccc1-run-once` endete 38,7 s nach dem Start statt 70,6 s.

**Jetzt:** Für Besitzer-Reparaturaufträge (`executeOwnerRepairTask`) läuft jeder Prüfschritt über
`.github/continuum/step-watchdog.mjs`. Der kleine Wächterprozess startet den festen Prüfbefehl über `runOwnedCommand`
(eigene Prozessgruppe, über eine vererbte Lauf-ID verfolgte, auch abgelöste Nachfahren wie ein Gradle-Daemon), liest
alle 500 ms den Backlog-Auftrag und beendet den Schritt, sobald er `ABORTED` ist oder der wartende Worker nicht mehr
lebt. Danach läuft der Checkpoint auch **nach** jedem Prüfschritt: Ein Ergebnis aus einem abgebrochenen Lauf zählt
nie und wird nicht gelernt (vorher konnte ein Abbruch während des unabhängigen Laufs als gescheiterte unabhängige
Prüfung im Reparatur-Ledger landen).

Unverändert für den Aufrufer: stdout/stderr werden durchgereicht (bytegleich), Exit-Code und beendendes Signal werden
weitergegeben, eine spawnSync-Zeitüberschreitung bleibt eine Zeitüberschreitung (`timedOut`) und beendet jetzt die
ganze Gruppe statt nur des direkten Kindes. Ohne Abbruchwache (alle anderen Aufrufer) läuft alles exakt wie zuvor.

## Belege

- `mutations.jsonl` / `mutations.py`: zehn gezielte Fehler (Abbrucherkennung aus, Wache ignoriert, kein Checkpoint nach
  dem Schritt, kein Fail-closed, Exit-Code/Signal nicht durchgereicht, Elternprüfung aus, Ausgabe nicht vererbt,
  SIGTERM nicht weitergeleitet, Endsignal nicht erfasst). **Jeder** wird von genau den erwarteten Tests erkannt.
- Tests: `.github/continuum/step-watchdog.test.mjs` (7), neuer Ende-zu-Ende-Test in
  `backlog-repair-worker.test.mjs` (60-s-Prüfschritt, Abbruch aus einem anderen Prozess wie `ccc1-abort`: Schritt endet
  nach ≈1 s, Testprozess beendet, Auftrag `ABORTED`, kein Bericht/Patch, Arbeitsbäume entfernt), Signaltest in
  `scripts/continuum-owned-process.test.mjs`.

## Echter Lauf über die Termux-Aufrufe (`e2e/`)

Gleicher Ablauf wie `cancel-step-boundary`: frischer Klon von `opus/continuum-next-20261023` @ `02540e8d`
(`e2e/identity.txt`), dieselbe lokal eingeschleuste Regression in `console-policy.mjs`, exakt die Aufrufe der App
(`continuum-mobile-builder.mjs ccc1-*`), `ccc1-abort` 5 s nachdem der Worker den Auftrag übernommen hat
(Treiber: wie `cancel-midrun.mjs` der früheren Belege).

| Lauf | `ccc1-run-once` endet nach |
|---|---|
| ohne Abbruch (`../ccc1-e2e-20261023/cancel/`) | 70,6 s |
| Abbruch an der Schrittgrenze (`c397ff1b`) | 38,7 s |
| **Abbruch mit Schrittwächter (jetzt)** | **6,5 s** (Abbruch bei ≈5,6 s) |

Ergebnis unverändert: `DONE_INVESTIGATION` / CCC1 `ABSTAINED`, kein Patch, zweiter Abbruch `ALREADY_ENDED`, danach
`IDLE`. Nachher (`e2e/after-cancel.txt`): Backlog-Auftrag `ABORTED`; im Berichtsordner nur der 52-Byte-Kopf der
abgebrochenen Baseline-JUnit-Datei (kein Testfall, kein Bericht, kein Patch, kein unabhängiger Lauf); Checkout
sauber, temporäre Arbeitsbäume entfernt, kein übrig gebliebener Prozess.

## Grenzen

- Linux-Container, nicht das Fold7. Auf Android/Termux hängt die vollständige Bestätigung davon ab, dass `/proc` der
  eigenen Prozesse lesbar ist; sonst meldet die Quittung `terminationConfirmed:false` (der Abbruch zählt trotzdem).
- Der Wächter selbst schützt nicht gegen böswillige Prüfbefehle (feste, geprüfte Befehle; keine Sandbox).
