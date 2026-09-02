package com.irigoyen.btcalert.notify

import android.Manifest
import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import androidx.core.content.ContextCompat
import com.irigoyen.btcalert.R
import com.irigoyen.btcalert.engine.Firing
import com.irigoyen.btcalert.ui.MainActivity

object Notifier {
    const val CH_ALERTS = "alerts"
    const val CH_CHECKIN = "checkin"
    const val CH_SERVICE = "service"
    const val SERVICE_NOTIFICATION_ID = 1

    fun ensureChannels(context: Context) {
        val nm = context.getSystemService(NotificationManager::class.java)
        nm.createNotificationChannel(
            NotificationChannel(CH_ALERTS, "Price alerts", NotificationManager.IMPORTANCE_HIGH).apply {
                description = "Threshold crossings and big moves"
            }
        )
        nm.createNotificationChannel(
            NotificationChannel(CH_CHECKIN, "Periodic check-ins", NotificationManager.IMPORTANCE_LOW).apply {
                description = "Quiet scheduled price updates"
            }
        )
        nm.createNotificationChannel(
            NotificationChannel(CH_SERVICE, "Real-time monitor", NotificationManager.IMPORTANCE_MIN).apply {
                description = "Persistent notification while real-time mode is on"
                setShowBadge(false)
            }
        )
    }

    fun hasPermission(context: Context): Boolean =
        ContextCompat.checkSelfPermission(context, Manifest.permission.POST_NOTIFICATIONS) == PackageManager.PERMISSION_GRANTED

    private fun openAppIntent(context: Context): PendingIntent =
        PendingIntent.getActivity(
            context, 0,
            Intent(context, MainActivity::class.java).addFlags(Intent.FLAG_ACTIVITY_SINGLE_TOP),
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT,
        )

    fun postAlert(context: Context, firing: Firing) {
        if (!hasPermission(context)) return
        val channel = if (firing.quiet) CH_CHECKIN else CH_ALERTS
        val n = NotificationCompat.Builder(context, channel)
            .setSmallIcon(R.drawable.ic_notification)
            .setContentTitle(firing.title)
            .setContentText(firing.body)
            .setStyle(NotificationCompat.BigTextStyle().bigText(firing.body))
            .setPriority(if (firing.quiet) NotificationCompat.PRIORITY_LOW else NotificationCompat.PRIORITY_HIGH)
            .setCategory(NotificationCompat.CATEGORY_STATUS)
            .setContentIntent(openAppIntent(context))
            .setAutoCancel(true)
            .build()
        // Rule id hash → each rule gets its own notification slot; a re-fire replaces the old one.
        safeNotify(context, firing.rule.id.hashCode(), n)
    }

    fun safeNotify(context: Context, id: Int, n: Notification) {
        if (!hasPermission(context)) return
        try {
            NotificationManagerCompat.from(context).notify(id, n)
        } catch (_: SecurityException) {
            // Permission revoked between the check and the call; nothing to do.
        }
    }

    fun serviceNotification(context: Context, text: String): Notification =
        NotificationCompat.Builder(context, CH_SERVICE)
            .setSmallIcon(R.drawable.ic_notification)
            .setContentTitle(text)
            .setContentText("BTC Alert real-time monitor")
            .setOngoing(true)
            .setSilent(true)
            .setPriority(NotificationCompat.PRIORITY_MIN)
            .setContentIntent(openAppIntent(context))
            .build()
}
