package com.alamkanak.weekview

import android.graphics.Rect
import android.os.Bundle
import androidx.core.view.accessibility.AccessibilityNodeInfoCompat
import androidx.core.view.accessibility.AccessibilityNodeInfoCompat.AccessibilityActionCompat
import androidx.customview.widget.ExploreByTouchHelper
import java.text.DateFormat.LONG
import java.text.DateFormat.SHORT
import java.text.SimpleDateFormat
import java.util.Calendar
import kotlin.math.roundToInt

internal class WeekViewAccessibilityTouchHelper<T : Any>(
    private val view: WeekView<T>,
    private val config: WeekViewConfigWrapper,
    private val drawingContext: DrawingContext,
    private val gestureHandler: WeekViewGestureHandler<T>,
    private val eventChipCache: EventChipCache<T>
) : ExploreByTouchHelper(view) {

    private val dateFormatter = SimpleDateFormat.getDateInstance(LONG)
    private val dateTimeFormatter = SimpleDateFormat.getDateTimeInstance(LONG, SHORT)

    private val touchHandler = WeekViewTouchHandler(config)
    private val store = VirtualViewIdStore<T>()

    override fun getVirtualViewAt(x: Float, y: Float): Int {
        // First, we check if an event chip was hit
        val eventChip = gestureHandler.findHitEvent(x, y)
        val eventChipVirtualViewId = eventChip?.let { store[it] }
        if (eventChipVirtualViewId != null) {
            return eventChipVirtualViewId
        }

        // If no event chip was hit, we still want to inform the user what date they
        // just interacted with
        val date = touchHandler.calculateTimeFromPoint(x, y)?.atStartOfDay
        val dateVirtualViewId = date?.let { store[it] }
        if (dateVirtualViewId != null) {
            return dateVirtualViewId
        }

        return HOST_ID
    }

    override fun getVisibleVirtualViews(virtualViewIds: MutableList<Int>) {
        val dateRange = drawingContext.dateRange
        val visibleEventChips = eventChipCache.allEventChipsInDateRange(dateRange)
        virtualViewIds += store.put(visibleEventChips)
        virtualViewIds += dateRange.map { store.put(it) }
    }

    override fun onPerformActionForVirtualView(
        virtualViewId: Int,
        action: Int,
        arguments: Bundle?
    ): Boolean {
        val eventChip = checkNotNull(store.findEventChip(virtualViewId))
        val data = checkNotNull(eventChip.originalEvent.data)
        val rect = checkNotNull(eventChip.rect)

        return when (action) {
            AccessibilityNodeInfoCompat.ACTION_CLICK -> {
                gestureHandler.onEventClickListener?.onEventClick(data, rect)
                true
            }
            AccessibilityNodeInfoCompat.ACTION_LONG_CLICK -> {
                gestureHandler.onEventLongClickListener?.onEventLongClick(data, rect)
                true
            }
            else -> false
        }
    }

    override fun onPopulateNodeForVirtualView(
        virtualViewId: Int,
        node: AccessibilityNodeInfoCompat
    ) {
        val eventChip = store.findEventChip(virtualViewId)
        if (eventChip != null) {
            populateNodeWithEventInfo(eventChip, node)
            return
        }

        val date = store.findDate(virtualViewId)
        if (date != null) {
            populateNodeWithDateInfo(date, node)
        }
    }

    private fun populateNodeWithEventInfo(
        eventChip: EventChip<T>,
        node: AccessibilityNodeInfoCompat
    ) {
        node.contentDescription = createDescriptionForVirtualView(eventChip.originalEvent)

        node.addAction(AccessibilityActionCompat.ACTION_CLICK)
        node.addAction(AccessibilityActionCompat.ACTION_LONG_CLICK)

        val bounds = Rect()
        eventChip.rect?.round(bounds)
        node.setBoundsInParent(bounds)
    }

    private fun populateNodeWithDateInfo(
        date: Calendar,
        node: AccessibilityNodeInfoCompat
    ) {
        node.contentDescription = createDescriptionForVirtualView(date)

        node.addAction(AccessibilityActionCompat.ACTION_CLICK)
        node.addAction(AccessibilityActionCompat.ACTION_LONG_CLICK)

        val dateWithStartPixel = drawingContext.dateRangeWithStartPixels
            .firstOrNull { it.first == date } ?: return

        val left = dateWithStartPixel.second.roundToInt()
        val right = left + config.totalDayWidth.roundToInt()
        val top = config.headerHeight.roundToInt()
        val bottom = view.height

        val bounds = Rect(left, top, right, bottom)
        node.setBoundsInParent(bounds)
    }

    private fun createDescriptionForVirtualView(event: WeekViewEvent<T>): String {
        val date = dateTimeFormatter.format(event.startTime.time)
        return "$date: ${event.title}, ${event.location}"
    }

    private fun createDescriptionForVirtualView(date: Calendar): String {
        return dateFormatter.format(date.time)
    }
}

private class VirtualViewIdStore<T> {

    private val eventChipsToVirtualViewIds = mutableMapOf<EventChip<T>, Int>()
    private val datesToVirtualViewIds = mutableMapOf<Calendar, Int>()
    private var maximumId = 0

    operator fun get(eventChip: EventChip<T>): Int? = eventChipsToVirtualViewIds[eventChip]

    operator fun get(date: Calendar): Int? = datesToVirtualViewIds[date]

    fun findEventChip(
        virtualViewId: Int
    ): EventChip<T>? = eventChipsToVirtualViewIds.entries.firstOrNull { it.value == virtualViewId }?.key

    fun findDate(
        virtualViewId: Int
    ): Calendar? = datesToVirtualViewIds.entries.firstOrNull { it.value == virtualViewId }?.key

    fun put(date: Calendar): Int {
        val startOfDay = date.atStartOfDay
        val existingVirtualViewId = datesToVirtualViewIds[date]

        return if (existingVirtualViewId != null) {
            existingVirtualViewId
        } else {
            datesToVirtualViewIds[startOfDay] = maximumId
            maximumId++
        }
    }

    fun put(eventChips: List<EventChip<T>>): List<Int> {
        val outdated = eventChipsToVirtualViewIds.keys - eventChips
        outdated.forEach { eventChipsToVirtualViewIds.remove(it) }

        val newEventChips = eventChips - eventChipsToVirtualViewIds.keys
        val virtualViewIds = mutableListOf<Int>()

        for (eventChip in newEventChips) {
            eventChipsToVirtualViewIds[eventChip] = maximumId
            virtualViewIds += maximumId
            maximumId++
        }

        return virtualViewIds
    }
}
