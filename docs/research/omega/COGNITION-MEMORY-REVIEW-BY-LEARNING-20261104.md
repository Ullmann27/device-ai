# Unabhängiger Memory-Review durch Selbstlernen

**Ergebnis:** Im begrenzten Prüfbereich kein neu eingeführter Fehler bei v2-Kanonisierung, v1-Lesen/v2-Schreiben oder gemischten Journalketten gefunden. Ein bereits bestehender Audit-Blindfleck bleibt bestehen; siehe Befund 1. Nur Quelltext- und Evidenzprüfung, keine Codeänderung und kein zusätzlicher JVM-Lauf.

## Befund 1: fehlende Ledgerfakten bleiben unentdeckt — Vorbestand

`SignedOwnerCorrectionJournal.audit` prüft ausschließlich die in `ownerFacts(ledger)` vorhandenen Datensätze gegen die letzten signierten Payloads. Es prüft nicht, ob jeder im gültigen Journal zuletzt signierte Fakt noch im übergebenen Ledger vorkommt.

Konkreter statisch nachvollziehbarer Fall: Bei einem gültigen Journal mit zwei signierten Fakten ergibt `audit(emptyList())` die Rückgabe `VERIFIED` mit `signedMutations=2`. Die Liste `unsigned` bleibt leer, weil die Schleife keinen aktuellen Datensatz besucht. Auch das Weglassen eines einzelnen signierten Fakts wird so nicht beanstandet, sofern alle verbleibenden Fakten passen.

Dieselbe Lücke steht bereits in `memory/red-04/sources/SignedOwnerCorrectionJournal.kt`; der Encoding-Fix führt sie nicht ein. Die vorhandenen Tests prüfen Änderungen vorhandener Fakten, aber keine Entfernung eines signierten Fakts aus dem übergebenen Bestand. Der Befund begrenzt die Aussagekraft eines vollständigen Integritätsaudits; er ist kein Gegenbeweis zur geprüften Versionskompatibilität. Keine Laufzeitreproduktion zusätzlich ausgeführt.

## Geprüfter Änderungsumfang

| Bereich | Prüfergebnis |
|---|---|
| Eindeutige v2-Bytes | Separate Kennungen `PCM2`/`MRC2`; Zeichenketten mit Längenpräfix und unveränderten UTF-16-Codeeinheiten; `null` durch Länge `-1` von leerer Zeichenkette getrennt. Listenanzahl und einzelne Längen verhindern Trennzeichenverschiebungen. |
| Mengenbedeutung | Referenzlisten werden weiterhin sortiert und dedupliziert. Reihenfolge und Duplikate sind absichtlich keine unterschiedlichen signierten Aussagen. |
| Zahlen | Zeitwerte werden als feste 64-Bit-Werte geschrieben. `MemoryRecord` erlaubt nur endliche Konfidenzwerte im bisherigen Wertebereich. |
| v1-Lesen | `decode` setzt bei `v1` explizit Schema 1. Die alten kanonischen Payloadbytes und Recordhashes bleiben verfügbar. Alte Signaturen werden überprüft, nicht neu erzeugt. |
| v2-Schreiben | Der reale Writer verlangt das aktuelle Schema und schreibt ausschließlich `v2`. URL-Base64 pro UTF-16-Feld und pro Listenelement verhindert Kollisionen mit Tabulatoren bzw. Kommas des Journalformats. |
| Gemischte Kette | Neue v2-Mutationen verweisen über `previousMutationHash` auf den unveränderten Hash der vorherigen v1-Payload. Der neue `beforeHash` verwendet bewusst das Schema der neuen Mutation. |
| Auditversion | Der letzte vollständige Payload pro Record wird gespeichert. Der aktuelle Recordhash wird anhand genau dessen `schemaVersion` berechnet; unveränderte alte Fakten können v1 bleiben, während geänderte Fakten bereits v2 nutzen. |
| Keine Altdatenumschreibung | `entries`/`audit` lesen lediglich; `recordDiff` öffnet die Journaldatei mit Append-Modus. Das bestehende Dateinamen-Suffix `v1.log` bleibt für Kompatibilität erhalten, bestimmt aber nicht das Schema jeder Zeile. |
| Versionen/Manipulation | Unbekannte Versionen werden abgewiesen. Das bloße Umschreiben einer v2-Zeile auf v1 kann die vorhandene v2-Signatur nicht übernehmen. Ungerade UTF-16-Bytezahlen und leere einzelne Referenzen scheitern an Validierung. |

Die v1-Kompatibilität bewahrt auch die historische v1-Darstellung. Vorhandene v1-Signaturen erhalten dadurch nicht nachträglich die zusätzliche Eindeutigkeit des v2-Formats.

## Aktivierungsgrenze

`ContinuumLearningCore(baseDirectory, nowMillis)` und die Standardvariante `withAutomaticRecovery(baseDirectory, nowMillis)` reichen ausdrücklich `null` als `correctionSigner` weiter. `correctionJournal` entsteht ausschließlich durch `correctionSigner?.let { ... }`. Ohne Signer läuft `signedOwnerMutation` nur den bisherigen Ledgerblock; `correctionAudit()` liefert `NOT_CONFIGURED`.

Die im Produktionsquellbaum gefundenen direkten Core-Aufrufer nutzen die unsignierte Standardvariante beziehungsweise nur einen Zeitgeber. Ein automatisches Aktivieren der signierten Owner-Korrekturjournale in `AgentActivity` oder den bestehenden Schedulern ist durch diesen Fix **nicht** belegt. Die explizite Signer-Variante wird in den geprüften Journaltests verwendet. Das ist eine Aktivierungsgrenze des Korrekturjournals, keine Aussage darüber, ob andere generische Proof-Carrying-Memory-Aufrufer die v2-Kanonisierung nutzen.

## Evidenzprüfung

Gelesen: `ProofCarryingMemory.kt`, `SignedOwnerCorrectionJournal.kt`, beide neuen Encoding-Testdateien, bestehende `SignedOwnerCorrectionJournalTest.kt`, relevante Core-Konstruktoren/Aufrufer sowie `docs/evidence/cognition-learning-loop-20261104/memory/red-04` und `green-01`.

- `red-04`: 18 Tests, 10 Fehler; beide Zielquellen erfolgreich kompiliert.
- `green-01`: 28 Tests, keine Fehler; beide Zielquellen erfolgreich kompiliert.
- Die ursprünglichen zwölf Canonical-Encoding-Tests und sechs bestehenden Journaltests haben zwischen RED und GREEN identische Quellenhashes. GREEN ergänzt zehn Versions-/Wire-Kompatibilitätstests.
- Die in den neuen Tests eingebettete v1-Datei ist bytegleich zur Ausgabe des echten alten Writers aus `red-04/legacy-fixture/owner-corrections-pcm-v1.log`.
- Alle aktuell geprüften Produktions- und Testdateien entsprechen ihren Hashes im GREEN-Manifest.
- Beide Statusdateien bestätigen unveränderte Abhängigkeitsbytes. Diese vorhandenen Ergebnisse wurden gelesen, nicht erneut ausgeführt.

Geprüfte Produktionshashes:

| Datei | SHA-256 |
|---|---|
| `ProofCarryingMemory.kt` | `12e5cbd6a85c9b6836b28fb9b57761dce2922803a4f6354b4118741854c37082` |
| `SignedOwnerCorrectionJournal.kt` | `5fcd47670d19324e9f03da4b16f62898f84759a18770f015b3eca255a6fbedac` |

Der Review deckt keine Android-/Fold7-Aktivierung, keinen vollständigen Sicherheitsnachweis des vorhandenen Journals und keine höhere Erinnerungsqualität ab.
