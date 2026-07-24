package com.wordmemo.app.data.epub

/**
 * 章节数据模型。
 *
 * @param index 在 spine 中的序号（阅读顺序）
 * @param title 章节标题（取自 toc.ncx 或 xhtml <title>）
 * @param rawHtml 原始 XHTML 内容（用于 jsoup 进一步解析）
 * @param text 纯文本内容（去除 HTML 标签后）
 * @param filePath OPF manifest 中的 href 路径
 */
data class Chapter(
    val index: Int,
    val title: String,
    val rawHtml: String,
    val text: String,
    val filePath: String
)

/**
 * EPUB 解析结果。
 *
 * @param title 书名（取自 OPF <dc:title>）
 * @param author 作者（取自 OPF <dc:creator>）
 * @param chapters 章节列表（按 spine 阅读顺序）
 * @param bookCoverPath 封面图片路径（如有）
 */
data class Book(
    val title: String = "",
    val author: String = "",
    val chapters: List<Chapter> = emptyList(),
    val bookCoverPath: String? = null
)
