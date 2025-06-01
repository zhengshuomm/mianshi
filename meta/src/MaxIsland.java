public class MaxIsland {
    static int[][] dir = {{0, 1}, {1, 0}, {0, -1}, {-1, 0}};
    public static void main(String[] args) {
        int max = 0;
        int[][] matrix = {{1,1,1, 0}, {1, 1, 1, 0}, {1,1, 1, 0}};
        boolean[][] visited = new boolean[3][4];
        for (int i = 0 ; i < matrix.length ; i ++) {
            for (int j = 0 ; j < matrix[0].length ; j ++) {
                max =  Math.max(max, dfs(matrix, i, j, visited));
            }
        }
        System.out.println(max);
    }

    public static int dfs(int[][] matrix, int i, int j, boolean[][] visited) {
        if (i < 0 || i >= matrix.length || j <  0 || j >= matrix[0].length || matrix[i][j] == 0 || visited[i][j] || !isValid(i, j, matrix)) {
            return 0;
        }
        visited[i][j] = true;
        int max = 0;
        for (int[] d : dir) {
            max = Math.max(max, dfs(matrix, i + d[0], j + d[1], visited));
        }
        return max + 1;
    }

    private static boolean isValid(int x, int y, int[][] matrix) {
        for (int[] d : dir) {
            int i = x + d[0];
            int j = y + d[1];
            if (i < 0 || i >= matrix.length || j <  0 || j >= matrix[0].length || matrix[i][j] == 0) {
                return true;
            }
        }
        return false;
    }
}
