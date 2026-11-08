package com.altf4.hush.data

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class OnboardingTest {
    @Test fun privateDnsSettingValuesMap() {
        assertEquals(PrivateDnsMode.Off, PrivateDnsMode.fromSetting("off"))
        assertEquals(PrivateDnsMode.Automatic, PrivateDnsMode.fromSetting("opportunistic"))
        assertEquals(PrivateDnsMode.Strict, PrivateDnsMode.fromSetting("hostname"))
        assertEquals(PrivateDnsMode.Unknown, PrivateDnsMode.fromSetting(null))
        assertEquals(PrivateDnsMode.Unknown, PrivateDnsMode.fromSetting("weird"))
    }

    @Test fun onlyStrictPrivateDnsBypasses() {
        assertTrue(PrivateDnsMode.Strict.bypassesHush)
        assertFalse(PrivateDnsMode.Off.bypassesHush)
        assertFalse(PrivateDnsMode.Automatic.bypassesHush)
        assertFalse(PrivateDnsMode.Unknown.bypassesHush)
    }

    @Test fun stepsStartWithWelcomeAndEndWithDone() {
        assertEquals(Onboarding.Step.Welcome, Onboarding.steps.first())
        assertEquals(Onboarding.Step.Done, Onboarding.steps.last())
        assertEquals(1, Onboarding.indexOf(Onboarding.Step.Protection))
    }

    @Test fun stepsAreMetFromTheFacts() {
        fun met(step: Onboarding.Step, vpn: Boolean = false, dns: PrivateDnsMode = PrivateDnsMode.Off, a11y: Boolean = false) =
            Onboarding.isMet(step, vpn, dns, a11y)
        assertFalse(met(Onboarding.Step.Protection))
        assertTrue(met(Onboarding.Step.Protection, vpn = true))
        assertFalse(met(Onboarding.Step.PrivateDns, dns = PrivateDnsMode.Strict))
        assertTrue(met(Onboarding.Step.PrivateDns, dns = PrivateDnsMode.Automatic))
        assertFalse(met(Onboarding.Step.NightLock))
        assertTrue(met(Onboarding.Step.NightLock, a11y = true))
        assertTrue(met(Onboarding.Step.Welcome))
        assertFalse(Onboarding.isMet(Onboarding.Step.Battery, false, PrivateDnsMode.Off, false, batteryUnrestricted = false))
        assertTrue(Onboarding.isMet(Onboarding.Step.Battery, false, PrivateDnsMode.Off, false, batteryUnrestricted = true))
    }
}
