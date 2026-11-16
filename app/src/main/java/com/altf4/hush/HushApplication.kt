package com.altf4.hush

import android.app.Application
import com.altf4.hush.dns.ProtectionSurfaces
import com.altf4.hush.dns.Stats

class HushApplication : Application() {
    override fun onCreate() {
        super.onCreate()
        // Keep the widget's count in step whenever the counts are saved or cleared.
        Stats.onSaved = { ProtectionSurfaces.refresh(this) }
    }
}
