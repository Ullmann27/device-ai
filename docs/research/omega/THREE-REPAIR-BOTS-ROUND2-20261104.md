# Drei Reparatur-Bots — zweite Runde

Stand: 04.11.2026. Besitzerauftrag: „Weiter“.

## Ergebnis

Drei weitere reproduzierte Fehler sind behoben: relative Tagesfenster bei einer
Mitternachts-Zeitumstellung, verlorene Abschlussereignisse während visueller
Pausen und eine fehlende Signaturvariable in der QA-Sperre. Änderungen und
Regressionstests liegen im vorhandenen isolierten [Entwurfs-PR #505](https://github.com/Ullmann27/device-ai/pull/505).

Der Koordinator prüfte den gemeinsamen Quellstand unabhängig: **81 Build-
Vertragstests, 29 Memory-Tests, 25 UI-Hosttests und acht separate Tagesgrenzenfälle
bestanden.** Eine unabhängige Signaturmatrix besteht 12/12. Die Zahlen werden
wegen überlappender Verhaltensprüfungen nicht zu einer unabhängigen Gesamtzahl addiert.

Die gezielte echte Robolectric-Prüfung besteht **25/25 Lifecycle-Tests** auf dem
finalen Quellstand. Beide neuen Queue-Fehler wurden zuvor am alten Code in echtem
Robolectric reproduziert. Der vollständige Gradle-Versuch endete dagegen beim
Kotlin-Kompilieren durch einen unerwartet beendeten Daemon, bevor ein Test
ausgeführt wurde. Die Suchqualität bleibt bei
**42/44 Treffern unter den ersten drei; der bekannte Theater-Treffer bleibt auf
Rang 5.** Es gibt keine neue APK- oder Geräteabnahme.

## Quellenbindung

| Stand | Identität |
| --- | --- |
| Gemeinsame ursprüngliche Integration #491 | `e101e393dd6c80803ac9d9ac91fccd9812c4a18d` |
| Veröffentlichter Abschluss der ersten Runde | `4ddbcb092d955226bb83c2a10f7ac52bb34f59eb` |
| Inhaltsgleiche lokale Basis der zweiten Runde | `63dec3b1296b1d4257310296de3781d75b7a266a` |
| Finaler lokaler Produktions-/Teststand | `d91fe8b1141d35341b67298a04f3913fd1bf0876` |
| Inhaltsgleicher veröffentlichter Quellstand | [`b2bc510a59e29d4ece478109e12b9785be11ee7d`](https://github.com/Ullmann27/device-ai/commit/b2bc510a59e29d4ece478109e12b9785be11ee7d) |
| Gemeinsamer vollständiger Baum dieses Checkpoints | `7770e3ef628f301bf40161e4a99906836c63b296` |
| Unveränderter Unterbaum `app` | `40f3d8d6c14ad0053f501baa42512ccc096d828a` |
| Unveränderter Unterbaum `scripts` | `cd493d45655d6b3d07962af8474a030d65e866b5` |
| Unveränderter Unterbaum `tools` | `7ec692de5a4e9edfe58194949b28f8bf1a837b83` |

Spätere Belegcommits ändern diese Produktionsquellen und Unterbäume nicht. Lokale
und veröffentlichte Commit-IDs unterscheiden sich wegen der Veröffentlichung über
die Git-Baum-API; die vollständigen Bäume wurden vor jedem Branch-Update auf
Identität geprüft. Branch-Updates verwendeten die erwartete vorherige Spitze und
kein Force. Die drei Produktionsdateien sind dieselben wie in Runde 1.

## 1. Tagesgrenzen: Ursache und Reparatur

Der alte `TemporalCue` leitete Tagesgrenzen von einem bereits zonierten
Tagesbeginn ab. Beginnt der heutige Tag wegen einer Zeitumstellung erst um 01:00,
konnte diese Uhrzeit auf andere Kalenderdaten übertragen werden. Dadurch fehlte
etwa die erste Stunde von „gestern“, während „heute“ die erste Stunde des
Folgetags einschließen konnte.

Die neue Berechnung bestimmt zuerst das lokale Kalenderdatum als `LocalDate` und
löst Anfang und Ende jedes Fensters separat mit `atStartOfDay(zone)` auf. Das gilt
für heute, gestern, vorgestern, ausgeschriebene und numerische Tagesabstände sowie
die bestehende ungefähre Spanne „vor einem Monat“. Benannte Wochen-/Monatsfenster,
Overflow-Grenzen, Trust-Regeln, Ranking und persistente Formate behalten ihre Bedeutung.

Die vollständige alte Retrieverquelle scheitert an acht der 29 regulären
Testmethoden; nach dem Fix bestehen 29/29. Der Koordinator reproduzierte den Fehler
zusätzlich mit eigenen festen UTC-Orakeln: sechs rote Santiago-Fälle und zwei
bereits grüne Wiener Kontrollen werden zu acht grünen Fällen. Die Wiener
Kontrollen erfassen einen 23- und einen 25-Stunden-Tag. Die bisherigen Corpus-,
Trust- und Overflow-Tests laufen unverändert mit.

Datei: `app/src/main/java/at/heinz/deviceai/learning/ContinuumMemoryRetriever.kt`.
[Memory-Bericht mit RED/GREEN und Rankingdiagnose](../../evidence/three-repair-bots-round2-20261104/memory/HANDOFF.md).

## 2. Abschlusszustellung bei Pause und Detach

`TurnEnded` wurde zuvor wie ein visuelles Ereignis behandelt. Bei abgeschalteter
Bewegung, Detach oder Generationenwechsel vor der Zustellung konnte der Abschluss
verloren gehen. Spätere Worker-, Timeline- oder Command-Ereignisse konnten den
erledigten Auftrag wieder anzeigen.

Ein tatsächlich empfangener Abschluss wird jetzt vor der visuellen Sperre in der
vorhandenen Auftragsgeschichte verarbeitet. Normale visuelle Ereignisse behalten
Bewegungs-/Detach-Sperren und Generationsprüfung. Die unabhängige Prüfung des
ersten Kandidaten fand zusätzlich den nicht angehängten `View.post`-Pfad:
Ein Hintergrund-Abschluss konnte dort auf ein späteres Attach warten. Der finale
Kandidat stellt ausschließlich solche Abschlüsse über einen expliziten Handler
am Main-Looper zu. Dafür ist kein späteres Anfügen der View nötig.

Die Host-Suite wächst von 20 auf 25 Fälle. Drei neue Fälle reproduzieren verlorene
Abschlüsse im alten Code; der finale Kandidat besteht 25/25, einschließlich des
unabhängigen Koordinatorlaufs. Der Host-Handler wirft bei jedem `post` und simuliert
keine erfolgreiche Queue. Die Hostprüfung allein belegt keine Android-Zustellung.

Die echte Robolectric-Klasse enthält jetzt ebenfalls 25 Methoden. Zwei zusätzliche
Methoden prüfen genau den nie angehängten Subscriber und 1.000 Hintergrund-
Abschlüsse bei dauerhaft abgehängter View. Nach Abarbeiten des Main-Loopers muss
die Historie ohne Reattach genau die letzten 24 IDs enthalten. Testblob vor und
nach Handlerfix: `51db7f4f6996b314ff715a7ea0978b9b744578dd`.

Der Build-Bot führte diese beiden Methoden auf dem vorherigen Kandidaten
`d24ad51b0235826cf0baaa998a2fb4af80a17404` mit echtem Robolectric aus: zwei
ausgeführt, zwei erwartete Assertion-Fehler. Auf dem finalen gemeinsamen
Quellcommit `d91fe8b1141d35341b67298a04f3913fd1bf0876` bestehen anschließend
alle 25 unveränderten Methoden. Beide gezielten Kompiliervorgänge enden mit
Exitcode 0; JUnit endet vor der Reparatur mit 1, danach mit 0. Das belegt die
Queue-Korrektur im gezielten realen Robolectric-Prüfstand.

Die 24 IDs begrenzen nur die vorhandene Historie. Daraus folgt keine allgemeine
Kapazitätsgrenze der Android-MessageQueue und kein umfassender Leak-/Lastnachweis.
Es gibt kein Replay für Ereignisse, die dieser Subscriber nie empfangen hat, und
keine Übertragung der Historie auf eine neu erzeugte View.

Datei: `app/src/main/java/at/heinz/deviceai/ui/JarvisSequenceView.kt`.
[UI-Bericht mit erster Korrektur, Handler-Nachkorrektur und Hostgrenzen](../../evidence/three-repair-bots-round2-20261104/ui/README.md).

## 3. Fehlende Signaturvariable in der QA-Sperre

Die Ausschlussliste prüfte fünf Signaturvariablen, ließ aber
`CONTINUUM_MENTOR_SIGNING_P12_BASE64` aus. In beiden QA-Modi konnte das Skript damit
die verbotene Vorbereitung beziehungsweise Download-Canary erreichen. Genau dieser
sechste Schlüssel wurde in die vorhandene Schleife aufgenommen. Der Einstieg wird
vor Vorbereitung und Download mit Exitcode 69 abgelehnt.

Die unabhängige Koordinatorprobe besteht vorher 10/12, nachher 12/12. Sie verwendet
harmlose Werte, temporäre Git-Repositories und eine Dummy-Download-Canary; kein
Download oder Signiervorgang findet statt. Der Build-Bot erweiterte die reguläre
Matrix auf sechs Schlüssel in beiden Modi: vorher 41/43, nachher 43/43. Mit den
38 weiteren bestehenden Vertragsprüfungen bestehen 81/81; der Koordinator
wiederholte diesen Lauf auf dem gemeinsamen finalen Stand.

Datei: `scripts/render-continuum-owner-continuous-qa.sh`.
[Build-Bericht](R2-BUILD-HANDOFF-20261104.md) und
[unabhängige Signaturprobe](../../evidence/three-repair-bots-round2-20261104/coordinator/signing-fixed.json).

## Tatsächlicher Prüfstatus

| Prüfung | Ergebnis | Aussagebereich |
| --- | --- | --- |
| Build-/Release-Verträge | 81/81 PASS, Bot und Koordinator | Skriptverhalten mit lokalen Fixtures |
| Separate Signaturmatrix | 12/12 PASS nach 2 reproduzierten Fehlern | Sechs Schlüssel × zwei QA-Modi |
| Memory-/Kalender-/Trust-Suite | 29/29 PASS, Bot und Koordinator | Vollständige Retrieverquelle auf Host-JVM |
| Separate Tagesgrenzenprobe | 8/8 PASS nach 6 reproduzierten Fehlern | Feste UTC-Orakel, Santiago und Wien |
| UI-/BrainOrb-Hostsuite | 25/25 PASS, Bot und Koordinator | Originale Logik mit ausgewiesenen Android-/Painter-Stand-ins |
| Neuer vollständiger Android-Gradle-Lauf | Abbruch beim Kotlin-Kompilieren; 0 Tests | Kein fachlicher RED-/GREEN-Nachweis |
| Gezielte echte Robolectric-Lifecycle-Prüfung | RED: 2 erwartete Fehler; GREEN: 25/25 PASS | Echte Android-View-/Looper-/Handler-Klassen, gezielter Widget-Prüfstand |
| Frühere eigene UI-/Kalenderfälle auf #512 | XMLs geprüft: 11/11 UI und 15/15 Kalender | Nur der gebundene #512-Integrationsstand |
| Fold7, APK-Abnahme, GPU, hörbare Sprache | NOT_RUN | Keine Gerätefunktionsaussage |

Der Memory-Hostcheck verwendet einen ausdrücklich scheiternden Compile-Stand-in
für den nicht ausgeführten Event-Decorator. Die UI-Hostprüfung enthält vollständige
originale View-/Effekt-/Bus-Quellen, aber ausgewiesene Android-/Painter-Stand-ins.
Diese Grenzen werden durch bestandene Hosttests nicht aufgehoben.

[Koordinator-Receipt mit Quellen, Befehlen und Rohloghashes](../../evidence/three-repair-bots-round2-20261104/coordinator/receipt.json).
[Unabhängige Gegenprüfung und zusätzlich gefundener Handler-Pfad](../../evidence/three-repair-bots-round2-20261104/coordinator/REVIEW.md).

## Echte Android-Umgebung und Gesamtlauf

Der ursprüngliche Offline-Blocker aus Runde 1, fehlendes AGP 8.10.0, wurde mit einem
geprüften privaten Snapshot vorhandener Abhängigkeiten überwunden. Der fremde
aktive Cache wurde nicht als eigener Schreibcache benutzt. Die Kopie erfolgte
unter lesendem Lock mit Vorher-/Nachher-Dateimanifesten und zusätzlichem AGP-
Inhaltshashvergleich. Der Inhalt aller 5.119 Dateien wurde dabei nicht einzeln
gehasht. Reflinks waren nicht unterstützt; es wurden keine Hardlinks verwendet.

Im echten Gradle-Versuch wurden AGP geladen und Ressourcen-/Manifestaufgaben
ausgeführt. Bei `:app:compileDebugKotlin` verschwand der Daemon unerwartet: Exit 1,
keine JUnit-XML-Datei, kein ausgeführter Test. Die Ursache ist nicht gesichert.
Der freie gemeinsame Plattenplatz schwankte stark; daraus folgt kein bewiesener
OOM- oder Plattenplatzgrund für den Daemonabbruch.

Nach Sicherung der Rohlogs wurden nur der eigene reproduzierbare Gradle-
Versionscache und eigene abgebrochene Build-Zwischenprodukte entfernt. Echte
Android-/Robolectric-Abhängigkeiten blieben für die kleinere gezielte Ausführung
erhalten. Dieser direkte Prüfweg ist kein Ersatz für eine vollständige
App-Kompilierung. [Originaler Gradle-Versuch](../../evidence/three-repair-bots-round2-20261104/build/android-attempt-1.json),
[Ressourcenbefund](../../evidence/three-repair-bots-round2-20261104/build/android-attempt-1-resource-diagnostic.json)
und [Bereinigungsreceipt](../../evidence/three-repair-bots-round2-20261104/build/cache-cleanup-receipt.json).

### Erfolgreicher gezielter Robolectric-Lauf

Für den direkten JUnit-Prüfweg wurden 24 vollständige Original-Produktionsdateien
einschließlich `JarvisSequenceView`, Effekten, echtem `AndroidContinuumPainter`
und Fonts sowie die unveränderte Testdatei frisch kompiliert. Verwendet wurden
die vorhandenen echten Robolectric-4.14.1-/Android-28-Bibliotheken, Kotlin 2.4.0,
JDK 17 und JUnit 4.13.2 im Offline-Modus. Es wurden weder Android-/Handler-
noch Business-Stand-ins benutzt.

Der unbenutzte Pill-Action-Business-Zweig wurde durch vorhandene echte
Produktionsklassen bereitgestellt. Seine vollständige `SafePillActionGate.kt`-
Quelle stimmt bytegenau mit dem Kandidaten überein; Klassenhashes sind erfasst.
Die frisch kompilierten Kandidatenklassen stehen vor diesem Verzeichnis im
Classpath. Der Klassenladebeleg weist den frisch kompilierten `JarvisSequenceView`
und die echten Robolectric-Android-Klassen aus; die Pill-Action-Klasse wurde in
diesem Lauf nicht ausgeführt. Daraus folgt keine neue Abnahme von ToolGate.

Der direkte Prüfstand verwendet kein Appmanifest und fällt ausdrücklich auf
Android-OS-Ressourcen zurück. Die Testdatei wurde dafür nicht umgeschrieben und
keine Assertion abgeschwächt. Das Ergebnis ist eine echte gezielte Prüfung von
Queue und View-Lifecycle; vollständiges Appmanifest, Appressourcen, APK,
Gerätebetrieb und Darstellung bleiben außerhalb dieses Nachweises.

[Robolectric-Receipt und Rohlogbindung](../../evidence/three-repair-bots-round2-20261104/build/robolectric-receipt.json).
Der Koordinator prüfte anschließend alle 27 Build-Beleghashes, die 25 Original-
Eingänge je Lauf, 47 identische Laufzeit-JARs, beide instrumentierten SDK-JARs und
die Auswahl aus den ursprünglichen vollständigen Klassenladeprotokollen selbst.
[Unabhängiger Abgleich](../../evidence/three-repair-bots-round2-20261104/coordinator/native-robolectric-review.json).

Bei diesem späteren Abgleich waren die fremden temporären Business-Klassen und
danach auch deren Checkout-Quelldatei nicht mehr verfügbar. Ihre ursprünglichen
Laufhashes sind erhalten, konnten vom Koordinator aber nicht erneut gemessen
werden. Die Pill-Klasse wurde in keinem Lauf geladen; alle tatsächlich beobachteten
Appklassen gehören zur frischen Kandidatenkompilation. Für eine künftige erneute
Kompilierung muss diese dokumentierte Compile-Abhängigkeit wieder bereitstehen.

## Abgleich mit der parallelen Integration

#512 bei `f9267a3743a87a77bf6421011c014b2ca2d6b0d6` enthält in seinem Quellcheckpoint
`52fbfc624c9a2846c1da571d330327c7874b1639` alle drei Produktionsdateien der ersten
Reparaturrunde byte-identisch. Dessen Bericht nennt 305 bestandene Tests in
45 Klassen und NewApi-Lint ohne Befund.

Der Koordinator prüfte die originalen XMLs der übernommenen elf UI-Lifecycle-
und fünfzehn Kalendertests persönlich: null Fehler und Übersprünge. Quellblobs und
Hashes von XML, Gradle-Protokoll und Zusammenfassung stimmen mit dem veröffentlichten
Manifest überein. Der alte NOT_RUN-Status dieser elf UI-Fälle ist damit auf diesem
Integrationsstand überholt.

Die Zahl 305 wurde mit dem Receipt abgeglichen; der Koordinator führte die 305 Tests
nicht selbst aus und prüfte nur die zwei genannten XML-Suiten einzeln. Neue Quellen
dieser zweiten Runde werden dadurch nicht mitgeprüft. Der
[maschinelle Abgleich](../../evidence/three-repair-bots-round2-20261104/coordinator/integration-512-reconciliation.json)
bewahrt diese Unterscheidung. Weitere Kombinationen mit #506/#512/#513 benötigen
eine Abnahme ihres jeweiligen exakten Standes; deren Zweige wurden nicht überschrieben.

## Offene Suchqualität

Im unveränderten synthetischen Corpus mit 1.000 Erinnerungen und 44 Fragen bleibt
Char-N-Gramm + Zeit bei Recall@3 42/44, gerundet 0,955. Die Kalenderkorrektur aus
Runde 1 ließ berechtigt weitere Oktober-Treffer zu. Kürzere Thomas-Texte erhalten
in der bestehenden lexikalischen Ähnlichkeitsfunktion höhere Werte als der
Theater-Treffer; das Zeitgewicht unterscheidet Treffer desselben Monats nicht.

Ein allgemeiner Implementierungsfehler der Rangformel ist für diesen Fall nicht
belegt. Eine auf Theater, Thomas oder die bekannte ID zugeschnittene Bevorzugung
wäre kein Nachweis besserer Suchqualität. Corpus, Mindestschwellen, Ranker und
Modellgewichte wurden deshalb nicht angepasst. Der vorhandene M2.9-Baustein wird
durch diesen PR nicht neu in den Hauptchat eingebaut. Eine allgemeine Verbesserung
braucht getrennte, zuvor nicht zum Abstimmen benutzte Prüfungen und neue Messungen.

## Abschlussgrenzen und Rücknahme

Drei echte Unteragenten arbeiteten in eigenen Arbeitsbäumen. Der Koordinator
kombinierte ihre Quellen im vorhandenen PR und prüfte sie unabhängig. Es wurden
keine dauerhaften Android-Worker, neue Reparaturpipeline, externen Modellaufrufe
oder bezahlten Dienste eingerichtet. Saubere eigene frühere Arbeitskopien wurden
erst nach Sicherung ihrer Commits und Belege entfernt.

Der PR bleibt als Entwurf isoliert. Kein Main-Merge, APK-Release, Signieren oder
Installieren erfolgte. Besitzerwissen, Offline-Steuerung, ToolGate/CCC1, Paket,
Versionswert und Updatekanal bleiben erhalten. Die Produktionskorrekturen enthalten
keine Datenmigration und sind einzeln rücknehmbar. Der veröffentlichte Abschluss
der ersten Runde ist die Rückfallreferenz für diese zweite Runde.
