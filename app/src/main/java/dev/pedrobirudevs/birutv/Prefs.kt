package dev.pedrobirudevs.birutv

import android.content.Context
import org.json.JSONObject

object Prefs {
    private fun sp(ctx: Context) = ctx.getSharedPreferences("birutv", Context.MODE_PRIVATE)

    fun lastInput(ctx: Context): String = sp(ctx).getString("lastInput", "") ?: ""
    fun setLastInput(ctx: Context, v: String) = sp(ctx).edit().putString("lastInput", v).apply()

    fun extraSet(ctx: Context): String = sp(ctx).getString("extraSet", "") ?: ""
    fun setExtraSet(ctx: Context, raw: String) {
        // aceita o link inteiro (https://7tv.app/emote-sets/XXXX) ou só o ID
        val id = Regex("emote-sets/([A-Za-z0-9]+)").find(raw)?.groupValues?.get(1) ?: raw.trim()
        sp(ctx).edit().putString("extraSet", id).apply()
    }

    /** Configurações que o content.js lê (mesmo formato do chrome.storage da extensão). */
    fun settingsJson(ctx: Context): String = JSONObject()
        .put("enabled", true)
        .put("size", 28)
        .put("tooltips", false)
        .put("extraSet", extraSet(ctx))
        .toString()
}
