
import java.util.*;

// import java.util.Map;

public class ExcelSheet {
    public static void main(String[] args) {
        SpreadSheet spreadsheet = new SpreadSheet();
        spreadsheet.setCell("A1", "1");
        spreadsheet.setCell("A3", "10");
        spreadsheet.setCell("A2", "=A1 + A3"); // 11
        spreadsheet.setCell("B3", "2");
        // print(spreadsheet.map.get("A2").upNodes);
        spreadsheet.setCell("B1", "=B2 + B3");
        spreadsheet.setCell("B2", "=A2 + A1"); // 11 + 1 = 12
        spreadsheet.setCell("C1", "=C2");
        spreadsheet.setCell("C2", "=C3");

        System.out.println("A1:" + spreadsheet.getCell("A1")); // 1
        System.out.println("A2:" + spreadsheet.getCell("A2")); // 11
        System.out.println("B2:" + spreadsheet.getCell("B2")); // 12
        System.out.println("B1:" + spreadsheet.getCell("B1")); // 14

        try {
            spreadsheet.setCell("C3", "=C1");
        } catch (Exception e) {
            e.printStackTrace(); // circle
        }

        try {
            spreadsheet.setCell("A1", "=B1");
        } catch (Exception e) {
            e.printStackTrace(); // circle
        }

    }
}

class Node {
    boolean isExp;
    int value;
    String exp;
    String name;

    List<Node> children;

    public Node(String name) {
        this.name = name;
        this.children = new ArrayList<>();
    }
}

class SpreadSheet {
    Map<String, Node> map = new HashMap<>();

    public void setCell(String name, String exp) {
        map.putIfAbsent(name, new Node(name));
        Node node = map.get(name);
        node.children.clear();
        if (exp.startsWith("=")) {
            node.isExp = true;
            String[] names = exp.substring(1).split("\\+");
            for (String n : names) {
                String trimmed = n.trim(); // FIX: 统一使用 trimmed
                map.putIfAbsent(trimmed, new Node(trimmed));
                node.children.add(map.get(trimmed));
            }
        } else {
            node.isExp = false;
            node.value = Integer.parseInt(exp);
        }

    }

    public int getCell(String name) {
        return getCellHelper(name, new HashSet<>());
    }

    private int getCellHelper(String name, HashSet<String> visiting) {
        if (!map.containsKey(name))
            return 0;
        if (visiting.contains(name)) {
            throw new IllegalStateException("Circular dependency detected: " + name);
        }

        Node node = map.get(name);
        if (!node.isExp)
            return node.value;

        visiting.add(name);
        int value = 0;
        for (Node n : node.children) {
            value += getCellHelper(n.name, visiting);
        }
        visiting.remove(name);
        return value;
    }

}

class Node2 {
    Set<Node2> upNodes = new HashSet<>();
    Set<Node2> downNodes = new HashSet<>();

    boolean isExp;
    int value;
    String name;

    public Node2(String name) {
        this.name = name;
    }
}

class SpreadSheet2 {
    Map<String, Node2> map = new HashMap<>();

    public void setCell(String name, String exp) {
        map.putIfAbsent(name, new Node2(name));
        Node2 node = map.get(name);
        for (Node2 up : node.upNodes) {
            up.downNodes.remove(node);
        }
        node.upNodes.clear();

        int oldValue = node.value;
        node.value = 0;
        if (exp.startsWith("=")) {
            node.isExp = true;
            String[] names = exp.substring(1).split("\\+");
            for (String n : names) {
                String trimmed = n.trim(); // FIX: 统一使用 trimmed
                map.putIfAbsent(trimmed, new Node2(trimmed));
                node.upNodes.add(map.get(trimmed));
                map.get(trimmed).downNodes.add(node);
                node.value += map.get(trimmed).value;
            }
        } else {
            node.isExp = false;
            node.value = Integer.parseInt(exp);
        }

        // detect cycle
        Set<String> visited = new HashSet<>();
        for (String n : map.keySet()) {
            if (visited.contains(n))
                continue;
            if (dfs(n, visited, new HashSet<>())) {
                throw new IllegalStateException("Circular dependency detected");
            }
        }

        int delta = node.value - oldValue;
        update(node, delta);

    }

    private void update(Node2 node, int delta) {
        for (Node2 n : node.downNodes) {
            n.value += delta;
            update(n, delta);
        }
    }

    private boolean dfs(String name, Set<String> visited, Set<String> visiting) {
        if (visiting.contains(name))
            return true;
        if (visited.contains(name))
            return false;

        if (!map.containsKey(name))  // FIX: 防御性检查
            return false;

        visited.add(name);
        visiting.add(name);

        for (Node2 n : map.get(name).upNodes) {
            if (dfs(n.name, visited, visiting)) {
                return true;
            }
        }
        visiting.remove(name);
        return false;
    }

    public int getCell(String name) {
        if (!map.containsKey(name))
            return 0;
        return map.get(name).value;
    }
}
