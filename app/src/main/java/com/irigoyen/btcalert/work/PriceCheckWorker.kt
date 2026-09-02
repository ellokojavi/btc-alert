package com.irigoyen.btcalert.work

import android.content.Context
import androidx.work.CoroutineWorker
import androidx.work.WorkerParameters
import com.irigoyen.btcalert.data.PriceChecker

/** Battery-saver mode: WorkManager runs this every 15 minutes (the platform minimum). */
class PriceCheckWorker(context: Context, params: WorkerParameters) : CoroutineWorker(context, params) {
    override suspend fun doWork(): Result {
        val sample = PriceChecker.runOnce(applicationContext)
        return if (sample != null) Result.success() else if (runAttemptCount < 3) Result.retry() else Result.failure()
    }
}
