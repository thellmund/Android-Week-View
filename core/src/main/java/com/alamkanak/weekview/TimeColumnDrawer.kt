package com.alamkanak.weekview

import android.graphics.Canvas
import android.util.SparseArray

internal class TimeColumnDrawer(
    private val view: WeekView<*>,
    private val config: WeekViewConfigWrapper
) : CachingDrawer {

    private val timeLabelCache = SparseArray<String>()

    init {
        cacheTimeLabels()
    }

    private fun cacheTimeLabels() = with(config) {
        for (hour in minHour..maxHour step timeColumnHoursInterval) {
            timeLabelCache.put(hour, dateTimeInterpreter.interpretTime(hour + minHour))
        }
    }

    override fun draw(
        drawingContext: DrawingContext,
        canvas: Canvas
    ) = with(config) {
        var topMargin = headerHeight
        val bottom = view.height.toFloat()

        canvas.drawRect(0f, topMargin, timeColumnWidth, bottom, timeColumnBackgroundPaint)

        val hourLines = FloatArray(hoursPerDay * 4)
        val hourStep = timeColumnHoursInterval

        for (hour in startHour until hoursPerDay step hourStep) {
            val heightOfHour = hourHeight * (hour - minHour)
            topMargin = headerHeight + currentOrigin.y + heightOfHour

            val isOutsideVisibleArea = topMargin > bottom
            if (isOutsideVisibleArea) {
                continue
            }

            val x = timeTextWidth + timeColumnPadding
            var y = topMargin + timeTextHeight / 2

            // If the hour separator is shown in the time column, move the time label below it
            if (showTimeColumnHourSeparator) {
                y += timeTextHeight / 2 + hourSeparatorPaint.strokeWidth + timeColumnPadding
            }

            canvas.drawText(timeLabelCache[hour], x, y, timeTextPaint)

            if (showTimeColumnHourSeparator && hour > 0) {
                val j = hour - 1
                hourLines[j * 4] = 0f
                hourLines[j * 4 + 1] = topMargin
                hourLines[j * 4 + 2] = timeColumnWidth
                hourLines[j * 4 + 3] = topMargin
            }
        }

        // Draw the vertical time column separator
        if (showTimeColumnSeparator) {
            val lineX = timeColumnWidth - timeColumnSeparatorStrokeWidth
            canvas.drawLine(lineX, headerHeight, lineX, bottom, timeColumnSeparatorPaint)
        }

        // Draw the hour separator inside the time column
        if (showTimeColumnHourSeparator) {
            canvas.drawLines(hourLines, hourSeparatorPaint)
        }
    }

    override fun clear() {
        timeLabelCache.clear()
        cacheTimeLabels()
    }
}
