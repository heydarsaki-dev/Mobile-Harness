package com.jarves.mh.runtime

import org.json.JSONObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Test

/**
 * Claude Code writes error text to `errors`, not to `result`, and separates limit
 * outcomes from failures with `subtype` and `terminal_reason`. These cover the
 * three ways the bridge used to get this wrong: it read the wrong field, it had no
 * text to show, and it killed the session on outcomes that resume on the next prompt.
 */
class ClaudeResultEventTest {

    private fun event(json: String): JSONObject = JSONObject(json)

    @Test
    fun errorTextIsReadFromErrorsNotResult() {
        val json = event(
            """{"type":"result","subtype":"error_during_execution","is_error":true,""" +
                """"result":"","errors":["spawn ENOENT /workspace/.bin/claude"],"num_turns":3}""",
        )
        assertEquals("spawn ENOENT /workspace/.bin/claude", ClaudeResultEvent.describe(json))
    }

    @Test
    fun severalErrorsAreJoined() {
        val json = event(
            """{"type":"result","subtype":"error_during_execution","is_error":true,""" +
                """"errors":["first failure","second failure"]}""",
        )
        assertEquals("first failure second failure", ClaudeResultEvent.describe(json))
    }

    @Test
    fun startupFailureReasonIsExplainedInPlainWords() {
        val json = event(
            """{"type":"result","subtype":"error_during_execution","is_error":true,""" +
                """"result":"","startup_failure_reason":"session_held_by_background","errors":[""]}""",
        )
        assertEquals(
            "A background worker is still holding this session.",
            ClaudeResultEvent.describe(json),
        )
    }

    @Test
    fun unknownStartupCodeIsStillReported() {
        val json = event(
            """{"type":"result","subtype":"error_during_execution","is_error":true,""" +
                """"startup_failure_reason":"some_future_code"}""",
        )
        assertEquals(
            "The runtime could not start: some_future_code.",
            ClaudeResultEvent.describe(json),
        )
    }

    @Test
    fun aFailureWithNoTextStillNamesItsCodes() {
        val json = event(
            """{"type":"result","subtype":"error_during_execution","is_error":true,""" +
                """"result":"","terminal_reason":"api_error"}""",
        )
        assertEquals(
            "Claude Code reported an error (error_during_execution, api_error).",
            ClaudeResultEvent.describe(json),
        )
    }

    @Test
    fun aFailureWithNothingAtAllStillSaysSomething() {
        assertNotNull(ClaudeResultEvent.describe(event("""{"type":"result","is_error":true}""")))
    }

    @Test
    fun maxTurnsIsRecoverableSoTheSessionSurvives() {
        val outcome = ClaudeResultEvent.outcome(
            event("""{"type":"result","subtype":"error_max_turns","is_error":true,"terminal_reason":"max_turns"}"""),
        )
        assertNotNull(outcome)
        assertEquals("Reached the turn limit", outcome!!.title)
    }

    @Test
    fun exhaustedBudgetIsRecoverable() {
        assertNotNull(
            ClaudeResultEvent.outcome(
                event("""{"type":"result","subtype":"error_max_budget_usd","is_error":true}"""),
            ),
        )
    }

    @Test
    fun deferredToolIsRecoverable() {
        assertNotNull(
            ClaudeResultEvent.outcome(
                event("""{"type":"result","subtype":"error_during_execution","is_error":true,"terminal_reason":"tool_deferred"}"""),
            ),
        )
    }

    @Test
    fun aRealFailureIsNotRecoverable() {
        assertNull(
            ClaudeResultEvent.outcome(
                event(
                    """{"type":"result","subtype":"error_during_execution","is_error":true,""" +
                        """"errors":["connection reset"]}""",
                ),
            ),
        )
    }

    @Test
    fun aRealApiFailureIsNotRecoverable() {
        assertNull(
            ClaudeResultEvent.outcome(
                event("""{"type":"result","subtype":"error_during_execution","is_error":true,"terminal_reason":"api_error"}"""),
            ),
        )
    }
}
