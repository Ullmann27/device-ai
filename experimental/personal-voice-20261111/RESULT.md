# Continuum – Persönliches Sprachlernen

## Forschungsbericht vom 11. November 2026

**Die begrenzte Forschungsrunde ist durchgeführt. Ein belastbarer Gewinn des Sprachverständnisses durch die neuen LM1-Gewichte wurde nicht nachgewiesen. Beide Gewichtskandidaten bleiben ausgeschlossen.** Nachgewiesene Teilfortschritte betreffen die Qualität und Prüfung der synthetischen Lernbeispiele, einen eng begrenzten Kontextadapter sowie die kontrollierte, verschlüsselte Ablage bestätigter Korrekturen. Diese Bausteine sind Forschungsimplementierungen und noch keine auf dem Fold7 abgenommene App-Funktion.

Die Hauptintegration liegt in [PR #753](https://github.com/Ullmann27/device-ai/pull/753). Genau drei spezialisierte Bots arbeiteten parallel: [Daten und persönliche Lernfreigabe, PR #756](https://github.com/Ullmann27/device-ai/pull/756), [native Modellgewichte, PR #754](https://github.com/Ullmann27/device-ai/pull/754) und [unabhängige Prüfung, PR #755](https://github.com/Ullmann27/device-ai/pull/755). Die Haupt-KI legte Budgets und Datenfreigabe fest, implementierte den Korrekturadapter und den geschützten Speicher, führte Ergebnisse zusammen und prüfte die Rohresultate erneut.

## 1. Was diese Runde beantwortet

| Frage | Nachgewiesener Stand |
| --- | --- |
| Versteht die installierte Continuum-App Heinz jetzt besser? | Kein Geräte- oder vollständiger App-Nachweis. Es wurde nichts installiert oder produktiv umgestellt. |
| Verstehen die neuen LM1-Gewichte unbekannte Anweisungen zuverlässig besser? | Nein. Beide Kandidaten scheitern an Gegensatzpaaren, Erhaltung und Fehlaktionsprüfungen. |
| Gibt es einen gemessenen Fortschritt im Kontextbaustein? | Ja, eng begrenzt: automatische Korrektur-/Kontextfälle 4/10 auf 8/10 gegenüber dem eingefrorenen Forschungsaltstand. |
| Können bestätigte Korrekturen technisch kontrolliert vorgemerkt werden? | Ja: lokale verschlüsselte Ablage, getrennte Zustimmung, Inhalts-/Versionsbindung, Prüfung, Änderung, Löschung und spätere Chargenbereitstellung sind implementiert und auf JVM geprüft. |
| Gibt es eine sichtbare Bedienkomponente? | Ein isolierter Android-Prüfdialog ist implementiert und gegen die Android-API kompiliert. Activity-Anschluss, tatsächliche Darstellung und Fold7-Bedienung bleiben offen. |
| Wurde die eigentliche Spracherkennung trainiert? | Nein. Keine akustischen Gewichte, keine echten Audio-/Referenztranskript-Paare und keine WER-/CER-Messung. |

Die fehlende Gesamt-App-Abnahme ist wesentlich: Der kleine LM1-Spezialist ist nicht das produktive Gemma-Sprachmodell. Seine Zahlen beschreiben vier Absichtsklassen unter einem festgelegten Testformat. Sie sind keine Genauigkeitsangabe für den normalen Continuum-Dialog.

Eine Abweichung vom gewünschten Ablauf bleibt ausdrücklich dokumentiert: Der native Kontextvergleich wurde erst nach den beiden Gewichtstrainingsläufen fertig ausgeführt. Er ersetzt daher weder den vorgesehenen vorgelagerten durchgängigen Sprachtest noch einen vollständigen A/B/C-Vergleich der aktuellen App. Die unabhängigen LM1-Prüfdaten und Übernahmegrenzen waren vor der Gewichtsprüfung eingefroren und wurden durch die späteren Kontext- und Datenschutzarbeiten nicht geändert.

## 2. Ausgangscode und vorhandene Entwicklung

Die Gewichtsversuche beginnen exakt auf [PR #728](https://github.com/Ullmann27/device-ai/pull/728), Commit `1d9eab1275a7fcc92ad4c5e2d1dd52d1db411667`. Die dortigen drei Kandidaten wurden wegen mangelnder Übertragung und Rückschritten nicht übernommen. Der unveränderte ursprüngliche Checkpoint besitzt 132.160 Parameter, eine native minGRU-Architektur, zwei Schichten, Dimension 64, ein Vokabular von 512 Tokens und ein Kontextfenster von 256 Tokens.

Dieser Forschungszweig beruht auf einem älteren App-Stand. Deshalb wurde zusätzlich der aktuelle Sprachcode gelesen: PR #747 an `23146e7f817b825e48a2b086f73da5cf28e0a274`, der Code53-Checkout `4515e71ab5d39319e54c710d97a1816d82b6202e` sowie der in PR #752 benannte Stand `fd86f11a4a822ee4805f4c462791baeb1d741db4`. Die geprüften vier Sprach-/Kontextdateien waren zwischen diesen aktuellen Ständen inhaltlich identisch.

Der aktuelle Code enthält bereits die Wiederverwendung des Android-Spracherkenners und eine begrenzte Wiederherstellung nach Client-/Busy-Fehlern. Normales Diktat führt in das editierbare Eingabefeld; der ausdrücklich eingeschaltete Gesprächsmodus kann eine erkannte Äußerung über `sendCurrentInput()` weitergeben. Vorhanden sind außerdem längerer Gesprächskontext, ein Situationsmodell und spezielle Bezüge auf Entwürfe/PDFs. Diese Arbeiten wurden erhalten und nicht nochmals gebaut. Ihre Existenz im Code ist kein neuer Gerätebeweis dieser Runde.

Auch eine bestehende `ResearchFeedback`-Logik mit Vorschlag, Prüfung, Bestätigung und Widerruf ist vorhanden. Sie ist bislang auf synthetische Forschungsherkunft begrenzt. Ein persönlicher Sprachdatensatz darf dort nicht als synthetische Quelle oder als bestätigter Besitzerfakt ausgegeben werden. Das bestehende Faktenjournal `SignedOwnerCorrectionJournal` wurde nicht für Trainings-Transkripte verwendet. Einzelheiten und Quellhashes stehen im [aktuellen Sprachaudit](data/CURRENT-SPEECH-SOURCE-AUDIT.md).

## 3. Was an den Lernbeispielen verbessert wurde

Der öffentliche Forschungsbestand besteht aus 204 ausdrücklich synthetischen Datensätzen:

- 128 ausgeglichene Kontrastbeispiele für das Training, 32 je Absichtsklasse;
- 32 neu formulierte einfache Öffnen-/Suchen-Beispiele zur Wiederholung alter Aufgaben;
- 32 Entwicklungsbeispiele mit getrennten Formulierungsfamilien;
- 12 reichhaltigere Diagnostikfälle für Korrekturen, Bezüge und mehrere Aufträge. Diese wurden nicht in eine falsche einzelne LM1-Klasse gepresst.

Die 64 historischen Öffnen-/Suchen-Prüffälle bleiben ausschließlich Prüfdaten. Eine früh erkannte Überschneidung mit alten Trainingsbeispielen wurde dadurch vermieden, dass neue Wiederholungsaufgaben verfasst wurden. Es wurden keine persönlichen Gespräche, Familieninformationen, echten Sprachaufnahmen oder privaten Transkripte in den Forschungsdaten veröffentlicht.

Alle 80 Gegensatzpaare in TRAIN und DEV enthalten innerhalb eines Paars denselben Wortbestand bei unterschiedlicher Bedeutung. Ein Verfahren, das nur Wörter zählt und ihre Anordnung ignoriert, kann auf diesen Paaren höchstens die Hälfte der Einzelaufgaben treffen. Das schließt andere oberflächliche Abkürzungen, beispielsweise feste Positions- oder Schablonenregeln, nicht vollständig aus. Deshalb verwendet die unabhängige Prüfung zusätzliche unbekannte Formulierungen und eigene Satzfamilien.

Das Datenformat trennt erkannten Wortlaut, bestätigte Wortlautkorrektur und bestätigte Bedeutung. Dazu kommen Gesprächskontext, frühere Aufgabenbezüge, gewünschtes Ergebnis, verbotene Auslegungen, Herkunft, Rechte, Freigaben und Prüfung sensibler Inhalte. Eine Wortlautkorrektur bestätigt nicht automatisch eine Interpretation. Eine Freigabe ist an den vollständigen Inhalt gebunden; Änderungen machen alte Zustimmungen ungültig. Eine Textfreigabe autorisiert keine Tonaufnahme. Der öffentliche Export lässt nur synthetische Forschungsbeispiele zu.

17 Daten- und Mutationstests bestanden. Der tatsächliche native Tokenizer kontrollierte die Trainingseingaben: höchstens 68 Präfixtokens gegenüber der festgelegten Grenze von 252. Der kleine LM1-Trainingsbestand besteht daher überwiegend aus kurzen, kontrollierten Sätzen. Ein Nachweis für minutenlange freie Sprachaufnahmen lässt sich daraus nicht ableiten. [Daten, Schema und Prüfungen](data/README.md)

## 4. Genau zwei echte Gewichtstrainingsversuche

Beide Kandidaten starteten vom unveränderten Originalmodell und verwendeten denselben Seed `20261111`, dieselbe Stichprobenfolge und jeweils genau 128 Aktualisierungen. Jeder Schritt erhielt acht beaufsichtigte Beispiele, zwei je bestehender Absichtsklasse: keine Aktion, öffnen, suchen und merken. Die Register, Datenhashes und Grenzen waren vor dem ersten Update veröffentlicht.

**Kandidat 1 – ausgewogenes Training und Wiederholung:** Beaufsichtigtes Lernen der vier Klassen, ausdrücklich beaufsichtigte Wiederholung von Öffnen/Suchen und zusätzliches öffentliches Text-Replay.

**Kandidat 2 – zusätzliche Erhaltung:** Derselbe Ablauf mit einer zusätzlichen KL-Erhaltung der ursprünglichen LM1-Ausgabeverteilung auf öffentlichen Replaytexten. Der Lehrer ist das unveränderte lokale Originalmodell. Es gab keinen externen Cloud-Lehrer und keine Übernahme fremder Modellantworten.

Es wurden echte native Gewichte verändert. Ein unabhängiger Parser prüfte Dateiprüfsumme, Architektur, Tokenizer, Tensorstruktur und endliche Float32-Werte. In beiden Kandidaten änderten sich sämtliche 132.160 numerischen Parameter; auch Weight Decay trägt zu dieser Zahl bei. Die Zahl geänderter Gewichte ist daher kein Fähigkeitsbeweis. Die unabhängige Rekonstruktion der Zufallsfolge bestätigte alle 128 Stichproben je Kandidat. Originalmodell und Lehrer blieben unverändert.

Es gab keinen dritten Kandidaten, keine zusätzliche Hyperparametersuche und keine Anpassung anhand der neuen Prüfergebnisse. Der vorhandene Trainingsweg besitzt keinen verifizierten LoRA-Anschluss. Ein zusätzlicher Klassifikationskopf wurde ebenfalls nicht eingeführt. [Vollständiger Trainingsbericht](training/RESULT.md)

## 5. Unabhängige Gewichtsprüfung

Vor der Auswertung wurden 64 neue Prüffälle mit 32 Gegensatzpaaren eingefroren, darunter 17 Paare mit identischem Wortbestand. Jede der vier Klassen kommt 16-mal vor. Die Datenprüfung fand zwischen TEST und TRAIN/DEV/Replay weder identische Eingaben noch identische Wortbeutel. Die Trennung erfolgte organisatorisch zwischen Bots im gemeinsamen Repository; sie ist kein kryptografisch abgeschotteter Geheimtest.

| Prüfung | Original | Kandidat 1 | Kandidat 2 |
| --- | ---: | ---: | ---: |
| Exakte richtige Antwort samt Endezeichen | 0/64 | 17/64 | 17/64 |
| Auswahl nur unter den vier Absichtsklassen | 16/64 | 17/64 | 17/64 |
| Vollständig richtige Gegensatzpaare | 0/32 | 1/32 | 1/32 |
| Vollständig richtige Paare mit identischen Wörtern | 0/17 | 0/17 | 0/17 |
| Alte Öffnen-/Suchen-Prüfung, eingeschränkte Auswahl | 32/64 | 18/64 | 25/64 |
| Ungültiges Antwortformat im neuen Test | 64/64 | 0/64 | 0/64 |
| Falsche gültige Aktionslabels bei 16 Fällen ohne Aktion | 0/16 | 16/16 | 16/16 |
| Längen der vier Textfortsetzungen | 7 / 4 / 32 / 32 | 0 / 4 / 2 / 1 | 6 / 4 / 3 / 1 |

Die entscheidende Trennung ist die zwischen **Antwortformat und Bedeutung**. Nach dem Training geben die Modelle zuverlässig eine Ziffer mit Endezeichen aus. Ihre Auswahl unter den vier Bedeutungen steigt jedoch nur von 16 auf 17 richtigen Fällen. Das Original liegt damit in dieser ausgewogenen Vier-Klassen-Prüfung auf dem Niveau einer konstanten Klassenantwort. Beide Kandidaten liefern dieselben neuen Testentscheidungen: 37-mal suchen, 26-mal öffnen, einmal keine Aktion und nie merken. Die einzelne Keine-Aktion-Antwort steht an der falschen Stelle.

Ein tatsächlich geprüfter Gegensatz zeigt das Problem:

> „Untersagt ist das Öffnen von Kiesel; erlaubt ist das Lesen des Namens. Erledige jetzt die erlaubte Handlung.“
>
> „Erlaubt ist das Öffnen von Kiesel; untersagt ist das Lesen des Namens. Erledige jetzt die erlaubte Handlung.“

Erwartet wird zuerst keine Öffnen-Aktion, danach Öffnen. Beide trainierten Kandidaten geben in beiden Fällen Öffnen aus. Diese Gegenprüfung belegt, warum mehr formal richtige Ziffern nicht als gelerntes Verstehen von Erlaubnis und Verneinung gewertet werden dürfen.

Die 16 falschen Aktionslabels sind ein **Prüfwert für mögliche Fehlaktionen**, keine ausgeführten Geräteaktionen. Das Original hatte dort ungültige freie Antworten; das ist keine korrekte oder sichere Rückfrage. Bei auf vier Klassen beschränkter Auswahl waren bereits beim Original alle 16 entsprechenden Fälle falsch. Das Training machte die falschen Entscheidungen zusätzlich formal verwertbar. Der Adapter verlieh keinem Modell Ausführungsrechte.

Auch die Erhaltung scheitert. Die eingeschränkte historische Prüfung sinkt von 32/64 auf 18/64 beziehungsweise 25/64. Schon der ursprüngliche Wert war kein Beleg für eine zuverlässig beherrschte allgemeine Gerätefunktion. Die beiden ursprünglich 32 Tokens langen Fortsetzungen schrumpfen auf zwei/einen beziehungsweise drei/einen Token. Kandidat 1 beendet eine zuvor nichtleere Fortsetzung sofort.

Die Textverlustkennzahl verbessert sich gleichzeitig von 2,8014 auf 2,6763 beziehungsweise 2,71395 Bits pro Byte. Gerade diese Kombination zeigt, weshalb ein niedrigerer Verlust den Verhaltenstest nicht ersetzen darf. Beide Kandidaten verfehlen mehrere vorab festgelegte Übernahmekriterien. **Entscheidung: beide ausgeschlossen.** Die Haupt-KI hat alle drei Rohprotokolle erneut geparst und die Messwerte und Ablehnungsentscheidungen unabhängig von den fertigen Zusammenfassungstexten nachvollzogen. [Unabhängige Auswertung](evaluation/WEIGHT-RESULT.md)

## 6. Was durch Kontext und bestätigte Korrekturen besser funktioniert

Der neue Forschungsadapter benutzt den vorhandenen `LocalConversationContext`. Er übernimmt atomar den vollständigen verfügbaren Besitzerkontext, bevor eine Korrektur den alten Gesprächsfokus verwirft. Unvollständiger Kontext wird nicht als sichere leere Historie behandelt. Wortlaut- und Bedeutungskorrekturen benötigen unterschiedliche Bestätigungen und passen nur zu genau dem Eingabetext, der Sitzung und dem Kontext, für den sie geprüft wurden.

Der unabhängige native Vergleich enthält 24 unveränderte Fälle und kompiliert echte vorhandene Quellklassen. Es wird keine Ersatzimplementierung des Kontextsystems getestet.

| Getrennter Teilbereich | Vorhandener Kontextbaustein A | Forschungsadapter B |
| --- | ---: | ---: |
| Automatische Korrektur-/Kontextfälle | 4/10 | 8/10 |
| Vom Menschen bestätigte Wortlaut-/Bedeutungsänderungen | 0/3 | 3/3 |
| Integrität der Korrekturfreigabe | 0/5 | 5/5 |
| Grenzen und Integrität der Eingabe | 0/2 | 2/2 |
| Unveränderte Erhaltung des Eingabetextes | 4/4 | 4/4 |
| Gesamtzahl erfüllter Komponentenprüfungen | 8/24 | 22/24 |

Keine der acht zuvor bestandenen Prüfungen verschlechtert sich. Die Gesamtzahl 22/24 ist **keine Sprachverständnisquote von 91,7 Prozent**: Sie enthält neue Freigabe- und Eingabeprüfungen sowie vom Menschen vorgegebene Korrekturen. Der automatische Teil enthält auch angemessene Rückfrageentscheidungen bei unvollständigem Kontext oder langen Korrekturen.

Konkrete Fälle:

- „Nein, so habe ich das nicht gemeint. Ich meinte den Zweig Sand.“ führt jetzt im geprüften Baustein zur Aktualisierung des Gesprächsfokus auf „Zweig Sand“.
- Bei „den anderen Entwicklungszweig“ liefert der geprüfte Baustein die Rückfrageentscheidung `CLARIFY`, solange der Gegenstand nicht eindeutig feststeht. Die tatsächliche Ausgabe einer Rückfrage in der App wurde nicht getestet.
- „Öffne Bilder“ kann nach ausdrücklich bestätigter Wortlautkorrektur als „Öffne Downloads“ weitergegeben werden. Die richtige Änderung wurde hier vom Menschen geliefert; sie ist kein neu gelernter akustischer Erkennungsgewinn.
- Nicht bestätigte, zu einem anderen Eingabetext gehörende oder veraltete Korrekturen werden abgelehnt.

Zwei unbekannte Formulierungen scheitern weiterhin: „Nein, das war anders gemeint. Ich bezog mich auf den Zweig Sand.“ und „Na, so hab i des ned gmeint. I man den Zweig Sand.“ Sie wurden nach Sichtung des Ergebnisses nicht passend gemacht. Der Adapter erkennt einen begrenzten Korrekturtyp; allgemeines österreichisches Sprachverständnis ist damit nicht bewiesen.

Jeder der 24 Fälle wurde außerdem mit allen vier möglichen beratenden Modelllabels geprüft. In 96 solchen Varianten blieb die eigentliche Kontextentscheidung unverändert; es entstanden keine Werkzeugfreigaben und keine Trainingsstarts. Das prüft die Abgrenzung von Modellvorschlägen. Es ersetzt keinen vollständigen C-Durchlauf mit neuen Gewichten in der App. Der Kontextbefund gilt für den Forschungsaltstand, nicht als belegter Gewinn gegenüber der vollständigen aktuellen Code53-App. [Native Kontextauswertung](evaluation/CONTEXT-RESULT.md)

## 7. Persönlicher Lernablauf und Benutzerkontrolle

Implementiert sind eine geschützte lokale Beispielablage und ein separater Android-Prüfdialog. Der Dialog zeigt den ursprünglichen erkannten Text und den Gesprächskontext, erlaubt eine getrennte Wortlaut- und Bedeutungskorrektur und verlangt eine neue ausdrückliche Bestätigung nach Änderungen. Beispiele können gelesen, ersetzt und gelöscht werden. Textlernen ist ausdrücklich aktivierbar; der Dialog weist darauf hin, dass keine Audioaufnahme implementiert ist. Er enthält keinen scheinbar funktionsfähigen Aufnahme-Schalter.

Die Ablage verwendet AES-GCM. Die Android-Fabrik sieht einen eigenen Keystore-Schlüssel und das private No-Backup-Verzeichnis der App vor. Hosttests prüfen echte Verschlüsselung, Manipulationserkennung und falsche Schlüssel. Sie belegen keine Hardwareabsicherung des Fold7, keinen Android-Keystore-Lauf und keine korrekte Darstellung des Dialogs auf dem Gerät.

Freigaben sind an Inhalt, Speicherbereich und eine fortlaufende Prüfversion gebunden. Die Zustimmung gilt gemeinsam für alle Speicherinstanzen; Dateisperren koordinieren den Zugriff. Widerruf und Löschung machen ältere Freigaben ungültig. Alte Dialogformulare dürfen keine neue Zustimmung für inzwischen veränderte Zustände ausstellen. Ein verzögertes Einschalten wird abgelehnt, wenn zwischenzeitlich eine neuere Freigabeentscheidung getroffen wurde. Das Textschaltelement verändert nur die Textfreigabe und erhält die aktuelle separate Audioentscheidung.

Die unabhängigen Prüfer fanden während der Umsetzung konkrete Fehler: zunächst den instanzübergreifenden Widerruf, wiederverwendbare Freigaben nach Löschung und blockierte Änderungen bei voller Ablage; später zwei verspätete UI-Zustimmungsabläufe. Die jeweiligen roten Vorher-Nachweise bleiben erhalten. Die implementierten Korrekturen besitzen gezielte Regressionsprüfungen und unabhängige Nachprüfungen. Die ursprüngliche volle Ablage verlor keine Daten; sie verweigerte lediglich die Änderung. [Vorher-/Nachher-Prüfung](data/store-review/README.md)

Der endgültige Stand besteht 34 JVM-Prüfungen für Datenschutz und Korrekturfreigaben. Zusätzlich bestehen vier unabhängig verfasste Speicherprüfungen und drei unabhängige Prüfungen gezielt verspäteter Einwilligungsaufrufe. Die letzte Android-API-Kompilation bindet genau die integrierten Quellhashes. Die drei Einwilligungsprüfungen führen den echten verschlüsselten Store aus; sie simulieren keine vollständige Android-Bedienung. [Abschließende Einwilligungsprüfung](ui/consent-checks-receipt.json)

Eine einzelne Korrektur startet kein Training. Geprüfte Beispiele werden entdoppelt und erst als kontrollierte lokale Charge bereitgestellt. Die Mindestmenge des Prototyps ist eine technische Schranke, kein wissenschaftlicher Nachweis ausreichender Datenqualität. Ein späterer Trainer muss vor dem tatsächlichen Verbrauch die gültige Freigabe erneut prüfen. Löschen eines Beispiels entfernt nicht automatisch dessen Einfluss aus bereits trainierten Gewichten; betroffene Kandidaten müssen ausgeschlossen oder vom bewahrten Original neu trainiert werden. Eine neuronale „Vergessensfunktion“ wird nicht behauptet.

Der Dialog und der Android-Speicheradapter sind kompiliert, aber nicht an `AgentActivity` angeschlossen. Es existieren keine automatische Aufnahme, Hintergrundsammlung, Cloudübertragung oder personenbezogenen Trainingsläufe dieser Runde. [Bedienkomponente und Grenzen](ui/README.md)

## 8. Tatsächliche Ressourcen

| Messung | Kandidat 1 | Kandidat 2 |
| --- | ---: | ---: |
| Gewichtsaktualisierungen | 128 | 128 |
| Trainingslaufzeit | 26,176934 s | 38,703392 s |
| CPU-Zeit, Benutzeranteil | 27,718781 s | 41,320978 s |
| CPU-Zeit, Systemanteil | 0,488150 s | 0,459306 s |
| Höchster Prozessspeicher | 119.768 KiB | 126.928 KiB |
| Gesicherte Modelldatei | 529.634 Byte | 529.636 Byte |

Zusammen ergeben sich 256 Aktualisierungen und 64,880326 Sekunden reine Trainingslaufzeit. Der höchste gemessene Prozessspeicher beträgt ungefähr 124 MiB. Die drei nativen Modellprüfungen brauchten zusammen 13,4178 Sekunden; deren eigene CPU-/Spitzenspeichermessung war im Auswerter nicht verfügbar. Der Kontextlauf benötigte 0,5164 Sekunden bei 52.088 KiB Spitzenspeicher. Diese Werte stammen vom Host, nicht vom Smartphone. Kürzere Ausgaben beeinflussen die Modellmesszeiten; sie sind daher kein Nachweis einer nützlichen Beschleunigung.

Vorbereitung, Kompilierung, Abhängigkeitsbeschaffung, Prüfprogramme und die Rechenarbeit der Haupt-KI/Bots sind nicht in den rund 65 Sekunden enthalten. Stromverbrauch, Akku, Temperatur und Fold7-Latenz wurden nicht gemessen. Es wurden keine zusätzlichen bezahlten GPU- oder externen Lehrmodell-Aufträge gestartet.

Separat erfasst sind 133,379003 Sekunden für die letzte gemeinsame Kompilation von Android-Prüfdialog, Einwilligungsprobe, Store, Bridge und Android-Fabrik sowie 0,284293 Sekunden für den dokumentierten 17-Fälle-Datentest. Für frühere Kompilationen und weitere Prüfungen liegt kein vollständiger gemeinsamer Ressourcenbeleg vor. Daraus wird keine Gesamt-Rechenzeit oder Gesamtkostenzahl errechnet.

## 9. Wissenschaftliche Einordnung und nächster sinnvoller Schritt

Verwendet wurden überprüfbare Verfahren: beaufsichtigtes Lernen aus Beispielen, kontrastive Aufgaben, Wiederholung alter Aufgaben, eine getrennte Auswertung und ein optionaler Erhaltungsterm. Die Methodenwahl knüpft an veröffentlichte SFT-/Feedback-Forschung, CheckList-Verhaltenstests und Replay-Forschung an. Die Quellen stützen diese Vorgehensweisen, nicht einen Erfolg dieses LM1-Versuchs. Es wurden keine unbekannten internen Trainingsmethoden von GPT-6 erfunden. [Primärquellen und Anwendungsgrenzen](SCIENTIFIC-BASIS.md)

Für eine größere, bereits sprachlich kompetente offene Modellarchitektur lohnt sich als nächster Ansatz ein bestätigter persönlicher Korrekturbestand mit SFT, kontextreichen Beispielen und repräsentativem Erhaltungs-Replay. Ein sparsames Verfahren wie LoRA kommt erst infrage, wenn trainierbare Ausgangsgewichte, Lizenz, Tokenizer, tatsächliche Hardware und der Export in die vorhandene Inferenzkette geprüft sind. Eine in der App ladbare Inferenzdatei belegt noch keinen unterstützten Anpassungsweg.

Die jetzigen Ergebnisse rechtfertigen keine weitere Runde mit bloß mehr Schritten auf denselben Beispielen. Zuerst müssen die unabhängigen Ablehnungsgründe ausgewertet und die reale Aufgabenrepräsentation sowie ein geeigneter Modellansatz festgelegt werden. Für akustische Personalisierung wären getrennt freigegebene Aufnahmen und bestätigte Wortlautreferenzen nötig. Für nachträgliches Sprachverständnis können bestätigte Textbeispiele bereits eine geeignete Grundlage liefern. Diese beiden Ziele müssen in der Messung getrennt bleiben.

Die neuen Testfälle sind nach Veröffentlichung verbraucht. Sie dürfen für Regression und Reproduktion weiterverwendet werden, aber nicht als neuer unbekannter Bestätigungstest nach einer darauf abgestimmten Änderung. Quellen, Startgewichte, Konfigurationen, Zufallsfolgen, Endgewichte und Rohprotokolle sind gesichert. Eine zweite vollständige Trainingswiederholung wurde innerhalb dieser begrenzten Runde nicht durchgeführt.

## 10. Integrationsentscheidung

**Für die weitere technische Prüfung geeignet:** synthetisches Datenschema und Datenkontrollen, native Auswertungswerkzeuge, begrenzter Kontextadapter, verschlüsseltes Korrektur-Staging und der isolierte Prüfdialog.

**Ausgeschlossen:** beide trainierten LM1-Kandidaten. Sie dürfen weder als allgemeines Sprachmodell bezeichnet noch als Ersatz für Gemma oder als produktiver Aktionsentscheider übernommen werden.

**Offen und ausdrücklich nicht nachgewiesen:** echte persönliche Spracherkennungsqualität, personenbezogene Modellanpassung, vollständiger A/B/C-Durchlauf der aktuellen App, Aktivierung des Dialogs in der App, Keystore-/UI-Lifecycle auf dem Fold7, Thermik und Geräteabnahme. Eine spätere Integration muss gezielt gegen den dann aktuellen App-Stand erfolgen; der ältere Forschungszweig darf nicht vollständig über die laufende APK-/Cinema-/Avatar-Entwicklung gelegt werden.

`main`, Signierung, Veröffentlichung und produktive Modellauswahl wurden nicht geändert. Die drei Forschungszweige und ihre Draft-PRs bleiben zugänglich. Der Rückfall besteht aus dem unveränderten ursprünglichen Modell und dem Nichtübernehmen der experimentellen Komponenten.
