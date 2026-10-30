# M6 · Reproduzierbarer Fold7-Abnahmepfad (vorbereitet, nicht ausgeführt)

**Status: BLOCKIERT auf Gerät.** Diese Umgebung hat kein Fold7, kein Android-SDK (dl.google.com 403) und keinen Signaturschlüssel. Alles unten ist vorbereitet. Bis auf das Erzeugen der Vorlage ist **nichts davon hier gelaufen**.

## Grundsatz

Jeder Schritt bindet an **einen** Quellcommit dieses Zweigs (`opus/continuum-m1-m8-20261030`). Ein Ergebnis gilt nur für diesen Commit. Host-Tests ersetzen keine Gerätemessung. Selbst eingetragene PASS-Werte bleiben unbeglaubigt.

## Schritte (in dieser Reihenfolge, jeder bricht bei Abweichung ab)

1. **Sauberer Checkout auf dem Fold7 (Termux), exakter Commit**, keine vorhandene Arbeit überschreiben:
   ```sh
   cd "$HOME/device-ai-fold7-candidate" && test -z "$(git status --porcelain)" && \
   git fetch origin opus/continuum-m1-m8-20261030 && git switch -c review/continuum-m18-<SHA12> <SHA> && \
   bash scripts/continuum-fusion-full-regression.sh <SHA>
   ```
   Das ist das in #242 dokumentierte unsignierte QA-Skript mit nativem AAPT2-Pfad. Es baut und installiert nichts.
2. **Host-Belege prüfen** (gehärtet in #244; Exit 2 = konsistent, Gerät offen):
   `node scripts/continuum-host-evidence-gate.mjs <SHA> <beleg-ordner>`
3. **Geräteprotokoll mit 12 Fällen** (aus #243): Vorlage erzeugen, auf dem Gerät ausfüllen und prüfen.
   `node scripts/continuum-fold7-acceptance.mjs --template <SHA>` → ausfüllen → `--check <bericht.json> <SHA>`
   Pflichtfälle sind unter anderem: Offline-Gemma-Dialog, Pronomen/Korrektur/Zeit, Widerruf, Kaltstart, Falten während Streaming, Abbruch ohne Doppelaktion, ToolGate-Ablehnung, 30 Minuten Temperatur/Speicher/Latenz.
4. **Zusätzlich für #244 auf dem Gerät zu erheben (lesend):**
   - Flag-Rate der neuen Aussagenprüfung: Anteil `CLAIM_AUDIT_UNSUPPORTED` an belegten Gemma-Antworten, gegen das Besitzer-Feedback (`CONFIRMED/CORRECTED`) derselben Traces.
   - ART-Latenz der Shadow-Pfade (Host-Wert: +60 µs/Zug bzw. 0,7 ms/Antwort, M7).
5. **Signierte Aktualisierung erst danach** und nur über den getrennt zu prüfenden Updater aus #236 mit Besitzerfreigabe. Der Besitzer signiert mit dem originalen Schlüssel auf dem Fold7. Schlüssel kommen nie in Chat oder Repository.

## Prüfung PR #236 (nur gelesen, nicht verändert)

| Prüfpunkt | Befund |
|---|---|
| Vorab-Policy | Paket `at.heinz.deviceai.continuumlocal`, Kanal OWNER_RELEASE, strikt höhere `versionCode`, ausdrückliche Freigabe, Signer-Pin, HTTPS nur zum fest eingebauten Host ohne Query/Fragment/Benutzerdaten, Pfadmuster mit SHA im Dateinamen |
| Download | Größe begrenzt und exakt, SHA-256 über den Datenstrom, fsync |
| Archivprüfung | Paketname, `versionCode` = Freigabe und > installiert, Signer des Archivs = Pin = installierter Signer, genau ein Signer |
| Installation | `PackageInstaller`-Sitzung, Systembestätigung durch den Besitzer, Abbruch verwirft die Sitzung |
| Anmerkungen (kein Fehler) | `longVersionCode.toInt()` ignoriert `versionCodeMajor` (heute 0). Der Signer-Pin `6354e91d…` kann hier nicht gegen das echte Besitzerzertifikat geprüft werden; das muss der Besitzer auf dem Gerät bestätigen. Android erzwingt zusätzlich dieselbe Signatur beim Update. |
| Ergebnis | Kein ausnutzbarer Fehler gefunden. #236 braucht trotzdem die getrennte Sicherheits- und Lebenszyklusabnahme mit einem echten Zweit-Update auf dem Gerät. |
