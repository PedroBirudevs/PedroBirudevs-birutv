// Ponte entre o content.js (feito para extensão de navegador) e o app Android.
// Faz o papel do background.js: busca os emotes no 7TV pelo app (BiruBridge.fetch).
(() => {
  if (window.__stvChrome) return;

  const API = "https://7tv.io/v3";
  const ZERO_WIDTH = 1 << 8;
  const pending = {};
  let seq = 0;
  const memo = {};

  window.__stvFetchDone = (id, status, body) => {
    const p = pending[id];
    delete pending[id];
    if (p) p({ status, body });
  };

  function nativeFetch(url) {
    return new Promise((resolve) => {
      const id = ++seq;
      pending[id] = resolve;
      BiruBridge.fetch(id, url);
    });
  }

  async function getJSON(url) {
    if (memo[url]) return memo[url];
    const r = await nativeFetch(url);
    if (r.status === 404) return null;
    if (r.status < 200 || r.status >= 300) throw new Error("HTTP " + r.status);
    const json = JSON.parse(r.body);
    memo[url] = json;
    return json;
  }

  function compact(set) {
    if (!set || !Array.isArray(set.emotes)) return [];
    return set.emotes
      .filter((e) => e && e.name && e.data && e.data.host && e.data.host.url)
      .map((e) => {
        let url = e.data.host.url;
        if (url.startsWith("//")) url = "https:" + url;
        const files = (e.data.host.files || []).filter((f) => f.format === "WEBP" || /\.webp$/.test(f.name));
        const f1 = files.find((f) => f.name.startsWith("1x"));
        return {
          n: e.name,
          id: e.id,
          u: url,
          z: ((e.data.flags || 0) & ZERO_WIDTH) !== 0,
          w: f1 && f1.width && f1.height ? f1.width / f1.height : 1,
        };
      });
  }

  async function loadChannel(channelId) {
    if (!channelId) return { emotes: [], user: null };
    const conn = await getJSON(`${API}/users/youtube/${encodeURIComponent(channelId)}`);
    if (!conn) return { emotes: [], user: null };
    let set = conn.emote_set && Array.isArray(conn.emote_set.emotes) ? conn.emote_set : null;
    if (!set && conn.emote_set_id) set = await getJSON(`${API}/emote-sets/${conn.emote_set_id}`);
    const user = conn.user ? conn.user.display_name || conn.user.username : null;
    return { emotes: compact(set), user };
  }

  async function loadAll(msg) {
    const [global, channel, extra] = await Promise.all([
      getJSON(`${API}/emote-sets/global`).then(compact).catch(() => []),
      loadChannel(msg.channelId).catch(() => ({ emotes: [], user: null })),
      msg.extraSet ? getJSON(`${API}/emote-sets/${encodeURIComponent(msg.extraSet)}`).then(compact).catch(() => []) : Promise.resolve([]),
    ]);
    return { global, channel: channel.emotes, user: channel.user, extra };
  }

  let settings = {};
  try {
    settings = JSON.parse(BiruBridge.getSettings());
  } catch {}

  window.__stvChrome = {
    runtime: {
      lastError: null,
      getURL: () => "",
      sendMessage(msg, cb) {
        if (!msg || msg.type !== "stv:emotes") return cb && cb({});
        loadAll(msg)
          .then((res) => {
            try {
              // manda a lista pro app (autocompletar e seletor nativos), canal primeiro
              const list = [...res.channel, ...res.extra, ...res.global].map((e) => ({ n: e.n, u: e.u }));
              BiruBridge.onEmotes(JSON.stringify(list));
            } catch {}
            cb && cb(res);
          })
          .catch((err) => cb && cb({ error: String(err) }));
      },
    },
    storage: {
      sync: { get: async (defaults) => ({ ...defaults, ...settings }) },
      onChanged: { addListener() {} },
    },
  };
})();
