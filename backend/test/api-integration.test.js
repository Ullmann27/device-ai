import assert from "node:assert/strict";
import { spawn } from "node:child_process";
import { once } from "node:events";
import http from "node:http";
import net from "node:net";
import path from "node:path";
import test, { before, after } from "node:test";
import { fileURLToPath } from "node:url";

const root = fileURLToPath(new URL("..", import.meta.url));
const token = "continuum-local-integration-test-token";
const fakeKey = "continuum-local-mock-api-key";
const requests = [];
const bearer = { authorization: "Bearer " + token };
let upstream;
let backend;
let url;
let logs = "";

async function getUnusedPort() {
  const socket = net.createServer();
  socket.listen(0, "127.0.0.1");
  await once(socket, "listening");
  const port = socket.address().port;
  await new Promise((resolve, reject) => socket.close(error => error ? reject(error) : resolve()));
  return port;
}
function respond(res, status, data) {
  res.writeHead(status, { "content-type": "application/json" });
  res.end(JSON.stringify(data));
}
async function call(route, options = {}) {
  const headers = { accept: "application/json", ...options.headers };
  if (options.body !== undefined) headers["content-type"] = "application/json";
  const response = await fetch(url + route, {
    method: options.method || "GET",
    headers,
    body: options.body === undefined ? undefined : JSON.stringify(options.body),
    signal: AbortSignal.timeout(3000)
  });
  const raw = await response.text();
  let body;
  try { body = JSON.parse(raw); } catch { body = raw; }
  return { status: response.status, body };
}
function turn(body, headers = bearer) {
  return call("/v1/agent/turn", { method: "POST", headers, body });
}

before(async () => {
  upstream = http.createServer(async (req, res) => {
    try {
      let raw = "";
      for await (const chunk of req) raw += chunk;
      const body = JSON.parse(raw || "{}");
      requests.push({ path: req.url, authorization: req.headers.authorization, body });
      const invalidStrictTool = req.url === "/responses"
        ? body.tools?.find(tool => tool.strict === true && (
          tool.parameters?.additionalProperties !== false ||
          Object.keys(tool.parameters?.properties ?? {}).some(
            name => !tool.parameters.required?.includes(name)
          )
        ))
        : null;
      if (invalidStrictTool) {
        respond(res, 400, { error: { message: "Invalid strict tool: " + invalidStrictTool.name } });
      } else if (req.url === "/conversations") {
        respond(res, 200, { id: "mock-conversation" });
      } else if (req.url !== "/responses") {
        respond(res, 404, { error: { message: "Unknown mock route" } });
      } else if (body.input?.[0]?.content === "simulate-error") {
        respond(res, 429, { error: { message: "local-upstream-failure" } });
      } else if (body.input?.[0]?.content === "simulate-invalid-json") {
        res.writeHead(200, { "content-type": "application/json" });
        res.end("not-json");
      } else {
        respond(res, 200, {
          id: "mock-response",
          status: "completed",
          output: [
            { type: "message", content: [{
              type: "output_text",
              text: "Lokale Antwort",
              annotations: [{
                type: "url_citation",
                title: "Testquelle",
                url: "https://example.org/evidence"
              }]
            }] },
            { type: "function_call", call_id: "call-01", name: "get_device_info", arguments: "{}" }
          ]
        });
      }
    } catch (error) {
      respond(res, 500, { error: { message: String(error) } });
    }
  });
  upstream.listen(0, "127.0.0.1");
  await once(upstream, "listening");
  const port = await getUnusedPort();
  url = "http://127.0.0.1:" + port;
  backend = spawn(process.execPath, [path.join(root, "src", "server.js")], {
    cwd: root,
    stdio: ["ignore", "pipe", "pipe"],
    env: {
      ...process.env,
      PORT: String(port),
      DEVICE_AI_TOKEN: token,
      OPENAI_API_KEY: fakeKey,
      OPENAI_BASE_URL: "http://127.0.0.1:" + upstream.address().port,
      OPENAI_MODEL: "local-mock-model",
      WEB_SEARCH_ENABLED: "false",
      CONTINUUM_DAEMON_ENABLED: "false",
      CONTINUUM_BRAIN_FACTORY_ENABLED: "false",
      CONTINUUM_HYPOTHESIS_TOURNAMENT_ENABLED: "false"
    }
  });
  for (const stream of [backend.stdout, backend.stderr]) {
    stream.on("data", chunk => { logs = (logs + chunk.toString()).slice(-3000); });
  }
  const deadline = Date.now() + 8000;
  while (Date.now() < deadline) {
    if (backend.exitCode !== null) throw new Error("Backend exited: " + logs);
    try {
      if ((await call("/health")).status === 200) return;
    } catch { /* Await server readiness. */ }
    await new Promise(resolve => setTimeout(resolve, 50));
  }
  throw new Error("Backend readiness failed: " + logs);
});

after(async () => {
  if (backend && backend.exitCode === null) {
    const closed = once(backend, "close");
    backend.kill("SIGTERM");
    const timeout = setTimeout(() => {
      if (backend.exitCode === null) backend.kill("SIGKILL");
    }, 2500);
    timeout.unref();
    await closed;
    clearTimeout(timeout);
  }
  if (upstream) {
    upstream.closeAllConnections();
    await new Promise((resolve, reject) => upstream.close(err => err ? reject(err) : resolve()));
  }
});

test("health reports disabled hosted research without exposing credentials", async () => {
  const result = await call("/health");
  assert.equal(result.status, 200);
  assert.equal(result.body.ok, true);
  assert.equal(result.body.model, "local-mock-model");
  assert.equal(result.body.web_search, false);
  assert.equal(result.body.continuum_daemon, false);
  assert.doesNotMatch(JSON.stringify(result.body), /continuum-local-/);
});

test("continuum status enforces bearer authentication", async () => {
  assert.equal((await call("/v1/continuum/status")).status, 401);
  assert.equal((await call("/v1/continuum/status", {
    headers: { authorization: "Bearer invalid" }
  })).status, 401);
  const result = await call("/v1/continuum/status", { headers: bearer });
  assert.equal(result.status, 200);
  assert.equal(result.body.autonomousResearchEnabled, false);
  assert.equal(result.body.brainEvolutionEnabled, false);
  assert.equal(result.body.hypothesisTournamentEnabled, false);
  assert.equal(result.body.daemon, null);
});

test("unauthorized or invalid agent turns never call upstream", async () => {
  const count = requests.length;
  assert.equal((await turn({ session_id: "first", message: "Hallo" }, {})).status, 401);
  for (const body of [
    { session_id: "", message: "Hallo" },
    { session_id: "s".repeat(129), message: "Hallo" },
    { session_id: "first" }
  ]) {
    assert.equal((await turn(body)).status, 400);
  }
  assert.equal(requests.length, count);
});

test("agent turn maps mock Responses API to Android reply contract", async () => {
  const result = await turn({
    session_id: "integration-01",
    message: "Geräteinfo",
    device: { manufacturer: "Samsung", model: "Fold", android: "16", sdk: 36, shizuku_ready: false }
  });
  assert.equal(result.status, 200);
  assert.equal(result.body.response_id, "mock-response");
  assert.equal(result.body.status, "completed");
  assert.equal(result.body.message, "Lokale Antwort");
  assert.deepEqual(result.body.tool_calls, [
    { call_id: "call-01", name: "get_device_info", arguments: {} }
  ]);
  assert.deepEqual(result.body.citations, [
    { title: "Testquelle", url: "https://example.org/evidence" }
  ]);
  assert.equal(requests.filter(r => r.path === "/conversations").length, 1);
  const sent = requests.filter(r => r.path === "/responses").at(-1);
  assert.equal(sent.authorization, "Bearer " + fakeKey);
  assert.equal(sent.body.conversation, "mock-conversation");
  assert.equal(sent.body.input[0].content, "Geräteinfo");
  assert.equal(sent.body.parallel_tool_calls, false);
  assert.ok(sent.body.instructions.includes("Shizuku=false"));
  assert.ok(sent.body.tools.some(tool => tool.name === "get_device_info"));
  assert.ok(!sent.body.tools.some(tool => tool.name === "web_search" || tool.name === "web_read"));
  for (const tool of sent.body.tools) {
    assert.equal(tool.strict, true);
    assert.equal(tool.parameters.additionalProperties, false);
    assert.deepEqual(
      [...tool.parameters.required].sort(),
      Object.keys(tool.parameters.properties).sort(),
      "Strict tool schema is incomplete: " + tool.name
    );
  }
  const apps = sent.body.tools.find(tool => tool.name === "list_installed_apps");
  assert.deepEqual(apps.parameters.properties.userOnly.type, ["boolean", "null"]);
});

test("tool-result continuation reuses session and limits the forwarded result count", async () => {
  const previousConversations = requests.filter(r => r.path === "/conversations").length;
  const outputs = Array.from({ length: 19 }, (_, index) => ({
    call_id: "tool-" + index, output: "ok"
  }));
  const result = await turn({ session_id: "integration-01", tool_outputs: outputs });
  assert.equal(result.status, 200);
  const sent = requests.filter(r => r.path === "/responses").at(-1);
  assert.equal(sent.body.conversation, "mock-conversation");
  assert.equal(sent.body.input.length, 16);
  assert.equal(sent.body.input[0].type, "function_call_output");
  assert.equal(sent.body.input[0].call_id, "tool-0");
  assert.equal(requests.filter(r => r.path === "/conversations").length, previousConversations);
});

test("oversized payload is rejected before upstream forwarding", async () => {
  const count = requests.length;
  const result = await turn({
    session_id: "integration-01",
    tool_outputs: [{ call_id: "large", output: "x".repeat(300000) }]
  });
  assert.equal(result.status, 413);
  assert.equal(requests.length, count);
});

test("upstream HTTP and JSON failures are bounded and do not leak secrets", async () => {
  for (const [message, expected] of [
    ["simulate-error", "local-upstream-failure"],
    ["simulate-invalid-json", "OpenAI lieferte keine gültige JSON-Antwort."]
  ]) {
    const result = await turn({ session_id: "integration-01", message });
    assert.equal(result.status, 502);
    assert.equal(result.body.error, expected);
    assert.doesNotMatch(JSON.stringify(result.body), /continuum-local-/);
  }
});
