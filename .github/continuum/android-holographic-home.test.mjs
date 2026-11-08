import test from 'node:test';
import assert from 'node:assert/strict';
import fs from 'node:fs';

const activity=fs.readFileSync('app/src/main/java/at/heinz/deviceai/AgentActivity.kt','utf8');
const orb=fs.readFileSync('app/src/main/java/at/heinz/deviceai/ContinuumHologramView.kt','utf8');
const brand=fs.readFileSync('app/src/main/res/drawable/device_ai_holo_logo.xml','utf8');
const ui='app/src/main/java/at/heinz/deviceai/ui/';
const renderer=fs.readFileSync(ui+'ContinuumHologramRenderer.kt','utf8');
const homeView=fs.readFileSync(ui+'ContinuumHomeView.kt','utf8');
const homeScreen=fs.readFileSync(ui+'ContinuumHomeScreen.kt','utf8');
const dashboardState=fs.readFileSync(ui+'ContinuumDashboardState.kt','utf8');
const drawn=['ContinuumHomeElements.kt','ContinuumWideElements.kt','ContinuumCockpitElements.kt','ContinuumSurfaces.kt']
  .map(f=>fs.readFileSync(ui+f,'utf8')).join('\n');
const section=(start,end)=>{
 const a=activity.indexOf(start),b=activity.indexOf(end,a);
 assert.ok(a>=0&&b>a,'Expected real Android UI method boundaries '+start);
 return activity.slice(a,b);
};

test('transparent native 3D hero has no network, model execution or fake status',()=>{
 assert.match(orb,/class ContinuumHologramView/);
 assert.match(orb,/override fun onDraw\(canvas: Canvas\)/);
 assert.match(orb,/private val renderFrame = ContinuumHologramRenderer\.Frame\(/);
 assert.match(orb,/renderFrame\.phase = phase/);
 assert.match(orb,/renderFrame\.energy = proof\?\.orbEnergy \?: energy/);
 assert.match(orb,/renderFrame\.audio = if \(proof != null\) proof\.pose\.speakEnergy else audioEnergy/);
 assert.match(orb,/diagnosticBlink = proof\?\.blinkOverride/);
 assert.match(orb,/receipt = receipt/);
 assert.match(renderer,/Fill\.Radial\(/);
 assert.match(renderer,/drawOrbits\(p, f, front = true\)/);
 // the backdrop glow ends in a fully transparent stop: the view paints no opaque background of its own
 assert.match(renderer,/backdrop = Fill\.Radial\([\s\S]{0,200}?, 0\),\s*floatArrayOf/);
 // speech lines react only to a measured audio level, never to a timer
 assert.match(renderer,/f\.audio > 0f/);
 const strip=s=>s.replace(/\/\*[\s\S]*?\*\//g,'').replace(/\/\/[^\n]*/g,'');
 for(const code of [strip(orb),strip(renderer)])
  assert.doesNotMatch(code,/(?:http:|https:|WebView|model\.generate|networkRequest|canvas\.drawColor\()/);
 assert.match(brand,/<vector\b/);
 assert.match(brand,/android:pathData=/);
 assert.doesNotMatch(brand,/<bitmap\b/);
});

test('reference start screen reaches all seven real Android feature paths',()=>{
 const pane=section('private fun buildConversationPane(wide: Boolean): View','private fun openHomeSlot(');
 const slots=section('private fun openHomeSlot(','private fun refreshDashboard()');
 const nav=section('private fun navigateWorkArea(','private fun refreshConversationLayout()');
 assert.match(pane,/val liveHologram = \(hologramView \?: ContinuumHologramView\(this\)\)\.also/);
 assert.match(pane,/hologramView = it/);
 assert.match(pane,/avatarDirector\.attach\(it\)/);
 assert.match(pane,/ContinuumHomeView\(this, liveHologram\)/);
 assert.match(pane,/openHomeSlot\(slot, x, y\)/);
 for(const call of ['focusChatComposer()','showResearchModeDialog()','showLearningCenter()','showModelCenter()',
  'showDevelopmentCenter()','showMemoryCenter()','showSystemCenter()'])
  assert.ok((slots+nav).includes(call),'Missing live handler '+call);
 // every tappable element of the start screen has a real route; none is decoration only
 for(const slot of ['BRAND','SEARCH','NOTIFICATIONS','PROFILE','MODEL_PILL','MODEL','LABEL_CONNECTED','LABEL_THINKING',
  'LABEL_LEARNING','LEARNING','LABEL_ANALYSING','LABEL_DEVELOPING','LABEL_EVOLVING','STORAGE','DEVELOPMENT',
  'SYSTEM_STATUS','SYSTEM_ACTIVITY'])
  assert.match(slots,new RegExp('Slot\\.'+slot+'\\b[^\\n]*->\\s*(show|navigate|if)'),'No route for '+slot);
 assert.doesNotMatch(slots+homeScreen,/Lumo/i);
});

test('responsive dashboard retains chat composer, updates and keyboard visibility',()=>{
 const pane=section('private fun buildConversationPane(wide: Boolean): View','private fun openHomeSlot(');
 const home=section('private fun buildUi(): View','private fun buildConversationPane(wide: Boolean): View');
 assert.match(pane,/dashboardScroll/);
 assert.match(pane,/buildComposer\(\)/);
 assert.match(pane,/chatScroll/);
 assert.match(home,/ComposerUiPolicy\.viewport/);
 assert.match(home,/keyboardCompact/);
 // every start-screen element (inner ElementView, not the standalone navigation view) is a focusable, clickable
 // view that speaks its real values
 const elementView=homeView.slice(homeView.indexOf('inner class ElementView'),homeView.indexOf('/** Standalone drawn element'));
 assert.ok(elementView.length>200,'ElementView class not found');
 assert.match(elementView,/isClickable = true/);
 assert.match(elementView,/isFocusable = true/);
 assert.match(elementView,/contentDescription = element\.describe\(state\)/);
 assert.match(elementView,/setOnClickListener \{\s*if \(slot in listOf\(Slot\.VIEW_AVATAR, Slot\.VIEW_ORB, Slot\.VIEW_COMPACT\)\) selectPresentation\(slot\)\s*else onSlot\(slot, downX \/ density, downY \/ density\)/);
 // The active shell has its own real Android action Views, sharing the same Activity dispatcher.
 const shellAction=homeView.slice(homeView.indexOf('private inner class ShellActionView'),homeView.indexOf('private inner class ShellRailList'));
 assert.ok(shellAction.length>200,'ShellActionView class not found');
 assert.match(pane,/home\.onNavigateTarget = ::navigateDashboardTarget/);
 assert.match(pane,/home\.setJarvisShellStage\(true\)/);
 assert.match(shellAction,/isClickable = true/);
 assert.match(shellAction,/isFocusable = true/);
 assert.match(shellAction,/setOnClickListener \{ openJarvisTarget\(target\) \}/);
 assert.match(shellAction,/JarvisShellContent\.tileValue\(target, state, System\.currentTimeMillis\(\)\)/);
 assert.match(shellAction,/contentDescription = label \+ \(value\?\.let/);
 assert.doesNotMatch(pane,/buildQuickActions\(\)/);
});

test('reference-style composer keeps real model/web/file actions and truthful idle state',()=>{
 const composer=section('private fun buildComposer(): View',
   'private fun sendCurrentInput(');
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
 // Owner decision 05.11.: the composer microphone starts hands-free talk; long press dictates.
 assert.match(composer,/setOnClickListener \{ onChatMicrophoneTap\(\) \}/);
 assert.match(composer,/setOnLongClickListener \{ onChatMicrophoneLongPress\(\); true \}/);
 assert.match(composer,/sendCurrentInput\(\)/);
 assert.match(busy,/sendButton\.contentDescription/);
 assert.match(busy,/else "➤"/);
});

test('large text is bounded and propagated into adaptive layout without window shrinking',()=>{
 assert.match(homeView,/fontScale\.coerceIn\(\.85f, 2f\)/);
 assert.match(homeScreen,/fun elements\(layout: ContinuumHomeLayout\.Layout, textScale: Float\)/);
 assert.match(homeView,/ContinuumReferenceLayout\.create\(windowWidthDp, if \(wide\) windowHeightDp else 0f,\s*wide, textScale, compactPresentation\)/);
 assert.match(homeView,/JarvisShellLayout\.forContent\(width \/ density, jarvisShell\.viewportHeightDp,\s*resources\.configuration\.fontScale/);
 const layout=fs.readFileSync(ui+'ContinuumHomeLayout.kt','utf8') + fs.readFileSync(ui+'ContinuumReferenceLayout.kt','utf8');
 assert.match(layout,/return Layout\(wide, width, 1f, contentHeight, boxes,/);
 assert.match(layout,/font > 1\.3f/);
 const shell=fs.readFileSync(ui+'JarvisShellLayout.kt','utf8');
 assert.match(shell,/val profile = JarvisWindowProfile\.resolve\(widthDp, heightDp, fontScale, foldRail\)/);
 assert.match(shell,/val tileH = maxOf\(JarvisCockpitGrid\.MIN_TILE_DP \* font, tileWidth \/ JarvisCockpitGrid\.TILE_ASPECT\)/);
 // text that does not fit its card is shortened with an ellipsis instead of running into a neighbour
 assert.match(drawn,/fun fit\(p: ContinuumPainter, text: String, style: TextStyle, maxWidth: Float\): String/);
 assert.match(drawn,/"…"/);
});

test('start screen never manufactures ready model or internet state',()=>{
 const badges=section('private fun buildStatusRow(): View','private fun buildModeRow(): View');
 // defaults mean "offline / not measured", never sample numbers
 assert.match(dashboardState,/val online: Boolean = false/);
 assert.match(dashboardState,/val stateLabel: String = "Offline"/);
 assert.match(dashboardState,/val qualityPercent: Int\? = null/);
 assert.match(dashboardState,/val networkOnline: Boolean\? = null/);
 assert.match(activity,/val modelReady = ::localRuntime\.isInitialized && localRuntime\.loadedModel != null &&\s*localRuntime\.state in setOf\(LocalModelState\.READY, LocalModelState\.GENERATING\)/);
 for(const token of ['shizukuBadge','cloudBadge','localBadge']) assert.ok(badges.includes(token));
 assert.doesNotMatch(dashboardState+homeScreen+drawn+badges,/Llama 3\.2|Gemini 1\.5|Modell geladen/);
 // "Aktiv/online" requires an actually loaded READY/GENERATING model, never a readable SWB1 status.
 const collector=fs.readFileSync('app/src/main/java/at/heinz/deviceai/ContinuumDashboardCollector.kt','utf8');
 assert.match(collector,/online = live\.modelReady,/);
 assert.doesNotMatch(collector,/online = [^\n]*status != null/);
 assert.match(collector,/status != null -> "Nur Startkern"/);
 // an empty or unreadable error list is never presented as "optimal"; a holdout score is not called "Leistung"
 assert.match(dashboardState,/null -> "Liste unlesbar"/);
 assert.match(dashboardState,/0 -> "keine offenen Fehlermeldungen"/);
 assert.match(collector,/diagnostics\.open\(\)\.size \}\.getOrNull\(\)/);
 assert.doesNotMatch(dashboardState+homeScreen+drawn+collector,/"Optimal"|nominal|"Echtzeit"|"Leistung"|neue Erkenntnisse|aktive Projekte|aktive Quellen/);
 // connectivity is yes/no: no invented 100 % level for the network
 assert.doesNotMatch(drawn,/networkOnline\?\.let \{ if \(it\) 100/);
 // the start core answers with the topic of the last turn only
 assert.match(collector,/"1 Zug \(Thema\)"/);
 // the example numbers of the reference images exist only in the JVM preview sources
 assert.ok(!fs.existsSync(ui+'ReferencePreviewData.kt'));
 assert.ok(fs.existsSync('tools/continuum-jvm-preview/src/at/heinz/deviceai/ui/ReferencePreviewData.kt'));
 // Offline reference data remains host-JVM-only, never compiled into the Android app.
});
