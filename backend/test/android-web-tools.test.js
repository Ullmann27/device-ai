import test from "node:test";
import assert from "node:assert/strict";
import { readFileSync } from "node:fs";
import { androidWebTools } from "../src/android-web-tools.js";

test("web tools are exact bounded Android function proposals, never hosted browsing", () => {
  const tools = androidWebTools();
  assert.deepEqual(tools.map(t => t.name), ["web_search", "web_read"]);
  for (const t of tools) {
    assert.equal(t.type, "function");
    assert.equal(t.strict, true);
    assert.equal(t.parameters.additionalProperties, false);
    assert.deepEqual(Object.keys(t.parameters.properties), t.parameters.required);
    assert.equal(t.parameters.properties[t.parameters.required[0]].type, "string");
    assert.match(t.description, /Nur Android entscheidet/);
    assert.match(t.description, /OFF, ASK oder ALWAYS_ON/);
  }
  assert.equal(tools[0].parameters.properties.query.maxLength, 512);
  assert.equal(tools[1].parameters.properties.url.maxLength, 2048);
});

test("disabled discovery advertises no web capability; new calls do not share mutable schemas", () => {
  assert.deepEqual(androidWebTools(false), []);
  const a = androidWebTools();
  a[0].parameters.required.push("private_memory");
  assert.deepEqual(androidWebTools()[0].parameters.required, ["query"]);
});

test("actual server advertises Android proposals and removes hosted web-search bypass", () => {
  const server = readFileSync(new URL("../src/server.js", import.meta.url), "utf8");
  assert.match(server, /\.\.\.androidWebTools\(WEB_SEARCH_ENABLED\)/);
  assert.doesNotMatch(server, /type:\s*["']web_search["']/);
});
