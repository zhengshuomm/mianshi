import java.util.*;
import java.math.*;

class Shard {
    String id;
    int start;
    int end;

    Shard(String s) {
        String[] parts = s.split(":");
        this.id = parts[0];
        this.start = Integer.parseInt(parts[1]);
        this.end = Integer.parseInt(parts[2]);
    }

    @Override
    public String toString() {
        return id + ":" + start + ":" + end;
    }
}

class Rebalance {
public List<String> rebalance(int limit, List<String> shardStrings) {
        if (shardStrings == null || shardStrings.size() == 0 || limit <= 0) {
            return new ArrayList<>();
        }

        // 1. 解析并排序
        List<Shard> shards = new ArrayList<>();
        for (String s : shardStrings) {
            shards.add(new Shard(s));
        }
        shards.sort((a, b) -> a.start != b.start ? Integer.compare(a.start, b.start) : Integer.compare(a.end, b.end));

        List<Shard> result = new ArrayList<>();
        // 存储当前活跃分片的 end 时间，堆顶是最小的 end
        PriorityQueue<Integer> activeEnds = new PriorityQueue<>();
        
        // 追踪当前连续覆盖到的最远点
        int maxReached = Integer.MIN_VALUE;
        Shard lastProcessed = null;

        for (Shard shard : shards) {
            int originalStart = shard.start;
            int originalEnd = shard.end;

            // 清理掉已经过期的分片（在当前 shard 的原始 start 之前结束的）
            while (!activeEnds.isEmpty() && activeEnds.peek() < originalStart) {
                activeEnds.poll();
            }

            // 规则：Resolve Overlaps (Shift)
            // 如果达到限制，新分片必须从最早空出的位置开始
            int effectiveStart = originalStart;
            while (activeEnds.size() >= limit && !activeEnds.isEmpty()) {
                effectiveStart = Math.max(effectiveStart, activeEnds.peek() + 1);
                // 每次移动后都要清理由于移动导致过期的旧分片
                while (!activeEnds.isEmpty() && activeEnds.peek() < effectiveStart) {
                    activeEnds.poll();
                }
                if (activeEnds.size() < limit) break;
            }

            // 规则：Delete Excess (Drop)
            if (effectiveStart > originalEnd) {
                continue;
            }

            // 规则：Fill Gaps (Extend)
            if (lastProcessed != null && maxReached != Integer.MIN_VALUE && effectiveStart > maxReached + 1) {
                lastProcessed.end = effectiveStart - 1;
                // 更新堆中对应的结束位置（由于 lastProcessed 的 end 变了，这里逻辑上需要同步，
                // 但因为它是延展，不会影响当前 effectiveStart 的计算，所以简化处理）
            }

            // 更新分片状态并记录
            shard.start = effectiveStart;
            result.add(shard);
            activeEnds.offer(shard.end);
            
            // 更新全局状态
            maxReached = Math.max(maxReached, shard.end);
            lastProcessed = shard;
        }

        List<String> output = new ArrayList<>();
        for (Shard s : result) {
            output.add(s.toString());
        }
        return output;
    }
} {
    
}
