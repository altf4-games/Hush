package com.altf4.hush.dns

import android.app.job.JobInfo
import android.app.job.JobScheduler
import android.content.ComponentName
import android.content.Context
import java.util.concurrent.TimeUnit

/**
 * Arms or cancels the opt-in weekly list update: one framework job, charging and unmetered
 * network only. Not persisted across reboot (that would need RECEIVE_BOOT_COMPLETED), so it is
 * re-armed whenever the app or the tunnel starts; the tunnel is restarted by Always-on VPN.
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
            .build()
        scheduler.schedule(job)
    }
}
