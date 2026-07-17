#!/usr/bin/env bash
# ============================================================
# build-deploy.sh — 词鼠书记 原子化构建发布脚本
# 用法: ./build-deploy.sh v5.1.0 "更新说明"
# ============================================================
set -euo pipefail

VERSION="$1"
NOTES="$2"
ROOT="/c/Users/73580/tools/wordmemo"
APK_OUT="app/build/outputs/apk/release/app-release.apk"
AAPT="/c/Android/android-sdk/build-tools/34.0.0/aapt2.exe"
APKSIGNER="/c/Android/android-sdk/build-tools/34.0.0/apksigner.bat"
GRADLE="/c/Users/73580/tools/gradle-8.9/bin/gradle.bat"
GH="/c/Program Files/GitHub CLI/gh.exe"

export JAVA_HOME="/c/Users/73580/tools/jdk17/extracted/jdk-17.0.19+10"
export ANDROID_HOME="/c/Android/android-sdk"
export KEYSTORE_PATH="/c/Users/73580/android-release-key.jks"
export KEYPASS="SelfUse!APK#2026$"

cd "$ROOT"

echo "=== [1/5] 更新版本号 ==="
MAJOR=$(echo "$VERSION" | sed 's/v//' | cut -d. -f1)
MINOR=$(echo "$VERSION" | sed 's/v//' | cut -d. -f2)
PATCH=$(echo "$VERSION" | sed 's/v//' | cut -d. -f3)
CODE=$((MAJOR * 10000 + MINOR * 100 + PATCH))
sed -i "s/versionCode = [0-9]*/versionCode = $CODE/" app/build.gradle.kts
sed -i "s/versionName = \".*\"/versionName = \"$VERSION\"/" app/build.gradle.kts
echo "  versionCode=$CODE, versionName=$VERSION"

echo "=== [2/5] 构建 ==="
"$GRADLE" assembleRelease --no-daemon 2>&1 | grep "BUILD"
CHECK=$("$AAPT" dump badging "$APK_OUT" 2>/dev/null | grep "versionName=")
echo "  构建产物: $CHECK"

echo "=== [3/5] 签名验证 ==="
"$APKSIGNER" verify --print-certs "$APK_OUT" 2>&1 | grep "Signer #1" | head -1

echo "=== [4/5] 发布到 GitHub ==="
# 删除旧release（如果存在）
"$GH" release view "$VERSION" --repo JSJ521/wordmemo >/dev/null 2>&1 && \
  "$GH" release delete "$VERSION" --repo JSJ521/wordmemo --yes 2>&1 || true
# 创建新release
"$GH" release create "$VERSION" \
  "$APK_OUT#APK" \
  --repo JSJ521/wordmemo \
  --title "词鼠书记 $VERSION" \
  --notes "$NOTES" \
  --latest 2>&1

echo "=== [5/5] 验证发布 ==="
"$GH" release view "$VERSION" --repo JSJ521/wordmemo --json assets 2>&1 | grep -E "name|downloadCount"

echo ""
echo "✅ 发布完成: $VERSION"
