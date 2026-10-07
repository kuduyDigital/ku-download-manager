// KuDownloader's page script for the built-in browser (injected into every
// page). It draws the KuDownload button over videos, applies the ad blocker's
// element-hiding rules and collects links for Fetch Projects. It talks only to
// the app, through `KuBridge`, with a per-tab secret.
(function () {
  "use strict";
  if (window.__kuPage) return;
  window.__kuPage = true;
  var TOKEN = "__KU_TOKEN__";
  var PILL = __KU_PILL__;
  var LABEL = __KU_LABEL__;
  var ACCENT = "__KU_ACCENT__"; // the app's accent, "r,g,b"
  var bridge = window.KuBridge;
  if (!bridge) return;
  // Hide the bridge from the page where the browser allows it (it is
  // non-configurable on some WebViews; deleting it then throws in strict mode).
  try {
    delete window.KuBridge;
  } catch (e) {}
  // Runs in every frame, from the start of each document: movie and
  // streaming sites play inside an embedded player (an iframe from another
  // site). In frames only the video part runs.
  var TOP = window === window.top;

  // ───────── streams the player loads ─────────
  // Players behind blob: URLs (HLS/DASH via MediaSource) fetch a playlist
  // first. Its address is the downloadable one, so the app is told about it;
  // recognised by type, not only by name ("/master", "/playlist?id=…").
  var streams = {};
  function sawStream(url, type) {
    if (!url || typeof url !== "string" || url.indexOf("http") !== 0 || streams[url]) return;
    if (!/mpegurl|dash\+xml/i.test(type || "") && !/\.(m3u8|mpd)(?:[?#]|$)/i.test(url)) return;
    streams[url] = 1;
    try {
      bridge.stream(TOKEN, url, location.href);
    } catch (e) {}
  }
  try {
    var origFetch = window.fetch;
    if (origFetch) {
      window.fetch = function () {
        var p = origFetch.apply(this, arguments);
        p.then(function (r) {
          try {
            sawStream(r.url, r.headers.get("content-type"));
          } catch (e) {}
        }, function () {});
        return p;
      };
    }
    var xo = XMLHttpRequest.prototype.open;
    var xs = XMLHttpRequest.prototype.send;
    XMLHttpRequest.prototype.open = function (m, u) {
      this.__kuUrl = u;
      return xo.apply(this, arguments);
    };
    XMLHttpRequest.prototype.send = function () {
      var x = this;
      x.addEventListener("load", function () {
        try {
          sawStream(x.responseURL || String(x.__kuUrl || ""), x.getResponseHeader("content-type"));
        } catch (e) {}
      });
      return xs.apply(this, arguments);
    };
  } catch (e) {}

  // ───────── element hiding ─────────
  function style(id, css) {
    if (!css) return;
    var el = document.getElementById(id);
    if (!el) {
      el = document.createElement("style");
      el.id = id;
      (document.head || document.documentElement).appendChild(el);
    }
    el.textContent = css;
  }

  var generic = false;
  var exceptions = "[]";
  var seenClass = {};
  var seenId = {};

  // Runs when the page is idle, never more than every 5 s, and only looks at a
  // bounded number of elements: the native lookup is synchronous.
  var idle = window.requestIdleCallback || function (f) { return setTimeout(f, 200); };
  function scanGeneric() {
    if (!generic) return;
    var classes = [];
    var ids = [];
    var all = document.querySelectorAll("[class],[id]");
    for (var i = 0; i < all.length && i < 2500; i++) {
      var el = all[i];
      if (el.id && !seenId[el.id]) {
        seenId[el.id] = 1;
        ids.push(el.id);
      }
      var cl = el.classList;
      if (cl) {
        for (var j = 0; j < cl.length; j++) {
          if (!seenClass[cl[j]]) {
            seenClass[cl[j]] = 1;
            classes.push(cl[j]);
          }
        }
      }
    }
    if (!classes.length && !ids.length) return;
    if (classes.length > 800) classes.length = 800;
    var css = bridge.hidden(TOKEN, JSON.stringify({ classes: classes, ids: ids, exceptions: JSON.parse(exceptions) }));
    // Only the new rules are added (a small extra <style>): rewriting one
    // ever-growing sheet made the whole page restyle each time.
    if (css) {
      var el = document.createElement("style");
      el.className = "__ku_generic";
      el.textContent = css;
      (document.head || document.documentElement).appendChild(el);
    }
  }

  window.__kuCosmetic = function (css, isGeneric, ex) {
    style("__ku_hide", css);
    generic = isGeneric;
    exceptions = ex || "[]";
    idle(scanGeneric);
  };

  // ───────── links (Fetch Projects) ─────────
  window.__kuLinks = function () {
    var out = [];
    var seen = {};
    var push = function (u, text) {
      if (!u || seen[u] || !/^(https?|ftp|magnet):/i.test(u)) return;
      seen[u] = 1;
      out.push({ url: u, text: (text || "").trim().slice(0, 200) });
    };
    var a = document.querySelectorAll("a[href]");
    for (var i = 0; i < a.length; i++) push(a[i].href, a[i].textContent);
    var m = document.querySelectorAll("video[src],audio[src],source[src],img[src]");
    for (var k = 0; k < m.length; k++) push(m[k].src, m[k].alt || m[k].title);
    bridge.links(TOKEN, JSON.stringify(out.slice(0, 5000)));
  };

  // ───────── KuDownload button on videos ─────────
  var host = null;
  var btn = null;
  var target = null;

  function ensureButton() {
    if (btn) return;
    host = document.createElement("ku-download");
    host.style.cssText = "all:initial;position:fixed;z-index:2147483646;top:0;left:0;width:0;height:0;";
    var root = host.attachShadow ? host.attachShadow({ mode: "closed" }) : host;
    var css = document.createElement("style");
    css.textContent =
      // Frosted glass in the app's accent: the video shows through, blurred.
      ".b{position:fixed;display:none;align-items:center;gap:6px;height:34px;padding:0 14px;border-radius:17px;" +
      "background:linear-gradient(180deg,rgba(" + ACCENT + ",.62),rgba(" + ACCENT + ",.48));" +
      "-webkit-backdrop-filter:blur(14px) saturate(170%);backdrop-filter:blur(14px) saturate(170%);" +
      "border:1px solid rgba(255,255,255,.38);color:#fff;font:600 13px system-ui,-apple-system,Roboto,sans-serif;letter-spacing:.01em;" +
      "text-shadow:0 1px 2px rgba(0,0,0,.25);" +
      "box-shadow:0 6px 20px rgba(" + ACCENT + ",.45),inset 0 1px 0 rgba(255,255,255,.45);" +
      "touch-action:manipulation;-webkit-tap-highlight-color:transparent;transition:transform .12s,background .12s}" +
      ".b:active{transform:scale(.96);background:rgba(" + ACCENT + ",.78)}.b svg{width:16px;height:16px}";
    btn = document.createElement("button");
    btn.className = "b";
    btn.setAttribute("aria-label", LABEL);
    // Built node by node: sites with Trusted Types (YouTube) reject innerHTML.
    var NS = "http://www.w3.org/2000/svg";
    var svg = document.createElementNS(NS, "svg");
    svg.setAttribute("viewBox", "0 0 24 24");
    svg.setAttribute("fill", "none");
    svg.setAttribute("stroke", "currentColor");
    svg.setAttribute("stroke-width", "2.6");
    svg.setAttribute("stroke-linecap", "round");
    svg.setAttribute("stroke-linejoin", "round");
    [["M12 4v11"], ["M7 11l5 5 5-5"], ["M6 20h12", "#D4FF00"]].forEach(function (d) {
      var path = document.createElementNS(NS, "path");
      path.setAttribute("d", d[0]);
      if (d[1]) path.setAttribute("stroke", d[1]);
      svg.appendChild(path);
    });
    var label = document.createElement("span");
    label.textContent = LABEL;
    btn.appendChild(svg);
    btn.appendChild(label);
    btn.addEventListener(
      "click",
      function (e) {
        e.preventDefault();
        e.stopPropagation();
        var v = target;
        var src = v ? v.currentSrc || v.src || "" : "";
        if (src && src.indexOf("blob:") === 0) src = "";
        bridge.media(TOKEN, JSON.stringify({ src: src, page: location.href, title: document.title }));
      },
      true,
    );
    root.appendChild(css);
    root.appendChild(btn);
    (document.body || document.documentElement).appendChild(host);
  }

  function visible(v) {
    if (!TOP && innerWidth * innerHeight < 200 * 112) return null;
    var r = v.getBoundingClientRect();
    if (r.width < 160 || r.height < 90) return null;
    if (r.bottom < 40 || r.top > innerHeight - 40 || r.right < 40 || r.left > innerWidth - 40) return null;
    return r;
  }

  function pick() {
    var vids = document.getElementsByTagName("video");
    var best = null;
    var bestArea = 0;
    for (var i = 0; i < vids.length; i++) {
      var r = visible(vids[i]);
      if (!r) continue;
      var area = r.width * r.height;
      if (area > bestArea) {
        best = vids[i];
        bestArea = area;
      }
    }
    return best;
  }

  var reported = null;
  var timer = 0;
  function place() {
    timer = 0;
    var v = pick();
    // In frames only a player-sized, visible video counts (not ad slots).
    var has = TOP ? !!v || document.getElementsByTagName("video").length > 0 : !!v || null;
    if (has !== reported && has !== null) {
      reported = has;
      try {
        bridge.video(TOKEN, has);
      } catch (e) {}
    }
    if (!PILL) return;
    target = v;
    if (!v) {
      if (btn) btn.style.display = "none";
      return;
    }
    ensureButton();
    if (!host.isConnected) (document.body || document.documentElement).appendChild(host);
    var r = v.getBoundingClientRect();
    btn.style.display = "inline-flex";
    var w = btn.offsetWidth || 120;
    btn.style.top = Math.max(8, Math.min(innerHeight - 44, r.top + 10)) + "px";
    btn.style.left = Math.max(8, Math.min(innerWidth - w - 8, r.right - w - 10)) + "px";
  }
  // Coalesced: at most one placement per `wait` ms, after things settle.
  function schedule(wait) {
    if (timer) clearTimeout(timer);
    timer = setTimeout(function () { requestAnimationFrame(place); }, typeof wait === "number" ? wait : 150);
  }
  // While scrolling the button hides (no layout work per frame), and comes
  // back where the video is once the page stops.
  function onScroll() {
    if (btn && btn.style.display !== "none") btn.style.display = "none";
    schedule(180);
  }

  addEventListener("scroll", onScroll, { passive: true, capture: true });
  addEventListener("resize", schedule, { passive: true });
  document.addEventListener("fullscreenchange", schedule);
  document.addEventListener("loadedmetadata", schedule, true);
  document.addEventListener("play", schedule, true);

  // Busy sites (YouTube) change the page all the time: react at most every
  // 1 s for the button and every 5 s for element hiding.
  var lastMut = 0;
  var lastScan = 0;
  var mo = new MutationObserver(function () {
    var now = Date.now();
    if (now - lastMut > 1000) {
      lastMut = now;
      schedule(400);
    }
    if (generic && now - lastScan > 5000) {
      lastScan = now;
      idle(scanGeneric);
    }
  });
  function start() {
    mo.observe(document.documentElement, { childList: true, subtree: true });
    schedule();
  }
  if (document.documentElement) start();
  else document.addEventListener("readystatechange", start, { once: true });
})();
