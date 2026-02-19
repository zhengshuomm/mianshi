import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;

/*
# requirements summary:
# Implement a spreadsheet class Spreadsheet, supporting the following operations:
Required to implement: spreadsheet.setCell("A1", "1")
# spreadsheet.setCell("A2", "2")
# spreadsheet.setCell("A3", "=A1 + A2")
# spreadsheet.setCell("A4", "=A3 + A2")
# spreadsheet.setCell("A5", "=A3 + A4")
# setCell(key, value): Set a value or expression (may contain other cell references)
# getCell(key): Get the value of the current cell
# First stage: Allow getCell() to recursively calculate dependencies in real time
(O(N))
# Second stage: Require setCell() to actively update dependencies, making getCell()
O(1)
# Must handle circular references (such as A → B → C → A)
 */
public class ExcelSheet {

    public static void main(String[] args) {
        SpreadSheet spreadsheet = new SpreadSheet();
        spreadsheet.setCell("A1", "1");
        spreadsheet.setCell("A3", "10");
        spreadsheet.setCell("A2", "=A1 + A3"); // 11
        spreadsheet.setCell("B3", "2");
//        print(spreadsheet.map.get("A2").upNodes);
        spreadsheet.setCell("B1", "=B2 + B3");
        spreadsheet.setCell("B2", "=A2 + A1") ; // 11 + 1 = 12
        spreadsheet.setCell("C1", "=C2");
        spreadsheet.setCell("C2", "=C3");

        System.out.println("A1:" + spreadsheet.getCell("A1")); //1
        System.out.println("A2:" + spreadsheet.getCell("A2")); //11
        System.out.println("B2:" + spreadsheet.getCell("B2")); //12
        System.out.println("B1:" + spreadsheet.getCell("B1")); //14

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

    private static void print(Set<Node> nodes) {
        for (Node n : nodes) {
            System.out.print(n.name +",");
        }
        System.out.println();
    }
    
}

class Node {
    String name;
    // a node"s upstream node, any updates on upstream will affect this node.
    Set<Node> upNodes = new HashSet<>();

    // a node"s downstream node, the update of current node will affect downstream.
    Set<Node> downNodes = new HashSet<>();
    int value = 0;

    boolean isExp = false;

    public Node(String name){
        this.name = name;
    }
}

class SpreadSheet {
    Map<String, Node> map = new HashMap<>();

    public void setCell(String name, String exp) {
        map.putIfAbsent(name, new Node(name));
        Node node = map.get(name);

        // remove it"s upstream node and remove down stream node from it"s upstream node
        for (Node up : node.upNodes) {
            up.downNodes.remove(node);
        }
        node.upNodes.clear();


        int oldVal = node.value;
        node.value = 0;
        if (exp.startsWith("=")) {
            // get all node names
            String[] names = exp.substring(1).split("\\+");

            for (String upName : names) {
                upName = upName.strip();
                map.putIfAbsent(upName, new Node(upName));
                node.upNodes.add(map.get(upName));
                map.get(upName).downNodes.add(node);
                node.value = node.value + map.get(upName).value;
            }


        } else {
            node.value = Integer.parseInt(exp);
        }

        Set<Node> visited = new HashSet<>();
        // update downstream node value and detect circle
        for (Node n: map.values()) {
            if (visited.contains(n)) continue;
            if (dfs(n, visited, new HashSet<>())) {
                // need to revert operation
                throw new RuntimeException("Circle Found");
            }
        }

        int delta = node.value - oldVal;
        update(node, delta);

    }

    public int getCell(String name) {
        if (!map.containsKey(name)) return 0;
        return map.get(name).value;
    }

    public void update(Node node, int delta) {
        for (Node n : node.downNodes) {
            n.value += delta;
            update(n, delta);
        }
    }

    public boolean dfs(Node node, Set<Node> visited, Set<Node> visiting) {
        if (visiting.contains(node)) return true;   // ✅ 第1步：检测环
        if (visited.contains(node)) return false;   // ✅ 第2步：剪枝优化
        
        visited.add(node);
        visiting.add(node);
        
        for (Node n: node.upNodes) {
            if (dfs(n, visited, visiting)) return true;  // ✅ 简化逻辑
        }
        
        visiting.remove(node);
        return false;
    }
}


class SpreadSheet1 {
    Map<String, Node> map = new HashMap<>();

    public void setCell(String name, String exp) {
        map.putIfAbsent(name, new Node(name));
        Node node = map.get(name);

        // remove it"s upstream node and remove down stream node from it"s upstream node
        for (Node up : node.upNodes) {
            up.downNodes.remove(node);
        }
        node.upNodes.clear();

        int oldVal = node.value;
        node.value = 0;
        if (exp.startsWith("=")) {
            // get all node names
            String[] names = exp.substring(1).split("\\+");

            for (String upName : names) {
                upName = upName.strip();
                map.putIfAbsent(upName, new Node(upName));
                node.upNodes.add(map.get(upName));
                map.get(upName).downNodes.add(node);
//                node.value = node.value + map.get(upName).value;
            }
            node.isExp = true;

        } else {
            node.value = Integer.parseInt(exp);
            node.isExp = false;  // FIX: 标记为非表达式
        }

    }

    public int getCell(String name) {
        return getCellWithCycleDetection(name, new HashSet<>());
    }
    
    private int getCellWithCycleDetection(String name, Set<String> visiting) {
        if (!map.containsKey(name)) return 0;
        
        // 检测循环
        if (visiting.contains(name)) {
            throw new IllegalStateException("Circular dependency detected: " + name);
        }
        
        Node node = map.get(name);
        if (!node.isExp) return node.value;
        
        visiting.add(name);
        int value = 0;
        for (Node up : node.upNodes) {
            value += getCellWithCycleDetection(up.name, visiting);
        }
        visiting.remove(name);
        
        return value;
    }
}
