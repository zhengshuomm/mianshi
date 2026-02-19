public class MarsRover {
    private int x;
    private int y;
    private Direction direction;

    public MarsRover(int x, int y, Direction direction) {
        this.x = x;
        this.y = y;
        this.direction = direction;
    }

    public String execute(String commands) {
        StringBuilder report = new StringBuilder();
        CommandParser parser = new CommandParser();
        java.util.List<Command> parsedCommands = parser.parse(commands);
        for (Command cmd : parsedCommands) {
            cmd.execute(this);
            report.append(getStatus()).append("\n");
        }
        return report.toString().trim();
    }

    public void move() {
        switch (direction) {
            case NORTH:
                y++;
                break;
            case EAST:
                x++;
                break;
            case SOUTH:
                y--;
                break;
            case WEST:
                x--;
                break;
        }
    }

    public void turnLeft() {
        direction = direction.turnLeft();
    }

    public void turnRight() {
        direction = direction.turnRight();
    }

    public String getStatus() {
        return String.format("(%d, %d) facing %s", x, y, direction);
    }

    public int getX() {
        return x;
    }

    public int getY() {
        return y;
    }

    public Direction getDirection() {
        return direction;
    }

    // For collision detection in Phase 2
    public void setPosition(int x, int y) {
        this.x = x;
        this.y = y;
    }
}
