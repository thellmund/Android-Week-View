package com.alamkanak.weekview

import java.util.Calendar

private typealias EventsTriple<T> =
    Triple<List<WeekViewEvent<T>>?, List<WeekViewEvent<T>>?, List<WeekViewEvent<T>>?>
private fun <T> EventsTriple<T>.shiftLeft(): EventsTriple<T> = EventsTriple(second, third, null)
private fun <T> EventsTriple<T>.shiftRight(): EventsTriple<T> = EventsTriple(null, first, second)

/**
 * This class is responsible for loading [WeekViewEvent]s into [WeekView]. It uses
 * [OnMonthChangeListener.onMonthChange] to synchronously load events whenever the currently
 * displayed month changes. Asynchronous event loading can be performed via [AsyncLoader].
 */
internal class EventsLoader<T>(
    private val cache: EventCache<T>
) {

    var shouldRefreshEvents: Boolean = false
    var onMonthChangeListener: OnMonthChangeListener<T>? = null

    /**
     * Returns a list of [WeekViewEvent]s of the [FetchRange] around the first visible date, or
     * null if:
     *
     * a) No [OnMonthChangeListener] is registered
     *
     * b) Events don't need to be refreshed thanks to caching
     */
    fun loadEventsIfNecessary(firstVisibleDate: Calendar?): List<WeekViewEvent<T>>? {
        if (onMonthChangeListener == null) {
            // No OnMonthChangeListener is set. This is possible if WeekView.onLoadMore() is used
            // instead of an OnMonthChangeListener.
            return null
        }

        val hasNoEvents = cache.hasEvents.not()
        val firstVisibleDay = checkNotNull(firstVisibleDate)
        val fetchPeriods = FetchRange.create(firstVisibleDay)

        return if (hasNoEvents || shouldRefreshEvents || fetchPeriods !in cache) {
            shouldRefreshEvents = false
            loadEvents(fetchPeriods)
        } else {
            null
        }
    }

    private fun loadEvents(fetchRange: FetchRange): List<WeekViewEvent<T>> {
        if (shouldRefreshEvents) {
            cache.clear()
        }

        val oldFetchRange = cache.fetchedRange ?: fetchRange
        val newCurrentPeriod = fetchRange.current

        val periods = fetchRange.periods
        val events = EventsTriple(
            cache.previousPeriodEvents,
            cache.currentPeriodEvents,
            cache.nextPeriodEvents
        )

        val shiftedEvents = when (newCurrentPeriod) {
            oldFetchRange.previous -> events.shiftRight()
            oldFetchRange.next -> events.shiftLeft()
            else -> events
        }

        val periodsToBeLoaded = periods
            .zip(shiftedEvents.toList())
            .filter { it.second == null }
            .map { it.first }

        cache.fetchedRange = fetchRange

        val results = mutableListOf<WeekViewEvent<T>>()
        periodsToBeLoaded.forEach { period ->
            val periodEvents = loadEvents(period)
            cache[period] = periodEvents
            results += periodEvents
        }
        return results
    }

    private fun loadEvents(period: Period): List<WeekViewEvent<T>> {
        val listener = checkNotNull(onMonthChangeListener)

        val startDate = today()
            .withYear(period.year)
            .withMonth(period.month)
            .withDayOfMonth(1)

        val maxDays = startDate.lengthOfMonth
        val endDate = startDate
            .withDayOfMonth(maxDays)
            .atEndOfDay

        return listener
            .onMonthChange(startDate, endDate)
            .map { it.toWeekViewEvent() }
    }
}
