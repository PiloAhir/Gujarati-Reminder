package com.example.gujaratireminder

import android.content.Context
import android.content.SharedPreferences
import org.json.JSONArray
import org.json.JSONObject

object Store {
    fun prefs(c: Context): SharedPreferences = c.getSharedPreferences("data", Context.MODE_PRIVATE)

    fun load(c: Context): MutableList<Reminder> {
        return try {
            parse(prefs(c).getString("list", "[]") ?: "[]").toMutableList()
        } catch (e: Exception) {
            mutableListOf()
        }
    }

    fun save(c: Context, list: List<Reminder>) {
        prefs(c).edit().putString("list", toArray(list).toString()).apply()
    }

    private fun toArray(list: List<Reminder>): JSONArray {
        val a = JSONArray()
        list.forEach { a.put(it.toJson()) }
        return a
    }

    private fun parse(arrayText: String): List<Reminder> {
        val a = JSONArray(arrayText)
        return List(a.length()) { Reminder.fromJson(a.getJSONObject(it)) }
    }

    fun export(c: Context): String =
        JSONObject()
            .put("app", "gujarati-reminder")
            .put("version", 1)
            .put("items", toArray(load(c)))
            .toString(2)

    /** માન્ય ન હોય તો null */
    fun parseBackup(text: String): List<Reminder>? = try {
        val o = JSONObject(text)
        if (o.optString("app") != "gujarati-reminder") null
        else parse(o.getJSONArray("items").toString())
    } catch (e: Exception) {
        null
    }
}
