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
// Code70: bridge and layer of Code69. Its stage while the on-device model computes is the tested resting state;
// the window effects added afterwards must leave exactly that state as it was.
const restRef = process.env.COMMAND03_REST_BASELINE_REF || '717375cc1';
let restFiles = null, restError = '';
try {
  restFiles = Object.fromEntries(['native-bridge.js', 'native-layer.css'].map(name => [name,
    execFileSync('git', ['show', `${restRef}:app/src/main/assets/continuum-command03/${name}`],
      {cwd: resolve(root, '../../../../..'), maxBuffer: 8 * 1024 * 1024, stdio: ['ignore', 'pipe', 'pipe']})]));
} catch (error) {restError = String(error.message).split('\n')[0].slice(0, 200);}
const mime ={'.html':'text/html', '.css':'text/css', '.js':'application/javascript',
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
    const relative = decodeURIComponent(url.pathname.replace(/^\/(before|after|base|rest)\//, ''));
    const path = resolve(root, relative);
    if (!path.startsWith(root + sep)) {res.writeHead(403).end();return;}
    const earlier = url.pathname.startsWith('/base/') ? baselineFiles : url.pathname.startsWith('/rest/') ? restFiles : null;
    const bytes = earlier?.[relative] ? earlier[relative] :
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
  // Code68: the travelling light described from here on is the stage of a cloud brain. While the on-device model
  // computes, the stage rests (checked further down under "local computing").
  await receive({busy:true,presence:'LEARNING',appearance:{neon:70,lightMotion:true},quality:'BALANCED',activity,
    cloudBrain:true});
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
      deviceScaleFactor:options.scale||1,reducedMotion:options.reducedMotion||'no-preference'});
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
    await send({busy:presence!=='LISTENING',listening:presence==='LISTENING',presence,cloudBrain:true});
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
  await send({busy:true,presence:'THINKING',quality:'BALANCED',cloudBrain:true});
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
  // Code68 · local computing (owner finding on the Fold7: with the effects on, the device overheated as soon as an
  // order started). While the on-device model computes, the orb rests, nothing travels or drifts, and one slow
  // pulse of the corona shows the thinking. The same work with a cloud brain keeps the full motion.
  for(const presence of ['THINKING','RESEARCHING','LEARNING']) {
    await send({busy:true,presence});
    lit=await light();
    const rest=await stage.evaluate(()=>({compute:document.body.dataset.nativeCompute,limit:document.body.dataset.frameLimit,
      drift:getComputedStyle(document.querySelector('.native-holo-field'),'::before').animationName,
      pulse:getComputedStyle(document.querySelector('.native-orb-light')).animationDuration}));
    assert.deepEqual([lit.orb,lit.shell,lit.corona,rest.pulse,rest.drift,rest.compute,rest.limit],
      ['fixed','none','native-corona-breathe','1.8s','none','local','5'],presence+': the stage rests while the device computes');
  }
  await send({busy:true,presence:'THINKING',cloudBrain:true});
  lit=await light();
  assert.deepEqual([lit.orb,lit.shell,await stage.evaluate(()=>document.body.dataset.nativeCompute)],
    ['live','native-shell-out',undefined],'A cloud brain keeps the full motion');
  await send({busy:false,presence:'IDLE'});
  lit=await light();
  assert.deepEqual([lit.orb,lit.corona,await stage.evaluate(()=>document.body.dataset.frameLimit)],
    ['live','native-corona-breathe','30'],'After the work the orb is alive again');
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
  // ───────── Code70: cinematic windows on real events (begin) ─────────
  // The work window, the live window (monitor) and the result window in a real browser engine: Chat, Stop and
  // Settings stay reachable on phone and Fold7 (closed, open, keyboard, rotated), a dispatched input sits on the
  // pixel Android measured, and the stage that rests while the on-device model computes is exactly the Code69 one.
  {
    const shots=process.env.COMMAND03_SHOTS_OUTPUT?resolve(process.env.COMMAND03_SHOTS_OUTPUT):null;
    if(shots)await mkdir(shots,{recursive:true});
    report.code70={restBaseline:restFiles?restRef:'NOT_RUN: '+restError,shots:shots?[]:'NOT_REQUESTED'};
    if(!restFiles && process.env.COMMAND03_REQUIRE_BASELINE==='1')
      assert.fail('Baseline '+restRef+' is not readable from git: '+restError);
    const steps70={revision:31,kind:'RESEARCH',active:true,title:'Recherchiert',detail:'Förderprogramme für Wärmepumpen',
      steps:[['Gedächtnis durchsucht','DONE'],['Dateisuche in „Dokumente/Heizung“','DONE'],
        ['Websuche: Landesförderung Oberösterreich','ACTIVE'],['Ergebnis gegen zweite Quelle prüfen','WAITING']]
        .map(([label,state])=>({label,state}))};
    const sources70={...scope,sourceSetId:'55555555-5555-4555-8555-555555555557',items:[
      ['Förderung Wärmepumpe – Land Oberösterreich','https://www.land-oberoesterreich.gv.at/foerderungen/waermepumpe'],
      ['Raus aus Öl und Gas: Bundesförderung','https://www.umweltfoerderung.at/privatpersonen/raus-aus-oel'],
      ['Heizungstausch – Voraussetzungen','https://www.oesterreich.gv.at/themen/bauen_wohnen/heizungstausch']]
      .map(([title,url],index)=>({id:'66666666-6666-4666-8666-66666666667'+index,title,url}))};
    const tap={sequence:6,kind:'CLICK',state:'DISPATCHED',x:.3,y:.19,frameSequence:12,observationRevision:4};
    const work70={consentPending:false,taskId:'computer-synthetic',captureSession:'7',stage:'DISPATCHED',app:'Dateien',
      goal:'Download-Ordner öffnen',actions:[{sequence:3,kind:'SCROLL',state:'VERIFIED',frameSequence:8,observationRevision:2},tap]};
    const put=(room,values)=>room.evaluate(packet=>window.ContinuumNative.receive(packet),{...state,status:'Bereit',...values});
    const say=(room,user,text)=>room.evaluate(packet=>window.ContinuumNative.message(packet),{...scope,user,text});
    const frames=room=>room.evaluate(()=>new Promise(done=>requestAnimationFrame(()=>requestAnimationFrame(done))));
    // A synthetic picture drawn in the page (no device image exists on this host). Like the real feed, the stream
    // keeps delivering; a picture that is not renewed leaves after its lease.
    const startStream=(room,generation=1)=>room.evaluate(async([scope,generation])=>{
      const canvas=document.createElement('canvas');canvas.width=360;canvas.height=640;
      const c=canvas.getContext('2d');
      c.fillStyle='#f4f6fa';c.fillRect(0,0,360,640);c.fillStyle='#1b6ef3';c.fillRect(0,0,360,64);
      c.fillStyle='#fff';c.font='600 20px sans-serif';c.fillText('Dateien',20,40);
      ['Downloads','Dokumente','Bilder','Musik','Videos'].forEach((name,index)=>{
        c.fillStyle='#fff';c.fillRect(16,88+index*84,328,68);c.fillStyle='#20242c';c.font='15px sans-serif';
        c.fillText(name,36,128+index*84);});
      c.fillStyle='#8a93a3';c.font='12px sans-serif';c.fillText('SYNTHETISCHES TESTBILD · kein Gerätebild',20,620);
      const packet={...scope,generation,sequence:1,active:true,status:'LIVE',reason:'Live',taskId:'computer-synthetic',
        captureSession:'7',targetPackage:'com.google.android.documentsui',sourceEpoch:generation,ageMs:10,staleAfterMs:1500,
        frameDataUri:canvas.toDataURL('image/jpeg',.82),width:360,height:640,viewport:[0,0,1,1],sourceSequence:12,
        observationRevision:4};
      let sequence=1;
      const next=()=>window.ContinuumNative.computer({...packet,sequence:++sequence,sourceSequence:11+sequence});
      window.ContinuumNative.computer(packet);
      clearInterval(window.__stream);window.__stream=setInterval(next,350);
      await window.ContinuumWorkspaceMotion.whenSettled();
      const started=performance.now();
      while(!document.querySelector('.native-computer-image') && performance.now()-started<8000)
        await new Promise(done=>setTimeout(done,50));
    },[scope,generation]);
    const stopStream=room=>room.evaluate(()=>clearInterval(window.__stream));
    const endStream=(room,generation=1)=>room.evaluate(([scope,generation])=>{
      clearInterval(window.__stream);
      window.ContinuumNative.computer({...scope,generation,sequence:100000,active:false,status:'ENDED',reason:'Live-Bild angehalten'});
    },[scope,generation]);
    const cloud={busy:true,presence:'THINKING',cloudBrain:true,status:'Computer-Auftrag läuft'};
    const local={busy:true,presence:'THINKING',status:'Antwort wird berechnet'};
    // Each state is built from the packets Android would send, in their order.
    const states70={
      idle:async room=>{await put(room,{presence:'IDLE'});},
      working:async room=>{
        await put(room,{busy:true,presence:'RESEARCHING',cloudBrain:true,status:'Recherche läuft',activity:steps70});
        await say(room,true,'Welche Förderung gibt es für Wärmepumpen in Oberösterreich?');
        await room.evaluate(packet=>window.ContinuumNative.sources(packet),sources70);
      },
      live:async room=>{
        await put(room,{...cloud,computerWork:{...work70,stage:'VERIFIED',actions:[work70.actions[0]]}});
        await say(room,true,'Öffne den Download-Ordner in Dateien.');
        await startStream(room);
        await put(room,{...cloud,computerWork:work70});
      },
      ended:async room=>{
        await states70.live(room);
        await endStream(room);
        await put(room,{presence:'IDLE',computerWork:{...work70,stage:'COMPLETED',
          actions:work70.actions.map(action=>({...action,state:'VERIFIED'}))}});
        await say(room,false,'Der Download-Ordner ist geöffnet. Darin liegen 14 Dateien.');
      },
      result:async room=>{
        await put(room,{busy:true,presence:'THINKING',cloudBrain:true,status:'Dokument wird gelesen',
          activity:{...steps70,kind:'DOCUMENT',title:'Dokument wird gelesen',detail:'Argon4.pdf · 3 Seiten'}});
        await say(room,true,'Öffne Argon4 und fasse die Messreihe zusammen.');
        await room.evaluate(packet=>window.ContinuumNative.document(packet),{...scope,
          documentId:'33333333-3333-4333-8333-333333333337',name:'Argon4.pdf',pageCount:3,textState:'EXTRACTED',
          text:'Argon4 – Messprotokoll.\nDie Messreihe vom 1. Oktober zeigt stabile Werte.',canDraft:true});
        await room.evaluate(()=>window.ContinuumWorkspaceMotion.whenSettled());
      },
      rest:async room=>{
        await states70.working(room);
        await put(room,{...local,activity:{...steps70,revision:32,kind:'THINK',title:'Denkt nach'}});
        await room.evaluate(packet=>window.ContinuumNative.draft(packet),
          {...scope,text:'Für Oberösterreich gibt es eine Landesförderung; zusätzlich fördert der Bund den Tausch'});
      }};
    const busyStates=new Set(['working','live','result','rest']);
    const devices70=[['phone',412,915],['phone-keyboard',412,480],['fold-closed',344,882],['fold-closed-keyboard',344,460],
      ['fold-closed-rotated',882,344],['fold-open',834,1112],['fold-open-keyboard',834,640],['fold-open-rotated',1112,834]];
    /** Reachability and containment of everything the windows add, at the current size of the same page. */
    const inspect70=(room,busy)=>room.evaluate(busy=>{
      // Every reading follows its scroll in the same task: the live stream replaces its picture several times a
      // second and a picture that arrives or leaves changes the height of the monitor above the composer.
      const failures=[];
      const root=document.documentElement, name=node=>(typeof node?.className==='string' && node.className.split(' ')[0]) || node?.tagName;
      if(root.scrollWidth>root.clientWidth+1)failures.push('root horizontal overflow');
      scrollTo(200,scrollY);
      if(scrollX!==0)failures.push('page can pan horizontally');
      // Chat, Stop (or Send) and Settings: on screen at their own centre, never under a window or a light layer.
      const controls=['#command-input','#listen-button',busy?'.native-stop-button':'.send-button',
        '.header-tools>[data-action="settings"]','.native-setting-shortcut','#close-button'];
      for(const selector of controls) {
        const found=[...document.querySelectorAll(selector)];
        if(!found.length)failures.push(selector+': missing');
        for(const control of found) {
          if(control.hidden || getComputedStyle(control).display==='none') {failures.push(selector+': not shown');continue;}
          control.scrollIntoView({block:'center',inline:'center'});
          const r=control.getBoundingClientRect();
          if(r.width<=0 || r.height<=0) {failures.push(selector+': no box');continue;}
          if(r.x<-1 || r.right>innerWidth+1 || r.y<-1 || r.bottom>innerHeight+1)failures.push(selector+': outside the viewport');
          const top=document.elementFromPoint(r.x+r.width/2,r.y+r.height/2);
          if(top!==control && !control.contains(top))failures.push(selector+': covered by '+name(top));
          else if(!control.matches('#command-input') && (r.width<43.5 || r.height<43.5))failures.push(selector+': smaller than 44px');
        }
      }
      if(busy && getComputedStyle(document.querySelector('.send-button')).display!=='none')failures.push('Send shown beside Stop');
      // Nothing a window adds leaves the workspace sideways or collapses.
      const body=document.querySelector('#workspace').getBoundingClientRect();
      for(const selector of ['#native-live-panel','#native-computer-panel','#native-document-panel','.native-window-frame',
        '#workspace .op-mode-label','.op-window-id','.native-computer-screen','.native-computer-image',
        '.native-computer-marker','.native-computer-work','.native-computer-stage','.native-computer-log li',
        '.native-action-kind','.native-action-state','.native-monitor-record','.native-record-badge',
        '.native-monitor-record>strong','.native-monitor-record>p','#native-live-panel .native-source-link',
        '#native-live-panel .native-source-host','.native-chat-line','.native-draft']) {
        for(const node of document.querySelectorAll(selector)) {
          if(!node.getClientRects().length)continue;
          const r=node.getBoundingClientRect();
          if(r.width<=0 || r.height<=0)failures.push(selector+': collapsed');
          if(r.x<body.x-9 || r.right>body.right+9)failures.push(selector+': outside the work window');
          if(r.x<-1 || r.right>root.clientWidth+1)failures.push(selector+': outside the viewport');
        }
      }
      // The work window never ends under the composer: scrolled to the end, its lower edge is above it.
      scrollTo(0,root.scrollHeight);
      const shell=document.querySelector('.workspace-shell').getBoundingClientRect();
      const composer=document.querySelector('#command-form').getBoundingClientRect();
      if(composer.y<-1 || composer.bottom>innerHeight+1 || composer.x<-1 || composer.right>root.clientWidth+1)
        failures.push('composer outside the viewport at the end of the page');
      if(shell.bottom>composer.top+1 && shell.top<composer.bottom)failures.push('work window ends under the composer by '+
        Math.round(shell.bottom-composer.top)+' px');
      scrollTo(0,0);
      return failures;
    },busy);
    /** The marker's centre against the pixel Android measured, and the image still topmost there. */
    const markerAt=(room,x,y)=>room.evaluate(async([x,y])=>{
      // A marker needs a fresh picture; on a busy host the next frame of the stream may still be on its way.
      const started=performance.now();
      while(!document.querySelector('.native-computer-marker') && performance.now()-started<2000)
        await new Promise(done=>setTimeout(done,40));
      document.querySelector('.native-computer-marker')?.scrollIntoView({block:'center'});
      await new Promise(done=>requestAnimationFrame(()=>requestAnimationFrame(done)));
      // Read in one go: the stream replaces the picture several times a second.
      const dot=document.querySelector('.native-computer-marker'), image=document.querySelector('.native-computer-image');
      if(!dot || !image)return {missing:!dot?'marker':'image'};
      const a=image.getBoundingClientRect(), b=dot.getBoundingClientRect(), cx=b.x+b.width/2, cy=b.y+b.height/2;
      return {dx:cx-(a.x+x*a.width),dy:cy-(a.y+y*a.height),topmost:document.elementFromPoint(cx,cy)===image,
        ring:getComputedStyle(dot,'::after').animationName,state:dot.dataset.state,pointer:getComputedStyle(dot).pointerEvents};
    },[x,y]);
    for(const name of Object.keys(states70)) {
      const room=await openRoom('after',devices70[0][1],devices70[0][2],{presence:'IDLE'});
      await states70[name](room);
      for(const [device,width,height] of devices70) {
        // Rotation, folding and the keyboard change the size of the page that is already showing this state.
        await room.setViewportSize({width,height});
        await room.evaluate(()=>window.dispatchEvent(new Event('resize')));
        await frames(room);
        const item={label:`code70-${name}-${device}-${width}x${height}`,failures:[]};
        if(name==='live') {
          // The marker left after its short time; a new real input brings one for this size.
          await put(room,{...cloud,computerWork:{...work70,actions:[work70.actions[0],{...tap,sequence:6+devices70.findIndex(row=>row[0]===device)+1}]}});
          const at=await markerAt(room,tap.x,tap.y);
          if(at.missing)item.failures.push('no '+at.missing+' in the live window');
          else {
            if(Math.abs(at.dx)>1.5 || Math.abs(at.dy)>1.5)item.failures.push('marker '+at.dx.toFixed(1)+'/'+at.dy.toFixed(1)+' px off the measured pixel');
            if(!at.topmost || at.pointer!=='none')item.failures.push('the marker covers the picture for the visibility check');
          }
          // Target, state and the list of inputs are readable at this size, not only present in the page.
          const readable=await room.evaluate(()=>['.op-window-id','.op-mode-label','.native-computer-stage','.native-computer-log li']
            .map(selector=>{
              const node=document.querySelector('#native-computer-panel '+selector), r=node?.getBoundingClientRect();
              return [selector,node?.textContent ?? null,!!r && r.width>0 && r.height>0];}));
          for(const [selector,text,visible] of readable)
            if(!visible || !text)item.failures.push(selector+': not readable in the live window');
          if(readable[0][1]!=='Dateien · com.google.android.documentsui')item.failures.push('title is '+readable[0][1]);
        }
        item.failures.push(...await inspect70(room,busyStates.has(name)));
        report.checks.push(item);
      }
      await stopStream(room);
      await room.context().close();
    }
    // What the windows say, read from the real page.
    {
      const room=await openRoom('after',834,1112,{presence:'IDLE'});
      await states70.live(room);
      const read=()=>room.evaluate(()=>{
        const panel=document.querySelector('#native-computer-panel'), text=selector=>document.querySelector(selector)?.textContent ?? null;
        return {badge:panel?.querySelector('.op-mode-label').textContent ?? null,state:panel?.dataset.monitorState ?? null,
          title:text('#native-computer-panel .op-window-id'),stage:text('#native-computer-panel .native-computer-stage'),
          log:[...document.querySelectorAll('#native-computer-panel .native-computer-log li')].map(item=>item.textContent),
          image:!!document.querySelector('.native-computer-image'),window:document.body.dataset.nativeWindow,
          record:document.querySelector('.native-monitor-record:not([hidden])')?.innerText ?? null,
          lamp:panel?getComputedStyle(panel.querySelector('.op-browser-status .status-dot')).animationName:null};});
      let shown=await read();
      assert.deepEqual([shown.badge,shown.state,shown.title,shown.stage,shown.image,shown.window],
        ['LIVE','live','Dateien · com.google.android.documentsui','Download-Ordner öffnen · Eingabe ausgelöst',true,'live']);
      assert.deepEqual(shown.log,['ScrollenWirkung bestätigt','Tippenausgelöst']);
      assert.equal(shown.lamp,'native-live-lamp','Full tier, cloud brain, real frames: the live lamp runs');
      // The stream stops delivering. Nothing ended it, so the window stays – and says that the picture is gone.
      await stopStream(room);
      await room.waitForFunction(()=>document.querySelector('#native-computer-panel')?.dataset.monitorState==='stale',null,{timeout:10000});
      shown=await read();
      assert.deepEqual([shown.badge,shown.image],['KEIN FRISCHES BILD',false],'A stale stream is shown as such');
      assert.notEqual(shown.lamp,'native-live-lamp','No live lamp without a live picture');
      // The source delivers again (a new capture generation, as after a real interruption).
      await startStream(room,2);
      assert.equal((await read()).badge,'LIVE');
      await endStream(room,2);
      shown=await read();
      assert.deepEqual([shown.badge,shown.image,shown.window],[null,false,'work'],'An ended source keeps no monitor and no picture');
      assert.match(shown.record,/LIVE-FENSTER\s+BEENDET[\s\S]*Dateien · com\.google\.android\.documentsui[\s\S]*Live-Bild angehalten\. Es wird kein Bild mehr übertragen\./);
      await put(room,{...cloud,computerWork:{consentPending:true}});
      assert.match((await read()).record,/WARTET AUF DEINE FREIGABE/);
      report.code70.windowStates='PASS';
      await room.context().close();
    }
    // Heat. While the on-device model computes, the stage must be the Code69 resting stage: the same looping
    // animations, the same frame limit, the same paint on every panel that existed before, no new canvas, no blur
    // of the background – with every window of this section filled and its events arriving.
    const added=['native-step-settle','native-row-arrive','native-tile-arrive','native-marker-ping','native-live-lamp'];
    const motion=room=>room.evaluate(added=>{
      const label=animation=>{
        const target=animation.effect?.target;
        return (animation.animationName || 'transition')+'@'+((typeof target?.className==='string' && target.className.split(' ')[0]) ||
          target?.tagName)+(animation.effect?.pseudoElement || '');};
      const running=document.getAnimations().filter(animation=>animation.playState==='running');
      const endless=animation=>animation.effect?.getComputedTiming().iterations===Infinity;
      const paint=selector=>{
        const node=document.querySelector(selector);if(!node)return null;
        const style=getComputedStyle(node), moving=style.animationName!=='none';
        // A running animation's momentary opacity and transform depend on the clock; its name is the fact.
        return [style.boxShadow,style.backgroundImage,style.filter,moving?'animated':style.transform,
          moving?'animated':style.opacity,style.animationName,style.transitionProperty];};
      return {endless:[...new Set(running.filter(endless).map(label))].sort(),
        addedRunning:[...new Set(running.filter(animation=>added.includes(animation.animationName)).map(label))].sort(),
        canvases:document.querySelectorAll('canvas').length,
        blurred:[...document.querySelectorAll('body,body *')].filter(node=>{
          const style=getComputedStyle(node);
          return (style.backdropFilter || 'none')!=='none' || (style.webkitBackdropFilter || 'none')!=='none';}).length,
        veil:getComputedStyle(document.querySelector('.native-depth-veil') || document.body).display,
        limit:document.body.dataset.frameLimit,compute:document.body.dataset.nativeCompute,effects:document.body.dataset.nativeEffects,
        paint:Object.fromEntries(['.workspace-shell','#native-live-panel','.native-chat-line.native-owner','.native-draft',
          '.native-transcript','.presence-wing-left .native-instrument','.native-activity-steps li','.native-telemetry-panel',
          '#command-form','.native-orb-light','.native-room-light','.native-holo-field','.core-status','.avatar-crop',
          '.orb-zone','.presence-system'].map(selector=>[selector,paint(selector)]))};
    },added);
    // The same packets for both versions; the Code69 page ignores what it does not know.
    const restStage=async(version,width,height,quality,monitor)=>{
      const room=await openRoom(version,width,height,{presence:'IDLE',quality});
      await put(room,{busy:true,presence:'RESEARCHING',cloudBrain:true,quality,activity:steps70});
      await say(room,true,'Welche Förderung gibt es für Wärmepumpen in Oberösterreich?');
      await room.evaluate(packet=>window.ContinuumNative.sources(packet),sources70);
      await say(room,false,'Es gibt eine Landesförderung.');
      await say(room,true,'Und vom Bund?');
      const computing={...local,quality,activity:{...steps70,revision:33,kind:'THINK',title:'Denkt nach',
        steps:steps70.steps.map((step,index)=>index===2?{...step,state:'DONE'}:index===3?{...step,state:'ACTIVE'}:step)}};
      await put(room,computing);
      if(monitor) {
        await put(room,{...computing,computerWork:{...work70,actions:[work70.actions[0]]}});
        await startStream(room);
        await put(room,{...computing,computerWork:work70});
      } else await room.evaluate(packet=>window.ContinuumNative.draft(packet),{...scope,text:'Der Bund fördert den Tausch'});
      await frames(room);
      // The glass eases into the light of the new state (Code64); the comparison reads the settled paint.
      await room.waitForFunction(()=>!document.getAnimations().some(animation=>
        animation.constructor.name==='CSSTransition' && animation.playState==='running'),null,{timeout:20000});
      return room;
    };
    const literal=['native-corona-breathe@native-orb-light','native-status-breathe@native-instrument-art',
      'status-pulse@status-dot','transfer@I','workspace-side-signal@SPAN'];
    report.code70.rest={};
    for(const [width,height] of [[834,1112],[412,915]]) {
      // Every tier on the unfolded display; the phone in the tier a cool device starts in.
      for(const [quality,limit] of [['FULL','5'],['BALANCED','4'],['REDUCED','3']].filter(([quality])=>width===834 || quality==='FULL')) {
        for(const monitor of [false,true]) {
          const item={label:`code70-rest-${monitor?'monitor':'answer'}-${quality.toLowerCase()}-${width}x${height}`,failures:[]};
          const now=await restStage('after',width,height,quality,monitor), current=await motion(now);
          if(current.compute!=='local' || current.effects!=='rest')item.failures.push('not the resting stage: '+current.compute+'/'+current.effects);
          if(current.limit!==limit)item.failures.push('frame limit '+current.limit+' instead of '+limit);
          if(current.addedRunning.length)item.failures.push('window animations run while the device computes: '+current.addedRunning.join());
          if(current.blurred)item.failures.push(current.blurred+' elements blur their background');
          if(current.veil!=='none')item.failures.push('the depth layer is painted while the device computes');
          // Reduced has no looping light at all; the list is that of Code69 for the tier.
          const expected=quality==='REDUCED'?literal.filter(name=>!name.startsWith('native-corona')):literal;
          if(JSON.stringify(current.endless)!==JSON.stringify(expected))
            item.failures.push('looping animations '+JSON.stringify(current.endless)+' instead of '+JSON.stringify(expected));
          // One event of each kind while computing: a step moves, a draft grows, an input lands. Nothing starts.
          await put(now,{...local,quality,activity:{...steps70,revision:34,kind:'THINK',title:'Denkt nach',
            steps:steps70.steps.map(step=>({...step,state:'DONE'}))},
            ...(monitor?{computerWork:{...work70,actions:[work70.actions[0],{...tap,sequence:9}]}}:{})});
          if(!monitor)await now.evaluate(packet=>window.ContinuumNative.draft(packet),{...scope,text:'Der Bund fördert den Tausch mit einem Zuschuss'});
          const after=await motion(now);
          if(after.addedRunning.length)item.failures.push('an event started '+after.addedRunning.join()+' while the device computes');
          if(JSON.stringify(after.endless)!==JSON.stringify(current.endless))item.failures.push('an event changed the looping animations');
          if(monitor) {
            const at=await markerAt(now,tap.x,tap.y);
            if(at.missing)item.failures.push('the input is not marked while the device computes');
            else if(at.ring!=='none')item.failures.push('the marker ring animates while the device computes');
          }
          if(restFiles) {
            const before=await restStage('rest',width,height,quality,monitor), original=await motion(before);
            if(JSON.stringify(original.endless)!==JSON.stringify(current.endless))
              item.failures.push('looping animations differ from '+restRef+': '+JSON.stringify(original.endless)+' → '+JSON.stringify(current.endless));
            if(original.canvases!==current.canvases)item.failures.push('canvases '+original.canvases+' → '+current.canvases);
            if(original.blurred!==current.blurred)item.failures.push('background blur '+original.blurred+' → '+current.blurred);
            if(original.limit!==current.limit)item.failures.push('frame limit '+original.limit+' → '+current.limit);
            for(const [selector,value] of Object.entries(original.paint))
              if(JSON.stringify(value)!==JSON.stringify(current.paint[selector]))
                item.failures.push(selector+': paint changed while the device computes '+JSON.stringify(value)+' → '+JSON.stringify(current.paint[selector]));
            await stopStream(before);
            await before.context().close();
          }
          report.code70.rest[item.label]={endless:current.endless,limit:current.limit,canvases:current.canvases};
          await stopStream(now);
          await now.context().close();
          report.checks.push(item);
        }
      }
    }
    // Outside the resting stage the added motion follows Android's tier: one finite pass per real event in
    // "full" and "balanced", the live lamp in "full" only, nothing in the reduced tier.
    for(const [quality,tier,finite,lamp] of [['FULL','full',true,true],['BALANCED','balanced',true,false],['REDUCED','light',false,false]]) {
      const item={label:'code70-tier-'+tier,failures:[]};
      const room=await openRoom('after',834,1112,{presence:'IDLE',quality});
      await put(room,{...cloud,quality,activity:steps70,computerWork:{...work70,actions:[work70.actions[0]]}});
      await say(room,true,'Öffne den Download-Ordner in Dateien.');
      await startStream(room);
      const passes=()=>room.evaluate(added=>document.getAnimations().filter(animation=>added.includes(animation.animationName))
        .map(animation=>[animation.animationName,animation.effect.getComputedTiming().iterations,animation.playState]),added);
      // A real input lands on a picture that has just arrived; its ring is read in the call that delivers it.
      const landing=await room.evaluate(async([packet,added])=>{
        const shown=()=>document.querySelector('.native-computer-image')?.dataset.sequence;
        const first=shown(), started=performance.now();
        while(shown()===first && performance.now()-started<8000)await new Promise(done=>setTimeout(done,20));
        window.ContinuumNative.receive(packet);
        return {effects:document.body.dataset.nativeEffects,marker:!!document.querySelector('.native-computer-marker'),
          passes:document.getAnimations().filter(animation=>added.includes(animation.animationName))
            .map(animation=>[animation.animationName,animation.effect.getComputedTiming().iterations])};
      },[{...state,...cloud,quality,activity:steps70,computerWork:work70},added]);
      if(landing.effects!==tier)item.failures.push('tier '+landing.effects+' instead of '+tier);
      if(!landing.marker)item.failures.push('the landed input has no marker');
      const ring=landing.passes.filter(([name])=>name==='native-marker-ping');
      if(finite && (ring.length!==1 || ring[0][1]!==1))item.failures.push('the input ring is not one finite pass: '+JSON.stringify(ring));
      if(!finite && ring.length)item.failures.push('the input ring runs in the reduced tier');
      const lit=landing.passes.filter(([name])=>name==='native-live-lamp');
      if(lamp!==(lit.length===1 && lit[0][1]===Infinity))item.failures.push('live lamp: '+JSON.stringify(lit));
      if(landing.passes.some(([name,iterations])=>name!=='native-live-lamp' && iterations!==1))
        item.failures.push('an event animation is not finite: '+JSON.stringify(landing.passes));
      // A step moves and the answer arrives: one pass each, then nothing of them is left running.
      await endStream(room);
      const arrival=await room.evaluate(([step,message,added])=>{
        window.ContinuumNative.receive(step);window.ContinuumNative.message(message);
        return document.getAnimations().filter(animation=>added.includes(animation.animationName))
          .map(animation=>[animation.animationName,animation.effect.getComputedTiming().iterations]);
      },[{...state,...cloud,quality,activity:{...steps70,revision:35,steps:steps70.steps.map((step,index)=>
        index===2?{...step,state:'DONE'}:index===3?{...step,state:'ACTIVE'}:step)}},
        {...scope,user:false,text:'Der Download-Ordner ist geöffnet.'},added]);
      const names=[...new Set(arrival.map(([name])=>name))].sort();
      if(finite && !(names.includes('native-step-settle') && names.includes('native-row-arrive')))
        item.failures.push('step and answer did not get their one pass: '+names.join());
      if(!finite && names.length)item.failures.push('motion in the reduced tier: '+names.join());
      if(arrival.some(([,iterations])=>iterations!==1))item.failures.push('an arrival is not finite');
      await room.waitForFunction(added=>!document.getAnimations().some(animation=>added.includes(animation.animationName) &&
        animation.playState==='running'),added,{timeout:10000}).catch(()=>item.failures.push('a window animation keeps running'));
      // The owner's switch and the system preference stop every one of them, also in the full tier.
      if(tier==='full') {
        for(const [label,prepare,restore] of [
          ['light motion off',()=>put(room,{...cloud,quality,appearance:{neon:70,lightMotion:false}}),
            ()=>put(room,{...cloud,quality,appearance:{neon:70,lightMotion:true}})],
          ['prefers-reduced-motion',()=>room.emulateMedia({reducedMotion:'reduce'}),()=>room.emulateMedia({reducedMotion:'no-preference'})]]) {
          await prepare();
          await startStream(room,label==='light motion off'?2:3);
          await put(room,{...cloud,quality,appearance:{neon:70,lightMotion:label!=='light motion off'},
            computerWork:{...work70,actions:[work70.actions[0],{...tap,sequence:label==='light motion off'?20:30}]}});
          await say(room,false,'Noch eine Antwort.');
          const still=await passes();
          if(still.length)item.failures.push(label+': '+JSON.stringify(still));
          await endStream(room,label==='light motion off'?2:3);
          await restore();
        }
      }
      await room.context().close();
      report.checks.push(item);
    }
    // Evidence for the owner: the five states on phone and unfolded Fold at twice the density. Host render with
    // synthetic state and a synthetic picture; no device was involved.
    if(shots) {
      for(const [device,width,height] of [['phone',412,915],['fold-open',834,1112]]) {
        for(const [name,file] of [['idle','1-idle'],['working','2-working-with-steps'],['live','3-live-monitor-with-action-marker'],
          ['result','4-result-document'],['rest','5-local-compute-rest']]) {
          const room=await openRoom('after',width,height,{presence:'IDLE'},{scale:2});
          await states70[name](room);
          // A marker stays for a short while only and a large capture takes longer on this host: the same
          // synthetic input is reported again, as a new one, until the picture is taken.
          if(name==='live')await room.evaluate(([packet,tap])=>{
            let sequence=40;
            const land=()=>window.ContinuumNative.receive({...packet,computerWork:{...packet.computerWork,
              actions:[packet.computerWork.actions[0],{...tap,sequence:++sequence}]}});
            land();window.__landing=setInterval(land,800);
          },[{...state,status:'Computer-Auftrag läuft',...cloud,computerWork:work70},tap]);
          // The whole page in one picture: the window is made as tall as the page, the composer stays at its end.
          const tall=await room.evaluate(()=>document.documentElement.scrollHeight);
          await room.setViewportSize({width,height:Math.min(Math.max(height,tall),4200)});
          await room.evaluate(()=>{window.dispatchEvent(new Event('resize'));scrollTo(0,0);});
          await room.waitForTimeout(700);
          await frames(room);
          const path=resolve(shots,`${file}-${device}.png`);
          await room.screenshot({path});
          if(name==='live')await room.evaluate(()=>clearInterval(window.__landing));
          report.code70.shots.push(path);
          await stopStream(room);
          await room.context().close();
        }
      }
    }
    report.code70.result={reachability:'PASS',marker:'PASS',restingStage:restFiles?'PASS':'PASS (literal list; Code69 comparison NOT_RUN)',
      tiers:'PASS'};
  }
  // ───────── Code70 (end) ─────────
  // Heat (Fold7 finding): the stage hands animation frames to the original scene, orb and avatar at most as often
  // as Android's tier and the running work allow. Measured in a real browser. The canvases are not painted here so
  // that this host's software renderer is fast enough for the limit to show at all; the scripts run unchanged.
  {
    const item={label:'code65-frame-limit',failures:[],rates:{}};
    // The first page still draws its full scene in the background and would starve this measurement.
    await context.close();
    const governed=await browser.newContext({viewport:{width:834,height:1112},isMobile:true,hasTouch:true,deviceScaleFactor:1});
    const stage65=await governed.newPage();
    stage65.on('pageerror',error=>errors.push(error.message));
    await stage65.addInitScript(()=>document.addEventListener('DOMContentLoaded',()=>{
      const style=document.createElement('style');style.textContent='canvas{display:none!important}';
      document.head.appendChild(style);}));
    await stage65.goto(`${url}/after/index.android.html`,{waitUntil:'load'});
    await stage65.evaluate(value=>window.ContinuumNative.receive(value),state);
    await stage65.evaluate(()=>{window.__delivered=0;
      const loop=()=>{window.__delivered++;requestAnimationFrame(loop);};requestAnimationFrame(loop);});
    await stage65.waitForTimeout(2000);
    const rate=async values=>{
      await stage65.evaluate(value=>window.ContinuumNative.receive(value),{...state,...values});
      await stage65.waitForTimeout(400);
      const [count,started]=await stage65.evaluate(()=>[window.__delivered,performance.now()]);
      await stage65.waitForTimeout(3000);
      const [after,ended]=await stage65.evaluate(()=>[window.__delivered,performance.now()]);
      return {limit:Number(await stage65.evaluate(()=>document.body.dataset.frameLimit)),
        perSecond:(after-count)/((ended-started)/1000)};
    };
    for(const [name,values,limit] of [
      ['full-idle',{quality:'FULL',presence:'IDLE'},30],
      ['full-speaking',{quality:'FULL',presence:'SPEAKING',speaking:true},30],
      ['full-computing',{quality:'FULL',presence:'THINKING',busy:true},5],
      ['full-computing-cloud',{quality:'FULL',presence:'THINKING',busy:true,cloudBrain:true},15],
      ['balanced-computing',{quality:'BALANCED',presence:'THINKING',busy:true},4],
      ['reduced-idle',{quality:'REDUCED',presence:'IDLE'},10],
      ['reduced-computing',{quality:'REDUCED',presence:'THINKING',busy:true},3]]) {
      const measured=await rate(values);
      item.rates[name]={limit:measured.limit,deliveredPerSecond:Math.round(measured.perSecond*10)/10};
      if(measured.limit!==limit)item.failures.push(name+': limit '+measured.limit+' instead of '+limit);
      // Never more than the limit (one frame of slack per second); the stage keeps moving.
      if(measured.perSecond>limit+1)item.failures.push(name+': '+measured.perSecond.toFixed(1)+' frames/s above '+limit);
      if(measured.perSecond<.5)item.failures.push(name+': the stage stopped drawing');
    }
    // The frame counter of the owner's heat measurement reports what the governor handed on in a real browser:
    // the same frames the counting loop above received, never more than the limit allows.
    await stage65.evaluate(value=>window.ContinuumNative.receive(value),{...state,quality:'FULL',presence:'THINKING',busy:true});
    await stage65.waitForTimeout(400);
    const probeStarted=await stage65.evaluate(()=>{window.__probeBase=window.__delivered;return window.ContinuumNative.frameProbe('start');});
    await stage65.waitForTimeout(3000);
    const probe=await stage65.evaluate(()=>({stats:window.ContinuumNative.frameProbe('stop'),seen:window.__delivered-window.__probeBase,
      after:window.ContinuumNative.frameProbe('read')}));
    if(probeStarted!==true)item.failures.push('frame counter did not start');
    if(probe.stats?.schema!=='continuum-stage-frame-probe-v1')item.failures.push('frame counter returned no statistics');
    else {
      const intervals=probe.stats.histogram.counts.reduce((sum,entry)=>sum+entry[1],0)+probe.stats.histogram.overflow;
      if(Math.abs(probe.stats.delivered-probe.seen)>1)item.failures.push('frame counter: '+probe.stats.delivered+' counted, '+probe.seen+' delivered');
      if(probe.stats.delivered<2 || probe.stats.delivered>5*3.4+1)item.failures.push('frame counter: '+probe.stats.delivered+' frames in about 3 s at a limit of 5');
      if(probe.stats.limit!==5)item.failures.push('frame counter: limit '+probe.stats.limit);
      if(probe.stats.ticks<probe.stats.delivered)item.failures.push('frame counter: fewer display frames than delivered frames');
      if(intervals!==probe.stats.delivered-1)item.failures.push('frame counter: '+intervals+' intervals for '+probe.stats.delivered+' frames');
      if(probe.stats.visibility!=='visible')item.failures.push('frame counter: page visibility '+probe.stats.visibility);
      item.frameCounter={delivered:probe.stats.delivered,seen:probe.seen,displayFrames:probe.stats.ticks,limit:probe.stats.limit};
    }
    if(probe.after!==null)item.failures.push('frame counter kept counting after stop');
    // A cancelled frame is not delivered and a throwing callback does not stop the others.
    const behaviour=await stage65.evaluate(()=>new Promise(resolve=>{
      const seen=[];window.addEventListener('error',event=>{event.preventDefault();seen.push('reported');},{once:true});
      const cancelled=requestAnimationFrame(()=>seen.push('cancelled'));cancelAnimationFrame(cancelled);
      requestAnimationFrame(()=>{throw new Error('synthetic frame failure');});
      requestAnimationFrame(time=>{seen.push(typeof time);setTimeout(()=>resolve(seen),50);});}));
    if(behaviour.includes('cancelled'))item.failures.push('a cancelled frame was delivered');
    if(!behaviour.includes('number'))item.failures.push('the frame after a throwing callback was not delivered with its time');
    if(!behaviour.includes('reported'))item.failures.push('a throwing callback was swallowed');
    errors.splice(0,errors.length,...errors.filter(message=>message!=='synthetic frame failure'));
    report.code65={frameRates:item.rates};
    await governed.close();
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

