# Continuum · VIDEO 4 – Claude, MCP und echte JARVIS-Automatisierung

**Auditdatum:** 2026-10-30. **Status:** SOURCE_REVIEWED, TRANSCRIPT_SAVED, INTEGRATION_NOT_PERFORMED, ON_DEVICE_NOT_TESTED.
**Original:** `352eb01fc3727a42309837f9dd2b6be9.mp4`, privates Drive-Objekt `194Af4-pesBXQxmC0UR7GyoRtZqEHgtWZ`, Originaldatei 6.687.921 Bytes, 87,433 s, H264 576x1024 + AAC.
**Transkript:** [38 zeitcodierte SRT-Blöcke](./JARVIS-CLAUDE-MCP-ORIGINALTRANSKRIPT-20261030.srt) aus Original-Audio, KI-generiert, Fachbegriffe gegen Bild überprüft; `Claude Design` als Sprecherbezeichnung nicht als Produktname verifiziert.
**Descript-Projekt:** https://web.descript.com/657128c5-05cf-4d37-91ed-23ab57b96c4a

## Film-Aussagen vs. unabhängige Evidenz

| Sprechzeit | Behauptung | Ergebnis | Relevanz |
|---|---|---|---|
| 00:00–00:10 | Auf Zuruf wird binnen 3 Minuten ein JARVIS-Dashboard in Claude Design gebaut | **NICHT NACHGEWIESEN.** Video zeigt UI und Sprecherbehauptung, keine nachvollziehbaren Commits, Build oder unabhängigen Laufzeitmessungen. | Anregung für vorhandenes `ContinuumJarvisMissionControlActivity`, kein Zweitprojekt |
| 00:11–00:15 | ElevenLabs verbindet Stimme mit Agent | **ECHT:** https://elevenlabs.io/mcp ; https://elevenlabs.io/pricing . Freie 10k Credits/Monat, aber kommerzielle Lizenz erst laut Tarif „Starter“. Gehosteter Dienst bleibt Cloud-Abhängigkeit; alter Open-Source-MCP-Server ist laut Maintainer veraltet: https://github.com/elevenlabs/elevenlabs-mcp . | Erst Android-lokale Speech/TTS-/VOICE-1-Schnittstellen evaluieren; ElevenLabs nur optional per Benutzer-Opt-in |
| 00:15–00:19 | Playwright MCP steuert einen Browser | **ECHT:** Offizielles Microsoft-Repo https://github.com/microsoft/playwright-mcp , Apache-2.0, Browser via Accessibility Tree/Tool-Calls; benötigt separate Browserlaufzeit, Android Fold7 nicht automatisch als produktive Laufzeit verifiziert. | Höchster neuer Wiederverwendungswert: externe, isolierte **PC-/Termux-kompatible** Tool-Sandbox nach unabhängigen Laufzeittests; die Haupt-App bleibt ohne Chromium-Pflicht |
| 00:20–00:25 | 2.459 Downloads, 4.289 USD Umsatz in 7 Tagen | **NICHT VERIFIZIERT:** keine roh nachlesbaren Analytics-/Konto-/Backend-Belege; Demo-/Sprecherzahlen | In Continuum-Dashboard nur verifizierte Receipts, keine erfundenen Geschäftsergebnisse |
| 00:25–00:31 | Tools über MCP anschließen | **ECHT als offener Standard:** https://modelcontextprotocol.io/specification/2025-11-25 ; jeder Connector bleibt getrennt und muss authn/authz/scope/beleggebundene Ergebnisse liefern | Bestehendes ToolGate/OwnerGate um Adapter- und Review-Vertrag ergänzen, kein neues Privileg aus Tool-Schema |
| 00:31–00:36 | 3 Short-Videos/Tag, Buffer MCP für automatisches Posting | **ECHT:** https://buffer.com/mcp ; Free: max. 3 Social-Channels, je 10 gleichzeitig eingeplante Posts, öffentliche offizielle Tarifangaben https://buffer.com/pricing ; Cloud-Social-API, nicht komplett lokal | Optionaler Content-Draft/Read-only-Pilot; Post-Scheduling/Publishing nur freigegeben |
| 00:36–00:46 | 475 USD Werbeausgaben, ROAS 1,5; Meta offiziell an Claude angebunden | **MIXED:** Meta Ads Connector https://mcp.facebook.com/ads nach aktueller Drittquellenüberprüfung erreichbar; kontoabhängige Freischaltung und Freigaben erforderlich; im Video keine echte Werbekonto-Kontoauslesung belegbar. | Für Continuum derzeit KEINE Anbindung produktiver Geldkonten; allenfalls read-only-Kampagnenübersicht nach Freigabe |
| 00:47–00:51 | 16 E-Mails, davon 13 selbst bearbeitet | **NICHT VERIFIZIERT:** Aussage ohne einzelne Nachrichten-ID, Ergebnisbelege oder Ausführungsprotokolle; automatisches Antworten kann gefährlich sein | E-Mail-Entwurf + Quellenprüfung vor Senden, Standardmodus nie automatische externe Nachrichten |
| 00:51–00:56 | Wiederkehrende Aufgaben / Routines ohne Wiederholungsprompt | **ECHT:** Claude-Routines/Tasks sind Cloud-Technik: https://claude.com/resources/articles/introducing-routines-in-claude-code , und lokal limitierte `/loop`-Tasks: https://support.claude.com/en/articles/14554000-claude-code-power-user-tips . | Wir haben bereits `ContinuumDeviceTeamleaderScheduler` auf Android. Erweitern, nicht duplizieren. Energie-/Thermal-/Cancel-Gates bleiben |
| 00:57–01:11 | Spezialagent Tom, Scout, Subagents und PR-Freigaben | **ECHT ALS ARCHITEKTUR**, nicht als vorgeführter verifizierter autonomer Erfolg. Unabhängiger Test und Rechtebeleg fehlen. | Bestehender M8 Typed-Coding-Agent + CCC1 und Task-/Owner-Verträge; keine Selbstfreigabe/Schreibberechtigung |
| 01:12–01:17 | Tägliches Briefing mit Terminen und To-dos | **EINFACH UMSETZBAR:** lokaler Scheduler + gültige freigegebene Events/Tasks + tatsächlich beobachtete Geräte-Quittungen | PRIORITÄT: kleines überprüfbares Daily-Briefing, ohne neue LLM-Abhängigkeit |
| 01:17–01:23 | „Kompletter Bauplan“ per Kommentar | **WERBE-CALL-TO-ACTION**, keine Lizenz-/Download-/Quellen-Evidenz | Keine fremden Skripte, Token oder Browser-Extensions blind installieren |

## Integration zuerst nach Effizienzgewinn, nicht nach Werbung

**P0 – aktuelle APK-Strecke bewahren:** PR #236/#257/#260 bilden die One-Tap-Update-Strecke, die noch aktuelle Android-Kompilation/Originalsignatur/Fold7-Test braucht. Dieser Video-Audit darf sie nicht überschreiben; **kein Auto-Merge nach main und keine P12-Übertragung**.

**P1 – vorhandene Strukturen zusammenführen:** `ContinuumDeviceTeamleaderScheduler.kt` als einzige Android-Scheduler-Instanz, bestehendes CCC1/ToolGate/OwnerGate, `ContinuumJarvisMissionControlActivity.kt` zur Anzeige realer belegter Arbeitsstände, EWM-v2 zum Gedächtnis. Ein neuer MCP-/Subagent-Layer wird nur als **Adapter** integriert.

**P2 – definierter MCP-Broker (offline-first):**
- Verbindungsprofile je Provider: `id`, `transport`, `trustRoot`, `origin`, `authMode`, `allowedCapabilities`, `maxCallsPerCycle`, `maximumExternalSpend=0`, `ttl`, `userReviewRequired`, `offDeviceAllowed`, `readOnly`, `shutdownTimeout`.
- Phasen: `DISCOVER` (nur Metadaten) → `CAPABILITY_GATING` → `PROPOSE` → `OWNER_REVIEW` → `EXECUTE` → `RECEIPT_VERIFY` → `AUDIT`; externe Modelltexte dürfen Gate/Reviewer/Tokenvalidierung nicht editieren.
- Security: Remote HTTPS + OAuth/PKCE/Audience, Tokens niemals in Chat/Logs/GitHub; statische erlaubte Endpoints, SSRF-/redirect-Gates, Modelloutputs untrusted, kein Catch-all. Offizielle Sicherheitsregeln: https://github.com/modelcontextprotocol/modelcontextprotocol/blob/main/docs/specification/2026-07-28/basic/authorization/security-considerations.mdx .
- MCP-Browser nur in getrenntem lokalen Rechnerprofil oder isolierter Sandbox, kein fremdes Profil-Cookie in laufende KI kopieren. Playwright selbst ist laut upstream **keine Sicherheits-Sandbox**.
- Monetäre, irreversible oder externe Aktionen (`ads_spend`, `post_publish`, `email_send`, `files_delete`, `system_control`, `release_sign`) bleiben per Default `DENY`. Nur konkrete Benutzerfreigabe pro signierter Aktion mit Limit und Ergebnisquittung. Kein Automatismus via Tool-Description.

**P3 – Daily-Briefing als erster Ende-zu-Ende-Gewinn:** on-device JobScheduler beim freigegebenen Zeitfenster, lokal gespeicherte offene Tasks, explizit autorisierte Kalender-, Build-, Fehler-, Forschungs- und Update-Daten. Jede Zeile mit `source`, `observedAt`, `stale`, `status`, `actionAvailable`, `permissions`; bei nicht verbundenem Konto ausdrücklich „nicht verbunden“. Kein fiktiver Umsatz/Leads oder selbst erledigte E-Mails. Tests: leere Quellen, frische Quellen, alte Quellen, Abbruch, Reboot, Batterie-/Thermal-Sperre, Zurückziehen der Rechte.

**P4 – Playwright MCP wiederverwenden statt Browser neu bauen:** Auf geeignetem lokalen Desktop/Server `@playwright/mcp` versionsgepinnt im isolierten Container/Benutzerprofil nach Lizenz-/SHA-/Dependency-Check. Test mit fünf eigenen/autorisierten Webseiten; Browseraktionen durchbestehendes ToolGate, begrenzter Domain-Allowlist, ohne Save-/Publish-Klick. Standalone-PC optional; Galaxy Fold7 benötigt kein Chromium-/Node-Monolith.

**P5 – Sprache/Voice:** Vorhandene `VOICE-1`-Forschung und Android-TextToSpeech/Spracherkennung prüfen; reproduzierbares Offline-Latenz-/Deutsch-WakeWord-Profil. ElevenLabs darf nach Preis-/Privatsphäre-Review nur **optionaler** Cloud-Provider sein, nicht Voraussetzung für offline JARVIS.

**P6 – Spezialagenten:** `Researcher` lesen, `Coder` Patchvorschläge in isolierter Worktree, `Verifier` unabhängig tests, `ReleaseObserver` nur signierte Metadaten und `Briefing` nur zusammenfassen. Kein Selbst-Merge oder OwnerGate-Override. M8-Benchmark 30 Aufgaben, nachgewiesener Blind-Gewinn statt erfundener Agenten-Titeln.

**P7 – optionale soziale Werkzeuge:** Buffer Free MCP mit OAuth read-only oder Draft; Meta read-only auf genehmigtem Konto; vor jeder kostenwirksamen Änderung Review, API-Quota, Budgetlimit; keine Daten aus Gedächtnis an externe Werbedienste ohne Einwilligung.

**P8 – Abnahmekriterien:** echte Android-Tests auf exakt demselben SHA, unabhängiger 240-Fall-EWM-Holdout, 30-Task-Coding-Benchmark mit *echtem* Modell, positive/negative Tool-/OAuth-/Prompt-Injection-Tests, RAM/CPU/Batterie und Netz-Captures, Besitzer-Verweigerungs-/Cancel-Test, Rollback. Fortschrittsstatus strikt `RESEARCH` / `SOURCE_INTEGRATED` / `TESTED` / `ON_DEVICE_VERIFIED`; nie Prozentzahlen ohne definierte Messung.

## Kosten-/Lizenzprinzip
Der kostenlose MCP-Standard ersetzt **nicht** kostenpflichtige API-Inferenz, Cloud-Telefonie, Social-API, Werbebudget oder die originale APK-Signatur. Kostenpflichtige Funktionen dürfen **durch eigene, unabhängig entwickelte Implementierungen oder lizenzkonforme freie Software ersetzt** werden; damit erwirbt man nicht das Recht, fremden Quellcode, Tokens, Markenzeichen oder Bezahlschranken zu kopieren/umgehen. `DEFAULT_EXTERNAL_AI_SPEND=0`, offline-first.

## Aktueller GitHub-Wahrheitsstatus
- Video-SRT 38 Cues im gleichen Forschungsbranch gesichert.
- Codebasis nach Readback: `ContinuumDeviceTeamleaderScheduler.kt` existiert; `DeviceToolExecutor.kt` existiert; `ContinuumJarvisMissionControlActivity.kt` existiert. Kernstatus und M6/M8/Update-Gates bleiben unverified.
- Dieser PR liefert Dokumentation/Primärtranskript; `NO_EXTERNAL_MCP_CONNECTED`, `NO_NEW_CLOUD_API`, `NO_ON_DEVICE_BUILD`, `NO_MAIN_MERGE`. Die oben genannten Integrationsschritte sind **Plan**, keine bereits erzeugten produktiven Funktionen.
