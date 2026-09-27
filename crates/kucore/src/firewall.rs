//! Linux host firewalls (firewalld on Fedora/openSUSE, ufw on Ubuntu/Mint)
//! drop KuAirSend's incoming connections and discovery packets silently.
//! Windows and macOS ask the user themselves when the app starts listening.

use std::path::Path;

/// The active firewall that still blocks `port`, if any ("firewalld" or "ufw").
#[cfg(target_os = "linux")]
pub fn blocking(port: u16, dir: &Path) -> Option<&'static str> {
    use std::process::Command;
    let ok = |cmd: &str, args: &[&str]| Command::new(cmd).args(args).output().map(|o| (o.status.success(), String::from_utf8_lossy(&o.stdout).trim().to_string())).ok();

    // firewalld answers queries without root.
    if let Some((true, state)) = ok("firewall-cmd", &["--state"]) {
        if state == "running" {
            let open = |proto: &str| matches!(ok("firewall-cmd", &["--query-port", &format!("{port}/{proto}")]), Some((true, _)));
            return if open("tcp") && open("udp") { None } else { Some("firewalld") };
        }
    }
    // ufw's rules need root to read: trust our own marker once it was opened.
    let enabled = std::fs::read_to_string("/etc/ufw/ufw.conf").is_ok_and(|c| c.lines().any(|l| l.trim().eq_ignore_ascii_case("ENABLED=yes")));
    if enabled && !matches!(ok("systemctl", &["is-active", "--quiet", "ufw"]), Some((false, _))) {
        let rules = std::fs::read_to_string("/etc/ufw/user.rules").ok();
        let allowed = match rules {
            Some(r) => r.contains(&format!("--dport {port} ")) || r.contains(&format!("--dport {port}\n")),
            None => opened_marker(port, dir).exists(),
        };
        return if allowed { None } else { Some("ufw") };
    }
    None
}

#[cfg(not(target_os = "linux"))]
pub fn blocking(_port: u16, _dir: &Path) -> Option<&'static str> {
    None
}

#[cfg(target_os = "linux")]
fn opened_marker(port: u16, dir: &Path) -> std::path::PathBuf {
    dir.join(format!("ufw-opened-{port}"))
}

/// Allow `port` (TCP and UDP) through the active firewall. Asks for the
/// administrator password once through polkit (pkexec).
#[cfg(target_os = "linux")]
pub fn open(port: u16, dir: &Path) -> anyhow::Result<()> {
    use anyhow::{bail, Context};
    let script = match blocking(port, dir) {
        None => return Ok(()),
        Some("firewalld") => format!("firewall-cmd --permanent --add-port={port}/tcp --add-port={port}/udp && firewall-cmd --add-port={port}/tcp --add-port={port}/udp"),
        Some(_) => format!("ufw allow {port}/tcp comment KuAirSend && ufw allow {port}/udp comment KuAirSend"),
    };
    let out = std::process::Command::new("pkexec")
        .args(["/bin/sh", "-c", &script])
        .output()
        .context("Could not ask for the administrator password (pkexec is missing).")?;
    match out.status.code() {
        Some(0) => {
            let _ = std::fs::write(opened_marker(port, dir), b"");
            Ok(())
        }
        // 126: the password dialog was dismissed; 127: not authorised.
        Some(126) | Some(127) => bail!("The firewall was not changed."),
        _ => bail!("The firewall could not be changed: {}", String::from_utf8_lossy(&out.stderr).trim()),
    }
}

#[cfg(not(target_os = "linux"))]
pub fn open(_port: u16, _dir: &Path) -> anyhow::Result<()> {
    Ok(())
}
