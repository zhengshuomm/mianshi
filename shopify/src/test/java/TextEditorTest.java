import static org.junit.jupiter.api.Assertions.*;
import org.junit.jupiter.api.Test;

public class TextEditorTest {
    
    @Test
    void testBasicAddText() {
        TextEditor editor = new TextEditor();
        editor.addText("leetcode");
        // After adding, cursor is at the end
        // Left stack: ['l','e','e','t','c','o','d','e']
        // Right stack: []
        assertEquals("", editor.cursorLeft(0));
    }
    
    @Test
    void testDeleteText() {
        TextEditor editor = new TextEditor();
        editor.addText("leetcode");
        assertEquals(4, editor.deleteText(4)); // Delete 4 characters
        // Left stack: ['l','e','e','t']
        // Right stack: []
        assertEquals("leet", editor.cursorLeft(10));
    }
    
    @Test
    void testDeleteMoreThanAvailable() {
        TextEditor editor = new TextEditor();
        editor.addText("abc");
        assertEquals(3, editor.deleteText(10)); // Try to delete 10, but only 3 available
        assertEquals("", editor.cursorLeft(10));
    }
    
    @Test
    void testCursorLeft() {
        TextEditor editor = new TextEditor();
        editor.addText("leetcode");
        // Move cursor left 4 positions
        String result = editor.cursorLeft(4);
        // Left stack: ['l','e','e','t']
        // Right stack: ['c','o','d','e']
        assertEquals("leet", result);
    }
    
    @Test
    void testCursorRight() {
        TextEditor editor = new TextEditor();
        editor.addText("leetcode");
        editor.cursorLeft(4); // Move to position 4
        // Left stack: ['l','e','e','t']
        // Right stack: ['c','o','d','e']
        
        String result = editor.cursorRight(2);
        // After moving right 2: Left stack: ['l','e','e','t','c','o']
        // Right stack: ['d','e']
        assertEquals("leetc", result);
    }
    
    @Test
    void testCursorLeftReturnsMax10Chars() {
        TextEditor editor = new TextEditor();
        editor.addText("abcdefghijklmnopqrstuvwxyz");
        // Move cursor to the beginning
        String result = editor.cursorLeft(26);
        // Should return only last 10 characters: "qrstuvwxyz"
        assertEquals(10, result.length());
        assertEquals("qrstuvwxyz", result);
    }
    
    @Test
    void testComplexScenario() {
        TextEditor editor = new TextEditor();
        editor.addText("leetcode");
        assertEquals(4, editor.deleteText(4));
        editor.addText("practice");
        // Current state: "leetpractice"
        String result = editor.cursorRight(3);
        // After moving right 3, should show last 10 chars of left stack
        assertEquals("leetpracti", result);
    }
    
    @Test
    void testLeetCodeExample1() {
        TextEditor editor = new TextEditor();
        editor.addText("leetcode");
        assertEquals(4, editor.deleteText(4));
        editor.addText("practice");
        // Current text: "leetpractice", cursor at end
        // cursorRight(3) - cursor already at end, can't move right
        // Should return last 10 chars: "leetpracti"
        String result1 = editor.cursorRight(3);
        assertEquals("leetpracti", result1);
        
        // cursorLeft(8) - move left 8 positions
        // Left stack should have "leet" (4 chars)
        String result2 = editor.cursorLeft(8);
        assertEquals("leet", result2);
        
        // deleteText(10) - delete all 4 chars, return 4
        assertEquals(4, editor.deleteText(10));
        
        // cursorLeft(2) - no chars left, return empty
        assertEquals("", editor.cursorLeft(2));
        
        // cursorRight(6) - no chars to move, return empty
        assertEquals("", editor.cursorRight(6));
    }
    
    @Test
    void testEmptyEditor() {
        TextEditor editor = new TextEditor();
        assertEquals(0, editor.deleteText(10));
        assertEquals("", editor.cursorLeft(10));
        assertEquals("", editor.cursorRight(10));
    }
    
    @Test
    void testCursorAtBeginning() {
        TextEditor editor = new TextEditor();
        editor.addText("hello");
        editor.cursorLeft(10); // Move to beginning
        assertEquals("", editor.cursorLeft(5)); // Try to move left more
        assertEquals("", editor.cursorLeft(1));
    }
    
    @Test
    void testCursorAtEnd() {
        TextEditor editor = new TextEditor();
        editor.addText("hello");
        // Cursor is already at end
        assertEquals("hello", editor.cursorLeft(10));
        assertEquals("", editor.cursorRight(5)); // Try to move right more
    }
    
    @Test
    void testAddAfterDelete() {
        TextEditor editor = new TextEditor();
        editor.addText("abc");
        editor.deleteText(2);
        editor.addText("xyz");
        // Should be "axyz"
        assertEquals("axyz", editor.cursorLeft(10));
    }
    
    @Test
    void testMultipleCursorMovements() {
        TextEditor editor = new TextEditor();
        editor.addText("abcdefghij");
        editor.cursorLeft(5); // Move to position 5
        editor.cursorRight(2); // Move to position 7
        editor.cursorLeft(3); // Move to position 4
        String result = editor.cursorLeft(10);
        assertEquals("abcd", result);
    }
    
    @Test
    void testDeleteAfterCursorMove() {
        TextEditor editor = new TextEditor();
        editor.addText("leetcode");
        editor.cursorLeft(4); // Move to position 4
        assertEquals(2, editor.deleteText(2)); // Delete 2 chars before cursor
        // Should be "leetode"
        String result = editor.cursorLeft(10);
        assertEquals("leetode", result);
    }
    
    @Test
    void testAddAfterCursorMove() {
        TextEditor editor = new TextEditor();
        editor.addText("leet");
        editor.cursorLeft(2); // Move to position 2
        editor.addText("code");
        // Should be "lecodeet"
        String result = editor.cursorLeft(10);
        assertEquals("lecodeet", result);
    }
}
