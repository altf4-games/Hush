package com.altf4.hush.data

/** System Private DNS setting, as far as it affects Hush. */
enum class PrivateDnsMode {
    /** Off: lookups use the tunnel. */
    Off,

    /** Automatic: tries encrypted DNS to the tunnel's address, then falls back to it. Works. */
    Automatic,

    /** A named provider: the system sends lookups there directly and Hush never sees them. */
    Strict,

    /** Could not be read. */
    Unknown;

    /** True when this setting would bypass site blocking. */
    val bypassesHush: Boolean get() = this == Strict

    companion object {
        /** Reads the value of the `private_dns_mode` global setting. */
        fun fromSetting(value: String?): PrivateDnsMode = when (value) {
            null -> Unknown
            "off" -> Off
            "opportunistic" -> Automatic
            "hostname" -> Strict
            else -> Unknown
        }
    }
}

/** The first-run setup pages, in order. Pure: the facts are passed in so this stays testable. */
object Onboarding {
    enum class Step { Welcome, Protection, Dns, PrivateDns, NightLock, Battery, Done }

    val steps: List<Step> = Step.entries

    /** Whether a step's goal is already met, so its button can say "Done" instead of asking again. */
    fun isMet(
        step: Step,
        protectionOn: Boolean,
        privateDns: PrivateDnsMode,
        accessibilityOn: Boolean,
        batteryUnrestricted: Boolean = false,
    ): Boolean =
        when (step) {
            Step.Welcome, Step.Dns, Step.Done -> true
            Step.Protection -> protectionOn
            Step.PrivateDns -> !privateDns.bypassesHush
            Step.NightLock -> accessibilityOn
            Step.Battery -> batteryUnrestricted
        }

    /** 0-based position of [step], for the progress indicator. */
    fun indexOf(step: Step): Int = steps.indexOf(step)
}
