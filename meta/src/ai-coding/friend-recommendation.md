# Friend Recommendation

## 题目背景

给定一个简单社交网络，需要实现好友推荐。

题目通常分几部分：

1. Debug `valid_recommend(user, candidates)`，修复 test case。
2. 实现 `random_recommend(user, users, k)`。
3. 讨论如何衡量好友推荐算法好不好。
4. 基于现有 `User` class 实现 mutual friends recommendation，并生成测试。

面试里的关键限制：

- `User` class 通常只有：
  - `id`
  - `currentFriends`
- 不要假设有生日、性别、学校、公司、group、location 等额外属性。
- 所以能用的 signal 主要是 graph structure，例如：
  - 是否已经是好友
  - 是否是自己
  - mutual friends 数量
  - friend-of-friend

## 数据模型

```python
class User:
    def __init__(self, id, currentFriends=None):
        self.id = id
        self.currentFriends = set(currentFriends or [])

    def __repr__(self):
        return f"User(id={self.id})"
```

这里假设：

- `currentFriends` 存的是 friend id，不是 `User` object。
- 如果原题里存的是 `User` object，需要相应改成 `friend.id`。

## Stage 1：Debug valid_recommend

### 问题描述

已有 `valid_recommend(user, candidates)` 用来判断推荐结果是否合法。

Bug：

- 它只检查 candidates 不是当前好友。
- 但没有检查 candidates 里是否包含 user 自己。

所以会出现：

```python
user = User(1, currentFriends={2, 3})
candidates = [User(1), User(4)]
```

这应该是 invalid，因为不能推荐自己。

### Buggy code

```python
def valid_recommend(user, candidates):
    for candidate in candidates:
        if candidate.id in user.currentFriends:
            return False
    return True
```

### Fixed code

```python
def valid_recommend(user, candidates):
    seen = set()

    for candidate in candidates:
        if candidate.id == user.id:
            return False
        if candidate.id in user.currentFriends:
            return False
        if candidate.id in seen:
            return False
        seen.add(candidate.id)

    return True
```

### 面试解释

- 推荐结果必须满足：
  - 不能推荐自己。
  - 不能推荐已经是好友的人。
  - 不能重复推荐同一个人。
- 这一问通常不需要 AI，读 test case 和 helper function 就能发现。

## Stage 2：实现 random_recommend

### 问题描述

实现一个随机推荐函数：

```python
def random_recommend(user, users, k):
    ...
```

要求：

- 从所有 users 里随机推荐最多 `k` 个用户。
- 不能推荐自己。
- 不能推荐已有好友。
- 不能重复。
- 如果合法候选少于 `k` 个，就返回所有合法候选。

### Code

```python
import random


def random_recommend(user, users, k, seed=None):
    rng = random.Random(seed)

    candidates = []
    for other in users:
        if other.id == user.id:
            continue
        if other.id in user.currentFriends:
            continue
        candidates.append(other)

    rng.shuffle(candidates)
    return candidates[:k]
```

### Big O

设：

- `N = len(users)`
- `F = len(user.currentFriends)`

如果 `currentFriends` 是 set：

- Time: `O(N)`
- Space: `O(N)` for candidates

如果 `currentFriends` 是 list：

- 判断 friend 需要 `O(F)`
- 总时间变成 `O(N * F)`
- 所以最好把 friends 转成 set。

### Dry run

```python
users = [
    User(1, {2, 3}),
    User(2, {1}),
    User(3, {1}),
    User(4, set()),
    User(5, set()),
]

random_recommend(users[0], users, k=2, seed=0)
```

对 user 1：

- user 1 自己不能推荐。
- user 2, 3 已经是好友，不能推荐。
- 合法候选是 user 4, 5。
- 返回 `[User(4), User(5)]` 或 `[User(5), User(4)]`。

## Stage 3：如何衡量推荐算法好不好

### 先看可用数据

如果 `User` 只有：

```python
id
currentFriends
```

那么不能回答：

- 相同学校
- 相同公司
- 相同城市
- 年龄性别
- 兴趣 group
- profile similarity

因为数据模型里没有这些 attributes。

可以回答的指标和信号：

- Mutual friends 数量。
- 推荐是否已经是 friend-of-friend。
- 推荐是否被用户接受。
- 推荐是否被用户 ignore/block。
- 推荐列表的合法性和去重。

### Offline metrics

如果有历史 friend creation data，可以做离线评估：

- Precision@K：
  - 推荐的 K 个用户里，有多少后来真的成为好友。
- Recall@K：
  - 用户后来新增的好友里，有多少被推荐系统覆盖。
- Hit Rate@K：
  - 推荐列表里是否至少有一个用户后来加了好友。
- MRR / NDCG：
  - 如果越靠前越重要，可以衡量排序质量。

### Online metrics

上线后可以看：

- Recommendation impression -> click rate。
- Friend request sent rate。
- Friend request accepted rate。
- Hide / dismiss / block rate。
- Spam report rate。
- Long-term retention impact。

### Guardrail metrics

好友推荐可能会伤害用户体验，所以要看：

- 推荐已是好友的比例，应为 0。
- 推荐自己的比例，应为 0。
- 重复推荐率。
- Block/report rate。
- 发送好友请求后的拒绝率。

### 面试回答重点

如果面试官问“AI 回答里哪些能用”，要回到 data model：

```text
User 只有 id 和 currentFriends，所以当前能直接实现的是 mutual friends / friend-of-friend。
其它 demographic 或 interest-based feature 需要额外数据，现在不能假设。
```

## Stage 4：Mutual friends recommendation

### 问题描述

实现：

```python
def mutual_friends_recommend(user, users, k):
    ...
```

推荐逻辑：

- 不推荐自己。
- 不推荐已有好友。
- 候选用户按 mutual friends 数量从高到低排序。
- mutual friends 数一样时，可以按 user id 排序，保证 deterministic output。
- 返回前 `k` 个。

### Example

```python
u1 = User(1, {2, 3})
u2 = User(2, {1, 4, 5})
u3 = User(3, {1, 4})
u4 = User(4, {2, 3})
u5 = User(5, {2})
```

对 user 1：

- user 4 的 mutual friends 是 `{2, 3}`，数量 2。
- user 5 的 mutual friends 是 `{2}`，数量 1。

所以推荐顺序：

```python
[User(4), User(5)]
```

### Code

```python
def mutual_friend_count(user, candidate):
    return len(user.currentFriends & candidate.currentFriends)


def mutual_friends_recommend(user, users, k):
    scored = []

    for candidate in users:
        if candidate.id == user.id:
            continue
        if candidate.id in user.currentFriends:
            continue

        score = mutual_friend_count(user, candidate)
        if score == 0:
            continue

        scored.append((score, candidate.id, candidate))

    scored.sort(key=lambda x: (-x[0], x[1]))
    return [candidate for _, _, candidate in scored[:k]]
```

### 如果要允许没有 mutual friend 的候选

有些产品会在 mutual friends 不够时 fallback 到 random。

```python
def mutual_friends_recommend_with_fallback(user, users, k, seed=None):
    primary = mutual_friends_recommend(user, users, k)
    if len(primary) == k:
        return primary

    selected = {u.id for u in primary}
    fallback_candidates = []

    for candidate in users:
        if candidate.id == user.id:
            continue
        if candidate.id in user.currentFriends:
            continue
        if candidate.id in selected:
            continue
        fallback_candidates.append(candidate)

    rng = random.Random(seed)
    rng.shuffle(fallback_candidates)
    return primary + fallback_candidates[: k - len(primary)]
```

## 完整参考实现

```python
import random


class User:
    def __init__(self, id, currentFriends=None):
        self.id = id
        self.currentFriends = set(currentFriends or [])

    def __repr__(self):
        return f"User(id={self.id})"


def valid_recommend(user, candidates):
    seen = set()

    for candidate in candidates:
        if candidate.id == user.id:
            return False
        if candidate.id in user.currentFriends:
            return False
        if candidate.id in seen:
            return False
        seen.add(candidate.id)

    return True


def random_recommend(user, users, k, seed=None):
    rng = random.Random(seed)

    candidates = []
    for other in users:
        if other.id == user.id:
            continue
        if other.id in user.currentFriends:
            continue
        candidates.append(other)

    rng.shuffle(candidates)
    return candidates[:k]


def mutual_friend_count(user, candidate):
    return len(user.currentFriends & candidate.currentFriends)


def mutual_friends_recommend(user, users, k):
    scored = []

    for candidate in users:
        if candidate.id == user.id:
            continue
        if candidate.id in user.currentFriends:
            continue

        score = mutual_friend_count(user, candidate)
        if score == 0:
            continue

        scored.append((score, candidate.id, candidate))

    scored.sort(key=lambda x: (-x[0], x[1]))
    return [candidate for _, _, candidate in scored[:k]]


def mutual_friends_recommend_with_fallback(user, users, k, seed=None):
    primary = mutual_friends_recommend(user, users, k)
    if len(primary) == k:
        return primary

    selected = {u.id for u in primary}
    fallback_candidates = []

    for candidate in users:
        if candidate.id == user.id:
            continue
        if candidate.id in user.currentFriends:
            continue
        if candidate.id in selected:
            continue
        fallback_candidates.append(candidate)

    rng = random.Random(seed)
    rng.shuffle(fallback_candidates)
    return primary + fallback_candidates[: k - len(primary)]
```

## 测试用例

```python
def test_valid_recommend_rejects_self():
    user = User(1, {2, 3})
    candidates = [User(1), User(4)]
    assert valid_recommend(user, candidates) is False


def test_valid_recommend_rejects_existing_friend():
    user = User(1, {2, 3})
    candidates = [User(2), User(4)]
    assert valid_recommend(user, candidates) is False


def test_valid_recommend_rejects_duplicates():
    user = User(1, {2, 3})
    candidates = [User(4), User(4)]
    assert valid_recommend(user, candidates) is False


def test_valid_recommend_accepts_valid_candidates():
    user = User(1, {2, 3})
    candidates = [User(4), User(5)]
    assert valid_recommend(user, candidates) is True


def test_random_recommend_returns_only_valid_users():
    users = [
        User(1, {2, 3}),
        User(2, {1}),
        User(3, {1}),
        User(4, set()),
        User(5, set()),
    ]

    result = random_recommend(users[0], users, k=10, seed=0)

    assert len(result) == 2
    assert {u.id for u in result} == {4, 5}
    assert valid_recommend(users[0], result)


def test_mutual_friends_recommend_orders_by_mutual_count():
    users = [
        User(1, {2, 3}),
        User(2, {1, 4, 5}),
        User(3, {1, 4}),
        User(4, {2, 3}),
        User(5, {2}),
        User(6, set()),
    ]

    result = mutual_friends_recommend(users[0], users, k=10)

    assert [u.id for u in result] == [4, 5]
    assert valid_recommend(users[0], result)


def test_mutual_friends_recommend_respects_k():
    users = [
        User(1, {2, 3}),
        User(2, {1, 4, 5}),
        User(3, {1, 4}),
        User(4, {2, 3}),
        User(5, {2}),
    ]

    result = mutual_friends_recommend(users[0], users, k=1)
    assert [u.id for u in result] == [4]


def test_mutual_friends_fallback_fills_when_not_enough():
    users = [
        User(1, {2}),
        User(2, {1}),
        User(3, set()),
        User(4, set()),
    ]

    result = mutual_friends_recommend_with_fallback(users[0], users, k=2, seed=0)
    assert len(result) == 2
    assert valid_recommend(users[0], result)
```

## Big O

设：

- `N = len(users)`
- `F = average number of friends`
- `k = number of recommendations`

### valid_recommend

如果 `currentFriends` 是 set：

- Time: `O(k)`
- Space: `O(k)` for duplicate check

### random_recommend

- Time: `O(N)`
- Space: `O(N)`

### mutual_friends_recommend

对每个 candidate 计算 set intersection：

- Time: `O(N * F)`，更准确是 `sum(min(len(user.friends), len(candidate.friends)))`
- Sorting: `O(N log N)`
- Space: `O(N)`

如果只要 top `k`，可以用 heap：

- Time: `O(N * F + N log k)`
- Space: `O(k)`

## Heap 优化版本

```python
import heapq


def mutual_friends_recommend_heap(user, users, k):
    heap = []

    for candidate in users:
        if candidate.id == user.id:
            continue
        if candidate.id in user.currentFriends:
            continue

        score = mutual_friend_count(user, candidate)
        if score == 0:
            continue

        # min-heap by score, tie-break by larger id as worse
        item = (score, -candidate.id, candidate)
        if len(heap) < k:
            heapq.heappush(heap, item)
        else:
            if item > heap[0]:
                heapq.heapreplace(heap, item)

    result = [item[2] for item in heap]
    result.sort(key=lambda candidate: (-mutual_friend_count(user, candidate), candidate.id))
    return result
```

## 面试解释重点

### Stage 1

- `valid_recommend` 的输入是一个 user 和一个 candidate list。
- 合法性必须检查：
  - candidate 不是 user 自己。
  - candidate 不是已有好友。
  - candidate 不重复。

### Stage 2

- `random_recommend` 先过滤合法候选，再 shuffle。
- 不要从全部 users 里直接 random，否则可能推荐自己或已有好友。

### Stage 3

- 衡量推荐算法好不好时，要基于现有数据模型。
- 如果 `User` 只有 `id/currentFriends`，就不要假设性别、生日、group。
- 能直接用的是 graph signal：
  - mutual friends
  - friend-of-friend
  - recommendation acceptance rate

### Stage 4

- Mutual friends 是最自然的 baseline。
- 按 mutual count 排序。
- tie-break 用 user id，让测试 deterministic。

## 常见 follow-up

### Follow-up 1：如果 currentFriends 存的是 User object 怎么办？

把 friend id 取出来：

```python
def friend_ids(user):
    result = set()
    for friend in user.currentFriends:
        if isinstance(friend, User):
            result.add(friend.id)
        else:
            result.add(friend)
    return result
```

然后算法里统一用 `friend_ids(user)`。

### Follow-up 2：如何避免每次都扫全量 users？

可以从 friend-of-friend 候选开始：

```python
candidate_ids = set()
for friend_id in user.currentFriends:
    friend = id_to_user[friend_id]
    candidate_ids |= friend.currentFriends
```

然后过滤：

- 自己
- 已有好友

这样候选规模通常远小于全量 users。

### Follow-up 3：如果要支持大规模社交网络？

可以离线预计算：

- 每个 user 的 top mutual-friend candidates。
- 存到 recommendation cache。
- 在线请求只做读取和少量过滤。

或者用 graph processing：

- MapReduce / Spark / Flink 统计二跳关系。
- 对每个 `(user, candidate)` 计算 mutual friend count。

### Follow-up 4：如果要做更好的推荐？

在有更多数据后可以加入：

- profile similarity
- same group/school/company
- location
- interaction history
- embeddings
- ranking model

但在原题数据模型只有 `id/currentFriends` 时，不应该假设这些 feature 已经存在。
