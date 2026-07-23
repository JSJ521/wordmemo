# 词鼠书记 需求规格说明书

**文档编号**: REQ_SPEC_v1 | **版本**: v1 | **日期**: 2026-07-22  
**应用**: 词鼠书记 (WordMemo) v7 → v8 | **状态**: ✅ 已通过 Schema 校验

---

## 一、项目背景

| 维度 | 内容 |
|------|------|
| 技术栈 | Jetpack Compose + Kotlin 1.9.22 + Gradle 8.9 + JDK 17 |
| DI | Hilt 2.50 (KSP) |
| 数据库 | Room 2.6.1 (KSP) |
| 网络 | OkHttp 4.12.0 |
| OCR | Tesseract (tess-two 9.1.0) |
| 设计理念 | 无注册、纯离线、数据全本地、权限最小化 |
| 兼容性 | minSdk=26, targetSdk=34, 国产手机无GMS |

---

## 二、模块一：影子跟读学习模块

**模块ID**: M1 | **风险等级**: 🔴 高 | **能力标签**: `shadowing`, `video_playback`, `bilibili_import`, `local_video_import`, `subtitle_sync`, `sentence_segmentation`, `voice_recording`, `audio_playback`, `file_management`, `yt-dlp_integration`

### 功能清单（8个）

| ID | 名称 | 优先级 | 验收标准数 |
|----|------|--------|-----------|
| M1-F1 | B站视频导入 | P0 | 7 |
| M1-F2 | 本地视频文件导入 | P0 | 5 |
| M1-F3 | 视频播放与字幕同步显示 | P0 | 7 |
| M1-F4 | 句子级分割与逐句跟读 | P0 | 5 |
| M1-F5 | 跟读录音与回放对比 | P0 | 8 |
| M1-F6 | 跟读进度管理 | P1 | 5 |
| M1-F7 | 视频管理（列表/删除/重命名） | P1 | 4 |
| M1-F8 | 从跟读提取单词到词库 | P2 | 4 |

### 用户交互流程（8步）

```
① 主页入口 → ② 导入视频（B站链接 / 本地文件）
→ ③ 视频处理（提取音频→Whisper识别→SRT字幕）
→ ④ 视频播放+字幕同步
→ ⑤ 切换跟读模式 → ⑥ 听原音→录音→松开
→ ⑦ 回放对比（原音↔用户录音交替播放）
→ ⑧ 下一句 / 完成
```

### 核心约束

- ✅ **离线优先**: 视频播放与字幕显示完全离线；B站下载需网络
- ✅ **权限最小化**: 仅 RECORD_AUDIO + READ_MEDIA_VIDEO（SAF替代存储权限）
- ✅ **数据全本地**: 视频/字幕/录音均存于应用私有目录

### 推荐技术路线

| 组件 | 方案 | 说明 |
|------|------|------|
| 视频下载 | yt-dlp-android wrapper | B站视频下载 |
| 视频播放 | Media3 (ExoPlayer) | 系统MediaCodec解码，无GMS |
| 语音识别 | Whisper (在线API或离线whisper.cpp) | 句子级时间戳对齐 |
| 录音 | MediaRecorder (AAC) | Push-to-Talk模式 |
| 持久化 | Room | 跟读进度/录音元数据 |

---

## 三、模块二：发音测评与纠正模块

**模块ID**: M2 | **风险等级**: 🟡 中 | **能力标签**: `pronunciation_assessment`, `gop_algorithm`, `phoneme_scoring`, `whisper_transcription`, `ai_correction`, `mispronunciation_detection`, `speech_analysis`, `progress_tracking`

### 功能清单（6个）

| ID | 名称 | 优先级 | 验收标准数 |
|----|------|--------|-----------|
| M2-F1 | 录音测评 — 整体发音评分 | P0 | 7 |
| M2-F2 | 音素级GOP评分 | P0 | 5 |
| M2-F3 | 错误音素纠正建议 | P0 | 6 |
| M2-F4 | AI对比式诊断报告 | P1 | 5 |
| M2-F5 | 发音进步追踪 | P1 | 5 |
| M2-F6 | 标准发音音库（参考音频） | P2 | 5 |

### 用户交互流程（7步）

```
① 选择录音（跟读录音 / 现场录音）
→ ② 系统分析（Whisper转录→音素对齐→GOP评分）
→ ③ 显示整体评分（百分制+等级）
→ ④ 逐音素高亮（绿=正确 / 红=错误 / 黄=偏差）
→ ⑤ 显示纠正建议（舌位/口型描述 + 标准发音音频）
→ ⑥ 用户重录再测评
→ ⑦ 与历史评分对比，展示进步曲线
```

### 技术方案演进

| 版本 | 方案 | 精度 | 离线 |
|------|------|------|------|
| v1 | DeepSeek API 文本级分析 | 中 | ❌ |
| v2 | Whisper + DeepSeek 时间戳对齐 | 高 | ❌ |
| v3 | whisper.cpp JNI + GOP 算法 | 最高 | ✅ |

### 核心约束

- ✅ **离线优先**: 标准发音音库和音标浏览可完全离线
- ✅ **权限最小化**: 仅复用 RECORD_AUDIO（与M1共享）
- ✅ **数据全本地**: 测评结果仅存本地 Room 数据库

---

## 四、交叉依赖分析（3组）

| 来源 | 目标 | 依赖 | 类型 |
|------|------|------|------|
| M2 发音测评 | M1 影子跟读 | M2直接复用M1的跟读录音作为测评输入 | 强依赖 |
| M1 影子跟读 | 现有词库 | 从字幕提取单词需调用现有AI翻译流程 | 中依赖 |
| M2 发音测评 | 现有AI基础设施 | 复用DeepSeek API生成纠正建议 | 强依赖 |

---

## 五、架构影响

### 新增包（6个）
```
com.wordmemo.app.ui.screen.shadowing
com.wordmemo.app.ui.screen.pronunciation
com.wordmemo.app.data.shadowing
com.wordmemo.app.data.pronunciation
com.wordmemo.app.domain.usecase.shadowing
com.wordmemo.app.domain.usecase.pronunciation
```

### 修改文件（7个）
```
app/build.gradle.kts          → 新增 Media3 / yt-dlp 依赖
AndroidManifest.xml           → 新增 RECORD_AUDIO 权限
Screen.kt                     → 新增路由 Shadowing / PronunciationAssessment
NavGraph.kt                   → 新增导航路由注册
WordMemoDatabase.kt           → 新增 Room Entity 注册（DB版本迁移）
Di/DatabaseModule.kt          → 新增 DAO 注入
```

### 新增 Room 表（5个）
```
shadowing_videos      — 视频元数据
shadowing_sentences   — 句子分割表
shadowing_records     — 跟读记录表
assessment_records    — 发音测评记录
phoneme_scores        — 各音素评分明细
```

### 版本迁移
```
next_version = v8 (versionCode=80000)
db_migration    = Room 增量迁移（仅新增表，无破坏性变更）
```

---

## 六、发布计划（6周）

| 里程碑 | 周次 | 交付物 |
|--------|------|--------|
| M1.1 | W1 | 视频导入+播放+字幕同步基础功能 |
| M1.2 | W2 | 句子分割+跟读录音+回放对比 |
| M2.1 | W3 | 发音测评DeepSeek API集成+整体评分 |
| M2.2 | W4 | 纠正建议生成+标准发音音库 |
| Integration | W5 | 两模块集成+跟读→测评数据打通 |
| QA & Polish | W6 | 全流程测试+国产手机兼容+UI打磨 |

---

## 七、术语表

| 术语 | 说明 |
|------|------|
| Shadowing | 影子跟读法 — 听原音后立即模仿跟读的口语训练法 |
| GOP | Goodness of Pronunciation — 音素级发音准确度评分算法 |
| Phoneme | 音素 — 语言中最小的语音单位（英语约44个） |
| IPA | 国际音标 (International Phonetic Alphabet) |
| Whisper | OpenAI 语音识别模型，支持多语言转写和句子级时间戳 |
| yt-dlp | youtube-dl 分支，支持B站等数百个视频网站下载 |
| SAF | Storage Access Framework — 无需存储权限的文件选择框架 |
| SRT | SubRip 字幕格式 — 含序号、时间轴、文本的标准字幕格式 |

---

*文档生成时间: 2026-07-22 | Schema 校验: ✅ PASS | 总验收标准: 78条*
