import java.util.ArrayList;
import java.util.List;


// sql, iterator, excel sheet
public class InfectionSpread3 {

    private static final int[][] DIRS = {{0, 1}, {1, 0}, {0, -1}, {-1, 0}};

    /**
     * Part 1， 2: N=1，完全感染所需步数。0=健康，1=感染, 2=immune
     */
    public int timeToFullInfection(int[][] grid) {
        if (grid == null || grid.length == 0) return -1;
        int day = 0;
        int healthyCount = 0;
        boolean hasInfected = false;
        int m = grid.length, n = grid[0].length;

        int[][] current = new int[m][n];
        for (int i = 0 ; i < m ; i ++) {
            for (int j = 0 ; j < n ; j ++) {
                current[i][j] = grid[i][j];
                if (grid[i][j] == 0) {
                    healthyCount ++;
                } else if (grid[i][j] == 1){
                    hasInfected = true;
                }
            }
        }

        if (!hasInfected) return -1;
        if (healthyCount == 0) return 0;

        while (healthyCount > 0) {
            int[][] next = new int[m][n];
            boolean found = false;
            for (int i = 0 ; i < m ; i ++) {
                for (int j = 0 ; j < n ; j ++) {
                    next[i][j] = current[i][j];
                    if (current[i][j] != 0) continue;
                    int count = 0;
                    for (int[] dir : DIRS) {
                        int ni = i + dir[0];
                        int nj = j + dir[1];
                        if (ni >= 0 && ni < m && nj >=0 && nj < n && current[ni][nj] == 1) {
                            count ++;
                        }
                    }
                    if (count >= 1) {
                        next[i][j] = 1;
                        healthyCount --;
                        found = true;
                    }
                }
            }

            if (!found) return -1;
            day ++;
            current = next;
        }
        return day;
    }

    /**
     * Part 3: 康复与免疫。0=健康，1=初始感染, 2=immune；感染 D 天后康复并永久免疫。
     * 返回：感染完全停止时的天数（无活跃感染）。
     */
    public int timeToFullInfectionN(int[][] grid, int D) {
        if (grid == null || grid.length == 0) return -1;
        int day = 0;
        int affectedNum = 0;
        int m = grid.length, n = grid[0].length;

        int[][] current = new int[m][n];
        for (int i = 0 ; i < m ; i ++) {
            for (int j = 0 ; j < n ; j ++) {
                if (grid[i][j] == 1) {
                    affectedNum ++ ;
                    current[i][j] = 0;
                } else if (grid[i][j] == 0){
                    current[i][j] = -1;
                }else if (grid[i][j] == 2){
                    current[i][j] = -2;
                }  
            }
        }

        if (affectedNum == 0) return 0;

        while (affectedNum > 0) {
            for (int i = 0 ; i < m ; i ++) {
                for (int j = 0 ; j < n ; j ++) {
                    if (current[i][j] >= 0 && day - current[i][j] >= D) {
                        current[i][j] = -2;
                        affectedNum --;
                    }
                }
            }

            int[][] next = new int[m][n];
            for (int i = 0 ; i < m ; i ++) {
                for (int j = 0 ; j < n ; j ++) {
                    next[i][j] = current[i][j];
                    if (current[i][j] != -1) continue;
                    int count = 0;
                    for (int[] dir : DIRS) {
                        int ni = i + dir[0];
                        int nj = j + dir[1];
                        if (ni >= 0 && ni < m && nj >=0 && nj < n && current[ni][nj] >= 0) {
                            count ++;
                        }
                    }
                    if (count >= 1) {
                        next[i][j] = day;
                        affectedNum ++;
                    }
                }
            }

            current = next;
            day ++;
        }
        return day;
    }


    public static void main(String[] args) {
        InfectionSpread3 s = new InfectionSpread3();

        // 空 / 无感染 / 全感染
        if (s.timeToFullInfection(null) != -1) throw new AssertionError("null");
        if (s.timeToFullInfection(new int[0][]) != -1) throw new AssertionError("empty rows");
        if (s.timeToFullInfection(new int[][]{{}}) != -1) throw new AssertionError("empty grid");
        if (s.timeToFullInfection(new int[][]{{0, 0}, {0, 0}}) != -1) throw new AssertionError("no infected");
        if (s.timeToFullInfection(new int[][]{{1, 1}, {1, 1}}) != 0) throw new AssertionError("all infected");

        // 单格
        if (s.timeToFullInfection(new int[][]{{1}}) != 0) throw new AssertionError("1x1 infected");
        if (s.timeToFullInfection(new int[][]{{0}}) != -1) throw new AssertionError("1x1 healthy");

        // 中心一点 3x3：第 1 轮四邻，第 2 轮四角 → 共 2 天
        int[][] center = {{0, 0, 0}, {0, 1, 0}, {0, 0, 0}};
        if (s.timeToFullInfection(center) != 2) throw new AssertionError("center 3x3: " + s.timeToFullInfection(center));

        // 一角 3x3：最远 (2,2) 距离 4 → 4 天
        int[][] corner = {{1, 0, 0}, {0, 0, 0}, {0, 0, 0}};
        if (s.timeToFullInfection(corner) != 4) throw new AssertionError("corner 3x3: " + s.timeToFullInfection(corner));

        // 对角的两个角都有感染：等效最远距离
        int[][] twoCorners = {{1, 0, 0}, {0, 0, 0}, {0, 0, 1}};
        if (s.timeToFullInfection(twoCorners) != 2) throw new AssertionError("two corners: " + s.timeToFullInfection(twoCorners));

        // 一行：1,0,0,0,0 → 4 天
        int[][] line = {{1, 0, 0, 0, 0}};
        if (s.timeToFullInfection(line) != 4) throw new AssertionError("line: " + s.timeToFullInfection(line));

        // 一列
        int[][] col = {{1}, {0}, {0}, {0}};
        if (s.timeToFullInfection(col) != 3) throw new AssertionError("col: " + s.timeToFullInfection(col));

        // 2x2 一个感染
        int[][] twoByTwo = {{1, 0}, {0, 0}};
        if (s.timeToFullInfection(twoByTwo) != 2) throw new AssertionError("2x2: " + s.timeToFullInfection(twoByTwo));

        // 十字形：中间和四邻已感染，一步收完四角
        int[][] cross = {{0, 1, 0}, {1, 1, 1}, {0, 1, 0}};
        if (s.timeToFullInfection(cross) != 1) throw new AssertionError("cross: " + s.timeToFullInfection(cross));

        // ========== Part 3: 康复与免疫 (timeToFullInfectionN(grid, D)) ==========
        // 无感染 → 0 天即停止
        if (s.timeToFullInfectionN(new int[][]{{0, 0}, {0, 0}}, 2) != 0) throw new AssertionError("Part3: no infected");
        if (s.timeToFullInfectionN(new int[][]{{2, 2}, {2, 2}}, 2) != 0) throw new AssertionError("Part3: all immune");

        // 单格感染：第 D 天康复后循环末尾 day++ 再退出，故返回 D+1
        if (s.timeToFullInfectionN(new int[][]{{1}}, 1) != 2) throw new AssertionError("Part3: 1x1 D=1");
        if (s.timeToFullInfectionN(new int[][]{{1}}, 5) != 6) throw new AssertionError("Part3: 1x1 D=5");

        // D=0：当天就康复，下一轮退出返回 1
        if (s.timeToFullInfectionN(new int[][]{{1}}, 0) != 1) throw new AssertionError("Part3: 1x1 D=0");

        // 中心 3x3，D=1：第 0 天中心感染、传四邻；第 1 天全部满 1 天康复，下一轮退出 → 2
        int[][] c3 = {{0, 0, 0}, {0, 1, 0}, {0, 0, 0}};
        if (s.timeToFullInfectionN(c3, 1) != 2) throw new AssertionError("Part3: center 3x3 D=1: " + s.timeToFullInfectionN(c3, 1));

        // 中心 3x3，D=3：第 2 天全被感染，第 3 天中心与四邻康复，第 4 天四角康复 → 5
        if (s.timeToFullInfectionN(new int[][]{{0, 0, 0}, {0, 1, 0}, {0, 0, 0}}, 3) != 5) throw new AssertionError("Part3: center 3x3 D=3");

        // 一角 3x3，D=2：传播到最远需 4 天，感染会先康复，停止时间 > 4
        int[][] corner3 = {{1, 0, 0}, {0, 0, 0}, {0, 0, 0}};
        if (s.timeToFullInfectionN(corner3, 2) != 6) throw new AssertionError("Part3: corner 3x3 D=2: " + s.timeToFullInfectionN(corner3, 2));

        // 一行 1,0,0，D=2：第 0 天传一个，第 1 天再传一个；第 2 天前两个康复，第 3 天最后一个康复 → 4
        int[][] line3 = {{1, 0, 0}};
        if (s.timeToFullInfectionN(line3, 2) != 4) throw new AssertionError("Part3: line 1,0,0 D=2: " + s.timeToFullInfectionN(line3, 2));

        // 含免疫格：中间有 2 阻挡传播
        int[][] withImmune = {{1, 0, 0}, {0, 2, 0}, {0, 0, 0}};
        if (s.timeToFullInfectionN(withImmune, 10) != 14) throw new AssertionError("Part3: with immune D=10: " + s.timeToFullInfectionN(withImmune, 10));

        // 全已感染 D=1：第 1 天全部康复，下一轮退出 → 2
        if (s.timeToFullInfectionN(new int[][]{{1, 1}, {1, 1}}, 1) != 2) throw new AssertionError("Part3: all infected D=1");

        // null / 空网格
        if (s.timeToFullInfectionN(null, 2) != -1) throw new AssertionError("Part3: null");
        if (s.timeToFullInfectionN(new int[0][], 2) != -1) throw new AssertionError("Part3: empty rows");

        System.out.println("All InfectionSpread3 tests passed.");
    }
}
