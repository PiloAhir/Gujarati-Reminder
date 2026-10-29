package com.example.gujaratireminder

import org.json.JSONObject

/** repeat: 0 = એક વાર, 1 = દરરોજ, 2 = દર અઠવાડિયે */
data class Reminder(
    val id: Long,
    var title: String,
    var note: String,
    var time: Long,
    var repeat: Int,
    var done: Boolean = false
) {
    fun toJson(): JSONObject = JSONObject()
        .put("id", id).put("title", title).put("note", note)
        .put("time", time).put("repeat", repeat).put("done", done)

    companion object {
        fun fromJson(o: JSONObject) = Reminder(
            o.getLong("id"),
            o.getString("title"),
            o.optString("note", ""),
            o.getLong("time"),
            o.optInt("repeat", 0),
            o.optBoolean("done", false)
        )
    }
}
