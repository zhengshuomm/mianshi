import static org.junit.jupiter.api.Assertions.*;
import org.junit.jupiter.api.Test;
import java.util.List;

public class FileSystemTest {
    
    @Test
    void testBasicOperations() {
        FileSystem fs = new FileSystem();
        
        // Test ls on root
        List<String> result = fs.ls("/");
        assertEquals(0, result.size());
        
        // Test mkdir
        fs.mkdir("/a/b/c");
        
        // Test ls on root - should see "a"
        result = fs.ls("/");
        assertEquals(1, result.size());
        assertEquals("a", result.get(0));
        
        // Test addContentToFile
        fs.addContentToFile("/a/b/c/d", "hello");
        
        // Test ls on root - should still see "a"
        result = fs.ls("/");
        assertEquals(1, result.size());
        assertEquals("a", result.get(0));
        
        // Test readContentFromFile
        String content = fs.readContentFromFile("/a/b/c/d");
        assertEquals("hello", content);
    }
    
    @Test
    void testLsOnFile() {
        FileSystem fs = new FileSystem();
        fs.addContentToFile("/a/b", "hello");
        
        // ls on a file should return only the file name
        List<String> result = fs.ls("/a/b");
        assertEquals(1, result.size());
        assertEquals("b", result.get(0));
    }
    
    @Test
    void testLsOnDirectory() {
        FileSystem fs = new FileSystem();
        fs.mkdir("/a/b/c");
        fs.addContentToFile("/a/b/d", "file1");
        fs.addContentToFile("/a/b/e", "file2");
        
        // ls on directory should return all children in lexicographic order
        List<String> result = fs.ls("/a/b");
        assertEquals(3, result.size());
        assertEquals("c", result.get(0));
        assertEquals("d", result.get(1));
        assertEquals("e", result.get(2));
    }
    
    @Test
    void testAppendContent() {
        FileSystem fs = new FileSystem();
        fs.addContentToFile("/a/b", "hello");
        fs.addContentToFile("/a/b", " world");
        
        String content = fs.readContentFromFile("/a/b");
        assertEquals("hello world", content);
    }
    
    @Test
    void testComplexScenario() {
        FileSystem fs = new FileSystem();
        
        // Create directories
        fs.mkdir("/a/b/c");
        fs.mkdir("/a/b/d");
        
        // Add files
        fs.addContentToFile("/a/b/c/file1", "content1");
        fs.addContentToFile("/a/b/c/file2", "content2");
        
        // List directory
        List<String> result = fs.ls("/a/b/c");
        assertEquals(2, result.size());
        assertEquals("file1", result.get(0));
        assertEquals("file2", result.get(1));
        
        // Read file
        String content = fs.readContentFromFile("/a/b/c/file1");
        assertEquals("content1", content);
    }
}
