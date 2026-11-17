<!-- coderabbit-ai-generated:v1 -->

# PR #351: unabhängige Node- und P0-Analyse

Stand 2. November 2026. Untersucht wurde PR #351 bei `057474de37d6e20d993bd64f166b6585bc4a357a`, Baum `0a4fb4f025e46fc9f489963ba67f0861b9179153`, auf Branch `coderabbit/get-device-ai-project-status/de3d7eb3`. Ubuntu 26.04.1, Node 24.21.0, npm 11.19.0; vollständiger Checkout, keine echten Nutzerdaten, lokale temporäre Prozesse und Loopback-Storage. Die Prüfung betrifft EXP-00/02/08/11 und den experimentellen Storage-Prototyp; keine Schema-, Android-, Signatur- oder Produktionsänderung. Rollback: diesen Dokumentationscommit auf dem isolierten Branch revertieren.

## Ausgeführte Prüfungen

Alle Befehle im Repository-Wurzelverzeichnis auf dem genannten sauberen Ausgangsbaum:

| Befehl | Exit | Beobachtung |
|---|---:|---|
| `node --test .github/continuum/coding-agent-loop.test.mjs` | 0 | 15 bestanden; darunter die zuvor fehlgeschlagene Revision-Recovery-Prüfung. |
| `node --test .github/continuum/step-watchdog.test.mjs .github/continuum/storage-fabric.test.mjs` | 0 | 14 bestanden, 0 übersprungen. |
| `node --test scripts/continuum-architecture-proof.test.mjs .github/continuum/*test.mjs` | 1 | 1.301 Tests: 1.277 bestanden, 11 fehlgeschlagen, 13 übersprungen. Beide historischen P0-Fälle und der Coding-Agent-Fall bestanden. |
| `node /tmp/device-ai-watchdog-probe.mjs` | 0 | Synthetischer `/proc`-Zugriffsschutz, erfolgreicher direkter Kindprozess: Watchdog gibt 0 zurück. |
| `NODE_OPTIONS='--require=/tmp/device-ai-deny-proc.cjs' node --input-type=module -e 'import {runOwnedCommand} from "./scripts/continuum-owned-process.mjs"; const r=await runOwnedCommand(process.execPath,["-e","process.exit(0)"],{timeout:5000}); console.log(JSON.stringify({ok:r.ok,code:r.code,error:r.error,terminationConfirmed:r.terminationConfirmed})); if(r.ok!==false||r.code!==0||r.terminationConfirmed!==false)process.exitCode=1;'` | 0 | `ok:false`, `code:0`, `error:TERMINATION_UNCONFIRMED`, `terminationConfirmed:false`. |
| `node /tmp/device-ai-watchdog-orphan-probe.mjs` | 0 | Watchdog meldet Exit 0 und ein abgekoppelter Kindprozess lebt noch; der Test räumt ihn per SIGKILL auf. |

Die 13 übersprungenen Tests benötigen unter anderem konfigurierte Kotlin-/JUnit-/Shizuku-Abhängigkeiten, Harness SDK oder Docker. Android-/Gradle-/Fold7-Tests und Power-Loss-Tests wurden nicht ausgeführt. Ein grüner Node-Test belegt keine Geräteabnahme. Die historischen 16 Fehler aus dem PR-Dokument stammen von einem älteren Lauf und sind keine Anzahl der Fehler dieses Laufs.

## Prozesskontrolle

**Nachgewiesen unter künstlichem Fehler:** `runOwnedCommand` meldet bei nicht lesbarem `/proc` unbestätigte Terminierung; `step-watchdog.mjs` reicht dennoch `result.code` durch, wenn dieser 0 ist. Der gezielte Test verweigert `readFileSync` für `/proc/<pid>/stat` durch einen Node-Preload, startet einen echten abgekoppelten Kindprozess und beobachtet nach erfolgreichem Ende des direkten Prozesses sowohl Watchdog-Exit 0 als auch den lebenden Kindprozess. Das simuliert eine Berechtigungsstörung; es beweist nicht, dass sie auf dem Fold7 auftritt. Bei ausreichender Testergebnis-Evidenz kann `repair-loop.mjs` einen Status 0 weiter als erfolgreichen Profilabschluss behandeln. Ein Owner-Abbruch ist dieser Versuch nicht.

**Historischer Owner-Abbruch:** Die frühere Assertion auf `terminationConfirmed` bestand isoliert und im erweiterten Lauf. Der frühere Fehler war an dieser Assertion; der Test erreichte die folgende Prüfung des tatsächlichen Prozesszustands nicht. Ob damals ein Prozess weiterlebte und was genau die unbestätigte Terminierung auslöste, ist unbekannt. `/proc`-Tracking ist ausdrücklich keine Sandbox gegen feindliche Tools.

**Reparaturvorschlag:** Einen separaten, gegen Kollisionen geschützten strukturierten Abschlussstatus für *alle* überwachten Schritte einführen, einschließlich `terminationConfirmed` und Ursache; der Repair-Loop darf bei fehlendem, fehlerhaftem oder negativem Nachweis keinen Erfolg melden und muss weitere Arbeit/Lease-Freigabe gemäß dem bestehenden Aufrufervertrag stoppen. Regressionen: normaler Exit und Signale, verweigerte `/proc`-Zugriffe mit lebendem detached Kind, Owner-Abbruch mit bestätigter und unbestätigter Terminierung, Timeout, nicht vorhandener/kaputter Status und unabhängige Prozesszustandsprüfung. Verhalten unter Android/Termux separat prüfen.

**Vertragskonflikt:** Der bestehende Kopfkommentar von `step-watchdog.mjs` verspricht unveränderte Weitergabe von Ausgabe, Exit-Code und Signal; die vorgeschlagene Ausnahme für unbestätigte Terminierung ändert die beobachtbare Erfolgssemantik. Ein neuer Statuskanal muss mit `repair-loop.mjs`, `ccc1-independent-verifier.mjs` und allen übrigen Aufrufern abgestimmt werden. Deshalb wurde keine Laufzeitänderung vorgenommen.

## Storage-Durability

**Beobachtet:** Der historische Fehler `FULL_REPLICATION_REQUIRED_METADATA_NOT_COMMITTED` trat beim initialen `put` auf; in beiden neuen Läufen bestand der Versions-/Tombstone-Test. Es gibt keinen Beleg für tatsächlichen Datenverlust oder dafür, dass die damalige Ursache ein Timeout war. Der Testadapter setzt ein 500-ms-Limit; Timeout oder Last sind mögliche Ursachen, die pauschale Fehlermeldung verwirft jedoch die Einzelursachen.

**Bestehender Vertrag:** Alle Replikate müssen den Block akzeptieren, bevor Katalogmetadaten committen; Tombstone muss vor Löschung persistieren. Der Katalog ist zentral und nicht hochverfügbar. Laut `docs/research/omega/SOL-STORAGE-FABRIC-20261027.md` können fehlgeschlagene Writes verwaiste verschlüsselte Blöcke hinterlassen; fsync/rename beweist keinen Power-Loss-Erfolg, und Crash kann Lock/temporäre Dateien hinterlassen. Die erfolgreichen Tests prüfen kontrollierten Neustart, nicht Stromausfall.

**Reparaturvorschlag:** Replikatfehler datensparsam nach Node-ID und Fehlerklasse (Timeout/HTTP/IO) beobachten, ohne Token, Daten oder URLs zu protokollieren. Mit gezielter Fehler- und Verzögerungsinjektion testen: ein fehlendes oder langsames Replikat darf niemals Metadaten committen; danach Revision/Lesbarkeit/Restart kontrollieren. Für Tombstones Prozessabbrüche vor und nach Dateisync, Rename und Verzeichnissync simulieren und nach Wiederanlauf HMAC, Revision, ausstehende Löschungen und unveränderte andere Objekte prüfen. Quoten und verwaiste Blöcke getrennt untersuchen. Zeitlimits nur nach gemessenen Ursachen ändern, niemals Replikations- oder Tombstone-Gates abschwächen.

## Elf verbleibende Node-Fehler

Alle sind fehlgeschlagene Quelltext-/Verdrahtungs-Assertions, keine bestätigten Android-Laufzeitdefekte:

1. ALWAYS_ON: `InternetAccessMode.valueOf` in Activity nicht gefunden.
2. Activity-Requestcodes: behaupteter `SPEECH_REQUEST`-Handler nicht gefunden.
3. Wikipedia: erwartete Provider-Verdrahtung nicht gefunden.
4. Owner-Fakten: erwartete Kennzeichnung `nicht unabhängig verifizierter` nicht gefunden.
5. Developer Speech: erwartetes `RecognizerIntent.ACTION_RECOGNIZE_SPEECH` in Activity nicht gefunden.
6. Voice Preview: erwarteter Activity-Quelltextabschnitt nicht gefunden.
7. Gemma-Import: erwartetes `modelCatalog.import` nicht gefunden.
8. Backend-Frage: erwartetes `put("provider", "GROQ")` nicht gefunden.
9. Importfehler: erwartetes `localImportError = error.message` im ermittelten Abschnitt nicht gefunden.
10. Nexus: erwartetes `RecognizerIntent.ACTION_RECOGNIZE_SPEECH` in Activity nicht gefunden.
11. Research Permission: erwartetes `if(!enabled)` nicht gefunden.

Pro Fall ursprüngliche Invariante, aktuellen Kotlin-Caller und tatsächliche Laufzeittests prüfen. Nur nach Beleg einer äquivalenten Verlagerung den Test auf die neue Schnittstelle umstellen; andernfalls die Implementierung reparieren. Insbesondere Consent, ToolGate, Quellenkennzeichnung und Sprachvorschau nicht durch Ersatz von Regex allein als erfüllt erklären.

## Fortsetzung

Zuerst den oben beschriebenen Statusvertrag entscheiden, dann RED/GREEN-Regressionen und getrennte Last-/Fehlerinjektion implementieren. Alle elf Quelltests einzeln gegen die ursprüngliche Anforderung und den aktuellen Aufrufpfad nachverfolgen; anschließend dieselbe vollständige Node-Suite und die betroffenen Android-Tests erneut ausführen. <!-- coderabbit-ai-generated:v1 -->

<!-- coderabbit-ai-generated-section:v1:94292dc7b8182654ea0d01be7e9b0de2b59a572d22286213cd666b1b6e5d2fae -->

Keine APK freigeben, keine Gewichte promoten, keine fremden Branches ändern. PR #351 bleibt unabhängig und unverändert.

## Reproduktionsskripte

Auf dem Prüfhost aus dem Repository-Wurzelverzeichnis: Dateien wie gezeigt unter `/tmp` ablegen und `node /tmp/device-ai-watchdog-orphan-probe.mjs` ausführen. Der Prozess wird im `finally` beendet. Ein erfolgreiches Ergebnis zeigt die Sicherheitslücke, nicht eine erfolgreiche Reparatur.

### `/tmp/device-ai-deny-proc.cjs`

```js
const fs = require('node:fs');
const original = fs.readFileSync;
fs.readFileSync = function(file, ...args) {
  if (/^\/proc\/[0-9]+\/stat$/.test(String(file))) throw Object.assign(new Error('synthetic denial'), {code:'EACCES'});
  return original.call(this, file, ...args);
};
```

### `/tmp/device-ai-watchdog-orphan-probe.mjs`

```js
import {spawnSync} from 'node:child_process';
import path from 'node:path';
import fs from 'node:fs';
const marker='/tmp/device-ai-orphan-probe.pid';
try { fs.unlinkSync(marker); } catch (e) { if(e.code!=='ENOENT') throw e; }
const code=`const {spawn}=require('node:child_process'); const fs=require('node:fs'); const p=spawn(process.execPath,['-e','setInterval(()=>{},1000)'],{detached:true,stdio:'ignore'}); fs.writeFileSync(${JSON.stringify(marker)},String(p.pid));p.unref();`;
const env={...process.env,NODE_OPTIONS:'--require=/tmp/device-ai-deny-proc.cjs'};
let pid;
try {
 const r=spawnSync(process.execPath,[path.resolve('.github/continuum/step-watchdog.mjs'),'/tmp/missing-backlog.json','repair-watched','/tmp/device-ai-unused-receipt.json','5000',String(process.pid),'--',process.execPath,'-e',code],{env,encoding:'utf8',timeout:10000});
 pid=Number(fs.readFileSync(marker,'utf8')); let live=false; try {process.kill(pid,0);live=true;} catch {}
 console.log(JSON.stringify({watchdogStatus:r.status,detachedChildAlive:live}));
 if(r.status!==0||!live)process.exitCode=1;
} finally {if(pid)try {process.kill(pid,'SIGKILL');}catch{};try{fs.unlinkSync(marker);}catch{}}
```


<!-- /coderabbit-ai-generated:v1 -->
