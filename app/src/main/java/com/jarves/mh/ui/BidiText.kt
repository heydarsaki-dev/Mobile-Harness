package com.jarves.mh.ui

import androidx.compose.ui.text.style.TextDirection
import androidx.compose.ui.unit.LayoutDirection

/**
 * Bidirectional (mixed Persian/English) text helpers for chat bubbles.
 *
 * Compose resolves [TextDirection.Unspecified] from the *device locale*, not from
 * the text itself, so a Persian message on an English phone is laid out with a
 * left-to-right base direction. That is what scrambles sentences which mix
 * Persian prose with Latin identifiers, file names or punctuation.
 */

// Unicode bidi isolate controls (U+2066..U+2069). Android has understood these
// since API 18, so they are safe to emit on every supported release.
private const val LRI = '\u2066' // left-to-right isolate
private const val PDI = '\u2069' // pop directional isolate

/** Characters with a strong right-to-left directionality (Unicode 6.3+). */
private fun isStrongRtl(ch: Char): Boolean {
    val c = ch.code
    return c in 0x0590..0x05FF || // Hebrew
        c in 0x0600..0x06FF || // Arabic (Persian, Urdu, ...)
        c in 0x0700..0x074F || // Syriac
        c in 0x0750..0x077F || // Arabic Supplement
        c in 0x0780..0x07BF || // Thaana
        c in 0x07C0..0x08FF || // NKo, Samaritan, Mandaic, Arabic Extended-A
        c in 0xFB1D..0xFDFF || // Hebrew and Arabic presentation forms A
        c in 0xFE70..0xFEFC || // Arabic presentation forms B
        c in 0x10800..0x10CFF || // ancient RTL scripts
        c in 0x10D00..0x10FFF || // more ancient RTL scripts
        c in 0x1E800..0x1EFFF // Adlam, Arabic mathematical symbols
}

/** Characters with a strong left-to-right directionality. */
private fun isStrongLtr(ch: Char): Boolean {
    val c = ch.code
    return c in 0x0041..0x005A || // A-Z
        c in 0x0061..0x007A || // a-z
        c in 0x00C0..0x02AF // Latin-1 supplement through IPA extensions
}

/** True when [text] contains at least one strong right-to-left character. */
fun containsRtl(text: String): Boolean = text.any(::isStrongRtl)

private fun containsStrongLtr(text: String): Boolean = text.any(::isStrongLtr)

/**
 * Base direction for a chat message.
 *
 * [TextDirection.Content] lets the Unicode bidi algorithm pick the paragraph
 * direction from the first strong character, which is the correct behaviour for
 * prose. It also keeps a mostly-English message left-to-right, so the choice is
 * made per message instead of per device.
 */
fun messageTextDirection(text: String): TextDirection = when {
    containsRtl(text) -> TextDirection.Content
    containsStrongLtr(text) -> TextDirection.Ltr
    else -> TextDirection.Content
}

/**
 * Layout direction for a chat bubble, so Persian messages start on the right
 * edge and read right to left regardless of the device locale.
 */
fun messageLayoutDirection(text: String): LayoutDirection =
    if (containsRtl(text)) LayoutDirection.Rtl else LayoutDirection.Ltr

/**
 * Latin runs that a Persian sentence must not reorder: URLs, dotted names such
 * as `main.py` and slashed paths such as `src/main/App.kt`. The character class
 * deliberately keeps brackets, quotes and parentheses out of the match so
 * markdown link syntax (`[label](url)`) still parses after wrapping.
 */
private val LTR_RUN = Regex(
    "`[^`\n]+`" +
        "|(?:https?://|ftp://|www\\.)[A-Za-z0-9\\-._~:/?#@!$&'*+,;=%\\[\\]]+" +
        "|[A-Za-z0-9_\\-]+(?:\\.[A-Za-z0-9_\\-]+)+" +
        "|[A-Za-z0-9_\\-]+/[A-Za-z0-9_\\-./]*",
)

/**
 * Wraps embedded Latin runs in directional isolates so a Persian sentence keeps
 * them in place. Purely left-to-right text is returned untouched, which keeps
 * copied English free of invisible control characters.
 */
fun isolateLtrRuns(text: String): String {
    if (!containsRtl(text)) return text
    if (text.indexOf(PDI) >= 0) return text // already isolated upstream

    val result = StringBuilder(text.length + 16)
    var last = 0
    for (match in LTR_RUN.findAll(text)) {
        // Trailing separators are neutral characters that belong to the
        // sentence, not to the Latin run, so leave them outside the isolate.
        var end = match.range.last + 1
        while (end > match.range.first && text[end - 1] in ".,;:!?") end--
        if (end == match.range.first) continue

        result.append(text.substring(last, match.range.first))
        result.append(LRI).append(text.substring(match.range.first, end)).append(PDI)
        last = end
    }
    result.append(text.substring(last, text.length))
    return result.toString()
}
