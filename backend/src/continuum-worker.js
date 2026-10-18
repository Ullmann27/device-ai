import path from "node:path";
import { ContinuumResearchDaemon, createOpenAiResearchFunction } from "./continuum-daemon.js";
import { createPublicResearchFunction } from "./public-research.js";

const enabled = (process.env.CONTINUUM_DAEMON_ENABLED ?? "true").toLowerCase() !== "false";
if (!enabled) {
  console.log("Device AI Continuum daemon disabled by configuration.");
  process.exit(0);
}

const statePath = process.env.CONTINUUM_STATE_PATH ??
  path.join(process.cwd(), "data", "continuum-research-state.json");
const intervalMs = Number(process.env.CONTINUUM_RESEARCH_INTERVAL_MS ?? 20 * 60 * 1000);
const dailyLimit = Number(process.env.CONTINUUM_DAILY_CYCLE_LIMIT ?? 48);
const provider = (process.env.CONTINUUM_RESEARCH_PROVIDER ?? "public").toLowerCase();
if (!["public", "openai"].includes(provider)) throw new Error("Unknown Continuum research provider");

const daemon = new ContinuumResearchDaemon({
  statePath,
  minIntervalMs: intervalMs,
  dailyCycleLimit: dailyLimit,
  research: provider === "public" ? createPublicResearchFunction() : createOpenAiResearchFunction({
    apiKey: process.env.OPENAI_API_KEY ?? "",
    model: process.env.CONTINUUM_RESEARCH_MODEL ?? process.env.OPENAI_MODEL ?? "gpt-6-luna",
    baseUrl: process.env.OPENAI_BASE_URL ?? "https://api.openai.com/v1"
  })
});

function shutdown(signal) {
  console.log("Continuum daemon stopping on " + signal);
  daemon.stop();
  process.exit(0);
}

process.on("SIGTERM", () => shutdown("SIGTERM"));
process.on("SIGINT", () => shutdown("SIGINT"));

console.log("Device AI Continuum research daemon starting with provider:", provider);
console.log(JSON.stringify(daemon.status()));
daemon.start();
