package com.pingai.app.notification

import android.annotation.SuppressLint
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.media.AudioAttributes
import android.net.Uri
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import com.pingai.app.R
import com.pingai.app.core.LocaleHelper
import com.pingai.app.data.Topic
import com.pingai.app.ui.detail.DetailActivity

object NotificationHelper {

    private const val CHANNEL_ID = "ping_ai_tips_channel"

    fun ensureChannel(context: Context) {
        val soundUri = Uri.parse("android.resource://${context.packageName}/${R.raw.ping_sound}")
        val attrs = AudioAttributes.Builder()
            .setUsage(AudioAttributes.USAGE_NOTIFICATION)
            .setContentType(AudioAttributes.CONTENT_TYPE_SONIFICATION)
            .build()
        val channel = NotificationChannel(
            CHANNEL_ID,
            LocaleHelper.wrap(context).getString(R.string.notification_channel_name),
            NotificationManager.IMPORTANCE_HIGH
        ).apply {
            setSound(soundUri, attrs)
            enableVibration(true)
        }
        context.getSystemService(NotificationManager::class.java).createNotificationChannel(channel)
    }

    @SuppressLint("MissingPermission")
    fun showTipNotification(context: Context, topic: Topic) {
        val manager = NotificationManagerCompat.from(context)
        if (!manager.areNotificationsEnabled()) return
        val loc = LocaleHelper.wrap(context)

        val intent = Intent(context, DetailActivity::class.java).apply {
            putExtra(DetailActivity.EXTRA_TOPIC_ID, topic.id)
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP
        }
        val pending = PendingIntent.getActivity(
            context, topic.id.hashCode(), intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
        val notification = NotificationCompat.Builder(context, CHANNEL_ID)
            .setSmallIcon(R.drawable.ic_notification)
            .setContentTitle(loc.getString(R.string.notification_title, topic.name))
            .setContentText(topic.shortInfo)
            .setStyle(NotificationCompat.BigTextStyle().bigText(topic.shortInfo))
            .setPriority(NotificationCompat.PRIORITY_HIGH)
            .setAutoCancel(true)
            .setContentIntent(pending)
            .build()
        manager.notify(topic.id.hashCode(), notification)
    }
}
