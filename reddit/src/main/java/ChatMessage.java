import java.util.*;

public class ChatMessage {
    
    static class Interval {
        int start;
        int end;
        
        Interval(int start, int end) {
            this.start = start;
            this.end = end;
        }
    }

    // 已给 API
    static List<Integer> get(int postId) {
        // mock
        return new ArrayList<>();
    }

    public static List<Integer> mergePosts(List<Integer> ids) {
        List<Interval> intervals = new ArrayList<>();

        // 1. 调 API，转成区间
        for (int id : ids) {
            List<Integer> posts = get(id);
            if (posts.isEmpty()) continue;

            intervals.add(
                new Interval(posts.get(0), posts.get(posts.size() - 1))
            );
        }

        // 2. 按 start 排序
        // intervals.sort(Comparator.comparingInt(a -> a.start));
        Collections.sort(intervals, (a, b) -> a.start - b.start);

        // 3. merge intervals
        List<Interval> merged = new ArrayList<>();
        for (Interval cur : intervals) {
            if (merged.isEmpty()) {
                merged.add(cur);
            } else {
                Interval last = merged.get(merged.size() - 1);
                if (cur.start <= last.end + 1) {
                    last.end = Math.max(last.end, cur.end);
                } else {
                    merged.add(cur);
                }
            }
        }

        // 4. 展开成结果 list
        List<Integer> result = new ArrayList<>();
        for (Interval in : merged) {
            for (int i = in.start; i <= in.end; i++) {
                result.add(i);
            }
        }

        return result;
    }
}