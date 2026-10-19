package dev.riyadm.applock.core.update

import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.withStyle

/**
 * Turns GitHub-flavoured release-note markdown into readable styled text: headings become bold
 * lines, list items become bullets, links keep their label and inline markers are dropped.
 */
object ReleaseNotesFormatter {
    private val headingRegex = Regex("^#{1,6}\\s+(.*)$")
    private val bulletRegex = Regex("^(\\s*)[-*+]\\s+(?:\\[[ xX]\\]\\s+)?(.*)$")
    private val orderedRegex = Regex("^(\\s*)(\\d+)[.)]\\s+(.*)$")
    private val imageRegex = Regex("!\\[([^\\]]*)\\]\\([^)]*\\)")
    private val linkRegex = Regex("\\[([^\\]]+)\\]\\([^)]*\\)")
    private val htmlCommentRegex = Regex("<!--.*?-->", RegexOption.DOT_MATCHES_ALL)
    private val htmlTagRegex = Regex("</?[a-zA-Z][^>]*>")
    private val boldRegex = Regex("\\*\\*(.+?)\\*\\*|__(.+?)__")
    private val horizontalRuleRegex = Regex("^\\s*([-*_])(\\s*\\1){2,}\\s*$")

    fun format(markdown: String): AnnotatedString {
        val cleaned = markdown
            .replace("\r\n", "\n")
            .replace(htmlCommentRegex, "")
            .replace(imageRegex, "$1")
            .replace(linkRegex, "$1")
            .replace(htmlTagRegex, "")

        val lines = cleaned.lines()
            .filterNot { it.trim().startsWith("```") || horizontalRuleRegex.matches(it) }
            .map { it.trimEnd() }

        return buildAnnotatedString {
            var previousBlank = true
            var first = true
            for (raw in lines) {
                if (raw.isBlank()) {
                    if (!previousBlank) append("\n")
                    previousBlank = true
                    continue
                }
                if (!first) append("\n")
                first = false
                previousBlank = false

                val heading = headingRegex.find(raw)
                val bullet = bulletRegex.find(raw)
                val ordered = orderedRegex.find(raw)
                when {
                    heading != null -> withStyle(SpanStyle(fontWeight = FontWeight.Bold)) {
                        append(stripInline(heading.groupValues[1]))
                    }

                    bullet != null -> {
                        val indent = "    ".repeat(bullet.groupValues[1].length / 2)
                        append("$indent•  ")
                        appendInline(bullet.groupValues[2])
                    }

                    ordered != null -> {
                        val indent = "    ".repeat(ordered.groupValues[1].length / 2)
                        append("$indent${ordered.groupValues[2]}.  ")
                        appendInline(ordered.groupValues[3])
                    }

                    else -> appendInline(raw.trimStart().removePrefix(">").trimStart())
                }
            }
        }
    }

    private fun AnnotatedString.Builder.appendInline(text: String) {
        var index = 0
        for (match in boldRegex.findAll(text)) {
            append(stripInline(text.substring(index, match.range.first)))
            val content = match.groupValues[1].ifEmpty { match.groupValues[2] }
            withStyle(SpanStyle(fontWeight = FontWeight.SemiBold)) {
                append(stripInline(content))
            }
            index = match.range.last + 1
        }
        append(stripInline(text.substring(index)))
    }

    private fun stripInline(text: String): String = text
        .replace("**", "")
        .replace("__", "")
        .replace("`", "")
        .replace("~~", "")
}
