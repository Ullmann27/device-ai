import { createInworldMentor } from "../src/groq-mentor.js";

// Explicit operator check only. No automatic startup, CI or user-data probe.
// A successful real invocation can consume provider credits.
function failure(code, exitCode = 2) {
  console.log(JSON.stringify({ ok: false, provider: "INWORLD", code }));
  process.exitCode = exitCode;
}

async function main() {
  if (process.argv.slice(2).join(" ") !== "--confirm-live") {
    failure("LIVE_TEST_NOT_CONFIRMED");
    return;
  }
  if (!process.env.INWORLD_API_KEY?.trim()) {
    failure("INWORLD_API_KEY_MISSING");
    return;
  }
  let mentor;
  try {
    mentor = createInworldMentor();
  } catch {
    failure("INWORLD_CONFIGURATION_INVALID");
    return;
  }
  if (!mentor.status().available) {
    failure("INWORLD_MENTOR_DISABLED");
    return;
  }
  const result = await mentor.answer("Antworte ausschließlich mit dem Wort OK.");
  if (!result.ok) {
    failure(result.code, 1);
    return;
  }
  console.log(JSON.stringify({
    ok: true, provider: "INWORLD", model: result.model,
    textReceived: result.message.length > 0
  }));
}

await main().catch(() => failure("INWORLD_TEST_FAILED", 1));
