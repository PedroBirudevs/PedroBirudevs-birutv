package dev.pedrobirudevs.birutv

import android.content.Intent
import android.os.Bundle
import android.view.inputmethod.EditorInfo
import android.widget.EditText
import android.widget.TextView
import androidx.appcompat.app.AppCompatActivity
import com.google.android.material.button.MaterialButton
import java.util.concurrent.Executors

class MainActivity : AppCompatActivity() {
    private val bg = Executors.newSingleThreadExecutor()
    private lateinit var input: EditText
    private lateinit var extraSet: EditText
    private lateinit var status: TextView
    private lateinit var openBtn: MaterialButton

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        input = findViewById(R.id.input)
        extraSet = findViewById(R.id.extra_set)
        status = findViewById(R.id.status)
        openBtn = findViewById(R.id.open)

        input.setText(Prefs.lastInput(this))
        extraSet.setText(Prefs.extraSet(this))

        openBtn.setOnClickListener { open() }
        input.setOnEditorActionListener { _, actionId, _ ->
            if (actionId == EditorInfo.IME_ACTION_GO) { open(); true } else false
        }

        handleShare(intent)
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        handleShare(intent)
    }

    /** Veio do botão "Compartilhar" do app do YouTube. */
    private fun handleShare(intent: Intent?) {
        if (intent?.action != Intent.ACTION_SEND) return
        val text = intent.getStringExtra(Intent.EXTRA_TEXT) ?: return
        val link = Regex("https?://\\S+").find(text)?.value ?: text
        input.setText(link)
        open()
    }

    override fun onPause() {
        super.onPause()
        Prefs.setExtraSet(this, extraSet.text.toString())
    }

    private fun open() {
        val text = input.text.toString().trim()
        if (text.isEmpty()) {
            status.text = "Cole o link de uma live ou o @ do canal."
            return
        }
        Prefs.setLastInput(this, text)
        Prefs.setExtraSet(this, extraSet.text.toString())
        status.text = "Procurando a live..."
        openBtn.isEnabled = false
        bg.execute {
            val videoId = try { VideoResolver.resolve(text) } catch (e: Exception) { null }
            runOnUiThread {
                openBtn.isEnabled = true
                if (videoId == null) {
                    status.text = "Não achei live nesse link/canal agora. Confere se está ao vivo."
                } else {
                    status.text = ""
                    startActivity(Intent(this, LiveActivity::class.java).putExtra(LiveActivity.EXTRA_VIDEO, videoId))
                }
            }
        }
    }
}
