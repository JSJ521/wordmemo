package com.wordmemo.app.util

/**
 * 影子跟读 + 发音测评 文案常量。
 *
 * 使用示例：
 *   text = CopyConstants.Shadowing.VideoList.EMPTY_FULL
 *   text = CopyConstants.Pronunciation.Result.SCORE_EXCELLENT
 *
 * @see copy_draft.json 完整文案草稿（docs/shadowing-pronunciation/）
 */
object CopyConstants {

    // ─────────────────────────────────────────────
    // 跨模块通用
    // ─────────────────────────────────────────────
    const val BACK = "返回"
    const val CANCEL = "取消"
    const val CONFIRM = "确认"
    const val RETRY = "重试"
    const val DONE = "完成"
    const val SAVE = "保存"
    const val DELETE = "删除"
    const val LOADING = "加载中…"
    const val ERROR_GENERIC = "出错了"
    const val NETWORK_ERROR = "网络异常"

    // ═════════════════════════════════════════════
    // M1 影子跟读
    // ═════════════════════════════════════════════
    object Shadowing {
        const val TITLE = "影子跟读"

        // ── 视频列表页 ──
        object VideoList {
            const val EMPTY_TITLE = "暂无视频"
            const val EMPTY_ACTION = "导入开始练习"
            const val EMPTY_FULL = "暂无视频，导入开始练习"
            const val BTN_IMPORT = "导入视频"
            const val BILIBILI_PLACEHOLDER = "粘贴B站视频链接"
        }

        // ── 视频播放页 ──
        object Player {
            const val PLAY = "播放"
            const val PAUSE = "暂停"
            const val FORWARD_10S = "快进10秒"
            const val REWIND_10S = "快退10秒"
            const val SPEED_SELECTOR = "倍速"

            /** 有效倍速值列表 */
            val SPEED_OPTIONS: Set<Float> = setOf(0.75f, 1.0f, 1.25f, 1.5f)

            /** 格式化倍速显示标签 */
            fun speedLabel(speed: Float): String = when (speed) {
                0.75f -> "0.75x"
                1.0f  -> "1.0x"
                1.25f -> "1.25x"
                1.5f  -> "1.5x"
                else  -> "${speed}x"
            }
        }

        // ── 跟读模式页 ──
        object ShadowingMode {
            const val TITLE = "跟读"
            const val PRESS_TO_RECORD = "按住录音"
            const val RELEASE_TO_STOP = "松开停止"
            const val ORIGINAL_AUDIO = "原音"
            const val MY_RECORDING = "我的录音"
            const val LOOP_COMPARE = "循环对比"
            const val LOOP_COMPARE_ACTIVE = "循环对比中"
        }

        // ── 状态提示 ──
        object Status {
            /** 下载进度 {0} = 百分比数字 */
            const val DOWNLOADING = "正在下载 %d%%"

            const val PROCESSING = "正在分析音频…"
            const val DOWNLOAD_FAILED = "下载失败，点击重试"
            const val IMPORT_FAILED = "导入失败"
            const val FORMAT_NOT_SUPPORTED = "不支持的格式"
        }
    }

    // ═════════════════════════════════════════════
    // M2 发音测评
    // ═════════════════════════════════════════════
    object Pronunciation {
        const val TITLE = "发音测评"

        // ── 测评主页 ──
        object Main {
            const val EMPTY_HINT = "选择录音进行测评"
            const val START_ASSESSMENT = "开始测评"
        }

        // ── 测评结果页 ──
        object Result {
            const val TITLE = "测评结果"
            const val SCORE_EXCELLENT = "优秀"
            const val SCORE_GOOD = "良好"
            const val SCORE_FAIR = "一般"
            const val SCORE_NEEDS_IMPROVEMENT = "需改进"
            const val ACCURACY = "发音准确度"
            const val FLUENCY = "流利度"
            const val COMPLETENESS = "完整度"

            /** 音素标记 */
            const val PHONEME_CORRECT = "发音正确"
            const val PHONEME_DEVIATED = "发音偏差"
            const val PHONEME_INCORRECT = "发音错误"
        }

        // ── 纠正建议 ──
        object Correction {
            const val TITLE = "纠正建议"
            const val TONGUE_POSITION_PREFIX = "试试这样："
            const val COMMON_ERRORS = "常见错误"
            const val IMPROVEMENT_TIPS = "改进建议"
        }

        // ── 进步追踪 ──
        object Progress {
            const val SCORE_TREND = "评分趋势"
            const val COMMON_ERRORS = "常见错误"
            const val NO_HISTORY = "暂无测评记录"
        }
    }
}
