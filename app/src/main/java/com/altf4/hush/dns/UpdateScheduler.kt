package com.altf4.hush.dns

import android.app.job.JobInfo
import android.app.job.JobScheduler
import android.content.ComponentName
import android.content.Context
import java.util.concurrent.TimeUnit

/**
 * Arms or cancels the weekly list update: one framework job, charging and unmetered network
 * only. It is persisted across reboot (RECEIVE_BOOT_COMPLETED) and also re-armed whenever the app
 * or the tunnel starts. Scheduling can never crash the caller.
 */
object UpdateScheduler {
    private const val JOB_ID = 4201

    fun sync(context: Context, enabled: Boolean) {
        val scheduler = context.getSystemService(JobScheduler::class.java) ?: return
        if (!enabled) {
            scheduler.cancel(JOB_ID)
            return
        }
        if (scheduler.getPendingJob(JOB_ID) != null) return // already armed

        val job = JobInfo.Builder(JOB_ID, ComponentName(context, ListUpdateJobService::class.java))
            .setRequiresCharging(true)
            .setRequiredNetworkType(JobInfo.NETWORK_TYPE_UNMETERED)
            .setPeriodic(TimeUnit.DAYS.toMillis(7))
            .setPersisted(true)
            .build()
        try {
            scheduler.schedule(job)
        } catch (e: RuntimeException) {
            // Scheduling is a convenience; a refusal must not take the VPN down with it.
        }
    }
}
