package com.alamkanak.weekview

import java.util.Calendar

private typealias EventsTriple<T> =
    Triple<List<WeekViewEvent<T>>?, List<WeekViewEvent<T>>?, List<WeekViewEvent<T>>?>
private fun <T> EventsTriple<T>.shiftLeft(): EventsTriple<T> = EventsTriple(second, third, null)
private fun <T> EventsTriple<T>.shiftRight(): EventsTriple<T> = EventsTriple(null, first, second)

internal class EventChipsProvider<T>(
    private val cache: EventCache<T>,
    private val eventSplitter: WeekViewEventSplitter<T>,
    private val chipCache: EventChipCache<T>
) {

    var shouldRefreshEvents: Boolean = false
    var monthLoader: MonthLoader<T>? = null

    fun loadEventsIfNecessary(firstVisibleDate: Calendar?) {
        val hasNoEvents = cache.hasEvents.not()
        val firstVisibleDay = checkNotNull(firstVisibleDate)
        val fetchPeriods = FetchRange.create(firstVisibleDay)

        if (hasNoEvents || shouldRefreshEvents || fetchPeriods !in cache) {
            loadEvents(fetchPeriods)
            shouldRefreshEvents = false
        }
    }

    private fun loadEvents(fetchRange: FetchRange) {
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

        val loader = monthLoader ?: return
        cache.fetchedRange = fetchRange

        periodsToBeLoaded.forEach { period ->
            val results = loader.load(period)
            createAndCacheEventChips(results)
            cache[period] = results
        }
    }

    // TODO: Move to EventChipCache?
    fun createAndCacheEventChips(vararg eventsLists: List<WeekViewEvent<T>>) {
        for (events in eventsLists) {
            chipCache += convertEventsToEventChips(events)
        }
    }

    private fun convertEventsToEventChips(
        events: List<WeekViewEvent<T>>
    ): List<EventChip<T>> = events.sorted().map(this::convertEventToEventChips).flatten()

    private fun convertEventToEventChips(
        event: WeekViewEvent<T>
    ): List<EventChip<T>> {
        if (event.startTime >= event.endTime) {
            return emptyList()
        }
        return eventSplitter.split(event).map { EventChip(it, event, null) }
    }
}
