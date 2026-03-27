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

public class ChatBot2 {
    private Map<String, ChatContext> channelContext;
    List<Bot> bots = new ArrayList<>();
    
    public void createChannel(String channelName) {

    }

    public void sendMessage(String channelName, String user, String message) {
        ChatContext ctx = channelContext.get(channelName);
        ctx.addMessage(user + ": " + message);

        for (Bot bot : bots) {
            bot.handle(user, message, ctx);
        }
    }
}
