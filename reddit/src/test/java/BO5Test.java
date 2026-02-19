import static org.junit.jupiter.api.Assertions.*;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.BeforeEach;

public class BO5Test {
    private BO5 match;

    @BeforeEach
    void setUp() {
        match = new BO5();
    }

    @Test
    void testInitialState() {
        assertEquals('#', match.getWinner());
        // assertFalse(match.isFinished());
        assertEquals("", match.getMatchScore().trim());
    }

    @Test
    void testWinMatch() {
        // Player A wins 3 sets
        for (int set = 0; set < 3; set++) {
            // Win a set (6 games)
            for (int game = 0; game < 6; game++) {
                // Win a game (4 points)
                for (int point = 0; point < 4; point++) {
                    match.pointTo('A');
                }
            }
        }

        assertEquals('A', match.getWinner());
        // assertTrue(match.isFinished());
        assertEquals(3, match.getSetsA());
        assertEquals(0, match.getSetsB());
    }

    @Test
    void testMatchScore() {
        // A wins first set
        for (int game = 0; game < 6; game++) {
            for (int point = 0; point < 4; point++) {
                match.pointTo('A');
            }
        }

        String score = match.getMatchScore();
        assertTrue(score.contains("6-"));
    }

    @Test
    void testMatchNotFinished() {
        // A wins 2 sets
        for (int set = 0; set < 2; set++) {
            for (int game = 0; game < 6; game++) {
                for (int point = 0; point < 4; point++) {
                    match.pointTo('A');
                }
            }
        }

        assertEquals('#', match.getWinner());
        // assertFalse(match.isFinished());
        assertEquals(2, match.getSetsA());
    }
}
