// Code71 · tactical command centre in a real browser engine: the status strip, the order rail, the start-up check,
// the info cards and the window hand-over. Part of responsive-layout.test.mjs (which calls tacticalStage with its
// browser and server); run on its own for the same checks only:
//   COMMAND03_PLAYWRIGHT_MODULE=… node tools/command03-native-qa/tactical-stage.checks.mjs
// Host render with synthetic state. Nothing here measures a device: no frame rate, no temperature.
import assert from 'node:assert/strict';
import {createRequire} from 'node:module';
import {createServer} from 'node:http';
import {readFile} from 'node:fs/promises';
import {resolve, extname, sep} from 'node:path';
import {fileURLToPath} from 'node:url';

const tactical = (extra = {}) => ({brain:'CLAUDE', internet:'ALWAYS_ON', thermal:0, battery:84, charging:false, ...extra});
// The longest texts Android's bounds allow: 80 characters per step, 60/90 per card, 40/120 per check.
const longStep = number => ('Schritt ' + number + ': Rechnung der Werkstatt im Ordner Belege suchen und mit dem Kalender abgleichen und prüfen').slice(0, 80);
const mission = (revision, items, extra = {}) => ({revision, source:'MISSION', key:'m3', title:'', total:8, active:true, outcome:null,
  items:items.map(([number, state, label]) => ({key:'m' + (number - 1), number, label:label || longStep(number), state})), ...extra});
const turn = (revision, key, items, extra = {}) => ({revision, source:'TURN', key, title:'Wie wird das Wetter morgen in Linz?',
  total:items.length, active:true, outcome:null,
  items:items.map(([number, state, label]) => ({key:'t' + label, number, label, state})), ...extra});
const checks = states => [['brain', 'Gehirn', 'Claude ausgewählt'], ['voice', 'Stimme und Sprachausgabe des Geräts', 'Sprachausgabe nicht bereit – die Stimme wurde in den Android-Einstellungen noch nicht heruntergeladen oder ist stumm'],
  ['memory', 'Gedächtnis', 'Chatverlauf geladen'], ['internet', 'Internet', 'Nur auf Nachfrage'],
  ['tools', 'Gerätewerkzeuge', 'Bereit'], ['screen', 'Bildschirmfreigabe', '']]
  .map(([key, label, detail], index) => ({key, label, state:states[index], detail}));
const card = (id, kind, state = 'info') => ({id, kind, state,
  title:('Heute und morgen im Kalender der Familie Ullmann, Werkstatt').slice(0, 60),
  lines:Array.from({length:4}, (_, index) => ((index + 1) + ' · 14:30 Werkstatt Reifenwechsel, danach Zahnarzt Dr. Huber in der Landstraße und Einkauf').slice(0, 90))});
const cards = [card('cal', 'calendar'), card('weather', 'weather'), card('msg', 'messages', 'attention'), card('tasks', 'tasks'),
  card('news', 'news'), card('sys', 'system')];
const devices = [['phone', 412, 915], ['phone-small', 320, 740], ['phone-keyboard', 412, 480], ['fold-closed', 344, 882],
  ['fold-closed-keyboard', 344, 460], ['fold-closed-rotated', 882, 344], ['fold-open', 834, 1112], ['fold-open-keyboard', 834, 640],
  ['fold-open-rotated', 1112, 834]];

export async function tacticalStage({browser, url, state, scope, output = null, errors = []}) {
  const items = [], summary = {};
  async function open(width, height, values, options = {}) {
    const context = await browser.newContext({viewport:{width, height}, isMobile:true, hasTouch:true, deviceScaleFactor:1,
      reducedMotion:options.reducedMotion || 'no-preference'});
    const room = await context.newPage();
    room.on('pageerror', error => errors.push('code71: ' + error.message));
    // The scene, orb and avatar canvases are not painted here: these checks read the regions above the stage, and
    // this host's software renderer would otherwise need most of its time and memory for pictures nobody reads.
    await room.addInitScript(() => document.addEventListener('DOMContentLoaded', () => {
      const style = document.createElement('style');style.textContent = 'canvas{display:none!important}';
      document.head.appendChild(style);}));
    await room.goto(`${url}/after/index.android.html`, {waitUntil:'load', timeout:120000});
    // `values` null: the page is loaded and the first snapshot is left to the caller.
    if (values) await room.evaluate(value => window.ContinuumNative.receive(value), {...state, status:'Bereit', ...values});
    await room.evaluate(() => document.fonts.ready);
    await room.evaluate(() => {window.dispatchEvent(new Event('resize'));
      return new Promise(done => requestAnimationFrame(() => requestAnimationFrame(done)));});
    return room;
  }
  const put = (room, values) => room.evaluate(packet => window.ContinuumNative.receive(packet), {...state, status:'Bereit', ...values});
  const frames = room => room.evaluate(() => new Promise(done => requestAnimationFrame(() => requestAnimationFrame(done))));
  /**
   * What moves right now: the section's CSS passes and loops, and the glides of a layout change (FLIP). With a
   * packet (or a control to press) the event and its reading happen in one task of the page: no time passes
   * between them, so a short pass cannot be over before it is read on a slow host.
   */
  const moving = (room, event = null) => room.evaluate(async event => {
    if (event?.packet) window.ContinuumNative.receive(event.packet);
    if (event?.press) document.querySelector(event.press).click();
    // The hand-over between windows is decided at the end of the task that changed them.
    if (event) await Promise.resolve();
    const label = animation => {
      const target = animation.effect?.target;
      return (animation.animationName || 'glide') + '@' + ((typeof target?.className === 'string' && target.className.split(' ').at(-1)) ||
        target?.tagName) + (animation.effect?.pseudoElement || '');};
    const all = document.getAnimations().filter(animation => animation.playState === 'running' || animation.playState === 'pending');
    // The glides of this section add their offset to whatever the element already has (composite "add").
    const own = all.filter(animation => animation.animationName ? animation.animationName.startsWith('native-tac-') :
      animation.constructor.name === 'Animation' && animation.effect?.composite === 'add');
    const endless = animation => animation.effect?.getComputedTiming().iterations === Infinity;
    return {passes:own.filter(animation => !endless(animation)).map(label).sort(), loops:[...new Set(own.filter(endless).map(label))].sort(),
      properties:[...new Set(own.flatMap(animation => animation.effect.getKeyframes().flatMap(frame => Object.keys(frame)))
        .filter(key => !['offset', 'easing', 'composite', 'computedOffset'].includes(key)))].sort(),
      count:own.length, total:all.length,
      plates:[...document.querySelectorAll('.native-window-ghost-name')].map(node => node.textContent),
      marks:[...document.querySelectorAll('[data-native-enter],[data-native-leaving],.native-tac [data-changed],.native-window-ghost')]
        .map(node => node.className.split(' ').at(-1)).sort()};
  }, event);
  const sending = values => ({packet:{...state, status:'Bereit', ...values}});
  /** Jumps every finite animation to its end – the state the page must also have without any animation. */
  const settle = room => room.evaluate(async () => {
    for (let round = 0; round < 8; round++) {
      const finite = document.getAnimations().filter(animation => animation.constructor.name !== 'CSSTransition' &&
        animation.playState !== 'finished' && animation.effect?.getComputedTiming().iterations !== Infinity);
      if (!finite.length) break;
      for (const animation of finite) {try {animation.finish();} catch {}}
      await new Promise(done => requestAnimationFrame(() => requestAnimationFrame(done)));
    }
    await window.ContinuumWorkspaceMotion?.whenSettled?.();
  });
  /** The regions as text and boxes: what must be the same with and without animation. */
  const picture = room => room.evaluate(() => {
    scrollTo(0, 0);
    const box = node => {const r = node.getBoundingClientRect();return [r.x, r.y + scrollY, r.width, r.height].map(Math.round);};
    const read = selector => [...document.querySelectorAll(selector)].map(node => ({is:node.id || node.className.split(' ').at(-1),
      text:node.textContent, box:box(node), state:node.dataset.state || node.dataset.orderState || node.dataset.mode || null}));
    return {regions:read('#native-tac-hud,#native-tac-strip,#native-arming,#native-order-rail,#native-briefing'),
      parts:read('.native-tac-cell,.native-order-card,.native-order-chip,.native-arming-row,.native-brief-card'),
      stage:read('.center-stage>.presence,.presence-core,.workspace-shell,#command-form').map(entry => entry.box),
      window:document.body.dataset.nativeWindow || null, ghosts:document.querySelectorAll('.native-window-ghost').length,
      marks:document.querySelectorAll('[data-native-enter],[data-native-leaving]').length,
      attention:document.body.dataset.nativeAttention || null};
  });
  /** The first place where two pictures differ. */
  const difference = (a, b, path = '') => {
    if (JSON.stringify(a) === JSON.stringify(b)) return '';
    if (a === null || b === null || typeof a !== 'object' || typeof b !== 'object') return path + ': ' + JSON.stringify(a) + ' ≠ ' + JSON.stringify(b);
    for (const key of new Set([...Object.keys(a), ...Object.keys(b)])) {
      const found = difference(a[key] ?? null, b[key] ?? null, path + '/' + key);
      if (found) return found;
    }
    return path + ': differs';
  };
  /** Geometry, legibility and reachability of the regions at the current size of the page. */
  const inspect = (room, expectations) => room.evaluate(expectations => {
    const failures = [], root = document.documentElement;
    const name = node => (typeof node?.className === 'string' && node.className.split(' ').at(-1)) || node?.id || node?.tagName;
    scrollTo(0, 0);
    if (root.scrollWidth > root.clientWidth + 1) failures.push('root horizontal overflow');
    scrollTo(200, scrollY);
    if (scrollX !== 0) failures.push('page can pan horizontally');
    const column = document.querySelector('.center-stage').getBoundingClientRect();
    const rect = selector => document.querySelector(selector)?.getBoundingClientRect() ?? null;
    const order = ['#native-tac-hud', '#native-order-rail', '#native-briefing', '.center-stage>.presence'];
    for (const selector of expectations.regions) {
      const r = rect(selector);
      if (!r) {failures.push(selector + ': missing');continue;}
      if (r.width <= 0 || r.height <= 0) failures.push(selector + ': collapsed');
      if (r.x < column.x - 1 || r.right > column.right + 1) failures.push(selector + ': outside the stage column');
      if (r.x < -1 || r.right > root.clientWidth + 1) failures.push(selector + ': outside the viewport');
    }
    // One below the other, never over each other and never over the stage.
    const present = order.map(selector => [selector, rect(selector)]).filter(([, r]) => r);
    for (let index = 1; index < present.length; index++)
      if (present[index - 1][1].bottom > present[index][1].top + 1)
        failures.push(present[index - 1][0] + ' overlaps ' + present[index][0]);
    // Every part stays inside its region and every text inside its part; nothing is cut sideways.
    const deck = document.querySelector('#native-briefing'), swipes = deck && deck.scrollWidth > deck.clientWidth + 1;
    for (const [selector, host] of [['.native-tac-cell', '#native-tac-strip'], ['.native-order-head', '#native-order-rail'],
      ['.native-order-card', '#native-order-rail'], ['.native-order-chip', '#native-order-rail'],
      ['.native-arming-row', '#native-arming'], ['.native-arming-verdict', '#native-arming'], ['.native-brief-card', '#native-briefing']]) {
      const outer = rect(host);
      for (const node of document.querySelectorAll(selector)) {
        if (!node.getClientRects().length) continue;
        const r = node.getBoundingClientRect();
        if (r.width <= 0 || r.height <= 0) failures.push(selector + ': collapsed');
        // On a phone the info cards are a row that is swiped: a card may lie beside the visible part, never outside its row.
        if (!(swipes && selector === '.native-brief-card') && (r.x < outer.x - 1 || r.right > outer.right + 1))
          failures.push(selector + ': outside ' + host);
        if (r.y < outer.y - 1 || r.bottom > outer.bottom + 1) failures.push(selector + ': below or above ' + host);
        if (node.scrollWidth > node.clientWidth + 1) failures.push(selector + ': content wider than its box');
        for (const text of node.querySelectorAll('span,strong,small,li,p')) {
          if (!text.getClientRects().length || !text.textContent.trim()) continue;
          const t = text.getBoundingClientRect();
          if (t.x < r.x - 1 || t.right > r.right + 1) failures.push(name(text) + ': text outside ' + selector);
          if (parseFloat(getComputedStyle(text).fontSize) < 10) failures.push(name(text) + ': smaller than 10px');
        }
      }
    }
    if (swipes && getComputedStyle(deck).overflowX !== 'auto') failures.push('info cards overflow without being a row to swipe');
    // What can be pressed is large enough and on top at its own centre.
    for (const control of document.querySelectorAll('[data-native-tac]')) {
      control.scrollIntoView({block:'center', inline:'center'});
      const r = control.getBoundingClientRect();
      if (r.width < 43.5 || r.height < 43.5) failures.push(name(control) + ': smaller than 44px');
      const top = document.elementFromPoint(r.x + r.width / 2, r.y + r.height / 2);
      if (top !== control && !control.contains(top)) failures.push(name(control) + ': covered by ' + name(top));
    }
    // Bringing a cell into view moves the page, never the row of cells inside the strip.
    for (const selector of ['#native-tac-strip', '#native-order-rail', '#native-arming']) {
      const box = document.querySelector(selector);
      if (box && (box.scrollLeft !== 0 || box.scrollTop !== 0)) failures.push(selector + ': scrolled inside itself');
    }
    // The rail stays in view while the page scrolls – and then leaves the composer, Stop and the work window alone.
    const rail = document.querySelector('#native-order-rail'), composer = document.querySelector('#command-form');
    if (rail) {
      scrollTo(0, 0);
      const sticky = getComputedStyle(rail).position === 'sticky';
      if (sticky !== expectations.sticky) failures.push('rail position ' + getComputedStyle(rail).position);
      document.querySelector('.workspace-shell').scrollIntoView({block:'start'});
      const r = rail.getBoundingClientRect(), c = composer.getBoundingClientRect();
      const fixed = getComputedStyle(composer).position === 'fixed';
      if (sticky && scrollY > 0) {
        if (r.top < -1 || r.top > 1) failures.push('the rail does not stay at the top while scrolling: ' + Math.round(r.top));
        if (r.height > innerHeight * .5) failures.push('the rail covers ' + Math.round(r.height) + ' of ' + innerHeight + ' px');
        if (fixed && r.bottom > c.top - 1) failures.push('the rail reaches the composer');
        if (fixed && c.top - r.bottom < 160) failures.push('only ' + Math.round(c.top - r.bottom) + ' px stay between rail and composer');
      }
      for (const selector of ['#command-input', '.native-stop-button', '#listen-button']) {
        const control = document.querySelector(selector);
        if (!control || control.hidden) continue;
        if (!fixed) control.scrollIntoView({block:'center'});
        const b = control.getBoundingClientRect(), top = document.elementFromPoint(b.x + b.width / 2, b.y + b.height / 2);
        if (b.y < -1 || b.bottom > innerHeight + 1) failures.push(selector + ': outside the viewport');
        if (top !== control && !control.contains(top)) failures.push(selector + ': covered by ' + name(top));
      }
    }
    scrollTo(0, 0);
    return failures;
  }, expectations);
  const everything = {tactical:tactical(), busy:true, cloudBrain:true, presence:'THINKING', systemCheck:checks(['OK', 'FAIL', 'OK', 'WARN', 'OK', 'OFF']),
    briefingCards:cards, orders:mission(1, [[1, 'DONE'], [2, 'DONE'], [3, 'FAILED'], [4, 'STOPPED'], [5, 'SKIPPED'], [6, 'ACTIVE'], [7, 'WAITING'], [8, 'ACTIVE']])};

  // 1 · Every size: phone, Fold closed, Fold open, keyboard open, rotated – with the fullest regions Android can send.
  {
    const room = await open(412, 915, everything);
    await room.locator('[data-native-tac="systems"]').click();
    for (const [device, width, height] of devices) {
      await room.setViewportSize({width, height});
      await room.evaluate(() => {window.dispatchEvent(new Event('resize'));scrollTo(0, 0);});
      await settle(room);
      const item = {label:`code71-regions-${device}-${width}x${height}`, failures:[]};
      item.failures.push(...await inspect(room, {sticky:height > 620,
        regions:['#native-tac-hud', '#native-tac-strip', '#native-arming', '#native-order-rail', '#native-briefing']}));
      const shown = await room.evaluate(() => ({cards:[...document.querySelectorAll('.native-order-card')].filter(node => node.getClientRects().length).length,
        chips:document.querySelectorAll('.native-order-chip').length, cells:document.querySelectorAll('.native-tac-cell').length,
        checks:document.querySelectorAll('.native-arming-row').length, info:document.querySelectorAll('.native-brief-card').length,
        count:document.querySelector('.native-order-count').textContent, stamp:document.querySelector('.native-order-stamp').textContent}));
      if (JSON.stringify(shown) !== JSON.stringify({cards:3, chips:2, cells:7, checks:6, info:6, count:'2/8 ERLEDIGT · 6 OFFEN', stamp:'WARTET AUF DICH'}))
        item.failures.push('content ' + JSON.stringify(shown));
      // Unfolded by its head the rail shows every open step in full and scrolls with the page.
      await room.evaluate(() => document.querySelector('.native-order-head').click());
      await settle(room);
      item.failures.push(...(await inspect(room, {sticky:false, regions:['#native-order-rail']})).map(failure => 'unfolded: ' + failure));
      const unfolded = await room.evaluate(() => [...document.querySelectorAll('.native-order-card')].filter(node => node.getClientRects().length).length);
      if (unfolded !== 6) item.failures.push('unfolded rail shows ' + unfolded + ' of 6 open steps');
      await room.evaluate(() => document.querySelector('.native-order-head').click());
      if (output && ['phone', 'fold-closed', 'fold-open', 'phone-keyboard'].includes(device)) {
        await room.evaluate(() => scrollTo(0, document.querySelector('#native-tac-hud').getBoundingClientRect().top + scrollY - 8));
        await room.screenshot({path:resolve(output, `code71-regions-${device}.png`)});
      }
      items.push(item);
    }
    await room.context().close();
  }

  // 2 · Motion in the full tier, and the same packets without motion: the final state must be the same picture.
  {
    const item = {label:'code71-motion-full', failures:[], peak:0};
    const cardsTwo = [cards[0], cards[1]].map(entry => ({...entry, title:'Heute', lines:entry.lines.slice(0, 2).map(line => line.slice(0, 30))}));
    const script = [
      ['order and cards arrive', {tactical:tactical(), busy:true, cloudBrain:true, presence:'THINKING', briefingCards:cardsTwo,
        orders:turn(1, 't7', [[1, 'ACTIVE', 'Websuche: Wetter Linz morgen']])},
        ['native-tac-in-banner@native-order-banner', 'native-tac-in-drop@native-order-rail', 'native-tac-in-fly@native-brief-card',
          'native-tac-in-slide@native-order-card', 'native-tac-mark-flash@native-tac-value', 'native-tac-in-window-a@native-live-panel',
          'native-tac-in-part@native-transcript']],
      ['a step is done and docks, the next one is dispatched', {tactical:tactical(), busy:true, cloudBrain:true, presence:'THINKING',
        briefingCards:cardsTwo, orders:turn(2, 't7', [[1, 'DONE', 'Websuche: Wetter Linz morgen'], [2, 'ACTIVE', 'Antwort schreiben']])},
        ['native-tac-in-chip@native-order-chip', 'native-tac-in-slide@native-order-card', 'native-tac-out-dock@native-order-card']],
      ['the step waits for the owner and a card leaves', {tactical:tactical(), busy:true, cloudBrain:true, presence:'WAITING_FOR_APPROVAL',
        briefingCards:[cardsTwo[1]], orders:turn(3, 't7', [[1, 'DONE', 'Websuche: Wetter Linz morgen'], [2, 'WAITING', 'Antwort schreiben']])},
        ['native-tac-mark-stamp@native-order-stamp', 'native-tac-mark-stamp@native-order-state', 'native-tac-out-fly@native-brief-card']],
      ['the order is completed', {tactical:tactical(), presence:'IDLE', briefingCards:[cardsTwo[1]],
        orders:turn(4, 't7', [[1, 'DONE', 'Websuche: Wetter Linz morgen'], [2, 'DONE', 'Antwort schreiben']], {active:false, outcome:'COMPLETED'})},
        ['native-tac-in-chip@native-order-chip', 'native-tac-mark-stamp@native-order-stamp', 'native-tac-out-dock@native-order-card']],
      ['Android withdraws the order and the last card', {tactical:tactical(), presence:'IDLE', briefingCards:[], orders:null},
        ['native-tac-out-lift@native-briefing', 'native-tac-out-slide@native-order-rail']]];
    const live = await open(834, 1112, {tactical:tactical(), presence:'IDLE', quality:'FULL'});
    const still = await open(834, 1112, {tactical:tactical(), presence:'IDLE', quality:'FULL'}, {reducedMotion:'reduce'});
    await settle(live);
    const loopsIdle = (await moving(live)).loops;
    if (JSON.stringify(loopsIdle) !== JSON.stringify(['native-tac-loop-sweep@native-tac-strip::after']))
      item.failures.push('idle loops ' + JSON.stringify(loopsIdle));
    for (const [step, packet, expected] of script) {
      const now = await moving(live, sending({...packet, quality:'FULL'}));
      item.peak = Math.max(item.peak, now.count);
      for (const pass of expected) if (!now.passes.includes(pass)) item.failures.push(step + ': no ' + pass + ' in ' + JSON.stringify(now.passes));
      if (step.startsWith('order and cards') && !['glide@presence-orbit', 'glide@workspace-motion-stage'].every(name => now.passes.includes(name)))
        item.failures.push(step + ': what the new regions push down does not glide ' + JSON.stringify(now.passes));
      if (now.marks.length === 0) item.failures.push(step + ': no mark was set');
      if (process.env.COMMAND03_TACTICAL_TRACE) console.error(step, JSON.stringify(now));
      if (JSON.stringify(now.properties) !== JSON.stringify(['opacity', 'transform']) && JSON.stringify(now.properties) !== JSON.stringify(['transform']))
        item.failures.push(step + ': animated properties ' + now.properties.join());
      if (step.startsWith('order and cards') && JSON.stringify(now.loops) !==
        JSON.stringify(['native-tac-loop-lamp@native-order-stamp::before', 'native-tac-loop-sweep@native-tac-strip::after']))
        item.failures.push('loops with an open order ' + JSON.stringify(now.loops));
      // Jump to the end: no mark is left, nothing still moves, and the picture is the one of the page without motion.
      await settle(live);
      const after = await moving(live);
      if (after.passes.length || after.marks.length) item.failures.push(step + ': left over after its end ' + JSON.stringify([after.passes, after.marks]));
      await put(still, {...packet, quality:'FULL'});
      const quiet = await moving(still);
      await settle(still);
      if (quiet.count || quiet.marks.length) item.failures.push(step + ': moves against the system preference ' + JSON.stringify([quiet.passes, quiet.loops, quiet.marks]));
      const [a, b] = [await picture(live), await picture(still)];
      if (JSON.stringify(a) !== JSON.stringify(b)) item.failures.push(step + ': the final state differs from the page without motion: ' + difference(a, b));
    }
    if (item.peak > 24) item.failures.push('more than 24 animations at once: ' + item.peak);
    // Interrupted: a step finishes and, before its card has left, the next order arrives. The newer event wins cleanly.
    await put(live, {tactical:tactical(), busy:true, cloudBrain:true, presence:'THINKING', quality:'FULL',
      orders:turn(5, 't8', [[1, 'ACTIVE', 'App suchen'], [2, 'ACTIVE', 'App öffnen']])});
    await frames(live);
    const cut = await live.evaluate(([done, next]) => {
      window.ContinuumNative.receive(done);
      const leaving = document.querySelectorAll('.native-order-card[data-native-leaving="true"]').length;
      window.ContinuumNative.receive(next);
      return {leaving, rails:document.querySelectorAll('#native-order-rail').length,
        old:document.querySelectorAll('.native-order-card[data-native-leaving="true"]').length,
        call:document.querySelector('.native-order-call').textContent};
    }, [{...state, status:'Bereit', tactical:tactical(), busy:true, cloudBrain:true, presence:'THINKING', quality:'FULL',
      orders:turn(6, 't8', [[1, 'DONE', 'App suchen'], [2, 'ACTIVE', 'App öffnen']])},
    {...state, status:'Bereit', tactical:tactical(), busy:true, cloudBrain:true, presence:'THINKING', quality:'FULL',
      orders:turn(7, 't9', [[1, 'ACTIVE', 'Kamera öffnen']])}]);
    if (cut.leaving !== 1) item.failures.push('the finished card did not start to leave: ' + cut.leaving);
    if (cut.rails !== 1 || cut.old !== 0 || cut.call !== 'AUFTRAG A-09') item.failures.push('interrupted order: ' + JSON.stringify(cut));
    await settle(live);
    await put(still, {tactical:tactical(), busy:true, cloudBrain:true, presence:'THINKING', quality:'FULL', orders:turn(7, 't9', [[1, 'ACTIVE', 'Kamera öffnen']])});
    await settle(still);
    const [a, b] = [await picture(live), await picture(still)];
    if (JSON.stringify(a) !== JSON.stringify(b)) item.failures.push('after the interruption the state differs from the page without motion: ' + difference(a, b));

    // Window hand-over: one out, one in; the newer window wins; nothing is left when it is over.
    const files = revision => ({revision, kind:'APPS', label:'Kamera', total:3, more:false, names:['Kamera', 'Open Camera', 'Galerie']});
    const base = {tactical:tactical(), busy:true, cloudBrain:true, presence:'THINKING', quality:'FULL', orders:turn(7, 't9', [[1, 'ACTIVE', 'Kamera öffnen']])};
    for (const page of [live, still]) await page.evaluate(packet => window.ContinuumNative.message(packet), {...scope, user:true, text:'Öffne die Kamera.'});
    await settle(live);
    let hand = await moving(live, sending({...base, files:files(1)}));
    const handed = hand.passes.filter(name => /window|ghost/.test(name) || name.includes('native-tac-in-part') || name.includes('@LI'));
    if (!handed.includes('native-tac-out-window@native-window-ghost')) item.failures.push('hand-over: the leaving window has no plate ' + JSON.stringify(hand.passes));
    if (!handed.some(name => /^native-tac-in-window-[ab]@native-files-panel$/.test(name))) item.failures.push('hand-over: the arriving window does not slide in ' + JSON.stringify(hand.passes));
    if (!hand.passes.some(name => name.startsWith('native-tac-in-part@'))) item.failures.push('hand-over: the parts of the window do not follow');
    if (hand.passes.filter(name => name.startsWith('native-tac-in-fly@')).length !== 3) item.failures.push('the three app hits do not fly in: ' + JSON.stringify(hand.passes));
    item.peak = Math.max(item.peak, hand.count);
    if (JSON.stringify(hand.plates) !== JSON.stringify(['ARBEITSFENSTER'])) item.failures.push('the leaving plate names ' + JSON.stringify(hand.plates));
    // Interrupted: the owner closes the list while it is still arriving.
    hand = await moving(live, {press:'#close-button'});
    if (JSON.stringify(hand.plates) !== JSON.stringify(['APPS'])) item.failures.push('interrupted hand-over keeps plates ' + JSON.stringify(hand.plates));
    if (!hand.passes.some(name => /^native-tac-in-window-[ab]@native-live-panel$/.test(name))) item.failures.push('the work window does not return with its entrance');
    await settle(live);
    await put(still, {...base, files:files(1)});
    await moving(still, {press:'#close-button'});
    // The original window controller eases a resized work window into place, also without motion preference.
    await settle(still);
    const [c, d] = [await picture(live), await picture(still)];
    if (JSON.stringify(c) !== JSON.stringify(d)) item.failures.push('after the hand-over the state differs from the page without motion: ' + difference(c, d));
    if (c.ghosts || c.marks) item.failures.push('hand-over left ' + c.ghosts + ' plates and ' + c.marks + ' marks');
    if (item.peak > 24) item.failures.push('more than 24 animations at once: ' + item.peak);
    summary.motion = {peakAnimations:item.peak};
    items.push(item);

    // 3 · Tiers on the same page: balanced moves less, the reduced tier and the owner's switch not at all, and
    // while the on-device model computes nothing moves and no mark is set.
    for (const [tier, values, moves] of [
      ['balanced', {quality:'BALANCED', cloudBrain:true}, true], ['light', {quality:'REDUCED', cloudBrain:true}, false],
      ['rest', {quality:'FULL'}, false], ['light-motion-off', {quality:'FULL', cloudBrain:true, appearance:{neon:70, lightMotion:false}}, false]]) {
      const check = {label:'code71-tier-' + tier, failures:[]};
      const serial = 20 + items.length;
      await put(live, {tactical:tactical(), presence:'IDLE', ...values, orders:null, briefingCards:[]});
      await settle(live);
      const now = await moving(live, sending({tactical:tactical({brain:values.cloudBrain ? 'CLAUDE' : 'LOCAL'}), busy:true, presence:'THINKING', ...values,
        briefingCards:[{...cardsTwo[0], id:'tier-' + tier}], orders:turn(1, 't' + serial, [[1, 'ACTIVE', 'Websuche'], [2, 'ACTIVE', 'Dateisuche']]),
        files:files(serial)}));
      const effects = await live.evaluate(() => document.body.dataset.nativeEffects);
      if (effects !== (tier === 'light-motion-off' ? 'full' : tier)) check.failures.push('tier ' + effects);
      if (moves) {
        for (const expected of ['native-tac-in-drop@native-order-rail', 'native-tac-in-slide@native-order-card', 'native-tac-in-fly@native-brief-card'])
          if (!now.passes.includes(expected)) check.failures.push('missing pass ' + expected);
        if (now.passes.some(name => /banner|in-part@(?!LI)|arming/.test(name))) check.failures.push('the balanced tier runs a pass of the full tier: ' + JSON.stringify(now.passes));
        if (now.loops.length) check.failures.push('a loop runs outside the full tier: ' + JSON.stringify(now.loops));
        if (!now.passes.some(name => name.startsWith('glide@'))) check.failures.push('the layout change does not glide');
      } else if (now.count || now.marks.length) check.failures.push('moves: ' + JSON.stringify([now.passes, now.loops, now.marks]));
      // A step docks, a card leaves, the window changes, the order ends: still nothing in the tiers without motion.
      const later = await moving(live, sending({tactical:tactical({brain:values.cloudBrain ? 'CLAUDE' : 'LOCAL'}), busy:true, presence:'THINKING', ...values,
        briefingCards:[], orders:turn(2, 't' + serial, [[1, 'DONE', 'Websuche'], [2, 'WAITING', 'Dateisuche']]), files:null}));
      if (!moves && (later.count || later.marks.length)) check.failures.push('an event moves: ' + JSON.stringify([later.passes, later.loops, later.marks]));
      if (moves && later.loops.length) check.failures.push('a loop runs outside the full tier: ' + JSON.stringify(later.loops));
      const shown = await live.evaluate(() => ({cards:[...document.querySelectorAll('.native-order-card:not([data-native-leaving])')].map(node => node.dataset.state),
        chips:document.querySelectorAll('.native-order-chip').length, info:document.querySelectorAll('#native-briefing:not([data-native-leaving]) .native-brief-card').length,
        stamp:document.querySelector('.native-order-stamp').textContent, effects:document.querySelector('[data-cell="effects"] .native-tac-value').textContent}));
      const word = {balanced:'AUSGEWOGEN', light:'REDUZIERT', rest:'RUHE', 'light-motion-off':'VOLL'}[tier];
      if (JSON.stringify(shown) !== JSON.stringify({cards:['waiting'], chips:1, info:0, stamp:'WARTET AUF DICH', effects:word}))
        check.failures.push('content ' + JSON.stringify(shown));
      await settle(live);
      items.push(check);
    }
    await live.context().close();
    await still.context().close();
  }

  // 4 · Start-up sequence: every check ticks in, the instruments come in from their sides, the composer is free;
  // "alles bereit" only with every check ready; any input ends it.
  for (const [name, states, verdict] of [['ready', ['OK', 'OK', 'OK', 'OK', 'OK', 'OK'], 'ALLE SYSTEME BEREIT'],
    ['problems', ['OK', 'FAIL', 'OK', 'WARN', 'OK', 'OFF'], null]]) {
    const item = {label:'code71-arming-' + name, failures:[]};
    const room = await open(412, 915, null);
    // The first snapshot and what it starts are read in one task: nothing has moved on yet.
    const start = await room.evaluate(packet => {
      window.ContinuumNative.receive(packet);
      const rows = [...document.querySelectorAll('.native-arming-row')];
      const composer = document.querySelector('#command-form').getBoundingClientRect(), panel = document.querySelector('#native-arming').getBoundingClientRect();
      const field = document.querySelector('#command-input'), b = field.getBoundingClientRect();
      return {mode:document.querySelector('#native-arming').dataset.mode, body:document.body.dataset.nativeArming,
        delays:rows.map(row => parseFloat(getComputedStyle(row).animationDelay)), names:[...new Set(rows.map(row => getComputedStyle(row).animationName))],
        verdict:document.querySelector('.native-arming-verdict').textContent, verdictDelay:parseFloat(getComputedStyle(document.querySelector('.native-arming-verdict')).animationDelay),
        wings:['.presence-wing-left', '.presence-wing-right'].map(selector => getComputedStyle(document.querySelector(selector)).animationName),
        pass:getComputedStyle(document.querySelector('#native-arming'), '::after').animationName,
        overlap:panel.bottom > composer.top && panel.top < composer.bottom,
        composerFree:document.elementFromPoint(b.x + b.width / 2, b.y + b.height / 2) === field};
    }, {...state, status:'Bereit', tactical:tactical(), presence:'IDLE', quality:'FULL', systemCheck:checks(states)});
    // The sequence is held at its settled picture (every check in place, its pass still under way) for the geometry.
    await room.evaluate(() => {
      for (const animation of document.getAnimations()) {
        const glide = !animation.animationName && animation.constructor.name === 'Animation' && animation.effect?.composite === 'add';
        if (!glide && !animation.animationName?.startsWith('native-tac-')) continue;
        if (animation.animationName === 'native-tac-arming-pass') animation.pause();
        else if (animation.effect.getComputedTiming().iterations !== Infinity) animation.finish();
      }
      return document.fonts.ready;
    });
    await frames(room);
    const held = await room.evaluate(() => [document.querySelector('#native-arming')?.dataset.mode, document.body.dataset.nativeArming]);
    if (JSON.stringify(held) !== JSON.stringify(['run', 'run'])) item.failures.push('the sequence ended before its pass: ' + JSON.stringify(held));
    if (start.mode !== 'run' || start.body !== 'run') item.failures.push('not the start-up sequence: ' + start.mode + '/' + start.body);
    if (JSON.stringify(start.names) !== JSON.stringify(['native-tac-in-tick'])) item.failures.push('checks do not tick in: ' + start.names.join());
    if (start.delays.length !== 6 || start.delays.some((delay, index) => index > 0 && delay <= start.delays[index - 1]))
      item.failures.push('checks do not tick in one after the other: ' + start.delays.join());
    if (!(start.verdictDelay > start.delays.at(-1))) item.failures.push('the verdict does not come last');
    if (JSON.stringify(start.wings) !== JSON.stringify(['native-tac-in-wing-left', 'native-tac-in-wing-right'])) item.failures.push('instruments: ' + start.wings.join());
    if (start.pass !== 'native-tac-arming-pass') item.failures.push('no pass of the sequence: ' + start.pass);
    if (!start.composerFree) item.failures.push('the sequence covers the composer');
    if (verdict ? start.verdict !== verdict : !/^NICHT ALLES BEREIT · Fehler: /.test(start.verdict) || /ALLE SYSTEME BEREIT/.test(start.verdict))
      item.failures.push('verdict: ' + start.verdict);
    item.failures.push(...(await inspect(room, {sticky:true, regions:['#native-tac-hud', '#native-tac-strip', '#native-arming']})));
    if (output) await room.screenshot({path:resolve(output, `code71-arming-${name}.png`)});
    if (name === 'ready') {
      // Left alone the sequence ends by itself: its pass is over, the list closes, the strip keeps the count.
      await room.evaluate(() => document.getAnimations().filter(animation => animation.animationName === 'native-tac-arming-pass')
        .forEach(animation => animation.finish()));
      await frames(room);
      await settle(room);
      await frames(room);
    } else {
      // Any input ends it at once – here a tap on the stage's heading. What is not ready stays listed.
      await room.locator('#state-eyebrow').tap();
      await frames(room);
    }
    const end = await room.evaluate(() => ({mode:document.querySelector('#native-arming')?.dataset.mode ?? null, body:document.body.dataset.nativeArming ?? null,
      rows:[...document.querySelectorAll('.native-arming-row')].map(row => row.dataset.state),
      marks:document.querySelectorAll('#native-tac-hud [data-native-enter]').length,
      wings:['.presence-wing-left', '.presence-wing-right'].map(selector => getComputedStyle(document.querySelector(selector)).animationName),
      systems:document.querySelector('[data-cell="systems"] .native-tac-value').textContent,
      expanded:document.querySelector('[data-cell="systems"]').getAttribute('aria-expanded')}));
    await settle(room);
    const expected = name === 'ready' ? {mode:null, body:null, rows:[], marks:0, wings:['none', 'none'], systems:'6/6', expanded:'false'} :
      {mode:'problems', body:null, rows:['fail', 'warn', 'off'], marks:0, wings:['none', 'none'], systems:'3/6', expanded:'true'};
    const gone = await room.evaluate(() => document.querySelector('#native-arming')?.dataset.nativeLeaving === 'true' ? null : (document.querySelector('#native-arming')?.dataset.mode ?? null));
    if (JSON.stringify({...end, mode:gone}) !== JSON.stringify(expected)) item.failures.push('end of the sequence: ' + JSON.stringify({...end, mode:gone}));
    if (name === 'problems') {
      await room.locator('.native-arming-close').click();
      await settle(room);
      if (await room.locator('#native-arming').count()) item.failures.push('the list does not close');
      await room.locator('[data-native-tac="systems"]').click();
      if (await room.locator('#native-arming').getAttribute('data-mode') !== 'list') item.failures.push('the strip cell does not open the list');
    }
    items.push(item);
    await room.context().close();
  }
  return {items, summary};
}

// Run on its own.
if (process.argv[1] === fileURLToPath(import.meta.url)) {
  const require = createRequire(import.meta.url);
  const {chromium} = require(process.env.COMMAND03_PLAYWRIGHT_MODULE || 'playwright');
  const root = resolve(fileURLToPath(new URL('../../app/src/main/assets/continuum-command03/', import.meta.url)));
  const mime = {'.html':'text/html', '.css':'text/css', '.js':'application/javascript', '.png':'image/png', '.ttf':'font/ttf', '.glb':'model/gltf-binary'};
  const server = createServer(async (req, res) => {
    try {
      const path = resolve(root, decodeURIComponent(new URL(req.url, 'http://localhost').pathname.replace(/^\/after\//, '')));
      if (!path.startsWith(root + sep)) {res.writeHead(403).end();return;}
      res.writeHead(200, {'Content-Type':mime[extname(path)] || 'application/octet-stream', 'Cache-Control':'no-store'}).end(await readFile(path));
    } catch {res.writeHead(404).end();}
  });
  await new Promise(done => server.listen(0, '127.0.0.1', done));
  const scope = {sessionId:'12345678-1234-1234-1234-123456789abc', pageId:'11111111-1111-4111-8111-111111111111',
    viewId:'22222222-2222-4222-8222-222222222222', viewRevision:1};
  const state = {...scope, draftRevision:1, ackEditRevision:0, viewKind:'CENTRAL', viewLabel:'JARVIS', cpu:12, ram:84,
    telemetryAtElapsedMs:100, chats:[], workRows:[]};
  const browser = await chromium.launch({executablePath:process.env.COMMAND03_CHROMIUM_PATH || undefined, headless:true,
    args:['--no-sandbox', '--enable-unsafe-swiftshader']});
  const errors = [];
  try {
    const {items, summary} = await tacticalStage({browser, url:`http://127.0.0.1:${server.address().port}`, state, scope, errors,
      output:process.env.COMMAND03_LAYOUT_OUTPUT ? resolve(process.env.COMMAND03_LAYOUT_OUTPUT) : null});
    const failures = items.flatMap(item => item.failures.map(failure => item.label + ': ' + failure));
    console.log(JSON.stringify({checks:items.length, summary, failures, errors}, null, 2));
    assert.deepEqual(errors, [], 'Unexpected page errors');
    assert.deepEqual(failures, [], 'Tactical stage regression');
  } finally {
    await browser.close();
    await new Promise(done => server.close(done));
  }
}
