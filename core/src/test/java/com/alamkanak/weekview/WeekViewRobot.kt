package com.alamkanak.weekview

import android.content.Context
import com.google.common.truth.Truth.assertThat
import org.mockito.Mockito.verify
import org.mockito.Mockito.verifyNoMoreInteractions
import java.util.Calendar

internal fun weekViewRobot(
    context: Context,
    block: WeekViewRobot.() -> Unit
) {
    WeekViewRobot(context).apply { block() }
}

internal class WeekViewRobot(
    context: Context
) {

    val weekView = WeekView<Unit>(context)

    fun scrollToDate(date: Calendar) {
        weekView.goToDate(date)
        simulateRefresh(date)
    }

    fun fillCache(vararg events: ResolvedWeekViewEvent<Unit>) {
        val cache = weekView.eventsCacheWrapper.get()
        if (cache is SimpleEventsCache) {
            cache.update(events.toList())
        }
    }

    fun assertDateRangeContains(date: Calendar, vararg events: ResolvedWeekViewEvent<Unit>) {
        val loader = weekView.eventsLoaderWrapper.get()
        val dateRangeEvents = loader.refresh(date)

        for (event in events) {
            assertThat(dateRangeEvents).contains(event)
        }
    }

    private fun simulateRefresh(date: Calendar) {
        val eventsLoader = weekView.eventsLoaderWrapper.get()
        eventsLoader.refresh(date)
    }

    internal fun assertOnLoadMoreCalled(
        listener: OnLoadMoreListener,
        periods: List<Period>
    ) {
        assertOnLoadMoreCalled(listener, *periods.toTypedArray())
    }

    internal fun assertOnLoadMoreCalled(
        listener: OnLoadMoreListener,
        vararg periods: Period
    ) {
        for (period in periods) {
            verify(listener).onLoadMore(period.startDate, period.endDate)
        }
        verifyNoMoreInteractions(listener)
    }

    internal fun assertOnMonthChangeCalled(
        fetchRange: FetchRange
    ): List<WeekViewDisplayable<Unit>> {
        val listener = checkNotNull(weekView.onMonthChangeListener)
        val results = mutableListOf<WeekViewDisplayable<Unit>>()
        for (period in fetchRange.periods) {
            results += listener.onMonthChange(period.startDate, period.endDate)
        }
        return results
    }

    internal fun assertCachingEventsLoader() {
        val loader = weekView.eventsLoaderWrapper.currentEventsLoader
        assertThat(loader).isInstanceOf(CachingEventsLoader::class.java)
    }

    internal fun assertPagedEventsLoader() {
        val loader = weekView.eventsLoaderWrapper.currentEventsLoader
        assertThat(loader).isInstanceOf(PagedEventsLoader::class.java)
    }

    internal fun assertLegacyEventsLoader() {
        val loader = weekView.eventsLoaderWrapper.currentEventsLoader
        assertThat(loader).isInstanceOf(LegacyEventsLoader::class.java)
    }
}
