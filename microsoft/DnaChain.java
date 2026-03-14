import java.util.*;

/**
 * DNA 题：(start_tag, end_tag, payload)，next.start_tag == prev.end_tag 则相连。
 * Q1 单链拼串；Q2 可正向/反向；Q3 多链各输出一条串。
 */
public class DnaChain {

    public static class Segment {
        String startTag, endTag, payload;
        public Segment(String startTag, String endTag, String payload) {
            this.startTag = startTag;
            this.endTag = endTag;
            this.payload = payload;
        }
    }

    /** 把 segments 排成一条链（头：startTag 不是任何 endTag），返回有序列表 */
    private static List<Segment> toOrderedChain(List<Segment> segments) {
        if (segments == null || segments.isEmpty()) return List.of();
        Set<String> ends = new HashSet<>();
        for (Segment s : segments) ends.add(s.endTag);
        Segment head = null;
        for (Segment s : segments)
            if (!ends.contains(s.startTag)) { head = s; break; }
        if (head == null) return List.of();
        Map<String, Segment> byStart = new HashMap<>();
        for (Segment s : segments) byStart.put(s.startTag, s);
        List<Segment> out = new ArrayList<>();
        for (Segment c = head; c != null; c = byStart.get(c.endTag)) out.add(c);
        return out;
    }

    private static String chainToStr(List<Segment> chain) {
        StringBuilder sb = new StringBuilder();
        for (Segment s : chain) sb.append(s.payload);
        return sb.toString();
    }

    // Q1
    public static String assembleOneChain(List<Segment> segments) {
        return chainToStr(toOrderedChain(segments));
    }

    // Q2
    public static String assembleReversed(List<Segment> segments) {
        List<Segment> chain = toOrderedChain(segments);
        StringBuilder sb = new StringBuilder();
        for (int i = chain.size() - 1; i >= 0; i--) sb.append(chain.get(i).payload);
        return sb.toString();
    }

    // Q3：按连通分量拆开，每块排成链再拼串
    public static List<String> assembleAllChains(List<Segment> segments) {
        if (segments == null || segments.isEmpty()) return List.of();
        Map<String, List<Segment>> byStart = new HashMap<>();
        Map<String, List<Segment>> byEnd = new HashMap<>();
        for (Segment s : segments) {
            byStart.computeIfAbsent(s.startTag, k -> new ArrayList<>()).add(s);
            byEnd.computeIfAbsent(s.endTag, k -> new ArrayList<>()).add(s);
        }
        Set<Segment> visited = new HashSet<>();
        List<String> result = new ArrayList<>();
        for (Segment s : segments) {
            if (visited.contains(s)) continue;
            List<Segment> component = new ArrayList<>();
            Deque<Segment> q = new ArrayDeque<>(List.of(s));
            while (!q.isEmpty()) {
                Segment cur = q.poll();
                if (!visited.add(cur)) continue;
                component.add(cur);
                for (Segment n : byStart.getOrDefault(cur.endTag, List.of())) q.add(n);
                for (Segment n : byEnd.getOrDefault(cur.startTag, List.of())) q.add(n);
            }
            result.add(chainToStr(toOrderedChain(component)));
        }
        return result;
    }

    public static void main(String[] args) {
        List<Segment> q1 = List.of(
            new Segment("XXX", "ATG", "Hello"),
            new Segment("ATG", "GCA", "World"),
            new Segment("GCA", "TAG", "!"),
            new Segment("TAG", "YYY", "Done")
        );
        System.out.println("Q1: " + assembleOneChain(q1));
        System.out.println("Q2: " + assembleReversed(q1));
        List<Segment> q3 = new ArrayList<>(q1);
        q3.add(new Segment("A", "B", "Chain2-"));
        q3.add(new Segment("B", "C", "second"));
        System.out.println("Q3: " + assembleAllChains(q3));
    }
}
