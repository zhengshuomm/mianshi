/*
面试题：Time-based Versioned Key-Value Store with Serialization
📝 问题描述：
请实现一个类 KVStore，支持以下功能：

✅ 功能要求：
void set(String key, String value, long timestamp)
在指定时间点设置键值对。

String get(String key, long timestamp)
返回该 key 在给定时间点的 value。如果没有找到精确时间点的值，返回给定时间点前的最新值。

void persistToFile(String filePath)
将当前所有数据写入磁盘。
要求：

不能使用 Java 内建的对象序列化、JSON、XML 等库；

key 与 value 可能包含任意字符（包括 \n, : 等）；

需要自行设计序列化协议。

void loadFromFile(String filePath)
从文件中加载数据，恢复原始状态。

✨ 示例用法：
java
Copy
Edit
KVStore store = new KVStore();
store.set("user1", "hello", 10);
store.set("user1", "world", 15);

System.out.println(store.get("user1", 12)); // 输出: hello
System.out.println(store.get("user1", 20)); // 输出: world
🧩 Follow-up 问题：
1. 如何在多线程下保证更新一致性？
多线程同时调用 set 和 get，如何保证线程安全？

你会使用哪些锁机制？比如 synchronized, ReentrantLock, ReadWriteLock 等，解释理由与性能比较。

2. 如何 mock timestamp 写测试？
假设你要写一个 set(key, value)（不传 timestamp），自动使用系统时间，你会如何 mock 时间方便单元测试？

如何保证 set 时传入的 timestamp 始终严格递增（比如多线程写入）？

3. 如何处理未来时间点的 get？
允许调用 get(key, futureTimestamp)，如果该时间点尚未有写入，需要支持“延迟返回”（例如等到时间点之前有写入）。

如何设计数据结构来支持这种 get 操作可以等待后续 set 的能力？（提示：可以考虑使用 CompletableFuture 或 Condition 等机制）
 */


import java.io.*;
import java.util.HashMap;
import java.util.Map;
import java.util.TreeMap;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.locks.ReadWriteLock;
import java.util.concurrent.locks.ReentrantReadWriteLock;

public class KVStoreFinal {

    private Map<String, TreeMap<Long, String>> store = new HashMap<>();
    private ReadWriteLock rwLock = new ReentrantReadWriteLock(true);
    // Condition 已删除（代码中未使用）

    private final ScheduledExecutorService scheduler = Executors.newSingleThreadScheduledExecutor();


    public void set(String key, String value, long timestamp) {
        rwLock.writeLock().lock();
        try {
            store.computeIfAbsent(key, k -> new TreeMap<>()).put(timestamp, value);
        } finally {
             rwLock.writeLock().unlock();
        }
    }

    public String get(String key, long timestamp) {
        rwLock.readLock().lock();
        try {
            if (!store.containsKey(key)) return null;
            Map.Entry<Long, String> entry = store.get(key).floorEntry(timestamp);
            return entry == null ? null : entry.getValue();
        } finally {
            rwLock.readLock().unlock();
        }
    }

    /**
     * 使用二进制格式持久化到文件（支持任意字符，包括换行符）
     * 格式：[entryCount][key_length][key_bytes][timestamp][value_length][value_bytes]...
     */
    public void persistToFile(String filePath) throws IOException {
        rwLock.readLock().lock();
        try (DataOutputStream out = new DataOutputStream(
                new BufferedOutputStream(new FileOutputStream(filePath)))) {
            
            // 计算总条目数
            int totalEntries = 0;
            for (TreeMap<Long, String> versions : store.values()) {
                totalEntries += versions.size();
            }
            out.writeInt(totalEntries);
            
            // 写入每个条目
            for (Map.Entry<String, TreeMap<Long, String>> entry : store.entrySet()) {
                String key = entry.getKey();
                for (Map.Entry<Long, String> version : entry.getValue().entrySet()) {
                    long ts = version.getKey();
                    String val = version.getValue();
                    
                    // 写入 key（长度前缀 + UTF-8 字节）
                    byte[] keyBytes = key.getBytes("UTF-8");
                    out.writeInt(keyBytes.length);
                    out.write(keyBytes);
                    
                    // 写入 timestamp
                    out.writeLong(ts);
                    
                    // 写入 value（长度前缀 + UTF-8 字节）
                    byte[] valBytes = val.getBytes("UTF-8");
                    out.writeInt(valBytes.length);
                    out.write(valBytes);
                }
            }
        } finally {
            rwLock.readLock().unlock();
        }
    }

    /**
     * 从二进制文件加载数据
     */
    public void loadFromFile(String filePath) throws IOException {
        rwLock.writeLock().lock();
        try (DataInputStream in = new DataInputStream(
                new BufferedInputStream(new FileInputStream(filePath)))) {
            
            store.clear();
            int totalEntries = in.readInt();
            
            for (int i = 0; i < totalEntries; i++) {
                // 读取 key
                int keyLen = in.readInt();
                byte[] keyBytes = new byte[keyLen];
                in.readFully(keyBytes);
                String key = new String(keyBytes, "UTF-8");
                
                // 读取 timestamp
                long ts = in.readLong();
                
                // 读取 value
                int valLen = in.readInt();
                byte[] valBytes = new byte[valLen];
                in.readFully(valBytes);
                String val = new String(valBytes, "UTF-8");
                
                store.computeIfAbsent(key, k -> new TreeMap<>()).put(ts, val);
            }
        } finally {
            rwLock.writeLock().unlock();
        }
    }
    
    public CompletableFuture<String> getFuture(String key, long futureTimestamp) {
        long delay = futureTimestamp - System.currentTimeMillis();
        
        if (delay <= 0) {
            // ✅ 立即完成的 future（同步）
            return CompletableFuture.completedFuture(get(key, futureTimestamp));
        } else {
            // ✅ 延迟完成的 future（异步）
            CompletableFuture<String> future = new CompletableFuture<>();
            scheduler.schedule(() -> {
                try {
                    future.complete(get(key, futureTimestamp));
                } catch (Exception e) {
                    future.completeExceptionally(e);
                }
            }, delay, TimeUnit.MILLISECONDS);
            return future;
        }
    }

    public static void main(String[] args) throws IOException {
        KVStoreFinal store = new KVStoreFinal();

        store.set("user1", "hello\nthere", 10);
        store.set("user1", "world", 20);
        store.set("user2", "value2", 15);

        System.out.println(store.get("user1", 15)); // hello\nthere
        System.out.println(store.get("user1", 25)); // world
        System.out.println(store.get("user2", 10)); // null

        store.persistToFile("kvstore.txt");

        KVStoreFinal newStore = new KVStoreFinal();
        newStore.loadFromFile("kvstore.txt");

        System.out.println(newStore.get("user1", 25)); // world
    }

}
