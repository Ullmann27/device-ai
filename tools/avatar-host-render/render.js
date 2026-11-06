import * as THREE from 'three';
import { GLTFLoader } from 'three/addons/loaders/GLTFLoader.js';
const P = new URLSearchParams(location.search); const W = +P.get('w'), H = +P.get('h');
const canvas = document.getElementById('c');
const renderer = new THREE.WebGLRenderer({ canvas, antialias: true, alpha: true, preserveDrawingBuffer: true });
renderer.setPixelRatio(1); renderer.setSize(W, H, false);
renderer.toneMapping = THREE.ACESFilmicToneMapping; renderer.outputColorSpace = THREE.SRGBColorSpace;
renderer.setClearColor(0x000000, 0);
const scene = new THREE.Scene();
// FilamentAvatarRenderer lights, scaled by its camera exposure (f/11, 1/125 s, ISO 100 -> 5.51e-5).
const EXPOSURE = 1 / (1.2 * 121 * 125);
const key = new THREE.DirectionalLight(new THREE.Color(.50, .82, 1), 80000 * EXPOSURE);
key.position.set(.35, .40, 1); scene.add(key); scene.add(key.target);
const violet = new THREE.PointLight(new THREE.Color(.55, .24, 1), 28000 / (4 * Math.PI) * EXPOSURE, 1.5, 2); violet.position.set(-.38, .32, .38); scene.add(violet);
const cyan = new THREE.PointLight(new THREE.Color(.18, .82, 1), 24000 / (4 * Math.PI) * EXPOSURE, 1.2, 2); cyan.position.set(.32, .35, -.16); scene.add(cyan);
const camera = new THREE.OrthographicCamera(-1, 1, 1, -1, .05, 10);
let nodes = {}, morphMeshes = [], timeline = null;
window.setup = async (win) => {
  timeline = await (await fetch('./timeline.json')).json();
  const gltf = await new GLTFLoader().loadAsync('./continuum-avatar.glb');
  scene.add(gltf.scene);
  gltf.scene.traverse(o => {
    if (o.name && o.name.startsWith('Tooth')) o.visible = false;   // renderer removes the tooth rows
    if (o.morphTargetInfluences && o.morphTargetDictionary) morphMeshes.push(o);
    for (const n of ['Neck', 'Head', 'EyeLeft', 'EyeRight', 'Jaw']) if (o.name === n && !nodes[n]) nodes[n] = o;
  });
  for (const n of Object.values(nodes)) { n.userData.restQ = n.quaternion.clone(); }
  const f = timeline.focus || { centerX: 0, centerZ: .03 };
  camera.left = win.left; camera.right = win.right; camera.top = win.halfHeight; camera.bottom = -win.halfHeight;
  camera.position.set(f.centerX, win.cameraCenterY, f.centerZ + 2); camera.lookAt(f.centerX, win.cameraCenterY, f.centerZ);
  camera.updateProjectionMatrix();
  return { frames: timeline.frames.length, morphMeshes: morphMeshes.length, nodes: Object.keys(nodes) };
};
const rot = (node, pitchDeg, yawDeg) => {
  const q = new THREE.Quaternion().setFromEuler(new THREE.Euler(THREE.MathUtils.degToRad(pitchDeg), THREE.MathUtils.degToRad(yawDeg), 0, 'YXZ'));
  node.quaternion.copy(node.userData.restQ).multiply(q);
};
window.renderFrame = (i) => {
  const fr = timeline.frames[i];
  for (const m of morphMeshes) timeline.targets.forEach((name, k) => {
    const idx = m.morphTargetDictionary[name]; if (idx !== undefined) m.morphTargetInfluences[idx] = fr.w[k]; });
  const neck = fr.neck || [0, 0], total = fr.headTotal || [fr.headPitch || 0, fr.headYaw || 0], eye = fr.eye || [fr.eyePitch || 0, fr.eyeYaw || 0];
  rot(nodes.Neck, neck[0], neck[1]); rot(nodes.Head, total[0] - neck[0], total[1] - neck[1]);
  rot(nodes.EyeLeft, eye[0], eye[1]); rot(nodes.EyeRight, eye[0], eye[1]);
  rot(nodes.Jaw, fr.jawPitch ?? fr.jaw ?? 0, 0);
  renderer.render(scene, camera);
  return canvas.toDataURL('image/png');
};
