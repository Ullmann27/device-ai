/** Read-only retrieval of non-authoritative, cited, public research observations. */
const MAX_RETURNED = 8;
function normalize(value) {
  return String(value ?? "").normalize("NFKC").toLocaleLowerCase("de-DE")
    .replace(/[^\p{L}\p{N}]+/gu, " ").trim();
}
function words(value) {
  return [...new Set(normalize(value).split(/\s+/).filter(token => token.length >= 3))].slice(0, 24);
}
export function selectResearchKnowledge(state, { query = "", limit = 6 } = {}) {
  if (!Number.isInteger(limit) || limit < 1 || limit > MAX_RETURNED) {
    throw new RangeError("Knowledge retrieval limit must be 1..8");
  }
  if (typeof query !== "string" || query.length > 200) {
    throw new TypeError("Knowledge query must be at most 200 characters");
  }
  const terms = words(query);
  const observations = Array.isArray(state?.observations) ? state.observations : [];
  return observations.filter(item =>
    item?.trust === "WEB_OBSERVATION" && item.authority === "NONE" &&
    typeof item.summary === "string" && Array.isArray(item.citations)
  ).map(item => {
    const visibleCitations = item.citations.filter(c =>
      typeof c?.title === "string" && typeof c?.url === "string" &&
      c.url.startsWith("https://")
    ).slice(0, 8);
    const text = normalize([item.query, item.summary,
      ...visibleCitations.map(c => c.title)].join(" "));
    const score = terms.reduce((count, token) => count + Number(text.includes(token)), 0);
    return { item, visibleCitations, score };
  }).filter(x => x.visibleCitations.length > 0 && (!terms.length || x.score > 0))
    .sort((a, b) => b.score - a.score ||
      (Number(b.item.createdAt) || 0) - (Number(a.item.createdAt) || 0))
    .slice(0, limit)
    .map(({ item, visibleCitations }) => ({
      id: String(item.id).slice(0, 70),
      summary: item.summary.slice(0, 2_500),
      citations: visibleCitations.map(c => ({
        title: c.title.slice(0, 180), url: c.url.slice(0, 1_000)
      })),
      createdAt: item.createdAt,
      trust: "WEB_OBSERVATION",
      authority: "NONE",
      uncertainty: Number.isFinite(item.uncertainty) ? item.uncertainty : 1
    }));
}
