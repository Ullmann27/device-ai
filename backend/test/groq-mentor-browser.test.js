import test from "node:test";
import assert from "node:assert/strict";
import { readFileSync } from "node:fs";

const html = readFileSync(new URL("../public/mentor.html", import.meta.url), "utf8");
const browser = readFileSync(new URL("../public/mentor.js", import.meta.url), "utf8");
const server = readFileSync(new URL("../src/server.js", import.meta.url), "utf8");

test("Groq browser UI never embeds API credentials, persists device token, or loads third-party scripts", () => {
  assert.match(html, /type="password"/);
  assert.match(html, /autocomplete="off"/);
  assert.match(html, /src="\/mentor\/ui\.js"/);
  assert.doesNotMatch(html, /https?:\/\//);
  assert.doesNotMatch(browser, /localStorage|sessionStorage|indexedDB|document\.cookie/);
  assert.doesNotMatch(browser, /innerHTML|window\.location|new Function|eval\(/);
  assert.match(browser, /credentials: "omit"/);
  assert.match(browser, /redirect: "error"/);
  assert.match(browser, /textContent = message/);
});

test("every real model request requires a new owner checkbox consent", () => {
  assert.match(html, /id="consent" type="checkbox"/);
  assert.match(browser, /if \(!consent\.checked\)/);
  assert.match(browser, /consent\.checked = false;/);
  assert.match(browser, /provider: "GROQ", consent: true, question: text/);
  assert.match(browser, /"Authorization": "Bearer " \+ secret/);
});

test("render only serves local UI assets; mentor POST keeps auth and owner consent", () => {
  assert.match(server, /\["\/mentor", "mentor\.html"\]/);
  assert.match(server, /\["\/mentor\/ui\.js", "mentor\.js"\]/);
  assert.match(server, /res\.set\("Cache-Control", "no-store"\)/);
  assert.match(server, /app\.post\("\/v1\/mentor\/answer", mentorAuth,/);
  assert.match(server, /req\.body\?\.consent !== true/);
});
