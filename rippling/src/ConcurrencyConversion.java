import java.util.*;

public class ConcurrencyConversion {
    public static void main(String[] args) {
        System.out.println("Hello world!");
        List<List<String>> input = new ArrayList<>();
        List<String> l1 = Arrays.asList("JPY", "EUR", "0.3");
        List<String> l2 = Arrays.asList("EUR", "USD", "1.1");
        input.add(l1);
        input.add(l2);

        Map<String, Map<String, Float>> map = new HashMap<>();
        for (List<String> l : input) {
            map.putIfAbsent(l.get(0), new HashMap<>());
            String to = l.get(1);
            Float v = Float.parseFloat(l.get(2));
            map.get(l.get(0)).put(to, v);
        }
        System.out.println(new ConcurrencyConversion().BFS("JPY", "USD", map));
    }

    public float BFS(String start, String target, Map<String, Map<String, Float>>  map) {
        Queue<Node> queue = new LinkedList<>();
        Set<String> set = new HashSet<>();
        queue.add(new Node(start, 1));
        set.add(start);
        while (!queue.isEmpty()) {
            Node cur = queue.poll();
            Map<String, Float> neighbors = map.get(cur.currency);
            if (cur.currency.equals(target)) return cur.value;
            for (String s: neighbors.keySet()) {
                float value =neighbors.get(s);
                if (set.contains(s)) continue;
                set.add(s);
                queue.add(new Node(s, cur.value * value));
            }
        }
        return -1;
    }

    class Node {
        String currency;
        float value;

        public Node(String currency, float value) {
            this.currency = currency;
            this.value = value;
        }
    }




}