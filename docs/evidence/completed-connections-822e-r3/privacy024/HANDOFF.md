# PR #529 / C1-PRIVACY-024: abgeschlossene Produktverbindung

Status: **SOURCE_INTEGRATED_NOT_ANDROID_VERIFIED**. Basis dieser Integration ist `75533fa97532ace8333d1023c689ee2a86e87668`. Root besitzt Veröffentlichung, den separaten C1-Test-/Historienmerge und die echte Android-Abnahme.

## Gebundene Quelle

Über den autorisierten GitHub-Connector gelesen und geprüft:

- [PR #529](https://github.com/Ullmann27/device-ai/pull/529), veröffentlichter Beleghead `00049e5c76b30e3a3587755a65ac6a30a693eb8a`, Baum `93828a9b4d0fea44940c2b9751277059b8d388c1`.
- [Geprüfte Produktquelle](https://github.com/Ullmann27/device-ai/commit/4a60c758fd31f5436401769d9ed91da646de815c), Baum `cf3f45bb3c59aa9bfc20d063c896d4d1d8a7d95d`.
- [Originalhandoff](https://github.com/Ullmann27/device-ai/blob/00049e5c76b30e3a3587755a65ac6a30a693eb8a/docs/research/omega/MEMORY-NIGHT-HANDOFF-20261104.md) und [originale Android-Belege](https://github.com/Ullmann27/device-ai/tree/00049e5c76b30e3a3587755a65ac6a30a693eb8a/docs/evidence/memory-privacy-r4-20261104/android-final) bleiben an ihrer Quelle.

Die beiden `fetch_file`-Antworten am geprüften Commit liefern Compiler-Blob `0e4718176518131828688092ea95e894a1d4c2fa` und Ecology-Blob `3368e6939061d259e9c8b8c6d419e3dc71cabcaf`. Die selbst berechneten Git-Blob-IDs der C1-Intake-Mitglieder stimmen überein; `target/` und `public-verified/` enthalten jeweils identische Bytes. Nur diese fest gebundenen Textdateien dienen als Transport. Vollständige Datei-, Archiv- und Ergebnis-Hashes stehen in `PROVENANCE.json`.

## Produktmapping

| Datei | Übernahme und Integration |
| --- | --- |
| `cognitive/AiContextCompiler.kt` | Erfasste Zugangsdaten einschließlich Bearer/Basic und zitierter Werte vollständig redigieren; nicht abgeschlossene zitierte Werte mit `UNTERMINATED_CREDENTIAL_VALUE` ablehnen. Jedes rohe Belegfeld vor dem Escaping redigieren; bereits gerahmte Evidence-Abschnitte nicht erneut gemeinsam redigieren. |
| `cognitive/MemoryEcology.kt` | Das vorhandene `containsSensitiveInformation`-Merkmal erhält bei der Einstufung Vorrang vor Geräteinformationen. |

`MemoryEcology.kt` ist byteidentisch zur gepinnten Quelle. Der Compiler entspricht dieser Quelle vollständig bis auf die bewusst erhaltene #523-Optimierung: lokale `usedBytes`-Variable, einmalige Messung des fertigen Abschnitts und Zählererhöhung erst nach dessen atomarer Aufnahme. Es gibt keine erneute Messung des gesamten bisherigen Puffers.

Vorhandener Recall-Observer, Zeitpunkt und Inhalt seiner Benachrichtigung, #522-Widerspruchs-Privacy, Quellenidentität und Ausschluss mehrdeutiger bzw. nicht zulässiger Erinnerungen bleiben erhalten. Die Observer- und Privacy-Blöcke wurden gegen die Integrationsbasis bytegleich abgeglichen. Tests, Activity, Darstellung, Speicherung und Freigabepfade werden hier nicht verändert.

## Prüfung und nächste Schritte

Durchgeführt: gepinnter GitHub-/Archiv-Blobabgleich, semantischer Diffreview, bytegleicher Abgleich der erhaltenen Blöcke und `git diff --check`. **Keine Tests, keine Gradle-/SDK-/UI-Ausführung** in diesem Helper. Die vom Quell-PR dokumentierten 100 SDK/UI-PASS und NewApi 0 gelten ausschließlich für dessen geprüfte Quelle; sie sind kein Ergebnis dieser Integration.

Root übernimmt die bereits eingefrorenen fünf C1-Testdateien separat und führt die gemeinsame Abnahme aus. Die verständliche UI-Einordnung von `UNTERMINATED_CREDENTIAL_VALUE` bleibt entsprechend dem Quellhandoff bei C1; hier wird ausschließlich der abgeschlossene Compiler-/Policy-Fix verbunden. Bestehende Datensätze werden nicht neu klassifiziert, und die Einstufung verwendet weiterhin das vorhandene Sensitivitätsmerkmal.

Bei einem Integrationsfehler diese beiden Dateien gegen Basis und gepinnte Quelle vergleichen. Ein Revert dieses isolierten Commits entfernt nur diesen Anschluss, stellt aber die dokumentierte Redaction-Lücke wieder her; er ersetzt keine sichere Fehlerbehandlung.
