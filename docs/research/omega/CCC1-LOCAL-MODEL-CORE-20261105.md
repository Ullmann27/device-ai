# CCC1 – lokaler Modell-Core für die eigenen Coding-Bots (05.11.2026)

**Befund:** CCC1 hatte kein Sprachmodell (`lexical`/`probe`/`disambiguate`; `ccc1-lm1-core.mjs` fehlte).
**Neu:** `--core local-model` → `LocalModelLanguageCore` (`.github/continuum/ccc1-local-model-core.mjs`).

- Fragt ein **On-Device-Modell** über einen OpenAI-kompatiblen Endpunkt (`/v1/chat/completions`, z. B. llama.cpp-Server in Termux), `temperature 0`.
- **Nur Loopback** (`127.0.0.1`/`localhost`/`::1`); entfernte Hosts, Zugangsdaten in URLs und andere Protokolle werden abgelehnt.
- **Strenges JSON-Schema** `{"ranking":[{"id","score"}]}` mit nur gelisteten IDs; erfundene, doppelte oder ungültige Einträge → verworfen.
- **Fail-closed:** fehlender Endpunkt, HTTP-Fehler, Zeitüberschreitung oder ungültige Antwort → exakt die lexikalische Rangfolge; Grund in `core.last.reason`.
- Modell bewertet nur eine Vorauswahl (12) vorhandener Funktionen; es kann keine Funktion erfinden. RED→GREEN-Verifier, Freigaben und Abbruch bleiben unverändert.

## Start in Termux (Gerät)
```
llama-server -m <gguf> --host 127.0.0.1 --port 8080 &
export CONTINUUM_CCC1_MODEL_URL=http://127.0.0.1:8080 CONTINUUM_CCC1_MODEL=<name>
node .github/continuum/ccc1-cli.mjs run-once --project <repo> --state <dir> --core local-model
```

## Nachweis / Grenzen
Host-Tests mit echtem Loopback-HTTP-Server: 4/4 (+ Worker/CLI 10/10). **Nicht** geprüft: Qualität eines realen
Modells, Fold7/Termux-Ausführung, Latenz. Nächster Schritt: eingefrorener CCC1-Repair-Benchmark lexical vs. local-model.
