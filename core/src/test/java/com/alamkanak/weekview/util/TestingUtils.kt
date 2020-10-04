package com.alamkanak.weekview.util

import com.alamkanak.weekview.ResolvedWeekViewEntity
import java.util.Calendar

internal fun createResolvedWeekViewEvent(
    startTime: Calendar,
    endTime: Calendar
): ResolvedWeekViewEntity {
    return ResolvedWeekViewEntity.Event(
        id = 0,
        title = "Title",
        startTime = startTime,
        endTime = endTime,
        subtitle = null,
        isAllDay = false,
        style = ResolvedWeekViewEntity.Style(
            backgroundColor = 0,
            borderColor = 0,
            borderWidth = 0,
            textColor = 0
        ),
        data = Unit
    )
}
