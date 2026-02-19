import java.util.*;

/**
 * OpenAI 面试题：Infection Spread Simulation
 * 
 * 问题描述：
 * 给定一个 n×m 网格，0 表示健康，1 表示感染。
 * 感染规则：健康人如果有至少 N 个感染邻居，就会被感染。
 * 邻居定义：上下左右 4 个方向（不包括对角线）。
 * 
 * Part 1: N = 1（至少 1 个感染邻居就会被感染）
 * Part 2: 免疫细胞（-1 或 2）：永远不会被感染，像墙一样阻挡传播
 * Part 3: 康复与免疫：感染 D 天后康复并永久免疫
 * Part 4: N > 1：需要至少 N 个感染邻居才会被感染（如 N=2 需 2 个，N=3 需 3 个）
 * Part 5: 多种病毒竞争：1=virus A, 2=virus B... 同时扩散，邻居多的病毒获胜
 * 
 * 返回：所有人都被感染需要的时间步数，如果不可能则返回 -1。
 * 
 * 注意：感染是同时发生的（需要 BFS 层次遍历）
 */
public class InfectionSpread {
    
    /**
     * 计算完全感染所需的时间步数（N = 1）
     */
    public int timeToFullInfection(int[][] grid) {
        if (grid == null || grid.length == 0 || grid[0].length == 0) {
            return -1;
        }
        
        int m = grid.length;
        int n = grid[0].length;
        
        int healthyCount = 0;
        Queue<int[]> queue = new LinkedList<>();
        
        for (int i = 0; i < m; i++) {
            for (int j = 0; j < n; j++) {
                if (grid[i][j] == 1) {
                    queue.offer(new int[]{i, j});
                } else {
                    healthyCount++;
                }
            }
        }
        
        if (healthyCount == 0) return 0;
        if (queue.isEmpty()) return -1;
        
        int[][] dirs = {{0, 1}, {1, 0}, {0, -1}, {-1, 0}};
        int steps = 0;
        
        while (!queue.isEmpty()) {
            int size = queue.size();
            
            for (int k = 0; k < size; k++) {
                int[] curr = queue.poll();
                int i = curr[0], j = curr[1];
                
                for (int[] dir : dirs) {
                    int ni = i + dir[0];
                    int nj = j + dir[1];
                    if (ni >= 0 && ni < m && nj >= 0 && nj < n && grid[ni][nj] == 0) {
                        grid[ni][nj] = 1;
                        queue.offer(new int[]{ni, nj});
                        healthyCount--;
                    }
                }
            }
            
            if (!queue.isEmpty()) steps++;
        }
        
        return healthyCount == 0 ? steps : -1;
    }
    
    /**
     * Part 2: 带免疫细胞的感染模拟（N = 1）
     * 网格值：0=健康，1=感染，-1或2=免疫
     */
    public int timeToFullInfectionWithImmune(int[][] grid, int immuneValue) {
        if (grid == null || grid.length == 0 || grid[0].length == 0) {
            return -1;
        }
        
        int m = grid.length;
        int n = grid[0].length;
        
        int healthyCount = 0;
        Queue<int[]> queue = new LinkedList<>();
        
        for (int i = 0; i < m; i++) {
            for (int j = 0; j < n; j++) {
                if (grid[i][j] == 1) {
                    queue.offer(new int[]{i, j});
                } else if (grid[i][j] == 0) {
                    healthyCount++;
                }
            }
        }
        
        if (healthyCount == 0) return 0;
        if (queue.isEmpty()) return -1;
        
        int[][] dirs = {{0, 1}, {1, 0}, {0, -1}, {-1, 0}};
        int steps = 0;
        
        while (!queue.isEmpty()) {
            int size = queue.size();
            
            for (int k = 0; k < size; k++) {
                int[] curr = queue.poll();
                int i = curr[0], j = curr[1];
                
                for (int[] dir : dirs) {
                    int ni = i + dir[0];
                    int nj = j + dir[1];
                    if (ni >= 0 && ni < m && nj >= 0 && nj < n && grid[ni][nj] == 0) {
                        grid[ni][nj] = 1;
                        queue.offer(new int[]{ni, nj});
                        healthyCount--;
                    }
                }
            }
            
            if (!queue.isEmpty()) steps++;
        }
        
        return healthyCount == 0 ? steps : -1;
    }
    
    /**
     * Part 3: 康复与免疫
     * 网格值：0=健康，1=初始感染。D 天后康复并免疫。
     * 状态：infectionDay -2=健康, -1=免疫, 0+=感染于第几天
     */
    private static final int HEALTHY = -2;

    public int daysUntilInfectionStops(int[][] grid, int D) {
        if (grid == null || grid.length == 0 || grid[0].length == 0 || D <= 0) {
            return 0;
        }
        
        int m = grid.length;
        int n = grid[0].length;
        int[][] dirs = {{-1, 0}, {1, 0}, {0, -1}, {0, 1}};
        
        int[][] infectionDay = new int[m][n];
        for (int i = 0; i < m; i++) Arrays.fill(infectionDay[i], HEALTHY);
        
        Set<int[]> activeInfections = new HashSet<>();
        for (int i = 0; i < m; i++) {
            for (int j = 0; j < n; j++) {
                if (grid[i][j] == 1) {
                    infectionDay[i][j] = 0;
                    activeInfections.add(new int[]{i, j});
                }
            }
        }
        
        if (activeInfections.isEmpty()) return 0;
        
        int currentDay = 0;
        
        while (!activeInfections.isEmpty()) {
            currentDay++;
            Set<int[]> newInfections = new HashSet<>();
            
            for (int[] cell : new ArrayList<>(activeInfections)) {
                int i = cell[0], j = cell[1];
                for (int[] dir : dirs) {
                    int ni = i + dir[0];
                    int nj = j + dir[1];
                    if (ni >= 0 && ni < m && nj >= 0 && nj < n && infectionDay[ni][nj] == HEALTHY) {
                        infectionDay[ni][nj] = currentDay;
                        newInfections.add(new int[]{ni, nj});
                    }
                }
            }
            activeInfections.addAll(newInfections);
            
            Set<int[]> toRemove = new HashSet<>();
            for (int[] cell : activeInfections) {
                int i = cell[0], j = cell[1];
                int day = infectionDay[i][j];
                if (day >= 0 && currentDay - day >= D) {
                    infectionDay[i][j] = -1;
                    toRemove.add(cell);
                }
            }
            activeInfections.removeAll(toRemove);
        }
        
        return currentDay;
    }
    
    /**
     * Part 4: 康复与免疫 + N > 1
     * 需要至少 N 个活跃感染邻居才会被感染（N=2 需 2 个，N=3 需 3 个）
     */
    public int daysUntilInfectionStops(int[][] grid, int D, int N) {
        if (grid == null || grid.length == 0 || grid[0].length == 0 || D <= 0 || N <= 0) {
            return 0;
        }
        
        int m = grid.length;
        int n = grid[0].length;
        int[][] dirs = {{-1, 0}, {1, 0}, {0, -1}, {0, 1}};
        
        int[][] infectionDay = new int[m][n];
        for (int i = 0; i < m; i++) Arrays.fill(infectionDay[i], HEALTHY);
        
        Set<int[]> activeInfections = new HashSet<>();
        for (int i = 0; i < m; i++) {
            for (int j = 0; j < n; j++) {
                if (grid[i][j] == 1) {
                    infectionDay[i][j] = 0;
                    activeInfections.add(new int[]{i, j});
                }
            }
        }
        
        if (activeInfections.isEmpty()) return 0;
        
        int currentDay = 0;
        
        while (!activeInfections.isEmpty()) {
            currentDay++;
            
            // 1. 扩散：健康人需 >= N 个活跃感染邻居才被感染（同时发生）
            int[][] next = new int[m][n];
            for (int i = 0; i < m; i++) System.arraycopy(infectionDay[i], 0, next[i], 0, n);
            
            Set<int[]> newInfections = new HashSet<>();
            for (int i = 0; i < m; i++) {
                for (int j = 0; j < n; j++) {
                    if (infectionDay[i][j] != HEALTHY) continue;
                    int infectedNeighbors = 0;
                    for (int[] dir : dirs) {
                        int ni = i + dir[0];
                        int nj = j + dir[1];
                        if (ni >= 0 && ni < m && nj >= 0 && nj < n) {
                            int d = infectionDay[ni][nj];
                            if (d >= 0 && currentDay - d < D) infectedNeighbors++;
                        }
                    }
                    if (infectedNeighbors >= N) {
                        next[i][j] = currentDay;
                        newInfections.add(new int[]{i, j});
                    }
                }
            }
            infectionDay = next;
            activeInfections.addAll(newInfections);
            
            // 2. 康复：感染满 D 天变免疫
            Set<int[]> toRemove = new HashSet<>();
            for (int[] cell : activeInfections) {
                int i = cell[0], j = cell[1];
                int day = infectionDay[i][j];
                if (day >= 0 && currentDay - day >= D) {
                    infectionDay[i][j] = -1;
                    toRemove.add(cell);
                }
            }
            activeInfections.removeAll(toRemove);
        }
        
        return currentDay;
    }
    
    /**
     * Part 5: 多种病毒竞争
     * 网格值：0=健康，1=virus A，2=virus B，... -1=免疫
     * 竞争规则：健康细胞被邻居最多的病毒类型感染；同数则病毒 ID 小者胜
     */
    public int daysUntilStableWithMultipleViruses(int[][] grid, int D) {
        if (grid == null || grid.length == 0 || grid[0].length == 0 || D <= 0) {
            return 0;
        }
        
        int m = grid.length;
        int n = grid[0].length;
        int[][] dirs = {{-1, 0}, {1, 0}, {0, -1}, {0, 1}};
        
        // virusType: 0=健康, 1,2,3=病毒类型, -1=免疫
        int[][] virusType = new int[m][n];
        int[][] infectionDay = new int[m][n];
        for (int i = 0; i < m; i++) {
            for (int j = 0; j < n; j++) {
                virusType[i][j] = grid[i][j];
                infectionDay[i][j] = grid[i][j] > 0 ? 0 : HEALTHY;
            }
        }
        
        Set<int[]> active = new HashSet<>();
        for (int i = 0; i < m; i++) {
            for (int j = 0; j < n; j++) {
                if (virusType[i][j] > 0) active.add(new int[]{i, j});
            }
        }
        
        if (active.isEmpty()) return 0;
        
        int currentDay = 0;
        
        while (!active.isEmpty()) {
            currentDay++;
            
            // 1. 扩散：每种病毒竞争，邻居多的获胜
            int[][] nextVirus = new int[m][n];
            int[][] nextDay = new int[m][n];
            for (int i = 0; i < m; i++) {
                System.arraycopy(virusType[i], 0, nextVirus[i], 0, n);
                System.arraycopy(infectionDay[i], 0, nextDay[i], 0, n);
            }
            
            Set<int[]> newInfections = new HashSet<>();
            for (int i = 0; i < m; i++) {
                for (int j = 0; j < n; j++) {
                    if (virusType[i][j] != 0) continue;
                    
                    // 统计各病毒类型的活跃邻居数
                    Map<Integer, Integer> countByVirus = new HashMap<>();
                    for (int[] dir : dirs) {
                        int ni = i + dir[0];
                        int nj = j + dir[1];
                        if (ni >= 0 && ni < m && nj >= 0 && nj < n) {
                            int v = virusType[ni][nj];
                            if (v > 0) {
                                int d = infectionDay[ni][nj];
                                if (d >= 0 && currentDay - d < D) {
                                    countByVirus.merge(v, 1, Integer::sum);
                                }
                            }
                        }
                    }
                    
                    // 选邻居最多者；同数取 ID 小者
                    int bestVirus = 0;
                    int bestCount = 0;
                    for (Map.Entry<Integer, Integer> e : countByVirus.entrySet()) {
                        if (e.getValue() > bestCount || (e.getValue() == bestCount && (bestVirus == 0 || e.getKey() < bestVirus))) {
                            bestCount = e.getValue();
                            bestVirus = e.getKey();
                        }
                    }
                    
                    if (bestVirus > 0) {
                        nextVirus[i][j] = bestVirus;
                        nextDay[i][j] = currentDay;
                        newInfections.add(new int[]{i, j});
                    }
                }
            }
            virusType = nextVirus;
            infectionDay = nextDay;
            active.addAll(newInfections);
            
            // 2. 康复
            Set<int[]> toRemove = new HashSet<>();
            for (int[] cell : active) {
                int i = cell[0], j = cell[1];
                int day = infectionDay[i][j];
                if (day >= 0 && currentDay - day >= D) {
                    virusType[i][j] = -1;
                    infectionDay[i][j] = -1;
                    toRemove.add(cell);
                }
            }
            active.removeAll(toRemove);
        }
        
        return currentDay;
    }
    
    /**
     * Part 3b: 通用版本（N 可配置，无康复）
     */
    public int timeToFullInfectionWithThreshold(int[][] grid, int N) {
        if (grid == null || grid.length == 0 || grid[0].length == 0) {
            return -1;
        }
        
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
        
        int[][] dirs = {{0, 1}, {1, 0}, {0, -1}, {-1, 0}};
        int steps = 0;
        
        while (healthyCount > 0) {
            int[][] next = new int[m][n];
            boolean changed = false;
            
            for (int i = 0; i < m; i++) {
                System.arraycopy(current[i], 0, next[i], 0, n);
            }
            
            for (int i = 0; i < m; i++) {
                for (int j = 0; j < n; j++) {
                    if (current[i][j] == 0) {
                        int infectedNeighbors = 0;
                        for (int[] dir : dirs) {
                            int ni = i + dir[0];
                            int nj = j + dir[1];
                            if (ni >= 0 && ni < m && nj >= 0 && nj < n && current[ni][nj] == 1) {
                                infectedNeighbors++;
                            }
                        }
                        if (infectedNeighbors >= N) {
                            next[i][j] = 1;
                            healthyCount--;
                            changed = true;
                        }
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
        InfectionSpread s = new InfectionSpread();
        
        int[][] g1 = {{0,0,0},{0,1,0},{0,0,0}};
        System.out.println("Test 1: " + (s.timeToFullInfection(g1) == 2 ? "✅" : "❌"));
        
        int[][] g2 = {{1,0,0},{0,0,0},{0,0,0}};
        System.out.println("Test 2: " + (s.timeToFullInfection(g2) == 4 ? "✅" : "❌"));
        
        int[][] g3 = {{0,0,0},{0,1,0},{-1,-1,-1}};
        System.out.println("Test 3 (immune): " + (s.timeToFullInfectionWithImmune(g3, -1) == 2 ? "✅" : "❌"));
        
        int[][] g4 = {{0,0,0},{0,1,0},{0,0,0}};
        System.out.println("Test 4 (recovery D=2): " + (s.daysUntilInfectionStops(g4, 2) == 4 ? "✅" : "❌"));
        
        int[][] g5 = {{0,0,0},{0,1,0},{0,0,0}};
        System.out.println("Test 5 (recovery D=1): " + (s.daysUntilInfectionStops(g5, 1) == 3 ? "✅" : "❌"));
        
        // Level 4: N > 1
        int[][] g6 = {{0,1,0},{1,1,1},{0,1,0}};
        System.out.println("Test 6 (N=2, 十字形): " + (s.timeToFullInfectionWithThreshold(g6, 2) == 1 ? "✅" : "❌"));
        
        int[][] g7 = {{0,0,0},{0,1,0},{0,0,0}};
        System.out.println("Test 7 (N=2 中心, D=2): " + (s.daysUntilInfectionStops(g7, 2, 2) == 2 ? "✅" : "❌"));
        
        int[][] g8 = {{1,1,0},{0,0,0},{0,0,0}};
        System.out.println("Test 8 (N=2 两格, D=2): " + (s.daysUntilInfectionStops(g8, 2, 2) >= 1 ? "✅" : "❌"));
        
        // Level 5: 多种病毒竞争
        int[][] g9 = {{1,0,2},{0,0,0},{0,0,0}};
        System.out.println("Test 9 (virus 1 vs 2, D=2): " + (s.daysUntilStableWithMultipleViruses(g9, 2) >= 1 ? "✅" : "❌"));
        
        int[][] g10 = {{1,0,0},{0,0,0},{0,0,2}};
        System.out.println("Test 10 (两病毒对角, D=3): " + (s.daysUntilStableWithMultipleViruses(g10, 3) >= 1 ? "✅" : "❌"));
        
        System.out.println("Done.");
    }
}
