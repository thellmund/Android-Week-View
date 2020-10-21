package com.alamkanak.weekview

/**
 * This interface must be implemented by classes that should be displayed in [WeekView].
 */
interface WeekViewDisplayable {

    /**
     * Returns a [WeekViewEvent] for use in [WeekView].
     */
    @Deprecated(
        message = "Use toWeekViewEntity() instead.",
        replaceWith = ReplaceWith(expression = "toWeekViewEntity"),
        level = DeprecationLevel.ERROR
    )
    fun <T> toWeekViewEvent(): WeekViewEvent<T> {
        throw IllegalStateException("toWeekViewEvent() is deprecated. Use toWeekViewEntity() instead.")
    }

    /**
     * Returns a [WeekViewEntity] for use in [WeekView]. This can either be a
     * [WeekViewEntity.Event] or a [WeekViewEntity.BlockedTime].
     */
    fun toWeekViewEntity(): WeekViewEntity
}
