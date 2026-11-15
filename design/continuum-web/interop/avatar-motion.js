/**
 * Presentation adapter for the preserved Device AI avatar. Constants and
 * mouth-opening compensation follow AvatarMorphMapper.kt at Code55 commit
 * 1b90611388cc2b1978ddfe32064a25dd3a08cee1. Cosmetic presence is not speech.
 */
export const AVATAR_TARGETS = Object.freeze([
  'SIL', 'PP', 'FF', 'TH', 'DD', 'KK', 'CH', 'SS', 'NN', 'RR', 'AA', 'E', 'I', 'O', 'U',
  'BlinkLeft', 'BlinkRight', 'BrowUp', 'BrowDown', 'Smile', 'JawOpen',
]);

const VISEMES = AVATAR_TARGETS.slice(0, 15);
const OPENING = Object.freeze({ SIL: 0, PP: 0, FF: .06 / .8, TH: .18 / .8,
  DD: .20 / .8, KK: .30 / .8, CH: .15 / .8, SS: 0, NN: .11 / .8,
  RR: .22 / .8, AA: .65 / .8, E: .23 / .8, I: .10 / .8,
  O: .36 / .8, U: .10 / .8 });
const clamp = (v, lo, hi) => Number.isFinite(v) ? Math.min(hi, Math.max(lo, v)) : 0;
const move = (v, to, step) => v < to ? Math.min(to, v + step) : Math.max(to, v - step);

class CriticalSpring {
  constructor() { this.value = 0; this.velocity = 0; }
  step(target, dt, omega) {
    if (dt <= 0) return this.value;
    const delta = this.value - target;
    const c = this.velocity + omega * delta;
    const decay = Math.exp(-omega * dt);
    this.value = target + (delta + c * dt) * decay;
    this.velocity = (this.velocity - omega * c * dt) * decay;
    return this.value;
  }
}

export class OriginalAvatarMotion {
  constructor() {
    this.state = 'idle';
    this.last = null;
    this.seed = 1640377;
    this.nextBlink = 0;
    this.blinkOnset = -100;
    this.blinkDuration = .19;
    this.nextGaze = 0;
    this.idleGaze = { x: 0, y: 0 };
    this.directedGaze = null;
    this.speech = null;
    this.headYaw = new CriticalSpring();
    this.headPitch = new CriticalSpring();
    this.eyeYaw = new CriticalSpring();
    this.eyePitch = new CriticalSpring();
    this.visemeBlend = Object.fromEntries(VISEMES.map(name => [name, 0]));
    this.brow = 0;
    this.work = 0;
    this.speechAccent = 0;
  }
  random() {
    this.seed = (Math.imul(this.seed, 1664525) + 1013904223) >>> 0;
    return this.seed / 4294967296;
  }
  setState(state) {
    if (['idle', 'listening', 'forming', 'working', 'speaking', 'result'].includes(state)) this.state = state;
  }
  setGaze(x, y, durationMs = 1800, nowMs = performance.now()) {
    this.directedGaze = { x: clamp(x, -1, 1), y: clamp(y, -1, 1),
      until: nowMs * .001 + clamp(durationMs, 0, 30000) * .001 };
  }
  /** Only call with an actual TTS/viseme event. The app's speaking state is insufficient. */
  setSpeechFrame(frame, nowMs = performance.now()) {
    if (!frame || !VISEMES.includes(frame.viseme)) return false;
    this.speech = { viseme: frame.viseme, weight: clamp(frame.weight ?? 1, 0, 1),
      jawOpen: clamp(frame.jawOpen ?? 0, 0, 1),
      until: nowMs * .001 + clamp(frame.durationMs ?? 160, 20, 500) * .001 };
    return true;
  }
  clearSpeech() {
    this.speech = null;
    for (const name of VISEMES) this.visemeBlend[name] = 0;
  }
  update(nowMs) {
    const seconds = nowMs * .001;
    const first = this.last === null;
    const dt = first ? 1 / 60 : clamp(seconds - this.last, 0, .1);
    this.last = seconds;
    if (first) {
      this.nextBlink = seconds + 2.2;
      this.nextGaze = seconds + 1.5;
    }
    if (seconds >= this.nextGaze) {
      const direct = this.random() < .7;
      this.idleGaze = { x: direct ? (this.random() - .5) * .055 : (this.random() - .5) * .22,
        y: direct ? (this.random() - .5) * .035 : (this.random() - .5) * .13 };
      this.nextGaze = seconds + 1.1 + this.random() * 4.9;
    }
    const task = this.state === 'working' || this.state === 'forming';
    const smooth = 1 - Math.exp(-dt / .4);
    this.work += ((task ? 1 : 0) - this.work) * smooth;
    let gx = this.idleGaze.x * (1 - this.work) + .43 * this.work;
    let gy = this.idleGaze.y * (1 - this.work) - .25 * this.work;
    if (this.state === 'listening') { gx *= .3; gy *= .3; }
    if (this.directedGaze && seconds <= this.directedGaze.until) {
      gx = this.directedGaze.x; gy = this.directedGaze.y;
    }
    const targetYaw = gx * 24;
    const targetPitch = -gy * 12;
    const headYaw = this.headYaw.step(targetYaw, dt, 13.61);
    const headPitch = this.headPitch.step(targetPitch, dt, 13.61);
    const eyeYaw = this.eyeYaw.step(clamp(gx * 12 + targetYaw - headYaw, -30, 30), dt, 54.44);
    const eyePitch = this.eyePitch.step(clamp(-gy * 8 + targetPitch - headPitch, -20, 20), dt, 54.44);

    const activeSpeech = this.speech && seconds <= this.speech.until;
    const cue = activeSpeech ? this.speech : { viseme: 'SIL', weight: 0, jawOpen: 0 };
    const close = cue.viseme === 'PP' && cue.weight > 0;
    if (cue.viseme === 'SIL' || cue.weight <= 0 || close) {
      for (const name of VISEMES) this.visemeBlend[name] = 0;
      if (close) this.visemeBlend.PP = cue.weight;
    }
    const weights = Object.fromEntries(AVATAR_TARGETS.map(name => [name, 0]));
    let renderedOpening = 0;
    for (const name of VISEMES) {
      const target = name === cue.viseme && name !== 'SIL' ? cue.weight : 0;
      this.visemeBlend[name] = move(this.visemeBlend[name], target, dt / .07);
      weights[name] = this.visemeBlend[name];
      renderedOpening += OPENING[name] * weights[name];
    }
    const jaw = close ? 0 : cue.jawOpen;
    weights.JawOpen = Math.max(0, jaw - renderedOpening);
    const opening = Math.max(jaw, renderedOpening);
    this.speechAccent += (opening - this.speechAccent) * (1 - Math.exp(-dt / .12));

    if (seconds >= this.nextBlink) {
      if (opening >= .35 && seconds - this.nextBlink < .45) {
        // Defer only briefly during an actual open spoken syllable.
      } else {
        this.blinkOnset = seconds;
        this.blinkDuration = .15 + this.random() * .095;
        this.nextBlink = seconds + 2.7 + this.random() * 4.8;
      }
    }
    const blinkPhase = (seconds - this.blinkOnset) / this.blinkDuration;
    const blink = blinkPhase >= 0 && blinkPhase <= 1 ? Math.pow(Math.sin(Math.PI * blinkPhase), 1.2) : 0;
    weights.BlinkLeft = blink;
    weights.BlinkRight = blink;
    const browTarget = this.state === 'listening' ? .18 : task ? .035 : .045;
    this.brow += (browTarget - this.brow) * smooth;
    weights.BrowUp = Math.max(this.brow, this.speechAccent * .14);
    weights.BrowDown = this.work * .06;

    // Independent, low-amplitude presence; the original rig carries the motion.
    const breath = Math.sin(seconds * Math.PI * 2 / 4.6);
    const breathEnvelope = .82 + .18 * Math.sin(seconds * Math.PI * 2 / 17.2);
    const extraPitch = .5 * breath * breathEnvelope + .16 * Math.sin(seconds * Math.PI * 2 / 8.3) + this.speechAccent * .8;
    const extraYaw = .36 * Math.sin(seconds * Math.PI * 2 / 7.7) + .11 * Math.sin(seconds * Math.PI * 2 / 13.1);
    const totalYaw = headYaw + extraYaw;
    const totalPitch = headPitch + extraPitch;
    return {
      weights,
      neckYaw: totalYaw * .65,
      neckPitch: totalPitch * .65,
      headYaw: totalYaw * .35,
      headPitch: totalPitch * .35,
      eyeYaw: clamp(eyeYaw - extraYaw * .8, -30, 30),
      eyePitch: clamp(eyePitch - extraPitch * .8, -20, 20),
      jawPitch: opening * 15,
      breathOffset: breath * breathEnvelope * .00065,
      breathRoll: .0017 * Math.sin(seconds * Math.PI * 2 / 9.1),
      activeSpeech: Boolean(activeSpeech),
    };
  }
}
