import java.util.*;

/**
 * DoorDash 面试题：Find Closest Dashmart
 * 
 * 问题描述：
 * 给定一个二维网格 city，其中：
 * - 'D' 表示 Dashmart（配送中心）
 * - 'X' 表示障碍物（不可通行）
 * - '.' 表示空地（可通行）
 * 
 * 给定多个位置 locations，找到每个位置到最近 Dashmart 的最短距离。
 * 
 * 原始代码的 Bug 分析：见 ClosestDashmart_BugReport.md
 */
class Solution {
    public int[] getClosestDashmart(char[][] city, int[][] locations) {
       int[] res = new int[locations.length];
       
       // ✅ 正确做法：DFS 不用 cache，每次独立搜索
       for (int i = 0; i < locations.length; i++) {
           boolean[][] visited = new boolean[city.length][city[0].length];
           res[i] = dfs(locations[i][0], locations[i][1], city, visited);
       }
       
       return res;
    }

    int[][] dirs = {{0, 1}, {1, 0}, {0, -1}, {-1, 0}};
    
    /**
     * DFS 找最短路径（不使用 cache）
     * 注意：visited 回溯时不能用全局 cache！
     */
    private int dfs(int i, int j, char[][] city, boolean[][] visited) {
        int m = city.length;
        int n = city[0].length;
        
        // 边界检查
        if (i < 0 || i >= m || j < 0 || j >= n || 
            city[i][j] == 'X' || visited[i][j]) {
            return -1;
        }
        
        // 找到 Dashmart
        if (city[i][j] == 'D') {
            return 0;
        }

        visited[i][j] = true;
        int minDist = Integer.MAX_VALUE;

        for (int[] dir : dirs) {
            int ni = i + dir[0];
            int nj = j + dir[1];
            int dist = dfs(ni, nj, city, visited);
            
            if (dist != -1 && dist != Integer.MAX_VALUE) {
                minDist = Math.min(minDist, dist + 1);
            }
        }

        visited[i][j] = false;  // 回溯
        
        return minDist == Integer.MAX_VALUE ? -1 : minDist;
    }
}


// 测试类
public class ClosestDashmart {
    public static void main(String[] args) {
        // 测试用例
        char[][] city = {
            {'D', '.', 'X', '.', '.'},
            {'.', 'X', 'X', '.', '.'},
            {'.', '.', '.', '.', 'D'},
            {'X', '.', 'X', '.', '.'},
            {'.', '.', '.', 'X', '.'}
        };
        
        int[][] locations = {
            {0, 1},  // 位置 (0, 1)
            {4, 0},  // 位置 (4, 0)
            {1, 4},  // 位置 (1, 4)
            {3, 3}   // 位置 (3, 3)
        };
        
        System.out.println("=== 原始代码（有 Bug）===");
        Solution original = new Solution();
        int[] result1 = original.getClosestDashmart(city, locations);
        System.out.println("Results: " + Arrays.toString(result1));
        
    }
}
