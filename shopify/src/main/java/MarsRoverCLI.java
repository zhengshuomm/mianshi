import java.util.Scanner;

public class MarsRoverCLI {
    public static void main(String[] args) {
        RoverController controller = new RoverController();
        Scanner scanner = new Scanner(System.in);
        System.out.println("Mars Rover Controller Initialized.");
        System.out.println("Commands: create <id> <x> <y> <dir>, select <id>, delete <id>, move <cmds>, exit");

        while (true) {
            System.out.print("> ");
            String input = scanner.nextLine();
            if (input.equalsIgnoreCase("exit"))
                break;

            String[] parts = input.split(" ");
            String cmd = parts[0].toLowerCase();

            try {
                switch (cmd) {
                    case "create":
                        System.out.println(controller.createRover(parts[1], Integer.parseInt(parts[2]),
                                Integer.parseInt(parts[3]), Direction.valueOf(parts[4].toUpperCase())));
                        break;
                    case "select":
                        System.out.println(controller.selectRover(parts[1]));
                        break;
                    case "delete":
                        System.out.println(controller.deleteRover(parts[1]));
                        break;
                    case "move":
                        System.out.println(controller.executeCommand(parts[1]));
                        break;
                    default:
                        System.out.println("Unknown command.");
                }
            } catch (Exception e) {
                System.out.println("Error: Invalid input format.");
            }
        }
        scanner.close();
    }
}
