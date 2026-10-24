# Dritte Kontrollrunde – unabhängiger Quellenreview

Geprüfter gemeinsamer Quellstand: **ec28d661e27090c91a3eb83707bf5f46376b97b5**.
Basis: Opus **d0f0892e156b9bcfa6a9a80d6fcd515173a62565**; attestierte Originalblobs **2ef71365f7efc3749d094adecf7640b1138a54f2**.
Separater Reviewer-Agent ohne Implementierungskontext, nur lesend. **Kein verbleibender Codeblocker im gelesenen Bereich; keine Besitzerfreigabe.**
Vollständige maschinenlesbare Pin-/Quellidentitäten: `release-source-audit-ec28d661.json`; nachvollziehbare Einzeldiffs: `source-diffs/`.

## Unabhängige Befunde und Reparaturen

1. Alter Activity-Executor konnte nach `shutdown()` einen vorgemerkten Reset ausführen: Verlauf einer Ersatzinstanz **2→0**, Besitzerfakten blieben erhalten. Behoben: AtomicBoolean-Lebenszeit in `AgentActivity.onDestroy` widerrufen; Prädikat erst **innerhalb CORE_LOCK** prüfen. Ergebnis muss `true` sein, bevor Sitzungs-ID/UI wechseln. Finale unabhängige Reproduktion **2→2**; Regression im echten Core.
2. CCC1-Abbruchdialog konnte nach einem Statuswechsel statt Auftrag A Auftrag B abbrechen. Behoben: konkrete ID anzeigen/erfassen, geändertes Ziel vor Dispatch ablehnen.
3. Verspätete Abbruchantwort A konnte die neue Abbruchidentität B entfernen; eine formal gültige Antwort für ein anderes Ziel wurde als Erfolg angezeigt. Behoben: Ziel zusammen mit eigenem ABORT-Callbackslot sichern, Antwort an dieses Ziel binden; neuere CCC1_TASK erhalten. Altes Pending ohne Ziel wird als nicht belegter Abschluss behandelt, nicht als bestätigter Abbruch.
4. v19-Build ließ die optionalen Android-Widgettests aus. Behoben: `-PcontinuumUiWidgetTests=true` im bestehenden Gradle-Releasegate. Alle22 bisherigen Pin-Zeilen bleiben unverändert.

Reviewer:69/69 gezielte Node-Prüfungen auf dem eingefrorenen Stand,0 Fehler/Skips. Tatsächliche Android-XML gelesen: Reset6/6, Builder-Lifecycle5/5, Abbruchantwort4/4. Die koordinierte vollständige Testreihe ist separat belegt und wird nicht durch diese Teilprüfung ersetzt.

## Elf abweichende Pins – inhaltliche Prüfung

| Datei | Kontrolliertes Verhalten |
|---|---|
| AgentActivity.kt | Explizite M5-Route mit ungesendetem begrenztem Entwurf, Shadow-Gesprächs-ID und geschützter Baseline-Neustart; Premium-Layout erhalten. |
| ContinuumMobileBuilderActivity.kt | Zwei feste Projekte, einmalige M5-Auswahl; neue Bestätigungen, Activity-/Job-/Abbruchzielbindung; kein Intent startet einen Build. |
| ContinuumMobileBuildPlan.kt | Feste Operationen und Enum-Auswahl des Rezepts; keine Modell-/Benutzershell. |
| ContinuumMobileResultPolicy.kt | Paket/Rezept/Plan/Quelle/APK gebunden; historische Belege, Host-Build und Geräteabnahme getrennt. |
| ContinuumMobileBuildResultReceiver.kt | Nicht exportiert, One-shot-Callback und Operation/ID-URI; MAIN/PROJECT_STATUS/ABORT getrennt, neu Abbruchzielbindung. |
| ContinuumWorkerChatRouter.kt | Ausdrücklicher Projektplanbefehl, maximal400 Zeichen; Steuerzeichen vor Normalisierung verworfen. |
| scripts/continuum-owned-app.mjs | Zwei feste Vorlagen, kein Überschreiben vorhandener Projekte; begrenzte Buildinputs, echte Toolbelege, unveränderliche APK-Prüfkopie, Recovery und Prozess-Lease. |
| BootstrapBrain.kt | Sicherheitskorrektur für installier/deinstallier in gemischten Geräteaufträgen. |
| ContinuumOwnedAppContractTest.kt | Striktere Projekt-/Planbindung. |
| ContinuumWorkerChatRouterTest.kt | Expliziter Planungsweg, Begrenzung, Zeilenumbruch-Ablehnung. |
| scripts/continuum-owned-app.test.mjs | Historische Recovery und Ablehnung gefälschter Build-/Quellnachweise. |

## Zehn bislang nicht attestierte APK-Quellen

| Datei(en) | Kontrolliertes Verhalten |
|---|---|
| AndroidManifest.xml | Neues M5-Labor nicht exportiert und nur in Preview/Owner-Preview aktiviert; bestehender Launcher, nicht exportierter Receiver erhalten. |
| continuum-owned-project-plans-v1.json; ContinuumOwnedProjectPlan.kt | Zwei kanonisch gehashte Offline-Pläne ohne Berechtigungen; feste Identitäten, Aufgaben und Budgets. |
| ContinuumCodingConversation.kt; ContinuumM5LabActivity.kt | Begrenzte Anforderungen, Revision/Hash/Einmaligkeit; ungesendete Änderungen und veraltete Dialoge entwerten Freigaben; Übergabe öffnet bestehenden Builder. |
| ContinuumCognitiveRuntime.kt; ContinuumLearningCore.kt | Sichtbare Baselineantwort erhalten, optionaler Shadow; Reset löscht begrenzten Gesprächskontext, keine Fakten/Gewichte. |
| ContinuumPublicStudyCycleResult.kt | Stopp während fehlgeschlagenem Schreibversuch wird STOPPED; kein erfundener Persistenzerfolg. Issue76-Gerätetest offen. |
| Ctx3Context.kt; Ctx3Shadow.kt | Beobachtende begrenzte Forschung, OFF bzw. ausdrücklich SHADOW; kein Antwortersatz, Faktenschreiben, Training oder Toolrecht. TEST3 bleibt FAIL. |

## Grenzen und Besitzerentscheidung

- Elf der22 Pins sind unverändert passend, elf bedürfen ausdrücklicher Zustimmung. Zusätzlich alle10 neuen/geänderten APK-Quellen und die strengere Release-Testzeile prüfen. Rollenbezeichnungen sind keine Freigaben.
- Builder ist bereits exportiert, ohne Aufrufer-Allowlist. Fremde Aufrufer können nur die öffentliche feste Auswahl/UI öffnen; Erzeugen/Bauen bleiben sichtbar bestätigungspflichtig. M5 und Ergebnisreceiver bleiben nicht exportiert.
- M4-Vorlagen sind unveränderlich. Ihr CCC1-Verifier prüft Buildbelege; beliebige automatische Reparatur freier Android-Projekte oder allgemeine App-Synthese ist damit nicht bewiesen. CCC1-Reparatur des Continuum-Checkouts nutzt weiterhin dieselbe vorhandene Pipeline und ihre unabhängigen Prüfläufe.
- Expliziter Sitzungsneustart ist abgesichert; Baselinehistorie bleibt profilweit in einer Datei. Keine allgemeine Baseline-Pronominalkorrektheit oder vollständige Isolation beliebiger paralleler Core-Aufrufer behaupten.
- Keine neue kostenpflichtige API-Abhängigkeit, Gewichtsänderung, Datenmigration oder Änderung der Premium-Hologrammquellen. Nativer Modelllauf/Fold7/Termux-IPC und Issue76 benötigen Gerätebelege.
-0.3.17/code18 ist byteweise heruntergeladen, gehasht und mit genau dem Besitzerzertifikat geprüft. Das ist kein Nachweis, dass Android nach einem code19-Update einen datenbewahrenden Downgrade zulässt.
- Besitzerziel bleibt0.3.18/code19, `at.heinz.deviceai.mentorpaired`, Zertifikat `2b05fa9de378fb6c6c5376143774a573897dc9c9151305f1c44010275c76a9cc`, ausschließlich vorhandener privater Render-Dienst. **SIGNING BLOCKED**, bis konkrete Quellenfreigabe und sämtliche Release-Gates vorliegen.
