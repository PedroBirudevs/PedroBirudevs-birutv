package dev.pedrobirudevs.birutv

import androidx.activity.ComponentActivity
import androidx.activity.result.IntentSenderRequest
import androidx.activity.result.contract.ActivityResultContracts
import com.google.android.gms.auth.api.identity.AuthorizationRequest
import com.google.android.gms.auth.api.identity.Identity
import com.google.android.gms.common.api.Scope

/**
 * Login com a conta Google (tela oficial do Google, fora do app) e permissão para mandar
 * mensagens no chat. Criar no onCreate da Activity.
 */
class GoogleAuth(private val activity: ComponentActivity) {
    companion object {
        const val SCOPE = "https://www.googleapis.com/auth/youtube.force-ssl"
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
            val result = Identity.getAuthorizationClient(activity).getAuthorizationResultFromIntent(data)
            cb?.invoke(result.accessToken, if (result.accessToken == null) "Login não concluído" else null)
        } catch (e: Exception) {
            cb?.invoke(null, "Login cancelado")
        }
    }

    /** Devolve um token de acesso (abre a tela do Google só na primeira vez). */
    fun token(cb: (token: String?, error: String?) -> Unit) {
        val request = AuthorizationRequest.builder()
            .setRequestedScopes(listOf(Scope(SCOPE)))
            .build()
        Identity.getAuthorizationClient(activity)
            .authorize(request)
            .addOnSuccessListener { result ->
                if (result.hasResolution()) {
                    val pi = result.pendingIntent
                    if (pi == null) {
                        cb(null, "Não foi possível abrir o login do Google")
                        return@addOnSuccessListener
                    }
                    pending = cb
                    launcher.launch(IntentSenderRequest.Builder(pi.intentSender).build())
                } else {
                    cb(result.accessToken, null)
                }
            }
            .addOnFailureListener { e ->
                cb(null, "Falha no login do Google: ${e.message}")
            }
    }
}
