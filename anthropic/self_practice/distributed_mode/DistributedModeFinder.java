package anthropic.self_practice.distributed_mode;

import java.util.*;
import java.util.concurrent.*;

public class DistributedModeFinder {

    // ---------------------------------------------------------
    // 1. 模拟分布式环境的通信基建 (Messaging Infrastructure)
    // ---------------------------------------------------------

    // 每个 worker 都有一个自己的信箱 (阻塞队列)，用于接收别的 worker 发来的数据
    private static final Map<Integer, BlockingQueue<Object>> mailboxes = new ConcurrentHashMap<>();
    
    // 初始化集群通信信箱
    public static void initCluster(int numWorkers) {
        mailboxes.clear();
        for (int i = 0; i < numWorkers; i++) {
            mailboxes.put(i, new LinkedBlockingQueue<>());
        }
    }

    // 题目要求的 send 接口
    private static void send(int targetWorkerId, Object data) {
        try {
            mailboxes.get(targetWorkerId).put(data);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }
    }

    // 题目要求的 recv 接口 (注意：这里需要知道是哪个 worker 在调用，所以在 Java 中我们传入 currentWorkerId)
    private static Object recv(int currentWorkerId) {
        try {
            return mailboxes.get(currentWorkerId).take();
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            return null;
        }
    }

    // 用于在 Phase 2 传输的键值对数据结构
    static class KeyCountPair {
        Integer key;
        Integer count;

        KeyCountPair(Integer key, Integer count) {
            this.key = key;
            this.count = count;
        }
    }

    // ---------------------------------------------------------
    // 2. 核心算法逻辑 (四个 Phase)
    // ---------------------------------------------------------

    public static Integer findModeDistributed(List<Integer> localData, int workerId, int numWorkers) {
        // Phase 1: Count Locally (Map 阶段)
        Map<Integer, Integer> localCounter = phase1LocalCount(localData);

        // Phase 2: Grouping by Key (Shuffle 阶段)
        List<KeyCountPair> receivedData = phase2ShuffleCounts(localCounter, workerId, numWorkers);

        // Phase 3: Find Local Winners (Reduce 阶段)
        KeyCountPair localTop1 = phase3AggregateLocal(receivedData);

        // Phase 4: Find the Global Winner (最终合并阶段)
        return phase4GlobalReduction(localTop1, workerId, numWorkers);
    }

    private static Map<Integer, Integer> phase1LocalCount(List<Integer> localData) {
        Map<Integer, Integer> counter = new HashMap<>();
        for (Integer val : localData) {
            counter.put(val, counter.getOrDefault(val, 0) + 1);
        }
        return counter;
    }

    private static List<KeyCountPair> phase2ShuffleCounts(Map<Integer, Integer> localCounter, int workerId, int numWorkers) {
        // 创建发送桶
        List<List<KeyCountPair>> buckets = new ArrayList<>(numWorkers);
        for (int i = 0; i < numWorkers; i++) {
            buckets.add(new ArrayList<>());
        }

        // 按取模哈希分发数据
        for (Map.Entry<Integer, Integer> entry : localCounter.entrySet()) {
            int key = entry.getKey();
            int count = entry.getValue();
            // 防止负数取模越界
            int targetWorker = Math.abs(key) % numWorkers; 
            buckets.get(targetWorker).add(new KeyCountPair(key, count));
        }

        // 发送数据给其他 worker
        for (int targetId = 0; targetId < numWorkers; targetId++) {
            if (targetId != workerId) {
                // 将对应 bucket 发送出去
                send(targetId, buckets.get(targetId));
            }
        }

        // 接收来自其他 worker 的数据
        List<KeyCountPair> receivedData = new ArrayList<>();
        // 期望接收 (numWorkers - 1) 个包
        for (int i = 0; i < numWorkers - 1; i++) {
            @SuppressWarnings("unchecked")
            List<KeyCountPair> data = (List<KeyCountPair>) recv(workerId);
            if (data != null) {
                receivedData.addAll(data);
            }
        }

        // 把留给自己的那一份数据也加进去
        receivedData.addAll(buckets.get(workerId));

        return receivedData;
    }

    private static KeyCountPair phase3AggregateLocal(List<KeyCountPair> receivedData) {
        Map<Integer, Integer> aggregated = new HashMap<>();
        for (KeyCountPair pair : receivedData) {
            aggregated.put(pair.key, aggregated.getOrDefault(pair.key, 0) + pair.count);
        }

        // 找出本节点频率最高的 Key
        KeyCountPair best = new KeyCountPair(null, 0);
        for (Map.Entry<Integer, Integer> entry : aggregated.entrySet()) {
            if (entry.getValue() > best.count || 
               (entry.getValue().equals(best.count) && best.key != null && entry.getKey() < best.key)) { // Tie-breaking: 取较小的key
                best.key = entry.getKey();
                best.count = entry.getValue();
            }
        }
        return best;
    }

    private static Integer phase4GlobalReduction(KeyCountPair localTop1, int workerId, int numWorkers) {
        if (workerId == 0) {
            KeyCountPair globalBest = new KeyCountPair(localTop1.key, localTop1.count);

            for (int i = 0; i < numWorkers - 1; i++) {
                KeyCountPair remoteTop1 = (KeyCountPair) recv(workerId);
                if (remoteTop1 != null && remoteTop1.count > 0) {
                    if (remoteTop1.count > globalBest.count || 
                       (remoteTop1.count.equals(globalBest.count) && globalBest.key != null && remoteTop1.key < globalBest.key)) {
                        globalBest.key = remoteTop1.key;
                        globalBest.count = remoteTop1.count;
                    }
                }
            }
            return globalBest.key;
        } else {
            send(0, localTop1);
            return null; // 非 0 节点不需要返回最终结果
        }
    }

    // ---------------------------------------------------------
    // 3. 测试与执行入口 (Main Method)
    // ---------------------------------------------------------

    public static void main(String[] args) throws InterruptedException, ExecutionException {
        int numWorkers = 3;
        initCluster(numWorkers);

        // 构造测试数据: 
        // 大部分数据只出现1次，数字 42 出现了3次，数字 99 出现了2次
        List<List<Integer>> distributedData = Arrays.asList(
            Arrays.asList(1, 2, 42, 99),           // Worker 0
            Arrays.asList(3, 4, 42, 100, 99),      // Worker 1
            Arrays.asList(5, 6, 42)                // Worker 2
        );

        ExecutorService executor = Executors.newFixedThreadPool(numWorkers);
        List<Future<Integer>> futures = new ArrayList<>();

        // 启动所有 Worker
        for (int i = 0; i < numWorkers; i++) {
            final int workerId = i;
            futures.add(executor.submit(() -> {
                return findModeDistributed(distributedData.get(workerId), workerId, numWorkers);
            }));
        }

        // 等待所有线程执行完毕
        for (int i = 0; i < numWorkers; i++) {
            Integer result = futures.get(i).get();
            if (i == 0) {
                System.out.println("Global Mode (Worker 0 reports): " + result); // 期望输出 42
            }
        }

        executor.shutdown();
    }
}
