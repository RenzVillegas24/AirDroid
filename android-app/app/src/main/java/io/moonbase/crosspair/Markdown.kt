package io.moonbase.crosspair

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.dp

/**
 * The Markdown subset GitHub release notes use: headings, bullets, rules,
 * fenced code, and inline bold, italic, code and links. Anything else is
 * shown as plain text rather than dropped.
 */
private sealed interface MdBlock {
    data class Heading(val level: Int, val text: String) : MdBlock
    data class Bullet(val text: String) : MdBlock
    data class Paragraph(val text: String) : MdBlock
    data class Code(val text: String) : MdBlock
    data object Rule : MdBlock
}

/** Splits release notes into blocks; the inline styles are applied when drawn. */
private fun parseMarkdown(markdown: String): List<MdBlock> {
    val blocks = mutableListOf<MdBlock>()
    val paragraph = StringBuilder()
    val code = StringBuilder()
    var inCode = false

    fun flushParagraph() {
        val text = paragraph.toString().trim()
        if (text.isNotEmpty()) blocks += MdBlock.Paragraph(text)
        paragraph.clear()
    }

    markdown.replace("\r\n", "\n").lines().forEach { raw ->
        val line = raw.trimEnd()
        val trimmed = line.trim()
        when {
            inCode -> {
                if (trimmed.startsWith("```")) {
                    blocks += MdBlock.Code(code.toString().trimEnd())
                    code.clear()
                    inCode = false
                } else {
                    code.appendLine(raw)
                }
            }
            trimmed.startsWith("```") -> {
                flushParagraph()
                inCode = true
            }
            trimmed.isEmpty() -> flushParagraph()
            trimmed == "---" || trimmed == "***" -> {
                flushParagraph()
                blocks += MdBlock.Rule
            }
            trimmed.startsWith("#") -> {
                flushParagraph()
                val level = trimmed.takeWhile { it == '#' }.length.coerceIn(1, 3)
                blocks += MdBlock.Heading(level, trimmed.drop(level).trim())
            }
            trimmed.startsWith("- ") || trimmed.startsWith("* ") -> {
                flushParagraph()
                blocks += MdBlock.Bullet(trimmed.drop(2).trim())
            }
            else -> {
                if (paragraph.isNotEmpty()) paragraph.append(' ')
                paragraph.append(trimmed)
            }
        }
    }
    if (inCode && code.isNotEmpty()) blocks += MdBlock.Code(code.toString().trimEnd())
    flushParagraph()
    return blocks
}

/** Inline `**bold**`, `*italic*`, `` `code` `` and `[text](url)`. */
private fun markdownInline(text: String, linkColor: Color): AnnotatedString = buildAnnotatedString {
    var i = 0
    while (i < text.length) {
        when {
            text.startsWith("**", i) -> {
                val end = text.indexOf("**", i + 2)
                if (end < 0) {
                    append(text[i]); i++
                } else {
                    withStyle(SpanStyle(fontWeight = FontWeight.SemiBold)) {
                        append(text.substring(i + 2, end))
                    }
                    i = end + 2
                }
            }
            text.startsWith("`", i) -> {
                val end = text.indexOf('`', i + 1)
                if (end < 0) {
                    append(text[i]); i++
                } else {
                    withStyle(
                        SpanStyle(
                            fontFamily = FontFamily.Monospace,
                            background = Color(0x22808080),
                        ),
                    ) {
                        append(text.substring(i + 1, end))
                    }
                    i = end + 1
                }
            }
            text.startsWith("*", i) -> {
                val end = text.indexOf('*', i + 1)
                if (end < 0) {
                    append(text[i]); i++
                } else {
                    withStyle(SpanStyle(fontStyle = FontStyle.Italic)) {
                        append(text.substring(i + 1, end))
                    }
                    i = end + 1
                }
            }
            text.startsWith("[", i) -> {
                val close = text.indexOf(']', i + 1)
                val open = if (close >= 0) text.indexOf('(', close) else -1
                val end = if (open == close + 1) text.indexOf(')', open) else -1
                if (close < 0 || open < 0 || end < 0) {
                    append(text[i]); i++
                } else {
                    withStyle(
                        SpanStyle(color = linkColor, textDecoration = androidx.compose.ui.text.style.TextDecoration.Underline),
                    ) {
                        append(text.substring(i + 1, close))
                    }
                    i = end + 1
                }
            }
            else -> {
                append(text[i]); i++
            }
        }
    }
}

@Composable
fun MarkdownText(markdown: String, modifier: Modifier = Modifier) {
    val colors = MaterialTheme.colorScheme
    val blocks = remember(markdown) { parseMarkdown(markdown) }
    Column(
        modifier = modifier,
        verticalArrangement = Arrangement.spacedBy(6.dp),
    ) {
        blocks.forEach { block ->
            when (block) {
                is MdBlock.Heading -> Text(
                    text = markdownInline(block.text, colors.primary),
                    style = when (block.level) {
                        1 -> MaterialTheme.typography.titleMedium
                        2 -> MaterialTheme.typography.titleSmall
                        else -> MaterialTheme.typography.labelLarge
                    },
                    fontWeight = FontWeight.SemiBold,
                    color = colors.onSurface,
                )
                is MdBlock.Bullet -> Row {
                    Text("•", style = MaterialTheme.typography.bodySmall, color = colors.primary)
                    Spacer(Modifier.width(6.dp))
                    Text(
                        text = markdownInline(block.text, colors.primary),
                        style = MaterialTheme.typography.bodySmall,
                        color = colors.onSurface,
                    )
                }
                is MdBlock.Paragraph -> Text(
                    text = markdownInline(block.text, colors.primary),
                    style = MaterialTheme.typography.bodySmall,
                    color = colors.onSurface,
                )
                is MdBlock.Code -> Text(
                    text = block.text,
                    style = MaterialTheme.typography.bodySmall.copy(fontFamily = FontFamily.Monospace),
                    color = colors.onSurface,
                    modifier = Modifier
                        .fillMaxWidth()
                        .background(
                            colors.surfaceContainerHigh,
                            RoundedCornerShape(8.dp),
                        )
                        .padding(8.dp),
                )
                MdBlock.Rule -> Spacer(
                    Modifier
                        .fillMaxWidth()
                        .padding(vertical = 2.dp)
                        .background(colors.outlineVariant)
                        .padding(top = 1.dp),
                )
            }
        }
    }
}
