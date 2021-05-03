package com.alamkanak.weekview.util

import com.alamkanak.weekview.ResolvedWeekViewEntity
import java.util.Calendar
import kotlin.random.Random

internal fun createResolvedWeekViewEvent(
    startTime: Calendar,
    endTime: Calendar
): ResolvedWeekViewEntity = ResolvedWeekViewEntity.Event(
    id = 0,
    title = "Title",
    startTime = startTime,
    endTime = endTime,
    subtitle = null,
    isAllDay = false,
    style = ResolvedWeekViewEntity.Style(),
    data = Unit
)

internal fun ResolvedWeekViewEntity.withDifferentId(): ResolvedWeekViewEntity {
    return when (this) {
        is ResolvedWeekViewEntity.Event<*> -> copy(id = Random.nextLong())
        is ResolvedWeekViewEntity.BlockedTime -> copy(id = Random.nextLong())
    }
}
