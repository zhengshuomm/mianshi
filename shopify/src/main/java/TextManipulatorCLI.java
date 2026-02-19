import java.util.Scanner;

/**
 * Command Line Interface for Text Manipulator
 */
public class TextManipulatorCLI {
    public static void main(String[] args) {
        Scanner scanner = new Scanner(System.in);
        
        System.out.println("=== Text Manipulator ===");
        System.out.println("Commands:");
        System.out.println("  h - move cursor left");
        System.out.println("  l - move cursor right");
        System.out.println("  r<c> - replace character at cursor with <c>");
        System.out.println("  [N]h - move cursor left N positions");
        System.out.println("  [N]l - move cursor right N positions");
        System.out.println("  [N]r<c> - replace N characters with <c>");
        System.out.println();
        System.out.println("Type 'quit' to exit");
        System.out.println();
        
        while (true) {
            System.out.print("Enter initial text: ");
            String text = scanner.nextLine().trim();
            
            if (text.equalsIgnoreCase("quit")) {
                break;
            }
            
            if (text.isEmpty()) {
                System.out.println("Text cannot be empty. Try again.");
                continue;
            }
            
            System.out.print("Enter commands: ");
            String commands = scanner.nextLine().trim();
            
            if (commands.equalsIgnoreCase("quit")) {
                break;
            }
            
            TextManipulator tm = new TextManipulator(text);
            tm.processCommands(commands);
            
            System.out.println("Output: " + tm.getText());
            System.out.println("Cursor: " + tm.getCursor());
            System.out.println();
            
            // Show cursor position visually
            String output = tm.getText();
            int cursor = tm.getCursor();
            StringBuilder visual = new StringBuilder();
            for (int i = 0; i < output.length(); i++) {
                if (i == cursor) {
                    visual.append("[").append(output.charAt(i)).append("]");
                } else {
                    visual.append(output.charAt(i));
                }
            }
            if (cursor == output.length()) {
                visual.append("[]");
            }
            System.out.println("Visual: " + visual.toString());
            System.out.println();
        }
        
        scanner.close();
    }
}
