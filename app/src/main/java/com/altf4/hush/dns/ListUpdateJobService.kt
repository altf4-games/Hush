package com.altf4.hush.dns

import android.app.job.JobParameters
import android.app.job.JobService

/**
 * Runs the opt-in weekly list update. The system only starts it while charging on an unmetered
 * network (see [UpdateScheduler]), so it costs no battery or mobile data.
 */
class ListUpdateJobService : JobService() {
    override fun onStartJob(params: JobParameters): Boolean {
        Thread({
            val outcome = ListUpdater.update(applicationContext)
            jobFinished(params, !outcome.ok) // retry later (with backoff) only on failure
        }, "hush-weekly").start()
        return true // work continues on the thread
    }

    override fun onStopJob(params: JobParameters): Boolean {
        // Conditions changed (unplugged, left Wi-Fi). Try again the next time they hold.
        return true
    }
}
