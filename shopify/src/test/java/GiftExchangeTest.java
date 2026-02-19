import static org.junit.jupiter.api.Assertions.*;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.io.TempDir;
import java.io.*;
import java.nio.file.Path;
import java.util.*;

public class GiftExchangeTest {
    private GiftExchange exchange;
    
    @BeforeEach
    void setUp() {
        exchange = new GiftExchange();
    }
    
    @Test
    void testReadParticipantsFromFile(@TempDir Path tempDir) throws IOException {
        // Create a temporary CSV file
        File csvFile = tempDir.resolve("test.csv").toFile();
        try (PrintWriter writer = new PrintWriter(csvFile)) {
            writer.println("Alice,alice@example.com");
            writer.println("Bob,bob@example.com");
            writer.println("Charlie,charlie@example.com");
        }
        
        exchange.readParticipantsFromFile(csvFile.getAbsolutePath());
        
        List<Participant> participants = exchange.getParticipants();
        assertEquals(3, participants.size());
        assertEquals("Alice", participants.get(0).getName());
        assertEquals("alice@example.com", participants.get(0).getEmail());
    }
    
    @Test
    void testReadParticipantsWithWhitespace(@TempDir Path tempDir) throws IOException {
        File csvFile = tempDir.resolve("test.csv").toFile();
        try (PrintWriter writer = new PrintWriter(csvFile)) {
            writer.println("  Alice  ,  alice@example.com  ");
            writer.println("Bob,bob@example.com");
        }
        
        exchange.readParticipantsFromFile(csvFile.getAbsolutePath());
        
        List<Participant> participants = exchange.getParticipants();
        assertEquals(2, participants.size());
        assertEquals("Alice", participants.get(0).getName());
        assertEquals("alice@example.com", participants.get(0).getEmail());
    }
    
    @Test
    void testGenerateMatches() throws IOException {
        // Create participants manually
        exchange.readParticipantsFromFile("group1.csv");
        
        Map<Participant, Participant> matches = exchange.generateMatches();
        
        // Check all participants are matched
        assertEquals(5, matches.size());
        
        // Check no one matches themselves
        for (Map.Entry<Participant, Participant> entry : matches.entrySet()) {
            assertNotEquals(entry.getKey(), entry.getValue(), 
                "Participant " + entry.getKey().getName() + " matched with themselves!");
        }
        
        // Check everyone gives a gift
        Set<Participant> givers = matches.keySet();
        assertEquals(5, givers.size());
        
        // Check everyone receives a gift
        Set<Participant> receivers = new HashSet<>(matches.values());
        assertEquals(5, receivers.size());
    }
    
    @Test
    void testGenerateMatchesWithTwoPeople() throws IOException {
        File csvFile = File.createTempFile("test", ".csv");
        try (PrintWriter writer = new PrintWriter(csvFile)) {
            writer.println("Alice,alice@example.com");
            writer.println("Bob,bob@example.com");
        }
        
        exchange.readParticipantsFromFile(csvFile.getAbsolutePath());
        Map<Participant, Participant> matches = exchange.generateMatches();
        
        assertEquals(2, matches.size());
        
        // With 2 people, they must match each other
        Participant alice = exchange.getParticipants().get(0);
        Participant bob = exchange.getParticipants().get(1);
        
        // One should give to the other, and vice versa
        assertTrue((matches.get(alice).equals(bob) && matches.get(bob).equals(alice)) ||
                   (matches.get(alice).equals(bob) && matches.get(bob).equals(alice)));
    }
    
    @Test
    void testGenerateMatchesThrowsExceptionForOnePerson() {
        File csvFile = null;
        try {
            csvFile = File.createTempFile("test", ".csv");
            try (PrintWriter writer = new PrintWriter(csvFile)) {
                writer.println("Alice,alice@example.com");
            }
            
            exchange.readParticipantsFromFile(csvFile.getAbsolutePath());
            
            assertThrows(IllegalStateException.class, () -> {
                exchange.generateMatches();
            });
        } catch (IOException e) {
            fail("IOException: " + e.getMessage());
        } finally {
            if (csvFile != null) {
                csvFile.delete();
            }
        }
    }
    
    @Test
    void testNoSelfMatching() throws IOException {
        exchange.readParticipantsFromFile("group1.csv");
        
        // Run multiple times to ensure no self-matching
        for (int i = 0; i < 100; i++) {
            Map<Participant, Participant> matches = exchange.generateMatches();
            
            for (Map.Entry<Participant, Participant> entry : matches.entrySet()) {
                assertNotEquals(entry.getKey(), entry.getValue(),
                    "Self-match found in iteration " + i);
            }
        }
    }
    
    @Test
    void testAllParticipantsMatched() throws IOException {
        exchange.readParticipantsFromFile("group1.csv");
        List<Participant> originalParticipants = exchange.getParticipants();
        
        Map<Participant, Participant> matches = exchange.generateMatches();
        
        // All participants should be givers
        assertEquals(originalParticipants.size(), matches.size());
        
        // All participants should be receivers
        Set<Participant> receivers = new HashSet<>(matches.values());
        assertEquals(originalParticipants.size(), receivers.size());
        
        // Receivers should be a subset of participants
        for (Participant receiver : receivers) {
            assertTrue(originalParticipants.contains(receiver));
        }
    }
    
    @Test
    void testParticipantClass() {
        Participant p1 = new Participant("Alice", "alice@example.com");
        Participant p2 = new Participant("Alice", "alice@example.com");
        Participant p3 = new Participant("Bob", "bob@example.com");
        
        assertEquals(p1, p2);
        assertNotEquals(p1, p3);
        assertEquals(p1.hashCode(), p2.hashCode());
        
        assertEquals("Alice (alice@example.com)", p1.toString());
    }
}
