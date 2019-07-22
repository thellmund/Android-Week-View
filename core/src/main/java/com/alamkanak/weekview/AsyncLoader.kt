package com.alamkanak.weekview

import androidx.collection.ArrayMap
import java.util.Calendar

internal class AsyncLoader<T>(
    private val eventCache: EventCache<T>,
    private val eventChipsProvider: EventChipsProvider<T>
) : OnMonthChangeListener<T> {

    var onLoadMore: ((startDate: Calendar, endDate: Calendar) -> Unit)? = null

    private val inTransit = mutableSetOf<Pair<Calendar, Calendar>>()

    override fun onMonthChange(
        startDate: Calendar,
        endDate: Calendar
    ): List<WeekViewDisplayable<T>> {
        val id = startDate to endDate
        if (id !in inTransit) {
            inTransit += id
            onLoadMore?.invoke(startDate, endDate)
        }
        return emptyList()
    }

    /**
     * Caches the list of provided [WeekViewDisplayable]s.
     *
     * @return Whether the provided items are from the currently visible date range
     */
    fun submit(
        items: List<WeekViewDisplayable<T>>,
        dateRange: List<Calendar>
    ): Boolean {
        val events = items.map { it.toWeekViewEvent() }
        val startDate = events.map { it.startTime.atStartOfDay }.min() ?: return false
        val endDate = events.map { it.startTime.atEndOfDay }.max() ?: return false

        val id = startDate to endDate
        if (id in inTransit) {
            // If the loading of these events was prompted by WeekView.onLoadMore(), then remove
            // it from inTransit to allow new refreshes for this period in the future.
            inTransit -= id
        }

        val eventsByPeriod = mapToPeriod(events)
        cacheEvents(eventsByPeriod)
        cacheEventChips(eventsByPeriod.values.flatten())

        return dateRange.any { it.isBetween(startDate, endDate, inclusive = true) }
    }

    private fun mapToPeriod(
        events: List<WeekViewEvent<T>>
    ): ArrayMap<Period, MutableList<WeekViewEvent<T>>> {
        val eventsByPeriod = ArrayMap<Period, MutableList<WeekViewEvent<T>>>()
        for (event in events) {
            val period = Period.fromDate(event.startTime)
            eventsByPeriod.add(period, event)
        }
        return eventsByPeriod
    }

    private fun cacheEvents(eventsByPeriod: Map<Period, MutableList<WeekViewEvent<T>>>) {
        for ((period, periodEvents) in eventsByPeriod) {
            eventCache.update(period, periodEvents)
        }
    }

    private fun cacheEventChips(events: List<WeekViewEvent<T>>) {
        eventChipsProvider.createAndCacheEventChips(events)
    }
}
