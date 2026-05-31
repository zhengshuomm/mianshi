package anthropic.refined_java;

import java.io.*;
import java.util.*;
import java.util.concurrent.locks.ReentrantReadWriteLock;

public class LRUCache {
    
    // --- Memory Cache Structures ---
    private final int capacity;
    private final Map<String, Node> cache;
    private final Node head, tail;
    
    // Thread safety
    private final ReentrantReadWriteLock lock = new ReentrantReadWriteLock();
    
    // --- Persistence Structures ---
    private final String logFilePath;
    private BufferedWriter logWriter;

    // Doubly Linked List Node
    private static class Node {
        String key;
        String value;
        Node prev;
        Node next;
        public Node(String key, String value) {
            this.key = key;
            this.value = value;
        }
    }

    public LRUCache(int capacity, String logFilePath) throws IOException {
        this.capacity = capacity;
        this.cache = new HashMap<>();
        this.logFilePath = logFilePath;
        
        // Dummy head and tail to avoid null checks
        this.head = new Node(null, null);
        this.tail = new Node(null, null);
        head.next = tail;
        tail.prev = head;

        // 1. Recover state from disk
        recoverFromDisk();

        // 2. Open log file for appending new operations
        this.logWriter = new BufferedWriter(new FileWriter(logFilePath, true));
    }

    public String get(String key) {
        lock.writeLock().lock(); // Write lock because GET modifies LRU order
        try {
            if (!cache.containsKey(key)) {
                return null;
            }
            Node node = cache.get(key);
            moveToHead(node);
            
            // Log the GET to maintain perfect LRU recovery
            appendLog("G", key, null); 
            
            return node.value;
        } finally {
            lock.writeLock().unlock();
        }
    }

    public void put(String key, String value) {
        lock.writeLock().lock();
        try {
            if (cache.containsKey(key)) {
                Node node = cache.get(key);
                node.value = value;
                moveToHead(node);
            } else {
                Node newNode = new Node(key, value);
                cache.put(key, newNode);
                addToHead(newNode);

                if (cache.size() > capacity) {
                    Node tailNode = removeTail();
                    cache.remove(tailNode.key);
                }
            }
            // Log the PUT operation
            appendLog("P", key, value);
        } finally {
            lock.writeLock().unlock();
        }
    }

    // --- Persistence Methods ---
    private void appendLog(String op, String key, String value) {
        try {
            if (value == null) {
                logWriter.write(op + "\t" + key + "\n");
            } else {
                // In production, escape tabs/newlines in the value
                logWriter.write(op + "\t" + key + "\t" + value + "\n");
            }
            // Flush immediately for safety (Write-through). 
            // Interview note: To prioritize speed, we would remove this flush 
            // and have a background thread call logWriter.flush() every 100ms.
            logWriter.flush(); 
        } catch (IOException e) {
            System.err.println("Failed to write to WAL: " + e.getMessage());
        }
    }

    private void recoverFromDisk() throws IOException {
        File file = new File(logFilePath);
        if (!file.exists()) return;

        try (BufferedReader reader = new BufferedReader(new FileReader(file))) {
            String line;
            while ((line = reader.readLine()) != null) {
                String[] parts = line.split("\t");
                String op = parts[0];
                String key = parts[1];

                if (op.equals("P") && parts.length == 3) {
                    // Replay Put
                    internalPut(key, parts[2]);
                } else if (op.equals("G")) {
                    // Replay Get (updates LRU order)
                    if (cache.containsKey(key)) {
                        moveToHead(cache.get(key));
                    }
                }
            }
        }
    }

    // Helper method to bypass logging during recovery
    private void internalPut(String key, String value) {
        if (cache.containsKey(key)) {
            Node node = cache.get(key);
            node.value = value;
            moveToHead(node);
        } else {
            Node newNode = new Node(key, value);
            cache.put(key, newNode);
            addToHead(newNode);
            if (cache.size() > capacity) {
                Node tailNode = removeTail();
                cache.remove(tailNode.key);
            }
        }
    }

    // --- Standard LRU DLL Operations ---

    private void addToHead(Node node) {
        node.prev = head;
        node.next = head.next;
        head.next.prev = node;
        head.next = node;
    }

    private void removeNode(Node node) {
        node.prev.next = node.next;
        node.next.prev = node.prev;
    }

    private void moveToHead(Node node) {
        removeNode(node);
        addToHead(node);
    }

    private Node removeTail() {
        Node res = tail.prev;
        removeNode(res);
        return res;
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