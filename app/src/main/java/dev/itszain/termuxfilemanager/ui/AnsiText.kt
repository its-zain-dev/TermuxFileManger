package dev.itszain.termuxfilemanager.ui

import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextDecoration

/** Parses ANSI SGR escape sequences into styled Compose AnnotatedStrings and strips control noise. */
object AnsiParser {
    private val OSC_REGEX = Regex("""\u001B\][^\u0007\u001B]*(?:\u0007|\u001B\\)""")
    private val CSI_REGEX = Regex("""\u001B\[([0-9;?]*)([A-Za-z])""")

    private val DARK_BASIC = listOf(
        Color(0xFF475569), Color(0xFFF87171), Color(0xFF4ADE80), Color(0xFFFACC15),
        Color(0xFF60A5FA), Color(0xFFC084FC), Color(0xFF2DD4BF), Color(0xFFE2E8F0)
    )
    private val DARK_BRIGHT = listOf(
        Color(0xFF94A3B8), Color(0xFFFCA5A5), Color(0xFF86EFAC), Color(0xFFFDE047),
        Color(0xFF93C5FD), Color(0xFFD8B4FE), Color(0xFF5EEAD4), Color(0xFFFFFFFF)
    )
    private val LIGHT_BASIC = listOf(
        Color(0xFF1E293B), Color(0xFFDC2626), Color(0xFF15803D), Color(0xFFB45309),
        Color(0xFF1D4ED8), Color(0xFF7E22CE), Color(0xFF0F766E), Color(0xFF475569)
    )
    private val LIGHT_BRIGHT = listOf(
        Color(0xFF64748B), Color(0xFFEF4444), Color(0xFF16A34A), Color(0xFFD97706),
        Color(0xFF2563EB), Color(0xFF9333EA), Color(0xFF0D9488), Color(0xFF334155)
    )

    fun strip(text: String): String = CSI_REGEX.replace(OSC_REGEX.replace(text, ""), "")

    fun parse(raw: String, dark: Boolean): AnnotatedString {
        if ('\u001B' !in raw) return AnnotatedString(raw)
        val cleaned = OSC_REGEX.replace(raw, "")
        val basic = if (dark) DARK_BASIC else LIGHT_BASIC
        val bright = if (dark) DARK_BRIGHT else LIGHT_BRIGHT
        var fg: Color = Color.Unspecified
        var bg: Color = Color.Unspecified
        var bold = false
        var italic = false
        var underline = false

        fun currentStyle(): SpanStyle? {
            if (fg == Color.Unspecified && bg == Color.Unspecified && !bold && !italic && !underline) return null
            return SpanStyle(
                color = fg,
                background = bg,
                fontWeight = if (bold) FontWeight.Bold else null,
                fontStyle = if (italic) FontStyle.Italic else null,
                textDecoration = if (underline) TextDecoration.Underline else null
            )
        }

        return buildAnnotatedString {
            var cursor = 0
            for (match in CSI_REGEX.findAll(cleaned)) {
                if (match.range.first > cursor) {
                    val segment = cleaned.substring(cursor, match.range.first)
                    val style = currentStyle()
                    if (style != null) {
                        pushStyle(style); append(segment); pop()
                    } else append(segment)
                }
                if (match.groupValues[2] == "m") {
                    val codes = match.groupValues[1].split(';').mapNotNull { it.toIntOrNull() }.ifEmpty { listOf(0) }
                    var idx = 0
                    while (idx < codes.size) {
                        when (val c = codes[idx]) {
                            0 -> { fg = Color.Unspecified; bg = Color.Unspecified; bold = false; italic = false; underline = false }
                            1 -> bold = true
                            3 -> italic = true
                            4 -> underline = true
                            22 -> bold = false
                            23 -> italic = false
                            24 -> underline = false
                            39 -> fg = Color.Unspecified
                            49 -> bg = Color.Unspecified
                            in 30..37 -> fg = basic[c - 30]
                            in 90..97 -> fg = bright[c - 90]
                            in 40..47 -> bg = basic[c - 40].copy(alpha = 0.25f)
                            in 100..107 -> bg = bright[c - 100].copy(alpha = 0.25f)
                            38 -> if (idx + 2 < codes.size && codes[idx + 1] == 5) {
                                fg = color256(codes[idx + 2], basic, bright)
                                idx += 2
                            }
                        }
                        idx++
                    }
                }
                cursor = match.range.last + 1
            }
            if (cursor < cleaned.length) {
                val tail = cleaned.substring(cursor)
                val style = currentStyle()
                if (style != null) {
                    pushStyle(style); append(tail); pop()
                } else append(tail)
            }
        }
    }

    private fun color256(n: Int, basic: List<Color>, bright: List<Color>): Color = when {
        n in 0..7 -> basic[n]
        n in 8..15 -> bright[n - 8]
        n in 16..231 -> {
            val v = n - 16
            val r = (v / 36) * 51
            val g = ((v % 36) / 6) * 51
            val b = (v % 6) * 51
            Color(r, g, b)
        }
        n in 232..255 -> {
            val gray = 8 + (n - 232) * 10
            Color(gray, gray, gray)
        }
        else -> Color.Unspecified
    }
}
