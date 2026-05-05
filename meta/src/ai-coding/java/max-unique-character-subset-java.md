# Max Unique Character Subset Java Version

## 题目阶段

1. Debug helper：修复 unique character count。
2. 实现 solver：找一个 word subset，使得字符互不重复，并最大化 unique character 数。
3. 先写 backtracking，再用 bitmask 优化。
4. 如果输入很大，递归可能 stack overflow，改 iterative DP。

## Java 参考实现：Iterative DP

支持字符：

- `a-z`
- `0-9`

最大字符数是 36。

```java
import java.util.*;

public class MaxUniqueCharacterSubset {
    public static List<String> solve(List<String> words) {
        List<Item> items = new ArrayList<>();

        for (String word : words) {
            Long mask = toMask(word);
            if (mask != null) {
                items.add(new Item(word, mask));
            }
        }

        Map<Long, List<String>> dp = new HashMap<>();
        dp.put(0L, new ArrayList<>());

        long bestMask = 0L;

        for (Item item : items) {
            Map<Long, List<String>> updates = new HashMap<>();

            for (Map.Entry<Long, List<String>> entry : dp.entrySet()) {
                long usedMask = entry.getKey();
                if ((usedMask & item.mask) != 0) {
                    continue;
                }

                long newMask = usedMask | item.mask;
                if (dp.containsKey(newMask) || updates.containsKey(newMask)) {
                    continue;
                }

                List<String> subset = new ArrayList<>(entry.getValue());
                subset.add(item.word);
                updates.put(newMask, subset);

                if (Long.bitCount(newMask) > Long.bitCount(bestMask)) {
                    bestMask = newMask;
                }
            }

            dp.putAll(updates);

            // lowercase + digits upper bound
            if (Long.bitCount(bestMask) == 36) {
                break;
            }
        }

        return dp.get(bestMask);
    }

    private static Long toMask(String word) {
        long mask = 0L;
        for (char ch : word.toCharArray()) {
            int idx = charIndex(ch);
            long bit = 1L << idx;
            if ((mask & bit) != 0) {
                return null; // duplicate char inside the word
            }
            mask |= bit;
        }
        return mask;
    }

    private static int charIndex(char ch) {
        if (ch >= 'a' && ch <= 'z') {
            return ch - 'a';
        }
        if (ch >= '0' && ch <= '9') {
            return 26 + (ch - '0');
        }
        throw new IllegalArgumentException("unsupported character: " + ch);
    }

    static class Item {
        String word;
        long mask;

        Item(String word, long mask) {
            this.word = word;
            this.mask = mask;
        }
    }

    public static boolean isValidSubset(List<String> subset) {
        Set<Character> seen = new HashSet<>();
        for (String word : subset) {
            Set<Character> local = new HashSet<>();
            for (char ch : word.toCharArray()) {
                if (!local.add(ch)) return false;
                if (!seen.add(ch)) return false;
            }
        }
        return true;
    }

    public static int uniqueCount(List<String> subset) {
        Set<Character> seen = new HashSet<>();
        for (String word : subset) {
            for (char ch : word.toCharArray()) {
                seen.add(ch);
            }
        }
        return seen.size();
    }
}
```

## Backtracking 版本

```java
public static List<String> solveBacktracking(List<String> words) {
    List<Item> items = new ArrayList<>();
    for (String word : words) {
        Long mask = toMask(word);
        if (mask != null) items.add(new Item(word, mask));
    }

    List<String> best = new ArrayList<>();
    dfs(items, 0, 0L, new ArrayList<>(), best);
    return best;
}

private static void dfs(
        List<Item> items,
        int index,
        long usedMask,
        List<String> chosen,
        List<String> best
) {
    if (Long.bitCount(usedMask) > uniqueCount(best)) {
        best.clear();
        best.addAll(chosen);
    }

    if (index == items.size()) return;

    Item item = items.get(index);

    dfs(items, index + 1, usedMask, chosen, best);

    if ((usedMask & item.mask) == 0) {
        chosen.add(item.word);
        dfs(items, index + 1, usedMask | item.mask, chosen, best);
        chosen.remove(chosen.size() - 1);
    }
}
```

## 面试重点

- Word 自身有重复字符时不能选。
- 子集内 word 之间不能有 overlap。
- Bitmask overlap check：`(usedMask & wordMask) == 0`。
- Java 里 lowercase + digits 共 36 位，`long` 足够。
- 如果字符集大于 64，需要 `BitSet` 或动态 mapping。

## 复杂度

- Backtracking：`O(2^N)`
- Iterative DP：`O(N * S)`，`S` 是可达 mask 数。

