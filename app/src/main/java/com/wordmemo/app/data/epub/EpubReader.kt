package com.wordmemo.app.data.epub

import android.content.Context
import android.net.Uri
import android.util.Xml
import org.jsoup.Jsoup
import org.xmlpull.v1.XmlPullParser
import java.io.InputStream
import java.util.zip.ZipFile

/**
 * EPUB 自解析器（零EPUB库依赖）。
 *
 * 解析流程：
 * 1. 解压 ZIP → 读 META-INF/container.xml → 找 OPF 路径
 * 2. 解析 OPF → manifest（文件清单）+ spine（阅读顺序）
 * 3. 解析 toc.ncx → 获取章节标题
 * 4. 按 spine 顺序读取各 XHTML → Jsoup 提取纯文本
 *
 * 适用：API 26+, Kotlin, 标准 EPUB 2/3 格式
 */
class EpubReader {

    /**
     * 从 URI 解析 EPUB 文件。
     * @param context Android context（用于 ContentResolver 读取 URI）
     * @param uri EPUB 文件的 content:// 或 file:// URI
     * @return 解析结果 Book
     */
    fun parse(context: Context, uri: Uri): Book {
        val inputStream = context.contentResolver.openInputStream(uri)
            ?: throw IllegalStateException("无法打开文件: $uri")

        val tempFile = java.io.File.createTempFile("epub_", ".epub", context.cacheDir)
        tempFile.deleteOnExit()
        inputStream.use { src ->
            tempFile.outputStream().use { dst ->
                src.copyTo(dst)
            }
        }

        return parseFromFile(tempFile)
    }

    /**
     * 从 assets 目录解析 EPUB 文件。
     * @param context Android context
     * @param assetPath assets 下的相对路径（如 "emerson.epub"）
     * @return 解析结果 Book
     */
    fun parseFromAssets(context: Context, assetPath: String): Book {
        val inputStream = context.assets.open(assetPath)
        val tempFile = java.io.File.createTempFile("epub_", ".epub", context.cacheDir)
        tempFile.deleteOnExit()
        inputStream.use { src ->
            tempFile.outputStream().use { dst ->
                src.copyTo(dst)
            }
        }
        return parseFromFile(tempFile)
    }

    /**
     * 从文件路径解析 EPUB。
     * @param file EPUB 文件
     * @return 解析结果 Book
     */
    fun parseFromFile(file: java.io.File): Book {
        ZipFile(file).use { zip ->
            // Step 1: 解析 container.xml → 获取 OPF 路径
            val containerEntry = zip.getEntry("META-INF/container.xml")
                ?: throw IllegalStateException("EPUB 格式无效: 缺少 META-INF/container.xml")
            val opfPath = parseContainerXml(zip.getInputStream(containerEntry))

            // Step 2: 解析 OPF → manifest + spine
            val opfEntry = zip.getEntry(opfPath)
                ?: throw IllegalStateException("OPF 文件未找到: $opfPath")
            val opfDir = opfPath.substringBeforeLast('/', "").let { if (it.isNotEmpty()) "$it/" else "" }

            val opfResult = parseOpf(zip.getInputStream(opfEntry), opfDir)

            // Step 3: 解析 toc.ncx → 获取章节标题
            val tocTitles = if (opfResult.tocHref != null) {
                parseTocNcx(zip, opfResult.tocHref, opfResult.spine)
            } else {
                emptyMap()
            }

            // Step 4: 按 spine 顺序读取各 XHTML
            val chapters = mutableListOf<Chapter>()
            val spineItems = opfResult.spine.mapNotNull { idRef ->
                opfResult.manifest[idRef]?.let { path -> idRef to path }
            }

            spineItems.forEachIndexed { index, (idRef, filePath) ->
                val entry = zip.getEntry(filePath)
                if (entry != null) {
                    val html = zip.getInputStream(entry).readBytes().toString(Charsets.UTF_8)
                    val doc = Jsoup.parse(html)
                    val text = doc.body()?.text()?.trim() ?: ""

                    // 尝试获取标题：优先 toc.ncx，次选 <h1>/<h2>，末选 <title>
                    val title = tocTitles[idRef]
                        ?: doc.select("h1, h2").firstOrNull()?.text()
                        ?: doc.title()
                        ?: "第 ${index + 1} 章"

                    chapters.add(
                        Chapter(
                            index = index,
                            title = title,
                            rawHtml = html,
                            text = text,
                            filePath = filePath
                        )
                    )
                }
            }

            return Book(
                title = opfResult.bookTitle,
                author = opfResult.bookAuthor,
                chapters = chapters,
                bookCoverPath = opfResult.bookCoverPath
            )
        }
    }

    // ==================== container.xml 解析 ====================

    private fun parseContainerXml(inputStream: InputStream): String {
        val parser = Xml.newPullParser()
        parser.setInput(inputStream, "UTF-8")
        parser.nextTag()

        var opfPath = ""

        while (parser.eventType != XmlPullParser.END_DOCUMENT) {
            if (parser.eventType == XmlPullParser.START_TAG && parser.name == "rootfile") {
                opfPath = parser.getAttributeValue(null, "full-path") ?: ""
                if (opfPath.startsWith("/")) opfPath = opfPath.removePrefix("/")
            }
            parser.next()
        }

        require(opfPath.isNotEmpty()) { "container.xml 中未找到 rootfile/full-path" }
        return opfPath
    }

    // ==================== OPF 解析 ====================

    private data class OpfResult(
        val manifest: Map<String, String>, // id → file path
        val spine: List<String>,           // idref 列表（阅读顺序）
        val bookTitle: String,
        val bookAuthor: String,
        val bookCoverPath: String?,
        val tocHref: String?               // toc.ncx 路径
    )

    private fun parseOpf(inputStream: InputStream, opfDir: String): OpfResult {
        val parser = Xml.newPullParser()
        parser.setInput(inputStream, "UTF-8")
        parser.nextTag()

        val manifest = mutableMapOf<String, String>()
        val spine = mutableListOf<String>()
        var title = ""
        var author = ""
        var coverPath: String? = null
        var tocHref: String? = null
        var coverId: String? = null

        var inMetadata = false
        var inManifest = false
        var inSpine = false
        var currentTag = ""

        while (parser.eventType != XmlPullParser.END_DOCUMENT) {
            when (parser.eventType) {
                XmlPullParser.START_TAG -> {
                    currentTag = parser.name
                    when (parser.name) {
                        "metadata" -> inMetadata = true
                        "manifest" -> inManifest = true
                        "spine" -> inSpine = true
                        "item" -> {
                            if (inManifest) {
                                val id = parser.getAttributeValue(null, "id")
                                val href = parser.getAttributeValue(null, "href")
                                val mediaType = parser.getAttributeValue(null, "media-type")
                                if (id != null && href != null) {
                                    val fullPath = opfDir + href
                                    manifest[id] = fullPath
                                    // 检测 toc.ncx
                                    if (id == "ncx" || id == "toc" ||
                                        mediaType == "application/x-dtbncx+xml"
                                    ) {
                                        tocHref = fullPath
                                    }
                                    // 检测封面
                                    if (mediaType?.startsWith("image/") == true &&
                                        (id.contains("cover", ignoreCase = true) ||
                                                href.contains("cover", ignoreCase = true))
                                    ) {
                                        coverPath = fullPath
                                        coverId = id
                                    }
                                    val props = parser.getAttributeValue(null, "properties")
                                    if (props?.contains("cover-image") == true) {
                                        coverPath = fullPath
                                        coverId = id
                                    }
                                }
                            }
                        }
                        "itemref" -> {
                            if (inSpine) {
                                val idref = parser.getAttributeValue(null, "idref")
                                if (idref != null) spine.add(idref)
                            }
                        }
                        "meta" -> {
                            if (inMetadata) {
                                val name = parser.getAttributeValue(null, "name")
                                val content = parser.getAttributeValue(null, "content")
                                if (name == "cover" && content != null) {
                                    coverId = content
                                }
                            }
                        }
                    }
                }

                XmlPullParser.TEXT -> {
                    val text = parser.text?.trim() ?: ""
                    when {
                        inMetadata && currentTag == "title" && title.isEmpty() -> title = text
                        inMetadata && currentTag == "creator" && author.isEmpty() -> author = text
                    }
                }

                XmlPullParser.END_TAG -> {
                    when (parser.name) {
                        "metadata" -> inMetadata = false
                        "manifest" -> inManifest = false
                        "spine" -> inSpine = false
                    }
                    currentTag = ""
                }
            }
            parser.next()
        }

        // 如果 meta cover 指定了 id，从 manifest 获取路径
        if (coverId != null && coverPath == null) {
            coverPath = manifest[coverId]
        }

        return OpfResult(
            manifest = manifest,
            spine = spine,
            bookTitle = title,
            bookAuthor = author,
            bookCoverPath = coverPath,
            tocHref = tocHref
        )
    }

    // ==================== toc.ncx 解析 ====================

    /**
     * 解析 toc.ncx → 获取章节标题映射。
     * 返回 Map<spine_idref, title>
     *
     * toc.ncx 结构：
     * <navMap>
     *   <navPoint id="..." playOrder="1">
     *     <navLabel><text>第一章</text></navLabel>
     *     <content src="chapter1.xhtml"/>
     *   </navPoint>
     *   ...
     * </navMap>
     *
     * 我们将 content 的 src 与 manifest 的 href 匹配，找到对应的 idref。
     */
    private fun parseTocNcx(
        zip: ZipFile,
        tocPath: String,
        spineIdRefs: List<String>
    ): Map<String, String> {
        val result = mutableMapOf<String, String>()
        try {
            val entry = zip.getEntry(tocPath) ?: return emptyMap()
            val parser = Xml.newPullParser()
            parser.setInput(zip.getInputStream(entry), "UTF-8")
            parser.nextTag()

            // 先构建 manifest href → idref 的反向映射（通过解析 OPF 得到）
            // 但这里我们只有 spineIdRefs，没有 full manifest
            // 所以先用 navPoint 顺序匹配 spine 顺序

            val navLabels = mutableListOf<String>()
            var inNavMap = false
            var inNavPoint = false
            var inNavLabel = false
            var currentText = ""

            while (parser.eventType != XmlPullParser.END_DOCUMENT) {
                when (parser.eventType) {
                    XmlPullParser.START_TAG -> {
                        when (parser.name) {
                            "navMap" -> inNavMap = true
                            "navPoint" -> {
                                inNavPoint = true
                                currentText = ""
                            }
                            "navLabel" -> if (inNavPoint) inNavLabel = true
                            "text" -> { /* 等 TEXT 事件抓值 */ }
                        }
                    }
                    XmlPullParser.TEXT -> {
                        val text = parser.text?.trim() ?: ""
                        if (inNavLabel && text.isNotEmpty()) {
                            currentText = text
                        }
                    }
                    XmlPullParser.END_TAG -> {
                        when (parser.name) {
                            "navPoint" -> {
                                if (currentText.isNotEmpty()) {
                                    navLabels.add(currentText)
                                }
                                inNavPoint = false
                                currentText = ""
                            }
                            "navLabel" -> inNavLabel = false
                            "navMap" -> inNavMap = false
                        }
                    }
                }
                parser.next()
            }

            // 按顺序匹配到 spine idrefs
            navLabels.forEachIndexed { index, label ->
                if (index < spineIdRefs.size) {
                    result[spineIdRefs[index]] = label
                }
            }
        } catch (_: Exception) {
            // toc.ncx 解析失败不影响主体功能
        }
        return result
    }
}
