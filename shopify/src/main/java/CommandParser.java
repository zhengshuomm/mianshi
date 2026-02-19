import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

public class CommandParser {
    private final Map<Character, Command> registry = new HashMap<>();

    public CommandParser() {
        // Default commands
        register('L', new TurnLeftCommand());
        register('R', new TurnRightCommand());
        register('M', new MoveCommand());
    }

    public void register(char key, Command cmd) {
        registry.put(key, cmd);
    }

    public List<Command> parse(String commands) {
        List<Command> parsedCommands = new ArrayList<>();
        for (char c : commands.toCharArray()) {
            Command cmd = registry.get(c);
            if (cmd != null) {
                parsedCommands.add(cmd);
            }
        }
        return parsedCommands;
    }
}
