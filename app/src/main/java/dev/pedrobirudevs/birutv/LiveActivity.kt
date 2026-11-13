package dev.pedrobirudevs.birutv

import android.annotation.SuppressLint
import android.content.Intent
import android.content.pm.ActivityInfo
import android.graphics.Color
import android.graphics.drawable.GradientDrawable
import android.net.Uri
import android.os.Bundle
import android.text.Editable
import android.text.TextWatcher
import android.view.View
import android.view.ViewGroup
import android.view.inputmethod.EditorInfo
import android.webkit.JavascriptInterface
import android.webkit.WebChromeClient
import android.webkit.WebResourceRequest
import android.webkit.WebView
import android.webkit.WebViewClient
import android.widget.EditText
import android.widget.FrameLayout
import android.widget.ImageButton
import android.widget.LinearLayout
import android.widget.TextView
import android.widget.Toast
import androidx.activity.OnBackPressedCallback
import androidx.appcompat.app.AppCompatActivity
import androidx.core.view.WindowCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.WindowInsetsControllerCompat
import com.google.android.material.bottomsheet.BottomSheetDialog
import org.json.JSONArray
import org.json.JSONObject
import java.util.concurrent.Executors

class LiveActivity : AppCompatActivity() {
    companion object {
        const val EXTRA_VIDEO = "video_id"
        const val EXTRA_TITLE = "title"
        private const val ORIGIN = "https://pedrobirudevs.github.io"
    }

    private lateinit var videoId: String
    private lateinit var player: WebView
    private lateinit var playerError: View
    private lateinit var chat: WebView
    private lateinit var input: EditText
    private lateinit var suggest: LinearLayout
    private lateinit var suggestScroll: View
    private lateinit var auth: GoogleAuth

    private val io = Executors.newCachedThreadPool()
    private val sender = Executors.newSingleThreadExecutor() // mantém a ordem das mensagens

    @Volatile private var emotes: List<Pair<String, String>> = emptyList() // nome -> url
    @Volatile private var liveChatId: String? = null
    @Volatile private var token: String? = null
    private var echoSeq = 0

    private var customView: View? = null
    private var customCallback: WebChromeClient.CustomViewCallback? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_live)
        requestedOrientation = ActivityInfo.SCREEN_ORIENTATION_PORTRAIT

        videoId = intent.getStringExtra(EXTRA_VIDEO) ?: run { finish(); return }
        auth = GoogleAuth(this)

        val playerBox = findViewById<FrameLayout>(R.id.player_box)
        player = findViewById(R.id.player)
        playerError = findViewById(R.id.player_error)
        chat = findViewById(R.id.chat)
        input = findViewById(R.id.input)
        suggest = findViewById(R.id.suggest)
        suggestScroll = findViewById(R.id.suggest_scroll)
        findViewById<TextView>(R.id.live_title).text = intent.getStringExtra(EXTRA_TITLE) ?: Channel.NAME

        // player em 16:9
        playerBox.layoutParams = playerBox.layoutParams.apply {
            height = resources.displayMetrics.widthPixels * 9 / 16
        }

        setupPlayer()
        setupChat()
        setupInput()
        prefetchSendInfo()

        findViewById<View>(R.id.back).setOnClickListener { finish() }
        findViewById<View>(R.id.open_youtube).setOnClickListener {
            startActivity(Intent(Intent.ACTION_VIEW, Uri.parse("https://www.youtube.com/watch?v=$videoId")))
        }
        findViewById<ImageButton>(R.id.emote_btn).setOnClickListener { openPicker() }

        onBackPressedDispatcher.addCallback(this, object : OnBackPressedCallback(true) {
            override fun handleOnBackPressed() {
                if (customView != null) exitFullscreen() else finish()
            }
        })
    }

    // ---------------- player ----------------

    @SuppressLint("SetJavaScriptEnabled")
    private fun setupPlayer() {
        player.settings.javaScriptEnabled = true
        player.settings.domStorageEnabled = true
        player.settings.mediaPlaybackRequiresUserGesture = false
        player.setBackgroundColor(Color.BLACK)
        player.addJavascriptInterface(PlayerBridge(), "BiruPlayer")
        player.webChromeClient = object : WebChromeClient() {
            override fun onShowCustomView(view: View, callback: CustomViewCallback) = enterFullscreen(view, callback)
            override fun onHideCustomView() = exitFullscreen()
        }
        val html = assetText("player.html").replace("{{VIDEO_ID}}", videoId).replace("{{ORIGIN}}", ORIGIN)
        player.loadDataWithBaseURL("$ORIGIN/", html, "text/html", "utf-8", null)
    }

    inner class PlayerBridge {
        @JavascriptInterface
        fun onError(code: Int) {
            // 101/150/152/153 = o dono não deixa tocar fora do YouTube
            runOnUiThread { playerError.visibility = View.VISIBLE }
        }
    }

    private fun enterFullscreen(view: View, callback: WebChromeClient.CustomViewCallback) {
        if (customView != null) {
            callback.onCustomViewHidden()
            return
        }
        customView = view
        customCallback = callback
        (window.decorView as FrameLayout).addView(
            view,
            FrameLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT),
        )
        requestedOrientation = ActivityInfo.SCREEN_ORIENTATION_SENSOR_LANDSCAPE
        WindowCompat.getInsetsController(window, window.decorView).apply {
            systemBarsBehavior = WindowInsetsControllerCompat.BEHAVIOR_SHOW_TRANSIENT_BARS_BY_SWIPE
            hide(WindowInsetsCompat.Type.systemBars())
        }
    }

    private fun exitFullscreen() {
        val view = customView ?: return
        (window.decorView as FrameLayout).removeView(view)
        customView = null
        customCallback?.onCustomViewHidden()
        customCallback = null
        requestedOrientation = ActivityInfo.SCREEN_ORIENTATION_PORTRAIT
        WindowCompat.getInsetsController(window, window.decorView).show(WindowInsetsCompat.Type.systemBars())
    }

    // ---------------- chat ----------------

    @SuppressLint("SetJavaScriptEnabled")
    private fun setupChat() {
        chat.settings.javaScriptEnabled = true
        chat.settings.domStorageEnabled = true
        // versão de computador do chat (é o layout que o script dos emotes conhece)
        chat.settings.userAgentString = Net.UA_DESKTOP
        chat.setBackgroundColor(Color.parseColor("#0F0F12"))
        chat.addJavascriptInterface(ChatBridge(), "BiruBridge")
        chat.webViewClient = object : WebViewClient() {
            override fun shouldOverrideUrlLoading(view: WebView, request: WebResourceRequest): Boolean {
                val url = request.url
                if (url.host == "www.youtube.com" && url.path.orEmpty().startsWith("/live_chat")) return false
                // links clicados no chat abrem fora do app
                runCatching { startActivity(Intent(Intent.ACTION_VIEW, url)) }
                return true
            }

            override fun onPageFinished(view: WebView, url: String?) {
                if (url?.contains("/live_chat") == true) injectChatScripts()
            }
        }
        chat.loadUrl("https://www.youtube.com/live_chat?v=$videoId&dark_theme=1")
    }

    private fun injectChatScripts() {
        val css = assetText("content.css") + "\n" + assetText("app.css")
        val js = buildString {
            append(assetText("shim.js"))
            append("\n;(function(chrome){\n")
            append(assetText("content.js"))
            append("\n})(window.__stvChrome);\n")
            append(assetText("echo.js"))
            append("\n(function(){if(document.getElementById('biru-css'))return;var s=document.createElement('style');s.id='biru-css';s.textContent=")
            append(JSONObject.quote(css))
            append(";document.documentElement.appendChild(s);})();")
        }
        chat.evaluateJavascript(js, null)
    }

    private fun chatJs(js: String) = chat.post { chat.evaluateJavascript(js, null) }

    inner class ChatBridge {
        @JavascriptInterface
        fun fetch(id: Int, url: String) {
            // a página do chat só pode pedir coisas da API do 7TV
            if (!url.startsWith("https://7tv.io/")) {
                done(id, 0, "")
                return
            }
            io.execute {
                val r = try { Net.request(url, userAgent = "BiruTV") } catch (e: Exception) { Net.Resp(0, "") }
                done(id, r.code, r.body)
            }
        }

        private fun done(id: Int, code: Int, body: String) =
            chatJs("window.__stvFetchDone && window.__stvFetchDone($id,$code,${JSONObject.quote(body)})")

        @JavascriptInterface
        fun getSettings(): String = Prefs.settingsJson()

        @JavascriptInterface
        fun onEmotes(json: String) {
            val arr = JSONArray(json)
            emotes = (0 until arr.length())
                .map { arr.getJSONObject(it) }
                .map { it.getString("n") to it.getString("u") }
                .distinctBy { it.first }
        }
    }

    // ---------------- campo de mensagem ----------------

    private fun setupInput() {
        findViewById<View>(R.id.send_btn).setOnClickListener { sendCurrent() }
        input.setOnEditorActionListener { _, actionId, _ ->
            if (actionId == EditorInfo.IME_ACTION_SEND) { sendCurrent(); true } else false
        }
        input.addTextChangedListener(object : TextWatcher {
            override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) {}
            override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) {}
            override fun afterTextChanged(s: Editable?) = updateSuggestions()
        })
    }

    private fun currentWord(): String? {
        val text = input.text.toString()
        val end = input.selectionEnd.coerceIn(0, text.length)
        return Regex("(\\S+)$").find(text.substring(0, end))?.groupValues?.get(1)
    }

    private fun updateSuggestions() {
        suggest.removeAllViews()
        val word = currentWord()
        val list = emotes
        if (word == null || word.length < 2 || list.isEmpty()) {
            suggestScroll.visibility = View.GONE
            return
        }
        val lw = word.lowercase()
        val starts = list.filter { it.first.lowercase().startsWith(lw) }
        val contains = list.filter { !it.first.lowercase().startsWith(lw) && it.first.lowercase().contains(lw) }
        val matches = (starts + contains).take(25)
        if (matches.isEmpty() || (matches.size == 1 && matches[0].first == word)) {
            suggestScroll.visibility = View.GONE
            return
        }
        val dp = resources.displayMetrics.density
        for ((name, _) in matches) {
            val chip = TextView(this).apply {
                text = name
                setTextColor(Color.WHITE)
                textSize = 14f
                setPadding((14 * dp).toInt(), (7 * dp).toInt(), (14 * dp).toInt(), (7 * dp).toInt())
                background = GradientDrawable().apply {
                    cornerRadius = 16 * dp
                    setColor(Color.parseColor("#2F2F35"))
                }
                setOnClickListener { replaceCurrentWord(name) }
            }
            val lp = LinearLayout.LayoutParams(ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT)
                .apply { marginEnd = (6 * dp).toInt() }
            suggest.addView(chip, lp)
        }
        suggestScroll.visibility = View.VISIBLE
    }

    private fun replaceCurrentWord(name: String) {
        val word = currentWord() ?: return
        val end = input.selectionEnd
        input.text.replace(end - word.length, end, "$name ")
    }

    private fun insertEmote(name: String) {
        val text = input.text
        val pos = input.selectionEnd.coerceIn(0, text.length)
        val before = if (pos > 0 && !text[pos - 1].isWhitespace()) " " else ""
        text.insert(pos, "$before$name ")
    }

    @SuppressLint("SetJavaScriptEnabled")
    private fun openPicker() {
        val list = emotes
        if (list.isEmpty()) {
            toast("Os emotes ainda estão carregando...")
            return
        }
        val dialog = BottomSheetDialog(this)
        val wv = WebView(this)
        wv.settings.javaScriptEnabled = true
        wv.setBackgroundColor(Color.parseColor("#1F1F23"))
        wv.addJavascriptInterface(PickerBridge(dialog), "Picker")
        val json = JSONArray(list.map { JSONObject().put("n", it.first).put("u", it.second) }).toString()
        wv.webViewClient = object : WebViewClient() {
            override fun onPageFinished(view: WebView, url: String?) {
                view.evaluateJavascript("render($json)", null)
            }
        }
        wv.loadDataWithBaseURL("$ORIGIN/", assetText("picker.html"), "text/html", "utf-8", null)
        dialog.setContentView(
            wv,
            ViewGroup.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, (resources.displayMetrics.heightPixels * 0.6).toInt()),
        )
        dialog.setOnDismissListener { wv.destroy() }
        dialog.show()
    }

    inner class PickerBridge(private val dialog: BottomSheetDialog) {
        @JavascriptInterface
        fun pick(name: String) {
            runOnUiThread {
                insertEmote(name)
                dialog.dismiss()
            }
        }
    }

    // ---------------- enviar ----------------

    /** Já deixa pronto o login e o ID do chat, para a primeira mensagem sair rápido. */
    private fun prefetchSendInfo() {
        auth.token(interactive = false) { t, _ ->
            if (t == null) return@token
            token = t
            sender.execute { runCatching { liveChatId = YouTubeApi.liveChatId(videoId, t) } }
        }
    }

    private fun sendCurrent() {
        val text = input.text.toString().trim()
        if (text.isEmpty()) return
        withToken { tok ->
            // aparece na hora no chat e o campo já fica livre
            input.text.clear()
            val id = ++echoSeq
            chatJs("window.__biruEcho && __biruEcho($id, ${JSONObject.quote(text)})")
            sender.execute { deliver(text, id, tok, retried = false) }
        }
    }

    /** Roda na thread de envio. */
    private fun deliver(text: String, id: Int, tok: String, retried: Boolean) {
        try {
            val chatId = liveChatId ?: YouTubeApi.liveChatId(videoId, tok).also { liveChatId = it }
            YouTubeApi.send(chatId, text, tok)
            chatJs("window.__biruEchoState && __biruEchoState($id, 'sent')")
        } catch (e: YouTubeApi.ApiError) {
            if (e.code == 401 && !retried) {
                GoogleAuth.clearCache()
                token = null
                runOnUiThread { withToken { t -> sender.execute { deliver(text, id, t, retried = true) } } }
                return
            }
            failed(text, id, e.message ?: "Erro ao enviar")
        } catch (e: Exception) {
            failed(text, id, "Sem conexão. Tenta de novo.")
        }
    }

    private fun failed(text: String, id: Int, message: String) {
        chatJs("window.__biruEchoState && __biruEchoState($id, 'fail')")
        runOnUiThread {
            toast(message)
            // devolve o texto para a pessoa tentar de novo
            if (input.text.isEmpty()) {
                input.setText(text)
                input.setSelection(text.length)
            }
        }
    }

    private fun withToken(cb: (String) -> Unit) {
        token?.let { cb(it); return }
        auth.token(interactive = true) { t, err ->
            if (t != null) {
                token = t
                cb(t)
            } else if (err != null) {
                toast(err)
            }
        }
    }

    // ---------------- util ----------------

    private val assetCache = HashMap<String, String>()
    private fun assetText(name: String): String =
        assetCache.getOrPut(name) { assets.open(name).bufferedReader().use { it.readText() } }

    private fun toast(msg: String) = Toast.makeText(this, msg, Toast.LENGTH_LONG).show()

    override fun onDestroy() {
        if (::chat.isInitialized) chat.destroy()
        if (::player.isInitialized) player.destroy()
        io.shutdown()
        sender.shutdown()
        super.onDestroy()
    }
}
