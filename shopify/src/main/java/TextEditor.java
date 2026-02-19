import java.util.*;

/**
 * LeetCode 2296: Design a Text Editor
 * 
 * A text editor that supports cursor operations, text insertion, and deletion.
 * Uses two stacks (Deques) to efficiently manage cursor position.
 */
public class TextEditor {
    // Left stack: characters to the left of cursor (top is the character just before cursor)
    // Right stack: characters to the right of cursor (top is the character just after cursor)
    private Deque<Character> leftStack;
    private Deque<Character> rightStack;
    
    public TextEditor() {
        leftStack = new ArrayDeque<>();
        rightStack = new ArrayDeque<>();
    }
    
    /**
     * Adds text at the cursor position. The cursor moves to the right of the inserted text.
     * 
     * @param text The text to add
     */
    public void addText(String text) {
        for (char c : text.toCharArray()) {
            leftStack.addLast(c);
        }
    }
    
    /**
     * Deletes up to k characters to the left of the cursor.
     * 
     * @param k Maximum number of characters to delete
     * @return The actual number of characters deleted
     */
    public int deleteText(int k) {
        int count = Math.min(k, leftStack.size());
        for (int i = 0; i < count; i++) {
            leftStack.removeLast();
        }
        return count;
    }
    
    /**
     * Moves the cursor left by up to k positions.
     * 
     * @param k Maximum number of positions to move left
     * @return The last min(10, len) characters to the left of the cursor
     */
    public String cursorLeft(int k) {
        int move = Math.min(k, leftStack.size());
        // Move characters from left stack to right stack
        for (int i = 0; i < move; i++) {
            rightStack.addLast(leftStack.removeLast());
        }
        // Return the last min(10, leftStack.size()) characters
        return getLastNChars(10);
    }
    
    /**
     * Moves the cursor right by up to k positions.
     * 
     * @param k Maximum number of positions to move right
     * @return The last min(10, len) characters to the left of the cursor
     */
    public String cursorRight(int k) {
        int move = Math.min(k, rightStack.size());
        // Move characters from right stack to left stack
        for (int i = 0; i < move; i++) {
            leftStack.addLast(rightStack.removeLast());
        }
        // Return the last min(10, leftStack.size()) characters
        return getLastNChars(10);
    }
    
    /**
     * Helper method to get the last n characters from the left stack
     * without destroying the stack structure.
     * 
     * @param n Maximum number of characters to return
     * @return The last min(n, leftStack.size()) characters
     */
    private String getLastNChars(int n) {
        int count = Math.min(n, leftStack.size());
        if (count == 0) {
            return "";
        }
        
        // Temporarily remove characters
        List<Character> temp = new ArrayList<>();
        for (int i = 0; i < count; i++) {
            temp.add(leftStack.removeLast());
        }
        
        // Build the string (in reverse order since we removed from end)
        StringBuilder sb = new StringBuilder();
        for (int i = temp.size() - 1; i >= 0; i--) {
            sb.append(temp.get(i));
        }
        
        // Put characters back
        for (int i = temp.size() - 1; i >= 0; i--) {
            leftStack.addLast(temp.get(i));
        }
        
        return sb.toString();
    }
}
