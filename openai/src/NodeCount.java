import java.util.*;

interface MessageHandler {
    void receiveMessage(String fromId, String message);
    void sendMessage(String toId, String message);
}

class MachineNode implements MessageHandler {
    public final String id;
    private final String parentId;
    private final List<String> children;

    // State for count
    private final Set<String> receivedFrom = new HashSet<>();
    private final Map<String, Integer> childCounts = new HashMap<>();
    private boolean countStarted = false;

    // State for topology
    private final Map<String, List<String>> subTree = new HashMap<>();
    private final Map<String, Map<String, List<String>>> childTopoResponses = new HashMap<>();

    public MachineNode(String id, String parentId, List<String> children) {
        this.id = id;
        this.parentId = parentId;
        this.children = children;
    }

    @Override
    public void receiveMessage(String fromId, String message) {
        if (message.equals("StartCount")) {
            handleCount();
        } else if (message.startsWith("Count")) {
            int count = Integer.parseInt(message.split(":" )[1]);
            handleCountResponse(fromId, count);
        }  else if (message.equals("StartTopo")) {
            handleTopo();
        }  else if (message.startsWith("Topo")) {
            Map<String, List<String>> topo = parseTopoMessage(message);
            handleTopoResponse(fromId, topo);
        }
    }

    private void handleTopoResponse(String fromId, Map<String, List<String>> topo) {
        if (childTopoResponses.containsKey(fromId)) return;
        childTopoResponses.put(fromId, topo);
        if (childTopoResponses.size() == children.size()) {
            Map<String, List<String>> merged = new HashMap<>();
            for (Map<String, List<String>> part : childTopoResponses.values()) {
                merged.putAll(part);
            }
            merged.put(id, children);
            if (parentId == null) {
                System.out.println("Topology: " + merged);
            } else {
                sendMessage(parentId, serializeTopo(merged));
            }
        }
    }

    private Map<String, List<String>> parseTopoMessage(String message) {
        String body = message.substring(5); // after "Topo:"
        Map<String, List<String>> result = new HashMap<>();
        System.out.println(message);
//        Topo:{D=[]}
//        Topo:{E=[]}
//        Topo:{D=[], E=[], B=[D, E]}
//        Topo:{C=[]}
        // Dummy simple parser (not production safe)
        body = body.replace("{", "").replace("}", "");
//        String[] entries = body.split(", ");
        int index = body.indexOf("]", 0);
        int prev = 0;
        while (index > -1) {
            String entry = body.substring(prev, index + 1);
            String[] kv = entry.split("=");
            String key = kv[0];
            String[] vals = kv[1].replace("[", "").replace("]", "").split(",");
            List<String> list = new ArrayList<>();
            for (String v : vals) if (!v.isEmpty()) list.add(v.trim());
            result.put(key, list);
            prev = index + 2;
            index = body.indexOf("]", prev);
        }
        return result;
    }

    private void handleTopo() {
        Map<String, List<String>> map = new HashMap<>();

        map.put(id, new ArrayList<>(children));
        if (children.isEmpty()) {
            sendMessage(parentId, serializeTopo(map));
        } else {
            for (String child : children) {
                sendMessage(child, "StartTopo");
            }
//            childTopoResponses.put(id, map); // self init
        }
    }

    private String serializeTopo(Map<String, List<String>> map) {
        return "Topo:" + map.toString();
    }

    private void handleCountResponse(String fromId, int count) {
        if (childCounts.containsKey(fromId)) return; // avoid duplicate
        childCounts.put(fromId, count);
        if (childCounts.size() == children.size()) {
            int total = 1 + childCounts.values().stream().mapToInt(Integer::intValue).sum();
            if (parentId == null) {
                System.out.println("Total nodes in tree " + total);
            } else {
                sendMessage(parentId, "Count:" + total);
            }
        }
    }

    private void handleCount() {
        // the current node already receive the StartCount command
        if (countStarted) return; //idempotent
        countStarted = true;
        if (this.children.isEmpty()) {
            sendMessage(parentId, "Count:1");
        } else {
            for (String child: children) {
                sendMessage(child, "StartCount");
            }
        }
    }

    @Override
    public void sendMessage(String toId, String message) {
        MachineNode to = MachineNodeRegistry.getMachineNode(toId);
        to.receiveMessage(id, message);
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

class TreeSimulation {
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
        a.receiveMessage(null, "StartCount");

        // Print topology
        a.receiveMessage(null, "StartTopo");
    }
}
