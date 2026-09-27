// KuDownloader site: small, dependency-free interactions.
(() => {
  "use strict";
  const REPO = "kuduyDigital/ku-download-manager";
  const reduced = matchMedia("(prefers-reduced-motion: reduce)").matches;
  const dark = () => matchMedia("(prefers-color-scheme: dark)").matches;
  const $ = (s, el = document) => el.querySelector(s);
  const $$ = (s, el = document) => [...el.querySelectorAll(s)];

  $$("[data-year]").forEach((y) => (y.textContent = String(new Date().getFullYear())));

  // ───────── Navigation shadow once the page moves ─────────
  const nav = $("[data-nav]");
  const onScrollNav = () => nav.classList.toggle("scrolled", scrollY > 8);
  addEventListener("scroll", onScrollNav, { passive: true });
  onScrollNav();

  // ───────── Reveal on scroll ─────────
  const io = new IntersectionObserver(
    (entries) => entries.forEach((e) => {
      if (e.isIntersecting) {
        e.target.classList.add("in");
        io.unobserve(e.target);
      }
    }),
    { rootMargin: "0px 0px -8% 0px" },
  );
  $$(".reveal").forEach((el, i) => {
    el.style.transitionDelay = `${Math.min(i % 4, 3) * 70}ms`;
    io.observe(el);
  });

  // ───────── Star field: twinkling stars in three depths, drifting with the
  // pointer, and now and then a shooting star ─────────
  function field(canvas) {
    const ctx = canvas.getContext("2d");
    const dense = canvas.hasAttribute("data-dense");
    let w = 0, h = 0, dpr = 1, stars = [], meteors = [], raf = 0, visible = true, nextMeteor = 0;
    const pointer = { x: 0, y: 0, tx: 0, ty: 0 };
    const palette = () => (dark() ? ["#ffffff", "#dbe7ff", "#a9c4ff", "#fff4d6"] : ["#1e3a8a", "#2563eb", "#475569", "#7c3aed"]);

    function build() {
      const r = canvas.getBoundingClientRect();
      dpr = Math.min(devicePixelRatio || 1, 2);
      w = r.width;
      h = r.height;
      canvas.width = w * dpr;
      canvas.height = h * dpr;
      ctx.setTransform(dpr, 0, 0, dpr, 0, 0);
      const colors = palette();
      const count = Math.min(Math.round((w * h) / (dense ? 2600 : 3200)), 700);
      stars = Array.from({ length: count }, () => {
        const depth = Math.random(); // 0 far … 1 near
        return {
          x: Math.random() * w,
          y: Math.random() * h,
          depth,
          r: 0.4 + depth * depth * 1.8,
          base: 0.25 + depth * 0.6,
          tw: 0.6 + Math.random() * 2.2, // twinkle speed
          phase: Math.random() * Math.PI * 2,
          color: colors[(Math.random() * colors.length) | 0],
        };
      });
      meteors = [];
    }

    function frame(now) {
      const t = now / 1000;
      pointer.x += (pointer.tx - pointer.x) * 0.05;
      pointer.y += (pointer.ty - pointer.y) * 0.05;
      ctx.clearRect(0, 0, w, h);
      const light = !dark();
      for (const s of stars) {
        // Near stars move more with the pointer (parallax) and drift slowly.
        const px = s.x + pointer.x * s.depth * 18 + t * s.depth * 3;
        const py = s.y + pointer.y * s.depth * 12;
        const x = ((px % w) + w) % w;
        const y = ((py % h) + h) % h;
        const a = s.base * (0.55 + 0.45 * Math.sin(t * s.tw + s.phase)) * (light ? 0.7 : 1);
        ctx.globalAlpha = a;
        ctx.fillStyle = s.color;
        ctx.beginPath();
        ctx.arc(x, y, s.r, 0, Math.PI * 2);
        ctx.fill();
        // The brightest near stars get a soft cross glint.
        if (s.r > 1.6 && a > 0.55) {
          ctx.globalAlpha = a * 0.35;
          ctx.fillRect(x - s.r * 3, y - 0.5, s.r * 6, 1);
          ctx.fillRect(x - 0.5, y - s.r * 3, 1, s.r * 6);
        }
      }
      // Shooting stars.
      if (!reduced && now > nextMeteor) {
        nextMeteor = now + 2500 + Math.random() * 4500;
        meteors.push({ x: Math.random() * w * 0.8 + w * 0.1, y: Math.random() * h * 0.35, vx: 7 + Math.random() * 4, vy: 3 + Math.random() * 2, life: 1 });
      }
      for (const m of meteors) {
        m.x += m.vx;
        m.y += m.vy;
        m.life -= 0.018;
        const g = ctx.createLinearGradient(m.x, m.y, m.x - m.vx * 14, m.y - m.vy * 14);
        const c = light ? "37, 99, 235" : "255, 255, 255";
        g.addColorStop(0, `rgba(${c}, ${Math.max(m.life, 0)})`);
        g.addColorStop(1, `rgba(${c}, 0)`);
        ctx.globalAlpha = 1;
        ctx.strokeStyle = g;
        ctx.lineWidth = 1.6;
        ctx.lineCap = "round";
        ctx.beginPath();
        ctx.moveTo(m.x, m.y);
        ctx.lineTo(m.x - m.vx * 14, m.y - m.vy * 14);
        ctx.stroke();
      }
      meteors = meteors.filter((m) => m.life > 0 && m.x < w + 200 && m.y < h + 200);
      ctx.globalAlpha = 1;
      if (visible && !reduced) raf = requestAnimationFrame(frame);
    }

    const host = canvas.parentElement;
    host.addEventListener("pointermove", (e) => {
      const r = canvas.getBoundingClientRect();
      pointer.tx = (e.clientX - r.left) / r.width - 0.5;
      pointer.ty = (e.clientY - r.top) / r.height - 0.5;
    });
    host.addEventListener("pointerleave", () => { pointer.tx = 0; pointer.ty = 0; });
    matchMedia("(prefers-color-scheme: dark)").addEventListener("change", build);
    new IntersectionObserver(([e]) => {
      visible = e.isIntersecting;
      cancelAnimationFrame(raf);
      if (visible) raf = requestAnimationFrame(frame);
    }).observe(canvas);
    new ResizeObserver(() => {
      build();
      if (reduced) frame(performance.now());
    }).observe(canvas);
    build();
    raf = requestAnimationFrame(frame);
  }
  $$("[data-field]").forEach(field);

  // ───────── Docs: highlight the section being read ─────────
  const tocLinks = $$(".toc a");
  const docSections = $$(".docs-body section[id]");
  if (tocLinks.length && docSections.length) {
    const spy = () => {
      // The last section whose top has passed a third of the way down the window.
      const line = innerHeight * 0.33;
      let current = docSections[0];
      for (const s of docSections) if (s.getBoundingClientRect().top <= line) current = s;
      tocLinks.forEach((a) => a.classList.toggle("on", a.getAttribute("href") === `#${current.id}`));
    };
    addEventListener("scroll", spy, { passive: true });
    addEventListener("resize", spy);
    spy();
  }
  // ───────── The product frame stands up as it scrolls into view ─────────
  const frameEl = $("[data-tilt]");
  if (frameEl && !reduced) {
    const tilt = () => {
      const r = frameEl.getBoundingClientRect();
      const p = Math.min(1, Math.max(0, (innerHeight - r.top) / (innerHeight * 0.9)));
      frameEl.style.setProperty("--tilt", `${(1 - p) * 20}deg`);
      frameEl.style.setProperty("--scale", `${0.9 + p * 0.1}`);
    };
    addEventListener("scroll", tilt, { passive: true });
    tilt();
  }

  // ───────── Statement: words light up with scroll ─────────
  const words = $("[data-words]");
  if (words) {
    words.innerHTML = words.textContent.trim().split(/\s+/).map((w) => `<span class="w">${w}</span>`).join(" ");
    const spans = $$(".w", words);
    const light = () => {
      const r = words.getBoundingClientRect();
      const p = Math.min(1, Math.max(0, (innerHeight * 0.85 - r.top) / (r.height + innerHeight * 0.35)));
      const n = Math.round(p * spans.length);
      spans.forEach((s, i) => s.classList.toggle("on", i < n));
    };
    if (reduced) spans.forEach((s) => s.classList.add("on"));
    else {
      addEventListener("scroll", light, { passive: true });
      light();
    }
  }

  // ───────── Cards: spotlight follows the pointer ─────────
  $$("[data-glow]").forEach((card) =>
    card.addEventListener("pointermove", (e) => {
      const r = card.getBoundingClientRect();
      card.style.setProperty("--mx", `${e.clientX - r.left}px`);
      card.style.setProperty("--my", `${e.clientY - r.top}px`);
    }),
  );

  // ───────── Features: pick one, the picture follows (and cycles on its own) ─────────
  const list = $("[data-features]");
  const shot = $("[data-feature-img]");
  if (list && shot) {
    const items = $$("li", list);
    let current = 0, timer = 0, userPicked = false;
    const src = (li) => (dark() && li.dataset.shotDark) || li.dataset.shot;
    const show = (i) => {
      current = i;
      items.forEach((li, j) => li.classList.toggle("active", j === i));
      const next = src(items[i]);
      if (shot.getAttribute("src") === next) return;
      shot.classList.add("swap");
      setTimeout(() => {
        shot.src = next;
        shot.onload = () => shot.classList.remove("swap");
      }, 220);
    };
    items.forEach((li, i) => {
      li.tabIndex = 0;
      li.addEventListener("click", () => { userPicked = true; show(i); });
      li.addEventListener("keydown", (e) => { if (e.key === "Enter" || e.key === " ") { e.preventDefault(); userPicked = true; show(i); } });
    });
    shot.src = src(items[0]);
    // Advance while the section is on screen, until the user chooses.
    new IntersectionObserver(([e]) => {
      clearInterval(timer);
      if (e.isIntersecting && !reduced) timer = setInterval(() => { if (!userPicked) show((current + 1) % items.length); }, 4200);
    }, { threshold: 0.4 }).observe(list);
  }

  // ───────── People rail arrows ─────────
  const rail = $("[data-rail]");
  $$("[data-scroll]").forEach((b) =>
    b.addEventListener("click", () => rail.scrollBy({ left: Number(b.dataset.scroll) * (rail.clientWidth * 0.8), behavior: reduced ? "auto" : "smooth" })),
  );

  // ───────── KuAirSend radar: the app's own pixel animals ─────────
  const SPRITES = {
    cat: { half: ["o.....", "oo....", "obo...", "oaoooo", "oaaaaa", "oakaaa", "oakaaa", "obaaan", "oaaaaa", ".oaaaa", "..oooo"], pal: { o: "#3b2314", a: "#f39c38", b: "#ffd9a8", k: "#1b1b1b", n: "#ff8fab" }, bg: "#fff0dc" },
    penguin: { half: ["..oooo", ".oaaaa", "oaaaaa", "oaawww", "oawkww", "oawwwn", "oawwww", "oawwww", ".oawww", "..oooo"], pal: { o: "#10131c", a: "#2c3a5a", w: "#f5f7fb", k: "#10131c", n: "#ffa726" }, bg: "#e1eafb" },
    fox: { half: ["o.....", "oo....", "obo...", "obbo..", "oaaooo", "oaaaaa", "oakaaa", "owaaaa", "owwaaa", ".owwwd", "..owww", "...ooo"], pal: { o: "#3a1f10", a: "#f06a24", b: "#ffc9a0", w: "#fff4e6", d: "#1b1b1b", k: "#1b1b1b" }, bg: "#ffe6d8" },
    frog: { half: [".ooo..", "owwwo.", "owkwoo", "owwwoa", "oaaaaa", "oaaaaa", "onaaaa", "oaoooo", ".oaaaa", "..oooo"], pal: { o: "#1d3b17", a: "#5cc15a", w: "#ffffff", k: "#1b1b1b", n: "#ff9fb5" }, bg: "#e2f6dd" },
  };
  $$("canvas[data-animal]").forEach((c) => {
    const s = SPRITES[c.dataset.animal];
    const rows = s.half.length;
    const size = 16;
    c.width = size;
    c.height = size;
    const g = c.getContext("2d");
    g.fillStyle = s.bg;
    g.fillRect(0, 0, size, size);
    const top = Math.floor((size - rows) / 2);
    s.half.forEach((row, y) => {
      [...(row + [...row].reverse().join(""))].forEach((ch, x) => {
        if (ch === ".") return;
        g.fillStyle = s.pal[ch] || "#f0f";
        g.fillRect(x + 2, y + top, 1, 1);
      });
    });
  });

  // ───────── Your system: label the buttons and link the right file ─────────
  function detectOs() {
    const ua = navigator.userAgent;
    const plat = (navigator.userAgentData && navigator.userAgentData.platform) || navigator.platform || "";
    if (/android/i.test(ua)) return "android";
    if (/iphone|ipad|ipod/i.test(ua)) return "ios";
    if (/win/i.test(plat) || /windows/i.test(ua)) return "windows";
    if (/mac/i.test(plat) || /mac os/i.test(ua)) return "mac";
    if (/linux|x11/i.test(plat + ua)) return "linux";
    return "";
  }
  const os = detectOs();
  const NAMES = { windows: "Windows", mac: "macOS", linux: "Linux", android: "Android" };
  if (NAMES[os]) {
    $$("[data-os-label] span").forEach((s) => (s.textContent = `Download for ${NAMES[os]}`));
    const card = $(`[data-platform="${os}"]`);
    if (card) {
      card.classList.add("mine");
      card.parentElement.prepend(card);
    }
  }
  $$("[data-platform-jump]").forEach((a) =>
    a.addEventListener("click", () => setTimeout(() => $(`[data-platform="${a.dataset.platformJump}"]`)?.classList.add("mine"), 400)),
  );

  // Direct links from the latest release (the release page stays the fallback).
  fetch(`https://api.github.com/repos/${REPO}/releases/latest`, { headers: { Accept: "application/vnd.github+json" } })
    .then((r) => (r.ok ? r.json() : Promise.reject(r.status)))
    .then((rel) => {
      const version = (rel.tag_name || "").replace(/^v/, "");
      const assets = rel.assets || [];
      const find = (suffix) => assets.find((a) => a.name.endsWith(suffix));
      $$("[data-asset]").forEach((a) => {
        const hit = find(a.dataset.asset);
        if (hit) a.href = hit.browser_download_url;
      });
      const primary = {
        windows: "_x64-setup.exe",
        mac: "_aarch64.dmg",
        linux: "_amd64.deb",
        android: "_android-arm64-v8a.apk",
      }[os];
      const hit = primary && find(primary);
      if (hit) $$("[data-os-label]").forEach((a) => (a.href = hit.browser_download_url));
      if (version) {
        const v = $("[data-version]");
        if (v) v.textContent = `Version ${version} · Windows · macOS · Linux · Android`;
        const vl = $("[data-version-long]");
        if (vl) vl.textContent = `Version ${version}. Free and open source. Pick your system.`;
      }
    })
    .catch(() => {});
})();
