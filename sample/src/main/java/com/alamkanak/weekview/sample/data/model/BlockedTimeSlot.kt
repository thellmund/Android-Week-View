package com.alamkanak.weekview.sample.data.model

import android.graphics.Color
import com.alamkanak.weekview.WeekViewDisplayable
import com.alamkanak.weekview.WeekViewEntity
import java.util.Calendar

data class BlockedTimeSlot(
    val id: Long,
    private val startTime: Calendar,
    private val endTime: Calendar
) : WeekViewDisplayable<Event> {

    override fun toWeekViewEntity(): WeekViewEntity {
        val style = WeekViewEntity.Style.Builder()
            .setTextColor(Color.RED)
            .setPattern(WeekViewEntity.Style.Pattern.Diagonal, Color.LTGRAY)
            .setCornerRadius(0)
            .build()

        return WeekViewEntity.BlockedTime.Builder()
            .setId(id)
            .setStartTime(startTime)
            .setEndTime(endTime)
            .setStyle(style)
            .build()
    }
}
