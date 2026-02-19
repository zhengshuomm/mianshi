# Reddit Folder - Java Programs

This folder contains Java programs for various LeetCode problems.

## Structure

```
reddit/
├── src/
│   ├── main/java/     # Main source code
│   └── test/java/     # Test files
├── bin/                # Compiled class files
├── run_tests.sh       # Script to compile and run tests
└── README.md          # This file
```

## Current Programs

- **WordLadder.java** - LeetCode 127: Word Ladder (BFS solution)

## How to Use in Cursor/VS Code

1. **Refresh Java Project**: After adding files, you may need to refresh the Java project:
   - Open Command Palette (Cmd+Shift+P / Ctrl+Shift+P)
   - Run: `Java: Clean Java Language Server Workspace`
   - Or: `Java: Reload Projects`

2. **Run Tests**: 
   - Use the test runner in Cursor/VS Code (click the play button next to test methods)
   - Or run the script: `./run_tests.sh`

3. **Compile Manually**:
   ```bash
   cd reddit
   ./run_tests.sh
   ```

## Configuration

The `.vscode/settings.json` has been updated to include:
- `reddit/src/main/java` in source paths
- `reddit/src/test/java` in source paths

If tests still don't run, try:
1. Reload the window: `Cmd+Shift+P` → `Developer: Reload Window`
2. Clean and rebuild the project
