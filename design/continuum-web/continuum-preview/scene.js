/* Continuum spatial atmosphere. Original avatar and readable UI remain in the DOM. */
(function () {
  "use strict";

  const TAU = Math.PI * 2;
  const clamp = (n, a, b) => Math.max(a, Math.min(b, n));
  const mix = (a, b, x) => a + (b - a) * x;
  const smooth = (a, b, x) => {
    const p = clamp((x - a) / (b - a), 0, 1);
    return p * p * (3 - 2 * p);
  };
  const STATES = {
    idle: { energy: 0.56, connection: 0.16, warmth: 0 },
    listening: { energy: 0.79, connection: 0.3, warmth: 0 },
    forming: { energy: 1.22, connection: 1, warmth: 0 },
    working: { energy: 0.94, connection: 0.75, warmth: 0 },
    speaking: { energy: 0.86, connection: 0.64, warmth: 0.06 },
    result: { energy: 0.81, connection: 0.52, warmth: 0.22 },
  };

  let canvas;
  let ctx;
  let width = 1;
  let height = 1;
  let pixelRatio = 1;
  let phone = false;
  let state = "idle";
  let stateStarted = 0;
  let animationFrame = 0;
  let started = performance.now();
  let previousTime = started;
  let energy = STATES.idle.energy;
  let connection = STATES.idle.connection;
  let warmth = 0;
  let intensity = 1;
  let quality = 1;
  let explicitQuality = false;
  let audioLevel = null;
  let audioSampleAt = 0;
  let orbVisible = false;
  let averageFrame = 16.7;
  let averageCpu = 0;
  let qualitySample = 0;
  let resolutionScale = 1;
  let budgetChangedAt = 0;
  let pressureSamples = 0;
  let resizeTimer = 0;
  let resizeObserver = null;
  let bufferResizes = 0;
  let backdrop = null;
  let backdropWidth = 0;
  let backdropHeight = 0;
  let backdropBuilds = 0;
  let audioEventPending = false;
  let lastAudioEventAt = -Infinity;
  let reducedMotion = false;
  let destroyed = false;
  let active = false;
  let renderedFrames = 0;
  let anchorTimer = 0;
  let orbAnchor = null;
  let workAnchor = null;
  let orb = { x: 0, y: 0, r: 0 };
  let work = { x: 0, y: 0 };
  const center = { x: 0, y: 0 };
  const curveA = { x: 0, y: 0 }, curveB = { x: 0, y: 0 };
  const curveC = { x: 0, y: 0 }, curveD = { x: 0, y: 0 };
  const curveFrom = { x: 0, y: 0 }, curveTo = { x: 0, y: 0 };
  const connectionSamples = new Float32Array(61 * 3);
  const pointer = { x: 0, y: 0, tx: 0, ty: 0 };
  const sprites = Object.create(null);
  const lightFields = Object.create(null);
  const atmosphereTextures = Object.create(null);
  const atmospherePlanes = [
    { depth: 0.17, haze: "far", y: 0.23, width: 1.24, height: 0.19, speed: 0.0117, phase: 0.8, alpha: 0.14 },
    { depth: 0.48, haze: "middle", y: 0.49, width: 1.16, height: 0.23, speed: 0.0183, phase: 3.1, alpha: 0.105 },
    { depth: 0.86, haze: "near", y: 0.77, width: 1.38, height: 0.21, speed: 0.0269, phase: 5.4, alpha: 0.075 },
  ];
  const stars = [];
  const dust = [];
  const orbitalDust = [];
  let randomSeed = 753091;
  function random() {
    randomSeed |= 0;
    randomSeed = (randomSeed + 0x6d2b79f5) | 0;
    let value = Math.imul(randomSeed ^ (randomSeed >>> 15), 1 | randomSeed);
    value = (value + Math.imul(value ^ (value >>> 7), 61 | value)) ^ value;
    return ((value ^ (value >>> 14)) >>> 0) / 4294967296;
  }

  for (let i = 0; i < 112; i++) {
    stars.push({ x: random(), y: random(), z: random(), layer: i % 3, phase: random() * TAU, radius: 0.4 + random() * 0.8 });
  }
  for (let i = 0; i < 84; i++) {
    dust.push({ x: random(), y: random(), z: random(), layer: i % 3, speed: 0.002 + random() * 0.006, phase: random() * TAU });
  }
  for (let i = 0; i < 48; i++) {
    orbitalDust.push({ theta: random() * TAU, inclination: random() * Math.PI, radius: 0.62 + random() * 0.76, phase: random() * TAU, speed: 0.06 + random() * 0.14, size: 0.45 + random() * 1.25 });
  }

  function makeSprite(name, rgb) {
    const surface = document.createElement("canvas");
    surface.width = surface.height = 96;
    const context = surface.getContext("2d");
    const glow = context.createRadialGradient(48, 48, 0, 48, 48, 48);
    glow.addColorStop(0, "rgba(245,255,255,1)");
    glow.addColorStop(0.035, `rgba(${rgb},.95)`);
    glow.addColorStop(0.12, `rgba(${rgb},.55)`);
    glow.addColorStop(0.38, `rgba(${rgb},.12)`);
    glow.addColorStop(1, `rgba(${rgb},0)`);
    context.fillStyle = glow;
    context.fillRect(0, 0, 96, 96);
    sprites[name] = surface;
  }

  function glow(x, y, size, alpha, color = "cyan") {
    if (alpha <= 0 || size <= 0) return;
    ctx.globalAlpha = clamp(alpha, 0, 1);
    ctx.drawImage(sprites[color], x - size, y - size, size * 2, size * 2);
    ctx.globalAlpha = 1;
  }

  function softLight(x, y, radius, rgb, alpha) {
    if (!lightFields[rgb]) {
      const field = document.createElement("canvas");
      field.width = field.height = 256;
      const context = field.getContext("2d");
      const gradient = context.createRadialGradient(128, 128, 0, 128, 128, 128);
      gradient.addColorStop(0, `rgba(${rgb},1)`);
      gradient.addColorStop(0.45, `rgba(${rgb},.36)`);
      gradient.addColorStop(1, `rgba(${rgb},0)`);
      context.fillStyle = gradient;
      context.fillRect(0, 0, 256, 256);
      lightFields[rgb] = field;
    }
    ctx.globalAlpha = clamp(alpha, 0, 1);
    ctx.drawImage(lightFields[rgb], x - radius, y - radius, radius * 2, radius * 2);
    ctx.globalAlpha = 1;
  }

  function makeAtmosphereTextures() {
    // Small reusable rasters give the fog soft edges without full-screen blur
    // passes, shader compilation or per-frame texture allocations on phones.
    for (const [name, rgb, seed] of [["far", "38,73,137", 1.3], ["middle", "32,112,147", 3.7], ["near", "73,62,123", 7.1]]) {
      const surface = document.createElement("canvas"); surface.width = 384; surface.height = 160;
      const context = surface.getContext("2d");
      context.globalCompositeOperation = "screen";
      for (let i = 0; i < 7; i++) {
        const x = 40 + i * 51, y = 77 + Math.sin(i * 1.47 + seed) * 22;
        const radius = 53 + (i % 3) * 9;
        const cloud = context.createRadialGradient(x, y, 0, x, y, radius);
        cloud.addColorStop(0, `rgba(${rgb},.46)`); cloud.addColorStop(.42, `rgba(${rgb},.2)`); cloud.addColorStop(1, `rgba(${rgb},0)`);
        context.fillStyle = cloud; context.fillRect(x - radius, y - radius, radius * 2, radius * 2);
      }
      atmosphereTextures[name] = surface;
    }
    for (const [name, rgb] of [["rayBlue", "64,139,211"], ["rayCyan", "58,183,211"], ["rayViolet", "115,99,192"]]) {
      const surface = document.createElement("canvas"); surface.width = 96; surface.height = 512;
      const context = surface.getContext("2d");
      const falloff = context.createLinearGradient(0, 0, 0, 512);
      falloff.addColorStop(0, `rgba(${rgb},0)`); falloff.addColorStop(.1, `rgba(${rgb},.6)`);
      falloff.addColorStop(.36, `rgba(${rgb},.85)`); falloff.addColorStop(.7, `rgba(${rgb},.3)`); falloff.addColorStop(1, `rgba(${rgb},0)`);
      context.fillStyle = falloff; context.fillRect(0, 0, 96, 512);
      context.globalCompositeOperation = "destination-in";
      const edge = context.createLinearGradient(0, 0, 96, 0);
      edge.addColorStop(0, "rgba(255,255,255,0)"); edge.addColorStop(.22, "rgba(255,255,255,.2)");
      edge.addColorStop(.5, "rgba(255,255,255,1)"); edge.addColorStop(.78, "rgba(255,255,255,.2)"); edge.addColorStop(1, "rgba(255,255,255,0)");
      context.fillStyle = edge; context.fillRect(0, 0, 96, 512);
      context.beginPath(); context.moveTo(35, 0); context.lineTo(61, 0); context.lineTo(96, 512); context.lineTo(0, 512); context.closePath();
      context.globalCompositeOperation = "destination-in"; context.fillStyle = "#fff"; context.fill();
      atmosphereTextures[name] = surface;
    }
  }

  function updateAnchors() {
    orbAnchor = document.querySelector("[data-orb-anchor]");
    workAnchor = document.querySelector("[data-work-anchor]");
    phone = width <= 760;
    const compact = width < 1060;
    let x = width * (phone ? 0.76 : 0.73);
    let y = phone ? 176 : compact ? 240 : 260;
    let r = phone ? clamp(width * 0.16, 46, 68) : clamp(width * 0.092, 90, 150);
    if (orbAnchor) {
      const bounds = orbAnchor.getBoundingClientRect();
      if (bounds.width > 0 && bounds.height > 0) {
        x = bounds.left + bounds.width / 2;
        y = bounds.top + bounds.height * 0.42;
        r = Math.min(bounds.width * 0.32, bounds.height * 0.30) * 1.04;
      }
    }
    let wx = width * (phone ? 0.57 : 0.52), wy = phone ? 326 : 440;
    if (workAnchor) {
      const bounds = workAnchor.getBoundingClientRect();
      if (bounds.width > 0 && bounds.height > 0) {
        wx = bounds.left + bounds.width * 0.56;
        wy = bounds.top + Math.min(36, bounds.height * 0.08);
      }
    }
    const changed = x !== orb.x || y !== orb.y || r !== orb.r || wx !== work.x || wy !== work.y;
    orb.x = x; orb.y = y; orb.r = r; work.x = wx; work.y = wy;
    return changed;
  }

  function buildBackdrop() {
    // The broad diffuse lights and quiet architecture are one reusable raster.
    // Fog, rays, veins and particles retain their own continuous motion above it.
    if (!backdrop) backdrop = document.createElement("canvas");
    const margin = 32;
    backdropWidth = width + margin * 2; backdropHeight = height + margin * 2;
    const scale = Math.min(1, Math.sqrt(1100000 / (backdropWidth * backdropHeight)));
    backdrop.width = Math.ceil(backdropWidth * scale);
    backdrop.height = Math.ceil(backdropHeight * scale);
    const context = backdrop.getContext("2d");
    context.setTransform(scale, 0, 0, scale, margin * scale, margin * scale);
    context.fillStyle = "#030810";
    context.fillRect(-margin, -margin, backdropWidth, backdropHeight);
    const fields = [[.61, .22, .65, "17,39,84", .27], [.22, .34, .49, "9,78,105", .22], [.865, .47, .46, "55,29,104", .18]];
    for (const [x, y, radius, rgb, alpha] of fields) {
      const cx = width * x, cy = height * y, r = width * radius;
      const light = context.createRadialGradient(cx, cy, 0, cx, cy, r);
      light.addColorStop(0, `rgba(${rgb},${alpha * intensity})`);
      light.addColorStop(.45, `rgba(${rgb},${alpha * intensity * .36})`);
      light.addColorStop(1, `rgba(${rgb},0)`);
      context.fillStyle = light; context.fillRect(cx - r, cy - r, r * 2, r * 2);
    }
    context.globalCompositeOperation = "screen";
    for (let i = 0; i < 5; i++) {
      const inset = i * (phone ? 15 : 29);
      context.beginPath();
      context.ellipse(width * .5, phone ? 260 : 370, Math.max(1, width * .72 - inset),
        (phone ? 320 : 475) - inset, -.03, Math.PI * 1.02, Math.PI * 1.98);
      context.strokeStyle = i === 1 ? `rgba(70,184,221,${.105 * intensity})` : `rgba(51,101,151,${.037 * intensity})`;
      context.lineWidth = i === 1 ? 1.1 : 3; context.stroke();
    }
    backdropBuilds++;
  }

  function resize() {
    if (!canvas || destroyed) return;
    const bounds = canvas.getBoundingClientRect();
    const nextWidth = Math.max(1, bounds.width || window.innerWidth);
    const nextHeight = Math.max(1, bounds.height || window.innerHeight);
    const sizeChanged = nextWidth !== width || nextHeight !== height;
    width = nextWidth; height = nextHeight;
    phone = width <= 760;
    const maximumRatio = quality < .7 ? 1.05 : phone ? 1.2 : 1.3;
    const pixelBudget = phone ? 580000 : 1550000;
    const nextRatio = Math.min(window.devicePixelRatio || 1, maximumRatio,
      Math.sqrt(pixelBudget / (width * height))) * resolutionScale;
    const pixelsWide = Math.round(width * nextRatio), pixelsHigh = Math.round(height * nextRatio);
    const bufferChanged = canvas.width !== pixelsWide || canvas.height !== pixelsHigh;
    pixelRatio = nextRatio;
    if (bufferChanged) {
      canvas.width = pixelsWide; canvas.height = pixelsHigh;
      ctx.setTransform(pixelRatio, 0, 0, pixelRatio, 0, 0);
      bufferResizes++;
    }
    if (sizeChanged || !backdrop) buildBackdrop();
    const anchorChanged = updateAnchors();
    if (sizeChanged || bufferChanged || anchorChanged) window.dispatchEvent(new CustomEvent("continuum:sceneresize"));
  }

  function queueResize() {
    if (resizeTimer || destroyed) return;
    resizeTimer = requestAnimationFrame(() => { resizeTimer = 0; resize(); });
  }

  function drawHazePlane(index, t, light) {
    const plane = atmospherePlanes[index], surface = atmosphereTextures[plane.haze];
    if (!surface) return;
    const sway = Math.sin(t * plane.speed + plane.phase), curl = Math.cos(t * (plane.speed * 0.61) + plane.phase * 1.3);
    const w = width * plane.width, h = height * plane.height;
    const x = width * (.5 + sway * .065) + pointer.x * plane.depth * 24;
    const y = height * (plane.y + curl * .037) + pointer.y * plane.depth * 13;
    ctx.save(); ctx.translate(x, y); ctx.rotate(.035 * sway + (index - 1) * .055);
    ctx.globalAlpha = plane.alpha * light; ctx.drawImage(surface, -w / 2, -h / 2, w, h);
    if (index === 1) { ctx.globalAlpha = plane.alpha * .5 * light; ctx.drawImage(surface, -w * .72, h * .13, w * 1.25, h * .76); }
    ctx.restore();
  }

  function drawVolumetricRays(t, light) {
    const count = phone ? 3 : 5;
    for (let i = 0; i < count; i++) {
      const x = width * (.05 + i * (phone ? .44 : .245)) + Math.sin(t * (.0131 + i * .0037) + i * 1.7) * width * .035;
      const y = -height * (.12 + (i % 2) * .08);
      const length = height * (1.05 + (i % 2) * .2), spread = width * (phone ? .19 : .12);
      const angle = (i % 2 === 0 ? -.25 : .24) + Math.sin(t * (.0089 + i * .0023) + i) * .025;
      ctx.save(); ctx.translate(x + pointer.x * (3 + i), y); ctx.rotate(angle);
      ctx.globalAlpha = (phone ? .075 : .088) * (.93 + Math.sin(t * (.0211 + i * .0031) + i * 2.1) * .07) * light;
      ctx.drawImage(atmosphereTextures[i % 3 === 0 ? "rayCyan" : i % 3 === 1 ? "rayBlue" : "rayViolet"], -spread / 2, 0, spread, length);
      ctx.restore();
    }
  }

  function drawLightVeins(layer, t, light) {
    const depth = atmospherePlanes[layer].depth;
    for (let j = 0; j < 2; j++) {
      const i = layer * 2 + j, phase = t * (.0107 + i * .0031) + i * 1.73;
      const y = height * (.15 + i * .124) + Math.sin(phase) * height * .018 + pointer.y * depth * 15;
      const a = curveA, b = curveB, c = curveC, d = curveD;
      a.x = -width * .08; a.y = y - 23; b.x = width * .24; b.y = y + Math.cos(phase * .63) * 47;
      c.x = width * .72; c.y = y - 32; d.x = width * 1.08; d.y = y + 23;
      const shift = pointer.x * depth * 18;
      ctx.save(); ctx.translate(shift, 0); ctx.globalAlpha = 1;
      ctx.beginPath(); ctx.moveTo(a.x, a.y); ctx.bezierCurveTo(b.x, b.y, c.x, c.y, d.x, d.y);
      ctx.strokeStyle = `rgba(${i % 2 ? "105,99,192" : "51,164,192"},${(.028 + depth * .026) * light})`;
      ctx.lineWidth = .55 + depth * .45; ctx.stroke();
      const progress = (t * (.0061 + i * .0013) + i * .217) % 1;
      const from = curvePoint(progress, a, b, c, d, curveFrom), to = curvePoint(Math.min(1, progress + .026), a, b, c, d, curveTo);
      const fade = Math.sin(progress * Math.PI) ** 2;
      ctx.beginPath(); ctx.moveTo(from.x, from.y); ctx.lineTo(to.x, to.y);
      ctx.strokeStyle = `rgba(114,215,236,${(.1 + depth * .09) * fade * light})`;
      ctx.lineWidth = .8 + depth * .55; ctx.stroke();
      glow(from.x, from.y, 5 + depth * 5, .075 * fade * light, i % 2 ? "violet" : "cyan");
      ctx.restore();
    }
  }

  function drawDepthParticles(layer, t, light) {
    const depth = atmospherePlanes[layer].depth;
    const starCount = Math.floor((phone ? 66 : stars.length) * quality);
    for (let i = 0; i < starCount; i++) {
      const s = stars[i]; if (s.layer !== layer) continue;
      const sx = s.x * width + pointer.x * (2 + depth * 11);
      const sy = s.y * height + pointer.y * (1 + depth * 8);
      const alpha = (.105 + depth * .16) * (.82 + Math.sin(t * (.113 + s.z * .127) + s.phase) * .18) * light;
      ctx.fillStyle = "#92c3de"; ctx.globalAlpha = alpha;
      ctx.beginPath(); ctx.arc(sx, sy, s.radius * (.55 + depth * .35), 0, TAU); ctx.fill();
      if (i % 23 === 0) glow(sx, sy, 6 + depth * 7, alpha * .4, "blue");
    }
    ctx.globalAlpha = 1;
    const dustCount = Math.floor((phone ? 45 : dust.length) * quality);
    for (let i = 0; i < dustCount; i++) {
      const p = dust[i]; if (p.layer !== layer) continue;
      const travel = p.speed * (.055 + depth * .09);
      const x = (p.x + t * travel) % 1, y = (p.y - t * travel * (.5 + depth * .2) + 1000) % 1;
      const sx = x * (width + 30) - 15 + Math.sin(t * (.031 + depth * .019) + p.phase) * (4 + depth * 9) + pointer.x * depth * 23;
      const sy = y * (height + 30) - 15 + Math.cos(t * (.024 + depth * .013) + p.phase) * (3 + depth * 8) + pointer.y * depth * 12;
      const edge = Math.min(1, Math.max(0, sx / 25), Math.max(0, (width - sx) / 25), Math.max(0, sy / 25), Math.max(0, (height - sy) / 25));
      glow(sx, sy, 1.5 + depth * (3.2 + p.z * 2), (.09 + depth * .12) * edge * light, i % 29 === 0 ? "amber" : i % 4 === 0 ? "violet" : "cyan");
    }
  }

  function atmosphere(elapsed) {
    // The opaque cached raster covers the viewport. Copy also retires the
    // previous frame explicitly rather than retaining a compositing history.
    ctx.globalCompositeOperation = "copy";
    ctx.globalAlpha = 1;

    // One uninterrupted timebase, independent incommensurate layer frequencies.
    // No state transition starts or resets atmosphere phases.
    const t = elapsed * (reducedMotion ? .35 : 1), light = intensity * (.87 + energy * .13);
    const driftX = Math.sin(t / 43.7) * 7 + pointer.x * 3;
    const driftY = Math.sin(t / 71.3) * 4 + pointer.y * 2;
    ctx.drawImage(backdrop, -32 + driftX, -32 + driftY, backdropWidth, backdropHeight);
    ctx.globalCompositeOperation = "source-over";

    ctx.save();
    ctx.globalCompositeOperation = "screen";
    drawHazePlane(0, t, light); drawLightVeins(0, t, light); drawDepthParticles(0, t, light);
    drawVolumetricRays(t, light);
    for (let layer = 1; layer < 3; layer++) {
      drawHazePlane(layer, t, light); drawLightVeins(layer, t, light); drawDepthParticles(layer, t, light);
    }
    ctx.restore();
  }

  function project(point, center, radius) {
    const camera = radius * 5.5;
    const perspective = camera / (camera - point.z);
    return { x: center.x + point.x * perspective, y: center.y + point.y * perspective, z: point.z, scale: perspective };
  }

  function orientation(ax, ay, az) {
    const sx = Math.sin(ax), cx = Math.cos(ax);
    const sy = Math.sin(ay), cy = Math.cos(ay);
    const sz = Math.sin(az), cz = Math.cos(az);
    return function (x, y, z) {
      const yy = y * cx - z * sx;
      const zz = y * sx + z * cx;
      const xx = x * cy + zz * sy;
      const zzz = -x * sy + zz * cy;
      return { x: xx * cz - yy * sz, y: xx * sz + yy * cz, z: zzz };
    };
  }

  function makeRingGeometry(t, center, radius) {
    const faces = [];
    const lights = [];
    const segments = quality < 0.7 ? 56 : phone ? 64 : 80;
    const crossSections = quality < 0.7 ? 4 : 6;
    const ringSettings = [
      { r: 1.06, ax: 0.94, ay: 0.28, az: 0.26, period: 23.7, dir: 1, phase: 0.1, hue: "cyan" },
      { r: 1.18, ax: 0.29, ay: 1.07, az: -0.64, period: 17.3, dir: -1, phase: 1.6, hue: "blue" },
      { r: 1.3, ax: 1.29, ay: -0.55, az: 1.19, period: 11.9, dir: 1, phase: 3.7, hue: "violet" },
    ];
    for (let index = 0; index < ringSettings.length; index++) {
      const settings = ringSettings[index];
      const angle = t * TAU / settings.period * settings.dir + settings.phase;
      const transform = orientation(
        settings.ax + Math.sin(t * 0.051 + index) * 0.055,
        settings.ay + Math.sin(t * 0.037 + index) * 0.07,
        settings.az + Math.sin(t * 0.029 + index) * 0.05
      );
      const ringRadius = radius * settings.r;
      const thickness = radius * (index === 2 ? 0.046 : index === 1 ? 0.063 : 0.077);
      const depth = thickness * 0.62;
      for (let j = 0; j < segments; j++) {
        const theta = j / segments * TAU + angle;
        const theta2 = (j + 0.985) / segments * TAU + angle;
        const plate = Math.floor(j / 7);
        const gap = j % 7 === 6;
        for (let k = 0; k < crossSections; k++) {
          const phi = k / crossSections * TAU;
          const phi2 = (k + 1) / crossSections * TAU;
          const points = [
            transform((ringRadius + thickness * Math.cos(phi)) * Math.cos(theta), (ringRadius + thickness * Math.cos(phi)) * Math.sin(theta), depth * Math.sin(phi)),
            transform((ringRadius + thickness * Math.cos(phi)) * Math.cos(theta2), (ringRadius + thickness * Math.cos(phi)) * Math.sin(theta2), depth * Math.sin(phi)),
            transform((ringRadius + thickness * Math.cos(phi2)) * Math.cos(theta2), (ringRadius + thickness * Math.cos(phi2)) * Math.sin(theta2), depth * Math.sin(phi2)),
            transform((ringRadius + thickness * Math.cos(phi2)) * Math.cos(theta), (ringRadius + thickness * Math.cos(phi2)) * Math.sin(theta), depth * Math.sin(phi2)),
          ];
          const midTheta = (theta + theta2) * 0.5;
          const midPhi = (phi + phi2) * 0.5;
          const normal = transform(Math.cos(midTheta) * Math.cos(midPhi), Math.sin(midTheta) * Math.cos(midPhi), Math.sin(midPhi));
          const illumination = clamp(normal.x * -0.37 + normal.y * -0.48 + normal.z * 0.8, 0, 1);
          const specular = Math.pow(illumination, 17);
          const edgeLight = Math.pow(1 - Math.abs(normal.z), 3) * 0.14;
          const brightness = gap ? 0.15 : 0.23 + illumination * 0.55 + specular * 1.05 + edgeLight;
          const materialBlue = index === 2 ? 1.04 : 1.17;
          const red = Math.round(clamp(42 * brightness + 173 * specular, 9, 230));
          const green = Math.round(clamp(91 * brightness + 170 * specular, 16, 250));
          const blue = Math.round(clamp(125 * brightness * materialBlue + 135 * specular, 26, 255));
          const projected = points.map(p => project(p, center, radius));
          faces.push({ points: projected, z: points.reduce((sum, p) => sum + p.z, 0) / 4, fill: `rgb(${red},${green},${blue})` });
        }

        // Emissive inlays move with the actual segmented metal ring.
        if (!gap) {
          const stripeR = ringRadius + thickness * 0.45;
          const a = transform(stripeR * Math.cos(theta), stripeR * Math.sin(theta), depth * 0.91);
          const b = transform(stripeR * Math.cos(theta2), stripeR * Math.sin(theta2), depth * 0.91);
          lights.push({ a: project(a, center, radius), b: project(b, center, radius), z: (a.z + b.z) * 0.5, color: settings.hue, bright: plate % 3 === 0 ? 0.95 : 0.42, width: Math.max(0.8, radius * 0.012) });
        }
        if (j % 7 === 0) {
          const p = transform(ringRadius * Math.cos(theta), ringRadius * Math.sin(theta), depth * 1.13);
          lights.push({ point: project(p, center, radius), z: p.z, color: j % 21 === 0 ? "amber" : settings.hue, bright: 0.8, bolt: true });
        }
      }
    }
    faces.sort((a, b) => a.z - b.z);
    lights.sort((a, b) => a.z - b.z);
    return { faces, lights };
  }

  function drawRingLayer(geometry, front) {
    ctx.globalCompositeOperation = "source-over";
    for (const face of geometry.faces) {
      if ((face.z >= 0) !== front) continue;
      ctx.globalAlpha = front ? 0.98 : 0.74;
      ctx.fillStyle = face.fill;
      ctx.beginPath();
      ctx.moveTo(face.points[0].x, face.points[0].y);
      for (let i = 1; i < face.points.length; i++) ctx.lineTo(face.points[i].x, face.points[i].y);
      ctx.closePath();
      ctx.fill();
    }
    ctx.globalAlpha = 1;
    ctx.globalCompositeOperation = "screen";
    for (const light of geometry.lights) {
      if ((light.z >= 0) !== front) continue;
      const layerAlpha = front ? 1 : 0.48;
      if (light.bolt) {
        glow(light.point.x, light.point.y, orb.r * 0.035, layerAlpha * light.bright, light.color);
        continue;
      }
      const color = light.color === "cyan" ? "76,226,255" : light.color === "blue" ? "96,148,255" : "166,118,255";
      ctx.strokeStyle = `rgba(${color},${light.bright * layerAlpha})`;
      ctx.lineWidth = light.width;
      ctx.beginPath();
      ctx.moveTo(light.a.x, light.a.y);
      ctx.lineTo(light.b.x, light.b.y);
      ctx.stroke();
    }
    ctx.globalCompositeOperation = "source-over";
  }

  function drawPlatform(t, center, radius) {
    const baseY = center.y + radius * 1.32;
    const platformWidth = radius * 1.28;
    ctx.save();
    ctx.globalCompositeOperation = "screen";
    softLight(center.x, baseY, radius * 1.65, "18,117,185", 0.12 * energy);
    const beam = ctx.createLinearGradient(center.x, center.y + radius * 0.4, center.x, baseY);
    beam.addColorStop(0, "rgba(59,204,255,0)");
    beam.addColorStop(0.68, `rgba(52,177,242,${0.025 * energy})`);
    beam.addColorStop(1, `rgba(83,214,255,${0.14 * energy})`);
    ctx.fillStyle = beam;
    ctx.beginPath();
    ctx.moveTo(center.x - radius * 0.4, center.y + radius * 0.5);
    ctx.lineTo(center.x + radius * 0.4, center.y + radius * 0.5);
    ctx.lineTo(center.x + radius * 0.15, baseY);
    ctx.lineTo(center.x - radius * 0.15, baseY);
    ctx.closePath();
    ctx.fill();
    ctx.globalCompositeOperation = "source-over";
    const metal = ctx.createLinearGradient(center.x, baseY - 9, center.x, baseY + 19);
    metal.addColorStop(0, "rgba(52,84,109,.9)");
    metal.addColorStop(0.22, "rgba(8,20,34,.9)");
    metal.addColorStop(0.65, "rgba(27,48,68,.9)");
    metal.addColorStop(1, "rgba(3,10,19,0)");
    ctx.fillStyle = metal;
    ctx.beginPath();
    ctx.ellipse(center.x, baseY + 7, platformWidth, radius * 0.18, 0, 0, TAU);
    ctx.fill();
    for (let i = 0; i < 4; i++) {
      const rr = platformWidth * (1 - i * 0.18);
      ctx.beginPath();
      ctx.ellipse(center.x, baseY + i * 0.8, rr, radius * (0.16 - i * 0.025), 0, 0, TAU);
      ctx.strokeStyle = i === 0 ? "rgba(109,190,233,.32)" : i === 2 ? "rgba(101,225,255,.7)" : "rgba(75,108,178,.32)";
      ctx.lineWidth = i === 0 ? 1.6 : 0.8;
      ctx.stroke();
    }
    ctx.globalCompositeOperation = "screen";
    for (let i = 0; i < 9; i++) {
      const angle = t * 0.13 + i / 9 * TAU;
      const x = center.x + Math.cos(angle) * platformWidth * 0.78;
      const y = baseY + Math.sin(angle) * radius * 0.12;
      glow(x, y, radius * 0.09, 0.72, i % 4 === 0 ? "violet" : "cyan");
    }
    glow(center.x, baseY, radius * 0.4, 0.65 * energy, "cyan");
    ctx.restore();
  }

  function drawCore(t, center, radius, voice) {
    const r = radius * 0.79;
    const pulse = 0.92 + Math.sin(t * TAU / 4.7) * 0.055 + Math.sin(t * TAU / 7.1) * 0.035;
    const e = (0.48 + energy * 0.78) * pulse * intensity;
    ctx.save();
    ctx.globalCompositeOperation = "screen";
    softLight(center.x, center.y, radius * 1.52, "41,75,246", 0.21 * e);
    softLight(center.x, center.y, radius * 0.95, "15,157,224", 0.3 * e);
    for (let cloud = 0; cloud < 6; cloud++) {
      const angle = t * (cloud % 2 ? -0.09 : 0.12) + cloud * 2.399963;
      const cloudRadius = r * (0.26 + (cloud % 3) * 0.18);
      const px = center.x + Math.cos(angle) * cloudRadius;
      const py = center.y + Math.sin(angle) * cloudRadius * 0.8;
      softLight(px, py, r * (0.38 + cloud % 2 * 0.14), cloud % 2 ? "83,65,238" : "27,153,237", 0.19 * e);
    }

    const globe = ctx.createRadialGradient(center.x - r * 0.19, center.y - r * 0.18, r * 0.04, center.x, center.y, r);
    globe.addColorStop(0, "rgba(80,166,240,.12)");
    globe.addColorStop(0.43, "rgba(17,53,125,.12)");
    globe.addColorStop(0.78, "rgba(17,65,138,.21)");
    globe.addColorStop(0.92, "rgba(49,144,225,.17)");
    globe.addColorStop(0.975, "rgba(127,200,255,.42)");
    globe.addColorStop(1, "rgba(84,130,252,0)");
    ctx.fillStyle = globe;
    ctx.beginPath();
    ctx.arc(center.x, center.y, r, 0, TAU);
    ctx.fill();

    // Field lines are projected on a sphere, with depth-dependent illumination.
    const rotation = orientation(0.48, -0.18, t * 0.055);
    const lineCount = phone ? 7 : 11;
    for (let i = 0; i < lineCount; i++) {
      const latitude = (i / (lineCount - 1) - 0.5) * Math.PI * 0.85;
      const ringRadius = r * Math.cos(latitude);
      const yy = r * Math.sin(latitude);
      for (let half = 0; half < 2; half++) {
        ctx.beginPath();
        for (let j = 0; j <= 50; j++) {
          const a = j / 50 * Math.PI + half * Math.PI + t * 0.025;
          const point = rotation(ringRadius * Math.cos(a), yy, ringRadius * Math.sin(a));
          const pp = project(point, center, radius);
          if (!j) ctx.moveTo(pp.x, pp.y); else ctx.lineTo(pp.x, pp.y);
        }
        ctx.strokeStyle = half ? "rgba(103,120,246,.09)" : "rgba(78,194,255,.18)";
        ctx.lineWidth = 0.65;
        ctx.stroke();
      }
    }

    // Multi-scale plasma streamlines spiral inward instead of resembling an atom icon.
    const streamCount = phone ? 7 : 10;
    for (let stream = 0; stream < streamCount; stream++) {
      ctx.beginPath();
      for (let j = 0; j <= 70; j++) {
        const u = j / 70;
        const a = u * TAU * 1.12 + stream / streamCount * TAU + t * (stream % 2 ? -0.17 : 0.21);
        const rr = r * (0.12 + u * 0.83) * (0.95 + 0.05 * Math.sin(a * 3 - t * 0.3));
        const x = center.x + Math.cos(a) * rr;
        const y = center.y + Math.sin(a) * rr * (0.7 + 0.2 * Math.sin(stream * 1.7));
        if (!j) ctx.moveTo(x, y); else ctx.lineTo(x, y);
      }
      ctx.strokeStyle = stream % 3 ? `rgba(52,185,255,${0.24 * e})` : `rgba(177,119,255,${0.32 * e})`;
      ctx.lineWidth = stream % 3 ? 0.7 : 1.1;
      ctx.stroke();
    }

    for (let i = 0; i < 46; i++) {
      const theta = i * 2.399963 + t * (i % 2 ? 0.085 : -0.13);
      const rr = r * Math.sqrt((i + 1) / 47) * (0.87 + Math.sin(t * 0.12 + i) * 0.035);
      const x = center.x + Math.cos(theta) * rr;
      const y = center.y + Math.sin(theta) * rr * 0.88;
      const alpha = (0.42 + Math.sin(i * 4.2 + t * 0.8) * 0.19) * e;
      glow(x, y, (i % 7 === 0 ? 9 : 4.5) * (phone ? 0.7 : 1), alpha, i % 5 ? "cyan" : "violet");
    }

    // A white source, cyan falloff and distinct purple envelope give the core optical depth.
    glow(center.x, center.y, radius * 0.82, 0.8 * e, "blue");
    glow(center.x, center.y, radius * (0.48 + voice * 0.026), 0.95 * e, "cyan");
    const kernel = ctx.createRadialGradient(center.x, center.y, 0, center.x, center.y, radius * 0.16);
    kernel.addColorStop(0, "rgba(255,255,255,.97)");
    kernel.addColorStop(0.16, "rgba(205,255,255,.94)");
    kernel.addColorStop(0.4, "rgba(73,225,255,.74)");
    kernel.addColorStop(1, "rgba(23,175,255,0)");
    ctx.fillStyle = kernel;
    ctx.beginPath();
    ctx.arc(center.x, center.y, radius * 0.16, 0, TAU);
    ctx.fill();

    const flare = ctx.createLinearGradient(center.x - r * 1.24, center.y, center.x + r * 1.24, center.y);
    flare.addColorStop(0, "rgba(30,203,255,0)");
    flare.addColorStop(0.42, `rgba(47,211,255,${0.28 * e})`);
    flare.addColorStop(0.5, `rgba(223,255,255,${0.9 * e})`);
    flare.addColorStop(0.58, `rgba(47,211,255,${0.28 * e})`);
    flare.addColorStop(1, "rgba(30,203,255,0)");
    ctx.strokeStyle = flare;
    ctx.lineWidth = 0.8;
    ctx.beginPath();
    ctx.moveTo(center.x - r * 1.24, center.y);
    ctx.lineTo(center.x + r * 1.24, center.y);
    ctx.stroke();

    // A coherent grazing reflection moves around the glass envelope.
    ctx.beginPath();
    ctx.arc(center.x, center.y, r * 0.985, t * 0.08 + 3.5, t * 0.08 + 4.3);
    ctx.strokeStyle = "rgba(199,229,255,.55)";
    ctx.lineWidth = 1.1;
    ctx.stroke();
    ctx.restore();
  }

  function drawOrbitalDust(t, center, radius, front) {
    ctx.save();
    ctx.globalCompositeOperation = "screen";
    const count = Math.floor(orbitalDust.length * quality);
    for (let i = 0; i < count; i++) {
      const p = orbitalDust[i];
      const a = p.theta + t * p.speed;
      const transform = orientation(p.inclination, p.phase, p.phase * 0.2);
      const point = transform(Math.cos(a) * radius * p.radius, Math.sin(a) * radius * p.radius, Math.sin(t * 0.14 + p.phase) * radius * 0.035);
      if ((point.z >= 0) !== front) continue;
      const pp = project(point, center, radius);
      glow(pp.x, pp.y, p.size * (phone ? 3.3 : 4.5), (front ? 0.68 : 0.28) * (0.65 + Math.sin(t * 0.45 + p.phase) * 0.24), i % 9 ? "cyan" : "amber");
    }
    ctx.restore();
  }

  function curvePoint(u, a, b, c, d, out = curveFrom) {
    const v = 1 - u;
    out.x = v * v * v * a.x + 3 * v * v * u * b.x + 3 * v * u * u * c.x + u * u * u * d.x;
    out.y = v * v * v * a.y + 3 * v * v * u * b.y + 3 * v * u * u * c.y + u * u * u * d.y;
    return out;
  }

  function drawConnection(t, center, radius, elapsed) {
    const a = curveA, b = curveB, c = curveC, d = work;
    a.x = center.x - radius * .45; a.y = center.y + radius * .55;
    b.x = a.x - radius * .35; b.y = a.y + radius * .93;
    c.x = d.x + radius * .58; c.y = d.y - radius * .28;
    const reveal = state === "forming" ? smooth(0.18, 0.63, elapsed) : 1;
    const alpha = connection * intensity;
    // Evaluate the shared cubic once, then reuse its points for all strands.
    for (let i = 0; i <= 60; i++) {
      const u = i / 60 * reveal;
      curvePoint(u, a, b, c, d, curveFrom);
      connectionSamples[i * 3] = curveFrom.x;
      connectionSamples[i * 3 + 1] = curveFrom.y;
      connectionSamples[i * 3 + 2] = Math.sin(u * Math.PI) * (phone ? 2.7 : 5.7);
    }
    ctx.save();
    ctx.globalCompositeOperation = "screen";
    for (let line = 0; line < 3; line++) {
      ctx.beginPath();
      for (let i = 0; i <= 60; i++) {
        const u = i / 60 * reveal;
        const wave = Math.sin(u * TAU * 1.7 - t * (0.5 + line * 0.12) + line) * connectionSamples[i * 3 + 2];
        const x = connectionSamples[i * 3] + wave, y = connectionSamples[i * 3 + 1] + line * 2;
        if (!i) ctx.moveTo(x, y); else ctx.lineTo(x, y);
      }
      ctx.strokeStyle = line === 1 ? `rgba(127,110,255,${alpha * 0.25})` : `rgba(76,208,255,${alpha * (line === 0 ? 0.5 : 0.14)})`;
      ctx.lineWidth = line === 2 ? 5.5 : 0.85;
      ctx.stroke();
    }
    for (let i = 0; i < 5; i++) {
      const u = (t * 0.2 + i / 5) % 1;
      if (u > reveal) continue;
      const point = curvePoint(u, a, b, c, d, curveFrom);
      const tail = curvePoint(Math.max(0, u - 0.06), a, b, c, d, curveTo);
      ctx.beginPath();
      ctx.moveTo(tail.x, tail.y);
      ctx.lineTo(point.x, point.y);
      ctx.strokeStyle = `rgba(119,232,255,${alpha * Math.sin(u * Math.PI) * 0.8})`;
      ctx.lineWidth = 1.2;
      ctx.stroke();
      glow(point.x, point.y, phone ? 10 : 17, alpha * Math.sin(u * Math.PI) * 0.6, "cyan");
    }
    glow(d.x, d.y, phone ? 28 : 45, alpha * 0.38 * reveal, "cyan");
    ctx.restore();
  }

  function resultWave(elapsed, center, radius) {
    if (elapsed < 0 || elapsed > 2.4) return;
    const progress = elapsed / 2.4;
    ctx.save();
    ctx.globalCompositeOperation = "screen";
    ctx.strokeStyle = `rgba(139,226,255,${Math.sin(Math.PI * progress) * (1 - progress) * 0.4})`;
    ctx.lineWidth = 1;
    ctx.beginPath();
    ctx.ellipse(center.x, center.y, radius * (1 + progress * 3.2), radius * (0.32 + progress * 0.8), -0.12, 0, TAU);
    ctx.stroke();
    glow(center.x + radius * 0.53, center.y - radius * 0.36, radius * 0.5, Math.sin(Math.PI * progress) * 0.38, "amber");
    ctx.restore();
  }

  function render(timestamp) {
    if (destroyed || !ctx) return;
    const cpuStarted = performance.now();
    const interval = timestamp - previousTime;
    const dt = clamp((timestamp - previousTime) / 1000, 0.001, 0.06);
    previousTime = timestamp;
    const t = (timestamp - started) / 1000;
    const elapsed = t - stateStarted;
    const ease = 1 - Math.exp(-dt * 3.4);
    energy = mix(energy, STATES[state].energy, ease);
    connection = mix(connection, STATES[state].connection, ease);
    warmth = mix(warmth, STATES[state].warmth, ease);
    pointer.x = mix(pointer.x, pointer.tx, 1 - Math.exp(-dt * 2.5));
    pointer.y = mix(pointer.y, pointer.ty, 1 - Math.exp(-dt * 2.5));
    const anchored = orbAnchor?.dataset.orbMotion === "fixed" || orbAnchor?.dataset.orbMotion === "anchored";
    center.x = orb.x + (anchored ? 0 : pointer.x * (phone ? 2 : 4));
    center.y = orb.y + (anchored ? 0 : Math.sin(t * TAU / 9.73) * (reducedMotion ? .35 : phone ? 1.6 : 2.8) + pointer.y * 2);
    const radius = orb.r;
    const voiceActive = state === "listening" || state === "speaking";
    const voice = voiceActive && audioLevel !== null ? audioLevel : 0;

    atmosphere(t);
    drawConnection(t, center, radius, elapsed);
    if (orbVisible) {
    drawPlatform(t, center, radius);
    drawOrbitalDust(t, center, radius, false);
    const geometry = makeRingGeometry(t, center, radius);
    drawRingLayer(geometry, false);
    drawCore(t, center, radius, voice);
    drawRingLayer(geometry, true);
    drawOrbitalDust(t, center, radius, true);

    if (voiceActive) {
      ctx.save();
      ctx.globalCompositeOperation = "screen";
      ctx.beginPath();
      ctx.ellipse(center.x, center.y, radius * (1.51 + voice * 0.06), radius * (0.44 + voice * 0.025), -0.16, Math.PI * 0.03, Math.PI * 0.96);
      ctx.strokeStyle = `rgba(90,224,255,${0.18 + voice * 0.33})`;
      ctx.lineWidth = 0.8 + voice;
      ctx.stroke();
      ctx.restore();
    }
    if (warmth > 0.005) {
      ctx.save();
      ctx.globalCompositeOperation = "screen";
      glow(center.x - radius * 0.7, center.y - radius * 0.3, radius * 0.52, warmth, "amber");
      ctx.restore();
    }
    if (state === "result") resultWave(elapsed, center, radius);
    }

    renderedFrames++;
    // Every depth and motion system remains active. Even the explicit "high"
    // density mode adapts its raster size to sustained frame-budget pressure.
    // These are CPU submission/RAF observations, never a GPU-time estimate.
    if (interval > 0 && interval < 120) averageFrame = mix(averageFrame, interval, .035);
    averageCpu = mix(averageCpu, performance.now() - cpuStarted, .035);
    if (timestamp - qualitySample > 1800 && t > 3) {
      qualitySample = timestamp;
      const pressured = averageFrame > 24 || averageCpu > 9;
      pressureSamples = pressured ? pressureSamples + 1 : 0;
      if (pressureSamples >= 2 && resolutionScale > .7) {
        resolutionScale = Math.max(.7, resolutionScale - .1);
        pressureSamples = 0; budgetChangedAt = timestamp; queueResize();
      } else if (!pressured && averageFrame < 18 && averageCpu < 5 &&
          timestamp - budgetChangedAt > 12000 && resolutionScale < 1) {
        resolutionScale = Math.min(1, resolutionScale + .05);
        budgetChangedAt = timestamp; queueResize();
      }
    }
    if (audioEventPending && performance.now() - lastAudioEventAt >= 33) emitSceneChange();
    animationFrame = requestAnimationFrame(render);
  }

  function setState(next) {
    if (!Object.prototype.hasOwnProperty.call(STATES, next)) return false;
    if (state !== next) {
      state = next;
      stateStarted = (performance.now() - started) / 1000;
      emitSceneChange();
    }
    return true;
  }

  function emitSceneChange() {
    audioEventPending = false;
    lastAudioEventAt = performance.now();
    window.dispatchEvent(new CustomEvent("continuum:scenechange", {
      detail: { state, intensity, quality, audioLevel, audioSampleAt },
    }));
  }

  function setQuality(value) {
    const presets = { low: 0.6, medium: 0.8, high: 1 };
    if (value === "auto") {
      if (!explicitQuality) return;
      explicitQuality = false;
      emitSceneChange();
      return;
    }
    const numeric = typeof value === "string" ? presets[value] : Number(value);
    if (!Number.isFinite(numeric)) return;
    const next = clamp(numeric, .6, 1);
    const changed = quality !== next || !explicitQuality;
    explicitQuality = true;
    if (!changed) return;
    quality = next;
    queueResize();
    emitSceneChange();
  }

  function onPointer(event) {
    if (event.pointerType === "touch") return;
    pointer.tx = clamp(event.clientX / width * 2 - 1, -1, 1);
    pointer.ty = clamp(event.clientY / height * 2 - 1, -1, 1);
  }

  function onPointerLeave() {
    pointer.tx = pointer.ty = 0;
  }

  function onScroll() {
    if (!orbAnchor && !workAnchor) return;
    if (anchorTimer) return;
    anchorTimer = requestAnimationFrame(() => {
      anchorTimer = 0;
      updateAnchors();
    });
  }

  function start() {
    if (active || destroyed) return;
    canvas = document.getElementById("scene-canvas");
    if (!canvas) return;
    ctx = canvas.getContext("2d", { alpha: false, desynchronized: true });
    if (!ctx) return;
    active = true;
    reducedMotion = window.matchMedia("(prefers-reduced-motion: reduce)").matches;
    makeSprite("cyan", "37,208,255");
    makeSprite("blue", "57,109,255");
    makeSprite("violet", "152,86,255");
    makeSprite("amber", "255,180,90");
    makeAtmosphereTextures();
    resize();
    if (typeof ResizeObserver !== "undefined") {
      resizeObserver = new ResizeObserver(queueResize);
      resizeObserver.observe(canvas);
    }
    window.addEventListener("resize", queueResize, { passive: true });
    window.addEventListener("pointermove", onPointer, { passive: true });
    document.addEventListener("pointerleave", onPointerLeave, { passive: true });
    window.addEventListener("scroll", onScroll, { passive: true });
    previousTime = performance.now();
    animationFrame = requestAnimationFrame(render);
  }

  function destroy() {
    destroyed = true;
    cancelAnimationFrame(animationFrame);
    cancelAnimationFrame(anchorTimer);
    cancelAnimationFrame(resizeTimer);
    resizeObserver?.disconnect();
    window.removeEventListener("resize", queueResize);
    window.removeEventListener("pointermove", onPointer);
    document.removeEventListener("pointerleave", onPointerLeave);
    window.removeEventListener("scroll", onScroll);
  }

  window.ContinuumScene = Object.freeze({
    setState,
    resize: queueResize,
    setQuality,
    setOrbVisible() {
      // Compatibility hook only: the procedural replacement is retired.
      orbVisible = false;
    },
    setIntensity(value) {
      const numeric = Number(value);
      if (Number.isFinite(numeric)) {
        const next = clamp(numeric, .35, 1.6);
        if (next === intensity) return;
        intensity = next;
        if (backdrop) buildBackdrop();
        emitSceneChange();
      }
    },
    setAudioLevel(value) {
      if (value === null) { if (audioLevel === null) return; audioLevel = null; }
      else if (Number.isFinite(Number(value))) audioLevel = clamp(Number(value), 0, 1);
      else return;
      audioSampleAt = performance.now();
      if (value === null || audioSampleAt - lastAudioEventAt >= 33) emitSceneChange();
      else audioEventPending = true;
    },
    getState() { return state; },
    getStatus() { return { active, state, quality, intensity, audioLevel, audioSampleAt, orbVisible, width, height, pixelRatio,
      atmosphereLayers: 3, dustLayers: 3, rayCount: phone ? 3 : 5,
      resolutionScale, adaptiveResolution: true, detailMode: explicitQuality ? "selected" : "auto", renderedFrames,
      bufferResizes, backdropBuilds, renderCpuAverageMs: averageCpu, frameIntervalAverageMs: averageFrame,
      elapsedSeconds: (performance.now() - started) / 1000 }; },
    destroy,
  });

  if (document.readyState === "loading") document.addEventListener("DOMContentLoaded", start, { once: true });
  else start();
})();
