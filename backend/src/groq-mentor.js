/**
 * Optional owner-invoked remote language mentor.
 * Does not receive device context, owner memory, web results or tool schemas.
 * This is NOT part of the autonomous research daemon or model training.
 */
import { formatMentorAnswer } from "./mentor-answer-format.js";

const API_URL = "https://api.groq.com/openai/v1/chat/completions";
const OPENROUTER_URL = "https://openrouter.ai/api/v1/chat/completions";
const DEFAULT_MODEL = "openai/gpt-oss-20b";
const MAX_INPUT_CHARS = 1800;
const MAX_RESPONSE_BYTES = 64 * 1024;
const DEFAULT_MAX_DAILY = 20;

export class GroqMentor {
  constructor({
    enabled = false,
    apiKey = "",
    provider = "GROQ",
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
    if (provider !== "GROQ" && provider !== "OPENROUTER") {
      throw Error("Ungültiger Mentor-Anbieter.");
    }
    this.provider = provider;
    this.apiUrl = provider === "OPENROUTER" ? OPENROUTER_URL : API_URL;
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
      provider: this.provider,
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
      // One owner-approved mentor request may repeat the same bounded question
      // once when the provider returns no user-visible content. Reasoning-only
      // fields are deliberately never shown or written to memory.
      const gptOss = /^openai\/gpt-oss-(?:20b|120b)$/.test(this.model);
      for (let attempt = 0; attempt < 2; attempt++) {
        const response = await this.fetcher(this.apiUrl, {
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
            max_completion_tokens: attempt === 0 ? 2048 : 3072,
            ...(gptOss ? { reasoning_effort: "low", include_reasoning: false } : {}),
            stream: false,
            tool_choice: "none",
            n: 1,
            messages: [
              { role: "system", content:
                "Du bist ein externer, unverifizierter Sprachmentor für die Android-App Device AI Continuum. " +
                "Öffentliche Projektbeschreibung (statisch, keine Gerätedaten): " +
                "Device AI Continuum ist ein persönlicher Android-Assistent mit lokalem Startkern, " +
                "bestätigten Erinnerungen, Forschungsansicht, Gerätewerkzeugen mit Benutzerfreigaben " +
                "und einem Entwicklungsassistenten für überprüfbare Codevorschläge. " +
                "SWB1 ist ein kompakter lokaler Subwort- und Intent-Lernkern; " +
                "CRE1 ist ein experimenteller relationaler Encoder zum Vergleich und Reranking, " +
                "dessen Verifikation und Aktivierung vom Trainings- und Auditstatus abhängen. " +
                "Erfinde niemals ausgeschriebene Namen für die Abkürzungen SWB1 und CRE1. " +
                "Keiner dieser Kerne ist ein vollwertiges generatives Sprachmodell. " +
                "ein optionales LiteRT-LM-Sprachmodell muss separat installiert werden. " +
                "Der Teamleiter prüft den Backlog, entwickelt aber noch keine Apps eigenständig. " +
                "Der externe Mentor wird nur nach ausdrücklicher Einmalfreigabe für einzelne Fragen aufgerufen; " +
                "er darf keine Geräteaktionen, Git-Patches oder Lernübernahmen ausführen. " +
                "Für APK-Builds existiert eine getrennte Entwicklungsumgebung, kein Android-eigener Compiler. " +
                "Wenn nach 'dieser App' oder 'unserer App' gefragt wird, erkläre diese Funktionen verständlich. " +
                "Du siehst weder den aktuellen Gerätezustand noch das Repository; " +
                "unterscheide strikt nachgewiesene Architektur, Planungen und Unsicherheit. " +
                "Behaupte keine laufenden Tests, aktuellen Gerätestände oder unbekannte Funktionen. " +
                "Antworte auf Deutsch. Kennzeichne Unsicherheit, erfinde keine Quellen. " +
                "Du hast keine Geräteberechtigungen, keine Internettools und kein Langzeitgedächtnis. " +
                "Führe keine Befehle aus. Schreibe Klartext in kurzen Absätzen, " +
                "keine Markdown-Tabellen, Pipe-Zeichen oder Sterne zur Formatierung. " +
                "Bei mehrteiligen Fragen nummeriere die Antworten kurz und präzise. " +
                "Halte die Gesamtausgabe möglichst unter 3500 Zeichen; " +
                "schließe jeden Satz vollständig ab." },
              { role: "user", content: question.trim() }
            ]
          })
        });
        if (response.status === 429) return { ok: false, code: "UPSTREAM_LIMIT" };
        if (response.status === 401 || response.status === 403) {
          return { ok: false, code: this.provider === "OPENROUTER"
            ? "OPENROUTER_KEY_REJECTED" : "GROQ_KEY_REJECTED" };
        }
        if (response.status === 404) return { ok: false, code: this.provider === "OPENROUTER"
          ? "OPENROUTER_MODEL_UNAVAILABLE" : "GROQ_MODEL_UNAVAILABLE" };
        if (response.status === 400 || response.status === 422) {
          return { ok: false, code: this.provider === "OPENROUTER"
            ? "OPENROUTER_REQUEST_REJECTED" : "GROQ_REQUEST_REJECTED" };
        }
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
        for (const part of bytes) {
          joined.set(part, offset);
          offset += part.length;
        }
        const payload = JSON.parse(new TextDecoder("utf-8", { fatal: true }).decode(joined));
        const answer = formatMentorAnswer(payload?.choices?.[0]?.message?.content, {
          incomplete: payload?.choices?.[0]?.finish_reason === "length"
        });
        if (answer) {
          return {
            ok: true,
            message: answer,
            model: this.model,
            provider: this.provider,
            trust: "UNVERIFIED_EXTERNAL",
            tool_calls: [],
            memory_written: false
          };
        }
        if (attempt === 1) {
          return {
            ok: false,
            code: payload?.choices?.[0]?.finish_reason === "length"
              ? "RESPONSE_TOKEN_LIMIT" : "EMPTY_RESPONSE"
          };
        }
      }
      return { ok: false, code: "EMPTY_RESPONSE" };
    } catch {
      return { ok: false, code: "UPSTREAM_UNAVAILABLE" };
    } finally {
      clearTimeout(timeout);
      this.inFlight = false;
    }
  }
}
