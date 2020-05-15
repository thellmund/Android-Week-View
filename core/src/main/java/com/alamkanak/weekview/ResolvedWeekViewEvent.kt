package com.alamkanak.weekview

import android.content.Context
import android.graphics.Paint
import android.text.TextPaint
import java.util.Calendar
import kotlin.math.roundToInt

internal fun <T> WeekViewDisplayable<T>.toResolvedWeekViewEvent(
    context: Context
) = toWeekViewEvent().resolve(context)

internal fun <T> WeekViewEvent<T>.resolve(
    context: Context
) = ResolvedWeekViewEvent(
    id = id,
    title = titleResource.resolve(context),
    startTime = startTime,
    endTime = endTime,
    location = locationResource?.resolve(context),
    isAllDay = isAllDay,
    style = style.resolve(context),
    data = data
)

internal fun WeekViewEvent.Style.resolve(
    context: Context
) = ResolvedWeekViewEvent.Style(
    backgroundColor = backgroundColorResource?.resolve(context) ?: 0, // TODO Default event color
    borderColor = borderColorResource?.resolve(context) ?: 0, // TODO Default border color
    borderWidth = borderWidthResource?.resolve(context) ?: 0,
    textColor = textColorResource?.resolve(context) ?: 0, // TODO
    isTextStrikeThrough = isTextStrikeThrough
)

internal data class ResolvedWeekViewEvent<T>(
    val id: Long,
    val title: CharSequence,
    val startTime: Calendar,
    val endTime: Calendar,
    val location: CharSequence?,
    val isAllDay: Boolean,
    val style: Style,
    val data: T
) {

    data class Style(
        val backgroundColor: Int,
        val borderColor: Int,
        val borderWidth: Int,
        val textColor: Int,
        val isTextStrikeThrough: Boolean
    )

    internal val isNotAllDay: Boolean
        get() = isAllDay.not()

    internal val durationInMinutes: Int
        get() = ((endTime.timeInMillis - startTime.timeInMillis).toFloat() / 60_000).roundToInt()

    internal val isMultiDay: Boolean
        get() = startTime.isSameDate(endTime).not()

    internal fun isWithin(
        minHour: Int,
        maxHour: Int
    ): Boolean = startTime.hour >= minHour && endTime.hour <= maxHour

    internal fun collidesWith(other: ResolvedWeekViewEvent<T>): Boolean {
        if (isAllDay != other.isAllDay) {
            return false
        }

        if (startTime.isEqual(other.startTime) && endTime.isEqual(other.endTime)) {
            // Complete overlap
            return true
        }

        // Resolve collisions by shortening the preceding event by 1 ms
        if (endTime.isEqual(other.startTime)) {
            endTime -= Millis(1)
            return false
        } else if (startTime.isEqual(other.endTime)) {
            other.endTime -= Millis(1)
        }

        return !startTime.isAfter(other.endTime) && !endTime.isBefore(other.startTime)
    }

    internal fun startsOnEarlierDay(
        originalEvent: ResolvedWeekViewEvent<T>
    ): Boolean = startTime.isNotEqual(originalEvent.startTime)

    internal fun endsOnLaterDay(
        originalEvent: ResolvedWeekViewEvent<T>
    ): Boolean = endTime.isNotEqual(originalEvent.endTime)

    internal fun getTextPaint(config: WeekViewConfigWrapper): TextPaint {
        val textPaint = if (isAllDay) {
            config.allDayEventTextPaint
        } else {
            config.eventTextPaint
        }

        textPaint.color = style.textColor

        if (style.isTextStrikeThrough) {
            textPaint.flags = textPaint.flags or Paint.STRIKE_THRU_TEXT_FLAG
        }

        return textPaint
    }
}
