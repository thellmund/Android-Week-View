package com.alamkanak.weekview

import androidx.collection.ArrayMap
import java.util.Calendar

internal class EventChipCache<T> {

    val allEventChips: List<EventChip<T>>
        get() = normalEventChipsByDate.values.flatten() + allDayEventChipsByDate.values.flatten()

    private val normalEventChipsByDate = ArrayMap<Calendar, MutableList<EventChip<T>>>()
    private val allDayEventChipsByDate = ArrayMap<Calendar, MutableList<EventChip<T>>>()

    fun groupedByDate(): Map<Calendar, List<EventChip<T>>> {
        return allEventChips.groupBy { it.event.startTime.atStartOfDay }
    }

    fun normalEventChipsByDate(date: Calendar): List<EventChip<T>> {
        return normalEventChipsByDate[date.atStartOfDay].orEmpty()
    }

    fun allDayEventChipsByDate(date: Calendar): List<EventChip<T>> {
        return allDayEventChipsByDate[date.atStartOfDay].orEmpty()
    }

    private fun put(newChips: List<EventChip<T>>) {
        val (allDay, normal) = newChips.partition { it.event.isAllDay }

        normal.forEach {
            val key = it.event.startTime.atStartOfDay
            normalEventChipsByDate.addOrReplace(key, it)
        }

        allDay.forEach {
            val key = it.event.startTime.atStartOfDay
            allDayEventChipsByDate.addOrReplace(key, it)
        }
    }

    operator fun plusAssign(newChips: List<EventChip<T>>) = put(newChips)

    fun clearCache() {
        allEventChips.filter { it.originalEvent.isNotAllDay }.forEach(EventChip<T>::clearCache)
    }

    fun clear() {
        allDayEventChipsByDate.clear()
        normalEventChipsByDate.clear()
    }
}
