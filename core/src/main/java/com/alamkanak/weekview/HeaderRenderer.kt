package com.alamkanak.weekview

import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.Rect
import android.graphics.RectF
import android.text.StaticLayout
import android.util.SparseArray
import androidx.collection.ArrayMap
import java.util.Calendar
import kotlin.math.roundToInt

internal class HeaderRenderer(
    viewState: ViewState,
    eventChipsCache: EventChipsCache,
    onHeaderHeightChanged: () -> Unit
) : Renderer, DateFormatterDependent {

    private val allDayEventLabels = ArrayMap<EventChip, StaticLayout>()
    private val dateLabelLayouts = SparseArray<StaticLayout>()

    private val headerUpdater = HeaderUpdater(
        viewState = viewState,
        labelLayouts = dateLabelLayouts,
        onHeaderHeightChanged = onHeaderHeightChanged
    )

    private val eventsUpdater = AllDayEventsUpdater(
        viewState = viewState,
        eventsLabelLayouts = allDayEventLabels,
        eventChipsCache = eventChipsCache
    )

    private val dateLabelDrawer = DateLabelsDrawer(
        viewState = viewState,
        dateLabelLayouts = dateLabelLayouts
    )

    private val eventsDrawer = AllDayEventsDrawer(
        viewState = viewState,
        allDayEventLayouts = allDayEventLabels
    )

    private val headerDrawer = HeaderDrawer(
        viewState = viewState
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
        eventsUpdater.update()
        headerUpdater.update()

        headerDrawer.draw(canvas)
        dateLabelDrawer.draw(canvas)
        eventsDrawer.draw(canvas)
    }
}

private class HeaderUpdater(
    private val viewState: ViewState,
    private val labelLayouts: SparseArray<StaticLayout>,
    private val onHeaderHeightChanged: () -> Unit
) : Updater {

    private val animator = ValueAnimator()

    override fun update() {
        val missingDates = viewState.dateRange.filterNot { labelLayouts.hasKey(it.toEpochDays()) }
        for (date in missingDates) {
            val key = date.toEpochDays()
            labelLayouts.put(key, calculateStaticLayoutForDate(date))
        }

        val dateLabels = viewState.dateRange.map { labelLayouts[it.toEpochDays()] }
        updateHeaderHeight(dateLabels)
    }

    private fun updateHeaderHeight(
        dateLabels: List<StaticLayout>
    ) {
        val maximumLayoutHeight = dateLabels.map { it.height.toFloat() }.maxOrNull() ?: 0f
        viewState.dateLabelHeight = maximumLayoutHeight

        val currentHeaderHeight = viewState.headerHeight
        val newHeaderHeight = viewState.calculateHeaderHeight()

        if (currentHeaderHeight == 0f || currentHeaderHeight == newHeaderHeight) {
            // The height hasn't been set yet or didn't change; simply update without an animation
            viewState.updateHeaderHeight(newHeaderHeight)
            return
        }

        if (animator.isRunning) {
            // We're already running the animation to change the header height
            return
        }

        animator.animate(
            fromValue = currentHeaderHeight,
            toValue = newHeaderHeight,
            onUpdate = { height ->
                viewState.updateHeaderHeight(height)
                onHeaderHeightChanged()
            }
        )
    }

    private fun calculateStaticLayoutForDate(date: Calendar): StaticLayout {
        val dayLabel = viewState.dateFormatter(date)
        val textPaint = when {
            date.isToday -> viewState.todayHeaderTextPaint
            date.isWeekend -> viewState.weekendHeaderTextPaint
            else -> viewState.headerTextPaint
        }
        return dayLabel.toTextLayout(textPaint = textPaint, width = viewState.dayWidth.toInt())
    }

    private fun <E> SparseArray<E>.hasKey(key: Int): Boolean = indexOfKey(key) >= 0
}

private class DateLabelsDrawer(
    private val viewState: ViewState,
    private val dateLabelLayouts: SparseArray<StaticLayout>
) : Drawer {

    override fun draw(canvas: Canvas) {
        canvas.drawInBounds(viewState.headerBounds) {
            viewState.dateRangeWithStartPixels.forEach { (date, startPixel) ->
                drawLabel(date, startPixel)
            }
        }
    }

    private fun Canvas.drawLabel(day: Calendar, startPixel: Float) {
        val key = day.toEpochDays()
        val textLayout = dateLabelLayouts[key]

        withTranslation(
            x = startPixel + viewState.dayWidth / 2f,
            y = viewState.headerPadding
        ) {
            draw(textLayout)
        }
    }
}

private class AllDayEventsUpdater(
    private val viewState: ViewState,
    private val eventsLabelLayouts: ArrayMap<EventChip, StaticLayout>,
    private val eventChipsCache: EventChipsCache
) : Updater {

    private val boundsCalculator = EventChipBoundsCalculator(viewState)
    private val textFitter = TextFitter(viewState)

    private var previousHorizontalOrigin: Float? = null

    private val isRequired: Boolean
        get() {
            val didScrollHorizontally = previousHorizontalOrigin != viewState.currentOrigin.x
            val dateRange = viewState.dateRange
            val containsNewChips = eventChipsCache.allDayEventChipsInDateRange(dateRange).any { it.bounds.isEmpty }
            return didScrollHorizontally || containsNewChips
        }

    override fun update() {
        if (!isRequired) {
            return
        }

        eventsLabelLayouts.clear()

        val datesWithStartPixels = viewState.dateRangeWithStartPixels
        for ((date, startPixel) in datesWithStartPixels) {
            // If we use a horizontal margin in the day view, we need to offset the start pixel.
            val modifiedStartPixel = when {
                viewState.isSingleDay -> startPixel + viewState.singleDayHorizontalPadding.toFloat()
                else -> startPixel
            }

            val eventChips = eventChipsCache.allDayEventChipsByDate(date)

            eventChips.forEachIndexed { index, eventChip ->
                eventChip.updateBounds(index = index, startPixel = modifiedStartPixel)
                if (eventChip.bounds.isNotEmpty) {
                    eventsLabelLayouts[eventChip] = textFitter.fit(eventChip)
                } else {
                    eventsLabelLayouts.remove(eventChip)
                }
            }
        }

        val maximumChipHeight = eventsLabelLayouts.keys
            .map { it.bounds.height().roundToInt() }
            .maxOrNull() ?: 0

        viewState.currentAllDayEventHeight = maximumChipHeight

        val maximumChipsPerDay = eventsLabelLayouts.keys
            .groupBy { it.event.startTime.toEpochDays() }
            .values
            .maxByOrNull { it.size }?.size ?: 0

        viewState.maxNumberOfAllDayEvents = maximumChipsPerDay
    }

    private fun EventChip.updateBounds(index: Int, startPixel: Float) {
        val candidate = boundsCalculator.calculateAllDayEvent(index, eventChip = this, startPixel)
        bounds = if (candidate.isValid) candidate else RectF()
    }

    private val RectF.isValid: Boolean
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
        canvas.drawInBounds(viewState.headerBounds) {
            for ((eventChip, textLayout) in allDayEventLayouts) {
                eventChipDrawer.draw(eventChip, canvas, textLayout)
            }
        }
    }
}

private class HeaderDrawer(
    private val viewState: ViewState
) : Drawer {

    override fun draw(canvas: Canvas) {
        val width = viewState.viewWidth.toFloat()

        val backgroundPaint = if (viewState.showHeaderBottomShadow) {
            viewState.headerBackgroundWithShadowPaint
        } else {
            viewState.headerBackgroundPaint
        }

        canvas.drawRect(0f, 0f, width, viewState.headerHeight, backgroundPaint)

        if (viewState.showWeekNumber) {
            canvas.drawWeekNumber(viewState)
        }

        if (viewState.showHeaderBottomLine) {
            val y = viewState.headerHeight - viewState.headerBottomLinePaint.strokeWidth
            canvas.drawLine(0f, y, width, y, viewState.headerBottomLinePaint)
        }
    }

    private fun Canvas.drawWeekNumber(state: ViewState) {
        val weekNumber = state.dateRange.first().weekOfYear.toString()

        val bounds = state.weekNumberBounds
        val textPaint = state.weekNumberTextPaint

        val textHeight = textPaint.textHeight
        val textOffset = (textHeight / 2f).roundToInt() - textPaint.descent().roundToInt()

        val width = textPaint.getTextBounds("52").width() * 2.5f
        val height = textHeight * 1.5f

        val backgroundRect = RectF(
            bounds.centerX() - width / 2f,
            bounds.centerY() - height / 2f,
            bounds.centerX() + width / 2f,
            bounds.centerY() + height / 2f
        )

        drawRect(bounds, state.headerBackgroundPaint)

        val backgroundPaint = state.weekNumberBackgroundPaint
        val radius = state.weekNumberBackgroundCornerRadius
        drawRoundRect(backgroundRect, radius, radius, backgroundPaint)

        drawText(weekNumber, bounds.centerX(), bounds.centerY() + textOffset, textPaint)
    }
}

private val Paint.textHeight: Int
    get() = (descent() - ascent()).roundToInt()

private fun Paint.getTextBounds(text: String): Rect {
    val rect = Rect()
    getTextBounds(text, 0, text.length, rect)
    return rect
}
