public class Infection {

    int[][] DIRS = {{0, 1}, {1, 0}, {-1, 0}, {0 , - 1}};
    public int timeToFullInfection(int[][] grid) {
        if (grid == null || grid.length == 0) return -1;
        int day = 0;
        int healthCount = 0;
        int m = grid.length, n = grid[0].length;
        boolean hasInfected = false;

        int[][] current = new int[m][n];
        for (int i = 0 ; i < m ; i ++) {
            for (int j = 0; j < n ; j ++) {
                current[i][j] = grid[i][j];
                if (grid[i][j] == 0) {
                    healthCount ++;
                } else if (grid[i][j] == 1) {
                    hasInfected = true;
                }
            }
        }

        if (!hasInfected || healthCount == 0) return 0;
        while (healthCount > 0) {
            int[][] next = new int[m][n];
            boolean found = false;
            for (int i = 0 ; i < m ; i ++) {
                for (int j = 0; j < n ; j ++) {
                    next[i][j] = current[i][j];
                    if (current[i][j] != 0) continue;
                    int count = 0;
                    for(int[] dir : DIRS) {
                        int ni = i + dir[0];
                        int nj = j + dir[1];
                        if (ni >= 0 && ni < m && nj >=0 && nj < n && current[ni][nj] == 1) {
                            count ++;
                        }
                    }

                    if (count >= 1) {
                        next[i][j]  = 1;
                        healthCount --;
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

    public int timeToFullInfectionN(int[][] grid, int D) {
        if (grid == null || grid.length == 0) return -1;
        int day = 0;
        // int healthCount = 0;
        int affectedNum = 0;
        int m = grid.length, n = grid[0].length;

        int[][] current = new int[m][n];
        for (int i = 0 ; i < m ; i ++) {
            for (int j = 0; j < n ; j ++) {
                // current[i][j] = grid[i][j];
                if (grid[i][j] == 1) {
                    current[i][j] = 0;
                    // healthCount ++;
                } else if (grid[i][j] == 0) {
                    affectedNum ++;
                    current[i][j] = -1;
                } else {
                    current[i][j] = -2;
                }
            }
        }

        // if (!hasInfected || healthCount == 0) return 0;
        while (affectedNum > 0) {
            for (int i = 0 ;  i< m ; i ++) {
                for (int  j = 0 ; j < n ; j ++) {
                    if (current[i][j] >= 0 && day - current[i][j] >= D) {
                        current[i][j] = -2;
                        affectedNum --;
                    }
                }
            }

            int[][] next = new int[m][n];
            for (int i = 0 ; i < m ; i ++) {
                for (int j = 0; j < n ; j ++) {
                    next[i][j] = current[i][j];
                    if (current[i][j] != -1) continue;
                    int count = 0;
                    for(int[] dir : DIRS) {
                        int ni = i + dir[0];
                        int nj = j + dir[1];
                        if (ni >= 0 && ni < m && nj >=0 && nj < n && current[ni][nj] >= 0) {
                            count ++;
                        }
                    }

                    if (count >= 1) {
                        next[i][j]  = 1;
                        affectedNum ++;
                    }
                }
            }

            day ++;
            current = next;
        }
        return day;    
    }
    
}
