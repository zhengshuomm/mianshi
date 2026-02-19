import java.util.*;

/**
 * Report Chain Manager
 * Manages employee reporting relationships and provides various queries
 */
public class ReportChain {
    private Map<String, String> manager;  // employee -> manager
    private Map<String, List<String>> reports;  // manager -> list of direct reports
    private Set<String> roots;  // root nodes (no manager)
    
    public ReportChain(String[] relationships) {
        manager = new HashMap<>();
        reports = new HashMap<>();
        roots = new HashSet<>();
        
        // Parse relationships: "A,B,C" means A manages B and C
        for (String rel : relationships) {
            String[] parts = rel.split(",");
            if (parts.length < 2) continue;
            
            String mgr = parts[0];
            roots.add(mgr);
            
            for (int i = 1; i < parts.length; i++) {
                String emp = parts[i].trim();
                manager.put(emp, mgr);
                roots.remove(emp);
                reports.computeIfAbsent(mgr, k -> new ArrayList<>()).add(emp);
            }
        }
    }
    
    // Part 1: Print tree structure
    public void printTree() {
        for (String root : roots) {
            printTree(root, 0);
        }
    }
    
    private void printTree(String node, int level) {
        for (int i = 0; i < level; i++) System.out.print("....");
        System.out.println(node);
        
        List<String> children = reports.getOrDefault(node, new ArrayList<>());
        Collections.sort(children);
        for (String child : children) {
            printTree(child, level + 1);
        }
    }
    
    // Part 2: Find skip meeting pairs using DFS
    public void printSkipMeetings() {
        Map<String, Map<String, Set<String>>> skipGroups = new TreeMap<>();
        
        // Use DFS to collect all skip meeting relationships
        for (String root : roots) {
            dfsSkipMeetings(root, new ArrayList<>(), skipGroups);
        }
        
        // Print grouped by (skipMgr, directMgr)
        for (String skipMgr : skipGroups.keySet()) {
            Map<String, Set<String>> directMgrs = skipGroups.get(skipMgr);
            for (String directMgr : directMgrs.keySet()) {
                Set<String> emps = directMgrs.get(directMgr);
                System.out.println(getIndent(getLevel(skipMgr)) + skipMgr);
                System.out.println(getIndent(getLevel(directMgr)) + directMgr + " SKIPPED");
                for (String emp : emps) {
                    System.out.println(getIndent(getLevel(emp)) + emp);
                }
                System.out.println();
            }
        }
    }
    
    private void dfsSkipMeetings(String node, List<String> path, Map<String, Map<String, Set<String>>> skipGroups) {
        path.add(node);
        
        // If path has at least 3 nodes (skipMgr -> directMgr -> emp), collect it
        if (path.size() >= 3) {
            String skipMgr = path.get(path.size() - 3);
            String directMgr = path.get(path.size() - 2);
            String emp = path.get(path.size() - 1);
            
            skipGroups.computeIfAbsent(skipMgr, k -> new TreeMap<>())
                      .computeIfAbsent(directMgr, k -> new TreeSet<>())
                      .add(emp);
        }
        
        // Continue DFS for all direct reports
        List<String> children = reports.getOrDefault(node, new ArrayList<>());
        Collections.sort(children);
        for (String child : children) {
            dfsSkipMeetings(child, new ArrayList<>(path), skipGroups);
        }
    }
    
    private int getLevel(String employee) {
        int level = 0;
        String current = manager.get(employee);
        while (current != null) {
            level++;
            current = manager.get(current);
        }
        return level;
    }
    
    private String getIndent(int level) {
        return "....".repeat(level);
    }
    
    public List<String[]> findSkipMeetings() {
        List<String[]> pairs = new ArrayList<>();
        for (String emp : manager.keySet()) {
            String directMgr = manager.get(emp);
            if (directMgr == null) continue;
            String skipMgr = manager.get(directMgr);
            if (skipMgr == null) continue;
            pairs.add(new String[]{skipMgr, emp});
        }
        return pairs;
    }
    
    // Part 3: Print single-line chain (all managers above + employee + all reports below)
    public void printChain(String employee) {
        // Get all managers above (from root to employee)
        List<String> managers = new ArrayList<>();
        String current = manager.get(employee);
        while (current != null) {
            managers.add(0, current);
            current = manager.get(current);
        }
        
        // Print managers above
        for (int i = 0; i < managers.size(); i++) {
            for (int j = 0; j < i; j++) System.out.print("....");
            System.out.println(managers.get(i));
        }
        
        // Print employee
        for (int i = 0; i < managers.size(); i++) System.out.print("....");
        System.out.println(employee);
        
        // Print reports below using level-order traversal (BFS)
        Queue<String> queue = new LinkedList<>();
        queue.offer(employee);
        int currentLevel = managers.size();
        
        while (!queue.isEmpty()) {
            int levelSize = queue.size();
            currentLevel++;
            
            for (int i = 0; i < levelSize; i++) {
                String currentEmp = queue.poll();
                List<String> directReports = reports.getOrDefault(currentEmp, new ArrayList<>());
                Collections.sort(directReports);
                
                for (String report : directReports) {
                    for (int j = 0; j < currentLevel; j++) System.out.print("....");
                    System.out.println(report);
                    queue.offer(report);
                }
            }
        }
    }
    
    // Part 4: Find Lowest Common Ancestor (LCA)
    public String findLCA(String emp1, String emp2) {
        Set<String> path1 = new HashSet<>();
        String current = emp1;
        while (current != null) {
            path1.add(current);
            current = manager.get(current);
        }
        
        current = emp2;
        while (current != null) {
            if (path1.contains(current)) {
                return current;
            }
            current = manager.get(current);
        }
        
        return null;
    }
    
}
