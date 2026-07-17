# 词鼠书记 UI 合并方案

> 目标: 关系网独立保留, AI助记+AI生成合并为一个板块, AI关系移除
> 现状分析时间: 基于当前代码库完整审计

---

## 一、信息架构图 — 新导航结构

```
┌─────────────────────────────────────────┐
│  底部导航 / 入口                         │
│  ┌────────┐ ┌────────┐ ┌──────┐ ┌────┐ │
│  │ 单词本  │ │ 复习    │ │ 分组  │ │ 设置│ │
│  └────┬───┘ └────────┘ └──────┘ └────┘ │
│       │                                  │
│       ▼                                  │
│  ┌─────────────┐  ┌──────────────────┐  │
│  │ 单词详情      │  │ 单词详情 (续)     │  │
│  │ ├ 释义/备注   │  │ ├ AI学习 (新)     │  │
│  │ ├ 分组        │  │ ├ 单词图谱 (保留)  │  │
│  │ └ 添加时间    │  │ └── 关系网(独立)  │  │
│  └──────┬───────┘  └──────────────────┘  │
│         │                                 │
│         ├────────────────────┐             │
│         ▼                    ▼             │
│  ┌─────────────┐   ┌──────────────────┐   │
│  │ AI学习 (新)  │   │ 单词图谱 (保留)   │   │
│  │ ├ 助记方法    │   │ └ 网状关系图     │   │
│  │ │ ・谐音记忆    │   └──────────────────┘   │
│  │ │ ・词根词缀    │                         │
│  │ │ ・故事联想    │                         │
│  │ └─────────────┘                         │
│  │ ├ AI生成       │                         │
│  │   │ ・生成按钮   │                         │
│  │   │ ・词汇列表   │                         │
│  └─────────────────┘                        │
└─────────────────────────────────────────┘
```

### 变更摘要

| 板块 | 原状态 | 新状态 | 说明 |
|------|--------|--------|------|
| 关系网 (WordGraph) | 独立 | ✅ **保留** | 完全不变 |
| AI助记 (AiMnemonics) | 独立 | ➡️ **合并入 AI学习** | 成为 Tab 页签 |
| AI生成 (WordList中的AiVocabSection) | 内嵌在单词本页 | ➡️ **合并入 AI学习** | 成为 Tab 页签 |
| AI关系/关联图谱 (AiRelations) | 独立 | ❌ **移除** | 与关系网功能重叠 |

---

## 二、建议屏幕布局 — "AI学习" 页面原型

```
┌─────────────────────────────────────┐
│  ← 返回          AI学习              │  ← TopAppBar
├─────────────────────────────────────┤
│  persist                            │
│    English Word                      │  ← 单词标题 (字号 28sp, Bold)
│    中文释义                          │  ← 释义 (字号 18sp, Gray)
├─────────────────────────────────────┤
│  [助记方法]  [AI生成]                │  ← TabRow 页签
├─────────────────────────────────────┤
│  ┌── Tab 1: 助记方法 ────────────┐  │
│  │                                │  │
│  │  ┌─ Card ───────────────────┐ │  │
│  │  │ [谐音记忆]               │ │  │  ← 紫色 AiBadge
│  │  │ 内容...                  │ │  │
│  │  └─────────────────────────┘ │  │
│  │  ┌─ Card ───────────────────┐ │  │
│  │  │ [词根词缀]               │ │  │
│  │  │ 内容...                  │ │  │
│  │  └─────────────────────────┘ │  │
│  │  ┌─ Card ───────────────────┐ │  │
│  │  │ [故事联想]               │ │  │
│  │  │ 内容...                  │ │  │
│  │  └─────────────────────────┘ │  │
│  └────────────────────────────────┘  │
│                                      │
│  ┌── Tab 2: AI生成 ──────────────┐  │
│  │  ┌──────────────────────────┐ │  │
│  │  │ 🤖 海外 EPC 项目词汇      │ │  │  ← 浅蓝背景 Card
│  │  │                     [生成] │ │  │  ← 按钮
│  │  │  ✅ 已生成 5 个行业词汇    │ │  │  ← 结果消息
│  │  │  ▸ word1 — 释义1          │ │  │
│  │  │  ▸ word2 — 释义2          │ │  │  ← 生成结果列表
│  │  └──────────────────────────┘ │  │
│  └────────────────────────────────┘  │
└─────────────────────────────────────┘
```

### Tab 方案对比 & 推荐

| 方案 | 描述 | 优点 | 缺点 |
|------|------|------|------|
| **A. TabRow 页签** ✅ **推荐** | 顶部两个 Tab，"助记方法" + "AI生成" | 信息组织清晰，适合两大功能域；用户可快速切换 | 需要额外 Tab 实现 |
| B. 上下滚动 | 同一页纵向排列，上面助记方法、下面AI生成 | 实现简单，所有内容一目了然 | 助记方法可能很长时 AI生成在下面不可见 |
| C. BottomSheet + 浮动按钮 | 默认显示助记方法，点击"生成"弹出底部面板 | 对现有代码改动最小 | 交互不够直观，不符合原设计风格 |

**推荐方案 A**: 因为两个功能域内容上独立、但都属于 AI 增强范畴，用 Tab 分割最自然。

---

## 三、需删除/新增/修改的文件清单

### 🗑️ 删除文件 (3个)

| # | 文件路径 | 原因 |
|---|----------|------|
| 1 | `app/.../ui/screen/airelations/AiRelationsScreen.kt` | AI关系功能移除 |
| 2 | `app/.../ui/screen/airelations/AiRelationsViewModel.kt` | AI关系功能移除 |
| 3 | `app/.../ui/screen/airelations/` (目录) | 可选的空目录清理 |

### ✨ 新增文件 (2个)

| # | 文件路径 | 内容说明 |
|---|----------|----------|
| 1 | `app/.../ui/screen/ailearning/AiLearningScreen.kt` | 合并后的 AI学习页面，含 TabRow + 助记列表 + AI生成卡片 |
| 2 | `app/.../ui/screen/ailearning/AiLearningViewModel.kt` | 合并后的 ViewModel，管理助记数据 + AI生成状态 |

### ✏️ 修改文件 (5个)

| # | 文件路径 | 修改内容 |
|---|----------|----------|
| 1 | `Screen.kt` | **(a)** 移除 `AiRelations` data object; **(b)** 将 `AiMnemonics` 重命名为 `AiLearning` (route `"ai_learning/{wordId}"`, title `"AI学习"`); **(c)** 如果仍需要助记导航可保留旧 route 别名，否则直接替换 |
| 2 | `NavGraph.kt` | **(a)** 移除 `AiRelationsScreen` import 和 composable 块; **(b)** 用 `AiLearningScreen` 替换 `AiMnemonicsScreen` composable; **(c)** 更新 import |
| 3 | `WordDetailScreen.kt` | **(a)** 将 `onNavigateToMnemonics` → `onNavigateToAiLearning`; **(b)** 修改按钮文字 "AI 助记 — 谐音/词根/故事联想" → "AI 学习 — 助记 & 批量生成"; **(c)** **移除** "关联图谱 — 同义词/近义词/搭配" 按钮; **(d)** 保留 "单词图谱 — 网状关系" 按钮不变 |
| 4 | `WordListScreen.kt` | **(a)** **移除** `AiVocabSection` 组件调用 (第89-95行); **(b)** 可移除 `AiVocabSection` 私有 Composable (第238-306行)，或注释标注待删 |
| 5 | `WordListViewModel.kt` | **(a)** 移除 `isGeneratingAi`, `aiGeneratedWords`, `aiGenerationResult` 状态字段; **(b)** 移除 `generateAiVocab()` 方法; **(c)** 移除 `dismissAiResult()` 方法; **(d)** 移除 `AiWordGenerator`, `WordMemoDatabase`, `FlashcardEntity`, `WordEntity` 等不再需要的 import |

### 🔁 无修改 (保留)

| # | 文件路径 | 说明 |
|---|----------|------|
| 1 | `WordGraphScreen.kt` | 关系网完全保留，不动 |
| 2 | `WordGraphViewModel.kt` | 不动 |

---

## 四、实施步骤 (推荐执行顺序)

```
Step 1: 创建 ailearning 包 + AiLearningViewModel.kt (合并助记+生成逻辑)
    ↓
Step 2: 创建 AiLearningScreen.kt (TabRow + 助记列表 + AI生成卡片)
    ↓
Step 3: 修改 Screen.kt (重命名、移除 route)
    ↓
Step 4: 修改 NavGraph.kt (替换 composable、更新 import)
    ↓
Step 5: 修改 WordDetailScreen.kt (合并为2个按钮)
    ↓
Step 6: 修改 WordListScreen.kt (移除 AiVocabSection)
    ↓
Step 7: 修改 WordListViewModel.kt (移除 AI 生成相关字段和方法)
    ↓
Step 8: 删除 AiRelationsScreen.kt + AiRelationsViewModel.kt
```

> **注意**: 步骤 6 和 7 (移除单词本页的 AI生成) 为**可选**——如果您希望单词本首页仍保留快速生成入口，可以不执行这2步，AI学习页中作为次要入口保留同样功能。但推荐移除以保证功能入口集中。

---

## 五、AiLearningViewModel 关键设计

```kotlin
// 合并后的 UiState
data class AiLearningUiState(
    val word: Word? = null,
    // 助记相关
    val mnemonics: List<AiMnemonic> = emptyList(),
    val isLoading: Boolean = true,
    val error: String? = null,
    // AI生成相关
    val isGenerating: Boolean = false,
    val generatedWords: List<GeneratedWord> = emptyList(),
    val generationResult: String? = null
)
```

- 两个数据源 (`AiMnemonicsViewModel.loadMnemonics()` + `WordListViewModel.generateAiVocab()`) 合并到一个 ViewModel 中
- 由于两个功能无数据依赖，可以各自独立加载/操作
- 初始加载默认走 `loadMnemonics()` (Tab1 页签) 的数据，`AI生成` 相关字段默认为空，用户点击 Tab2 或点击"生成"按钮后才触发

---

## 六、影响范围总结

| 维度 | 范围 |
|------|------|
| 屏幕文件 | -2 (2 删除) + 2 (2 新增) = 净 0 |
| ViewModel | -1 (删除) + 1 (新增) = 净 0 |
| 导航定义 | 3 routes → 2 routes (移除 ai_relations) |
| 导航图 composable | 3 个 → 2 个 |
| 单词详情页按钮 | 3 个 → 2 个 |
| 单词本页 | 移除 AiVocabSection 卡片 |
| 功能完整性 | ✅ 所有功能保留 (助记 + 生成 + 关系网) |
| 用户数据 | ✅ 无数据迁移，只涉及 UI 导航 |
