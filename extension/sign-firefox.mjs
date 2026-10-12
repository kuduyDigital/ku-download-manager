// Sends dist/firefox to addons.mozilla.org. Needs AMO API credentials:
//   https://addons.mozilla.org/developers/addon/api/key/
//   AMO_JWT_ISSUER=... AMO_JWT_SECRET=... node sign-firefox.mjs
//
// Default (listed): a new version of the add-on on Firefox Add-ons. It is
// submitted without waiting for Mozilla's review; once approved, Firefox
// updates everyone who installed it from the store.
//
// KU_AMO_CHANNEL=unlisted: a Mozilla-signed .xpi for self-distribution instead
// (dist/kudmx.signed.xpi, installs permanently in one click). A version number
// can only be used once across both channels.
import { execFileSync } from "node:child_process";
import { existsSync, readdirSync, renameSync } from "node:fs";
import { join } from "node:path";

const { AMO_JWT_ISSUER, AMO_JWT_SECRET } = process.env;
const channel = process.env.KU_AMO_CHANNEL === "unlisted" ? "unlisted" : "listed";
if (!AMO_JWT_ISSUER || !AMO_JWT_SECRET) {
  console.error("Set AMO_JWT_ISSUER and AMO_JWT_SECRET (addons.mozilla.org → Developer Hub → API keys).");
  process.exit(1);
}
const out = join(import.meta.dirname, "dist", "signed");
const args = ["--yes", "web-ext", "sign", "--source-dir", "dist/firefox", "--channel", channel, "--artifacts-dir", out, "--api-key", AMO_JWT_ISSUER, "--api-secret", AMO_JWT_SECRET];
if (channel === "listed") args.push("--approval-timeout", "0");
execFileSync("npx", args, { stdio: "inherit", shell: true, cwd: import.meta.dirname });
if (channel === "listed") {
  console.log("Submitted to Firefox Add-ons; Firefox updates store installs once Mozilla approves it.");
} else {
  const xpi = existsSync(out) ? readdirSync(out).find((f) => f.endsWith(".xpi")) : null;
  if (!xpi) throw new Error("Mozilla returned no signed file.");
  renameSync(join(out, xpi), join(import.meta.dirname, "dist", "kudmx.signed.xpi"));
  console.log("dist/kudmx.signed.xpi ready: installs permanently in Firefox with one click.");
}
