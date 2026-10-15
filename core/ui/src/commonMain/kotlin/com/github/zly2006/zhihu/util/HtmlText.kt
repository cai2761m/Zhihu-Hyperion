/*
 * Zhihu-Hyperion - Free & Ad-Free Zhihu client for all platforms.
 * Copyright (C) 2024-2026, zly2006 <i@zly2006.me>
 * Co-author: eltavine <me@eltavine.com>
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU Affero General Public License as published by
 * the Free Software Foundation (version 3 only).
 *
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU Affero General Public License for more details.
 *
 * You should have received a copy of the GNU Affero General Public License
 * along with this program.  If not, see <https://www.gnu.org/licenses/>.
 */

package com.github.zly2006.zhihu.util

import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.LinkAnnotation
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.TextLinkStyles
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.style.TextDecoration

// Only recognize HTML tags used in previews: literal text such as vector<bool> must survive.
private val previewHtmlTag = Regex(
    """</?(em|a|b|strong|i|u|s|del|span|font|sup|sub|p|div|br|hr|ul|ol|li|blockquote|h[1-6]|img)(?=[\s/>])(?:[^"'<>]|"[^"]*"|'[^']*')*>""",
    RegexOption.IGNORE_CASE,
)
private val previewHtmlBlockTags = setOf("p", "div", "br", "hr", "ul", "ol", "li", "blockquote", "h1", "h2", "h3", "h4", "h5", "h6")
private val previewHtmlAttribute = Regex("""\s+([\w:-]+)(?:\s*=\s*(?:"([^"]*)"|'([^']*)'|([^\s"'=<>`]+)))?""")
private val previewHtmlEntity = Regex("&([^;&]+);")

private fun decodePreviewHtmlEntity(entity: String): String? = when (entity) {
    "lt" -> {
        "<"
    }

    "gt" -> {
        ">"
    }

    "quot" -> {
        "\""
    }

    "amp" -> {
        "&"
    }

    "nbsp" -> {
        "\u00a0"
    }

    "apos" -> {
        "'"
    }

    else -> {
        val radix = when {
            entity.startsWith("#x", ignoreCase = true) -> 16
            entity.startsWith('#') -> 10
            else -> null
        }
        val codePoint = radix?.let { entity.substring(if (it == 16) 2 else 1).toIntOrNull(it) }
        when {
            codePoint == null || codePoint !in 0..0x10FFFF || codePoint in 0xD800..0xDFFF -> {
                null
            }

            codePoint <= 0xFFFF -> {
                codePoint.toChar().toString()
            }

            else -> {
                val offset = codePoint - 0x10000
                charArrayOf(((offset shr 10) + 0xD800).toChar(), ((offset and 0x3FF) + 0xDC00).toChar()).concatToString()
            }
        }
    }
}

fun parseEmphasizedHtmlText(
    html: String,
    emphasisColor: Color,
): AnnotatedString = buildAnnotatedString {
    var cursor = 0
    var emphasisStart: Int? = null
    var endsWithWhitespace = true
    var linkUrl: String? = null
    var linkStart = 0
    val linkStyles = TextLinkStyles(SpanStyle(color = emphasisColor, textDecoration = TextDecoration.Underline))

    fun finishLink() {
        linkUrl?.let { url ->
            if (linkStart < length) addLink(LinkAnnotation.Url(url, linkStyles), linkStart, length)
        }
        linkUrl = null
    }
    while (cursor < html.length) {
        val tag = if (html[cursor] == '<') previewHtmlTag.matchAt(html, cursor) else null
        when {
            tag != null -> {
                val name = tag.groupValues[1].lowercase()
                if (name == "a") {
                    finishLink()
                    if (!tag.value.startsWith("</")) {
                        val attribute = previewHtmlAttribute.findAll(tag.value).firstOrNull { it.groupValues[1].equals("href", ignoreCase = true) }
                        val href = attribute?.groupValues?.drop(2)?.firstOrNull(String::isNotEmpty)
                        linkUrl = href
                            ?.let { value ->
                                previewHtmlEntity.replace(value) { entity -> decodePreviewHtmlEntity(entity.groupValues[1]) ?: entity.value }
                            }?.trim()
                            ?.takeIf(String::isNotEmpty)
                        linkStart = length
                    }
                } else if (name == "em") {
                    if (tag.value.startsWith("</")) {
                        emphasisStart?.let { start ->
                            if (start < length) {
                                addStyle(SpanStyle(color = emphasisColor), start, length)
                            }
                        }
                        emphasisStart = null
                    } else {
                        emphasisStart = length
                    }
                } else if (name in previewHtmlBlockTags && !endsWithWhitespace) {
                    append(' ')
                    endsWithWhitespace = true
                }
                cursor = tag.range.last + 1
            }

            html[cursor] == '&' -> {
                val entityEnd = html.indexOf(';', cursor + 1)
                val entity = entityEnd.takeIf { it != -1 }?.let { html.substring(cursor + 1, it) }
                val decoded = entity?.let(::decodePreviewHtmlEntity)
                append(decoded ?: "&")
                endsWithWhitespace = decoded?.last()?.isWhitespace() == true
                cursor = if (decoded == null) cursor + 1 else entityEnd + 1
            }

            else -> {
                append(html[cursor])
                endsWithWhitespace = html[cursor].isWhitespace()
                cursor++
            }
        }
    }
    finishLink()
}

/**
 * Composable function to parse HTML text with Material Theme primary color for emphasis.
 * This is a convenience function that uses the current theme's primary color.
 *
 * @param html The HTML string to parse
 * @return AnnotatedString with styled text using theme colors
 */
@Composable
fun parseEmphasizedHtmlTextWithTheme(html: String): AnnotatedString {
    val emphasisColor = MaterialTheme.colorScheme.primary
    return remember(html, emphasisColor) { parseEmphasizedHtmlText(html, emphasisColor) }
}
