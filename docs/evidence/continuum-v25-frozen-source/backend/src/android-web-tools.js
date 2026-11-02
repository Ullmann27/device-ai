/** Proposals only. The backend cannot browse: Android consumes an exact owner
 * grant and executes its bounded Research Engine. Never advertise hosted search.
 */
export function androidWebTools(enabled = true) {
  if (!enabled) return [];
  return [
    { name: "web_search", key: "query", maxLength: 512,
      description: "Schlägt eine öffentliche Webrecherche vor. Nur Android entscheidet anhand der Eigentümer-Einstellung OFF, ASK oder ALWAYS_ON über die Ausführung." },
    { name: "web_read", key: "url", maxLength: 2048,
      description: "Schlägt das Lesen einer öffentlichen HTTPS-Quelle vor. Nur Android entscheidet anhand der Eigentümer-Einstellung OFF, ASK oder ALWAYS_ON über die Ausführung." }
  ].map(({ name, key, maxLength, description }) => ({
    type: "function", name, description, strict: true,
    parameters: { type: "object", additionalProperties: false, required: [key],
      properties: { [key]: { type: "string", minLength: 1, maxLength } } }
  }));
}
