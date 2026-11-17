# Originalsignierung – geprüfter Code55-Testkandidat

Die vollständige QA [43373431089](https://github.com/Ullmann27/device-ai/actions/runs/43373431089)
und der APK-Lauf [43373431159](https://github.com/Ullmann27/device-ai/actions/runs/43373431159)
sind bestanden. Quelle: `889e06d47fffd2230fa5b062dd1f31b65992e728`.

Der bestehende Workflow verlangt eine ausdrückliche Freigabe genau dieses
Quellstands und anschließend die geschützte Besitzerbestätigung. Diese
Freigaben wurden hier nicht stellvertretend eingetragen. Der verfügbare
GitHub-Connector kann keinen Workflow starten; im gemeinsamen Browser ist
keine GitHub-Anmeldung vorhanden. Deshalb folgt jetzt dieser konkrete
Besitzerschritt.

1. [Signierworkflow öffnen](https://github.com/Ullmann27/device-ai/actions/workflows/continuum-code52-sign-release.yml).
2. **Run workflow** öffnen und als Branch
   `chatgpt/code52-owner-signing-20261109` wählen.
3. Diese Werte eintragen:

| Feld | Wert |
| --- | --- |
| `source_sha` | `889e06d47fffd2230fa5b062dd1f31b65992e728` |
| `qa_run_id` | `43373431089` |
| `owner_approved_sha` | `889e06d47fffd2230fa5b062dd1f31b65992e728` |

4. Mit **Run workflow** bestätigen. Nach bestandenem Preflight die
   angezeigte Prüfung für `continuum-original-signing` als Besitzer
   freigeben. Die Oberfläche kann dafür **Review deployments** anzeigen.
5. Der Lauf prüft Quelle, vollständige QA, Originalzertifikat, Paket,
   APK-Hash und Signatur erneut und legt einen **unveröffentlichten
   Testentwurf** an. Die bisherige Latest-Veröffentlichung bleibt erhalten.
6. Erst die erfolgreich originalsignierte APK aus diesem Entwurf ist für
   den Update-Test vorgesehen. Der [Fold7-Testablauf](FOLD7-TESTABLAUF.md)
   nennt die konkret implementierten und noch ungeprüften Schritte.

Geschützter Signiercode:
`f232d34c5f28be3375d71f861120d6f673a62157`,
[PR #828](https://github.com/Ullmann27/device-ai/pull/828), 61 bestandene
Tests ohne Verwendung von Signiergeheimnissen. Die tatsächliche
Environment-Konfiguration und Signierung sind hier noch nicht überprüft;
der Preflight bricht bei fehlenden Voraussetzungen ab. Die Gates werden
dafür nicht abgeschaltet.

Unsigned APK: 103.787.127 Byte; SHA256
`d8fb018277076009631118616775d41688aaad455856c4b5b79b207dc8251332`.
Die signierte Datei erhält eine andere Prüfsumme, die der Signierlauf erst
tatsächlich erzeugen und prüfen muss.
