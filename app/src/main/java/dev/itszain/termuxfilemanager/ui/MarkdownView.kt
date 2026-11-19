package dev.itszain.termuxfilemanager.ui

import android.content.Intent
import android.net.Uri
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.ClickableText
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

/** Full pure-Compose Markdown renderer with syntax-highlighted code fences, tables, lists, blockquotes and inline formatting. */
@Composable
fun TfmMarkdownView(text: String, onRunShell: ((String) -> Unit)? = null) {
    val fenceRegex = Regex("```([^\\n]*)\\n([\\s\\S]*?)```")
    val blocks = fenceRegex.findAll(text).toList()
    var cursor = 0
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        for (block in blocks) {
            if (block.range.first > cursor) {
                MarkdownProse(text.substring(cursor, block.range.first))
            }
            val lang = block.groupValues[1].trim()
            val code = block.groupValues[2].trimEnd()
            val runnable = onRunShell != null && lang.lowercase() in setOf("sh", "bash", "shell", "zsh")
            TfmCodeBlock(
                text = code,
                language = lang.ifBlank { "plain" },
                copy = true,
                onRun = if (runnable) { { onRunShell?.invoke(code) } } else null
            )
            cursor = block.range.last + 1
        }
        if (cursor < text.length) {
            MarkdownProse(text.substring(cursor))
        }
    }
}

@Composable
private fun MarkdownProse(raw: String) {
    val trimmed = raw.trim()
    if (trimmed.isEmpty()) return
    val lines = trimmed.lines()
    val tfm = LocalTfmColors.current
    val cs = MaterialTheme.colorScheme
    val context = LocalContext.current

    var i = 0
    SelectionContainer {
        Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
            while (i < lines.size) {
                val line = lines[i]
                val stripped = line.trim()
                if (stripped.isEmpty()) { i++; continue }

                // Horizontal rule
                if (stripped.matches(Regex("^([-*_])\\1{2,}$"))) {
                    HorizontalDivider(Modifier.padding(vertical = 4.dp), color = cs.outlineVariant)
                    i++; continue
                }

                // Markdown table (| a | b |)
                if (stripped.startsWith("|") && i + 1 < lines.size && lines[i + 1].trim().matches(Regex("^\\|?[\\s:|-]+\\|?$"))) {
                    val tableLines = mutableListOf<String>()
                    while (i < lines.size && lines[i].trim().startsWith("|")) {
                        tableLines += lines[i].trim()
                        i++
                    }
                    MarkdownTable(tableLines)
                    continue
                }

                // Blockquote
                if (stripped.startsWith(">")) {
                    val quoteLines = mutableListOf<String>()
                    while (i < lines.size && lines[i].trim().startsWith(">")) {
                        quoteLines += lines[i].trim().removePrefix(">").trim()
                        i++
                    }
                    Row(
                        Modifier
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(10.dp))
                            .background(cs.surfaceContainerHigh)
                            .padding(10.dp),
                        horizontalArrangement = Arrangement.spacedBy(10.dp)
                    ) {
                        Box(
                            Modifier
                                .width(3.dp)
                                .heightIn(min = 18.dp)
                                .background(cs.primary, RoundedCornerShape(2.dp))
                        )
                        InlineMarkdownText(
                            text = quoteLines.joinToString("\n"),
                            style = MaterialTheme.typography.bodyMedium.copy(color = cs.onSurfaceVariant),
                            onOpenUrl = { url -> runCatching { context.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(url))) } }
                        )
                    }
                    continue
                }

                // Headings
                val headingMatch = Regex("^(#{1,4})\\s+(.+)$").find(stripped)
                if (headingMatch != null) {
                    val level = headingMatch.groupValues[1].length
                    val hStyle = when (level) {
                        1 -> MaterialTheme.typography.titleLarge
                        2 -> MaterialTheme.typography.titleMedium
                        3 -> MaterialTheme.typography.titleSmall
                        else -> MaterialTheme.typography.labelLarge
                    }
                    InlineMarkdownText(
                        text = headingMatch.groupValues[2],
                        style = hStyle.copy(color = cs.onSurface),
                        onOpenUrl = { url -> runCatching { context.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(url))) } }
                    )
                    i++; continue
                }

                // Bullet / task / numbered lists
                val taskMatch = Regex("^[-*]\\s+\\[([ xX])]\\s+(.+)$").find(stripped)
                val bulletMatch = Regex("^[-*•]\\s+(.+)$").find(stripped)
                val numMatch = Regex("^(\\d+\\.)\\s+(.+)$").find(stripped)
                when {
                    taskMatch != null -> {
                        val checked = taskMatch.groupValues[1].equals("x", true)
                        Row(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.Top) {
                            Text(if (checked) "☑" else "☐", color = if (checked) tfm.success else cs.onSurfaceVariant, style = MaterialTheme.typography.bodyMedium)
                            InlineMarkdownText(
                                text = taskMatch.groupValues[2],
                                style = MaterialTheme.typography.bodyMedium.copy(color = cs.onSurface),
                                onOpenUrl = { url -> runCatching { context.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(url))) } }
                            )
                        }
                    }
                    bulletMatch != null -> {
                        Row(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.Top) {
                            Text("•", color = cs.primary, fontWeight = FontWeight.Bold, style = MaterialTheme.typography.bodyMedium)
                            InlineMarkdownText(
                                text = bulletMatch.groupValues[1],
                                style = MaterialTheme.typography.bodyMedium.copy(color = cs.onSurface),
                                onOpenUrl = { url -> runCatching { context.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(url))) } }
                            )
                        }
                    }
                    numMatch != null -> {
                        Row(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.Top) {
                            Text(numMatch.groupValues[1], color = cs.primary, fontWeight = FontWeight.SemiBold, style = MaterialTheme.typography.bodyMedium)
                            InlineMarkdownText(
                                text = numMatch.groupValues[2],
                                style = MaterialTheme.typography.bodyMedium.copy(color = cs.onSurface),
                                onOpenUrl = { url -> runCatching { context.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(url))) } }
                            )
                        }
                    }
                    else -> {
                        InlineMarkdownText(
                            text = line,
                            style = MaterialTheme.typography.bodyMedium.copy(color = cs.onSurface),
                            onOpenUrl = { url -> runCatching { context.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(url))) } }
                        )
                    }
                }
                i++
            }
        }
    }
}

@Composable
private fun MarkdownTable(lines: List<String>) {
    fun splitRow(row: String): List<String> = row.trim('|').split('|').map { it.trim() }
    val headers = splitRow(lines.first())
    val bodyRows = lines.drop(2).map(::splitRow)
    val cs = MaterialTheme.colorScheme
    Surface(
        shape = RoundedCornerShape(12.dp),
        color = cs.surfaceContainerHigh,
        border = BorderStroke(1.dp, cs.outlineVariant),
        modifier = Modifier.fillMaxWidth()
    ) {
        Column(Modifier.horizontalScroll(rememberScrollState()).padding(8.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
            Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                headers.forEach { cell ->
                    Text(cell, style = MaterialTheme.typography.labelMedium, fontWeight = FontWeight.Bold, color = cs.primary, modifier = Modifier.widthIn(min = 84.dp))
                }
            }
            HorizontalDivider(color = cs.outlineVariant)
            bodyRows.forEach { row ->
                Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                    headers.indices.forEach { idx ->
                        Text(row.getOrElse(idx) { "" }, style = MaterialTheme.typography.bodySmall, color = cs.onSurface, modifier = Modifier.widthIn(min = 84.dp))
                    }
                }
            }
        }
    }
}

private val INLINE_REGEX = Regex("""(`[^`\n]+`)|(\*\*[^*\n]+\*\*)|(~~[^~\n]+~~)|(\*[^*\n]+\*)|(\[([^\]\n]+)]\((https?://[^)\s]+)\))""")

@Suppress("DEPRECATION")
@Composable
private fun InlineMarkdownText(
    text: String,
    style: androidx.compose.ui.text.TextStyle,
    onOpenUrl: (String) -> Unit
) {
    val cs = MaterialTheme.colorScheme
    val tfm = LocalTfmColors.current
    val annotated: AnnotatedString = buildAnnotatedString {
        var cursor = 0
        for (m in INLINE_REGEX.findAll(text)) {
            if (m.range.first > cursor) append(text.substring(cursor, m.range.first))
            when {
                m.groups[1] != null -> {
                    pushStyle(SpanStyle(fontFamily = FontFamily.Monospace, fontSize = 12.5.sp, background = tfm.codeHeaderBg, color = cs.primary))
                    append(" " + m.groups[1]!!.value.drop(1).dropLast(1) + " ")
                    pop()
                }
                m.groups[2] != null -> {
                    pushStyle(SpanStyle(fontWeight = FontWeight.Bold))
                    append(m.groups[2]!!.value.drop(2).dropLast(2))
                    pop()
                }
                m.groups[3] != null -> {
                    pushStyle(SpanStyle(textDecoration = TextDecoration.LineThrough, color = cs.onSurfaceVariant))
                    append(m.groups[3]!!.value.drop(2).dropLast(2))
                    pop()
                }
                m.groups[4] != null -> {
                    pushStyle(SpanStyle(fontStyle = FontStyle.Italic))
                    append(m.groups[4]!!.value.drop(1).dropLast(1))
                    pop()
                }
                m.groups[5] != null -> {
                    val label = m.groups[6]!!.value
                    val url = m.groups[7]!!.value
                    pushStringAnnotation(tag = "URL", annotation = url)
                    pushStyle(SpanStyle(color = cs.secondary, textDecoration = TextDecoration.Underline))
                    append(label)
                    pop()
                    pop()
                }
            }
            cursor = m.range.last + 1
        }
        if (cursor < text.length) append(text.substring(cursor))
    }
    if (annotated.getStringAnnotations("URL", 0, annotated.length).isNotEmpty()) {
        ClickableText(
            text = annotated,
            style = style,
            onClick = { offset ->
                annotated.getStringAnnotations("URL", offset, offset).firstOrNull()?.let { onOpenUrl(it.item) }
            }
        )
    } else {
        Text(annotated, style = style)
    }
}
