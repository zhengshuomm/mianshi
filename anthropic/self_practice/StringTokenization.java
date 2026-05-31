package anthropic.self_practice;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

class TrieNode {
    Map<Character, TrieNode> children = new HashMap<>();
    String id;
}

// Q6
// Tokenization Strategies
// Algorithm Complexity
// Ambiguity and Optimal Tokenization
// Handling UNK in Real Systems
// done
public class StringTokenization {

    public List<String> tokenize(String text, List<String> dictionary) {
        TrieNode root = buildTrie(dictionary);

        // assertEquals(
        //     tokenizer.tokenize("helloworld", List.of("hello:H", "world:W")),
        //     List.of("H", "W"),
        //     "basic two-token match"
        // );
        List<String> res = new ArrayList<>();
        int i = 0;
        int n = text.length(); 
        while ( i < n) {
            TrieNode node = root;
            String bestId = null;
            int bestEnd = i;
            
            int j = i;
            while (j < n) {
                char c = text.charAt(j);
                TrieNode next = node.children.get(c);
                if (next == null) {
                    break;
                }

                node = next;
                j ++;

                if (node.id != null) {
                    bestId = node.id;
                    bestEnd = j;
                }
            }

            if (bestId != null) {
                res.add(bestId);
                i = bestEnd;
            } else {
                res.add(String.valueOf(text.charAt(i)));
                i ++;
            }
        }
        return res;
    }

    private TrieNode buildTrie(List<String> dictionary) { 
        TrieNode root =  new TrieNode();
        for (String dict : dictionary) {
            int index = dict.indexOf(":");
            if (index == -1 ) {
                continue;
            }
            String key = dict.substring(0, index);
            String value = dict.substring(index + 1);

            TrieNode node = root;
            for (int i = 0 ; i < key.length() ; i ++) {
                char c = key.charAt(i);
                node.children.putIfAbsent(c, new TrieNode());
                node = node.children.get(c);
            }
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
