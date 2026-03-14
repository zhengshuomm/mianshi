/**
 * 感染扩散模拟 — 不用 BFS，只用每轮两层 for 遍历网格，同步感染。
 * 每步：for i for j 扫一遍，根据当前状态写出下一状态到 next，再 current = next。
 * 
 * 这个版本的好
 */
public class InfectionSpread2 {

    private static final int[][] DIRS = {{0, 1}, {1, 0}, {0, -1}, {-1, 0}};

    /**
     * Part 1: N=1，完全感染所需步数。0=健康，1=感染。
     */
    public int timeToFullInfection(int[][] grid) {
        if (grid == null || grid.length == 0 || grid[0].length == 0) return -1;
        int m = grid.length;
        int n = grid[0].length;

        int[][] current = new int[m][n];
        int healthyCount = 0;
        boolean hasInfected = false;
        for (int i = 0; i < m; i++) {
            for (int j = 0; j < n; j++) {
                current[i][j] = grid[i][j];
                if (grid[i][j] == 0) healthyCount++;
                else hasInfected = true;
            }
        }
        if (healthyCount == 0) return 0;
        if (!hasInfected) return -1;

        int steps = 0;
        while (healthyCount > 0) {
            int[][] next = new int[m][n];
            boolean changed = false;
            for (int i = 0; i < m; i++) {
                for (int j = 0; j < n; j++) {
                    next[i][j] = current[i][j];
                    if (current[i][j] != 0) continue;
                    int infected = 0;
                    for (int[] d : DIRS) {
                        int ni = i + d[0], nj = j + d[1];
                        if (ni >= 0 && ni < m && nj >= 0 && nj < n && current[ni][nj] == 1)
                            infected++;
                    }
                    if (infected >= 1) {
                        next[i][j] = 1;
                        healthyCount--;
                        changed = true;
                    }
                }
            }
            if (!changed) return -1;
            current = next;
            steps++;
        }
        return steps;
    }

    /**
     * Part 2: 带免疫。0=健康，1=感染，immuneValue=免疫（不感染、不传播）。
     */
    public int timeToFullInfectionWithImmune(int[][] grid, int immuneValue) {
        if (grid == null || grid.length == 0 || grid[0].length == 0) return -1;
        int m = grid.length;
        int n = grid[0].length;

        int[][] current = new int[m][n];
        int healthyCount = 0;
        boolean hasInfected = false;
        for (int i = 0; i < m; i++) {
            for (int j = 0; j < n; j++) {
                current[i][j] = grid[i][j];
                if (grid[i][j] == 0) healthyCount++;
                else if (grid[i][j] == 1) hasInfected = true;
            }
        }
        if (healthyCount == 0) return 0;
        if (!hasInfected) return -1;

        int steps = 0;
        while (healthyCount > 0) {
            int[][] next = new int[m][n];
            boolean changed = false;
            for (int i = 0; i < m; i++) {
                for (int j = 0; j < n; j++) {
                    next[i][j] = current[i][j];
                    if (current[i][j] != 0) continue;
                    int infected = 0;
                    for (int[] d : DIRS) {
                        int ni = i + d[0], nj = j + d[1];
                        if (ni >= 0 && ni < m && nj >= 0 && nj < n && current[ni][nj] == 1)
                            infected++;
                    }
                    if (infected >= 1) {
                        next[i][j] = 1;
                        healthyCount--;
                        changed = true;
                    }
                }
            }
            if (!changed) return -1;
            current = next;
            steps++;
        }
        return steps;
    }

    /** 状态：-2=健康，-1=免疫，0+=感染于第几天 */
    private static final int HEALTHY = -2;
    private static final int IMMUNE = -1;

    /**
     * Part 3: 康复与免疫。0=健康，1=初始感染；感染 D 天后康复并永久免疫。
     * 返回：感染完全停止时的天数（无活跃感染）。
     */
    public int daysUntilInfectionStops(int[][] grid, int D) {
        if (grid == null || grid.length == 0 || grid[0].length == 0 || D <= 0) return 0;
        int m = grid.length;
        int n = grid[0].length;

        int[][] day = new int[m][n];
        boolean hasActive = false;
        for (int i = 0; i < m; i++) {
            for (int j = 0; j < n; j++) {
                day[i][j] = grid[i][j] == 1 ? 0 : HEALTHY;
                if (grid[i][j] == 1) hasActive = true;
            }
        }
        if (!hasActive) return 0;

        int currentDay = 0;
        while (true) {
            currentDay++;
            int[][] next = new int[m][n];
            boolean anyActive = false;
            // 先按“当前仍活跃”做扩散：活跃 = 已感染且 currentDay - day <= D（本日结束才康复）
            for (int i = 0; i < m; i++) {
                for (int j = 0; j < n; j++) {
                    int v = day[i][j];
                    if (v == HEALTHY) {
                        int activeNeighbors = 0;
                        for (int[] d : DIRS) {
                            int ni = i + d[0], nj = j + d[1];
                            if (ni >= 0 && ni < m && nj >= 0 && nj < n) {
                                int nd = day[ni][nj];
                                if (nd >= 0 && currentDay - nd <= D) activeNeighbors++;
                            }
                        }
                        next[i][j] = activeNeighbors >= 1 ? currentDay : HEALTHY;
                        if (next[i][j] >= 0) anyActive = true;
                    } else if (v == IMMUNE) {
                        next[i][j] = IMMUNE;
                    } else {
                        if (currentDay - v >= D) {
                            next[i][j] = IMMUNE;
                        } else {
                            next[i][j] = v;
                            anyActive = true;
                        }
                    }
                }
            }
            day = next;
            if (!anyActive) return currentDay;
        }
    }

    /**
     * Part 4: 需要至少 N 个感染邻居才被感染（无康复）。
     */
    public int timeToFullInfectionWithThreshold(int[][] grid, int N) {
        if (grid == null || grid.length == 0 || grid[0].length == 0) return -1;
        int m = grid.length;
        int n = grid[0].length;

        int[][] current = new int[m][n];
        int healthyCount = 0;
        boolean hasInfected = false;
        for (int i = 0; i < m; i++) {
            for (int j = 0; j < n; j++) {
                current[i][j] = grid[i][j];
                if (grid[i][j] == 0) healthyCount++;
                else hasInfected = true;
            }
        }
        if (healthyCount == 0) return 0;
        if (!hasInfected) return -1;

        int steps = 0;
        while (healthyCount > 0) {
            int[][] next = new int[m][n];
            boolean changed = false;
            for (int i = 0; i < m; i++) {
                for (int j = 0; j < n; j++) {
                    next[i][j] = current[i][j];
                    if (current[i][j] != 0) continue;
                    int infected = 0;
                    for (int[] d : DIRS) {
                        int ni = i + d[0], nj = j + d[1];
                        if (ni >= 0 && ni < m && nj >= 0 && nj < n && current[ni][nj] == 1)
                            infected++;
                    }
                    if (infected >= N) {
                        next[i][j] = 1;
                        healthyCount--;
                        changed = true;
                    }
                }
            }
            if (!changed) return -1;
            current = next;
            steps++;
        }
        return steps;
    }

    public static void main(String[] args) {
        InfectionSpread2 s = new InfectionSpread2();

        int[][] g1 = {{0,0,0},{0,1,0},{0,0,0}};
        System.out.println("Test 1: " + (s.timeToFullInfection(g1) == 2 ? "✅" : "❌"));

        int[][] g2 = {{1,0,0},{0,0,0},{0,0,0}};
        System.out.println("Test 2: " + (s.timeToFullInfection(g2) == 4 ? "✅" : "❌"));

        int[][] g3 = {{0,0,0},{0,1,0},{-1,-1,-1}};
        System.out.println("Test 3 (immune): " + (s.timeToFullInfectionWithImmune(g3, -1) == 2 ? "✅" : "❌"));

        int[][] g6 = {{0,1,0},{1,1,1},{0,1,0}};
        System.out.println("Test 6 (N=2): " + (s.timeToFullInfectionWithThreshold(g6, 2) == 1 ? "✅" : "❌"));

        int[][] g4 = {{0,0,0},{0,1,0},{0,0,0}};
        System.out.println("Test 4 (Part3 D=2): " + (s.daysUntilInfectionStops(g4, 2) == 4 ? "✅" : "❌"));

        int[][] g5 = {{0,0,0},{0,1,0},{0,0,0}};
        System.out.println("Test 5 (Part3 D=1): " + (s.daysUntilInfectionStops(g5, 1) == 3 ? "✅" : "❌"));

        System.out.println("Done.");
    }
}
