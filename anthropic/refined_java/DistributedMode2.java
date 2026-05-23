package anthropic.refined_java;

import java.util.*;

public class DistributedMode2 {

    static class Pair {
        int key, count;
        Pair(int key, int count) {
            this.key = key;
            this.count = count;
        }
    }

    static class Top1 {
        Integer key;
        int count;
        Top1(Integer key, int count) {
            this.key = key;
            this.count = count;
        }
    }

    // Provided APIs
    static void send(int workerId, Object data) { }

    static Object recv() { return null; }

    public static Integer findModeDistributed(List<Integer> localData, int workerId, int numWorkers) {
        Map<Integer, Integer> localCounter = phase1LocalCount(localData);
        List<Pair> receivedData = phase2ShuffleCounts(localCounter, workerId, numWorkers);
        Top1 localTop1 = phase3AggregateLocal(receivedData);
        return phase4GlobalReduction(localTop1, workerId, numWorkers);
    }

    private static Map<Integer, Integer> phase1LocalCount(List<Integer> localData) {
        Map<Integer, Integer> counter = new HashMap<>();
        for (int num : localData) {
            counter.put(num, counter.getOrDefault(num, 0) + 1);
        }
        return counter;
    }

    private static List<Pair> phase2ShuffleCounts(
            Map<Integer, Integer> localCounter, int workerId, int numWorkers) {
        List<List<Pair>> buckets = new ArrayList<>();
        for (int i = 0; i < numWorkers; i++) {
            buckets.add(new ArrayList<>());
        }
        for (Map.Entry<Integer, Integer> entry : localCounter.entrySet()) {
            int key = entry.getKey(), count = entry.getValue();
            int target = Math.floorMod(key, numWorkers);
            buckets.get(target).add(new Pair(key, count));
        }
        for (int targetId = 0; targetId < numWorkers; targetId++) {
            if (targetId != workerId) {
                send(targetId, buckets.get(targetId));
            }
        }
        List<Pair> receivedData = new ArrayList<>();
        for (int i = 0; i < numWorkers - 1; i++) {
            @SuppressWarnings("unchecked")
            List<Pair> data = (List<Pair>) recv();
            receivedData.addAll(data);
        }
        receivedData.addAll(buckets.get(workerId));
        return receivedData;
    }

    private static Top1 phase3AggregateLocal(List<Pair> receivedData) {
        Map<Integer, Integer> aggregated = new HashMap<>();
        for (Pair pair : receivedData) {
            aggregated.put(pair.key, aggregated.getOrDefault(pair.key, 0) + pair.count);
        }
        Integer bestKey = null;
        int bestCount = 0;
        for (Map.Entry<Integer, Integer> entry : aggregated.entrySet()) {
            int key = entry.getKey(), count = entry.getValue();
            if (bestKey == null || count > bestCount || (count == bestCount && key < bestKey)) {
                bestKey = key;
                bestCount = count;
            }
        }
        return new Top1(bestKey, bestCount);
    }

    private static Integer phase4GlobalReduction(Top1 localTop1, int workerId, int numWorkers) {
        if (workerId != 0) {
            send(0, localTop1);
            return null;
        }
        Integer bestKey = localTop1.key;
        int bestCount = localTop1.count;
        for (int i = 0; i < numWorkers - 1; i++) {
            Top1 remote = (Top1) recv();
            if (remote.key != null && (bestKey == null || remote.count > bestCount
                    || (remote.count == bestCount && remote.key < bestKey))) {
                bestKey = remote.key;
                bestCount = remote.count;
            }
        }
        return bestKey;
    }
}
