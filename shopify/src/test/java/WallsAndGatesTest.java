import static org.junit.jupiter.api.Assertions.*;
import org.junit.jupiter.api.Test;

public class WallsAndGatesTest {
    private static final int INF = 2147483647;
    private static final int WALL = -1;
    private static final int GATE = 0;
    
    @Test
    void testExample1() {
        WallsAndGates solution = new WallsAndGates();
        int[][] rooms = {
            {INF, WALL, GATE, INF},
            {INF, INF, INF, WALL},
            {INF, WALL, INF, WALL},
            {GATE, WALL, INF, INF}
        };
        
        solution.wallsAndGates(rooms);
        
        int[][] expected = {
            {3, WALL, GATE, 1},
            {2, 2, 1, WALL},
            {1, WALL, 2, WALL},
            {GATE, WALL, 3, 4}
        };
        
        assertArrayEquals(expected, rooms);
    }
    
    @Test
    void testSingleWall() {
        WallsAndGates solution = new WallsAndGates();
        int[][] rooms = {{WALL}};
        
        solution.wallsAndGates(rooms);
        
        int[][] expected = {{WALL}};
        assertArrayEquals(expected, rooms);
    }
    
    @Test
    void testSingleGate() {
        WallsAndGates solution = new WallsAndGates();
        int[][] rooms = {{GATE}};
        
        solution.wallsAndGates(rooms);
        
        int[][] expected = {{GATE}};
        assertArrayEquals(expected, rooms);
    }
    
    @Test
    void testSingleRoom() {
        WallsAndGates solution = new WallsAndGates();
        int[][] rooms = {{INF}};
        
        solution.wallsAndGates(rooms);
        
        // Room cannot reach any gate, so it remains INF
        int[][] expected = {{INF}};
        assertArrayEquals(expected, rooms);
    }
    
    @Test
    void testAllGates() {
        WallsAndGates solution = new WallsAndGates();
        int[][] rooms = {
            {GATE, GATE},
            {GATE, GATE}
        };
        
        solution.wallsAndGates(rooms);
        
        int[][] expected = {
            {GATE, GATE},
            {GATE, GATE}
        };
        assertArrayEquals(expected, rooms);
    }
    
    @Test
    void testAllWalls() {
        WallsAndGates solution = new WallsAndGates();
        int[][] rooms = {
            {WALL, WALL},
            {WALL, WALL}
        };
        
        solution.wallsAndGates(rooms);
        
        int[][] expected = {
            {WALL, WALL},
            {WALL, WALL}
        };
        assertArrayEquals(expected, rooms);
    }
    
    @Test
    void testSingleGateInCenter() {
        WallsAndGates solution = new WallsAndGates();
        int[][] rooms = {
            {INF, INF, INF},
            {INF, GATE, INF},
            {INF, INF, INF}
        };
        
        solution.wallsAndGates(rooms);
        
        int[][] expected = {
            {2, 1, 2},
            {1, GATE, 1},
            {2, 1, 2}
        };
        assertArrayEquals(expected, rooms);
    }
    
    @Test
    void testRoomsBlockedByWalls() {
        WallsAndGates solution = new WallsAndGates();
        int[][] rooms = {
            {INF, WALL, GATE},
            {INF, WALL, INF},
            {INF, WALL, INF}
        };
        
        solution.wallsAndGates(rooms);
        
        // Rooms on the left side cannot reach the gate due to walls
        int[][] expected = {
            {INF, WALL, GATE},
            {INF, WALL, 1},
            {INF, WALL, 2}
        };
        assertArrayEquals(expected, rooms);
    }
    
    @Test
    void testMultipleGates() {
        WallsAndGates solution = new WallsAndGates();
        int[][] rooms = {
            {GATE, INF, INF, GATE},
            {INF, WALL, WALL, INF},
            {INF, INF, INF, INF}
        };
        
        solution.wallsAndGates(rooms);
        
        // Each room should have distance to nearest gate
        int[][] expected = {
            {GATE, 1, 2, GATE},
            {1, WALL, WALL, 1},
            {2, 3, 4, 1}
        };
        assertArrayEquals(expected, rooms);
    }
    
    @Test
    void testEmptyGrid() {
        WallsAndGates solution = new WallsAndGates();
        int[][] rooms = {};
        
        // Should not throw exception
        solution.wallsAndGates(rooms);
        assertEquals(0, rooms.length);
    }
}
