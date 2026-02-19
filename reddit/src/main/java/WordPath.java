import java.util.*;

public class WordPath {

    public static boolean canConnect(
            String start,
            String end,
            Set<String> dict) {

        if (!dict.contains(end)) return false;

        Queue<String> queue = new LinkedList<>();
        Set<String> visited = new HashSet<>();

        queue.offer(start);
        visited.add(start);

        while (!queue.isEmpty()) {
            String cur = queue.poll();

            if (cur.equals(end)) return true;

            for (String next : neighbors(cur, dict)) {
                if (!visited.contains(next)) {
                    visited.add(next);
                    queue.offer(next);
                }
            }
        }
        return false;
    }

    private static List<String> neighbors(String word, Set<String> dict) {
        List<String> res = new ArrayList<>();
        char[] arr = word.toCharArray();
        int n = arr.length;

        // 1-char change
        for (int i = 0; i < n; i++) {
            char old = arr[i];
            for (char c = 'a'; c <= 'z'; c++) {
                if (c == old) continue;
                arr[i] = c;
                String next = new String(arr);
                if (dict.contains(next)) res.add(next);
            }
            arr[i] = old;
        }

        // 2-char change
        for (int i = 0; i < n; i++) {
            for (int j = i + 1; j < n; j++) {
                char c1 = arr[i], c2 = arr[j];
                for (char x = 'a'; x <= 'z'; x++) {
                    for (char y = 'a'; y <= 'z'; y++) {
                        if (x == c1 && y == c2) continue;
                        arr[i] = x;
                        arr[j] = y;
                        String next = new String(arr);
                        if (dict.contains(next)) res.add(next);
                    }
                }
                arr[i] = c1;
                arr[j] = c2;
            }
        }
        return res;
    }
}