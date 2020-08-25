package com.alamkanak.weekview

import java.util.Calendar
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.LinkedBlockingQueue

internal class EventChipsCache {

    val allEventChips: List<EventChip>
        get() = normalEventChipsByDate.values.flatten() + allDayEventChipsByDate.values.flatten()

    private val normalEventChipsByDate = ConcurrentHashMap<Long, LinkedBlockingQueue<EventChip>>()
    private val allDayEventChipsByDate = ConcurrentHashMap<Long, LinkedBlockingQueue<EventChip>>()

    fun allEventChipsInDateRange(
        dateRange: List<Calendar>
    ): List<EventChip> {
        val results = mutableListOf<EventChip>()
        for (date in dateRange) {
            results += allDayEventChipsByDate[date.atStartOfDay.timeInMillis].orEmpty()
            results += normalEventChipsByDate[date.atStartOfDay.timeInMillis].orEmpty()
        }
        return results
    }

    fun normalEventChipsByDate(
        date: Calendar
    ): Collection<EventChip> = normalEventChipsByDate[date.atStartOfDay.timeInMillis] ?: mutableListOf()

    fun allDayEventChipsByDate(
        date: Calendar
    ): Collection<EventChip> = allDayEventChipsByDate[date.atStartOfDay.timeInMillis] ?: mutableListOf()

    fun allDayEventChipsInDateRange(
        dateRange: List<Calendar>
    ): List<EventChip> {
        val results = mutableListOf<EventChip>()
        for (date in dateRange) {
            results += allDayEventChipsByDate[date.atStartOfDay.timeInMillis].orEmpty()
        }
        return results
    }

    private fun put(newChips: List<EventChip>) {
        for (eventChip in newChips) {
            val key = eventChip.event.startTime.atStartOfDay.timeInMillis
            if (eventChip.event.isAllDay) {
                allDayEventChipsByDate.addOrReplace(key, eventChip)
            } else {
                normalEventChipsByDate.addOrReplace(key, eventChip)
            }
        }
    }

    operator fun plusAssign(newChips: List<EventChip>) = put(newChips)

    fun clearSingleEventsCache() {
        allEventChips.filter { it.originalEvent.isNotAllDay }.forEach(EventChip::clearCache)
    }

    fun clear() {
        allDayEventChipsByDate.clear()
        normalEventChipsByDate.clear()
    }

    private fun ConcurrentHashMap<Long, LinkedBlockingQueue<EventChip>>.addOrReplace(
        key: Long,
        eventChip: EventChip
    ) {
        val results = getOrElse(key) { LinkedBlockingQueue() }
        results.removeIf { it.event.id == eventChip.event.id }
        results.add(eventChip)

        this[key] = results
    }
}
