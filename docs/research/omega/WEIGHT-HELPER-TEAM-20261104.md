# Modellgewichte – zusätzliches Helferteam, 04.11.2026

Status: WIP / RESEARCH_SHADOW. Drei zusätzliche Arbeitsagenten wurden im Helferchat tatsächlich gestartet. Dies ist kein dauerhaft laufender Android-Bot-Dienst.

## Auftrag und Anschluss

Heinz beauftragt die Unterstützung des parallelen Chats „Modellgewichte erweitern“: sechs bis zehn Millionen eigene, trainierbare Parameter; keine fremden vortrainierten Gewichte. Bestehendes LM1 und dessen eigenes Ausgangsmodell bleiben der Anschluss. Bloße Vergrößerung belegt weder zusätzlich gelerntes Wissen noch verbesserte Fähigkeiten.

- Führender Gewichte-PR: [#492](https://github.com/Ullmann27/device-ai/pull/492).
- Geprüfte Basis: `ee5779725690c573d3310c061c6e2e6faf5f0a8b`.
- Verifizierter Quellbaum: `fb5a31775ed8bee96a91a46f68011d8313217bf9`.
- Helferbranch: `research/continuum-weight-helper-team-20261104`; Draft-PR direkt auf dem Branch von #492.
- Relevante Experimente: EXP-04, EXP-05, EXP-07, EXP-12.

## Arbeitsteilung

1. `weights_architecture`: Parameterformel, 6–10-Millionen-Konfigurationen, funktionserhaltende Verbreiterung/Vertiefung und Trainierbarkeit neuer Freiheitsgrade.
2. `weights_training`: Fortsetzungstraining, Daten-/Checkpointbindung, wirksame Gradienten und nachvollziehbarer Nachweis echter Aktualisierung.
3. `weights_validation`: unabhängige Negativtests für Ressourcen-, Auswahl-, Integritäts- und Wiederanlaufgrenzen.

Der führende Chat behält seine laufenden Experimente, Originaldateien und Holdouts. Das Helferteam veröffentlicht kleine übernehmbare Änderungen und Belege auf seinem eigenen Branch. Bereits behobene Review-Hinweise werden vor einer Änderung am aktuellen Quellstand erneut geprüft; der alte Fehler mit standardmäßig bestandener Kapazitätsprüfung ist in der Basis bereits bearbeitet.

## Aktueller Prüfstand

- GitHub-HEAD, PR-Diskussion, Experimentvertrag und Versuchsprotokoll gelesen.
- Alle neun Dateien des Gewichte-PRs gegen ihre Git-Blob-Identitäten geprüft; der vollständige lokale Indexbaum entspricht der angegebenen GitHub-Basis.
- Eigene neue Funktionsprüfungen: NOT_RUN. Beiträge werden erst nach Quellenprüfung und tatsächlicher Ausführung als bestanden ausgewiesen.
- Zusätzliches Training im Helferchat: NOT_RUN. Kein Qualitätszuwachs, Geräteergebnis oder produktives Modell wird hier behauptet.

## Grenzen und Wiederaufnahme

Keine Änderung am aktiven Branch/Workspace des führenden Chats, keine Einsicht in versiegelte Holdouttexte, kein Training mit Prüfdaten, kein Modelldownload, kein kostenpflichtiger Dienst, kein Main-Merge, kein Release, keine Installation oder Modell-Promotion. Kein geänderter Paketname, Signaturvertrag oder Updatekanal. Private Datensätze und Gewichtsdateien bleiben außerhalb des öffentlichen Repositories.

Vor Wiederaufnahme aktuellen HEAD von #492 und diesem Helfer-PR prüfen, neue Kommentare abgleichen und ausschließlich noch offene Arbeit übernehmen. Tests, Quell-SHAs, genaue Änderungen und nächster Einstieg werden mit jedem überprüften Checkpoint ergänzt. Rückfall: Helferänderung nicht übernehmen; Originalgewichte und bestehender Lauf bleiben erhalten.
