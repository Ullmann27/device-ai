/**
 * Zero-paid-API research adapter for the existing ContinuumResearchDaemon.
 * Stores source-labelled bibliographic leads, never model-generated factual claims.
 * Remote providers are fixed public HTTPS APIs; no arbitrary URLs are fetched.
 */
const MAX_RESPONSE_BYTES = 350_000;
const MAX_QUERY_LENGTH = 220;
const MAX_RESULTS_PER_PROVIDER = 3;
const USER_AGENT = "DeviceAI-Continuum/0.4 (+https://github.com/Ullmann27/device-ai)";

function plain(value, max = 240) {
  return String(value ?? "").replace(/<[^>]*>/g, " ")
    .replace(/[\u0000-\u001f\u007f]/g, " ").replace(/\s+/g, " ").trim().slice(0, max);
}
function citationUrl(value) {
  try {
    const url = new URL(String(value));
    if (url.protocol !== "https:" || url.username || url.password || url.port) return null;
    url.hash = "";
    return url.href.slice(0, 950);
  } catch { return null; }
}
function source(title, url, provider, excerpt = "") {
  const cleanUrl = citationUrl(url);
  const cleanTitle = plain(title, 180);
  if (!cleanUrl || cleanTitle.length < 6) return null;
  return { title: cleanTitle, url: cleanUrl, provider, excerpt: plain(excerpt, 500) };
}
/** Only bounded public metadata responses from fixed API hosts are accepted. */
async function getJson(url, fetchImpl, timeoutMs) {
  const allowed = new Set(["de.wikipedia.org", "api.openalex.org", "api.crossref.org"]);
  const parsed = new URL(url);
  if (parsed.protocol !== "https:" || !allowed.has(parsed.hostname) || parsed.port || parsed.username || parsed.password) {
    throw new Error("Unapproved public research endpoint");
  }
  const controller = new AbortController();
  const timer = setTimeout(() => controller.abort(), timeoutMs);
  timer.unref?.();
  try {
    const response = await fetchImpl(parsed.href, {
      method: "GET",
      redirect: "error",
      headers: { Accept: "application/json", "User-Agent": USER_AGENT },
      signal: controller.signal
    });
    if (!response.ok) throw new Error("Public source HTTP " + response.status);
    const declared = Number(response.headers?.get?.("content-length"));
    if (Number.isFinite(declared) && declared > MAX_RESPONSE_BYTES) throw new Error("Research metadata too large");
    const reader = response.body?.getReader?.();
    if (!reader) throw new Error("Public source has no readable body");
    const chunks = [];
    let total = 0;
    try {
      for (;;) {
        const { done, value } = await reader.read();
        if (done) break;
        total += value.byteLength;
        if (total > MAX_RESPONSE_BYTES) throw new Error("Research metadata too large");
        chunks.push(value);
      }
    } finally { reader.releaseLock(); }
    const bytes = new Uint8Array(total);
    let offset = 0;
    for (const item of chunks) { bytes.set(item, offset); offset += item.byteLength; }
    return JSON.parse(new TextDecoder("utf-8", { fatal: true }).decode(bytes));
  } finally { clearTimeout(timer); }
}
async function wikipedia(query, fetchImpl, timeoutMs) {
  const url = "https://de.wikipedia.org/w/api.php?action=query&list=search&format=json&srlimit=" +
    MAX_RESULTS_PER_PROVIDER + "&srsearch=" + encodeURIComponent(query);
  const result = await getJson(url, fetchImpl, timeoutMs);
  return (Array.isArray(result?.query?.search) ? result.query.search : [])
    .slice(0, MAX_RESULTS_PER_PROVIDER)
    .map(item => source(item.title,
      "https://de.wikipedia.org/wiki/" + encodeURIComponent(String(item.title ?? "").replaceAll(" ", "_")),
      "Wikipedia", item.snippet))
    .filter(Boolean);
}
async function openAlex(query, fetchImpl, timeoutMs) {
  const url = "https://api.openalex.org/works?per_page=" + MAX_RESULTS_PER_PROVIDER +
    "&search=" + encodeURIComponent(query);
  const result = await getJson(url, fetchImpl, timeoutMs);
  return (Array.isArray(result?.results) ? result.results : [])
    .slice(0, MAX_RESULTS_PER_PROVIDER)
    .map(item => source(item.title, item.doi || item.id,
      "OpenAlex", item.publication_year ? "Publikationsjahr: " + item.publication_year : ""))
    .filter(Boolean);
}
async function crossref(query, fetchImpl, timeoutMs) {
  const url = "https://api.crossref.org/works?rows=" + MAX_RESULTS_PER_PROVIDER +
    "&query.bibliographic=" + encodeURIComponent(query);
  const result = await getJson(url, fetchImpl, timeoutMs);
  return (Array.isArray(result?.message?.items) ? result.message.items : [])
    .slice(0, MAX_RESULTS_PER_PROVIDER)
    .map(item => source(Array.isArray(item.title) ? item.title[0] : item.title,
      item.URL || (item.DOI ? "https://doi.org/" + encodeURIComponent(item.DOI) : ""),
      "Crossref", "Bibliografischer Nachweis; Inhalt nicht überprüft"))
    .filter(Boolean);
}
/** Citation URLs identify leads, not verified claims or training examples. */
export function createPublicResearchFunction({ fetchImpl = fetch, timeoutMs = 8_000 } = {}) {
  if (typeof fetchImpl !== "function") throw new TypeError("fetchImpl must be a function");
  if (!Number.isInteger(timeoutMs) || timeoutMs < 1_000 || timeoutMs > 30_000) {
    throw new RangeError("Research timeout must be 1000..30000 ms");
  }
  return async ({ query, target }) => {
    const q = plain(query, MAX_QUERY_LENGTH);
    if (q.length < 5) throw new Error("Research query is too short");
    const settled = await Promise.allSettled([
      wikipedia(q, fetchImpl, timeoutMs),
      openAlex(q, fetchImpl, timeoutMs),
      crossref(q, fetchImpl, timeoutMs)
    ]);
    const records = settled.filter(r => r.status === "fulfilled").flatMap(r => r.value);
    const seen = new Set();
    const unique = records.filter(item => {
      if (seen.has(item.url)) return false;
      seen.add(item.url);
      return true;
    }).slice(0, 8);
    if (!unique.length) throw new Error("Keine verwendbaren öffentlichen Quellen gefunden");
    const providers = new Set(unique.map(item => item.provider));
    const sample = unique.slice(0, 4).map(item => item.provider + ": " + item.title).join("; ");
    const summary = "Automatisch recherchierte öffentliche Quelleneinträge zu " +
      plain(target?.text || q, 110) + ": " + sample +
      ". Dies sind bibliografische Hinweise, keine unabhängig bestätigten Aussagen über den Inhalt.";
    return {
      summary: summary.slice(0, 4_900),
      citations: unique.map(({ title, url }) => ({ title, url })),
      nextQuestions: [
        "Welche Aussagen aus den gefundenen Quellen lassen sich unabhängig überprüfen?",
        "Welche Gegenbelege und neueren Arbeiten existieren zu diesem Thema?"
      ],
      contradictions: "Quelleninhalte nicht unabhängig gegengeprüft; aus Titeln allein folgen keine Fakten.",
      uncertainty: providers.size >= 2 ? 0.75 : 0.9,
      topicHint: target?.type === "topic" ? plain(target.text, 150) : ""
    };
  };
}
