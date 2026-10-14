import crypto from "node:crypto";
import path from "node:path";
import express from "express";
import helmet from "helmet";
import { rateLimit } from "express-rate-limit";
import { ContinuumResearchDaemon, createOpenAiResearchFunction } from "./continuum-daemon.js";
import { BrainFactory, createOpenAiBrainFactoryAdapters } from "./brain-factory.js";

const app = express();
app.disable("x-powered-by");
app.use(helmet());
app.use(express.json({ limit: "256kb" }));
app.use(rateLimit({
  windowMs: 10 * 60 * 1000,
  limit: 90,
  standardHeaders: "draft-8",
  legacyHeaders: false
}));

const OPENAI_API_KEY = process.env.OPENAI_API_KEY ?? "";
const DEVICE_AI_TOKEN = process.env.DEVICE_AI_TOKEN ?? "";
const OPENAI_MODEL = process.env.OPENAI_MODEL ?? "gpt-6-luna";
const OPENAI_BASE_URL = (process.env.OPENAI_BASE_URL ?? "https://api.openai.com/v1").replace(/\/+$/, "");
const WEB_SEARCH_ENABLED = (process.env.WEB_SEARCH_ENABLED ?? "true").toLowerCase() !== "false";
const PORT = Number(process.env.PORT ?? 8080);
const CONTINUUM_DAEMON_ENABLED = (process.env.CONTINUUM_DAEMON_ENABLED ?? "false").toLowerCase() === "true";
const BRAIN_FACTORY_ENABLED = (process.env.CONTINUUM_BRAIN_FACTORY_ENABLED ?? "false").toLowerCase() === "true";
const CONTINUUM_STATE_PATH = process.env.CONTINUUM_STATE_PATH ??
  path.join(process.cwd(), "data", "continuum-research-state.json");
const BRAIN_STATE_PATH = process.env.CONTINUUM_BRAIN_STATE_PATH ??
  path.join(process.cwd(), "data", "continuum-brain-state.json");
const CONTINUUM_RESEARCH_INTERVAL_MS = Number(process.env.CONTINUUM_RESEARCH_INTERVAL_MS ?? 20 * 60 * 1000);
const CONTINUUM_DAILY_CYCLE_LIMIT = Number(process.env.CONTINUUM_DAILY_CYCLE_LIMIT ?? 48);
const BRAIN_FACTORY_EVERY_SUCCESSFUL_CYCLES = Math.max(1,
  Number(process.env.CONTINUUM_BRAIN_EVERY_SUCCESSFUL_CYCLES ?? 6));
const CONTINUUM_RESEARCH_MODEL = process.env.CONTINUUM_RESEARCH_MODEL ?? OPENAI_MODEL;
const CONTINUUM_BRAIN_MODEL = process.env.CONTINUUM_BRAIN_MODEL ?? OPENAI_MODEL;

let continuumDaemon = null;
let brainFactory = null;
let lastBrainEvolutionSuccessfulCycle = 0;

const sessions = new Map();
const SESSION_TTL_MS = 6 * 60 * 60 * 1000;

const SYSTEM_PROMPT = [
  "Du bist Device AI, ein persönlicher Android-Assistent auf dem eigenen Gerät des Nutzers.",
  "Antworte standardmäßig auf Deutsch und knapp, aber präzise.",
  "Nutze ausschließlich die angebotenen Geräte-Tools. Erfinde niemals Shell-Befehle.",
  "READ_ONLY-Tools dürfen geplant werden. Bestätigung und Ausführung sicherheitsrelevanter Tools übernimmt die Android-App.",
  "Wenn ein Tool fehlschlägt, berücksichtige die Fehlermeldung und wähle höchstens wenige sichere Alternativen.",
  "Nutze Websuche bei aktuellen Fakten, Softwareänderungen, Preisen, Nachrichten oder Rechtslage.",
  "Unterscheide klar zwischen lokal gemessenen Daten und Webrecherche.",
  "Für Lumo: zuerst Version/Paket prüfen, bei Fehlern Logcat lesen, dann erst Neustart oder APK-Installation vorschlagen."
].join("\n");

const functionTools = [
  tool("get_device_info", "Liest Modell, Android-Version, SDK und Build.", objectSchema({})),
  tool("get_battery_info", "Liest Batteriestatus und Ladezustand.", objectSchema({})),
  tool("get_storage_info", "Liest Speicherbelegung und freien Platz.", objectSchema({})),
  tool("list_installed_apps", "Listet installierte Apps.", objectSchema({
    userOnly: { type: "boolean", description: "Nur Benutzer-Apps anzeigen." }
  })),
  tool("get_app_info", "Liest Paket- und Versionsinformationen einer App.", objectSchema({
    packageOrName: stringProp("Paketname oder sichtbarer App-Name.")
  }, ["packageOrName"])),
  tool("list_processes", "Liest aktive Prozesse und CPU-Nutzung.", objectSchema({})),
  tool("read_logcat", "Liest Android-Logs, optional gefiltert auf eine App.", objectSchema({
    packageOrName: stringProp("Optionaler Paketname oder App-Name."),
    lines: { type: "integer", minimum: 40, maximum: 600 }
  })),
  tool("launch_app", "Startet eine installierte App.", objectSchema({
    packageOrName: stringProp("Paketname oder App-Name.")
  }, ["packageOrName"])),
  tool("stop_app", "Stoppt eine App. Erfordert Bestätigung in der Android-App.", objectSchema({
    packageOrName: stringProp("Paketname oder App-Name.")
  }, ["packageOrName"])),
  tool("restart_app", "Startet eine App neu. Erfordert Bestätigung.", objectSchema({
    packageOrName: stringProp("Paketname oder App-Name.")
  }, ["packageOrName"])),
  tool("install_apk", "Installiert eine APK aus freigegebenem Gerätespeicher. Erfordert Bestätigung.", objectSchema({
    path: stringProp("Absoluter APK-Pfad unter /sdcard/Download, Documents, DCIM, Pictures oder Movies.")
  }, ["path"])),
  tool("uninstall_app", "Deinstalliert eine App. Destruktiv und bestätigungspflichtig.", objectSchema({
    packageOrName: stringProp("Paketname oder App-Name.")
  }, ["packageOrName"])),
  tool("list_files", "Listet Dateien in einem freigegebenen Ordner.", objectSchema({
    path: stringProp("Absoluter Pfad im freigegebenen Speicher.")
  }, ["path"])),
  tool("find_files", "Sucht Dateien in freigegebenen Ordnern.", objectSchema({
    query: stringProp("Dateiname oder Suchbegriff."),
    roots: { type: "array", items: { type: "string" }, maxItems: 5 },
    limit: { type: "integer", minimum: 1, maximum: 250 }
  }, ["query"])),
  tool("find_latest_apk", "Findet aktuelle APKs im Download-Ordner.", objectSchema({
    filter: stringProp("Optionaler Filter, z. B. lumo.")
  })),
  tool("get_large_files", "Findet große Dateien.", objectSchema({
    roots: { type: "array", items: { type: "string" }, maxItems: 5 },
    limit: { type: "integer", minimum: 1, maximum: 100 }
  })),
  tool("move_file", "Verschiebt eine Datei im freigegebenen Speicher. Erfordert Bestätigung.", objectSchema({
    source: stringProp("Quellpfad."),
    destination: stringProp("Zielpfad.")
  }, ["source", "destination"])),
  tool("delete_file", "Löscht eine Datei. Destruktiv und bestätigungspflichtig.", objectSchema({
    path: stringProp("Dateipfad.")
  }, ["path"])),
  tool("take_screenshot", "Erstellt nur auf ausdrücklichen Nutzerauftrag einen Screenshot.", objectSchema({})),
  tool("record_screen", "Nimmt den Bildschirm kurz auf. Erfordert Bestätigung.", objectSchema({
    seconds: { type: "integer", minimum: 5, maximum: 60 }
  })),
  tool("open_android_settings", "Öffnet eine Android-Einstellungsseite. Erfordert Bestätigung.", objectSchema({
    page: stringProp("Gewünschte Seite, z. B. WLAN, Bluetooth, Apps oder Speicher.")
  }, ["page"])),
  tool("get_network_summary", "Liest eine kompakte Netzwerkübersicht.", objectSchema({}))
];

function tool(name, description, parameters) {
  return { type: "function", name, description, parameters, strict: true };
}

function stringProp(description) {
  return { type: "string", description };
}

function objectSchema(properties, required = []) {
  return {
    type: "object",
    properties,
    required,
    additionalProperties: false
  };
}

function toolsForRequest() {
  return WEB_SEARCH_ENABLED
    ? [{ type: "web_search" }, ...functionTools]
    : functionTools;
}

function auth(req, res, next) {
  if (!DEVICE_AI_TOKEN) {
    res.status(503).json({ error: "DEVICE_AI_TOKEN ist serverseitig nicht konfiguriert." });
    return;
  }
  const header = req.get("authorization") ?? "";
  const supplied = header.startsWith("Bearer ") ? header.slice(7) : "";
  const a = Buffer.from(supplied);
  const b = Buffer.from(DEVICE_AI_TOKEN);
  if (a.length !== b.length || !crypto.timingSafeEqual(a, b)) {
    res.status(401).json({ error: "Nicht autorisiert." });
    return;
  }
  next();
}

async function openai(path, options = {}) {
  if (!OPENAI_API_KEY) throw new Error("OPENAI_API_KEY fehlt.");
  const response = await fetch(OPENAI_BASE_URL + path, {
    method: options.method ?? "POST",
    headers: {
      "Authorization": "Bearer " + OPENAI_API_KEY,
      "Content-Type": "application/json"
    },
    body: options.body ? JSON.stringify(options.body) : undefined
  });

  const text = await response.text();
  let payload = {};
  try {
    payload = text ? JSON.parse(text) : {};
  } catch {
    throw new Error("OpenAI lieferte keine gültige JSON-Antwort.");
  }

  if (!response.ok) {
    const message = payload?.error?.message ?? ("OpenAI HTTP " + response.status);
    throw new Error(message);
  }
  return payload;
}

async function conversationFor(sessionId) {
  const current = sessions.get(sessionId);
  if (current && Date.now() - current.touchedAt < SESSION_TTL_MS) {
    current.touchedAt = Date.now();
    return current.id;
  }

  const created = await openai("/conversations", {
    body: { metadata: { device_ai_session: sessionId.slice(0, 64) } }
  });

  sessions.set(sessionId, { id: created.id, touchedAt: Date.now() });
  return created.id;
}

function normalizeToolOutputs(items) {
  if (!Array.isArray(items)) return [];
  return items
    .slice(0, 16)
    .map((item) => ({
      type: "function_call_output",
      call_id: String(item?.call_id ?? "").slice(0, 200),
      output: String(item?.output ?? "").slice(0, 60000)
    }))
    .filter((item) => item.call_id);
}

function extractText(response) {
  const parts = [];
  for (const item of response.output ?? []) {
    if (item.type !== "message") continue;
    for (const content of item.content ?? []) {
      if (content.type === "output_text" && content.text) {
        parts.push(content.text);
      }
    }
  }
  return parts.join("\n\n").trim();
}

function extractFunctionCalls(response) {
  const calls = [];
  for (const item of response.output ?? []) {
    if (item.type !== "function_call") continue;
    let args = {};
    try {
      args = JSON.parse(item.arguments || "{}");
    } catch {
      args = {};
    }
    calls.push({
      call_id: item.call_id,
      name: item.name,
      arguments: args
    });
  }
  return calls;
}

function extractCitations(response) {
  const found = new Map();
  for (const item of response.output ?? []) {
    if (item.type !== "message") continue;
    for (const content of item.content ?? []) {
      for (const annotation of content.annotations ?? []) {
        if (annotation.type !== "url_citation" || !annotation.url) continue;
        found.set(annotation.url, {
          title: annotation.title || annotation.url,
          url: annotation.url
        });
      }
    }
  }
  return [...found.values()];
}

app.get("/health", (_req, res) => {
  res.json({
    ok: true,
    service: "device-ai-backend",
    model: OPENAI_MODEL,
    web_search: WEB_SEARCH_ENABLED,
    continuum_daemon: Boolean(continuumDaemon),
    brain_factory: Boolean(brainFactory)
  });
});

app.get("/v1/continuum/status", auth, (_req, res) => {
  try {
    res.json({
      daemon: continuumDaemon ? continuumDaemon.status() : null,
      brain: brainFactory ? brainFactory.status() : null,
      autonomousResearchEnabled: Boolean(continuumDaemon),
      brainEvolutionEnabled: Boolean(brainFactory)
    });
  } catch (error) {
    res.status(500).json({ error: (error instanceof Error ? error.message : String(error)).slice(0, 500) });
  }
});

app.post("/v1/agent/turn", auth, async (req, res) => {
  try {
    const sessionId = String(req.body?.session_id ?? "").trim();
    if (!sessionId || sessionId.length > 128) {
      res.status(400).json({ error: "Ungültige session_id." });
      return;
    }

    const message = typeof req.body?.message === "string"
      ? req.body.message.trim().slice(0, 12000)
      : "";
    const outputs = normalizeToolOutputs(req.body?.tool_outputs);

    if (!message && outputs.length === 0) {
      res.status(400).json({ error: "message oder tool_outputs erforderlich." });
      return;
    }

    const conversation = await conversationFor(sessionId);
    const input = outputs.length > 0
      ? outputs
      : [{ role: "user", content: message }];

    const device = req.body?.device ?? {};
    const deviceContext = [
      "Gerätekontext:",
      "Hersteller=" + String(device.manufacturer ?? "unbekannt").slice(0, 80),
      "Modell=" + String(device.model ?? "unbekannt").slice(0, 80),
      "Android=" + String(device.android ?? "unbekannt").slice(0, 40),
      "SDK=" + String(device.sdk ?? "unbekannt").slice(0, 20),
      "DeviceAI=" + String(device.app_version ?? "unbekannt").slice(0, 40),
      "Shizuku=" + String(Boolean(device.shizuku_ready))
    ].join(" ");

    const response = await openai("/responses", {
      body: {
        model: OPENAI_MODEL,
        conversation,
        instructions: SYSTEM_PROMPT + "\n" + deviceContext,
        input,
        tools: toolsForRequest(),
        tool_choice: "auto",
        parallel_tool_calls: false,
        max_tool_calls: 8,
        store: true
      }
    });

    res.json({
      response_id: response.id ?? null,
      status: response.status ?? "completed",
      message: extractText(response) || null,
      tool_calls: extractFunctionCalls(response),
      citations: extractCitations(response)
    });
  } catch (error) {
    const message = error instanceof Error ? error.message : "Unbekannter Serverfehler.";
    res.status(502).json({ error: message.slice(0, 500) });
  }
});

setInterval(() => {
  const now = Date.now();
  for (const [key, value] of sessions.entries()) {
    if (now - value.touchedAt > SESSION_TTL_MS) {
      sessions.delete(key);
    }
  }
}, 30 * 60 * 1000).unref();

function startContinuumServices() {
  if (!CONTINUUM_DAEMON_ENABLED) return;

  if (BRAIN_FACTORY_ENABLED) {
    const adapters = createOpenAiBrainFactoryAdapters({
      apiKey: OPENAI_API_KEY,
      model: CONTINUUM_BRAIN_MODEL,
      baseUrl: OPENAI_BASE_URL,
      maxCases: Number(process.env.CONTINUUM_BRAIN_MAX_CASES ?? 4)
    });
    brainFactory = new BrainFactory({
      statePath: BRAIN_STATE_PATH,
      generateCandidates: adapters.generateCandidates,
      evaluateCandidate: adapters.evaluateCandidate,
      challengerCount: Number(process.env.CONTINUUM_BRAIN_CHALLENGERS ?? 3),
      datasetLimit: Number(process.env.CONTINUUM_BRAIN_DATASET_LIMIT ?? 10)
    });
  }

  continuumDaemon = new ContinuumResearchDaemon({
    statePath: CONTINUUM_STATE_PATH,
    minIntervalMs: CONTINUUM_RESEARCH_INTERVAL_MS,
    dailyCycleLimit: CONTINUUM_DAILY_CYCLE_LIMIT,
    research: createOpenAiResearchFunction({
      apiKey: OPENAI_API_KEY,
      model: CONTINUUM_RESEARCH_MODEL,
      baseUrl: OPENAI_BASE_URL,
      strategyProvider: () => brainFactory?.championStrategy()
    })
  });

  continuumDaemon.start(async (result, researchState) => {
    if (!brainFactory || !result?.success) return;
    const successfulCycles = Number(result.summary?.successfulCycles ?? 0);
    if (successfulCycles <= 0 ||
        successfulCycles % BRAIN_FACTORY_EVERY_SUCCESSFUL_CYCLES !== 0 ||
        successfulCycles === lastBrainEvolutionSuccessfulCycle) return;

    lastBrainEvolutionSuccessfulCycle = successfulCycles;
    try {
      const evolved = await brainFactory.evolve(researchState);
      console.log("Continuum Brain Factory:", JSON.stringify({
        promoted: evolved.promoted ?? false,
        championBefore: evolved.championBefore ?? null,
        championAfter: evolved.championAfter ?? null,
        skipped: evolved.skipped ?? false,
        reason: evolved.reason ?? null
      }));
    } catch (error) {
      console.error("Continuum Brain Factory cycle failed:",
        error instanceof Error ? error.message : error);
    }
  });

  console.log("Device AI Continuum research daemon enabled.");
  if (brainFactory) console.log("Device AI Brain Factory enabled.");
}

startContinuumServices();

const httpServer = app.listen(PORT, "0.0.0.0", () => {
  console.log("Device AI backend listening on port " + PORT);
});

function shutdown(signal) {
  console.log("Device AI backend stopping on " + signal);
  continuumDaemon?.stop();
  httpServer.close(() => process.exit(0));
  setTimeout(() => process.exit(1), 10_000).unref();
}

process.on("SIGTERM", () => shutdown("SIGTERM"));
process.on("SIGINT", () => shutdown("SIGINT"));
