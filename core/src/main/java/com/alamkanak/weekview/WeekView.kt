package com.alamkanak.weekview

import android.annotation.SuppressLint
import android.content.Context
import android.graphics.Canvas
import android.graphics.RectF
import android.graphics.Typeface
import android.os.Parcelable
import android.util.AttributeSet
import android.view.MotionEvent
import android.view.View
import android.view.accessibility.AccessibilityManager
import androidx.core.view.ViewCompat
import java.util.Calendar
import kotlin.math.min
import kotlin.math.roundToInt

class WeekView @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null,
    defStyleAttr: Int = 0
) : View(context, attrs, defStyleAttr) {

    private val viewState: ViewState by lazy {
        ViewState.make(context, attrs)
    }

    private val eventChipsCache = EventChipsCache()

    private val touchHandler = WeekViewTouchHandler(viewState)
    private val gestureHandler = WeekViewGestureHandler(
        context = context,
        viewState = viewState,
        eventChipsCache = eventChipsCache,
        touchHandler = touchHandler,
        onInvalidation = { ViewCompat.postInvalidateOnAnimation(this) }
    )

    private var accessibilityTouchHelper = WeekViewAccessibilityTouchHelper(
        view = this,
        viewState = viewState,
        gestureHandler = gestureHandler,
        touchHandler = touchHandler,
        eventChipsCache = eventChipsCache
    )

    private val renderers: List<Renderer> = listOf(
        TimeColumnRenderer(viewState),
        CalendarRenderer(viewState, eventChipsCache),
        HeaderRenderer(viewState, eventChipsCache)
    )

    init {
        val accessibilityManager =
            context.getSystemService(Context.ACCESSIBILITY_SERVICE) as AccessibilityManager

        val isAccessibilityEnabled = accessibilityManager.isEnabled
        val isExploreByTouchEnabled = accessibilityManager.isTouchExplorationEnabled

        val isAccessibilityHelperActive = isAccessibilityEnabled && isExploreByTouchEnabled
        if (isAccessibilityHelperActive) {
            ViewCompat.setAccessibilityDelegate(this, accessibilityTouchHelper)
        }

        setLayerType(LAYER_TYPE_SOFTWARE, null)
    }

    override fun onDraw(canvas: Canvas) {
        super.onDraw(canvas)
        performPendingScrolls()
        updateDataHolders()
        notifyScrollListeners()
        refreshEvents()
        performRendering(canvas)
    }

    private fun performPendingScrolls() {
        val pendingDateScroll = viewState.scrollToDate
        viewState.scrollToDate = null
        pendingDateScroll?.let { date ->
            goToDate(date)
        }

        val pendingHourScroll = viewState.scrollToHour
        viewState.scrollToHour = null
        pendingHourScroll?.let { hour ->
            goToHour(hour)
        }
    }

    private fun updateDataHolders() {
        viewState.update()
    }

    private fun refreshEvents() {
        if (isInEditMode) {
            return
        }

        val pagedAdapter = adapter as? PagingAdapter ?: return
        pagedAdapter.triggerEventsFetching(firstVisibleDate)
    }

    private fun performRendering(canvas: Canvas) {
        for (renderer in renderers) {
            renderer.render(canvas)
        }
    }

    override fun onSaveInstanceState(): Parcelable? {
        val superState = super.onSaveInstanceState()
        return superState?.let {
            SavedState(it, viewState.numberOfVisibleDays, viewState.firstVisibleDate)
        }
    }

    override fun onRestoreInstanceState(state: Parcelable) {
        val savedState = state as SavedState
        super.onRestoreInstanceState(savedState.superState)

        if (this.viewState.restoreNumberOfVisibleDays) {
            this.viewState.numberOfVisibleDays = savedState.numberOfVisibleDays
        }

        goToDate(savedState.firstVisibleDate)
    }

    override fun onSizeChanged(width: Int, height: Int, oldWidth: Int, oldHeight: Int) {
        super.onSizeChanged(width, height, oldWidth, oldHeight)
        viewState.onSizeChanged(width, height)
        renderers.forEach { it.onSizeChanged(width, height) }
    }

    private fun notifyScrollListeners() {
        val oldFirstVisibleDay = viewState.firstVisibleDate
        val totalDayWidth = viewState.totalDayWidth
        val visibleDays = viewState.numberOfVisibleDays

        val daysScrolled = viewState.currentOrigin.x / totalDayWidth
        val delta = daysScrolled.roundToInt() * (-1)

        val firstVisibleDate = today() + Days(delta)
        viewState.firstVisibleDate = firstVisibleDate

        val hasFirstVisibleDayChanged = oldFirstVisibleDay.toEpochDays() != firstVisibleDate.toEpochDays()
        if (hasFirstVisibleDayChanged) {
            val lastVisibleDate = firstVisibleDate + Days(visibleDays - 1)
            adapter?.onRangeChanged(firstVisibleDate, lastVisibleDate)
            adapter?.onFirstVisibleDateChanged(firstVisibleDate)
        }
    }

    override fun invalidate() {
        viewState.invalidate()
        super.invalidate()
    }

    /*
     ***********************************************************************************************
     *
     *   Calendar configuration
     *
     ***********************************************************************************************
     */

    /**
     * Returns the first day of the week. Possible values are [java.util.Calendar.SUNDAY],
     * [java.util.Calendar.MONDAY], [java.util.Calendar.TUESDAY],
     * [java.util.Calendar.WEDNESDAY], [java.util.Calendar.THURSDAY],
     * [java.util.Calendar.FRIDAY], [java.util.Calendar.SATURDAY].
     */
    @PublicApi
    var firstDayOfWeek: Int
        get() = viewState.firstDayOfWeek
        set(value) {
            viewState.firstDayOfWeek = value
            invalidate()
        }

    /**
     * Returns the number of visible days.
     */
    @PublicApi
    var numberOfVisibleDays: Int
        get() = viewState.numberOfVisibleDays
        set(value) {
            viewState.updateNumberOfVisibleDays(value)
            dateTimeInterpreter.onSetNumberOfDays(value)
            renderers.filterIsInstance(DateFormatterDependent::class.java).forEach {
                it.onDateFormatterChanged(viewState.dateFormatter)
            }
            invalidate()
        }

    /**
     * Returns whether the first day of the week should be displayed at the left-most position
     * when WeekView is displayed for the first time.
     */
    @PublicApi
    var isShowFirstDayOfWeekFirst: Boolean
        get() = viewState.showFirstDayOfWeekFirst
        set(value) {
            viewState.showFirstDayOfWeekFirst = value
        }

    /*
     ***********************************************************************************************
     *
     *   Header bottom line
     *
     ***********************************************************************************************
     */

    @PublicApi
    var isShowHeaderRowBottomLine: Boolean
        /**
         * Returns whether a horizontal line should be displayed at the bottom of the header row.
         */
        get() = viewState.showHeaderRowBottomLine
        /**
         * Sets whether a horizontal line should be displayed at the bottom of the header row.
         */
        set(value) {
            viewState.showHeaderRowBottomLine = value
            invalidate()
        }

    @PublicApi
    var headerRowBottomLineColor: Int
        /**
         * Returns the color of the horizontal line at the bottom of the header row.
         */
        get() = viewState.headerRowBottomLinePaint.color
        /**
         * Sets the color of the horizontal line at the bottom of the header row. Whether the line
         * is displayed, is determined by [isShowHeaderRowBottomLine]
         */
        set(value) {
            viewState.headerRowBottomLinePaint.color = value
            invalidate()
        }

    @PublicApi
    var headerRowBottomLineWidth: Int
        /**
         * Returns the stroke width of the horizontal line at the bottom of the header row.
         */
        get() = viewState.headerRowBottomLinePaint.strokeWidth.toInt()
        /**
         * Sets the stroke width of the horizontal line at the bottom of the header row. Whether the
         * line is displayed, is determined by [isShowHeaderRowBottomLine]
         */
        set(value) {
            viewState.headerRowBottomLinePaint.strokeWidth = value.toFloat()
            invalidate()
        }

    /*
     ***********************************************************************************************
     *
     *   Time column
     *
     ***********************************************************************************************
     */

    /**
     * Returns the padding in the time column to the left and right side of the time label.
     */
    @PublicApi
    var timeColumnPadding: Int
        get() = viewState.timeColumnPadding
        set(value) {
            viewState.timeColumnPadding = value
            invalidate()
        }

    /**
     * Returns the text color of the labels in the time column.
     */
    @PublicApi
    var timeColumnTextColor: Int
        get() = viewState.timeColumnTextColor
        set(value) {
            viewState.timeColumnTextColor = value
            invalidate()
        }

    /**
     * Returns the background color of the time column.
     */
    @PublicApi
    var timeColumnBackgroundColor: Int
        get() = viewState.timeColumnBackgroundColor
        set(value) {
            viewState.timeColumnBackgroundColor = value
            invalidate()
        }

    /**
     * Returns the text size of the labels in the time column.
     */
    @PublicApi
    var timeColumnTextSize: Int
        get() = viewState.timeColumnTextSize
        set(value) {
            viewState.timeColumnTextSize = value
            invalidate()
        }

    /**
     * Returns whether the label for the midnight hour is displayed in the time column. This setting
     * is only considered if [isShowTimeColumnHourSeparator] is set to true.
     */
    @PublicApi
    var isShowMidnightHour: Boolean
        get() = viewState.showMidnightHour
        set(value) {
            viewState.showMidnightHour = value
            invalidate()
        }

    /**
     * Returns whether a horizontal line is displayed for each hour in the time column.
     */
    @PublicApi
    var isShowTimeColumnHourSeparator: Boolean
        get() = viewState.showTimeColumnHourSeparator
        set(value) {
            viewState.showTimeColumnHourSeparator = value
            invalidate()
        }

    /**
     * Returns the interval in which time labels are displayed in the time column.
     */
    @PublicApi
    var timeColumnHoursInterval: Int
        get() = viewState.timeColumnHoursInterval
        set(value) {
            viewState.timeColumnHoursInterval = value
            invalidate()
        }

    /*
     ***********************************************************************************************
     *
     *   Time column separator
     *
     ***********************************************************************************************
     */

    /**
     * Returns whether a vertical line is displayed at the end of the time column.
     */
    @PublicApi
    var isShowTimeColumnSeparator: Boolean
        get() = viewState.showTimeColumnSeparator
        set(value) {
            viewState.showTimeColumnSeparator = value
            invalidate()
        }

    /**
     * Returns the color of the time column separator.
     */
    @PublicApi
    var timeColumnSeparatorColor: Int
        get() = viewState.timeColumnSeparatorColor
        set(value) {
            viewState.timeColumnSeparatorColor = value
            invalidate()
        }

    /**
     * Returns the stroke width of the time column separator.
     */
    @PublicApi
    var timeColumnSeparatorWidth: Int
        get() = viewState.timeColumnSeparatorStrokeWidth
        set(value) {
            viewState.timeColumnSeparatorStrokeWidth = value
            invalidate()
        }

    /*
     ***********************************************************************************************
     *
     *   Header row
     *
     ***********************************************************************************************
     */

    /**
     * Returns the header row padding, which is applied above and below the all-day event chips.
     */
    @PublicApi
    var headerRowPadding: Int
        get() = viewState.headerRowPadding
        set(value) {
            viewState.headerRowPadding = value
            invalidate()
        }

    /**
     * Returns the header row background color.
     */
    @PublicApi
    var headerRowBackgroundColor: Int
        get() = viewState.headerRowBackgroundColor
        set(value) {
            viewState.headerRowBackgroundColor = value
            invalidate()
        }

    /**
     * Returns the text color used for all date labels except today.
     */
    @PublicApi
    var headerRowTextColor: Int
        get() = viewState.headerRowTextColor
        set(value) {
            viewState.headerRowTextColor = value
            invalidate()
        }

    /**
     * Returns the text color used for today's date label.
     */
    @PublicApi
    var todayHeaderTextColor: Int
        get() = viewState.todayHeaderTextColor
        set(value) {
            viewState.todayHeaderTextColor = value
            invalidate()
        }

    /**
     * Returns the text size of all date labels.
     */
    @PublicApi
    var headerRowTextSize: Int
        get() = viewState.headerRowTextSize
        set(value) {
            viewState.headerRowTextSize = value
            invalidate()
        }

    /*
     ***********************************************************************************************
     *
     *   Event chips
     *
     ***********************************************************************************************
     */

    /**
     * Returns the corner radius of an [EventChip].
     */
    @PublicApi
    var eventCornerRadius: Int
        get() = viewState.eventCornerRadius
        set(value) {
            viewState.eventCornerRadius = value
            invalidate()
        }

    /**
     * Returns the text size of a single-event [EventChip].
     */
    @PublicApi
    var eventTextSize: Int
        get() = viewState.eventTextPaint.textSize.toInt()
        set(value) {
            viewState.eventTextPaint.textSize = value.toFloat()
            invalidate()
        }

    /**
     * Returns whether the text size of the [EventChip] is adapting to the [EventChip] height.
     */
    @PublicApi
    var isAdaptiveEventTextSize: Boolean
        get() = viewState.adaptiveEventTextSize
        set(value) {
            viewState.adaptiveEventTextSize = value
            invalidate()
        }

    /**
     * Returns the text size of an all-day [EventChip].
     */
    @PublicApi
    var allDayEventTextSize: Int
        get() = viewState.allDayEventTextPaint.textSize.toInt()
        set(value) {
            viewState.allDayEventTextPaint.textSize = value.toFloat()
            invalidate()
        }

    /**
     * Returns the default text color of an [EventChip].
     */
    @PublicApi
    var defaultEventTextColor: Int
        get() = viewState.eventTextPaint.color
        set(value) {
            viewState.eventTextPaint.color = value
            invalidate()
        }

    /**
     * Returns the horizontal padding within an [EventChip].
     */
    @PublicApi
    var eventPaddingHorizontal: Int
        get() = viewState.eventPaddingHorizontal
        set(value) {
            viewState.eventPaddingHorizontal = value
            invalidate()
        }

    /**
     * Returns the vertical padding within an [EventChip].
     */
    @PublicApi
    var eventPaddingVertical: Int
        get() = viewState.eventPaddingVertical
        set(value) {
            viewState.eventPaddingVertical = value
            invalidate()
        }

    /**
     * Returns the default text color of an [EventChip].
     */
    @PublicApi
    var defaultEventColor: Int
        get() = viewState.defaultEventColor
        set(value) {
            viewState.defaultEventColor = value
            invalidate()
        }

    /*
     ***********************************************************************************************
     *
     *   Event margins
     *
     ***********************************************************************************************
     */

    /**
     * Returns the column gap at the end of each day.
     */
    @PublicApi
    var columnGap: Int
        get() = viewState.columnGap
        set(value) {
            viewState.columnGap = value
            invalidate()
        }

    /**
     * Returns the horizontal gap between overlapping [EventChip]s.
     */
    @PublicApi
    var overlappingEventGap: Int
        get() = viewState.overlappingEventGap
        set(value) {
            viewState.overlappingEventGap = value
            invalidate()
        }

    /**
     * Returns the vertical margin of an [EventChip].
     */
    @PublicApi
    var eventMarginVertical: Int
        get() = viewState.eventMarginVertical
        set(value) {
            viewState.eventMarginVertical = value
            invalidate()
        }

    /**
     * Returns the horizontal margin of an [EventChip]. This margin is only applied in single-day
     * view and if there are no overlapping events.
     */
    @PublicApi
    var eventMarginHorizontal: Int
        get() = viewState.eventMarginHorizontal
        set(value) {
            viewState.eventMarginHorizontal = value
            invalidate()
        }

    /*
     ***********************************************************************************************
     *
     *   Colors
     *
     ***********************************************************************************************
     */

    /**
     * Returns the background color of a day.
     */
    @PublicApi
    var dayBackgroundColor: Int
        get() = viewState.dayBackgroundPaint.color
        set(value) {
            viewState.dayBackgroundPaint.color = value
            invalidate()
        }

    /**
     * Returns the background color of the current date.
     */
    @PublicApi
    var todayBackgroundColor: Int
        get() = viewState.todayBackgroundPaint.color
        set(value) {
            viewState.todayBackgroundPaint.color = value
            invalidate()
        }

    /**
     * Returns whether weekends should have a background color different from [dayBackgroundColor].
     *
     * The weekend background colors can be defined by [pastWeekendBackgroundColor] and
     * [futureWeekendBackgroundColor].
     */
    @PublicApi
    var isShowDistinctWeekendColor: Boolean
        get() = viewState.showDistinctWeekendColor
        set(value) {
            viewState.showDistinctWeekendColor = value
            invalidate()
        }

    /**
     * Returns whether past and future days should have background colors different from
     * [dayBackgroundColor].
     *
     * The past and future day colors can be defined by [pastBackgroundColor] and
     * [futureBackgroundColor].
     */
    @PublicApi
    var isShowDistinctPastFutureColor: Boolean
        get() = viewState.showDistinctPastFutureColor
        set(value) {
            viewState.showDistinctPastFutureColor = value
            invalidate()
        }

    /**
     * Returns the background color for past dates. If not explicitly set, WeekView will used
     * [dayBackgroundColor].
     */
    @PublicApi
    var pastBackgroundColor: Int
        get() = viewState.pastBackgroundPaint.color
        set(value) {
            viewState.pastBackgroundPaint.color = value
            invalidate()
        }

    /**
     * Returns the background color for past weekend dates. If not explicitly set, WeekView will
     * used [pastBackgroundColor].
     */
    @PublicApi
    var pastWeekendBackgroundColor: Int
        get() = viewState.pastWeekendBackgroundPaint.color
        set(value) {
            viewState.pastWeekendBackgroundPaint.color = value
            invalidate()
        }

    /**
     * Returns the background color for future dates. If not explicitly set, WeekView will used
     * [dayBackgroundColor].
     */
    @PublicApi
    var futureBackgroundColor: Int
        get() = viewState.futureBackgroundPaint.color
        set(value) {
            viewState.futureBackgroundPaint.color = value
            invalidate()
        }

    /**
     * Returns the background color for future weekend dates. If not explicitly set, WeekView will
     * used [futureBackgroundColor].
     */
    @PublicApi
    var futureWeekendBackgroundColor: Int
        get() = viewState.futureWeekendBackgroundPaint.color
        set(value) {
            viewState.futureWeekendBackgroundPaint.color = value
            invalidate()
        }

    /*
     ***********************************************************************************************
     *
     *   Hour height
     *
     ***********************************************************************************************
     */

    /**
     * Returns the current height of an hour.
     */
    @PublicApi
    var hourHeight: Float
        get() = viewState.hourHeight
        set(value) {
            viewState.newHourHeight = value
            invalidate()
        }

    /**
     * Returns the minimum height of an hour.
     */
    @PublicApi
    var minHourHeight: Int
        get() = viewState.minHourHeight
        set(value) {
            viewState.minHourHeight = value
            invalidate()
        }

    /**
     * Returns the maximum height of an hour.
     */
    @PublicApi
    var maxHourHeight: Int
        get() = viewState.maxHourHeight
        set(value) {
            viewState.maxHourHeight = value
            invalidate()
        }

    /**
     * Returns whether the complete day should be shown, in which case [hourHeight] automatically
     * adjusts to accommodate all hours between [minHour] and [maxHour].
     */
    @PublicApi
    var isShowCompleteDay: Boolean
        get() = viewState.showCompleteDay
        set(value) {
            viewState.showCompleteDay = value
            invalidate()
        }

    /*
     ***********************************************************************************************
     *
     *   Now line
     *
     ***********************************************************************************************
     */

    /**
     * Returns whether a horizontal line should be displayed at the current time.
     */
    @PublicApi
    var isShowNowLine: Boolean
        get() = viewState.showNowLine
        set(value) {
            viewState.showNowLine = value
            invalidate()
        }

    /**
     * Returns the color of the horizontal "now" line.
     */
    @PublicApi
    var nowLineColor: Int
        get() = viewState.nowLinePaint.color
        set(value) {
            viewState.nowLinePaint.color = value
            invalidate()
        }

    /**
     * Returns the stroke width of the horizontal "now" line.
     */
    @PublicApi
    var nowLineStrokeWidth: Int
        get() = viewState.nowLinePaint.strokeWidth.toInt()
        set(value) {
            viewState.nowLinePaint.strokeWidth = value.toFloat()
            invalidate()
        }

    /**
     * Returns whether a dot at the start of the "now" line is displayed. The dot is only displayed
     * if [isShowNowLine] is set to true.
     */
    @PublicApi
    var isShowNowLineDot: Boolean
        get() = viewState.showNowLineDot
        set(value) {
            viewState.showNowLineDot = value
            invalidate()
        }

    /**
     * Returns the color of the dot at the start of the "now" line.
     */
    @PublicApi
    var nowLineDotColor: Int
        get() = viewState.nowDotPaint.color
        set(value) {
            viewState.nowDotPaint.color = value
            invalidate()
        }

    /**
     * Returns the radius of the dot at the start of the "now" line.
     */
    @PublicApi
    var nowLineDotRadius: Int
        get() = viewState.nowDotPaint.strokeWidth.toInt()
        set(value) {
            viewState.nowDotPaint.strokeWidth = value.toFloat()
            invalidate()
        }

    /*
     ***********************************************************************************************
     *
     *   Hour separators
     *
     ***********************************************************************************************
     */

    @PublicApi
    var isShowHourSeparators: Boolean
        get() = viewState.showHourSeparators
        set(value) {
            viewState.showHourSeparators = value
            invalidate()
        }

    @PublicApi
    var hourSeparatorColor: Int
        get() = viewState.hourSeparatorPaint.color
        set(value) {
            viewState.hourSeparatorPaint.color = value
            invalidate()
        }

    @PublicApi
    var hourSeparatorStrokeWidth: Int
        get() = viewState.hourSeparatorPaint.strokeWidth.toInt()
        set(value) {
            viewState.hourSeparatorPaint.strokeWidth = value.toFloat()
            invalidate()
        }

    /*
     ***********************************************************************************************
     *
     *   Day separators
     *
     ***********************************************************************************************
     */

    /**
     * Returns whether vertical lines are displayed as separators between dates.
     */
    @PublicApi
    var isShowDaySeparators: Boolean
        get() = viewState.showDaySeparators
        set(value) {
            viewState.showDaySeparators = value
            invalidate()
        }

    /**
     * Returns the color of the separators between dates.
     */
    @PublicApi
    var daySeparatorColor: Int
        get() = viewState.daySeparatorPaint.color
        set(value) {
            viewState.daySeparatorPaint.color = value
            invalidate()
        }

    /**
     * Returns the stroke color of the separators between dates.
     */
    @PublicApi
    var daySeparatorStrokeWidth: Int
        get() = viewState.daySeparatorPaint.strokeWidth.toInt()
        set(value) {
            viewState.daySeparatorPaint.strokeWidth = value.toFloat()
            invalidate()
        }

    /*
     ***********************************************************************************************
     *
     *   Date range
     *
     ***********************************************************************************************
     */

    /**
     * Returns the minimum date that [WeekView] will display, or null if none is set. Events before
     * this date will not be shown.
     */
    @PublicApi
    var minDate: Calendar?
        get() = viewState.minDate?.copy()
        set(value) {
            val maxDate = viewState.maxDate
            if (maxDate != null && value != null && value.isAfter(maxDate)) {
                throw IllegalArgumentException("Can't set a minDate that's after maxDate")
            }

            viewState.minDate = value
            invalidate()
        }

    /**
     * Returns the maximum date that [WeekView] will display, or null if none is set. Events after
     * this date will not be shown.
     */
    @PublicApi
    var maxDate: Calendar?
        get() = viewState.maxDate?.copy()
        set(value) {
            val minDate = viewState.minDate
            if (minDate != null && value != null && value.isBefore(minDate)) {
                throw IllegalArgumentException("Can't set a maxDate that's before minDate")
            }

            viewState.maxDate = value
            invalidate()
        }

    /*
     ***********************************************************************************************
     *
     *   Time range
     *
     ***********************************************************************************************
     */

    /**
     * Returns the minimum hour that [WeekView] will display. Events before this time will not be
     * shown.
     */
    @PublicApi
    var minHour: Int
        get() = viewState.minHour
        set(value) {
            if (value < 0 || value > viewState.maxHour) {
                throw IllegalArgumentException("minHour must be between 0 and maxHour.")
            }

            viewState.minHour = value
            invalidate()
        }

    /**
     * Returns the maximum hour that [WeekView] will display. Events before this time will not be
     * shown.
     */
    @PublicApi
    var maxHour: Int
        get() = viewState.maxHour
        set(value) {
            if (value > 24 || value < viewState.minHour) {
                throw IllegalArgumentException("maxHour must be between minHour and 24.")
            }

            viewState.maxHour = value
            invalidate()
        }

    /*
     ***********************************************************************************************
     *
     *   Scrolling
     *
     ***********************************************************************************************
     */

    /**
     * Returns the scrolling speed factor in horizontal direction.
     */
    @PublicApi
    @Deprecated("This value is no longer being taken into account.")
    var xScrollingSpeed: Float
        get() = viewState.xScrollingSpeed
        set(value) {
            viewState.xScrollingSpeed = value
        }

    /**
     * Returns whether WeekView can fling horizontally.
     */
    @PublicApi
    @Deprecated(
        message = "Use isHorizontalScrollingEnabled instead.",
        replaceWith = ReplaceWith("isHorizontalScrollingEnabled")
    )
    var isHorizontalFlingEnabled: Boolean
        get() = viewState.horizontalFlingEnabled
        set(value) {
            viewState.horizontalFlingEnabled = value
        }

    /**
     * Returns whether WeekView can scroll horizontally.
     */
    @PublicApi
    var isHorizontalScrollingEnabled: Boolean
        get() = viewState.horizontalScrollingEnabled
        set(value) {
            viewState.horizontalScrollingEnabled = value
        }

    /**
     * Returns whether WeekView can fling vertically.
     */
    @Deprecated("This value is no longer being taken into account.")
    @PublicApi
    var isVerticalFlingEnabled: Boolean
        get() = viewState.verticalFlingEnabled
        set(value) {
            viewState.verticalFlingEnabled = value
        }

    @PublicApi
    @Deprecated("This value is no longer being taken into account.")
    var scrollDuration: Int
        get() = viewState.scrollDuration
        set(value) {
            viewState.scrollDuration = value
        }

    @SuppressLint("ClickableViewAccessibility")
    override fun onTouchEvent(event: MotionEvent): Boolean = gestureHandler.onTouchEvent(event)

    /*
     ***********************************************************************************************
     *
     *   Date methods
     *
     ***********************************************************************************************
     */

    /**
     * Returns the first visible date.
     */
    @PublicApi
    val firstVisibleDate: Calendar
        get() = viewState.firstVisibleDate.copy()

    /**
     * Returns the last visible date.
     */
    @PublicApi
    val lastVisibleDate: Calendar
        get() = viewState.firstVisibleDate.copy() + Days(viewState.numberOfVisibleDays - 1)

    /**
     * Shows the current date.
     */
    @PublicApi
    fun goToToday() {
        goToDate(today())
    }

    /**
     * Shows the current date and time.
     */
    @PublicApi
    fun goToCurrentTime() {
        now().apply {
            goToDate(this)
            goToHour(hour)
        }
    }

    /**
     * Shows a specific date. If it is before [minDate] or after [maxDate], these will be shown
     * instead.
     *
     * @param date The date to show.
     */
    @PublicApi
    fun goToDate(date: Calendar) {
        val adjustedDate = viewState.getDateWithinDateRange(date)
        gestureHandler.forceScrollFinished()

        val isWaitingToBeLaidOut = ViewCompat.isLaidOut(this).not()
        if (viewState.areDimensionsInvalid || isWaitingToBeLaidOut) {
            // If the view's dimensions have just changed or if it hasn't been laid out yet, we
            // postpone the action until onDraw() is called the next time.
            viewState.scrollToDate = adjustedDate
            return
        }

        adapter?.requireRefresh()

        val diff = adjustedDate.daysFromToday
        viewState.currentOrigin.x = diff.toFloat() * (-1f) * viewState.totalDayWidth
        invalidate()
    }

    /**
     * Refreshes the view and loads the events again.
     */
    @PublicApi
    fun notifyDataSetChanged() {
        adapter?.requireRefresh()
        invalidate()
    }

    /**
     * Scrolls to a specific hour.
     *
     * @param hour The hour to scroll to, in 24-hour format. Supported values are 0-24.
     *
     * @throws IllegalArgumentException Throws exception if the provided hour is smaller than
     *                                   [minHour] or larger than [maxHour].
     */
    @PublicApi
    fun goToHour(hour: Int) {
        if (viewState.areDimensionsInvalid) {
            viewState.scrollToHour = hour
            return
        }

        if (hour !in viewState.minHour..viewState.maxHour) {
            throw IllegalArgumentException(
                "The provided hour ($hour) is outside of the set time range " +
                    "(${viewState.minHour} – ${viewState.maxHour})"
            )
        }

        val hourHeight = viewState.hourHeight
        val desiredOffset = hourHeight * (hour - viewState.minHour)

        // We make sure that WeekView doesn't "over-scroll" by limiting the offset to the total day
        // height minus the height of WeekView, which would result in scrolling all the way to the
        // bottom.
        val maxOffset = viewState.totalDayHeight - height
        val finalOffset = min(maxOffset, desiredOffset)

        viewState.currentOrigin.y = finalOffset * (-1)
        invalidate()
    }

    /**
     * Returns the first hour that is visible on the screen.
     */
    @PublicApi
    val firstVisibleHour: Double
        get() = (viewState.currentOrigin.y * -1 / viewState.hourHeight).toDouble()

    /*
     ***********************************************************************************************
     *
     *   Typeface
     *
     ***********************************************************************************************
     */

    /**
     * Returns the typeface used for events, time labels and date labels.
     */
    @PublicApi
    var typeface: Typeface
        get() = viewState.typeface
        set(value) {
            viewState.typeface = value
            invalidate()
        }

    /*
     ***********************************************************************************************
     *
     *   Adapter
     *
     ***********************************************************************************************
     */

    private var internalAdapter: Adapter<*>? = null

    var adapter: Adapter<*>?
        get() = internalAdapter
        set(value) {
            setAdapterInternal(value)
        }

    private fun setAdapterInternal(adapter: Adapter<*>?) {
        if (adapter == null) {
            internalAdapter?.unregisterObserver()
            touchHandler.adapter = null
            return
        }

        adapter.eventChipsCache = eventChipsCache
        internalAdapter = adapter
        touchHandler.adapter = adapter

        internalAdapter?.registerObserver(this)
    }

    @PublicApi
    @Deprecated("Use setDateFormatter() and setTimeFormatter() instead.")
    var dateTimeInterpreter: DateTimeInterpreter
        get() = object : DateTimeInterpreter {
            override fun interpretDate(date: Calendar): String = viewState.dateFormatter(date)
            override fun interpretTime(hour: Int): String = viewState.timeFormatter(hour)
        }
        set(value) {
            setDateFormatter { value.interpretDate(it) }
            setTimeFormatter { value.interpretTime(it) }
            renderers.filterIsInstance(DateFormatterDependent::class.java).forEach {
                it.onDateFormatterChanged(value::interpretDate)
            }
            renderers.filterIsInstance(TimeFormatterDependent::class.java).forEach {
                it.onTimeFormatterChanged(value::interpretTime)
            }
            invalidate()
        }

    @PublicApi
    fun setDateFormatter(formatter: DateFormatter) {
        viewState.dateFormatter = formatter
        renderers.filterIsInstance(DateFormatterDependent::class.java).forEach {
            it.onDateFormatterChanged(formatter)
        }
    }

    @PublicApi
    fun setTimeFormatter(formatter: TimeFormatter) {
        viewState.timeFormatter = formatter
        renderers.filterIsInstance(TimeFormatterDependent::class.java).forEach {
            it.onTimeFormatterChanged(formatter)
        }
    }

    override fun dispatchHoverEvent(event: MotionEvent): Boolean {
        if (accessibilityTouchHelper.dispatchHoverEvent(event)) {
            return true
        }
        return super.dispatchHoverEvent(event)
    }

    abstract class Adapter<T>(protected val context: Context) {

        internal abstract val eventsCache: EventsCache<T>

        private val eventChipsFactory = EventChipsFactory()

        internal lateinit var eventChipsCache: EventChipsCache

        internal val eventsDiffer: EventsDiffer<T> by lazy {
            EventsDiffer(
                context = context,
                eventsCache = eventsCache,
                eventChipsCache = eventChipsCache,
                eventChipsFactory = eventChipsFactory
            )
        }

        protected var shouldRefreshEvents: Boolean = false

        internal fun requireRefresh() {
            shouldRefreshEvents = true
        }

        internal fun handleClick(x: Float, y: Float): Boolean {
            val eventChip = findHitEvent(x, y) ?: return false
            val data = findEventData(id = eventChip.eventId) ?: return false

            onEventClick(data)

            eventChip.bounds?.let { bounds ->
                onEventClick(data, bounds)
            }

            return true
        }

        internal fun handleEmptyViewClick(time: Calendar) {
            onEmptyViewClick(time)
        }

        internal fun handleLongClick(x: Float, y: Float): Boolean {
            val eventChip = findHitEvent(x, y) ?: return false
            val data = findEventData(id = eventChip.eventId) ?: return false

            onEventLongClick(data)

            eventChip.bounds?.let { bounds ->
                onEventLongClick(data, bounds)
            }

            return true
        }

        internal fun handleEmptyViewLongClick(time: Calendar) {
            onEmptyViewLongClick(time)
        }

        private fun findHitEvent(x: Float, y: Float): EventChip? {
            val candidates = eventChipsCache.allEventChips.filter { it.isHit(x, y) }
            return when {
                candidates.isEmpty() -> null
                // Two events hit. This is most likely because an all-day event was clicked, but a
                // single event is rendered underneath it. We return the all-day event.
                candidates.size == 2 -> candidates.first { it.event.isAllDay }
                else -> candidates.first()
            }
        }

        internal fun updateObserver() {
            weekView?.invalidate()
        }

        internal var weekView: WeekView? = null
            private set

        internal fun registerObserver(weekView: WeekView) {
            this.weekView = weekView
        }

        internal fun unregisterObserver() {
            weekView = null
        }

        internal fun onEventClick(id: Long) {
            val event = eventsCache[id] ?: return
            onEventClick(data = event.data)
        }

        internal fun onEventLongClick(id: Long) {
            val event = eventsCache[id] ?: return
            onEventLongClick(data = event.data)
        }

        private fun findEventData(id: Long): T? = eventsCache[id]?.data

        // TODO: Comments

        open fun onEventClick(data: T) = Unit
        open fun onEventClick(data: T, bounds: RectF) = Unit
        open fun onEventLongClick(data: T) = Unit
        open fun onEventLongClick(data: T, bounds: RectF) = Unit
        open fun onEmptyViewClick(time: Calendar) = Unit
        open fun onEmptyViewClick(time: Calendar, bounds: RectF) = Unit
        open fun onEmptyViewLongClick(time: Calendar) = Unit
        open fun onEmptyViewLongClick(time: Calendar, bounds: RectF) = Unit
        open fun onRangeChanged(firstVisibleDate: Calendar, lastVisibleDate: Calendar) = Unit
        open fun onFirstVisibleDateChanged(date: Calendar) = Unit
    }

    open class PagingAdapter<T>(context: Context) : Adapter<T>(context) {

        override val eventsCache = PagedEventsCache<T>()

        fun submit(events: List<WeekViewDisplayable<T>>) {
            val viewState = weekView?.viewState ?: return
            eventsDiffer.submit(events, viewState, onFinished = this::updateObserver)
        }

        open fun onLoadMore(startDate: Calendar, endDate: Calendar) = Unit

        internal fun triggerEventsFetching(firstVisibleDate: Calendar) {
            val fetchRange = FetchRange.create(firstVisibleDate)
            val needsRefresh = shouldRefreshEvents || fetchRange !in eventsCache

            if (needsRefresh) {
                val periods = eventsCache.determinePeriodsToFetch(fetchRange)
                val needsToFetchAllPeriods = periods.size == 3
                eventsCache.prepare(fetchRange, needsToFetchAllPeriods)
                fetchPeriods(periods)
            }

            shouldRefreshEvents = false
        }

        private fun PagedEventsCache<T>.determinePeriodsToFetch(
            fetchRange: FetchRange
        ) = fetchRange.periods.filter { it !in this }

        private fun PagedEventsCache<T>.prepare(fetchRange: FetchRange, needsToFetchAllPeriods: Boolean) {
            if (shouldRefreshEvents && needsToFetchAllPeriods) {
                clear()
            }

            shouldRefreshEvents = false
            adjustToFetchRange(fetchRange)
        }

        private fun fetchPeriods(periods: List<Period>) {
            for (period in periods) {
                onLoadMore(period.startDate, period.endDate)
            }
        }
    }

    open class SimpleAdapter<T>(context: Context) : Adapter<T>(context) {

        override val eventsCache = SimpleEventsCache<T>()

        fun submit(events: List<WeekViewDisplayable<T>>) {
            val viewState = weekView?.viewState ?: return
            eventsDiffer.submit(events, viewState, onFinished = this::updateObserver)
        }
    }
}
