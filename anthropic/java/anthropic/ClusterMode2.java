package anthropic;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

/** Distributed median (from cluster_mode2.py). */
public class ClusterMode2 {

    static class Worker {
        final int workerId;
        final int k;
        final List<Integer> localData;
        final ClusterMode2 cluster;

        Worker(int workerId, int k, List<Integer> localData, ClusterMode2 cluster) {
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

        void sendLocalStats() {
            if (!localData.isEmpty()) {
                int mn = Integer.MAX_VALUE;
                int mx = Integer.MIN_VALUE;
                for (int v : localData) {
                    mn = Math.min(mn, v);
                    mx = Math.max(mx, v);
                }
                sendAsyncMessage(0, "STAT " + localData.size() + " " + mn + " " + mx);
            }
            sendAsyncMessage(0, "DONE_STATS");
        }

        int[] aggregateGlobalStats() {
            int total = 0;
            int gLo = Integer.MAX_VALUE;
            int gHi = Integer.MIN_VALUE;
            int done = 0;
            while (done < k) {
                String msg = receive();
                if (msg.startsWith("STAT")) {
                    String[] p = msg.split(" ");
                    int s = Integer.parseInt(p[1]);
                    total += s;
                    if (s > 0) {
                        gLo = Math.min(gLo, Integer.parseInt(p[2]));
                        gHi = Math.max(gHi, Integer.parseInt(p[3]));
                    }
                } else if ("DONE_STATS".equals(msg)) {
                    done++;
                }
            }
            if (total == 0) return new int[] {0, 0, 0};
            return new int[] {total, gLo, gHi};
        }

        void sendCountsForPivot(int pivot) {
            int le = 0;
            for (int v : localData) {
                if (v <= pivot) le++;
            }
            sendAsyncMessage(0, "CNT " + le);
            sendAsyncMessage(0, "DONE_CNT");
        }

        int aggregateLeCount() {
            int total = 0;
            int done = 0;
            while (done < k) {
                String msg = receive();
                if (msg.startsWith("CNT")) {
                    total += Integer.parseInt(msg.split(" ")[1]);
                } else if ("DONE_CNT".equals(msg)) {
                    done++;
                }
            }
            return total;
        }
    }

    final int k;
    final List<List<Integer>> shards = new ArrayList<>();
    final List<List<String>> mailboxes = new ArrayList<>();
    final List<Integer> readIndices = new ArrayList<>();
    final Worker[] workers;

    public ClusterMode2(List<Integer> data, int k) {
        this.k = k;
        for (int i = 0; i < k; i++) {
            shards.add(new ArrayList<>());
            mailboxes.add(new ArrayList<>());
            readIndices.add(0);
        }
        int n = data.size();
        int base = n / k;
        int rem = n % k;
        int idx = 0;
        for (int w = 0; w < k; w++) {
            int cs = base + (w < rem ? 1 : 0);
            for (int j = 0; j < cs; j++) {
                shards.get(w).add(data.get(idx++));
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
        int ri = readIndices.get(workerId);
        if (ri < box.size()) {
            readIndices.set(workerId, ri + 1);
            return box.get(ri);
        }
        return "";
    }

    public double findMedian() {
        for (Worker w : workers) {
            w.sendLocalStats();
        }
        int[] g = workers[0].aggregateGlobalStats();
        int n = g[0];
        if (n == 0) return Double.NaN;
        int lo = g[1];
        int hi = g[2];
        int i = (n - 1) / 2;
        int j = n / 2;
        return (selectKth(i, lo, hi) + selectKth(j, lo, hi)) / 2.0;
    }

    int selectKth(int targetIndex, int low, int high) {
        int left = low;
        int right = high;
        while (left < right) {
            int mid = left + (right - left) / 2;
            for (Worker w : workers) {
                w.sendCountsForPivot(mid);
            }
            int le = workers[0].aggregateLeCount();
            if (le <= targetIndex) {
                left = mid + 1;
            } else {
                right = mid;
            }
        }
        return left;
    }

    public static void main(String[] args) {
        List<Integer> d = Arrays.asList(1, 3, 5);
        System.out.println(new ClusterMode2(new ArrayList<>(d), 2).findMedian());
    }
}
