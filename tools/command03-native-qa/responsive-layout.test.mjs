import assert from 'node:assert/strict';
import {createRequire} from 'node:module';
import {createServer} from 'node:http';
import {execFileSync} from 'node:child_process';
import {readFile, mkdir, writeFile} from 'node:fs/promises';
import {resolve, extname, sep} from 'node:path';
import {fileURLToPath} from 'node:url';

const require = createRequire(import.meta.url);
const {chromium} = require(process.env.COMMAND03_PLAYWRIGHT_MODULE || 'playwright');
const root = resolve(fileURLToPath(new URL('../../app/src/main/assets/continuum-command03/', import.meta.url)));
const output = resolve(process.env.COMMAND03_LAYOUT_OUTPUT || 'command03-layout-evidence');
const baseline = process.env.COMMAND03_BASELINE_CSS;
// Code64: bridge and layer of the commit whose arrangement must stay exactly as it is.
const baselineRef = process.env.COMMAND03_BASELINE_REF || '823458185';
let baselineFiles = null, baselineError = '';
try {
  baselineFiles = Object.fromEntries(['native-bridge.js', 'native-layer.css'].map(name => [name,
    execFileSync('git', ['show', `${baselineRef}:app/src/main/assets/continuum-command03/${name}`],
      {cwd: resolve(root, '../../../../..'), maxBuffer: 8 * 1024 * 1024, stdio: ['ignore', 'pipe', 'pipe']})]));
} catch (error) {baselineError = String(error.message).split('\n')[0].slice(0, 200);}
const mime = {'.html':'text/html', '.css':'text/css', '.js':'application/javascript',
  '.png':'image/png', '.ttf':'font/ttf', '.glb':'model/gltf-binary'};
const scope = {sessionId:'12345678-1234-1234-1234-123456789abc',
  pageId:'11111111-1111-4111-8111-111111111111',
  viewId:'22222222-2222-4222-8222-222222222222', viewRevision:1};
const state = {...scope, draftRevision:1, ackEditRevision:0, viewKind:'CENTRAL',
  viewLabel:'JARVIS', cpu:12, ram:84, telemetryAtElapsedMs:100, chats:[], workRows:[]};
await mkdir(output, {recursive:true});
const server = createServer(async (req, res) => {
  try {
    const url = new URL(req.url, 'http://localhost');
    const before = url.pathname.startsWith('/before/');
    const relative = decodeURIComponent(url.pathname.replace(/^\/(before|after|base)\//, ''));
    const path = resolve(root, relative);
    if (!path.startsWith(root + sep)) {res.writeHead(403).end();return;}
    const bytes = url.pathname.startsWith('/base/') && baselineFiles?.[relative] ? baselineFiles[relative] :
      await readFile(before && relative==='native-layer.css' && baseline ? baseline : path);
    res.writeHead(200, {'Content-Type':mime[extname(path)] || 'application/octet-stream',
      'Cache-Control':'no-store'}).end(bytes);
  } catch {res.writeHead(404).end();}
});
await new Promise(resolve => server.listen(0, '127.0.0.1', resolve));
const url = `http://127.0.0.1:${server.address().port}`;
let browser;
const report = {source:process.env.GITHUB_SHA || null, browser:null, baseline:null, checks:[]};
try {
  browser = await chromium.launch({executablePath:process.env.COMMAND03_CHROMIUM_PATH || undefined,headless:true, args:['--no-sandbox','--enable-unsafe-swiftshader']});
  report.browser = browser.version();
  const context = await browser.newContext({viewport:{width:412,height:915}, isMobile:true,
    hasTouch:true, deviceScaleFactor:1});
  const page = await context.newPage();
  const errors = [];
  page.on('pageerror', error => errors.push(error.message));
  async function open(version) {
    await page.goto(`${url}/${version}/index.android.html`, {waitUntil:'load'});
    await page.evaluate(value => window.ContinuumNative.receive(value), state);
    await page.evaluate(() => document.fonts.ready);
  }
  async function inspect(label, capture=false) {
    await page.evaluate(() => new Promise(resolve => requestAnimationFrame(() => requestAnimationFrame(resolve))));
    const value = await page.evaluate(() => {
      const rect = selector => {
        const el = document.querySelector(selector), r = el.getBoundingClientRect();
        return {x:r.x,y:r.y,width:r.width,height:r.height,right:r.right,bottom:r.bottom,
          scroll:el.scrollWidth,client:el.clientWidth,display:getComputedStyle(el).display};
      };
      return {width:innerWidth,height:innerHeight,scroll:document.documentElement.scrollWidth,
        scrollX, viewport:document.documentElement.clientWidth,
        composer:rect('#command-form'),settings:rect('.header-tools>[data-action="settings"]'),
        shortcuts:['voice','model','internet','appearance'].map(kind=>
          ({kind,...rect('.native-setting-shortcut[data-action="settings-'+kind+'"]')})),
        cards:['[data-presence-left]','[data-presence-right]','[data-telemetry-left]',
          '[data-telemetry-right]'].map(selector => ({selector,...rect(selector)})),
        wings:['.presence-wing-left','.presence-wing-right'].map(rect)};
    });
    const failures = [];
    if(value.scroll>value.viewport+1)failures.push('root horizontal overflow');
    for(let i=0;i<value.cards.length;i++) {
      const card=value.cards[i], wing=value.wings[i%2];
      if(card.width<Math.min(150,wing.width)-1)failures.push(`${card.selector}: collapsed card`);
      if(card.x<wing.x-1 || card.right>wing.right+1)failures.push(`${card.selector}: outside wing`);
      if(card.x<0 || card.right>value.viewport+1)failures.push(`${card.selector}: outside viewport`);
    }
    if(value.settings.width<44 || value.settings.right>value.viewport+1)failures.push('settings unreachable');
    for(const shortcut of value.shortcuts) {
      if(shortcut.width<44 || shortcut.height<44 || shortcut.x<0 || shortcut.right>value.viewport+1)
        failures.push(shortcut.kind+': settings shortcut outside viewport');
    }
    if(value.viewport<=760 && (value.composer.y<0 || value.composer.bottom>value.height+1 ||
      value.composer.x<0 || value.composer.right>value.viewport+1))failures.push('composer outside viewport');
    await page.evaluate(() => window.scrollTo(200,window.scrollY));
    if(await page.evaluate(() => window.scrollX)!==0)failures.push('page can pan horizontally');
    const item={label,...value,failures};
    if(capture)await page.screenshot({path:resolve(output,label+'.png')});
    return item;
  }
  if(baseline) {
    await open('before');
    report.baseline=await inspect('before-412',true);
    assert.ok(report.baseline.failures.some(f=>f.includes('collapsed card')),
      'Baseline must reproduce the reported collapsed RAM card');
  }
  await open('after');
  for(const [width,height] of [[320,740],[360,800],[390,844],[412,915],[450,962],[480,960],
    [600,900],[720,960],[760,900],[761,900],[834,1112],[960,900],[1280,800],
    [960,540],[412,400],[834,1112],[412,915]]) {
    await page.setViewportSize({width,height});
    const item=await inspect(`after-${width}x${height}-${report.checks.length}`,
      [320,412,834].includes(width) && height>700);
    report.checks.push(item);
  }
  await page.setViewportSize({width:412,height:915});
  await page.locator('#command-input').fill('Mehrzeiliger Auftrag\nZweite Zeile\nDritte Zeile\nVierte Zeile');
  await page.evaluate(() => window.scrollTo(0,document.documentElement.scrollHeight));
  report.checks.push(await inspect('multiline-bottom',true));
  const tail=await page.evaluate(() => ({bar:document.querySelector('#command-form').getBoundingClientRect().top,
    last:document.querySelector('.timeline-strip').getBoundingClientRect().bottom}));
  if(tail.last>tail.bar)report.checks.at(-1).failures.push('composer covers final content');
  await page.locator('[data-action="toggle-instruments"]').click();
  await page.locator('.instrument-sidebar').waitFor({state:'visible'});
  const drawer=await page.locator('.instrument-sidebar').boundingBox();
  assert.ok(drawer && drawer.x>=0 && drawer.x+drawer.width<=412,'Instrument drawer must fit');
  await page.locator('.instrument-sidebar [data-action="close-panels"]').click();
  report.checks.push(await inspect('drawer-closed'));
  await page.evaluate(value=>window.ContinuumNative.receive(value),
    {...state,appearance:{neon:30,lightMotion:false}});
  const quiet=await page.locator('.native-holo-field').evaluate(el=>({
    level:getComputedStyle(el).opacity,motion:getComputedStyle(el,'::before').animationPlayState}));
  assert.equal(quiet.level,'0.3');
  assert.equal(quiet.motion,'paused');
  await page.evaluate(value=>window.ContinuumNative.receive(value),
    {...state,appearance:{neon:70,lightMotion:true}});
  await page.emulateMedia({reducedMotion:'reduce'});
  assert.equal(await page.locator('.native-holo-field').evaluate(el=>getComputedStyle(el,'::before').animationName),'none');
  await page.emulateMedia({reducedMotion:'no-preference'});
  await page.evaluate(()=>window.scrollTo(0,0));
  const blocked=await page.locator('.native-setting-shortcut,.native-settings-entry').evaluateAll(buttons=>
    buttons.filter(button=>{
      const r=button.getBoundingClientRect();
      return document.elementFromPoint(r.x+r.width/2,r.y+r.height/2)?.closest('button')!==button;
    }).map(button=>button.textContent.trim()));
  assert.deepEqual(blocked,[],'Light layers must not block settings');
  report.appearance={nativePreference:'PASS',reducedMotion:'PASS',settingsHitTargets:'PASS'};
  await page.setViewportSize({width:834,height:1112});
  await page.evaluate(()=>window.scrollTo(0,0));
  for(const [mode,values] of [['idle',{}],['listening',{listening:true}],
      ['working',{busy:true}],['speaking',{speaking:true}]]) {
    await page.evaluate(value=>window.ContinuumNative.receive(value),{...state,...values});
    assert.equal(await page.locator('body').getAttribute('data-state'),mode);
    assert.doesNotMatch(await page.locator('.presence-system').innerText(),/Argon4|Demonstration|Entwurfswerte/);
    if(mode==='working') {
      await page.locator('.native-stop-button').waitFor({state:'visible'});
      assert.equal(await page.locator('.send-button').isVisible(),false);
    }
    await page.screenshot({path:resolve(output,'cinema-'+mode+'-fold.png'),fullPage:true});
  }
  await page.evaluate(value=>window.ContinuumNative.receive(value),{...state,busy:true});
  await page.setViewportSize({width:320,height:740});
  const stop=await page.locator('.native-stop-button').evaluate(button=>{
    const r=button.getBoundingClientRect();
    return {width:r.width,height:r.height,hit:document.elementFromPoint(r.x+r.width/2,r.y+r.height/2)===button};
  });
  assert.ok(stop.width>=44 && stop.height>=44 && stop.hit,'Stop remains touchable at 320px');
  report.checks.push(await inspect('cinema-busy-320',true));
  await page.evaluate(value=>window.ContinuumNative.receive(value),{...state,busy:true,appearance:{neon:70,lightMotion:false}});
  assert.equal(await page.locator('.native-activity .native-instrument-art').evaluate(el=>getComputedStyle(el).animationPlayState),'paused');
  await page.evaluate(()=>window.dispatchEvent(new Event('pagehide')));
  assert.equal(await page.locator('.native-activity .native-instrument-art').evaluate(el=>getComputedStyle(el).animationPlayState),'paused');
  await page.evaluate(()=>window.dispatchEvent(new Event('pageshow')));
  await page.emulateMedia({reducedMotion:'reduce'});
  assert.equal(await page.locator('.native-activity .native-instrument-art').evaluate(el=>getComputedStyle(el).animationName),'none');
  report.cinema={stateLighting:'PASS',demoInstrumentsRemoved:'PASS',stopTouchTarget:'PASS',motionPreference:'PASS'};
  // Optional Android activity: the longest permitted title, detail and six steps.
  await page.emulateMedia({reducedMotion:'no-preference'});
  const receive=value=>page.evaluate(packet=>window.ContinuumNative.receive(packet),{...state,...value});
  const activity={revision:1,kind:'RESEARCH',active:true,
    title:'Quellenrecherche zu Wärmepumpen-Förderprogrammen in Oberösterreich und Salzburg',
    detail:'Durchsuche freigegebene Dokumente und offene Webquellen nach aktuellen Fördersätzen, Fristen und '+
      'Voraussetzungen für private Haushalte im Bestand und prüfe sie.',
    steps:[['Gedächtnis nach früheren Förderfragen und gespeicherten Bescheiden durchsucht','DONE'],
      ['Dateisuche in „Dokumente/Heizung“ mit vierzehn Treffern vollzählig abgeschlossen','DONE'],
      ['Websuche: Landesförderung Oberösterreich Wärmepumpe Sanierung Bestandsgebäude','ACTIVE'],
      ['Quelle land-oberoesterreich.gv.at/foerderungen konnte nicht geladen werden','FAILED'],
      ['Freigabe für die kostenpflichtige Abfrage der Förderdatenbank des Bundes','WAITING'],
      ['Ergebnis gegen eine zweite unabhängige Quelle prüfen und Abweichungen nennen','UNVERIFIED']]
      .map(([label,state])=>({label,state}))};
  // The fixture stays inside Android's bounds and close to them, so nothing is cut by the page.
  assert.ok(activity.title.length>=76 && activity.title.length<=80 && activity.detail.length>=152 &&
    activity.detail.length<=160 && activity.steps.length===6 &&
    activity.steps.every(step=>step.label.length>=70 && step.label.length<=80),'Activity fixture bounds');
  for(const [width,height,label] of [[320,740,'activity-320'],[834,1112,'activity-fold']]) {
    await page.setViewportSize({width,height});
    await receive({busy:true,presence:'RESEARCHING',status:'Recherche läuft',activity});
    await page.evaluate(()=>document.querySelector('.native-activity').scrollIntoView({block:'center'}));
    const item=await inspect(label);
    const card=await page.evaluate(()=>{
      const node=document.querySelector('.native-activity');
      const box=el=>{const r=el.getBoundingClientRect();return {x:r.x,y:r.y,right:r.right,bottom:r.bottom,width:r.width,height:r.height};};
      const style=getComputedStyle(node);
      return {card:box(node),wing:box(document.querySelector('.presence-wing-left')),
        below:box(document.querySelector('[data-telemetry-left]')),viewport:document.documentElement.clientWidth,
        scroll:node.scrollWidth,client:node.clientWidth,
        inner:node.getBoundingClientRect().bottom-parseFloat(style.borderBottomWidth)-parseFloat(style.paddingBottom),
        title:node.querySelector('strong').textContent,detail:node.querySelector('p').textContent,
        parts:[...node.querySelectorAll('h3,strong,p,.native-activity-steps,.native-activity-steps li,.native-step-label,.native-step-state')]
          .map(el=>({name:el.className||el.tagName,...box(el)})),
        steps:[...node.querySelectorAll('.native-activity-steps li')].map(el=>el.dataset.state+':'+el.textContent)};
    });
    assert.equal(card.title,activity.title);assert.equal(card.detail,activity.detail);
    assert.deepEqual(card.steps,activity.steps.map(step=>step.state.toLowerCase()+':'+step.label+
      {DONE:'erledigt',ACTIVE:'läuft',FAILED:'fehlgeschlagen',WAITING:'wartet',UNVERIFIED:'ungeprüft'}[step.state]));
    if(card.scroll>card.client+1)item.failures.push('activity card scrolls horizontally');
    if(card.card.x<card.wing.x-1 || card.card.right>card.wing.right+1 || card.card.x<0 ||
      card.card.right>card.viewport+1)item.failures.push('activity card outside its wing or the viewport');
    if(card.below.y<card.card.bottom-1 && card.below.x<card.card.right && card.below.right>card.card.x)
      item.failures.push('activity card overlaps the telemetry card');
    for(const part of card.parts) {
      if(part.width<=0 || part.height<=0)item.failures.push(part.name+': activity text collapsed');
      if(part.x<card.card.x-1 || part.right>card.card.right+1)item.failures.push(part.name+': activity text outside card');
      if(part.bottom>card.inner+1)item.failures.push(part.name+': activity text below card');
    }
    // Evidence only: the whole card above the fixed composer, in a taller window of the same width.
    await page.setViewportSize({width,height:Math.max(height,Math.ceil(card.card.height)+260)});
    await page.evaluate(()=>document.querySelector('.native-activity').scrollIntoView({block:'start'}));
    await page.screenshot({path:resolve(output,label+'.png')});
    await page.setViewportSize({width,height});
    report.checks.push(item);
  }
  // Presence, light, tier, speech level and gaze against the byte-locked scene, orb and avatar.
  const live=()=>page.evaluate(()=>({orb:window.ContinuumOriginalOrb.getStatus().state,
    scene:window.ContinuumScene.getStatus(),avatar:window.ContinuumAvatar.getStatus().state,
    presence:document.body.dataset.presence,quality:document.body.dataset.nativeQuality,
    text:document.querySelector('#orb-status').textContent,
    pill:getComputedStyle(document.querySelector('.core-status')).color,
    drift:getComputedStyle(document.querySelector('.native-holo-field'),'::before').animationName,
    cross:getComputedStyle(document.querySelector('.native-holo-field'),'::after').animationName,
    pulse:getComputedStyle(document.querySelector('.native-activity .native-instrument-art')).animationName,
    shown:['.avatar-crop','.orb-zone','#command-form','.native-activity','.native-evidence','.native-holo-field',
      '.native-stop-button','.native-settings-shortcuts'].filter(selector=>{
        const el=document.querySelector(selector), style=getComputedStyle(el), r=el.getBoundingClientRect();
        return style.display!=='none' && style.visibility==='visible' && Number(style.opacity)>0 && r.width>0 && r.height>0;
      }).length}));
  let now=await live();
  assert.deepEqual([now.orb,now.scene.state,now.avatar,now.text,now.pulse],
    ['research','working','working','RECHERCHIERT','native-status-breathe']);
  assert.equal(now.scene.intensity,.78,'Default Android light keeps the present scene light');
  await receive({busy:true,presence:'RESEARCHING',appearance:{neon:100,lightMotion:true},quality:'REDUCED',activity});
  now=await live();
  assert.deepEqual([now.orb,now.scene.state,now.avatar,now.scene.intensity,now.scene.quality,now.quality],
    ['research','working','working',1,.6,'reduced'],'Light and tier changes keep the orb in its research mode');
  assert.deepEqual([now.drift,now.cross,now.shown],['none','none',8],'Reduced eases only secondary light');
  await receive({busy:true,presence:'LEARNING',appearance:{neon:70,lightMotion:true},quality:'BALANCED',activity});
  now=await live();
  assert.deepEqual([now.orb,now.scene.state,now.avatar,now.text,now.scene.intensity,now.scene.quality,now.quality],
    ['learning','working','working','LERNT',.78,.8,'balanced']);
  assert.deepEqual([now.drift,now.cross,now.shown],['native-holo-drift','none',8]);
  await receive({busy:true,presence:'WAITING_FOR_APPROVAL',activity:{...activity,revision:2,active:false}});
  now=await live();
  assert.deepEqual([now.orb,now.scene.state,now.avatar,now.text,now.presence,now.pill,now.pulse,now.quality,now.scene.quality],
    ['idle','idle','idle','WARTET AUF FREIGABE','waiting-for-approval','rgb(255, 200, 128)','none',undefined,1]);
  assert.deepEqual([now.drift,now.cross,now.shown],['native-holo-drift','native-holo-cross',8]);
  await receive({speaking:true});
  const speech={...scope,generation:1,sequence:1,active:true,utteranceId:'layout-utterance',viseme:'AA',
    weight:.7,jawOpen:.45,durationMs:400,source:'PCM_ENVELOPE'};
  await page.evaluate(packet=>window.ContinuumNative.speech(packet),speech);
  assert.equal((await live()).scene.audioLevel,.45,'The original scene receives the real speech level');
  await page.evaluate(packet=>window.ContinuumNative.speech(packet),{...scope,generation:1,sequence:2,active:false});
  assert.equal((await live()).scene.audioLevel,null);
  now=await live();
  assert.deepEqual([now.orb,now.text,now.presence],['speaking','SPRICHT',undefined]);
  const gaze=await page.evaluate(packet=>new Promise(done=>{
    window.addEventListener('continuum:gaze',event=>{
      const r=event.target.getBoundingClientRect();
      done({name:event.target.className,same:event.detail.target===event.target,width:r.width,height:r.height});
    },{once:true});
    window.ContinuumNative.sources(packet);
  }),{...scope,sourceSetId:'55555555-5555-4555-8555-555555555555',
    items:[{id:'66666666-6666-4666-8666-666666666666',title:'Quelle',url:'https://example.org/evidence'}]});
  assert.ok(gaze.name.includes('native-source-card') && gaze.same && gaze.width>0 && gaze.height>0,
    'The avatar is pointed at a source card that has a real box');
  report.native={activityLayout:'PASS',presenceOrbModes:'PASS',qualityTiers:'PASS',speechLevel:'PASS',gazeTarget:'PASS'};
  // ───────── Code64: unchanged arrangement, animated original orb, one room, real work sequence ─────────
  // The page is loaded once with the bridge and layer of the baseline commit and once as it is now.
  // Every box of the presence block, the tiles, navigation and composer must be the same within 1 px.
  const boxSelectors=['.masthead','.plugin-toolbar','#main-nav','.native-settings-shortcuts','.presence-topline',
    '.presence-system','.presence-core','.orb-zone','#continuum-original-orb','.avatar-zone','.avatar-crop',
    '.avatar-caption','.core-status','.avatar-linkbar','.presence-wing-left','.presence-wing-right',
    '[data-presence-left]','[data-presence-right]','[data-telemetry-left]','[data-telemetry-right]',
    '.native-activity','.native-evidence','.presence-copy','.workspace-shell','#command-form'];
  const textSelectors=['.native-activity h3','.native-activity>strong','.native-activity>p','.native-step-label',
    '.native-step-state','.native-evidence>strong','.native-telemetry-panel strong','.native-telemetry-panel h3',
    '#orb-status','.avatar-caption','.presence-copy h1','#workspace-label','#command-input','.nav-button'];
  const roomViewports=[[320,740],[301,772],[344,882],[412,915],[834,1112],[1112,834],[1280,800]];
  const roomStates={idle:{presence:'IDLE'},
    working:{busy:true,presence:'THINKING',status:'Auftrag läuft',activity:{...activity,revision:11,kind:'DOCUMENT'}}};
  // Every viewport is compared with its fullest tiles; the resting state on one phone and the unfolded display.
  const comparedStates=width=>Object.entries(roomStates).filter(([name])=>name==='working' || width===412 || width===834);
  async function openRoom(version,width,height,values,options={}) {
    const roomContext=await browser.newContext({viewport:{width,height},isMobile:true,hasTouch:true,
      deviceScaleFactor:1,reducedMotion:options.reducedMotion||'no-preference'});
    const room=await roomContext.newPage();
    room.on('pageerror',error=>errors.push(version+': '+error.message));
    await room.goto(`${url}/${version}/index.android.html`,{waitUntil:'load'});
    await room.evaluate(value=>window.ContinuumNative.receive(value),{...state,...values});
    await room.evaluate(()=>document.fonts.ready);
    // The composer measures itself on resize; both versions do so after their fonts are ready.
    await room.evaluate(()=>{window.dispatchEvent(new Event('resize'));
      return new Promise(done=>requestAnimationFrame(()=>requestAnimationFrame(done)));});
    return room;
  }
  const measure=room=>room.evaluate(([boxes,texts])=>({
    boxes:Object.fromEntries(boxes.map(selector=>{
      const node=document.querySelector(selector);if(!node)return [selector,null];
      const r=node.getBoundingClientRect();return [selector,[r.x+scrollX,r.y+scrollY,r.width,r.height]];})),
    texts:Object.fromEntries(texts.map(selector=>{
      const node=document.querySelector(selector);if(!node)return [selector,null];
      const style=getComputedStyle(node);return [selector,[style.color,style.fontSize,style.fontWeight]];})),
    height:document.documentElement.scrollHeight,scroll:document.documentElement.scrollWidth,
    viewport:document.documentElement.clientWidth}),[boxSelectors,textSelectors]);
  /** Controls stay the topmost element at their own centre: no light layer may cover a touch target. */
  const blockedControls=room=>room.evaluate(async()=>{
    const blocked=[];
    for(const selector of ['.header-tools>[data-action="settings"]','.native-setting-shortcut','.avatar-linkbar .icon-button',
      '#main-nav .nav-button','.presence-mission-actions button','#listen-button','.send-button,.native-stop-button',
      '#command-input','#expand-button','#close-button','[data-action="toggle-instruments"]']) {
      for(const control of document.querySelectorAll(selector)) {
        if(control.hidden || getComputedStyle(control).display==='none')continue;
        control.scrollIntoView({block:'center',inline:'center'});
        const r=control.getBoundingClientRect();
        if(r.width<=0 || r.height<=0) {blocked.push(selector+': no box');continue;}
        const top=document.elementFromPoint(r.x+r.width/2,r.y+r.height/2);
        if(top!==control && !control.contains(top))blocked.push(selector+': covered by '+(top?.className||top?.tagName));
        else if(!control.matches('#command-input,.nav-button') && (r.width<43.5 || r.height<43.5))
          blocked.push(selector+': smaller than 44px');
      }
    }
    scrollTo(0,0);return blocked;
  });
  const baselineReady=baselineFiles!==null;
  if(!baselineReady && process.env.COMMAND03_REQUIRE_BASELINE==='1')
    assert.fail('Baseline '+baselineRef+' is not readable from git: '+baselineError);
  report.code64={baseline:baselineReady?baselineRef:'NOT_RUN: '+baselineError,boxSelectors:boxSelectors.length};
  for(const [width,height] of roomViewports) {
    for(const [name,values] of comparedStates(width)) {
      const item={label:`code64-boxes-${name}-${width}x${height}`,failures:[]};
      const now=await openRoom('after',width,height,values);
      const current=await measure(now);
      if(current.scroll>current.viewport+1)item.failures.push('root horizontal overflow');
      for(const selector of ['.avatar-zone','.orb-zone','.native-activity','.native-evidence','#command-form'])
        if(!current.boxes[selector] || current.boxes[selector][2]<=0 || current.boxes[selector][3]<=0)
          item.failures.push(selector+': missing box');
      if(baselineReady) {
        const before=await openRoom('base',width,height,values);
        const original=await measure(before);
        for(const selector of boxSelectors) {
          const a=original.boxes[selector], b=current.boxes[selector];
          if(!a || !b) {if(a!==b)item.failures.push(selector+': exists only in one version');continue;}
          if(a.some((value,index)=>Math.abs(value-b[index])>1))
            item.failures.push(selector+': moved or resized '+JSON.stringify(a.map(Math.round))+' → '+JSON.stringify(b.map(Math.round)));
        }
        if(Math.abs(original.height-current.height)>1)item.failures.push('page height changed');
        for(const selector of textSelectors)
          if(JSON.stringify(original.texts[selector])!==JSON.stringify(current.texts[selector]))
            item.failures.push(selector+': text colour or size changed');
        if(width===834 && name==='idle') {
          // Evidence for the fix: the baseline orb drew its resting picture once, the orb now runs.
          const frames=async room=>{
            const first=await room.evaluate(()=>window.ContinuumOriginalOrb.getStatus().frames);
            await room.waitForTimeout(900);
            return [first,await room.evaluate(()=>window.ContinuumOriginalOrb.getStatus().frames)];
          };
          const still=await frames(before), alive=await frames(now);
          report.code64.orbFrames={baseline:still,current:alive};
          assert.equal(still[1],still[0],'The baseline orb is a resting picture');
          assert.ok(alive[1]>alive[0],'The original orb animates');
          assert.equal(await before.locator('.orb-zone').getAttribute('data-orb-motion'),'fixed');
          assert.equal(await now.locator('.orb-zone').getAttribute('data-orb-motion'),'live');
        }
        await before.context().close();
      }
      item.failures.push(...await blockedControls(now));
      // The presence block as the owner sees it.
      await now.evaluate(()=>document.querySelector('.presence').scrollIntoView({block:'start'}));
      await now.waitForTimeout(350);
      await now.screenshot({path:resolve(output,`code64-room-${name}-${width}x${height}.png`)});
      await now.context().close();
      report.checks.push(item);
    }
  }
  // Light, tiers and the work sequence in a real browser engine, on the unfolded display.
  const stage=await openRoom('after',834,1112,{presence:'IDLE'});
  const send=values=>stage.evaluate(packet=>window.ContinuumNative.receive(packet),{...state,...values});
  const light=()=>stage.evaluate(()=>{
    const corona=document.querySelector('.native-orb-light'), shell=corona.querySelector('i'), second=corona.querySelectorAll('i')[1];
    const haze=document.querySelector('.native-room-light'), tile=document.querySelector('.presence-wing-left .native-instrument');
    const style=getComputedStyle;
    return {energy:document.body.dataset.energy,corona:style(corona).animationName,shell:style(shell).animationName,
      duration:style(shell).animationDuration,shellDisplay:style(shell).display,secondDisplay:style(second).display,
      shellBorder:style(shell).borderTopStyle,play:style(corona).animationPlayState,
      glow:Number(style(corona.querySelector('b')).opacity),haze:style(haze).backgroundImage,
      mask:style(haze).maskImage||style(haze).webkitMaskImage,floor:style(haze,'::after').display,
      tile:style(tile).boxShadow,window:style(document.querySelector('.workspace-shell')).boxShadow,
      orb:document.querySelector('.orb-zone').dataset.orbMotion,pointer:style(corona).pointerEvents+style(haze).pointerEvents,
      phase:document.body.dataset.workPhase,surface:document.body.dataset.workSurface};});
  let lit=await light();
  assert.deepEqual([lit.energy,lit.corona,lit.shell,lit.orb,lit.pointer,lit.phase],
    ['idle','native-corona-breathe','none','live','nonenone','idle'],'Ready: the corona breathes, nothing travels');
  const idleTile=lit.tile, idleWindow=lit.window, idleHaze=lit.haze;
  for(const [presence,shell,duration] of [['LISTENING','native-shell-in','2.6s'],['THINKING','native-shell-out','2.2s'],
    ['RESEARCHING','native-shell-out','1.7s'],['LEARNING','native-shell-out','3s']]) {
    await send({busy:presence!=='LISTENING',listening:presence==='LISTENING',presence});
    await stage.evaluate(()=>document.querySelector('.presence').scrollIntoView({block:'start'}));
    // The glass eases into the new light; the engine's own clock decides when it has arrived.
    await stage.waitForFunction(([before,expected])=>
      getComputedStyle(document.querySelector('.presence-wing-left .native-instrument')).boxShadow!==before &&
      getComputedStyle(document.querySelector('.native-orb-light i')).animationName===expected,[idleTile,shell],{timeout:20000});
    await stage.waitForTimeout(700);
    lit=await light();
    assert.deepEqual([lit.shell,lit.duration,lit.orb],[shell,duration,'live'],presence);
    assert.notEqual(lit.tile,idleTile,'The tiles stand in the orb light of '+presence);
    assert.notEqual(lit.window,idleWindow,'The work window stands in the orb light of '+presence);
    assert.notEqual(lit.haze,idleHaze,'The haze follows '+presence);
    await stage.screenshot({path:resolve(output,`code64-room-${presence.toLowerCase()}-834x1112.png`)});
  }
  for(const presence of ['WAITING_FOR_APPROVAL','BLOCKED']) {
    await send({busy:true,presence});
    lit=await light();
    assert.deepEqual([lit.energy,lit.shell,lit.shellBorder,lit.corona],['hold','none','dashed','none'],presence+' is held still');
    await stage.waitForTimeout(700);
    await stage.screenshot({path:resolve(output,`code64-room-${presence.toLowerCase().replace(/_/g,'-')}-834x1112.png`)});
  }
  // Speaking: no keyframes; the corona is the measured level.
  // Each frame is read in the call that delivers it. The system preference removes the 90 ms easing,
  // so the value does not depend on how fast this machine paints.
  await stage.emulateMedia({reducedMotion:'reduce'});
  await send({speaking:true,presence:'SPEAKING'});
  const utter=(sequence,jawOpen,active=true)=>stage.evaluate(packet=>{
    window.ContinuumNative.speech(packet);
    const corona=document.querySelector('.native-orb-light');
    return {energy:document.body.dataset.energy,shell:getComputedStyle(corona.querySelector('i')).animationName,
      corona:getComputedStyle(corona).animationName,glow:Number(getComputedStyle(corona.querySelector('b')).opacity),
      haze:getComputedStyle(document.querySelector('.native-room-light')).backgroundImage};
  },{...scope,generation:7,sequence,active,utteranceId:'code64-utterance',viseme:'AA',weight:.7,jawOpen,
    durationMs:500,source:'PCM_ENVELOPE'});
  const soft=await utter(1,.1), loud=await utter(2,.9);
  assert.deepEqual([soft.energy,soft.shell,soft.corona],['speech','none','none']);
  assert.ok(loud.glow>soft.glow+.2,'A louder real speech frame gives a brighter corona: '+soft.glow+' → '+loud.glow);
  assert.notEqual(loud.haze,soft.haze,'The room haze follows the speech level');
  await utter(3,.9);
  await stage.screenshot({path:resolve(output,'code64-room-speaking-834x1112.png')});
  assert.ok((await utter(4,0,false)).glow<loud.glow,'The level leaves with the speech');
  await stage.emulateMedia({reducedMotion:'no-preference'});
  // Thermal tiers, the owner's switch and the system preference reach every new light.
  await send({busy:true,presence:'THINKING',quality:'BALANCED'});
  lit=await light();
  assert.deepEqual([lit.orb,lit.shell,lit.shellDisplay,lit.secondDisplay],['live','native-shell-out','block','none']);
  await send({busy:true,presence:'THINKING',quality:'REDUCED'});
  lit=await light();
  assert.deepEqual([lit.orb,lit.shellDisplay,lit.floor,lit.mask,lit.corona],['fixed','none','none','none','none'],
    'Reduced: the orb rests, shells, floor grid and mask are gone');
  assert.ok((await stage.evaluate(()=>['.avatar-crop','.orb-zone','.native-activity','.native-evidence','#command-form']
    .every(selector=>{const r=document.querySelector(selector).getBoundingClientRect();return r.width>0 && r.height>0;}))),
    'Reduced keeps avatar, orb, tiles and composer');
  await stage.screenshot({path:resolve(output,'code64-room-reduced-tier-834x1112.png')});
  await send({busy:true,presence:'THINKING',appearance:{neon:70,lightMotion:false}});
  lit=await light();
  assert.deepEqual([lit.orb,lit.play],['fixed','paused'],'The owner switched light motion off');
  await send({busy:false,presence:'IDLE',appearance:{neon:70,lightMotion:true}});
  await stage.emulateMedia({reducedMotion:'reduce'});
  await send({busy:true,presence:'THINKING'});
  lit=await light();
  assert.deepEqual([lit.orb,lit.shell,lit.corona],['fixed','none','none'],'prefers-reduced-motion stops every new animation');
  await stage.emulateMedia({reducedMotion:'no-preference'});
  await send({busy:false,presence:'IDLE'});
  // Work sequence: activation → file window → projected → document → reply → return.
  const files={revision:1,kind:'SEARCH',label:'Argon4',total:14,more:false,names:['Argon4.pdf','Argon4-Notizen.txt',
    'Argon4 Messprotokoll 2026-10-01 Laborhalle Nord (zweite Fassung, geprüft).pdf','Argon-Lieferschein.pdf',
    'argon4_rohdaten_export_final_wirklich_final_v7.csv','Argon4.docx','Foto Argon4 Anlage.jpg','Argon4 Wartung.xlsx',
    'Argon4 Angebot.pdf','Argon4 Rechnung.pdf','Argon4 Protokoll.txt','Argon4 Skizze.png']};
  const work={busy:true,presence:'THINKING',activity:{...activity,revision:21,kind:'DOCUMENT'}};
  // Every phase change is recorded inside the page. Sampling the attribute from the test process missed a
  // short phase whenever the host was busy (seen: "opening:files" already over at the first read).
  await stage.evaluate(()=>{
    const seen=[];
    const read=()=>{
      const phase=document.body.dataset.workPhase||'idle', surface=document.body.dataset.workSurface||'';
      const value=(phase==='opening'||phase==='projected')?phase+':'+surface:phase;
      if(seen[seen.length-1]!==value)seen.push(value);
    };
    new MutationObserver(read).observe(document.body,
      {attributes:true,attributeFilter:['data-work-phase','data-work-surface']});
    window.__workPhaseLog=seen;
  });
  await send(work);
  await send({...work,files});
  await stage.evaluate(()=>window.ContinuumWorkspaceMotion.whenSettled());
  await stage.waitForFunction(()=>Number(getComputedStyle(document.querySelector('.presence.presence-orbit'),'::after').opacity)>.5,
    null,{timeout:20000}); // the projection reaches the open work window
  const fileWindow=async(page,label)=>{
    const value=await page.evaluate(()=>{
      const panel=document.querySelector('#native-files-panel'), body=document.querySelector('#workspace');
      const box=node=>{const r=node.getBoundingClientRect();return {x:r.x,right:r.right,width:r.width,height:r.height};};
      return {panel:box(panel),body:box(body),scroll:panel.scrollWidth,client:panel.clientWidth,
        root:document.documentElement.scrollWidth,viewport:document.documentElement.clientWidth,
        rows:[...panel.querySelectorAll('li')].map(row=>({...box(row),name:box(row.querySelector('.native-file-name')),
          text:row.querySelector('.native-file-name').textContent})),
        count:panel.querySelector('.native-files-count').textContent,
        shown:[...body.children].filter(node=>getComputedStyle(node).display!=='none').map(node=>node.id)};});
    const item={label,failures:[]};
    if(value.root>value.viewport+1)item.failures.push('root horizontal overflow');
    if(value.scroll>value.client+1)item.failures.push('file window scrolls horizontally');
    if(value.panel.x<value.body.x-1 || value.panel.right>value.body.right+1)item.failures.push('file window outside the workspace');
    if(value.shown.join()!=='native-files-panel')item.failures.push('workspace shows '+value.shown.join());
    if(value.rows.length!==12)item.failures.push('expected twelve rows');
    for(const row of value.rows) {
      if(row.height<43.5)item.failures.push(row.text+': row lower than 44px');
      if(row.x<value.panel.x-1 || row.right>value.panel.right+1)item.failures.push(row.text+': row outside the window');
      if(row.name.width<=0 || row.name.x<row.x || row.name.right>row.right+1)item.failures.push(row.text+': name outside its row');
    }
    assert.equal(value.count,'14 Dateien gefunden · 12 angezeigt');
    report.checks.push(item);
  };
  await fileWindow(stage,'code64-files-834x1112');
  await stage.evaluate(()=>document.querySelector('.presence-copy').scrollIntoView({block:'start'}));
  await stage.screenshot({path:resolve(output,'code64-work-files-834x1112.png')});
  await stage.evaluate(packet=>window.ContinuumNative.document(packet),{...scope,
    documentId:'33333333-3333-4333-8333-333333333333',name:'Argon4.pdf',pageCount:3,textState:'EXTRACTED',
    text:'Argon4 – Messprotokoll.\nDie Messreihe vom 1. Oktober zeigt stabile Werte.',canDraft:true});
  await stage.evaluate(()=>window.ContinuumWorkspaceMotion.whenSettled());
  await stage.screenshot({path:resolve(output,'code64-work-document-834x1112.png')});
  await stage.evaluate(packet=>window.ContinuumNative.message(packet),{...scope,user:false,text:'Zusammenfassung: stabile Werte.'});
  const returnStarted=Date.now();
  await send({busy:false,presence:'IDLE',activity:{...work.activity,revision:22,active:false},files});
  await stage.waitForFunction(()=>document.body.dataset.workPhase==='idle',null,{timeout:30000});
  const returned=Date.now()-returnStarted;
  const sequence=await stage.evaluate(()=>window.__workPhaseLog.slice());
  assert.deepEqual(sequence,['activation','opening:files','projected:files','opening:document','projected:document',
    'settling','idle'],'The stage shows exactly the sequence Android reported');
  assert.ok(returned>=900,'The return lasts as long as its own animation ('+returned+' ms), not a fixed cut');
  assert.equal(await stage.locator('#native-files-panel').count(),0,'The file window left with its turn');
  assert.equal(await stage.locator('#native-document-panel').count(),1,'The opened document stays');
  report.code64.sequence=sequence;report.code64.returnMs=returned;
  // Without motion the same sequence returns at once.
  await stage.emulateMedia({reducedMotion:'reduce'});
  await send({...work,activity:{...work.activity,revision:23}});
  await stage.evaluate(packet=>window.ContinuumNative.message(packet),{...scope,user:false,text:'Zweite Antwort.'});
  await send({busy:false,presence:'IDLE',activity:{...work.activity,revision:24,active:false}});
  assert.equal((await light()).phase,'idle');
  await stage.emulateMedia({reducedMotion:'no-preference'});
  await stage.context().close();
  // The file window on the narrowest phone and the Fold cover display.
  for(const [width,height] of [[320,740],[344,882]]) {
    const phone=await openRoom('after',width,height,{...work,files});
    await phone.evaluate(()=>window.ContinuumWorkspaceMotion.whenSettled());
    await fileWindow(phone,`code64-files-${width}x${height}`);
    await phone.evaluate(()=>document.querySelector('.workspace-shell').scrollIntoView({block:'start'}));
    await phone.screenshot({path:resolve(output,`code64-work-files-${width}x${height}.png`)});
    await phone.context().close();
  }
  // Large font: every text 30 % larger, as a text zoom does. Nothing may leave its panel or the viewport.
  for(const [width,height] of [[320,740],[412,915],[834,1112],[1280,800]]) {
    const large=await openRoom('after',width,height,{...work,files});
    await large.evaluate(()=>window.ContinuumWorkspaceMotion.whenSettled());
    await large.evaluate(()=>{
      const sizes=[...document.querySelectorAll('body *')].filter(node=>!node.closest('svg') &&
        [...node.childNodes].some(child=>child.nodeType===3 && child.textContent.trim()) ||
        node.matches('textarea,input')).map(node=>[node,parseFloat(getComputedStyle(node).fontSize)]);
      for(const [node,size] of sizes)node.style.setProperty('font-size',(size*1.3)+'px','important');
      window.dispatchEvent(new Event('resize'));
      return new Promise(done=>requestAnimationFrame(()=>requestAnimationFrame(done)));
    });
    const item={label:`code64-large-font-${width}x${height}`,failures:[]};
    const value=await large.evaluate(()=>{
      const box=node=>{const r=node.getBoundingClientRect();return {x:r.x,right:r.right,y:r.y,bottom:r.bottom,width:r.width,height:r.height};};
      const inside=(panel,parts)=>[...document.querySelectorAll(panel)].flatMap(node=>{
        const outer=box(node);
        return [...node.querySelectorAll(parts)].filter(part=>getComputedStyle(part).display!=='none').map(part=>{
          const inner=box(part);
          return inner.width>0 && inner.height>0 && inner.x>=outer.x-1 && inner.right<=outer.right+1 &&
            inner.y>=outer.y-1 && inner.bottom<=outer.bottom+1?null:panel+' '+(part.className||part.tagName);});
      }).filter(Boolean);
      return {root:document.documentElement.scrollWidth,viewport:document.documentElement.clientWidth,
        outside:[...inside('.native-instrument','h3,strong,p,.native-step-label,.native-step-state'),
          ...inside('#native-files-panel','.native-files-kicker,.native-files-title,.native-files-count,li,.native-file-name,.native-files-note'),
          ...inside('.core-status','#orb-status'),...inside('#command-form','#command-input,label')],
        core:box(document.querySelector('.presence-core')),avatar:box(document.querySelector('.avatar-zone')),
        orb:box(document.querySelector('.orb-zone'))};});
    if(value.root>value.viewport+1)item.failures.push('root horizontal overflow');
    item.failures.push(...value.outside.map(name=>name+': text outside its panel'));
    if(value.avatar.width<=0 || value.orb.width<=0)item.failures.push('avatar or orb lost');
    item.failures.push(...await blockedControls(large));
    await large.evaluate(()=>document.querySelector('.presence').scrollIntoView({block:'start'}));
    await large.waitForTimeout(300);
    await large.screenshot({path:resolve(output,`code64-large-font-${width}x${height}.png`)});
    await large.context().close();
    report.checks.push(item);
  }
  report.code64.result={arrangement:baselineReady?'PASS':'NOT_RUN',orbAnimated:baselineReady?'PASS':'NOT_RUN',
    presenceLight:'PASS',tiers:'PASS',workSequence:'PASS',filesWindow:'PASS',largeFont:'PASS'};
  assert.deepEqual(errors, [], 'Unexpected page errors');
  const failures=report.checks.flatMap(check=>check.failures.map(f=>check.label+': '+f));
  console.log(JSON.stringify({baseline:report.baseline?.failures,checks:report.checks.length,failures},null,2));
  assert.deepEqual(failures, [], 'Responsive layout regression');
} finally {
  await writeFile(resolve(output,'layout-results.json'),JSON.stringify(report,null,2)+'\n');
  await browser?.close();
  await new Promise(resolve => server.close(resolve));
}

