# Fable 5.1 · Code 51 · P1 Websuche aus Chat/Sprache ohne Button – Handoff (08.11.2026)

**Branch:** `fable/code51-p1-chat-web-research-20261108` (Basis `473f7972`)
**Draft-PR:** gegen `chatgpt/code50-qa-staging-20261108` (kein Main-Merge)
**Status:** `KOTLIN_UNIT_TESTS_CHECKED` – **nicht** `DEVICE_VERIFIED`; keine echte Netzrecherche in dieser Umgebung ausgeführt.

## Befund (Quellstand 473f7972)

| Eingabe | Alter Pfad | Verhalten |
|---|---|---|
| `Welche Inflationsrate hat Österreich aktuell?` (Frage) | `PublicKnowledgeTopicRouter.topic` + `FreshKnowledgeNeed` | ALWAYS_ON: automatische Recherche; ASK: Dialog – **funktionierte** |
| `Jarvis recherchiere die Inflationsrate …` (Befehl, kein Komma) | kein Fragewort → `topic == null`; `WebToolRequest.fromChat` verlangt `^recherchiere …` ohne Anrede | Modell-/Rückfallantwort, keine Recherche |
| `Suche im Internet nach Filament Android` | **`OwnerAppLookupIntent`** (`suche` ist App-Verb) → `find_app` | „keine passende startbare App gefunden“ |
| `Such nach …`, `Prüfe neueste Quellen zu …`, `Google …`, `Mach eine Recherche zu …` | nichts | Modellantwort ohne Quellen |

Der Web-Button (`researchArmed`) war damit der einzige zuverlässige Weg für Befehle.

## Änderung (keine zweite Netz-Pipeline)

- **Neu** `cognitive/OwnerResearchCommand.kt` (pure Kotlin): erkennt deutsche Rechercheaufträge – `recherchiere …`,
  `such/schau (im Internet/online) nach …`, `prüf(e) (die) neuesten/aktuellen Quellen/Infos zu …`, `google …`,
  `finde aktuelle Quellen zu …`, `mach eine Recherche zu …`, `was sagen aktuelle Quellen zu …`, `Recherche: …` –
  mit Anrede (`OwnerAppLookupIntent.stripAddress`), Höflichkeits-/Medium-Füllwörtern und führendem Artikel. Liefert
  nur das **öffentliche Thema** (2–200 Zeichen).
  - `null` bei Negation/Offline-Wunsch (eigene Regex **und** `NetworkTurnConstraint.forbidsExternalResearch`),
    bei Nicht-Befehlen (Fragen, App-Öffnen, interne Navigation).
  - `Refused` statt Senden bei persönlichen Markern (`PublicKnowledgeTopicRouter.looksPrivate`: Passwort/PIN/Token/
    mein(e)/Konto/Adresse/IBAN/…, Dialogbezug „was ich dir gesagt habe“), bei Link/Pfad/`@`.
- `PublicKnowledgeTopicRouter.looksPrivate(text)` neu (dieselben privaten Marker wie bisher, nur freigegeben).
- `AgentActivity.sendCurrentInput()`: Befehlserkennung **vor** `OwnerAppLookupIntent`; Autorisierung unverändert über
  `confirmWebRequest` → `ApprovedWebToolGate.authorizeAlwaysOn` (ALWAYS_ON) bzw. `prepare/approve` mit Dialog (ASK);
  OFF → `OwnerResearchCommand.offlineReply()` ohne Anfrage. Ausführung unverändert `runResearch(session)` →
  bestehende `ResearchEngine` (Quellen, Abrufzeit, Negativbefunde wie bisher im Evidenzfenster).
  Tool-Aktivität macht die Route sichtbar: `WEB-RESEARCH · Sprach-/Chatbefehl · dauerhafte Freigabe ALWAYS_ON ·
  öffentliches Thema: …` / `… einmalige Freigabe nötig …` / `WEB · AUS · keine Anfrage gesendet` /
  `WEB · zurückgehalten · …`.

## Tests

- `OwnerResearchCommandTest` (RED auf 473f7972): 15 natürliche Befehle → Thema; 15 Nicht-Befehle → `null`;
  5 Negationen → `null`; 8 private Aufträge → `Refused` (nichts gesendet); Längen-/Steuerzeichen-Grenzen; Aktivitätszeilen.
- Vollauf `scripts/android-kotlin-unit-check.sh` (JDK 21, außerhalb Gradle) → PR-Body.

## Offen – Fold7 Geräteabnahme (nicht behauptet)

1. ALWAYS_ON: „Jarvis recherchiere …“ per Sprache → Evidenzfenster mit echten Quellen und Abrufzeit; ASK: Dialog erscheint
   einmal; OFF: Hinweis, kein Netzverkehr (z. B. per Diagnose/Netzmonitor prüfen).
2. „Suche im Internet nach …“ landet nicht mehr in der App-Suche.
3. „Recherchiere meine Adresse“ → Rückhalt-Antwort, kein Request.
4. Nicht Teil: Qualität der Rechercheergebnisse selbst (`ResearchEngine` unverändert).
