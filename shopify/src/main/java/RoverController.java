import java.util.HashMap;
import java.util.Map;

public class RoverController {
    private final Map<String, MarsRover> rovers;
    private final CommandParser commandParser;
    private String selectedRoverId;

    public RoverController() {
        this.rovers = new HashMap<>();
        this.commandParser = new CommandParser();
    }

    public String createRover(String id, int x, int y, Direction direction) {
        if (rovers.containsKey(id)) {
            return "Error: Rover with ID " + id + " already exists.";
        }
        if (isPositionOccupied(x, y)) {
            return "Error: Position (" + x + ", " + y + ") is already occupied.";
        }
        MarsRover rover = new MarsRover(x, y, direction);
        rovers.put(id, rover);
        selectedRoverId = id;
        return "Rover " + id + " created at " + rover.getStatus();
    }

    public String deleteRover(String id) {
        if (!rovers.containsKey(id)) {
            return "Error: Rover with ID " + id + " does not exist.";
        }
        rovers.remove(id);
        if (id.equals(selectedRoverId)) {
            selectedRoverId = null;
        }
        return "Rover " + id + " deleted.";
    }

    public String selectRover(String id) {
        if (!rovers.containsKey(id)) {
            return "Error: Rover with ID " + id + " does not exist.";
        }
        selectedRoverId = id;
        return "Rover " + id + " selected.";
    }

    public String executeCommand(String commands) {
        if (selectedRoverId == null) {
            return "Error: No rover selected.";
        }
        MarsRover rover = rovers.get(selectedRoverId);
        StringBuilder report = new StringBuilder();

        java.util.List<Command> parsedCommands = commandParser.parse(commands);
        for (Command cmd : parsedCommands) {
            if (cmd instanceof MoveCommand) {
                int nextX = rover.getX();
                int nextY = rover.getY();
                switch (rover.getDirection()) {
                    case NORTH:
                        nextY++;
                        break;
                    case EAST:
                        nextX++;
                        break;
                    case SOUTH:
                        nextY--;
                        break;
                    case WEST:
                        nextX--;
                        break;
                }

                if (isPositionOccupied(nextX, nextY)) {
                    report.append("Collision detected at (").append(nextX).append(", ").append(nextY)
                            .append("). Movement aborted.\n");
                    break;
                }
            }

            cmd.execute(rover);
            report.append(rover.getStatus()).append("\n");
        }

        return report.toString().trim();
    }

    private boolean isPositionOccupied(int x, int y) {
        for (MarsRover rover : rovers.values()) {
            if (rover.getX() == x && rover.getY() == y) {
                return true;
            }
        }
        return false;
    }

    public String getSelectedRoverId() {
        return selectedRoverId;
    }
}
