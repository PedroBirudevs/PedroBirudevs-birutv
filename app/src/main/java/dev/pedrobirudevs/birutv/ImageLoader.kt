package dev.pedrobirudevs.birutv

import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.os.Handler
import android.os.Looper
import android.util.LruCache
import android.widget.ImageView
import java.net.HttpURLConnection
import java.net.URL
import java.util.concurrent.Executors

/** Carregador de imagem simples (avatar e capa da live), com cache em memória. */
object ImageLoader {
    private val cache = LruCache<String, Bitmap>(8)
    private val pool = Executors.newFixedThreadPool(2)
    private val main = Handler(Looper.getMainLooper())

    fun load(url: String?, into: ImageView) {
        if (url.isNullOrEmpty()) return
        into.tag = url
        cache.get(url)?.let { into.setImageBitmap(it); return }
        pool.execute {
            val bmp = try {
                val c = URL(url).openConnection() as HttpURLConnection
                c.connectTimeout = 10_000
                c.readTimeout = 15_000
                try { c.inputStream.use { BitmapFactory.decodeStream(it) } } finally { c.disconnect() }
            } catch (_: Exception) {
                null
            } ?: return@execute
            cache.put(url, bmp)
            main.post { if (into.tag == url) into.setImageBitmap(bmp) }
        }
    }
}
