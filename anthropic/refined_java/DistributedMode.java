package anthropic.refined_java;

import java.util.*;

public class DistributedMode {

    static class Pair {
        int key, count;
        Pair(int key, int count) {
            this.key = key;
            this.count = count;
        }
    }

    static class TopResult {
        Integer key;
        int count;
        TopResult(Integer key, int count) {
            this.key = key;
            this.count = count;
        }
    }

    static class Message {
        Object data;
        Message(Object data) { this.data = data; }
    }

    static Map<Integer, Integer> countLocal(List<Integer> localData) {
        Map<Integer, Integer> counter = new HashMap<>();
        for (int num : localData) {
            counter.put(num, counter.getOrDefault(num, 0) + 1);
        }
        return counter;
    }

    static Map<Integer, Integer> aggregatePairs(List<Pair> pairs) {
        Map<Integer, Integer> hist = new HashMap<>();
        for (Pair p : pairs) {
            hist.put(p.key, hist.getOrDefault(p.key, 0) + p.count);
        }
        return hist;
    }

    static List<List<Pair>> partitionByWorker(Map<Integer, Integer> localCounter, int numWorkers) {
        List<List<Pair>> buckets = new ArrayList<>();
        for (int i = 0; i < numWorkers; i++) {
            buckets.add(new ArrayList<>());
        }
        for (Map.Entry<Integer, Integer> e : localCounter.entrySet()) {
            int key = e.getKey(), count = e.getValue();
            buckets.get(Math.floorMod(key, numWorkers)).add(new Pair(key, count));
        }
        return buckets;
    }

    static List<Pair> mergeOwnAndInbox(List<Pair> own, int numWorkers, java.util.function.Supplier<List<Pair>> recvFn) {
        List<Pair> all = new ArrayList<>(own);
        for (int i = 0; i < numWorkers - 1; i++) {
            all.addAll(recvFn.get());
        }
        return all;
    }

    static class Worker {
        int id, workerNum;
        List<Integer> localData;
        Map<Integer, Integer> finalCounter = new HashMap<>();
        List<Pair> ownPairs = new ArrayList<>();
        Queue<Message> inbox = new LinkedList<>();
        Cluster cluster;

        Worker(int id, int workerNum, List<Integer> localData, Cluster cluster) {
            this.id = id;
            this.workerNum = workerNum;
            this.localData = localData;
            this.cluster = cluster;
        }

        void send(int workerId, Object data) { cluster.send(workerId, data); }

        Message recv() { return inbox.poll(); }

        void receive(Message msg) { inbox.offer(msg); }

        void sendShuffle() {
            List<List<Pair>> buckets = partitionByWorker(countLocal(localData), workerNum);
            ownPairs = buckets.get(id);
            for (int t = 0; t < workerNum; t++) {
                if (t != id) {
                    send(t, buckets.get(t));
                }
            }
        }

        void recvAggregate() {
            finalCounter = aggregatePairs(mergeOwnAndInbox(
                    ownPairs, workerNum, () -> (List<Pair>) recv().data));
        }

        TopResult localTop1() {
            Integer bestKey = null;
            int bestCount = 0;
            for (Map.Entry<Integer, Integer> e : finalCounter.entrySet()) {
                int key = e.getKey(), count = e.getValue();
                if (bestKey == null || count > bestCount || (count == bestCount && key < bestKey)) {
                    bestKey = key;
                    bestCount = count;
                }
            }
            return new TopResult(bestKey, bestCount);
        }
    }

    static class Cluster {
        List<Worker> workers = new ArrayList<>();

        Cluster(List<List<Integer>> dataset) {
            for (int i = 0; i < dataset.size(); i++) {
                workers.add(new Worker(i, dataset.size(), dataset.get(i), this));
            }
        }

        void send(int workerId, Object data) {
            workers.get(workerId).receive(new Message(data));
        }

        Integer findMode() {
            for (Worker w : workers) w.sendShuffle();
            for (Worker w : workers) w.recvAggregate();
            Integer bestKey = null;
            int bestCount = 0;
            for (Worker w : workers) {
                TopResult local = w.localTop1();
                if (local.key != null && (bestKey == null || local.count > bestCount
                        || (local.count == bestCount && local.key < bestKey))) {
                    bestKey = local.key;
                    bestCount = local.count;
                }
            }
            return bestKey;
        }
    }

    public static void main(String[] args) {
        Cluster cluster = new Cluster(Arrays.asList(
                Arrays.asList(1, 2, 3),
                Arrays.asList(4, 5, 6),
                Arrays.asList(7, 8, 3)));
        System.out.println(cluster.findMode());
    }
}

class DistributedMedianSystem {

    static class Worker {
        int id, workerNum;
        List<Integer> localData;
        Map<Integer, Integer> localHistogram = new HashMap<>();
        List<DistributedMode.Pair> ownPairs = new ArrayList<>();
        Queue<DistributedMode.Message> inbox = new LinkedList<>();
        Cluster cluster;

        Worker(int id, int workerNum, List<Integer> localData, Cluster cluster) {
            this.id = id;
            this.workerNum = workerNum;
            this.localData = localData;
            this.cluster = cluster;
        }

        void send(int target, Object data) { cluster.send(target, data); }

        DistributedMode.Message recv() { return inbox.poll(); }

        void receive(DistributedMode.Message msg) { inbox.offer(msg); }

        void sendShuffle() {
            List<List<DistributedMode.Pair>> buckets =
                    DistributedMode.partitionByWorker(DistributedMode.countLocal(localData), workerNum);
            ownPairs = buckets.get(id);
            for (int t = 0; t < workerNum; t++) {
                if (t != id) {
                    send(t, buckets.get(t));
                }
            }
        }

        void recvAggregate() {
            localHistogram = DistributedMode.aggregatePairs(DistributedMode.mergeOwnAndInbox(
                    ownPairs, workerNum, () -> (List<DistributedMode.Pair>) recv().data));
        }

        int[] pivotCounts(int pivot) {
            int less = 0, equal = 0, greater = 0;
            for (Map.Entry<Integer, Integer> e : localHistogram.entrySet()) {
                int key = e.getKey(), count = e.getValue();
                if (key < pivot) less += count;
                else if (key == pivot) equal += count;
                else greater += count;
            }
            return new int[] {less, equal, greater};
        }
    }

    static class Cluster {
        List<Worker> workers = new ArrayList<>();

        Cluster(List<List<Integer>> dataset) {
            for (int i = 0; i < dataset.size(); i++) {
                workers.add(new Worker(i, dataset.size(), dataset.get(i), this));
            }
        }

        void send(int target, Object data) {
            workers.get(target).receive(new DistributedMode.Message(data));
        }

        Integer findMedian() {
            if (workers.isEmpty()) return null;
            for (Worker w : workers) w.sendShuffle();
            for (Worker w : workers) w.recvAggregate();

            int total = 0, lo = Integer.MAX_VALUE, hi = Integer.MIN_VALUE;
            for (Worker w : workers) {
                for (Map.Entry<Integer, Integer> e : w.localHistogram.entrySet()) {
                    total += e.getValue();
                    lo = Math.min(lo, e.getKey());
                    hi = Math.max(hi, e.getKey());
                }
            }
            if (total == 0) return null;
            return quickSelect(lo, hi, total / 2);
        }

        private Integer quickSelect(int low, int high, int targetPos) {
            while (low < high) {
                int pivot = low + (high - low) / 2;
                int less = 0, equal = 0, greater = 0;
                for (Worker w : workers) {
                    int[] c = w.pivotCounts(pivot);
                    less += c[0];
                    equal += c[1];
                    greater += c[2];
                }
                if (targetPos < less) high = pivot - 1;
                else if (targetPos < less + equal) return pivot;
                else low = pivot + 1;
            }
            return low;
        }
    }

    public static void main(String[] args) {
        Cluster cluster = new Cluster(Arrays.asList(
                Arrays.asList(1, 5, 9),
                Arrays.asList(2, 6),
                Arrays.asList(3, 7, 8)));
        System.out.println(cluster.findMedian()); // 6
    }
}
