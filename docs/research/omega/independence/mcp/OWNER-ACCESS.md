# Lokaler MCP-Zugang und tatsächliche ChatGPT-Grenze

Stand: 03.11.2026. Der Coding-Dienst arbeitet unabhängig von diesem Beobachter. Die vier Werkzeuge `get_status`, `list_agents`, `list_tasks`, `get_evidence` lesen seinen bestehenden Zustand. Sie nehmen keine Aufträge an und ändern keine Freigaben. Eine MCP-Abbruchnachricht beendet höchstens einen Lesevorgang, keinen Coding-Auftrag.

## Lokal starten

Voraussetzung ist die gültige Konfiguration des Coding-Dienstes einschließlich seines vorhandenen Profilbestands. Der MCP-Beobachter wird aus demselben geprüften Quellstand gestartet. Linux-Host und Node 24 sind hier getestet; Android/Fold7 ist eine gesonderte offene Abnahme.

```sh
install -d -m 700 "$HOME/.config/continuum-mcp-owner"
node scripts/continuum-coding-mcp.mjs init-token \
  --token-file "$HOME/.config/continuum-mcp-owner/token"
node scripts/continuum-coding-mcp.mjs serve \
  --config /absoluter/pfad/zum/dienst/config.json \
  --token-file "$HOME/.config/continuum-mcp-owner/token" \
  --port 8766
```

Der Zugangsschlüssel wird mit Modus 0600 erzeugt und nie ausgegeben. Sein Verzeichnis benötigt 0700 und darf weder im Projekt noch in Quellbaum, Profilverzeichnis oder Dienstzustand liegen. Bestehende Dateien werden nicht überschrieben. Symlinks und Hardlinks werden abgewiesen. Ein lokaler Client liest den Schlüssel selbst aus der privaten Datei und setzt den HTTP-Header `Authorization: Bearer …`. Den Schlüssel nicht in URL, Befehlszeilenargumente, Chat oder Protokolle kopieren.

Der Prozess meldet die echte Adresse `http://127.0.0.1:8766/mcp`. Er bietet MCP Streamable HTTP 2025-06-18, initialisierte Sitzungen und JSON-Antworten. Ein Client sendet `Accept: application/json, text/event-stream`, initialisiert die Sitzung, bestätigt `notifications/initialized` und übernimmt `Mcp-Session-Id` sowie `MCP-Protocol-Version` für weitere Anfragen. GET liefert 405, weil kein optionaler SSE-Dauerstrom angeboten wird. Der vierteilige Werkzeugkatalog enthält die exakten Argument-Schemas.

Strg-C/SIGTERM stoppt den Beobachter und seine lesenden Unterprozesse. Der Coding-Dienst läuft unabhängig weiter. Entfernen, Ersetzen oder unsichere Dateirechte des Schlüssels entziehen den Zugang und verwerfen alte Sitzungen. Die lokale Betriebssystemidentität bleibt eine Vertrauensgrenze: 0700/0600 schützen nicht vor beliebigem Code mit derselben UID. Erzeugter Bot-Code muss weiterhin im tatsächlich abgesicherten CCC1-Executor bleiben.

## Ist ChatGPT bereits verbunden?

**Nein. `CHATGPT_CONNECTED = NOT_CONFIGURED`; kein echter ChatGPT-Client wurde angebunden.** Erfolgreiche lokale HTTP-Tests sind kein ChatGPT-Verbindungsnachweis. Die ChatGPT-Cloud kann diese Loopback-Adresse nicht direkt erreichen. Der private lokale Bearer-Schlüssel implementiert außerdem keinen OAuth-Discovery-/PKCE-Ablauf.

Am 03.11.2026 direkt geprüfte OpenAI-Dokumentation beschreibt zwei erreichbare Wege: öffentliches HTTPS oder einen Secure MCP Tunnel. Für kontobezogene Daten beschreibt sie OAuth 2.1 mit Resource-Metadaten, Authorization-Server-Metadaten, gebundenen Tokens und PKCE. Der Tunnel ist ein Transport und ersetzt diese Anmeldung nicht automatisch.

Für einen später ausdrücklich freigegebenen Anschluss sind deshalb konkrete zusätzliche Schritte nötig:

1. Verfügbaren ChatGPT-/Workspace-Zugang zu benutzerdefinierten MCP-Servern feststellen. Der hier gebaute Dienst benötigt diesen Zugang für seine lokale Coding-Arbeit nicht.
2. Die Übertragung der begrenzten Status-/Belegdaten und ihren Umfang ausdrücklich freigeben. Keine Freigabe für allgemeine Dateien, private Gesprächsarchive oder Schreibwerkzeuge daraus ableiten.
3. Eine unterstützte private Transportverbindung und authentifizierte OAuth-Erweiterung fertig implementieren und unabhängig prüfen. Aktuelle Dokumentation nennt Secure MCP Tunnel mit `tunnel_id`, Runtime-API-Schlüssel, Plattformberechtigungen **Tunnels Read/Manage** für Einrichtung sowie **Read/Use** für Betrieb und richtiger Workspace-Zuordnung. Zugangsdaten werden lokal eingegeben, nicht in dieser Unterhaltung. Die nötigen Kontowerte und Berechtigungen sind hier nicht nachgewiesen.
4. Erst bei nachgewiesenem Transport und unterstützter Anmeldung in [ChatGPT Plugins](https://chatgpt.com/plugins) **Plus → Add custom MCP server** öffnen, **Connection: Tunnel** oder den genehmigten HTTPS-Endpunkt wählen, Anmeldung konfigurieren und **Create as a plugin** ausführen. Danach nur die vier lesenden Werkzeuge prüfen.
5. Tatsächliche Discovery, Anmeldung, Statusabruf, Rechteentzug und abgewiesene Schreibaufrufe im echten ChatGPT-Client testen. Erst dann darf die Verbindung als hergestellt gelten. Schreibwerkzeuge bleiben auch dann ein eigener, noch nicht implementierter Auftrag mit Freigabeprüfung.

Hier wurde kein Tunnel eingerichtet, kein öffentliches Interface geöffnet, keine Verbindung angelegt und kein API-Schlüssel beschafft. Es entstanden keine zusätzlichen Dienste oder Modellkosten.

## Primärquellen

- [OpenAI: Connect and test your plugin](https://developers.openai.com/plugins/deploy/connect-chatgpt)
- [OpenAI: Authentication](https://developers.openai.com/plugins/build/auth)
- [OpenAI: Secure MCP Tunnel](https://developers.openai.com/api/docs/guides/secure-mcp-tunnels)
- [MCP 2025-06-18: Streamable HTTP](https://modelcontextprotocol.io/specification/2025-06-18/basic/transports)
- [MCP 2025-06-18: Lifecycle](https://modelcontextprotocol.io/specification/2025-06-18/basic/lifecycle)

Diese dynamischen Dokumente wurden frisch gelesen; spätere UI-/Tarif-/Protokolländerungen müssen vor einer Einrichtung erneut geprüft werden. Die Tunnel- und OAuth-Funktionen sind veröffentlichte externe Möglichkeiten, keine in diesem Branch erreichte Integration.
