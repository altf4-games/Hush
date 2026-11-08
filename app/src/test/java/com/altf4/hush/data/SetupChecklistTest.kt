package com.altf4.hush.data

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class SetupChecklistTest {
    @Test fun everythingMissingMeansNothingDone() {
        val items = SetupChecklist.items(false, false, 0, false)
        assertEquals(4, items.size)
        assertTrue(items.none { it.done })
        assertFalse(SetupChecklist.isComplete(items))
    }

    @Test fun eachFactTicksItsOwnLine() {
        fun done(items: List<SetupItem>, id: SetupItem.Id) = items.first { it.id == id }.done
        val items = SetupChecklist.items(protectionOn = true, accessibilityOn = false, chosenApps = 3, nightOn = false)
        assertTrue(done(items, SetupItem.Id.Protection))
        assertFalse(done(items, SetupItem.Id.Accessibility))
        assertTrue(done(items, SetupItem.Id.Apps))
        assertFalse(done(items, SetupItem.Id.Night))
    }

    @Test fun completeWhenAllFourAreDone() {
        assertTrue(SetupChecklist.isComplete(SetupChecklist.items(true, true, 1, true)))
    }
}
