package com.alamkanak.weekview

import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.RectF
import android.graphics.Typeface
import android.text.SpannableStringBuilder
import android.text.StaticLayout
import android.text.style.StyleSpan

internal class EventChipDrawer<T>(
    private val config: WeekViewConfigWrapper
) {

    private val textFitter = TextFitter<T>(config)
    private val textLayoutCache = mutableMapOf<Long, StaticLayout>()

    private val backgroundPaint = Paint()
    private val borderPaint = Paint()

    internal fun draw(
        eventChip: EventChip<T>,
        canvas: Canvas,
        textLayout: StaticLayout? = null
    ) {
        val event = eventChip.event

        val cornerRadius = config.eventCornerRadius.toFloat()
        updateBackgroundPaint(event, backgroundPaint)

        val rect = checkNotNull(eventChip.bounds)
        canvas.drawRoundRect(rect, cornerRadius, cornerRadius, backgroundPaint)

        if (event.style.borderWidth != 0) {
            updateBorderPaint(event, borderPaint)

            val borderWidth = event.style.borderWidth
            val adjustedRect = RectF(
                rect.left + borderWidth / 2f,
                rect.top + borderWidth / 2f,
                rect.right - borderWidth / 2f,
                rect.bottom - borderWidth / 2f)
            canvas.drawRoundRect(adjustedRect, cornerRadius, cornerRadius, borderPaint)
        }

        if (event.isNotAllDay) {
            drawCornersForMultiDayEvents(eventChip, cornerRadius, canvas)
        }

        if (textLayout != null) {
            // The text height has already been calculated
            drawEventTitle(eventChip, textLayout, canvas)
        } else {
            calculateTextHeightAndDrawTitle(eventChip, canvas)
        }
    }

    private fun drawCornersForMultiDayEvents(
        eventChip: EventChip<T>,
        cornerRadius: Float,
        canvas: Canvas
    ) {
        val event = eventChip.event
        val originalEvent = eventChip.originalEvent
        val rect = checkNotNull(eventChip.bounds)

        updateBackgroundPaint(event, backgroundPaint)

        if (event.startsOnEarlierDay(originalEvent)) {
            val topRect = RectF(rect.left, rect.top, rect.right, rect.top + cornerRadius)
            canvas.drawRect(topRect, backgroundPaint)
        }

        if (event.endsOnLaterDay(originalEvent)) {
            val bottomRect = RectF(rect.left, rect.bottom - cornerRadius, rect.right, rect.bottom)
            canvas.drawRect(bottomRect, backgroundPaint)
        }

        if (event.style.borderWidth != 0) {
            drawStroke(eventChip, canvas)
        }
    }

    private fun drawStroke(
        eventChip: EventChip<T>,
        canvas: Canvas
    ) {
        val event = eventChip.event
        val originalEvent = eventChip.originalEvent
        val rect = checkNotNull(eventChip.bounds)

        val borderWidth = event.style.borderWidth
        val innerWidth = rect.width() - borderWidth * 2

        val borderStartX = rect.left + borderWidth
        val borderEndX = borderStartX + innerWidth

        updateBorderPaint(event, backgroundPaint)

        if (event.startsOnEarlierDay(originalEvent)) {
            // Remove top rounded corners by drawing a rectangle
            val borderStartY = rect.top
            val borderEndY = borderStartY + borderWidth
            val newRect = RectF(borderStartX, borderStartY, borderEndX, borderEndY)
            canvas.drawRect(newRect, backgroundPaint)
        }

        if (event.endsOnLaterDay(originalEvent)) {
            // Remove bottom rounded corners by drawing a rectangle
            val borderEndY = rect.bottom
            val borderStartY = borderEndY - borderWidth
            val newRect = RectF(borderStartX, borderStartY, borderEndX, borderEndY)
            canvas.drawRect(newRect, backgroundPaint)
        }
    }

    private fun drawEventTitle(
        eventChip: EventChip<T>,
        textLayout: StaticLayout,
        canvas: Canvas
    ) {
        val rect = checkNotNull(eventChip.bounds)
        canvas.apply {
            save()
            translate(
                rect.left + config.eventPaddingHorizontal,
                rect.top + config.eventPaddingVertical
            )
            textLayout.draw(this)
            restore()
        }
    }

    private fun calculateTextHeightAndDrawTitle(
        eventChip: EventChip<T>,
        canvas: Canvas
    ) {
        val event = eventChip.event
        val bounds = checkNotNull(eventChip.bounds)

        val fullHorizontalPadding = config.eventPaddingHorizontal * 2
        val fullVerticalPadding = config.eventPaddingVertical * 2

        val negativeWidth = bounds.right - bounds.left - fullHorizontalPadding < 0
        val negativeHeight = bounds.bottom - bounds.top - fullVerticalPadding < 0
        if (negativeWidth || negativeHeight) {
            return
        }

        val title = event.title.emojify()
        val text = SpannableStringBuilder(title)
        text.setSpan(StyleSpan(Typeface.BOLD))

        val location = event.location?.emojify()
        if (location != null) {
            text.appendln().append(location)
        }

        val chipHeight = (bounds.bottom - bounds.top - fullVerticalPadding).toInt()
        val chipWidth = (bounds.right - bounds.left - fullHorizontalPadding).toInt()

        if (chipHeight == 0 || chipWidth == 0) {
            return
        }

        val didAvailableAreaChange = eventChip.didAvailableAreaChange(
            area = bounds,
            horizontalPadding = fullHorizontalPadding,
            verticalPadding = fullVerticalPadding
        )
        val isCached = event.id in textLayoutCache

        if (didAvailableAreaChange || !isCached) {
            textLayoutCache[event.id] = textFitter.fit(
                eventChip = eventChip,
                title = title,
                location = location,
                chipHeight = chipHeight,
                chipWidth = chipWidth
            )
            eventChip.updateAvailableArea(chipWidth, chipHeight)
        }

        val textLayout = textLayoutCache[event.id] ?: return
        if (textLayout.height <= chipHeight) {
            drawEventTitle(eventChip, textLayout, canvas)
        }
    }

    private fun updateBackgroundPaint(
        event: ResolvedWeekViewEvent<T>,
        paint: Paint
    ) {
        paint.color = event.style.backgroundColor
        paint.isAntiAlias = true
        paint.strokeWidth = 0f
        paint.style = Paint.Style.FILL
    }

    private fun updateBorderPaint(
        event: ResolvedWeekViewEvent<T>,
        paint: Paint
    ) {
        paint.color = event.style.backgroundColor
        paint.isAntiAlias = true
        paint.strokeWidth = event.style.borderWidth.toFloat()
        paint.style = Paint.Style.STROKE
    }
}
