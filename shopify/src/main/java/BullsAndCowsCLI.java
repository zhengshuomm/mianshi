import java.util.Scanner;

/**
 * Command Line Interface for Bulls and Cows game
 */
public class BullsAndCowsCLI {
    public static void main(String[] args) {
        Scanner scanner = new Scanner(System.in);
        BullsAndCows game = new BullsAndCows();
        
        System.out.println("=== Bulls and Cows Number Guessing Game ===");
        System.out.println("Guess the 4-digit number!");
        System.out.println("- 4 digits, all unique, cannot start with 0");
        System.out.println("- You have 7 attempts");
        System.out.println("- Bulls: correct digit in correct position");
        System.out.println("- Cows: correct digit in wrong position");
        System.out.println("- Type 'hint' for a hint (can use once per game)");
        System.out.println("- Type 'quit' to exit");
        System.out.println();
        
        while (true) {
            System.out.println("Attempts remaining: " + game.getRemainingAttempts());
            System.out.print("Enter your guess (or 'hint' for hint, 'quit' to exit): ");
            String input = scanner.nextLine().trim();
            
            if (input.equalsIgnoreCase("quit")) {
                System.out.println("The secret number was: " + game.getSecretNumber());
                System.out.println("Thanks for playing! Goodbye!");
                break;
            }
            
            if (input.equalsIgnoreCase("hint")) {
                System.out.println(game.getHint());
                System.out.println();
                continue;
            }
            
            String result = game.makeGuess(input);
            System.out.println(result);
            System.out.println();
            
            // Check if game is over
            if (result.contains("Congratulations")) {
                System.out.println("Would you like to play again? (yes/no)");
                String playAgain = scanner.nextLine().trim();
                if (playAgain.equalsIgnoreCase("yes")) {
                    game.reset();
                    System.out.println("New game started! Good luck!");
                    System.out.println();
                } else {
                    System.out.println("Thanks for playing! Goodbye!");
                    break;
                }
            } else if (game.isGameOver() && !result.contains("Congratulations")) {
                System.out.println("Game Over! You've used all 7 attempts.");
                System.out.println("The secret number was: " + game.getSecretNumber());
                System.out.println("Would you like to play again? (yes/no)");
                String playAgain = scanner.nextLine().trim();
                if (playAgain.equalsIgnoreCase("yes")) {
                    game.reset();
                    System.out.println("New game started! Good luck!");
                    System.out.println();
                } else {
                    System.out.println("Thanks for playing! Goodbye!");
                    break;
                }
            }
        }
        
        scanner.close();
    }
}
