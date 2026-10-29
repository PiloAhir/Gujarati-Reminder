package com.example.gujaratireminder

import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.os.Build
import androidx.core.app.NotificationCompat

class AlarmReceiver : BroadcastReceiver() {
    override fun onReceive(c: Context, i: Intent) {
        val id = i.getLongExtra("id", -1L)
        val list = Store.load(c)
        val r = list.find { it.id == id } ?: return

        notify(c, r)

        if (r.repeat != 0) {
            Scheduler.rollForward(r)
            Scheduler.schedule(c, r)
        } else {
            r.done = true
        }
        Store.save(c, list)
    }

    private fun notify(c: Context, r: Reminder) {
        val nm = c.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        if (Build.VERSION.SDK_INT >= 26) {
            nm.createNotificationChannel(
                NotificationChannel("reminders", c.getString(R.string.notif_channel), NotificationManager.IMPORTANCE_HIGH)
            )
        }
        val open = PendingIntent.getActivity(
            c, 0, Intent(c, MainActivity::class.java), PendingIntent.FLAG_IMMUTABLE
        )
        val n = NotificationCompat.Builder(c, "reminders")
            .setSmallIcon(R.drawable.ic_notif)
            .setContentTitle(r.title)
            .setContentText(r.note.ifBlank { c.getString(R.string.app_name) })
            .setStyle(NotificationCompat.BigTextStyle().bigText(r.note.ifBlank { r.title }))
            .setPriority(NotificationCompat.PRIORITY_HIGH)
            .setAutoCancel(true)
            .setContentIntent(open)
            .build()
        nm.notify((r.id and 0x7fffffff).toInt(), n)
    }
}
