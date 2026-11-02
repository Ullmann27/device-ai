"use strict";
// First-party page only. Never write the token, question or reply to browser storage.
const token = document.getElementById("device-token");
const question = document.getElementById("question");
const consent = document.getElementById("consent");
const status = document.getElementById("status");
const answer = document.getElementById("answer");
const check = document.getElementById("check");
const send = document.getElementById("send");

function show(output, message) {
  output.textContent = message;
}
async function guardedRequest(route, options = {}) {
  const secret = token.value.trim();
  if (secret.length < 16) throw Error("Bitte zuerst den Device-Token von Render eingeben.");
  const response = await fetch(route, {
    method: options.method || "GET",
    cache: "no-store",
    credentials: "omit",
    redirect: "error",
    headers: {
      "Authorization": "Bearer " + secret,
      "Accept": "application/json",
      ...(options.body ? { "Content-Type": "application/json" } : {})
    },
    ...(options.body ? { body: JSON.stringify(options.body) } : {})
  });
  const raw = await response.text();
  let result;
  try { result = JSON.parse(raw); } catch { throw Error("Ungültige Serverantwort."); }
  if (!response.ok) {
    const code = typeof result.error === "string" ? result.error : "HTTP " + response.status;
    throw Error("Anfrage nicht erfolgreich: " + code);
  }
  return result;
}
check.addEventListener("click", async () => {
  check.disabled = true;
  show(status, "Prüfe geschützte Backend-Konfiguration …");
  try {
    const data = await guardedRequest("/v1/mentor/status");
    show(status, data.available === true
      ? "✓ Server, Device-Token und Groq-Konfiguration erkannt. " +
        String(Number.isInteger(data.remainingToday) ? data.remainingToday : 0) +
        " Serverversuche heute übrig. Modellaufruf noch nicht geprüft."
      : "Backend erreichbar; Groq ist serverseitig noch nicht aktiviert.");
  } catch (error) {
    show(status, "Verbindung fehlgeschlagen: " + error.message);
  } finally {
    check.disabled = false;
  }
});
send.addEventListener("click", async () => {
  const text = question.value.trim();
  if (!consent.checked) {
    show(answer, "Bitte zuerst ausdrücklich die Einmalfreigabe bestätigen.");
    return;
  }
  if (text.length < 3 || text.length > 1800) {
    show(answer, "Bitte eine Frage mit 3 bis 1800 Zeichen eingeben.");
    return;
  }
  consent.checked = false;
  send.disabled = true;
  show(answer, "Einmalige Anfrage an den externen KI-Mentor läuft …");
  try {
    const data = await guardedRequest("/v1/mentor/answer", {
      method: "POST",
      body: { provider: "GROQ", consent: true, question: text }
    });
    if (data.provider !== "GROQ" || data.trust !== "UNVERIFIED_EXTERNAL" ||
        data.memory_written !== false || !Array.isArray(data.tool_calls) ||
        data.tool_calls.length !== 0 || typeof data.message !== "string") {
      throw Error("Antwort verletzt das isolierte Mentor-Protokoll.");
    }
    show(answer, "EXTERN · UNBESTÄTIGT\n\n" + data.message);
  } catch (error) {
    show(answer, "Mentor nicht verfügbar: " + error.message);
  } finally {
    send.disabled = false;
  }
});
