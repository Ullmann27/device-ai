/**
 * Presence 02: actual native Canvas/SVG study, not a browser capture.
 * UI data and instrument SVGs are read by executing the real app modules in
 * jsdom. Original orb is fixed; original avatar remains a labelled still PNG.
 */
import fs from 'node:fs/promises';
import path from 'node:path';
import vm from 'node:vm';
import crypto from 'node:crypto';
import { spawn } from 'node:child_process';
import { once } from 'node:events';
import { fileURLToPath } from 'node:url';
import requireDev from './require-dev.cjs';
import { createContinuumOriginalRenderer, BrowserContinuumPainter, GEOMETRY, ORIGINAL_COMMIT } from './orb-original-source.js';

const { createCanvas, loadImage, GlobalFonts } = requireDev('continuum-native-canvas', '@napi-rs/canvas');
const { JSDOM } = requireDev('jsdom');
const root = path.resolve(path.dirname(fileURLToPath(import.meta.url)), '..');
const preview = path.join(root, 'continuum-preview');
const assets = path.join(preview, 'assets');
const width = 1200, height = 720, fps = 24, seconds = 8;
GlobalFonts.registerFromPath(path.join(assets, 'fonts/manrope-0.ttf'), 'Manrope');
GlobalFonts.registerFromPath(path.join(assets, 'fonts/space-grotesk-4.ttf'), 'Space Grotesk');
const inputs = {};
for (const file of ['index.html', 'app.js', 'scene.js', 'presence-instruments.js', 'presence-instruments.css']) {
  inputs[file] = await fs.readFile(path.join(preview, file), 'utf8');
}
const dom = new JSDOM(inputs['index.html'], { runScripts: 'outside-only', url: 'https://continuum-study.invalid/' });
const ui = dom.window;
ui.requestAnimationFrame = () => 1;
ui.cancelAnimationFrame = () => {};
ui.setTimeout = () => 1;
ui.setInterval = () => 1;
ui.ContinuumScene = { setState() {}, setIntensity() {}, setQuality() {}, resize() {} };
const style = ui.document.createElement('style');
style.textContent = inputs['presence-instruments.css'];
ui.document.head.append(style);
ui.eval(inputs['app.js']);
ui.eval(inputs['presence-instruments.js']);
ui.ContinuumPreview.selectPhase(2);
const data = ui.ContinuumPreview.getVisualizationData();
if (data.phases.length !== 6 || !ui.ContinuumPresenceInstruments.getStatus().connected) {
  throw new Error('The actual six-phase instrument module is not connected.');
}
const selected = data.phases[data.selectedPhase];
const left = ui.document.querySelector('[data-presence-left]');
const right = ui.document.querySelector('[data-presence-right]');
const labels = {
  leftHeading: left.querySelector('h3').textContent,
  rightHeading: right.querySelector('h3').textContent,
  currentName: left.querySelector('[data-pi-current-name]').textContent,
  currentEnd: left.querySelector('[data-pi-current-end]').textContent,
  document: right.querySelector('.pi-document-label').textContent,
  proof: right.querySelector('[data-pi-source-proof]').textContent,
  phaseButtons: [...left.querySelectorAll('.pi-phase-button')].map(button => ({ text: button.textContent, number: button.querySelector('.pi-phase-number').textContent, value: button.querySelector('.pi-phase-end').textContent, selected: button.getAttribute('aria-pressed') === 'true' })),
  sourceButtons: [...right.querySelectorAll('.pi-source-button')].map(button => ({ number: button.querySelector('span').textContent, label: button.querySelector('small').textContent, selected: button.getAttribute('aria-pressed') === 'true' })),
};

function svgTemplate(node, pixelWidth, pixelHeight) {
  if (!node) throw new Error('Actual instrument SVG missing.');
  const copy = node.cloneNode(true);
  const originals = [node, ...node.querySelectorAll('*')];
  const clones = [copy, ...copy.querySelectorAll('*')];
  const properties = ['fill', 'stroke', 'stroke-width', 'stroke-dasharray', 'stroke-dashoffset', 'stroke-linecap', 'stroke-linejoin', 'opacity', 'font-family', 'font-size', 'font-weight', 'text-anchor'];
  for (let i = 0; i < originals.length; i++) {
    const computed = ui.getComputedStyle(originals[i]);
    for (const property of properties) {
      const value = computed.getPropertyValue(property);
      if (value) clones[i].style.setProperty(property, value);
    }
    clones[i].style.removeProperty('animation');
    clones[i].style.removeProperty('transition');
    clones[i].style.removeProperty('filter');
  }
  copy.setAttribute('xmlns', 'http://www.w3.org/2000/svg');
  copy.setAttribute('width', pixelWidth);
  copy.setAttribute('height', pixelHeight);
  copy.style.width = `${pixelWidth}px`;
  copy.style.height = `${pixelHeight}px`;
  for (const node of copy.querySelectorAll('.pi-time-impulse')) {
    node.style.removeProperty('stroke-dashoffset'); node.setAttribute('stroke-dashoffset', '__TIME_DASH__');
  }
  for (const node of copy.querySelectorAll('.pi-source-impulse')) {
    node.style.removeProperty('stroke-dashoffset'); node.setAttribute('stroke-dashoffset', '__SOURCE_DASH__');
  }
  for (const node of copy.querySelectorAll('[data-selected="true"] .pi-source-corona')) {
    node.style.removeProperty('opacity'); node.setAttribute('opacity', '__CORONA__');
  }
  // The pinned native SVG rasterizer treats quoted local paint URLs as black.
  // Equivalent unquoted fragment syntax preserves the actual SVG gradients.
  return copy.outerHTML.replace(/url\((?:&quot;|["'])(#[^"'&()]+)(?:&quot;|["'])\)/g, 'url($1)');
}
const leftSVG = svgTemplate(left.querySelector('.pi-time-chart'), 248, 116);
const rightSVG = svgTemplate(right.querySelector('.pi-source-chart'), 248, 143);
const sourceGroups = [...new Set(data.phases.map(p => p.source))];
const canvas = createCanvas(width, height);
const ctx = canvas.getContext('2d');
const portrait = await loadImage(path.join(assets, 'continuum-avatar-original.png'));
const orbLayer = createCanvas(width, height);
const orbContext = orbLayer.getContext('2d');
const orbBox = { left: 350, top: 90, width: 500, height: 450 };
const original = createContinuumOriginalRenderer({ geometry: GEOMETRY.REFERENCE_COCKPIT });
original.layout(orbBox.width, orbBox.height);
const fixedFrame = { phase: .4, energy: .18, audio: 0, weights: Float32Array.of(1, 0, 0, 0, 0, 0) };
orbContext.translate(orbBox.left, orbBox.top);
const painter = new BrowserContinuumPainter(orbContext);
painter.begin(); original.draw(painter, fixedFrame, true);

let now = 0, nextRoomFrame = null;
canvas.getBoundingClientRect = () => ({ left: 0, top: 0, width, height });
const host = { innerWidth: width, innerHeight: height, devicePixelRatio: 1,
  addEventListener() {}, removeEventListener() {}, dispatchEvent() {},
  matchMedia: () => ({ matches: false, addEventListener() {} }) };
const nativeDocument = { readyState: 'complete',
  getElementById: id => id === 'scene-canvas' ? canvas : null,
  createElement: name => { if (name !== 'canvas') throw new Error(`Unexpected native surface: ${name}`); return createCanvas(1, 1); },
  querySelector: selector => selector === '[data-orb-anchor]' ? { dataset: { orbMotion: 'fixed' }, getBoundingClientRect: () => orbBox }
    : selector === '[data-work-anchor]' ? { getBoundingClientRect: () => ({ left: 400, top: 580, width: 400, height: 60 }) } : null,
  addEventListener() {}, removeEventListener() {} };
const room = vm.createContext({ window: host, document: nativeDocument, performance: { now: () => now },
  requestAnimationFrame: callback => { nextRoomFrame = callback; return 1; }, cancelAnimationFrame() {},
  CustomEvent: class { constructor(type, options) { this.type = type; Object.assign(this, options); } }, console, Math });
vm.runInContext(inputs['scene.js'], room);
host.ContinuumScene.setQuality('high');
host.ContinuumScene.setOrbVisible(false);
host.ContinuumScene.setAudioLevel(0);

function text(value, x, y, size, color, font = 'Manrope', align = 'left') {
  ctx.fillStyle = color; ctx.font = `${size}px "${font}"`; ctx.textAlign = align; ctx.fillText(value, x, y);
}
function stroke(x1, y1, x2, y2, color, size = 1) {
  ctx.strokeStyle = color; ctx.lineWidth = size; ctx.beginPath(); ctx.moveTo(x1, y1); ctx.lineTo(x2, y2); ctx.stroke();
}
function panel(x, y, w, h, t, violet = false) {
  ctx.fillStyle = 'rgba(5,13,25,.82)'; ctx.strokeStyle = violet ? 'rgba(141,150,185,.34)' : 'rgba(116,160,181,.35)';
  ctx.lineWidth = 1; ctx.beginPath(); ctx.roundRect(x + 5, y + 6, w, h, 14); ctx.fill(); ctx.stroke();
  const glass = ctx.createLinearGradient(x, y, x + w, y + h);
  glass.addColorStop(0, violet ? 'rgba(40,46,71,.88)' : 'rgba(29,53,73,.88)');
  glass.addColorStop(.45, 'rgba(8,23,39,.94)'); glass.addColorStop(1, 'rgba(11,21,39,.94)');
  ctx.fillStyle = glass; ctx.strokeStyle = violet ? '#616b8a' : '#54778a';
  ctx.beginPath(); ctx.roundRect(x, y, w, h, 14); ctx.fill(); ctx.stroke();
  ctx.strokeStyle = 'rgba(198,230,244,.14)'; ctx.beginPath(); ctx.roundRect(x + 3, y + 3, w - 6, h - 6, 11); ctx.stroke();
  ctx.save(); ctx.beginPath(); ctx.roundRect(x, y, w, h, 14); ctx.clip();
  const period = violet ? 29.3 : 21.7;
  const offset = ((t + (violet ? 11.4 : 6.5)) / period % 1) * (w * 2) - w * .5;
  const reflection = ctx.createLinearGradient(x + offset - 80, y, x + offset + 80, y + h);
  reflection.addColorStop(0, 'rgba(175,222,241,0)'); reflection.addColorStop(.54, 'rgba(175,222,241,.07)'); reflection.addColorStop(1, 'rgba(175,222,241,0)');
  ctx.fillStyle = reflection; ctx.fillRect(x, y, w, h); ctx.restore();
}
function button(x, y, w, h, selected, upper, lower, violet = false) {
  ctx.fillStyle = selected ? (violet ? '#283b52' : '#244c5d') : '#102638';
  ctx.strokeStyle = selected ? (violet ? '#a3afcf' : '#8ad7cd') : '#3a566b';
  ctx.lineWidth = 1; ctx.beginPath(); ctx.roundRect(x, y, w, h, 6); ctx.fill(); ctx.stroke();
  text(upper, x + w / 2, y + 16, 11, selected ? '#bddedc' : '#7295aa', 'Manrope', 'center');
  text(lower, x + w / 2, y + 34, 14, selected ? '#e1f9f0' : '#a3bfce', 'Space Grotesk', 'center');
}
async function composition(t) {
  ctx.save(); ctx.globalCompositeOperation = 'source-over'; ctx.globalAlpha = .7;
  ctx.drawImage(orbLayer, 0, 0); ctx.globalAlpha = 1;
  ctx.drawImage(portrait, 375, 170, 450, 337.5);
  panel(55, 201, 272, 350, t);
  panel(873, 201, 272, 350, t, true);
  const leftFrame = leftSVG.replaceAll('__TIME_DASH__', String(240 - 475 * ((t / 5.3) % 1)));
  const rightFrame = rightSVG.replaceAll('__SOURCE_DASH__', String(135 - 266 * ((t / 6.7) % 1)))
    .replaceAll('__CORONA__', String(.65 + .25 * Math.sin(t / 8.9 * Math.PI * 2)));
  const [leftImage, rightImage] = await Promise.all([loadImage(Buffer.from(leftFrame)), loadImage(Buffer.from(rightFrame))]);
  text(labels.leftHeading, 73, 231, 17, '#d1e9f0', 'Space Grotesk');
  text('ms', 309, 231, 12, '#83a5b8', 'Space Grotesk', 'right');
  text('Entwurfswerte', 73, 257, 12, '#83a7bc');
  text(labels.currentName, 73, 278, 14, '#bfdeeb');
  text(`${labels.currentEnd}`, 281, 278, 31, '#daf4ef', 'Space Grotesk', 'right');
  text('ms', 309, 278, 12, '#82a8b6', 'Manrope', 'right');
  ctx.drawImage(leftImage, 67, 291, 248, 116);
  text(`${Math.min(...data.phases.map(p => p.start))} ms`, 73, 424, 11, '#7e9fb2');
  text(`${Math.max(...data.phases.map(p => p.end))} ms`, 309, 424, 11, '#7e9fb2', 'Manrope', 'right');
  labels.phaseButtons.forEach((b, i) => button(73 + (i % 3) * 79, 439 + Math.floor(i / 3) * 50, 74, 44, b.selected, b.number, b.value));

  text(labels.rightHeading, 891, 231, 17, '#d3dbea', 'Space Grotesk');
  text('Argon4', 891, 259, 15, '#becbe3', 'Space Grotesk');
  text('Demonstration', 1127, 259, 11, '#91a4be', 'Manrope', 'right');
  ctx.drawImage(rightImage, 885, 274, 248, 143);
  labels.sourceButtons.forEach((b, i) => button(891 + i * 79, 439, 74, 44, b.selected, b.number, b.label, true));
  text('belegt', 891, 510, 12, '#91a9bd');
  text(labels.proof, 933, 510, 13, '#c6ddeb');
  text('Quelle und Auswahl bleiben verbunden.', 891, 533, 10.5, '#718ea6');

  text('CONTINUUM', 600, 524, 12, '#ccedf0', 'Space Grotesk', 'center');
  text('ORIGINALPRÄSENZ', 600, 544, 10, '#89aeba', 'Manrope', 'center');
  // Actual reference remains still; ambient scene and material impulses continue.
  text('Blender-Lichtstudie · ruhiger Originalkopf', 600, 579, 11, '#9bb9c9', 'Manrope', 'center');
  text('Originalkern stationär · Raum und Licht in Bewegung', 600, 601, 11, '#7899b1', 'Manrope', 'center');
  stroke(55, 53, 70, 53, '#8bdfee', 2);
  text('DEVICE AI  /  CONTINUUM', 83, 59, 23, '#e3f2f9', 'Space Grotesk');
  text('PRÄSENZ 02', 1145, 58, 15, '#a0c6d7', 'Space Grotesk', 'right');
  text('DIE ASSISTENTIN IM MITTELPUNKT', 55, 90, 11.5, '#8eafc4');
  text('RAUMSTUDIE · NATIVE CANVAS', 1145, 90, 11, '#7d9cb4', 'Manrope', 'right');
  stroke(55, 110, 1145, 110, 'rgba(139,187,212,.16)');
  stroke(55, 653, 1145, 653, 'rgba(139,187,212,.16)');
  text('Sechs Phasen aus der Argon4-Demonstration · stabile Entwurfswerte, keine Messdaten', 55, 680, 12, '#8baabd');
  text('OHNE AUDIO', 1145, 680, 11, '#7595ac', 'Manrope', 'right');
  ctx.restore();
}

const output = path.join(root, 'Continuum_Praesenz_02.mp4');
const encoder = spawn('ffmpeg', ['-y', '-hide_banner', '-loglevel', 'error', '-f', 'image2pipe', '-framerate', String(fps), '-vcodec', 'png', '-i', 'pipe:0', '-an', '-c:v', 'libx264', '-preset', 'medium', '-crf', '19', '-pix_fmt', 'yuv420p', '-movflags', '+faststart', output], { stdio: ['pipe', 'ignore', 'pipe'] });
let encoderError = '';
encoder.stderr.on('data', data => { encoderError += data.toString(); });
const finished = once(encoder, 'close');
for (let index = 0; index < seconds * fps; index++) {
  now = index / fps * 1000;
  if (index === 0) host.ContinuumScene.setState('idle');
  if (index === 48) host.ContinuumScene.setState('working');
  if (index === 156) host.ContinuumScene.setState('result');
  const renderRoom = nextRoomFrame;
  renderRoom(now);
  await composition(index / fps);
  const png = await canvas.encode('png');
  if (index === 96) await fs.writeFile(path.join(root, 'Continuum_Praesenz_02.png'), png);
  if (!encoder.stdin.write(png)) await once(encoder.stdin, 'drain');
  if (index % 48 === 0) console.log(`Presence 02: ${index}/${seconds * fps} native frames`);
}
encoder.stdin.end();
const [code] = await finished;
if (code !== 0) throw new Error(`ffmpeg: ${encoderError}`);
await fs.writeFile(path.join(root, 'handoff-3d/Praesenz_02_Nachweis.json'), JSON.stringify({
  title: 'Continuum · Präsenz 02', renderer: 'Native Canvas2D/SVG via @napi-rs/canvas, not a browser capture',
  sourceRepository: 'https://github.com/Ullmann27/device-ai', sourceCommit: ORIGINAL_COMMIT,
  sourceSha256: Object.fromEntries(Object.entries(inputs).map(([file, content]) => [file, crypto.createHash('sha256').update(content).digest('hex')])),
  originalAvatarSha256: 'c8b88385c73fa01596a5734d20859440b1ecaef06cb1abea6cd7a47e62b08b9a',
  avatar: 'Unchanged original Blender PNG, static and labelled as such',
  orb: { mode: 'fixed', phase: .4, energy: .18, audio: 0, metrics: original.getMetrics() },
  instrumentDataSource: 'Actual app.js and presence-instruments.js executed in jsdom; actual SVG plus computed styles rasterized',
  phases: data.phases, selectedPhase: data.selectedPhase, sourceGroups, labels,
  video: { width, height, fps, frames: seconds * fps, seconds, audio: false },
  scope: 'A composed room study of the central layout, not a full UI screenshot, GPU benchmark, speech recording or real PDF analysis.',
}, null, 2));
dom.window.close();
console.log(`Completed ${output}`);
