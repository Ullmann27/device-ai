import test from 'node:test';
import assert from 'node:assert/strict';
import fs from 'node:fs';
import path from 'node:path';
import {fileURLToPath} from 'node:url';

const root=path.resolve(path.dirname(fileURLToPath(import.meta.url)),'../..');
const activity=fs.readFileSync(path.join(root,
  'app/src/main/java/at/heinz/deviceai/AgentActivity.kt'),'utf8');
const start=activity.indexOf('private fun buildQuickActions(): View');
const end=activity.indexOf('private fun buildComposer(): View',start);

test('Device AI quick actions are exclusive to Continuum and not to unrelated apps',()=>{
  assert.ok(start>=0&&end>start,'Cockpit quick-action area must exist');
  const shortcuts=activity.slice(start,end);
  assert.doesNotMatch(shortcuts,/Lumo/i);
  for(const label of ['KI-Modell','Teamleiter','System','Downloads','Speicher','Akku','Web']){
    assert.ok(shortcuts.includes('"'+label+'" to'),label+' is missing');
  }
});

test('Every cockpit shortcut is connected to a usable UI or command',()=>{
  const shortcuts=activity.slice(start,end);
  for(const [label,handler] of [
    ['KI-Modell','showModelCenter()'],
    ['Teamleiter','runDeviceTeamleaderCheck()'],
    ['System','showSystemCenter()'],
    ['Web','showResearchModeDialog()']
  ]){
    assert.ok(shortcuts.includes('"'+label+'" -> '+handler),label+' handler missing');
  }
  assert.match(shortcuts,/else -> \{\s*input\.setText\(command\)[\s\S]*?focusChatComposer\(\)/);
  assert.match(shortcuts,/contentDescription = when \(label\)/);
});

test('Cockpit shortcuts remain accessible on narrow phones and folding displays',()=>{
  const shortcuts=activity.slice(start,end);
  assert.match(shortcuts,/resources\.configuration\.screenWidthDp >= 700/);
  assert.match(shortcuts,/val columns = if \(resources\.configuration\.screenWidthDp >= 700\) 4 else 3/);
  assert.match(shortcuts,/actions\.chunked\(columns\)/);
  assert.match(shortcuts,/LinearLayout\.LayoutParams\(0, dp\(45\), 1f\)/);
  assert.doesNotMatch(shortcuts,/HorizontalScrollView\(/);
});
