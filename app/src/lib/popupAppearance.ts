import { useEffect } from "react";
import { listen } from "@tauri-apps/api/event";
import { applyAppearance } from "./appearance";
import { settingsStore } from "./store";
import type { CoreEvent } from "./types";

/**
 * Popup windows (progress, Download File) look like the main window and
 * follow it live: a theme, accent or palette change made there, and the OS
 * switching light / dark for the "System" theme.
 */
export function usePopupAppearance() {
  const settings = settingsStore.use();
  useEffect(() => void applyAppearance(settings), [settings]);
  useEffect(() => {
    // Popups don't run the main window's event store: refresh on our own.
    const un = listen<CoreEvent>("ku", ({ payload }) => {
      if (payload.type === "settingsChanged") void settingsStore.refresh();
    });
    const mq = matchMedia("(prefers-color-scheme: dark)");
    const onOs = () => void applyAppearance(settingsStore.get());
    mq.addEventListener("change", onOs);
    return () => {
      void un.then((f) => f());
      mq.removeEventListener("change", onOs);
    };
  }, []);
}
