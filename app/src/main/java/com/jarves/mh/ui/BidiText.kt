package com.jarves.mh.ui

import androidx.compose.ui.text.style.TextDirection

/**
 * Bidirectional (mixed Persian/English) text helpers for chat bubbles.
 *
 * Compose resolves [TextDirection.Unspecified] from the *device locale*, not from
 * the text itself, so a Persian message on an English phone was laid out with a
 * left-to-right base direction. That is what scrambles sentences which mix
 * Persian prose with Latin identifiers, file names or punctuation.
 *
 * The direction itself is no longer guessed per message: it is a per-project
 * setting chosen by the user. What is left here is keeping embedded Latin runs
 * in place inside a right-to-left paragraph.
 */

// Unicode bidi isolate controls (U+2066..U+2069). Android has understood these
// since API 18, so they are safe to emit on every supported release.
private const val LRI = '\u2066' // left-to-right isolate
private const val PDI = '\u2069' // pop directional isolate

/** Characters with a strong right-to-left directionality (Unicode 6.3+).
 */
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
private fun containsRtl(text: String): Boolean = text.any(::isStrongRtl)

/**
 * Paragraph direction for a project.
 *
 * The user picks right-to-left or left-to-right once per project instead of the
 * app guessing from each message. Guessing was unstable: a Persian sentence
 * carrying several English identifiers counted as English and flipped to
 * left-to-right, so two Persian messages in the same chat ended up aligned to
 * opposite edges.
 */
fun projectTextDirection(isRtl: Boolean): TextDirection =
    if (isRtl) TextDirection.Rtl else TextDirection.Ltr

/**
 * Latin runs that a Persian sentence must not reorder: inline code, URLs,
 * dotted names such as `main.py`, slashed paths such as `src/main/App.kt` and
 * multi-word English phrases such as `machine learning model`.
 *
 * The character class deliberately keeps brackets, quotes and parentheses out
 * of the match so markdown link syntax (`[label](url)`) still parses after
 * wrapping.
 *
 * A multi-word phrase is wrapped as one run, never one run per word: neutrals
 * between two separate isolates resolve to the paragraph direction, which would
 * reverse the word order. Single Latin words are left alone because they already
 * form a correct left-to-right run on their own.
 */
private val LTR_RUN = Regex(
    "`[^`\n]+`" +
        "|(?:https?://|ftp://|www\\.)[A-Za-z0-9\\-._~:/?#@!$&'*+,;=%\\[\\]]+" +
        "|[A-Za-z0-9_\\-]+(?:\\.[A-Za-z0-9_\\-]+)+" +
        "|[A-Za-z0-9_\\-]+/[A-Za-z0-9_\\-./]*" +
        "|[A-Za-z][A-Za-z0-9_'\\-]*(?: +[A-Za-z][A-Za-z0-9_'\\-]*)+",
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
