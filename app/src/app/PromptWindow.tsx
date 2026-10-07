import { useEffect, useLayoutEffect, useMemo, useState } from "react";
import { invoke } from "@tauri-apps/api/core";
import { getCurrentWindow } from "@tauri-apps/api/window";
import type { AddRequest } from "../lib/types";
import { usePopupAppearance } from "../lib/popupAppearance";
import { autoFit } from "../lib/fitWindow";
import { ToastHost } from "../ui/overlays";
import { AddDownloadDialog } from "./AddDownloadDialog";
import { AppContext, type AppApi } from "./context";

/**
 * Standalone "Download File" window (IDM-style) for a download caught in the
 * browser. Renders only the add dialog; closes itself when done.
 */
export function PromptWindow({ id }: { id: string }) {
  const [request, setRequest] = useState<AddRequest | null>(null);
  // Follows the main window's theme live (and the OS for "System").
  usePopupAppearance();
  // One handle for the window's lifetime (getCurrentWindow() returns a new object each call).
  const win = useMemo(() => getCurrentWindow(), []);

  useEffect(() => {
    void invoke<AddRequest | null>("get_prompt", { id }).then((r) => (r ? setRequest(r) : void win.close()));
  }, [id, win]);

  // Fit the window to the dialog, then show it. It follows "More options"
  // opening and closing; resized by hand, the fields scroll instead.
  useLayoutEffect(() => {
    if (!request) return;
    const form = document.querySelector<HTMLElement>(".dialog");
    const body = form?.querySelector<HTMLElement>(".dialog-body");
    if (!form || !body) return;
    return autoFit(form, body, { onFirstFit: () => void win.show().then(() => win.setFocus()) });
  }, [request, win]);

  const close = () => void win.close();
  const api = useMemo(
    () =>
      ({
        openMedia: (req) => {
          void invoke("open_media_in_main", { url: req?.url ?? "", cookies: req?.cookies ?? [] }).finally(close);
        },
      }) as AppApi,
    // eslint-disable-next-line react-hooks/exhaustive-deps
    [],
  );

  if (!request) return null;
  return (
    <AppContext.Provider value={api}>
      <AddDownloadDialog prefill={request} onClose={close} />
      <ToastHost />
    </AppContext.Provider>
  );
}
