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
const cockpit=read(ui+'ContinuumCockpitElements.kt');
const orb=read(ui+'ContinuumHologramRenderer.kt');
const view=read(ui+'ContinuumHomeView.kt');
const activity=read('app/src/main/java/at/heinz/deviceai/AgentActivity.kt');
const navigation=read('app/src/main/java/at/heinz/deviceai/ContinuumWorkAreaNavigationSpec.kt');
const preview=read('tools/continuum-jvm-preview/src/at/heinz/deviceai/ui/ContinuumPreviewMain.kt');

// The owner-approved ZETA redesign supersedes the former scaled840dp 3×2 rectangles.
// These are source-wiring contracts; Kotlin geometry and native widget/ink tests provide pixel evidence.
test('portrait selection keeps actual Fold window threshold and separate landscape rail',()=>{
 assert.match(layout,/fun isPortraitFold\(widthDp: Float, heightDp: Float\)/);
 assert.match(layout,/isWide\(widthDp, heightDp\) && heightDp >= widthDp \* 1\.02f/);
 assert.match(layout,/val portrait = wide && height > 0f && isPortraitFold\(width, height\)/);
 assert.match(layout,/val rail = if \(wide && !portrait\) railWidthDp\(width\) else 0f/);
 assert.match(layout,/if \(rail > 0f\) boxes\[Slot\.RAIL\] = Box/);
 assert.match(activity,/val portraitFold = wide &&/);
 assert.match(activity,/if \(!portraitFold\) \{/);
 assert.match(activity,/val sideRail = wide && !at\.heinz\.deviceai\.ui\.ContinuumHomeLayout\.isPortraitFold/);
 assert.match(activity,/if \(sideRail\) railWidthPx\(\)/);
 assert.match(home,/if \(layout\[Slot\.RAIL\] != null\) map\[Slot\.RAIL\] = RailElement/);
 // WORKERS is now a real backlog card on every size, not a portrait-layout discriminator.
 assert.doesNotMatch(home,/val portrait = wide && layout\[Slot\.WORKERS\] != null/);
});

test('seven live cards reflow in shared columns and remain inside scrolling content',()=>{
 for(const slot of ['MODEL','LEARNING','STORAGE','DEVELOPMENT','WORKERS','SYSTEM_STATUS','SYSTEM_ACTIVITY']) {
  assert.match(layout,new RegExp('Slot\\.'+slot+'\\b'));
  assert.match(home,new RegExp('Slot\\.'+slot+'\\b'));
 }
 assert.match(home,/if \(layout\[slot\] != null\) map\[slot\] = CockpitMetricCard\(slot, textScale, layout\.wide\)/);
 assert.match(layout,/if \(!sidebar\) cards \+= listOf\(Slot\.SYSTEM_STATUS, Slot\.SYSTEM_ACTIVITY\)/);
 // The half-height sidebar only applies on broad windows at ordinary font size.
 assert.match(layout,/val sidebar = wide && room >= 1000f && font <= 1\.1f/);
 assert.match(layout,/val columns = when \{[\s\S]*?-> 3[\s\S]*?-> 2[\s\S]*?else -> 1/);
 assert.match(layout,/val cardWidth = \(room - \(columns - 1\) \* gap\) \/ columns/);
 assert.match(layout,/val x = left \+ \(index % columns\) \* \(cardWidth \+ gap\)/);
 assert.match(layout,/val y = cardTop \+ \(index \/ columns\) \* \(cardHeight \+ gap\)/);
 assert.match(layout,/boxes\[slot\] = Box\(x, y, x \+ cardWidth, y \+ cardHeight\)/);
 assert.match(layout,/val contentHeight = cardTop \+ \(\(cards\.size \+ columns - 1\) \/ columns\) \* \(cardHeight \+ gap\)/);
 assert.match(layout,/return Layout\(wide, width, 1f, contentHeight, boxes\)/);
});

test('six controls stay below the shared hero and all real card data remains connected',()=>{
 for(const slot of ['LABEL_CONNECTED','LABEL_THINKING','LABEL_LEARNING','LABEL_ANALYSING','LABEL_DEVELOPING','LABEL_EVOLVING']) {
  assert.match(layout,new RegExp('Slot\\.'+slot+'\\b'));
  assert.match(home,new RegExp('Slot\\.'+slot+' to CockpitActionTile\\('));
 }
 assert.match(layout,/boxes\[Slot\.HERO\] = Box\(left, heroTop,[\s\S]*?heroTop \+ heroHeight\)/);
 assert.match(layout,/val actionTop = heroTop \+ heroHeight \+ gap/);
 assert.match(layout,/val y = actionTop \+ \(index \/ actionColumns\) \* \(actionHeight \+ gap\)/);
 assert.match(layout,/boxes\[slot\] = Box\(x, y, x \+ actionWidth, y \+ actionHeight\)/);
 // Existing chart components remain available; adaptive Home now binds concise live-state cards.
 for(const name of ['PortraitModelCard','PortraitLearningCard','PortraitStorageCard',
  'PortraitDevelopmentCard','PortraitActivityCard','PortraitWorkersCard','PortraitSystemStatusStrip'])
  assert.match(elements,new RegExp('class '+name+'\\b'));
 assert.match(elements,/class PortraitSystemStatusStrip[\s\S]*Donut\.draw/);
 assert.match(elements,/class PortraitSystemStatusStrip[\s\S]*WaveChart\.draw/);
 for(const field of ['model.name','model.qualityPercent','learning.today','storage.activeSources',
  'development.runningTasks','system.appCpuPercent','system.memoryPercent','system.batteryPercent'])
  assert.ok(cockpit.includes('s.'+field),'live card field disconnected: '+field);
 assert.match(core,/ContinuumSurfaces\.iconDisc\(p, icon, discX, cy, 56f, 29f\)/);
 assert.match(orb,/val FOLD_PORTRAIT = Geometry\(centerY = \.42f, radiusOfWidth = \.375f/);
 assert.match(view,/spec\.wide -> ContinuumHologramRenderer\.Geometry\.FOLD/);
 assert.match(view,/else -> ContinuumHologramRenderer\.Geometry\.PHONE/);
 assert.match(preview,/Geometry\.FOLD_PORTRAIT/);
 assert.match(activity,/Slot\.WORKERS -> showDevelopmentBacklog\(\)/);
 assert.doesNotMatch(elements+cockpit,/\b(435 gespeicherte|129 Lernereignisse|aktive Worker.{0,5}3)\b/);
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

test('phone and Fold preserve renderer lifecycle through the shared adaptive layout',()=>{
 assert.match(layout,/fun phone\(widthDp: Float, fontScale: Float = 1f\): Layout = adaptive\(widthDp, 0f, false, fontScale\)/);
 assert.match(layout,/fun wide\(widthDp: Float, heightDp: Float = 0f, fontScale: Float = 1f\): Layout =\s*adaptive\(widthDp, heightDp, true, fontScale\)/);
 assert.match(orb,/val PHONE = Geometry\(/);
 assert.match(orb,/val FOLD = Geometry\(/);
 assert.match(home,/if \(layout\[Slot\.NAV\] != null\) map\[Slot\.NAV\] = BottomNavElement/);
 assert.match(home,/if \(layout\[Slot\.RAIL\] != null\) map\[Slot\.RAIL\] = RailElement/);
 assert.match(activity,/wideContent\.addView\(buildRail\(\), LinearLayout\.LayoutParams/);
 assert.match(activity,/it\.setForegroundActive\(foregroundLearningActive\)/);
 assert.match(view,/sciFiEffects\.drawScanlines/);
});
