package anthropic.self_practice;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

public class DistributedMedianSimple2 {

    static class Worker {
        int id;
        int workerNum;
        List<Integer> localData;
        Cluster cluster;

        public Worker(int id, int workerNum, List<Integer> localData, Cluster cluster) {
            this.id = id;
            this.workerNum = workerNum;
            this.localData = localData;
            this.cluster = cluster;
        }

        public Map<Integer, Integer> countLocal() {
            Map<Integer, Integer> counter = new HashMap<>();
            for (int num : localData) {
                counter.put(num, counter.getOrDefault(num, 0) + 1);
            }
            return counter;
        }

        int[] pivotCounts(int pivot) {
            int less = 0, equal = 0;
            for (int num : localData) {
                if (num < pivot) {
                    less++;
                } else if (num == pivot) {
                    equal++;
                }
            }
            return new int[] {less, equal};
        }
    }

    static class Cluster {
        List<Worker> workers = new ArrayList<>();

        public Cluster(List<List<Integer>> dataset) {
            for (int i = 0; i < dataset.size(); i++) {
                workers.add(new Worker(i, dataset.size(), dataset.get(i), this));
            }
        }

        public Integer findMedian() {
            if (workers.isEmpty()) return null;

            int total = 0, low = Integer.MAX_VALUE, high = Integer.MIN_VALUE;
            for (Worker worker : workers) {
                for (Map.Entry<Integer, Integer> e : worker.countLocal().entrySet()) {
                    total += e.getValue();
                    low = Math.min(low, e.getKey());
                    high = Math.max(high, e.getKey());
                }
            }

            if (total == 0) return null;
            return binarySearchRange(low, high, total / 2);
        }

        private Integer binarySearchRange(int low, int high, int targetPos) {
            while (low < high) {
                int pivot = (low + high) / 2;
                int less = 0, equal = 0;

                for (Worker worker : workers) {
                    int[] c = worker.pivotCounts(pivot);
                    less += c[0];
                    equal += c[1];
                }

                if (targetPos < less) {
                    high = pivot - 1;
                } else if (targetPos < less + equal) {
                    return pivot;
                } else {
                    low = pivot + 1;
                }
            }
            return low;
        }
    }

    public static void main(String[] args) {
        Cluster cluster = new Cluster(Arrays.asList(
                Arrays.asList(1, 2, 3),
                Arrays.asList(4, 5, 6),
                Arrays.asList(7, 8, 9)
        ));

        System.out.println("Global Median: " + cluster.findMedian());
    }
}
