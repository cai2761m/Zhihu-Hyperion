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

import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.LinkAnnotation
import androidx.compose.ui.text.style.TextDecoration
import kotlin.test.Test
import kotlin.test.assertEquals

class HtmlTextTest {
    @Test
    fun articlePreviewPreservesTheBlueUnderlinedHyperlinkAndItsTarget() {
        val url = "http://link.zhihu.com/?target=https%3A//zhuanlan.zhihu.com/p/123"
        val preview = parseEmphasizedHtmlText("关于<a href=\"$url\">僵尸</a>[1]的文章", Color.Blue)

        assertEquals("关于僵尸[1]的文章", preview.text)
        val range = preview.getLinkAnnotations(0, preview.length).single()
        val link = range.item as LinkAnnotation.Url
        assertEquals("僵尸", preview.text.substring(range.start, range.end))
        assertEquals(url, link.url)
        assertEquals(Color.Blue, link.styles?.style?.color)
        assertEquals(TextDecoration.Underline, link.styles?.style?.textDecoration)
    }

    @Test
    fun linksDecodeAttributesAndKeepIndependentTextRanges() {
        val preview = parseEmphasizedHtmlText(
            "<A title=\"href='错误地址'\" HREF='/question/1?name=&#x4E2D;&amp;v=1'><b>第一&#x1F600;</b></A> 普通文字 " +
                "<a href=https://example.com/second>第二</a> <a name='ref_1'>[1]</a> " +
                "<a href=\"https://example.com/last\">未闭合",
            Color.Blue,
        )
        val links = preview.getLinkAnnotations(0, preview.length)
        assertEquals(listOf("第一😀", "第二", "未闭合"), links.map { preview.text.substring(it.start, it.end) })
        assertEquals(
            listOf("/question/1?name=中&v=1", "https://example.com/second", "https://example.com/last"),
            links.map { (it.item as LinkAnnotation.Url).url },
        )
    }

    @Test
    fun articleSummariesDisplayTextInsteadOfHtmlSource() {
        val summaries = mapOf(
            "（<i>Discours de la Méthode</i>）[1]在哲学概念中" to "（Discours de la Méthode）[1]在哲学概念中",
            "我今天有点累，关于<a href=\"http://link.zhihu.com/?target=https%3A//example.com\">僵尸</a>的文章" to
                "我今天有点累，关于僵尸的文章",
            "被假定为是一个在 <b>每一个</b>方面都与你完全一样的存在" to
                "被假定为是一个在 每一个方面都与你完全一样的存在",
            "<p>第一段<br>第二行</p><p>第三段 &amp; &#x4E2D;</p>" to "第一段 第二行 第三段 & 中 ",
            "<span title=\"1 > 0\">比较</span>：1 < 2，vector<bool>" to "比较：1 < 2，vector<bool>",
            "<span title='1 > 0'>&nbsp;L&apos;idée &amp; &#x1F600;</span>" to "\u00a0L'idée & 😀",
        )

        summaries.forEach { (html, expected) ->
            assertEquals(expected, parseEmphasizedHtmlText(html, Color.Red).text)
        }
    }

    @Test
    fun searchHighlightsSurviveOtherHtmlTagsAndAttributes() {
        val highlighted = parseEmphasizedHtmlText(
            "<p>查找<a href=\"https://example.com\"><EM class=\"highlight\"><b>关键</b>词</EM></a>与<em>另一个</em></p>",
            Color.Red,
        )

        assertEquals("查找关键词与另一个 ", highlighted.text)
        assertEquals(listOf("关键词", "另一个"), highlighted.spanStyles.map { highlighted.text.substring(it.start, it.end) })
        assertEquals(listOf(Color.Red, Color.Red), highlighted.spanStyles.map { it.item.color })
    }

    @Test
    fun parsesZhihuEmphasisWithoutDamagingLiteralTextOrEntities() {
        val highlighted = parseEmphasizedHtmlText(
            "为什么互联网给我一种想<em>搜</em>的东西什么都搜不到，屁用没有的信息一大堆的无力感？",
            Color.Red,
        )

        val highlightedSpan = highlighted.spanStyles.single()
        assertEquals("为什么互联网给我一种想搜的东西什么都搜不到，屁用没有的信息一大堆的无力感？", highlighted.text)
        assertEquals(1, highlighted.spanStyles.size)
        assertEquals(Color.Red, highlightedSpan.item.color)
        assertEquals(
            "搜",
            highlighted.text.substring(highlightedSpan.start, highlightedSpan.end),
        )

        val multiple = parseEmphasizedHtmlText("Search <em>keyword1</em> and <em>keyword2</em>", Color.Red)
        assertEquals("Search keyword1 and keyword2", multiple.text)
        assertEquals(2, multiple.spanStyles.size)
        assertEquals(listOf(Color.Red, Color.Red), multiple.spanStyles.map { it.item.color })

        listOf(
            "为什么Deepseek在输入<think 后会匹配到疑似其他对话?",
            "vector<bool>",
        ).forEach { source ->
            val text = parseEmphasizedHtmlText(source, Color.Red)

            assertEquals(source, text.text)
            assertEquals(0, text.spanStyles.size)
        }

        assertEquals("% 中 😀", parseEmphasizedHtmlText("&#37; &#x4E2D; &#X1F600;", Color.Red).text)
        assertEquals(
            "&#; &#x; &#x110000; &#xD800;",
            parseEmphasizedHtmlText("&#; &#x; &#x110000; &#xD800;", Color.Red).text,
        )
        assertEquals("", parseEmphasizedHtmlText("", Color.Red).text)
        assertEquals(
            "Test <em>keyword</em>",
            parseEmphasizedHtmlText("Test &lt;em&gt;<em>keyword</em>&lt;/em&gt;", Color.Red).text,
        )
    }
}
