import static org.junit.jupiter.api.Assertions.*;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.BeforeEach;

public class TennisGameTest {
    private TennisGame game;
    
    @BeforeEach
    void setUp() {
        game = new TennisGame();
    }
    
    @Test
    void testInitialScore() {
        assertEquals("0:0", game.getScore());
        assertEquals('#', game.getWinner());
    }
    
    @Test
    void testBasicScoring() {
        game.pointTo('A');
        assertEquals("1:0", game.getScore());
        
        game.pointTo('B');
        assertEquals("1:1", game.getScore());
        
        game.pointTo('A');
        assertEquals("2:1", game.getScore());
        
        game.pointTo('A');
        assertEquals("3:1", game.getScore());
    }
    
    @Test
    void testDeuce() {
        // Both players reach 3 points (deuce)
        game.pointTo('A');
        game.pointTo('A');
        game.pointTo('A');
        game.pointTo('B');
        game.pointTo('B');
        game.pointTo('B');
        
        assertEquals("3:3", game.getScore());
        assertEquals('#', game.getWinner());
    }
    
    @Test
    void testAdvantage() {
        // Reach deuce
        game.pointTo('A');
        game.pointTo('A');
        game.pointTo('A');
        game.pointTo('B');
        game.pointTo('B');
        game.pointTo('B');
        
        // Player A gets advantage
        game.pointTo('A');
        assertEquals("4:3", game.getScore());
        assertEquals('#', game.getWinner());
        
        // Back to deuce
        game.pointTo('B');
        assertEquals("3:3", game.getScore());
    }
    
    @Test
    void testWinByTwoPoints() {
        // Player A wins
        game.pointTo('A');
        game.pointTo('A');
        game.pointTo('A');
        game.pointTo('A');
        game.pointTo('A');
        game.pointTo('A');
        
        assertEquals('A', game.getWinner());
        
        // Try to add more points after game ends
        game.pointTo('B');
        assertEquals('A', game.getWinner()); // Winner shouldn't change
    }
    
    @Test
    void testWinAfterDeuce() {
        // Reach deuce
        game.pointTo('A');
        game.pointTo('A');
        game.pointTo('A');
        game.pointTo('B');
        game.pointTo('B');
        game.pointTo('B');
        
        // Player A wins after deuce
        game.pointTo('A');
        game.pointTo('A');
        
        assertEquals('A', game.getWinner());
    }
    
    @Test
    void testPlayerBWins() {
        game.pointTo('B');
        game.pointTo('B');
        game.pointTo('B');
        game.pointTo('B');
        game.pointTo('B');
        game.pointTo('B');
        
        assertEquals('B', game.getWinner());
    }
    
    @Test
    void testMultipleDeuce() {
        // Reach deuce multiple times
        game.pointTo('A');
        game.pointTo('A');
        game.pointTo('A');
        game.pointTo('B');
        game.pointTo('B');
        game.pointTo('B');
        
        // A gets advantage
        game.pointTo('A');
        assertEquals("4:3", game.getScore());
        
        // Back to deuce
        game.pointTo('B');
        assertEquals("3:3", game.getScore());
        
        // B gets advantage
        game.pointTo('B');
        assertEquals("3:4", game.getScore());
        
        // Back to deuce again
        game.pointTo('A');
        assertEquals("3:3", game.getScore());
    }
    
    @Test
    void testNoWinnerBeforeFourPoints() {
        game.pointTo('A');
        game.pointTo('A');
        game.pointTo('A');
        
        assertEquals('#', game.getWinner());
        assertEquals("3:0", game.getScore());
    }
    
    @Test
    void testScoreDisplayAfterDeuce() {
        // Both players at 3
        game.pointTo('A');
        game.pointTo('A');
        game.pointTo('A');
        game.pointTo('B');
        game.pointTo('B');
        game.pointTo('B');
        assertEquals("3:3", game.getScore());
        
        // A scores, should show 4:3
        game.pointTo('A');
        assertEquals("4:3", game.getScore());
        
        // B scores, back to 3:3
        game.pointTo('B');
        assertEquals("3:3", game.getScore());
        
        // Both score again
        game.pointTo('A');
        game.pointTo('B');
        assertEquals("3:3", game.getScore());
    }
}
