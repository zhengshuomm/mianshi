import java.util.*;

interface Bot {
    void handle(String user, String msg, ChatContext ctx);
}

class ChatContext {
    List<String> messages = new ArrayList<>();
    Map<String, String> awayStatus = new HashMap<>();
    Map<String, Integer> tacoCount = new HashMap<>();
    int meetCounter = 0;
    
    void addMessage(String msg) {
        messages.add(msg);
    }
}

class AwayBot implements Bot {
    public void handle(String user, String msg, ChatContext ctx) {
        if (msg.startsWith("/away ")) {
            String reason = msg.substring(6);
            ctx.awayStatus.put(user, reason);
            ctx.addMessage("AwayBot: " + user + " is away: " + reason);
        }
    }
}

class TacoBot implements Bot {
    public void handle(String user, String msg, ChatContext ctx) {
        if (msg.startsWith("/givetaco ")) {
            String[] parts = msg.substring(10).split(" ");
            String recipient = parts[0].substring(1); // remove @
            int count = parts.length > 1 ? Integer.parseInt(parts[1]) : 1;
            
            ctx.tacoCount.put(recipient, ctx.tacoCount.getOrDefault(recipient, 0) + count);
            int total = ctx.tacoCount.get(recipient);
            
            String tacoWord = count == 1 ? "taco" : "tacos";
            String totalWord = total == 1 ? "taco" : "tacos";
            ctx.addMessage(String.format("TacoBot: @%s gave @%s %d %s. @%s now has %d %s.",
                user, recipient, count, tacoWord, recipient, total, totalWord));
        }
    }
}

class MeetBot implements Bot {
    public void handle(String user, String msg, ChatContext ctx) {
        if (msg.startsWith("/meet ")) {
            String target = msg.substring(6);
            
            // Check if target is away
            if (ctx.awayStatus.containsKey(target)) {
                ctx.addMessage("AwayBot: " + target + " is away: " + ctx.awayStatus.get(target));
            }
            
            // Schedule meeting
            ctx.addMessage("MeetBot: Google Meet with @" + user + ", and " + target + 
                " starting at /abc-def-123");
            
            // Mark both as away
            String reason = "@" + user + " may be in a meeting right now";
            ctx.awayStatus.put(user, reason);
            reason = "@" + target + " may be in a meeting right now";
            ctx.awayStatus.put(target, reason);
        }
    }
}

public class ChatApp {
    private Map<String, ChatContext> channels = new HashMap<>();
    private List<Bot> bots = Arrays.asList(new AwayBot(), new TacoBot(), new MeetBot());
    
    public void createChannel(String channelName) {
        channels.put(channelName, new ChatContext());
    }
    
    public void sendMessage(String channelName, String user, String msg) {
        ChatContext ctx = channels.get(channelName);
        ctx.addMessage(user + ": " + msg);
        for (Bot bot : bots) {
            bot.handle(user, msg, ctx);
        }
    }
    
    public List<String> getMessages(String channelName) {
        return new ArrayList<>(channels.get(channelName).messages);
    }
    
    public static void main(String[] args) {
        ChatApp app = new ChatApp();
        
        // Create channels
        app.createChannel("general");
        app.createChannel("dev");
        
        // Messages in general
        app.sendMessage("general", "Alice", "Hello");
        app.sendMessage("general", "Bob", "Hi");
        app.sendMessage("general", "Alice", "Nice job on your presentations");
        app.sendMessage("general", "Cindy", "/givetaco @justin");
        app.sendMessage("general", "Bob", "/givetaco @justin 2");
        app.sendMessage("general", "Alice", "Bob let's meet");
        app.sendMessage("general", "Bob", "/meet Alice");
        app.sendMessage("general", "David", "/away out for lunch");
        app.sendMessage("general", "Emily", "Anyone around?");
        app.sendMessage("general", "Frank", "/meet David");
        
        // Message in dev
        app.sendMessage("dev", "Alice", "/givetaco @justin 2");
        
        System.out.println("=== General Channel ===");
        for (String m : app.getMessages("general")) {
            System.out.println(m);
        }
        
        System.out.println("\n=== Dev Channel ===");
        for (String m : app.getMessages("dev")) {
            System.out.println(m);
        }
    }
}
