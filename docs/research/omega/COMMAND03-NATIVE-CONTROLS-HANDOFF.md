# Command03: native Bedienung im Original-HTML vervollständigen

Status: **PORTIERT / 84 frische Releasevertragstests bestanden / native Abnahme BLOCKED_SHARED_RESOURCE_PRESSURE / keine neue APK**.

## Aktueller Fortsetzungsstand: 16.11.2026

Produkt- und Testquelle: **`62be11f898586eb3546f79a0f2b38ee53acc4d84`**, PR854. Der Merge erhält sowohl diese Fortsetzung als auch den zwischenzeitlich erweiterten kanonischen Stand `3d5efd9e` aus PR851. Vollständiger `app`-Baum: `fca7fd5e1f5f87c784f43340e19451aadcf11a1c`; Produktionsbaum: `5f215bf5a60f3f570b7329c424e89d4a080e372f`. Ein nachfolgender Dokumentationscommit verändert diese Anwendung nicht.

| Prüfung | Tatsächliches Ergebnis |
|---|---|
| Releaseverträge auf `62be11f8` | **44 Python + 40 Node PASS**, keine Skips |
| Drei Workflowdateien / 32 eingebettete Shellabschnitte | Syntax PASS |
| Originalasset-Vertrag / Bridge-Syntax | PASS; **35 Originalassets / 15.864.219 Bytes bytegleich** |
| Identischer Webadapter im letzten Entwicklungslauf | **44/44 DOM PASS**; aktueller Pflicht-Interopfall noch offen |
| Erster Android-HTML-Lauf auf `68a0eba6` | **29 PASS / 5 FAIL** bei 34 Fällen; beide Kompilationen bestanden |
| Gezielt korrigierte Testfixtures und kanonische Zusatztests | Implementiert; erneute Ausführung noch nicht bestätigt |
| Hauptkompilation auf `62be11f8` | Abbruch nach **36,927 s** durch Daemonverlust; keine Kotlin-Compilerdiagnose |
| Aktuelle native Auswahl | **37 HTML + 77 Unit + 32 Klassik + 6 Dokument: NOT_RUN** |
| Aktueller DOM-Lauf mit frischem grünem Kotlin-Paket | **NOT_RUN**; 45 Fälle vorgesehen |
| Neue unsigned APK / unabhängiger Audit | **NOT_RUN** |

Die sieben Bedienbereiche sind im Code angeschlossen: aktive Computer-Navigation, bestätigter Entwurfs-/Diktatabgleich, getrennte Dialogansichten mit ausdrücklich auswählbarer Textwiederherstellung, sichtbarer Fokus, Mikrofon-Langdruck, native Quellenkarten und aktueller PDF-Entwurf. Bestehende Capture-, Bot-, Sprach- und Dokumentpfade werden weiterverwendet.

### Konkreter Blocker und Nachweisgrenze

Nach dem Daemonverlust wurden keine weiteren Tests oder Builds gestartet. Sieben Messungen über 180,034 Sekunden zeigten nur 1.522.340 bis 2.193.960 KiB verfügbaren Arbeitsspeicher. Der globale `oom_kill`-Zähler stieg währenddessen von 61 auf 64, obwohl keine eigene JVM lief. Damit ist fortbestehender geteilter Speicherdruck belegt; ein konkreter OOM-Kill unseres Compiler-PID ist mangels zugänglicher Kernel-/cgroup-Belege **nicht** nachgewiesen. Der Abschlussstand des freien Workspaceplatzes lag außerdem 110.247.936 Bytes unter dem unveränderten 700-MiB-Gate für den APK-Bau.

Alle eigenen Worker sind beendet. Der ursprüngliche Checkout ist sauber auf `e92001ad6c28cdfd75e3fc29bb6efb65e832afa0` restauriert, beide ursprünglichen Locks sind freigegeben. Frühere fehlende RAM-Verweise bleiben als `RESET_MISSING` dokumentiert; keine Compilerhistorie wurde erfunden, ersetzt oder gelöscht. Die erhaltene APK mit SHA `00def8e9…` gehört ausschließlich zum früheren b56-Medienstand.

Die [versionierten Zwischenbelege](command03-controls-checkpoint-62be11f8/README.md) verbinden frische Releasevertragstests, den Web-Entwicklungsstand, die ursprünglichen fünf nativen Testbefunde, den Daemonverlust und die abgeschlossene Ressourcenbeobachtung. **Diese Belege sind kein vollständig grüner Android- oder APK-Abschluss.** Die Fortsetzung bleibt deshalb in PR854; es erfolgt noch kein Fast-forward dieser Änderungen nach PR851.

### Exakter nächster Schritt

Auf derselben Produktquelle nach stabiler Ressourcenprüfung die normale Haupt- und Testkompilation ausführen. Anschließend die vorbereiteten 152 nativen Fälle tatsächlich ausführen. Erst nach vollständig grünem HTML-Lauf das frische synthetische Kotlin-Paket exportieren und den verpflichtenden 45er-DOM-Lauf durchführen. Danach regulär unsigned bauen und die neue APK unabhängig prüfen. Die vorhandenen Gates, Testassertions und Quellenbindungen bleiben erhalten. Erst diese Belege erlauben die kanonische Integration der neuen Controls. **GERÄTEBESTÄTIGT: NOT_RUN.**

## Quelle und Integration

Ausgangspunkt: `e92001ad6c28cdfd75e3fc29bb6efb65e832afa0`, kanonische Linie `integrate/continuum-command03-android-20261115`, PR851. Der vorige Medienstand wurde auf `b56a614b` mit153 gezielten Fällen und einer unabhängigen unsigned APK-Prüfung bestätigt. Diese Ergebnisse gelten nicht automatisch für die folgenden Korrekturen.

Integrationsverantwortlich: Root; genau drei bestehende unterstützende Agents für Android, Web und QA. Arbeitszweig: `fix/command03-native-controls-20261116`. Ziel ist die bestehende kanonische Linie, keine neue Produkt- oder Renderarchitektur.

## Belegte noch offene Anschlüsse

1. **Computer-Tab:** Bei bereits aktivem Computer ruft die HTML-Route device den Stop-Pfad auf und öffnet einen zusätzlichen nativen Dialog. Der aktive Computer soll stattdessen im vorhandenen HTML-Bereich ausgewählt werden; inaktive Einstiege und tatsächlich verdeckende Routen behalten ihre bisherigen Grenzen.
2. **Entwurf und Sprache:** dirtyDraft verhindert nach HTML-Bearbeitung spätere native Diktat-/Vorbelegungs-/Sendeaktualisierungen. Erforderlich ist eine bestätigte lokale Editrevision statt pauschalem Überschreiben.
3. **Fokus und Mikrofon:** Native Composer-Fokuswünsche erreichen das verdeckte EditText. Der vorhandene stille Diktier-/Gesprächsende-Langdruck ist im HTML nicht angeschlossen.
4. **Dialogansichten:** Hauptchat und Helferbot verwenden im HTML dieselbe Sitzungskennung; beim Wechsel werden Verläufe sichtbar angehängt. Die nativen Stores sind bereits getrennt. Eine zusätzliche revisionsgebundene Ansicht muss verspätete Verläufe, Antworten und Eingaben abweisen.
5. **Quellenkarten:** Die vorhandenen anklickbaren Quellen werden ausschließlich im verdeckten nativen Container ausgegeben. Der HTML-Adapter benötigt den bestehenden, an den aktuellen Dialog gebundenen Quellenzugriff.
6. **PDF:** Der vorhandene native Knopf zur Entwurfserstellung fehlt im HTML-Dokumentbereich; die Funktion ist per normalem Auftrag bereits erreichbar.

Die Befunde wurden im tatsächlichen Activity-/Stage-/JS-Aufrufpfad geprüft. Allgemeine Phone-/Fold-Geometriefehler werden daraus nicht abgeleitet.

## Umsetzung und Prüfungen

Die35 Originalassets und der vorhandene Renderer bleiben bytegleich. Native Capture-, ToolGate-, Composer-, Sprach-, Bot- und Dokumentpfade werden weiterverwendet. Gezielte negative Prüfungen müssen alte Dialog-/Seitenkennungen, neue lokale Eingaben während nativer Verarbeitung, späte Rückmeldungen und tatsächliche Verdeckung abdecken.

Die Implementierung und Entwicklungs-Rot-/Grün-Belege liegen vor. Offen bleiben die vollständig bestandenen gezielten Android-/Web-Regressionen auf einem gemeinsamen veröffentlichten Quellstand und die aktualisierte unsigned Prüf-APK.

### Entwicklungscheckpoint 16.11.2026, 06:10 UTC

Der erste kleine Patch erhält eine bereits aktive Computersitzung beim erneuten Auswählen von Computer. Der neue native Regressionstest führt die tatsächliche Stage-Route zweimal aus und prüft zusätzlich, dass Chat die Sitzung weiterhin beendet und ein anschließender inaktiver Computereinstieg nur das Center öffnet. **NOT_RUN**: Dieser neue Androidfall wurde noch nicht kompiliert oder ausgeführt.

Der begonnene Wire-v2-Vertrag ergänzt native Seiten- und Ansichtsinstanzen sowie eine geordnete Editbestätigung. Der reine DraftLedger vergleicht Revision, vollständigen Text und Auswahl zusammen. Activity, Stage und HTML werden im nächsten Schritt gemeinsam umgestellt; dieser Zwischenstand ist ausdrücklich **nicht als kompilierbar bestätigt**.

Ein gesonderter DOM-Rotlauf gegen den noch unveränderten bisherigen Adapter reproduziert sieben Fehler bei 34 Fällen (27 bestanden, 7 fehlgeschlagen, keine übersprungen). Das ist ein Fehlernachweis des alten Adapters und kein bestandener Test dieses Zwischenstands. Die endgültige grüne Prüfung muss von der später veröffentlichten vollständigen Quelle stammen.

### Entwicklungscheckpoint: Stage und HTML auf Wire v2

Stage und separater HTML-Adapter sind gemeinsam umgestellt. Alle Transportaktionen und Rückmeldungen tragen die native Seitenkennung, eine eigene Ansichtsinstanz mit monotoner Revision und die Gesprächskennung. Text und Auswahl werden als eine Editrevision übergeben. Die Stage verwirft alte Queues beim Seiten-/Ansichtswechsel; echte Stop-Aktionen bleiben auch bei einem nicht übertragbaren Entwurf möglich. Der HTML-Adapter ergänzt die vorhandenen Quellen- und PDF-Aktionen, den Mikrofon-Langdruck und gebundene Fokuswünsche.

Die sieben zuvor roten neuen HTML-Kontrollfälle bestehen im gezielten Entwicklungslauf jetzt **7/7**; die JavaScript-Syntaxprüfung besteht. Der unveränderte Rotlauf mitsamt damaligen Test-/Adapterhashes ist separat erhalten. Die bisherige DOM-Regressionsauswahl wird erst anschließend auf den strengeren Transportvertrag umgestellt. **Kein Gesamtgrün und kein Android-Kompiliernachweis**: Activity und native Testfixtures sind noch in Arbeit. Das nächste Abnahmekriterium ist weiterhin der gemeinsame saubere Quellfreeze mit vollständig ausgeführten gezielten Regressionen.

### Entwicklungscheckpoint: native Produktionsanbindung

Die Activity-Anbindung ist jetzt als zusammenhängender Patch geschrieben: echte Ansichtsinstanzen für Haupt-/Helferchat, exakt gebundene Entwürfe und Verlaufsladungen, native Mikrofon- und Fokuspfade, opake Quellen- und Dokumentkennungen sowie ein Produzenten-Guard für verspätete Cloudantworten und deren Werkzeugfortsetzung. Bei einer durch den angenommenen Auftrag selbst ausgelösten Botwahl darf nur dessen ausdrücklich zugeordneter neuerer Entwurf kontrolliert übernommen werden. Ein begrenzter Übergangshinweis erlaubt dem HTML, noch unbestätigte lokale Eingaben separat zur manuellen Wiederherstellung anzubieten; diese HTML-Ergänzung ist noch in Arbeit.

Beim erfolgreichen PDF-Entwurf schließt nur der Abschluss zum exakt aktuellen Dokument die HTML-PDF und zeigt das Gesprächsergebnis. Alte Dokumentantworten dürfen eine inzwischen andere PDF nicht schließen. Fehlermeldungen bleiben dem auslösenden Dokument zugeordnet. Automatische Ansichtswechsel vergeben keinen neuen Eingabefokus; dafür ist ein ausdrücklicher Fokusauftrag erforderlich.

Dieser veröffentlichte Zwischenstand dient einer **diagnostischen Hauptkompilation**, während native Testfixtures und letzte HTML-Negativfälle vervollständigt werden. Noch **kein Compilererfolg**, keine frische Android-Testwertung und keine neue APK bestätigt. Der finale saubere Quellfreeze und seine tatsächlichen Prüfbelege folgen gesondert.

## Gemeinsamer Prüfkandidat

Die diagnostische Hauptkompilation auf `5e7b9eddb5252d570e7a99eaaa845afb53e38485` bestand am 16.11.2026 in 34,275 Sekunden. Der reguläre Task `:app:compileDebugKotlin` lief tatsächlich, ohne Kotlin-Compilerfehler; der ursprüngliche Checkout wurde unverändert restauriert. Diese Diagnose ist kein Testergebnis der später ergänzten Quelle.

Der nun vollständige Kandidat ergänzt noch den Cause-Token im zweiten asynchronen Bot-Auswahlschritt, den effektiven nativen Fokusfilter und die Zurückweisung alter PDF-Abschlüsse vor Änderungen an der aktuell geöffneten Arbeit. Ein alter PDF-Erfolg oder -Fehler darf nur seinen eigenen Journaleintrag aktualisieren, nicht den heutigen Dokumentbezug, die Ansicht oder den Aktionszustand.

Vorbereitete gezielte Auswahl: 34 native HTML-/Bridge-/Medienfälle, 77 Unitfälle einschließlich sechs neuer DraftLedger-Fälle, 32 bisherige Klassikfälle und sechs bereits vorhandene Dokumentregressionen. Die Dokumentfixture wählt dafür jetzt ausdrücklich den klassischen Modus; ihre Assertions bleiben bestehen. Die tatsächlichen Ergebniszahlen werden erst aus frischen Laufbelegen übernommen.

Der letzte vollständige Entwicklungslauf des HTML-Adapters besteht mit **44/44** Fällen, ohne übersprungene Tests. Die finale Prüfung muss zusätzlich das frisch von der aktuellen Kotlin-Stage erzeugte JSON verwenden; dafür ist `COMMAND03_REQUIRE_NATIVE_PACKET=1` gesetzt. Die ersten Rotläufe, fokussierte Zwischenprüfungen und die Diagnosequelle werden getrennt vom finalen Nachweis aufbewahrt.

Die statische Quellenprüfung besteht weiterhin für **35 Originalassets / 15.864.219 Bytes**. Ihr veralteter wortwörtlicher Session-Guard wurde an den neuen vollständigen Seiten-/Ansichts-/Sitzungsguard angepasst; Asset-Pins, Blob-Hashes und der Vertrag der exakt zwei separaten Android-Includes bleiben unverändert. Die Verhaltenssicherheit wird zusätzlich von den echten negativen DOM-/Androidfällen geprüft, nicht aus dieser Zeichenfolgenprüfung abgeleitet.

Nächste tatsächliche Schritte: saubere veröffentlichte Quelle kompilieren, vorbereitete native Auswahl ausführen, frisches Kotlin-Paket exportieren und den vollständigen DOM-Lauf damit durchführen, bestehende Releaseverträge prüfen, unsigned APK regulär bauen und unabhängig auf Quellen-/Assetgleichheit prüfen. Bei Fehlern wird der konkrete Befund repariert und ein neuer vollständiger Prüfstand veröffentlicht. **GERÄTEBESTÄTIGT: NOT_RUN.**

### Erster gemeinsamer Androidlauf und gezielte Fixture-Reparatur

Auf `68a0eba67dfda45e20c6abcdf3d0bfbe80006654` bestanden die Hauptkompilation und die separate Testkompilation. Der erste tatsächliche HTML-/Bridge-/Medienlauf ergab **34 Fälle: 29 bestanden, fünf fehlgeschlagen, null Fehler und null übersprungen**. Die frischen XML-Dateien und Laufbelege bleiben unverändert erhalten; dies war ausdrücklich kein Gesamtgrün.

Drei Fehler entstanden im Testzugriff auf den lazy erzeugten Bot-Profilspeicher: Der private Getter `getBotProfiles()` ersetzt die Reflection auf ein nicht vorhandenes Feld. Ein PDF-Test erwartete fälschlich „anderem Auftrag“; er prüft jetzt die bereits vorhandene vollständige Meldung „Entwurf nicht gestartet – JARVIS arbeitet an einem anderen Auftrag.“ Die Produktionsmeldung bleibt unverändert.

Der fünfte Befund betrifft die lokale Robolectric-Implementierung: Der tatsächlich installierte `ShadowWebView` 4.14.1 verwendet für `ViewDelegate.requestFocus(int, Rect)` einen Proxy, dessen `nullish`-Behandlung immer `false` liefert. Dies wurde im lokalen Android-28-/Robolectric-Bytecode geprüft. Der bestehende Hostadapter leitet deshalb zusätzlich zum vorhandenen Layoutaufruf ausschließlich die Fokusanfrage an die echte `View.requestFocus`-Implementierung weiter. Reale Focusability-, Touch-Mode-, Eltern- und `hasFocus()`-Prüfungen bleiben wirksam; weder Sichtbarkeit noch Fokus werden als Ergebnis vorgegeben. Die positive Fokusassertion und die Prüfung des verdeckten Editors bleiben erhalten. Die zwei korrigierten Testdateien verändern keine Produktionsquelle.

Separat bestanden auf dem ersten Freeze **44 Python- und 40 Node-Releasevertragstests**, beide Syntaxprüfungen und der Originalasset-Quellenvertrag. Diese Komponentenbelege ersetzen weder den fehlgeschlagenen Androidlauf noch die noch ausstehende APK. Der korrigierte Kandidat muss erneut vollständig ausgewertet werden.

### Zusammenführung der zwischenzeitlich erweiterten kanonischen Linie

Vor dem erneuten Androidlauf zeigte die frische GitHub-Abfrage einen inzwischen erweiterten PR851: `3d5efd9ec4b1a8a5c9c12bb39835be8aef9a9b64`, aufbauend auf dem Navigationsfix `7e783f3e` und dessen drei zusätzlichen Regressionstests. Diese Arbeit wird durch einen echten Merge mit beiden Eltern erhalten. Der dortige aktive Computerpfad entspricht fachlich dem bereits in dieser Fortsetzung korrigierten Einstieg; seine zusätzliche Einplanung der bestehenden Bildaktualisierung wird übernommen. Die vollständige v2-Bindung und der zusätzliche Guard für eine vorhandene sichtbare HTML-Stage bleiben erhalten.

Die drei kanonischen Tests für aktiven Computereinstieg, unveränderte Leerlaufkontrollen und weiterhin wirksames Verlassen/Stoppen bleiben mit allen Assertions erhalten. Lediglich die Prüfung des inzwischen strukturierten, an die Ansicht gebundenen `section`-Pakets wird auf v2 umgestellt. Die vorbereitete native HTML-Auswahl wächst damit auf **37 Fälle**; Unit77, Klassik32 und Dokument6 bleiben unverändert. Die älteren156 kanonischen Navigationsergebnisse werden als historische Belege übernommen und nicht zur neuen Abnahme addiert.

Der vorher vorbereitete `05c7f432`-Lauf begann nicht: Seine äußere Speichervorprüfung brach vor Checkoutwechsel und JVM-Start ab. Die spätere Kapazitätsprüfung erlaubte die Kompilation wieder. Der damalige freie Wert wurde nicht vollständig gesichert; aus dem Abbruch wird weder ein Compilerfehler noch ein Testergebnis abgeleitet. Die neue Prüfung beginnt erst auf dem veröffentlichten gemeinsamen Mergecommit.

## Arbeitsumgebung

Während der lesenden Bestandsprüfung wurde die lokale Umgebung vorübergehend unavailable. Nach Wiederanbindung sind Quelle, Toolchain, aktuelle Compilerzustände, b56-APK und das dauerhaft gesicherte Prüfpaket vorhanden. Frühere RAM-Auslagerungen fehlen nach dem Neustart. Diese werden ausdrücklich als fehlende historische Zwischenstände dokumentiert; keine alten Testergebnisse oder Dateien werden fingiert. Eine neue externe Orchestrierung wird auf den tatsächlich vorhandenen b56-Nachweis gebunden und erhält die bisherigen Quell-, Lock-, Platz- und Ergebnisgrenzen.

Originalsignierung und Veröffentlichung bleiben an die vorhandene vollständige quellgebundene Release-QA und geschützte Besitzerfreigabe gebunden. Der neue Arbeitszweig löst diese Schritte nicht aus.
