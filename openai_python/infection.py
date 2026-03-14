class Infection:

    #  Part 1， 2: N=1，完全感染所需步数。0=健康，1=感染, 2=immune

    def time_to_affect(grid: list[list[int]], N: int) -> int:
        dirs = [(0, 1), (1, 0), (-1, 0), (0, -1)]
        if grid is None or  grid[0] is None:
            return -1
        m = len(grid)
        n = len(grid[0])
        health_count = 0
        affect_count = 0
        day = 0

        for i in range(m):
            for j in range(n):
                if grid[i][j] == 0:
                    health_count += 1
                if grid[i][j] == 1:
                    affect_count += 1
    
        if affect_count == 0 :
            return -1
        if health_count == 0:
            return 0
        
        # BUG 1 FIX: copy grid into current (was: current = [[0]*n ...], ignoring original state)
        current = [row[:] for row in grid]
        while health_count > 0:
            next_grid = [[0] * n for _ in range(m)]
            found = False
            for i in range(m):
                for j in range(n):
                    next_grid[i][j] = current[i][j]
                    if current[i][j] != 0:
                        continue
                    count = 0
                    for di, dj in dirs:
                        ni = i + di
                        nj = j + dj
                        # BUG 2 FIX: was current[i][j] == 1, should check neighbor current[ni][nj]
                        if ni >= 0 and ni < m and nj >= 0 and nj < n and current[ni][nj] == 1:
                            count += 1
                    if count >= N:
                        next_grid[i][j] = 1
                        found = True
                        health_count -= 1
            
            if not found:
                return -1
            current = next_grid
            day = day + 1
        return day

    #  Part 3: 康复与免疫。0=健康，1=初始感染, 2=immune；感染 D 天后康复并永久免疫。w
    # 返回：感染完全停止时的天数（无活跃感染）。
    def time_to_affect3(grid: list[list[int]], N: int, D: int) -> int:
        dirs = [(0, 1), (1, 0), (-1, 0), (0, -1)]
        if grid is None or  grid[0] is None:
            return -1
        m = len(grid)
        n = len(grid[0])
        affect_count = 0
        day = 0

        current = [[0] * n for _ in range(m)]
        for i in range(m):
            for j in range(n):
                if grid[i][j] == 0:
                    current[i][j] = -1
                if grid[i][j] == 1:
                    affect_count += 1
                if grid[i][j] == 2:
                    current[i][j] = -2
    
        if affect_count == 0:
            return 0
        
        while affect_count > 0:
            # recover first
            for i in range(m):
                for j in range(n):
                    if current[i][j] >= 0 and day - current[i][j] >= D:
                        current[i][j] = -2
                        affect_count -= 1

            next_grid = [[0] * n for _ in range(m)]
            for i in range(m):
                for j in range(n):
                    next_grid[i][j] = current[i][j]
                    if current[i][j] != -1:
                        continue
                    count = 0
                    for di, dj in dirs:
                        ni = i + di
                        nj = j + dj
                        # BUG 3 FIX: was current[i][j] == 1, should check neighbor current[ni][nj]
                        if ni >= 0 and ni < m and nj >= 0 and nj < n and current[ni][nj] >= 0:
                            count += 1
                    if count >= N:
                        next_grid[i][j] = day
                        affect_count += 1
            
            current = next_grid
            # BUG 4 FIX: day++ must be at END (was at beginning, causing off-by-one in recovery timing)
            day = day + 1
        return day


    # Part 4: 多病毒竞争
    # grid: 0=健康, 正整数=病毒类型, 负数/2=免疫
    # 规则：健康格统计每种病毒邻居数量，数量最多且 >= N 的病毒感染该格；
    #       若最大值由多种病毒并列（tie），该格保持健康（竞争抵消）。
    # 返回：最后一轮发生变化的天数（0 = 从一开始就没有扩散）
    def time_to_affect_multi(grid: list[list[int]], N: int) -> int:
        dirs = [(0, 1), (1, 0), (-1, 0), (0, -1)]
        if grid is None or not grid or not grid[0]:
            return -1
        m, n = len(grid), len(grid[0])

        virus_count = sum(1 for i in range(m) for j in range(n) if grid[i][j] > 0)
        healthy_count = sum(1 for i in range(m) for j in range(n) if grid[i][j] == 0)
        if virus_count == 0:
            return -1
        if healthy_count == 0:
            return 0

        current = [row[:] for row in grid]
        day = 0

        while True:
            next_grid = [row[:] for row in current]
            changed = False

            for i in range(m):
                for j in range(n):
                    if current[i][j] != 0:
                        continue
                    # 统计每种病毒类型的邻居数
                    type_count: dict[int, int] = {}
                    for di, dj in dirs:
                        ni, nj = i + di, j + dj
                        if 0 <= ni < m and 0 <= nj < n and current[ni][nj] > 0:
                            t = current[ni][nj]
                            type_count[t] = type_count.get(t, 0) + 1

                    if not type_count:
                        continue
                    max_count = max(type_count.values())
                    if max_count < N:
                        continue
                    # 并列 tie → 保持健康
                    winners = [t for t, c in type_count.items() if c == max_count]
                    if len(winners) == 1:
                        next_grid[i][j] = winners[0]
                        changed = True

            if not changed:
                break
            current = next_grid
            day += 1

        return day


if __name__ == "__main__":
    ta = Infection.time_to_affect
    ta3 = Infection.time_to_affect3
    tam = Infection.time_to_affect_multi

    # ===== Part 1/2: time_to_affect =====

    # 边界
    assert ta(None, 1) == -1,                          "null grid"
    assert ta([[0, 0], [0, 0]], 1) == -1,              "no infected"
    assert ta([[1, 1], [1, 1]], 1) == 0,               "all infected"
    assert ta([[1]], 1) == 0,                           "1x1 infected"
    assert ta([[0]], 1) == -1,                          "1x1 healthy"

    # 中心 3x3：第1轮四邻，第2轮四角 → 2
    assert ta([[0,0,0],[0,1,0],[0,0,0]], 1) == 2,       "center 3x3"

    # 一角 3x3：最远(2,2)曼哈顿距离4 → 4
    assert ta([[1,0,0],[0,0,0],[0,0,0]], 1) == 4,       "corner 3x3"

    # 对角两个角都感染 → 2
    assert ta([[1,0,0],[0,0,0],[0,0,1]], 1) == 2,       "two corners"

    # 一行 1,0,0,0,0 → 4
    assert ta([[1,0,0,0,0]], 1) == 4,                   "row of 5"

    # 一列 → 3
    assert ta([[1],[0],[0],[0]], 1) == 3,                "col of 4"

    # 2x2 一个感染 → 2
    assert ta([[1,0],[0,0]], 1) == 2,                   "2x2 one infected"

    # 十字形 → 1
    assert ta([[0,1,0],[1,1,1],[0,1,0]], 1) == 1,       "cross"

    # 免疫格（2）本身不会被感染，且不传染邻居；但感染可绕路
    assert ta([[1,0,2,0]], 1) == -1,                    "immune fully blocks 1D path"
    assert ta([[1,0,0],[0,2,0],[0,0,0]], 1) == 4,       "immune blocks center, infection goes around"

    # N=2：需要至少 2 个感染邻居才能感染
    # [1,0,1]: 中间格有2个感染邻居 → 第1天被感染
    assert ta([[1,0,1]], 2) == 1,                       "N=2 middle has 2 infected neighbors"
    # [1,0,0,1]: 内部格最多只有1个感染邻居 → 永远传不到
    assert ta([[1,0,0,1]], 2) == -1,                    "N=2 gap too large"
    # 上行全感染，下行每格只有1个上方邻居 → 无法达到 N=2 → -1
    assert ta([[1,1,1],[0,0,0],[0,0,0]], 2) == -1,      "N=2 only 1 infected neighbor per cell"

    print("All Part1/2 tests passed.")

    # ===== Part 3: time_to_affect3 =====

    # 无感染 → 0
    assert ta3([[0,0],[0,0]], 1, 2) == 0,               "no infected"
    assert ta3([[2,2],[2,2]], 1, 2) == 0,               "all immune"

    # 1x1 各种 D
    assert ta3([[1]], 1, 0) == 1,                       "1x1 D=0"
    assert ta3([[1]], 1, 1) == 2,                       "1x1 D=1"
    assert ta3([[1]], 1, 5) == 6,                       "1x1 D=5"

    # 全感染 D=1 → 2
    assert ta3([[1,1],[1,1]], 1, 1) == 2,               "all infected D=1"

    # 中心 3x3 D=1 → 2
    assert ta3([[0,0,0],[0,1,0],[0,0,0]], 1, 1) == 2,   "center 3x3 D=1"

    # 中心 3x3 D=3 → 5
    assert ta3([[0,0,0],[0,1,0],[0,0,0]], 1, 3) == 5,   "center 3x3 D=3"

    # 一角 3x3 D=2 → 6
    assert ta3([[1,0,0],[0,0,0],[0,0,0]], 1, 2) == 6,   "corner 3x3 D=2"

    # 一行 1,0,0 D=2 → 4
    assert ta3([[1,0,0]], 1, 2) == 4,                   "line 1,0,0 D=2"

    # 含免疫格阻断传播 D=10 → 14
    assert ta3([[1,0,0],[0,2,0],[0,0,0]], 1, 10) == 14, "immune blocks D=10"

    print("All Part3 tests passed.")

    # ===== Part 4: time_to_affect_multi =====

    # 边界
    assert tam(None, 1) == -1,                                   "multi: null"
    assert tam([[0, 0]], 1) == -1,                               "multi: no virus"
    assert tam([[1, 2]], 1) == 0,                                "multi: no healthy"
    assert tam([[1, 1], [1, 1]], 1) == 0,                        "multi: all same virus, no healthy"

    # 单病毒：退化为普通传播
    assert tam([[1, 0, 0]], 1) == 2,                             "multi: single virus 1x3"
    assert tam([[0, 0, 1]], 1) == 2,                             "multi: single virus reversed"
    assert tam([[1, 0, 0, 0]], 1) == 3,                          "multi: single virus 1x4"

    # 两种病毒，中间格 tie → 永远不被感染，返回 1（两侧各扩一步后停）
    # [1,0,0,0,2]: 第1天 -> [1,1,0,2,2]，第2天 (0,2) tie，停止 → 1
    assert tam([[1, 0, 0, 0, 2]], 1) == 1,                       "multi: two viruses meet tie in middle"

    # 两种病毒，奇数个健康格中间 tie
    # [1,0,2]: (0,1) 左边type1=1，右边type2=1，tie → 0 天扩散
    assert tam([[1, 0, 2]], 1) == 0,                             "multi: immediate tie, no spread"

    # 病毒1 数量优势赢得竞争：type1 有2个邻居，type2 只有1个
    # [[1,0,2],         (0,1): type1 邻居=(0,0)=1, type2 邻居=(0,2)=2 → tie
    #  [1,0,0]]         (1,1): type1 邻居=(1,0)=1 → wins; (1,2): type2 邻居=(0,2)=2 → wins
    # day1: [[1,0,2],[1,1,2]]
    # day2: (0,1): type1=(0,0)+(1,1)=2, type2=(0,2)=1 → type1 wins
    # → day2: [[1,1,2],[1,1,2]] → 2
    assert tam([[1, 0, 2], [1, 0, 0]], 1) == 2,                  "multi: type1 outnumbers type2"

    # N=2：需要至少 2 个同类邻居才能感染
    # [1,0,1]: 中间格两侧都是 type1，count=2>=2 → 感染，1天
    assert tam([[1, 0, 1]], 2) == 1,                             "multi: N=2 same type wins"
    # [1,0,2]: 中间格 type1=1, type2=1，均 < N=2 → 0 天
    assert tam([[1, 0, 2]], 2) == 0,                             "multi: N=2 tie below threshold"

    # 3种病毒，中心健康格三方并列
    # Day1: (1,0)→type1, (1,1)→type2, (1,2)→type3, (2,0)→type2, (2,2)→type2
    # Day2: (0,1) 邻居 type1/2/3 各1 → 3-way tie → 永不被感染
    # 只扩散1天就停止 → 1
    assert tam([[1, 0, 3], [0, 0, 0], [0, 2, 0]], 1) == 1,      "multi: 3 viruses, center contested"

    print("All Part4 tests passed.")
