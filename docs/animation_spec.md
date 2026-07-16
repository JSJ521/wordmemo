# 流徵动效审查报告 — 单词宝 App

> **审查角色**: 流徵（动效设计师）
> **审查范围**: `ui/component/` + `ui/screen/` 全部 24 个 .kt 文件
> **版本**: v1.0 · 2026-07-15

---

## 1. 现有动效评估

### 1.1 FlashcardView — 翻卡旋转动画

**当前实现** `(FlashcardView.kt:32-36)`

```kotlin
val rotation by animateFloatAsState(
    targetValue = if (isFlipped) 180f else 0f,
    animationSpec = tween(durationMillis = 400),
    label = "flip"
)
```

**问题诊断**:

| 问题 | 严重度 | 说明 |
|------|--------|------|
| ⚠️ 纯 Y 轴旋转无透视 | P1 | 使用 `Modifier.rotate()` 做平面 180° 旋转，没有 `graphicsLayer { rotationY = ... }` + `cameraDistance` 配合，翻卡是"纸片翻转"而非"卡片翻面"，视觉上文字会镜像到背面 |
| ⚠️ `tween` 而非 `spring` | P2 | 抽认卡翻卡是典型的手势触发动效，应该用 `spring()` 获得物理弹性——从 0f 到 180f 经过 90° 时应该有自然的惯性减速 |
| ⚠️ 内容未隐藏 | P1 | 旋转过程中 front/back text 是根据 `isFlipped` 布尔值直接切换显示，而非通过 `graphicsLayer.rotationY` 在 >90° 时切换。文字会突然闪现而非平滑过渡 |
| ℹ️ `animateContentSize` import 未使用 | P3 | 第 3 行导入了 `animateContentSize` 但 Modifier 链中从未调用 |

**改进建议**:

```kotlin
val rotation by animateFloatAsState(
    targetValue = if (isFlipped) 180f else 0f,
    animationSpec = spring(
        dampingRatio = Spring.DampingRatioMediumBouncy,
        stiffness = Spring.StiffnessMedium
    ),
    label = "flip"
)

Box(
    modifier = Modifier
        .fillMaxSize()
        .graphicsLayer {
            rotationY = rotation
            cameraDistance = 12f * density  // 透视景深
        },
    contentAlignment = Alignment.Center
) {
    // 根据 rotation 的中间值决定显隐，而非 isFlipped 布尔
    if (rotation < 90f) {
        // 正面（英文）
        Text(frontText, ...)
    } else {
        // 背面（中文）
        Text(backText, ...)
    }
}
```

---

### 1.2 TranslationPanel — 显示/隐藏滑动动画

**当前实现** `(TranslationPanel.kt:36-39)`

```kotlin
AnimatedVisibility(
    visible = isVisible,
    enter = slideInVertically(initialOffsetY = { it }),
    exit = slideOutVertically(targetOffsetY = { it }),
)
```

**评估**:
- ✅ **方向正确**：从底部滑入/滑出符合 bottom sheet 的行为隐喻
- ⚠️ **缺少淡入淡出**：纯滑动搭配 alpha 渐变更柔和（当前硬切透明度）
- ⚠️ **无弹性**：翻译面板的出现用 `slideInVertically` + 默认 `tween(300)`，应该用 `spring(stiffness = Spring.StiffnessMediumLow)` 使面板自然"弹"出来
- ℹ️ 翻译面板内部的 AI 翻译区域纯静态出现（`if (aiTranslation != null && isOnline)`），没用 `AnimatedVisibility` 做展开

**改进建议**:

```kotlin
AnimatedVisibility(
    visible = isVisible,
    enter = slideInVertically(
        initialOffsetY = { it },
        animationSpec = spring(
            dampingRatio = Spring.DampingRatioMediumBouncy,
            stiffness = Spring.StiffnessMediumLow
        )
    ) + fadeIn(animationSpec = tween(200)),
    exit = slideOutVertically(
        targetOffsetY = { it },
        animationSpec = tween(250)
    ) + fadeOut(animationSpec = tween(150))
)
```

AI 译文的展开也建议包裹 `AnimatedVisibility`：

```kotlin
AnimatedVisibility(visible = aiTranslation != null && isOnline) {
    // AI 翻译 Surface ...
}
```

---

### 1.3 RatingBar — ⚠️ 零动效

**当前实现** `(RatingBar.kt:70-96)` — 纯静态 `Button` + `Text`
**评估**:
- ❌ **完全没有动画**：4 个评分按钮点击时没有任何反馈（无 scale、无 ripple 以外的视觉变化）
- ❌ **没有 enter 动画**：翻卡后评分栏从 `else { Spacer(Modifier.height(48.dp)) }` 瞬间变为 `Text + RatingBar`，无渐进出现
- ❌ **没有选中态过渡**：点击后触发下一张卡，中间无视觉过渡

**改进建议**:

```kotlin
// 1. 评分按钮加入点击缩放
private fun RatingButton(...) {
    var pressed by remember { mutableStateOf(false) }
    val scale by animateFloatAsState(
        targetValue = if (pressed) 0.92f else 1f,
        animationSpec = spring(dampingRatio = 0.5f, stiffness = Spring.StiffnessHigh)
    )
    Button(
        modifier = modifier
            .width(72.dp)
            .height(64.dp)
            .graphicsLayer { scaleX = scale; scaleY = scale }
            .pointerInput(Unit) {
                detectTapGestures(
                    onPress = { pressed = true; tryAwaitRelease(); pressed = false },
                    onTap = onClick
                )
            },
        ...
    )
}

// 2. 整个 RatingBar 出现时加动画
AnimatedVisibility(
    visible = isFlipped,
    enter = fadeIn(tween(200)) + slideInVertically(initialOffsetY = { it / 2 })
) {
    // RatingBar 内容...
}
```

---

## 2. 缺失动效清单

### 2.1 页面导航过渡 ❌ P0

**当前**: `NavGraph.kt` 使用基本 `composable()`，无自定义 `enterTransition`/`exitTransition`
**影响**: 所有页面跳转为硬切，app 体验"跳帧感"

**建议**:

```kotlin
composable(
    route = Screen.AddWord.route,
    enterTransition = { slideInHorizontally { it } + fadeIn(tween(300)) },
    exitTransition = { fadeOut(tween(200)) },
    popEnterTransition = { fadeIn(tween(200)) },
    popExitTransition = { slideOutHorizontally { it } + fadeOut(tween(200)) }
)
```

导航方向原则：**子页滑入 = 向右(forward)，返回 = 向左(back)**。

### 2.2 LazyColumn Item 入场动画 ❌ P1

**当前**: `WordListScreen`、`GroupsScreen`、`AiRelationsScreen` 的 `LazyColumn` items 瞬间出现

**建议**: 使用 `Modifier.animateItemPlacement()` + 配合 `items(..., contentType = ...)`

```kotlin
LazyColumn {
    items(displayWords, key = { it.id }) { word ->
        WordItem(
            word = word,
            modifier = Modifier.animateItem()  // Compose 1.7+ 
        )
    }
}
```

### 2.3 空状态 → 数据填充动画 ❌ P2

**当前**: `WordListScreen` 空状态（大图标 + 提示文字）到数据出现之间没有过渡

**建议**: 用 `Crossfade(targetState = displayWords.isEmpty())` 包裹空状态和 List

### 2.4 SkeletonLoader 脉冲动画 ❌ P2

**当前** `(CommonComponents.kt:35-49)`: 静态灰色 Surface 矩形，无 `InfiniteTransition` 脉冲

**建议**:

```kotlin
@Composable
fun SkeletonLoader(modifier: Modifier = Modifier) {
    val infiniteTransition = rememberInfiniteTransition(label = "skeleton")
    val alpha by infiniteTransition.animateFloat(
        initialValue = 0.3f, targetValue = 0.7f,
        animationSpec = infiniteRepeatable(
            animation = tween(800, easing = LinearEasing),
            repeatMode = RepeatMode.Reverse
        ), label = "pulse"
    )
    Column(modifier = modifier.padding(16.dp)) {
        repeat(3) {
            Surface(
                modifier = Modifier
                    .fillMaxWidth().height(80.dp).padding(vertical = 4.dp),
                color = Color.LightGray.copy(alpha = alpha),
                shape = RoundedCornerShape(8.dp)
            ) {}
        }
    }
}
```

### 2.5 复习完成状态的庆祝动画 ❌ P2

**当前** `(ReviewSessionScreen.kt:52-76)`: 复习完成时仅显示文字+"🎉" emoji

**建议**: 加入简单的 `scale` + `fadeIn` 组合动画，如标题放大进入

### 2.6 评分按钮禁用态的渐显 ❌ P2

**当前**: 翻卡后评分按钮 `enabled` 未使用控制时序

**建议**: 翻卡动画进行中时评分按钮应保持禁用，翻卡完成后（旋转 > 90°）再逐步启用

---

## 3. 动效规范建议

### 3.1 动画曲线统一

| 场景 | 推荐参数 | 理由 |
|------|----------|------|
| **翻卡** | `spring(dampingRatio=0.6f, stiffness=Spring.StiffnessMedium)` | 物理弹性，自然收放 |
| **面板出现** | `spring(dampingRatio=0.8f, stiffness=Spring.StiffnessMediumLow)` | 偏阻尼，柔和不弹跳过头 |
| **按钮按压** | `spring(dampingRatio=0.5f, stiffness=Spring.StiffnessHigh)` | 快速回弹，触感清晰 |
| **列表插入** | `tween(300, easing=EaseOutBack)` | 略微 overshoot 增加趣味 |
| **淡入淡出** | `tween(200)` | 200ms 是最佳注意力引导时间 |
| **骨架屏脉冲** | `infiniteRepeatable(tween(800, easing=LinearEasing), RepeatMode.Reverse)` | 稳定呼吸感 |

### 3.2 Spring 参数速查

```kotlin
// 系统预设（推荐直接使用）
Spring.DampingRatioHighBouncy ≈ 0.2f   // 很弹（慎用）
Spring.DampingRatioMediumBouncy ≈ 0.5f // 微弹 ✓
Spring.DampingRatioNoBouncy ≈ 1.0f     // 不弹
Spring.DampingRatioLowBouncy ≈ 0.75f   // 轻微弹性

Spring.StiffnessHigh ≈ 10_000f         // 快
Spring.StiffnessMedium ≈ 1_500f        // 适中 ✓
Spring.StiffnessMediumLow ≈ 400f       // 慢
Spring.StiffnessLow ≈ 200f             // 很慢
```

### 3.3 导航过渡映射

| 操作 | 进入 | 退出 |
|------|------|------|
| push 进入子页 | `slideInHorizontally { width }` + `fadeIn(200)` | `fadeOut(150)` |
| pop 返回 | `fadeIn(150)` | `slideOutHorizontally { width }` + `fadeOut(200)` |
| 弹窗/Dialog | `scaleIn(0.9→1.0)` + `fadeIn(200)` | `fadeOut(150)` |

### 3.4 持续时间规范

| 类型 | 时长 | 用途 |
|------|------|------|
| **微交互** | 100-150ms | 按钮、切换、ripple |
| **过渡** | 200-300ms | 页面转场、面板展开 |
| **重点动效** | 300-500ms | 翻卡、Celebration |
| **装饰动效** | 600-1000ms | 骨架屏脉冲、加载指示 |

---

## 4. 优先级

| ID | 描述 | 优先级 | 影响范围 |
|----|------|--------|----------|
| **AN-01** | 翻卡使用 `graphicsLayer { rotationY }` + `cameraDistance` 替代平面 `Modifier.rotate()` | **P0** | FlashcardView — 核心交互 |
| **AN-02** | 翻卡内容在 rotation > 90° 时切换，非 isFlipped 布尔 | **P0** | FlashcardView — 核心体验 |
| **AN-03** | 翻卡 `tween(400)` → `spring(DampingRatioMediumBouncy)` | **P1** | FlashcardView |
| **AN-04** | 导航过渡：所有 composable 添加 `enterTransition`/`exitTransition` | **P1** | NavGraph — 全局质感 |
| **AN-05** | RatingBar 按钮按压 scale 反馈 | **P1** | RatingBar |
| **AN-06** | TranslationPanel 添加 `fadeIn`/`fadeOut` 复合 | **P1** | TranslationPanel |
| **AN-07** | RatingBar 整体 `AnimatedVisibility` 入场 | **P1** | ReviewSessionScreen |
| **AN-08** | SkeletonLoader 脉冲呼吸动画 | **P2** | CommonComponents — 加载体验 |
| **AN-09** | LazyColumn `animateItemPlacement` | **P2** | 所有列表页 |
| **AN-10** | AI 翻译区域 `AnimatedVisibility` 展开 | **P2** | TranslationPanel |
| **AN-11** | 复习完成庆祝内容 scale+fade 入场 | **P2** | ReviewSessionScreen |
| **AN-12** | 翻卡进行中评分按钮保持 disabled | **P2** | ReviewSessionScreen |
| **AN-13** | 空状态→数据 Crossfade | **P2** | WordListScreen |
| **AN-14** | 梳理移除无用 `animateContentSize` import | **P3** | FlashcardView |

---

## 5. 总体评价

**动效成熟度评级: ★☆☆☆☆ (1/5) — 基本级**

单词宝 App 的动效系统目前处于**几乎空白**的状态：

- **仅存 2 处动效**：FlashcardView 的翻卡旋转 + TranslationPanel 的滑入/滑出
- **零动效系统设计**：没有统一 `animationSpec` 常量，没有 motion theme，没有 spring 参数共识
- **关键交互缺失**：评分按钮、导航过渡、列表渲染这三个最高频的用户触点完全静态

**核心问题不是"缺少动效"，而是缺少动效系统思维**。当前两个动效用了 `tween`，但手动翻卡交互天然适合 `spring`。导航用默认 `composable()` 无自定义过渡，导致页面切换像"闪换"。

### 改造成本估算

| 阶段 | 内容 | 预估工时 |
|------|------|----------|
| Phase 1 (P0) | 翻卡重写 + 导航过渡 | 1-2 天 |
| Phase 2 (P1) | RatingBar + TranslationPanel 增强 | 0.5 天 |
| Phase 3 (P2) | Skeleton + Item 动画 + 余下优化 | 1 天 |

### 快速见效项（1小时内可完成）

1. `NavGraph.kt` 所有 `composable` 添加 `enterTransition`/`exitTransition`（复制粘贴相同模板）
2. `SkeletonLoader` 添加 `InfiniteTransition` 脉冲
3. `RatingBar` 按钮包裹 `graphicsLayer { scaleX/Y }` 按压缩放
4. `TranslationPanel` AI 区域包裹 `AnimatedVisibility`

---

*报告完 · 流徵 · weizheng@motion.design*
