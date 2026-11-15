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
  let qualitySample = 0;
  let reducedMotion = false;
  let destroyed = false;
  let active = false;
  let anchorTimer = 0;
  let orbAnchor = null;
  let workAnchor = null;
  let orb = { x: 0, y: 0, r: 0 };
  let work = { x: 0, y: 0 };
  const pointer = { x: 0, y: 0, tx: 0, ty: 0 };
  const sprites = Object.create(null);
  const lightFields = Object.create(null);
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
    stars.push({ x: random(), y: random(), z: random(), phase: random() * TAU, radius: 0.4 + random() * 0.8 });
  }
  for (let i = 0; i < 84; i++) {
    dust.push({ x: random(), y: random(), z: random(), speed: 0.002 + random() * 0.006, phase: random() * TAU });
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

  function updateAnchors() {
    orbAnchor = document.querySelector("[data-orb-anchor]");
    workAnchor = document.querySelector("[data-work-anchor]");
    phone = width < 700;
    const compact = width < 1060;
    let x = width * (phone ? 0.76 : 0.73);
    let y = phone ? 176 : compact ? 240 : 260;
    let r = phone ? clamp(width * 0.16, 46, 68) : clamp(width * 0.092, 90, 150);
    if (orbAnchor) {
      const bounds = orbAnchor.getBoundingClientRect();
      if (bounds.width > 0 && bounds.height > 0) {
        x = bounds.left + bounds.width / 2;
        y = bounds.top + bounds.height / 2;
        r = clamp(Math.min(bounds.width, bounds.height) * 0.365, 40, 172);
      }
    }
    orb = { x, y, r };
    work = { x: width * (phone ? 0.57 : 0.52), y: phone ? 326 : 440 };
    if (workAnchor) {
      const bounds = workAnchor.getBoundingClientRect();
      if (bounds.width > 0 && bounds.height > 0) {
        work = { x: bounds.left + bounds.width * 0.56, y: bounds.top + Math.min(36, bounds.height * 0.08) };
      }
    }
  }

  function resize() {
    if (!canvas || destroyed) return;
    const bounds = canvas.getBoundingClientRect();
    width = Math.max(1, bounds.width || window.innerWidth);
    height = Math.max(1, bounds.height || window.innerHeight);
    const maximumRatio = quality < 0.7 ? 1.25 : phone ? 1.65 : 1.8;
    pixelRatio = Math.min(window.devicePixelRatio || 1, maximumRatio);
    canvas.width = Math.round(width * pixelRatio);
    canvas.height = Math.round(height * pixelRatio);
    ctx.setTransform(pixelRatio, 0, 0, pixelRatio, 0, 0);
    updateAnchors();
    window.dispatchEvent(new CustomEvent("continuum:sceneresize"));
  }

  function atmosphere(t) {
    ctx.globalCompositeOperation = "source-over";
    ctx.globalAlpha = 1;
    ctx.fillStyle = "#030810";
    ctx.fillRect(0, 0, width, height);

    // These broad, independently drifting fields describe the distant room.
    const drift = reducedMotion ? 0.35 : 1;
    softLight(width * (0.65 + Math.sin(t / 43.7) * 0.05 * drift), 160, width * 0.59, "19,39,87", 0.22);
    softLight(width * 0.29, height * (0.26 + Math.sin(t / 71.3) * 0.04 * drift), width * 0.44, "8,97,121", 0.13);
    softLight(width * 0.83, height * 0.42, width * 0.44, "66,28,122", 0.12);

    ctx.save();
    ctx.globalCompositeOperation = "screen";
    // Architectural arches remain quieter than the objects they enclose.
    for (let i = 0; i < 5; i++) {
      const inset = i * (phone ? 15 : 29);
      const x = width * 0.5 + pointer.x * (2 + i);
      const y = phone ? 260 : 370;
      ctx.beginPath();
      ctx.ellipse(x, y, width * 0.72 - inset, (phone ? 320 : 475) - inset, -0.03, Math.PI * 1.02, Math.PI * 1.98);
      ctx.strokeStyle = i === 1 ? "rgba(70,184,221,.095)" : "rgba(51,101,151,.042)";
      ctx.lineWidth = i === 1 ? 1.1 : 3;
      ctx.stroke();
    }

    // Long narrow beams suggest light passing through thin atmospheric haze.
    for (let i = 0; i < 4; i++) {
      const bx = width * (0.1 + i * 0.27) + Math.sin(t * 0.023 + i) * 20;
      const beam = ctx.createLinearGradient(bx, 20, bx - 90, Math.min(height, 650));
      beam.addColorStop(0, "rgba(77,170,225,.005)");
      beam.addColorStop(0.3, `rgba(48,115,173,${0.024 + Math.sin(t * 0.051 + i) * 0.006})`);
      beam.addColorStop(1, "rgba(24,79,116,0)");
      ctx.fillStyle = beam;
      ctx.beginPath();
      ctx.moveTo(bx - 15, 0);
      ctx.lineTo(bx + 11, 0);
      ctx.lineTo(bx - 70, height * 0.77);
      ctx.lineTo(bx - 210, height * 0.77);
      ctx.closePath();
      ctx.fill();
    }

    const starCount = Math.floor(stars.length * quality);
    for (let i = 0; i < starCount; i++) {
      const s = stars[i];
      const sx = s.x * width + pointer.x * s.z * 11;
      const sy = s.y * height + pointer.y * s.z * 8;
      const alpha = (0.13 + s.z * 0.33) * (0.76 + Math.sin(t * (0.32 + s.z * 0.3) + s.phase) * 0.24);
      ctx.fillStyle = `rgba(153,209,237,${alpha})`;
      ctx.beginPath();
      ctx.arc(sx, sy, s.radius * (phone ? 0.7 : 1), 0, TAU);
      ctx.fill();
      if (i % 19 === 0) glow(sx, sy, 9 + s.z * 7, alpha * 0.55, "blue");
    }

    const dustCount = Math.floor(dust.length * quality);
    for (let i = 0; i < dustCount; i++) {
      const p = dust[i];
      const sx = ((p.x + t * p.speed * 0.12) % 1) * width + Math.sin(t * 0.07 + p.phase) * 14 + pointer.x * p.z * 18;
      const sy = ((p.y - t * p.speed * 0.15 + 100) % 1) * height + Math.cos(t * 0.05 + p.phase) * 10;
      const alpha = 0.13 + p.z * 0.28;
      glow(sx, sy, 2.4 + p.z * 5, alpha, i % 11 === 0 ? "amber" : i % 4 === 0 ? "violet" : "cyan");
    }

    // Fine light veins cross different depths and never restart with app state.
    for (let i = 0; i < 5; i++) {
      const baseY = height * (0.18 + i * 0.145);
      const phase = t * (0.035 + i * 0.007) + i * 1.7;
      ctx.beginPath();
      ctx.moveTo(-50, baseY + Math.sin(phase) * 25);
      ctx.bezierCurveTo(width * 0.22, baseY - 55, width * 0.66, baseY + 55, width + 50, baseY - 42);
      ctx.strokeStyle = `rgba(${i % 2 ? "103,88,183" : "46,153,185"},${0.035 + i * 0.003})`;
      ctx.lineWidth = 0.7;
      ctx.stroke();
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

  function curvePoint(u, a, b, c, d) {
    const v = 1 - u;
    return { x: v * v * v * a.x + 3 * v * v * u * b.x + 3 * v * u * u * c.x + u * u * u * d.x,
      y: v * v * v * a.y + 3 * v * v * u * b.y + 3 * v * u * u * c.y + u * u * u * d.y };
  }

  function drawConnection(t, center, radius, elapsed) {
    const a = { x: center.x - radius * 0.45, y: center.y + radius * 0.55 };
    const d = work;
    const b = { x: a.x - radius * 0.35, y: a.y + radius * 0.93 };
    const c = { x: d.x + radius * 0.58, y: d.y - radius * 0.28 };
    const reveal = state === "forming" ? smooth(0.18, 0.63, elapsed) : 1;
    const alpha = connection * intensity;
    ctx.save();
    ctx.globalCompositeOperation = "screen";
    for (let line = 0; line < 3; line++) {
      ctx.beginPath();
      for (let i = 0; i <= 60; i++) {
        const u = i / 60 * reveal;
        const point = curvePoint(u, a, b, c, d);
        const wave = Math.sin(u * TAU * 1.7 - t * (0.5 + line * 0.12) + line) * Math.sin(u * Math.PI) * (phone ? 2.7 : 5.7);
        if (!i) ctx.moveTo(point.x + wave, point.y + line * 2); else ctx.lineTo(point.x + wave, point.y + line * 2);
      }
      ctx.strokeStyle = line === 1 ? `rgba(127,110,255,${alpha * 0.25})` : `rgba(76,208,255,${alpha * (line === 0 ? 0.5 : 0.14)})`;
      ctx.lineWidth = line === 2 ? 5.5 : 0.85;
      ctx.stroke();
    }
    for (let i = 0; i < 5; i++) {
      const u = (t * 0.2 + i / 5) % 1;
      if (u > reveal) continue;
      const point = curvePoint(u, a, b, c, d);
      const tail = curvePoint(Math.max(0, u - 0.06), a, b, c, d);
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
    const center = {
      x: orb.x + pointer.x * (phone ? 2 : 4),
      y: orb.y + Math.sin(t * TAU / 9.73) * (reducedMotion ? 0.35 : phone ? 1.6 : 2.8) + pointer.y * 2,
    };
    const radius = orb.r;
    const voiceActive = state === "listening" || state === "speaking";
    const voice = voiceActive ? audioLevel === null ? (Math.sin(t * 7.3) * Math.sin(t * 4.13) + 1) * 0.28 : audioLevel : 0;

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

    // Adapt only detail density. Every depth and motion system remains present.
    averageFrame = mix(averageFrame, dt * 1000, 0.035);
    if (!explicitQuality && timestamp - qualitySample > 6000 && t > 5) {
      qualitySample = timestamp;
      const next = averageFrame > 30 ? Math.max(0.6, quality - 0.1) : averageFrame < 19 ? Math.min(1, quality + 0.05) : quality;
      if (next !== quality) {
        quality = next;
        resize();
        emitSceneChange();
      }
    }
    animationFrame = requestAnimationFrame(render);
  }

  function setState(next) {
    if (!Object.prototype.hasOwnProperty.call(STATES, next)) return false;
    if (state !== next) {
      state = next;
      stateStarted = (performance.now() - started) / 1000;
    }
    emitSceneChange();
    return true;
  }

  function emitSceneChange() {
    window.dispatchEvent(new CustomEvent("continuum:scenechange", {
      detail: { state, intensity, quality, audioLevel, audioSampleAt },
    }));
  }

  function setQuality(value) {
    const presets = { low: 0.6, medium: 0.8, high: 1 };
    if (value === "auto") {
      explicitQuality = false;
      emitSceneChange();
      return;
    }
    const numeric = typeof value === "string" ? presets[value] : Number(value);
    if (!Number.isFinite(numeric)) return;
    explicitQuality = true;
    quality = clamp(numeric, 0.6, 1);
    resize();
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
    resize();
    window.addEventListener("resize", resize, { passive: true });
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
    window.removeEventListener("resize", resize);
    window.removeEventListener("pointermove", onPointer);
    document.removeEventListener("pointerleave", onPointerLeave);
    window.removeEventListener("scroll", onScroll);
  }

  window.ContinuumScene = Object.freeze({
    setState,
    resize,
    setQuality,
    setOrbVisible(value) {
      orbVisible = Boolean(value);
    },
    setIntensity(value) {
      const numeric = Number(value);
      if (Number.isFinite(numeric)) {
        intensity = clamp(numeric, 0.35, 1.6);
        emitSceneChange();
      }
    },
    setAudioLevel(value) {
      if (value === null) audioLevel = null;
      else if (Number.isFinite(Number(value))) audioLevel = clamp(Number(value), 0, 1);
      audioSampleAt = performance.now();
      emitSceneChange();
    },
    getState() { return state; },
    getStatus() { return { active, state, quality, intensity, audioLevel, audioSampleAt, orbVisible, width, height, elapsedSeconds: (performance.now() - started) / 1000 }; },
    destroy,
  });

  if (document.readyState === "loading") document.addEventListener("DOMContentLoaded", start, { once: true });
  else start();
})();
