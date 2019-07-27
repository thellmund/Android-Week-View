package com.alamkanak.weekview.sample.apiclient

import android.graphics.Color
import com.alamkanak.weekview.WeekViewDisplayable
import com.alamkanak.weekview.WeekViewEvent
import com.google.gson.annotations.Expose
import com.google.gson.annotations.SerializedName
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Locale

/**
 * An event model that was built for automatic serialization from json to object.
 * Created by Raquib-ul-Alam Kanak on 1/3/16.
 * Website: http://alamkanak.github.io
 */
data class ApiEvent(
    @Expose
    @SerializedName("name")
    var name: String,
    @Expose
    @SerializedName("dayOfMonth")
    var dayOfMonth: Int,
    @Expose
    @SerializedName("startTime")
    var startTime: String,
    @Expose
    @SerializedName("endTime")
    var endTime: String,
    @Expose
    @SerializedName("color")
    var color: String
) : WeekViewDisplayable<ApiEvent> {

    override fun toWeekViewEvent(): WeekViewEvent<ApiEvent> {
        val id = name.split(" ").last().toLong()

        val sdf = SimpleDateFormat("HH:mm", Locale.getDefault())
        val start = checkNotNull(sdf.parse(startTime))
        val end = checkNotNull(sdf.parse(endTime))

        val now = Calendar.getInstance()

        val startTime = now.clone() as Calendar
        startTime.timeInMillis = start.time
        startTime.set(Calendar.YEAR, now.get(Calendar.YEAR))
        startTime.set(Calendar.MONTH, now.get(Calendar.MONTH))
        startTime.set(Calendar.DAY_OF_MONTH, dayOfMonth)

        val endTime = startTime.clone() as Calendar
        endTime.timeInMillis = end.time
        endTime.set(Calendar.YEAR, startTime.get(Calendar.YEAR))
        endTime.set(Calendar.MONTH, startTime.get(Calendar.MONTH))
        endTime.set(Calendar.DAY_OF_MONTH, startTime.get(Calendar.DAY_OF_MONTH))

        val color = Color.parseColor(color)

        val style = WeekViewEvent.Style.Builder()
            .setBackgroundColor(color)
            .build()

        return WeekViewEvent.Builder<ApiEvent>(this)
            .setId(id)
            .setTitle(name)
            .setStartTime(startTime)
            .setEndTime(endTime)
            .setAllDay(false)
            .setStyle(style)
            .build()
    }
}
