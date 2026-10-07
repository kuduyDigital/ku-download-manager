import { useState } from "react";
import { File, FileArchive, FileAudio, FileImage, FileText, FileVideo, Disc3, Package, Magnet, Clapperboard, type LucideIcon } from "lucide-react";
import { Icon } from "../../ui/primitives";
import type { Download } from "../../lib/types";

export const CATEGORY_ICON: Record<string, LucideIcon> = {
  archives: FileArchive,
  "images-disk": Disc3,
  programs: Package,
  video: FileVideo,
  music: FileAudio,
  documents: FileText,
  images: FileImage,
  torrents: Magnet,
};

/** A colour per kind of file, for the gradient icon tiles (same as on Android). */
export function glyphColor(d: Pick<Download, "kind" | "category">): string {
  if (d.kind === "torrent" || d.kind === "magnet") return "#16A34A";
  if (d.kind === "media") return d.category === "music" ? "#7C3AED" : "#DB2777";
  return (
    {
      video: "#DB2777",
      music: "#7C3AED",
      archives: "#EA580C",
      "images-disk": "#0D9488",
      programs: "#52525B",
      documents: "#2563EB",
      images: "#0891B2",
      torrents: "#16A34A",
    } as Record<string, string>
  )[d.category] ?? "#64748B";
}

export function glyphFor(d: Pick<Download, "kind" | "category">): LucideIcon {
  if (d.kind === "torrent" || d.kind === "magnet") return Magnet;
  if (d.kind === "media") return d.category === "music" ? FileAudio : Clapperboard;
  return CATEGORY_ICON[d.category] ?? File;
}

/** File-type glyph, or the media thumbnail when one is known. */
export function FileGlyph({ d, size = 16 }: { d: Download; size?: number }) {
  const [failed, setFailed] = useState(false);
  const thumb = d.meta.thumbnail;
  return (
    <span className="row-icon">
      {thumb && !failed ? <img src={thumb} alt="" loading="lazy" referrerPolicy="no-referrer" onError={() => setFailed(true)} /> : <Icon icon={glyphFor(d)} size={size} />}
    </span>
  );
}
