# Wiederaufnahme: Forschungsfehler und Android-Worker, 21.10.2026

Branch: `astra/continuum-research-verification-20261021`.
Ausgangs-HEAD: `06a41709ac1eba32b3081e0aee63e450fce3b3dc` des Experimentbranches.
`main` unverändert. Die zwischenzeitlichen Quellenreparaturen bis 0.3.9 werden übernommen und geprüft, nicht erneut entwickelt. Der ältere lokale Sol-Arbeitsstand ist separat erhalten.

## Meilenstein 0 – tatsächlicher Stand

AGENTS.md, Issue #41, Experimentvertrag/-register und Astra-Resume-/Limitprotokoll gelesen. Betroffen: EXP-00/01/02/04/06/08/11/12. Keine neue Agentenarchitektur, keine Modellpromotion, keine Installation. ChatGPT-Restquote nicht zugänglich; keine Prozentangabe ableitbar.

Eine echte, begrenzte HTTPS-Abfrage der vorhandenen deutschen Wikipedia-API für `Asilomar Conference on Beneficial AI` lieferte in dieser Sitzung:

```json
{"batchcomplete":"","query":{"pages":{"-1":{"ns":0,"title":"Asilomar Conference on Beneficial AI","missing":""}}}}
```

Damit ist ein fehlender Artikel **jetzt** reproduziert. Der historische 08:21-Abbruch bleibt ohne damaligen Stacktrace nicht eindeutig zuordenbar. Der alte Fetch-Code warf bei fehlendem Auszug `IllegalStateException`; die inzwischen vorhandene Reparatur klassifiziert diesen Fall als SOURCE_UNAVAILABLE. Weitere HTTP-/JSON-/Transportfehler brauchen echte Verhaltenstests und feste Diagnosecodes.

Aktuell laufen unveränderte Baseline-Prüfungen: vollständige Node-/Script-Suite sowie Android Unit/Compile/Lint/Debug-APK (lokaler Debug-Signer, kein Download-/Installationskandidat). Ergebnisse werden nach Abschluss ergänzt.

Nächster kleiner Schritt: tatsächlichen Fetch über eine injizierbare HTTPS-Verbindung prüfen: fehlender Artikel, ungültige Antwort, 429/503, Timeout, Abbruch, Bytegrenze. Produktions-Host/Consent/Quellen- und Holdout-Grenzen erhalten. Danach die 20%-Teamleiterplanung und eine eng begrenzte Lücke im vorhandenen Coding-Worker prüfen.

Rollback: nur Änderungen dieses Prüfbranches zurücknehmen; persistente Telefonzustände werden hier nicht geöffnet oder verändert. Kein Push auf main oder den parallel bearbeiteten Experimentbranch.
