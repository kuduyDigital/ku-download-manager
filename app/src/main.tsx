import { StrictMode } from "react";
import { createRoot } from "react-dom/client";
import "@fontsource-variable/inter";
import "@fontsource-variable/bricolage-grotesque";
import "./design-system/tokens.css";
import "./design-system/base.css";
import "./design-system/components.css";
import "./app/app.css";
import "./app/fluent.css";
import App from "./App";
import { initLanguage } from "./lib/i18n";
import { PromptWindow } from "./app/PromptWindow";
import { ProgressWindow } from "./app/ProgressWindow";

// Popup windows load the same page: "Download File" for a browser download,
// and the per-download progress window.
const prompt = location.hash.match(/^#prompt=(\d+)$/)?.[1];
const progress = location.hash.match(/^#progress=([\w-]+)$/)?.[1];
if (prompt) document.documentElement.dataset.window = "prompt";
if (progress) document.documentElement.dataset.window = "progress";

// Every window (main, progress, Download File): no web-page menu (Back,
// Refresh, Save as, Print) on right-click. Text fields and selected text keep
// it for copy and paste; the app's own menus call preventDefault themselves.
window.addEventListener("contextmenu", (e) => {
  const t = e.target as HTMLElement | null;
  const editable = !!t && (t.tagName === "INPUT" || t.tagName === "TEXTAREA" || t.isContentEditable || !!t.closest(".selectable"));
  const selected = (window.getSelection()?.toString() ?? "").trim().length > 0;
  if (!editable && !selected) e.preventDefault();
});
// …nor the web-page shortcuts: reload (F5, Ctrl+R) would reset a window,
// Back/Forward would leave it, Print/View source make no sense here.
window.addEventListener("keydown", (e) => {
  const k = e.key.toLowerCase();
  const ctrl = e.ctrlKey || e.metaKey;
  if (k === "f5" || (ctrl && (k === "r" || k === "p" || k === "u")) || (e.altKey && (k === "arrowleft" || k === "arrowright")) || k === "browserback" || k === "browserforward") {
    e.preventDefault();
  }
});

// The interface language is loaded before the first render.
void initLanguage().finally(() =>
  createRoot(document.getElementById("root")!).render(<StrictMode>{prompt ? <PromptWindow id={prompt} /> : progress ? <ProgressWindow id={progress} /> : <App />}</StrictMode>),
);
