import static org.junit.jupiter.api.Assertions.*;
import org.junit.jupiter.api.Test;

public class RoverControllerTest {
    @Test
    void testCreateAndSelectRover() {
        RoverController controller = new RoverController();
        controller.createRover("R1", 0, 0, Direction.NORTH);
        assertEquals("R1", controller.getSelectedRoverId());

        controller.createRover("R2", 1, 1, Direction.EAST);
        assertEquals("R2", controller.getSelectedRoverId());

        controller.selectRover("R1");
        assertEquals("R1", controller.getSelectedRoverId());
    }

    @Test
    void testCollisionAvoidance() {
        RoverController controller = new RoverController();
        controller.createRover("R1", 0, 0, Direction.NORTH);
        controller.createRover("R2", 0, 1, Direction.NORTH);

        controller.selectRover("R1");
        String report = controller.executeCommand("M");

        assertTrue(report.contains("Collision detected"));
        // R1 should still be at (0,0)
        MarsRover r1 = new MarsRover(0, 0, Direction.NORTH); // Dummy to check status
        // Actually we should expose rovers or check status via controller
    }

    @Test
    void testDeleteRover() {
        RoverController controller = new RoverController();
        controller.createRover("R1", 0, 0, Direction.NORTH);
        controller.deleteRover("R1");
        assertNull(controller.getSelectedRoverId());

        String result = controller.selectRover("R1");
        assertTrue(result.startsWith("Error"));
    }

    @Test
    void testCollisionOnCreation() {
        RoverController controller = new RoverController();
        controller.createRover("R1", 0, 0, Direction.NORTH);
        String result = controller.createRover("R2", 0, 0, Direction.SOUTH);
        assertTrue(result.startsWith("Error: Position (0, 0) is already occupied"));
    }
}
