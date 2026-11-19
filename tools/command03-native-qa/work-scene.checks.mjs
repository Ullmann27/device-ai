// Code73 · the bust's transition and the work scenes in a real browser engine. Part of responsive-layout.test.mjs
// (which calls workScenes with its browser and server); run on its own for the same checks only:
//   COMMAND03_PLAYWRIGHT_MODULE=… COMMAND03_LAYOUT_OUTPUT=… node tools/command03-native-qa/work-scene.checks.mjs
// Host render with synthetic state. Nothing here measures a device: no frame rate, no temperature, no WebView.
import assert from 'node:assert/strict';
import {createRequire} from 'node:module';
import {createServer} from 'node:http';
import {readFile} from 'node:fs/promises';
import {resolve, extname, sep} from 'node:path';
import {fileURLToPath} from 'node:url';

const GB = 1e9, MB = 1e6;
const categories = [['WHATSAPP', 'WhatsApp-Medien', 1834, 6.42 * GB], ['VIDEO', 'Videos', 212, 4.81 * GB],
  ['LARGE_DOWNLOAD', 'Große Dateien im Download-Ordner', 9, 2.37 * GB], ['DUPLICATE', 'Identische Dateien (Duplikate)', 146, 1.12 * GB],
  ['APK', 'Installationspakete (APK)', 7, 612 * MB], ['OWN_CACHE', 'Eigener Zwischenspeicher von Continuum', 388, 431 * MB],
  ['PARTIAL_DOWNLOAD', 'Abgebrochene Downloads', 4, 188 * MB], ['PREVIEW', 'Vorschaubilder, die Android neu erzeugt', 2210, 96 * MB]]
  .map(([key, label, count, bytes]) => ({key, label, count, bytes: Math.round(bytes)}));
const level = (count, bytes) => ({count, bytes: Math.round(bytes)});
const before = {freeBytes: Math.round(38.6 * GB), totalBytes: 512 * GB, atMs: 1000};
const clean = (phase, extra = {}) => ({schema: 'continuum.deepclean.v1', runId: 'run-7', mode: 'DEEP_CLEAN', phase, before, categories,
  progress: {done: 1460, total: 2602, unit: 'files'},
  levels: {GREEN: level(2602, 715 * MB), YELLOW: level(155, 3.49 * GB), RED: level(204, 11.2 * GB), EXCLUDED: level(41, .2 * GB)},
  after: null, removed: {files: 1460, bytes: Math.round(402 * MB), source: 'sum_of_removed_file_sizes'}, choice: {count: 155, bytes: Math.round(3.49 * GB)},
  sharedAccess: 'MISSING', updatedAtMs: 2000, ...extra});
// The longest texts the mail lane allows: 80 characters per sender, 120 per subject, 200 for the result.
const mail = {version: 1, revision: 9, phase: 'READ', query: 'Rechnung der Werkstatt Huber für den Reifenwechsel im November und die Garantie'.slice(0, 120),
  found: 6, read: 2, progress: {done: 2, total: 6}, result: 'Die Werkstatt Huber meldet: Das Auto ist abholbereit; die Rechnung über den Reifenwechsel ist beigefügt und bis Monatsende zu zahlen. Anna fragt nach Samstag.'.slice(0, 200),
  updatedAtMillis: 5000, cards: Array.from({length: 6}, (_, index) => ({index: index + 1,
    sender: ('Kundendienst der Autowerkstatt Huber & Söhne GmbH, Linz-Urfahr, Filiale ' + index).slice(0, 80),
    subject: ('Re: Ihr Termin zum Reifenwechsel und die Rechnung Nr. 2026-11-' + index + ' – bitte bis Monatsende überweisen, danke').slice(0, 120),
    dateMillis: Date.now() - index * 86400000, state: ['UNREAD', 'READ', 'SELECTED'][index % 3]}))};
const devices = [['phone-301', 301, 650], ['phone-small', 320, 740], ['fold-closed', 344, 882], ['phone', 412, 915],
  ['phone-keyboard', 412, 480], ['fold-closed-keyboard', 344, 460], ['fold-open', 834, 1112], ['fold-open-keyboard', 834, 640],
  ['fold-open-rotated', 1112, 834]];

export async function workScenes({browser, url, state, output = null, errors = [], baseline = false}) {
  const items = [], summary = {bust: {}, motion: {}};
  async function open(width, height, values, {paint = false, version = 'after'} = {}) {
    const context = await browser.newContext({viewport: {width, height}, isMobile: true, hasTouch: true, deviceScaleFactor: 1});
    const room = await context.newPage();
    room.on('pageerror', error => errors.push('code73: ' + error.message));
    // Only the bust checks need the avatar's own picture; elsewhere the canvases stay unpainted (see code71).
    if (!paint) await room.addInitScript(() => document.addEventListener('DOMContentLoaded', () => {
      const style = document.createElement('style');style.textContent = 'canvas{display:none!important}';
      document.head.appendChild(style);}));
    await room.goto(`${url}/${version}/index.android.html`, {waitUntil: 'load', timeout: 120000});
    if (values) await room.evaluate(value => window.ContinuumNative.receive(value), {...state, status: 'Bereit', ...values});
    await room.evaluate(() => document.fonts.ready);
    await room.evaluate(() => {window.dispatchEvent(new Event('resize'));
      return new Promise(done => requestAnimationFrame(() => requestAnimationFrame(done)));});
    return room;
  }
  const put = (room, values) => room.evaluate(packet => window.ContinuumNative.receive(packet), {...state, status: 'Bereit', ...values});
  const settle = room => room.evaluate(async () => {
    for (let round = 0; round < 6; round++) {
      const running = document.getAnimations().filter(animation => animation.playState !== 'finished' &&
        animation.effect?.getComputedTiming().iterations !== Infinity && animation.constructor.name !== 'CSSTransition');
      if (!running.length) break;
      running.forEach(animation => {try {animation.finish();} catch {}});
      await new Promise(done => requestAnimationFrame(() => requestAnimationFrame(done)));
    }
  });

  // ── 1 · the bust: the figure alone, read back with its alpha; no hard lower edge at any size ──
  /**
   * The original avatar's frame alone (everything else invisible, transparent page), as the browser composites it with
   * the mask. Per column of the figure below the neck: the steepest fall of opacity over 3 px. The cut of the model is
   * such a fall (opaque → nothing) along a horizontal line; the dissolve has none.
   */
  async function bustProfile(room) {
    await room.waitForFunction(() => window.ContinuumAvatar?.getStatus().renderer === 'original-webgl', null, {timeout: 90000})
      .catch(() => {});
    const renderer = await room.evaluate(() => window.ContinuumAvatar?.getStatus().renderer);
    await room.addStyleTag({content: 'html,body{background:transparent!important}body *{visibility:hidden!important}' +
      '.avatar-crop,.avatar-crop *{visibility:visible!important}#scene-canvas{display:none!important}'});
    await room.evaluate(() => {document.querySelector('.avatar-crop').scrollIntoView({block: 'center'});
      return new Promise(done => requestAnimationFrame(() => requestAnimationFrame(done)));});
    await room.waitForTimeout(400);
    const box = await room.evaluate(() => {
      const crop = document.querySelector('.avatar-crop'), r = crop.getBoundingClientRect(), zone = crop.closest('.avatar-zone');
      const percent = name => parseFloat(zone.style.getPropertyValue(name)) / 100;
      return {x: r.x, y: r.y, width: r.width, height: r.height, neck: percent('--bust-neck'), cut: percent('--bust-cut')};
    });
    const clip = {x: Math.max(0, Math.floor(box.x)), y: Math.max(0, Math.floor(box.y)), width: Math.floor(box.width), height: Math.floor(box.height)};
    const png = await room.screenshot({clip, omitBackground: true});
    return {renderer, box, ...await room.evaluate(async ([data, box]) => {
      const image = new Image();image.src = 'data:image/png;base64,' + data;await image.decode();
      const canvas = document.createElement('canvas');canvas.width = image.width;canvas.height = image.height;
      const context = canvas.getContext('2d');context.drawImage(image, 0, 0);
      const {data: pixels, width, height} = context.getImageData(0, 0, image.width, image.height);
      const alpha = (x, y) => pixels[(y * width + x) * 4 + 3] / 255;
      // Without the measured custom properties (the baseline has none) the model's plane is computed the same way.
      const aspect = box.width / box.height, half = .225 * Math.max(1, (.6 / .45) / aspect);
      const neckRow = Math.round(height * (Number.isFinite(box.neck) ? box.neck : (.215 + half - .15) / (2 * half)));
      const cutRow = Math.round(height * (Number.isFinite(box.cut) ? box.cut : (.215 + half) / (2 * half)));
      let figure = 0, hard = 0;
      const rows = [];
      for (let x = 0; x < width; x++) {
        let any = false, drop = 0, at = -1;
        for (let y = neckRow; y < height - 3; y++) {
          if (alpha(x, y) > .5) any = true;
          const fall = alpha(x, y) - alpha(x, y + 3);
          if (fall > drop) {drop = fall;at = y;}
        }
        if (!any) continue;
        figure++;
        if (drop > .5) {hard++;rows.push(at);}
      }
      rows.sort((a, b) => a - b);
      // Opacity left on the model's own lower plane (the line where the cut was).
      let onCut = 0;
      for (let y = Math.max(0, cutRow - 2); y <= Math.min(height - 1, cutRow); y++)
        for (let x = 0; x < width; x++) onCut = Math.max(onCut, alpha(x, y));
      return {figureColumns: figure, hardColumns: hard, hardShare: figure ? hard / figure : 0,
        hardRowSpread: rows.length ? rows.at(-1) - rows[0] : null, onCut: Math.round(onCut * 1000) / 1000, neckRow, cutRow};
    }, [png.toString('base64'), box])};
  }
  for (const [name, width, height] of [['phone-301', 301, 650], ['phone-small', 320, 740], ['fold-closed', 344, 882], ['phone', 412, 915],
    ['phone-keyboard', 412, 480], ['fold-open', 834, 1112], ['fold-open-rotated', 1112, 834]]) {
    const item = {label: `code73-bust-${name}-${width}x${height}`, failures: []};
    const room = await open(width, height, {presence: 'IDLE', cloudBrain: true, quality: 'FULL'}, {paint: true});
    const now = await bustProfile(room);
    await room.context().close();
    summary.bust[name] = {after: now};
    if (now.renderer !== 'original-webgl') item.failures.push('the original avatar did not draw (renderer ' + now.renderer + '): nothing measured');
    else {
      if (now.figureColumns < 40) item.failures.push('no figure below the neck: ' + now.figureColumns + ' columns');
      if (now.hardShare > .05) item.failures.push('hard lower edge in ' + Math.round(now.hardShare * 100) + ' % of the figure (' + now.hardColumns + ' columns)');
      if (now.onCut > .06) item.failures.push('the model\'s lower plane is still visible (opacity ' + now.onCut + ')');
      // The measure itself: the same frame without the dissolve (the Code64 baseline) shows the cut.
      if (baseline && (name === 'phone' || name === 'fold-open')) {
        const old = await open(width, height, {presence: 'IDLE', cloudBrain: true, quality: 'FULL'}, {paint: true, version: 'base'});
        const then = await bustProfile(old);
        await old.context().close();
        summary.bust[name].baseline = then;
        if (then.renderer === 'original-webgl' && then.hardShare < .5)
          item.failures.push('the measure does not see the cut of the baseline (' + Math.round(then.hardShare * 100) + ' %)');
      }
    }
    items.push(item);
  }

  // ── 2 · scenes at every size: inside the viewport, nothing cut, controls reachable above the composer ──
  const scenes = [['clean', {busy: true, cloudBrain: true, presence: 'THINKING', deepClean: clean('CLEAN'), scene: {kind: 'deepclean', since: 1}}],
    ['choice', {cloudBrain: true, presence: 'WAITING_FOR_APPROVAL', deepClean: clean('CHOICE', {progress: {done: 2602, total: 2602}}), scene: {kind: 'deepclean', since: 1}}],
    ['done', {cloudBrain: true, presence: 'IDLE', deepClean: clean('DONE', {progress: {done: 2602, total: 2602}, choice: {count: 0, bytes: 0},
      after: {freeBytes: Math.round(39.33 * GB), totalBytes: 512 * GB, atMs: 9000}, removed: {files: 2602, bytes: Math.round(715 * MB)}}), scene: {kind: 'deepclean', since: 1}}],
    ['mail', {busy: true, cloudBrain: true, presence: 'THINKING', mailWork: mail, scene: {kind: 'mail', since: 1}}]];
  for (const [device, width, height] of devices) {
    const room = await open(width, height, {presence: 'IDLE', cloudBrain: true, quality: 'FULL', tactical: {brain: 'CLAUDE', battery: 84}});
    for (const [name, values] of scenes) {
      const item = {label: `code73-scene-${name}-${device}-${width}x${height}`, failures: []};
      await put(room, {tactical: {brain: 'CLAUDE', battery: 84}, quality: 'FULL', ...values});
      await settle(room);
      const value = await room.evaluate(() => {
        const scene = document.querySelector('#native-scene');
        if (!scene) return null;
        const box = node => {const r = node.getBoundingClientRect();return [r.left, r.top, r.right, r.bottom];};
        const cut = [...scene.querySelectorAll('*')].filter(node => !node.closest('[hidden]') && node.children.length === 0 &&
          node.textContent && node.scrollWidth > node.clientWidth + 1 && getComputedStyle(node).overflow !== 'visible')
          .map(node => node.className);
        return {viewport: document.documentElement.clientWidth, page: document.documentElement.scrollWidth, scene: box(scene),
          sceneScroll: scene.scrollWidth - scene.clientWidth, cut,
          // Every part the bridge filled (and did not hide) is really drawn: no original rule may swallow it.
          swallowed: [...scene.querySelectorAll('*')].filter(node => !node.closest('[hidden]') && node.children.length === 0 &&
            node.textContent.trim() && node.getBoundingClientRect().height === 0).map(node => node.className),
          wide: [...scene.querySelectorAll('*')].filter(node => !node.closest('[hidden]') && node.getBoundingClientRect().width > 0 &&
            (node.getBoundingClientRect().right > scene.getBoundingClientRect().right + 1 || node.getBoundingClientRect().left < scene.getBoundingClientRect().left - 1))
            .map(node => node.className).slice(0, 5),
          controls: [...scene.querySelectorAll('button')].map(button => [button.className, button.getBoundingClientRect().width, button.getBoundingClientRect().height])};
      });
      if (!value) {item.failures.push('no scene');items.push(item);continue;}
      if (value.page > value.viewport + 1) item.failures.push('root horizontal overflow ' + value.page + ' > ' + value.viewport);
      if (value.scene[0] < -1 || value.scene[2] > value.viewport + 1) item.failures.push('scene outside the viewport ' + JSON.stringify(value.scene.map(Math.round)));
      if (value.sceneScroll > 1) item.failures.push('scene content wider than the scene by ' + value.sceneScroll + ' px');
      if (value.wide.length) item.failures.push('parts stick out of the window: ' + value.wide.join(', '));
      if (value.cut.length) item.failures.push('text cut: ' + value.cut.join(', '));
      if (value.swallowed.length) item.failures.push('filled but not drawn: ' + value.swallowed.join(', '));
      for (const [name, w, h] of value.controls) if (w < 44 || h < 44) item.failures.push(name + ' smaller than 44 px: ' + Math.round(w) + '×' + Math.round(h));
      // Every control and the last line of the window can be brought into view above the composer and is topmost there.
      const reach = await room.evaluate(() => {
        const form = document.querySelector('#command-form').getBoundingClientRect();
        const targets = [...document.querySelectorAll('#native-scene button'), document.querySelector('#native-scene .native-scene-foot')];
        return targets.map(node => {
          node.scrollIntoView({block: 'center'});
          const r = node.getBoundingClientRect(), x = r.left + r.width / 2, y = r.top + Math.min(r.height / 2, 20);
          const hit = document.elementFromPoint(x, y);
          const composer = document.querySelector('#command-form').getBoundingClientRect();
          return [node.className, !!hit && (hit === node || node.contains(hit)), composer.top > form.top - 1 ? Math.round(composer.top - r.bottom) : null];
        });
      });
      for (const [name, topmost] of reach) if (!topmost) item.failures.push(name + ' is covered (composer or another layer) when brought into view');
      if (output) {
        await room.evaluate(() => document.querySelector('#native-scene').scrollIntoView({block: 'start'}));
        await room.screenshot({path: resolve(output, `${item.label}.png`)});
      }
      items.push(item);
    }
    await room.context().close();
  }

  // ── 3 · tiers: what moves when a scene opens and a card arrives ──
  for (const [tier, values, moves] of [['rest', {busy: true, presence: 'THINKING'}, false],
    ['light', {busy: true, cloudBrain: true, presence: 'THINKING', quality: 'REDUCED'}, false],
    ['balanced', {busy: true, cloudBrain: true, presence: 'THINKING', quality: 'BALANCED'}, true],
    ['full', {busy: true, cloudBrain: true, presence: 'THINKING', quality: 'FULL'}, true]]) {
    for (const [width, height] of [[412, 915], [834, 1112]]) {
      const item = {label: `code73-tier-${tier}-${width}x${height}`, failures: []};
      const room = await open(width, height, values);
      const read = packet => room.evaluate(packet => {
        window.ContinuumNative.receive(packet);
        const own = document.getAnimations().filter(animation => animation.animationName?.startsWith('native-scene-') ||
          (animation.constructor.name === 'Animation' && animation.effect?.target?.closest?.('#native-scene')));
        return {effects: document.body.dataset.nativeEffects, names: [...new Set(own.map(animation => animation.animationName || 'glide'))].sort(),
          endless: own.filter(animation => animation.effect?.getComputedTiming().iterations === Infinity).length,
          properties: [...new Set(own.flatMap(animation => animation.effect.getKeyframes().flatMap(frame => Object.keys(frame)))
            .filter(key => !['offset', 'easing', 'composite', 'computedOffset'].includes(key)))].sort(),
          marks: document.querySelectorAll('#native-scene[data-native-enter],#native-scene [data-native-enter],#native-scene [data-changed]').length,
          cards: document.querySelectorAll('.native-scene-card').length};
      }, {...state, status: 'Bereit', ...packet});
      const opened = await read({...values, deepClean: clean('SCAN', {categories: categories.slice(0, 2), progress: {done: 300, total: 0}}), scene: {kind: 'deepclean', since: 1}});
      const grew = await read({...values, deepClean: clean('CLEAN'), scene: {kind: 'deepclean', since: 1}});
      summary.motion[item.label] = {opened, grew};
      for (const [moment, value] of [['open', opened], ['cards', grew]]) {
        if (value.endless) item.failures.push(moment + ': a loop runs');
        if (value.properties.some(property => property !== 'transform' && property !== 'opacity'))
          item.failures.push(moment + ': animates ' + value.properties.join(','));
        if (moves && !value.names.length) item.failures.push(moment + ': nothing moves in the ' + tier + ' tier');
        if (!moves && (value.names.length || value.marks)) item.failures.push(moment + ': moves while it must rest: ' + value.names.join(','));
      }
      if (tier === 'rest' && opened.effects !== 'rest') item.failures.push('not the resting stage: ' + opened.effects);
      if (grew.cards !== 8) item.failures.push('final content incomplete: ' + grew.cards + ' cards');
      items.push(item);
      await room.context().close();
    }
  }
  return {items, summary};
}

// Run on its own.
if (process.argv[1] === fileURLToPath(import.meta.url)) {
  const require = createRequire(import.meta.url);
  const {execFileSync} = await import('node:child_process');
  const {chromium} = require(process.env.COMMAND03_PLAYWRIGHT_MODULE || 'playwright');
  const root = resolve(fileURLToPath(new URL('../../app/src/main/assets/continuum-command03/', import.meta.url)));
  const baselineRef = process.env.COMMAND03_BASELINE_REF || '823458185';
  let base = null;
  try {
    base = Object.fromEntries(['native-bridge.js', 'native-layer.css'].map(name => [name, execFileSync('git',
      ['show', `${baselineRef}:app/src/main/assets/continuum-command03/${name}`], {cwd: resolve(root, '../../../../..'), maxBuffer: 8 << 20})]));
  } catch {}
  const mime = {'.html': 'text/html', '.css': 'text/css', '.js': 'application/javascript', '.png': 'image/png', '.ttf': 'font/ttf', '.glb': 'model/gltf-binary'};
  const server = createServer(async (req, res) => {
    try {
      const pathname = new URL(req.url, 'http://localhost').pathname;
      const relative = decodeURIComponent(pathname.replace(/^\/(after|base)\//, ''));
      const path = resolve(root, relative);
      if (!path.startsWith(root + sep)) {res.writeHead(403).end();return;}
      const bytes = pathname.startsWith('/base/') && base?.[relative] ? base[relative] : await readFile(path);
      res.writeHead(200, {'Content-Type': mime[extname(path)] || 'application/octet-stream', 'Cache-Control': 'no-store'}).end(bytes);
    } catch {res.writeHead(404).end();}
  });
  await new Promise(done => server.listen(0, '127.0.0.1', done));
  const scope = {sessionId: '12345678-1234-1234-1234-123456789abc', pageId: '11111111-1111-4111-8111-111111111111',
    viewId: '22222222-2222-4222-8222-222222222222', viewRevision: 1};
  const state = {...scope, draftRevision: 1, ackEditRevision: 0, viewKind: 'CENTRAL', viewLabel: 'JARVIS', cpu: 12, ram: 84,
    telemetryAtElapsedMs: 100, chats: [], workRows: []};
  const browser = await chromium.launch({executablePath: process.env.COMMAND03_CHROMIUM_PATH || undefined, headless: true,
    args: ['--no-sandbox', '--enable-unsafe-swiftshader']});
  const errors = [];
  try {
    const {items, summary} = await workScenes({browser, url: `http://127.0.0.1:${server.address().port}`, state, errors, baseline: !!base,
      output: process.env.COMMAND03_LAYOUT_OUTPUT ? resolve(process.env.COMMAND03_LAYOUT_OUTPUT) : null});
    const failures = items.flatMap(item => item.failures.map(failure => item.label + ': ' + failure));
    console.log(JSON.stringify({checks: items.length, bust: summary.bust, failures, errors}, null, 2));
    assert.deepEqual(errors, [], 'Unexpected page errors');
    assert.deepEqual(failures, [], 'Work scene regression');
  } finally {
    await browser.close();
    await new Promise(done => server.close(done));
  }
}
