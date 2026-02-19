public class BackCommand implements Command {
    @Override
    public void execute(MarsRover rover) {
        // Simple implementation: turn twice, move, turn twice
        // Or better, add a moveBackwards to MarsRover, but for demo we can just use
        // existing methods
        rover.turnLeft();
        rover.turnLeft();
        rover.move();
        rover.turnLeft();
        rover.turnLeft();
    }
}
