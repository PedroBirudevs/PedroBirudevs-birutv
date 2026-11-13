// Roda dentro do iframe do chat ao vivo do YouTube (youtube.com/live_chat).
// Pede os emotes ao background, troca as palavras das mensagens pelos emotes,
// Tab autocompleta e o botão abre o seletor de emotes.

(() => {
  if (window.__stvLoaded) return;
  window.__stvLoaded = true;

  const DEFAULTS = { enabled: true, size: 28, extraSet: "", tooltips: true };
  let settings = { ...DEFAULTS };
  let emoteMap = new Map(); // nome -> emote
  let emoteList = []; // para autocompletar / seletor (Wallacetaa primeiro)

  const MSG_SELECTOR = [
    "yt-live-chat-text-message-renderer #message",
    "yt-live-chat-paid-message-renderer #message",
    "yt-live-chat-membership-item-renderer #message",
    "yt-live-chat-item-list-renderer #items [id=message]",
  ].join(",");
  // cria elemento sem innerHTML (o YouTube bloqueia innerHTML com Trusted Types)
  function el(tag, props = {}, ...children) {
    const e = Object.assign(document.createElement(tag), props);
    e.append(...children);
    return e;
  }
  // ícone desenhado em SVG (o YouTube bloqueia imagens data: e chrome-extension: no chat)
  function iconSvg() {
    const NS = "http://www.w3.org/2000/svg";
    const svg = document.createElementNS(NS, "svg");
    svg.setAttribute("viewBox", "0 0 24 24");
    svg.setAttribute("width", "22");
    svg.setAttribute("height", "22");
    const parts = [
      ["circle", { cx: 12, cy: 12, r: 10, fill: "#ffc43d" }],
      ["path", { d: "M7.6 9.6l2.2 1.1-2.2 1.1M16.4 9.6l-2.2 1.1 2.2 1.1", stroke: "#241f33", "stroke-width": 1.4, fill: "none", "stroke-linecap": "round", "stroke-linejoin": "round" }],
      ["path", { d: "M7.8 13.6Q12 19 16.2 13.6Z", fill: "#241f33" }],
    ];
    for (const [tag, attrs] of parts) {
      const node = document.createElementNS(NS, tag);
      for (const k in attrs) node.setAttribute(k, attrs[k]);
      svg.appendChild(node);
    }
    return svg;
  }
  const INPUT_SELECTOR = "yt-live-chat-text-input-field-renderer #input";

  // ---------- carregar emotes ----------

  function sendMessage(msg) {
    return new Promise((resolve) => {
      try {
        chrome.runtime.sendMessage(msg, (res) => resolve(chrome.runtime.lastError ? null : res));
      } catch {
        resolve(null);
      }
    });
  }

  async function loadEmotes() {
    const res = await sendMessage({ type: "stv:emotes", extraSet: settings.extraSet.trim() });
    if (!res || res.error) {
      toast("7TV: não consegui carregar os emotes" + (res && res.error ? ` (${res.error})` : ""));
      return;
    }
    const map = new Map();
    const tag = (arr, scope) => arr.forEach((e) => map.set(e.n, { ...e, s: scope }));
    tag(res.global, "Global");
    tag(res.extra, "Set extra");
    tag(res.main, "Wallacetaa");
    emoteMap = map;
    const rank = { Wallacetaa: 0, "Set extra": 1, Global: 2 };
    emoteList = [...map.values()].sort((a, b) => rank[a.s] - rank[b.s] || a.n.localeCompare(b.n));

    const parts = [`${res.main.length} do Wallacetaa`, `${res.global.length} globais`];
    if (res.extra.length) parts.push(`${res.extra.length} do set extra`);
    const sample = emoteList.filter((e) => /^[A-Za-z]{2,10}$/.test(e.n)).slice(0, 4).map((e) => e.n);
    toast("7TV: " + parts.join(" · ") + (sample.length ? ` — teste: ${sample.join(", ")}` : ""), 12000);
    console.log("[7TV-YT] emotes:", map.size, "| exemplos:", sample.join(", "));

    document.querySelectorAll(MSG_SELECTOR).forEach(queue);
  }

  // ---------- renderizar emotes nas mensagens ----------

  function emoteEl(e) {
    const wrap = document.createElement("span");
    wrap.className = "stv-emote";
    wrap.dataset.stvName = e.n;
    wrap.appendChild(emoteImg(e));
    return wrap;
  }

  function emoteImg(e) {
    const img = document.createElement("img");
    img.src = `${e.u}/1x.webp`;
    img.srcset = `${e.u}/1x.webp 1x, ${e.u}/2x.webp 2x, ${e.u}/3x.webp 3x`;
    img.alt = e.n;
    img.loading = "lazy";
    img.decoding = "async";
    img.draggable = false;
    img.dataset.stvName = e.n;
    return img;
  }

  function processMessage(msgEl) {
    if (!settings.enabled || !emoteMap.size) return;
    const walker = document.createTreeWalker(msgEl, NodeFilter.SHOW_TEXT, {
      acceptNode: (n) => (n.parentElement && n.parentElement.closest(".stv-emote") ? NodeFilter.FILTER_REJECT : NodeFilter.FILTER_ACCEPT),
    });
    const textNodes = [];
    while (walker.nextNode()) textNodes.push(walker.currentNode);

    let lastEmote = null; // para emotes zero-width (sobrepostos)
    for (const node of textNodes) {
      const tokens = node.nodeValue.split(/(\s+)/);
      if (!tokens.some((t) => emoteMap.has(t))) {
        if (node.nodeValue.trim()) lastEmote = null;
        continue;
      }
      const frag = document.createDocumentFragment();
      let buf = "";
      const flush = () => {
        if (buf) frag.appendChild(document.createTextNode(buf));
        buf = "";
      };
      for (const t of tokens) {
        const e = t && emoteMap.get(t);
        if (!e) {
          if (t.trim()) lastEmote = null;
          buf += t;
          continue;
        }
        if (e.z && lastEmote) {
          const zw = emoteImg(e);
          zw.classList.add("stv-zw");
          lastEmote.appendChild(zw);
          lastEmote.dataset.stvName += " " + e.n;
          // tira o espaço que separava o zero-width do emote base
          buf = buf.replace(/\s+$/, "");
          continue;
        }
        flush();
        lastEmote = emoteEl(e);
        frag.appendChild(lastEmote);
      }
      flush();
      node.replaceWith(frag);
    }
  }

  // usado pelo app (prévia da mensagem enviada)
  window.__stvRender = (el) => processMessage(el);

  const pending = new Set();
  let scheduled = false;
  function queue(msgEl) {
    pending.add(msgEl);
    if (scheduled) return;
    scheduled = true;
    requestAnimationFrame(() => {
      scheduled = false;
      const items = [...pending];
      pending.clear();
      items.forEach((el) => el.isConnected && processMessage(el));
    });
  }

  const observer = new MutationObserver((mutations) => {
    for (const m of mutations) {
      const target = m.target.nodeType === 1 ? m.target : m.target.parentElement;
      const inMsg = target && target.closest && target.closest(MSG_SELECTOR);
      if (inMsg) queue(inMsg);
      for (const n of m.addedNodes) {
        if (n.nodeType !== 1) continue;
        if (n.matches(MSG_SELECTOR)) queue(n);
        n.querySelectorAll && n.querySelectorAll(MSG_SELECTOR).forEach(queue);
      }
    }
  });

  // ---------- tooltip ----------

  let tip = null;
  function showTip(target) {
    const names = (target.dataset.stvName || "").split(" ");
    const e = emoteMap.get(names[0]);
    if (!e || !settings.tooltips) return;
    if (!tip) {
      tip = document.createElement("div");
      tip.className = "stv-tooltip";
      document.body.appendChild(tip);
    }
    tip.replaceChildren();
    const img = document.createElement("img");
    img.src = `${e.u}/4x.webp`;
    const name = document.createElement("div");
    name.className = "stv-tip-name";
    name.textContent = names.join(" + ");
    const scope = document.createElement("div");
    scope.className = "stv-tip-scope";
    scope.textContent = `Emote 7TV · ${e.s}`;
    tip.append(img, name, scope);
    const r = target.getBoundingClientRect();
    tip.style.display = "block";
    const tr = tip.getBoundingClientRect();
    let left = r.left + r.width / 2 - tr.width / 2;
    left = Math.max(4, Math.min(left, window.innerWidth - tr.width - 4));
    let top = r.top - tr.height - 6;
    if (top < 4) top = r.bottom + 6;
    tip.style.left = `${left}px`;
    tip.style.top = `${top}px`;
  }
  function hideTip() {
    if (tip) tip.style.display = "none";
  }
  document.addEventListener("mouseover", (ev) => {
    const t = ev.target.closest && ev.target.closest(".stv-emote, .stv-picker-item");
    if (t) showTip(t);
  });
  document.addEventListener("mouseout", (ev) => {
    const t = ev.target.closest && ev.target.closest(".stv-emote, .stv-picker-item");
    if (t && !t.contains(ev.relatedTarget)) hideTip();
  });

  // ---------- escrever no campo do chat ----------

  function getInput() {
    return document.querySelector(INPUT_SELECTOR);
  }

  function insertAtCaret(text) {
    const input = getInput();
    if (!input) return;
    input.focus();
    const sel = window.getSelection();
    if (!sel.rangeCount || !input.contains(sel.anchorNode)) {
      const r = document.createRange();
      r.selectNodeContents(input);
      r.collapse(false);
      sel.removeAllRanges();
      sel.addRange(r);
    }
    // espaço antes se a última coisa não for espaço
    const before = textBeforeCaret(input);
    const prefix = before && !/\s$/.test(before) ? " " : "";
    document.execCommand("insertText", false, prefix + text + " ");
  }

  function textBeforeCaret(input) {
    const sel = window.getSelection();
    if (!sel.rangeCount) return "";
    const r = sel.getRangeAt(0).cloneRange();
    r.setStart(input, 0);
    return r.toString();
  }

  // ---------- Tab para autocompletar ----------

  let tabState = null; // { candidates, idx, inserted }

  document.addEventListener(
    "keydown",
    (ev) => {
      const input = getInput();
      if (!input || !input.contains(ev.target)) return;
      if (ev.key !== "Tab" || ev.shiftKey && !tabState) {
        if (ev.key !== "Shift") tabState = null;
        return;
      }
      if (!settings.enabled || !emoteList.length) return;

      const sel = window.getSelection();
      if (!sel.rangeCount || !sel.isCollapsed) return;
      const node = sel.anchorNode;
      if (!node || node.nodeType !== 3) return;
      const before = node.nodeValue.slice(0, sel.anchorOffset);

      if (tabState && before.endsWith(tabState.inserted)) {
        tabState.idx = (tabState.idx + (ev.shiftKey ? -1 : 1) + tabState.candidates.length) % tabState.candidates.length;
      } else {
        const word = (before.match(/(\S+)$/) || [])[1];
        if (!word) return;
        const lw = word.toLowerCase();
        const starts = emoteList.filter((e) => e.n.toLowerCase().startsWith(lw));
        const contains = emoteList.filter((e) => !e.n.toLowerCase().startsWith(lw) && e.n.toLowerCase().includes(lw));
        const candidates = [...starts, ...contains].map((e) => e.n);
        if (!candidates.length) return;
        tabState = { candidates, idx: 0, inserted: word };
      }

      ev.preventDefault();
      ev.stopPropagation();
      const replaceLen = tabState.inserted.length;
      const r = document.createRange();
      r.setStart(node, sel.anchorOffset - replaceLen);
      r.setEnd(node, sel.anchorOffset);
      sel.removeAllRanges();
      sel.addRange(r);
      const next = tabState.candidates[tabState.idx];
      document.execCommand("insertText", false, next);
      tabState.inserted = next;
    },
    true
  );

  // ---------- seletor de emotes ----------

  let picker = null;
  function buildPicker() {
    picker = el(
      "div",
      { className: "stv-picker" },
      el(
        "div",
        { className: "stv-picker-head" },
        el("input", { className: "stv-picker-search", type: "text", placeholder: "Buscar emote 7TV..." }),
        el("button", { className: "stv-picker-close", title: "Fechar", textContent: "✕" })
      ),
      el("div", { className: "stv-picker-body" })
    );
    document.body.appendChild(picker);
    const search = picker.querySelector(".stv-picker-search");
    search.addEventListener("input", () => renderPicker(search.value));
    search.addEventListener("keydown", (ev) => {
      ev.stopPropagation();
      if (ev.key === "Escape") togglePicker(false);
      if (ev.key === "Enter") {
        const first = picker.querySelector(".stv-picker-item");
        if (first) first.click();
      }
    });
    picker.querySelector(".stv-picker-close").addEventListener("click", () => togglePicker(false));
    picker.querySelector(".stv-picker-body").addEventListener("click", (ev) => {
      const item = ev.target.closest(".stv-picker-item");
      if (!item) return;
      insertAtCaret(item.dataset.stvName);
      if (!ev.shiftKey) togglePicker(false);
    });
  }

  function renderPicker(q) {
    const body = picker.querySelector(".stv-picker-body");
    body.replaceChildren();
    const lq = (q || "").toLowerCase();
    const groups = new Map();
    for (const e of emoteList) {
      if (lq && !e.n.toLowerCase().includes(lq)) continue;
      if (!groups.has(e.s)) groups.set(e.s, []);
      groups.get(e.s).push(e);
    }
    if (!groups.size) {
      body.textContent = emoteList.length ? "Nenhum emote encontrado." : "Nenhum emote carregado.";
      return;
    }
    for (const [scope, list] of groups) {
      const h = document.createElement("div");
      h.className = "stv-picker-group";
      h.textContent = `${scope} (${list.length})`;
      const grid = document.createElement("div");
      grid.className = "stv-picker-grid";
      for (const e of list.slice(0, 400)) {
        const b = document.createElement("button");
        b.className = "stv-picker-item";
        b.dataset.stvName = e.n;
        const img = document.createElement("img");
        img.src = `${e.u}/2x.webp`;
        img.loading = "lazy";
        img.alt = e.n;
        b.appendChild(img);
        grid.appendChild(b);
      }
      body.append(h, grid);
    }
  }

  function togglePicker(force) {
    if (!picker) buildPicker();
    const open = force !== undefined ? force : !picker.classList.contains("open");
    picker.classList.toggle("open", open);
    hideTip();
    if (open) {
      const search = picker.querySelector(".stv-picker-search");
      search.value = "";
      renderPicker("");
      setTimeout(() => search.focus(), 0);
    }
  }

  function ensureButton() {
    if (document.querySelector(".stv-picker-btn")) return;
    const input = getInput();
    if (!input) return; // sem campo (deslogado / chat de replay)
    const btn = document.createElement("button");
    btn.className = "stv-picker-btn";
    btn.title = "Emotes 7TV";
    btn.appendChild(iconSvg());
    btn.addEventListener("click", (ev) => {
      ev.preventDefault();
      togglePicker();
    });
    const slot =
      document.querySelector("yt-live-chat-message-input-renderer #picker-buttons") ||
      document.querySelector("yt-live-chat-message-input-renderer #buttons");
    if (slot) {
      slot.prepend(btn);
    } else {
      btn.classList.add("stv-floating");
      document.body.appendChild(btn);
    }
  }

  // ---------- aviso rápido ----------

  function toast(text, ms = 4500) {
    const t = document.createElement("div");
    t.className = "stv-toast";
    t.textContent = text;
    document.body.appendChild(t);
    setTimeout(() => t.classList.add("hide"), ms);
    setTimeout(() => t.remove(), ms + 700);
  }

  // ---------- configurações ----------

  function applySize() {
    document.documentElement.style.setProperty("--stv-size", `${settings.size}px`);
  }

  chrome.storage.onChanged.addListener((changes, area) => {
    if (area !== "sync") return;
    const old = { ...settings };
    for (const k of Object.keys(changes)) if (k in DEFAULTS) settings[k] = changes[k].newValue ?? DEFAULTS[k];
    applySize();
    if (old.extraSet !== settings.extraSet || changes.enabled) loadEmotes();
  });

  // ---------- início ----------

  async function start() {
    settings = { ...DEFAULTS, ...(await chrome.storage.sync.get(DEFAULTS)) };
    applySize();
    observer.observe(document.body, { childList: true, subtree: true, characterData: true });
    setInterval(ensureButton, 2000);
    ensureButton();
    await loadEmotes();
  }

  start();
})();
