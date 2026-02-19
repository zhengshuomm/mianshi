import java.util.*;

public class WallsAndGates {
    private static final int INF = 2147483647;
    private static final int GATE = 0;
    
    // Directions: up, down, left, right
    private static final int[][] DIRECTIONS = {{-1, 0}, {1, 0}, {0, -1}, {0, 1}};
    
    /**
     * Fills each empty room with the distance to its nearest gate.
     * Uses multi-source BFS starting from all gates simultaneously.
     * 
     * @param rooms The m x n grid where:
     *              -1 represents a wall
     *              0 represents a gate
     *              INF (2147483647) represents an empty room
     */
    public void wallsAndGates(int[][] rooms) {
        if (rooms == null || rooms.length == 0 || rooms[0].length == 0) {
            return;
        }
        
        int m = rooms.length;
        int n = rooms[0].length;
        
        // Queue for BFS: stores [row, col] coordinates
        Queue<int[]> queue = new LinkedList<>();
        
        // Add all gates to the queue as starting points
        for (int i = 0; i < m; i++) {
            for (int j = 0; j < n; j++) {
                if (rooms[i][j] == GATE) {
                    queue.offer(new int[]{i, j});
                }
            }
        }
        
        // Multi-source BFS: expand from all gates simultaneously
        while (!queue.isEmpty()) {
            int[] current = queue.poll();
            int row = current[0];
            int col = current[1];
            int distance = rooms[row][col];
            
            // Explore all 4 directions
            for (int[] dir : DIRECTIONS) {
                int newRow = row + dir[0];
                int newCol = col + dir[1];
                
                // Check if the new position is valid and is an empty room
                if (newRow >= 0 && newRow < m && 
                    newCol >= 0 && newCol < n && 
                    rooms[newRow][newCol] == INF) {
                    
                    // Update the distance (current distance + 1)
                    rooms[newRow][newCol] = distance + 1;
                    queue.offer(new int[]{newRow, newCol});
                }
            }
        }
    }
    
    /**
     * Alternative implementation using DFS (less efficient but works)
     * This is included for reference but BFS is preferred.
     */
    public void wallsAndGatesDFS(int[][] rooms) {
        if (rooms == null || rooms.length == 0 || rooms[0].length == 0) {
            return;
        }
        
        int m = rooms.length;
        int n = rooms[0].length;
        
        // Start DFS from each gate
        for (int i = 0; i < m; i++) {
            for (int j = 0; j < n; j++) {
                if (rooms[i][j] == GATE) {
                    dfs(rooms, i, j, 0, m, n);
                }
            }
        }
    }
    
    private void dfs(int[][] rooms, int row, int col, int distance, int m, int n) {
        // Check bounds and validity
        if (row < 0 || row >= m || col < 0 || col >= n || 
            rooms[row][col] < distance) {
            return;
        }
        
        // Update the room with the minimum distance
        rooms[row][col] = distance;
        
        // Explore all 4 directions
        for (int[] dir : DIRECTIONS) {
            dfs(rooms, row + dir[0], col + dir[1], distance + 1, m, n);
        }
    }
}
