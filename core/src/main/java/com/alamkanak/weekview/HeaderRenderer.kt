package com.alamkanak.weekview

import android.graphics.Canvas
import android.graphics.RectF
import android.text.SpannableStringBuilder
import android.text.StaticLayout
import android.util.SparseArray
import androidx.collection.ArrayMap
import java.util.Calendar
import kotlin.math.roundToInt

internal class HeaderRenderer(
    viewState: ViewState,
    eventChipsCache: EventChipsCache
) : Renderer, DateFormatterDependent {

    private val allDayEventLabels = ArrayMap<EventChip, StaticLayout>()
    private val dateLabelLayouts = SparseArray<StaticLayout>()

    private val headerRowUpdater = HeaderRowUpdater(
        viewState = viewState,
        labelLayouts = dateLabelLayouts,
        eventChipsCache = eventChipsCache
    )

    private val dateLabelDrawer = DayLabelsDrawer(
        viewState = viewState,
        dateLabelLayouts = dateLabelLayouts
    )

    private val eventsUpdater = AllDayEventsUpdater(
        viewState = viewState,
        eventsLabelLayouts = allDayEventLabels,
        eventChipsCache = eventChipsCache
    )

    private val eventsDrawer = AllDayEventsDrawer(
        viewState = viewState,
        allDayEventLayouts = allDayEventLabels
    )

    override fun onSizeChanged(width: Int, height: Int) {
        allDayEventLabels.clear()
        dateLabelLayouts.clear()
    }

    override fun onDateFormatterChanged(formatter: DateFormatter) {
        allDayEventLabels.clear()
        dateLabelLayouts.clear()
    }

    override fun render(canvas: Canvas) {
        if (headerRowUpdater.isRequired()) {
            headerRowUpdater.update()
        }
        dateLabelDrawer.draw(canvas)

        if (eventsUpdater.isRequired()) {
            eventsUpdater.update()
        }
        eventsDrawer.draw(canvas)
    }
}

private class HeaderRowUpdater(
    private val viewState: ViewState,
    private val labelLayouts: SparseArray<StaticLayout>,
    private val eventChipsCache: EventChipsCache
) : Updater {

    private var previousHorizontalOrigin: Float? = null
    private val previousAllDayEventIds = mutableSetOf<Long>()

    override fun isRequired(): Boolean {
        val didScrollHorizontally = previousHorizontalOrigin != viewState.currentOrigin.x
        val currentTimeColumnWidth = viewState.timeTextWidth + viewState.timeColumnPadding * 2
        val didTimeColumnChange = currentTimeColumnWidth != viewState.timeColumnWidth
        val allDayEvents = eventChipsCache.allDayEventChipsInDateRange(viewState.dateRange)
            .map { it.eventId }
            .toSet()
        val didEventsChange = allDayEvents.hashCode() != previousAllDayEventIds.hashCode()
        return (didScrollHorizontally || didTimeColumnChange || didEventsChange).also {
            previousAllDayEventIds.clear()
            previousAllDayEventIds += allDayEvents
        }
    }

    override fun update() {
        val dateLabels = updateDateLabels(viewState)
        updateHeaderHeight(viewState, dateLabels)
    }

    private fun updateDateLabels(state: ViewState): List<StaticLayout> {
        val textLayouts = state.dateRange.map { date ->
            date.toEpochDays() to calculateStaticLayoutForDate(date)
        }.toMap()

        labelLayouts.clear()
        labelLayouts += textLayouts

        return textLayouts.values.toList()
    }

    private fun updateHeaderHeight(
        state: ViewState,
        dateLabels: List<StaticLayout>
    ) {
        val maximumLayoutHeight = dateLabels.map { it.height.toFloat() }.max() ?: 0f
        state.headerTextHeight = maximumLayoutHeight
        refreshHeaderHeight()
    }

    private fun refreshHeaderHeight() {
        val visibleEvents = eventChipsCache.allDayEventChipsInDateRange(viewState.dateRange)
        viewState.hasEventInHeader = visibleEvents.isNotEmpty()
        viewState.refreshHeaderHeight()
    }

    private fun calculateStaticLayoutForDate(date: Calendar): StaticLayout {
        val dayLabel = viewState.dateFormatter(date)
        return dayLabel.toTextLayout(
            textPaint = if (date.isToday) viewState.todayHeaderTextPaint else viewState.headerTextPaint,
            width = viewState.totalDayWidth.toInt()
        )
    }

    private operator fun <E> SparseArray<E>.plusAssign(elements: Map<Int, E>) {
        elements.entries.forEach { put(it.key, it.value) }
    }
}

private class DayLabelsDrawer(
    private val viewState: ViewState,
    private val dateLabelLayouts: SparseArray<StaticLayout>
) : Drawer {

    override fun draw(canvas: Canvas) {
        val left = viewState.timeColumnWidth
        val top = 0f
        val right = canvas.width.toFloat()
        val bottom = viewState.getTotalHeaderHeight()

        canvas.drawInRect(left, top, right, bottom) {
            viewState.dateRangeWithStartPixels.forEach { (date, startPixel) ->
                drawLabel(date, startPixel, this)
            }
        }
    }

    private fun drawLabel(day: Calendar, startPixel: Float, canvas: Canvas) {
        val key = day.toEpochDays()
        val textLayout = dateLabelLayouts[key]

        canvas.withTranslation(
            x = startPixel + viewState.widthPerDay / 2,
            y = viewState.headerRowPadding.toFloat()
        ) {
            textLayout.draw(this)
        }
    }
}

private class AllDayEventsUpdater(
    private val viewState: ViewState,
    private val eventsLabelLayouts: ArrayMap<EventChip, StaticLayout>,
    private val eventChipsCache: EventChipsCache
) : Updater {

    private val boundsCalculator = EventChipBoundsCalculator(viewState)
    private val spannableStringBuilder = SpannableStringBuilder()

    private var previousHorizontalOrigin: Float? = null
    private var dummyTextLayout: StaticLayout? = null

    override fun isRequired(): Boolean {
        val didScrollHorizontally = previousHorizontalOrigin != viewState.currentOrigin.x
        val dateRange = viewState.dateRange
        val containsNewChips = eventChipsCache.allDayEventChipsInDateRange(dateRange).any { it.bounds == null }
        return didScrollHorizontally || containsNewChips
    }

    override fun update() {
        eventsLabelLayouts.clear()

        val datesWithStartPixels = viewState.dateRangeWithStartPixels
        for ((date, startPixel) in datesWithStartPixels) {
            // If we use a horizontal margin in the day view, we need to offset the start pixel.
            val modifiedStartPixel = when {
                viewState.isSingleDay -> startPixel + viewState.eventMarginHorizontal.toFloat()
                else -> startPixel
            }

            val eventChips = eventChipsCache.allDayEventChipsByDate(date)
            for (eventChip in eventChips) {
                calculateTextLayout(eventChip, modifiedStartPixel)
            }
        }

        val maximumChipHeight = eventsLabelLayouts.keys
            .mapNotNull { it.bounds }
            .map { it.height().roundToInt() }
            .max() ?: 0

        viewState.updateAllDayEventHeight(maximumChipHeight)
    }

    private fun calculateTextLayout(
        eventChip: EventChip,
        startPixel: Float
    ) {
        val chipRect = boundsCalculator.calculateAllDayEvent(eventChip, startPixel)
        eventChip.bounds = if (chipRect.isValidEventBounds) chipRect else null

        if (chipRect.isValidEventBounds) {
            val textLayout = calculateChipTextLayout(eventChip)
            if (textLayout != null) {
                eventsLabelLayouts[eventChip] = textLayout
            }
        }
    }

    private fun calculateChipTextLayout(eventChip: EventChip): StaticLayout? {
        val event = eventChip.event
        val bounds = checkNotNull(eventChip.bounds)

        val fullHorizontalPadding = viewState.eventPaddingHorizontal * 2
        val fullVerticalPadding = viewState.eventPaddingVertical * 2

        val width = bounds.width() - fullHorizontalPadding
        val height = bounds.height() - fullVerticalPadding

        if (height < 0) {
            return null
        }

        if (width < 0) {
            // This happens if there are many all-day events
            val dummyTextLayout = createDummyTextLayout(event)
            val chipHeight = dummyTextLayout.height + fullVerticalPadding
            bounds.bottom = bounds.top + chipHeight
            return dummyTextLayout
        }

        spannableStringBuilder.clear()
        val title = event.title.emojify()
        spannableStringBuilder.append(title)

        val location = event.location?.emojify()
        if (location != null) {
            spannableStringBuilder.append(' ')
            spannableStringBuilder.append(location)
        }

        val text = spannableStringBuilder.build()
        val availableWidth = width.toInt()

        val textPaint = viewState.getTextPaint(event)
        val textLayout = text.toTextLayout(textPaint, availableWidth)
        val lineHeight = textLayout.height / textLayout.lineCount

        // For an all day event, we display just one line
        val chipHeight = lineHeight + fullVerticalPadding
        bounds.bottom = bounds.top + chipHeight

        return eventChip.ellipsizeText(text, availableWidth, existingTextLayout = textLayout)
    }

    /**
     * Creates a dummy text layout that is only used to determine the height of all-day events.
     */
    private fun createDummyTextLayout(
        event: ResolvedWeekViewEvent<*>
    ): StaticLayout {
        if (dummyTextLayout == null) {
            val textPaint = viewState.getTextPaint(event)
            dummyTextLayout = "".toTextLayout(textPaint, width = 0)
        }
        return checkNotNull(dummyTextLayout)
    }

    private fun EventChip.ellipsizeText(
        text: CharSequence,
        availableWidth: Int,
        existingTextLayout: StaticLayout
    ): StaticLayout {
        val textPaint = viewState.getTextPaint(event)
        val bounds = checkNotNull(bounds)
        val width = bounds.width().roundToInt() - (viewState.eventPaddingHorizontal * 2)

        val ellipsized = text.ellipsized(textPaint, availableWidth)
        val isTooSmallForText = width < 0
        if (isTooSmallForText) {
            // This day contains too many all-day events. We only draw the event chips,
            // but don't attempt to draw the event titles.
            return existingTextLayout
        }

        return ellipsized.toTextLayout(textPaint, width)
    }

    private val RectF.isValidEventBounds: Boolean
        get() = (left < right &&
            left < viewState.viewWidth &&
            top < viewState.viewHeight &&
            right > viewState.timeColumnWidth &&
            bottom > 0)
}

internal class AllDayEventsDrawer(
    private val viewState: ViewState,
    private val allDayEventLayouts: ArrayMap<EventChip, StaticLayout>
) : Drawer {

    private val eventChipDrawer = EventChipDrawer(viewState)

    override fun draw(canvas: Canvas) {
        val left = viewState.timeColumnWidth
        val top = 0f
        val right = canvas.width.toFloat()
        val bottom = viewState.getTotalHeaderHeight()

        canvas.drawInRect(left, top, right, bottom) {
            for ((eventChip, textLayout) in allDayEventLayouts) {
                eventChipDrawer.draw(eventChip, canvas, textLayout)
            }
        }
    }
}
