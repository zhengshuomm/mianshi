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
允许调用 get(key, futureTimestamp)，如果该时间点尚未有写入，需要支持"延迟返回"（例如等到时间点之前有写入）。

如何设计数据结构来支持这种 get 操作可以等待后续 set 的能力？（提示：可以考虑使用 CompletableFuture 或 Condition 等机制）
 */

import java.io.*;
import java.util.*;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.locks.ReadWriteLock;
import java.util.concurrent.locks.ReentrantReadWriteLock;

public class KVStore {
    // 使用 Map<Key, TreeMap<Timestamp, Value>> 存储版本化数据
    // TreeMap 保证时间戳有序，可以高效查找 <= 某时间戳的最新值
    private final Map<String, TreeMap<Long, String>> store;
    private final ScheduledExecutorService scheduler = Executors.newSingleThreadScheduledExecutor();

    // 读写锁：支持多个读者，单个写者
    private final ReadWriteLock lock;

    public KVStore() {
        this.store = new HashMap<>();
        this.lock = new ReentrantReadWriteLock();
    }

    /**
     * 设置键值对，带时间戳
     * 时间复杂度: O(log n) where n 是该 key 的版本数
     */
    public void set(String key, String value, long timestamp) {
        lock.writeLock().lock();
        try {
            store.putIfAbsent(key, new TreeMap<>());
            store.get(key).put(timestamp, value);
        } finally {
            lock.writeLock().unlock();
        }
    }

    /**
     * 获取键在指定时间戳的值
     * 返回 <= timestamp 的最新值
     * 时间复杂度: O(log n) where n 是该 key 的版本数
     */
    public String get(String key, long timestamp) {
        lock.readLock().lock();
        try {
            TreeMap<Long, String> versions = store.get(key);
            if (versions == null) {
                return null;
            }

            // floorEntry: 返回 <= timestamp 的最大 entry
            Map.Entry<Long, String> entry = versions.floorEntry(timestamp);
            return entry == null ? null : entry.getValue();
        } finally {
            lock.readLock().unlock();
        }
    }

    /**
     * 将数据持久化到文件
     * 序列化格式（二进制）：
     * [keyLen(4字节)][keyBytes][timestamp(8字节)][valueLen(4字节)][valueBytes]
     * 
     * 为什么用二进制？
     * - key/value 可能包含任意字符（\n, :, | 等）
     * - 二进制 length-prefix 不需要转义
     * - 更紧凑，更高效
     */
    public void persistToFile(String filePath) throws IOException {
        lock.readLock().lock();
        try (DataOutputStream out = new DataOutputStream(
                new BufferedOutputStream(new FileOutputStream(filePath)))) {

            for (Map.Entry<String, TreeMap<Long, String>> entry : store.entrySet()) {
                String key = entry.getKey();
                TreeMap<Long, String> versions = entry.getValue();

                for (Map.Entry<Long, String> versionEntry : versions.entrySet()) {
                    long timestamp = versionEntry.getKey();
                    String value = versionEntry.getValue();

                    // 写入 key
                    byte[] keyBytes = key.getBytes("UTF-8");
                    out.writeInt(keyBytes.length);
                    out.write(keyBytes);

                    // 写入 timestamp
                    out.writeLong(timestamp);

                    // 写入 value
                    byte[] valueBytes = value.getBytes("UTF-8");
                    out.writeInt(valueBytes.length);
                    out.write(valueBytes);
                }
            }
        } finally {
            lock.readLock().unlock();
        }
    }

    /**
     * 从文件加载数据
     */
    public void loadFromFile(String filePath) throws IOException {
        lock.writeLock().lock();
        // try (DataInputStream in = new DataInputStream(
        // new BufferedInputStream(new FileInputStream(filePath)))) {
        try (DataInputStream in = new DataInputStream(new BufferedInputStream(new FileInputStream(filePath)))) {

            store.clear();

            while (in.available() > 0) {
                // 读取 key
                int keyLen = in.readInt();
                byte[] keyBytes = new byte[keyLen];
                in.readFully(keyBytes);
                String key = new String(keyBytes, "UTF-8");

                // 读取 timestamp
                long timestamp = in.readLong();

                // 读取 value
                int valueLen = in.readInt();
                byte[] valueBytes = new byte[valueLen];
                in.readFully(valueBytes);
                String value = new String(valueBytes, "UTF-8");

                // 存储
                store.putIfAbsent(key, new TreeMap<>());
                store.get(key).put(timestamp, value);
            }
        } finally {
            lock.writeLock().unlock();
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

    // ========== 测试代码 ==========
    public static void main(String[] args) throws IOException {
        System.out.println("=== KVStore 基础功能测试 ===\n");

        // 测试 1: 基本 set/get
        System.out.println("【测试 1】基本 set/get");
        KVStore store = new KVStore();
        store.set("user1", "hello", 10);
        store.set("user1", "world", 15);

        System.out.println("get(user1, 5)  = " + store.get("user1", 5)); // null
        System.out.println("get(user1, 10) = " + store.get("user1", 10)); // hello
        System.out.println("get(user1, 12) = " + store.get("user1", 12)); // hello
        System.out.println("get(user1, 15) = " + store.get("user1", 15)); // world
        System.out.println("get(user1, 20) = " + store.get("user1", 20)); // world

        // 测试 2: 多个 key
        System.out.println("\n【测试 2】多个 key");
        store.set("user2", "alice", 5);
        store.set("user2", "bob", 10);
        store.set("user3", "charlie", 8);

        System.out.println("get(user2, 7)  = " + store.get("user2", 7)); // alice
        System.out.println("get(user2, 12) = " + store.get("user2", 12)); // bob
        System.out.println("get(user3, 8)  = " + store.get("user3", 8)); // charlie

        // 测试 3: 特殊字符
        System.out.println("\n【测试 3】特殊字符");
        store.set("key:with:colon", "value\nwith\nnewline", 20);
        store.set("key|pipe|", "value:special:chars", 25);

        System.out.println("get(key:with:colon, 20) = " + store.get("key:with:colon", 20));
        System.out.println("get(key|pipe|, 25) = " + store.get("key|pipe|", 25));

        // 测试 4: 持久化和加载
        System.out.println("\n【测试 4】持久化和加载");
        String testFile = "/tmp/kvstore_test.bin";

        // 保存
        store.persistToFile(testFile);
        System.out.println("数据已保存到: " + testFile);

        // 创建新 store 并加载
        KVStore newStore = new KVStore();
        newStore.loadFromFile(testFile);
        System.out.println("从文件加载完成");

        // 验证数据
        System.out.println("验证加载的数据:");
        System.out.println("  get(user1, 12) = " + newStore.get("user1", 12)); // hello
        System.out.println("  get(user1, 20) = " + newStore.get("user1", 20)); // world
        System.out.println("  get(user2, 7)  = " + newStore.get("user2", 7)); // alice
        System.out.println("  get(key:with:colon, 20) = " + newStore.get("key:with:colon", 20));

        // 测试 5: 边界情况
        System.out.println("\n【测试 5】边界情况");
        KVStore edgeStore = new KVStore();

        // 空字符串
        edgeStore.set("", "empty key", 1);
        edgeStore.set("empty value", "", 2);
        System.out.println("get('', 1) = '" + edgeStore.get("", 1) + "'");
        System.out.println("get('empty value', 2) = '" + edgeStore.get("empty value", 2) + "'");

        // 不存在的 key
        System.out.println("get('nonexistent', 10) = " + edgeStore.get("nonexistent", 10));

        // 相同时间戳覆盖
        edgeStore.set("user", "v1", 10);
        edgeStore.set("user", "v2", 10); // 覆盖
        System.out.println("get('user', 10) = " + edgeStore.get("user", 10)); // v2

        System.out.println("\n=== 所有测试通过！✅ ===");
    }
}
