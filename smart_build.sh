#!/usr/bin/env bash
set -e

echo "🚀 SMART BUILD START..."

echo "🔍 gradlew dhoond raha hoon..."
WRAPPER="$(find . -maxdepth 5 -type f -name gradlew | head -n 1 || true)"

if [ -n "$WRAPPER" ]; then
  PROJECT_DIR="$(dirname "$WRAPPER")"
  echo "✅ gradlew mil gaya: $WRAPPER"
  cd "$PROJECT_DIR"
  chmod +x ./gradlew
  ./gradlew assembleDebug
  echo "✅ BUILD PASS via gradlew"
  exit 0
fi

echo "⚠️ gradlew nahi mila. Ab settings.gradle/settings.gradle.kts dhoond raha hoon..."
ROOT="$(find . -maxdepth 5 -type f \( -name settings.gradle -o -name settings.gradle.kts \) | head -n 1 | xargs -r dirname || true)"

if [ -z "$ROOT" ]; then
  echo "❌ Android Gradle project root nahi mila."
  echo "👉 Tum wrong folder me ho ya gradle files missing hain."
  echo "📂 Current folder:"
  pwd
  echo "📋 Files:"
  ls -la
  exit 1
fi

cd "$ROOT"
echo "✅ Project root mila: $(pwd)"

if command -v gradle >/dev/null 2>&1; then
  echo "✅ System gradle mila. Build start..."
  gradle assembleDebug
  echo "✅ BUILD PASS via system gradle"
  exit 0
fi

echo "❌ gradlew bhi nahi hai aur system gradle bhi installed nahi hai."
echo "👉 Termux me pehle ye install karo:"
echo "pkg update -y && pkg install -y gradle openjdk-17"
exit 1
