# Kollektive Gesprächssätze und Planning-UI – 11.11.2026

**Finaler Kotlin-Stand: 101/101 bestanden. Unabhängiger Quellenreview: OK.
Erneuter Android-UI-Lauf: NOT_RUN, beim Integrator.**

- Basis: `5b935b4feaf960bb6973dd4d630f02744e41913f`.
- Fortsetzung auf `work/omega-app-cancel-fixture-20261111`, Draft PR #781 gegen #769.
- Der eingefrorene Root-Quellstand wurde nicht verändert.
- Produktionsumfang: ausschließlich `cognitive/OwnerDeviceRequestSafety.kt`.
- Finale Produktions-SHA256:
  `97121251eec075051e677fbb164c7527e4e37184a31d1059ad282b89ec74d460`.

## Tatsächliche Befunde

Im gemeinsamen Android-Lauf `0fb80769-cffc-481d-99db-4c0a7ae6a4ba` scheitern zwei
Fälle aus `OwnerAppPlanningContextUiTest`:

1. `pluralStatementsReachConversationWithoutAppLookup`: Zeitüberschreitung bei
   „Ordinary statement reaches inference“.
2. `roleHeadersCannotReachReadyInferenceOrTheCorrectionShortcut`: Die Root-View
   nimmt den Test-Touch auf `jarvis-bottom:chat` nicht an.

Der erste Fehler ist ein Produktbefund. Der App-Parser verwirft „Wir starten“,
„Lass uns jetzt starten“ und „Morgen wollen wir das Projekt starten“ bereits als
App-Aufträge. Der spätere `OwnerDeviceRequestSafety` erkennt trotzdem `starten`
als Änderung. `BootstrapBrain.decide()` antwortet dann vor dem freien Dialog mit
einem Veto und `allowNeuralRecall=false`. Eine bereite Runtime kann nicht aufgerufen
werden. Alle drei Sätze wurden zusätzlich in getrennten Kotlin-Tests rot belegt.

Der zweite Befund betrifft den wiederholten Einstieg der Fixture. `submit()`
berührte vor jedem Turn die Home-Navigation. `addAssistantMessage()` lässt die
Activity jedoch auf `CHAT`; `refreshConversationLayout()` blendet dabei
`dashboardSection` aus. `JarvisHomeTestRoutes.controls()` prüft Sichtbarkeit nur
innerhalb von Home und kann daher kein erneutes Tippen auf Home im aktiven Chat
begründen. Die Fixture navigiert nun nur außerhalb von `CHAT` über denselben echten
Touch und verlangt danach ausdrücklich `CHAT` sowie `conversationActive=true`.
Die konkreten Rollen-/Korrektur-/Bot-Assertions bleiben erhalten. Der Wait-Fehler
des Pluraltests nennt nun zusätzlich den betroffenen Satz.

## Eng begrenzte Reparatur

Eine neue interne Kategorie `CONVERSATION` lässt ausschließlich die vollständige
einfache Kollektiväußerung zum normalen Dialog weiter. Die ganze normalisierte
Anfrage muss genau diese Klausel sein; nur abschließende `.`, `!`, `?` werden
entfernt. Zulässig sind die verankerten Kollektivformen, feste Zeitangaben und eine
optionale einfache Objektgruppe aus Artikel und einem auf 64 Zeichen begrenzten
Namen-/Nomen-Token. Es gibt keine freie Wortfolge im Zielteil.

Die Kategorie liefert **keinen** `readOnlyText`: „Wir starten die Apps“ ist dadurch
auch keine Berechtigung zur App-Inventur. Rollenprüfung, mehrere Mutationsverben,
Negation, spätere Freigabe und ausdrücklich lesende Diagnose behalten ihre
bestehende Reihenfolge. Das globale Mutationslexikon bleibt unverändert.

Zusätzliche Klauseln, Konjunktionen oder Imperativfolgen wie „Wir starten; sende
die Fotos“, „Wir starten, öffne WhatsApp“, „Wir starten. Kaufe X“, „Lass uns die
Fotos senden und dann starten“, „Wir starten mach Chrome auf“ oder „Wir starten
schalte das WLAN aus“ behalten das bisherige Veto. Auch komplexere gewöhnliche
Äußerungen außerhalb dieser kleinen Grammatik behalten konservativ das bisherige
Verhalten; dies ist keine allgemeine semantische Spracherkennung.

App-Suche, Bestätigung, `UNKNOWN`, Activity-Produktion, Modellkonfiguration,
Tokenlimits und Berechtigungen wurden nicht geändert.

## Quellengebundene Prüfung

| Phase | Fälle | Fehler | Bedeutung |
| --- | ---: | ---: | --- |
| Basis + neue Gesprächsregressionen | 11 | 5 | Alle drei Sätze einzeln rot |
| Erster Entwurf | 100 | 0 | Durch folgenden Review-Befund überholt |
| Review: Zusatzaufträge | 12 | 2 | Neue Ausnahme zu weit |
| Zweiter Entwurf | 101 | 0 | Durch direkten Imperativrest überholt |
| Review: direkte Imperativfolge | 12 | 1 | Freie Wortfolge weiterhin zu weit |
| Finale begrenzte Grammatik | 101 | 0 | 94 bestehende + 7 neue Fälle |

Die endgültige Prüfung umfasst dieselben 16 Klassen des bisherigen 94-Fälle-Laufs,
ergänzt um die neuen Methoden. Alle 21 kompilierten Quellen wurden nach dem Lauf
gegen das Eingabemanifest geprüft: keine Abweichung. Die gemeinsame Testdatei des
letzten RED und des endgültigen GREEN ist bytegleich. Frühere Zwischenstände sind
als überholt dokumentiert und wurden nicht als freigegebener Kandidat veröffentlicht.

Alle Läufe verwenden `flock /tmp/continuum-omega-gradle.lock`, einen JVM-Prozessor,
512 MiB Heap, Kotlin 2.4.0, JUnit 4.13.2 und JDK 21.0.13. Wie im vorigen reinen
Kotlin-Nachweispfad werden unveränderte Abhängigkeiten aus dem benannten bestehenden
Buildcache verwendet; die fünf betreffenden Produktionsdateien werden frisch
kompiliert. Das ersetzt keine gemeinsame Android-Kompilierung oder Geräteprüfung.

Rohlogs, Eingabemanifeste, exakte ausgewählte Android-Fehlerdatensätze, ursprünglicher
RED-Testpatch und überholte Produktionspatches stehen unter
`docs/evidence/omega-collective-start-20261111/`. `provenance.json` bindet die Phasen
an Quellen und SHA256. Der unabhängige Reviewer `/root/extensions` hat genau die
oben genannte finale Produktions-SHA gelesen; kein Test wurde durch ihn ausgeführt.

## Integration und nächste Prüfung

Die vorherige STOP-Fixturekorrektur steht separat in lokalem Commit `f84433ea9`,
der idempotente Planning-Einstieg in `71a71d89d`. Die Produktionsreparatur und ihre
Belege folgen als eigener Commit. Der Integrator prüft danach ungekürzt
`OwnerAppActionWorkSceneUiTest`, `OwnerAppPlanningContextUiTest` sowie den getrennten
Memory-Fixturestand in `OwnerObjectStateConversationUiTest` auf einem gemeinsamen
Quellstand. Diese erneute Android-Prüfung ist hier ausdrücklich **NOT_RUN**.

Keine eigene Gradle-Runde, kein APK-Build, keine Signierung und keine Installation.
Die getrennte MCP-Fixturearbeit bleibt pausiert.
