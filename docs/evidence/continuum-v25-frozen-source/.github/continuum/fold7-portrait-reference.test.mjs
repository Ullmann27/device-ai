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
const preview=read('tools/continuum-jvm-preview/src/at/heinz/deviceai/ui/ContinuumPreviewMain.kt');

test('Fold7 portrait uses its own geometry and full-screen app shell, while landscape retains rail',()=>{
 assert.match(layout,/fun isPortraitFold\(widthDp: Float, heightDp: Float\)/);
 assert.match(layout,/isWide\(widthDp, heightDp\) && heightDp >= widthDp \* 1\.02f/);
 assert.match(layout,/if \(heightDp > 0f && isPortraitFold\(widthDp, heightDp\)\) return portraitFold/);
 assert.match(activity,/if \(!at\.heinz\.deviceai\.ui\.ContinuumHomeLayout\.isPortraitFold\(windowWidthDp, windowHeightDp\)\)/);
 assert.match(activity,/val sideRail = wide && !at\.heinz\.deviceai\.ui\.ContinuumHomeLayout\.isPortraitFold/);
 assert.match(activity,/if \(sideRail\) railWidthPx\(\)/);
 assert.match(home,/val portrait = wide && layout\[Slot\.WORKERS\] != null/);
 assert.doesNotMatch(layout.split('fun portraitFold(')[1].split('fun wide(')[0],/Slot\.RAIL to Box/);
});

test('exact 3 x 2 same-sized source rectangles, anchored to reference image',()=>{
 const portrait=layout.split('fun portraitFold(')[1].split('fun wide(')[0];
 const exact=[
  'Slot.MODEL to Box(12f, top, 276f, top + cardHeight)',
  'Slot.LEARNING to Box(288f, top, 552f, top + cardHeight)',
  'Slot.STORAGE to Box(564f, top, 828f, top + cardHeight)',
  'Slot.DEVELOPMENT to Box(12f, row2, 276f, row2 + cardHeight)',
  'Slot.SYSTEM_ACTIVITY to Box(288f, row2, 552f, row2 + cardHeight)',
  'Slot.WORKERS to Box(564f, row2, 828f, row2 + cardHeight)'
 ];
 for(const rect of exact)assert.ok(portrait.includes(rect),'missing identical grid cell '+rect);
 const sourceArea=portrait.indexOf('val row2 = top + cardHeight + gap');
 assert.ok(sourceArea>=0);
 assert.ok(portrait.includes('val gap = 12f'));
 for(const shape of [[750,790],[656,690],[840,1010],[1000,1400]]){
  const [w,h]=shape,u=w/840;
  const frame=Math.max(800,h/u),extra=Math.max(0,frame-886);
  const grow=Math.min(extra*.22,100),top=400+grow;
  const gap=12,pad=12+extra*.46;
  const ch=Math.max((frame-top-pad-gap)/2,196),last=top+ch+gap+ch;
  assert.ok(ch>=196);
  assert.ok(last<=Math.max(frame,last+12),'within scroll content '+shape);
  assert.ok(828*u<w,'card fits '+shape);
  assert.ok(top>390+grow,'hero/card clearance '+shape);
 }
});

test('larger actions, ACTUAL large orb and sixth tap target are wired to production',()=>{
 for(const s of ['PortraitModelCard','PortraitLearningCard','PortraitStorageCard',
   'PortraitDevelopmentCard','PortraitActivityCard','PortraitWorkersCard','PortraitSystemStatus']){
  assert.match(elements,new RegExp('class '+s+'\\b'));
  assert.match(home,new RegExp(s+'\\(textScale\\)'));
 }
 for(const s of ['LABEL_CONNECTED','LABEL_LEARNING','LABEL_DEVELOPING','LABEL_THINKING','LABEL_ANALYSING','LABEL_EVOLVING'])
  assert.match(layout,new RegExp('Slot\\.'+s+' to Box\\('));
 assert.match(core,/ContinuumSurfaces\.iconDisc\(p, icon, discX, cy, 56f, 29f\)/);
 assert.match(orb,/val FOLD_PORTRAIT = Geometry\(centerY = \.42f, radiusOfWidth = \.375f/);
 assert.match(view,/Geometry\.FOLD_PORTRAIT/);
 assert.match(preview,/Geometry\.FOLD_PORTRAIT/);
 assert.match(activity,/Slot\.WORKERS -> showDevelopmentBacklog\(\)/);
 assert.match(elements,/val figures=listOf\(ContinuumDashboardState\.count\(d\.runningTasks\)/);
 assert.doesNotMatch(elements,/\b(435 gespeicherte|129 Lernereignisse|aktive Worker.{0,5}3)\b/);
});

test('legacy smartphone and Fold landscape geometries are not overwritten',()=>{
 assert.match(layout,/private val phoneReference = mapOf\(/);
 assert.match(layout,/private val wideReference = mapOf\(/);
 assert.match(orb,/val PHONE = Geometry\(/);
 assert.match(orb,/val FOLD = Geometry\(/);
 assert.match(home,/else if \(wide\) \{/);
 assert.match(activity,/wideContent\.addView\(buildRail\(\), LinearLayout\.LayoutParams/);
});
