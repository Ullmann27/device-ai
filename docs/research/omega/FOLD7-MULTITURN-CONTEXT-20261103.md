# Continuum Lokal 0.3.38: Fold7 Mehrturn-Kontextverlust – Diagnose und Reparatur

Datum: **2026-11-03**. Private GitHub-Quellen: `Ullmann27/device-ai`. Bestehende installierte App: `at.heinz.deviceai.continuumlocal`, `0.3.38` / `versionCode=39`, originalsignierter Publisher `srv-dbf1tr2vcj2c73ehpfog`, veröffentlichte Quell-SHA `a9709854f48d2fd6589f9ce5ea97f6ec3be0d233`.

## Besitzer-Feldtest (Internet am Fold7 aus, App-Webzugriff "Nicht zulassen")

Der Besitzer hat die verbotene Speichern-Formulierung aus dem alten Routingtest entfernt und drei Nachrichten **im selben Dialog** gesendet, jeweils vollständig abgeschlossen.

1. Nutzer: `In einem Beispiel liegen in der roten Kiste 4 Ringe und in der blauen Kiste 7 Ringe. Welche Kiste enthält mehr? Bitte nur Farbe und Anzahl nennen.`
   - Antwort: **"Die blaue Kiste enthält mehr. Sie hat 7 Ringe, während die rote Kiste nur 4 Ringe hat."** – PASS.
2. Nutzer: `Nun hat die rote Kiste 9 Ringe statt 4. Welche Kiste enthält jetzt mehr? Bitte nur Farbe und Anzahl nennen.`
   - Antwort: **"Du hast mir nur gesagt, dass die rote Kiste jetzt 9 Ringe hat, aber ich weiß nicht, wie viele Ringe die andere Kiste hat."** – FAIL; die zuvor genannte blaue Kiste mit 7 Ringen fehlt.
3. Nutzer: `Welche Kiste hatte ursprünglich mehr Ringe, bevor sich die Anzahl verändert hat?`
   - Antwort: **"Ich habe keinen Zugriff auf deine früheren Beispiele ... müsstest du mir bitte die ursprünglichen Zahlen nennen."** – FAIL; Vorhistorie nicht in der Antwort berücksichtigt.

Der zweite Screenshot zeigt den dritten Modelllauf mit aktivem STOP; der erste Screenshot zeigt die fertige Antwort. Den Unterschied zwischen noch laufendem Modell und abgeschlossenem Antwortzustand weiterhin trennen.

## Quellpfad und Hypothese

Exakter veröffentlichter Code:

- `AgentActivity.runLocal` speichert nach erfolgreichem Modellturn `localConversationContext.record(sessionId, request, response)` und liest später `LocalConversationContext.render(...)` als `conversationContext`.
- `LiteRtLmLocalModelRuntime.stream` verwendet `freshPrompt=true` und `selectLocalConversation(..., freshPrompt, ...)`. Damit wird Gesprächsgeschichte **nicht** automatisch durch den nativen Conversation-Handle über mehrere eigenständige User-Turns garantiert: der Prompt muss sie erneut enthalten.
- `LocalPromptFrame.assemble` behandelte die gesamte `optionalHistory` als verzichtbar und ließ sie **ohne Fehlermeldung** weg, wenn `requiredBody + optionalHistory + requiredTail` die UTF-8-/Zeichenbegrenzung überschritt.

Dies ist eine **nachgewiesene riskante Codeentscheidung**, jedoch **keine bewiesene Ursache des konkreten Fold7-Laufs**: Ein tatsächliches Prompt-Readback/Token-Profil wurde nicht vom Gerät erhoben. Das Modell könnte trotz übermitteltem Text unzureichend darauf reagiert haben.

## Minimaler Patch in derselben ursprünglichen Architektur – Draft PR #365

Branch `repair/continuum-fold7-storage-verb-routing-20261103` (kein main-Merge).

- Neues, rein lokales `LocalConversationFollowUpPolicy` erkennt begrenzt Ausdrücke wie `Nun ... statt`, `ursprünglich`, `vorher`, `Korrektur:` und elliptische Anschlussfragen. Kein neues Gedächtnis, kein neuer Agent.
- `LocalPromptFrame.assemble(requireHistory=true)` darf eine vorhandene vollständige Vorgeschichte nicht mehr stillschweigend aus dem nativen Prompt entfernen. Bei zu wenig Platz wird der Turn **vor dem Modellaufruf** abgelehnt, statt ohne alten Sachverhalt zu raten. Der bestehende Prompt-/Tool-/Governance-Vertrag bleibt erhalten.
- `AgentActivity.runLocal`: fordert bei erkannten Anschlussfragen vorhandene frühere Owner-Turns und sicheren Verlauf, verlangt vollständige Aufnahme im erzeugten Prompt, protokolliert nur die **Anzahl** der übernommenen früheren Nutzerturns in der Tool-Aktivität. Fehlende Vorgeschichte liefert eine verständliche deutsche Rückfrage; übergroße Vorgeschichte wird als Kontextproblem gekennzeichnet, nicht beschnitten.
- Neue JUnit-Fälle: `LocalConversationFollowUpPolicyTest`, `LocalConversationFollowUpIntegrationTest`, Erweiterung `LocalPromptFrameTest`.
- Weiterer PR-Teil behebt das zuvor vom Besitzer erkannte `"speicher" in low`-Problem. Nach zwei Reviewhinweisen wurden die Unicode-Grenzen auf explizite Unicode-Letter/Digit-Lookarounds umgestellt und umgangssprachliche Speicherverb-Imperative (`Speicher das nicht`) von echten read-only Speicherprüfungen getrennt. Bestehende `BootstrapBrainTest`-Fälle ergänzt.

## Prüfungen und Risiken

- **Eigene isolierte Kotlin/JVM-Prüfung, JDK21:** Follow-up-Policy **11/11 PASS**; Speicher-Nomen/Imperativ-Erkennung nach Review **16/16 PASS**. Das reproduzierte Regelausdrücke und Logik separat, nicht die komplette Android-App/JUnit-Suite.
- **NOT_RUN:** voller Android-Gradle-/Robolectric-/JUnit-/Node-/Lint-Lauf des neuen Branch-HEAD, echter Prompt-Readback am Fold7, tatsächliche neue Gemma-Mehrturn-Qualität, Code40-APK.
- **Unbewiesen:** ob der konkrete Fold7-Fehler aus Prompt-Dropping oder aus mangelhafter Kontextnutzung **trotz** übermittelter Historie entstand. Die neue Anzeige `KONTEXT · <Anzahl> frühere Nutzerangaben ...` liefert für den nächsten Gerätelauf einen überprüfbaren Hinweis. Eine zusätzliche native Modell-/Prompt-Profilierung darf ohne private Textinhalte erfolgen.
- **Nicht** behaupten, dass Modell antwortet nur weil READY/Hosttests bestehen; der separate Gemma-Direkttest war allerdings auf dem Besitzergerät bereits erfolgreich (21.389 ms, `gemma4-e4b-it`, SHA `0b2a8980ce155fd97673d8e820b4d29d9c7d99b8fa6806f425d969b145bd52e0`).

## Sichere Fortsetzung

1. Aktuellsten PR365-HEAD frisch einlesen; alle Reviewhinweise mit echten JVM-Regressionsbefunden abgleichen. `BootstrapBrainTest`, `LocalPromptFrameTest`, alle neuen Follow-up-Tests und Android-Kerntests auf der exakten SHA ausführen und JUnit-XML sichern. Keine Testauslassung als PASS deklarieren.
2. Wenn QA grün: ausschließlich auf dem **bestehenden** originalsignierten Publisher, nach exakter Ownerfreigabe, `versionCode >= 40`, identisches Paket-/Signaturzertifikat. Keine zweite App, keine Deinstallation oder Datenlöschung, keine automatische Eigentümer-Installation.
3. Geräteabnahme dann dieselben drei Kistenfragen (ohne Web), sichere Prompt-Kontextzählung prüfen; weiterhin unrichtige Antworten als Modellqualitätsfehler markieren, selbst wenn der Prompt Kontext enthält. Bei Bedarf Fokus nur auf bestehende Conversation-/Prompt-Integration; kein zweites Gedächtnis.
4. Bis zur nächsten APK nur selbständige EIN-TURN-Denkfragen mit vollständig ausgeschriebenen Angaben als diagnostischen Workaround verwenden, nicht weitere mehrstufige Kontextfragen als bestanden markieren. Besitzer-Handy-Internet bleibt AUS; die nächste Netz-/Update-Prüfung wird ausdrücklich angesagt.

Rollback: Die minimalen PR365-Code-/Tests getrennt revertieren, keine Besitzer-Daten ändern.
