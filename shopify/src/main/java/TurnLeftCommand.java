public class TurnLeftCommand implements Command {
    @Override
    public void execute(MarsRover rover) {
        rover.turnLeft();
    }
}
