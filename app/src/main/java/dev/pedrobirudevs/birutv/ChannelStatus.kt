package dev.pedrobirudevs.birutv

import android.text.Html
import java.net.HttpURLConnection
import java.net.URL

/** Canal fixo do app. */
object Channel {
    const val HANDLE = "@wallacetaaLIVES"
    const val NAME = "Wallacetaa LIVES"
    const val ID = "UCcHXeP0IXzB2wjigNQ909iw"
    const val AVATAR =
        "https://yt3.googleusercontent.com/PUeraxzhs7lIRrM7Hx2qluqX76vPcgqlPH5pb0L1nYiNa7BEPqZE41xpJ81hYSnxclz_0MiDqE4=s240-c-k-c0x00ffffff-no-rj"
}

/** Descobre se o canal está ao vivo lendo a página /live só até achar a resposta. */
object ChannelStatus {
    enum class State { LIVE, UPCOMING, OFFLINE }

    data class Result(
        val state: State,
        val videoId: String? = null,
        val title: String? = null,
        val avatarUrl: String? = null,
        val scheduledAt: Long? = null, // epoch em segundos
    )

    private val CANONICAL = Regex("<link rel=\"canonical\" href=\"([^\"]+)\"")
    private val WATCH_ID = Regex("watch\\?v=([\\w-]{11})")
    private val OG_IMAGE = Regex("<meta property=\"og:image\" content=\"([^\"]+)\"")
    private val META_TITLE = Regex("<meta name=\"title\" content=\"([^\"]*)\"")
    private val LIVE_NOW = Regex("\"isLiveNow\":(true|false)")
    private val UPCOMING = Regex("\"isUpcoming\":true")
    private val SCHEDULED = Regex("\"scheduledStartTime\":\"(\\d+)\"")
    private const val MAX_BYTES = 4_000_000

    /** Chamar fora da thread principal. */
    fun fetch(): Result {
        val c = URL("https://www.youtube.com/${Channel.HANDLE}/live").openConnection() as HttpURLConnection
        c.connectTimeout = 15_000
        c.readTimeout = 20_000
        c.setRequestProperty("User-Agent", Net.UA_DESKTOP)
        c.setRequestProperty("Accept-Language", "pt-BR,pt;q=0.9")
        // a conexão já pede e descompacta gzip sozinha
        try {
            val reader = c.inputStream.bufferedReader(Charsets.UTF_8)
            val sb = StringBuilder()
            val buf = CharArray(32 * 1024)
            var canonical: String? = null
            var avatar: String? = null
            var title: String? = null
            var liveNow: String? = null
            var upcoming = false
            var scheduled: Long? = null
            while (sb.length < MAX_BYTES) {
                val n = reader.read(buf)
                if (n < 0) break
                // procura só no trecho novo (com uma sobra para não cortar no meio)
                val from = maxOf(0, sb.length - 600)
                sb.append(buf, 0, n)
                fun find(re: Regex) = re.find(sb, from)?.groupValues?.get(1)

                if (canonical == null) canonical = find(CANONICAL)
                if (avatar == null) avatar = find(OG_IMAGE)
                if (title == null) title = find(META_TITLE)?.let { decode(it) }
                if (liveNow == null) liveNow = find(LIVE_NOW)
                if (!upcoming) upcoming = UPCOMING.find(sb, from) != null
                if (scheduled == null) scheduled = find(SCHEDULED)?.toLongOrNull()

                val canon = canonical ?: continue
                val videoId = WATCH_ID.find(canon)?.groupValues?.get(1)
                if (videoId == null) {
                    // canal sem live: a página /live vira a página do canal
                    if (avatar != null) return Result(State.OFFLINE, avatarUrl = avatar)
                    continue
                }
                when (liveNow) {
                    "true" -> return Result(State.LIVE, videoId, title)
                    // live agendada também vem com isLiveNow=false
                    "false" -> return if (upcoming) Result(State.UPCOMING, videoId, title, scheduledAt = scheduled)
                    else Result(State.OFFLINE)
                }
            }
            return Result(State.OFFLINE)
        } finally {
            c.disconnect()
        }
    }

    private fun decode(s: String): String = Html.fromHtml(s, Html.FROM_HTML_MODE_LEGACY).toString()
}
