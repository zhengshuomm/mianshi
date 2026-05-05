# Max Unique Character Subset

## 题目背景

给定一个字符串数组 `words`，需要选择一个子集，使得：

- 子集中任意两个 word 之间不能有重复字符。
- 每个被选中的 word 自身内部也不能有重复字符。
- 目标是让最终子集覆盖的 unique characters 数量最大。
- 如果有多个合法答案，返回任意一个最大覆盖字符数的子集即可。

这个题在面试中通常分两部分：

1. Debug 已有 helper function：现有代码统计 unique character 的逻辑有 bug。
2. 实现 `solver(words)`：找到覆盖 unique characters 最多、且互不重复的 word subset。

## 例子

```python
words = ["jan", "feb", "mar", "apr", "may", "jun", "jul", "aug", "sep", "oct", "nov", "dec"]
```

一个合法输出：

```python
["feb", "mar", "jun", "oct"]
```

原因：

- `"feb"` 覆盖 `f, e, b`
- `"mar"` 覆盖 `m, a, r`
- `"jun"` 覆盖 `j, u, n`
- `"oct"` 覆盖 `o, c, t`
- 这些字符互不重复。
- 总共覆盖 12 个 unique characters。

注意：

```python
["jan", "feb", "may"]
```

不合法，因为：

- `"jan"` 和 `"may"` 都包含 `a`
- 子集内出现了重复字符。

## Stage 1：Debug helper function

### 问题描述

已有 helper function 用于统计一个 word 里 unique character 的数量，但它把重复字符也算进去了，导致测试失败。

### Buggy code

```python
def count_unique_chars(word):
    count = 0
    seen = set()
    for ch in word:
        if ch not in seen:
            count += 1
        # BUG: forgot to add ch into seen
    return count
```

例如：

```python
count_unique_chars("aba")
```

错误返回：

```text
3
```

正确返回：

```text
2
```

### 修复

```python
def count_unique_chars(word):
    seen = set()
    for ch in word:
        seen.add(ch)
    return len(seen)
```

或者：

```python
def count_unique_chars(word):
    return len(set(word))
```

## Stage 2：实现 solver

### 题目澄清

面试时最好先复述题意并确认：

- 我们要返回一个 subset of words，不是返回长度。
- subset 里的 word 不能有字符 overlap。
- word 自身如果有重复字符，例如 `"letter"`，它不能被选，因为它内部已经重复。
- 目标是 maximize covered unique characters。
- 多个答案都达到最大 unique character 数时，任意一个都可以。

### 输入输出

```python
def solver(words: list[str]) -> list[str]:
    ...
```

返回：

```python
list[str]
```

要求：

- 返回的 word subset 合法。
- 覆盖 unique character 数最大。

## 常见错误

### 错误 1：只检查 word 之间是否重复，不检查 word 自身

```python
words = ["aa", "bc"]
```

`"aa"` 自身内部重复，不应该被选。

### 错误 2：用全局 set 做 backtracking，但回溯时删除错字符

如果 word 中有重复字符，或者回溯逻辑没有对应 restore，很容易把 shared state 改坏。

### 错误 3：递归 backtracking 在大输入上撞 max recursion depth

如果 `words` 很多，递归深度可能超过 Python 默认限制。

可以改成：

- iterative DP
- 或者保留递归但加 pruning，并且确认输入规模不会太大

### 错误 4：bitmask 只支持 26 个 lowercase，忘记题目可能包含数字

描述里可见输入可能包含：

- lowercase letters：`a-z`
- digits：`0-9`

所以字符上限可能是：

```text
26 + 10 = 36
```

如果题目明确只包含 lowercase，则上限是 26。

## 解法 1：Backtracking with set

这是最容易先写出来的白板解法。

### 思路

- 先过滤掉自身有重复字符的 word。
- DFS 每个 word：
  - 选：如果它和当前 used chars 没有冲突。
  - 不选：跳过。
- 用 global best 保存目前覆盖字符数最多的 subset。

### Code

```python
def solver_backtracking(words):
    valid_words = []
    for word in words:
        chars = set(word)
        if len(chars) == len(word):
            valid_words.append((word, chars))

    best = []
    best_count = 0

    def dfs(index, used, chosen):
        nonlocal best, best_count

        if len(used) > best_count:
            best_count = len(used)
            best = chosen[:]

        if index == len(valid_words):
            return

        word, chars = valid_words[index]

        # Option 1: skip current word
        dfs(index + 1, used, chosen)

        # Option 2: take current word if no overlap
        if used.isdisjoint(chars):
            chosen.append(word)
            dfs(index + 1, used | chars, chosen)
            chosen.pop()

    dfs(0, set(), [])
    return best
```

### 优点

- 写起来直观，适合先通过小测试。
- 容易解释。
- 不需要 bitmask 细节。

### 缺点

- 时间复杂度最坏 `O(2^N)`。
- 递归深度是 `N`，输入很大时可能撞 `max recursion depth`。
- set copy 有额外成本。

## 解法 2：Backtracking + bitmask + pruning

### 字符编码

如果输入只包含 lowercase 和 digits，可以映射到 0-35：

```python
def char_index(ch):
    if "a" <= ch <= "z":
        return ord(ch) - ord("a")
    if "0" <= ch <= "9":
        return 26 + ord(ch) - ord("0")
    raise ValueError(f"unsupported character: {ch}")
```

### word 转 bitmask

如果 word 自身有重复字符，返回 `None`，表示不可选。

```python
def word_to_mask(word):
    mask = 0
    for ch in word:
        bit = 1 << char_index(ch)
        if mask & bit:
            return None
        mask |= bit
    return mask
```

### 剪枝

如果当前已经覆盖了所有可能字符，可以提前返回。

例如只支持 lowercase + digits：

```python
MAX_CHARS = 36
```

如果只支持 lowercase：

```python
MAX_CHARS = 26
```

但注意：

- 这个剪枝只能在已经达到理论上限时提前结束。
- 它不能解决所有大输入的指数复杂度。
- 如果输入 words 很多但字符种类只有 36，真正更稳的是 iterative DP by mask。

### Code

```python
def solver_bitmask_dfs(words):
    items = []
    for word in words:
        mask = word_to_mask(word)
        if mask is not None:
            items.append((word, mask))

    max_chars = 36
    best_words = []
    best_count = 0
    found_optimal = False

    def dfs(index, used_mask, chosen):
        nonlocal best_words, best_count, found_optimal

        if found_optimal:
            return

        count = used_mask.bit_count()
        if count > best_count:
            best_count = count
            best_words = chosen[:]
            if best_count == max_chars:
                found_optimal = True
                return

        if index == len(items):
            return

        word, mask = items[index]

        # skip
        dfs(index + 1, used_mask, chosen)

        # take
        if used_mask & mask == 0:
            chosen.append(word)
            dfs(index + 1, used_mask | mask, chosen)
            chosen.pop()

    dfs(0, 0, [])
    return best_words
```

### 优点

- 比 set 更快。
- overlap check 变成 `used_mask & mask == 0`。
- `bit_count()` 可以快速算 unique character 数。

### 缺点

- 仍然是递归 backtracking，最坏 `O(2^N)`。
- 输入很多时仍可能撞 recursion depth。
- bitmask 映射容易写错，尤其数字和字母混合时。

## 解法 3：Iterative DP by mask

这版更适合避免 recursion depth。

### 核心思想

因为字符全集最多 36 个，状态可以用 `mask` 表示。

维护：

```python
dp[mask] = subset of words that covers exactly this mask
```

遍历每个 word：

- 如果 word mask 和已有 mask 不冲突，就可以生成新状态：

```python
new_mask = old_mask | word_mask
```

最后选择 `bit_count()` 最大的 mask。

### Code

```python
def char_index(ch):
    if "a" <= ch <= "z":
        return ord(ch) - ord("a")
    if "0" <= ch <= "9":
        return 26 + ord(ch) - ord("0")
    raise ValueError(f"unsupported character: {ch}")


def word_to_mask(word):
    mask = 0
    for ch in word:
        bit = 1 << char_index(ch)
        if mask & bit:
            return None
        mask |= bit
    return mask


def solver(words):
    items = []
    for word in words:
        mask = word_to_mask(word)
        if mask is not None:
            items.append((word, mask))

    # dp maps used character mask -> chosen subset
    dp = {0: []}
    best_mask = 0

    for word, word_mask in items:
        updates = {}

        for used_mask, subset in dp.items():
            if used_mask & word_mask:
                continue

            new_mask = used_mask | word_mask
            if new_mask not in dp and new_mask not in updates:
                updates[new_mask] = subset + [word]

            if new_mask.bit_count() > best_mask.bit_count():
                best_mask = new_mask

        dp.update(updates)

    return dp[best_mask]
```

### 为什么这版避免 recursion depth

- 没有递归。
- 状态数量最多由可达 `mask` 数决定。
- 理论上最多 `2^C`，其中 `C` 是字符全集大小。
- 如果字符集是 lowercase + digits，则 `C = 36`，理论仍然很大，但实际可达状态通常远小于 `2^36`。

### 优点

- 不会撞 Python recursion depth。
- 用 mask 表达状态，overlap 检查快。
- 很容易返回实际 subset，而不是只返回最大长度。

### 缺点

- 状态数可能很多，最坏仍然指数级。
- `dp[mask] = subset list` 会复制 list，有额外内存。
- 如果只需要最大值，可以只存 parent pointer 来省内存，但代码会复杂一些。

## 更省内存的 DP：存 parent pointer

如果面试官追问内存优化，可以说：

- 不在 `dp` 里直接存完整 subset。
- 存 parent pointer：

```python
parent[new_mask] = (old_mask, word)
```

最后从 `best_mask` 反向恢复答案。

### Code

```python
def solver_dp_parent(words):
    items = []
    for word in words:
        mask = word_to_mask(word)
        if mask is not None:
            items.append((word, mask))

    reachable = {0}
    parent = {}
    best_mask = 0

    for word, word_mask in items:
        new_states = []

        for used_mask in list(reachable):
            if used_mask & word_mask:
                continue

            new_mask = used_mask | word_mask
            if new_mask in reachable:
                continue

            new_states.append((new_mask, used_mask, word))

            if new_mask.bit_count() > best_mask.bit_count():
                best_mask = new_mask

        for new_mask, old_mask, word in new_states:
            reachable.add(new_mask)
            parent[new_mask] = (old_mask, word)

    result = []
    cur = best_mask
    while cur != 0:
        prev, word = parent[cur]
        result.append(word)
        cur = prev

    result.reverse()
    return result
```

## 测试用例

```python
def is_valid_subset(subset):
    seen = set()
    for word in subset:
        if len(set(word)) != len(word):
            return False
        for ch in word:
            if ch in seen:
                return False
            seen.add(ch)
    return True


def unique_count(subset):
    return len(set("".join(subset)))


def run_tests():
    months = ["jan", "feb", "mar", "apr", "may", "jun", "jul", "aug", "sep", "oct", "nov", "dec"]
    ans = solver(months)
    assert is_valid_subset(ans)
    assert unique_count(ans) == 12

    words = ["aa", "bc", "de"]
    ans = solver(words)
    assert is_valid_subset(ans)
    assert unique_count(ans) == 4
    assert "aa" not in ans

    words = ["ab", "cd", "ef", "ad"]
    ans = solver(words)
    assert is_valid_subset(ans)
    assert unique_count(ans) == 6

    words = ["abc", "def", "ghij", "ad"]
    ans = solver(words)
    assert is_valid_subset(ans)
    assert unique_count(ans) == 10

    words = ["a1", "b2", "c3", "ab"]
    ans = solver(words)
    assert is_valid_subset(ans)
    assert unique_count(ans) == 6

    words = []
    ans = solver(words)
    assert ans == []

    print("all tests passed")
```

## 复杂度

设：

- `N` = words 数量
- `C` = 字符全集大小，例如 lowercase 是 26，lowercase + digits 是 36
- `S` = 实际可达 mask 状态数，最坏 `2^C`

Backtracking:

- Time: `O(2^N * average_word_length)`
- Space: `O(N + C)`
- 可能撞 recursion depth

Bitmask DFS:

- Time: `O(2^N)`
- Space: `O(N)`
- overlap 检查更快，但仍可能撞 recursion depth

Iterative DP:

- Time: `O(N * S)`
- Space:
  - 直接存 subset：`O(S * average_subset_size)`
  - parent pointer：`O(S)`
- 不会撞 recursion depth

## 面试解释重点

- 先确认题意：
  - 子集内 word 之间不能 share character。
  - word 自身也不能有 duplicate。
  - maximize unique character coverage。

- 先写 backtracking：
  - 能快速过小测试。
  - 逻辑最容易说清楚。

- 再优化 bitmask：
  - 把 set overlap 改成 bit operation。
  - `mask & word_mask == 0` 表示无冲突。

- 如果 input 很大：
  - 递归可能撞 `max recursion depth`。
  - 改 iterative DP by mask。

- 如果面试官提示输入只有 lowercase + digits：
  - 可以说理论最大 unique char 是 36。
  - 如果已经达到 36，可以 early return。
  - 但 early return 只是剪枝，不是完整解决指数复杂度。

## 可能的 follow-up

### Follow-up 1：如果只要求返回最大 unique count，不要求 subset？

可以只存最大长度：

```python
best = max(mask.bit_count() for mask in dp)
```

不用保存 subset 或 parent。

### Follow-up 2：如果字符集不止 lowercase + digits？

可以动态建立 char mapping：

```python
char_to_id = {}
```

但要注意：

- 字符种类越多，bitmask 状态空间越大。
- 如果 `C` 很大，bitmask DP 不再适合。

### Follow-up 3：如果 words 数量非常大？

可以先做预处理：

- 删除自身重复字符的 word。
- 删除被 dominated 的 word：
  - 如果 word A 的 mask 是 word B 的子集，并且 A 不比 B 有其他优势，可以考虑剪枝。
- 按 word 覆盖字符数从大到小排序，backtracking 更容易早点找到好答案。
- 用 upper bound pruning：
  - 预计算 suffix possible mask。
  - 如果 `used_count + suffix_possible_count <= best_count`，可以剪枝。

## 带 upper bound pruning 的 DFS

如果想保留 DFS，同时减少搜索量：

```python
def solver_dfs_pruned(words):
    items = []
    for word in words:
        mask = word_to_mask(word)
        if mask is not None:
            items.append((word, mask))

    # Larger words first usually finds good answer earlier.
    items.sort(key=lambda x: x[1].bit_count(), reverse=True)

    n = len(items)
    suffix_masks = [0] * (n + 1)
    for i in range(n - 1, -1, -1):
        suffix_masks[i] = suffix_masks[i + 1] | items[i][1]

    best = []
    best_count = 0

    def dfs(i, used_mask, chosen):
        nonlocal best, best_count

        current_count = used_mask.bit_count()
        if current_count > best_count:
            best_count = current_count
            best = chosen[:]

        if i == n:
            return

        # Upper bound: even if we could take all remaining characters,
        # we still cannot beat current best.
        possible = used_mask | suffix_masks[i]
        if possible.bit_count() <= best_count:
            return

        word, mask = items[i]

        if used_mask & mask == 0:
            chosen.append(word)
            dfs(i + 1, used_mask | mask, chosen)
            chosen.pop()

        dfs(i + 1, used_mask, chosen)

    dfs(0, 0, [])
    return best
```

这版仍然可能有递归深度问题，但搜索量会小很多。面试中如果时间紧，建议优先写 iterative DP。
