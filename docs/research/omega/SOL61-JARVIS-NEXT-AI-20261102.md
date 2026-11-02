# Continuum — Weiterverarbeitungsprotokoll für andere KIs

**Besitzer hat Funktionsarbeit gestoppt; Abschluss zuerst respektieren.**
Offizielles Repository `Ullmann27/device-ai`, offener Draft-PR361 auf PR360,
Branch `research/continuum-sol61-jarvis-gates-20261102`.

1. `git fetch`, aktuellen HEAD/PR-Reviews, `AGENTS.md`, Experimentvertrag und
   [Abschlussbericht](CONTINUUM-LAST20-CLOSURE-20261102.md) lesen. Getesteter
   Quellstand ist **48c0fb1f2b4db06619e170bf36f8d070e002eca4**; spätere Abschluss-
   Commits enthalten nur Dokumentation/Belege. Eine neue Codeänderung benötigt
   neue Tests. Die alten RED-Logs nicht als aktuellen Status behandeln.
2. Keine erneute Parallelarchitektur: Activity → bestehender ContextCompiler/
   ModelRuntime → BoundedAgent → ToolGate/Journal → TaskEvidence/ExperienceCompiler
   bleibt kanonisch. Modellwissen ist kein Besitzerwissen. CCC1 bleibt der eine
   Repair-/Workerpfad. GGUF und Avatar-Snapshots nicht als Gerätefunktion ausgeben.
3. Aktuelle Grenzen: 1.687 JVM- und1.969 Node-Tests ausgeführt/grün;3 bzw.13 Skips
   separat. Reale Gemma-/GGUF-Inferenz, Fold7 und Originalsignierung NICHT getestet.
   Hostmodelladapter/native Gegenproben sind Simulationen. Ein lokales HTTP-
   Protokollfixture ist kein verifiziertes Modell. CDX1-Gerät0/20 unverändert.
4. UNKNOWN, Quittungsfehler und Replay sperren den Aufgabenlauf auch gegen neue
   IDs. START_PENDING ist sichtbar, aber kein Ausführungsbeleg. Nie Quittungen
   löschen, Zustimmung aus einem Plan ableiten oder Side-Effect-Rollback behaupten.
   Bereits zugesagte Backends können fortwirken; tatsächlichen Nachzustand prüfen.
5. Ohne neuen Auftrag keine Features/Forschung beginnen. Falls der Besitzer die
   Geräteabnahme fortsetzt: bestehenden Fold7-/Termux-Status/Doctor auslesen,
   keine Pflicht-Neueinrichtung erfinden. [Besitzerprotokoll](CONTINUUM-FOLD7-OWNER-ACCEPTANCE-20261102.md)
   mit PASS/FAIL abarbeiten. Tatsächlich installierten Code/Signer zuerst prüfen.
6. Signierung nur mit Originalschlüssel auf dem bisherigen Publisher, expliziter
   exakter SHA-Freigabe und Freeze-Lift. Keine APK-Veröffentlichung/Installation,
   kein main-Merge, kein `approved:true`, keine Datenlöschung ohne Freigabe.
7. Spätere CCC1-Modellabnahme: auf isoliertem Besitzer-Workspace echte lokale
   Modellgewichte/Capabilities nachweisen, autorisierten Fehlerfall messen,
   RED→Kandidat→unabhängige GREEN-Prüfung→besitzerpflichtiger Vorschlag. Bestehende
   Host-Git-/Sandbox-Steuerungstests sind bereits grün; sie ersetzen diesen
   fehlenden Modell-/Android-Nachweis nicht.

Rohlogs und maschinenlesbare Übergabe:
`docs/evidence/sol61-jarvis-20261102/final-48c0fb1f/`.
Privates unsigned APK mit geprüftem Paket/Hash liegt im ursprünglichen Workspace
unter `/workspace/jarvis-qa/48c0fb1f/continuum-local-0.3.37-code38-UNSIGNED.apk`.
Ist der Workspace weg, aus dem exakten getesteten Commit reproduzieren; keinen
veralteten oder erfundenen Download-Link versprechen.
