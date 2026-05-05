# Friend Recommendation Java Version

## 题目阶段

1. Debug `validRecommend`：不能推荐自己。
2. 实现 `randomRecommend`。
3. 讨论推荐算法怎么衡量。
4. 实现 mutual friends recommendation。

## Java 参考实现

```java
import java.util.*;

public class FriendRecommendation {
    static class User {
        final int id;
        final Set<Integer> currentFriends;

        User(int id, Set<Integer> currentFriends) {
            this.id = id;
            this.currentFriends = new HashSet<>(currentFriends);
        }

        @Override
        public String toString() {
            return "User(" + id + ")";
        }
    }

    public static boolean validRecommend(User user, List<User> candidates) {
        Set<Integer> seen = new HashSet<>();

        for (User candidate : candidates) {
            if (candidate.id == user.id) return false;
            if (user.currentFriends.contains(candidate.id)) return false;
            if (!seen.add(candidate.id)) return false;
        }

        return true;
    }

    public static List<User> randomRecommend(User user, List<User> users, int k, long seed) {
        List<User> candidates = new ArrayList<>();
        for (User other : users) {
            if (other.id == user.id) continue;
            if (user.currentFriends.contains(other.id)) continue;
            candidates.add(other);
        }

        Collections.shuffle(candidates, new Random(seed));
        return candidates.subList(0, Math.min(k, candidates.size()));
    }

    public static int mutualFriendCount(User user, User candidate) {
        Set<Integer> smaller = user.currentFriends.size() <= candidate.currentFriends.size()
                ? user.currentFriends
                : candidate.currentFriends;
        Set<Integer> larger = smaller == user.currentFriends
                ? candidate.currentFriends
                : user.currentFriends;

        int count = 0;
        for (int friendId : smaller) {
            if (larger.contains(friendId)) count++;
        }
        return count;
    }

    public static List<User> mutualFriendsRecommend(User user, List<User> users, int k) {
        List<ScoredUser> scored = new ArrayList<>();

        for (User candidate : users) {
            if (candidate.id == user.id) continue;
            if (user.currentFriends.contains(candidate.id)) continue;

            int score = mutualFriendCount(user, candidate);
            if (score == 0) continue;

            scored.add(new ScoredUser(candidate, score));
        }

        scored.sort((a, b) -> {
            if (a.score != b.score) return Integer.compare(b.score, a.score);
            return Integer.compare(a.user.id, b.user.id);
        });

        List<User> result = new ArrayList<>();
        for (int i = 0; i < Math.min(k, scored.size()); i++) {
            result.add(scored.get(i).user);
        }
        return result;
    }

    static class ScoredUser {
        User user;
        int score;

        ScoredUser(User user, int score) {
            this.user = user;
            this.score = score;
        }
    }
}
```

## 测试思路

```java
User u1 = new User(1, Set.of(2, 3));
User u2 = new User(2, Set.of(1, 4, 5));
User u3 = new User(3, Set.of(1, 4));
User u4 = new User(4, Set.of(2, 3));
User u5 = new User(5, Set.of(2));

List<User> users = List.of(u1, u2, u3, u4, u5);

List<User> recs = FriendRecommendation.mutualFriendsRecommend(u1, users, 10);
// expected ids: [4, 5]
```

## Big O

- `validRecommend`: `O(k)`
- `randomRecommend`: `O(N)`
- `mutualFriendsRecommend`:
  - mutual count: `O(N * F)`
  - sorting: `O(N log N)`
  - total: `O(N * F + N log N)`

## 面试重点

- `validRecommend` 要检查：
  - 不能推荐自己。
  - 不能推荐已有好友。
  - 不能重复推荐。
- 如果 `User` 只有 `id/currentFriends`，不要假设 school、location、group。
- 当前能用的主要 signal 是 mutual friends / friend-of-friend。
- Tie-break 用 id，保证 deterministic tests。

