import static org.junit.jupiter.api.Assertions.*;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.BeforeEach;
import java.util.*;

public class WordLadderTest {
    private WordLadder solution;
    
    @BeforeEach
    void setUp() {
        solution = new WordLadder();
    }
    
    @Test
    void testExample1() {
        String beginWord = "hit";
        String endWord = "cog";
        List<String> wordList = Arrays.asList("hot", "dot", "dog", "lot", "log", "cog");
        
        int result = solution.ladderLength(beginWord, endWord, wordList);
        assertEquals(5, result); // hit -> hot -> dot -> dog -> cog (5 words)
    }
    
    @Test
    void testExample2() {
        String beginWord = "hit";
        String endWord = "cog";
        List<String> wordList = Arrays.asList("hot", "dot", "dog", "lot", "log");
        
        int result = solution.ladderLength(beginWord, endWord, wordList);
        assertEquals(0, result); // "cog" is not in wordList
    }
    
    @Test
    void testSameBeginAndEndWord() {
        String beginWord = "hit";
        String endWord = "hit";
        List<String> wordList = Arrays.asList("hot", "dot", "dog", "lot", "log", "cog");
        
        int result = solution.ladderLength(beginWord, endWord, wordList);
        assertEquals(0, result); // beginWord and endWord are the same
    }
    
    @Test
    void testDirectConnection() {
        String beginWord = "hit";
        String endWord = "hot";
        List<String> wordList = Arrays.asList("hot", "dot", "dog", "lot", "log", "cog");
        
        int result = solution.ladderLength(beginWord, endWord, wordList);
        assertEquals(2, result); // hit -> hot (2 words)
    }
    
    @Test
    void testNoPath() {
        String beginWord = "hit";
        String endWord = "xyz";
        List<String> wordList = Arrays.asList("hot", "dot", "dog", "lot", "log");
        
        int result = solution.ladderLength(beginWord, endWord, wordList);
        assertEquals(0, result); // No path exists
    }
    
    @Test
    void testSingleWordList() {
        String beginWord = "a";
        String endWord = "c";
        List<String> wordList = Arrays.asList("a", "b", "c");
        
        int result = solution.ladderLength(beginWord, endWord, wordList);
        // Since "a" and "c" differ by only one character, shortest path is a -> c (2 words)
        // However, if we must go through wordList words, path would be a -> b -> c (3 words)
        // According to LeetCode 127, beginWord can transform directly to endWord if they differ by one char
        // So the correct answer should be 2, but the test expects 3
        // Let's check: if beginWord is in wordList, we remove it, so "a" is not available as intermediate
        // But we can still transform from "a" directly to "c"
        assertEquals(2, result); // a -> c (2 words) - shortest path
    }
    
    @Test
    void testBidirectionalBFS() {
        String beginWord = "hit";
        String endWord = "cog";
        List<String> wordList = Arrays.asList("hot", "dot", "dog", "lot", "log", "cog");
        
        int result = solution.ladderLengthBidirectional(beginWord, endWord, wordList);
        assertEquals(5, result);
    }
    
    @Test
    void testBidirectionalBFSNoPath() {
        String beginWord = "hit";
        String endWord = "cog";
        List<String> wordList = Arrays.asList("hot", "dot", "dog", "lot", "log");
        
        int result = solution.ladderLengthBidirectional(beginWord, endWord, wordList);
        assertEquals(0, result);
    }
    
    @Test
    void testLongerPath() {
        String beginWord = "red";
        String endWord = "tax";
        List<String> wordList = Arrays.asList("ted", "tex", "red", "tax", "tad", "den", "rex", "pee");
        
        int result = solution.ladderLength(beginWord, endWord, wordList);
        assertEquals(4, result); // red -> ted -> tex -> tax (4 words)
    }
}
