import static org.junit.jupiter.api.Assertions.*;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.BeforeEach;

public class TennisSetTest {
    private TennisSet set;
    
    @BeforeEach
    void setUp() {
        set = new TennisSet();
    }
    
    @Test
    void testInitialScore() {
        assertEquals("0-0", set.getScore());
        assertEquals('#', set.getWinner());
    }
    
    @Test
    void testWinSetBy6Games() {
        // Player A wins 6 games
        for (int i = 0; i < 6; i++) {
            // Win a game (need 4 points with 2-point lead)
            for (int j = 0; j < 4; j++) {
                set.pointTo('A');
            }
        }
        
        assertEquals('A', set.getWinner());
        assertTrue(set.getScore().startsWith("6-"));
    }
    
    @Test
    void testWinSetWith2GameLead() {
        // A wins 6 games, B wins 4 games
        for (int i = 0; i < 6; i++) {
            for (int j = 0; j < 4; j++) {
                set.pointTo('A');
            }
        }
        
        for (int i = 0; i < 4; i++) {
            for (int j = 0; j < 4; j++) {
                set.pointTo('B');
            }
        }
        
        assertEquals('A', set.getWinner());
    }
    
    @Test
    void testSetNotWonWithoutLead() {
        // Both win 5 games
        for (int i = 0; i < 5; i++) {
            for (int j = 0; j < 4; j++) {
                set.pointTo('A');
            }
        }
        
        for (int i = 0; i < 5; i++) {
            for (int j = 0; j < 4; j++) {
                set.pointTo('B');
            }
        }
        
        assertEquals('#', set.getWinner()); // Not won yet
    }
}
