package com.unifiedmessages.app.matrix

import android.Manifest
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import android.util.Log
import androidx.core.app.NotificationCompat
import androidx.core.content.ContextCompat
import androidx.work.CoroutineWorker
import androidx.work.WorkerParameters
import com.unifiedmessages.app.MainActivity
import com.unifiedmessages.app.R
import com.unifiedmessages.app.provider.MessageReceived
import dagger.hilt.EntryPoint
import dagger.hilt.InstallIn
import dagger.hilt.android.EntryPointAccessors
import dagger.hilt.components.SingletonComponent
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.collect
import kotlinx.coroutines.launch
import kotlinx.coroutines.coroutineScope
import java.util.concurrent.ConcurrentHashMap

@EntryPoint
@InstallIn(SingletonComponent::class)
interface MatrixWorkerEntryPoint { fun matrixProvider(): MatrixProvider }

/** Best-effort phone-only sync. WorkManager's 15-minute minimum is not real-time push. */
class MatrixSyncWorker(context: Context, params: WorkerParameters) : CoroutineWorker(context, params) {
    override suspend fun doWork(): Result = coroutineScope {
        if (MatrixAppVisibility.isForeground) {
            Log.d("MatrixDebug", "Background sync skipped while app is foreground")
            return@coroutineScope Result.success()
        }
        Log.i("MatrixDebug", "Background Matrix catch-up started")
        val provider = EntryPointAccessors.fromApplication(
            applicationContext, MatrixWorkerEntryPoint::class.java,
        ).matrixProvider()
        val cutoffByAccount = ConcurrentHashMap<String, Long>()
        val listener = launch {
            provider.events.collect { event ->
                if (event is MessageReceived && shouldNotifyMatrixMessage(
                    cutoffByAccount[event.accountId], event.occurredAt,
                )) {
                    notifyNewMessage(event)
                }
            }
        }
        try {
            val accounts = provider.restoreAccounts().filter { it.connectionState == "CONNECTED" }
            accounts.forEach { account ->
                provider.previousSyncAt(account.id)?.let { cutoffByAccount[account.id] = it }
            }
            var failed = false
            accounts.forEach { account ->
                try { provider.syncOnce(account.id) }
                catch (cancelled: CancellationException) { throw cancelled }
                catch (error: Exception) {
                    failed = true
                    Log.e("MatrixDebug", "Background Matrix catch-up failed: ${error.javaClass.simpleName}")
                }
            }
            // Timeline listeners dispatch on the SDK's own executor after sync returns.
            if (accounts.isNotEmpty()) delay(2_500)
            if (failed) Result.retry() else Result.success()
        } catch (cancelled: CancellationException) { throw cancelled }
        catch (error: Exception) {
            Log.e("MatrixDebug", "Background Matrix restore failed: ${error.javaClass.simpleName}")
            Result.retry()
        }
        finally { listener.cancel() }
    }

    private fun notifyNewMessage(event: MessageReceived) {
        if (Build.VERSION.SDK_INT >= 33 && ContextCompat.checkSelfPermission(
                applicationContext, Manifest.permission.POST_NOTIFICATIONS,
            ) != PackageManager.PERMISSION_GRANTED) return
        val manager = applicationContext.getSystemService(NotificationManager::class.java)
        manager.createNotificationChannel(NotificationChannel(
            CHANNEL_ID, "Matrix messages", NotificationManager.IMPORTANCE_DEFAULT,
        ))
        val openApp = PendingIntent.getActivity(
            applicationContext, 0, Intent(applicationContext, MainActivity::class.java),
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT,
        )
        val notification = NotificationCompat.Builder(applicationContext, CHANNEL_ID)
            .setSmallIcon(R.drawable.ic_launcher)
            .setContentTitle("New Matrix message")
            .setContentText(event.thread.title)
            .setVisibility(NotificationCompat.VISIBILITY_PRIVATE)
            .setAutoCancel(true)
            .setContentIntent(openApp)
            .build()
        manager.notify(event.eventId.hashCode(), notification)
    }

    companion object { private const val CHANNEL_ID = "matrix_messages" }
}
