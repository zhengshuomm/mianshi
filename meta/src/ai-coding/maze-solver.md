# Maze Solver

## 题目背景

给定一个二维迷宫，要求从起点 `S` 找到终点 `E` 的一条路径，并把路径打印出来。

迷宫由字符组成：

- `S`：起点
- `E`：终点
- `.`：可通行空地
- `#`：墙，不可通过
- `*`：最终打印路径时使用的路径符号

面试分 4 个 stage：

1. Debug maze printer：路径打印错了，`*` 不能覆盖起点 `S` 和终点 `E`。
2. Debug BFS：大迷宫找不到路或跑很久，因为 BFS 没有正确维护 `visited`，会重复访问甚至无限循环。
3. 新增特殊地形：有些格子只能按指定方向通过，例如只能左右走，或者不能左右走。
4. 新增钥匙和门：钥匙分散在迷宫里，只有拿到对应钥匙才能通过对应的门。

## Stage 1：修复路径打印

### 问题描述

已有代码能找到路径，但打印路径时把起点和终点也覆盖成了 `*`。

例如：

```text
Input:
S..
##.
..E

Wrong output:
***
##*
..*

Expected output:
S**
##*
..E
```

### Buggy code

```python
def print_maze_with_path(maze, path):
    grid = [list(row) for row in maze]
    for r, c in path:
        grid[r][c] = "*"
    return ["".join(row) for row in grid]
```

### 修复思路

- `path` 里通常包含起点和终点。
- 打印路径时只能覆盖空地 `.`。
- 不能覆盖：
  - `S`
  - `E`
  - wall `#`
  - key/door/special terrain

### Fixed code

```python
def print_maze_with_path(maze, path):
    grid = [list(row) for row in maze]
    for r, c in path:
        if grid[r][c] == ".":
            grid[r][c] = "*"
    return ["".join(row) for row in grid]
```

## Stage 2：修复 BFS 无限循环 / 重复访问

### 问题描述

已有 BFS 在小迷宫里可能能跑出来，但在大迷宫里会跑很久，甚至无限循环。

原因是没有 `visited`，或者只在 pop 出队列时才标记 visited，导致同一个格子被很多路径反复加入队列。

### Buggy code

```python
from collections import deque

def solve_maze_buggy(maze):
    rows, cols = len(maze), len(maze[0])
    start = end = None

    for r in range(rows):
        for c in range(cols):
            if maze[r][c] == "S":
                start = (r, c)
            elif maze[r][c] == "E":
                end = (r, c)

    q = deque([(start, [start])])

    while q:
        (r, c), path = q.popleft()
        if (r, c) == end:
            return path

        for dr, dc in [(1, 0), (-1, 0), (0, 1), (0, -1)]:
            nr, nc = r + dr, c + dc
            if 0 <= nr < rows and 0 <= nc < cols and maze[nr][nc] != "#":
                q.append(((nr, nc), path + [(nr, nc)]))

    return None
```

### 修复思路

- BFS 必须记录已经访问过的 cell。
- 推荐在 enqueue 时就加入 `visited`，而不是 dequeue 时才加入。
- 如果 dequeue 时才标记，多个 parent 仍然可能把同一个 cell 重复塞入 queue。

### Fixed code

```python
from collections import deque

def solve_maze_basic(maze):
    rows, cols = len(maze), len(maze[0])
    start = end = None

    for r in range(rows):
        for c in range(cols):
            if maze[r][c] == "S":
                start = (r, c)
            elif maze[r][c] == "E":
                end = (r, c)

    if start is None or end is None:
        return None

    q = deque([(start, [start])])
    visited = {start}

    while q:
        (r, c), path = q.popleft()
        if (r, c) == end:
            return path

        for dr, dc in [(1, 0), (-1, 0), (0, 1), (0, -1)]:
            nr, nc = r + dr, c + dc
            if not (0 <= nr < rows and 0 <= nc < cols):
                continue
            if maze[nr][nc] == "#":
                continue
            if (nr, nc) in visited:
                continue

            visited.add((nr, nc))
            q.append(((nr, nc), path + [(nr, nc)]))

    return None
```

## Stage 3：新增特殊方向地形

### 问题描述

现在迷宫里新增特殊地形：

- `H`：horizontal-only，只能从这个格子往左或右走，不能往上或下走。
- `V`：vertical-only，只能从这个格子往上或下走，不能往左或右走。

注意：这个限制描述的是“从当前格子出去时可以走哪些方向”。

例如：

```text
S.H.E
```

如果站在 `H` 上，只能继续向左或向右移动。

### Trade-off

有两种常见解释：

- 解释 A：限制从当前格子出去的方向。
  - 例如当前 cell 是 `H`，下一步只能 left/right。
  - 实现简单，只需要看 current cell。

- 解释 B：限制进入某个格子的方向。
  - 例如门只能从左边进入。
  - 需要检查 movement direction 和 target cell。

本题采用解释 A。如果面试官说是“门只能从某个方向进入”，可以把 direction rule 放到 target cell 上检查。

### 实现思路

把固定的四方向移动抽象成函数：

```python
def allowed_moves(cell):
    if cell == "H":
        return [(0, -1), (0, 1)]
    if cell == "V":
        return [(-1, 0), (1, 0)]
    return [(1, 0), (-1, 0), (0, 1), (0, -1)]
```

## Stage 4：新增钥匙和门

### 问题描述

迷宫里新增：

- lowercase `a` 到 `z`：钥匙
- uppercase `A` 到 `Z`：门
- 只有拿到对应 lowercase key，才能通过对应 uppercase door。

例如：

```text
S.a
##A
..E
```

必须先拿到 `a`，才能通过 `A`。

### 关键点

这一问不能只用 `(r, c)` 做 visited。

原因：

- 第一次到达 `(r, c)` 时可能没有钥匙。
- 第二次到达 `(r, c)` 时可能已经拿到钥匙，此时可走的后续路径不同。

所以 BFS 状态必须是：

```text
(row, col, keys_mask)
```

visited 也必须是：

```text
visited = {(row, col, keys_mask)}
```

### 为什么只用 cell visited 会错

例如：

```text
S.AE
..a.
```

如果先到达门 `A` 附近但没有钥匙，后续走不通。
之后拿到 `a` 再回到同一个位置时，应该允许重新探索。

如果 `visited` 只记录 `(r, c)`，第二次回来会被错误跳过。

## 最终参考实现

```python
from collections import deque


DIRS = [(1, 0), (-1, 0), (0, 1), (0, -1)]


def find_points(maze):
    start = end = None
    for r, row in enumerate(maze):
        for c, ch in enumerate(row):
            if ch == "S":
                start = (r, c)
            elif ch == "E":
                end = (r, c)
    return start, end


def allowed_moves_from(cell):
    if cell == "H":
        return [(0, -1), (0, 1)]
    if cell == "V":
        return [(-1, 0), (1, 0)]
    return DIRS


def is_key(cell):
    return "a" <= cell <= "z"


def is_door(cell):
    return "A" <= cell <= "Z" and cell not in {"S", "E", "H", "V"}


def key_bit(cell):
    return 1 << (ord(cell.lower()) - ord("a"))


def can_enter(cell, keys_mask):
    if cell == "#":
        return False
    if is_door(cell):
        return (keys_mask & key_bit(cell)) != 0
    return True


def solve_maze(maze):
    if not maze or not maze[0]:
        return None

    rows, cols = len(maze), len(maze[0])
    start, end = find_points(maze)
    if start is None or end is None:
        return None

    start_state = (start[0], start[1], 0)
    q = deque([(start[0], start[1], 0, [start])])
    visited = {start_state}

    while q:
        r, c, keys_mask, path = q.popleft()
        if (r, c) == end:
            return path

        current_cell = maze[r][c]
        for dr, dc in allowed_moves_from(current_cell):
            nr, nc = r + dr, c + dc
            if not (0 <= nr < rows and 0 <= nc < cols):
                continue

            next_cell = maze[nr][nc]
            if not can_enter(next_cell, keys_mask):
                continue

            next_keys = keys_mask
            if is_key(next_cell):
                next_keys |= key_bit(next_cell)

            next_state = (nr, nc, next_keys)
            if next_state in visited:
                continue

            visited.add(next_state)
            q.append((nr, nc, next_keys, path + [(nr, nc)]))

    return None


def print_maze_with_path(maze, path):
    if path is None:
        return None

    grid = [list(row) for row in maze]
    for r, c in path:
        if grid[r][c] == ".":
            grid[r][c] = "*"
    return ["".join(row) for row in grid]
```

## 测试用例

```python
def run_tests():
    # Stage 1: path should not override S/E
    maze = [
        "S..",
        "##.",
        "..E",
    ]
    path = solve_maze(maze)
    printed = print_maze_with_path(maze, path)
    assert printed[0][0] == "S"
    assert printed[2][2] == "E"

    # Stage 2: BFS should not loop forever
    maze = [
        "S....",
        ".###.",
        ".#...",
        ".#.##",
        "...#E",
    ]
    path = solve_maze(maze)
    assert path is not None
    assert path[0] == (0, 0)
    assert path[-1] == (4, 4)

    # Stage 3: H only allows left/right, V only allows up/down
    maze = [
        "S.H.E",
        "#####",
    ]
    path = solve_maze(maze)
    assert path is not None

    maze = [
        "S.V",
        "..E",
    ]
    # Standing on V cannot move left/right, so this may block some paths.
    path = solve_maze(maze)
    assert path is not None

    # Stage 4: key and door
    maze = [
        "S.a",
        "##A",
        "..E",
    ]
    path = solve_maze(maze)
    assert path is not None

    # Cannot pass A without key a
    maze = [
        "SAE",
        "###",
        "..a",
    ]
    path = solve_maze(maze)
    assert path is None

    print("all tests passed")
```

## 面试解释重点

- Stage 1：
  - Printer bug 不在 BFS，而是在打印时无条件覆盖 cell。
  - 修复方式是只覆盖 `.`，不要覆盖 `S/E/key/door/special terrain`。

- Stage 2：
  - BFS 必须有 visited。
  - 最好 enqueue 时标记 visited，避免同一个 cell 被多个 parent 重复入队。

- Stage 3：
  - 不要把方向逻辑 hardcode 在 BFS 主循环里。
  - 抽象成 `allowed_moves_from(cell)`，以后新增地形更容易。

- Stage 4：
  - 有钥匙和门后，visited 不能只看 cell。
  - 同一个 cell，在不同 key set 下是不同状态。
  - BFS state 应该是 `(r, c, keys_mask)`。

## 复杂度

设：

- `R` = 行数
- `C` = 列数
- `K` = 钥匙数量，最多 26

没有钥匙时：

- Time: `O(R * C)`
- Space: `O(R * C)`

有钥匙时：

- State 数量最多是 `R * C * 2^K`
- Time: `O(R * C * 2^K)`
- Space: `O(R * C * 2^K)`

实际面试中通常钥匙数量很小，所以 bitmask BFS 是合理解。
