import java.util.*;

interface MessageHandler {
    void receiveMessage(String fromId, String message);
    void sendMessage(String toId, String message);
}

class MachineNode implements MessageHandler {

    String id;
    List<String> children;
    int total;
    int numberAlreadyCount;
    String parentId;
    Map<String, String> topoMap = new HashMap<>();

    public MachineNode(String id, String parentId, List<String> children) {
        this.id = id;
        this.parentId = parentId;
        this.children = children;
        this.total = 1;
    }

    @Override
    public void receiveMessage(String fromId, String message) {
        if (message.equals("startCount")) {  // ✅ 改为小写
            handleCountRequest();
        } else if (message.startsWith("COUNT:")) {
            handleCountResponse(Integer.parseInt(message.substring(6)));  // ✅ "COUNT:" 长度为 6
        } else if (message.equals("startTopo")) {  // ✅ 改为小写
            handleTopoRequest();
        } else { // TOPOLOGY:
            handleTopoResponse(message.substring(9), fromId);
        }
    }

    private void handleCountRequest() {
        if (children.isEmpty()) {
            if (parentId == null) {  // ✅ 直接判断 null
                System.out.println("Count is 1");
            } else {
                sendMessage(parentId, "COUNT:1");
            }
        } else {
            for (String child : children) {
                sendMessage(child, "startCount");  // ✅ 改为小写
            }
        }
    }

    private void handleCountResponse(int count) {
        numberAlreadyCount++;
        total += count;

        if (numberAlreadyCount == children.size()) {
            if (parentId == null) {  // ✅ 直接判断 null
                System.out.println("Count is " + total);
            } else {
                sendMessage(parentId, "COUNT:" + total);
            }
        }
    }

    private void handleTopoRequest() {
        if (children.isEmpty()) {
            if (parentId == null) {  // ✅ 直接判断 null
                System.out.println("Topo is " + id);
            } else {
                sendMessage(parentId, "TOPOLOGY:" + id);
            }
        } else {
            for (String child : children) {
                sendMessage(child, "startTopo");  // ✅ 改为小写
            }
        }
    }

    private void handleTopoResponse(String topo, String fromId) {
        topoMap.put(fromId, topo);

        if (topoMap.size() == children.size()) {
            String newTopo = buildTopo();
            if (parentId == null) {  // ✅ 直接判断 null
                System.out.println("Topo is " + newTopo);
            } else {
                sendMessage(parentId, "TOPOLOGY:" + newTopo);
            }
        }
    }

    private String buildTopo(){
        StringBuilder sb = new StringBuilder();
        if (children.isEmpty()) return id;
        sb.append(id);

        for (String topo : topoMap.values()) {
            sb.append("(").append(topo).append(")");
        }
        return sb.toString();
    }

    @Override
    public void sendMessage(String toId, String message) {
        MachineNode toNode = MachineNodeRegistry.getMachineNode(toId);
        toNode.receiveMessage(this.id, message);
    }
    
}

class MachineNodeRegistry {
    private static final Map<String, MachineNode> nodes = new HashMap<>();
    public static void register(MachineNode node) {
        nodes.put(node.id, node);
    }
    public static MachineNode getMachineNode(String id) {
        return nodes.get(id);
    }
}
public class NodeCount1 {

    public static void main(String[] args) {
        MachineNode a = new MachineNode("A", null, Arrays.asList("B", "C") );
        MachineNode b = new MachineNode("B", "A", Arrays.asList("D", "E"));
        MachineNode c = new MachineNode("C", "A", Collections.emptyList());
        MachineNode d = new MachineNode("D", "B", Collections.emptyList());
        MachineNode e = new MachineNode("E", "B", Collections.emptyList());

        MachineNodeRegistry.register(a);
        MachineNodeRegistry.register(b);
        MachineNodeRegistry.register(c);
        MachineNodeRegistry.register(d);
        MachineNodeRegistry.register(e);

        // Count nodes
        a.receiveMessage(null, "startCount");  // ✅ 改为小写

        // Print topology
        a.receiveMessage(null, "startTopo");  // ✅ 改为小写
    }
}
