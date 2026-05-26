package anthropic.refined_java;

import java.util.*;

public class DistributedModeSimple {

    // 1. 基础数据结构：复用于 Shuffle 传输和 Top 结果返回
    static class Pair {
        int key, count;
        Pair(int key, int count) {
            this.key = key;
            this.count = count;
        }
    }

    // 优化：引入泛型，消除编译器 Unchecked cast 警告
    static class Message<T> {
        T data;
        Message(T data) { this.data = data; }
    }

    // 2. Map 阶段：本地局部聚合 (Combiner 压缩数据)
    static Map<Integer, Integer> countLocal(List<Integer> localData) {
        Map<Integer, Integer> counter = new HashMap<>();
        for (int num : localData) {
            counter.put(num, counter.getOrDefault(num, 0) + 1);
        }
        return counter;
    }

    // 3. 路由拆分：按 Hash 分桶准备发送
    static List<List<Pair>> partitionByWorker(Map<Integer, Integer> localCounter, int numWorkers) {
        List<List<Pair>> buckets = new ArrayList<>();
        for (int i = 0; i < numWorkers; i++) buckets.add(new ArrayList<>());
        
        for (Map.Entry<Integer, Integer> e : localCounter.entrySet()) {
            // 亮点：使用 floorMod 防止负数 Hash 导致数组越界
            int workerIndex = Math.floorMod(e.getKey(), numWorkers);
            buckets.get(workerIndex).add(new Pair(e.getKey(), e.getValue()));
        }
        return buckets;
    }

    // 4. Worker 节点定义
    static class Worker {
        int id, workerNum;
        List<Integer> localData;
        Map<Integer, Integer> finalCounter = new HashMap<>();
        List<Pair> ownPairs = new ArrayList<>(); // 留给自己的数据
        
        // 优化：明确泛型类型，避免 Object 强转
        Queue<Message<List<Pair>>> inbox = new LinkedList<>();
        Cluster cluster;

        Worker(int id, int workerNum, List<Integer> localData, Cluster cluster) {
            this.id = id;
            this.workerNum = workerNum;
            this.localData = localData;
            this.cluster = cluster;
        }

        void receive(Message<List<Pair>> msg) { 
            inbox.offer(msg); 
        }

        // --- Shuffle Write (洗牌发送) ---
        void sendShuffle() {
            List<List<Pair>> buckets = partitionByWorker(countLocal(localData), workerNum);
            ownPairs = buckets.get(id); 
            
            for (int t = 0; t < workerNum; t++) {
                if (t != id) {
                    cluster.send(t, buckets.get(t));
                }
            }
        }

        // --- Shuffle Read & Reduce (聚合接收) ---
        void recvAggregate() {
            List<Pair> allPairs = new ArrayList<>(ownPairs);
            
            // 优化：动态处理收件箱，防止网络延迟或单线程模拟时的 NPE
            while (!inbox.isEmpty()) {
                Message<List<Pair>> msg = inbox.poll();
                if (msg != null && msg.data != null) {
                    allPairs.addAll(msg.data);
                }
            }

            // 汇总当前 Worker 负责的所有 key 的最终频率
            for (Pair p : allPairs) {
                finalCounter.put(p.key, finalCounter.getOrDefault(p.key, 0) + p.count);
            }
        }

        // --- 本地计算 Top 1 ---
        Pair localTop1() {
            Pair best = null;
            for (Map.Entry<Integer, Integer> e : finalCounter.entrySet()) {
                int key = e.getKey(), count = e.getValue();
                if (best == null || count > best.count || (count == best.count && key < best.key)) {
                    best = new Pair(key, count);
                }
            }
            return best; // 如果没有数据则返回 null
        }
    }

    // 5. 集群控制器 (Master)
    static class Cluster {
        List<Worker> workers = new ArrayList<>();

        Cluster(List<List<Integer>> dataset) {
            for (int i = 0; i < dataset.size(); i++) {
                workers.add(new Worker(i, dataset.size(), dataset.get(i), this));
            }
        }

        void send(int workerId, List<Pair> data) {
            workers.get(workerId).receive(new Message<>(data));
        }

        Integer findMode() {
            if (workers.isEmpty()) return null;

            // 阶段 1：Map & Shuffle
            for (Worker w : workers) w.sendShuffle();
            
            // 阶段 2：Reduce
            for (Worker w : workers) w.recvAggregate();
            
            // 阶段 3：Master 汇总全局 Top 1
            Pair globalBest = null;
            for (Worker w : workers) {
                Pair localBest = w.localTop1();
                if (localBest != null) {
                    if (globalBest == null || 
                        localBest.count > globalBest.count || 
                       (localBest.count == globalBest.count && localBest.key < globalBest.key)) {
                        globalBest = localBest;
                    }
                }
            }
            return globalBest != null ? globalBest.key : null;
        }
    }

    // --- 测试入口 ---
    public static void main(String[] args) {
        Cluster cluster = new Cluster(Arrays.asList(
                Arrays.asList(1, 2, 3),        // Worker 0
                Arrays.asList(4, 5, 6, 3),     // Worker 1
                Arrays.asList(7, 8, 3, 3)      // Worker 2
        ));
        
        System.out.println("Global Mode: " + cluster.findMode()); // 输出: 3
    }
}