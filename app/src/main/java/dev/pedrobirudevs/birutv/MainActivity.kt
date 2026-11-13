package dev.pedrobirudevs.birutv

import android.content.Intent
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.view.View
import android.widget.ImageView
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.swiperefreshlayout.widget.SwipeRefreshLayout
import com.google.android.material.button.MaterialButton
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.concurrent.Executors

/** Tela inicial: canal fixo, mostra se está ao vivo e leva para a live. */
class MainActivity : AppCompatActivity() {
    private val bg = Executors.newSingleThreadExecutor()
    private val handler = Handler(Looper.getMainLooper())
    private val refreshEvery = 60_000L
    private var loading = false
    private var current: ChannelStatus.Result? = null

    private lateinit var auth: GoogleAuth
    private lateinit var swipe: SwipeRefreshLayout
    private lateinit var avatar: ImageView
    private lateinit var pill: TextView
    private lateinit var liveCard: View
    private lateinit var offlineCard: View
    private lateinit var thumb: ImageView
    private lateinit var thumbBadge: TextView
    private lateinit var liveTitle: TextView
    private lateinit var liveSub: TextView
    private lateinit var enter: MaterialButton
    private lateinit var error: TextView
    private lateinit var checkedAt: TextView
    private lateinit var accountTitle: TextView
    private lateinit var accountSub: TextView
    private lateinit var loginBtn: MaterialButton

    private val autoRefresh = object : Runnable {
        override fun run() {
            refresh()
            handler.postDelayed(this, refreshEvery)
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)
        auth = GoogleAuth(this)

        swipe = findViewById(R.id.swipe)
        avatar = findViewById(R.id.avatar)
        pill = findViewById(R.id.status_pill)
        liveCard = findViewById(R.id.live_card)
        offlineCard = findViewById(R.id.offline_card)
        thumb = findViewById(R.id.thumb)
        thumbBadge = findViewById(R.id.thumb_badge)
        liveTitle = findViewById(R.id.live_title)
        liveSub = findViewById(R.id.live_sub)
        enter = findViewById(R.id.enter)
        error = findViewById(R.id.error)
        checkedAt = findViewById(R.id.checked_at)
        accountTitle = findViewById(R.id.account_title)
        accountSub = findViewById(R.id.account_sub)
        loginBtn = findViewById(R.id.login)

        findViewById<TextView>(R.id.channel_name).text = Channel.NAME
        ImageLoader.load(Prefs.avatar(this), avatar)

        swipe.setColorSchemeColors(getColor(R.color.accent))
        swipe.setProgressBackgroundColorSchemeColor(getColor(R.color.surface))
        swipe.setOnRefreshListener { refresh() }
        findViewById<View>(R.id.refresh).setOnClickListener { swipe.isRefreshing = true; refresh() }
        enter.setOnClickListener { openLive() }
        loginBtn.setOnClickListener { login() }

        updateAccount()
        // pega o token em silêncio se a pessoa já entrou antes
        if (Prefs.loggedIn(this)) auth.token(interactive = false) { t, _ -> if (t == null) Prefs.setLoggedIn(this, false); updateAccount() }
    }

    override fun onResume() {
        super.onResume()
        handler.post(autoRefresh)
    }

    override fun onPause() {
        super.onPause()
        handler.removeCallbacks(autoRefresh)
    }

    private fun refresh() {
        if (loading) return
        loading = true
        bg.execute {
            val result = runCatching { ChannelStatus.fetch() }
            runOnUiThread {
                loading = false
                swipe.isRefreshing = false
                result.onSuccess { show(it) }.onFailure {
                    error.text = "Sem conexão com o YouTube. Puxa para baixo para tentar de novo."
                    error.visibility = View.VISIBLE
                    if (current == null) pill.text = "Sem conexão"
                }
            }
        }
    }

    private fun show(r: ChannelStatus.Result) {
        current = r
        error.visibility = View.GONE
        checkedAt.text = "Atualizado às " + SimpleDateFormat("HH:mm", Locale.getDefault()).format(Date())
        r.avatarUrl?.let {
            if (it != Prefs.avatar(this)) { Prefs.setAvatar(this, it); ImageLoader.load(it, avatar) }
        }
        when (r.state) {
            ChannelStatus.State.LIVE -> {
                pill.text = "● AO VIVO"
                pill.setBackgroundResource(R.drawable.pill_live)
                thumbBadge.text = "● AO VIVO"
                thumbBadge.setBackgroundResource(R.drawable.pill_live)
                liveTitle.text = r.title ?: "Live do ${Channel.NAME}"
                liveSub.text = "Toque para assistir e comentar com os emotes."
                enter.text = "Entrar na live"
                enter.isEnabled = true
                ImageLoader.load("https://i.ytimg.com/vi/${r.videoId}/hqdefault_live.jpg", thumb)
                liveCard.visibility = View.VISIBLE
                offlineCard.visibility = View.GONE
            }
            ChannelStatus.State.UPCOMING -> {
                pill.text = "AGENDADA"
                pill.setBackgroundResource(R.drawable.pill_upcoming)
                thumbBadge.text = "AGENDADA"
                thumbBadge.setBackgroundResource(R.drawable.pill_upcoming)
                liveTitle.text = r.title ?: "Próxima live"
                liveSub.text = r.scheduledAt?.let {
                    "Começa " + SimpleDateFormat("dd/MM 'às' HH:mm", Locale.getDefault()).format(Date(it * 1000))
                } ?: "Live agendada"
                enter.text = "Abrir o chat da live agendada"
                enter.isEnabled = true
                ImageLoader.load("https://i.ytimg.com/vi/${r.videoId}/hqdefault.jpg", thumb)
                liveCard.visibility = View.VISIBLE
                offlineCard.visibility = View.GONE
            }
            ChannelStatus.State.OFFLINE -> {
                pill.text = "OFFLINE"
                pill.setBackgroundResource(R.drawable.pill_offline)
                liveCard.visibility = View.GONE
                offlineCard.visibility = View.VISIBLE
            }
        }
    }

    private fun openLive() {
        val r = current ?: return
        val id = r.videoId ?: return
        startActivity(
            Intent(this, LiveActivity::class.java)
                .putExtra(LiveActivity.EXTRA_VIDEO, id)
                .putExtra(LiveActivity.EXTRA_TITLE, r.title),
        )
    }

    private fun login() {
        if (Prefs.loggedIn(this)) {
            Toast.makeText(this, "Sua conta já está conectada.", Toast.LENGTH_SHORT).show()
            return
        }
        auth.token(interactive = true) { t, err ->
            if (t == null && err != null) Toast.makeText(this, err, Toast.LENGTH_LONG).show()
            updateAccount()
        }
    }

    private fun updateAccount() {
        if (Prefs.loggedIn(this)) {
            accountTitle.text = "Conta conectada"
            accountSub.text = "Você já pode comentar nas lives."
            loginBtn.text = "OK"
            loginBtn.isEnabled = false
        } else {
            accountTitle.text = "Comentar na live"
            accountSub.text = "Entre com a conta Google do seu YouTube."
            loginBtn.text = "Entrar"
            loginBtn.isEnabled = true
        }
    }

    override fun onDestroy() {
        handler.removeCallbacks(autoRefresh)
        bg.shutdown()
        super.onDestroy()
    }
}
