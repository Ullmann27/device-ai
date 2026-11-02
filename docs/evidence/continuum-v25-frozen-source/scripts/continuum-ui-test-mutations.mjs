#!/usr/bin/env node
// Mutation check for the start-screen source tests (owner request: the reworked tests must keep testing real
// functions). Each mutation breaks exactly one real behaviour (a route, a handler, an accessibility or honesty rule)
// in the working tree, runs the one test that is supposed to guard it and expects it to FAIL; the file is restored
// byte for byte afterwards. Refuses to run on a dirty checkout. Usage: node scripts/continuum-ui-test-mutations.mjs
import fs from 'node:fs';
import path from 'node:path';
import {spawnSync} from 'node:child_process';
import {fileURLToPath} from 'node:url';

const ROOT=path.resolve(path.dirname(fileURLToPath(import.meta.url)),'..');
const A='app/src/main/java/at/heinz/deviceai/AgentActivity.kt';
const UI='app/src/main/java/at/heinz/deviceai/ui/';
const H='.github/continuum/android-holographic-home.test.mjs',I='.github/continuum/android-integration-wiring.test.mjs',
 N='.github/continuum/app-navigation-wiring.test.mjs',B='.github/continuum/nexus-beginner-ui.test.mjs',
 C='.github/continuum/coding-agent-speech-wiring.test.mjs',M='.github/continuum/mobile-builder-existing-integration.test.mjs';
const S='at.heinz.deviceai.ui.ContinuumHomeLayout.Slot.';

export const MUTATIONS=[
 {file:UI+'ContinuumHologramRenderer.kt',from:'drawOrbits(p, f, front = true)',to:'Unit',test:H,name:'transparent native 3D hero'},
 {file:A,from:S+'LABEL_ANALYSING -> showResearchModeDialog()',to:S+'LABEL_ANALYSING -> Unit',test:H,name:'reference start screen reaches all seven'},
 {file:UI+'ContinuumHomeView.kt',from:'            isFocusable = true\n            importantForAccessibility',to:'            isFocusable = false\n            importantForAccessibility',test:H,name:'responsive dashboard retains chat composer'},
 {file:UI+'ContinuumHomeView.kt',from:'resources.configuration.fontScale.coerceIn(.85f, 1.3f)',to:'1f',test:H,name:'large text scales the drawn start screen'},
 {file:'app/src/main/java/at/heinz/deviceai/ContinuumDashboardCollector.kt',from:'online = live.modelReady,',to:'online = live.modelReady || status != null,',test:H,name:'start screen never manufactures'},
 {file:A,from:S+'MODEL_PILL, '+S+'MODEL -> showModelCenter()',to:S+'MODEL_PILL, '+S+'MODEL -> Unit',test:I,name:'local language model path is reachable'},
 {file:A,from:'                    3 -> showSystemCenter()',to:'                    3 -> Unit',also:[{from:S+'SYSTEM_STATUS -> showSystemCenter()',to:S+'SYSTEM_STATUS -> Unit'}],test:I,name:'every area has a center reachable from the start screen'},
 {file:UI+'ContinuumHomeView.kt',from:'            contentDescription = element.describe(state)\n',to:'            contentDescription = "Element"\n',test:I,name:'new visual start screen is local, accessible and honest'},
 {file:A,from:'                setOnClickListener { navigateWorkArea(destination) }\n            }, LinearLayout.LayoutParams(0',to:'                setOnClickListener { }\n            }, LinearLayout.LayoutParams(0',test:N,name:'home work areas are reachable without expert-only controls'},
 {file:A,from:'"Lernen" to { navigateWorkArea(ContinuumWorkArea.LERNEN) }',to:'"Lernen" to { }',test:N,name:'Fold rail offers the five work areas'},
 {file:A,from:'element.targets((b - t) / density)',to:'element.targets(649f)',test:N,name:'Fold rail and start screen reach all actions at short heights'},
 {file:UI+'ContinuumHomeLayout.kt',from:'widthDp >= 700f && heightDp >= 500f',to:'widthDp >= 700f',test:N,name:'start screen adapts to large fonts'},
 {file:A,from:'                    4 -> openMobileBuilder()',to:'                    4 -> Unit',test:N,name:'existing chat, learning monitoring and owner permission boundaries remain'},
 {file:A,from:S+'PROFILE -> showNexusPersonalization()',to:S+'PROFILE -> Unit',test:B,name:'the five beginner destinations stay reachable'},
 {file:A,from:'if (heightDp > 0f && y / heightDp > .78f) showMentorSources() else showMemoryCenter()',to:'showMemoryCenter()',test:B,name:'start-screen routes use real existing Android screens'},
 // research stays reachable through four entry points (core label, rail, More sheet, composer); remove all of them
 {file:A,from:'"Analysieren" to { showResearchModeDialog() }',to:'"Analysieren" to { }',also:[{from:S+'LABEL_ANALYSING -> showResearchModeDialog()',to:S+'LABEL_ANALYSING -> Unit'},
  {from:'                    0 -> showResearchModeDialog()',to:'                    0 -> Unit'},
  {from:'smallButton("◎ Web", "Recherche nur nach Freigabe") {\n                showResearchModeDialog()',to:'smallButton("◎ Web", "Recherche nur nach Freigabe") {\n                Unit'}],
  test:B,name:'advanced routes remain available'},
 {file:UI+'ContinuumHomeView.kt',from:'resources.configuration.fontScale.coerceIn(.85f, 1.3f)',to:'1f',test:B,name:'fold, small display and enlarged-font behaviour'},
 {file:A,from:'chatColumn?.visibility = if (chat) View.VISIBLE else View.GONE',to:'chatColumn?.visibility = View.VISIBLE',test:B,name:'start screen and chat never overlap'},
 {file:A,from:'CenterAction("ENTWICKLUNGSAUFGABE · STARTEN", accent = true) { showDevelopmentDialog() }',to:'CenterAction("ENTWICKLUNGSAUFGABE · STARTEN", accent = true) { }',test:C,name:'coding agent has one integrated entry'},
 {file:A,from:'"System & Sicherheit", "Handy-Builder · Termux"',to:'"System & Sicherheit"',test:M,name:'builder stays in the existing agent UI'}
];

function run(test,name){
 const env={...process.env};delete env.NODE_TEST_CONTEXT;
 const r=spawnSync(process.execPath,['--test','--test-name-pattern='+name.replace(/[.*+?^${}()|[\]\\]/g,'\\$&'),test],
  {cwd:ROOT,encoding:'utf8',timeout:120000,env});
 const tests=Number(/# tests (\d+)/.exec(r.stdout)?.[1]??0);
 return {exit:r.status,tests,failed:Number(/# fail (\d+)/.exec(r.stdout)?.[1]??0)};
}

function main(){
 const dirty=spawnSync('git',['status','--porcelain','--untracked-files=no'],{cwd:ROOT,encoding:'utf8'}).stdout.trim();
 if(dirty)throw Error('working tree must be clean (mutations are applied in place and restored)');
 const rows=[];
 for(const m of MUTATIONS){
  const file=path.join(ROOT,m.file);
  const original=fs.readFileSync(file);
  const text=original.toString('utf8');
  const edits=[{from:m.from,to:m.to},...(m.also??[])];
  for(const e of edits)if(text.split(e.from).length!==2)throw Error('mutation anchor not unique in '+m.file+': '+e.from.slice(0,60));
  const before=run(m.test,m.name);
  let mutated=text;for(const e of edits)mutated=mutated.replace(e.from,e.to);
  fs.writeFileSync(file,mutated);
  let after;
  try{after=run(m.test,m.name);}finally{fs.writeFileSync(file,original);}
  const killed=before.exit===0&&before.tests===1&&after.exit!==0&&after.failed===1;
  rows.push({test:m.test.split('/').pop(),name:m.name,mutation:m.file.split('/').pop()+': '+m.from.slice(0,70),greenBefore:before.exit===0&&before.tests===1,killed});
  console.log((killed?'KILLED ':'SURVIVED ')+m.test.split('/').pop()+' :: '+m.name);
 }
 const clean=spawnSync('git',['status','--porcelain','--untracked-files=no'],{cwd:ROOT,encoding:'utf8'}).stdout.trim();
 if(clean)throw Error('checkout not restored: '+clean);
 console.log(`SUMMARY ${rows.filter(r=>r.killed).length}/${rows.length} mutations detected by their test`);
 return rows;
}
if(process.argv[1]===fileURLToPath(import.meta.url)){const rows=main();if(rows.some(r=>!r.killed))process.exitCode=1;}
