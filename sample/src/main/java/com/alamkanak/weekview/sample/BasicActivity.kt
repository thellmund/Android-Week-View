package com.alamkanak.weekview.sample

import android.os.Bundle
import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.MutableLiveData
import com.alamkanak.weekview.WeekViewDisplayable
import com.alamkanak.weekview.sample.data.EventsDatabase
import com.alamkanak.weekview.sample.data.model.BlockedTimeSlot
import com.alamkanak.weekview.sample.data.model.Event
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

private class ViewModel(
    private val database: EventsDatabase
) {
    val events = MutableLiveData<List<WeekViewDisplayable<Event>>>()

    fun fetchEvents(startDate: LocalDate, endDate: LocalDate) {
        val dbEvents = database.getEventsInRange(startDate, endDate)
        val blockedTimes = listOf(
            BlockedTimeSlot(
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
            BlockedTimeSlot(
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

    private val viewModel: ViewModel by lazy {
        ViewModel(EventsDatabase(this))
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

        viewModel.events.observe(this) { events ->
            adapter.submit(events)
        }
    }
}

private class BasicActivityWeekViewAdapter(
    private val loadMoreHandler: (startDate: LocalDate, endDate: LocalDate) -> Unit
) : WeekViewPagingAdapterThreeTenAbp<Event>() {

    private val formatter = DateTimeFormatter.ofLocalizedDateTime(MEDIUM, SHORT)

    override fun onEventClick(data: Event) {
        context.showToast("Clicked ${data.title}")
    }

    override fun onEmptyViewClick(time: LocalDateTime) {
        context.showToast("Empty view clicked at ${formatter.format(time)}")
    }

    override fun onEventLongClick(data: Event) {
        context.showToast("Long-clicked ${data.title}")
    }

    override fun onEmptyViewLongClick(time: LocalDateTime) {
        context.showToast("Empty view long-clicked at ${formatter.format(time)}")
    }

    override fun onLoadMore(startDate: LocalDate, endDate: LocalDate) {
        loadMoreHandler(startDate, endDate)
    }
}
