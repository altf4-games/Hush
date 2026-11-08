package com.altf4.hush.data

/** One line of the setup checklist. */
data class SetupItem(val id: Id, val title: String, val done: Boolean) {
    enum class Id { Protection, Accessibility, Apps, Night }
}

/** Builds the setup checklist from plain facts, so the card stays dumb and this stays testable. */
object SetupChecklist {
    fun items(
        protectionOn: Boolean,
        accessibilityOn: Boolean,
        chosenApps: Int,
        nightOn: Boolean,
    ): List<SetupItem> = listOf(
        SetupItem(SetupItem.Id.Protection, "Turn on site blocking", protectionOn),
        SetupItem(SetupItem.Id.Accessibility, "Turn on the accessibility service", accessibilityOn),
        SetupItem(SetupItem.Id.Apps, "Choose apps to close at night", chosenApps > 0),
        SetupItem(SetupItem.Id.Night, "Switch on the night lock", nightOn),
    )

    fun isComplete(items: List<SetupItem>): Boolean = items.all { it.done }
}
