import path from "node:path";
import { ContinuumResearchDaemon, createOpenAiResearchFunction } from "./continuum-daemon.js";
import { createPublicResearchFunction } from "./public-research.js";
import { runPostResearchShadowCycle } from "../../.github/continuum/research-shadow-worker.mjs";

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
const shadowEnabled = (process.env.CONTINUUM_SHADOW_LEARNING_ENABLED ?? "false").toLowerCase() === "true";
const shadowDirectory = path.join(path.dirname(statePath), "cre1-research");
daemon.start(shadowEnabled ? async result => {
  if (result?.success !== true) return;
  try {
    const status = runPostResearchShadowCycle({
      researchResult: result,
      stateDir: shadowDirectory,
      budgetMs: 30_000
    });
    console.log("[CRE1 shadow]", JSON.stringify({
      status: status.status, sourceSha256: status.sourceSha256 ?? null,
      checkpointWritten: Boolean(status.checkpointWritten)
    }));
  } catch (error) {
    console.error("[CRE1 shadow] fail closed:", error instanceof Error ? error.message : String(error));
  }
} : null);
