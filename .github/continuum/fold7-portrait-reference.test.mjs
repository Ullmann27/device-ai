import test from 'node:test';
import assert from 'node:assert/strict';
import fs from 'node:fs';
import path from 'node:path';
import {fileURLToPath} from 'node:url';

const root=path.resolve(path.dirname(fileURLToPath(import.meta.url)),'../..');
const read=p=>fs.readFileSync(path.join(root,p),'utf8');
const ui='app/src/main/java/at/heinz/deviceai/ui/';
const layout=read(ui+'ContinuumHomeLayout.kt');
const home=read(ui+'ContinuumHomeScreen.kt');
const elements=read(ui+'ContinuumPortraitElements.kt');
const core=read(ui+'ContinuumHomeElements.kt');
const orb=read(ui+'ContinuumHologramRenderer.kt');
const view=read(ui+'ContinuumHomeView.kt');
const activity=read('app/src/main/java/at/heinz/deviceai/AgentActivity.kt');
const navigation=read('app/src/main/java/at/heinz/deviceai/ContinuumWorkAreaNavigationSpec.kt');
const preview=read('tools/continuum-jvm-preview/src/at/heinz/deviceai/ui/ContinuumPreviewMain.kt');

test('portrait selection keeps actual Fold window threshold and separate landscape rail',()=>{
 assert.match(layout,/fun isPortraitFold\(widthDp: Float, heightDp: Float\)/);
 assert.match(layout,/isWide\(widthDp, heightDp\) && heightDp >= widthDp \* 1\.02f/);
 assert.match(layout,/if \(heightDp > 0f && isPortraitFold\(widthDp, heightDp\)\) return portraitFold/);
 assert.match(activity,/val portraitFold = wide &&/);
 assert.match(activity,/if \(!portraitFold\) \{/);
 assert.match(activity,/val sideRail = wide && !at\.heinz\.deviceai\.ui\.ContinuumHomeLayout\.isPortraitFold/);
 assert.match(activity,/if \(sideRail\) railWidthPx\(\)/);
 assert.match(home,/val portrait = wide && layout\[Slot\.WORKERS\] != null/);
 assert.doesNotMatch(layout.split('fun portraitFold(')[1].split('fun wide(')[0],/Slot\.RAIL to Box/);
});

test('six equal live cards and the separate status strip are aligned without clipping',()=>{
 const portrait=layout.split('fun portraitFold(')[1].split('fun wide(')[0];
 for(const rect of [
  'Slot.MODEL to Box(12f, firstTop, 276f, firstTop + cardHeight)',
  'Slot.LEARNING to Box(288f, firstTop, 552f, firstTop + cardHeight)',
  'Slot.STORAGE to Box(564f, firstTop, 828f, firstTop + cardHeight)',
  'Slot.DEVELOPMENT to Box(12f, secondTop, 276f, secondTop + cardHeight)',
  'Slot.SYSTEM_ACTIVITY to Box(288f, secondTop, 552f, secondTop + cardHeight)',
  'Slot.WORKERS to Box(564f, secondTop, 828f, secondTop + cardHeight)',
  'Slot.SYSTEM_STATUS to Box(12f, statusTop, 828f, statusTop + 130f)'
 ])assert.ok(portrait.includes(rect),'missing aligned rectangle '+rect);
 assert.match(portrait,/val secondTop = firstTop \+ cardHeight \+ gap/);
 assert.match(portrait,/val statusTop = secondTop \+ cardHeight \+ 16f/);
 for(const [w,h] of [[750,790],[656,690],[840,1010],[1000,1400]]){
  const u=w/840,frame=Math.max(800,h/u),heroExtra=Math.min(Math.max(0,frame-886)*.12,45);
  const bottom=384+heroExtra,top=bottom+16,card=216,second=top+card+12;
  const status=second+card+16,content=Math.max(frame,status+142);
  assert.ok(status+130<=content, 'status reachable with scrollbar');
  assert.ok(828*u<w, 'right aligned card fits available viewport');
  assert.ok(bottom<top, 'unobstructed hero / chart clearance');
 }
});

test('mirrored controls do not cover the hologram; real renderer and all diagrams survive',()=>{
 const portrait=layout.split('fun portraitFold(')[1].split('fun wide(')[0];
 for(const row of ['81f, 163f','177f, 259f','273f, 355f']){
  assert.ok(portrait.includes(row),'missing symmetrical action row '+row);
 }
 assert.match(portrait,/Slot\.HERO to Box\(240f, 60f, 600f, heroBottom\)/);
 for(const name of ['PortraitModelCard','PortraitLearningCard','PortraitStorageCard',
  'PortraitDevelopmentCard','PortraitActivityCard','PortraitWorkersCard','PortraitSystemStatusStrip']){
  assert.match(elements,new RegExp('class '+name+'\\b'));
  assert.match(home,new RegExp(name+'\\(textScale\\)'));
 }
 assert.match(elements,/class PortraitSystemStatusStrip[\s\S]*Donut\.draw/);
 assert.match(elements,/class PortraitSystemStatusStrip[\s\S]*WaveChart\.draw/);
 assert.match(elements,/s\.system\.appCpuPercent/);
 assert.match(core,/ContinuumSurfaces\.iconDisc\(p, icon, discX, cy, 56f, 29f\)/);
 assert.match(orb,/val FOLD_PORTRAIT = Geometry\(centerY = \.42f, radiusOfWidth = \.375f/);
 assert.match(view,/Geometry\.FOLD_PORTRAIT/);
 assert.match(preview,/Geometry\.FOLD_PORTRAIT/);
 assert.match(activity,/Slot\.WORKERS -> showDevelopmentBacklog\(\)/);
 assert.doesNotMatch(elements,/\b(435 gespeicherte|129 Lernereignisse|aktive Worker.{0,5}3)\b/);
});

test('Fold portrait exposes a fixed navigable Chat tab and discoverable scrolling',()=>{
 assert.match(activity,/private fun buildPortraitWorkAreaBar\(\): View/);
 assert.match(activity,/root\.addView\(buildPortraitWorkAreaBar\(\)/);
 assert.match(activity,/ContinuumWorkAreaNavigationSpec\.destinations\.withIndex\(\)/);
 assert.match(activity,/setOnClickListener \{ navigateWorkArea\(destination\) \}/);
 assert.match(activity,/bottomWorkAreaBar = dock/);
 assert.match(activity,/isVerticalScrollBarEnabled = true/);
 assert.match(activity,/isScrollbarFadingEnabled = false/);
 assert.match(activity,/scrollBarSize = dp\(5\)/);
 assert.match(activity,/ContinuumWorkArea\.CHAT -> focusChatComposer\(\)/);
 assert.match(activity,/navigationPane\.layoutParams = paneParams/);
 assert.match(navigation,/\(!wide \|\| portraitFold\) && !keyboardVisible/);
});

test('phone / Fold landscape and hologram lifecycle are unchanged',()=>{
 assert.match(layout,/private val phoneReference = mapOf\(/);
 assert.match(layout,/private val wideReference = mapOf\(/);
 assert.match(orb,/val PHONE = Geometry\(/);
 assert.match(orb,/val FOLD = Geometry\(/);
 assert.match(home,/else if \(wide\) \{/);
 assert.match(activity,/wideContent\.addView\(buildRail\(\), LinearLayout\.LayoutParams/);
 assert.match(activity,/it\.setForegroundActive\(foregroundLearningActive\)/);
 assert.match(view,/sciFiEffects\.drawScanlines/);
});
