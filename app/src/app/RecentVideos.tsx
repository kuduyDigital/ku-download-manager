import { t } from "../lib/i18n";
import { useEffect, useState } from "react";
import { Clipboard, Clapperboard } from "lucide-react";
import { api } from "../lib/api";
import { useDownload, useDownloadIds } from "../lib/store";
import type { Download } from "../lib/types";
import { Icon } from "../ui/primitives";
import { useApp } from "./context";

const STATUS: Record<Download["status"], string> = {
  queued: "Queued",
  downloading: "Downloading",
  processing: "Processing",
  paused: "Paused",
  seeding: "Seeding",
  completed: "Complete",
  error: "Error",
};

const isMedia = (d: Download) => d.kind === "media";
const newestFirst = (a: Download, b: Download) => b.createdAt - a.createdAt;

/** One recent video: thumbnail, title, live status. Opens its details. */
function RecentRow({ id }: { id: string }) {
  const d = useDownload(id);
  const { showList, setSelection, setInspectorOpen } = useApp();
  if (!d) return null;
  const pct = d.total > 0 ? Math.floor((d.done / d.total) * 100) : null;
  const status = d.status === "downloading" && pct != null ? `${pct}%` : t(STATUS[d.status]);
  return (
    <button
      type="button"
      className="recent-video"
      onClick={() => {
        showList({ scope: "all", category: "" });
        setSelection(new Set([d.id]));
        setInspectorOpen(true);
      }}
    >
      {d.meta.thumbnail ? (
        <img src={d.meta.thumbnail} alt="" loading="lazy" referrerPolicy="no-referrer" />
      ) : (
        <span className="recent-video-ph">
          <Icon icon={Clapperboard} size={18} />
        </span>
      )}
      <span className="recent-video-text">
        <span className="truncate">{d.meta.mediaTitle || d.name}</span>
        <span className={`recent-video-status${d.status === "error" ? " is-error" : ""}`}>{status}</span>
      </span>
    </button>
  );
}

/** The Video Downloader before a link is entered: clipboard link and recent videos. */
export function VideoStart({ current, onPick }: { current: string; onPick: (url: string) => void }) {
  const ids = useDownloadIds(isMedia, newestFirst, []).slice(0, 6);
  const [clip, setClip] = useState<string | null>(null);
  useEffect(() => {
    const read = () =>
      void api
        .readClipboard()
        .then((c) => {
          const link = c?.trim().split(/\s+/)[0] ?? "";
          setClip(/^https?:\/\/\S+$/i.test(link) ? link : null);
        })
        .catch(() => setClip(null));
    read();
    window.addEventListener("focus", read);
    return () => window.removeEventListener("focus", read);
  }, []);
  const showClip = clip && clip !== current.trim();
  return (
    <>
      {showClip && (
        <button type="button" className="clip-chip" onClick={() => onPick(clip)} title={clip}>
          <Icon icon={Clipboard} size={14} />
          <span>{t("Use the link you copied")}</span>
          <span className="clip-chip-url truncate">{clip.replace(/^https?:\/\/(www\.)?/, "")}</span>
        </button>
      )}
      {ids.length > 0 && (
        <section className="recent-videos" aria-label={t("Recent videos")}>
          <h3>{t("Recent videos")}</h3>
          <div className="recent-videos-grid">
            {ids.map((id) => (
              <RecentRow key={id} id={id} />
            ))}
          </div>
        </section>
      )}
    </>
  );
}
