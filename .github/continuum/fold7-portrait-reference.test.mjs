import test from 'node:test';
import assert from 'node:assert/strict';
import fs from 'node:fs';
import path from 'node:path';
import {fileURLToPath} from 'node:url';

// Source contracts for the later owner directive recorded in
// docs/research/omega/ASTRA-REFERENCE-PARITY-20261104.md. The old six-card portrait
// and eight-tile ref5 geometry are superseded. These checks do not attest pixels,
// Android rendering, physical Fold7 behaviour or visual owner acceptance.
const root=path.resolve(path.dirname(fileURLToPath(import.meta.url)),'../..');
const read=p=>fs.readFileSync(path.join(root,p),'utf8');
const ui='app/src/main/java/at/heinz/deviceai/ui/';
const layout=read(ui+'ContinuumHomeLayout.kt');
const reference=read(ui+'ContinuumReferenceLayout.kt');
const profile=read(ui+'JarvisWindowProfile.kt');
const shell=read(ui+'JarvisShellLayout.kt');
const grid=read(ui+'JarvisCockpitGrid.kt');
const content=read(ui+'JarvisShellContent.kt');
const tiles=read(ui+'JarvisTiles.kt');
const menu=read(ui+'JarvisMenu.kt');
const view=read(ui+'ContinuumHomeView.kt');
const activity=read('app/src/main/java/at/heinz/deviceai/AgentActivity.kt');
const navigation=read('app/src/main/java/at/heinz/deviceai/ContinuumWorkAreaNavigationSpec.kt');
const activityMember=signature=>{
 const start=activity.indexOf(signature);
 assert.ok(start>=0,'Missing Activity member '+signature);
 const end=activity.indexOf('\n    private fun ',start+signature.length);
 return activity.slice(start,end<0?undefined:end);
};

test('Fold portrait uses the shared width/font profile and one labelled rail',()=>{
 assert.match(layout,/fun isPortraitFold\(widthDp: Float, heightDp: Float\)/);
 assert.match(layout,/isWide\(widthDp, heightDp\) && heightDp >= widthDp \* 1\.02f/);
 assert.match(layout,/ContinuumReferenceLayout\.create\(widthDp, heightDp, true, fontScale\)/);
 assert.match(reference,/val portrait = wide && height > 0f && ContinuumHomeLayout\.isPortraitFold\(width, height\)/);
 assert.match(profile,/const val MEDIUM_MIN_DP = 600f/);
 assert.match(profile,/const val EXPANDED_MIN_DP = 840f/);
 assert.match(profile,/val reference = size == WidthClass\.EXPANDED && heightDp >= REFERENCE_MIN_HEIGHT_DP &&\s*font <= 1\.3f && readableColumns == 5 && rail == JarvisShellLayout\.Rail\.LABELLED/);
 assert.match(shell,/fun create\(widthDp: Float, heightDp: Float, fontScale: Float = 1f, foldRail: Rail = Rail\.LABELLED,\s*includeBottomNavigation: Boolean = false, compactPresentation: Boolean = false\): Shell \{/);
 assert.match(shell,/val full = create\(widthDp \+ externalRailWidthDp, viewport, fontScale, foldRail, includeBottomNavigation, compactPresentation\)/);
 assert.match(shell,/val profile = JarvisWindowProfile\.resolve\(widthDp, heightDp, fontScale, foldRail\)/);
 assert.match(shell,/return if \(profile\.referenceComposition\) reference\(widthDp, heightDp, profile, foldRail\)\.let \{\s*if \(compactPresentation\) compactReference\(it\) else it\s*\} else reflow\(widthDp, heightDp, profile, foldRail, includeBottomNavigation, compactPresentation\)/);
 const ui=activityMember('private fun buildUi(): View');
 assert.match(ui,/val wide = windowWidthDp >= at\.heinz\.deviceai\.ui\.JarvisWindowProfile\.MEDIUM_MIN_DP/);
 assert.match(ui,/val rail = buildRail\(\)\.also/);
 assert.match(ui,/wideContent\.addView\(rail, LinearLayout\.LayoutParams/);
 assert.doesNotMatch(ui,/buildPortraitWorkAreaBar\(\)/);
 assert.match(navigation,/!wide && !keyboardVisible/);
});

test('current information panels and separate status keep real sources and complete scrollable rows',()=>{
 assert.match(grid,/const val REF_W = 1536f/);
 assert.match(grid,/const val REF_H = 1024f/);
 for(const [slot,left,right] of [['ROW_AGENTS',258,548],['ROW_TASKS',560,836],
   ['ROW_MEMORY',848,1152],['ROW_MODEL',1164,1520]]){
  assert.ok(grid.includes('a[Area.'+slot+'] = b('+left+'f, rowTop, '+right+'f, rowBottom)'),
   'Misaligned reference information panel '+slot);
  assert.match(shell,new RegExp('Slot\\.'+slot+' to gr\\[Area\\.'+slot+'\\]'));
 }
 assert.match(grid,/a\[Area\.STATUS\] = b\(16f, 906f \* u \+ extraHeight, 1520f, 978f \* u \+ extraHeight\)/);
 assert.match(content,/Slot\.ROW_AGENTS -> ContinuumCockpitPresentation\.agentRows\(s\.cockpit\)/);
 assert.match(content,/Slot\.ROW_TASKS -> ContinuumCockpitPresentation\.processRows\(s\.cockpit\)/);
 assert.match(content,/Slot\.ROW_MEMORY -> ContinuumCockpitPresentation\.memoryRows\(s, nowMillis\)/);
 assert.match(content,/Slot\.COCKPIT_PRIMARY, Slot\.ROW_MODEL ->[\s\S]*?ContinuumCockpitPresentation\.brainRows\(s\)/);
 assert.match(content,/Slot\.COCKPIT_SECONDARY, Slot\.STATUS -> ContinuumCockpitPresentation\.systemRows\(s\)/);
 assert.match(view,/jarvisShell\.content = JarvisShellContent\.forState\(\{ state \}/);
 // Medium/large-text rows keep all four records; they scroll instead of shrinking.
 assert.match(shell,/val infos = listOf\(Slot\.ROW_AGENTS, Slot\.ROW_TASKS, Slot\.ROW_MEMORY, Slot\.ROW_MODEL\)/);
 assert.match(shell,/val infoW = \(room - \(p\.informationColumns - 1\) \* gap\) \/ p\.informationColumns/);
 assert.match(shell,/b\[slot\] = Box\(x, t, x \+ infoW, t \+ infoH\)/);
 assert.match(shell,/y \+= \(\(infos\.size \+ p\.informationColumns - 1\) \/ p\.informationColumns\) \* \(infoH \+ gap\)/);
 assert.match(shell,/return Shell\(formFactor\(w, viewport\), w, maxOf\(viewport, y\)/);
 assert.doesNotMatch(content,/\b(435 gespeicherte|129 Lernereignisse|aktive Worker.{0,5}3)\b/);
});

test('ten action controls share draw bounds while head and original orb retain separate regions',()=>{
 const referenceTiles=tiles.split('val reference = listOf(')[1]?.split('/** Draws tile content')[0];
 assert.ok(referenceTiles,'Current reference tile list');
 assert.deepEqual([...referenceTiles.matchAll(/JarvisTile\("([^"]+)"/g)].map(m=>m[1]),
  ['chat','memory','logic','research','agents','tasks','web','learning','device','system']);
 assert.match(grid,/const val TILE_COLUMNS = 5/);
 assert.match(grid,/const val TILE_ROWS = 2/);
 assert.match(shell,/return Shell\(formFactor\(w, h\), w, h, b, 5, 2, rail,/);
 assert.match(shell,/val rows = \(10 \+ p\.tileColumns - 1\) \/ p\.tileColumns/);
 assert.match(shell,/return List\(minOf\(10, tileColumns \* tileRows\)\)/);
 assert.match(shell,/val tileH = maxOf\(64f \* font, 48f\)/);
 assert.match(view,/shell\.tileBoxes\(\)\.forEachIndexed/);
 assert.match(view,/ShellActionView\(context, spec\.key, spec\.target, spec\.label\)/);
 assert.match(view,/shellActionBounds = actions\.associate \{ it\.key to it\.box \}/);
 assert.match(view,/shellActions\[key\]\?\.layout\(\(box\.left \* density\)\.toInt\(\), \(box\.top \* density\)\.toInt\(\),/);
 assert.match(shell,/val head = gr\[Area\.HEAD\]/);
 assert.match(shell,/val orb = gr\[Area\.ORB\]/);
 assert.match(view,/val head = shell\?\.headStage \?: shell\?\.headBounds/);
 assert.match(view,/hologram\.setHeadFraming\(shell\.headAnchorX, shell\.headCloseUp\)/);
 assert.match(view,/val orb = shell\?\.orbBounds/);
 assert.match(view,/hologram\.setCockpitRegions\(local\(head\), local\(orb\)\)/);
 assert.match(view,/originX = \(stage\.left \* density\)\.toInt\(\)/);
 assert.match(view,/originY = \(stage\.top \* density\)\.toInt\(\)/);
 assert.match(view,/hologram\.setGeometry\(intendedGeometry\)/);
});

test('Fold chat remains a real native action and both content and rail stay scrollable',()=>{
 assert.match(tiles,/NavItem\("chat", "Gespräch", Icon\.CHAT\)/);
 assert.match(content,/"chat" to ContinuumHomeLayout\.Slot\.LABEL_THINKING/);
 assert.match(view,/setOnClickListener \{ openJarvisTarget\(target\) \}/);
 assert.match(view,/if \(native != null\) native\(target\) else onSlot\(requireNotNull\(route\), 0f, 0f\)/);
 assert.match(activity,/home\.onNavigateTarget = ::navigateDashboardTarget/);
 assert.match(activityMember('private fun navigateDashboardTarget('),/else -> menuNavigator\.navigate\(target\)/);
 assert.match(menu,/if \(openTarget\?\.invoke\(target\) != true\) open\(slot\)/);
 assert.match(activityMember('private fun openHomeSlot('),/Slot\.LABEL_THINKING -> navigateWorkArea\(ContinuumWorkArea\.CHAT\)/);
 assert.match(activityMember('private fun navigateWorkArea('),/ContinuumWorkArea\.CHAT -> focusChatComposer\(\)/);
 const pane=activityMember('private fun buildConversationPane(wide: Boolean): View');
 assert.match(pane,/val dashboardScroll = ScrollView\(this\)/);
 assert.match(pane,/isVerticalScrollBarEnabled = true/);
 assert.match(pane,/contentDescription = "Continuum-Cockpit, vertikal scrollbar:/);
 assert.match(view,/private val shellRailScroll = android\.widget\.ScrollView\(context\)/);
 assert.match(view,/val required = \(JarvisTiles\.railContentHeight\(w \/ density, font\) \* density\)\.toInt\(\)/);
 assert.match(tiles,/val pitch = maxOf\(48f \* maxOf\(1f, font\), 56f \* scale\)/);
 assert.match(view,/JarvisTiles\.railItemBoxes\(JarvisShellLayout\.Box\(0f, 0f, width \/ density, height \/ density\),\s*fontScale = resources\.configuration\.fontScale\)/);
});

test('phone and Fold share the original live surface and preserve its lifecycle during reflow',()=>{
 assert.match(layout,/fun phone\(widthDp: Float, fontScale: Float = 1f\): Layout =\s*ContinuumReferenceLayout\.create\(widthDp, 0f, false, fontScale\)/);
 assert.match(layout,/fun wide\(widthDp: Float, heightDp: Float = 0f, fontScale: Float = 1f\): Layout =\s*ContinuumReferenceLayout\.create\(widthDp, heightDp, true, fontScale\)/);
 const pane=activityMember('private fun buildConversationPane(wide: Boolean): View');
 assert.match(pane,/val liveHologram = \(hologramView \?: ContinuumHologramView\(this\)\)\.also/);
 assert.match(pane,/\(it\.parent as\? android\.view\.ViewGroup\)\?\.removeView\(it\)/);
 assert.match(pane,/avatarDirector\.attach\(it\)/);
 assert.match(pane,/it\.setForegroundActive\(foregroundLearningActive\)/);
 assert.match(pane,/ContinuumHomeView\(this, liveHologram\)/);
 assert.match(pane,/home\.setJarvisShellStage\(true\)/);
 assert.match(pane,/JarvisHoloAvatar\(home\.hologram\)/);
 assert.match(view,/slotRegistration\?\.close\(\); slotRegistration = null/);
 assert.match(view,/removeCallbacks\(waveFrame\)/);
 assert.match(view,/sciFiEffects\.drawScanlines/);
});
