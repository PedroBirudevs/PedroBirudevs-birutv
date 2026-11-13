// Mostra na hora a mensagem que a pessoa mandou ("Você: ... enviando"),
// e some quando a mensagem de verdade chega no chat do YouTube.
(() => {
  if (window.__biruEcho) return;

  const box = document.createElement("div");
  box.id = "biru-echo";
  document.body.appendChild(box);

  const items = new Map(); // id -> { text, row, st }
  const norm = (s) => s.replace(/\s+/g, " ").trim();

  // texto da mensagem, contando emotes/emojis pelo nome (alt)
  function textOf(node) {
    let out = "";
    node.childNodes.forEach((n) => {
      if (n.nodeType === 3) out += n.nodeValue;
      else if (n.nodeType === 1) out += n.tagName === "IMG" ? n.getAttribute("alt") || "" : textOf(n);
    });
    return out;
  }

  function remove(id, delay) {
    const it = items.get(id);
    if (!it) return;
    items.delete(id);
    setTimeout(() => it.row.remove(), delay || 0);
  }

  window.__biruEcho = (id, text) => {
    const row = document.createElement("div");
    row.className = "biru-echo-row";
    const who = document.createElement("span");
    who.className = "biru-echo-who";
    who.textContent = "Você";
    const msg = document.createElement("span");
    msg.textContent = text;
    const st = document.createElement("span");
    st.className = "biru-echo-st";
    st.textContent = " · enviando";
    row.append(who, msg, st);
    box.appendChild(row);
    if (window.__stvRender) window.__stvRender(msg);
    items.set(id, { text: norm(text), row, st });
  };

  window.__biruEchoState = (id, state) => {
    const it = items.get(id);
    if (!it) return;
    if (state === "sent") {
      it.st.textContent = " · enviada ✓";
      setTimeout(() => remove(id, 0), 15000); // se o chat demorar, some sozinho
    } else {
      it.row.classList.add("fail");
      it.st.textContent = " · não enviada";
      remove(id, 4000);
    }
  };

  // quando a mensagem real aparecer no chat, tira a prévia
  // (confere as últimas mensagens a cada meio segundo, só enquanto tem prévia na tela)
  setInterval(() => {
    if (!items.size) return;
    const msgs = document.querySelectorAll("yt-live-chat-text-message-renderer #message");
    for (let i = Math.max(0, msgs.length - 15); i < msgs.length; i++) {
      const t = norm(textOf(msgs[i]));
      for (const [id, it] of items) if (it.text === t) remove(id, 0);
    }
  }, 500);
})();
