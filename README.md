# BiruTV

App Android para assistir lives do YouTube com os **emotes do 7TV** no chat, no estilo do Purple TV, mas feito do zero. Não é o app do YouTube modificado: usa o player oficial e a API oficial do YouTube.

- Player oficial do YouTube (tela cheia deitando o celular)
- Chat ao vivo com emotes do 7TV (globais + do canal + set extra opcional)
- Mandar mensagem entrando com a conta Google (tela oficial do Google)
- Sugestões de emote enquanto digita e botão com todos os emotes
- "Compartilhar → BiruTV" direto do app do YouTube
- Aceita link da live, ID do vídeo ou `@canal` (abre a live atual do canal)

> App não oficial. Sem ligação com YouTube, Google ou 7TV.

---

## 1. Subir no GitHub e gerar o APK

1. Crie um repositório no GitHub (este: `PedroBirudevs/pedrobirudevs-birutv`).
2. Envie todos os arquivos deste projeto, **incluindo a pasta `.github`**.
3. No repositório: **Settings → Secrets and variables → Actions → New repository secret**. Crie os dois secrets que estão no arquivo `birutv-chaves.txt`:
   - `KEYSTORE_PASSWORD`
   - `KEYSTORE_BASE64`
4. Vá na aba **Actions**. A cada envio na `main` o GitHub gera o APK sozinho (leva uns 5 minutos). Para rodar na mão: **Gerar APK → Run workflow**.
5. O APK aparece em **Releases**. Link fixo para sempre baixar a última versão (dá para colocar no site):

   `https://github.com/PedroBirudevs/pedrobirudevs-birutv/releases/latest/download/BiruTV.apk`

Sem os secrets o APK é gerado mesmo assim, mas o login do Google não funciona.

## 2. Liberar o login do Google (para mandar mensagem)

Assistir e ver os emotes funciona sem nada disso. Isso aqui só é preciso para **mandar mensagem**.

1. Acesse https://console.cloud.google.com e crie um projeto (ex.: "BiruTV").
2. **APIs e serviços → Biblioteca** → procure **YouTube Data API v3** → **Ativar**.
3. **APIs e serviços → Tela de consentimento OAuth** (ou "Google Auth Platform"):
   - Tipo de usuário: **Externo**
   - Nome do app: BiruTV, seu e-mail de suporte e de contato
   - Em **Escopos/Acesso a dados**, adicione: `https://www.googleapis.com/auth/youtube.force-ssl`
   - Em **Público-alvo → Usuários de teste**, adicione os e-mails de quem vai testar (até 100)
4. **Credenciais → Criar credenciais → ID do cliente OAuth → Android**:
   - Nome do pacote: `dev.pedrobirudevs.birutv`
   - Impressão digital SHA-1: está no `birutv-chaves.txt`
5. Pronto. Não precisa colocar nenhuma chave dentro do app: o Google reconhece o app pelo pacote + SHA-1.

Enquanto o app estiver em modo de teste, só os e-mails cadastrados como usuários de teste conseguem entrar, e o Google mostra o aviso "o Google não verificou este app" (é só tocar em **Continuar**). Para liberar para qualquer pessoa, é preciso pedir a verificação do app ao Google.

### Limite da API
O Google dá **10.000 unidades por dia** de graça para o projeto inteiro. Cada mensagem enviada custa ~50 unidades, então dá umas **200 mensagens por dia somando todo mundo**. Ver o chat e os emotes **não gasta** nada da cota. Se muita gente for usar, dá para pedir aumento de cota no Google Cloud.

## 3. Instalar no celular

1. Baixe o `BiruTV.apk` no celular.
2. Abra o arquivo. O Android vai pedir para permitir instalar de "fontes desconhecidas" para o navegador/gerenciador de arquivos: permita.
3. Abra o BiruTV, cole o link da live ou o `@canal` e toque em **Assistir**.

Dica: na live, no app do YouTube, toque em **Compartilhar → BiruTV**.

## Como funciona por dentro

- `LiveActivity.kt`: tela da live. Em cima, o player oficial do YouTube (IFrame API). Embaixo, o chat ao vivo do YouTube (`youtube.com/live_chat`) com o mesmo script da extensão de navegador injetado (`assets/content.js`), que troca os nomes pelos emotes.
- `assets/shim.js`: ponte entre o script da extensão e o app. O app busca os emotes na API do 7TV e entrega para o chat.
- `YouTubeApi.kt` + `GoogleAuth.kt`: login e envio de mensagens pela YouTube Data API v3.
- Algumas lives não deixam tocar o vídeo fora do YouTube; nesse caso aparece um botão para abrir o vídeo no app do YouTube e o chat com emotes continua funcionando.
