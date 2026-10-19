/**
 * Bounded, tool-free presentation of unverified external mentor text.
 * Formatting is display-only; it never executes Markdown, HTML or model output.
 */
export const MAX_MENTOR_ANSWER_CHARS = 4200;
const TRUNCATION_NOTICE = "\n\n[Antwort gekürzt. Bitte frage nach einem einzelnen Teilthema.]";
const INCOMPLETE_NOTICE = "\n\n[Die Modellausgabe endete am Tokenlimit. Für den fehlenden Teil bitte nachfragen.]";

function normalizeMarkdown(text) {
  const normalized = text.replace(/\r\n?/g, "\n");
  const lines = normalized.split("\n").map(line => {
    const trimmed = line.trim();
    // Android intentionally renders text, not Markdown. Preserve table values.
    if (trimmed.startsWith("|") && trimmed.endsWith("|") && trimmed.length > 2) {
      const cells = trimmed.slice(1, -1).split("|")
        .map(cell => cell.trim().replace(/\*\*([^*\n]+)\*\*/g, "$1"));
      if (cells.every(cell => /^:?-{3,}:?$/.test(cell))) return "";
      if (cells.length >= 2) return cells[0] + ": " + cells.slice(1).join(" — ");
    }
    return line.replace(/^\s{0,3}#{1,6}\s+/u, "")
      .replace(/\*\*([^*\n]+)\*\*/g, "$1")
      .trimEnd();
  });
  return lines.join("\n").replace(/\n{3,}/g, "\n\n").trim();
}

export function formatMentorAnswer(value, { incomplete = false } = {}) {
  if (typeof value !== "string" || !value.trim()) return "";
  const text = normalizeMarkdown(value);
  const notice = incomplete ? INCOMPLETE_NOTICE : "";
  if (text.length + notice.length <= MAX_MENTOR_ANSWER_CHARS) return text + notice;

  const marker = incomplete ? INCOMPLETE_NOTICE : TRUNCATION_NOTICE;
  const length = MAX_MENTOR_ANSWER_CHARS - marker.length;
  const candidate = text.slice(0, length);
  const min = Math.max(0, length - 450);
  let boundary = -1;
  for (let i = min; i < candidate.length - 1; i++) {
    if (/[.!?]/.test(candidate[i]) && /\s/.test(candidate[i + 1])) boundary = i + 1;
  }
  if (boundary < min) {
    boundary = candidate.lastIndexOf(" ");
    if (boundary < min) boundary = length;
  }
  return candidate.slice(0, boundary).trimEnd() + marker;
}
