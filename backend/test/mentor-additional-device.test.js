import test from "node:test";
import assert from "node:assert/strict";
import { MentorDevicePairing } from "../src/mentor-pairing.js";

const rootToken = "owner-test-root-token-1234567890";
const old = Array.from({ length: 8 }, (_, i) => (i + 1).toString(16).padStart(64, "0"));
const extra = "A".repeat(64);
const authorized = (pairing, fingerprint) =>
  pairing.isMentorAuthorized("Bearer " + pairing.tokenFor(fingerprint));

test("new separately approved device preserves all eight legacy fingerprints", () => {
  const pairing = new MentorDevicePairing({
    rootToken,
    approvedFingerprints: old.join(","),
    additionalApprovedFingerprint: extra
  });
  assert.equal(pairing.approved.size, 9);
  for (const fp of old) assert.equal(authorized(pairing, fp), true);
  assert.equal(authorized(pairing, extra.toLowerCase()), true);
  assert.equal(authorized(pairing, "f".repeat(64)), false);
});

test("malformed extra approval never grants access", () => {
  for (const invalid of ["", "short", "z".repeat(64), old[0] + "," + extra, null]) {
    const pairing = new MentorDevicePairing({
      rootToken, approvedFingerprints: old.join(","),
      additionalApprovedFingerprint: invalid
    });
    assert.equal(pairing.approved.size, 8);
    assert.equal(authorized(pairing, "a".repeat(64)), false);
  }
});

test("revoking extra authorization does not revoke historical devices", () => {
  const paired = new MentorDevicePairing({
    rootToken, approvedFingerprints: old.join(","),
    additionalApprovedFingerprint: extra
  });
  assert.equal(authorized(paired, extra.toLowerCase()), true);
  const revoked = new MentorDevicePairing({
    rootToken, approvedFingerprints: old.join(","),
    additionalApprovedFingerprint: ""
  });
  assert.equal(authorized(revoked, extra.toLowerCase()), false);
  assert.equal(authorized(revoked, old[0]), true);
});
