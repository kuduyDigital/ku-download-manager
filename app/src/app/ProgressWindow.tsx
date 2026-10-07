import { te } from "../lib/engineText";
import { useEffect, useLayoutEffect, useMemo, useState, type CSSProperties } from "react";
import { t, tf } from "../lib/i18n";
import { invoke } from "@tauri-apps/api/core";
import { listen } from "@tauri-apps/api/event";
import { getCurrentWindow } from "@tauri-apps/api/window";
import { ArrowDownToLine, Check, ChevronDown, ChevronRight, FolderOpen, Gauge, Minus, Pause, Play, RotateCw, X } from "lucide-react";
import { glyphColor, glyphFor } from "./downloads/FileGlyph";
import { api, errorText } from "../lib/api";
import { settingsStore } from "../lib/store";
import { applyAppearance } from "../lib/appearance";
import { autoFit } from "../lib/fitWindow";
import * as fmt from "../lib/format";
import type { CoreEvent, Details, Download } from "../lib/types";
import { Button, Checkbox, Icon, IconButton, Progress, Select } from "../ui/primitives";

const ACTIVE = new Set(["downloading", "processing", "seeding"]);

/** Blocks for the map: aria2 piece bitfield or KuHTTP segment ranges → 0..1 fill per cell. */
function mapCells(details: Details | null, d: Download, cells = 120): number[] {
  if (details?.segments?.length && d.total > 0) {
    const out = new Array(cells).fill(0);
    for (const sg of details.segments) {
      const got = sg.done ? sg.end - sg.start : sg.downloaded;
      const from = sg.start;
      const to = sg.start + got;
      for (let i = 0; i < cells; i++) {
        const a = (i / cells) * d.total;
        const b = ((i + 1) / cells) * d.total;
        const overlap = Math.max(0, Math.min(b, to) - Math.max(a, from));
        out[i] = Math.min(1, out[i] + overlap / (b - a));
      }
    }
    return out;
  }
  const bf = details?.pieces?.bitfield;
  const count = details?.pieces?.count ?? 0;
  if (bf && count) {
    const bits: number[] = [];
    for (const ch of bf) {
      const n = parseInt(ch, 16);
      for (let b = 3; b >= 0 && bits.length < count; b--) bits.push((n >> b) & 1);
    }
    return Array.from({ length: Math.min(cells, count) }, (_, i) => {
      const per = count / Math.min(cells, count);
      const slice = bits.slice(Math.floor(i * per), Math.floor((i + 1) * per) || Math.floor(i * per) + 1);
      return slice.reduce((a, b) => a + b, 0) / slice.length;
    });
  }
  const pct = d.total > 0 ? d.done / d.total : 0;
  return Array.from({ length: cells }, (_, i) => ((i + 1) / cells <= pct ? 1 : 0));
}

/** "When done" choices, remembered for every progress window. */
function usePref(key: string): [boolean, (v: boolean) => void] {
  const [v, setV] = useState(() => {
    try {
      return localStorage.getItem(key) === "1";
    } catch {
      return false;
    }
  });
  const set = (x: boolean) => {
    setV(x);
    try {
      localStorage.setItem(key, x ? "1" : "0");
    } catch {
      /* storage unavailable: this window only */
    }
  };
  return [v, set];
}

const LIMITS = [0, 256 * 1024, 512 * 1024, 1024 ** 2, 2 * 1024 ** 2, 5 * 1024 ** 2, 10 * 1024 ** 2];

/** The last minute of speed as a small line: a feel for the trend, no axes. */
function Sparkline({ values }: { values: number[] }) {
  if (values.length < 2) return null;
  const w = 100;
  const h = 24;
  // Headroom: a steady speed draws a line in the lower half, not a full block.
  const max = Math.max(1, ...values) * 1.8;
  const step = w / (values.length - 1);
  const pts = values.map((v, i) => `${(i * step).toFixed(1)},${(h - (v / max) * h).toFixed(1)}`).join(" ");
  return (
    <svg className="pw-spark" viewBox={`0 0 ${w} ${h}`} preserveAspectRatio="none" aria-hidden="true">
      <polygon points={`0,${h} ${pts} ${w},${h}`} className="area" />
      <polyline points={pts} className="line" />
    </svg>
  );
}

/** IDM-style window for one download: status, speed, time left, live segment map. */
export function ProgressWindow({ id }: { id: string }) {
  const win = useMemo(() => getCurrentWindow(), []);
  const settings = settingsStore.use();
  const [d, setD] = useState<Download | null>(null);
  const [details, setDetails] = useState<Details | null>(null);
  const [more, setMore] = useState(false);
  const [error, setError] = useState<string | null>(null);
  const [speeds, setSpeeds] = useState<number[]>([]);
  const [openWhenDone, setOpenWhenDone] = usePref("ku-pw-open");
  const [closeWhenDone, setCloseWhenDone] = usePref("ku-pw-close");

  useEffect(() => void applyAppearance(settings), [settings]);

  // Initial state + live updates for this download only.
  useEffect(() => {
    void invoke<Download | null>("get_download", { id }).then((x) => (x ? setD(x) : void win.close()));
    const un = listen<CoreEvent>("ku", ({ payload: e }) => {
      if (e.type === "upsert" && e.download.id === id) setD(e.download);
      else if (e.type === "removed" && e.ids.includes(id)) void win.close();
      else if (e.type === "progress") {
        const p = e.items.find((i) => i.id === id);
        if (p) {
          setD((old) => (old ? { ...old, ...p } : old));
          setSpeeds((s) => [...s.slice(-59), p.speed ?? 0]);
        }
      }
    });
    return () => void un.then((f) => f());
  }, [id, win]);

  const live = !!d && ACTIVE.has(d.status);
  useEffect(() => {
    if (!live || !more) return;
    let alive = true;
    const load = () => void api.details(id).then((x) => alive && setDetails(x)).catch(() => {});
    load();
    const t = setInterval(load, 1000);
    return () => {
      alive = false;
      clearInterval(t);
    };
  }, [id, live, more]);

  // Finished while this window watched it: do what was asked.
  const complete = !!d && (d.status === "completed" || d.status === "seeding");
  const [watched, setWatched] = useState(false);
  useEffect(() => {
    if (live) setWatched(true);
  }, [live]);
  useEffect(() => {
    if (!complete || !watched) return;
    if (openWhenDone) void api.openFile(id).catch((e) => setError(errorText(e)));
    if (closeWhenDone) void win.close();
    // Only when it turns complete, not when the choices change afterwards.
    // eslint-disable-next-line react-hooks/exhaustive-deps
  }, [complete, watched]);

  // Fit the window to its content when it opens and whenever the content grows
  // or shrinks (details shown / hidden, finished, error). Resized by hand, it
  // keeps the user's size: the body scrolls and the buttons stay at the bottom.
  const ready = !!d;
  useLayoutEffect(() => {
    if (!ready) return;
    const el = document.querySelector<HTMLElement>(".pw");
    const body = el?.querySelector<HTMLElement>(".pw-body");
    if (!el || !body) return;
    return autoFit(el, body, { max: 680, onFirstFit: () => void win.show().then(() => win.setFocus()) });
  }, [ready, win]);

  if (!d) return null;
  const pct = fmt.percent(d.done, d.total);
  const eta = d.speed > 0 && d.total > 0 ? (d.total - d.done) / d.speed : null;
  const cells = mapCells(details, d);
  const conns = details?.segments
    ? details.segments.filter((s) => !s.done).map((s) => ({ label: `${fmt.bytes(s.start)} – ${fmt.bytes(s.end)}`, speed: s.speed, pct: fmt.percent(s.downloaded, s.end - s.start) }))
    : (details?.connections ?? []).map((c) => ({ label: c.currentUri || c.uri, speed: c.speed, pct: -1 }));
  const maxSpeed = Math.max(1, ...conns.map((c) => c.speed));
  const act = (p: Promise<unknown>) => void p.catch((e) => setError(errorText(e)));
  const state = complete ? "completed" : d.status === "error" ? "error" : d.status === "paused" ? "paused" : d.status === "queued" ? "queued" : "downloading";
  const status = complete ? "Download complete" : d.status === "error" ? "Failed" : d.status === "paused" ? "Paused" : d.status === "queued" ? "Queued" : d.status === "processing" ? "Merging…" : "Receiving data…";
  const limit = d.options?.speedLimit ?? 0;

  return (
    <div className="pw" data-state={state}>
      <header className="pw-head" data-tauri-drag-region>
        <Icon icon={ArrowDownToLine} />
        <span className="pw-title truncate" data-tauri-drag-region>
          {complete ? t("Download complete") : live && d.done > 0 && d.total > 0 ? `${Math.floor(pct)}% · ${d.name}` : d.name}
        </span>
        <IconButton icon={Minus} label={t("Minimize")} size="sm" onClick={() => void win.minimize()} />
        <IconButton icon={X} label={complete ? t("Close") : t("Close (the download continues)")} size="sm" onClick={() => void win.close()} />
      </header>

      <div className="pw-body">
        <div className="pw-hero">
          <span className="pw-glyph" style={state === "completed" || state === "error" ? undefined : ({ "--glyph": glyphColor(d) } as CSSProperties)} data-state={state}>
            <Icon icon={complete ? Check : glyphFor(d)} size={22} />
          </span>
          <div className="pw-id">
            <b className="pw-name selectable" title={d.name}>
              {d.name}
            </b>
            <span className="pw-meta faint">
              <span className="truncate" title={d.url}>
                {fmt.host(d.url) || d.url}
              </span>
              {d.total > 0 && <span className="num">{fmt.bytes(d.total)}</span>}
              {!complete && d.meta.resumable != null && (
                <span className="pw-chip" data-ok={d.meta.resumable}>
                  {d.meta.resumable ? t("Resumable") : t("Not resumable")}
                </span>
              )}
            </span>
          </div>
          <span className="pw-pct num">{d.total > 0 || complete ? `${Math.floor(complete ? 100 : pct)}%` : ""}</span>
        </div>

        <div className="pw-bar" data-state={state}>
          <Progress value={complete ? 100 : pct} state={state} indeterminate={live && d.total <= 0} />
        </div>

        <div className="pw-status" data-state={state}>
          <span className="pw-dot" />
          <span className="pw-status-text">{d.status === "error" ? te(d.error) || t(status) : t(status)}</span>
          {live && d.activeConnections > 0 && <span className="pw-conn-count num">{tf("{n} connections", { n: d.activeConnections })}</span>}
        </div>

        <div className="pw-stats">
          <div>
            <span>{complete ? t("Size") : t("Downloaded")}</span>
            <b className="num">
              {complete ? fmt.bytes(d.total || d.done) : fmt.bytes(d.done)}
              {!complete && d.total > 0 && <small> / {fmt.bytes(d.total)}</small>}
            </b>
          </div>
          {live ? (
            <>
              <div className="pw-stat-speed">
                <span>{t("Transfer rate")}</span>
                <b className="num">{live ? fmt.speed(d.speed) : "—"}</b>
                {live && <Sparkline values={speeds} />}
              </div>
              <div>
                <span>{t("Time left")}</span>
                <b className="num">{eta != null ? fmt.duration(eta) : "—"}</b>
              </div>
            </>
          ) : (
            <div>
              <span>{t("Saved to")}</span>
              <b className="truncate pw-path" title={d.dir}>
                {d.dir}
              </b>
            </div>
          )}
        </div>

        {(live || state === "queued") && (
          <div className="pw-options">
            <span className="faint">{t("When done:")}</span>
            <Checkbox checked={openWhenDone} onChange={setOpenWhenDone}>
              {t("Open the file")}
            </Checkbox>
            <Checkbox checked={closeWhenDone} onChange={setCloseWhenDone}>
              {t("Close this window")}
            </Checkbox>
            <span className="spacer" />
            <label className="pw-limit" title={t("Speed limit for this download")}>
              <Icon icon={Gauge} size={14} />
              <Select
                aria-label={t("Speed limit for this download")}
                value={limit}
                onChange={(e) => act(api.edit(d.id, { speedLimit: +e.target.value }))}
                options={LIMITS.map((v) => ({ value: v, label: v ? fmt.speed(v) : t("No limit") }))}
              />
            </label>
          </div>
        )}

        {!complete && (
          <>
            <button type="button" className="dl-more" onClick={() => setMore(!more)} aria-expanded={more}>
              <Icon icon={more ? ChevronDown : ChevronRight} size={14} /> {more ? t("Hide details") : t("Show details")}
            </button>
            {more && (
              <div className="pw-details">
                <div className="pw-map" aria-label={t("Downloaded parts of the file")} style={{ gridTemplateColumns: `repeat(${cells.length}, 1fr)` }}>
                  {cells.map((v, i) => (
                    <span key={i} style={{ opacity: v > 0 ? 0.35 + v * 0.65 : 1 }} data-have={v > 0} />
                  ))}
                </div>
                {conns.length > 0 && (
                  <div className="pw-conns num">
                    {conns.slice(0, 16).map((c, i) => (
                      <div key={i} className="conn-row" title={c.label}>
                        <span className="faint">
                          {d.engine === "kuhttp" ? t("Part") : t("Conn")} {String(i + 1).padStart(2, "0")}
                        </span>
                        <Progress value={c.pct >= 0 ? c.pct : (c.speed / maxSpeed) * 100} state="downloading" />
                        <span style={{ textAlign: "right" }}>{fmt.speed(c.speed)}</span>
                      </div>
                    ))}
                  </div>
                )}
                <div className="pw-facts">
                  <span>{t("Address")}</span>
                  <span className="truncate selectable" title={d.url}>
                    {d.url}
                  </span>
                  <span>{t("Saved to")}</span>
                  <span className="truncate selectable" title={d.dir}>
                    {d.dir}
                  </span>
                  <span>{t("Resume")}</span>
                  <span>{d.meta.resumable === false ? t("No — pausing restarts the file") : d.meta.resumable ? t("Yes") : t("Unknown")}</span>
                </div>
              </div>
            )}
          </>
        )}
        {error && <div className="pw-error">{error}</div>}
      </div>

      <footer className="pw-actions">
        <Button variant="ghost" icon={FolderOpen} onClick={() => act(api.openFolder(d.id))}>
          {t("Show in folder")}
        </Button>
        <span className="spacer" />
        {complete ? (
          <>
            <Button onClick={() => void win.close()}>{t("Close")}</Button>
            <Button variant="primary" onClick={() => act(api.openFile(d.id).then(() => win.close()))}>
              {t("Open")}
            </Button>
          </>
        ) : state === "error" ? (
          <>
            <Button onClick={() => void win.close()}>{t("Close")}</Button>
            <Button variant="primary" icon={RotateCw} onClick={() => act(api.resume([d.id]))}>
              {t("Retry")}
            </Button>
          </>
        ) : (
          <>
            {/* Like IDM: Cancel stops the download and keeps it in the list (resumable); deleting is done from the list. */}
            <Button className="is-danger" title={t("Stops the download and keeps it in your list, so you can resume it later.")} onClick={() => act((live || d.status === "queued" ? api.pause([d.id]) : Promise.resolve()).then(() => win.close()))}>
              {t("Cancel")}
            </Button>
            {live || d.status === "queued" ? (
              <Button variant="primary" icon={Pause} onClick={() => act(api.pause([d.id]))}>
                {t("Pause")}
              </Button>
            ) : (
              <Button variant="primary" icon={d.status === "error" ? RotateCw : Play} onClick={() => act(api.resume([d.id]))}>
                {d.status === "error" ? t("Retry") : t("Resume")}
              </Button>
            )}
          </>
        )}
      </footer>
    </div>
  );
}
