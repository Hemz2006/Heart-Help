package com.hearthelp.monitor

import android.Manifest.permission.*
import android.app.NotificationManager
import android.content.Context
import android.content.pm.PackageManager
import android.os.Build
import android.os.PowerManager
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

    /** Human-readable reasons emergency alerts might fail. Empty = all good. */
    suspend fun problems(ctx: Context, prefs: AppPrefs): List<String> = buildList {
        val missing = missingPermissions(ctx).map(::label).distinct()
        if (missing.isNotEmpty()) add("Permissions missing: ${missing.joinToString()}")
        if (!backgroundLocationGranted(ctx)) add("Location isn't set to 'Allow all the time' — SMS may lack your location")
        if (!batteryExempt(ctx)) add("Battery optimization is on — Android may stop monitoring")
        if (!fullScreenAllowed(ctx)) add("Full-screen alarms are not allowed")
        if (prefs.contacts.first().isEmpty()) add("No emergency contacts added")
        if (prefs.bandAddress.first() == null) add("No Fitbit Air selected")
    }
}
