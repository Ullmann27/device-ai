/* Android-only bridge; leaves every approved HTML/CSS/JS asset byte-identical. */
(() => {
  "use strict";
  if (window.__continuumNativeBound) return;
  window.__continuumNativeBound = true;
  const safe = new Set(["home","chat","chats","new-chat","files","research","agents",
                        "memory","device","settings","voice-input","stop"]);
  const transport = (type, value) => {
    if (type === "route" && !safe.has(value)) return;
    window.location.assign("continuum-native://action/" + type +
      "?value=" + encodeURIComponent(value));
  };
  const map = Object.freeze({
    home: "home", settings: "settings", agents: "agents",
    "new-chat": "new-chat", "quick-files": "files", "quick-memory": "memory",
    "research-demo": "research", "open-demo": "files", document: "files",
    listen: "voice-input", cancel: "stop"
  });
  document.addEventListener("click", event => {
    const nav = event.target.closest("button[data-section]");
    if (nav?.dataset.section && safe.has(nav.dataset.section)) {
      event.preventDefault(); event.stopImmediatePropagation();
      transport("route", nav.dataset.section);
      return;
    }
    const chat = event.target.closest("[data-chat]");
    if (chat) {
      // Preview chat titles are examples: use the app's real transcript selector instead.
      event.preventDefault(); event.stopImmediatePropagation();
      transport("route", "chats");
      return;
    }
    const action = event.target.closest("[data-action]");
    const mapped = action && map[action.dataset.action];
    if (mapped) {
      event.preventDefault(); event.stopImmediatePropagation();
      transport("route", mapped);
    }
  }, true);
  document.querySelector("#command-form")?.addEventListener("submit", event => {
    event.preventDefault(); event.stopImmediatePropagation();
    const input = document.querySelector("#command-input");
    const text = input?.value?.trim() || "";
    if (text && text.length <= 2000) transport("command", text);
  }, true);
  const badge = document.querySelector(".preview-badge");
  if (badge) badge.textContent = "ANDROID · ORIGINALDESIGN";
  const note = document.querySelector("#workspace-footnote");
  if (note) note.textContent = "Gestaltung aus PR #850 · Aufträge öffnen echte Android-Werkzeuge · noch keine visuelle Geräteabnahme";
})();
