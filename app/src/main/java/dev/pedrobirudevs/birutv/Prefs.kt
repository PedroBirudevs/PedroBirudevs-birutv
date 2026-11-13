package dev.pedrobirudevs.birutv

import android.content.Context
import org.json.JSONObject

object Prefs {
    private fun sp(ctx: Context) = ctx.getSharedPreferences("birutv", Context.MODE_PRIVATE)

    fun avatar(ctx: Context): String = sp(ctx).getString("avatar", Channel.AVATAR) ?: Channel.AVATAR
    fun setAvatar(ctx: Context, url: String) = sp(ctx).edit().putString("avatar", url).apply()

    fun loggedIn(ctx: Context): Boolean = sp(ctx).getBoolean("loggedIn", false)
    fun setLoggedIn(ctx: Context, v: Boolean) = sp(ctx).edit().putBoolean("loggedIn", v).apply()

    /** Configurações que o content.js lê (mesmo formato do chrome.storage da extensão). */
    fun settingsJson(): String = JSONObject()
        .put("enabled", true)
        .put("size", 28)
        .put("tooltips", false)
        .put("extraSet", "")
        .toString()
}
