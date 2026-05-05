# Maze Solver Java Version

## 题目阶段

1. Debug printer：打印路径时 `*` 不能覆盖 `S` 和 `E`。
2. Debug BFS：BFS 必须加 `visited`，否则大迷宫重复访问甚至无限循环。
3. 新增方向限制地形：
   - `H`：只能左右走。
   - `V`：只能上下走。
4. 新增钥匙和门：
   - `a-z` 是钥匙。
   - `A-Z` 是门。
   - 有对应钥匙才能通过门。

## Java 参考实现

```java
import java.util.*;

public class MazeSolver {
    private static final int[][] DIRS = {
            {1, 0}, {-1, 0}, {0, 1}, {0, -1}
    };

    static class Point {
        int r;
        int c;

        Point(int r, int c) {
            this.r = r;
            this.c = c;
        }

        @Override
        public boolean equals(Object o) {
            if (!(o instanceof Point)) return false;
            Point other = (Point) o;
            return r == other.r && c == other.c;
        }

        @Override
        public int hashCode() {
            return Objects.hash(r, c);
        }
    }

    static class State {
        int r;
        int c;
        int keysMask;
        List<Point> path;

        State(int r, int c, int keysMask, List<Point> path) {
            this.r = r;
            this.c = c;
            this.keysMask = keysMask;
            this.path = path;
        }
    }

    static class VisitState {
        int r;
        int c;
        int keysMask;

        VisitState(int r, int c, int keysMask) {
            this.r = r;
            this.c = c;
            this.keysMask = keysMask;
        }

        @Override
        public boolean equals(Object o) {
            if (!(o instanceof VisitState)) return false;
            VisitState other = (VisitState) o;
            return r == other.r && c == other.c && keysMask == other.keysMask;
        }

        @Override
        public int hashCode() {
            return Objects.hash(r, c, keysMask);
        }
    }

    public static List<Point> solve(char[][] maze) {
        if (maze == null || maze.length == 0 || maze[0].length == 0) {
            return null;
        }

        int rows = maze.length;
        int cols = maze[0].length;
        Point start = null;
        Point end = null;

        for (int r = 0; r < rows; r++) {
            for (int c = 0; c < cols; c++) {
                if (maze[r][c] == 'S') start = new Point(r, c);
                if (maze[r][c] == 'E') end = new Point(r, c);
            }
        }

        if (start == null || end == null) return null;

        Queue<State> queue = new ArrayDeque<>();
        Set<VisitState> visited = new HashSet<>();

        List<Point> startPath = new ArrayList<>();
        startPath.add(start);
        queue.offer(new State(start.r, start.c, 0, startPath));
        visited.add(new VisitState(start.r, start.c, 0));

        while (!queue.isEmpty()) {
            State cur = queue.poll();
            if (cur.r == end.r && cur.c == end.c) {
                return cur.path;
            }

            for (int[] d : allowedMovesFrom(maze[cur.r][cur.c])) {
                int nr = cur.r + d[0];
                int nc = cur.c + d[1];

                if (nr < 0 || nr >= rows || nc < 0 || nc >= cols) continue;

                char next = maze[nr][nc];
                if (!canEnter(next, cur.keysMask)) continue;

                int nextKeys = cur.keysMask;
                if (isKey(next)) {
                    nextKeys |= keyBit(next);
                }

                VisitState vs = new VisitState(nr, nc, nextKeys);
                if (visited.contains(vs)) continue;

                visited.add(vs);
                List<Point> nextPath = new ArrayList<>(cur.path);
                nextPath.add(new Point(nr, nc));
                queue.offer(new State(nr, nc, nextKeys, nextPath));
            }
        }

        return null;
    }

    private static int[][] allowedMovesFrom(char cell) {
        if (cell == 'H') {
            return new int[][]{{0, -1}, {0, 1}};
        }
        if (cell == 'V') {
            return new int[][]{{-1, 0}, {1, 0}};
        }
        return DIRS;
    }

    private static boolean canEnter(char cell, int keysMask) {
        if (cell == '#') return false;
        if (isDoor(cell)) {
            return (keysMask & keyBit(Character.toLowerCase(cell))) != 0;
        }
        return true;
    }

    private static boolean isKey(char cell) {
        return cell >= 'a' && cell <= 'z';
    }

    private static boolean isDoor(char cell) {
        return cell >= 'A' && cell <= 'Z'
                && cell != 'S' && cell != 'E'
                && cell != 'H' && cell != 'V';
    }

    private static int keyBit(char cell) {
        return 1 << (cell - 'a');
    }

    public static char[][] printWithPath(char[][] maze, List<Point> path) {
        char[][] copy = new char[maze.length][maze[0].length];
        for (int r = 0; r < maze.length; r++) {
            copy[r] = Arrays.copyOf(maze[r], maze[r].length);
        }

        if (path == null) return copy;

        for (Point p : path) {
            if (copy[p.r][p.c] == '.') {
                copy[p.r][p.c] = '*';
            }
        }
        return copy;
    }
}
```

## 面试重点

- Stage 1：打印路径时只覆盖 `.`，不要覆盖 `S/E/key/door/special terrain`。
- Stage 2：BFS 在 enqueue 时加入 visited，避免同一个 cell 被重复入队。
- Stage 3：方向规则抽象成 `allowedMovesFrom`。
- Stage 4：有钥匙后，visited 必须是 `(row, col, keysMask)`，不能只用 `(row, col)`。

## 复杂度

- 无钥匙：`O(R * C)`
- 有钥匙：`O(R * C * 2^K)`

