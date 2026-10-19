import crypto from "node:crypto";

const HEADER = "device-ai-mentor-pair-v1";
const MAX_AGE_MS = 120_000;
const TOKEN_LABEL = "device-ai-mentor-only-v1:";

const equal = (a, b) => {
  const x = Buffer.from(a ?? "", "utf8");
  const y = Buffer.from(b ?? "", "utf8");
  return x.length === y.length && crypto.timingSafeEqual(x, y);
};

export class MentorDevicePairing {
  constructor({ rootToken, approvedFingerprints = "", clock = Date.now, randomBytes = crypto.randomBytes } = {}) {
    this.rootToken = typeof rootToken === "string" ? rootToken : "";
    this.clock = clock;
    this.randomBytes = randomBytes;
    this.approved = new Set(approvedFingerprints.split(",").map(v => v.trim().toLowerCase())
      .filter(v => /^[a-f0-9]{64}$/.test(v)).slice(0, 8));
  }

  get ready() { return this.rootToken.length >= 16; }

  publicKey(publicKeyDer) {
    if (typeof publicKeyDer !== "string" || publicKeyDer.length < 80 || publicKeyDer.length > 320 ||
        !/^[A-Za-z0-9+/]+={0,2}$/.test(publicKeyDer)) throw Error("INVALID_KEY");
    const der = Buffer.from(publicKeyDer, "base64");
    if (der.length < 70 || der.length > 200 || der.toString("base64") !== publicKeyDer) throw Error("INVALID_KEY");
    const key = crypto.createPublicKey({ key: der, format: "der", type: "spki" });
    if (key.asymmetricKeyType !== "ec" || key.asymmetricKeyDetails?.namedCurve !== "prime256v1" ||
        !Buffer.from(key.export({ format: "der", type: "spki" })).equals(der)) throw Error("INVALID_KEY");
    return { fingerprint: crypto.createHash("sha256").update(der).digest("hex"), key };
  }

  challenge() {
    if (!this.ready) throw Error("PAIRING_DISABLED");
    const issuedAt = this.clock();
    const nonce = this.randomBytes(24).toString("base64url");
    const value = HEADER + "\n" + issuedAt + "\n" + nonce;
    const mac = crypto.createHmac("sha256", this.rootToken).update(value).digest("base64url");
    return { issuedAt, nonce, mac };
  }

  validateChallenge(c) {
    if (!this.ready || !c || !Number.isSafeInteger(c.issuedAt) ||
        !/^[A-Za-z0-9_-]{32}$/.test(c.nonce ?? "") ||
        !/^[A-Za-z0-9_-]{43}$/.test(c.mac ?? "")) throw Error("INVALID_CHALLENGE");
    const delta = this.clock() - c.issuedAt;
    if (delta < 0 || delta > MAX_AGE_MS) throw Error("EXPIRED_CHALLENGE");
    const payload = HEADER + "\n" + c.issuedAt + "\n" + c.nonce;
    const expected = crypto.createHmac("sha256", this.rootToken).update(payload).digest("base64url");
    if (!equal(expected, c.mac)) throw Error("INVALID_CHALLENGE");
    return payload;
  }

  redeem({ publicKey, signature, challenge } = {}) {
    const payload = this.validateChallenge(challenge);
    const { fingerprint, key } = this.publicKey(publicKey);
    if (typeof signature !== "string" || signature.length > 256 ||
        !/^[A-Za-z0-9+/]+={0,2}$/.test(signature)) throw Error("INVALID_SIGNATURE");
    const signatureBytes = Buffer.from(signature, "base64");
    if (signatureBytes.length < 64 || signatureBytes.length > 90 ||
        signatureBytes.toString("base64") !== signature ||
        !crypto.verify("sha256", Buffer.from(payload + "\n" + publicKey, "utf8"), key, signatureBytes)) {
      throw Error("INVALID_SIGNATURE");
    }
    if (!this.approved.has(fingerprint)) return { ok: false, code: "PAIRING_PENDING", fingerprint };
    return { ok: true, token: this.tokenFor(fingerprint), scope: "MENTOR_ONLY", fingerprint };
  }

  tokenFor(fingerprint) {
    if (!this.ready) throw Error("PAIRING_DISABLED");
    return crypto.createHmac("sha256", this.rootToken)
      .update(TOKEN_LABEL + fingerprint).digest("base64url");
  }

  isMentorAuthorized(header) {
    const token = header?.startsWith("Bearer ") ? header.slice(7) : "";
    if (!this.ready || !token) return false;
    let match = false;
    for (const fp of this.approved) match = equal(this.tokenFor(fp), token) || match;
    return match;
  }
}
