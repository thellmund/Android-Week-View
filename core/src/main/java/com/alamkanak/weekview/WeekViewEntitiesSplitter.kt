package com.alamkanak.weekview

internal fun ResolvedWeekViewEntity.split(viewState: ViewState): List<ResolvedWeekViewEntity> {
    if (startTime >= endTime) {
        return emptyList()
    }

    if (!isMultiDay && startTime.hour >= viewState.minHour && endTime.hour < viewState.maxHour) {
        return listOf(this)
    }

    return splitEventByDates(viewState)
}

private fun ResolvedWeekViewEntity.shortenTooLongAllDayEvent(
    viewState: ViewState
): ResolvedWeekViewEntity {
    val newEndTime = endTime.withTimeAtEndOfPeriod(viewState.maxHour)
    return createCopy(endTime = newEndTime)
}

private fun ResolvedWeekViewEntity.splitEventByDates(
    viewState: ViewState
): List<ResolvedWeekViewEntity> {
    val start = startTime.nextTimeWithinPeriod(viewState.minHour, viewState.maxHour)
    val end = endTime.lastTimeStrictlyWithinPeriod(viewState.minHour, viewState.maxHour)

    if (!start.before(end)) return emptyList()
    if (start.isSameDate(end)) {
        return listOf(createCopy(startTime = start, endTime = end))
    }

    val results = mutableListOf<ResolvedWeekViewEntity>()

    val firstEventEnd = start.withTimeAtEndOfPeriod(viewState.maxHour)
    if (start.before(firstEventEnd)) {
        val firstEvent = createCopy(startTime = start, endTime = firstEventEnd)
        results += firstEvent
    }

    val lastEventStart = end.withTimeAtStartOfPeriod(viewState.minHour)

    val oneDay = Days(1)
    var intermediateEnd = firstEventEnd + oneDay
    while (intermediateEnd.before(lastEventStart)) {
        val intermediateStart = intermediateEnd.withTimeAtStartOfPeriod(viewState.minHour)
        results += createCopy(startTime = intermediateStart, endTime = intermediateEnd)
        intermediateEnd = intermediateEnd + oneDay
    }

    results += createCopy(startTime = lastEventStart, endTime = end)

    return results
}
