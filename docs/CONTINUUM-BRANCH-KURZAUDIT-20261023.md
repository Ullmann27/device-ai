# Kurzaudit der Integrationslücken – 23.10.2026

Zeitlich begrenzter Quell-/Integrationscheck; keine APK, keine Modellpromotion.
Alle Remote-Branches explizit geholt: der lokale origin-Fetch war auf einen
Branch begrenzt; ein gewöhnliches `git fetch origin` zeigte daher nicht alle
neueren Zweige. Keine fremden Worktrees oder Remote-Branches verändert.

## Aktuelle Bezugspunkte

- Signierte Basis: `9fb4361c` (Code17-Quelle `6706da14`).
- UI, PR #91: `942b955417be2cd758eab4b19b6fa9c30a7185f9`.
- Aktive KI-Arbeit, PR #92: `524c1afc5325f376a0d102e3dba9378c983dc10b`; Opus arbeitet weiter.
- #75 LM1 (`6919264d`) und #82 NF (`14702b0d`) sind in #92 bereits
  kontrolliert übernommen. Nicht nochmals komplett mergen.
- #81 AAA-UI (`c717a282`) ist Vorfahr von #91. #79/#80/#83 sind dadurch
  bereits übernommen beziehungsweise überholt; keine Doppelübernahme.
- Checkpoint-Datenzweige `opus/lm1-checkpoints-20261022` und
  `opus/nf-checkpoints-20261022` bleiben getrennt; niemals als Code mergen.

## Tatsächlich geprüft

`git merge-tree --write-tree 942b955417be2cd758eab4b19b6fa9c30a7185f9 524c1afc5325f376a0d102e3dba9378c983dc10b` liefert konfliktfrei
Baum `361b029620ac63524f7525f7ec05bc3cda3cb9e7`. Keine Überschneidung der seit
`9fb4361c` geänderten Dateipfade. Das ist Quellverträglichkeit, kein Android-Build.

Diesen kombinierten Baum mit `git archive` in ein separates temporäres Verzeichnis
exportiert und dort mit Node 22 ausgeführt:

```sh
node --test .github/continuum/ccc1-worker.test.mjs \
  .github/continuum/ccc1.test.mjs \
  .github/continuum/behaviour-disambiguation.test.mjs
```

**25/25 bestanden, keine Fehler/übersprungenen Tests.** Umfasst echte temporäre
Coding-Jobs, Quittungsmanipulation, Besitzer-Gates, Mehrdeutigkeit, begrenzte
Reparaturrunden und Wiederanlauf nach SIGKILL. Beleg:
`docs/evidence/ui-surfaces-20261023/branch-combination-node.tap`.
Kein Modell-Benchmark, kein erneuter Zugriff auf verbrauchte LM1-Finaltests.

## Wichtigste noch offene Verbindungen

1. **GGUF/llama.cpp-Weg fehlt im signierten Integrationsstand.** Verifizierbare
   ältere Verbesserung: Commit `3f09c542`, u. a. `scripts/continuum-gguf.mjs`,
   `continuum-local-context.mjs`, zugehörige Tests und Korrektur der strukturierten
   llama.cpp-Anfrage in `continuum-local-brain.mjs`. Enthalten z. B. in
   `opus/continuum-mobile-coding-e2e-20261022` und #75, aber nicht in der
   aktuellen signierten Basis. #92 nennt diese ausgelassene Coding-Kette selbst.
   Nächster Integrationsauftrag: Diff dieses Commits samt Importabhängigkeiten
   gegen den dann aktuellen #92-HEAD prüfen, gezielt übernehmen und lokale
   GGUF-/Context-/Brain-Tests ausführen; keine Komplettübernahme der älteren App.
   Historischer x86-Inferenzbeleg ist keine Samsung-/ARM64-Abnahme.
2. **LM1-Runtime in #92 ist noch kein Android-Chatpfad.** Hash-/Tokenizerprüfung
   und begrenzte minGRU-Inferenz existieren; Antwortqualität genügt laut Schritt 7
   nicht für Besitzerantworten. Keine automatische Umschaltung eines funktionierenden
   Sprachpfads auf LM1. Ein späterer Adapter benötigt klare Herkunft/Qualitätsgrenzen.
3. **CCC1-Worker ist weiter als die PR-Kurzbeschreibung:** Commit `73958363`
   verbindet ihn mit der bestehenden lokalen Schleife; CLI und Experience-Log
   sind vorhanden und oben frisch geprüft. Android-/Termux-Rückkanal und sichtbare
   Aufgabensteuerung bleiben separat zu integrieren, keine Android-Autonomie behauptet.
4. **NF-7/NFC2 ist vorregistrierte aktive Forschung**, zuletzt Amendment
   `524c1afc` gegen Datenleckage. Nicht als fertige Fähigkeit aktivieren;
   Opus' laufenden Versuch und eingefrorene Daten unangetastet lassen.

## Nächster sicherer Einstieg

Nach Opus' Abschluss neue HEADs holen. Isolierten Integrationsbranch von der
signierten Basis erstellen, #91 und den dann eingefrorenen #92-Stand kombinieren,
danach kombinierte Node-/Kotlin-/JUnit-/Lint-Prüfung. Zuerst die fehlende GGUF-Kette
gezielt prüfen; APK erst im späteren kontrollierten Integrationsschritt.
Hier wurde bewusst kein aktiver Forschungsbranch gemergt oder main verändert.
Dieser Audit verändert nur Dokumentation/Evidenz, Rollback per Revert.
