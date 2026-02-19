import static org.junit.jupiter.api.Assertions.assertEquals;
import org.junit.jupiter.api.Test;

public class MarsRoverTest {
    @Test
    void testInitialPosition() {
        MarsRover rover = new MarsRover(0, 0, Direction.NORTH);
        assertEquals("(0, 0) facing NORTH", rover.getStatus());
    }

    @Test
    void testTurnLeft() {
        MarsRover rover = new MarsRover(0, 0, Direction.NORTH);
        rover.execute("L");
        assertEquals(Direction.WEST, rover.getDirection());
        rover.execute("L");
        assertEquals(Direction.SOUTH, rover.getDirection());
        rover.execute("L");
        assertEquals(Direction.EAST, rover.getDirection());
        rover.execute("L");
        assertEquals(Direction.NORTH, rover.getDirection());
    }

    @Test
    void testTurnRight() {
        MarsRover rover = new MarsRover(0, 0, Direction.NORTH);
        rover.execute("R");
        assertEquals(Direction.EAST, rover.getDirection());
        rover.execute("R");
        assertEquals(Direction.SOUTH, rover.getDirection());
        rover.execute("R");
        assertEquals(Direction.WEST, rover.getDirection());
        rover.execute("R");
        assertEquals(Direction.NORTH, rover.getDirection());
    }

    @Test
    void testMove() {
        MarsRover rover = new MarsRover(0, 0, Direction.NORTH);
        rover.execute("M");
        assertEquals("(0, 1) facing NORTH", rover.getStatus());
        rover.execute("R");
        rover.execute("M");
        assertEquals("(1, 1) facing EAST", rover.getStatus());
    }

    @Test
    void testComplexCommands() {
        MarsRover rover = new MarsRover(0, 0, Direction.NORTH);
        rover.execute("MMRMLM");
        // M -> (0,1) N
        // M -> (0,2) N
        // R -> (0,2) E
        // M -> (1,2) E
        // L -> (1,2) N
        // M -> (1,3) N
        assertEquals("(1, 3) facing NORTH", rover.getStatus());
    }

    @Test
    void testExtensibility() {
        MarsRover rover = new MarsRover(0, 0, Direction.NORTH);
        CommandParser parser = new CommandParser();
        parser.register('B', new BackCommand());

        java.util.List<Command> commands = parser.parse("B");
        for (Command cmd : commands) {
            cmd.execute(rover);
        }

        assertEquals("(0, -1) facing NORTH", rover.getStatus());
    }
}
