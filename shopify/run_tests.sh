#!/bin/bash
# Script to compile and run tests for shopify project

cd "$(dirname "$0")"

# Compile main source files
echo "Compiling main source files..."
javac -cp "../lib/*:." -d bin src/main/java/*.java 2>&1 | head -20

# Compile test files
echo "Compiling test files..."
javac -cp "../lib/*:bin:." -d bin src/test/java/*.java 2>&1 | head -20

# Run tests
echo "Running tests..."
java -cp "../lib/*:bin:." org.junit.platform.console.ConsoleLauncher --class-path bin --scan-class-path 2>&1 | head -50
