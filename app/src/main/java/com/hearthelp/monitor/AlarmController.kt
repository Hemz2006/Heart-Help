package com.hearthelp.monitor

import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.media.AudioAttributes
import android.media.AudioManager
import android.media.MediaPlayer
import android.media.RingtoneManager
import android.os.Build
import android.os.VibrationEffect
import android.os.Vibrator
import android.os.VibratorManager
import androidx.core.app.NotificationCompat
import com.hearthelp.R
import com.hearthelp.ui.AlarmActivity

/** Loud looping alarm on the alarm stream (max volume, restored afterwards), vibration, full-screen screen. */
class AlarmController(private val ctx: Context) {
    private val audio = ctx.getSystemService(AudioManager::class.java)
    private val vibrator: Vibrator = if (Build.VERSION.SDK_INT >= 31) {
        ctx.getSystemService(VibratorManager::class.java).defaultVibrator
    } else @Suppress("DEPRECATION") ctx.getSystemService(Vibrator::class.java)
    private var player: MediaPlayer? = null
    private var savedVolume: Int? = null

    @Suppress("DEPRECATION")
    fun start() {
        showScreen()
        if (player == null) {
            if (savedVolume == null) {
                runCatching {
                    savedVolume = audio.getStreamVolume(AudioManager.STREAM_ALARM)
                    audio.setStreamVolume(AudioManager.STREAM_ALARM, audio.getStreamMaxVolume(AudioManager.STREAM_ALARM), 0)
                }
            }
            val uris = listOfNotNull(
                RingtoneManager.getDefaultUri(RingtoneManager.TYPE_ALARM),
                RingtoneManager.getDefaultUri(RingtoneManager.TYPE_RINGTONE),
            )
            for (uri in uris) {
                val mp = MediaPlayer()
                val ok = runCatching {
                    mp.setAudioAttributes(AudioAttributes.Builder()
                        .setUsage(AudioAttributes.USAGE_ALARM)
                        .setContentType(AudioAttributes.CONTENT_TYPE_SONIFICATION).build())
                    mp.setDataSource(ctx, uri)
                    mp.isLooping = true
                    mp.prepare()
                    mp.start()
                }.isSuccess
                if (ok) { player = mp; break } else runCatching { mp.release() }
            }
        }
        vibrator.vibrate(
            VibrationEffect.createWaveform(longArrayOf(0, 800, 400), 0),
            AudioAttributes.Builder().setUsage(AudioAttributes.USAGE_ALARM).build())
    }

    fun silence() {
        player?.run { runCatching { stop() }; release() }
        player = null
        savedVolume?.let { v -> runCatching { audio.setStreamVolume(AudioManager.STREAM_ALARM, v, 0) } }
        savedVolume = null
        vibrator.cancel()
    }

    private fun screenIntent() = Intent(ctx, AlarmActivity::class.java).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)

    fun showScreen(dispatched: Boolean = false) {
        notifyAlert(dispatched)
        // If the app is in the foreground the full-screen intent only shows a heads-up; open directly too.
        runCatching { ctx.startActivity(screenIntent()) }
    }

    /**
     * Posts (or updates) the alarm notification. It carries an action button so the alert can be
     * answered even if the full-screen screen never appears.
     */
    fun notifyAlert(dispatched: Boolean) {
        val pi = PendingIntent.getActivity(ctx, 1, screenIntent(), PendingIntent.FLAG_IMMUTABLE)
        val (label, action, code) = if (dispatched) Triple("Stop alert", MonitoringService.ACTION_DISMISS, 12)
            else Triple("I'M OK", MonitoringService.ACTION_IM_OK, 11)
        val respond = PendingIntent.getService(ctx, code,
            Intent(ctx, MonitoringService::class.java).setAction(action), PendingIntent.FLAG_IMMUTABLE)
        Notifications.notify(ctx, Notifications.ALARM_ID,
            NotificationCompat.Builder(ctx, Notifications.CH_ALARM)
                .setSmallIcon(R.drawable.ic_heart)
                .setContentTitle(if (dispatched) "Contacting your emergency contacts" else "Heart-rate alert")
                .setContentText(if (dispatched) "Tap Stop alert once you're safe" else "Tap I'M OK if you're fine")
                .setPriority(NotificationCompat.PRIORITY_MAX)
                .setCategory(NotificationCompat.CATEGORY_ALARM)
                .setOngoing(true)
                .setFullScreenIntent(pi, true)
                .setContentIntent(pi)
                .addAction(0, label, respond)
                .build())
    }

    fun clear() {
        silence()
        Notifications.cancel(ctx, Notifications.ALARM_ID)
    }
}
