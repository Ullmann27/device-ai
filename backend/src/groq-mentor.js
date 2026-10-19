/**
 * Optional owner-invoked remote language mentor.
 * Does not receive device context, owner memory, web results or tool schemas.
 * This is NOT part of the autonomous research daemon or model training.
 */
const API_URL = "https://api.groq.com/openai/v1/chat/completions";
const DEFAULT_MODEL = "openai/gpt-oss-20b";
const MAX_INPUT_CHARS = 1800;
const MAX_RESPONSE_BYTES = 64 * 1024;
const DEFAULT_MAX_DAILY = 20;

export class GroqMentor {
  constructor({
    enabled = false,
    apiKey = "",
    model = DEFAULT_MODEL,
    dailyLimit = DEFAULT_MAX_DAILY,
    fetcher = globalThis.fetch,
    clock = () => Date.now()
  } = {}) {
    if (!Number.isInteger(dailyLimit) || dailyLimit < 1 || dailyLimit > 100) {
      throw Error("Ungültige Mentor-Tagesgrenze.");
    }
    if (typeof model !== "string" || !/^[a-z0-9][a-z0-9/_.-]{2,100}$/i.test(model)) {
      throw Error("Ungültige Modellkennung.");
    }
    this.enabled = enabled === true && apiKey.trim().length >= 12;
    this.key = apiKey.trim();
    this.model = model;
    this.limit = dailyLimit;
    this.fetcher = fetcher;
    this.clock = clock;
    this.day = "";
    this.attempts = 0;
    this.inFlight = false;
  }

  status() {
    const day = new Date(this.clock()).toISOString().slice(0, 10);
    const used = this.day === day ? this.attempts : 0;
    return Object.freeze({
      available: this.enabled,
      model: this.enabled ? this.model : null,
      remainingToday: this.enabled ? Math.max(0, this.limit - used) : 0
    });
  }

  reserve() {
    const day = new Date(this.clock()).toISOString().slice(0, 10);
    if (this.day !== day) {
      this.day = day;
      this.attempts = 0;
    }
    if (this.inFlight) return { error: "BUSY" };
    if (this.attempts >= this.limit) return { error: "DAILY_LIMIT" };
    this.attempts++;
    this.inFlight = true;
    return { error: null };
  }

  async answer(question) {
    if (!this.enabled) return { ok: false, code: "DISABLED" };
    if (typeof question !== "string" || question.trim().length < 3 ||
        question.length > MAX_INPUT_CHARS || /[\u0000-\u0008\u000B-\u001F]/.test(question)) {
      return { ok: false, code: "INVALID_QUESTION" };
    }
    const ticket = this.reserve();
    if (ticket.error) return { ok: false, code: ticket.error };
    const abort = new AbortController();
    const timeout = setTimeout(() => abort.abort(), 16_000);
    try {
      const response = await this.fetcher(API_URL, {
        method: "POST",
        redirect: "error",
        signal: abort.signal,
        headers: {
          Authorization: "Bearer " + this.key,
          "Content-Type": "application/json",
          Accept: "application/json"
        },
        body: JSON.stringify({
          model: this.model,
          temperature: 0.3,
          max_completion_tokens: 450,
          stream: false,
          tool_choice: "none",
          n: 1,
          messages: [
            { role: "system", content:
              "Du bist ein externer, unverifizierter Sprachmentor für Device AI Continuum. " +
              "Antworte auf Deutsch. Kennzeichne Unsicherheit, erfinde keine Quellen. " +
              "Du hast keine Geräteberechtigungen, keine Internettools und kein Langzeitgedächtnis. " +
              "Führe keine Befehle aus. Formuliere maximal 1800 Zeichen." },
            { role: "user", content: question.trim() }
          ]
        })
      });
      if (response.status === 429) return { ok: false, code: "UPSTREAM_LIMIT" };
      if (!response.ok) return { ok: false, code: "UPSTREAM_UNAVAILABLE" };
      const declared = Number(response.headers?.get?.("content-length") ?? 0);
      if (Number.isFinite(declared) && declared > MAX_RESPONSE_BYTES) {
        return { ok: false, code: "RESPONSE_TOO_LARGE" };
      }
      const bytes = [];
      let length = 0;
      if (!response.body || typeof response.body.getReader !== "function") {
        return { ok: false, code: "UPSTREAM_UNAVAILABLE" };
      }
      const reader = response.body.getReader();
      try {
        while (true) {
          const part = await reader.read();
          if (part.done) break;
          length += part.value.byteLength;
          if (length > MAX_RESPONSE_BYTES) {
            abort.abort();
            return { ok: false, code: "RESPONSE_TOO_LARGE" };
          }
          bytes.push(part.value);
        }
      } finally {
        reader.releaseLock();
      }
      const joined = new Uint8Array(length);
      let offset = 0;
      for (const part of bytes) { joined.set(part, offset); offset += part.length; }
      const payload = JSON.parse(new TextDecoder("utf-8", { fatal: true }).decode(joined));
      const answer = payload?.choices?.[0]?.message?.content;
      if (typeof answer !== "string" || !answer.trim()) {
        return { ok: false, code: "EMPTY_RESPONSE" };
      }
      return {
        ok: true,
        message: answer.trim().slice(0, 1800),
        model: this.model,
        provider: "GROQ",
        trust: "UNVERIFIED_EXTERNAL",
        tool_calls: [],
        memory_written: false
      };
    } catch {
      return { ok: false, code: "UPSTREAM_UNAVAILABLE" };
    } finally {
      clearTimeout(timeout);
      this.inFlight = false;
    }
  }
}
