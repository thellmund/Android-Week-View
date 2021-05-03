package com.alamkanak.weekview.util

import com.alamkanak.weekview.EventChipsCache
import com.alamkanak.weekview.EventChipsFactory
import com.alamkanak.weekview.EventsCache
import com.alamkanak.weekview.Hours
import com.alamkanak.weekview.PaginatedEventsCache
import com.alamkanak.weekview.ResolvedWeekViewEntity
import com.alamkanak.weekview.SimpleEventsCache
import com.alamkanak.weekview.TextResource
import com.alamkanak.weekview.WeekViewEntity
import com.alamkanak.weekview.plus
import java.util.Calendar
import kotlin.random.Random

internal object MockFactory {

    fun weekViewEntity(
        startTime: Calendar = Calendar.getInstance(),
        endTime: Calendar = Calendar.getInstance() + Hours(1),
        isAllDay: Boolean = false,
    ): WeekViewEntity {
        val id = Random.nextLong()
        return WeekViewEntity.Event(
            id = id,
            titleResource = TextResource.Value("Title $id"),
            startTime = startTime,
            endTime = endTime,
            subtitleResource = null,
            isAllDay = isAllDay,
            style = WeekViewEntity.Style(),
            data = Event(startTime, endTime),
        )
    }

    fun resolvedWeekViewEntities(count: Int): List<ResolvedWeekViewEntity> {
        return (0 until count).map { resolvedWeekViewEntity() }
    }

    fun resolvedWeekViewEntity(
        startTime: Calendar = Calendar.getInstance(),
        endTime: Calendar = Calendar.getInstance() + Hours(1),
        isAllDay: Boolean = false,
    ): ResolvedWeekViewEntity {
        val id = Random.nextLong()
        return ResolvedWeekViewEntity.Event(
            id = id,
            title = "Title $id",
            startTime = startTime,
            endTime = endTime,
            subtitle = null,
            isAllDay = isAllDay,
            style = ResolvedWeekViewEntity.Style(),
            data = Event(startTime, endTime),
        )
    }

    fun simpleEventsCache(): EventsCache {
        return SimpleEventsCache()
    }

    fun paginatedEventsCache(): EventsCache {
        return PaginatedEventsCache()
    }

    fun eventChipsCache(): EventChipsCache {
        return EventChipsCache()
    }

    fun eventChipsFactory(): EventChipsFactory {
        return EventChipsFactory()
    }
}
