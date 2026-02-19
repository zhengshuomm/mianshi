import java.io.*;
import java.util.*;

/**
 * Holiday Gift Exchange Program
 * 
 * Reads participants from a CSV file and randomly matches them
 * such that no one is matched with themselves, and everyone
 * both gives and receives a gift.
 */
public class GiftExchange {
    private List<Participant> participants;
    
    public GiftExchange() {
        this.participants = new ArrayList<>();
    }
    
    /**
     * Reads participants from a CSV file.
     * Expected format: name,email (one per line)
     * 
     * @param filename The path to the CSV file
     * @throws IOException If file cannot be read
     */
    public void readParticipantsFromFile(String filename) throws IOException {
        participants.clear();
        
        try (BufferedReader reader = new BufferedReader(new FileReader(filename))) {
            String line;
            while ((line = reader.readLine()) != null) {
                line = line.trim();
                if (line.isEmpty()) {
                    continue; // Skip empty lines
                }
                
                String[] parts = line.split(",");
                if (parts.length >= 2) {
                    String name = parts[0].trim();
                    String email = parts[1].trim();
                    participants.add(new Participant(name, email));
                }
            }
        }
    }
    
    /**
     * Generates random gift exchange matches.
     * Ensures no one is matched with themselves.
     * Uses a derangement algorithm to guarantee no self-matches.
     * 
     * @return A map where key is the giver and value is the receiver
     */
    public Map<Participant, Participant> generateMatches() {
        if (participants.size() < 2) {
            throw new IllegalStateException("Need at least 2 participants for gift exchange");
        }
        
        // Create a copy of participants list for receivers
        List<Participant> receivers = new ArrayList<>(participants);
        
        // Generate a derangement (permutation with no fixed points)
        // This ensures no one matches themselves
        Random random = new Random();
        int maxAttempts = 100; // Prevent infinite loop
        int attempts = 0;
        
        while (attempts < maxAttempts) {
            // Shuffle receivers
            Collections.shuffle(receivers, random);
            
            // Check if it's a valid derangement (no self-matches)
            boolean isValid = true;
            for (int i = 0; i < participants.size(); i++) {
                if (participants.get(i).equals(receivers.get(i))) {
                    isValid = false;
                    break;
                }
            }
            
            if (isValid) {
                // Create matches
                Map<Participant, Participant> matches = new HashMap<>();
                for (int i = 0; i < participants.size(); i++) {
                    matches.put(participants.get(i), receivers.get(i));
                }
                return matches;
            }
            
            attempts++;
        }
        
        // Fallback: use circular shift (guaranteed to work)
        // Shift by at least 1 position to avoid self-matches
        int shift = random.nextInt(participants.size() - 1) + 1;
        Map<Participant, Participant> matches = new HashMap<>();
        for (int i = 0; i < participants.size(); i++) {
            int receiverIndex = (i + shift) % participants.size();
            matches.put(participants.get(i), participants.get(receiverIndex));
        }
        
        return matches;
    }
    
    /**
     * Prints the gift exchange matches in a formatted way.
     * 
     * @param matches The matches to print
     */
    public void printMatches(Map<Participant, Participant> matches) {
        System.out.println("=== Holiday Gift Exchange Matches ===");
        System.out.println();
        
        for (Map.Entry<Participant, Participant> entry : matches.entrySet()) {
            Participant giver = entry.getKey();
            Participant receiver = entry.getValue();
            
            System.out.println(giver.getName() + " -> " + receiver.getName());
            System.out.println("  Email: " + giver.getEmail());
            System.out.println("  You are buying a gift for: " + receiver.getName() + " (" + receiver.getEmail() + ")");
            System.out.println();
        }
        
        System.out.println("Total participants: " + participants.size());
    }
    
    /**
     * Gets the list of participants.
     */
    public List<Participant> getParticipants() {
        return new ArrayList<>(participants);
    }
    
    /**
     * Main method to run the gift exchange program.
     * 
     * @param args Command line arguments: CSV filename
     */
    public static void main(String[] args) {
        if (args.length < 1) {
            System.out.println("Usage: java GiftExchange <csv_file>");
            System.out.println("Example: java GiftExchange group1.csv");
            return;
        }
        
        String filename = args[0];
        GiftExchange exchange = new GiftExchange();
        
        try {
            // Read participants from CSV file
            System.out.println("Reading participants from: " + filename);
            exchange.readParticipantsFromFile(filename);
            
            int participantCount = exchange.getParticipants().size();
            System.out.println("Found " + participantCount + " participants");
            System.out.println();
            
            if (participantCount < 2) {
                System.out.println("Error: Need at least 2 participants for gift exchange");
                return;
            }
            
            // Generate matches
            System.out.println("Generating random matches...");
            Map<Participant, Participant> matches = exchange.generateMatches();
            
            // Print matches
            exchange.printMatches(matches);
            
        } catch (IOException e) {
            System.err.println("Error reading file: " + e.getMessage());
            e.printStackTrace();
        } catch (Exception e) {
            System.err.println("Error: " + e.getMessage());
            e.printStackTrace();
        }
    }
}
