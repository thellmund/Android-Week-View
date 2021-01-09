package com.alamkanak.weekview.sample.ui

import android.graphics.Canvas
import android.graphics.Color
import android.graphics.DashPathEffect
import android.graphics.Paint
import android.os.Bundle
import androidx.appcompat.app.AppCompatActivity
import com.alamkanak.weekview.WeekView
import com.alamkanak.weekview.emoji.enableEmojiProcessing
import com.alamkanak.weekview.sample.data.EventsDatabase
import com.alamkanak.weekview.sample.databinding.ActivityBasicBinding
import com.alamkanak.weekview.sample.ui.basic.BasicActivityWeekViewAdapter
import com.alamkanak.weekview.sample.ui.basic.BasicViewModel
import com.alamkanak.weekview.sample.util.setupWithWeekView
import com.alamkanak.weekview.sample.util.showWithRetryAction
import com.google.android.material.snackbar.Snackbar
import kotlin.math.min

class CustomDrawersActivity : AppCompatActivity() {

    private val binding: ActivityBasicBinding by lazy {
        ActivityBasicBinding.inflate(layoutInflater)
    }

    private val viewModel: BasicViewModel by lazy {
        BasicViewModel(database = EventsDatabase(this))
    }

    private val snackbar: Snackbar by lazy {
        Snackbar.make(binding.weekView, "Something went wrong", Snackbar.LENGTH_INDEFINITE)
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(binding.root)

        val basicAdapter = BasicActivityWeekViewAdapter(
                loadMoreHandler = { params -> viewModel.fetchEvents(params) })

        binding.weekView.apply {
            enableEmojiProcessing()
            binding.toolbarContainer.toolbar.setupWithWeekView(this)
            adapter = basicAdapter
            additionalDrawers = listOf(
                    HalfHourLineDrawer(binding.weekView),
                    SampleTextDrawer()
            )
        }

        viewModel.viewState.observe(this) { viewState ->
            if (viewState.error != null) {
                val params = viewState.error.loadParams
                snackbar.showWithRetryAction { viewModel.retry(params) }
            } else {
                snackbar.dismiss()
            }

            basicAdapter.submitList(viewState.entities)
        }
    }
}

class HalfHourLineDrawer(private val view: WeekView) : WeekView.Drawer {
    override val base: WeekView.DrawBase = WeekView.DrawBase.GRID
    private val paint = Paint().apply {
        color = Color.argb(60, 75, 75, 200)
        strokeWidth = 2f
        pathEffect = DashPathEffect(floatArrayOf(20f, 10f), 0f)
        style = Paint.Style.STROKE
    }

    override fun draw(canvas: Canvas, bounds: WeekView.DrawBounds) {
        val gridBounds = bounds.calendarGridBounds
        canvas.clipRect(gridBounds)
        for (i in view.minHour..view.maxHour) {
            // Draw a line at the 30 min mark between each hour
            val y = gridBounds.top + bounds.currentOrigin.y +
                    ((i - view.minHour + 0.5) * bounds.hourHeight).toInt()

            canvas.drawLine(gridBounds.left, y, gridBounds.right, y, paint)
        }
    }
}

class SampleTextDrawer : WeekView.Drawer {
    val text = "Sample"

    override val base: WeekView.DrawBase = WeekView.DrawBase.TOP
    private val basePaint = Paint().apply {
        color = Color.argb(125, 150, 75, 75)
        isAntiAlias = true
        textSize = 1f
        textAlign = Paint.Align.CENTER
    }

    override fun draw(canvas: Canvas, bounds: WeekView.DrawBounds) {
        val maxSize = min(bounds.viewWidth, bounds.viewHeight)
        val paint = Paint(basePaint)

        // Use the largest text size that will fit
        while (true) {
            val measuredSize = paint.measureText(text)
            if (measuredSize > maxSize) {
                paint.textSize--
                break
            }
            paint.textSize++
        }
        val centerX = bounds.viewWidth / 2f
        val centerY = bounds.viewHeight / 2f
        canvas.rotate(45f, centerX, centerY)
        canvas.drawText(text, centerX, centerY, paint)
    }
}