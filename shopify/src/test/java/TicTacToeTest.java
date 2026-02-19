import static org.junit.jupiter.api.Assertions.*;
import org.junit.jupiter.api.Test;

public class TicTacToeTest {
    
    @Test
    void testPlayer1WinsRow() {
        TicTacToe game = new TicTacToe(3);
        assertEquals(0, game.move(0, 0, 1));
        assertEquals(0, game.move(0, 1, 1));
        assertEquals(1, game.move(0, 2, 1)); // Player 1 wins
    }
    
    @Test
    void testPlayer2WinsColumn() {
        TicTacToe game = new TicTacToe(3);
        game.move(0, 0, 1);
        assertEquals(0, game.move(0, 1, 2));
        game.move(1, 0, 1);
        assertEquals(0, game.move(1, 1, 2));
        game.move(2, 0, 1);
        assertEquals(2, game.move(2, 1, 2)); // Player 2 wins column 1
    }
    
    @Test
    void testPlayer1WinsDiagonal() {
        TicTacToe game = new TicTacToe(3);
        assertEquals(0, game.move(0, 0, 1));
        assertEquals(0, game.move(1, 1, 1));
        assertEquals(1, game.move(2, 2, 1)); // Player 1 wins main diagonal
    }
    
    @Test
    void testPlayer2WinsAntiDiagonal() {
        TicTacToe game = new TicTacToe(3);
        game.move(0, 0, 1);
        assertEquals(0, game.move(0, 2, 2));
        game.move(1, 0, 1);
        assertEquals(0, game.move(1, 1, 2));
        assertEquals(2, game.move(2, 0, 2)); // Player 2 wins anti-diagonal
    }
    
    @Test
    void testNoWinner() {
        TicTacToe game = new TicTacToe(3);
        assertEquals(0, game.move(0, 0, 1));
        assertEquals(0, game.move(0, 1, 2));
        assertEquals(0, game.move(0, 2, 1));
        assertEquals(0, game.move(1, 0, 1));
        assertEquals(0, game.move(1, 1, 1));
        assertEquals(0, game.move(1, 2, 2));
        assertEquals(0, game.move(2, 0, 2));
        assertEquals(0, game.move(2, 1, 1));
        assertEquals(0, game.move(2, 2, 2)); // No winner
    }
    
    @Test
    void testLargeBoard() {
        TicTacToe game = new TicTacToe(5);
        // Player 1 wins row 2
        assertEquals(0, game.move(2, 0, 1));
        assertEquals(0, game.move(2, 1, 1));
        assertEquals(0, game.move(2, 2, 1));
        assertEquals(0, game.move(2, 3, 1));
        assertEquals(1, game.move(2, 4, 1)); // Player 1 wins
    }
    
    @Test
    void testPlayer1WinsFirstMove() {
        TicTacToe game = new TicTacToe(1);
        assertEquals(1, game.move(0, 0, 1)); // Player 1 wins immediately
    }
    
    @Test
    void testAlternatingMoves() {
        TicTacToe game = new TicTacToe(3);
        assertEquals(0, game.move(0, 0, 1));
        assertEquals(0, game.move(1, 0, 2));
        assertEquals(0, game.move(0, 1, 1));
        assertEquals(0, game.move(1, 1, 2));
        assertEquals(1, game.move(0, 2, 1)); // Player 1 wins row 0
    }
    
    @Test
    void testPlayer2WinsRow() {
        TicTacToe game = new TicTacToe(3);
        game.move(0, 0, 1);
        assertEquals(0, game.move(1, 0, 2));
        game.move(0, 1, 1);
        assertEquals(0, game.move(1, 1, 2));
        game.move(2, 2, 1);
        assertEquals(2, game.move(1, 2, 2)); // Player 2 wins row 1
    }
    
    @Test
    void testPlayer1WinsColumn() {
        TicTacToe game = new TicTacToe(3);
        assertEquals(0, game.move(0, 0, 1));
        assertEquals(0, game.move(0, 1, 2));
        assertEquals(0, game.move(1, 0, 1));
        assertEquals(0, game.move(1, 1, 2));
        assertEquals(1, game.move(2, 0, 1)); // Player 1 wins column 0
    }
}
