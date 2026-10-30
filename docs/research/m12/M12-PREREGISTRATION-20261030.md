# M12 · Grenzen des Aussagenprüfers – Vorregistrierung und Ausgangsmessung

**Stand:** vor jeder Änderung an `LocalModelClaimAudit`. Die Basis ist als `ClaimAuditV1Frozen` eingefroren.
**Siegel:** DEV `713307301fed444a784aa03da19d0a33d1910001512be6cb3b2243cd3dcdf77d` · **HOLDOUT `259369315f84cff53b0c68d81144c4b23ae049c6e6dff72da5e7ebad52be4661`** (80 Fälle, andere Satzvorlagen und andere Namen, Orte und Zahlen als DEV).
Labels entstehen per Konstruktion (bekannter eingesetzter Fehler oder bekannte treue Umformulierung) und nicht durch Modellurteile.

## Ausgangsmessung (heutiger Prüfer, Host-JVM)

| Satz | Fehler erkannt | Fehlalarme | Lücken (0/5 erkannt) |
|---|---|---|---|
| DEV | 35/50 | 0/30 | zusammengesetzte Aussage, überholte Angabe, Widerspruch im Beleg |
| HOLDOUT | 30/50 | 0/30 | dieselben drei + unbelegte Aussage ohne Großschreibung |

## Regeln für die Verbesserung

1. Änderungen werden nur an DEV entwickelt. HOLDOUT läuft **genau einmal** danach.
2. **Erfolgskriterium:** mehr erkannte Fehler auf HOLDOUT bei **höchstens 2/30** Fehlalarmen. Mehr Fehlalarme gelten als Misserfolg, auch wenn mehr erkannt wird.
3. Die Erkennung bleibt deterministisch, ohne Modellaufruf. Ergebnis ist weiter nur eine Trace-Stufe (SHADOW). Neu ist `CLAIM_AUDIT_CONFLICT` für Belege, die sich selbst widersprechen.
4. **Antwortpolitiken** werden gemessen: unverändert antworten · markieren · gezielt vertieft prüfen · bei widersprüchlichem Beleg rückfragen.

## Ergebnis Holdout-Lauf 1 (v2 = Commit `0fb1a510`), unverändert berichtet

| | erkannt | Fehlalarme | Widerspruch erkannt |
|---|---|---|---|
| v1 (eingefroren) | 30/50 | 0/30 | 0/5 |
| **v2** | **50/50** | **5/30** | 5/5 |

**Kriterium verfehlt** (≤ 2/30 Fehlalarme). Alle 5 Fehlalarme betreffen „umgestellte Reihenfolge“ (z. B. „Nach Dornbirn fliegt Tobias am 6. Oktober.“).
**Ursache:** Die neue Teilsatz-Trennung behandelt den Ordinalpunkt „6.“ als Satzende. Ort und Person landen dadurch in verschiedenen Teilsätzen, und die Attributbindung schlägt fälschlich an. Im DEV-Satz stand der Ort nie hinter einem Datum, deshalb blieb der Fehler dort unsichtbar.
**Konsequenz:** v2 ist **nicht** validiert. Die Korrektur wird gegen einen **neuen, vor der Korrektur versiegelten** Satz HOLDOUT2 geprüft. HOLDOUT1 wird nicht erneut für eine Erfolgsaussage verwendet.
