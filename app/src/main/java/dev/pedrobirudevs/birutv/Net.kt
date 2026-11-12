package dev.pedrobirudevs.birutv

import java.net.HttpURLConnection
import java.net.URL

object Net {
    const val UA_DESKTOP =
        "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/131.0.0.0 Safari/537.36"

    data class Resp(val code: Int, val body: String)

    /** Requisição HTTP simples. Chamar fora da thread principal. */
    fun request(
        url: String,
        method: String = "GET",
        body: String? = null,
        token: String? = null,
        userAgent: String? = null,
    ): Resp {
        val c = URL(url).openConnection() as HttpURLConnection
        try {
            c.requestMethod = method
            c.connectTimeout = 15_000
            c.readTimeout = 20_000
            c.setRequestProperty("Accept-Language", "pt-BR,pt;q=0.9,en;q=0.8")
            userAgent?.let { c.setRequestProperty("User-Agent", it) }
            token?.let { c.setRequestProperty("Authorization", "Bearer $it") }
            if (body != null) {
                c.doOutput = true
                c.setRequestProperty("Content-Type", "application/json; charset=utf-8")
                c.outputStream.use { it.write(body.toByteArray(Charsets.UTF_8)) }
            }
            val code = c.responseCode
            val stream = if (code in 200..399) c.inputStream else c.errorStream
            val text = stream?.bufferedReader(Charsets.UTF_8)?.use { it.readText() } ?: ""
            return Resp(code, text)
        } finally {
            c.disconnect()
        }
    }
}
