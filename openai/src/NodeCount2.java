import java.util.*;

// 消息基类
abstract class Message {
    String id = UUID.randomUUID().toString();
}

class CountMsg extends Message {}
class CountResponse extends Message {
    int count;
    CountResponse(int count) { this.count = count; }
}

class TopoMsg extends Message {}
class TopoResponse extends Message {
    Map<String, Object> topology;
    TopoResponse(Map<String, Object> topo) { this.topology = topo; }
}

// 节点类
class Node2 {
    int nodeId;
    Integer parentId;
    List<Integer> children = new ArrayList<>();
    Set<String> processed = new HashSet<>();
    
    // Count 状态
    int pendingCounts = 0;
    int expected = 0;
    int total = 1;
    
    // Topology 状态
    int pendingTopo = 0;
    int expectedTopo = 0;
    List<Map<String, Object>> childTopologies = new ArrayList<>();
    
    static Map<Integer, Node2> registry = new HashMap<>();
    
    public Node2(int nodeId, Integer parentId) {
        this.nodeId = nodeId;
        this.parentId = parentId;
        registry.put(nodeId, this);
    }
    
    public void addChild(Node2 child) {
        children.add(child.nodeId);
        child.parentId = this.nodeId;
    }
    
    public void sendMessage(int toId, Message msg) {
        registry.get(toId).receiveMessage(this.nodeId, msg);
    }
    
    public void receiveMessage(Integer fromId, Message msg) {
        if (processed.contains(msg.id)) return;
        processed.add(msg.id);
        
        if (msg instanceof CountMsg) {
            handleCount((CountMsg) msg);
        } else if (msg instanceof CountResponse) {
            handleCountResponse((CountResponse) msg);
        } else if (msg instanceof TopoMsg) {
            handleTopo((TopoMsg) msg);
        } else if (msg instanceof TopoResponse) {
            handleTopoResponse((TopoResponse) msg);
        }
    }
    
    private void handleCount(CountMsg msg) {
        if (children.isEmpty()) {
            if (parentId != null) {
                sendMessage(parentId, new CountResponse(1));
            } else {
                // Root 节点且没有孩子
                System.out.println("Total machines: 1");
            }
        } else {
            pendingCounts = 0;
            expected = children.size();
            total = 1;
            for (int childId : children) {
                CountMsg newMsg = new CountMsg();
                newMsg.id = msg.id;
                sendMessage(childId, newMsg);
            }
        }
    }
    
    private void handleCountResponse(CountResponse msg) {
        pendingCounts++;
        total += msg.count;
        
        if (pendingCounts == expected) {
            if (parentId == null) {
                System.out.println("Total machines: " + total);
            } else {
                sendMessage(parentId, new CountResponse(total));
            }
        }
    }
    
    private void handleTopo(TopoMsg msg) {
        if (children.isEmpty()) {
            if (parentId != null) {
                Map<String, Object> topo = new HashMap<>();
                topo.put("id", nodeId);
                topo.put("children", new ArrayList<>());
                sendMessage(parentId, new TopoResponse(topo));
            } else {
                // Root 节点且没有孩子
                Map<String, Object> topo = new HashMap<>();
                topo.put("id", nodeId);
                topo.put("children", new ArrayList<>());
                System.out.println("Topology: " + topo);
            }
        } else {
            pendingTopo = 0;
            expectedTopo = children.size();
            childTopologies = new ArrayList<>();
            for (int childId : children) {
                TopoMsg newMsg = new TopoMsg();
                newMsg.id = msg.id;
                sendMessage(childId, newMsg);
            }
        }
    }
    
    private void handleTopoResponse(TopoResponse msg) {
        pendingTopo++;
        childTopologies.add(msg.topology);
        
        if (pendingTopo == expectedTopo) {
            Map<String, Object> topo = new HashMap<>();
            topo.put("id", nodeId);
            topo.put("children", childTopologies);
            
            if (parentId == null) {
                System.out.println("Topology: " + topo);
            } else {
                sendMessage(parentId, new TopoResponse(topo));
            }
        }
    }
}

public class NodeCount2 {
    public static void main(String[] args) {
        // Build tree: 1 -> [2, 3], 2 -> [4]
        Node2 root = new Node2(1, null);
        Node2 a = new Node2(2, null);
        Node2 b = new Node2(3, null);
        Node2 c = new Node2(4, null);
        
        root.addChild(a);
        root.addChild(b);
        a.addChild(c);
        
        // Start count
        root.receiveMessage(null, new CountMsg());
        
        // Start topology
        root.receiveMessage(null, new TopoMsg());
    }
}
