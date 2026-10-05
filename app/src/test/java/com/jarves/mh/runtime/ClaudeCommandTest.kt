package com.jarves.mh.runtime

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * The run used to carry `--max-turns 25`, which an agent that writes and runs
 * scripts exhausts part-way through a task, so every such task stopped and needed
 * a manual "continue". These cover the argument list and the resume that replaces
 * that hand-off.
 */
class ClaudeCommandTest {

    private fun command(resume: String? = null, maxTurns: Int = CLAUDE_MAX_TURNS) =
        claudeCommand(
            executable = "claude",
            model = "claude-opus-5",
            prompt = "build the page",
            resumeSessionId = resume,
            maxTurns = maxTurns,
        )

    private fun List<String>.valueAfter(flag: String): String =
        this[this.indexOf(flag) + 1]

    @Test
    fun turnBudgetIsLargeEnoughForRealWork() {
        assertTrue(
            "25 turns stopped agents mid-task",
            command().valueAfter("--max-turns").toInt() > 25,
        )
    }

    @Test
    fun streamingOutputFlagsAreUnchanged() {
        val args = command()
        assertEquals("stream-json", args.valueAfter("--output-format"))
        assertTrue("--include-partial-messages" in args)
        assertTrue("--verbose" in args)
        assertTrue("--bare" in args)
    }

    @Test
    fun modelAndPromptArePassedThrough() {
        val args = command()
        assertEquals("claude-opus-5", args.valueAfter("--model"))
        assertEquals("build the page", args.valueAfter("-p"))
    }

    @Test
    fun aFreshRunDoesNotResume() {
        assertFalse("--resume" in command())
    }

    @Test
    fun aResumedRunCarriesTheSession() {
        val args = command(resume = "session-abc")
        assertEquals("session-abc", args.valueAfter("--resume"))
    }

    @Test
    fun aBlankSessionIdIsNotSentAsAResume() {
        assertFalse("--resume" in command(resume = "  "))
        assertFalse("--resume" in command(resume = ""))
    }

    @Test
    fun autoContinuesAreBounded() {
        // An unbounded resume loop would run forever on a task that cannot finish.
        assertTrue(CLAUDE_MAX_AUTO_CONTINUES in 1..5)
    }
}
