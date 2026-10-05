package com.jarves.mh.runtime

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * The turn budget stops being an explanation. These cover the case where the app
 * has to name the step the agent kept repeating instead.
 */
class ToolLoopDetectorTest {

    private fun tracker() = ToolStepTracker()

    @Test
    fun normalWorkIsNotAStuckRun() {
        val tracker = tracker()
        repeat(3) { tracker.record("Read", "src/main.kt") }
        tracker.record("Edit", "src/main.kt")
        tracker.record("Bash", "npm run build")
        assertNull(tracker.repeated())
        assertFalse(tracker.isStuck())
    }

    @Test
    fun repeatingABuildTwiceIsStillNormal() {
        val tracker = tracker()
        repeat(2) { tracker.record("Bash", "npm run build") }
        assertFalse(tracker.isStuck())
    }

    @Test
    fun aStuckRunIsDetected() {
        val tracker = tracker()
        repeat(TOOL_LOOP_REPEAT_THRESHOLD) { tracker.record("Bash", "npm run build") }
        assertTrue(tracker.isStuck())
    }

    @Test
    fun theRepeatedStepIsNamedWithItsTool() {
        val tracker = tracker()
        repeat(TOOL_LOOP_REPEAT_THRESHOLD + 2) { tracker.record("Bash", "npm run build") }
        val repeated = tracker.repeated()
        assertNotNull(repeated)
        assertEquals("Bash npm run build", repeated!!.first)
        assertEquals(TOOL_LOOP_REPEAT_THRESHOLD + 2, repeated.second)
    }

    @Test
    fun theWorstStepWinsWhenTwoRepeat() {
        val tracker = tracker()
        repeat(TOOL_LOOP_REPEAT_THRESHOLD) { tracker.record("Read", "a.kt") }
        repeat(TOOL_LOOP_REPEAT_THRESHOLD + 4) { tracker.record("Bash", "npm test") }
        assertEquals("Bash npm test", tracker.repeated()!!.first)
    }

    @Test
    fun differentCommandsAreNotOneLoop() {
        val tracker = tracker()
        repeat(TOOL_LOOP_REPEAT_THRESHOLD) {
            tracker.record("Bash", "step $it of the build")
        }
        assertFalse("each command differs, so nothing repeated", tracker.isStuck())
    }

    @Test
    fun whitespaceDifferencesAreTheSameStep() {
        val tracker = tracker()
        repeat(TOOL_LOOP_REPEAT_THRESHOLD) { tracker.record("Bash", "npm   run\n build") }
        assertTrue(tracker.isStuck())
    }

    @Test
    fun totalCountsEveryStep() {
        val tracker = tracker()
        repeat(TOOL_LOOP_REPEAT_THRESHOLD) { tracker.record("Bash", "npm test") }
        tracker.record("Read", "a.kt")
        assertEquals(TOOL_LOOP_REPEAT_THRESHOLD + 1, tracker.total())
    }

    @Test
    fun clearResetsBetweenMessages() {
        val tracker = tracker()
        repeat(TOOL_LOOP_REPEAT_THRESHOLD) { tracker.record("Bash", "npm test") }
        assertTrue(tracker.isStuck())
        tracker.clear()
        assertFalse(tracker.isStuck())
        assertEquals(0, tracker.total())
    }

    @Test
    fun blankStepsAreIgnored() {
        val tracker = tracker()
        repeat(TOOL_LOOP_REPEAT_THRESHOLD * 2) { tracker.record("", "") }
        assertFalse(tracker.isStuck())
    }

    @Test
    fun theThresholdIsReachableButNotTrivial() {
        assertTrue(TOOL_LOOP_REPEAT_THRESHOLD in 3..10)
    }
}
