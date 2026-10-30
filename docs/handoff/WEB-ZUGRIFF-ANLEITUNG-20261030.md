# Webzugang für Continuum – Agent Reach prüfen oder selbst bauen (Opus 5.5, 30.10.2026)

## Agent Reach (Panniantong/Agent-Reach) – Stand laut Fremdquellen, Repository hier NICHT geöffnet
- **Was:** MIT-lizenzierte Python-CLI bzw. Skill. Sie liest X, Reddit, YouTube, GitHub, RSS und weitere Plattformen sowie Webseiten, ohne Schlüssel je Plattform.
- **Kostenfrei:** keine Lizenzkosten. **Aber nicht dienstefrei:**
  - Seiten liest es über **Jina Reader**, einen Cloud-Dienst. Die angefragten URLs gehen dabei an Dritte.
  - Die Websuche läuft über **Exa**; laut Quellen ist teils ein kostenloser Schlüssel nötig, die Angaben widersprechen sich.
  - Plattformzugriffe nutzen teils Cookies. Daraus folgt ein Datenschutzrisiko.
- **Vor Nutzung prüfen:**
  - Lizenzdatei, Commit pinnen.
  - Welche Befehle Netzwerk und Cookies nutzen.
  - Ob Jina oder Exa abschaltbar sind.

## Anbindung an Continuum (ohne zweites System)
1. Agent Reach in einer **eigenen** venv installieren und auf einen Commit pinnen.
2. Einen kleinen MCP-stdio-Server wie `.github/continuum/mcp-git-readonly-server.mjs` schreiben.
   - Er bietet nur `web_read{url}` und `web_search{query}` und ruft darin genau einen festen Agent-Reach-Befehl auf; die Befehle aus dessen Doku übernehmen.
   - Keine freie Shell.
3. In `mcp-adapter.mjs` registrieren:
   - `approvedServers` exakt mit Pfad und Argumenten.
   - Allowlist `{web_read:{risk:'READ_ONLY',externalSideEffect:true},web_search:{…}}`.
   - Profil `PROTECTED`, solange der Besitzer nichts anderes freigibt. `externalSideEffect` erzwingt die Besitzerfreigabe.
4. Grenzen setzen:
   - Domänen-Allowlist, Zeitlimit, Ergebnisgröße (`maxResultChars`).
   - Keine Cookies oder Logins ohne ausdrückliche Freigabe.
5. Webinhalte bleiben **nicht vertrauenswürdige Daten**: Sie werden nie als Anweisung befolgt. Das gleiche Prinzip gilt schon im Coding-Agent-Loop.

## Selbst bauen: vollständig lokal, ohne Fremddienst
- **Lesen:** Node-`fetch` mit Zeit- und Größenlimit, dann Mozilla **Readability** (Apache-2.0) und die Umwandlung in Markdown (z. B. Turndown, MIT). Für JavaScript-Seiten optional **Playwright** (Apache-2.0, headless, isoliertes Profil).
- **Suche:** **SearXNG** selbst betrieben (AGPL-3.0, kostenlos, Meta-Suche, keine Schlüssel) auf PC oder Server. Auf dem Fold7 ist das möglich, aber ressourcenintensiv.
- **Feeds:** RSS/Atom direkt parsen; für YouTube und Reddit die öffentlichen RSS- und JSON-Endpunkte im Rahmen ihrer Nutzungsbedingungen.
- **Pflichten:**
  - robots.txt und Ratenlimits beachten, Ergebnisse mit Zeitstempel und Quelle cachen.
  - Jeder Abruf erzeugt eine Quittung (URL, Zeit, SHA-256) über den MCP-Adapter.
- **Reihenfolge:**
  1. `web_read` mit Readability als MCP-Werkzeug, mit Tests (Größe, Timeout, Domänensperre, Injection-Text bleibt Daten).
  2. SearXNG-Anbindung als `web_search`.
  3. Optional Playwright für dynamische Seiten.
  4. Erst danach Agent Reach als zusätzliche Quelle für Plattformen vergleichen.
