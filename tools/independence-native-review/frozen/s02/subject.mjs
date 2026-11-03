/** Escape raw text for HTML without treating preexisting entities as trusted. */
export function escapeHtmlText(text) {
  return text.replace('<', '&lt;').replace('>', '&gt;')
    .replace('"', '&quot;').replace("'", '&#39;').replace('&', '&amp;');
}
