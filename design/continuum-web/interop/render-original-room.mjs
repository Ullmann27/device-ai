/**
 * Offline native-Canvas evidence, not a browser recording.
 * Uses the actual original-orb adapter and untouched original-avatar PNG.
 * The head is deliberately a labelled still; this file does not fake lip motion.
 */
import fs from 'node:fs/promises';
import path from 'node:path';
import vm from 'node:vm';
import requireDev from './require-dev.cjs';
import { spawn } from 'node:child_process';
import { once } from 'node:events';
import { fileURLToPath } from 'node:url';
import { createContinuumOriginalRenderer, BrowserContinuumPainter, GEOMETRY, ContinuumOriginalClock, ORIGINAL_COMMIT } from './orb-original-source.js';

const { createCanvas, loadImage, GlobalFonts } = requireDev('continuum-native-canvas', '@napi-rs/canvas');
const directory = path.resolve(path.dirname(fileURLToPath(import.meta.url)), '..');
const assets = path.join(directory, 'continuum-preview/assets');
GlobalFonts.registerFromPath(path.join(assets, 'fonts/manrope-0.ttf'), 'Manrope');
GlobalFonts.registerFromPath(path.join(assets, 'fonts/space-grotesk-4.ttf'), 'Space Grotesk');
const width = 960, height = 600, fps = 24, duration = 8;
const canvas = createCanvas(width, height);
const ctx = canvas.getContext('2d');
const portrait = await loadImage(path.join(assets, 'continuum-avatar-original.png'));
const original = createContinuumOriginalRenderer({ geometry: GEOMETRY.REFERENCE_COCKPIT });
original.layout(340, 320);
const metrics = original.getMetrics();
const painter = new BrowserContinuumPainter(ctx);
const clock = new ContinuumOriginalClock();
const orbBox = { x: 562, y: 92, width: 340, height: 320 };
const orbCenter = { x: orbBox.x + metrics.centerX, y: orbBox.y + metrics.centerY };
const orbRect = { left: orbCenter.x - metrics.radius / .73, top: orbCenter.y - metrics.radius / .73, width: metrics.radius / .365, height: metrics.radius / .365 };
let now = 0, nextFrame = null;
canvas.getBoundingClientRect = () => ({ left: 0, top: 0, width, height });
const host = {
  innerWidth: width, innerHeight: height, devicePixelRatio: 1,
  addEventListener() {}, removeEventListener() {}, dispatchEvent() {},
  matchMedia: () => ({ matches: false, addEventListener() {} }),
};
const doc = {
  readyState: 'complete',
  getElementById: id => id === 'scene-canvas' ? canvas : null,
  createElement: name => { if (name !== 'canvas') throw new Error(`Unexpected element ${name}`); return createCanvas(1, 1); },
  querySelector: selector => selector === '[data-orb-anchor]' ? { getBoundingClientRect: () => orbRect }
    : selector === '[data-work-anchor]' ? { getBoundingClientRect: () => ({ left: 422, top: 365, width: 415, height: 145 }) } : null,
  addEventListener() {}, removeEventListener() {},
};
const sandbox = vm.createContext({ window: host, document: doc, performance: { now: () => now },
  requestAnimationFrame: callback => { nextFrame = callback; return 1; }, cancelAnimationFrame() {},
  CustomEvent: class { constructor(type, options) { this.type = type; Object.assign(this, options); } },
  console, Math });
vm.runInContext(await fs.readFile(path.join(directory, 'continuum-preview/scene.js'), 'utf8'), sandbox);
host.ContinuumScene.setQuality('high');
host.ContinuumScene.setOrbVisible(false);
host.ContinuumScene.setAudioLevel(0);

const clamp = value => Math.max(0, Math.min(1, value));
const smooth = value => { const x = clamp(value); return x * x * (3 - 2 * x); };
function text(value, x, y, size, color, font = 'Manrope') {
  ctx.fillStyle = color; ctx.font = `${size}px "${font}"`; ctx.textAlign = 'left'; ctx.fillText(value, x, y);
}
function line(x1, y1, x2, y2, color, thickness = 1) {
  ctx.strokeStyle = color; ctx.lineWidth = thickness; ctx.beginPath(); ctx.moveTo(x1, y1); ctx.lineTo(x2, y2); ctx.stroke();
}
function glass(t) {
  const enter = smooth((t - 3.1) / .95);
  if (enter <= 0) return;
  const x = 419 + (1 - enter) * 38, y = 372 - (1 - enter) * 24;
  const w = 417 * (.86 + enter * .14), h = 142;
  ctx.save();
  ctx.globalAlpha = enter;
  ctx.translate(x, y + Math.sin(t * .41) * 1.25);
  ctx.transform(1, -.012 * (1 - enter), -.12 * (1 - enter), 1, 0, 0);
  // The separately visible rear casing, glass face and inset content plane.
  ctx.fillStyle = 'rgba(4,13,27,.84)'; ctx.strokeStyle = 'rgba(96,163,202,.25)'; ctx.lineWidth = 1;
  ctx.beginPath(); ctx.roundRect(-7, 8, w, h, 13); ctx.fill(); ctx.stroke();
  const panel = ctx.createLinearGradient(0, 0, w, h);
  panel.addColorStop(0, 'rgba(24,46,70,.90)'); panel.addColorStop(.5, 'rgba(9,24,41,.93)'); panel.addColorStop(1, 'rgba(17,22,49,.88)');
  ctx.fillStyle = panel; ctx.beginPath(); ctx.roundRect(0, 0, w, h, 12); ctx.fill();
  ctx.strokeStyle = 'rgba(123,209,244,.53)'; ctx.stroke();
  ctx.strokeStyle = 'rgba(191,228,247,.13)'; ctx.beginPath(); ctx.roundRect(4, 4, w - 8, h - 8, 9); ctx.stroke();
  text('ARBEITSFLÄCHE', 20, 27, 11, '#b9d9e9', 'Space Grotesk');
  text('Ein Zusammenhang wird sichtbar', 20, 53, 18, '#e6f5fb', 'Space Grotesk');
  const nodeY = 88;
  const nodes = [{ x: 29, label: 'Quelle' }, { x: 173, label: 'Kontext' }, { x: 332, label: 'Ergebnis' }];
  for (let i = 0; i < 2; i++) {
    line(nodes[i].x + 9, nodeY, nodes[i + 1].x - 9, nodeY, 'rgba(99,162,194,.37)');
    const p = ((t * .26 + i * .39) % 1);
    const px = nodes[i].x + 9 + p * (nodes[i + 1].x - nodes[i].x - 18);
    ctx.shadowColor = '#5ad6ff'; ctx.shadowBlur = 8;
    line(px - 10, nodeY, px + 3, nodeY, '#97e8ff', 1.6);
    ctx.shadowBlur = 0;
  }
  nodes.forEach((node, i) => {
    ctx.strokeStyle = i === 1 ? '#94dcff' : '#7396b6'; ctx.lineWidth = 1;
    ctx.fillStyle = i === 1 ? '#18374c' : '#0c1b2c';
    ctx.beginPath(); ctx.arc(node.x, nodeY, 5, 0, Math.PI * 2); ctx.fill(); ctx.stroke();
    text(node.label, node.x - 6, 116, 10, '#bed1df');
  });
  ctx.save(); ctx.beginPath(); ctx.roundRect(0, 0, w, h, 12); ctx.clip();
  const glintX = -120 + ((t * 27) % (w + 240));
  const glint = ctx.createLinearGradient(glintX - 100, 0, glintX + 100, h);
  glint.addColorStop(0, 'rgba(139,219,255,0)'); glint.addColorStop(.5, 'rgba(170,227,255,.075)'); glint.addColorStop(1, 'rgba(139,219,255,0)');
  ctx.fillStyle = glint; ctx.fillRect(0, 0, w, h); ctx.restore();
  ctx.restore();
}
function composition(t, label, step) {
  ctx.save();
  ctx.globalCompositeOperation = 'source-over'; ctx.globalAlpha = 1;
  // Unmodified image pixels from the real Blender import; no animated-face claim.
  ctx.drawImage(portrait, 46, 132, 432, 324);
  ctx.save(); ctx.translate(orbBox.x, orbBox.y); painter.begin();
  original.draw(painter, clock.advance(now), true); ctx.restore();
  ctx.globalAlpha = 1; ctx.globalCompositeOperation = 'source-over';
  glass(t);
  ctx.globalAlpha = 1; ctx.globalCompositeOperation = 'source-over';
  line(48, 43, 61, 43, '#74dcfb', 2);
  text('CONTINUUM', 72, 49, 23, '#e9f6fc', 'Space Grotesk');
  text('RAUMSTUDIE · ORIGINALE', 48, 79, 10.5, '#95b4c8');
  line(48, 101, 912, 101, 'rgba(142,194,226,.16)');
  ctx.textAlign = 'right'; ctx.font = '12px Manrope'; ctx.fillStyle = '#bad7e6'; ctx.fillText(`${step}  /  ${label}`, 912, 50);
  text('ORIGINALAVATAR', 145, 481, 11, '#bad8e8', 'Space Grotesk');
  text('Blender-Lichtstudie · ruhiges Bild', 145, 501, 10.5, '#7898b0');
  if (t < 3.1) {
    text('CONTINUUM-KERN', 649, 431, 11, '#bedbeb', 'Space Grotesk');
    text('Originalrenderer · kontinuierliche Bewegung', 612, 452, 10.5, '#88a9c1');
  }
  line(48, 550, 912, 550, 'rgba(142,194,226,.15)');
  text('Originalkopf und Originalkern aus Device AI · Licht und Raum in Bewegung', 48, 575, 10.5, '#92aabd');
  ctx.font = '10.5px Manrope'; ctx.textAlign = 'right'; ctx.fillStyle = '#718fa4'; ctx.fillText('OHNE AUDIO', 912, 575);
  ctx.restore();
}

const output = path.join(directory, 'Continuum_Originale_Raumstudie.mp4');
const encoder = spawn('ffmpeg', ['-y', '-hide_banner', '-loglevel', 'error', '-f', 'image2pipe', '-framerate', String(fps), '-vcodec', 'png', '-i', 'pipe:0', '-an', '-c:v', 'libx264', '-preset', 'medium', '-crf', '19', '-pix_fmt', 'yuv420p', '-movflags', '+faststart', output], { stdio: ['pipe', 'ignore', 'pipe'] });
let encoderError = '';
encoder.stderr.on('data', chunk => { encoderError += chunk.toString(); });
const finished = once(encoder, 'close');
let lastState = null;
for (let index = 0; index < duration * fps; index++) {
  now = index / fps * 1000;
  const t = index / fps;
  const state = t < 1.8 ? 'idle' : t < 3.1 ? 'listening' : t < 4.05 ? 'forming' : t < 6.7 ? 'working' : 'result';
  const label = t < 1.8 ? 'Bereitschaft' : t < 3.1 ? 'Zuhören' : t < 6.7 ? 'Arbeitsimpuls' : 'Die Welt bleibt in Bewegung';
  const step = t < 1.8 ? '01' : t < 3.1 ? '02' : t < 6.7 ? '03' : '04';
  if (state !== lastState) { host.ContinuumScene.setState(state); clock.setState(state); lastState = state; }
  const renderRoom = nextFrame;
  renderRoom(now);
  composition(t, label, step);
  const png = await canvas.encode('png');
  if (index === 0) await fs.writeFile(path.join(directory, 'Continuum_Originale_Raumstudie_Anfang.png'), png);
  if (index === 116) await fs.writeFile(path.join(directory, 'Continuum_Originale_Raumstudie.png'), png);
  if (index === duration * fps - 1) await fs.writeFile(path.join(directory, 'Continuum_Originale_Raumstudie_Ende.png'), png);
  if (!encoder.stdin.write(png)) await once(encoder.stdin, 'drain');
  if (index % 48 === 0) console.log(`Rendered ${index}/${duration * fps} original frames`);
}
encoder.stdin.end();
const [exitCode] = await finished;
if (exitCode !== 0) throw new Error(`ffmpeg failed: ${encoderError}`);
await fs.writeFile(path.join(directory, 'handoff-3d/Originale_Raumstudie_Nachweis.json'), JSON.stringify({
  title: 'Continuum · Raumstudie · Originale', renderer: '@napi-rs/canvas native Canvas2D; not a browser recording',
  originalRepository: 'https://github.com/Ullmann27/device-ai', originalCommit: ORIGINAL_COMMIT,
  avatarSource: 'byte-identical original GLB rendered in Blender; intentionally static PNG presence',
  avatarSha256: 'c8b88385c73fa01596a5734d20859440b1ecaef06cb1abea6cd7a47e62b08b9a',
  orbSource: 'interop/orb-original-source.js, ContinuumOriginalClock and original geometry/material/state port',
  orbMetrics: metrics, roomSource: 'continuum-preview/scene.js with replacement orb disabled',
  video: { width, height, fps, frames: duration * fps, durationSeconds: duration, audio: false },
  notes: ['The head is a labelled still image, not evidence of live facial animation.', 'No fake audio samples were supplied.', 'Panels illustrate light/material/transition; they do not claim real document processing.', 'Original orb has continuous phase across state transitions.'],
}, null, 2));
console.log(`Completed ${output}`);
