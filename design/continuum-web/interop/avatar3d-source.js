/**
 * Continuum original-avatar web adapter.
 * Source: Ullmann27/device-ai, Code55 1b90611388cc2b1978ddfe32064a25dd3a08cee1.
 * The GLB, geometry, textures, materials and authored morphs stay unchanged.
 * No artificial lip motion is inferred from a UI state or from a sine wave.
 */
import * as THREE from 'three';
import { GLTFLoader } from 'three/addons/loaders/GLTFLoader.js';
import { AVATAR_TARGETS, OriginalAvatarMotion } from './avatar-motion.js';

const SOURCE_SHA256 = 'c8b88385c73fa01596a5734d20859440b1ecaef06cb1abea6cd7a47e62b08b9a';
const SOURCE_BYTES = 12038416;
// The unchanged Blender fallback uses an orthographic 0.6 × 0.45 m window.
// Fitting that same window keeps the full bust proportional at any host size.
const PORTRAIT = Object.freeze({ width: .6, height: .45, centerY: .215, centerZ: .025, distance: 1.45 });
const SCRIPT_BASE = new URL('.', document.currentScript?.src || document.baseURI);
// Optional data: URLs support the separately packaged single-file preview.
const GLB_URL = window.CONTINUUM_ASSETS?.avatarModel || new URL('assets/continuum-avatar.glb', SCRIPT_BASE).href;
const FALLBACK_URL = window.CONTINUUM_ASSETS?.avatarFallback || new URL('assets/continuum-avatar-original.png', SCRIPT_BASE).href;
const DEG = Math.PI / 180;
const Y_AXIS = new THREE.Vector3(0, 1, 0);
const X_AXIS = new THREE.Vector3(1, 0, 0);
const yawRotation = new THREE.Quaternion();
const pitchRotation = new THREE.Quaternion();
const motion = new OriginalAvatarMotion();
let state = 'idle';
let intensity = .7;
let renderer = null;
let canvas = null;
let scene = null;
let camera = null;
let root = null;
let crop = null;
let joints = null;
let sceneGroup = null;
let keyLight = null;
let cyanLight = null;
let violetLight = null;
let taskLight = 0;
let lastRenderTime = null;
let framing = null;
let frame = 0;
let running = false;
let failed = false;
let loaded = false;
let sourceVerified = false;
let sourceVerification = 'pending';
let lastError = null;
let frames = 0;
let observer = null;
let resizeFrame = 0;
let morphMeshes = [];
const morphBindings = [];
let resolutionScale = 1;
let budgetSampleAt = 0;
let budgetChangedAt = 0;
let intervalAverage = 16.7;
let renderCpuAverage = 0;
let pressureSamples = 0;
let bufferResizes = 0;
let firstFrame = true;
const listeners = [];

function listen(target, event, handler, options) {
  target.addEventListener(event, handler, options);
  listeners.push(() => target.removeEventListener(event, handler, options));
}

function announce(mode) {
  window.dispatchEvent(new CustomEvent('continuum:avatarchange', { detail: {
    mode, state, loaded, sourceVerified, sourceVerification,
    sourceSha256: SOURCE_SHA256, frames, error: lastError,
  } }));
}

function showFallback(reason) {
  if (reason) lastError = String(reason);
  if (crop) {
    crop.style.backgroundImage = `url("${FALLBACK_URL}")`;
    crop.style.backgroundPosition = '50% 50%';
    crop.style.backgroundSize = crop.dataset.avatarFit === 'cover' ? 'cover' : 'contain';
    crop.style.backgroundRepeat = 'no-repeat';
    crop.dataset.avatarRenderer = 'original-png';
    crop.setAttribute('aria-label', 'Original-Continuum-Avatar – aus dem unveränderten 3D-Modell gerendert');
  }
  if (canvas) canvas.style.opacity = '0';
  announce('original-png');
}

function fitCamera() {
  if (!renderer || !camera || !crop) return;
  const width = Math.max(1, crop.clientWidth || 272);
  const height = Math.max(1, crop.clientHeight || 299);
  const aspect = width / height;
  const referenceAspect = PORTRAIT.width / PORTRAIT.height;
  const mode = crop.dataset.avatarFit === 'cover' ? 'cover' : 'contain';
  // Preserve the authored portrait projection. Only raster resolution adapts;
  // a larger CSS portrait must not grow into an unlimited DPR=2 render target.
  const compact = window.innerWidth <= 760;
  const maximumPixels = compact ? 280000 : 460000;
  const pixelRatio = Math.max(.75, Math.min(window.devicePixelRatio || 1,
    compact ? 1.5 : 1.7, Math.sqrt(maximumPixels / (width * height))) * resolutionScale);
  if (framing && framing.width === width && framing.height === height &&
      framing.mode === mode && Math.abs(framing.pixelRatio - pixelRatio) < .001) return;
  const fit = mode === 'cover' ? Math.min(1, referenceAspect / aspect) : Math.max(1, referenceAspect / aspect);
  const halfHeight = PORTRAIT.height * .5 * fit;
  framing = { width, height, mode, halfHeight, halfWidth: halfHeight * aspect, pixelRatio };
  camera.left = -halfHeight * width / height;
  camera.right = halfHeight * width / height;
  camera.top = halfHeight;
  camera.bottom = -halfHeight;
  camera.updateProjectionMatrix();
  // setDrawingBufferSize changes dimensions and DPR together, avoiding the
  // duplicate buffer reset caused by setPixelRatio followed by setSize.
  renderer.setDrawingBufferSize(width, height, pixelRatio);
  bufferResizes++;
}

function queueCameraFit() {
  if (resizeFrame || failed || !renderer) return;
  resizeFrame = requestAnimationFrame(() => { resizeFrame = 0; fitCamera(); });
}

function sampleBudget(now, intervalMs, cpuMs) {
  // CPU duration is an enqueue measurement, not GPU completion or measured fps.
  // Frame intervals also cover the rest of the app; sustained pressure lowers
  // only this raster's resolution. Motion still evaluates on every RAF.
  if (intervalMs > 0 && intervalMs < 120) intervalAverage += (intervalMs - intervalAverage) * .035;
  renderCpuAverage += (cpuMs - renderCpuAverage) * .035;
  if (frames < 90 || now - budgetSampleAt < 1800) return;
  budgetSampleAt = now;
  const pressured = intervalAverage > 24 || renderCpuAverage > 10;
  pressureSamples = pressured ? pressureSamples + 1 : 0;
  if (pressureSamples >= 2 && resolutionScale > .7) {
    resolutionScale = Math.max(.7, resolutionScale - .1);
    pressureSamples = 0; budgetChangedAt = now; queueCameraFit();
  } else if (!pressured && intervalAverage < 18 && renderCpuAverage < 5 &&
      now - budgetChangedAt > 12000 && resolutionScale < 1) {
    resolutionScale = Math.min(1, resolutionScale + .05);
    budgetChangedAt = now; queueCameraFit();
  }
}

function rotate(name, pitch, yaw) {
  const joint = joints[name];
  yawRotation.setFromAxisAngle(Y_AXIS, yaw * DEG);
  pitchRotation.setFromAxisAngle(X_AXIS, pitch * DEG);
  joint.object.quaternion.copy(joint.rest).multiply(yawRotation).multiply(pitchRotation);
}

function render(now) {
  if (!running || failed || !loaded) return;
  const cpuStarted = performance.now();
  const intervalMs = lastRenderTime === null ? 16.7 : now - lastRenderTime;
  try {
    const pose = motion.update(now);
    rotate('Neck', pose.neckPitch, pose.neckYaw);
    rotate('Head', pose.headPitch, pose.headYaw);
    rotate('EyeLeft', pose.eyePitch, pose.eyeYaw);
    rotate('EyeRight', pose.eyePitch, pose.eyeYaw);
    rotate('Jaw', pose.jawPitch, 0);
    for (const binding of morphBindings) {
      const value = pose.weights[binding.name];
      if (binding.influences[binding.index] !== value) binding.influences[binding.index] = value;
    }
    sceneGroup.position.y = pose.breathOffset;
    sceneGroup.rotation.z = pose.breathRoll;
    const seconds = now * .001;
    const work = state === 'forming' || state === 'working';
    const dt = lastRenderTime === null ? 1 / 60 : Math.max(0, Math.min(.1, (now - lastRenderTime) * .001));
    lastRenderTime = now;
    taskLight += ((work ? 1 : 0) - taskLight) * (1 - Math.exp(-dt / .55));
    const energy = .92 + Math.sin(seconds * .67) * .045 + Math.sin(seconds * 1.17) * .025;
    // A neutral face key stays present in front of the bright background orb;
    // coloured accents remain restrained and approach task changes smoothly.
    cyanLight.intensity = (.28 + taskLight * .1) * energy * (.8 + intensity * .2);
    cyanLight.position.x = .38 + Math.sin(seconds * .19) * .025;
    violetLight.intensity = .17 + Math.sin(seconds * .31 + .7) * .012;
    keyLight.intensity = 1.25 + Math.sin(seconds * .43) * .012;
    renderer.render(scene, camera);
    if (firstFrame && (renderer.getContext().isContextLost() || renderer.info.render.calls === 0)) {
      throw new Error('Original avatar did not produce a drawable WebGL frame.');
    }
    frames++;
    if (firstFrame) {
      firstFrame = false;
      canvas.style.opacity = '1';
      crop.style.backgroundImage = 'none';
      crop.style.animation = 'none';
      crop.dataset.avatarRenderer = 'original-webgl';
      crop.setAttribute('aria-label', 'Original-Continuum-Avatar mit aktivem 3D-Rig und Augenbewegung');
      announce('original-webgl');
    }
    sampleBudget(now, intervalMs, performance.now() - cpuStarted);
  } catch (error) {
    failed = true;
    running = false;
    showFallback(error?.message || error);
    return;
  }
  frame = requestAnimationFrame(render);
}

function setState(next) {
  motion.setState(next);
  state = motion.state;
}

function gazeAtElement(target, durationMs = 2200) {
  if (!(target instanceof Element) || !crop) return false;
  // Selection-time layout reads only; no geometry queries in the render loop.
  const chosen = target.getBoundingClientRect();
  const portrait = crop.getBoundingClientRect();
  if (chosen.width <= 0 || chosen.height <= 0 || portrait.width <= 0 || portrait.height <= 0) return false;
  const centerX = portrait.left + portrait.width * .5;
  const centerY = portrait.top + portrait.height * .43;
  const gazeX = THREE.MathUtils.clamp((chosen.left + chosen.width * .5 - centerX) / Math.max(240, window.innerWidth * .5), -.55, .55);
  const gazeY = THREE.MathUtils.clamp((centerY - chosen.top - chosen.height * .5) / Math.max(320, window.innerHeight * .55), -.38, .32);
  motion.setGaze(gazeX, gazeY, durationMs);
  return true;
}

function dispose() {
  running = false;
  cancelAnimationFrame(frame);
  cancelAnimationFrame(resizeFrame);
  observer?.disconnect();
  for (const remove of listeners.splice(0)) remove();
  if (root) root.traverse(object => {
    if (!object.isMesh) return;
    object.geometry?.dispose();
    for (const material of Array.isArray(object.material) ? object.material : [object.material]) {
      for (const value of Object.values(material)) if (value?.isTexture) value.dispose();
      material.dispose();
    }
  });
  renderer?.dispose();
  canvas?.remove();
  showFallback();
}

window.ContinuumAvatar = Object.freeze({
  setState,
  setGaze: (x, y, durationMs) => motion.setGaze(x, y, durationMs),
  /** Actual speech event: {viseme:'AA', weight:0..1, jawOpen:0..1, durationMs:20..500}. */
  setSpeechFrame: frame => motion.setSpeechFrame(frame),
  clearSpeech: () => motion.clearSpeech(),
  resize: queueCameraFit,
  getStatus: () => ({ renderer: !failed && loaded && !firstFrame ? 'original-webgl' : 'original-png',
    state, loaded, sourceVerified, sourceVerification, sourceSha256: SOURCE_SHA256,
    morphMeshCount: morphMeshes.length, frames, lastError, framing,
    resolutionScale, bufferResizes, renderCpuAverageMs: renderCpuAverage,
    frameIntervalAverageMs: intervalAverage,
    speechSource: 'external-viseme-events-only' }),
  dispose,
});

async function boot() {
  crop = document.querySelector('.avatar-zone .avatar-crop') || document.querySelector('.avatar-crop');
  if (!crop) { lastError = 'Avatar container missing'; return; }
  // The inspected original is also the fallback. Never show a different portrait.
  showFallback();
  const initial = window.ContinuumScene?.getStatus?.();
  if (initial?.state) setState(initial.state);
  if (Number.isFinite(initial?.intensity)) intensity = initial.intensity;
  listen(window, 'continuum:scenechange', event => {
    const detail = event.detail || {};
    if (detail.state) setState(detail.state);
    if (Number.isFinite(detail.intensity)) intensity = Math.min(1.8, Math.max(.1, detail.intensity));
  });
  listen(window, 'continuum:sceneresize', queueCameraFit);
  listen(window, 'continuum:viseme', event => motion.setSpeechFrame(event.detail));
  listen(window, 'continuum:speechend', () => motion.clearSpeech());
  listen(window, 'continuum:gaze', event => {
    const { x, y, durationMs, target } = event.detail || {};
    if (target && gazeAtElement(target, durationMs)) return;
    if (Number.isFinite(x) && Number.isFinite(y)) motion.setGaze(x, y, durationMs);
  });
  listen(document, 'pointerdown', event => {
    if (!(event.target instanceof Element)) return;
    const target = event.target.closest('[data-phase], [data-source], [data-op-source]') ||
      event.target.closest('.presence-instrument-left, .presence-instrument-right, .workspace-body, .chart-panel, .document-pane');
    if (!target) return;
    gazeAtElement(target);
  }, { passive: true });

  try {
    canvas = document.createElement('canvas');
    canvas.className = 'avatar-original-canvas';
    canvas.setAttribute('aria-hidden', 'true');
    Object.assign(canvas.style, { position: 'absolute', inset: '0', display: 'block',
      width: '100%', height: '100%', pointerEvents: 'none', opacity: '0', transition: 'opacity .4s ease' });
    const context = canvas.getContext('webgl2', { alpha: true, antialias: true,
      premultipliedAlpha: true, powerPreference: 'high-performance', preserveDrawingBuffer: false });
    if (!context) throw new Error('WebGL2 unavailable; using the verified original PNG.');
    renderer = new THREE.WebGLRenderer({ canvas, context, alpha: true, antialias: true });
    renderer.setClearColor(0x000000, 0);
    renderer.outputColorSpace = THREE.SRGBColorSpace;
    renderer.toneMapping = THREE.ACESFilmicToneMapping;
    renderer.toneMappingExposure = 1;
    crop.appendChild(canvas);
    scene = new THREE.Scene();
    camera = new THREE.OrthographicCamera(-.3, .3, .225, -.225, .05, 4);
    camera.position.set(0, PORTRAIT.centerY, PORTRAIT.distance);
    camera.lookAt(0, PORTRAIT.centerY, PORTRAIT.centerZ);
    const faceFocus = new THREE.Object3D();
    faceFocus.position.set(0, .27, .03);
    scene.add(faceFocus);
    scene.add(new THREE.HemisphereLight(0xe3ecf8, 0x11182b, .4));
    keyLight = new THREE.DirectionalLight(0xf5f7ff, 1.25);
    keyLight.position.set(-.36, .48, .74);
    keyLight.target = faceFocus;
    scene.add(keyLight);
    const faceFill = new THREE.DirectionalLight(0xe9f1fc, .23);
    faceFill.position.set(.22, .25, .9);
    faceFill.target = faceFocus;
    scene.add(faceFill);
    cyanLight = new THREE.DirectionalLight(0x8bd9fb, .28);
    cyanLight.position.set(.38, .35, .18);
    cyanLight.target = faceFocus;
    scene.add(cyanLight);
    violetLight = new THREE.DirectionalLight(0xac94f4, .17);
    violetLight.position.set(-.3, .36, -.15);
    violetLight.target = faceFocus;
    scene.add(violetLight);
    fitCamera();

    const response = await fetch(GLB_URL, { cache: 'force-cache' });
    if (!response.ok) throw new Error(`Original avatar HTTP ${response.status}`);
    const buffer = await response.arrayBuffer();
    if (buffer.byteLength !== SOURCE_BYTES) throw new Error('Original avatar byte length differs from the verified asset.');
    if (globalThis.crypto?.subtle) {
      const digest = await crypto.subtle.digest('SHA-256', buffer);
      const sha = Array.from(new Uint8Array(digest), n => n.toString(16).padStart(2, '0')).join('');
      if (sha !== SOURCE_SHA256) throw new Error('Original avatar integrity check failed.');
      sourceVerified = true;
      sourceVerification = 'sha256';
    } else {
      sourceVerification = 'byte-length-and-model-contract; sha256-verified-at-build';
    }
    const gltf = await new GLTFLoader().parseAsync(buffer, new URL('assets/', SCRIPT_BASE).href);
    root = gltf.scene;
    joints = {};
    for (const name of ['AvatarRoot', 'Neck', 'Head', 'EyeLeft', 'EyeRight', 'Jaw']) {
      const object = root.getObjectByName(name);
      if (!object) throw new Error(`Original rig node missing: ${name}`);
      joints[name] = { object, rest: object.quaternion.clone() };
    }
    root.traverse(object => {
      if (object.isMesh && object.morphTargetDictionary) {
        if (!AVATAR_TARGETS.every(name => Number.isInteger(object.morphTargetDictionary[name]))) {
          throw new Error(`Original morph contract differs on ${object.name}`);
        }
        morphMeshes.push(object);
        for (const name of AVATAR_TARGETS) morphBindings.push({ name,
          index: object.morphTargetDictionary[name], influences: object.morphTargetInfluences });
      }
    });
    if (morphMeshes.length !== 3) throw new Error(`Expected three original morph primitives; received ${morphMeshes.length}`);
    sceneGroup = new THREE.Group();
    sceneGroup.name = 'ContinuumPresentationOnly';
    sceneGroup.add(root);
    scene.add(sceneGroup);
    // Compile the unchanged material programs while the original PNG remains
    // visible; supported parallel shader compilation avoids a reveal-time hitch.
    await renderer.compileAsync(scene, camera);
    loaded = true;
    // Original materials and embedded textures are deliberately left untouched.
    listen(canvas, 'webglcontextlost', event => {
      event.preventDefault();
      running = false;
      failed = true;
      cancelAnimationFrame(frame);
      showFallback('WebGL context lost; original PNG remains available.');
    });
    observer = new ResizeObserver(queueCameraFit);
    observer.observe(crop);
    running = true;
    frame = requestAnimationFrame(render);
  } catch (error) {
    failed = true;
    running = false;
    renderer?.dispose();
    showFallback(error?.message || error);
  }
}

if (document.readyState === 'loading') document.addEventListener('DOMContentLoaded', boot, { once: true });
else boot();
