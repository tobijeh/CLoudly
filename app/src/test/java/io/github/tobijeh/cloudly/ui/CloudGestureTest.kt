package io.github.tobijeh.cloudly.ui

import androidx.activity.ComponentActivity
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.test.bottomCenter
import androidx.compose.ui.test.center
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performTouchInput
import androidx.compose.ui.test.pinch
import androidx.compose.ui.test.click
import androidx.compose.ui.test.longClick
import androidx.compose.ui.test.swipe
import androidx.compose.runtime.collectAsState
import io.github.tobijeh.cloudly.core.CloudController
import io.github.tobijeh.cloudly.core.CloudSettings
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/** Drives real touch input through the preview's gesture recogniser. */
@RunWith(RobolectricTestRunner::class)
@Config(qualifiers = "w800dp-h450dp-land-mdpi")
class CloudGestureTest {
    @get:Rule val rule = createAndroidComposeRule<ComponentActivity>()

    private val events = mutableListOf<String>()
    private val c = CloudController()
    private var wordId = 0L

    @Before
    fun setUp() {
        wordId = c.addWord("grace")!!
        val g = CloudGestures(
            onTap = { events += "tap"; c.tap(it) },
            onDoubleTap = { _, _ -> events += "double" },
            onHold = { _, _ -> events += "hold" },
            onDrag = { id, x, y -> c.drag(id, x, y) },
            onDragEnd = { id, binned -> events += if (binned) "bin" else "drop"; if (!binned) c.endDrag(id) },
            onPinch = { id, s -> c.emphasize(id, s) },
            onPinchEnd = { events += "pinchEnd"; c.endEmphasis() },
        )
        rule.setContent {
            val state = c.state.collectAsState()
            CloudView(state.value, CloudSettings(), Modifier.fillMaxSize().testTag("cloud"), gestures = g, showGuides = true)
        }
        // Layout runs on a background thread; give it real time, then let animations settle.
        repeat(6) { Thread.sleep(150); rule.mainClock.advanceTimeBy(500); rule.waitForIdle() }
    }

    private fun settle(ms: Long = 600) {
        rule.mainClock.advanceTimeBy(ms)
        rule.waitForIdle()
    }

    private val cloud get() = rule.onNodeWithTag("cloud")

    @Test
    fun `tap locks the word, after the short double-tap window`() {
        cloud.performTouchInput { click(center) }
        settle(DOUBLE_TAP_WINDOW_MS + 100)
        assertEquals(listOf("tap"), events)
        assertEquals(wordId, c.state.value.lock?.wordId)
        assertEquals(1, c.state.value.word(wordId)!!.count)
    }

    @Test
    fun `double-tap renames instead of tapping`() {
        cloud.performTouchInput { click(center); advanceEventTime(80); click(center) }
        settle()
        assertEquals(listOf("double"), events)
        assertNull(c.state.value.lock)
    }

    @Test
    fun `hold opens the menu`() {
        cloud.performTouchInput { longClick(center) }
        settle()
        assertEquals(listOf("hold"), events)
    }

    @Test
    fun `drag moves the word and leaves it pinned`() {
        cloud.performTouchInput { swipe(center, center + Offset(150f, -60f), durationMillis = 400) }
        settle()
        assertEquals(listOf("drop"), events)
        val pin = c.state.value.pins[wordId]
        assertNotNull(pin)
        assertTrue(pin!!.nx > 0.1f)
        assertFalse(pin.dragging)
    }

    @Test
    fun `dropping on the bin reports a bin drop`() {
        cloud.performTouchInput { swipe(center, bottomCenter - Offset(0f, 40f), durationMillis = 400) }
        settle()
        assertEquals(listOf("bin"), events)
    }

    @Test
    fun `pinch emphasises without changing counts`() {
        val before = c.state.value.words
        cloud.performTouchInput {
            pinch(center - Offset(20f, 0f), center - Offset(120f, 0f), center + Offset(20f, 0f), center + Offset(120f, 0f), durationMillis = 400)
        }
        settle()
        assertTrue("pinchEnd" in events)
        assertEquals(before, c.state.value.words)
        assertFalse(c.state.value.emphasis!!.active)
    }
}
