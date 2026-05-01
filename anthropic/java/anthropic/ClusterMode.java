package anthropic;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/** Distributed mode via hash-partitioned scatter (from cluster_mode.py). */
public class ClusterMode {

    static class Worker {
        final int workerId;
        final int k;
        final List<Integer> localData;
        final ClusterMode cluster;

        Worker(int workerId, int k, List<Integer> localData, ClusterMode cluster) {
            this.workerId = workerId;
            this.k = k;
            this.localData = localData;
            this.cluster = cluster;
        }

        void sendAsyncMessage(int targetWorkerId, String payload) {
            cluster.sendAsyncMessage(targetWorkerId, payload);
        }

        String receive() {
            return cluster.receive(workerId);
        }

        void scatterFreq() {
            Map<Integer, Integer> localFreq = new HashMap<>();
            for (int value : localData) {
                localFreq.merge(value, 1, Integer::sum);
            }
            for (Map.Entry<Integer, Integer> e : localFreq.entrySet()) {
                int owner = Math.floorMod(e.getKey(), k);
                sendAsyncMessage(owner, "DATA " + e.getKey() + " " + e.getValue());
            }
            for (int i = 0; i < k; i++) {
                sendAsyncMessage(i, "DONE");
            }
        }

        /** Returns {bestValue, bestCount} or null if empty. */
        int[] aggregateFreq() {
            Map<Integer, Integer> ownedFreq = new HashMap<>();
            int done = 0;
            while (done < k) {
                String msg = receive();
                if (msg.startsWith("DATA")) {
                    String[] parts = msg.split(" ");
                    int value = Integer.parseInt(parts[1]);
                    int count = Integer.parseInt(parts[2]);
                    ownedFreq.merge(value, count, Integer::sum);
                } else if ("DONE".equals(msg)) {
                    done++;
                }
            }
            if (ownedFreq.isEmpty()) return null;
            int bestValue = Integer.MAX_VALUE;
            int bestCount = 0;
            for (Map.Entry<Integer, Integer> e : ownedFreq.entrySet()) {
                int v = e.getKey();
                int c = e.getValue();
                if (c > bestCount || (c == bestCount && v < bestValue)) {
                    bestValue = v;
                    bestCount = c;
                }
            }
            return new int[] {bestValue, bestCount};
        }

        void reportLocal(int[] localMode) {
            if (localMode != null) {
                sendAsyncMessage(0, "DATA " + localMode[0] + " " + localMode[1]);
            }
            sendAsyncMessage(0, "DONE");
        }

        int gatherGlobal(int[] myLocalMode) {
            int bestValue = Integer.MAX_VALUE;
            int bestCount = 0;
            if (myLocalMode != null) {
                bestValue = myLocalMode[0];
                bestCount = myLocalMode[1];
            }
            int done = 0;
            while (done < k - 1) {
                String msg = receive();
                if (msg.startsWith("DATA")) {
                    String[] parts = msg.split(" ");
                    int value = Integer.parseInt(parts[1]);
                    int count = Integer.parseInt(parts[2]);
                    if (count > bestCount || (count == bestCount && value < bestValue)) {
                        bestValue = value;
                        bestCount = count;
                    }
                } else if ("DONE".equals(msg)) {
                    done++;
                }
            }
            return bestValue;
        }
    }

    final int k;
    final List<List<Integer>> shards = new ArrayList<>();
    final Map<Integer, List<String>> mailboxes = new HashMap<>();
    final Map<Integer, Integer> readIndices = new HashMap<>();
    final Worker[] workers;

    public ClusterMode(List<Integer> data, int k) {
        this.k = k;
        for (int i = 0; i < k; i++) {
            shards.add(new ArrayList<>());
            mailboxes.put(i, new ArrayList<>());
            readIndices.put(i, 0);
        }
        int totalSize = data.size();
        int baseSize = totalSize / k;
        int remainder = totalSize % k;
        int index = 0;
        for (int w = 0; w < k; w++) {
            int chunkSize = baseSize + (w < remainder ? 1 : 0);
            for (int j = 0; j < chunkSize; j++) {
                shards.get(w).add(data.get(index++));
            }
        }
        workers = new Worker[k];
        for (int i = 0; i < k; i++) {
            workers[i] = new Worker(i, k, shards.get(i), this);
        }
    }

    void sendAsyncMessage(int targetWorkerId, String payload) {
        mailboxes.get(targetWorkerId).add(payload);
    }

    String receive(int workerId) {
        List<String> box = mailboxes.get(workerId);
        int idx = readIndices.get(workerId);
        if (idx < box.size()) {
            readIndices.put(workerId, idx + 1);
            return box.get(idx);
        }
        return "";
    }

    public int findMode() {
        for (int i = 0; i < k; i++) {
            workers[i].scatterFreq();
        }
        int[][] localModes = new int[k][];
        for (int i = 0; i < k; i++) {
            localModes[i] = workers[i].aggregateFreq();
        }
        for (int i = 1; i < k; i++) {
            workers[i].reportLocal(localModes[i]);
        }
        return workers[0].gatherGlobal(localModes[0]);
    }

    public static void main(String[] args) {
        List<Integer> d1 = new ArrayList<>();
        for (int v : new int[] {1, 2, 2, 3, 3, 3, 4, 4, 4, 4}) d1.add(v);
        System.out.println(new ClusterMode(d1, 3).findMode());
        List<Integer> d2 = new ArrayList<>();
        for (int v : new int[] {1, 2, 3, 1, 2, 3}) d2.add(v);
        System.out.println(new ClusterMode(d2, 2).findMode());
    }
}
