import java.util.*;

public class AlienDictionary {

    public static void main(String[] args) {
        String[] words = {"wrt", "wrf", "er", "ett", "rftt"};
        System.out.print(new AlienDictionary().sort(words));
        // wertf
    }

    private String sort(String[] words) {
        Map<Character, Set<Character>> map = new HashMap<>();
        Map<Character, Integer> indegree = new HashMap<>();
        for (String word : words) {
            for (char c: word.toCharArray()) {
                indegree.put(c, 0);
            }
        }
        for (int i = 0; i < words.length - 1; i ++) {
            String pre = words[i];
            String next = words[i + 1];
            for (int j = 0; j < pre.length() ; j ++) {
                if (pre.charAt(j) != next.charAt(j)) {
                    map.putIfAbsent(pre.charAt(j), new HashSet<>());
                    Set<Character> set = map.get(pre.charAt(j));
                    if (!set.contains(next.charAt(j))) {
                        indegree.put(next.charAt(j), indegree.get(next.charAt(j)) + 1);
                    }
                    map.get(pre.charAt(j)).add(next.charAt(j));
                    break;
                }
            }
        }

        Queue<Character> queue = new LinkedList<>();
        for (char c : indegree.keySet()) {
            if (indegree.get(c) == 0) {
                queue.add(c);
            }
        }
        StringBuffer sb = new StringBuffer();
        while (!queue.isEmpty()) {
            char c = queue.poll();
            sb.append(c);
            if (map.get(c) == null) {
                System.out.println(c);
                continue;
            }
            for (char next : map.get(c)) {
                indegree.put(next, indegree.get(next) - 1);
                if (indegree.get(next) == 0) {
                    queue.add(next);
                }
            }
        }

        if (sb.length()!= indegree.size()) return "";
        return sb.toString();
    }
}
