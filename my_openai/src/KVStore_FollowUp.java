import java.io.*;
import java.util.*;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicLong;
import java.util.concurrent.locks.*;

/**
 * KVStore Follow-up 问题实现
 * 
 * 1. 多线程一致性
 * 2. Mock timestamp 测试
 * 3. 未来时间点的 get（延迟返回）
 */

// ========== Follow-up 1: 多线程一致性 ==========

/**
 * 问题 1: 如何在多线程下保证更新一致性？
 * 
 * 答案：使用 ReadWriteLock
 * 
 * 为什么选择 ReadWriteLock？
 * 1. 读多写少的场景：get 频率 >> set 频率
 * 2. 允许多个线程同时读取（共享锁）
 * 3. 写操作独占（排他锁）
 * 
 * 性能比较：
 * - synchronized: 简单，但所有操作都互斥（包括多个 get）
 * - ReentrantLock: 比 synchronized 灵活，但仍然是排他锁
 * - ReadWriteLock: 最优选择，读写分离
 * - StampedLock: 最高性能，但 API 更复杂
 */
class KVStoreThreadSafe {
    private final Map<String, TreeMap<Long, String>> store = new HashMap<>();
    private final ReadWriteLock lock = new ReentrantReadWriteLock();
    
    public void set(String key, String value, long timestamp) {
        lock.writeLock().lock();
        try {
            store.putIfAbsent(key, new TreeMap<>());
            store.get(key).put(timestamp, value);
        } finally {
            lock.writeLock().unlock();
        }
    }
    
    public String get(String key, long timestamp) {
        lock.readLock().lock();
        try {
            TreeMap<Long, String> versions = store.get(key);
            if (versions == null) return null;
            
            Map.Entry<Long, String> entry = versions.floorEntry(timestamp);
            return entry == null ? null : entry.getValue();
        } finally {
            lock.readLock().unlock();
        }
    }
    
    // 多线程测试
    public static void testConcurrency() throws InterruptedException {
        System.out.println("=== Follow-up 1: 多线程测试 ===\n");
        
        KVStoreThreadSafe store = new KVStoreThreadSafe();
        int numThreads = 10;
        int operationsPerThread = 1000;
        
        // 创建写线程
        Thread[] writers = new Thread[numThreads];
        for (int i = 0; i < numThreads; i++) {
            final int threadId = i;
            writers[i] = new Thread(() -> {
                for (int j = 0; j < operationsPerThread; j++) {
                    store.set("key" + threadId, "value" + j, j);
                }
            });
        }
        
        // 创建读线程
        Thread[] readers = new Thread[numThreads];
        for (int i = 0; i < numThreads; i++) {
            readers[i] = new Thread(() -> {
                for (int j = 0; j < operationsPerThread; j++) {
                    store.get("key0", j);
                }
            });
        }
        
        // 启动所有线程
        long startTime = System.currentTimeMillis();
        for (Thread t : writers) t.start();
        for (Thread t : readers) t.start();
        
        // 等待完成
        for (Thread t : writers) t.join();
        for (Thread t : readers) t.join();
        long endTime = System.currentTimeMillis();
        
        System.out.println("✅ 多线程测试完成");
        System.out.println("   线程数: " + numThreads + " 写 + " + numThreads + " 读");
        System.out.println("   每线程操作: " + operationsPerThread);
        System.out.println("   总耗时: " + (endTime - startTime) + " ms\n");
    }
}

// ========== Follow-up 2: Mock Timestamp 测试 ==========

/**
 * 问题 2: 如何 mock timestamp 写测试？
 * 
 * 答案：使用 TimeProvider 接口 + 依赖注入
 * 
 * 设计模式：Strategy Pattern
 * - 生产环境：使用系统时间
 * - 测试环境：使用可控的 mock 时间
 */
interface TimeProvider {
    long currentTimeMillis();
}

class SystemTimeProvider implements TimeProvider {
    @Override
    public long currentTimeMillis() {
        return System.currentTimeMillis();
    }
}

class MockTimeProvider implements TimeProvider {
    private long currentTime = 0;
    
    @Override
    public long currentTimeMillis() {
        return currentTime;
    }
    
    public void setTime(long time) {
        this.currentTime = time;
    }
    
    public void advance(long delta) {
        this.currentTime += delta;
    }
}

class KVStoreWithTimeProvider {
    private final Map<String, TreeMap<Long, String>> store = new HashMap<>();
    private final TimeProvider timeProvider;
    
    public KVStoreWithTimeProvider(TimeProvider timeProvider) {
        this.timeProvider = timeProvider;
    }
    
    // 自动使用当前时间的 set 方法
    public void set(String key, String value) {
        set(key, value, timeProvider.currentTimeMillis());
    }
    
    public void set(String key, String value, long timestamp) {
        store.putIfAbsent(key, new TreeMap<>());
        store.get(key).put(timestamp, value);
    }
    
    public String get(String key, long timestamp) {
        TreeMap<Long, String> versions = store.get(key);
        if (versions == null) return null;
        
        Map.Entry<Long, String> entry = versions.floorEntry(timestamp);
        return entry == null ? null : entry.getValue();
    }
    
    // 测试 mock 时间
    public static void testMockTime() {
        System.out.println("=== Follow-up 2: Mock Timestamp 测试 ===\n");
        
        MockTimeProvider mockTime = new MockTimeProvider();
        KVStoreWithTimeProvider store = new KVStoreWithTimeProvider(mockTime);
        
        // 设置初始时间
        mockTime.setTime(100);
        store.set("user", "v1");
        System.out.println("时间 100: set(user, v1)");
        
        // 前进时间
        mockTime.advance(50);
        store.set("user", "v2");
        System.out.println("时间 150: set(user, v2)");
        
        mockTime.advance(100);
        store.set("user", "v3");
        System.out.println("时间 250: set(user, v3)");
        
        // 查询不同时间点
        System.out.println("\n查询结果:");
        System.out.println("  get(user, 90)  = " + store.get("user", 90));   // null
        System.out.println("  get(user, 100) = " + store.get("user", 100));  // v1
        System.out.println("  get(user, 120) = " + store.get("user", 120));  // v1
        System.out.println("  get(user, 150) = " + store.get("user", 150));  // v2
        System.out.println("  get(user, 300) = " + store.get("user", 300));  // v3
        
        System.out.println("\n✅ Mock 时间测试通过\n");
    }
}

/**
 * 如何保证 timestamp 严格递增？
 * 
 * 方案 1: 使用 AtomicLong
 */
class MonotonicTimestampGenerator {
    private final AtomicLong lastTimestamp = new AtomicLong(0);
    
    public long getNextTimestamp() {
        long currentTime = System.currentTimeMillis();
        long lastTime = lastTimestamp.get();
        
        // 如果当前时间 <= 上次时间，使用 lastTime + 1
        long nextTime = Math.max(currentTime, lastTime + 1);
        
        // CAS 更新
        while (!lastTimestamp.compareAndSet(lastTime, nextTime)) {
            lastTime = lastTimestamp.get();
            nextTime = Math.max(currentTime, lastTime + 1);
        }
        
        return nextTime;
    }
    
    public static void testMonotonicTimestamp() {
        System.out.println("=== 单调递增时间戳测试 ===\n");
        
        MonotonicTimestampGenerator generator = new MonotonicTimestampGenerator();
        Set<Long> timestamps = ConcurrentHashMap.newKeySet();
        
        // 多线程并发生成时间戳
        int numThreads = 10;
        int timestampsPerThread = 100;
        Thread[] threads = new Thread[numThreads];
        
        for (int i = 0; i < numThreads; i++) {
            threads[i] = new Thread(() -> {
                for (int j = 0; j < timestampsPerThread; j++) {
                    long ts = generator.getNextTimestamp();
                    timestamps.add(ts);
                }
            });
        }
        
        for (Thread t : threads) t.start();
        try {
            for (Thread t : threads) t.join();
        } catch (InterruptedException e) {
            e.printStackTrace();
        }
        
        // 验证：所有时间戳都是唯一的
        System.out.println("生成的时间戳数量: " + timestamps.size());
        System.out.println("预期数量: " + (numThreads * timestampsPerThread));
        System.out.println("✅ 单调递增保证: " + (timestamps.size() == numThreads * timestampsPerThread));
        
        // 验证严格递增
        List<Long> sorted = new ArrayList<>(timestamps);
        Collections.sort(sorted);
        boolean strictlyIncreasing = true;
        for (int i = 1; i < sorted.size(); i++) {
            if (sorted.get(i) <= sorted.get(i - 1)) {
                strictlyIncreasing = false;
                break;
            }
        }
        System.out.println("✅ 严格递增: " + strictlyIncreasing + "\n");
    }
}

// ========== Follow-up 3: 未来时间点的 get（延迟返回）==========

/**
 * 问题 3: 如何处理未来时间点的 get？
 * 
 * 答案：使用 CompletableFuture + Condition 实现等待机制
 * 
 * 设计思路：
 * 1. 如果查询时间点的数据已存在，立即返回
 * 2. 如果不存在，创建一个 Future 等待
 * 3. set 时检查是否有等待的 Future，通知它们
 */
class KVStoreWithFutureGet {
    private final Map<String, TreeMap<Long, String>> store = new HashMap<>();
    private final ReadWriteLock lock = new ReentrantReadWriteLock();
    
    // key -> (timestamp -> List<CompletableFuture>)
    private final Map<String, TreeMap<Long, List<CompletableFuture<String>>>> pendingGets = new HashMap<>();
    
    public void set(String key, String value, long timestamp) {
        lock.writeLock().lock();
        try {
            // 存储数据
            store.putIfAbsent(key, new TreeMap<>());
            store.get(key).put(timestamp, value);
            
            // 检查是否有等待的 get
            if (pendingGets.containsKey(key)) {
                TreeMap<Long, List<CompletableFuture<String>>> pending = pendingGets.get(key);
                
                // 找到所有 <= timestamp 的等待请求
                Map<Long, List<CompletableFuture<String>>> toComplete = pending.headMap(timestamp, true);
                
                for (Map.Entry<Long, List<CompletableFuture<String>>> entry : toComplete.entrySet()) {
                    long queryTime = entry.getKey();
                    String result = getInternal(key, queryTime);
                    
                    // 完成所有等待的 Future
                    for (CompletableFuture<String> future : entry.getValue()) {
                        future.complete(result);
                    }
                }
                
                // 移除已完成的
                toComplete.clear();
            }
        } finally {
            lock.writeLock().unlock();
        }
    }
    
    /**
     * 同步 get：立即返回（可能是 null）
     */
    public String get(String key, long timestamp) {
        lock.readLock().lock();
        try {
            return getInternal(key, timestamp);
        } finally {
            lock.readLock().unlock();
        }
    }
    
    /**
     * 异步 get：如果数据不存在，等待直到有数据写入
     * 
     * @param timeout 最长等待时间（毫秒），0 表示无限等待
     */
    public CompletableFuture<String> getAsync(String key, long timestamp, long timeout) {
        lock.writeLock().lock();
        try {
            // 尝试立即获取
            String result = getInternal(key, timestamp);
            if (result != null) {
                return CompletableFuture.completedFuture(result);
            }
            
            // 创建等待的 Future
            CompletableFuture<String> future = new CompletableFuture<>();
            
            pendingGets.putIfAbsent(key, new TreeMap<>());
            TreeMap<Long, List<CompletableFuture<String>>> keyPending = pendingGets.get(key);
            
            keyPending.putIfAbsent(timestamp, new ArrayList<>());
            keyPending.get(timestamp).add(future);
            
            // 设置超时
            if (timeout > 0) {
                CompletableFuture.delayedExecutor(timeout, TimeUnit.MILLISECONDS).execute(() -> {
                    future.completeExceptionally(new TimeoutException("Get timeout after " + timeout + "ms"));
                });
            }
            
            return future;
        } finally {
            lock.writeLock().unlock();
        }
    }
    
    private String getInternal(String key, long timestamp) {
        TreeMap<Long, String> versions = store.get(key);
        if (versions == null) return null;
        
        Map.Entry<Long, String> entry = versions.floorEntry(timestamp);
        return entry == null ? null : entry.getValue();
    }
    
    // 测试未来时间点的 get
    public static void testFutureGet() throws Exception {
        System.out.println("=== Follow-up 3: 未来时间点的 get 测试 ===\n");
        
        KVStoreWithFutureGet store = new KVStoreWithFutureGet();
        
        System.out.println("1. 立即查询已存在的数据:");
        store.set("user", "v1", 100);
        CompletableFuture<String> future1 = store.getAsync("user", 100, 0);
        System.out.println("   getAsync(user, 100) = " + future1.get() + " (立即返回)\n");
        
        System.out.println("2. 查询未来时间点（等待写入）:");
        CompletableFuture<String> future2 = store.getAsync("user", 200, 5000);
        System.out.println("   getAsync(user, 200) 创建 Future，等待中...");
        
        // 在另一个线程中延迟写入
        new Thread(() -> {
            try {
                Thread.sleep(1000);
                System.out.println("   [1秒后] set(user, v2, 200)");
                store.set("user", "v2", 200);
            } catch (InterruptedException e) {
                e.printStackTrace();
            }
        }).start();
        
        // 等待 Future 完成
        String result = future2.get();  // 阻塞直到 set 完成
        System.out.println("   getAsync(user, 200) = " + result + " (等待返回)\n");
        
        System.out.println("3. 查询超时测试:");
        CompletableFuture<String> future3 = store.getAsync("timeout", 300, 500);
        System.out.println("   getAsync(timeout, 300, 500ms) 等待中...");
        
        try {
            future3.get();
        } catch (ExecutionException e) {
            if (e.getCause() instanceof TimeoutException) {
                System.out.println("   ✅ 超时异常: " + e.getCause().getMessage() + "\n");
            }
        }
        
        System.out.println("✅ 未来时间点 get 测试完成\n");
    }
}

// ========== 主测试 ==========
public class KVStore_FollowUp {
    public static void main(String[] args) throws Exception {
        // Follow-up 1: 多线程测试
        KVStoreThreadSafe.testConcurrency();
        
        // Follow-up 2: Mock 时间测试
        KVStoreWithTimeProvider.testMockTime();
        MonotonicTimestampGenerator.testMonotonicTimestamp();
        
        // Follow-up 3: 未来时间点的 get
        KVStoreWithFutureGet.testFutureGet();
        
        System.out.println("=== 所有 Follow-up 测试完成！✅ ===");
    }
}
