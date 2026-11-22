package com.burstbrainconcepts.aura

import android.content.Context
import androidx.work.Worker
import androidx.work.WorkerParameters

class RecoveryPollWorker(context: Context, parameters: WorkerParameters) : Worker(context, parameters) {
    override fun doWork(): Result = try {
        RemoteRecoveryClient(applicationContext).pollAndExecute()
        Result.success()
    } catch (_: Exception) {
        Result.retry()
    }
}
