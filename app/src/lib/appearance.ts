import type { Settings } from "./types";

/** Theme, accent, dark palette and density on <html> (main window and popups). */
export function applyAppearance(s: Settings | null | undefined): "light" | "dark" {
  const root = document.documentElement;
  const theme = s?.theme ?? "system";
  const resolved = theme === "system" ? (matchMedia("(prefers-color-scheme: dark)").matches ? "dark" : "light") : theme;
  root.dataset.theme = resolved;
  root.dataset.material = "none";
  root.dataset.accent = s?.accent && s.accent !== "blue" ? s.accent : "";
  // One palette attribute: the dark one in dark mode, the light one in light mode.
  const palette = resolved === "dark" ? s?.darkPalette : s?.lightPalette;
  root.dataset.palette = palette && palette !== "default" ? palette : "";
  root.dataset.density = s?.compact ? "compact" : "comfortable";
  return resolved;
}
