package com.example.gujaratireminder

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent

class BootReceiver : BroadcastReceiver() {
    override fun onReceive(c: Context, i: Intent) {
        if (i.action != Intent.ACTION_BOOT_COMPLETED) return
        val list = Store.load(c)
        list.forEach {
            if (!it.done) {
                Scheduler.rollForward(it)
                Scheduler.schedule(c, it)
            }
        }
        Store.save(c, list)
    }
}
