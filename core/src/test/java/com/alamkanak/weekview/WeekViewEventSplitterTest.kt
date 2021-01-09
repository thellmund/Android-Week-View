package com.alamkanak.weekview

import com.alamkanak.weekview.util.Event
import com.alamkanak.weekview.util.createResolvedWeekViewEvent
import org.junit.Assert.assertEquals
import org.junit.Test
import org.mockito.Mockito.`when` as whenever
import org.mockito.Mockito.mock
import org.mockito.MockitoAnnotations

class WeekViewEventSplitterTest {

    private val viewState = mock(ViewState::class.java)

    init {
        MockitoAnnotations.initMocks(this)
        whenever(viewState.minHour).thenReturn(0)
        whenever(viewState.maxHour).thenReturn(24)
    }

    @Test
    fun `single-day event is not split`() {
        val startTime = today().withHour(11)
        val endTime = startTime + Hours(2)
        val event = createResolvedWeekViewEvent(startTime, endTime)

        val results = event.split(viewState)
        val expected = listOf(event)

        assertEquals(expected, results)
    }

    @Test
    fun `event before range is ignored`() {
        whenever(viewState.minHour).thenReturn(7)
        whenever(viewState.maxHour).thenReturn(21)

        val event = createResolvedWeekViewEvent(today().withHour(1), today().withHour(2))

        val results = event.split(viewState)

        assertEquals(emptyList<ResolvedWeekViewEntity>(), results)
    }

    @Test
    fun `event after range is ignored`() {
        whenever(viewState.minHour).thenReturn(7)
        whenever(viewState.maxHour).thenReturn(21)

        val event = createResolvedWeekViewEvent(
                today().withHour(22), today().plus(Days(1)).withHour(6))

        val results = event.split(viewState)

        assertEquals(emptyList<ResolvedWeekViewEntity>(), results)
    }

    @Test
    fun `two-day event is split correctly`() {
        val startTime = today().withHour(11)
        val endTime = (startTime + Days(1)).withHour(2)

        val event = createResolvedWeekViewEvent(startTime, endTime)
        val results = event.split(viewState)

        val expected = listOf(
            Event(startTime, startTime.atEndOfDay),
            Event(endTime.atStartOfDay, endTime)
        )

        val expectedTimes = expected.map { it.startTime.timeInMillis to it.endTime.timeInMillis }
        val resultTimes = results.map { it.startTime.timeInMillis to it.endTime.timeInMillis }

        assertEquals(expectedTimes, resultTimes)
    }

    @Test
    fun `three-day event is split correctly`() {
        val startTime = today().withHour(11)
        val endTime = (startTime + Days(2)).withHour(2)

        val event = createResolvedWeekViewEvent(startTime, endTime)
        val results = event.split(viewState)

        val intermediateDate = startTime + Days(1)
        val expected = listOf(
            Event(startTime, startTime.atEndOfDay),
            Event(intermediateDate.atStartOfDay, intermediateDate.atEndOfDay),
            Event(endTime.atStartOfDay, endTime)
        )

        val expectedTimes = expected.map { it.startTime.timeInMillis to it.endTime.timeInMillis }
        val resultTimes = results.map { it.startTime.timeInMillis to it.endTime.timeInMillis }

        assertEquals(expectedTimes, resultTimes)
    }

    @Test
    fun `event with bounds outside of range is split correctly`() {
        val minHour = 7
        val maxHour = 21
        whenever(viewState.minHour).thenReturn(minHour)
        whenever(viewState.maxHour).thenReturn(maxHour)

        val startTime = today().withHour(5)
        val endTime = (startTime + Days(2)).withHour(23)

        val event = createResolvedWeekViewEvent(startTime, endTime)
        val results = event.split(viewState)

        val tomorrow = today() + Days(1)
        val expected = listOf(
                Event(startTime.withHour(minHour), startTime.withTimeAtEndOfPeriod(maxHour)),
                Event(tomorrow.withHour(minHour), tomorrow.withTimeAtEndOfPeriod(maxHour)),
                Event(endTime.withHour(minHour), endTime.withTimeAtEndOfPeriod(maxHour))
        )

        val expectedTimes = expected.map { it.startTime.timeInMillis to it.endTime.timeInMillis }
        val resultTimes = results.map { it.startTime.timeInMillis to it.endTime.timeInMillis }

        assertEquals(expectedTimes, resultTimes)
    }

    @Test
    fun `event ending before range start is split correctly`() {
        val minHour = 7
        val maxHour = 21
        whenever(viewState.minHour).thenReturn(minHour)
        whenever(viewState.maxHour).thenReturn(maxHour)

        val startTime = today().withHour(8)
        val endTime = (startTime + Days(1)).withHour(5)

        val event = createResolvedWeekViewEvent(startTime, endTime)
        val results = event.split(viewState)

        val expected = listOf(
                Event(startTime, startTime.withTimeAtEndOfPeriod(maxHour))
        )

        val expectedTimes = expected.map { it.startTime.timeInMillis to it.endTime.timeInMillis }
        val resultTimes = results.map { it.startTime.timeInMillis to it.endTime.timeInMillis }

        assertEquals(expectedTimes, resultTimes)
    }

    @Test
    fun `event starting after range end is split correctly`() {
        val minHour = 7
        val maxHour = 21
        whenever(viewState.minHour).thenReturn(minHour)
        whenever(viewState.maxHour).thenReturn(maxHour)

        val startTime = today().withHour(22)
        val endTime = (startTime + Days(1)).withHour(9)

        val event = createResolvedWeekViewEvent(startTime, endTime)
        val results = event.split(viewState)

        val expected = listOf(
                Event(endTime.withHour(minHour), endTime)
        )

        val expectedTimes = expected.map { it.startTime.timeInMillis to it.endTime.timeInMillis }
        val resultTimes = results.map { it.startTime.timeInMillis to it.endTime.timeInMillis }

        assertEquals(expectedTimes, resultTimes)
    }
}
