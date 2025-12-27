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
import java.util.Arrays;
import java.util.HashMap;
import java.util.Map;
import java.util.TreeMap;
//import java.util.concurrent.TimeUnit;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.locks.Condition;
import java.util.concurrent.locks.ReadWriteLock;
import java.util.concurrent.locks.ReentrantReadWriteLock;

public class KVStore {

    private Map<String, TreeMap<Long, String>> store = new HashMap<>();
    private ReadWriteLock rwLock = new ReentrantReadWriteLock(true);
    private Condition condition = rwLock.readLock().newCondition();

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

    public void persistToFile(String filePath) throws IOException {
        rwLock.readLock().lock();
        try (BufferedWriter writer = new BufferedWriter(new FileWriter(filePath))) {
            for (Map.Entry<String, TreeMap<Long, String>> entry : store.entrySet()) {
                String key = entry.getKey();
                for (Map.Entry<Long, String> version : entry.getValue().entrySet()) {
                    long ts = version.getKey();
                    String val = version.getValue();
                    writer.write(encode(key) + "|" + ts + "|" + encode(val));
                    writer.newLine();
                }
            }
        } finally {
            rwLock.readLock().unlock();
        }
    }

    public void loadFromFile(String filePath) throws IOException {
        rwLock.writeLock().lock();
        try (BufferedReader reader = new BufferedReader(new FileReader(filePath))) {
            store.clear();
            String line;
            while ((line = reader.readLine()) != null) {
                String[] parts = line.split("\\|", 3);
                if (parts.length != 3) continue;
                String key = decode(parts[0]);
                long ts = Long.parseLong(parts[1]);
                String val = decode(parts[2]);
                store.computeIfAbsent(key, k -> new TreeMap<>()).put(ts, val);
            }
        } finally {
            rwLock.writeLock().unlock();
        }
    }


    // Encode: length + ':' + content (to allow any characters in key/value)
    private String encode(String s) {
        return s.length() + ":" + s;
    }

    private String decode(String s) {
        int colon = s.indexOf(':');
        if (colon == -1) throw new IllegalArgumentException("Invalid encoded string: " + s);
        int len = Integer.parseInt(s.substring(0, colon));
        return s.substring(colon + 1, colon + 1 + len);
    }

    public CompletableFuture<String> getFuture(String key, long futureTimestamp) {
        CompletableFuture<String> future = new CompletableFuture<>();
        long delay = futureTimestamp - System.currentTimeMillis();
        if (delay <= 0) {
            future.complete(get(key, futureTimestamp));
        } else {
//            scheduler.schedule(() -> {
//                future.complete(get(key, futureTimestamp));
//            }, delay, TimeUnit.MILLISECONDS);
        }
        return future;
    }

    public static void main(String[] args) throws IOException {
        String str = "11:hello|world|1690000000|5:abcde";
        System.out.println(Arrays.toString(str.split("\\|", 3)));

        KVStore store = new KVStore();

        store.set("user1", "hello\nthere", 10);
        store.set("user1", "world", 20);
        store.set("user2", "value2", 15);

        System.out.println(store.get("user1", 15)); // hello\nthere
        System.out.println(store.get("user1", 25)); // world
        System.out.println(store.get("user2", 10)); // null

        store.persistToFile("kvstore.txt");

        KVStore newStore = new KVStore();
        newStore.loadFromFile("kvstore.txt");

        System.out.println(newStore.get("user1", 25)); // world
    }


    // 正确的解析
//    public static ParsedRecord parseLine(String line) {
//        int idx = 0;
//
//        // 解析 key
//        ParsedString keyParsed = parseLengthString(line, idx);
//        String key = keyParsed.content;
//        idx = keyParsed.nextIndex;
//
//        // 解析 timestamp
//        int tsEnd = line.indexOf('|', idx);
//        if (tsEnd == -1) {
//            throw new IllegalArgumentException("Invalid line, missing timestamp delimiter");
//        }
//        long timestamp = Long.parseLong(line.substring(idx, tsEnd));
//        idx = tsEnd + 1;
//
//        // 解析 value
//        ParsedString valueParsed = parseLengthString(line, idx);
//        String value = valueParsed.content;
//
//        return new ParsedRecord(key, timestamp, value);
//    }
//
//    // helper
//    private static ParsedString parseLengthString(String s, int startIdx) {
//        int colon = s.indexOf(':', startIdx);
//        if (colon == -1) throw new IllegalArgumentException("Invalid encoded string at " + startIdx);
//        int len = Integer.parseInt(s.substring(startIdx, colon));
//        int contentStart = colon + 1;
//        int contentEnd = contentStart + len;
//        if (contentEnd > s.length()) throw new IllegalArgumentException("Encoded length exceeds string size");
//        String content = s.substring(contentStart, contentEnd);
//        return new ParsedString(content, contentEnd);
//    }
//
//    // 辅助类
//    private static class ParsedString {
//        String content;
//        int nextIndex;
//        ParsedString(String content, int nextIndex) {
//            this.content = content;
//            this.nextIndex = nextIndex;
//        }
//    }

}



// per key level lock
//private final ConcurrentHashMap<String, TreeMap<Long, String>> store = new ConcurrentHashMap<>();
//    private final ConcurrentHashMap<String, ReentrantReadWriteLock> keyLocks = new ConcurrentHashMap<>();
//
//    private ReentrantReadWriteLock getLockForKey(String key) {
//        return keyLocks.computeIfAbsent(key, k -> new ReentrantReadWriteLock());
//    }
//
//    public void set(String key, String value, long timestamp) {
//        ReentrantReadWriteLock lock = getLockForKey(key);
//        lock.writeLock().lock();
//        try {
//            store.computeIfAbsent(key, k -> new TreeMap<>()).put(timestamp, value);
//        } finally {
//            lock.writeLock().unlock();
//        }
//    }
//
//    public String get(String key, long timestamp) {
//        ReentrantReadWriteLock lock = getLockForKey(key);
//        lock.readLock().lock();
//        try {
//            TreeMap<Long, String> versions = store.get(key);
//            if (versions == null) return null;
//            Map.Entry<Long, String> entry = versions.floorEntry(timestamp);
//            return entry == null ? null : entry.getValue();
//        } finally {
//            lock.readLock().unlock();
//        }
//    }




/*

1. 文本方式写入无法区分边界
假设你用 BufferedWriter.write(key + "\n" + value + "\n")，如果 value 本身是 "hello\nworld"，那在读取时你无法知道中间的换行符到底是分隔符还是 value 的一部分。

2. 反序列化困难
你用 BufferedReader.readLine() 去读取，会把实际数据中的 \n 也误认为是记录的结束。这样 loadFromFile() 无法准确还原原始数据。

而 DataOutputStream 的优势是：
用 writeInt(len) + write(bytes) 可以精确记录每段字符串的长度。

读取时知道要读取多少字节，不会受到字符内容的影响。

public class KVStore {
    private final Map<String, TreeMap<Long, String>> store = new ConcurrentHashMap<>();
    private final ReadWriteLock rwLock = new ReentrantReadWriteLock();
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

    public CompletableFuture<String> getFuture(String key, long futureTimestamp) {
        CompletableFuture<String> future = new CompletableFuture<>();
        long delay = futureTimestamp - System.currentTimeMillis();
        if (delay <= 0) {
            future.complete(get(key, futureTimestamp));
        } else {
            scheduler.schedule(() -> {
                future.complete(get(key, futureTimestamp));
            }, delay, TimeUnit.MILLISECONDS);
        }
        return future;
    }

    public void persistToFile(String filePath) throws IOException {
        rwLock.readLock().lock();
        try (DataOutputStream dos = new DataOutputStream(new BufferedOutputStream(new FileOutputStream(filePath)))) {
            for (Map.Entry<String, TreeMap<Long, String>> entry : store.entrySet()) {
                String key = entry.getKey();
                for (Map.Entry<Long, String> version : entry.getValue().entrySet()) {
                    long ts = version.getKey();
                    String val = version.getValue();
                    writeEncoded(dos, key);
                    dos.writeLong(ts);
                    writeEncoded(dos, val);
                }
            }
        } finally {
            rwLock.readLock().unlock();
        }
    }

    public void loadFromFile(String filePath) throws IOException {
        rwLock.writeLock().lock();
        try (DataInputStream dis = new DataInputStream(new BufferedInputStream(new FileInputStream(filePath)))) {
            store.clear();
            while (dis.available() > 0) {
                String key = readEncoded(dis);
                long ts = dis.readLong();
                String val = readEncoded(dis);
                store.computeIfAbsent(key, k -> new TreeMap<>()).put(ts, val);
            }
        } finally {
            rwLock.writeLock().unlock();
        }
    }

    private void writeEncoded(DataOutputStream dos, String s) throws IOException {
        byte[] bytes = s.getBytes("UTF-8");
        dos.writeInt(bytes.length);
        dos.write(bytes);
    }

    private String readEncoded(DataInputStream dis) throws IOException {
        int length = dis.readInt();
        byte[] bytes = new byte[length];
        dis.readFully(bytes);
        return new String(bytes, "UTF-8");
    }

    public void shutdown() {
        scheduler.shutdown();
    }
}




维度	synchronized	ReentrantLock	ReadWriteLock
锁类型	内置互斥锁	显式互斥锁	读写分离锁
可重入	✅	✅	✅
公平锁	❌	✅（可选）	✅（可选）
可中断	❌	✅	✅
超时获取	❌	✅	✅
多条件队列	❌	✅（Condition）	✅
读并发	❌	❌	✅
写互斥	✅	✅	✅
性能（JDK 8+）	⭐⭐⭐⭐	⭐⭐⭐⭐	⭐⭐⭐⭐⭐（读多）
使用复杂度	⭐	⭐⭐⭐	⭐⭐⭐⭐
*/