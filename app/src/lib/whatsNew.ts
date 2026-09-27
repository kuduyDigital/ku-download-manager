/**
 * What changed in each version, shown once after an update (not on a fresh
 * install). Newest first; keep each line short and about what people notice.
 */
export const WHATS_NEW: { version: string; items: string[] }[] = [
  {
    version: "0.2.10",
    items: [
      "The Download File and progress windows fit any size: make them small and the details scroll, the buttons stay.",
      "Show / Hide details no longer makes the progress window grow each time.",
      "This window: see what's new after every update.",
      "Android: fixed the Downloads header under the status bar and squeezed buttons on small screens.",
    ],
  },
  {
    version: "0.2.9",
    items: [
      "Redesigned progress window: big percentage, speed and time left at a glance, details on demand.",
      "Redesigned Download File window with the file's name, size and site up front.",
      "KuAirSend on Linux: a firewall that blocks it is detected, and one click lets it through.",
      "Uses much less memory while waiting in the tray.",
      "Android: a new browser with favourites, a privacy report, tab cards and a page menu; the keyboard no longer covers page fields.",
    ],
  },
];

/** "0.2.10" > "0.2.9" */
export function newer(a: string, b: string): boolean {
  const pa = a.split(/[.-]/).map((n) => parseInt(n) || 0);
  const pb = b.split(/[.-]/).map((n) => parseInt(n) || 0);
  for (let i = 0; i < Math.max(pa.length, pb.length); i++) {
    if ((pa[i] ?? 0) !== (pb[i] ?? 0)) return (pa[i] ?? 0) > (pb[i] ?? 0);
  }
  return false;
}

const KEY = "ku.seenVersion";

/**
 * The notes to show now, or null. Remembers the running version either way.
 * `upgraded`: the app was set up before (so an unknown last version means it
 * was updated from a version without this feature, not freshly installed).
 */
export function pendingNotes(current: string, upgraded: boolean): { version: string; items: string[] }[] | null {
  let seen: string | null = null;
  try {
    seen = localStorage.getItem(KEY);
    localStorage.setItem(KEY, current);
  } catch {
    return null;
  }
  if (seen === current) return null;
  if (seen == null && !upgraded) return null;
  const list = WHATS_NEW.filter((n) => !newer(n.version, current) && (seen == null ? n.version === current : newer(n.version, seen)));
  return list.length ? list : null;
}
