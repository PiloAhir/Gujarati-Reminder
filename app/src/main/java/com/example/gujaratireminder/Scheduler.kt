package com.example.gujaratireminder

import android.app.AlarmManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.os.Build
import java.util.Calendar

object Scheduler {
    private fun pi(c: Context, id: Long): PendingIntent {
        val i = Intent(c, AlarmReceiver::class.java).putExtra("id", id)
        return PendingIntent.getBroadcast(
            c, (id and 0x7fffffff).toInt(), i,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
    }

    fun canExact(c: Context): Boolean {
        if (Build.VERSION.SDK_INT < 31) return true
        return (c.getSystemService(Context.ALARM_SERVICE) as AlarmManager).canScheduleExactAlarms()
    }

    fun schedule(c: Context, r: Reminder) {
        if (r.done || r.time <= System.currentTimeMillis()) return
        val am = c.getSystemService(Context.ALARM_SERVICE) as AlarmManager
        val show = PendingIntent.getActivity(
            c, 0, Intent(c, MainActivity::class.java), PendingIntent.FLAG_IMMUTABLE
        )
        am.setAlarmClock(AlarmManager.AlarmClockInfo(r.time, show), pi(c, r.id))
    }

    fun cancel(c: Context, id: Long) {
        (c.getSystemService(Context.ALARM_SERVICE) as AlarmManager).cancel(pi(c, id))
    }

    /** પુનરાવર્તિત રિમાઇન્ડરનો સમય હવે પછીના સમય સુધી આગળ વધારે */
    fun rollForward(r: Reminder) {
        if (r.repeat == 0) return
        val cal = Calendar.getInstance().apply { timeInMillis = r.time }
        val now = System.currentTimeMillis()
        val step = if (r.repeat == 1) 1 else 7
        while (cal.timeInMillis <= now) cal.add(Calendar.DAY_OF_YEAR, step)
        r.time = cal.timeInMillis
    }
}
