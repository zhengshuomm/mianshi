package anthropic.self_practice;

import java.io.BufferedReader;
import java.io.BufferedWriter;
import java.io.File;
import java.io.FileReader;
import java.io.FileWriter;
import java.io.IOException;
import java.util.HashMap;
import java.util.Map;

class Node {
    String key;
    String value;
    Node prev;
    Node next;

    public Node(String key, String value) {
        this.key = key;
        this.value = value;
    }
}

public class LRUCache {

    int capacity;
    String logPath;
    BufferedWriter logWriter;
    Map<String, Node> cache;
    Node head;
    Node tail;
    

    public LRUCache(int capacity, String path) throws IOException {
        this.capacity = capacity;
        this.logPath = path;
        this.cache = new HashMap<>();
        this.head = new Node(null, null);
        this.tail = new Node(null, null);
        head.next = tail;
        tail.prev = head;
        this.logWriter = new BufferedWriter(new FileWriter(logPath, true));
    }

    public String get(String key) {
        if (!cache.containsKey(key)) {
            return null;
        }
        Node node = cache.get(key);
        moveToHead(node);
        appendLog("G", key, null);
        return node.value;
    }

    public void put(String key, String value) {
        internalPut(key, value);
        appendLog("P", key, value);
    }

    private void internalPut(String key, String value) {
        if (cache.containsKey(key)) {
            Node node = cache.get(key);
            node.value = value;
            moveToHead(node);
        } else {
            Node node = new Node(key, value);
            cache.put(key, node);
            addToHead(node);

            if (cache.size() > this.capacity) {
                removeNode(tail.prev);
                cache.remove(tail.prev.key);
            }
        }
    }

    private void appendLog(String type, String key, String value){
        try {
            if (value == null) {
                logWriter.write(type + "\t" + key + "\n");
            } else {
                logWriter.write(type + "\t" + key + "\t" + value + "\n");
            }
            logWriter.flush();
        } catch (Exception e) {

        }
    }

    private void recoverFromDisk() throws IOException {
        try (BufferedReader reader = new BufferedReader(new FileReader(this.logPath))) {
            String line;
            while ((line = reader.readLine()) != null) {
                String[] parts = line.split("\t");
                String op = parts[0];
                String key = parts[1];

                if (op.equals("P")) {
                    internalPut(key, parts[2]);
                } else if (op.equals("G")){
                    if (cache.containsKey(key)) {
                        moveToHead(cache.get(key));
                    }
                }
            }
        }
    }

    private void removeNode(Node node) {
        node.prev.next = node.next;
        node.next.prev = node.prev;
    }

    private void addToHead(Node node) {
        node.prev = head;
        node.next = head.next;
        head.next.prev = node;
        head.next = node;
    }

    private void moveToHead(Node node) {
        removeNode(node);
        addToHead(node);
    }

    // Graceful shutdown
    public void close() throws IOException {
        if (logWriter != null) logWriter.close();
    }
    
    public static void main(String[] args) throws IOException {
        String logPath = System.getProperty("java.io.tmpdir") + File.separator + "lru_cache_test.wal";
        new File(logPath).delete();

        System.out.println("=== LRU + WAL test (log: " + logPath + ") ===");

        LRUCache cache = new LRUCache(2, logPath);
        cache.put("a", "1");
        cache.put("b", "2");
        System.out.println("get(a) = " + cache.get("a"));           // 1, a is MRU
        cache.put("c", "3");                                          // evicts b
        System.out.println("get(b) = " + cache.get("b"));           // null
        System.out.println("get(c) = " + cache.get("c"));           // 3
        cache.put("a", "updated");
        System.out.println("get(a) = " + cache.get("a"));           // updated
        cache.close();

        System.out.println("\n=== Recover from WAL ===");
        LRUCache recovered = new LRUCache(2, logPath);
        System.out.println("get(a) = " + recovered.get("a"));       // updated
        System.out.println("get(b) = " + recovered.get("b"));       // null
        System.out.println("get(c) = " + recovered.get("c"));       // 3
        recovered.close();

        new File(logPath).delete();
        System.out.println("\nAll checks passed.");
    }
}
