package com.jarves.mh.runtime

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class ProviderRuntimeErrorDetectorTest {
    @Test
    fun userNotFoundIsFatal() {
        assertEquals(
            "User not found. Check the API key and provider account.",
            ProviderRuntimeErrorDetector.detect("Failed to authenticate. API Error: 401 User not found."),
        )
    }

    @Test
    fun authenticationRetryIsFatalImmediately() {
        val event = """{"type":"system","subtype":"api_retry","attempt":1,"error_status":401,"error":"authentication_failed"}"""
        assertEquals(
            "The provider rejected the saved API key.",
            ProviderRuntimeErrorDetector.detect(event),
        )
    }

    @Test
    fun ordinaryRuntimeOutputIsNotFatal() {
        assertNull(ProviderRuntimeErrorDetector.detect("Claude Code connected"))
    }

    @Test
    fun rateLimitRetryIsLeftToTheCli() {
        val event = """{"type":"system","subtype":"api_retry","attempt":2,"error_status":429,"error":"rate_limit_exceeded"}"""
        assertNull(
            "A 429 is retried by the CLI; killing the session here lost the task.",
            ProviderRuntimeErrorDetector.detect(event),
        )
        assertNull(ProviderRuntimeErrorDetector.detect("API Error: 429 Too Many Requests"))
    }

    @Test
    fun droppedConnectionIsNotAnAuthFailure() {
        assertNull(ProviderRuntimeErrorDetector.detect("API Error: 502 Bad Gateway"))
        assertNull(ProviderRuntimeErrorDetector.detect("API Error: 529 Overloaded"))
        assertNull(ProviderRuntimeErrorDetector.detect("fetch failed: ECONNRESET"))
        assertNull(ProviderRuntimeErrorDetector.detect("Request timed out after 600000 ms"))
    }

    @Test
    fun bareWordsInToolOutputAreNotFatal() {
        // The agent prints these while reading files or running builds. They used
        // to end the session and were misreported as a rejected API key.
        assertNull(ProviderRuntimeErrorDetector.detect("Read src/token.ts: 30 - the refresh token expired at midnight"))
        assertNull(ProviderRuntimeErrorDetector.detect("Ran make check: quota rules are green"))
        assertNull(ProviderRuntimeErrorDetector.detect("docs/billing.md mentions the per-hour rate limit"))
    }
}
