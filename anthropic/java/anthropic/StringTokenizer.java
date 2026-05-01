package anthropic;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/** Longest-match tokenizer (from string_tokenizer.py). */
public class StringTokenizer {

    static class Trie {
        Map<Character, Trie> children = new HashMap<>();
        String id;
    }

    public List<String> tokenize(String text, List<String> dictionary) {
        Trie root = buildTrie(dictionary);
        List<String> result = new ArrayList<>();
        int i = 0;
        while (i < text.length()) {
            Trie node = root;
            String bestId = null;
            int bestEnd = i;
            int j = i;
            while (j < text.length()) {
                char c = text.charAt(j);
                if (!node.children.containsKey(c)) break;
                node = node.children.get(c);
                j++;
                if (node.id != null) {
                    bestId = node.id;
                    bestEnd = j;
                }
            }
            if (bestId != null) {
                result.add(bestId);
                i = bestEnd;
            } else {
                result.add(String.valueOf(text.charAt(i)));
                i++;
            }
        }
        return result;
    }

    Trie buildTrie(List<String> dictionary) {
        Trie root = new Trie();
        for (String entry : dictionary) {
            int colon = entry.indexOf(':');
            if (colon < 0) continue;
            String key = entry.substring(0, colon);
            String val = entry.substring(colon + 1);
            Trie node = root;
            for (int i = 0; i < key.length(); i++) {
                char c = key.charAt(i);
                node.children.putIfAbsent(c, new Trie());
                node = node.children.get(c);
            }
            node.id = val;
        }
        return root;
    }

    public static void main(String[] args) {
        StringTokenizer s = new StringTokenizer();
        assertEq(
                s.tokenize(
                        "applepiepear",
                        Arrays.asList("app:10", "apple:20", "pie:30")),
                Arrays.asList("20", "30", "p", "e", "a", "r"));
        assertEq(
                s.tokenize("acdebe", Arrays.asList("a:1", "b:2", "cd:3")),
                Arrays.asList("1", "3", "e", "2", "e"));
        System.out.println("StringTokenizer: ok");
    }

    static void assertEq(List<String> a, List<String> b) {
        if (!a.equals(b)) throw new AssertionError(a + " != " + b);
    }
}
