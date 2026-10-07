/**
 * A file name on one line that, when too long, loses its middle rather than
 * its end: "Doraemon.New.Nobita…x264.mkv". The tail (with the extension)
 * always shows; the rest is cut with an ellipsis only if it doesn't fit.
 */
export function MiddleName({ name, className = "" }: { name: string; className?: string }) {
  const dot = name.lastIndexOf(".");
  const ext = dot > 0 && name.length - dot <= 6 ? name.slice(dot) : "";
  // Keep the extension and a few characters before it.
  const keep = Math.min(name.length, ext.length + 8);
  if (name.length <= keep + 4) {
    return (
      <span className={`middle-name ${className}`} title={name}>
        <span className="middle-name-head">{name}</span>
      </span>
    );
  }
  return (
    <span className={`middle-name ${className}`} title={name}>
      <span className="middle-name-head">{name.slice(0, name.length - keep)}</span>
      <span className="middle-name-tail">{name.slice(name.length - keep)}</span>
    </span>
  );
}
