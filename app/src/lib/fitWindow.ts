import { getCurrentWindow, LogicalSize } from "@tauri-apps/api/window";

/**
 * Height a scrolling flex column needs to show everything without scrolling.
 * (`scrollHeight` can't be used: it never reports less than the box's own
 * height, so a window could grow but never shrink back.)
 */
export function contentHeight(el: HTMLElement): number {
  const cs = getComputedStyle(el);
  const kids = [...el.children].filter((c): c is HTMLElement => c instanceof HTMLElement && getComputedStyle(c).position !== "absolute" && c.offsetParent !== null);
  const gap = parseFloat(cs.rowGap) || 0;
  let h = parseFloat(cs.paddingTop) + parseFloat(cs.paddingBottom) + Math.max(0, kids.length - 1) * gap;
  for (const k of kids) {
    const ks = getComputedStyle(k);
    h += k.offsetHeight + parseFloat(ks.marginTop) + parseFloat(ks.marginBottom);
  }
  return Math.ceil(h);
}

/** Height of `frame` if `scroller` (a part of it) showed all of its content. */
export function naturalHeight(frame: HTMLElement, scroller: HTMLElement): number {
  return frame.offsetHeight - scroller.clientHeight + contentHeight(scroller);
}

/**
 * Keep a small popup window as tall as its content: on open, and whenever the
 * content itself grows or shrinks (details shown / hidden). A change caused by
 * the user resizing the window (its width changed) is left alone, and the
 * content scrolls when the window is smaller than it.
 */
export function autoFit(frame: HTMLElement, scroller: HTMLElement, opts: { max?: number; onFirstFit?: () => void } = {}): () => void {
  const win = getCurrentWindow();
  const max = () => Math.min(opts.max ?? 720, screen.availHeight - 60);
  let lastWidth = -1;
  let lastNatural = -1;
  let first = true;
  let timer = 0;

  const fit = () => {
    clearTimeout(timer);
    // A timer, not requestAnimationFrame: the window starts hidden, and hidden
    // web views may not run animation frames.
    timer = window.setTimeout(() => {
      const natural = naturalHeight(frame, scroller);
      const width = window.innerWidth;
      const userResized = !first && width !== lastWidth;
      const changed = Math.abs(natural - lastNatural) > 1;
      lastWidth = width;
      lastNatural = natural;
      if (!first && (userResized || !changed)) return;
      const h = Math.min(natural, max());
      void win
        .setSize(new LogicalSize(width, h))
        .catch(() => {})
        .then(() => {
        if (first) {
          first = false;
          opts.onFirstFit?.();
        }
      });
    }, 16);
  };

  // Content changes: watch every block inside the scroller (re-attached when
  // blocks are added or removed, e.g. "More options").
  const ro = new ResizeObserver(fit);
  const watch = () => {
    ro.disconnect();
    for (const c of scroller.children) ro.observe(c);
  };
  const mo = new MutationObserver(() => {
    watch();
    fit();
  });
  mo.observe(scroller, { childList: true });
  watch();
  fit();
  return () => {
    ro.disconnect();
    mo.disconnect();
    clearTimeout(timer);
  };
}
