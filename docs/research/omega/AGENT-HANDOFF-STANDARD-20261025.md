# Device AI Continuum — Sicherer Agenten-Handoff und privater GitHub-Zugriff

Stand: 2026-10-25. Verbindlicher Forschungsprozess, kein Produktiv- oder Release-Gate.

## Zugriff auf das private Repository

- `Ullmann27/device-ai` besitzt die GitHub-Sichtbarkeit **private**. Das ist beabsichtigt und darf zur Fehlerbehebung nicht in `public` geändert werden.
- Ein berechtigter GitHub-Connector und ein externes Terminal sind zwei verschiedene Authentifizierungskontexte. Schreibrechte des Connectors bedeuten **nicht**, dass `git clone https://github.com/Ullmann27/device-ai.git` im externen Terminal authentifiziert ist.
- Nach `fatal: could not read Username for 'https://github.com'` denselben anonymen Clone nicht ohne neue Authentifizierung wiederholen. Mit GitHub-Connector direkt autorisierte Dateien lesen und schreiben oder einen gesondert freigegebenen, geheimnisgeschützten Terminal-Zugang verwenden.
- Niemals GitHub-PATs, OAuth-Tokens, private Schlüssel, Signiermaterial oder Kundendaten in Prompts, Commit-Logs, Testlogs oder Handoff-Dateien aufnehmen.
- Besteht kein vollständiger authentifizierter Checkout, dürfen GitHub-geholte Quell-Snippets nicht als komplette lokale Projektabnahme ausgegeben werden.

## Sicherungspflicht für Forschungsagenten

Nach jedem technisch brauchbaren und überprüfbaren Entwicklungsabschnitt:

1. Eigenen isolierten Branch verwenden; `main`, Release- und fremde aktive Forschungsbranches unverändert lassen.
2. Quellen und neue Tests vollständig mit passenden Pfaden sichern. Keine wichtigen ausschließlich flüchtigen Workspace-Dateien.
3. Tatsächliche Tests mit präzisen Befehlen, Laufzeitversionen und Ergebniszahlen dokumentieren. `NOT_RUN` und `FAILED` getrennt ausweisen.
4. Commit-SHA, verwendeten Ausgangs-SHA und GitHub-Draft-PR in einer Übergabedatei angeben.
5. Die Arbeitsgrundlage, Zweck und geprüften Sicherheitsgrenzen jeder Änderung erläutern.
6. Den **nächsten ausführbaren Entwicklungsschritt** mit Dateien, Anforderungen, erwarteten Negativtests und Erfolgsbedingungen benennen.
7. Bei technischem Schreibblocker alle Änderungen als vollständiges transferierbares Patchpaket bereitstellen; keine angeblichen Pushes ausgeben.

## Spezifischer aktueller Fokus: P-256 Besitzerfreigabe

- Forschungsbranch `research/continuum-sol-owner-consent-20261025`, Ausgangs-SHA `93b92c03cc8bc5df840edeaf0d48590bfeaed13b`; aktuelle Heads vor Änderungen selbst verifizieren.
- Noch nicht auf GitHub gesicherte lokale Prototypen für P-256-Signaturprüfung, Task-Hash-/Source-/Scope-Bindung oder persistente Nonces dürfen **nicht** als veröffentlichte Features bezeichnet werden.
- Eigentümeridentität und transaktionsgebundene Autorisierung sind getrennte Vertrauensgrenzen. Der Task-SHA-256-Fingerprint ist keine Signatur und kein Besitzernachweis.
- Negative Prüfungen mindestens: ungültige Signatur, fremde Key-ID, abgelaufene Freigabe, Task-/Commit-/Scope-Änderung, Replay, parallele Nonce-Nutzung, Crash vor/nach Reservierung, Widerruf und abweichende Host-Policy.
- Ein Research-Prototyp darf bestehende Android/Termux/CCC1-Gates nicht umgehen. Insbesondere `.github/continuum/coding-orchestrator.mjs::prepareCodingTask()` erzeugt bereits einen Grant und startet die Auftragsanlage.
- Signierte Freigaben nur in ausdrücklich autorisierter Testumgebung ausstellen und prüfen; kein produktiver Schlüsseltransfer, keine APK-Signierung, kein `main`-Merge.

## Maschinenlesbare Rückgabe je Meilenstein

```text
SOURCE_BRANCH:
SOURCE_COMMIT:
RESEARCH_BRANCH:
PUBLISHED_COMMIT:
DRAFT_PR:
CHANGED_FILES:
PURPOSE:
IMPLEMENTED:
INTEGRATED:
TEST_COMMANDS_AND_RESULTS:
TESTS_NOT_RUN:
SECURITY_PROPERTIES_PROVED:
SECURITY_PROPERTIES_NOT_PROVED:
KNOWN_LIMITATIONS:
NEXT_FILES_AND_TASK:
ROLLBACK_OR_REVOKE_NOTES:
```

Dieses Dokument ist ein Übergabestandard. Es ist **kein Beweis**, dass der Sol-Prototyp bereits veröffentlicht oder die gesamte Android-App getestet wurde.
