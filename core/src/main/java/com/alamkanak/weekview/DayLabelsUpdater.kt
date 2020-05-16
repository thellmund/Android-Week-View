package com.alamkanak.weekview

import android.text.StaticLayout
import android.util.SparseArray
import java.util.Calendar

internal class DayLabelsUpdater<T>(
    private val config: WeekViewConfigWrapper,
    private val cache: WeekViewCache<T>,
    private val eventsCacheWrapper: EventsCacheWrapper<T>
) : Updater {

    private var previousHorizontalOrigin: Float? = null
    private val previousAllDayEventIds = mutableSetOf<Long>()

    private val eventsCache: EventsCache<T>
        get() = eventsCacheWrapper.get()

    override fun isRequired(drawingContext: DrawingContext): Boolean {
        val didScrollHorizontally = previousHorizontalOrigin != config.currentOrigin.x
        val currentTimeColumnWidth = config.timeTextWidth + config.timeColumnPadding * 2
        val didTimeColumnChange = currentTimeColumnWidth != config.timeColumnWidth
        val allDayEvents = eventsCache[drawingContext.dateRange]
            .filter { it.isAllDay }
            .map { it.id }
            .toSet()
        val didEventsChange = allDayEvents.hashCode() != previousAllDayEventIds.hashCode()
        return (didScrollHorizontally || didTimeColumnChange || didEventsChange).also {
            previousAllDayEventIds.clear()
            previousAllDayEventIds += allDayEvents
        }
    }

    override fun update(drawingContext: DrawingContext) {
        cache.dayLabelLayouts.clear()

        val textLayouts = drawingContext.dateRange.map { date ->
            date.toEpochDays() to calculateStaticLayoutForDate(date)
        }

        cache.dayLabelLayouts += textLayouts.toMap()

        val maximumLayoutHeight = textLayouts.map { it.second.height.toFloat() }.max() ?: 0f
        config.headerTextHeight = maximumLayoutHeight
        drawingContext.refreshHeaderHeight()
    }

    private fun DrawingContext.refreshHeaderHeight() {
        val visibleEvents = eventsCache[dateRange].filter { it.isAllDay }
        config.hasEventInHeader = visibleEvents.isNotEmpty()
        config.refreshHeaderHeight()
    }

    private fun calculateStaticLayoutForDate(date: Calendar): StaticLayout {
        val dayLabel = config.dateFormatter(date)
        return dayLabel.toTextLayout(
            textPaint = if (date.isToday) config.todayHeaderTextPaint else config.headerTextPaint,
            width = config.totalDayWidth.toInt()
        )
    }

    private operator fun <E> SparseArray<E>.plusAssign(elements: Map<Int, E>) {
        elements.entries.forEach { put(it.key, it.value) }
    }
}
