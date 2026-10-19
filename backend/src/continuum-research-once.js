import path from "node:path";
import { ContinuumResearchDaemon } from "./continuum-daemon.js";
import { createPublicResearchFunction } from "./public-research.js";

/** A bounded foreground verification cycle; no paid model and no device access. */
const statePath = process.env.CONTINUUM_STATE_PATH ??
  path.join(process.cwd(), "data", "continuum-research-state.json");
const daemon = new ContinuumResearchDaemon({
  statePath,
  research: createPublicResearchFunction(),
  minIntervalMs: Number(process.env.CONTINUUM_RESEARCH_INTERVAL_MS ?? 20 * 60 * 1000),
  dailyCycleLimit: Number(process.env.CONTINUUM_DAILY_CYCLE_LIMIT ?? 48)
});
try {
  const result = await daemon.runOnce();
  console.log(JSON.stringify({
    skipped: Boolean(result.skipped),
    success: result.success ?? null,
    reason: result.reason ?? result.error ?? null,
    sources: result.observation?.citations?.length ?? 0,
    summary: result.summary
  }, null, 2));
  if (result.success === false) process.exitCode = 1;
} catch (error) {
  console.error("Continuum public research check failed:", error instanceof Error ? error.message : String(error));
  process.exitCode = 1;
}
