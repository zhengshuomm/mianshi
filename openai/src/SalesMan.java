import java.util.*;

class GridPathFinder {
    static class State {
        int x, y, mask;
        List<int[]> path;

        State(int x, int y, int mask, List<int[]> path) {
            this.x = x;
            this.y = y;
            this.mask = mask;
            this.path = path;
        }
    }

    private static final int[][] DIRS = {{1, 0}, {-1, 0}, {0, 1}, {0, -1}};

    public List<int[]> findShortestPath(char[][] grid) {
        int m = grid.length, n = grid[0].length;
        Map<String, Integer> starMap = new HashMap<>();
        int starCount = 0;

        // Assign an index to each '*' and store their coordinates
        int index = 0;
        for (int i = 0; i < m; i++) {
            for (int j = 0; j < n; j++) {
                if (grid[i][j] == '*') {
                    starMap.put(i + "," + j, index ++);
                    starCount++;
                }
            }
        }

        int finalMask = (1 << starCount) - 1;
        List<int[]> result = new ArrayList<>();

        // Try starting BFS from every non-wall cell
        for (int i = 0; i < m; i++) {
            for (int j = 0; j < n; j++) {
                if (grid[i][j] != '#') {
                    List<int[]> path = bfs(grid, i, j, starMap, finalMask);
                    if (!path.isEmpty() && (result.isEmpty() || path.size() < result.size())) {
                        result = path;
                    }
                }
            }
        }

        return result;
    }

    private List<int[]> bfs(char[][] grid, int x, int y, Map<String, Integer> startMap, int finalMask) {
        int m = grid.length, n = grid[0].length;
        Queue<State> queue = new LinkedList<>();
        boolean[][][] visited = new boolean[m][n][1<<startMap.size()];


        int startMask = 0;
        if (grid[x][y] == '*') {
            startMask |= (1 << startMap.get(x + "," + y));
        }
        queue.add(new State(x, y, startMask, List.of(new int[]{x, y})));
        visited[x][y][startMask] = true;

        while (!queue.isEmpty()) {
            State cur = queue.poll();
            if (cur.mask == finalMask) return cur.path;
            for (int[] dir : DIRS) {
                int nx = cur.x + dir[0];
                int ny = cur.y + dir[1];
                if (nx < 0 || nx >= m || ny < 0 || ny >= n || grid[nx][ny] == '#') continue;

                int mask = cur.mask;
                if (grid[nx][ny] == '*') {
                    mask |= (1 << startMap.get(nx + "," + ny));
                }
                if (visited[nx][ny][mask]) continue;
                visited[nx][ny][mask] = true;
                List<int[]> path = new ArrayList<>(cur.path);
                path.add(new int[]{nx, ny});
                queue.add(new State(nx, ny, mask, path));
            }
        }
        return List.of();
    }

//    private List<int[]> bfs(char[][] grid, int sx, int sy, Map<Integer, int[]> starMap, int finalMask) {
//        int m = grid.length, n = grid[0].length;
//        Queue<State> queue = new LinkedList<>();
//        boolean[][][] visited = new boolean[m][n][1 << starMap.size()];
//        Map<String, Integer> starIndexMap = new HashMap<>();
//        int idx = 0;
//        for (Map.Entry<Integer, int[]> entry : starMap.entrySet()) {
//            starIndexMap.put(entry.getValue()[0] + "," + entry.getValue()[1], idx++);
//        }
//
//        int startMask = 0;
//        if (grid[sx][sy] == '*') {
//            startMask |= (1 << starIndexMap.get(sx + "," + sy));
//        }
//
//        queue.offer(new State(sx, sy, startMask, new ArrayList<>(List.of(new int[]{sx, sy}))));
//        visited[sx][sy][startMask] = true;
//
//        while (!queue.isEmpty()) {
//            State cur = queue.poll();
//            if (cur.mask == finalMask) return cur.path;
//
//            for (int[] dir : DIRS) {
//                int nx = cur.x + dir[0];
//                int ny = cur.y + dir[1];
//
//                if (nx < 0 || ny < 0 || nx >= m || ny >= n || grid[nx][ny] == '#') continue;
//
//                int newMask = cur.mask;
//                if (grid[nx][ny] == '*') {
//                    int starBit = starIndexMap.get(nx + "," + ny);
//                    newMask |= (1 << starBit);
//                }
//
//                if (!visited[nx][ny][newMask]) {
//                    visited[nx][ny][newMask] = true;
//                    List<int[]> newPath = new ArrayList<>(cur.path);
//                    newPath.add(new int[]{nx, ny});
//                    queue.offer(new State(nx, ny, newMask, newPath));
//                }
//            }
//        }
//        return new ArrayList<>();
//    }

    // Test
    public static void main(String[] args) {
        GridPathFinder finder = new GridPathFinder();

        char[][] grid1 = {
                {'.', '*', '.'},
                {'.', '#', '.'},
                {'*', '.', '.'}
        };
        System.out.println("Example 1: " + format(finder.findShortestPath(grid1)));

        char[][] grid2 = {
                {'*', '.', '*'},
                {'.', '#', '.'},
                {'.', '.', '.'}
        };
        System.out.println("Example 2: " + format(finder.findShortestPath(grid2)));

        char[][] grid3 = {
                {'*', '#', '.'},
                {'.', '#', '*'},
                {'*', '.', '.'}
        };
        System.out.println("Example 3: " + format(finder.findShortestPath(grid3)));

        char[][] grid4 = {
                {'*', '#', '*'},
                {'*', '*', '*'},
                {'*', '*', '*'},
        };
        System.out.println("Example 4: " + format(finder.findShortestPath(grid4)));

    }

    private static String format(List<int[]> path) {
        if (path.isEmpty()) return "[]";
        StringBuilder sb = new StringBuilder("[");
        for (int[] p : path) {
            sb.append(Arrays.toString(p)).append(", ");
        }
        if (sb.length() > 1) sb.setLength(sb.length() - 2);
        return sb.append("]").toString();
    }
}
