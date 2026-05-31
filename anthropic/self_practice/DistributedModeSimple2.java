package anthropic.self_practice;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;
import java.util.Queue;

public class DistributedModeSimple2 {

    static class Pair {
        int key, count;

        Pair(int key, int count) {
            this.key = key;
            this.count = count;
        }
    }

    static class Worker {
        int id;
        int workerNum;
        List<Integer> localData;
        Cluster cluster;

        List<Pair> ownPairs = new ArrayList<>();
        Map<Integer, Integer> finalCounter = new HashMap<>();
        Queue<Object> inbox = new LinkedList<>();

        public Worker(int id, int workerNum, List<Integer> localData, Cluster cluster) {
            this.id = id;
            this.workerNum = workerNum;
            this.localData = localData;
            this.cluster = cluster;
        }

        void receive(Object msg) {
            inbox.offer(msg);
        }

        Map<Integer, Integer> countLocal() {
            Map<Integer, Integer> count = new HashMap<>();
            for (int num : localData) {
                count.put(num, count.getOrDefault(num, 0) + 1);
            }
            return count;
        }

        List<List<Pair>> partitionByWorker(Map<Integer, Integer> localCounter) {
            List<List<Pair>> buckets = new ArrayList<>();
            for (int i = 0; i < workerNum; i++) {
                buckets.add(new ArrayList<>());
            }

            for (Map.Entry<Integer, Integer> e : localCounter.entrySet()) {
                int workerIndex = e.getKey() % workerNum;
                buckets.get(workerIndex).add(new Pair(e.getKey(), e.getValue()));
            }
            return buckets;
        }

        void sendShuffle() {
            List<List<Pair>> buckets = partitionByWorker(countLocal());
            this.ownPairs = buckets.get(id);

            for (int i = 0; i < workerNum; i++) {
                if (i != id) {
                    cluster.send(i, buckets.get(i));
                }
            }
        }

        void recvAggregate() {
            List<Pair> pairs = new ArrayList<>(ownPairs);

            while (!inbox.isEmpty()) {
                List<Pair> msg = (List<Pair>) inbox.poll();
                pairs.addAll(msg);
            }

            for (Pair p : pairs) {
                finalCounter.put(p.key, finalCounter.getOrDefault(p.key, 0) + p.count);
            }
        }

        Pair localTop1() {
            Pair best = null;
            for (Map.Entry<Integer, Integer> e : finalCounter.entrySet()) {
                int key = e.getKey(), count = e.getValue();
                if (best == null || count > best.count) {
                    best = new Pair(key, count);
                }
            }
            return best;
        }
    }

    static class Cluster {
        List<Worker> workers = new ArrayList<>();

        public Cluster(List<List<Integer>> dataset) {
            for (int i = 0; i < dataset.size(); i++) {
                workers.add(new Worker(i, dataset.size(), dataset.get(i), this));
            }
        }

        void send(int workerId, List<Pair> data) {
            workers.get(workerId).receive(data);
        }

        Integer findMode() {
            if (workers.isEmpty()) return null;

            for (Worker w : workers) w.sendShuffle();
            for (Worker w : workers) w.recvAggregate();

            Pair globalBest = null;
            for (Worker w : workers) {
                Pair localBest = w.localTop1();
                if (localBest != null) {
                    if (globalBest == null
                            || localBest.count > globalBest.count
                            || (localBest.count == globalBest.count && localBest.key < globalBest.key)) {
                        globalBest = localBest;
                    }
                }
            }
            return globalBest != null ? globalBest.key : null;
        }
    }

    public static void main(String[] args) {
        Cluster cluster = new Cluster(Arrays.asList(
                Arrays.asList(1, 2, 3),
                Arrays.asList(4, 5, 6, 3),
                Arrays.asList(7, 8, 3, 3)
        ));

        System.out.println("Global Mode: " + cluster.findMode());
    }
}
