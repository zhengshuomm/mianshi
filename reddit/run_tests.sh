#!/bin/bash

# Script to compile and run tests for reddit folder

REDDIT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
PROJECT_ROOT="$(cd "$REDDIT_DIR/.." && pwd)"
LIB_DIR="$PROJECT_ROOT/lib"

# Create bin directory if it doesn't exist
mkdir -p "$REDDIT_DIR/bin"

# Compile main source files
echo "Compiling main source files..."
javac -d "$REDDIT_DIR/bin" \
    -cp "$LIB_DIR/*" \
    "$REDDIT_DIR/src/main/java/"*.java

if [ $? -ne 0 ]; then
    echo "Compilation failed for main sources"
    exit 1
fi

# Compile test files
echo "Compiling test files..."
javac -d "$REDDIT_DIR/bin" \
    -cp "$REDDIT_DIR/bin:$LIB_DIR/*" \
    "$REDDIT_DIR/src/test/java/"*.java

if [ $? -ne 0 ]; then
    echo "Compilation failed for test sources"
    exit 1
fi

# Run tests
echo "Running tests..."
java -cp "$REDDIT_DIR/bin:$LIB_DIR/*" \
    org.junit.platform.console.ConsoleLauncher \
    --class-path "$REDDIT_DIR/bin" \
    --scan-class-path
