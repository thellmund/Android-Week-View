package com.alamkanak.weekview

import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.RectF
import kotlin.math.sqrt

private data class Line(val startX: Float, val startY: Float, val stopX: Float, val stopY: Float)

internal fun Canvas.drawPattern(
    pattern: WeekViewEntity.Style.Pattern,
    bounds: RectF,
    spacing: Float,
    paint: Paint
) = when (pattern) {
    is WeekViewEntity.Style.Pattern.DiagonalLines -> drawDiagonalLines(bounds, spacing, paint)
    is WeekViewEntity.Style.Pattern.Dots -> drawDots(bounds, pattern.spacing, paint)
}

internal fun Canvas.drawDiagonalLines(bounds: RectF, spacing: Float, paint: Paint) {
    paint.style = Paint.Style.STROKE

    val lines = mutableListOf<Line>()
    var startX = bounds.left

    // Draw all the lines to the right of the top-left corner
    while (startX <= bounds.right) {
        lines += calculateDiagonalLine(startX, startY = bounds.top, stopY = bounds.bottom)
        startX += spacing
    }

    // Now, draw the lines to the left of the top-left corner
    var endX = bounds.right
    startX = bounds.left

    while (endX >= bounds.left) {
        lines += calculateDiagonalLine(startX, startY = bounds.top, stopY = bounds.bottom)
        startX -= spacing
        endX -= spacing
    }

    for (line in lines) {
        drawLine(line.startX, line.startY, line.stopX, line.stopY, paint)
    }
}

private fun calculateDiagonalLine(startX: Float, startY: Float, stopY: Float): Line {
    val height = stopY - startY
    val width = height / sqrt(2f)
    val stopX = startX + width
    return Line(startX, startY, stopX, stopY)
}

internal fun Canvas.drawDots(bounds: RectF, spacing: Int, paint: Paint) {
    paint.style = Paint.Style.FILL
    val strokeWidth = paint.strokeWidth

    val paddedDot = strokeWidth + spacing
    val horizontalDots = (bounds.width() / paddedDot).toInt()
    val verticalDots = (bounds.height() / paddedDot).toInt()

    val dotsWidth = horizontalDots * paddedDot
    val dotsHeight = verticalDots * paddedDot

    val horizontalPadding = bounds.width() - dotsWidth
    val verticalPadding = bounds.height() - dotsHeight

    val left = bounds.left + horizontalPadding / 2
    val top = bounds.top + verticalPadding / 2

    for (horizontalDot in 0 until horizontalDots) {
        for (verticalDot in 0 until verticalDots) {
            val leftBound = left + horizontalDot * paddedDot
            val topBound = top + verticalDot * paddedDot
            val radius = paint.strokeWidth / 2
            val x = leftBound + paddedDot / 2
            val y = topBound + paddedDot / 2
            drawCircle(x, y, radius, paint)
        }
    }
}
