import java.util.*;

interface Bot {
    void handle(String user, String message, ChatContext ctx);
}

class ChatContext {
    List<String> messages = new ArrayList<>();
    Map<String, String> awayStatus = new HashMap<>();
    Map<String, Integer> tacoCount = new HashMap<>();

    public void addMessage(String message) {
        messages.add(message);
    }
}

class AwayBot implements Bot {
    //  David: /away out for lunch
    //  AwayBot: David is away: out for lunch
    public void handle(String user, String message, ChatContext ctx) {
        if (!message.startsWith("/away ")) {
            return;
        }
        String reason = message.substring("/away ".length());
        ctx.awayStatus.put(user, reason);
        ctx.addMessage(String.format("%s is away: %s", user, reason));
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

class TacoBot implements Bot {
    // /givetaco @justin 2

    // @Bob gave @justin 2 tacos. @justin now has 3 tacos.
    public void handle(String user, String message, ChatContext ctx) {
        if (!message.startsWith("/givetaco ")) {
            return;
        }

        String[] lines = message.split(" ");
        String toUser = lines[1].substring(1);
        int count = Integer.parseInt(lines[2]);

        int totalCount = ctx.tacoCount.getOrDefault(toUser, 0) + count;
        ctx.tacoCount.put(toUser, totalCount);

        String tacoWord = count == 1 ? "taco" : "tacos";
        String totalTacoWord = totalCount == 1 ? "taco" : "tacos";

        ctx.addMessage(String.format("TacoBot: @%s gave @%s %d %s. @%s now has %s %s", user, toUser, count, tacoWord,
                toUser, totalCount, totalTacoWord));
    }

}

public class ChatBot {

    private Map<String, ChatContext> chanelContext;
    private List<Bot> bots = new ArrayList<>();

    public ChatBot() {
        this.chanelContext = new HashMap<>();
        this.bots = Arrays.asList(new AwayBot(), new MeetBot(), new TacoBot());
    }

    public void createChannel(String channelName) {
        this.chanelContext.put(channelName, new ChatContext());
    }

    public void sendMessage(String channelName, String user, String message) {
        ChatContext ctx = chanelContext.get(channelName);
        ctx.addMessage(user + ": " + message);

        for (Bot bot : bots) {
            bot.handle(user, message, ctx);
        }
    }

    public List<String> getMessages(String channelName) {
        return chanelContext.get(channelName).messages;
    }

    public static void main(String[] args) {
        ChatBot app = new ChatBot();

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

        /*
         * === General Channel ===
         * Alice: Hello
         * Bob: Hi
         * Alice: Nice job on your presentations
         * Cindy: /givetaco @justin
         * TacoBot: @Cindy gave @justin 1 taco. @justin now has 1 taco.
         * Bob: /givetaco @justin 2
         * TacoBot: @Bob gave @justin 2 tacos. @justin now has 3 tacos.
         * Alice: Bob let's meet
         * Bob: /meet Alice
         * MeetBot: Google Meet with @Bob, and Alice starting at /abc-def-123
         * David: /away out for lunch
         * AwayBot: David is away: out for lunch
         * Emily: Anyone around?
         * Frank: /meet David
         * AwayBot: David is away: out for lunch
         * MeetBot: Google Meet with @Frank, and David starting at /abc-def-123
         * 
         * === Dev Channel ===
         * Alice: /givetaco @justin 2
         * TacoBot: @Alice gave @justin 2 tacos. @justin now has 2 tacos.
         */
    }

}
