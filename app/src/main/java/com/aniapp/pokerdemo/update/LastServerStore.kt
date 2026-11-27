package com.aniapp.pokerdemo.update

import android.content.Context
import com.aniapp.pokerdemo.net.ServerAddress

class LastServerStore(context: Context) {
    private val preferences = context.applicationContext
        .getSharedPreferences("poker_update_server", Context.MODE_PRIVATE)

    suspend fun save(address: ServerAddress): UpdateEndpoint? {
        val endpoint = UpdateEndpoint.resolve(address) ?: return null
        preferences.edit()
            .putString(KEY_HOST, endpoint.host)
            .putBoolean(KEY_SECURE, endpoint.secure)
            .apply()
        return endpoint
    }

    suspend fun load(): UpdateEndpoint? {
        val host = preferences.getString(KEY_HOST, null) ?: return null
        val secure = preferences.getBoolean(KEY_SECURE, false)
        return UpdateEndpoint.resolve(ServerAddress(host, UpdateEndpoint.UPDATE_PORT, secure))
    }

    private companion object {
        const val KEY_HOST = "host"
        const val KEY_SECURE = "secure"
    }
}
