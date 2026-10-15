package com.unifiedmessages.app

import android.app.Application
import dagger.hilt.android.HiltAndroidApp
import androidx.work.Constraints
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.NetworkType
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkManager
import com.unifiedmessages.app.matrix.MatrixSyncWorker
import java.util.concurrent.TimeUnit

@HiltAndroidApp
class UnifiedMessagesApplication : Application() {
    override fun onCreate() {
        super.onCreate()
        WorkManager.getInstance(this).enqueueUniquePeriodicWork(
            "matrix-background-sync", ExistingPeriodicWorkPolicy.UPDATE,
            PeriodicWorkRequestBuilder<MatrixSyncWorker>(15, TimeUnit.MINUTES)
                .setConstraints(Constraints.Builder().setRequiredNetworkType(NetworkType.CONNECTED).build())
                .build(),
        )
    }
}
