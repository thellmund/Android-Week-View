package com.alamkanak.weekview.sample

import android.graphics.Color
import android.os.Bundle
import android.text.SpannableStringBuilder
import android.text.Spanned
import android.text.style.StrikethroughSpan
import android.text.style.TypefaceSpan
import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.MutableLiveData
import com.alamkanak.weekview.WeekViewEntity
import com.alamkanak.weekview.sample.data.EventsDatabase
import com.alamkanak.weekview.sample.data.model.CalendarEntity
import com.alamkanak.weekview.sample.util.setupWithWeekView
import com.alamkanak.weekview.sample.util.showToast
import com.alamkanak.weekview.threetenabp.WeekViewPagingAdapterThreeTenAbp
import com.alamkanak.weekview.threetenabp.setDateFormatter
import java.util.Locale
import kotlinx.android.synthetic.main.activity_basic.weekView
import kotlinx.android.synthetic.main.view_toolbar.toolbar
import org.threeten.bp.LocalDate
import org.threeten.bp.LocalDateTime
import org.threeten.bp.format.DateTimeFormatter
import org.threeten.bp.format.FormatStyle.MEDIUM
import org.threeten.bp.format.FormatStyle.SHORT

private class BasicViewModel(private val database: EventsDatabase) {

    val events = MutableLiveData<List<CalendarEntity>>()

    fun fetchEvents(startDate: LocalDate, endDate: LocalDate) {
        val dbEvents = database.getEventsInRange(startDate, endDate)
        val blockedTimes = listOf(
            CalendarEntity.BlockedTimeSlot(
                id = 123456789L,
                startTime = Calendar.getInstance().apply {
                    set(Calendar.HOUR_OF_DAY, 16)
                    set(Calendar.MINUTE, 0)
                },
                endTime = Calendar.getInstance().apply {
                    set(Calendar.HOUR_OF_DAY, 18)
                    set(Calendar.MINUTE, 0)
                }
            ),
            CalendarEntity.BlockedTimeSlot(
                id = 123456790L,
                startTime = Calendar.getInstance().apply {
                    set(Calendar.HOUR_OF_DAY, 19)
                    set(Calendar.MINUTE, 0)
                },
                endTime = Calendar.getInstance().apply {
                    set(Calendar.HOUR_OF_DAY, 21)
                    set(Calendar.MINUTE, 0)
                }
            )
        )
        events.value = dbEvents + blockedTimes
    }
}

class BasicActivity : AppCompatActivity() {

    private val weekdayFormatter = DateTimeFormatter.ofPattern("EEE", Locale.getDefault())
    private val dateFormatter = DateTimeFormatter.ofPattern("MM/dd", Locale.getDefault())

    private val viewModel: BasicViewModel by lazy {
        BasicViewModel(EventsDatabase(context = this))
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_basic)

        toolbar.setupWithWeekView(weekView)

        val adapter = BasicActivityWeekViewAdapter(loadMoreHandler = viewModel::fetchEvents)
        weekView.adapter = adapter

        weekView.setDateFormatter { date: LocalDate ->
            val weekdayLabel = weekdayFormatter.format(date)
            val dateLabel = dateFormatter.format(date)
            weekdayLabel + "\n" + dateLabel
        }

        viewModel.events.observe(this, adapter::submitList)
    }
}

private class BasicActivityWeekViewAdapter(
    private val loadMoreHandler: (startDate: LocalDate, endDate: LocalDate) -> Unit
) : WeekViewPagingAdapterThreeTenAbp<CalendarEntity>() {

    private val formatter = DateTimeFormatter.ofLocalizedDateTime(MEDIUM, SHORT)

    override fun onCreateEntity(item: CalendarEntity): WeekViewEntity {
        return when (item) {
            is CalendarEntity.Event -> createForEvent(item)
            is CalendarEntity.BlockedTimeSlot -> createForBlockedTimeSlot(item)
        }
    }

    private fun createForEvent(event: CalendarEntity.Event): WeekViewEntity {
        val backgroundColor = if (!event.isCanceled) event.color else Color.WHITE
        val textColor = if (!event.isCanceled) Color.WHITE else event.color
        val borderWidthResId = if (!event.isCanceled) R.dimen.no_border_width else R.dimen.border_width

        val style = WeekViewEntity.Style.Builder()
            .setTextColor(textColor)
            .setBackgroundColor(backgroundColor)
            .setBorderWidthResource(borderWidthResId)
            .setBorderColor(event.color)
            .build()

        val title = SpannableStringBuilder(event.title).apply {
            val titleSpan = TypefaceSpan("sans-serif-medium")
            setSpan(titleSpan, 0, event.title.length, Spanned.SPAN_EXCLUSIVE_EXCLUSIVE)
            if (event.isCanceled) {
                setSpan(StrikethroughSpan(), 0, event.title.length, Spanned.SPAN_EXCLUSIVE_EXCLUSIVE)
            }
        }

        val subtitle = SpannableStringBuilder(event.location).apply {
            if (event.isCanceled) {
                setSpan(StrikethroughSpan(), 0, event.location.length, Spanned.SPAN_EXCLUSIVE_EXCLUSIVE)
            }
        }

        return WeekViewEntity.Event.Builder(event)
            .setId(event.id)
            .setTitle(title)
            .setStartTime(event.startTime)
            .setEndTime(event.endTime)
            .setSubtitle(subtitle)
            .setAllDay(event.isAllDay)
            .setStyle(style)
            .build()
    }

    private fun createForBlockedTimeSlot(
        blockedTimeSlot: CalendarEntity.BlockedTimeSlot
    ): WeekViewEntity {
        val style = WeekViewEntity.Style.Builder()
            .setTextColor(Color.RED)
            .setPattern(WeekViewEntity.Style.Pattern.Diagonal, Color.LTGRAY)
            .setCornerRadius(0)
            .build()

        return WeekViewEntity.BlockedTime.Builder()
            .setId(blockedTimeSlot.id)
            .setStartTime(blockedTimeSlot.startTime)
            .setEndTime(blockedTimeSlot.endTime)
            .setStyle(style)
            .build()
    }

    override fun onEventClick(data: CalendarEntity) {
        if (data is CalendarEntity.Event) {
            context.showToast("Clicked ${data.title}")
        }
    }

    override fun onEmptyViewClick(time: LocalDateTime) {
        context.showToast("Empty view clicked at ${formatter.format(time)}")
    }

    override fun onEventLongClick(data: CalendarEntity) {
        if (data is CalendarEntity.Event) {
            context.showToast("Long-clicked ${data.title}")
        }
    }

    override fun onEmptyViewLongClick(time: LocalDateTime) {
        context.showToast("Empty view long-clicked at ${formatter.format(time)}")
    }

    override fun onLoadMore(startDate: LocalDate, endDate: LocalDate) {
        loadMoreHandler(startDate, endDate)
    }
}
