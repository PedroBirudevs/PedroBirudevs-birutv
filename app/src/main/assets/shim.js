// Ponte entre o content.js (feito para a extensão de navegador) e o app Android.
// Faz o papel do background.js: busca os emotes do 7TV pelo app (BiruBridge.fetch).
(() => {
  if (window.__stvChrome) return;

  const API = "https://7tv.io/v3";
  const MAIN_SET = "01FHBQ9GCG000BTBPFXYP13NFX"; // "Wallacetaa's Emotes"
  const ZERO_WIDTH = 1 << 8;
  const pending = {};
  let seq = 0;

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

  function compact(set) {
    return ((set && set.emotes) || [])
      .filter((e) => e && e.name && e.data && e.data.host && e.data.host.url)
      .map((e) => {
        const url = e.data.host.url.startsWith("//") ? "https:" + e.data.host.url : e.data.host.url;
        const f1 = (e.data.host.files || []).find((f) => f.name.startsWith("1x") && f.height);
        return { n: e.name, u: url, z: ((e.data.flags || 0) & ZERO_WIDTH) !== 0, w: f1 ? f1.width / f1.height : 1 };
      });
  }

  async function loadSet(id) {
    const r = await nativeFetch(`${API}/emote-sets/${encodeURIComponent(id)}`);
    if (r.status < 200 || r.status >= 300) return [];
    try {
      return compact(JSON.parse(r.body));
    } catch {
      return [];
    }
  }

  window.__stvChrome = {
    runtime: {
      lastError: null,
      sendMessage(msg, cb) {
        if (!msg || msg.type !== "stv:emotes") return cb && cb({});
        Promise.all([loadSet("global"), loadSet(MAIN_SET)]).then(([global, main]) => {
          try {
            // manda a lista pro app (sugestões e seletor nativos), Wallacetaa primeiro
            BiruBridge.onEmotes(JSON.stringify([...main, ...global].map((e) => ({ n: e.n, u: e.u }))));
          } catch {}
          cb && cb({ global, main, extra: [] });
        });
      },
    },
    storage: {
      sync: { get: async (defaults) => ({ ...defaults, ...JSON.parse(BiruBridge.getSettings()) }) },
      onChanged: { addListener() {} },
    },
  };
})();
