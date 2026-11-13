package dev.pedrobirudevs.birutv

import android.os.SystemClock
import androidx.activity.ComponentActivity
import androidx.activity.result.IntentSenderRequest
import androidx.activity.result.contract.ActivityResultContracts
import com.google.android.gms.auth.api.identity.AuthorizationRequest
import com.google.android.gms.auth.api.identity.Identity
import com.google.android.gms.common.api.Scope

/**
 * Login com a conta Google (tela oficial do Google) e permissão para mandar mensagens no chat.
 * Criar no onCreate da Activity.
 */
class GoogleAuth(private val activity: ComponentActivity) {
    companion object {
        const val SCOPE = "https://www.googleapis.com/auth/youtube.force-ssl"
        private const val TOKEN_TTL_MS = 45 * 60 * 1000L

        // compartilhado entre as telas enquanto o app está aberto
        @Volatile private var cachedToken: String? = null
        @Volatile private var cachedAt = 0L

        fun clearCache() {
            cachedToken = null
        }
    }

    private var pending: ((String?, String?) -> Unit)? = null

    private val launcher = activity.registerForActivityResult(
        ActivityResultContracts.StartIntentSenderForResult(),
    ) { res ->
        val cb = pending
        pending = null
        val data = res.data
        if (data == null) {
            cb?.invoke(null, "Login cancelado")
            return@registerForActivityResult
        }
        try {
            val token = Identity.getAuthorizationClient(activity).getAuthorizationResultFromIntent(data).accessToken
            remember(token)
            cb?.invoke(token, if (token == null) "Login não concluído" else null)
        } catch (e: Exception) {
            cb?.invoke(null, "Login cancelado")
        }
    }

    private fun remember(token: String?) {
        cachedToken = token
        cachedAt = SystemClock.elapsedRealtime()
        Prefs.setLoggedIn(activity, token != null)
    }

    /**
     * Devolve um token de acesso.
     * interactive=false: só pega se a pessoa já deu permissão antes (sem abrir tela nenhuma).
     */
    fun token(interactive: Boolean = true, cb: (token: String?, error: String?) -> Unit) {
        val cached = cachedToken
        if (cached != null && SystemClock.elapsedRealtime() - cachedAt < TOKEN_TTL_MS) {
            cb(cached, null)
            return
        }
        val request = AuthorizationRequest.builder()
            .setRequestedScopes(listOf(Scope(SCOPE)))
            .build()
        Identity.getAuthorizationClient(activity)
            .authorize(request)
            .addOnSuccessListener { result ->
                if (!result.hasResolution()) {
                    remember(result.accessToken)
                    cb(result.accessToken, null)
                    return@addOnSuccessListener
                }
                val pi = result.pendingIntent
                if (!interactive || pi == null) {
                    cb(null, null)
                    return@addOnSuccessListener
                }
                pending = cb
                launcher.launch(IntentSenderRequest.Builder(pi.intentSender).build())
            }
            .addOnFailureListener { e ->
                cb(null, if (interactive) "Falha no login do Google: ${e.message}" else null)
            }
    }
}
