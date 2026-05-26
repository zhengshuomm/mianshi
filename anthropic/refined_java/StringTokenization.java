package anthropic.refined_java;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

// https://www.1point3acres.com/interview/problems/post/7100003
// https://www.1point3acres.com/bbs/thread-1164836-1-1.html

public class StringTokenization {
    private static class TrieNode {
        Map<Character, TrieNode> children = new HashMap<>();
        String id;
    }

    public List<String> tokenize(String text, List<String> dictionary) {
        TrieNode root = buildTrie(dictionary);
        List<String> result = new ArrayList<>();

        int i = 0;
        int n = text.length();

        while (i < n) {
            TrieNode node = root;
            String bestId = null;
            int bestEnd = i;

            int j = i;
            while (j < n) {
                char c = text.charAt(j);
                
                // 优化 1：消除 containsKey 的重复查找
                TrieNode next = node.children.get(c);
                if (next == null) {
                    break;
                }

                node = next;
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
                // 原有的 String.valueOf() 没问题，或者使用 Character.toString(text.charAt(i)) 也可以
                result.add(String.valueOf(text.charAt(i)));
                i++;
            }
        }

        return result;
    }

    private TrieNode buildTrie(List<String> dictionary) {
        TrieNode root = new TrieNode();

        for (String entry : dictionary) {
            int colonIndex = entry.indexOf(':');
            if (colonIndex == -1) {
                continue;
            }

            String key = entry.substring(0, colonIndex);
            String value = entry.substring(colonIndex + 1);

            TrieNode node = root;
            for (int i = 0; i < key.length(); i++) {
                char c = key.charAt(i);
                node.children.putIfAbsent(c, new TrieNode());
                node = node.children.get(c);
            }

            // Duplicate key: last dictionary entry wins.
            node.id = value;
        }

        return root;
    }

    public static void main(String[] args) {
        StringTokenization tokenizer = new StringTokenization();

        assertEquals(
            tokenizer.tokenize("helloworld", List.of("hello:H", "world:W")),
            List.of("H", "W"),
            "basic two-token match"
        );

        assertEquals(
            tokenizer.tokenize("abcde", List.of("a:A", "abc:ABC", "bc:BC", "de:DE")),
            List.of("ABC", "DE"),
            "longest match wins"
        );

        assertEquals(
            tokenizer.tokenize("aaab", List.of("a:X", "aa:Y", "aa:Z")),
            List.of("Z", "X", "b"),
            "duplicate key last wins and literal fallback"
        );

        assertEquals(
            tokenizer.tokenize("xyz", List.of("ab:AB")),
            List.of("x", "y", "z"),
            "no match emits literal characters"
        );

        assertEquals(
            tokenizer.tokenize("", List.of("a:A")),
            List.of(),
            "empty text"
        );

        System.out.println("All StringTokenization tests passed.");
    }

    private static void assertEquals(List<String> actual, List<String> expected, String testName) {
        if (!actual.equals(expected)) {
            throw new AssertionError(testName + " failed. expected=" + expected + ", actual=" + actual);
        }
        System.out.println(testName + " passed");
    }
}
