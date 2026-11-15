/**
 * Device AI Continuum — browser adapter of the owner's original Kotlin orb.
 * Source repository: Ullmann27/device-ai
 * Pinned source: 1b90611388cc2b1978ddfe32064a25dd3a08cee1 (Code55)
 * Renderer blob: c9c6cf6602557abaa6cbb4595f0360c36a938fdb
 * The original renderer uses ContinuumPainter, not an OpenGL shader or a GLB.
 * Geometry, colours, depth splitting, network topology and state energies below
 * are translated from the originals retained alongside this source.
 * Browser rasterisation can differ from Android anti-aliasing; no pixel parity
 * or real-device performance result is claimed by this adapter.
 */

const TAU = Math.PI * 2;
const RAD = Math.PI / 180;
const clamp = (value, low, high) => Math.max(low, Math.min(high, value));
const { sin, cos, sqrt, abs, min, max, floor, exp, cbrt } = Math;

export const ORIGINAL_COMMIT = "1b90611388cc2b1978ddfe32064a25dd3a08cee1";
export const MODE = Object.freeze({ READY: 0, LISTENING: 1, THINKING: 2, SPEAKING: 3, RESEARCH: 4, LEARNING: 5 });
export const MODE_ENERGY = Object.freeze([.18, .42, .78, .12, .62, .52]);
export const GEOMETRY = Object.freeze({
  PHONE: Object.freeze({ centerY: .381, radiusOfWidth: .2455, radiusOfHeight: .329, pad: 1.11, wall: .33, brand: 1.545, tagline: 1.685, marks: 1.795, flat: 1, sphere: 1, haloNodes: 28, aura: .25, bloom: .6 }),
  FOLD: Object.freeze({ centerY: .495, radiusOfWidth: .2366, radiusOfHeight: .3334, pad: .99, wall: .1, brand: 1.27, tagline: 1.42, marks: null, flat: .62, sphere: 1.04, haloNodes: 72, aura: .35, bloom: 1 }),
  FOLD_PORTRAIT: Object.freeze({ centerY: .42, radiusOfWidth: .375, radiusOfHeight: .37, pad: .99, wall: .1, brand: 1.27, tagline: 1.42, marks: null, flat: .62, sphere: 1.04, haloNodes: 72, aura: .35, bloom: 1 }),
  REFERENCE_COCKPIT: Object.freeze({ centerY: .42, radiusOfWidth: .32, radiusOfHeight: .30, pad: .99, wall: .1, brand: 1.35, tagline: 1.58, marks: null, flat: .62, sphere: 1.04, haloNodes: 48, aura: .33, bloom: .88 }),
});

const Argb = Object.freeze({
  of(a, r, g, b) { return ((clamp(a, 0, 255) << 24) | (clamp(r, 0, 255) << 16) | (clamp(g, 0, 255) << 8) | clamp(b, 0, 255)) >>> 0; },
  rgb(r, g, b) { return this.of(255, r, g, b); },
  alpha(c) { return c >>> 24; },
  withAlpha(c, a) { return (((floor(clamp(a, 0, 1) * 255 + .5) << 24) | (c & 0xffffff)) >>> 0); },
  mix(a, b, t) {
    const k = clamp(t, 0, 1), channel = shift => floor(((a >>> shift) & 255) + (((b >>> shift) & 255) - ((a >>> shift) & 255)) * k + .5);
    return this.of(channel(24), channel(16), channel(8), channel(0));
  },
});
const cssColor = argb => `rgba(${(argb >>> 16) & 255},${(argb >>> 8) & 255},${argb & 255},${(argb >>> 24) / 255})`;
const Fill = Object.freeze({
  solid: argb => ({ type: "solid", argb, css: cssColor(argb) }),
  linear: (x0, y0, x1, y1, colors, stops = null) => ({ type: "linear", x0, y0, x1, y1, colors, stops }),
  radial: (cx, cy, radius, colors, stops = null) => ({ type: "radial", cx, cy, radius, colors, stops }),
  sweep: (cx, cy, colors, stops = null) => ({ type: "sweep", cx, cy, colors, stops }),
});

/** Canvas implementation of the original ContinuumPainter contract. */
export class BrowserContinuumPainter {
  constructor(context) { this.context = context; this.additive = false; }
  begin() { this.additive = false; this.context.globalCompositeOperation = "source-over"; }
  save() { this.context.save(); }
  restore() {
    this.context.restore();
    // Android saves Canvas transforms, but its separate Paint keeps xfermode.
    this.context.globalCompositeOperation = this.additive ? "lighter" : "source-over";
  }
  translate(x, y) { this.context.translate(x, y); }
  scale(x, y, px = 0, py = 0) { const c = this.context; c.translate(px, py); c.scale(x, y); c.translate(-px, -py); }
  rotate(degrees, px = 0, py = 0) { const c = this.context; c.translate(px, py); c.rotate(degrees * RAD); c.translate(-px, -py); }
  setAdditive(value) { this.additive = value; this.context.globalCompositeOperation = value ? "lighter" : "source-over"; }
  prepare(fill, alpha = 1, stroke = false, width = 1, round = true) {
    if (alpha <= 0) return false;
    const c = this.context;
    c.globalAlpha = clamp(alpha, 0, 1);
    c.lineCap = round ? "round" : "butt";
    c.lineJoin = "round";
    if (stroke) c.lineWidth = max(.0001, width);
    let style;
    if (fill.type === "solid") style = fill.css;
    else {
      // Browser gradients capture the current transform at creation; recreating
      // here preserves the Android shader's behaviour under breathing/parallax.
      if (fill.type === "linear") style = c.createLinearGradient(fill.x0, fill.y0, fill.x1, fill.y1);
      else if (fill.type === "radial") style = c.createRadialGradient(fill.cx, fill.cy, 0, fill.cx, fill.cy, fill.radius);
      else style = c.createConicGradient(0, fill.cx, fill.cy);
      fill.colors.forEach((color, i) => style.addColorStop(fill.stops ? fill.stops[i] : i / (fill.colors.length - 1), cssColor(color)));
    }
    if (stroke) c.strokeStyle = style; else c.fillStyle = style;
    return true;
  }
  fillRect(l, t, r, b, fill, alpha = 1) { if (this.prepare(fill, alpha)) this.context.fillRect(l, t, r - l, b - t); }
  ellipse(l, t, r, b, start = 0, sweep = TAU) { const c = this.context; c.beginPath(); c.ellipse((l + r) / 2, (t + b) / 2, max(0, (r - l) / 2), max(0, (b - t) / 2), 0, start, start + sweep); }
  fillOval(l, t, r, b, fill, alpha = 1) { if (!this.prepare(fill, alpha)) return; this.ellipse(l, t, r, b); this.context.fill(); }
  strokeOval(l, t, r, b, width, fill, alpha = 1) { if (!this.prepare(fill, alpha, true, width)) return; this.ellipse(l, t, r, b); this.context.stroke(); }
  fillCircle(x, y, radius, fill, alpha = 1) { this.fillOval(x - radius, y - radius, x + radius, y + radius, fill, alpha); }
  strokeCircle(x, y, radius, width, fill, alpha = 1) { this.strokeOval(x - radius, y - radius, x + radius, y + radius, width, fill, alpha); }
  strokeArc(l, t, r, b, start, sweep, width, fill, alpha = 1, round = true) { if (!this.prepare(fill, alpha, true, width, round)) return; this.ellipse(l, t, r, b, start * RAD, sweep * RAD); this.context.stroke(); }
  drawLine(x0, y0, x1, y1, width, fill, alpha = 1, round = true) { if (!this.prepare(fill, alpha, true, width, round)) return; const c = this.context; c.beginPath(); c.moveTo(x0, y0); c.lineTo(x1, y1); c.stroke(); }
  strokeSegments(points, count, width, fill, alpha) {
    if (!count || !this.prepare(fill, alpha, true, width, false)) return;
    const c = this.context; c.beginPath();
    for (let i = 0; i < count; i += 4) { c.moveTo(points[i], points[i + 1]); c.lineTo(points[i + 2], points[i + 3]); }
    c.stroke();
  }
  strokePath(points, width, fill, alpha = 1, close = false) {
    if (!points.length || !this.prepare(fill, alpha, true, width)) return;
    const c = this.context; c.beginPath(); c.moveTo(points[0], points[1]);
    for (let i = 2; i < points.length; i += 2) c.lineTo(points[i], points[i + 1]);
    if (close) c.closePath(); c.stroke();
  }
  fillRoundRect(l, t, r, b, radius, fill, alpha = 1) { if (!this.prepare(fill, alpha)) return; const c = this.context; c.beginPath(); c.roundRect(l, t, r - l, b - t, radius); c.fill(); }
  drawText(text, x, y, style, fill, alpha = 1) {
    if (!this.prepare(fill, alpha)) return;
    const c = this.context; c.font = `${style.weight} ${style.size}px "Space Grotesk", Manrope, sans-serif`; c.textAlign = "center"; c.textBaseline = "alphabetic";
    if ("letterSpacing" in c) c.letterSpacing = `${style.letterSpacing}px`;
    c.fillText(text, x, y);
    if ("letterSpacing" in c) c.letterSpacing = "0px";
  }
}

/** Original ContinuumOrbitalRing.kt: 72 segments, true depth-plane split. */
export class ContinuumOrbitalRing {
  constructor(radius, minor, tiltDegrees) {
    this.radius = radius; this.steps = 72; this.minorRatio = minor / radius;
    this.depthRatio = sqrt(1 - this.minorRatio * this.minorRatio); this.tilt = tiltDegrees * RAD;
    this.x = new Float32Array(73); this.y = new Float32Array(73); this.z = new Float32Array(73);
    this.back = new Float32Array(74 * 4); this.front = new Float32Array(74 * 4); this.backSize = 0; this.frontSize = 0;
  }
  project(phase, speed, cx, cy, scale) {
    this.backSize = this.frontSize = 0;
    const yaw = (phase - .4) * 1.25 * speed, pitch = .22 * sin((phase - .4) * .65 * speed);
    const c = cos(yaw), s = sin(yaw), cp = cos(pitch), sp = sin(pitch), ct = cos(this.tilt), st = sin(this.tilt);
    for (let i = 0; i <= this.steps; i++) {
      const t = (i % this.steps) * TAU / this.steps;
      const bx = cos(t) * this.radius, by = sin(t) * this.radius * this.minorRatio, bz = sin(t) * this.radius * this.depthRatio;
      const rx = bx * c + bz * s, rz = bz * c - bx * s, ry = by * cp - rz * sp;
      this.x[i] = rx * ct - ry * st; this.y[i] = rx * st + ry * ct; this.z[i] = by * sp + rz * cp;
    }
    for (let i = 0; i < this.steps; i++) {
      const j = i + 1;
      if ((this.z[i] >= 0) === (this.z[j] >= 0)) this.segment(this.x[i], this.y[i], this.z[i], this.x[j], this.y[j], this.z[j], this.z[i] >= 0, cx, cy, scale);
      else {
        const t = this.z[i] / (this.z[i] - this.z[j]);
        const mx = this.x[i] + (this.x[j] - this.x[i]) * t, my = this.y[i] + (this.y[j] - this.y[i]) * t;
        this.segment(this.x[i], this.y[i], this.z[i], mx, my, 0, this.z[i] >= 0, cx, cy, scale);
        this.segment(mx, my, 0, this.x[j], this.y[j], this.z[j], this.z[j] >= 0, cx, cy, scale);
      }
    }
  }
  segment(x0, y0, z0, x1, y1, z1, near, cx, cy, scale) {
    const out = near ? this.front : this.back, offset = near ? this.frontSize : this.backSize;
    const p0 = 8 / (8 - z0), p1 = 8 / (8 - z1);
    out[offset] = cx + x0 * scale * p0; out[offset + 1] = cy + y0 * scale * p0;
    out[offset + 2] = cx + x1 * scale * p1; out[offset + 3] = cy + y1 * scale * p1;
    if (near) this.frontSize += 4; else this.backSize += 4;
  }
  static lift(phase, radius) { return -.05 * radius * sin((phase - .4) * .85); }
}

const LAYER = Object.freeze({ OUTER: 0, INNER: 1, HALO: 2 });
const jitter = (i, salt) => { const value = sin(i * 12.9898 + salt * 78.233) * 43758.5453; return Math.fround(value - floor(value)); };

/** Original immutable Fibonacci shells, nearest-neighbour graph and 420 dust points. */
export class ContinuumHologramPlexus {
  constructor(haloNodes) {
    const outer = 128, inner = 48;
    this.nodeCount = outer + inner + haloNodes;
    this.layer = Uint8Array.from({ length: this.nodeCount }, (_, i) => i < outer ? LAYER.OUTER : i < outer + inner ? LAYER.INNER : LAYER.HALO);
    for (const name of ["bx", "by", "bz", "sx", "sy", "depth"]) this[name] = new Float32Array(this.nodeCount);
    this.dustCount = 420;
    for (const name of ["dx", "dy", "dz", "dustX", "dustY", "dustDepth", "dustSize"]) this[name] = new Float32Array(this.dustCount);
    this.dustTint = new Uint8Array(this.dustCount);
    this.arcCount = 8;
    for (const name of ["anx", "any", "anz", "arcStart", "arcSweep", "arcSpeed", "arcAngle", "arcRatio", "arcFrom", "arcSweepNow", "arcNormalX", "arcNormalY", "arcNormalZ"]) this[name] = new Float32Array(this.arcCount);
    this.arcMirror = new Uint8Array(this.arcCount);
    this.arcRadius = Float32Array.of(.62, .95, .78, .88, .7, .92, .83, .74);
    const place = (start, n, rMin, rMax, salt) => {
      for (let k = 0; k < n; k++) {
        const i = start + k, yy = 1 - 2 * (k + .5) / n, ring = sqrt(1 - yy * yy), a = k * Math.fround(2.39996323) + salt;
        let x = cos(a) * ring + (jitter(i, salt + 1) - .5) * .34;
        let y = yy + (jitter(i, salt + 2) - .5) * .34;
        let z = sin(a) * ring + (jitter(i, salt + 3) - .5) * .34;
        const len = sqrt(x * x + y * y + z * z), radius = rMin + (rMax - rMin) * jitter(i, salt + 4);
        x *= radius / len; y *= radius / len; z *= radius / len;
        this.bx[i] = x; this.by[i] = y; this.bz[i] = z;
      }
    };
    place(0, outer, .9, .97, 11); place(outer, inner, .5, .74, 23); place(outer + inner, haloNodes, 1.1, 1.7, 37);
    const pairs = new Set();
    const link = (i, first, end, k, maxDistance) => {
      const best = new Int32Array(k).fill(-1), bestD = new Float32Array(k).fill(3.4028234663852886e38);
      for (let j = first; j < end; j++) {
        if (j === i) continue;
        const dx = this.bx[i] - this.bx[j], dy = this.by[i] - this.by[j], dz = this.bz[i] - this.bz[j], d = dx * dx + dy * dy + dz * dz;
        if (d > maxDistance * maxDistance) continue;
        let slot = -1;
        for (let s = 0; s < k; s++) if (d < bestD[s] && (slot < 0 || bestD[s] > bestD[slot])) slot = s;
        if (slot >= 0) { best[slot] = j; bestD[slot] = d; }
      }
      for (const j of best) if (j >= 0) pairs.add(min(i, j) * 65536 + max(i, j));
    };
    for (let i = 0; i < outer; i++) link(i, 0, outer, 3, .55);
    for (let i = outer; i < outer + inner; i++) link(i, 0, outer + inner, 3, .7);
    for (let i = outer + inner; i < this.nodeCount; i++) link(i, outer + inner, this.nodeCount, 2, .8);
    this.edgeCount = pairs.size; this.edgeA = new Uint16Array(this.edgeCount); this.edgeB = new Uint16Array(this.edgeCount);
    let e = 0; for (const key of pairs) { this.edgeA[e] = floor(key / 65536); this.edgeB[e] = key % 65536; e++; }
    for (let i = 0; i < this.dustCount; i++) {
      const warm = i % 7 === 0; let x, y, z;
      if (warm) { x = .55 + (jitter(i, 51) - .5) * .7; y = -.45 + (jitter(i, 52) - .5) * .7; z = .45 + (jitter(i, 53) - .5) * .6; }
      else { const u = jitter(i, 54) * 2 - 1, t = jitter(i, 55) * TAU, s = sqrt(1 - u * u); x = s * cos(t); y = u; z = s * sin(t); }
      const len = max(sqrt(x * x + y * y + z * z), 1e-3), radius = warm ? min(.92, len) : .95 * cbrt(jitter(i, 56));
      this.dx[i] = x / len * radius; this.dy[i] = y / len * radius; this.dz[i] = z / len * radius;
      this.dustTint[i] = warm ? 2 : i % 5 === 0 ? 1 : 0; this.dustSize[i] = i % 13 === 0 ? 1.15 : i % 3 === 0 ? .72 : .45;
    }
    for (let i = 0; i < this.arcCount; i++) {
      const u = jitter(i, 61) * 2 - 1, t = jitter(i, 62) * TAU, s = sqrt(1 - u * u);
      this.anx[i] = s * cos(t); this.any[i] = u; this.anz[i] = s * sin(t);
      this.arcStart[i] = jitter(i, 63) * 360; this.arcSweep[i] = 70 + jitter(i, 64) * 80;
      this.arcSpeed[i] = (4 + jitter(i, 65) * 6) * (i % 2 === 0 ? 1 : -1);
    }
  }
  project(yaw, pitch, cx, cy, r, phase = 0) {
    const c = cos(yaw), s = sin(yaw), cp = cos(pitch), sp = sin(pitch);
    for (let i = 0; i < this.nodeCount; i++) {
      const x1 = this.bx[i] * c + this.bz[i] * s, z1 = -this.bx[i] * s + this.bz[i] * c;
      const y2 = this.by[i] * cp - z1 * sp, z2 = this.by[i] * sp + z1 * cp;
      this.sx[i] = cx + x1 * r; this.sy[i] = cy + y2 * r;
      const shell = sqrt(this.bx[i] ** 2 + this.by[i] ** 2 + this.bz[i] ** 2); this.depth[i] = clamp(z2 / shell, -1, 1);
    }
    for (let i = 0; i < this.dustCount; i++) {
      const x1 = this.dx[i] * c + this.dz[i] * s, z1 = -this.dx[i] * s + this.dz[i] * c;
      this.dustX[i] = cx + x1 * r; this.dustY[i] = cy + (this.dy[i] * cp - z1 * sp) * r;
      this.dustDepth[i] = clamp(this.dy[i] * sp + z1 * cp, -1, 1);
    }
    for (let i = 0; i < this.arcCount; i++) {
      const x1 = this.anx[i] * c + this.anz[i] * s, z1 = -this.anx[i] * s + this.anz[i] * c;
      const ny = this.any[i] * cp - z1 * sp, nz = this.any[i] * sp + z1 * cp, len = sqrt(x1 * x1 + ny * ny);
      this.arcNormalX[i] = x1; this.arcNormalY[i] = ny; this.arcNormalZ[i] = nz;
      this.arcAngle[i] = len < 1e-4 ? 0 : Math.atan2(x1 / len, -ny / len) / RAD;
      this.arcRatio[i] = abs(nz); this.arcMirror[i] = nz < 0 ? 1 : 0;
      this.arcFrom[i] = ((this.arcStart[i] + phase * this.arcSpeed[i]) % 360 + 360) % 360; this.arcSweepNow[i] = this.arcSweep[i];
    }
  }
}

/**
 * Draw order and every material stop mirror ContinuumHologramRenderer.kt.
 * showBrand defaults to false because the host already provides German labels.
 */
export function createContinuumOriginalRenderer(options = {}) {
  let geometry = options.geometry || GEOMETRY.REFERENCE_COCKPIT;
  let plexus = new ContinuumHologramPlexus(geometry.haloNodes);
  const showBrand = options.showBrand === true;
  const pageCount = Math.max(1, Math.floor(options.pageCount ?? 3));
  const pageIndex = clamp(Math.floor(options.pageIndex ?? 0), 0, pageCount - 1);
  const DEPTH_BUCKETS = 9, BOLTS = 7, BOLT_SEGMENTS = 6;
  let w = 0, h = 0, cx = 0, cy = 0, r = 0, scale = 1, rs = 0, sphereScale = 1;
  let padY = 0, baseTopY = 0, flangeTopY = 0, floorY = 0;
  let backdrop, rays, bloom, glass, glassCyan, glassViolet, rim, innerGlass, innerRim, innerSpecular, specular;
  let infinity, infinityGlow, beam, padGlow, padFace, baseFace, wall, rimLight, floorGlow, padCore, contactGlow;
  let orbitFills = [], floorFills = [], brandFill, taglineFill, coreHalo, aura, auraMagenta;
  const path = [], bolts = new Float32Array(BOLTS * BOLT_SEGMENTS * 4), unitGlows = new Map();
  const stars = Array.from({ length: 70 }, (_, i) => ({
    u: (i * .618034 + .137) % 1, v: (i * .754877 + .29) % 1,
    size: i % 9 === 0 ? 1.1 : i % 4 === 0 ? .7 : .42, tint: i % 3, flare: i % 11 === 0,
  }));
  const depthFills = Array.from({ length: DEPTH_BUCKETS }, (_, i) => {
    const t = i / (DEPTH_BUCKETS - 1);
    return Fill.solid(t < .5 ? Argb.mix(Argb.rgb(96, 78, 214), Argb.rgb(58, 138, 255), t * 2) : Argb.mix(Argb.rgb(58, 138, 255), Argb.rgb(178, 236, 255), t * 2 - 1));
  });
  const nodeCool = Fill.solid(Argb.rgb(196, 242, 255)), nodeViolet = Fill.solid(Argb.rgb(184, 150, 255)), nodePink = Fill.solid(Argb.rgb(255, 140, 220));
  const dustFills = [Fill.solid(Argb.rgb(150, 220, 255)), Fill.solid(Argb.rgb(170, 140, 255)), Fill.solid(Argb.rgb(255, 120, 170)), Fill.solid(Argb.rgb(255, 160, 96))];
  const pulseFill = Fill.solid(Argb.rgb(236, 250, 255)), haloFill = Fill.solid(Argb.rgb(110, 170, 255)), accentFill = Fill.solid(Argb.rgb(236, 112, 226));
  const orbitCore = Fill.solid(Argb.rgb(232, 248, 255)), orbitSpark = Fill.solid(Argb.rgb(235, 250, 255));
  const rimEdge = Fill.solid(Argb.rgb(215, 248, 255)), rimHighlight = Fill.solid(Argb.rgb(230, 250, 255));
  const infinityCore = Fill.solid(Argb.rgb(70, 80, 190)), infinityHighlight = Fill.solid(Argb.rgb(240, 252, 255));
  const stateCyan = Fill.solid(Argb.rgb(90, 225, 255)), stateViolet = Fill.solid(Argb.rgb(160, 120, 255)), speechCore = Fill.solid(Argb.rgb(204, 248, 255));
  const sparkLine = Fill.solid(Argb.rgb(235, 250, 255)), sparkCore = Fill.solid(Argb.rgb(248, 253, 255));
  const arcColors = [Argb.rgb(120, 225, 255), Argb.rgb(90, 150, 255), Argb.rgb(180, 130, 255), Argb.rgb(240, 120, 215)];
  const arcFills = arcColors.map(Fill.solid), arcCores = arcColors.map(c => Fill.solid(Argb.mix(c, Argb.rgb(255, 255, 255), .55)));
  const orbits = [
    { tilt: -11, a: 1.42, b: .34, speed: 1, colors: [Argb.rgb(80, 225, 255), Argb.rgb(120, 170, 255), Argb.rgb(210, 110, 255)], width: 1.5 },
    { tilt: 22, a: 1.32, b: .48, speed: -.8, colors: [Argb.rgb(120, 140, 255), Argb.rgb(190, 110, 255), Argb.rgb(255, 120, 210)], width: 1.25 },
    { tilt: -36, a: 1.22, b: .42, speed: .65, colors: [Argb.rgb(230, 100, 220), Argb.rgb(150, 120, 255), Argb.rgb(80, 210, 255)], width: 1.05 },
    { tilt: 74, a: 1.14, b: .3, speed: -.55, colors: [Argb.rgb(80, 215, 255), Argb.rgb(150, 150, 255), Argb.rgb(90, 225, 255)], width: .95 },
    { tilt: 7, a: 1.18, b: .6, speed: .45, colors: [Argb.rgb(90, 230, 255), Argb.rgb(150, 160, 255), Argb.rgb(240, 110, 220)], width: 1.1 },
  ];
  const orbitalGeometry = orbits.map(o => new ContinuumOrbitalRing(o.a, o.b, o.tilt));
  const coreOrbits = [new ContinuumOrbitalRing(.35, .17, -28), new ContinuumOrbitalRing(.32, .12, 62)];

  function layout(width, height) {
    if (width === w && height === h) return;
    w = width; h = height;
    r = min(width * geometry.radiusOfWidth, height * geometry.radiusOfHeight);
    cx = width / 2; cy = height * geometry.centerY;
    scale = r / 101; rs = r * geometry.sphere; sphereScale = rs / 101;
    padY = cy + r * geometry.pad;
    baseTopY = cy + r * (geometry.pad + .06);
    flangeTopY = cy + r * (geometry.pad + .06 + geometry.wall);
    floorY = cy + r * (geometry.pad + .13 + geometry.wall);
    backdrop = Fill.radial(cx, cy + r * .15, r * 2.1,
      [Argb.of(175, 24, 70, 175), Argb.of(110, 14, 46, 128), Argb.of(40, 8, 24, 70), 0], [0, .36, .68, 1]);
    rays = Fill.linear(0, cy - r * 1.25, 0, cy - r * .2,
      [0, Argb.of(90, 80, 170, 255), Argb.of(0, 80, 170, 255)], [0, .55, 1]);
    bloom = Fill.radial(cx, cy, rs * 1.34,
      [0, 0, Argb.of(190, 70, 150, 255), Argb.of(105, 80, 115, 255), Argb.of(36, 90, 90, 255), 0], [0, .68, .75, .82, .91, 1]);
    aura = Fill.radial(cx - rs * .3, cy, rs * 1.7,
      [0, 0, Argb.of(150, 18, 120, 225), Argb.of(80, 16, 92, 190), 0], [0, .45, .55, .75, 1]);
    auraMagenta = Fill.radial(cx + rs * .35, cy - rs * .05, rs * 1.5,
      [0, 0, Argb.of(120, 130, 50, 215), Argb.of(50, 100, 40, 175), 0], [0, .5, .6, .78, 1]);
    glass = Fill.radial(cx, cy + rs * .08, rs,
      [Argb.of(244, 12, 26, 104), Argb.of(246, 8, 20, 84), Argb.of(248, 10, 28, 102), Argb.of(250, 28, 62, 180), Argb.of(252, 58, 96, 240)], [0, .5, .78, .92, 1]);
    glassCyan = Fill.radial(cx - rs * .66, cy + rs * .1, rs * .8,
      [Argb.of(80, 30, 190, 255), Argb.of(26, 30, 160, 255), 0], [0, .55, 1]);
    glassViolet = Fill.radial(cx + rs * .6, cy - rs * .1, rs * .85,
      [Argb.of(110, 140, 60, 240), Argb.of(36, 110, 50, 225), 0], [0, .55, 1]);
    coreHalo = Fill.radial(cx, cy, rs * .68,
      [0, 0, Argb.of(205, 40, 132, 255), Argb.of(110, 40, 100, 245), 0], [0, .55, .68, .82, 1]);
    rim = Fill.sweep(cx, cy,
      [Argb.rgb(250, 96, 222), Argb.rgb(150, 112, 255), Argb.rgb(70, 170, 255), Argb.rgb(60, 222, 255), Argb.rgb(150, 236, 255), Argb.rgb(226, 242, 255), Argb.rgb(200, 150, 255), Argb.rgb(250, 96, 222)], [0, .16, .32, .46, .62, .74, .87, 1]);
    innerGlass = Fill.radial(cx - rs * .08, cy - rs * .1, rs * .44,
      [Argb.of(205, 34, 48, 176), Argb.of(195, 22, 30, 124), Argb.of(200, 36, 62, 198), Argb.of(230, 86, 146, 255)], [0, .55, .88, 1]);
    innerRim = Fill.sweep(cx, cy,
      [Argb.rgb(170, 110, 255), Argb.rgb(90, 160, 255), Argb.rgb(120, 225, 255), Argb.rgb(205, 235, 255), Argb.rgb(200, 110, 255), Argb.rgb(170, 110, 255)], [0, .25, .5, .7, .85, 1]);
    innerSpecular = Fill.radial(cx - rs * .14, cy - rs * .22, rs * .2,
      [Argb.of(130, 200, 225, 255), Argb.of(26, 150, 185, 255), 0], [0, .5, 1]);
    specular = Fill.radial(cx - rs * .45, cy - rs * .55, rs * .5,
      [Argb.of(110, 190, 230, 255), Argb.of(26, 120, 190, 255), 0], [0, .45, 1]);
    infinity = Fill.linear(cx - rs * .32, cy, cx + rs * .32, cy,
      [Argb.rgb(70, 222, 255), Argb.rgb(120, 146, 255), Argb.rgb(214, 100, 255)]);
    infinityGlow = Fill.radial(cx, cy + rs * .1, rs * .5,
      [Argb.of(120, 80, 120, 255), Argb.of(50, 80, 90, 245), 0], [0, .5, 1]);
    beam = Fill.linear(0, cy - rs, 0, padY + r * .02,
      [Argb.of(0, 170, 235, 255), Argb.of(150, 170, 235, 255), Argb.of(255, 220, 250, 255)], [0, .55, 1]);
    padGlow = Fill.radial(cx, padY, r * .8,
      [Argb.of(255, 230, 252, 255), Argb.of(245, 110, 220, 255), Argb.of(200, 30, 130, 245), Argb.of(0, 20, 80, 200)], [0, .18, .55, 1]);
    padFace = Fill.radial(cx, padY + r * .02, r,
      [Argb.rgb(40, 120, 230), Argb.rgb(14, 42, 104), Argb.rgb(10, 24, 62)], [0, .6, 1]);
    baseFace = Fill.linear(cx - r * 1.45, 0, cx + r * 1.45, 0,
      [Argb.rgb(14, 34, 80), Argb.rgb(6, 12, 34), Argb.rgb(7, 12, 36), Argb.rgb(30, 22, 74)]);
    wall = Fill.linear(cx - r * 1.45, 0, cx + r * 1.45, 0,
      [Argb.rgb(14, 34, 76), Argb.rgb(5, 10, 28), Argb.rgb(3, 7, 22), Argb.rgb(6, 9, 26), Argb.rgb(34, 20, 72)], [0, .25, .5, .75, 1]);
    rimLight = Fill.linear(cx - r * 1.55, 0, cx + r * 1.55, 0,
      [Argb.rgb(60, 205, 255), Argb.rgb(70, 160, 255), Argb.rgb(120, 140, 255), Argb.rgb(210, 100, 240)], [0, .35, .65, 1]);
    floorGlow = Fill.radial(cx, flangeTopY + r * .02, r * 2.2,
      [Argb.of(90, 40, 120, 240), Argb.of(35, 30, 70, 180), 0], [0, .45, 1]);
    padCore = Fill.radial(cx, padY, r * .32, [Argb.of(255, 250, 255, 255), Argb.of(0, 160, 230, 255)]);
    contactGlow = Fill.radial(cx, padY - r * .05, r * .78,
      [Argb.of(235, 225, 250, 255), Argb.of(150, 90, 200, 255), Argb.of(0, 40, 120, 255)], [0, .35, 1]);
    orbitFills = orbits.map(o => Fill.linear(cx - rs * o.a, cy, cx + rs * o.a, cy, o.colors));
    floorFills = Array.from({ length: 4 }, (_, i) => {
      const rx = r * (1.72 + i * .2), color = i % 2 === 0 ? Argb.rgb(60, 180, 255) : Argb.rgb(140, 110, 255);
      return Fill.linear(cx - rx, 0, cx + rx, 0, [color, Argb.rgb(40, 90, 200), Argb.rgb(190, 90, 240)]);
    });
    brandFill = Fill.solid(Argb.rgb(222, 232, 248)); taglineFill = Fill.solid(Argb.rgb(178, 194, 222));
  }

  function draw(p, f, transparentBackdrop = true) {
    if (r <= 0) return;
    if (!transparentBackdrop) {
      if (geometry === GEOMETRY.FOLD_PORTRAIT) { const reach = r * 2.1, center = cy + r * .15; p.fillRect(cx - reach, center - reach, cx + reach, center + reach, backdrop); }
      else p.fillRect(0, 0, w, h, backdrop);
    }
    drawRays(p, f); drawStars(p, f);
    p.fillCircle(cx - rs * .3, cy, rs * 1.7, aura, geometry.aura);
    p.fillCircle(cx + rs * .35, cy - rs * .05, rs * 1.5, auraMagenta, geometry.aura);
    p.fillOval(cx - r * 2.2, flangeTopY - r * .3, cx + r * 2.2, flangeTopY + r * .35, floorGlow);
    drawFloor(p, f); drawPedestal(p, f);
    const lift = ContinuumOrbitalRing.lift(f.phase, rs);
    p.save(); p.translate(0, lift);
    plexus.project(.4 * .16 + (f.phase - .4) * .52, .3 + .05 * sin(.4 * .21) + .08 * (sin(f.phase * .28) - sin(.4 * .28)), cx, cy, rs, f.phase);
    p.setAdditive(true); drawHalo(p, f); drawOrbits(p, f, false);
    p.setAdditive(false); drawSphere(p, f);
    p.setAdditive(true); drawOrbits(p, f, true); drawSparkles(p, f); p.restore();
    const contactY = padY - r * .05, contactBreath = .4 + f.energy * .1 + .065 * (sin(f.phase * .8) - sin(.4 * .8));
    p.fillOval(cx - r * .78, contactY - r * .14, cx + r * .78, contactY + r * .14, contactGlow, clamp(contactBreath + lift / rs * 1.8, .2, .65));
    p.setAdditive(false);
    if (showBrand) drawBrand(p);
  }
  function drawRays(p, f) {
    for (let i = 0; i < 9; i++) {
      const x = cx + (i - 4) * r * .19 + sin(i * 1.7) * r * .05, top = cy - r * (1.05 + (i % 3) * .12);
      const alpha = (.18 + .1 * sin(f.phase * .4 + i)) * (i % 2 === 0 ? 1 : .6);
      p.drawLine(x, top, x, cy - r * .35, .55 * scale, rays, alpha);
    }
  }
  function drawStars(p, f) {
    stars.forEach((s, i) => {
      const x = s.u * w, y = s.v * h * .86, dx = (x - cx) / r, dy = (y - cy) / r;
      if (dx * dx + dy * dy < 1.15) return;
      const twinkle = .55 + .45 * sin(f.phase * (.6 + (i % 5) * .13) + i);
      const color = s.tint === 0 ? Argb.rgb(150, 210, 255) : s.tint === 1 ? Argb.rgb(110, 160, 255) : Argb.rgb(205, 160, 255), fill = Fill.solid(color);
      p.fillCircle(x, y, s.size * .6, fill, twinkle * .9);
      if (s.flare) { glow(p, x, y, 2.8, Argb.withAlpha(color, .5), twinkle); p.drawLine(x - 3.6, y, x + 3.6, y, .35, fill, twinkle * .75); p.drawLine(x, y - 3.6, x, y + 3.6, .35, fill, twinkle * .75); }
    });
  }
  function drawFloor(p, f) {
    const y = floorY, reference = geometry === GEOMETRY.REFERENCE_COCKPIT;
    for (let i = 0; i <= 3; i++) { const rx = r * (1.72 + i * .2), ry = r * (.25 + i * .045) * geometry.flat; p.strokeOval(cx - rx, y - ry, cx + rx, y + ry, .7 * scale, floorFills[i], reference ? .22 - i * .045 : .5 - i * .1); }
    const rx = r * 1.72, ry = r * .25 * geometry.flat;
    p.strokeArc(cx - rx, y - ry, cx + rx, y + ry, 15 + f.phase * 12, 60, 1.1 * scale, Fill.solid(Argb.rgb(150, 230, 255)), reference ? .24 : .55);
  }
  function drawPedestal(p, f) {
    const light = .8 + f.energy * .2;
    drawTier(p, r * 1.56, r * .2 * geometry.flat, flangeTopY, r * .06, baseFace, .8 * light);
    drawTier(p, r * 1.43, r * .17 * geometry.flat, baseTopY, r * geometry.wall, baseFace, .95 * light);
    drawTier(p, r * .97, r * .125 * geometry.flat, padY, r * .07, padFace, light);
    p.fillOval(cx - r * .86, padY - r * .118, cx + r * .86, padY + r * .118, padGlow);
    p.fillOval(cx - r * .32, padY - r * .05, cx + r * .32, padY + r * .05, padCore, light);
    for (let i = 1; i <= 4; i++) { const rx = r * (.9 - i * .16), ry = r * (.112 - i * .02); p.strokeOval(cx - rx, padY - ry, cx + rx, padY + ry, .7 * scale, Fill.solid(Argb.rgb(200, 248, 255)), (.75 - i * .12) * light); }
  }
  function drawTier(p, rx, ry, topY, height, face, rimAlpha) {
    const bottomY = topY + height;
    p.fillRect(cx - rx, topY, cx + rx, bottomY, wall); p.fillOval(cx - rx, bottomY - ry, cx + rx, bottomY + ry, wall);
    p.strokeArc(cx - rx, bottomY - ry, cx + rx, bottomY + ry, 5, 170, scale, rimLight, rimAlpha * .75);
    p.fillOval(cx - rx, topY - ry, cx + rx, topY + ry, face);
    p.strokeOval(cx - rx, topY - ry, cx + rx, topY + ry, 2.4 * scale, rimLight, rimAlpha * .18);
    p.strokeOval(cx - rx, topY - ry, cx + rx, topY + ry, .9 * scale, rimLight, rimAlpha);
    p.strokeArc(cx - rx, topY - ry, cx + rx, topY + ry, 20, 140, 1.4 * scale, Fill.solid(Argb.rgb(170, 240, 255)), rimAlpha * .8);
  }
  function drawOrbits(p, f, front) {
    orbits.forEach((o, i) => {
      const ring = orbitalGeometry[i]; if (!front) ring.project(f.phase, o.speed, cx, cy, rs);
      const points = front ? ring.front : ring.back, count = front ? ring.frontSize : ring.backSize, k = front ? 1 : .42, wd = o.width * sphereScale, fill = orbitFills[i];
      p.strokeSegments(points, count, wd * 6.5, fill, .05 * k); p.strokeSegments(points, count, wd * 3.2, fill, .12 * k);
      p.strokeSegments(points, count, wd * 1.5, fill, .5 * k); p.strokeSegments(points, count, wd * .6, orbitCore, .5 * k);
    });
  }
  function drawSphere(p, f) {
    const breath = 1 + sin(f.phase * 1.4) * (.006 + f.energy * .008) + f.audio * .02;
    p.save(); p.scale(breath, breath, cx, cy);
    p.fillCircle(cx, cy, rs * 1.3, bloom, geometry.bloom); p.fillCircle(cx, cy, rs, glass); p.fillCircle(cx, cy, rs, glassCyan); p.fillCircle(cx, cy, rs, glassViolet);
    p.setAdditive(true); p.fillCircle(cx, cy, rs * .68, coreHalo, .85 + f.energy * .15); drawPlexus(p, f, false); drawLightning(p, f);
    for (let i = 0; i < 15; i++) {
      const t = (i - 7) / 7, x0 = cx + t * r * .5, x1 = cx + t * rs * .16, alpha = (.22 - abs(t) * .1) * (.8 + .2 * sin(f.phase * .9 + i));
      p.drawLine(x0, padY - r * .05 - ContinuumOrbitalRing.lift(f.phase, rs), x1, cy + rs * .12, (i % 3 === 0 ? 1.1 : .6) * scale, beam, alpha);
    }
    p.drawLine(cx, cy - rs * .98, cx, padY - ContinuumOrbitalRing.lift(f.phase, rs), 4.5 * scale, beam, .15 + f.energy * .08);
    p.drawLine(cx, cy - rs * .98, cx, padY - ContinuumOrbitalRing.lift(f.phase, rs), 1.1 * scale, beam, .5); drawStateEffects(p, f); p.setAdditive(false);
    p.fillCircle(cx, cy + rs * .05, rs * .62, infinityGlow, .32 + f.energy * .2); p.fillCircle(cx, cy, rs * .4, innerGlass);
    p.strokeCircle(cx, cy, rs * .4, rs * .06, innerRim, .18); p.strokeCircle(cx, cy, rs * .4, rs * .02, innerRim, .95);
    p.save(); p.translate(sin(f.phase * .83) * rs * .08, cos(f.phase * .61) * rs * .035);
    p.fillOval(cx - rs * .3, cy - rs * .36, cx + rs * .02, cy - rs * .12, innerSpecular); p.restore();
    drawEnergyCore(p, f, false); drawInfinity(p, f); drawEnergyCore(p, f, true); p.setAdditive(true); drawPlexus(p, f, true);
    p.strokeCircle(cx, cy, rs * 1.01, rs * .16, rim, .05); p.strokeCircle(cx, cy, rs, rs * .08, rim, .1); p.strokeCircle(cx, cy, rs, rs * .036, rim, .3);
    p.strokeCircle(cx, cy, rs * .993, rs * .01, rimEdge, .18); p.strokeArc(cx - rs * .955, cy - rs * .955, cx + rs * .955, cy + rs * .955, 196, 72, rs * .022, rimHighlight, .4);
    p.fillOval(cx - rs * .82, cy - rs * .9, cx - rs * .05, cy - rs * .32, specular); p.setAdditive(false); p.restore();
  }
  function lightningHash(i, j) {
    let value = (Math.imul(i, 374761393) + Math.imul(j, 668265263)) | 0;
    value = Math.imul(value ^ (value >>> 13), 1274126177);
    return ((value ^ (value >>> 16)) & 0xffff) / 65535;
  }
  function drawLightning(p, f) {
    const clock = f.phase * 2.4, epoch = floor(clock), flicker = max(0, sin((clock - epoch) * Math.PI));
    const k = flicker * (.35 + .65 * f.energy + .3 * f.weights[MODE.THINKING]); if (k <= .02) return;
    let n = 0;
    for (let b = 0; b < BOLTS; b++) {
      const angle = lightningHash(b, epoch) * TAU, ux = cos(angle), uy = sin(angle); let x = cx + ux * rs * .36, y = cy + uy * rs * .36;
      for (let s = 1; s <= BOLT_SEGMENTS; s++) {
        const radius = rs * (.36 + .6 * s / BOLT_SEGMENTS), j = (lightningHash(b * 31 + s, epoch) - .5) * rs * .16;
        const nx = cx + ux * radius - uy * j, ny = cy + uy * radius + ux * j;
        bolts[n] = x; bolts[n + 1] = y; bolts[n + 2] = nx; bolts[n + 3] = ny; n += 4; x = nx; y = ny;
      }
    }
    p.strokeSegments(bolts, n, 3.4 * sphereScale, stateViolet, .22 * k); p.strokeSegments(bolts, n, 1.4 * sphereScale, stateCyan, .55 * k); p.strokeSegments(bolts, n, .55 * sphereScale, pulseFill, .9 * k);
  }
  function drawEnergyCore(p, f, front) {
    const pulse = .78 + .12 * sin(f.phase * 2.1) + f.energy * .1; p.setAdditive(true);
    coreOrbits.forEach((orbit, i) => {
      if (!front) orbit.project(f.phase, i === 0 ? 1.35 : -.95, cx, cy, rs);
      const points = front ? orbit.front : orbit.back, count = front ? orbit.frontSize : orbit.backSize, opacity = (front ? .62 : .2) * pulse, fill = i === 0 ? stateCyan : stateViolet;
      p.strokeSegments(points, count, 3.2 * sphereScale, fill, opacity * .16); p.strokeSegments(points, count, .75 * sphereScale, fill, opacity);
      for (let packet = 0; packet <= 2; packet++) { if (count < 4) continue; const position = (f.phase * (i === 0 ? .22 : .16) + packet / 3) % 1, offset = floor((count / 4 - 1) * position) * 4; p.fillCircle(points[offset], points[offset + 1], 1.15 * sphereScale, orbitSpark, opacity); }
    }); p.setAdditive(false);
  }
  const depthFill = t => depthFills[clamp(floor(t * (DEPTH_BUCKETS - 1) + .5), 0, DEPTH_BUCKETS - 1)];
  function drawPlexus(p, f, front) {
    const px = plexus, intensity = .82 + f.weights[MODE.THINKING] * .25 + f.weights[MODE.LEARNING] * .18, core = rs * .42;
    drawArcs(p, front, intensity);
    for (let e = 0; e < px.edgeCount; e++) {
      const a = px.edgeA[e], b = px.edgeB[e]; if (px.layer[a] === LAYER.HALO) continue;
      const d = (px.depth[a] + px.depth[b]) * .5; if ((d >= 0) !== front) continue;
      const t = (d + 1) * .5; let alpha = (.08 + .8 * t * t) * intensity;
      if (front) { const mx = (px.sx[a] + px.sx[b]) * .5 - cx, my = (px.sy[a] + px.sy[b]) * .5 - cy; if (mx * mx + my * my < core * core) alpha *= .3; }
      p.drawLine(px.sx[a], px.sy[a], px.sx[b], px.sy[b], (.36 + .74 * t) * sphereScale, e % 6 === 0 ? accentFill : depthFill(t), alpha);
    }
    if (front) for (let e = 0; e < px.edgeCount; e += 7) {
      const a = px.edgeA[e], b = px.edgeB[e]; if (px.layer[a] === LAYER.HALO || px.depth[a] + px.depth[b] < 0) continue;
      const t = (f.phase * .3 + e * .618034) % 1, t1 = min(1, t + .16);
      p.drawLine(px.sx[a] + (px.sx[b] - px.sx[a]) * t, px.sy[a] + (px.sy[b] - px.sy[a]) * t, px.sx[a] + (px.sx[b] - px.sx[a]) * t1, px.sy[a] + (px.sy[b] - px.sy[a]) * t1, .9 * sphereScale, pulseFill, .75 * intensity * sin(t * Math.PI));
    }
    for (let i = 0; i < px.nodeCount; i++) {
      const layer = px.layer[i], d = px.depth[i]; if (layer === LAYER.HALO || (d >= 0) !== front) continue;
      const t = (d + 1) * .5, fill = i % 11 === 0 ? nodePink : layer === LAYER.INNER ? nodeViolet : nodeCool;
      p.fillCircle(px.sx[i], px.sy[i], (.4 + 1.05 * t) * sphereScale, fill, (.12 + .85 * t) * intensity);
      if (front && t > .78 && i % 3 === 0) glow(p, px.sx[i], px.sy[i], 3.6 * sphereScale, Argb.withAlpha(fill.argb, .6), intensity);
    }
    for (let i = 0; i < px.dustCount; i++) {
      const d = px.dustDepth[i]; if ((d >= 0) !== front) continue;
      const t = (d + 1) * .5, twinkle = .5 + .5 * sin(f.phase * (.9 + (i % 7) * .11) + i * 1.3);
      const fill = px.dustTint[i] === 2 ? dustFills[i % 2 === 0 ? 2 : 3] : px.dustTint[i] === 1 ? dustFills[1] : dustFills[0];
      p.fillCircle(px.dustX[i], px.dustY[i], px.dustSize[i] * (.75 + .55 * t) * sphereScale, fill, (.35 + .65 * t) * (.5 + .5 * twinkle));
      if (front && i % 17 === 0) glow(p, px.dustX[i], px.dustY[i], 3.4 * sphereScale, Argb.withAlpha(fill.argb, .5), twinkle);
    }
  }
  function drawArcs(p, front, intensity) {
    const px = plexus;
    for (let i = 0; i < px.arcCount; i++) {
      const from = px.arcFrom[i], to = from + px.arcSweepNow[i]; let windowStart = front ? 0 : 180;
      while (windowStart < to) { const a = max(from, windowStart), b = min(to, windowStart + 180); if (b > a) drawArc(p, i, a, b, front, intensity); windowStart += 360; }
    }
  }
  function drawArc(p, i, from, to, front, intensity) {
    const px = plexus, a = rs * px.arcRadius[i], b = a * px.arcRatio[i], start = px.arcMirror[i] ? -to : from, fill = arcFills[i % arcFills.length], k = (front ? 1 : .32) * intensity;
    p.save(); p.rotate(px.arcAngle[i], cx, cy);
    p.strokeArc(cx - a, cy - b, cx + a, cy + b, start, to - from, 3.2 * sphereScale, fill, .12 * k);
    p.strokeArc(cx - a, cy - b, cx + a, cy + b, start, to - from, 1.2 * sphereScale, fill, .45 * k);
    if (front) p.strokeArc(cx - a, cy - b, cx + a, cy + b, start, to - from, .5 * sphereScale, arcCores[i % arcCores.length], .9 * k);
    p.restore();
  }
  function drawHalo(p, f) {
    const px = plexus, k = geometry.aura;
    for (let e = 0; e < px.edgeCount; e++) {
      const a = px.edgeA[e], b = px.edgeB[e]; if (px.layer[a] !== LAYER.HALO) continue;
      const t = ((px.depth[a] + px.depth[b]) * .5 + 1) * .5;
      p.drawLine(px.sx[a], px.sy[a], px.sx[b], px.sy[b], (.4 + .45 * t) * sphereScale, haloFill, (.12 + .35 * t) * k);
    }
    for (let i = 0; i < px.nodeCount; i++) {
      if (px.layer[i] !== LAYER.HALO) continue;
      const t = (px.depth[i] + 1) * .5, twinkle = .6 + .4 * sin(f.phase * (.8 + (i % 5) * .15) + i);
      p.fillCircle(px.sx[i], px.sy[i], (.5 + .7 * t) * sphereScale, i % 4 === 0 ? nodePink : nodeCool, (.3 + .6 * t) * twinkle * k);
      if (i % 5 === 0) glow(p, px.sx[i], px.sy[i], 3 * sphereScale, Argb.of(150, 150, 210, 255), twinkle * k);
    }
  }
  function drawInfinity(p, f) {
    const a = rs * .29, b = rs * .135, oy = cy + rs * .02; path.length = 0;
    for (let i = 0; i <= 96; i++) { const t = i * TAU / 96; path.push(cx + a * sin(t), oy + b * sin(t) * cos(t) * 2); }
    const pulse = .85 + f.energy * .15 + f.audio * .2;
    p.setAdditive(true); p.strokePath(path, rs * .17, infinity, .22 * pulse, true); p.setAdditive(false);
    p.strokePath(path, rs * .085, infinity, 1, true); p.strokePath(path, rs * .032, infinityCore, .55, true); p.strokePath(path, rs * .011, infinityHighlight, .6 * pulse, true);
  }
  function drawStateEffects(p, f) {
    const listening = f.weights[MODE.LISTENING], thinking = f.weights[MODE.THINKING], speaking = f.weights[MODE.SPEAKING], research = f.weights[MODE.RESEARCH], learning = f.weights[MODE.LEARNING];
    if (listening > .02) for (let i = 0; i <= 2; i++) { const progress = (f.phase * .5 + i / 3) % 1, radius = rs * (.45 + (1 - progress) * .5); p.strokeCircle(cx, cy, radius, 1.1 * sphereScale, stateCyan, listening * progress * .7); }
    if (thinking > .02) {
      p.save(); p.rotate(f.phase * 44, cx, cy);
      for (let i = 0; i <= 2; i++) { const radius = rs * (.5 + i * .12); p.strokeArc(cx - radius, cy - radius, cx + radius, cy + radius, i * 110, 78, 1.7 * sphereScale, i === 1 ? stateCyan : stateViolet, thinking * .85); }
      p.restore();
    }
    if (speaking > .02 && f.audio > 0) {
      for (let line = 0; line <= 2; line++) {
        path.length = 0;
        for (let i = 0; i <= 64; i++) { const x = cx - rs * .62 + i * rs * 1.24 / 64, taper = sin(i * Math.PI / 64), wave = sin(i * .27 - f.phase * 8 + line * .8) * taper; path.push(x, cy + rs * .58 + (line - 1) * 4 * sphereScale + wave * f.audio * rs * (.12 - line * .02)); }
        p.strokePath(path, .9 * sphereScale, line === 1 ? speechCore : stateCyan, speaking * (.4 + f.audio * .6));
      }
    }
    if (research > .02) {
      p.save(); p.rotate(f.phase * 34, cx, cy); const rr = rs * .78;
      p.strokeArc(cx - rr, cy - rr, cx + rr, cy + rr, -65, 105, 1.8 * sphereScale, stateCyan, research * .85); p.drawLine(cx, cy, cx + rr * .7, cy - rr * .7, .8 * sphereScale, stateCyan, research * .5); p.restore();
    }
    if (learning > .02) for (let i = 0; i < 12; i++) { const progress = (f.phase * .24 + i / 12) % 1, x = cx + sin(i * 2.4 + progress * 3) * rs * (.55 - progress * .35), y = cy + rs * 1.05 - progress * rs * 1.05; p.fillCircle(x, y, (1.3 + progress * .6) * sphereScale, i % 2 === 0 ? stateViolet : stateCyan, learning * sin(progress * Math.PI) * .9); }
  }
  function drawSparkles(p, f) {
    const spots = [-.93, -.3, .97, -.2, -.55, .84, .62, .8, .02, -1, .38, -.93];
    for (let i = 0; i < spots.length / 2; i++) {
      const x = cx + spots[i * 2] * rs, y = cy + spots[i * 2 + 1] * rs, pulse = .6 + .4 * sin(f.phase * (1.1 + i * .2) + i * 1.3), size = rs * (.03 + (i % 2) * .018) * (1 + f.energy * .4);
      glow(p, x, y, size * 3.6, Argb.of(170, 175, 232, 255), pulse);
      p.drawLine(x - size * 3.2, y, x + size * 3.2, y, max(.4, size * .16), sparkLine, pulse * .8); p.drawLine(x, y - size * 3.2, x, y + size * 3.2, max(.4, size * .16), sparkLine, pulse * .8);
      p.fillCircle(x, y, size * .6, sparkCore, pulse);
    }
  }
  function drawBrand(p) {
    const cockpit = geometry === GEOMETRY.REFERENCE_COCKPIT;
    const brand = cockpit ? { weight: 600, size: max(11.5, 13 * scale), letterSpacing: 1.5 } : { weight: 400, size: 11.83 * scale, letterSpacing: 11.83 * .456 * scale };
    const tagline = cockpit ? { weight: 500, size: max(9.5, 10 * scale), letterSpacing: .8 } : { weight: 500, size: 7.76 * scale, letterSpacing: 7.76 * .476 * scale };
    p.drawText(cockpit ? "KI-KERN" : "CONTINUUM", cx + brand.letterSpacing / 2, cy + r * geometry.brand, brand, brandFill);
    p.drawText(cockpit ? "CONTINUUM" : "INTELLIGENCE  LIVES  ON", cx + tagline.letterSpacing / 2, cy + r * geometry.tagline, tagline, taglineFill);
    if (pageCount > 1 && geometry.marks !== null) {
      const y = cy + r * geometry.marks, active = 25 * scale, idle = 10 * scale, gap = 4.5 * scale, thick = 2.3 * scale;
      let x = cx - (active + (pageCount - 1) * (idle + gap)) / 2;
      for (let i = 0; i < pageCount; i++) {
        const length = i === pageIndex ? active : idle;
        p.fillRoundRect(x, y - thick / 2, x + length, y + thick / 2, thick / 2, Fill.solid(i === pageIndex ? Argb.rgb(150, 220, 255) : Argb.rgb(60, 80, 120)));
        x += length + gap;
      }
    }
  }
  function glow(p, x, y, radius, color, alpha) {
    if (alpha <= 0 || radius <= 0) return;
    let fill = unitGlows.get(color);
    if (!fill) { fill = Fill.radial(0, 0, 1, [color, Argb.withAlpha(color, 0)]); unitGlows.set(color, fill); }
    p.save(); p.translate(x, y); p.scale(radius, radius); p.fillCircle(0, 0, 1, fill, alpha); p.restore();
  }
  return Object.freeze({
    layout, draw,
    getMetrics: () => ({ width: w, height: h, centerX: cx, centerY: cy, radius: rs, nodeCount: plexus.nodeCount, edgeCount: plexus.edgeCount, dustCount: plexus.dustCount, outerRings: orbits.length, innerRings: coreOrbits.length }),
  });
}

/** Original HologramAudio.kt: 35 ms attack, 150 ms release, 180 ms freshness. */
export class HologramAudioEnvelope {
  constructor() { this.reset(); }
  reset() { this.target = 0; this.value = 0; this.lastSampleMs = null; this.lastFrameMs = null; }
  submit(level, nowMs) {
    this.valueAt(nowMs);
    this.target = Number.isFinite(level) ? clamp(level, 0, 1) : 0;
    this.lastSampleMs = nowMs;
  }
  valueAt(nowMs) {
    if (this.lastFrameMs === null) { this.lastFrameMs = nowMs; return this.value; }
    const start = min(this.lastFrameMs, nowMs), freshUntil = this.lastSampleMs === null ? start : this.lastSampleMs + 180;
    const activeMs = max(0, min(nowMs, freshUntil) - start);
    if (activeMs > 0) this.value += (this.target - this.value) * (1 - exp(-activeMs / (this.target > this.value ? 35 : 150)));
    const releaseMs = max(0, nowMs - max(start, freshUntil));
    if (releaseMs > 0) this.value *= exp(-releaseMs / 150);
    this.lastFrameMs = nowMs;
    if (this.value < .0001) this.value = 0;
    return this.value;
  }
}

const APP_MODES = Object.freeze({ idle: MODE.READY, ready: MODE.READY, result: MODE.READY,
  listening: MODE.LISTENING, forming: MODE.THINKING, working: MODE.THINKING,
  thinking: MODE.THINKING, speaking: MODE.SPEAKING, research: MODE.RESEARCH, learning: MODE.LEARNING });

/** The original View's continuous phase and exponential state crossfades. */
export class ContinuumOriginalClock {
  constructor() {
    this.mode = MODE.READY; this.state = "idle"; this.previousMs = null;
    this.envelope = new HologramAudioEnvelope();
    this.frame = { phase: .4, energy: .18, audio: 0, weights: Float32Array.of(1, 0, 0, 0, 0, 0) };
  }
  setState(state) {
    if (!Object.hasOwn(APP_MODES, state)) return false;
    if (this.mode === MODE.SPEAKING && APP_MODES[state] !== MODE.SPEAKING) this.envelope.reset();
    this.state = state; this.mode = APP_MODES[state]; return true;
  }
  submitAudio(level, nowMs) { this.envelope.submit(level, nowMs); }
  advance(nowMs) {
    const dt = this.previousMs === null ? 0 : clamp((nowMs - this.previousMs) / 1000, 0, .1);
    this.previousMs = nowMs;
    const f = this.frame, blend = 1 - exp(-dt * 7);
    for (let i = 0; i < f.weights.length; i++) f.weights[i] += ((i === this.mode ? 1 : 0) - f.weights[i]) * blend;
    f.audio = this.mode === MODE.SPEAKING ? sqrt(this.envelope.valueAt(nowMs)) : 0;
    const energy = this.mode === MODE.SPEAKING ? .12 + f.audio * .88 : MODE_ENERGY[this.mode];
    f.energy += (energy - f.energy) * (1 - exp(-dt * 9));
    // Keep the original velocity, but leave the browser's double phase unwrapped.
    // The Kotlin 1000-radian wrap would restart every layer together in a long session.
    f.phase += dt * (.28 + f.energy * .72);
    return f;
  }
}

/**
 * Host adapter: its own transparent layer follows the actual live orb anchor.
 * The old scene remains responsible for atmosphere and document connections.
 * Identity fallback is a PNG rendered from this same original renderer.
 */
export function mountContinuumOriginalOrb(options = {}) {
  const hostWindow = options.window || window, doc = hostWindow.document;
  const anchor = options.anchor || doc.querySelector("[data-orb-anchor]");
  if (!anchor) return null;
  const original = createContinuumOriginalRenderer({ geometry: GEOMETRY.REFERENCE_COCKPIT });
  const clock = new ContinuumOriginalClock();
  const fixedFrame = { phase: .4, energy: .18, audio: 0, weights: Float32Array.of(1, 0, 0, 0, 0, 0) };
  const normaliseMotion = mode => mode === "fixed" || mode === "anchored" ? "fixed" : mode === "live" ? "live" : null;
  let motion = normaliseMotion(options.motion || anchor.dataset.orbMotion) || "live";
  const canvas = doc.createElement("canvas");
  canvas.id = "continuum-original-orb"; canvas.setAttribute("aria-hidden", "true");
  Object.assign(canvas.style, { position: "absolute", zIndex: "0", display: "block", pointerEvents: "none", opacity: "0", contain: "strict" });
  // Keep the orb inside its own stacking context. The host puts this anchor
  // behind the avatar; no body-level canvas can cover dialogs or other tools.
  anchor.appendChild(canvas);
  let context = null;
  try { context = canvas.getContext("2d", { alpha: true, desynchronized: true }); } catch { /* The original PNG remains available below. */ }
  const painter = context ? new BrowserContinuumPainter(context) : null;
  let active = true, failed = false, ready = false, animationFrame = 0, frames = 0;
  let width = 0, height = 0, bleed = 0, ratio = 1, quality = 1, intensity = 1;
  let boundsDirty = true, pixelsDirty = true, visible = true, lastError = null, lastAudioSampleAt = -1;
  let appliedExposure = null;
  let layoutTrackingUntil = 0;
  let fallback = null, observer = null, motionObserver = null;
  const removers = [];
  const listen = (target, event, handler, eventOptions) => {
    target.addEventListener(event, handler, eventOptions);
    removers.push(() => target.removeEventListener(event, handler, eventOptions));
  };
  function setIntensity(value) {
    if (Number.isFinite(value)) intensity = clamp(value, .35, 1.6);
    updateExposure();
  }
  function updateExposure() {
    // A fixed orb changes light only: its original ready-state pixels, projected
    // rings, float position, sphere radius and internal network stay unchanged.
    const response = motion === "fixed" ? 1 + (clock.frame.energy - .18) * .18 : 1;
    const exposure = Math.round(intensity * response * 1000) / 1000;
    if (exposure === appliedExposure) return;
    appliedExposure = exposure;
    canvas.style.filter = exposure === 1 ? "none" : `brightness(${exposure})`;
    if (fallback) fallback.style.filter = canvas.style.filter;
  }
  function setMotionMode(value) {
    const next = normaliseMotion(value);
    if (!next) return false;
    if (next !== motion) { motion = next; pixelsDirty = true; updateExposure(); }
    return true;
  }
  function readScene(detail) {
    if (!detail) return;
    if (detail.state) clock.setState(detail.state);
    if (Number.isFinite(detail.quality) && quality !== detail.quality) { quality = clamp(detail.quality, .6, 1); boundsDirty = true; }
    if (Number.isFinite(detail.intensity)) setIntensity(detail.intensity);
    if (Number.isFinite(detail.audioSampleAt) && detail.audioSampleAt > lastAudioSampleAt) {
      lastAudioSampleAt = detail.audioSampleAt;
      clock.submitAudio(detail.audioLevel === null ? 0 : Number(detail.audioLevel), detail.audioSampleAt);
    }
  }
  function resize() { boundsDirty = true; }
  function trackLayout() { boundsDirty = true; layoutTrackingUntil = hostWindow.performance.now() + 1250; }
  function position() {
    const rect = anchor.getBoundingClientRect();
    visible = rect.width > 0 && rect.height > 0 && rect.bottom > 0 && rect.top < hostWindow.innerHeight;
    if (!visible) { canvas.style.visibility = "hidden"; if (fallback) fallback.style.visibility = "hidden"; return; }
    canvas.style.visibility = "visible";
    // Render in the anchor's local CSS units; parent translations and any scale
    // apply once to both avatar and orb instead of being applied again here.
    const anchorWidth = anchor.clientWidth || rect.width, anchorHeight = anchor.clientHeight || rect.height;
    const nextRatio = min(hostWindow.devicePixelRatio || 1, quality < .7 ? 1.25 : hostWindow.innerWidth <= 760 ? 1.5 : 1.8);
    if (width !== anchorWidth || height !== anchorHeight || ratio !== nextRatio) {
      width = anchorWidth; height = anchorHeight; ratio = nextRatio; bleed = min(width, height) * .48;
      canvas.width = Math.ceil((width + bleed * 2) * ratio); canvas.height = Math.ceil((height + bleed * 2) * ratio);
      canvas.style.width = `${width + bleed * 2}px`; canvas.style.height = `${height + bleed * 2}px`;
      original.layout(width, height);
      pixelsDirty = true;
    }
    canvas.style.left = `${-bleed}px`; canvas.style.top = `${-bleed}px`;
    if (fallback) Object.assign(fallback.style, { left: `${-bleed}px`, top: `${-bleed}px`, width: `${width + bleed * 2}px`, height: `${height + bleed * 2}px`, visibility: "visible" });
    boundsDirty = false;
  }
  function showFallback(error) {
    failed = true; ready = false; lastError = String(error?.message || error || "Canvas 2D unavailable");
    canvas.style.opacity = "0";
    if (!fallback) {
      fallback = doc.createElement("img"); fallback.alt = ""; fallback.setAttribute("aria-hidden", "true");
      fallback.src = options.fallbackUrl || "assets/continuum-orb-original.png";
      Object.assign(fallback.style, { position: "absolute", pointerEvents: "none", zIndex: "0", objectFit: "contain", filter: canvas.style.filter });
      anchor.appendChild(fallback);
    }
    boundsDirty = true; position();
    anchor.dataset.orbRenderer = "original-png";
    // The former procedural replacement intentionally stays disabled.
    hostWindow.ContinuumScene?.setOrbVisible?.(false);
  }
  function draw(now) {
    if (!active) return;
    if (boundsDirty || now < layoutTrackingUntil) position();
    const f = clock.advance(now);
    updateExposure();
    if (!failed && visible && !doc.hidden && (motion === "live" || pixelsDirty || !ready)) {
      try {
        context.setTransform(1, 0, 0, 1, 0, 0); context.clearRect(0, 0, canvas.width, canvas.height);
        context.setTransform(ratio, 0, 0, ratio, bleed * ratio, bleed * ratio);
        painter.begin(); original.draw(painter, motion === "fixed" ? fixedFrame : f, true); frames++;
        pixelsDirty = false;
        if (!ready) {
          ready = true; canvas.style.opacity = "1"; anchor.dataset.orbRenderer = "original-canvas";
          hostWindow.ContinuumScene?.setOrbVisible?.(false);
          hostWindow.dispatchEvent(new hostWindow.CustomEvent("continuum:orbready", { detail: { sourceCommit: ORIGINAL_COMMIT, renderer: "original-canvas", motion } }));
        }
      } catch (error) { showFallback(error); }
    }
    animationFrame = hostWindow.requestAnimationFrame(draw);
  }
  function dispose() {
    active = false; hostWindow.cancelAnimationFrame(animationFrame); observer?.disconnect(); motionObserver?.disconnect();
    removers.splice(0).forEach(remove => remove()); canvas.remove(); fallback?.remove();
    delete anchor.dataset.orbRenderer;
  }
  listen(hostWindow, "continuum:scenechange", event => readScene(event.detail));
  listen(hostWindow, "continuum:sceneresize", resize);
  listen(hostWindow, "resize", resize, { passive: true });
  listen(hostWindow, "scroll", resize, { passive: true, capture: true });
  listen(doc, "visibilitychange", resize);
  if (hostWindow.ResizeObserver) { observer = new hostWindow.ResizeObserver(resize); observer.observe(anchor); }
  if (hostWindow.MutationObserver) {
    motionObserver = new hostWindow.MutationObserver(() => setMotionMode(anchor.dataset.orbMotion || "live"));
    motionObserver.observe(anchor, { attributes: true, attributeFilter: ["data-orb-motion"] });
  }
  // Parent layout changes can move an unchanged-sized anchor during transitions.
  listen(doc, "transitionrun", trackLayout, true); listen(doc, "transitionend", resize, true);
  readScene(hostWindow.ContinuumScene?.getStatus?.()); position();
  hostWindow.ContinuumScene?.setOrbVisible?.(false);
  if (!context) showFallback("Canvas 2D unavailable");
  animationFrame = hostWindow.requestAnimationFrame(draw);
  return Object.freeze({
    resize, dispose, setMotionMode, setState: state => clock.setState(state),
    setAudioLevel: value => clock.submitAudio(Number(value) || 0, hostWindow.performance.now()),
    getStatus: () => ({ active, ready, renderer: failed ? "original-png" : "original-canvas", sourceCommit: ORIGINAL_COMMIT,
      state: clock.state, mode: clock.mode, motion, geometryFixed: motion === "fixed",
      phase: motion === "fixed" ? fixedFrame.phase : clock.frame.phase, clockPhase: clock.frame.phase,
      energy: clock.frame.energy, audio: clock.frame.audio, exposure: appliedExposure,
      frames, quality, intensity, pixelRatio: ratio, localLayer: true, lastError, ...original.getMetrics() }),
  });
}

if (typeof window !== "undefined" && typeof document !== "undefined") {
  const scriptBase = new URL(".", document.currentScript?.src || document.baseURI);
  const boot = () => {
    if (window.ContinuumOriginalOrb) return;
    window.ContinuumOriginalOrb = mountContinuumOriginalOrb({
      fallbackUrl: window.CONTINUUM_ASSETS?.orbFallback || new URL("assets/continuum-orb-original.png", scriptBase).href,
    });
  };
  if (document.readyState === "loading") document.addEventListener("DOMContentLoaded", boot, { once: true }); else boot();
}
