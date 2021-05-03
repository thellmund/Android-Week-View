package com.alamkanak.weekview

import android.content.Context
import android.os.Handler
import android.os.Looper
import androidx.annotation.WorkerThread
import java.util.concurrent.Executor
import java.util.concurrent.Executors

class MainExecutor : Executor {
    private val handler = Handler(Looper.getMainLooper())
    override fun execute(runnable: Runnable) {
        handler.post(runnable)
    }
}

/**
 * A helper class that processes the submitted [WeekViewEntity] objects and creates [EventChip]s
 * on a background thread.
 */
internal class EventsProcessor(
    private val context: Context,
    private val eventsCache: EventsCache,
    private val eventChipsFactory: EventChipsFactory,
    private val eventChipsCache: EventChipsCache
) {

    private val backgroundExecutor = Executors.newSingleThreadExecutor()
    private val mainThreadExecutor = MainExecutor()

    /**
     * Updates the [EventsCache] with the provided [WeekViewEntity] elements and creates
     * [EventChip]s.
     *
     * @param entities The list of new [WeekViewEntity] elements
     * @param viewState The current [ViewState] of [WeekView]
     * @param onFinished Callback to inform the caller whether [WeekView] should invalidate.
     */
    fun submit(
        entities: List<WeekViewEntity>,
        viewState: ViewState,
        onFinished: () -> Unit
    ) {
        backgroundExecutor.execute {
            submitEntities(entities, viewState)
            mainThreadExecutor.execute {
                onFinished()
            }
        }
    }

    @WorkerThread
    private fun submitEntities(
        entities: List<WeekViewEntity>,
        viewState: ViewState
    ) {
        val resolvedEntities = entities.map { it.resolve(context) }
        eventsCache.update(resolvedEntities)

        if (eventsCache is SimpleEventsCache) {
            submitEntitiesToSimpleCache(resolvedEntities, viewState)
        } else {
            submitEntitiesToPagedCache(resolvedEntities, viewState)
        }
    }

    private fun submitEntitiesToSimpleCache(
        entities: List<ResolvedWeekViewEntity>,
        viewState: ViewState,
    ) {
        val eventChips = eventChipsFactory.create(entities, viewState)
        eventChipsCache.replaceAll(eventChips)
    }

    private fun submitEntitiesToPagedCache(
        entities: List<ResolvedWeekViewEntity>,
        viewState: ViewState,
    ) {
        val diffResult = performDiff(entities)
        eventChipsCache.removeAll(diffResult.itemsToRemove)

        val eventChips = eventChipsFactory.create(diffResult.itemsToAddOrUpdate, viewState)
        eventChipsCache.addAll(eventChips)
    }

    private fun performDiff(entities: List<ResolvedWeekViewEntity>): DiffResult {
        val existingEventChips = eventChipsCache.allEventChips
        val existingEvents = existingEventChips.map { it.event }
        val existingEventIds = existingEventChips.map { it.event.id }

        val submittedEntityIds = entities.map { it.id }
        val addedEvents = entities.filter { it.id !in existingEventIds }
        val deletedEventIds = existingEventIds.filter { it !in submittedEntityIds }

        val updatedEvents = entities.filter { it.id in existingEventIds }
        val changed = updatedEvents.filter { it !in existingEvents }

        return DiffResult(
            itemsToAddOrUpdate = addedEvents + changed,
            itemsToRemove = deletedEventIds,
        )
    }

    private data class DiffResult(
        val itemsToAddOrUpdate: List<ResolvedWeekViewEntity>,
        val itemsToRemove: List<Long>,
    )
}
