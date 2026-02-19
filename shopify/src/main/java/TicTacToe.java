public class TicTacToe {
    private int n;
    // Track counts for each row, column, and diagonals
    private int[] rows;
    private int[] cols;
    private int diagonal;      // Main diagonal (top-left to bottom-right)
    private int antiDiagonal;  // Anti-diagonal (top-right to bottom-left)
    
    public TicTacToe(int n) {
        this.n = n;
        this.rows = new int[n];
        this.cols = new int[n];
        this.diagonal = 0;
        this.antiDiagonal = 0;
    }
    
    /**
     * Player makes a move at (row, col).
     * @param row The row of the board
     * @param col The column of the board
     * @param player The player, can be either 1 or 2
     * @return The current winning condition, can be either:
     *         0: No one wins
     *         1: Player 1 wins
     *         2: Player 2 wins
     */
    public int move(int row, int col, int player) {
        // Player 1 adds 1, Player 2 adds -1
        int value = (player == 1) ? 1 : -1;
        
        // Update row and column counts
        rows[row] += value;
        cols[col] += value;
        
        // Update diagonal if on main diagonal
        if (row == col) {
            diagonal += value;
        }
        
        // Update anti-diagonal if on anti-diagonal
        if (row + col == n - 1) {
            antiDiagonal += value;
        }
        
        // Check if player wins
        // Player 1 wins if count equals n
        // Player 2 wins if count equals -n
        if (Math.abs(rows[row]) == n || 
            Math.abs(cols[col]) == n || 
            Math.abs(diagonal) == n || 
            Math.abs(antiDiagonal) == n) {
            return player;
        }
        
        return 0;
    }
}
