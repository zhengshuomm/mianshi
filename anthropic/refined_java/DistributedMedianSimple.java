package anthropic.refined_java;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

class DistributedMedianSimple {

    static class Worker {
        int id;
        // 核心：直接对本地数据进行一次统计即可，不需要网络 Shuffle 洗牌！
        Map<Integer, Integer> localHistogram = new HashMap<>();

        Worker(int id, List<Integer> localData) {
            this.id = id;
            this.localHistogram = countLocal(localData);
        }


        Map<Integer, Integer> countLocal(List<Integer> localData) {
            Map<Integer, Integer> counter = new HashMap<>();
            for (int num : localData) {
                counter.put(num, counter.getOrDefault(num, 0) + 1);
            }
            return counter;
        }

        // 响应 Master (Cluster) 下发的 pivot，计算本地的 [less, equal, greater]
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
                workers.add(new Worker(i, dataset.get(i)));
            }
        }

        Integer findMedian() {
            if (workers.isEmpty()) return null;

            int total = 0, lo = Integer.MAX_VALUE, hi = Integer.MIN_VALUE;
            // 获取全局的 Range (min, max) 和总数
            for (Worker w : workers) {
                for (Map.Entry<Integer, Integer> e : w.localHistogram.entrySet()) {
                    total += e.getValue();
                    lo = Math.min(lo, e.getKey());
                    hi = Math.max(hi, e.getKey());
                }
            }
            if (total == 0) return null;
            
            // 这里暂按原逻辑返回单一元素（上中位数）
            return binarySearchRange(lo, hi, total / 2);
        }

        // 改名为更准确的 binarySearchRange
        private Integer binarySearchRange(int low, int high, int targetPos) {
            while (low < high) {
                // 修复 1：使用 long 避免跨越 0 时的 Integer.MAX_VALUE 溢出
                int pivot = (low + high) / 2;
                
                int less = 0, equal = 0, greater = 0;
                // Scatter & Gather：向所有 Worker 发送 pivot 并收集结果
                for (Worker w : workers) {
                    int[] c = w.pivotCounts(pivot);
                    less += c[0];
                    equal += c[1];
                    greater += c[2];
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
}
