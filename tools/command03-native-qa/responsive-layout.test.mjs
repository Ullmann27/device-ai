import assert from 'node:assert/strict';
import {createRequire} from 'node:module';
import {createServer} from 'node:http';
import {readFile, mkdir, writeFile} from 'node:fs/promises';
import {resolve, extname, sep} from 'node:path';
import {fileURLToPath} from 'node:url';

const require = createRequire(import.meta.url);
const {chromium} = require(process.env.COMMAND03_PLAYWRIGHT_MODULE || 'playwright');
const root = resolve(fileURLToPath(new URL('../../app/src/main/assets/continuum-command03/', import.meta.url)));
const output = resolve(process.env.COMMAND03_LAYOUT_OUTPUT || 'command03-layout-evidence');
const baseline = process.env.COMMAND03_BASELINE_CSS;
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
    const relative = decodeURIComponent(url.pathname.replace(/^\/(before|after)\//, ''));
    const path = resolve(root, relative);
    if (!path.startsWith(root + sep)) {res.writeHead(403).end();return;}
    const bytes = await readFile(before && relative==='native-layer.css' && baseline ? baseline : path);
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
  assert.deepEqual(errors, [], 'Unexpected page errors');
  const failures=report.checks.flatMap(check=>check.failures.map(f=>check.label+': '+f));
  console.log(JSON.stringify({baseline:report.baseline?.failures,checks:report.checks.length,failures},null,2));
  assert.deepEqual(failures, [], 'Responsive layout regression');
} finally {
  await writeFile(resolve(output,'layout-results.json'),JSON.stringify(report,null,2)+'\n');
  await browser?.close();
  await new Promise(resolve => server.close(resolve));
}

