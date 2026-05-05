# Card Game

## 题目背景

有一副牌：

- 4 种花色
- 每种花色有数字 `1` 到 `9`
- 总共 `4 * 9 = 36` 张牌

游戏开始时：

- 从 deck 里随机发 `16` 张牌到桌面 `table`。
- 每一轮从桌面上选择 `3` 张牌。
- 如果这 3 张牌的数字之和是 `15`，这一轮得 `15` 分。
- 抽走这 3 张牌后，从 deck 里补牌，尽量让桌面继续保持 16 张。
- 如果 deck 空了，或者桌面上再也找不到和为 15 的三张牌，游戏结束。

满分：

```text
36 张牌 / 每轮 3 张 = 12 轮
每轮 15 分
满分 = 12 * 15 = 180
```

面试分 4 个 stage：

1. Debug draw method：unittest 失败，因为有的牌不是从桌面现有的牌里抽的。
2. 实现 naive 抽牌策略：每轮任意找三张加起来为 15 的牌，先保证本轮得分。
3. Measure 策略效果：simulate 多局，看多少比例能拿满分。
4. 优化策略：用 backtracking 尝试所有抽牌方式，选择总分最高的策略。

## 数据模型

### 为什么需要 Card identity

牌有重复数字。

例如：

```text
Red 5
Blue 5
Green 5
Black 5
```

所以不能只用数字 `5` 表示一张牌，否则 remove 的时候不知道移除哪一张。

推荐用：

```python
from dataclasses import dataclass


@dataclass(frozen=True)
class Card:
    suit: str
    rank: int
```

`frozen=True` 可以让 `Card` hashable，方便放到 set 或作为 dict key。

## Stage 1：Debug draw method

### 问题描述

已有代码的 `draw_cards(cards)` 有 bug：

- 它没有检查被抽走的 3 张牌是否都在当前桌面 `table` 上。
- 测试里可能传入一张不存在于桌面的牌，代码仍然加分并移除。
- 这会导致游戏状态不合法。

### Buggy code

```python
class Game:
    def __init__(self, deck, table_size=16):
        self.deck = deck[:]
        self.table_size = table_size
        self.table = []
        self.score = 0
        self._deal_initial()

    def _deal_initial(self):
        while self.deck and len(self.table) < self.table_size:
            self.table.append(self.deck.pop())

    def draw_cards(self, cards):
        # BUG 1: does not check all cards are on the table
        # BUG 2: removes by rank-like logic in some implementations
        if len(cards) != 3:
            return False
        if sum(card.rank for card in cards) != 15:
            return False

        for card in cards:
            if card in self.table:
                self.table.remove(card)

        self.score += 15
        self._replenish()
        return True

    def _replenish(self):
        while self.deck and len(self.table) < self.table_size:
            self.table.append(self.deck.pop())
```

### Bug

如果 `cards` 里有一张不在 `self.table` 中：

```python
for card in cards:
    if card in self.table:
        self.table.remove(card)
```

这段代码会跳过不存在的牌，但仍然：

```python
self.score += 15
return True
```

这不合法。

### 修复思路

必须先验证：

- 正好 3 张牌。
- 三张牌都来自当前 table。
- 三张牌是三张不同的 card identity。
- 三张 rank 之和为 15。

只有全部满足，才能 remove 和加分。

### Fixed code

```python
class Game:
    def __init__(self, deck, table_size=16):
        self.deck = deck[:]
        self.table_size = table_size
        self.table = []
        self.score = 0
        self._deal_initial()

    def _deal_initial(self):
        while self.deck and len(self.table) < self.table_size:
            self.table.append(self.deck.pop())

    def _replenish(self):
        while self.deck and len(self.table) < self.table_size:
            self.table.append(self.deck.pop())

    def draw_cards(self, cards):
        if len(cards) != 3:
            return False

        # The same card identity cannot be used twice.
        if len(set(cards)) != 3:
            return False

        table_set = set(self.table)
        if any(card not in table_set for card in cards):
            return False

        if sum(card.rank for card in cards) != 15:
            return False

        for card in cards:
            self.table.remove(card)

        self.score += 15
        self._replenish()
        return True
```

## Stage 2：Naive strategy

### 问题描述

实现一个简单策略：

- 每一轮从桌面上找任意三张牌，使 rank sum 为 15。
- 找到就抽走。
- 先不保证全局最优。

这类似 3Sum，但数据规模很小，桌面最多 16 张，直接三重循环也可以。

### Code

```python
def find_any_valid_triple(table):
    n = len(table)
    for i in range(n):
        for j in range(i + 1, n):
            for k in range(j + 1, n):
                cards = [table[i], table[j], table[k]]
                if sum(card.rank for card in cards) == 15:
                    return cards
    return None


def play_naive(game):
    while True:
        triple = find_any_valid_triple(game.table)
        if triple is None:
            break
        game.draw_cards(triple)
    return game.score
```

### 优点

- 容易实现。
- 能通过基本测试。
- 每轮都能保证当前这一轮得 15 分。

### 缺点

- Greedy，不保证总分最优。
- 当前随便抽走一组三张牌，可能破坏后续更好的组合。
- 模拟里可能只有一部分牌局能满分。

## Stage 3：Measure strategy

### 问题描述

如何衡量 naive strategy 好不好？

可以随机生成很多局游戏，统计：

- 平均得分
- 满分比例
- 最低/最高得分

例如 naive 可能只有约 `40%` 左右的牌局能拿满分，具体取决于 shuffle 和测试数据。

### Deck generator

```python
import random


SUITS = ["R", "G", "B", "Y"]


def make_deck():
    return [Card(suit, rank) for suit in SUITS for rank in range(1, 10)]


def shuffled_deck(seed=None):
    deck = make_deck()
    rng = random.Random(seed)
    rng.shuffle(deck)
    return deck
```

### Simulation code

```python
def simulate(strategy, num_games=1000, seed=0):
    full_score = 180
    scores = []
    perfect = 0

    for i in range(num_games):
        deck = shuffled_deck(seed + i)
        game = Game(deck)
        score = strategy(game)
        scores.append(score)
        if score == full_score:
            perfect += 1

    return {
        "num_games": num_games,
        "average_score": sum(scores) / len(scores),
        "perfect_rate": perfect / num_games,
        "min_score": min(scores),
        "max_score": max(scores),
    }
```

### 面试解释

如果面试官问“你的策略有多好”，不要只说复杂度，也可以说：

- 我可以跑 simulation。
- 用随机 seed 生成固定数量牌局。
- 比较 naive 和 optimized 的平均分、满分率。

这是一个 practical engineering answer。

## Stage 4：Optimized strategy with backtracking

### 问题描述

Naive strategy 每轮只找任意一组三张和为 15 的牌，不保证最终得分最大。

优化目标：

- 在当前完整 game state 下，尝试所有合法抽牌方式。
- 选择能得到最高总分的下一步。

### 关键澄清

这里有一个现实语义问题：

- 如果玩家不知道 deck 后续顺序，只能基于当前桌面做策略，则无法保证全局最优。
- 如果 simulation/test 里 strategy 可以看到完整 deck 顺序，则可以对完整 game state 做 backtracking。

面试中通常可以先说清楚：

```text
If the strategy is allowed to know the remaining deck order, I can do full backtracking over game states.
If not, I can optimize only based on visible table, or use expected value / simulation.
```

下面默认 strategy 知道当前 `table` 和剩余 `deck` 顺序。

### State design

Game state 可以表示为：

```python
(table_tuple, deck_tuple)
```

每一步：

1. 从 `table` 里找所有 valid triples。
2. 对每个 triple：
   - 从 table 删除这 3 张。
   - 从 deck 补牌到 table_size。
   - 递归计算后续最高得分。
3. 选择总分最高的 triple。

### Helper functions

```python
from functools import lru_cache
from itertools import combinations


def valid_triples(table):
    result = []
    for triple in combinations(table, 3):
        if sum(card.rank for card in triple) == 15:
            result.append(triple)
    return result


def apply_move(table, deck, triple, table_size=16):
    table_list = list(table)
    deck_list = list(deck)

    for card in triple:
        table_list.remove(card)

    while deck_list and len(table_list) < table_size:
        table_list.append(deck_list.pop())

    return tuple(table_list), tuple(deck_list)
```

### Max score from state

```python
def max_score_from_state(table, deck, table_size=16):
    @lru_cache(None)
    def dfs(table_state, deck_state):
        triples = valid_triples(table_state)
        if not triples:
            return 0

        best = 0
        for triple in triples:
            next_table, next_deck = apply_move(table_state, deck_state, triple, table_size)
            best = max(best, 15 + dfs(next_table, next_deck))

        return best

    return dfs(tuple(table), tuple(deck))
```

### Returning the actual first move

如果需要真正 play game，不只是算分，需要返回最佳第一步。

```python
def choose_best_triple(table, deck, table_size=16):
    @lru_cache(None)
    def dfs(table_state, deck_state):
        triples = valid_triples(table_state)
        if not triples:
            return 0

        best = 0
        for triple in triples:
            next_table, next_deck = apply_move(table_state, deck_state, triple, table_size)
            best = max(best, 15 + dfs(next_table, next_deck))
        return best

    best_triple = None
    best_score = -1

    for triple in valid_triples(tuple(table)):
        next_table, next_deck = apply_move(tuple(table), tuple(deck), triple, table_size)
        score = 15 + dfs(next_table, next_deck)
        if score > best_score:
            best_score = score
            best_triple = list(triple)

    return best_triple
```

### Play optimized

```python
def play_optimized(game):
    while True:
        triple = choose_best_triple(game.table, game.deck, game.table_size)
        if triple is None:
            break
        game.draw_cards(triple)
    return game.score
```

### 优点

- 比 greedy 更接近全局最优。
- 能避免“当前拿一组三张，导致后面无牌可拿”的局部最优问题。
- 在桌面只有 16 张、总共 36 张牌的情况下，配合 memo 可以跑得动不少测试。

### 缺点

- 最坏情况仍然指数级。
- 状态里包含 table 和 deck，memo key 可能很多。
- 如果每次都在真实 play 中重新算，会重复做大量搜索；可以把 dfs cache 放到 strategy object 里复用。
- 如果真实游戏不允许知道 deck 顺序，这个策略使用了未来信息。

## 完整参考实现

```python
from dataclasses import dataclass
from functools import lru_cache
from itertools import combinations
import random


@dataclass(frozen=True)
class Card:
    suit: str
    rank: int


SUITS = ["R", "G", "B", "Y"]


def make_deck():
    return [Card(suit, rank) for suit in SUITS for rank in range(1, 10)]


def shuffled_deck(seed=None):
    deck = make_deck()
    rng = random.Random(seed)
    rng.shuffle(deck)
    return deck


class Game:
    def __init__(self, deck, table_size=16):
        self.deck = deck[:]
        self.table_size = table_size
        self.table = []
        self.score = 0
        self._deal_initial()

    def _deal_initial(self):
        self._replenish()

    def _replenish(self):
        while self.deck and len(self.table) < self.table_size:
            self.table.append(self.deck.pop())

    def draw_cards(self, cards):
        if len(cards) != 3:
            return False
        if len(set(cards)) != 3:
            return False

        table_set = set(self.table)
        if any(card not in table_set for card in cards):
            return False

        if sum(card.rank for card in cards) != 15:
            return False

        for card in cards:
            self.table.remove(card)

        self.score += 15
        self._replenish()
        return True


def valid_triples(table):
    return [
        triple
        for triple in combinations(table, 3)
        if sum(card.rank for card in triple) == 15
    ]


def find_any_valid_triple(table):
    triples = valid_triples(table)
    if not triples:
        return None
    return list(triples[0])


def play_naive(game):
    while True:
        triple = find_any_valid_triple(game.table)
        if triple is None:
            break
        game.draw_cards(triple)
    return game.score


def apply_move(table, deck, triple, table_size=16):
    table_list = list(table)
    deck_list = list(deck)

    for card in triple:
        table_list.remove(card)

    while deck_list and len(table_list) < table_size:
        table_list.append(deck_list.pop())

    return tuple(table_list), tuple(deck_list)


class BacktrackingStrategy:
    def __init__(self, table_size=16):
        self.table_size = table_size

    def choose(self, table, deck):
        table = tuple(table)
        deck = tuple(deck)
        table_size = self.table_size

        @lru_cache(None)
        def dfs(table_state, deck_state):
            triples = valid_triples(table_state)
            if not triples:
                return 0

            best = 0
            for triple in triples:
                next_table, next_deck = apply_move(table_state, deck_state, triple, table_size)
                best = max(best, 15 + dfs(next_table, next_deck))
            return best

        best_triple = None
        best_score = -1

        for triple in valid_triples(table):
            next_table, next_deck = apply_move(table, deck, triple, table_size)
            score = 15 + dfs(next_table, next_deck)
            if score > best_score:
                best_score = score
                best_triple = list(triple)

        return best_triple


def play_optimized(game):
    strategy = BacktrackingStrategy(game.table_size)
    while True:
        triple = strategy.choose(game.table, game.deck)
        if triple is None:
            break
        game.draw_cards(triple)
    return game.score


def simulate(strategy_func, num_games=100, seed=0):
    full_score = 180
    scores = []
    perfect = 0

    for i in range(num_games):
        game = Game(shuffled_deck(seed + i))
        score = strategy_func(game)
        scores.append(score)
        if score == full_score:
            perfect += 1

    return {
        "num_games": num_games,
        "average_score": sum(scores) / len(scores),
        "perfect_rate": perfect / num_games,
        "min_score": min(scores),
        "max_score": max(scores),
    }
```

## 测试用例

```python
def test_draw_rejects_card_not_on_table():
    deck = make_deck()
    game = Game(deck)

    table_cards = game.table[:2]
    fake_card = Card("Z", 15 - table_cards[0].rank - table_cards[1].rank)
    old_score = game.score
    old_table = game.table[:]

    assert game.draw_cards(table_cards + [fake_card]) is False
    assert game.score == old_score
    assert game.table == old_table


def test_draw_rejects_duplicate_same_card_identity():
    game = Game(make_deck())
    card = game.table[0]
    other = None
    for c1 in game.table:
        for c2 in game.table:
            if c1 != c2 and card.rank + c1.rank + c2.rank == 15:
                other = (c1, c2)
                break
        if other:
            break

    if other:
        assert game.draw_cards([card, card, other[0]]) is False


def test_naive_can_score_some_points():
    game = Game(shuffled_deck(0))
    score = play_naive(game)
    assert score % 15 == 0
    assert 0 <= score <= 180


def test_optimized_at_least_naive_for_same_deck():
    deck = shuffled_deck(1)

    naive_game = Game(deck[:])
    optimized_game = Game(deck[:])

    naive_score = play_naive(naive_game)
    optimized_score = play_optimized(optimized_game)

    assert optimized_score >= naive_score


def test_simulation_result_shape():
    result = simulate(play_naive, num_games=10, seed=0)
    assert result["num_games"] == 10
    assert 0 <= result["perfect_rate"] <= 1
    assert result["min_score"] <= result["average_score"] <= result["max_score"]
```

## 复杂度

### Naive strategy

桌面最多 `T = 16` 张。

每一轮找 triple：

```text
O(T^3)
```

最多 12 轮，所以整体很小。

### Backtracking strategy

每个 state 可能有很多 valid triples。

最坏情况：

```text
O(branching_factor ^ number_of_rounds)
```

但本题：

- 总牌数 36
- 每轮抽 3 张
- 最多 12 轮
- 桌面最多 16 张

配合 memo 后通常可以接受。

## 面试解释重点

### Stage 1

- 不是只要三张牌 sum 为 15 就能抽。
- 必须确认这三张牌都在当前桌面上。
- 牌要按 identity 判断，不能只按 rank。

### Stage 2

- Naive 可以像 3Sum：
  - 从 table 里找任意三张 sum 为 15 的牌。
  - 找到就抽。
- 它只保证当前轮得分，不保证全局最优。

### Stage 3

- 用 simulation measure 策略。
- 指标可以是：
  - average score
  - perfect rate
  - min/max score
- 这是一个 practical way to evaluate strategy。

### Stage 4

- 优化策略用 backtracking：
  - 枚举当前所有合法 triple。
  - 对每个 triple 模拟后续游戏。
  - 选择总分最高的下一步。
- 如果完整 deck 顺序可见，可以做全局最优搜索。
- 如果真实游戏不允许看未来 deck，只能做基于当前 table 的局部策略或 expected value simulation。

## 常见 follow-up

### Follow-up 1：如何让 backtracking 更快？

- Memoization：
  - key = `(tuple(table), tuple(deck))`
- Canonicalize table：
  - table 顺序不影响可选 triple，可以排序后作为 key。
- Alpha pruning / upper bound：
  - 如果当前分数 + 剩余最多可得分 <= best，就剪枝。

### Follow-up 2：如何 canonicalize table？

因为 `Card` 可以排序：

```python
def canonical_cards(cards):
    return tuple(sorted(cards, key=lambda c: (c.suit, c.rank)))
```

但要注意：

- deck 顺序会影响未来补牌，不能随便排序 deck。
- table 可以排序，因为桌面顺序不影响合法操作。

### Follow-up 3：如果只能看到当前 table，看不到 deck？

这时无法做完整全局最优。

可以做：

- greedy heuristic
- lookahead depth-limited search
- Monte Carlo simulation：
  - 随机假设未来 deck 顺序
  - 对每个 candidate triple 估算 expected score
  - 选 expected score 最高的 triple

### Follow-up 4：如果面试官问“为什么 optimized 不是 100% 满分？”

可能原因：

- 初始 table 和 deck 顺序决定了某些局没有办法组成 12 组三张和为 15 的组合。
- 即使全局最优，也不代表所有随机牌局都可满分。
- 如果 backtracking 使用完整 deck 仍不是 100%，说明有些 game state 本身不可完美完成。
