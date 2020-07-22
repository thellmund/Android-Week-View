package com.alamkanak.weekview

import java.util.Calendar
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.LinkedBlockingQueue

internal class EventChipsCache<T> {

    val allEventChips: List<EventChip<T>>
        get() = normalEventChipsByDate.values.flatten() + allDayEventChipsByDate.values.flatten()

    private val normalEventChipsByDate = ConcurrentHashMap<Long, LinkedBlockingQueue<EventChip<T>>>()
    private val allDayEventChipsByDate = ConcurrentHashMap<Long, LinkedBlockingQueue<EventChip<T>>>()

    fun allEventChipsInDateRange(dateRange: List<Calendar>): List<EventChip<T>> {
        val results = mutableListOf<EventChip<T>>()
        for (date in dateRange) {
            results += allDayEventChipsByDate[date.atStartOfDay.timeInMillis].orEmpty()
            results += normalEventChipsByDate[date.atStartOfDay.timeInMillis].orEmpty()
        }
        return results
    }

    fun normalEventChipsByDate(
        date: Calendar
    ): Collection<EventChip<T>> = normalEventChipsByDate[date.atStartOfDay.timeInMillis] ?: mutableListOf()

    fun allDayEventChipsByDate(
        date: Calendar
    ): Collection<EventChip<T>> = allDayEventChipsByDate[date.atStartOfDay.timeInMillis] ?: mutableListOf()

    fun allDayEventChipsInDateRange(
        dateRange: List<Calendar>
    ): List<EventChip<T>> {
        val results = mutableListOf<EventChip<T>>()
        for (date in dateRange) {
            results += allDayEventChipsByDate[date.atStartOfDay.timeInMillis].orEmpty()
        }
        return results
    }

    private fun put(newChips: List<EventChip<T>>) {
        for (eventChip in newChips) {
            val key = eventChip.event.startTime.atStartOfDay.timeInMillis
            if (eventChip.event.isAllDay) {
                allDayEventChipsByDate.addOrReplace(key, eventChip)
            } else {
                normalEventChipsByDate.addOrReplace(key, eventChip)
            }
        }
    }

    operator fun plusAssign(newChips: List<EventChip<T>>) = put(newChips)

    fun clearSingleEventsCache() {
        allEventChips.filter { it.originalEvent.isNotAllDay }.forEach(EventChip<T>::clearCache)
    }

    fun clear() {
        allDayEventChipsByDate.clear()
        normalEventChipsByDate.clear()
    }

    private fun <T> ConcurrentHashMap<Long, LinkedBlockingQueue<EventChip<T>>>.addOrReplace(key: Long, eventChip: EventChip<T>) {
        val results = getOrElse(key) { LinkedBlockingQueue() }
        results.removeIf { it.event.id == eventChip.event.id }
        results.add(eventChip)

        this[key] = results
    }
}
