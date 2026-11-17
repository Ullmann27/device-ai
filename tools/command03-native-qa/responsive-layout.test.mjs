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
  browser = await chromium.launch({headless:true, args:['--no-sandbox','--enable-unsafe-swiftshader']});
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
  assert.deepEqual(errors, [], 'Unexpected page errors');
  const failures=report.checks.flatMap(check=>check.failures.map(f=>check.label+': '+f));
  console.log(JSON.stringify({baseline:report.baseline?.failures,checks:report.checks.length,failures},null,2));
  assert.deepEqual(failures, [], 'Responsive layout regression');
} finally {
  await writeFile(resolve(output,'layout-results.json'),JSON.stringify(report,null,2)+'\n');
  await browser?.close();
  await new Promise(resolve => server.close(resolve));
}
