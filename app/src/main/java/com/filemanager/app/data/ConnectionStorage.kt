package com.filemanager.app.data

import android.content.Context
import android.content.SharedPreferences
import com.google.gson.Gson
import com.google.gson.reflect.TypeToken

class ConnectionStorage(context: Context) {
    private val prefs: SharedPreferences =
        context.getSharedPreferences("connections", Context.MODE_PRIVATE)
    private val gson = Gson()

    fun saveConnections(connections: List<ServerConnection>) {
        val json = gson.toJson(connections)
        prefs.edit().putString("connection_list", json).apply()
    }

    fun loadConnections(): List<ServerConnection> {
        val json = prefs.getString("connection_list", null) ?: return emptyList()
        val type = object : TypeToken<List<ServerConnection>>() {}.type
        return try {
            gson.fromJson(json, type)
        } catch (e: Exception) {
            emptyList()
        }
    }
}
