# Card Game Java Version

## 题目阶段

1. Debug `drawCards`：抽走的三张牌必须都来自当前桌面。
2. 实现 naive strategy：每轮任意找三张 rank sum 为 15 的牌。
3. 用 simulation 衡量策略表现。
4. 用 backtracking 选择总分最高的策略。

## Java 参考实现

```java
import java.util.*;

public class CardGame {
    static class Card {
        final String suit;
        final int rank;

        Card(String suit, int rank) {
            this.suit = suit;
            this.rank = rank;
        }

        @Override
        public boolean equals(Object o) {
            if (!(o instanceof Card)) return false;
            Card other = (Card) o;
            return rank == other.rank && Objects.equals(suit, other.suit);
        }

        @Override
        public int hashCode() {
            return Objects.hash(suit, rank);
        }

        @Override
        public String toString() {
            return suit + rank;
        }
    }

    static class Game {
        final int tableSize;
        final Deque<Card> deck;
        final List<Card> table;
        int score;

        Game(List<Card> deck) {
            this(deck, 16);
        }

        Game(List<Card> deck, int tableSize) {
            this.tableSize = tableSize;
            this.deck = new ArrayDeque<>(deck);
            this.table = new ArrayList<>();
            this.score = 0;
            replenish();
        }

        boolean drawCards(List<Card> cards) {
            if (cards.size() != 3) return false;
            if (new HashSet<>(cards).size() != 3) return false;

            Set<Card> tableSet = new HashSet<>(table);
            for (Card card : cards) {
                if (!tableSet.contains(card)) {
                    return false;
                }
            }

            int sum = 0;
            for (Card card : cards) {
                sum += card.rank;
            }
            if (sum != 15) return false;

            for (Card card : cards) {
                table.remove(card);
            }

            score += 15;
            replenish();
            return true;
        }

        private void replenish() {
            while (!deck.isEmpty() && table.size() < tableSize) {
                table.add(deck.removeLast());
            }
        }
    }

    static List<Card> makeDeck() {
        List<Card> deck = new ArrayList<>();
        String[] suits = {"R", "G", "B", "Y"};
        for (String suit : suits) {
            for (int rank = 1; rank <= 9; rank++) {
                deck.add(new Card(suit, rank));
            }
        }
        return deck;
    }

    static List<Card> shuffledDeck(long seed) {
        List<Card> deck = makeDeck();
        Collections.shuffle(deck, new Random(seed));
        return deck;
    }

    static List<Card> findAnyValidTriple(List<Card> table) {
        int n = table.size();
        for (int i = 0; i < n; i++) {
            for (int j = i + 1; j < n; j++) {
                for (int k = j + 1; k < n; k++) {
                    Card a = table.get(i);
                    Card b = table.get(j);
                    Card c = table.get(k);
                    if (a.rank + b.rank + c.rank == 15) {
                        return Arrays.asList(a, b, c);
                    }
                }
            }
        }
        return null;
    }

    static int playNaive(Game game) {
        while (true) {
            List<Card> triple = findAnyValidTriple(game.table);
            if (triple == null) break;
            game.drawCards(triple);
        }
        return game.score;
    }

    static List<List<Card>> validTriples(List<Card> table) {
        List<List<Card>> result = new ArrayList<>();
        int n = table.size();
        for (int i = 0; i < n; i++) {
            for (int j = i + 1; j < n; j++) {
                for (int k = j + 1; k < n; k++) {
                    Card a = table.get(i);
                    Card b = table.get(j);
                    Card c = table.get(k);
                    if (a.rank + b.rank + c.rank == 15) {
                        result.add(Arrays.asList(a, b, c));
                    }
                }
            }
        }
        return result;
    }

    static class State {
        final List<Card> table;
        final List<Card> deck;

        State(List<Card> table, List<Card> deck) {
            this.table = canonicalTable(table);
            this.deck = new ArrayList<>(deck);
        }

        @Override
        public boolean equals(Object o) {
            if (!(o instanceof State)) return false;
            State other = (State) o;
            return table.equals(other.table) && deck.equals(other.deck);
        }

        @Override
        public int hashCode() {
            return Objects.hash(table, deck);
        }
    }

    static List<Card> canonicalTable(List<Card> cards) {
        List<Card> copy = new ArrayList<>(cards);
        copy.sort(Comparator.comparing((Card c) -> c.suit).thenComparingInt(c -> c.rank));
        return copy;
    }

    static class BacktrackingStrategy {
        final int tableSize;
        final Map<State, Integer> memo = new HashMap<>();

        BacktrackingStrategy(int tableSize) {
            this.tableSize = tableSize;
        }

        List<Card> choose(List<Card> table, List<Card> deck) {
            List<Card> bestTriple = null;
            int bestScore = -1;

            for (List<Card> triple : validTriples(table)) {
                NextState next = applyMove(table, deck, triple);
                int score = 15 + dfs(next.table, next.deck);
                if (score > bestScore) {
                    bestScore = score;
                    bestTriple = triple;
                }
            }
            return bestTriple;
        }

        private int dfs(List<Card> table, List<Card> deck) {
            State state = new State(table, deck);
            if (memo.containsKey(state)) return memo.get(state);

            int best = 0;
            for (List<Card> triple : validTriples(table)) {
                NextState next = applyMove(table, deck, triple);
                best = Math.max(best, 15 + dfs(next.table, next.deck));
            }

            memo.put(state, best);
            return best;
        }

        private NextState applyMove(List<Card> table, List<Card> deck, List<Card> triple) {
            List<Card> nextTable = new ArrayList<>(table);
            List<Card> nextDeck = new ArrayList<>(deck);

            for (Card card : triple) {
                nextTable.remove(card);
            }

            while (!nextDeck.isEmpty() && nextTable.size() < tableSize) {
                nextTable.add(nextDeck.remove(nextDeck.size() - 1));
            }
            return new NextState(nextTable, nextDeck);
        }
    }

    static class NextState {
        final List<Card> table;
        final List<Card> deck;

        NextState(List<Card> table, List<Card> deck) {
            this.table = table;
            this.deck = deck;
        }
    }

    static int playOptimized(Game game) {
        BacktrackingStrategy strategy = new BacktrackingStrategy(game.tableSize);
        while (true) {
            List<Card> triple = strategy.choose(game.table, new ArrayList<>(game.deck));
            if (triple == null) break;
            game.drawCards(triple);
        }
        return game.score;
    }
}
```

## 面试重点

- `Card` 必须有 identity：同 rank 不等于同一张牌。
- `drawCards` 要先验证三张牌都在 table，再修改状态。
- Naive strategy 是局部 greedy，不保证总分最优。
- Backtracking 可以枚举所有 valid triples，用 memo 缓存 `(table, deck)`。
- Table 顺序不重要，可以 canonicalize；deck 顺序影响补牌，不能排序。

## 复杂度

- Naive 每轮 `O(T^3)`，`T <= 16`。
- Backtracking 最坏指数级，但总牌数 36、最多 12 轮，memo 后可接受部分测试。

