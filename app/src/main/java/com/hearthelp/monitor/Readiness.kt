package com.hearthelp.monitor

import android.Manifest.permission.*
import android.app.NotificationManager
import android.bluetooth.BluetoothManager
import android.content.Context
import android.content.pm.PackageManager
import android.os.Build
import android.os.PowerManager
import androidx.core.app.NotificationManagerCompat
import com.hearthelp.data.AppPrefs
import kotlinx.coroutines.flow.first

object Readiness {
    private val labels = mapOf(
        SEND_SMS to "Send SMS", RECEIVE_SMS to "Read SMS replies", CALL_PHONE to "Make calls",
        READ_PHONE_STATE to "Phone state", READ_CALL_LOG to "Call log", ACCESS_FINE_LOCATION to "Location",
        ACCESS_COARSE_LOCATION to "Location", BLUETOOTH_SCAN to "Find band", BLUETOOTH_CONNECT to "Connect to band",
        POST_NOTIFICATIONS to "Notifications",
    )

    fun runtimePermissions(): List<String> = buildList {
        addAll(listOf(SEND_SMS, RECEIVE_SMS, CALL_PHONE, READ_PHONE_STATE, READ_CALL_LOG,
            ACCESS_FINE_LOCATION, ACCESS_COARSE_LOCATION))
        if (Build.VERSION.SDK_INT >= 31) { add(BLUETOOTH_SCAN); add(BLUETOOTH_CONNECT) }
        if (Build.VERSION.SDK_INT >= 33) add(POST_NOTIFICATIONS)
    }

    fun missingPermissions(ctx: Context): List<String> =
        runtimePermissions().filter { ctx.checkSelfPermission(it) != PackageManager.PERMISSION_GRANTED }

    fun label(permission: String): String = labels[permission] ?: permission.substringAfterLast('.')

    fun backgroundLocationGranted(ctx: Context) =
        ctx.checkSelfPermission(ACCESS_BACKGROUND_LOCATION) == PackageManager.PERMISSION_GRANTED

    fun batteryExempt(ctx: Context) =
        ctx.getSystemService(PowerManager::class.java).isIgnoringBatteryOptimizations(ctx.packageName)

    fun fullScreenAllowed(ctx: Context) = Build.VERSION.SDK_INT < 34 ||
        ctx.getSystemService(NotificationManager::class.java).canUseFullScreenIntent()

    fun notificationsEnabled(ctx: Context) = NotificationManagerCompat.from(ctx).areNotificationsEnabled()

    fun alarmChannelBlocked(ctx: Context) = ctx.getSystemService(NotificationManager::class.java)
        .getNotificationChannel(Notifications.CH_ALARM)?.importance == NotificationManager.IMPORTANCE_NONE

    fun bluetoothOn(ctx: Context) = ctx.getSystemService(BluetoothManager::class.java)?.adapter?.isEnabled == true

    private fun granted(ctx: Context, p: String) = ctx.checkSelfPermission(p) == PackageManager.PERMISSION_GRANTED

    /** SOS can reach contacts if at least one of SMS or calls is allowed (other permissions only degrade it). */
    fun sosPermitted(ctx: Context) = granted(ctx, SEND_SMS) || granted(ctx, CALL_PHONE)

    /**
     * Android 14+ refuses to start a connectedDevice foreground service without a Bluetooth permission,
     * so the service (and an SOS that needs to start it) cannot run without one.
     */
    fun serviceCanStart(ctx: Context) = Build.VERSION.SDK_INT < 34 ||
        granted(ctx, BLUETOOTH_CONNECT) || granted(ctx, BLUETOOTH_SCAN)

    /** Human-readable reasons emergency alerts might fail. Empty = all good. */
    suspend fun problems(ctx: Context, prefs: AppPrefs): List<String> = buildList {
        val missing = missingPermissions(ctx).map(::label).distinct()
        if (missing.isNotEmpty()) add("Permissions missing: ${missing.joinToString()}")
        if (!backgroundLocationGranted(ctx)) add("Location isn't set to 'Allow all the time' — SMS may lack your location")
        if (!batteryExempt(ctx)) add("Battery optimization is on — Android may stop monitoring")
        if (!fullScreenAllowed(ctx)) add("Full-screen alarms are not allowed")
        if (!notificationsEnabled(ctx)) add("Notifications are turned off for HeartHelp")
        if (alarmChannelBlocked(ctx)) add("The 'Heart-rate alarm' notification channel is blocked")
        if (!bluetoothOn(ctx)) add("Bluetooth is off — the band can't be read")
        if (prefs.contacts.first().isEmpty()) add("No emergency contacts added")
        if (prefs.bandAddress.first() == null) add("No Fitbit Air selected")
    }
}
