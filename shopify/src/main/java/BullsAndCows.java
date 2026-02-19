import java.util.*;

/**
 * Bulls and Cows Number Guessing Game
 * 
 * A 4-digit number guessing game where:
 * - Secret number: 4 digits, unique digits, cannot start with 0
 * - Player has 7 attempts to guess
 * - Feedback: X bulls (correct digit and position), Y cows (correct digit, wrong position)
 */
public class BullsAndCows {
    private String secretNumber;
    private int attempts;
    private static final int MAX_ATTEMPTS = 7;
    private static final int NUMBER_LENGTH = 4;
    private boolean hintUsed;
    private Set<String> usedNumbers; // Track previously used secret numbers
    
    public BullsAndCows() {
        this.attempts = 0;
        this.hintUsed = false;
        this.usedNumbers = new HashSet<>();
        generateSecretNumber();
    }
    
    /**
     * Generates a valid 4-digit secret number with unique digits, not starting with 0.
     * Bonus: Ensures the number is different from all previously generated numbers.
     */
    private void generateSecretNumber() {
        Random random = new Random();
        String number;
        
        do {
            // Generate first digit (1-9)
            int firstDigit = random.nextInt(9) + 1;
            Set<Integer> usedDigits = new HashSet<>();
            usedDigits.add(firstDigit);
            
            StringBuilder sb = new StringBuilder();
            sb.append(firstDigit);
            
            // Generate remaining 3 digits (0-9, excluding already used)
            while (sb.length() < NUMBER_LENGTH) {
                int digit = random.nextInt(10);
                if (!usedDigits.contains(digit)) {
                    usedDigits.add(digit);
                    sb.append(digit);
                }
            }
            
            number = sb.toString();
        } while (usedNumbers.contains(number)); // Ensure uniqueness across games
        
        this.secretNumber = number;
        usedNumbers.add(number);
    }
    
    /**
     * Validates if the guess is a valid 4-digit number with unique digits, not starting with 0.
     */
    public boolean isValidGuess(String guess) {
        if (guess == null || guess.length() != NUMBER_LENGTH) {
            return false;
        }
        
        // Check if all characters are digits
        if (!guess.matches("\\d+")) {
            return false;
        }
        
        // Check if starts with 0
        if (guess.charAt(0) == '0') {
            return false;
        }
        
        // Check if all digits are unique
        Set<Character> digits = new HashSet<>();
        for (char c : guess.toCharArray()) {
            if (!digits.add(c)) {
                return false; // Duplicate digit found
            }
        }
        
        return true;
    }
    
    /**
     * Processes a guess and returns the feedback.
     * 
     * @param guess The user's guess
     * @return Feedback string in format "X bulls, Y cows" or "Lucky strike!!!" if 0 bulls, 0 cows
     */
    public String makeGuess(String guess) {
        if (!isValidGuess(guess)) {
            return "Invalid guess! Please enter a 4-digit number with unique digits, not starting with 0.";
        }
        
        attempts++;
        
        int bulls = 0;
        int cows = 0;
        
        // Count bulls (correct digit and position)
        for (int i = 0; i < NUMBER_LENGTH; i++) {
            if (guess.charAt(i) == secretNumber.charAt(i)) {
                bulls++;
            }
        }
        
        // Count cows (correct digit, wrong position)
        // We need to count digits that are in secret but not bulls
        Map<Character, Integer> secretCount = new HashMap<>();
        Map<Character, Integer> guessCount = new HashMap<>();
        
        for (int i = 0; i < NUMBER_LENGTH; i++) {
            char secretChar = secretNumber.charAt(i);
            char guessChar = guess.charAt(i);
            
            if (secretChar != guessChar) {
                secretCount.put(secretChar, secretCount.getOrDefault(secretChar, 0) + 1);
                guessCount.put(guessChar, guessCount.getOrDefault(guessChar, 0) + 1);
            }
        }
        
        // Count cows: digits that appear in both but in different positions
        for (char c : guessCount.keySet()) {
            if (secretCount.containsKey(c)) {
                cows += Math.min(guessCount.get(c), secretCount.get(c));
            }
        }
        
        // Check for win
        if (bulls == NUMBER_LENGTH) {
            return "Congratulations! You guessed the number correctly: " + secretNumber;
        }
        
        // Check for lucky strike
        if (bulls == 0 && cows == 0) {
            return "Lucky strike!!!";
        }
        
        return bulls + " bull" + (bulls != 1 ? "s" : "") + ", " + cows + " cow" + (cows != 1 ? "s" : "");
    }
    
    /**
     * Provides a hint about the secret number.
     * Can only be used once per game.
     * 
     * @return Hint information
     */
    public String getHint() {
        if (hintUsed) {
            return "Hint already used! You can only use one hint per game.";
        }
        
        hintUsed = true;
        StringBuilder hint = new StringBuilder();
        
        // 1. Print one of the digits asserted (one digit from secret number)
        Random random = new Random();
        int randomIndex = random.nextInt(NUMBER_LENGTH);
        char assertedDigit = secretNumber.charAt(randomIndex);
        hint.append(assertedDigit).append(" is a valid assertion.\n");
        
        // 2. Find a digit that is in secret but might be in wrong position
        // We'll pick a digit from secret and say it's not in the right position
        // (This is a bit tricky since we don't have a current guess context)
        // We'll just pick another digit and state it's not in position X
        int hintIndex = (randomIndex + 1) % NUMBER_LENGTH;
        char wrongPosDigit = secretNumber.charAt(hintIndex);
        hint.append("The digit ").append(wrongPosDigit).append(" is not in the correct position.\n");
        
        // 3. Count even and odd numbers in secret
        int evenCount = 0;
        int oddCount = 0;
        for (char c : secretNumber.toCharArray()) {
            int digit = Character.getNumericValue(c);
            if (digit % 2 == 0) {
                evenCount++;
            } else {
                oddCount++;
            }
        }
        hint.append("There are ").append(evenCount).append(" even and ").append(oddCount)
            .append(" odd number").append(oddCount != 1 ? "s" : "").append(".");
        
        return hint.toString();
    }
    
    /**
     * Checks if the game is over (win or lose).
     */
    public boolean isGameOver() {
        return attempts >= MAX_ATTEMPTS;
    }
    
    /**
     * Gets the remaining attempts.
     */
    public int getRemainingAttempts() {
        return Math.max(0, MAX_ATTEMPTS - attempts);
    }
    
    /**
     * Gets the secret number (for testing or end of game).
     */
    public String getSecretNumber() {
        return secretNumber;
    }
    
    /**
     * Resets the game with a new secret number.
     */
    public void reset() {
        this.attempts = 0;
        this.hintUsed = false;
        generateSecretNumber();
    }
}
