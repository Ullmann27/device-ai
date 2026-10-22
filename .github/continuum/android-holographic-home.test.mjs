import test from 'node:test';
import assert from 'node:assert/strict';
import fs from 'node:fs';
import {workspaceNav} from './workspace-nav-contract.mjs';

const activity=fs.readFileSync('app/src/main/java/at/heinz/deviceai/AgentActivity.kt','utf8');
const orb=fs.readFileSync('app/src/main/java/at/heinz/deviceai/ContinuumHologramView.kt','utf8');
const brand=fs.readFileSync('app/src/main/res/drawable/device_ai_holo_logo.xml','utf8');
const section=(start,end)=>{
 const a=activity.indexOf(start),b=activity.indexOf(end,a);
 assert.ok(a>=0&&b>a,'Expected real Android UI method boundaries '+start);
 return activity.slice(a,b);
};

test('transparent native 3D hero has no network, model execution or fake status',()=>{
 assert.match(orb,/class ContinuumHologramView/);
 assert.match(orb,/override fun onDraw\(canvas: Canvas\)/);
 assert.match(orb,/RadialGradient\(/);
 assert.match(orb,/drawOrbit\(canvas/);
 assert.match(orb,/Color\.TRANSPARENT/);
 const drawableCode=orb.replace(/\/\*[\s\S]*?\*\//g,'').replace(/\/\/[^\n]*/g,'');
 assert.doesNotMatch(drawableCode,/(?:http:|https:|WebView|model\.generate|networkRequest|canvas\.drawColor\()/);
 assert.match(brand,/<vector\b/);
 assert.match(brand,/android:pathData=/);
 assert.doesNotMatch(brand,/<bitmap\b/);
});

test('image-inspired start screen preserves all seven real Android feature paths',()=>{
 const dashboard=section('private fun buildBootstrapCoreCard(): View',
   'private fun focusChatComposer()');
 const targets=[
  ['Chat','focusChatComposer()'],
  ['Forschung','showResearchModeDialog()'],
  ['Lernen','showLearningCenter()'],
  ['Modelle','showModelCenter()'],
  ['Entwicklung','showDevelopmentCenter()'],
  ['Speicher','showMemoryCenter()'],
  ['System','showSystemCenter()']
 ];
 assert.match(dashboard,/ContinuumHologramView\(this\)/);
 assert.match(dashboard,/buildStatusRow\(\)/);
 // work areas come from the always-visible grid (owner request 22.10.2026); System keeps its own button
 const nav=workspaceNav(activity);
 for(const [label,call] of targets){
  if(label==='System'){
   assert.ok(dashboard.includes('System   ·')&&dashboard.includes(call),'Missing System button');
   continue;
  }
  assert.ok(nav.homeReaches(dashboard,call.replace('()','')),'Missing AI navigation tile '+label);
  assert.ok(activity.includes('private fun '+call.replace('()','')+'('),'Missing live handler '+call);
 }
 assert.match(dashboard,/neuralCuriosityButton = actionButton/);
 assert.doesNotMatch(dashboard,/Lumo/i);
});

test('responsive dashboard retains chat composer, updates and keyboard visibility',()=>{
 const pane=section('private fun buildConversationPane(wide: Boolean): View',
   'private fun buildHeader(wide: Boolean): View');
 const home=section('private fun buildUi(): View',
   'private fun buildSidebar(): View');
 const tile=section('private fun cockpitTile(', 'private fun violetTint(): Int');
 assert.match(pane,/dashboardScroll/);
 assert.match(pane,/buildComposer\(\)/);
 assert.match(pane,/chatScroll/);
 assert.match(home,/ComposerUiPolicy\.viewport/);
 assert.match(home,/keyboardCompact/);
 assert.match(tile,/isClickable = true/);
 assert.match(tile,/isFocusable = true/);
 assert.match(tile,/contentDescription = title/);
 assert.match(tile,/setOnClickListener \{ open\(\) \}/);
 assert.doesNotMatch(pane,/buildQuickActions\(\)/);
});

test('reference-style composer keeps real model/web/file actions and truthful idle state',()=>{
 const composer=section('private fun buildComposer(): View',
   'private fun sendCurrentInput()');
 const busy=section('private fun setBusy(value: Boolean)',
   'private fun scrollToBottom()');
 assert.match(composer,/hint = "Schreibe eine Nachricht …"/);
 assert.match(composer,/smallButton\("✧ Denken"/);
 assert.match(composer,/smallButton\("◎ Web"/);
 assert.match(composer,/smallButton\("⌁ Datei"/);
 assert.match(composer,/showResearchModeDialog\(\)/);
 assert.match(composer,/showDevelopmentDialog\(\)/);
 assert.match(composer,/localRuntime\.state == LocalModelState\.READY/);
 assert.match(composer,/else showModelCenter\(\)/);
 assert.match(composer,/startVoiceInput\(\)/);
 assert.match(composer,/sendCurrentInput\(\)/);
 assert.match(busy,/sendButton\.contentDescription/);
 assert.match(busy,/else "➤"/);
});

test('large text can expand neon tiles instead of being cropped by fixed row heights',()=>{
 const dashboard=section('private fun buildBootstrapCoreCard(): View',
   'private fun focusChatComposer()');
 const tile=section('private fun cockpitTile(', 'private fun violetTint(): Int');
 assert.match(dashboard,/LinearLayout\.LayoutParams\.WRAP_CONTENT, 1f/);
 assert.match(tile,/minimumHeight = dp\(94\)/);
 assert.match(tile,/maxLines = 2/);
});

test('branded device dashboard never manufactures ready model or internet state',()=>{
 const header=section('private fun buildHeader(wide: Boolean): View',
   'private fun buildStatusRow(): View');
 const badges=section('private fun buildStatusRow(): View',
   'private fun buildModeRow(): View');
 assert.match(header,/device_ai_holo_logo/);
 assert.match(header,/PRIVAT   •   LOKAL   •   LEISTUNGSSTARK/);
 for(const token of ['shizukuBadge','cloudBadge','localBadge']){
  assert.ok(badges.includes(token));
 }
 assert.doesNotMatch(badges,/Llama 3\.2|Gemini 1\.5|Modell geladen/);
});
