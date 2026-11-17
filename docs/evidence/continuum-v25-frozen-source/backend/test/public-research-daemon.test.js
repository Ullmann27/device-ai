import assert from "node:assert/strict";
import fs from "node:fs";
import os from "node:os";
import path from "node:path";
import test from "node:test";
import { ContinuumResearchDaemon } from "../src/continuum-daemon.js";
import { createPublicResearchFunction } from "../src/public-research.js";

test("zero-cost autonomous daemon persists public source observations across restarts", async () => {
  const dir = fs.mkdtempSync(path.join(os.tmpdir(), "continuum-free-research-"));
  try {
    const statePath = path.join(dir, "research-state.json");
    let calls = 0;
    const fetchImpl = async (url, options) => {
      calls++;
      assert.equal(options.redirect, "error");
      const host = new URL(url).hostname;
      const payload = host === "de.wikipedia.org"
        ? { query: { search: [{ title: "Artificial intelligence", snippet: "overview" }] } }
        : host === "api.openalex.org"
          ? { results: [{ title: "Independent AI Research Paper", doi: "https://doi.org/10.1000/source-a" }] }
          : { message: { items: [{ title: ["Independent AI Review Paper"], URL: "https://doi.org/10.1000/source-b" }] } };
      return new Response(JSON.stringify(payload), { status: 200 });
    };
    let now = 1700000000000;
    const research = createPublicResearchFunction({ fetchImpl });
    const daemon = new ContinuumResearchDaemon({
      statePath, research, now: () => now,
      minIntervalMs: 60_000, dailyCycleLimit: 2
    });
    const first = await daemon.runOnce();
    assert.equal(first.success, true);
    assert.equal(calls, 3);
    assert.equal(first.observation.trust, "WEB_OBSERVATION");
    assert.equal(first.observation.authority, "NONE");
    assert.equal(daemon.status().observations, 1);
    assert.ok(fs.existsSync(statePath));
    const restarted = new ContinuumResearchDaemon({
      statePath, research, now: () => now,
      minIntervalMs: 60_000, dailyCycleLimit: 2
    });
    assert.equal(restarted.status().observations, 1);
    const blockedByInterval = await restarted.runOnce();
    assert.equal(blockedByInterval.skipped, true);
    assert.equal(calls, 3);
    now += 60_001;
    const second = await restarted.runOnce();
    assert.equal(second.success, true);
    const blockedByBudget = await restarted.runOnce();
    assert.equal(blockedByBudget.skipped, true);
    assert.match(blockedByBudget.reason, /Tagesbudget|Mindestabstand/);
    assert.equal(restarted.status().cycles, 2);
    assert.ok(restarted.status().citationCount >= 3);
  } finally { fs.rmSync(dir, { recursive: true, force: true }); }
});
