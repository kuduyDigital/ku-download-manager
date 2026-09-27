import { Sparkles } from "lucide-react";
import { t, tf } from "../lib/i18n";
import { Button, Icon } from "../ui/primitives";
import { Dialog } from "../ui/overlays";

/** Shown once after an update: what changed since the last version used. */
export function WhatsNew({ notes, onClose }: { notes: { version: string; items: string[] }[]; onClose: () => void }) {
  return (
    <Dialog
      title={
        <span style={{ display: "inline-flex", alignItems: "center", gap: 8 }}>
          <Icon icon={Sparkles} size={18} />
          {t("What's new")}
        </span>
      }
      onClose={onClose}
      width={460}
      footer={
        <>
          <span className="spacer" />
          <Button variant="primary" data-autofocus onClick={onClose}>
            {t("Got it")}
          </Button>
        </>
      }
    >
      <div className="whats-new">
        {notes.map((n) => (
          <section key={n.version}>
            <div className="whats-new-version">{tf("Version {version}", { version: n.version })}</div>
            <ul>
              {n.items.map((item) => (
                <li key={item}>{t(item)}</li>
              ))}
            </ul>
          </section>
        ))}
      </div>
    </Dialog>
  );
}
