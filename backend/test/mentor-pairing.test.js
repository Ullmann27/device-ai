import test from "node:test";
import assert from "node:assert/strict";
import crypto from "node:crypto";
import { MentorDevicePairing } from "../src/mentor-pairing.js";

const ROOT_TOKEN = "owner-backend-only-secret-with-32-bytes";
const { privateKey, publicKey } = crypto.generateKeyPairSync("ec", { namedCurve: "prime256v1" });
const encoded = publicKey.export({ format: "der", type: "spki" }).toString("base64");
const at = 1_900_000_000_000;
const fixedClock = () => at;
const make = approvedFingerprints => new MentorDevicePairing({
  rootToken: ROOT_TOKEN, approvedFingerprints, clock: fixedClock,
  randomBytes: n => Buffer.alloc(n, 0x5a)
});
const sign = challenge => crypto.sign("sha256", Buffer.from(
  "device-ai-mentor-pair-v1\n" + challenge.issuedAt + "\n" + challenge.nonce + "\n" + encoded
), privateKey).toString("base64");

test("unapproved device cannot redeem; no credentials are exposed", () => {
  const p = make("");
  const challenge = p.challenge();
  const result = p.redeem({ publicKey: encoded, signature: sign(challenge), challenge });
  assert.equal(result.ok, false);
  assert.equal(result.code, "PAIRING_PENDING");
  assert.match(result.fingerprint, /^[a-f0-9]{64}$/);
  assert.equal(Object.hasOwn(result, "token"), false);
  assert.equal(p.isMentorAuthorized("Bearer " + ROOT_TOKEN), false);
});

test("owner-approved device receives only revocable mentor token", () => {
  const hash = make("").publicKey(encoded).fingerprint;
  const p = make(hash);
  const challenge = p.challenge();
  const result = p.redeem({ publicKey: encoded, signature: sign(challenge), challenge });
  assert.equal(result.ok, true);
  assert.equal(result.scope, "MENTOR_ONLY");
  assert.match(result.token, /^[A-Za-z0-9_-]{43}$/);
  assert.equal(p.isMentorAuthorized("Bearer " + result.token), true);
  assert.throws(() => p.redeem({ publicKey: encoded, signature: sign(challenge), challenge }), /CHALLENGE_REPLAY/);
  const restarted = new MentorDevicePairing({
    rootToken: ROOT_TOKEN, approvedFingerprints: hash, clock: fixedClock,
    randomBytes: n => Buffer.alloc(n, 0x7e)
  });
  assert.throws(() => restarted.redeem({ publicKey: encoded, signature: sign(challenge), challenge }));
  assert.equal(restarted.isMentorAuthorized("Bearer " + result.token), true);
  assert.equal(make("").isMentorAuthorized("Bearer " + result.token), false);
  assert.equal(p.isMentorAuthorized("Bearer invalid"), false);
  assert.equal(result.token === ROOT_TOKEN, false);
});

test("expired, forged, tampered, and replayable altered challenges are rejected", () => {
  const p = make(make("").publicKey(encoded).fingerprint);
  const challenge = p.challenge();
  const payload = { publicKey: encoded, signature: sign(challenge), challenge };
  assert.throws(() => p.redeem({ ...payload, challenge: { ...challenge, issuedAt: at - 120001 } }));
  assert.throws(() => p.redeem({ ...payload, challenge: { ...challenge, nonce: "a".repeat(32) } }));
  assert.throws(() => p.redeem({ ...payload, signature: Buffer.alloc(72).toString("base64") }));
  const other = crypto.generateKeyPairSync("ec", { namedCurve: "prime256v1" });
  const otherKey = other.publicKey.export({ format: "der", type: "spki" }).toString("base64");
  assert.throws(() => p.redeem({ ...payload, publicKey: otherKey }));
});

test("wrong elliptic curve and missing owner token fail closed", () => {
  const p = make("");
  const other = crypto.generateKeyPairSync("ec", { namedCurve: "secp384r1" });
  const der = other.publicKey.export({ format: "der", type: "spki" }).toString("base64");
  assert.throws(() => p.publicKey(der));
  assert.equal(new MentorDevicePairing({ rootToken: "", approvedFingerprints: "0".repeat(64) }).ready, false);
  assert.throws(() => new MentorDevicePairing({ rootToken: "" }).challenge());
});
