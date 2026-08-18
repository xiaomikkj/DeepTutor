package com.deeptutor.mobile.net

import android.content.Context

/**
 * 服务器地址管理：保存/读取用户配置的 DeepTutor 后端地址。
 * 内置网络协议无关的基础校验，聊天与语音接口地址均从此处派生。
 */
object ServerConfig {

    private const val PREFS = "deeptutor_prefs"
    private const val KEY_BASE_URL = "base_url"

    private fun prefs(ctx: Context) =
        ctx.getSharedPreferences(PREFS, Context.MODE_PRIVATE)

    fun save(ctx: Context, url: String) {
        prefs(ctx).edit().putString(KEY_BASE_URL, normalize(url)).apply()
    }

    fun load(ctx: Context): String =
        prefs(ctx).getString(KEY_BASE_URL, "") ?: ""

    fun clear(ctx: Context) {
        prefs(ctx).edit().remove(KEY_BASE_URL).apply()
    }

    /** 去掉末尾斜杠，缺协议时补 http://。 */
    fun normalize(url: String): String {
        var u = url.trim()
        if (u.isEmpty()) return ""
        if (!u.startsWith("http://") && !u.startsWith("https://")) {
            u = "http://$u"
        }
        return u.trimEnd('/')
    }

    /** http(s)://host:port → ws(s)://host:port */
    fun baseToWs(url: String): String {
        val u = normalize(url)
        return when {
            u.startsWith("https://") -> u.replaceFirst("https://", "wss://")
            u.startsWith("http://") -> u.replaceFirst("http://", "ws://")
            else -> "ws://$u"
        }
    }
}