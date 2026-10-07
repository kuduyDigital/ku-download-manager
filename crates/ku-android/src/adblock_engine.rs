//! Ad and tracker blocking for the built-in browser: EasyList-syntax filter
//! lists matched by Brave's adblock engine. Lists are downloaded when the user
//! turns blocking on (or updates them) and the compiled engine is cached, so
//! later starts load in milliseconds.

use adblock::lists::{FilterSet, ParseOptions};
use adblock::request::Request;
use adblock::Engine;
use anyhow::{bail, Result};
use kucore::Settings;
use serde_json::{json, Value};
use std::collections::HashSet;
use std::path::{Path, PathBuf};
use std::sync::RwLock;
use std::time::Duration;

/// Used when the interface does not name its own lists.
pub const DEFAULT_LISTS: &[&str] = &[
    "https://easylist.to/easylist/easylist.txt",
    "https://easylist.to/easylist/easyprivacy.txt",
    "https://secure.fanboy.co.nz/fanboy-annoyance.txt",
    // Pop-under and redirect networks, anti-adblock walls.
    "https://ublockorigin.github.io/uAssets/filters/filters.txt",
];

struct Loaded {
    engine: Engine,
    rules: usize,
    updated: i64,
    /// Domains of `||host^$popup` rules: the engine has no pop-up request
    /// type, so these are matched here (see [`should_block_popup`]).
    popups: HashSet<String>,
    /// Compiled from other lists than the current defaults (refresh soon).
    stale: bool,
}

/// `||ads.example^$popup` (optionally with third-party etc.) → "ads.example".
/// Rules limited to some sites (`domain=`) or exceptions are left out.
fn popup_domains(texts: &[String]) -> HashSet<String> {
    let mut out = HashSet::new();
    for t in texts {
        for line in t.lines() {
            let line = line.trim();
            let Some(rest) = line.strip_prefix("||") else { continue };
            let Some((pattern, opts)) = rest.split_once('$') else { continue };
            let opts: Vec<&str> = opts.split(',').map(str::trim).collect();
            if !opts.contains(&"popup") || opts.iter().any(|o| o.starts_with("domain=") || o.starts_with('~')) {
                continue;
            }
            let host = pattern.trim_end_matches('|').trim_end_matches('^').to_ascii_lowercase();
            if host.contains('.') && host.chars().all(|c| c.is_ascii_alphanumeric() || c == '.' || c == '-') {
                out.insert(host);
            }
        }
    }
    out
}

fn read_popups(d: &Path) -> HashSet<String> {
    std::fs::read_to_string(d.join("popups.txt")).map(|s| s.lines().map(str::to_string).filter(|l| !l.is_empty()).collect()).unwrap_or_default()
}

fn is_stale(meta: &Value) -> bool {
    let lists: Vec<&str> = meta["lists"].as_array().map(|a| a.iter().filter_map(|v| v.as_str()).collect()).unwrap_or_default();
    lists != DEFAULT_LISTS
}

static ENGINE: RwLock<Option<Loaded>> = RwLock::new(None);

fn dir(data: &Path) -> PathBuf {
    data.join("adblock")
}

fn now_ms() -> i64 {
    std::time::SystemTime::now().duration_since(std::time::UNIX_EPOCH).map(|d| d.as_millis() as i64).unwrap_or(0)
}

/// Load the compiled engine saved by the last update, if any.
pub async fn load_cached(data: &Path) {
    let d = dir(data);
    let Ok(bytes) = tokio::fs::read(d.join("engine.dat")).await else { return };
    let meta: Value = tokio::fs::read_to_string(d.join("meta.json")).await.ok().and_then(|s| serde_json::from_str(&s).ok()).unwrap_or_default();
    let loaded = tokio::task::spawn_blocking(move || {
        let mut engine = Engine::default();
        engine.deserialize(&bytes).ok()?;
        Some(engine)
    })
    .await
    .ok()
    .flatten();
    match loaded {
        Some(engine) => {
            *ENGINE.write().unwrap_or_else(|p| p.into_inner()) = Some(Loaded {
                engine,
                rules: meta["rules"].as_u64().unwrap_or(0) as usize,
                updated: meta["updated"].as_i64().unwrap_or(0),
                popups: read_popups(&d),
                stale: is_stale(&meta),
            });
        }
        None => tracing::warn!("ad blocker: the saved filters could not be read; update them in Settings"),
    }
}

/// Download the filter lists, compile them and save the result.
pub async fn update(data: &Path, lists: &[String], s: &Settings) -> Result<Value> {
    let lists: Vec<String> = if lists.is_empty() { DEFAULT_LISTS.iter().map(|s| s.to_string()).collect() } else { lists.to_vec() };
    let mut b = reqwest::Client::builder().timeout(Duration::from_secs(60)).gzip(true).user_agent(format!("KuDownloader/{}", env!("CARGO_PKG_VERSION")));
    if !s.proxy.trim().is_empty() {
        if let Ok(p) = reqwest::Proxy::all(s.proxy.trim()) {
            b = b.proxy(p);
        }
    }
    let client = b.build()?;
    let mut texts = Vec::new();
    let mut failed = Vec::new();
    for url in &lists {
        if !url.starts_with("https://") {
            failed.push(json!({"url": url, "error": "Only https addresses are accepted."}));
            continue;
        }
        let r = async {
            let resp = client.get(url).send().await?.error_for_status()?;
            if resp.content_length().is_some_and(|l| l > 32 << 20) {
                anyhow::bail!("The list is too large.");
            }
            Ok::<String, anyhow::Error>(resp.text().await?)
        }
        .await;
        match r {
            Ok(t) => texts.push(t),
            Err(e) => failed.push(json!({"url": url, "error": format!("{e:#}")})),
        }
    }
    if texts.is_empty() {
        bail!("No filter list could be downloaded. Check the connection and try again.");
    }
    let rules: usize = texts.iter().map(|t| t.lines().filter(|l| !l.is_empty() && !l.starts_with('!')).count()).sum();
    let (engine, bytes, popups) = tokio::task::spawn_blocking(move || {
        let popups = popup_domains(&texts);
        let mut set = FilterSet::new(false);
        for t in texts {
            set.add_filter_list(t, ParseOptions::default());
        }
        let engine = Engine::new_with_filter_set(set);
        let bytes = engine.serialize();
        (engine, bytes, popups)
    })
    .await?;
    let d = dir(data);
    tokio::fs::create_dir_all(&d).await?;
    tokio::fs::write(d.join("engine.dat"), &bytes).await?;
    tokio::fs::write(d.join("popups.txt"), popups.iter().map(String::as_str).collect::<Vec<_>>().join("\n")).await?;
    let updated = now_ms();
    tokio::fs::write(d.join("meta.json"), json!({"rules": rules, "updated": updated, "lists": lists}).to_string()).await?;
    let stale = lists.iter().map(String::as_str).collect::<Vec<_>>() != DEFAULT_LISTS;
    *ENGINE.write().unwrap_or_else(|p| p.into_inner()) = Some(Loaded { engine, rules, updated, popups, stale });
    Ok(json!({"rules": rules, "updated": updated, "failed": failed}))
}

pub fn clear(data: &Path) {
    *ENGINE.write().unwrap_or_else(|p| p.into_inner()) = None;
    let _ = std::fs::remove_dir_all(dir(data));
}

pub fn status() -> Value {
    match ENGINE.read().unwrap_or_else(|p| p.into_inner()).as_ref() {
        Some(l) => json!({"loaded": true, "rules": l.rules, "updated": l.updated, "stale": l.stale}),
        None => json!({"loaded": false, "rules": 0, "updated": null}),
    }
}

/// Should a new pop-up tab, or a page redirect, to `url` be stopped? A known
/// pop-under domain (or its subdomain), or an address the lists block as a
/// whole page. Never used for addresses the user typed or tapped.
pub fn should_block_popup(url: &str, source: &str) -> bool {
    let guard = ENGINE.read().unwrap_or_else(|p| p.into_inner());
    guard.as_ref().is_some_and(|l| popup_blocked(l, url, source))
}

fn popup_blocked(l: &Loaded, url: &str, source: &str) -> bool {
    let Some(host) = url::Url::parse(url).ok().and_then(|u| u.host_str().map(str::to_ascii_lowercase)) else { return false };
    // Same site as the page: a normal navigation, not an ad.
    let page_host = url::Url::parse(source).ok().and_then(|u| u.host_str().map(str::to_ascii_lowercase)).unwrap_or_default();
    if !page_host.is_empty() && (host == page_host || host.ends_with(&format!(".{page_host}")) || page_host.ends_with(&format!(".{host}"))) {
        return false;
    }
    let mut h = host.as_str();
    loop {
        if l.popups.contains(h) {
            return true;
        }
        match h.split_once('.') {
            Some((_, rest)) if rest.contains('.') => h = rest,
            _ => break,
        }
    }
    Request::new(url, if source.is_empty() { url } else { source }, "document", "GET").is_ok_and(|r| l.engine.check_network_request(&r).should_block())
}

/// Should the browser refuse this request? `kind` is the resource type
/// ("script", "image", "xmlhttprequest", "sub_frame", "other", …).
pub fn should_block(url: &str, source: &str, kind: &str) -> bool {
    let guard = ENGINE.read().unwrap_or_else(|p| p.into_inner());
    let Some(l) = guard.as_ref() else { return false };
    let Ok(req) = Request::new(url, source, kind, "GET") else { return false };
    l.engine.check_network_request(&req).should_block()
}

/// The ~1,000 generic element-hiding selectors that apply to every site
/// (complex ones like `[href*="…"]`). Injecting them into every page made the
/// browser re-match them on each change, which slows every site on a phone;
/// network blocking already removes the ads they target. Computed once per
/// filter set.
static GENERIC_MISC: RwLock<Option<(usize, HashSet<String>)>> = RwLock::new(None);

fn generic_misc(l: &Loaded) -> HashSet<String> {
    let key = l.updated as usize ^ l.rules;
    if let Some((k, set)) = GENERIC_MISC.read().unwrap_or_else(|p| p.into_inner()).as_ref() {
        if *k == key {
            return set.clone();
        }
    }
    let set: HashSet<String> = l.engine.url_cosmetic_resources("https://ku-generic.invalid/").hide_selectors;
    *GENERIC_MISC.write().unwrap_or_else(|p| p.into_inner()) = Some((key, set.clone()));
    set
}

/// Page-specific element hiding: CSS selectors, scriptlets, and whether
/// generic (class/id) rules apply. Only the site's own selectors are returned.
pub fn cosmetic(url: &str) -> Value {
    let guard = ENGINE.read().unwrap_or_else(|p| p.into_inner());
    let Some(l) = guard.as_ref() else { return json!({"hide": [], "script": "", "generic": false, "exceptions": []}) };
    let r = l.engine.url_cosmetic_resources(url);
    let generic = generic_misc(l);
    json!({
        "hide": r.hide_selectors.into_iter().filter(|s| !generic.contains(s)).collect::<Vec<_>>(),
        "script": r.injected_script,
        "generic": !r.generichide,
        "exceptions": r.exceptions.into_iter().collect::<Vec<_>>(),
    })
}

/// Generic hiding rules that apply to the classes and ids found on a page.
pub fn hidden(classes: &[String], ids: &[String], exceptions: &[String]) -> Vec<String> {
    let guard = ENGINE.read().unwrap_or_else(|p| p.into_inner());
    let Some(l) = guard.as_ref() else { return Vec::new() };
    let ex: HashSet<String> = exceptions.iter().cloned().collect();
    l.engine.hidden_class_id_selectors(classes, ids, &ex)
}

#[cfg(test)]
mod tests {
    use super::*;

    #[test]
    fn blocks_listed_requests_and_hides_elements() {
        let mut set = FilterSet::new(false);
        set.add_filter_list("||ads.example.com^\n@@||ads.example.com/allowed.js\nexample.org##.banner\n##.ad-box\n".into(), ParseOptions::default());
        let engine = Engine::new_with_filter_set(set);
        *ENGINE.write().unwrap() = Some(Loaded { engine, rules: 4, updated: 0, popups: HashSet::new(), stale: false });
        assert!(should_block("https://ads.example.com/x.js", "https://news.example.net/", "script"));
        assert!(!should_block("https://ads.example.com/allowed.js", "https://news.example.net/", "script"));
        assert!(!should_block("https://cdn.example.net/app.js", "https://news.example.net/", "script"));
        let c = cosmetic("https://example.org/page");
        assert!(c["hide"].as_array().unwrap().iter().any(|s| s == ".banner"));
        assert_eq!(hidden(&["ad-box".into()], &[], &[]), vec![".ad-box".to_string()]);
    }

    /// How much element hiding the real default lists inject into a page
    /// (`cargo test -p ku-android --release -- --ignored --nocapture cosmetic_size`).
    #[test]
    #[ignore = "downloads the filter lists"]
    fn cosmetic_size() {
        let rt = tokio::runtime::Runtime::new().unwrap();
        let mut set = FilterSet::new(false);
        for url in DEFAULT_LISTS {
            let text = rt.block_on(async { reqwest::get(*url).await?.text().await }).expect(url);
            println!("{url}: {} lines", text.lines().count());
            set.add_filter_list(text, ParseOptions::default());
        }
        let engine = Engine::new_with_filter_set(set);
        *ENGINE.write().unwrap() = Some(Loaded { engine, rules: 3, updated: 1, popups: HashSet::new(), stale: false });
        for page in ["https://www.youtube.com/", "https://m.facebook.com/", "https://www.bbc.com/news", "https://www.reddit.com/", "https://example.com/"] {
            let all = ENGINE.read().unwrap().as_ref().unwrap().engine.url_cosmetic_resources(page).hide_selectors.len();
            let sent = cosmetic(page)["hide"].as_array().unwrap().len();
            println!("{page}: {all} hide selectors in the lists, {sent} injected (site-specific)");
        }
    }
}

#[cfg(test)]
mod popup_tests {
    use super::*;

    // Its own engine, not the global one other tests swap.
    fn engine(rules: &str) -> Loaded {
        let texts = vec![rules.to_string()];
        let popups = popup_domains(&texts);
        let mut set = FilterSet::new(false);
        set.add_filter_list(rules.to_string(), ParseOptions::default());
        Loaded { engine: Engine::new_with_filter_set(set), rules: 3, updated: 0, popups, stale: false }
    }

    #[test]
    fn popups_and_redirects() {
        let l = engine("||popads.net^$popup,third-party\n||adsterra.com^\n||limited.com^$popup,domain=site.com\n");
        let should_block_popup = |u: &str, s: &str| popup_blocked(&l, u, s);
        // $popup rules (the engine ignores them) and their subdomains.
        assert!(should_block_popup("https://popads.net/x", "https://site.com/"));
        assert!(should_block_popup("https://c1.popads.net/x", "https://site.com/"));
        // Plain ad domains, as a whole page.
        assert!(should_block_popup("https://adsterra.com/x", "https://site.com/"));
        // Site-limited rules are left out; ordinary sites pass.
        assert!(!should_block_popup("https://limited.com/", "https://site.com/"));
        assert!(!should_block_popup("https://example.org/", "https://site.com/"));
        // Same site never blocked.
        assert!(!should_block_popup("https://m.popads.net/", "https://popads.net/"));
        assert!(!should_block_popup("not a url", "https://site.com/"));
    }
}
