# Android-Computerpfad: technische Bestandsprüfung

## Quellenstand und Verantwortung

Geprüfte Basis: `9918891a6da1d74275b087a04f4aa86c79074d46`, Tree `cae47c3abaee1a378b4157f26abf7e6ec33df2c1`.
Der Integrator ordnet diesen Tree dem öffentlichen PR-799-Stand `5f87bdc610944b93d3173a83c5beeea315abeb66` zu.
Gezielte Composer-Korrektur: `25b86244e219dbf0401b32248c64adb794b15452`.
Ehrliche Rückmeldung bei vorhandenem Entwurf: `aba74577cb7b9d529509c980718bff6cfe1b97d1`.
Integration und Android-Gates liegen beim gemeinsamen Integrator; kein eigener Main-Merge, keine Installation.

## Tatsächlich angeschlossener Ablauf

Normales Diktieren füllt zunächst das Eingabefeld (`AgentActivity.kt:14155–14181`). Der bewusst aktivierte Gesprächsmodus führt erkannte Sprache über denselben Sendepfad wie Text (`13989–13999`). `sendCurrentInput` ruft den Computerparser auf, wenn kein Bot ausgewählt ist (`6361–6362`). Der Parser gewährt selbst keine Rechte.

Die installierte App wird lokal gesucht, gegebenenfalls ausgewählt und konkret bestätigt (`OwnerAppActionFlow.kt:40–100`; `AgentActivity.kt:6034–6105`). `AndroidDeviceAppActions.kt:19–22` verwendet tatsächlich `getLaunchIntentForPackage` und `startActivity(NEW_TASK)`. `VerifiedForegroundLaunch.kt:27–56` fordert danach ein neueres Fensterereignis desselben Pakets und derselben Beobachtungsfreigabe; ein erfolgreich übergebener Intent allein genügt nicht.

Der Controller bindet einen Auftrag an Paket, Aufnahmesitzung und Beobachtungsepoche (`OwnerComputerSessionController.kt:93–161`). Der aktuelle Computerpfad benötigt Android 14, aktivierte Bildschirmbeobachtung und Screenshotfähigkeit (`AndroidLiveComputer.kt:80–95`). Vor Eingaben müssen Quelle und Live-Bild gültig sein. Gesteuert wird über Accessibility-Knoten, ihre Rollen, Beschriftungen und IDs; das Monitorbild ist kein angeschlossener visueller Planer.

| Fähigkeit | Tatsächlicher Umfang |
|---|---|
| App öffnen | Generischer bestätigter Launcherweg für auffindbare installierte Apps. Der zusätzliche Computerparser kennt WhatsApp, Facebook, Dateien, Word und Excel. |
| Suchen | Ein eindeutiges Suchfeld finden oder öffnen, den exakten Suchtext setzen und nachprüfen. Ein Suchtreffer oder abgeschickter Suchlauf wird nicht pauschal behauptet. |
| Klicken | Echte `ACTION_CLICK`-Eingabe auf einen aktuellen, eindeutigen zugänglichen Knoten. Im Computerrezept: Suche, Kontakt oder Download-Ordner. Kein freier Koordinatenplaner. |
| Scrollen | Native Vorwärts-/Rückwärtsprimitive vorhanden. Das freigegebene Rezept erlaubt nur höchstens drei Vorwärtsschritte zur WhatsApp-Kontaktsuche. |
| Text setzen | Echte `ACTION_SET_TEXT`-Eingabe; exakter Text, höchstens 500 Zeichen. Der Workflow setzt Suchtext und WhatsApp-Entwurf. |
| Nachricht senden | Nicht freigeschaltet. `SEND_TOOLS` ist leer; der Android-Port verweigert SEND. Ein SEND_MESSAGE-Auftrag endet beim bestätigten Entwurf mit Aufforderung zum manuellen Versand. |
| Word/Excel bearbeiten | Öffnen und das gemeinsame Suchrezept sind vorhanden. Dokumentbearbeitung, Formeln oder freie Office-Arbeitspläne sind hier nicht implementiert. |
| Allgemeine Planung | Dieser Pfad ist ein festes Rezept aus fünf Zieltypen. Die örtliche Gesprächs-/Modellfunktion ist davon zu unterscheiden; aus dem Workflow geht kein freier Planungs-, Seh- oder Reparaturzyklus hervor. |

Quellen: `OwnerComputerIntentParser.kt:5–35,53–136`, `OwnerComputerWorkflow.kt:31–110`, `OwnerComputerTaskGrant.kt:34–105`, `AndroidLiveNodePort.kt:179–186`, `ToolRegistry.kt:68–102`. Zeilen beziehen sich auf die geprüfte Basis.

## Ergebnisprüfung

`ActionEffectVerifier` fordert eine neuere Beobachtung derselben Aufgabe, App, Epoche, Fenster-ID und Aufnahmesitzung mit verändertem Digest und erfüllter Nachbedingung (`LiveComputerActions.kt:55–72`). Text wird über den exakten Digest des beobachteten Eingabefeldes geprüft (`344–355`). Scrollen braucht ein gebundenes natives Scrollereignis mit numerischer Bewegung; eine beliebige Bildschirmänderung reicht nicht (`LiveScrollEvidence.kt:52–69`). Der Port prüft unmittelbar vor der tatsächlichen Aktion erneut Knoten, Fenster, Revision und Freigabe (`AndroidLiveNodePort.kt:161–186`). Unklare Wirkungen werden nicht automatisch wiederholt.

## Homebildschirm und Appwechsel

Ein produktiver Home-/Zurück-/Recents-Bedienweg gehört nicht zum geprüften Computerpfad. `chief/DeviceControl.kt:8–17,19–29` enthält solche Namen und Shellvorlagen ausdrücklich als unregistrierten Kandidaten. Im aktuellen `ToolRegistry` und produktiven Accessibility-Port ist dieser Kandidat nicht angeschlossen.

Der Appwechsel ist ein tatsächlicher Android-Appstart. Er erzeugt keinen virtuellen Android-Desktop im Cockpit und arrangiert keinen geteilten Bildschirm. Für gleichzeitig sichtbares Cockpit und Ziel-App wird eine bestehende Mehrfensteransicht benötigt. Im Vollbild beendet `AgentActivity.onPause` die aktive Computerbedienung; `onStop` beendet sie ebenfalls (`816–818,902–905`). Der Controller akzeptiert nur eine sichtbare Sitzung (`6199–6200`, Controller `119–127`). Ein anderer Paket-/Fensterkontext wird nicht automatisch als neues Bedienziel übernommen.

Rückfragen speichern keinen fortsetzbaren Teilplan: Der Workflow kehrt zurück, der Controller widerruft anschließend den Grant (`163`); der Activity-Aufruf übergibt dem Parser keinen `contextApp` (`6140`). Die bisherige Frage zum Ersetzen eines vorhandenen Entwurfs (`Workflow:99–102`) hatte daher keinen angeschlossenen kurzen Antwortpfad. Commit `aba74577cb` ersetzt sie durch die konkrete Aufforderung, den Entwurf direkt in der App zu prüfen oder zu ändern und anschließend einen neuen Auftrag zu starten. Der Entwurfsschutz bleibt bestehen.

## Behobener konkreter Fehler

Die bisherige Zeile `OwnerComputerWorkflow.kt:88` leitete die Composer-Sprache allein aus dem exakten Suchhinweis `Search` ab. Bereits erlaubte Hinweise wie `Search...`, `Search…` oder ein über seine ID erkanntes Suchfeld führten fälschlich zur Erwartung `Nachricht`. Ein korrekt geöffnetes englisches `Message`-Feld wurde dadurch abgelehnt.

Die Korrektur verwendet eine ausdrückliche Composer-Nachbedingung analog zur bestehenden Suchfeld-Nachbedingung. Sie akzeptiert genau ein neues, sichtbares, aktiviertes, editierbares, nicht sensibles Nachrichtenfeld mit `Message` oder `Nachricht`; widersprüchliche Such-/Sendefeld-IDs bleiben ausgeschlossen. Empfängerüberschrift, exakter Entwurf, bestehender fremder Entwurf, Eindeutigkeit, Freigabe und Versandverbot bleiben geprüft. Activity, Renderer und Animationslogik wurden nicht verändert.

## Tatsächliche Prüfung

- Vorher: frische reine Kotlin-Kompilation; 17 Workflow-Tests, genau ein Fehler bei `Search...`, erwartet VERIFIED, tatsächlich PAUSED.
- Nachher: frische reine Kotlin-Kompilation; **67/67 Tests bestanden** in Workflow, TaskGrant, LiveSearchClick, LiveComputerActions und IntentParser.
- Die anschließende reine Rückmeldungskorrektur `aba74577cb` änderte keine Kontrolllogik; dafür wurde kein zusätzlicher Testlauf behauptet.
- Beide Läufe seriell unter der gemeinsamen Build-Sperre. Exakte Quellen- und Abhängigkeits-Hashes stehen im [Prüfbeleg](../evidence/computer-composer-contract-20261112/receipt.json); Logs und negative Testergänzung liegen daneben.
- Eine zusätzliche native Robolectric-Regression `AndroidLiveNodePortTest.contactClickVerifiesObservedEnglishComposer` ist enthalten. Ihre Ausführung und die Android-Anschlusskompilation liegen beim Integrator; aus dem reinen JVM-Lauf wird kein Android-/Geräteerfolg abgeleitet.
- Vorhandene relevante Tests: `VerifiedForegroundLaunchTest`, `OwnerAppActionFlowTest`, `AndroidLiveNodePortTest`, `AndroidLiveScrollTest`, `LiveWindowSafetyTest`, `LiveComputerDispatchGuardTest`. Vorhandensein ist kein neu ausgeführtes Ergebnis.

Negativkontrolle erneut herstellen: Basis `9918891a6d` in einem isolierten Checkout verwenden, `red-repro.patch` anwenden und `OwnerComputerWorkflowTest` ausführen. Auf dem korrigierten Stand die fünf oben genannten Klassen ausführen. Keine echte App-Nachricht wurde gesendet oder Geräteaktion ausgelöst.
