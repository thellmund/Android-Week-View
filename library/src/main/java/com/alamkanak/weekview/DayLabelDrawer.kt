package com.alamkanak.weekview

import android.graphics.Canvas
import android.os.Build
import android.text.Layout
import android.text.StaticLayout
import android.text.TextPaint
import java.util.*

internal class DayLabelDrawer(
        private val config: WeekViewConfig
) {

    private val drawingConfig: WeekViewDrawingConfig = config.drawingConfig

    fun draw(drawingContext: DrawingContext, canvas: Canvas) {
        drawingContext
                .getDateRangeWithStartPixels(config)
                .forEach { (date, startPixel) ->
                    drawLabel(date, startPixel, canvas)
                }
    }

    private fun drawLabel(day: Calendar, startPixel: Float, canvas: Canvas) {
        val dayLabel = drawingConfig.dateTimeInterpreter.interpretDate(day)

        val x = startPixel + drawingConfig.widthPerDay / 2

        val textPaint = if (day.isToday) {
            drawingConfig.todayHeaderTextPaint
        } else {
            drawingConfig.headerTextPaint
        }

        if (!config.enableMultilinesHeaderRow) {
            val y = config.headerRowPadding.toFloat() - textPaint.ascent()
            canvas.drawText(dayLabel, x, y, textPaint)
        } else {

            val textPaint2 = TextPaint(textPaint)
            val staticLayout = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
                                  StaticLayout.Builder
                                      .obtain(dayLabel, 0, dayLabel.length, textPaint2
                                              , config.totalDayWidth.toInt())
                                      .build()
                                } else {
                                  StaticLayout(dayLabel, textPaint2, config.totalDayWidth.toInt(),
                                      Layout.Alignment.ALIGN_CENTER, 1.0f, 0.0f, false)
                                }

            drawingConfig.headerTextHeight = staticLayout.height.toFloat()
            drawingConfig.refreshHeaderHeight(config)

            canvas.save()
            canvas.translate(x, config.headerRowPadding.toFloat())
            staticLayout.draw(canvas)
            canvas.restore()
        }
    }

}
