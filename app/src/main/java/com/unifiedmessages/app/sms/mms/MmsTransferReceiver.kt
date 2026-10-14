package com.unifiedmessages.app.sms.mms

import android.app.job.JobInfo
import android.app.job.JobParameters
import android.app.job.JobScheduler
import android.app.job.JobService
import android.content.BroadcastReceiver
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.os.Handler
import android.os.Looper
import android.os.PersistableBundle
import android.util.Log
import dagger.hilt.android.AndroidEntryPoint
import java.util.concurrent.ConcurrentHashMap
import javax.inject.Inject
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.launch

/** An explicit PendingIntent target; it schedules durable work before the broadcast ends. */
@AndroidEntryPoint
class MmsTransferReceiver : BroadcastReceiver() {
    @Inject lateinit var transport: MmsTransport

    override fun onReceive(context: Context, intent: Intent) {
        val token = intent.getStringExtra(MmsTransport.EXTRA_TOKEN) ?: return
        try {
            transport.captureSendResponse(intent)
        } catch (error: Exception) {
            Log.e("MmsTransferReceiver", "Could not save MMS send confirmation", error)
        }
        val extras = PersistableBundle().apply {
            putString(MmsTransport.EXTRA_TOKEN, token)
            putInt(EXTRA_RESULT_CODE, resultCode)
            putInt(MmsTransport.EXTRA_ATTEMPT, intent.getIntExtra(MmsTransport.EXTRA_ATTEMPT, 0))
        }
        val request = JobInfo.Builder(
            token.hashCode() and Int.MAX_VALUE,
            ComponentName(context, MmsTransferJobService::class.java),
        ).setExtras(extras)
            .setOverrideDeadline(0)
            .setBackoffCriteria(30_000, JobInfo.BACKOFF_POLICY_EXPONENTIAL)
            .build()
        val scheduler = context.getSystemService(JobScheduler::class.java)
        if (scheduler?.schedule(request) == JobScheduler.RESULT_SUCCESS) return

        // Scheduling may be unavailable on a device. Complete the callback within the broadcast budget.
        val pending = goAsync()
        val callback = Intent(intent)
        val code = resultCode
        CoroutineScope(SupervisorJob() + Dispatchers.IO).launch {
            try {
                transport.handleTransfer(callback, code)
            } catch (error: Exception) {
                Log.e("MmsTransferReceiver", "Could not complete MMS transfer", error)
            } finally {
                pending.finish()
            }
        }
    }

    companion object {
        private const val EXTRA_RESULT_CODE = "mms_result_code"
    }
}

/** The callback token resolves a transfer record saved before the platform operation began. */
@AndroidEntryPoint
class MmsTransferJobService : JobService() {
    @Inject lateinit var transport: MmsTransport
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private val running = ConcurrentHashMap<Int, Job>()
    private val main = Handler(Looper.getMainLooper())

    override fun onStartJob(params: JobParameters): Boolean {
        val token = params.extras.getString(MmsTransport.EXTRA_TOKEN) ?: return false
        val resultCode = params.extras.getInt("mms_result_code")
        val kind = params.extras.getString(MmsTransport.EXTRA_JOB_KIND)
        val job = scope.launch {
            var stopped = false
            var retry = false
            try {
                if (kind == MmsTransport.JOB_KIND_RETRY) {
                    transport.retryDownload(token)
                } else {
                    transport.handleTransfer(Intent()
                        .putExtra(MmsTransport.EXTRA_TOKEN, token)
                        .putExtra(MmsTransport.EXTRA_ATTEMPT,
                            params.extras.getInt(MmsTransport.EXTRA_ATTEMPT, 0)), resultCode)
                }
            } catch (_: CancellationException) {
                stopped = true
            } catch (error: Exception) {
                // Retry IO/database failures. Permanently invalid downloads are cleared by the transport.
                retry = true
                Log.e("MmsTransferJob", "Could not finish MMS transfer", error)
            } finally {
                running.remove(params.jobId)
                if (!stopped) main.post { jobFinished(params, retry) }
            }
        }
        running[params.jobId] = job
        return true
    }

    override fun onStopJob(params: JobParameters): Boolean {
        running.remove(params.jobId)?.cancel()
        return true
    }

    override fun onDestroy() {
        scope.cancel()
        super.onDestroy()
    }
}
