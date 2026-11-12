package dev.pedrobirudevs.birutv

import org.json.JSONObject

/** Envio de mensagens pela API oficial do YouTube (YouTube Data API v3). */
object YouTubeApi {
    private const val BASE = "https://www.googleapis.com/youtube/v3"

    class ApiError(message: String, val code: Int) : Exception(message)

    /** Custa 1 unidade da cota. Chamar fora da thread principal. */
    fun liveChatId(videoId: String, token: String): String {
        val r = Net.request("$BASE/videos?part=liveStreamingDetails&id=$videoId", token = token)
        check(r)
        val items = JSONObject(r.body).optJSONArray("items")
        val details = items?.optJSONObject(0)?.optJSONObject("liveStreamingDetails")
        val id = details?.optString("activeLiveChatId").orEmpty()
        if (id.isEmpty()) throw ApiError("Essa live não está com o chat aberto agora.", 404)
        return id
    }

    /** Custa ~50 unidades da cota. Chamar fora da thread principal. */
    fun send(liveChatId: String, text: String, token: String) {
        val body = JSONObject().put(
            "snippet",
            JSONObject()
                .put("liveChatId", liveChatId)
                .put("type", "textMessageEvent")
                .put("textMessageDetails", JSONObject().put("messageText", text)),
        ).toString()
        val r = Net.request("$BASE/liveChat/messages?part=snippet", method = "POST", body = body, token = token)
        check(r)
    }

    private fun check(r: Net.Resp) {
        if (r.code in 200..299) return
        var reason = ""
        var message = ""
        try {
            val err = JSONObject(r.body).getJSONObject("error")
            message = err.optString("message")
            reason = err.optJSONArray("errors")?.optJSONObject(0)?.optString("reason").orEmpty()
        } catch (_: Exception) {
        }
        val friendly = when {
            r.code == 401 -> "Sessão expirada, entrando de novo..."
            reason == "quotaExceeded" || reason == "dailyLimitExceeded" ->
                "O limite diário da API do YouTube acabou. Volta a funcionar amanhã."
            reason == "rateLimitExceeded" -> "Calma! Muitas mensagens em pouco tempo."
            reason == "liveChatEnded" || reason == "liveChatNotFound" || reason == "liveChatDisabled" ->
                "O chat dessa live está fechado."
            reason == "forbidden" || r.code == 403 ->
                "Você não pode mandar mensagem nesse chat (pode ser só para membros, modo lento ou bloqueio). $message"
            else -> "Erro ${r.code}: ${message.ifEmpty { "não deu para enviar" }}"
        }
        throw ApiError(friendly.trim(), r.code)
    }
}
