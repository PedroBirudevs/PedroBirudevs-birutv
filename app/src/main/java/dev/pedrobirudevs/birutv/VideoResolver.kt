package dev.pedrobirudevs.birutv

/** Descobre o ID do vídeo a partir de um link, de um ID ou de um @canal (pega a live atual). */
object VideoResolver {
    private val RAW_ID = Regex("^[\\w-]{11}$")

    fun parseDirect(input: String): String? {
        val s = input.trim()
        // um ID solto de 11 caracteres só com letras minúsculas pode ser um @canal (ex.: "wallacetaaa")
        if (RAW_ID.matches(s) && s.any { it.isDigit() || it.isUpperCase() || it == '-' || it == '_' }) return s
        Regex("[?&]v=([\\w-]{11})").find(s)?.let { return it.groupValues[1] }
        Regex("youtu\\.be/([\\w-]{11})").find(s)?.let { return it.groupValues[1] }
        Regex("youtube\\.com/(?:live|shorts|embed)/([\\w-]{11})").find(s)?.let { return it.groupValues[1] }
        return null
    }

    private fun channelLiveUrl(input: String): String? {
        val s = input.trim().trimEnd('/')
        if (Regex("^@[\\w.-]+$").matches(s)) return "https://www.youtube.com/$s/live"
        Regex("youtube\\.com/(@[\\w.-]+|channel/UC[\\w-]{22}|c/[\\w.-]+|user/[\\w.-]+)").find(s)?.let {
            return "https://www.youtube.com/${it.groupValues[1]}/live"
        }
        if (Regex("^[\\w.-]{3,}$").matches(s)) return "https://www.youtube.com/@$s/live"
        return null
    }

    /** Chamar fora da thread principal. Retorna null se não achar live. */
    fun resolve(input: String): String? {
        parseDirect(input)?.let { return it }
        val url = channelLiveUrl(input) ?: return null
        val html = Net.request(url, userAgent = Net.UA_DESKTOP).body
        Regex("<link rel=\"canonical\" href=\"https://www\\.youtube\\.com/watch\\?v=([\\w-]{11})\"")
            .find(html)?.let { return it.groupValues[1] }
        return null
    }
}
