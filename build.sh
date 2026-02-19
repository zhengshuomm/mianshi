#!/bin/bash
# 将所有模块的 .class 编译到 bin 目录
set -e
ROOT="$(cd "$(dirname "$0")" && pwd)"
BIN="$ROOT/bin"
mkdir -p "$BIN"

compile_module() {
  local dir="$1"
  if [ ! -d "$ROOT/$dir/src" ]; then return; fi
  local files=$(find "$ROOT/$dir/src" -maxdepth 1 -name "*.java" 2>/dev/null)
  if [ -z "$files" ]; then return; fi
  echo "Compiling $dir -> $BIN/$dir"
  mkdir -p "$BIN/$dir"
  javac -d "$BIN/$dir" $files
}

# 各模块
for m in openai doordash my_openai rippling dropbox coupang notion meta airbnb; do
  compile_module "$m"
done

# 根目录 src
if [ -d "$ROOT/src" ] && [ -n "$(ls -A "$ROOT/src"/*.java 2>/dev/null)" ]; then
  echo "Compiling src -> $BIN"
  javac -d "$BIN" "$ROOT/src"/*.java
fi

echo "Done. Classes in $BIN/"
