import static org.junit.jupiter.api.Assertions.*;
import org.junit.jupiter.api.Test;
import java.util.*;

public class ReportChainTest {
    
    @Test
    void testPart1PrintTree() {
        String[] relationships = {"A,B,C", "C,D", "B,E"};
        ReportChain rc = new ReportChain(relationships);
        
        // Just verify it doesn't throw exception
        assertDoesNotThrow(() -> rc.printTree());
    }
    
    @Test
    void testPart2SkipMeetings() {
        String[] relationships = {"A,B,C", "C,D", "B,E"};
        ReportChain rc = new ReportChain(relationships);
        
        List<String[]> pairs = rc.findSkipMeetings();
        assertNotNull(pairs);
        // A and E, A and D should be skip meeting pairs
        assertTrue(pairs.size() > 0);
    }
    
    @Test
    void testPart3PrintChain() {
        String[] relationships = {"A,B,C", "C,D", "B,E"};
        ReportChain rc = new ReportChain(relationships);
        
        // Just verify it doesn't throw exception
        assertDoesNotThrow(() -> rc.printChain("B"));
    }
    
    @Test
    void testPart4LCA() {
        String[] relationships = {"A,B,C", "C,D", "B,E"};
        ReportChain rc = new ReportChain(relationships);
        
        String lca = rc.findLCA("C", "E");
        assertEquals("A", lca);
        
        lca = rc.findLCA("A", "D");
        assertEquals("A", lca);
        
        lca = rc.findLCA("D", "E");
        assertEquals("A", lca);
    }
    
    @Test
    void testLCAWithSelf() {
        String[] relationships = {"A,B,C", "C,D", "B,E"};
        ReportChain rc = new ReportChain(relationships);
        
        String lca = rc.findLCA("C", "C");
        assertEquals("C", lca);
    }
}
