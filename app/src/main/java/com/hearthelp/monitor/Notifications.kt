package com.hearthelp.monitor

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import androidx.core.app.NotificationCompat
import com.hearthelp.R
import com.hearthelp.ui.MainActivity

object Notifications {
    const val CH_MONITOR = "monitor"
    const val CH_ALARM = "alarm"
    const val CH_WARN = "warnings"
    const val ONGOING_ID = 1
    const val ALARM_ID = 2
    const val SIGNAL_ID = 3
    const val WARN_ID = 4

    fun createChannels(ctx: Context) {
        val nm = ctx.getSystemService(NotificationManager::class.java)
        nm.createNotificationChannel(NotificationChannel(CH_MONITOR, "Monitoring", NotificationManager.IMPORTANCE_LOW))
        nm.createNotificationChannel(NotificationChannel(CH_ALARM, "Heart-rate alarm", NotificationManager.IMPORTANCE_HIGH).apply {
            setSound(null, null) // AlarmController plays the sound itself
            setBypassDnd(true)
        })
        nm.createNotificationChannel(NotificationChannel(CH_WARN, "Warnings", NotificationManager.IMPORTANCE_DEFAULT))
    }

    private fun openApp(ctx: Context) = PendingIntent.getActivity(
        ctx, 0, Intent(ctx, MainActivity::class.java), PendingIntent.FLAG_IMMUTABLE)

    fun ongoing(ctx: Context, text: String): Notification {
        val sos = PendingIntent.getForegroundService(
            ctx, 10,
            Intent("com.hearthelp.SOS").setClassName(ctx, "com.hearthelp.monitor.MonitoringService"),
            PendingIntent.FLAG_IMMUTABLE)
        return NotificationCompat.Builder(ctx, CH_MONITOR)
            .setSmallIcon(R.drawable.ic_heart)
            .setContentTitle("HeartHelp")
            .setContentText(text)
            .setOngoing(true)
            .setOnlyAlertOnce(true)
            .setContentIntent(openApp(ctx))
            .addAction(0, "SOS", sos)
            .build()
    }

    fun updateOngoing(ctx: Context, text: String) { notify(ctx, ONGOING_ID, ongoing(ctx, text)) }

    fun signalLost(ctx: Context, bluetoothOn: Boolean) = notify(ctx, SIGNAL_ID,
        NotificationCompat.Builder(ctx, CH_WARN)
            .setSmallIcon(R.drawable.ic_heart)
            .setContentTitle("No heart-rate signal")
            .setContentText(if (bluetoothOn) "Check the band is on your wrist and 'Share heart rate' is on"
                else "Bluetooth is off — turn it on to resume monitoring")
            .setContentIntent(openApp(ctx))
            .build())

    fun cancelSignalLost(ctx: Context) = cancel(ctx, SIGNAL_ID)

    fun warning(ctx: Context, problems: List<String>) {
        if (problems.isEmpty()) return cancel(ctx, WARN_ID)
        notify(ctx, WARN_ID, NotificationCompat.Builder(ctx, CH_WARN)
            .setSmallIcon(R.drawable.ic_heart)
            .setContentTitle("Emergency alerts may not work")
            .setContentText(problems.first())
            .setStyle(NotificationCompat.BigTextStyle().bigText(problems.joinToString("\n")))
            .setOngoing(true)
            .setOnlyAlertOnce(true)
            .setContentIntent(openApp(ctx))
            .build())
    }

    /** No-op if the notification permission was revoked. */
    fun notify(ctx: Context, id: Int, n: Notification) {
        runCatching { ctx.getSystemService(NotificationManager::class.java).notify(id, n) }
    }

    fun cancel(ctx: Context, id: Int) {
        ctx.getSystemService(NotificationManager::class.java).cancel(id)
    }
}
