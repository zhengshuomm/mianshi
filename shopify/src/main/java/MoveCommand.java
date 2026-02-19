public class MoveCommand implements Command {
    @Override
    public void execute(MarsRover rover) {
        rover.move();
    }
}
