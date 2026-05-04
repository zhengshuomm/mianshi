# Staff Engineer Deep Dive Trade-off 详细总结

> 适用于 Meta/Google/Uber/Stripe Staff+ 系统设计面试。每个 pattern 标注了适用的设计题、核心矛盾、三种方案对比、以及面试表达模板。

---

## Pattern 1: Exactly Once vs At-Least-Once

**核心矛盾**：delivery 保证 vs 实现复杂度

**适用设计题**：
- Ads Click Aggregation（广告点击计数，不能重复计费）
- Payment System（不能重复扣款）
- Job Scheduler / Task Queue（任务不能重复执行）
- Web Crawler（URL 不能重复处理）
- Notification System（通知不能重复发送）

| 方案　　　　　　　　　　　　　　　　　| 机制　　　　　　　　　　　　　　　 | 优点　　　　　　　　　　　　 | 缺点　　　　　　　　　　　　　　 |
| ---------------------------------------| ------------------------------------| ------------------------------| ----------------------------------|
| At-least-once + 幂等 sink　　　　　　 | 消费端幂等 key / unique constraint | 简单可靠，失败后 replay 不丢 | sink 必须设计成幂等，否则重复写　|
| Kafka transactional producer　　　　　| Kafka 内部原子 commit/abort　　　　| 减少 Kafka 内部重复　　　　　| 只解决 Kafka 内，不涉及外部 DB　 |
| Flink checkpoint + transactional sink | 2PC / overwrite sink　　　　　　　 | 端到端强保证　　　　　　　　 | 外部 sink 必须支持事务或幂等 key |

**关键细节与常见陷阱**：
- "Exactly-once" 在分布式系统里是个谎言。Kafka 的 exactly-once semantic 只保证 **Kafka topic 内**不重复，一旦写到外部 DB 或调用外部 API，就回到 at-least-once。
- 正确的思路是：**at-least-once delivery + idempotent consumer**。消费端必须能安全地重复处理同一条消息。
- Idempotency key 设计必须稳定：不能用时间戳（重试时时间变了），要用 `(source_id, event_id)` 或 `(partition, offset)` 这类确定性 key。
- Flink checkpoint 的 exactly-once 依赖 **barrier alignment**，高吞吐下 barrier 对齐会引入显著延迟，需要权衡。
- Kafka transactional producer 解决的是 producer → Kafka 这一段，不是 Kafka → sink。面试中很多候选人混淆这两段。
- **Dedup window 有限**：流式 dedup 只在内存窗口内有效，窗口外的 late arrival 需要 offline reconciliation（见 Pattern 18）。

**面试表达**：
> "我不会在面试里承诺端到端 exactly-once。更准确的说法是：Kafka/Flink 内部可以做到 exactly-once processing，但外部效果取决于 sink 是否幂等。对 Druid/ClickHouse，我会用 deterministic event_id 或 (window_key, event_id) 作为 dedup key，确保 replay 不会重复计数。如果面试官追问 late arrival，我会引出 Pattern 18 的 offline reconciliation：实时链路允许有误差，T+1 的 batch job 做对账修正，差异以离线结果为准。"

---

## Pattern 2: Source of Truth vs Derived Data

**核心矛盾**：一致性 vs 读性能

**适用设计题**：
- News Feed / Twitter Timeline（feed 是派生数据）
- Ticketmaster（search index 是派生，真实库存是 source of truth）
- YouTube / Netflix（推荐结果是派生）
- Typeahead / Search（index 是派生）
- Proximity / Uber（driver location 是派生 cache）

| 方案 | 机制 | 优点 | 缺点 |
|------|------|------|------|
| 全读 source of truth | 读 DB | 强一致 | 高 QPS 打爆 DB，延迟高 |
| 全用 derived data | Cache / Search / Read Model | 低延迟，高吞吐 | stale data，权限泄露，库存错误 |
| derived + read-time validation | 搜索/feed 读 derived，关键操作回 source of truth 校验 | 性能和正确性兼顾 | 链路复杂，多一次校验 |

**关键细节与常见陷阱**：
- Derived data 的问题不只是 stale，还有**权限泄露**：被删除的内容、被拉黑的用户、已过期的票，如果 search index 没及时更新，就会出现在结果里。
- **两类 derived data 要区分对待**：(1) 纯读优化（feed inbox、leaderboard cache）—— 允许短暂 stale，可以 rebuild；(2) 影响决策正确性的（搜索库存、座位状态）—— 必须在关键操作时回 source of truth 验证。
- Read-time validation 的实现方式：搜索返回 candidate set → batch fetch DB 状态 → filter 掉不可用的 → 返回结果。Batch fetch 比逐条查询延迟低得多（N+1 query 是常见性能陷阱）。
- **Derived data 不能存权限信息**：权限变更（取消关注、内容设为私密）有传播延迟，如果 cache 存了权限，可能泄露数据。权限必须每次都去 source of truth 查，或者用极短 TTL。
- Stale derived data 的修复路径要设计好：CDC → rebuild index 是标准姿势，但 rebuild 期间需要标记"index rebuilding"，避免用旧数据误导决策。

**面试表达**：
> "Search index、feed inbox、cache、recommendation result 都是 derived data，允许短暂 stale。但涉及钱、库存、权限、隐私的关键动作，必须回到 source of truth 做 read-time validation。购票时搜到结果并不代表能抢到——需要在 DB 做一次 conditional update。搜索返回候选集后，我会 batch fetch DB 当前状态，过滤掉已售、已过期、无权限的结果，再返回给客户端。N+1 查询是这里最常见的性能陷阱，batch 解决。"

---

## Pattern 3: DB 和 MQ 一致性（Dual Write vs Outbox/CDC）

**核心矛盾**：写路径原子性 vs 简单实现

**适用设计题**：
- News Feed（发帖后触发 fanout）
- Chat App（消息写后触发推送）
- Order System（下单后触发库存/通知/物流）
- Price Drop Tracker（价格更新触发通知）
- YouTube（视频上传后触发转码任务）

| 方案 | 机制 | 优点 | 缺点 |
|------|------|------|------|
| 写 DB 后直接 publish MQ | 代码顺序调用 | 实现简单 | crash 在中间会丢事件或产生脏事件 |
| 2PC | XA 协议 | 理论强一致 | 阻塞、可用性差、外部系统不支持 |
| Outbox / CDC | DB 和 outbox 同事务，CDC 异步消费 | 可靠，可 replay，可监控 | 有异步延迟，CDC pipeline 需要运维 |

**关键细节与常见陷阱**：
- Dual write 的隐患被严重低估。"先写 DB，再发 MQ" 的代码看起来很正常，但中间 crash 就会丢事件；"先发 MQ，再写 DB" 则会产生幽灵事件（DB 没有记录，但下游已经消费了）。
- **Outbox pattern 的正确实现**：outbox 表和业务表在同一个 DB 事务里写，保证原子性。CDC（如 Debezium）监听 binlog，把 outbox 表的变更发到 Kafka。这样即使应用进程 crash，CDC 也能从 binlog 恢复。
- CDC 的延迟通常在 100ms-1s 级别，对大多数异步场景可以接受。如果需要更低延迟，可以在写 outbox 后额外做一次 in-process notify（但这只是优化，不是保证，CDC 才是保证）。
- **不要用 application-level CDC**（应用自己 poll outbox 表）：这种方式在高负载下 poll 间隔不稳定，且 application 进程也可能 crash。数据库层 CDC 更可靠。
- Outbox 表需要清理策略：消费完的消息打标或删除，避免无限增长。保留一段时间用于 replay 是合理的（比如 7 天）。
- **事件 schema 要带足够上下文**：outbox 中的事件应该是自包含的（包含 entity 状态快照或足够的 delta），不要只存 entity_id 让消费者再去 DB 查，因为到消费时 DB 状态可能已经变化。

**面试表达**：
> "写路径只写 source of truth 和 outbox，保证原子性——两者在同一个 DB 事务里。后续 indexing、notification、analytics 从 outbox/CDC 异步消费。这样主链路不依赖下游可用性，且事件可以 replay 修复 derived data。关键设计点：事件要自包含，不能只存 entity_id；outbox 要有清理策略，7 天保留用于 replay。"

---

## Pattern 4: 幂等和去重

**核心矛盾**：副作用安全性 vs 实现复杂度

**适用设计题**：
- Payment System（不能重复扣款）
- Job Scheduler（任务不能重复触发）
- Notification System（同一条通知不能发两次）
- Ticketmaster（不能重复出票）
- Online Auction（出价不能重复计入）

| 方案 | 机制 | 优点 | 缺点 |
|------|------|------|------|
| 客户端防重复点击 | 前端 disable 按钮 | 改善 UX | 不能保证正确性，绕过方式多 |
| Idempotency key + unique constraint | DB unique key 或 redis set | 同一请求只产生一次效果 | 需要保存 key → 结果映射，设计 TTL |
| Stream dedup / event_id window | Flink/Kafka 消费端 dedup | 高吞吐流式去重 | 只在窗口内有效 |

**关键细节与常见陷阱**：
- Idempotency key 的生命周期管理是容易被忽略的细节：key 存多久？如果 TTL 太短，合法的重试（如几天后的 payment callback）会被误判为新请求；TTL 太长则浪费存储。Payment 场景通常 TTL 24h-7d，具体取决于 SLA。
- **返回值的幂等性**：同一个 key 的重试应该返回**原始操作的结果**，而不是"已存在"错误。比如支付第一次成功了，重试应该也返回"支付成功"，而不是 409 Conflict。这需要存储 key → response 的映射。
- DB unique constraint 是**最后一道防线**，不是第一道。应该在应用层先检查 Redis（快速路径），再落到 DB（保证正确性）。但应用层检查不是保证，DB 约束才是。
- **分布式去重的窗口问题**：Flink/Kafka 消费端 dedup 只在内存或 state backend 的窗口内有效。窗口外的重复需要写入 DB 后由 unique constraint 捕获，或者通过 offline reconciliation 发现。
- 幂等性和状态机要结合：对于有状态的操作（如订单状态流转），不只是去重，还要检查当前状态是否允许该操作。幂等 key 解决"同一请求不重复执行"，状态机解决"不合法的状态转换"，两者都要有。

**面试表达**：
> "所有可能重试的入口——API、consumer、webhook、payment callback——都默认可能重试。业务副作用必须通过 idempotency key 或 unique constraint 保护。key 设计为 (client_id, request_id)，DB unique constraint 是最终保障，不依赖 client 是否遵守。重试要返回原始操作的结果，不是报错——所以需要存 key → response 映射，TTL 根据业务 SLA 设置，支付场景通常 7 天。"

---

## Pattern 5: Ordering Guarantee

**核心矛盾**：消息顺序保证强度 vs 吞吐和可用性

**适用设计题**：
- Chat App（同一会话消息要有序）
- Order System / Uber Ride（同一订单状态流转要有序）
- Collaborative Editing（编辑操作要有序）
- Auction（出价时间戳要正确排序）

| 方案 | 机制 | 优点 | 缺点 |
|------|------|------|------|
| 全局顺序 | 单 partition / 全局序列号 | 语义简单 | 吞吐极低，跨 region 延迟高 |
| Per-entity ordering | Kafka partition by entity_id | 保序范围足够，可水平扩展 | 跨 entity 无全局顺序 |
| Client sequence + version check | 客户端带 seq_num，server 检测 gap | 支持离线、重试、gap 检测 | 协议复杂，状态管理难 |

**关键细节与常见陷阱**：
- Per-entity ordering 的前提是：同一 entity 的所有消息必须走**同一个 partition**。如果 producer 端有任何负载均衡或 partition 选择变化，顺序就会被打乱。
- **Partition 扩容会破坏顺序**：Kafka 增加 partition 后，同一 entity 的消息可能被路由到新 partition，导致旧消息和新消息在不同 partition 上乱序。扩容需要暂停消费或做迁移。
- Client sequence number 方案（客户端带 seq_num）的好处是**可以检测 gap**：seq 从 5 跳到 7，说明 6 丢了，可以主动触发重传。这是 chat 场景常用的补偿机制。
- **全局顺序的真实成本**：单 partition 意味着整个 topic 只有一个写入点，吞吐上限约 100MB/s。跨 region 时延迟可能达到几百毫秒。这对大多数系统是不可接受的。
- 多 consumer 并行消费同一 partition 时，顺序无法保证——Kafka 保证的是**单 consumer** 消费单 partition 时有序。如果需要并行，要在 consumer 端做重排或用 sequence number。
- Chat 场景的经典设计：消息写入时 server 分配单调递增的 `message_id`（per conversation），客户端按 message_id 渲染，断线重连时用 `last_seen_message_id` 拉取缺失消息。

**面试表达**：
> "大多数系统不需要全局顺序，只需要每个 order/thread/account 内部有序。Kafka 按 entity_id partition 就能保证这点，同时保留水平扩展能力。我不会为全局顺序牺牲吞吐。Chat 场景还要额外讲：server 分配单调递增的 message_id，客户端用 last_seen_message_id 做断线补齐，这样顺序和可靠性都由 server 保证，不依赖网络。"

---

## Pattern 6: Push vs Pull / Fanout 策略

**核心矛盾**：写放大 vs 读延迟

**适用设计题**：
- News Feed / Twitter（最经典）
- Slack / Chat（大 channel 的消息分发）
- Live Comment（热门直播的评论分发）
- WhatsApp Status（状态广播）
- YouTube Subscription（订阅更新通知）

| 方案 | 机制 | 优点 | 缺点 |
|------|------|------|------|
| Fanout-on-write | 发帖时写入所有 follower inbox | 读快，直接返回 | 写放大，大 V 会造成热点 |
| Fanout-on-read | 读时拉取所有 following 的最新帖 | 写轻，大 V 只写一次 | 读慢，需要 scatter-gather merge |
| Hybrid | 普通用户 write fanout，大 V read fanout | 平衡两端 | 读路径需要 merge 两类候选，系统更复杂 |

**关键细节与常见陷阱**：
- Fanout-on-write 的写放大有多严重：一个拥有 1000 万 follower 的大 V 发一条帖子，需要写 1000 万条 inbox 记录。即使用异步队列，这个 spike 也会让 Redis 和队列系统承受极大压力，需要**流控**（限制 fanout worker 的速率）。
- **Hybrid 方案的 merge 逻辑**不能忽视：读路径要合并 (1) 自己 inbox 里 write-fanout 的帖子 和 (2) 大 V 账号的最新帖子。两类结果要按时间排序，而且大 V 帖子也需要 pagination（不能无限拉）。
- 非活跃用户的 inbox 是浪费：如果用户 30 天没登录，他的 inbox 仍然在接收所有 fanout，占用 Redis 空间。常见优化是 **inactive user skip**：超过一定天数未活跃的用户不做 write fanout，等他们下次登录时再用 read fanout 拉取。
- **Follower 数量是动态的**：用户可能突然涨粉（比如上热搜），threshold 需要实时判断，不能在关注时就决定走哪条路。
- Feed 的时序性问题：Fanout 是异步的，所以不同 follower 看到的 feed 出现时间可能有差异（几秒到几分钟）。这是设计上的已知 trade-off，要主动说清楚。

**面试表达**：
> "默认 hybrid：普通用户 fanout-on-write，大 V（follower > threshold，比如 10w）fanout-on-read。读路径 merge 两类结果，用 Redis sorted set 承接 inbox，CDN 承接大 V 内容热点。threshold 可配置，这本身就是一个 trade-off 参数。还要补充两点：非活跃用户（30 天未登录）跳过 write fanout，等登录时再拉——节省大量 Redis 空间；fanout 是异步的，不同 follower 看到帖子的时间会有延迟，这是已知 trade-off。"

---

## Pattern 7: Cache Consistency

**核心矛盾**：缓存新鲜度 vs 实现和可用性

**适用设计题**：
- Key-Value Store
- Distributed Cache（Memcache/Redis 设计）
- News Feed / Leaderboard（缓存排行榜）
- Ticketmaster（缓存座位图）
- Typeahead（缓存热门搜索）

| 方案 | 机制 | 优点 | 缺点 |
|------|------|------|------|
| Cache-aside | 读 miss 时加载，TTL 或主动 invalidate | 简单，DB 是 source of truth | cache miss stampede，stale window |
| Write-through | 写 DB 同时更新 cache | cache 相对新鲜 | 写延迟增加，cache 故障影响写 |
| Write-behind | 先写 cache，异步落 DB | 写性能最好，可批量 | cache crash 丢数据，恢复复杂 |

**关键细节与常见陷阱**：
- **Cache stampede（缓存击穿）**是 cache-aside 最常见的生产问题：大量并发请求同时 miss，全部打到 DB，DB 被打崩。解法：mutex lock（只让一个请求回源，其余等待）或 probabilistic early expiration（TTL 快到期时提前少量概率刷新）。
- Write-through 看似更新及时，但有个隐患：如果 cache 节点 down，写操作是否要失败？大多数实现会降级为只写 DB，但 cache 不一致窗口就变大了。
- **Write-behind 的数据丢失风险被低估**：cache crash 时，还没 flush 到 DB 的数据全丢。只有极少数容忍数据丢失的场景（如 session 数据、实时游戏分）才适合用 write-behind。
- 缓存和 DB 双写不一致的经典场景：线程 A 更新 DB → 线程 B 更新 DB → 线程 B 更新 cache → 线程 A 更新 cache（用旧值覆盖）。即使都是 write-through，也可能产生不一致。**Cache 永远只做 invalidate，不做 update**，是更安全的策略（cache-aside 变体）。
- 对于**热点 cache key**，TTL 到期的瞬间会有大量并发 miss。解法是 **staggered TTL**（每个副本加随机 jitter）或 **background refresh**（TTL 临近时后台异步刷新，不等 miss）。

**面试表达**：
> "对 correctness-critical 数据（库存、余额、权限），不把 cache 当 source of truth，用 TTL + invalidation + read-time validation 控制风险。对纯读 derived data（feed、leaderboard），cache-aside 加短 TTL 就够了。Cache 只做 invalidate 不做 update，避免并发写导致 stale 值覆盖新值。高并发场景要防 stampede：用 mutex 或 probabilistic early expiration，避免 TTL 到期瞬间所有请求同时打 DB。"

---

## Pattern 8: Double Booking / Oversell 防超卖

**核心矛盾**：并发正确性 vs 吞吐性能

**适用设计题**：
- Ticketmaster（座位不能超卖）
- Uber（司机不能同时接两单）
- Seckill System / Flash Sale（秒杀库存）
- Online Auction（出价必须基于最新竞价）
- Payment System（余额不能透支）

| 方案 | 机制 | 优点 | 缺点 |
|------|------|------|------|
| Distributed lock（Redis SETNX） | 锁住资源 key | 容易理解，实现快 | lock 过期/crash/split-brain 带来风险 |
| DB conditional update / optimistic lock | `UPDATE inventory SET qty=qty-1 WHERE qty>0 AND version=X` | 正确性落在 DB source of truth | 热点行竞争严重，高并发下大量失败 |
| Single writer per partition | 按 event/seat 分区，单线程处理 | 串行化最强，无并发冲突 | 分区、failover、扩容复杂 |

**关键细节与常见陷阱**：
- Distributed lock 的最大陷阱：**锁过期不等于临界区安全退出**。Client A 拿到锁，开始执行，GC pause 30 秒，锁 TTL 到期，Client B 拿到锁，两个 client 同时在临界区。用 fencing token 解决：每次拿锁带一个单调递增 token，写入时存储层校验 token 是否是最新的。
- DB conditional update 在高并发下的问题：`WHERE qty > 0 AND version = X` 在热点行上会产生大量 row-level lock 竞争，导致高失败率和长尾延迟。10 万人同时抢 100 张票，99900 个请求会失败，且每个都占了一次 DB round-trip。
- **Queue + single writer 是秒杀场景的正确答案**：把请求先放入 in-memory queue，单线程按序消费（或按 seat partition 并行单线程），消除并发冲突。写吞吐瓶颈变成可预测的 QPS，而不是随机的竞争失败。
- Uber 司机抢单场景：不能用全局锁（延迟太高），正确做法是 DB conditional update `WHERE status = 'available' AND driver_id IS NULL`，CAS 语义。失败则找下一个司机。
- **乐观锁 vs 悲观锁**的选择：并发冲突低时用乐观锁（version check）；冲突高时用悲观锁或 queue 串行化。秒杀场景冲突极高，乐观锁会产生大量无效重试，queue 是更好的选择。

**面试表达**：
> "库存/座位/余额优先用 DB conditional write，把正确性的最终保证落在 source of truth 上，不依赖分布式锁。分布式锁只是性能优化层，不是正确性保证——因为锁可以过期，GC pause 可能导致两个 client 同时在临界区。对极热点（秒杀、音乐会开票），用 in-memory queue + single writer per partition 串行化，把不可预测的竞争失败变成可预测的排队等待，用户体验也更好（知道自己在第几位）。"

---

## Pattern 9: Saga vs Distributed Transaction

**核心矛盾**：跨服务原子性 vs 可用性和复杂度

**适用设计题**：
- Food Delivery / Uber Eats（下单、支付、商家确认、配送）
- Ticketmaster（选座、锁定、支付、出票）
- Payment System（扣款、入账、通知）
- Job Scheduler（任务分配、执行、回调）

| 方案 | 机制 | 优点 | 缺点 |
|------|------|------|------|
| 单体事务 | 同一 DB ACID | 简单，强一致 | 跨服务/跨系统不可行 |
| 2PC | XA 协议全员参与 | 理论强一致 | 阻塞，可用性差，外部系统不支持 |
| Saga + compensation | 每步本地提交，失败触发补偿 | 可重试，可补偿，最终一致 | 状态机复杂，需要设计补偿逻辑 |

**关键细节与常见陷阱**：
- Saga 分两种实现方式：**Choreography**（各服务自己监听事件触发下一步，去中心化）和 **Orchestration**（中央 coordinator 指挥每一步）。Choreography 更松耦合但难以 debug；Orchestration 流程清晰但 coordinator 是单点。面试中说 Orchestration 更容易被 Staff+ 接受，因为可观测性更好。
- **补偿事务不等于回滚**：补偿是一个新的正向操作（退款是新的 DB 写入），不是 UNDO。这意味着补偿本身也可能失败，需要重试，补偿操作也必须幂等。
- Saga 的"隔离性问题"是面试官经常追问的：两个 Saga 并发执行时，可能读到对方中间状态（脏读）。解法是 **semantic lock**：在开始 Saga 时锁定相关资源（如把订单状态置为 PENDING），其他操作看到 PENDING 就等待或拒绝。
- **超时设计很关键**：每一步必须有超时，超时后触发补偿，不然一个步骤 hang 住就卡死整个流程。Temporal 等 workflow engine 把这个内建了，手写 Saga 要自己实现。
- Ticketmaster 的 Saga 流程：seat lock → payment → issue ticket → send confirmation。如果 payment 失败：release seat lock；如果 issue ticket 失败：refund + release seat lock。每步补偿要有序执行，不能并行（可能有依赖）。

**面试表达**：
> "跨服务长流程用 Saga，推荐 Orchestration 模式（中央 coordinator），因为流程集中、可观测、容易 debug。每步本地提交，失败触发补偿——补偿是新的正向操作，本身也要幂等。关键要点：(1) 补偿要有序，不能并行；(2) 每步要有超时，超时触发补偿；(3) 用 semantic lock 避免并发 Saga 互相干扰。Saga 不是强一致，但每一步的 invariant 清晰，失败路径可以枚举和测试。"

---

## Pattern 10: Worker Failure / Task Ownership

**核心矛盾**：任务可靠性 vs 重复执行风险

**适用设计题**：
- Job Scheduler（worker crash 后任务不能丢）
- YouTube / Video Transcoding（转码任务不能丢）
- Web Crawler（爬取任务 ownership）
- Food Delivery（配送任务 ownership）
- Notification System（发送任务不丢不重）

| 方案 | 机制 | 优点 | 缺点 |
|------|------|------|------|
| Worker 直接执行不记录 | 内存 queue | 简单 | crash 后任务丢失或卡住 |
| Lease / visibility timeout | 任务 heartbeat，超时重新分配 | 容错好，任务不丢 | 可能重复执行，worker 必须幂等 |
| Durable workflow engine | Temporal / Step Functions | retry/timeout/compensation 内建 | 引入平台依赖，运维复杂 |

**关键细节与常见陷阱**：
- **Visibility timeout 设置是个微妙的 trade-off**：太短则任务还没执行完就重新可见，产生并发执行；太长则 worker crash 后任务长时间卡住。合理做法：timeout 设为任务 p99 执行时间的 2-3 倍，再加 heartbeat 续期机制。
- Heartbeat 续期的实现：worker 每 N 秒更新任务的 `last_heartbeat_at` 字段（或 Redis TTL），后台 monitor 进程扫描超过阈值未更新的任务，重置状态为 available。
- **Zombie worker 问题**：worker 进程没有 crash，但因为 GC pause / 网络分区 / 系统负载导致 heartbeat 停了一段时间，任务被重新分配，此时两个 worker 同时执行同一任务。这就是为什么 worker 必须幂等——不能假设自己是唯一执行者。
- 任务优先级：如果任务有优先级（如 premium user 的任务先执行），queue 需要支持优先级，或者用多个 queue（不同优先级）+ 不同数量的 consumer。
- **Poison pill 问题**：某个任务每次执行都失败（如 bug 导致 crash），会无限重试，占满 worker 资源。解法：设置 max retry count，超过后移入 DLQ（Dead Letter Queue），人工处理或告警。
- SQS / Kafka 的任务 ownership 语义不同：SQS 的 visibility timeout 是天然的 lease 机制；Kafka 的 consumer group offset commit 代表任务完成，没有 lease 语义，需要应用层自己实现 heartbeat。

**面试表达**：
> "Queue + lease + idempotent worker 是通用基础解。Worker 领取任务时开始 heartbeat，heartbeat 超时则任务重新可见。任务执行必须幂等——不能假设自己是唯一执行者，GC pause 可能导致任务被重新分配后自己仍在执行。还要讲 poison pill：设 max retry，超过后进 DLQ，避免单个坏任务打崩 worker 集群。复杂多步长流程可以考虑 Temporal，把 retry/timeout/heartbeat 逻辑内建。"

---

## Pattern 11: Backpressure / Admission Control

**核心矛盾**：系统保护 vs 请求接受率

**适用设计题**：
- LLM Inference System（GPU 资源有限，推理队列）
- Rate Limiter（流量超限处理）
- Seckill System（秒杀峰值流量）
- Monitoring / Alerting（告警风暴）
- Payment System（过载保护）

| 方案 | 机制 | 优点 | 缺点 |
|------|------|------|------|
| 无限排队 | 无 queue limit | 表面上不丢请求 | tail latency 爆炸，过期请求浪费资源，雪崩 |
| 快速拒绝 | 超过阈值直接 429/503 | 保护核心服务 | 用户体验差，需要 client 重试策略 |
| 优先级队列 + 降级 | 高价值优先，低价值 shed | 核心业务优先保障 | 策略复杂，公平性问题 |

**关键细节与常见陷阱**：
- **队列长度不是延迟的指标，是延迟的预警**：队列积压意味着未来延迟会持续升高，而且已经在队列里的请求很多可能已经超时，消费它们只是浪费资源（处理完客户端早就超时了）。要在消费前检查请求是否已超时（`request_deadline` 字段），过期请求直接 drop。
- **级联雪崩**的完整链路：downstream 慢 → upstream 积压 → upstream 内存耗尽 → upstream 也变慢 → 向上传导，最终整个调用链雪崩。Backpressure 的作用是在积压开始时就拒绝，防止级联。
- 拒绝策略要配合客户端重试策略：返回 429 + `Retry-After` header，客户端做 exponential backoff + jitter。如果所有客户端同时重试（thundering herd），拒绝了也没用。
- **LLM inference 的特殊性**：GPU 资源固定，queue 满了必须拒绝（不能像 CPU 服务那样多开实例快速扩容）。还要区分 streaming 请求（用户在等实时输出）和 batch 请求（后台处理），用不同的 timeout 和优先级。
- 降级策略要分层：(1) 先 shed 低优先级流量；(2) 再降级非核心功能（推荐 → 随机、个性化 → 默认）；(3) 最后才影响核心功能。要预先定义好每一层的触发阈值。

**面试表达**：
> "排队不是免费能力。无限排队只是把延迟问题变成 tail latency 雪崩，而且队列里大量请求已经超时，消费它们只是浪费资源。正确做法：设 queue limit + request deadline，消费前先检查是否超时，过期直接 drop。过载时返回 429 + Retry-After，客户端做 exponential backoff + jitter 防止 thundering herd。有优先级区分时，先 shed 低价值流量，再降级非核心功能，核心链路最后动。"

---

## Pattern 12: Hot Partition / Hot Key

**核心矛盾**：流量均匀分布假设 vs 现实中的热点

**适用设计题**：
- Ads Click Aggregation（热门广告）
- Trending Hashtags / TopK（热门 key）
- News Feed（大 V 发帖）
- Live Comment（热门直播）
- Game Leaderboard（热门玩家/游戏）

| 方案 | 机制 | 优点 | 缺点 |
|------|------|------|------|
| 普通 hash sharding | consistent hash | 简单，扩展方便 | 单个 hot key 仍打爆一个 shard |
| Hot key 拆分 | 加随机后缀，分散到多个 key | 分摊写入和读流量 | 读时需要聚合，一致性更复杂 |
| Single writer + read replica/cache | hot entity 单写，读通过 replica/CDN | 写入顺序清晰，读可扩展 | 写吞吐仍有上限 |

**关键细节与常见陷阱**：
- **Hot key 检测不能等报警**：到报警时 shard 已经打崩了。要在 proxy 或 client 层做实时 hot key 检测（滑动窗口计数），超过阈值自动触发拆分或 local cache。
- Key 拆分（加随机后缀 `key#0` ~ `key#N`）解决写热点，但**读必须聚合**：查询时 scatter-gather N 个 key，对于 counter 类数据只需要求和，但对于 sorted set 类数据聚合更复杂。
- **本地聚合（local aggregation）是广告计数的标准方案**：每台机器维护内存 counter，每隔 1-5 秒 flush 到中央存储（Redis 或 Kafka）。优点是减少 Redis 写入 QPS，缺点是 flush 周期内的数据在本地，crash 会丢失（通常可接受）。
- Kafka 的 hot partition 问题：如果 partition key 是 advertiser_id，热门广告主的所有事件全在一个 partition，consumer 跟不上。解法：对热门 key 用**多个虚拟 partition**（key + random suffix），consumer 端聚合。
- **Cache warming 策略**：系统重启后 cache 为空，所有流量打 DB，可能引发雪崩。提前识别 hot key，在上线前 warm up cache，或者在 key miss 时用 circuit breaker 保护 DB。

**面试表达**：
> "平均流量通常不可怕，真正的风险是单个 event/channel/product 瞬间成为热点。要在 proxy 层做实时 hot key 检测，不等报警。对广告计数，用 local aggregation + 定期 flush：每台机器维护内存 counter，每 1-5 秒批量写入，大幅降低 Redis 压力，crash 丢失的数据在可接受范围内，T+1 reconciliation 会修正。Kafka 热 partition 用虚拟 partition（key + suffix）打散，consumer 端聚合。"

---

## Pattern 13: Geo Index 方案选择

**核心矛盾**：查询精度/灵活性 vs 在线性能

**适用设计题**：
- Uber / Proximity Service（附近司机/商家）
- Google Maps（POI 搜索）
- Food Delivery（配送范围）
- Tinder（附近用户）
- Game（玩家地理分布）

| 方案 | 机制 | 优点 | 缺点 |
|------|------|------|------|
| PostGIS | 原生 geo 查询，支持 polygon | 地理能力强，查询表达力好 | 高 QPS online 压力大，需要 read replica |
| Geohash | 字符串 prefix 代表区域 | 实现简单，容易 shard | 边界问题，cell 大小固定 |
| H3 / S2 | 六边形/球面层级 cell | cell 层级清晰，邻居查询准确 | 实现和调参复杂 |

**关键细节与常见陷阱**：
- **不提 neighbor cells 是最常见的扣分点**：只查当前 cell 会丢失边界附近的结果。比如用户在 cell A 的边缘，最近的司机在相邻 cell B 里，只查 A 就找不到这个司机。必须查当前 cell + 所有 8 个邻居（或 H3 的 6 个邻居）。
- H3 vs Geohash 的关键区别：Geohash 的 cell 是矩形，高纬度时变形严重；H3 是六边形，形状更均匀，邻居查询更一致。面试时说 H3 更专业。
- **Resolution 选择影响精度和性能**：H3 的 resolution 越高，cell 越小，定位越精确，但存储和查询 overhead 也越大。Uber 用 resolution 7（边长约 1.2km）做司机匹配，这是常见的 benchmark 数字。
- 动态实体（司机位置）的更新频率：Uber 司机位置每 4 秒更新一次，不能每次都写 DB。标准做法是 Redis 存实时位置（按 H3 cell 索引），DB 做冷存储（记录轨迹用于计费和分析）。
- **Geo 查询的扇形/距离问题**：H3/Geohash 只能做矩形/多边形 cell 查询，不能直接做"距离 X 以内"的精确圆形查询。需要先用 cell 召回候选，再用 Haversine 公式精确过滤。

**面试表达**：
> "在线召回用 H3 resolution 7（约 1.2km cell）：把 driver/merchant 按 cell 存储在 Redis，查询时取当前 cell + 所有 6 个邻居 cells，避免边界漏结果——这是最容易被遗漏的点。候选集出来后用 Haversine 公式精确计算距离过滤。复杂配送范围和后台 polygon 校验用 PostGIS，但不走在线 QPS 热路径。动态实体只写 Redis，DB 做轨迹冷存储。"

---

## Pattern 14: Realtime Push 协议选择

**核心矛盾**：实时性 vs 连接管理复杂度

**适用设计题**：
- Chat App（双向实时消息）
- Uber / Food Delivery（rider 位置、订单状态）
- Live Comment（评论流推送）
- Collaborative Editing（编辑操作同步）
- Job Scheduler（任务状态推送）

| 方案 | 机制 | 优点 | 缺点 |
|------|------|------|------|
| Polling | 定时轮询 | 简单，无长连接 | 实时性差，频繁 polling 浪费资源 |
| SSE（Server-Sent Events） | 服务端单向推送 | 比 WebSocket 简单，HTTP 友好 | 只适合单向，移动端兼容性需考虑 |
| WebSocket | 全双工长连接 | 实时性好，双向通信 | 连接管理、扩容、重连恢复复杂 |

**关键细节与常见陷阱**：
- **水平扩展是 WebSocket 最难的问题**：用户 A 连在 server 1，用户 B 连在 server 2，B 给 A 发消息时，server 2 需要把消息路由到 server 1。标准解法：在 server 间用 **pub/sub（Redis Pub/Sub 或 Kafka）** 广播，或者用 consistent hashing 把同一 conversation 路由到同一 server。
- **Connection state 不能存在 server 内存**：server 重启后连接全断，客户端需要重连。所有 persistent state（消息、已读状态、在线状态）必须存在外部存储（Redis/DB），不存在 server 内存。
- 断线恢复的完整设计：客户端维护 `last_seen_message_id`，重连后立即发 `SYNC` 请求，server 返回这之后的所有消息。这样即使断线了几分钟，消息也不丢。
- **在线状态（presence）的 trade-off**：精确的在线状态需要每个 server 维护心跳，跨 server 同步开销大。大多数系统用"最近 N 分钟内有活动"作为"在线"的近似，不做精确实时同步。
- SSE 的适用场景：单向推送（通知、feed 更新、进度条）用 SSE 比 WebSocket 简单很多，不需要处理双向通信的复杂性。Chat 必须用 WebSocket；订单状态推送可以用 SSE。

**面试表达**：
> "协议不是重点，断线恢复才是重点。WebSocket/SSE 只是 delivery channel，不是 source of truth。断线后客户端用 last_seen_message_id 发 SYNC 请求，server 补齐缺失消息。水平扩展用 pub/sub 在 server 间转发（Redis Pub/Sub），所有 persistent state 在外部存储，server 本身无状态可随意重启。在线状态用"最近 5 分钟有活动"的近似，不做精确实时同步——精确同步的成本远超业务价值。"

---

## Pattern 15: TTL / Expiration 机制

**核心矛盾**：过期精度 vs 实现成本

**适用设计题**：
- Ticketmaster（座位 hold 期限）
- Online Auction（竞拍截止时间）
- Rate Limiter（时间窗口）
- Session / Token 管理
- Price Drop Tracker（价格监控周期）

| 方案 | 机制 | 优点 | 缺点 |
|------|------|------|------|
| DB TTL（expires_at 字段） | 读时判断，后台清理 | 实现简单 | 删除不精确，不能保证到点立刻不可见 |
| Delay queue | 到期时发事件触发操作 | 更接近实时触发 | 大规模 delayed message 成本高 |
| Time bucket scanner | 周期性扫描过期分桶 | 稳定，可恢复，容易 backfill | 不精确，需要扫描代价 |

**关键细节与常见陷阱**：
- **Correctness 和清理要分开**：expires_at 判断保证正确性（过期的不可用），TTL 清理只是节省存储，两者是独立的。即使清理 job 挂了，系统的正确性也不受影响——只是多占了一些空间。
- Delay queue 的实现：Redis ZSET 按到期时间戳作为 score，后台进程 `ZRANGEBYSCORE key 0 now`，批量获取到期事件。这是非常常见的 pattern（Ticketmaster 的 seat hold 超时、消息提醒等）。
- **时钟漂移问题**：分布式系统中各节点的时钟不完全同步。expires_at 判断要用服务端时钟，不用客户端时钟。如果节点间时钟差 1-2 秒，可能导致 hold 刚到期就被另一个节点认为未过期。解法：给 expires_at 判断加一个 grace period（如 5 秒缓冲）。
- Ticketmaster 的 seat hold 完整流程：用户选座 → DB 写 `seat_hold(seat_id, user_id, expires_at = now + 10min)` → 支付 → `UPDATE seat SET status=sold WHERE status=hold AND expires_at > now AND user_id = X`。如果 10 分钟内没支付，后台 scanner 或 delay queue 触发 release（但 release 只是清理，不是正确性保证，因为读路径的 WHERE 已经排除了过期的 hold）。
- **TTL 和 GDPR**：用户数据删除请求（right to be forgotten）不能只靠 TTL，需要主动删除所有副本（主库、replica、cache、search index、backup）。TTL 只是自然过期，不能用于合规删除。

**面试表达**：
> "Correctness 由读路径判断 expires_at 保证——查询时 WHERE expires_at > now，不依赖后台清理是否执行。Delay queue（Redis ZSET 按时间戳排序）做到期触发，比如 seat hold 到期后触发 release。注意：清理和正确性是独立的，清理 job 挂了不影响正确性，只影响存储占用。时钟漂移问题：加 grace period（5 秒缓冲）避免边界误判。"

---

## Pattern 16: Search Index Freshness vs Correctness

**核心矛盾**：搜索新鲜度 vs 主链路稳定性

**适用设计题**：
- Ticketmaster（演出搜索）
- YouTube / Netflix（视频搜索）
- Job Scheduler（任务状态搜索）
- News Feed（内容搜索）
- Typeahead（实时搜索建议）

| 方案 | 机制 | 优点 | 缺点 |
|------|------|------|------|
| 同步写 index | 写 DB 同时更新 Elasticsearch | freshness 好 | index 故障影响主写路径 |
| 异步 CDC 更新 index | CDC 消费 binlog 异步写 index | 解耦主链路，index 可 replay | 短暂不可搜或搜到旧结果 |
| 异步 index + 查询前校验 | 搜索结果在返回前校验 DB 状态 | 性能和正确性平衡 | 查询链路复杂，多一次 lookup |

**关键细节与常见陷阱**：
- **Search index 的 schema 和 DB 的 schema 会漂移**：随着时间推移，两边字段不同步，CDC 的 mapping 可能失效。需要监控 index lag（CDC 落后多少），并定期做全量 reindex 验证一致性。
- CDC lag 的影响：CDC 通常延迟 100ms-几秒。对于 Ticketmaster 这种刚售出的票，用户搜索时可能还在 index 里显示"可购买"。Read-time validation 就是在这种情况下 catch 住错误：搜索结果出来后，batch fetch DB 确认当前可购状态。
- **Elasticsearch 的 near-real-time**：ES 默认刷新间隔 1 秒，刚写入的文档在 1 秒内不可搜（refresh_interval 可调）。这是索引新鲜度的另一层延迟，要和 CDC lag 叠加考虑。
- Read-time validation 的性能：对 10 个搜索结果做 batch DB lookup，延迟约 5-20ms（同 region），完全可以接受。但如果搜索结果是 100 条，batch 的行数也是 100，要注意查询计划（用主键 IN 查询，走索引）。
- **Index 和 DB 的最终一致性窗口**：可以用 `index_version` 字段（每次 DB 更新递增）存在 ES，搜索时如果发现 index_version < expected，主动触发 re-fetch 或标记该结果为"需要验证"。

**面试表达**：
> "搜索 recall 短暂变差可以接受，但不能返回无权限、已删除、不可购买、已过期的结果。Index 用 CDC 异步更新（延迟 100ms-几秒），查询结果返回前用 batch DB lookup 做 permission/state/expiration validation，10 条结果一次 IN 查询，延迟 5-20ms 可接受。要监控 CDC lag，定期全量 reindex 做一致性验证，lag 过大要告警——说明 search 结果严重滞后。"

---

## Pattern 17: Global Consistency / Multi-region

**核心矛盾**：跨区域一致性 vs 可用性和延迟

**适用设计题**：
- Google Docs / Collaborative Editing
- Uber（全球多区域部署）
- Payment System（跨区域账户）
- Chat App（全球用户）
- Key-Value Store（多区域复制）

| 方案 | 机制 | 优点 | 缺点 |
|------|------|------|------|
| 全局同步写 | 跨 region 同步复制 | 强一致 | 延迟高，可用性差，region failure 影响大 |
| Home region 写入 | 实体有自然 owner region | ownership 清晰，写入低延迟 | 跨 region 读取有复制延迟 |
| Active-active | 多 region 同时接受写入 | 区域故障影响小，低延迟 | 冲突解决、权限撤销、一致性语义复杂 |

**关键细节与常见陷阱**：
- **Region affinity 设计**：用户的 home region 通常由注册时的 IP 或显式选择决定，一旦确定不轻易迁移。迁移需要把数据从旧 region 复制到新 region，同时停写旧 region——这是一个复杂的双写窗口操作。
- **跨 region 读的 stale 程度**：异步复制通常有 100ms-几秒的延迟。对于"查看自己账户余额"这类操作，用户一般在 home region 操作，读到的是新鲜数据。跨 region 读只发生在用户出国时，短暂 stale 通常可接受。
- Active-active 的冲突解决是真正的难题：如果用户在 region A 和 region B 同时修改同一个文档，如何合并？Last-write-wins（时钟决定）会丢数据；CRDT（Conflict-free Replicated Data Types）可以自动合并，但只适合特定数据结构（计数器、集合）；人工合并（像 Git merge）对用户体验差。Google Docs 用 OT（Operational Transformation）解决这个问题。
- **Region failure 的 failover**：当 region A 挂了，流量要切到 region B。但 region B 可能有几秒的复制延迟。这段时间内用户可能读到旧数据，或者无法写（如果系统设计为只允许 home region 写）。要提前设计 failover 策略和用户沟通方案。
- DNS-based routing / Anycast 是多 region 流量分发的基础设施，通常由 CDN（CloudFront、Cloudflare）或 Global Load Balancer 处理。

**面试表达**：
> "默认 home region + async replication（100ms-几秒延迟）。用户数据有自然 owner region，写入在 home region，read 允许短暂 stale。只有关键 invariant（余额不透支、权限不越界）才跨 region 同步协调，不让全系统承担全局一致性成本。Active-active 可以提，但要说清楚冲突解决策略——Last-write-wins 丢数据，CRDT 只适合特定结构，Google Docs 级别的需要 OT。Region failure 要设计 failover 策略，说清楚 RPO（数据丢失上限）和 RTO（恢复时间上限）。"

---

## Pattern 18: Reconciliation

**核心矛盾**：实时性 vs 数据最终正确性

**适用设计题**：
- Ads Click Aggregation（广告计费必须最终准确）
- Payment System（账单对账）
- TopK / Trending（实时近似 + 离线校正）
- Monitoring System（指标对账）
- Inventory Management（库存对账）

| 方案 | 机制 | 优点 | 缺点 |
|------|------|------|------|
| 完全依赖实时链路 | stream processing 结果直接使用 | 实时 | late event/duplicate 污染结果 |
| 离线 batch 重新计算 | 每天/每小时跑 batch job 重算 | 准确，可修正历史 | 延迟高，不能服务实时体验 |
| 实时 serving + 离线 reconciliation | 实时给用户看，离线校正计费数据 | 体验实时，最终正确 | 双路径，merge 规则需要清晰 |

**关键细节与常见陷阱**：
- Reconciliation 的触发时机：不只是每天跑一次 batch，还要能在发现异常时手动触发特定时间段的重算。所以 reconciliation job 要设计成幂等的、支持任意时间范围重算的。
- **差异处理策略要提前定义**：离线和实时结果有差异时，是自动用离线覆盖（适合广告计费），还是告警人工审核（适合支付）？不同业务有不同策略，要说清楚。
- Late event 的处理：事件因为网络延迟、设备离线等原因，可能在发生后几小时甚至几天才到达系统。实时流处理通常只接受在 watermark 窗口内的 late event（比如延迟不超过 1 小时），超出的事件由 reconciliation 批量处理。
- **Reconciliation 的数据来源**：要有一个不可变的 raw event log（Kafka topic 或 S3 object）作为 reconciliation 的 source，而不是依赖可能被修改的数据库记录。这就是为什么 event sourcing 在计费系统里很重要。
- 差异率监控：正常的差异率应该是可预期的（比如 late event 导致的 0.01% 误差）。如果差异率突然升高，说明有系统 bug 或数据丢失，要立即告警。

**面试表达**：
> "实时链路服务用户体验，离线 reconciliation 负责最终计费正确性。关键设计点：(1) raw event log（Kafka/S3）不可变，是 reconciliation 的 ground truth；(2) reconciliation job 幂等，支持任意时间范围重算，方便手动触发；(3) 差异处理策略要提前定义——广告计费自动用离线覆盖，支付差异告警人工审核；(4) 监控差异率，突然升高说明系统 bug 或数据丢失。"

---

## Pattern 19: Sliding Window / Time Window 聚合

**核心矛盾**：统计精度 vs 内存和计算成本

**适用设计题**：
- Rate Limiter（过去 N 秒请求数）
- Trending Hashtags（过去 1 小时热词）
- Ads Click Aggregation（时间窗口点击数）
- TopK（实时 topk 统计）
- Monitoring / Alerting（异常检测窗口）

| 方案 | 机制 | 优点 | 缺点 |
|------|------|------|------|
| 精确 event list | 保留每个 event 时间戳，滑动淘汰 | 准确 | 内存大，淘汰旧事件成本高 |
| Bucket aggregation | 按固定时间 bucket 聚合 | 内存可控，更新快 | 边界有精度损失（最多一个 bucket 误差） |
| Sketch / decay count | Count-Min Sketch, HyperLogLog | 省内存，高吞吐 | 有误差，不适合精确场景 |

**关键细节与常见陷阱**：
- Sliding window counter 的估算公式：假设窗口 60 秒，当前第 45 秒，上一个 bucket count = 80，当前 bucket count = 30。估算 = 80 × (15/60) + 30 = 20 + 30 = 50。误差在一个 bucket 量级内，对 rate limiting 足够精确。
- **Watermark 的设计**：流式处理的 watermark 定义了"我认为这之前的所有事件都已到达"。watermark 太激进（实时）会丢 late event；太保守（等太久）会增加延迟。Flink 的 bounded out-of-orderness watermark（等待 N 秒的乱序事件）是常见设置。
- TopK 的流式实现细节：Count-Min Sketch 统计每个 key 的近似频率，但不知道哪些 key 频率最高。需要配合 **min-heap**（维护当前 topK 候选）：新来一个 key，用 CMS 估计其频率，如果比 heap 最小值大，替换进去。
- **时间窗口的语义**：tumbling window（不重叠，每 1 分钟一个桶）vs sliding window（重叠，每秒滑动一次）vs session window（按活动间隔分割）。Rate limiting 用 sliding；trending 用 tumbling；用户行为分析用 session。
- ClickHouse / Druid 的时间分区：按时间字段分区，历史数据查询可以跳过无关分区，大幅减少扫描量。这是 OLAP 系统能处理任意时间范围查询的关键。

**面试表达**：
> "Rate limiting 用 sliding window counter（两个 bucket 估算，内存可控，误差一个 bucket 量级内）；trending 用 tumbling window + Count-Min Sketch 近似 TopK；任意时间范围的精确查询走 ClickHouse/Druid（按时间分区，历史数据跳过无关分区扫描）。Flink 的 watermark 设置要说：用 bounded out-of-orderness，等待 N 秒乱序事件，平衡延迟和完整性。"

---

## Pattern 20: ML Feature Consistency

**核心矛盾**：feature 计算一致性 vs 工程复杂度

**适用设计题**：
- Short Video Recommendation（推荐系统）
- High-risk Account ML Pipeline（风控）
- Restaurant Recommendation（个性化推荐）
- Ads Ranking（广告排序）
- RAG Chatbot（embedding freshness）

| 方案 | 机制 | 优点 | 缺点 |
|------|------|------|------|
| 离线和在线各写一套 feature logic | 各自维护特征计算 | 开发快 | training-serving skew，效果不可预测 |
| Feature Store 统一定义 | Feast/Tecton 统一管理 | 特征复用，离线在线一致 | 平台复杂，治理成本高 |
| Point-in-time join + online decision logging | 训练时按请求时刻 join 特征，记录线上决策 | 避免 data leakage，可 replay/debug | 数据工程复杂，存储成本高 |

**关键细节与常见陷阱**：
- **Training-serving skew 是 ML 系统最常见的线上问题**：训练时 feature 计算逻辑和线上 inference 时逻辑不一致，导致模型在训练集上效果好，线上效果差。Feature Store 的核心价值就是保证两者用同一份特征定义。
- **Point-in-time correctness**：训练数据里，某个时刻的 feature 值必须是**那个时刻**的真实值，不能用未来的数据。比如训练"用户会不会点击这个推荐"，feature 里的"用户过去 7 天点击次数"必须用那条样本**当时**的值，不能用现在的值（data leakage）。这要求 feature store 支持 point-in-time join。
- **Feature freshness 的分层**：不同特征有不同的更新频率。用户长期兴趣（月维度）可以 T+1 更新；用户近期行为（天维度）可以小时级更新；实时场景（会话级）需要几分钟级更新。Feature store 要支持多种 freshness SLA。
- **Online vs offline feature store 的架构**：offline store（Hive/S3）存历史特征，用于训练；online store（Redis/DynamoDB）存最新特征，用于 inference。两者要保持一致，常见做法是 batch job 把 offline 的特征同步到 online store。
- Feature vector logging 的重要性：线上每次推断要记录用了哪些特征值，这样出了问题可以 replay（用相同的特征值重跑推断，看结果是否一致），也可以用这些数据做再训练。

**面试表达**：
> "推荐/风控系统必须讲四个点：(1) Feature freshness——用户长期兴趣 T+1，近期行为小时级，会话级需要分钟级；(2) Point-in-time correctness——训练时 feature 必须是样本发生时刻的值，防止 data leakage；(3) Feature Store 统一 offline/online 特征定义，消除 training-serving skew；(4) Feature vector logging——每次 inference 记录用的特征值，用于 replay debug 和再训练。"

---

## Pattern 21: Exploration vs Exploitation

**核心矛盾**：短期指标 vs 长期学习效果

**适用设计题**：
- Short Video Recommendation（抖音/TikTok 推荐）
- News Feed Ranking
- Ads CTR 预估
- Restaurant / Product Recommendation
- Game Matchmaking

| 方案 | 机制 | 优点 | 缺点 |
|------|------|------|------|
| 只推最高分 | 纯 exploitation | 短期 CTR 高 | 头部效应，冷启动差，反馈闭环偏置 |
| 固定 exploration slot | 预留 N% 流量探索 | 容易实现和解释 | 探索效率低，可能浪费流量 |
| Contextual bandit / Thompson Sampling | 基于不确定性驱动探索 | 长期学习更好，冷启动友好 | 实现复杂，评估和 guardrail 难 |

**关键细节与常见陷阱**：
- **Feedback loop bias（反馈循环偏置）**是纯 exploitation 最危险的问题：系统只推高分内容 → 用户只看到这些内容 → 只有这些内容有点击数据 → 模型训练时低分内容的 label 全是 0（因为从没被展示过）→ 模型认为低分内容确实不好 → 更不推。这个偏置会越来越强，最终形成 filter bubble。
- **冷启动的正确解法**：新内容没有点击数据，用内容特征（title embedding、类别、作者历史）初始化得分，而不是默认给低分不推。这样新内容至少有展示机会，才能收集到真实的用户反馈。
- Guardrail metrics 的设计：探索预算不能只看 CTR 下降多少，还要看用户留存率、投诉率、diversity 指标。如果探索导致用户体验显著下降（比如留存率下降 2%），要自动缩减探索比例。
- Thompson Sampling 的直觉：每个 item 维护一个 Beta 分布（代表对 CTR 的不确定性），每次请求时 sample 一次，不确定性高（新内容）更容易被 sample 到高值。随着数据积累，分布收紧，不确定性降低，探索自然减少。这比固定 exploration rate 更高效。
- **A/B 实验和 exploration 的区别**：A/B 是为了评估策略效果（实验后撤掉其中一个）；exploration 是持续进行的在线学习（bandit 策略永久运行）。两者都要有，不能混淆。

**面试表达**：
> "推荐系统不能只讲 ranking。纯 exploitation 会造成反馈循环偏置，越来越向 filter bubble 退化——没被展示的内容永远没数据，模型认为它们不好，更不推。解法：固定 exploration budget（如 10% 流量），用 Thompson Sampling 驱动探索（不确定性高的内容自然获得更多展示机会）。冷启动用内容特征初始化，不默认低分不展示。Guardrail metrics：探索不能让用户留存率下降超过阈值，自动回缩。"

---

## Pattern 22: Observability / Debuggability

**核心矛盾**：系统可解释性 vs 存储和性能成本

**适用设计题**：
- LLM Inference System（请求路由和模型版本）
- Ads Ranking / Recommendation（为什么推了这个）
- Uber / Food Delivery（为什么派了这个 rider）
- Payment System（为什么交易被拒绝）
- Job Scheduler（任务为什么失败）

| 方案 | 机制 | 优点 | 缺点 |
|------|------|------|------|
| 只看 error rate 和 latency | 基础 metrics | 简单 | 无法解释业务决策错误 |
| 记录 decision reason / model version / feature vector | 每次决策写日志 | 可 debug，可审计，可回放 | 存储和隐私成本高 |
| Trace + replay + DLQ | 分布式 trace + 失败事件 DLQ | 故障恢复强，可重放修复 | 工程复杂，需要事件 schema 治理 |

**关键细节与常见陷阱**：
- **三个层次的 observability**：(1) Metrics（聚合指标，如 QPS、P99 latency、error rate）——告诉你系统是否健康；(2) Traces（单个请求的完整调用链）——告诉你某个请求为什么慢或失败；(3) Logs（结构化事件记录）——告诉你系统做了什么决策。三者缺一不可，只有 metrics 无法 debug 业务决策错误。
- **Decision logging 的设计**：对于推荐、风控、定价这类决策系统，每次决策要记录：请求 ID、时间戳、用户 ID、模型版本、输入特征 snapshot、输出结果、决策原因（如 blocked: fraud_score > 0.9）。这样线上出问题可以 replay 验证。
- DLQ 的设计要点：消息进 DLQ 时要记录失败原因和重试次数；DLQ 要有独立的 consumer 处理（人工审核或自动重放）；DLQ 也要有 TTL，不能无限积压；DLQ 的积压量要监控，突然增加说明系统有问题。
- **Distributed tracing 的 sampling 策略**：全量 trace 成本太高，通常只采样 1%-10%。但对于失败的请求、慢请求（P99 以上），要 100% 采样，确保问题可复现。Head-based sampling（请求开始就决定采不采）vs tail-based sampling（请求结束后根据结果决定）——tail-based 对捕获错误更有效但实现复杂。
- Audit trail 的合规要求：金融和医疗系统要求记录所有敏感操作（谁在什么时间做了什么），且这些记录不可删除、不可篡改（append-only log）。这是 observability 的另一个维度。

**面试表达**：
> "Observability 三层：metrics 判健康，trace 找慢请求，structured log 记决策。推荐/风控必须有 decision logging：每次决策记录 model version + feature snapshot + 决策原因，能 replay 验证。DLQ 是异步系统标配：失败消息进 DLQ，记录原因和重试次数，DLQ 积压量监控告警。Tracing 做 tail-based sampling——正常请求低采样，失败/慢请求 100% 采样，确保问题可复现。"

---

## Pattern 23: Read Path vs Write Path 分离优化

**核心矛盾**：写优化 vs 读优化，不能两者兼得

**适用设计题**：
- News Feed（读优化 inbox）
- Log Aggregation / Monitoring（写优化）
- Leaderboard（读优化排名）
- S3 / Distributed File System（写优化 append log）
- Search（写异步，读 index）

| 方案 | 机制 | 优点 | 缺点 |
|------|------|------|------|
| 写优化 | append-only，延迟构建 read model | 写吞吐高 | 查询需要聚合或异步构建 |
| 读优化 | fanout write，预计算 read model | 读低延迟 | 写放大，派生数据一致性复杂 |
| 分层读写模型 | source of truth 写简单，read model 按查询优化 | 职责清晰，可独立扩展 | 多套数据，异步 pipeline 运维 |

**关键细节与常见陷阱**：
- **CQRS（Command Query Responsibility Segregation）**是 read/write 分离的正式名字。Command 端（写）处理业务逻辑，保证 invariant；Query 端（读）是优化过的 read model，可以是完全不同的数据结构。面试时说 CQRS 比只说"读写分离"更专业。
- Read model 的物化方式：(1) DB read replica（最简单，自动同步，延迟小）；(2) Materialized view（DB 内部预计算，查询快但更新有延迟）；(3) External read model（Redis/ES/ClickHouse，完全解耦，延迟可能更大）。选择取决于 freshness 要求和查询复杂度。
- **Read model 的 rebuild 能力**：当 read model 损坏或 schema 变更时，要能从 source of truth 完整重建。这要求 source of truth 有足够的历史数据（event log 或 snapshot + log），且 rebuild job 是幂等的。
- Append-only log 的查询挑战：如果写路径是 append-only（如 Kafka），查询"某个 entity 的当前状态"需要聚合所有历史事件。在线查询做不到这点，必须有 materialized view（预先聚合好的当前状态）。这就是 event sourcing + CQRS 的经典组合。
- **写路径的性能优化**：append-only 写入不需要读取当前值，直接 insert，吞吐高。但需要后台 compaction（把多条 update 合并成一条 snapshot）避免读路径 fan-out 太多历史记录。

**面试表达**：
> "这是 CQRS 的核心思想：write path 保证 invariant（用 MySQL/Spanner），read path 用优化过的 read model（Redis sorted set、ES、ClickHouse）承接不同 query pattern。关键能力：read model 要能从 source of truth 完整 rebuild——说明 source of truth 有足够历史数据，rebuild job 幂等。Event sourcing + CQRS 是经典组合：写 append-only events，read model 是物化的当前状态视图。"

---

## Pattern 24: Strong Consistency 只保护关键 Invariant

**核心矛盾**：全局强一致 vs 关键操作正确

**适用设计题**：
- 几乎所有设计题都需要这个判断
- Payment System（余额不能透支）
- Ticketmaster（座位不能超卖）
- Uber（司机不能同时接两单）
- Access Control（权限不能越界）

| 方案 | 机制 | 优点 | 缺点 |
|------|------|------|------|
| 全系统强一致 | 所有操作同步协调 | 语义简单 | 性能、可用性、跨 region 延迟都很差 |
| 全系统最终一致 | 所有操作异步 | 高可用，高吞吐 | 关键业务可能出错（oversell、重复扣款） |
| 关键 invariant 强一致，其余最终一致 | 区分操作类型 | 成本和正确性平衡 | 需要清楚划分哪些是关键 invariant |

**关键细节与常见陷阱**：
- **Invariant 的枚举是 Staff+ 设计的核心能力**：不只说"用强一致"，要说清楚哪些是 invariant、为什么这些不能出错，以及如果出错了业务后果是什么（double charge 导致财务损失、oversell 导致运营问题、权限泄露导致法律风险）。
- Serializable vs Read Committed 的区别：MySQL 默认 Read Committed，允许 phantom read（你查询时没有的行，事务提交后变有了）。库存和余额场景需要 `SELECT FOR UPDATE` 或 serializable isolation 防止并发读到同一个值都认为还有库存。
- **Conditional write 的两种形式**：(1) Optimistic locking：`UPDATE SET version=version+1 WHERE version=X AND qty>0`，失败则重试；(2) `SELECT FOR UPDATE` 悲观锁，锁住行再更新。高并发场景下，悲观锁减少无效更新，乐观锁在冲突少时性能更好。
- 最终一致的数据出错时的修复路径：如果 feed 数据错了，从 outbox replay 重建；如果 index 数据错了，从 DB 全量 reindex；如果 cache 数据错了，invalidate 让它重新从 DB load。每种 derived data 都要有对应的修复策略。
- **两阶段提交 vs Saga 的选择边界**：如果所有服务都在同一个 DB（或支持 XA 的 DB 集群），可以用 2PC；如果跨越了不同 DB 或外部 API（如支付网关），必须用 Saga。

**面试表达**：
> "先定义 invariant：库存不能 oversell（后果：顾客购票后没座位，严重运营问题）；余额不能透支（后果：财务损失）；权限不能泄露（后果：法律合规风险）。这些用 serializable transaction 或 conditional write + optimistic lock 保护。其余——cache、index、feed、analytics、notification——全部最终一致，可 replay，可 rebuild。一旦出错，修复路径清晰：replay outbox 重建 feed，reindex 重建搜索，invalidate 重建 cache。"

---

## Pattern 25: Workflow Engine vs Queue + Worker

**核心矛盾**：任务编排复杂度 vs 引入平台依赖

**适用设计题**：
- YouTube / Video Transcoding（多步骤流水线）
- Food Delivery / Ticketmaster（长流程订单）
- Job Scheduler（复杂 DAG 任务）
- Payment System（支付 + 出账 + 通知流程）
- Web Crawler（爬取 + 解析 + 索引流水线）

| 方案 | 机制 | 优点 | 缺点 |
|------|------|------|------|
| Queue + worker | 简单 async worker | 简单，吞吐高 | 多步骤状态、timeout、补偿要自己实现 |
| Cron / scanner | 定期扫 DB 状态推进 | 实现简单 | 准确性差，故障恢复和去重麻烦 |
| Durable workflow engine | Temporal / AWS Step Functions | retry/timeout/compensation 内建，状态清晰 | 引入平台依赖，运维复杂 |

**关键细节与常见陷阱**：
- **自己维护状态机的陷阱**：业务代码用 DB 状态字段（`status = PENDING/PROCESSING/DONE/FAILED`）实现工作流，看起来简单，但 retry 逻辑、timeout 处理、并发 worker 拿同一任务、部分失败的补偿——每一个都是 edge case，很难做对。Temporal 把这些 edge case 都内建了。
- Temporal 的核心原语：**Activity**（单个可重试的操作，如调用支付 API）和 **Workflow**（有状态的协调器，定义 Activity 的执行顺序和错误处理）。Workflow 代码在 Temporal 的 replay 机制下是确定性的——即使 worker crash，Workflow 会从 checkpoint 重新 replay，结果一致。
- 什么时候用 queue + worker，什么时候用 Temporal：
  - **Queue + worker**：单步操作、重试逻辑简单（固定次数 + backoff）、无跨步骤依赖
  - **Temporal**：多步骤、步骤间有依赖、有补偿逻辑、需要人工审批中断（waitForSignal）、执行时间可能很长（小时/天级）
- **Cron vs queue 的区别**：Cron 是时间触发（每 N 分钟扫一次 DB），适合批量处理；Queue 是事件触发（有新任务立即处理），适合低延迟。Cron 扫 DB 的问题：扫全表成本高，大量 NOOP（大多数记录不需要处理），状态转换不精确。
- Video transcoding 的流水线：上传完成 → 触发 transcoding workflow → 并行转多个 resolution（1080p、720p、480p）→ 每个 resolution 完成后推 CDN → 所有 resolution 完成后更新 metadata。Temporal 可以用 `ActivityGroup` 并行执行多个转码，等所有完成后再继续。

**面试表达**：
> "简单单步异步任务用 queue + worker，retry 逻辑内联。多步骤长流程（选座 → 支付 → 出票 → 通知，或上传 → 并行转多 resolution → CDN 推送）用 Temporal：每步 Activity 自动 retry，Workflow 代码通过 replay 在 crash 后恢复，waitForSignal 支持人工审批中断。Cron scanner 只适合定时批量，不适合低延迟事件驱动——扫 DB 有大量 NOOP 且状态转换不精确。"

---

## Pattern 26: Security / Privacy as Correctness

**核心矛盾**：权限控制层次 vs 性能

**适用设计题**：
- News Feed / Status（私密内容不能泄露）
- Chat App（消息隐私）
- Google Docs（文档权限）
- Search（搜索结果权限过滤）
- S3 / File Storage（文件访问控制）

| 方案 | 机制 | 优点 | 缺点 |
|------|------|------|------|
| 只在前端隐藏 | 不渲染 UI | 无安全性保障 | 完全不安全，API 可绕过 |
| 服务端统一鉴权 | 每次请求校验 token + permission | 权限边界清晰 | 每次读写都带上下文，增加延迟 |
| 加密 / Signed URL / E2EE | 内容加密，密钥管理 | 隐私强，泄露风险低 | 搜索、索引、调试、恢复更复杂 |

**关键细节与常见陷阱**：
- **Signed URL 的设计细节**：URL 包含 `expires_at` 和 HMAC 签名（用 server-side secret key 签名，防篡改）。短 TTL（如 15 分钟）意味着即使 URL 泄露，影响时间窗口有限。客户端需要定期刷新 URL，不能长期缓存。
- **权限变更的传播延迟**：用户把帖子设为私密后，CDN 可能还缓存着公开版本（CDN TTL 内）。需要在权限变更时主动 invalidate CDN cache（代价较高）或接受短暂的权限变更延迟（更实际）。这是 Security 和 Performance 的 trade-off，要明确说出来。
- **Search index 的权限过滤**：可以在 index 里存 `visibility` 字段，查询时带上过滤条件。但如果用户权限是动态的（如基于订阅状态），每次查询都要实时校验，不能依赖 index 里的静态字段。
- E2EE 的代价：消息加密后，server 端无法搜索内容，无法做内容审核，无法帮用户找回密钥（忘记密码后消息全丢）。WhatsApp 选择 E2EE，牺牲了消息搜索和内容审核能力。这是一个显著的 trade-off，面试中提出这个点会加分。
- **Principle of least privilege**：每个服务只能访问它需要的数据。Storage service 不应该能读到未授权的 bucket；API server 不应该能执行任意 SQL。在系统设计时就要规划权限边界，不要事后补加。

**面试表达**：
> "权限和隐私是 correctness invariant。Signed URL：URL 带 HMAC 签名 + 短 TTL（15 分钟），不直接暴露原始路径，TTL 过期后重新生成。CDN 权限变更有传播延迟——这是已知 trade-off，接受还是主动 invalidate 取决于内容敏感程度。E2EE 要主动说代价：server 无法搜索内容、无法内容审核、用户丢失密钥消息不可恢复——提出这个 trade-off 比只说'用 E2EE'更有深度。"

---

## Pattern 27: Model / Policy / Config Versioning

**核心矛盾**：快速迭代 vs 可回滚和可解释

**适用设计题**：
- Short Video Recommendation（模型版本管理）
- Ads Ranking（排序策略版本）
- Rate Limiter（限流策略版本）
- High-risk Account ML Pipeline（风控模型版本）
- Job Scheduler（调度策略版本）

| 方案 | 机制 | 优点 | 缺点 |
|------|------|------|------|
| 直接覆盖配置或模型 | 原地更新 | 简单 | 事故后难定位，无法回滚 |
| Versioned config/model + canary | 版本化发布，灰度流量 | 可灰度，可回滚，可对比 | 发布系统和审计复杂 |
| Shadow traffic + A/B test | 影子流量预测，线上实验 | 上线前发现问题，量化效果 | 实验分析复杂，需要 guardrail |

**关键细节与常见陷阱**：
- **Config 版本化是最容易被忽略的**：代码有 Git，模型有 MLflow，但 rate limit 阈值、feature flag、调度策略往往直接改 DB 或配置文件，没有版本记录，出了问题无法回溯。把配置存入有版本号的 config service（如 etcd、LaunchDarkly），每次变更都是新版本。
- Canary 的指标选择：不只看 CTR，还要看 guardrail metrics（用户投诉率、系统 error rate、P99 latency）。新模型可能提高 CTR 但增加 latency，导致用户体验变差。要等所有指标都稳定后才全量。
- **Model serving 的版本管理**：同时运行多个版本（Blue/Green 或 Canary），需要 model registry 管理版本，inference server 支持动态加载。旧版本要保留一段时间，方便回滚。
- Shadow traffic（影子流量）：把生产流量同步发给新模型，比较输出差异，但不影响真实结果。这样可以在上线前发现新模型的 edge case（比如对某些用户画像输出异常）。
- **Rollback 的速度很关键**：如果发现新模型有问题，需要在几分钟内回滚，不是几小时。所以旧版本要保持热备，rollback 只是切流量，不需要重新部署。

**面试表达**：
> "配置、策略、模型都要版本化，直接改 DB 没有版本记录是生产事故的根源。标准流程：新版本先用 shadow traffic 验证输出差异，再 1% canary，观察 CTR + guardrail metrics（latency、error rate、投诉率），稳定后再逐步扩大。旧版本保持热备，rollback 是秒级切流量，不是重新部署。每个决策记录用了哪个版本——出问题时能精确定位是哪个版本引入的。"

---

## Pattern 28: Data Retention / Compaction / GC

**核心矛盾**：数据可追溯性 vs 存储成本和性能

**适用设计题**：
- S3 / Distributed File System（对象版本管理）
- Key-Value Store（版本 compaction）
- Log Aggregation（日志保留策略）
- Chat App（消息保留）
- Monitoring System（metrics 保留周期）

| 方案 | 机制 | 优点 | 缺点 |
|------|------|------|------|
| 永久保留 | 不删除 | 可追溯 | 成本高，查询慢，隐私风险 |
| TTL 删除 | 自动过期 | 简单 | 不精确，派生数据可能残留 |
| Retention policy + compaction + legal hold | 分级保留，合规特殊处理 | 成本、合规、恢复平衡 | 需要数据生命周期治理 |

**关键细节与常见陷阱**：
- **存储分层（Hot/Warm/Cold）**是 retention 策略的基础：近期数据（hot，SSD，高 QPS）→ 较旧数据（warm，HDD，低 QPS）→ 归档数据（cold，S3/Glacier，偶尔访问）。分层降低成本，但查询历史数据时延迟增加。
- KV store 的 compaction 细节：LSM-tree（LevelDB/RocksDB）的 compaction 把多个 SSTable 合并排序，删除 tombstone（删除标记）和旧版本。Compaction 期间会占用额外 IO 和 CPU，可能影响在线读写性能（compaction stall）。生产系统要监控 compaction lag。
- **GDPR 的"被遗忘权"**：用户可以要求删除所有个人数据。这需要系统能找到所有存有该用户数据的地方（主库、replica、backup、data warehouse、search index、cache）并删除。这是一个工程上的巨大挑战，通常用 pseudonymization（用 user_id 替代 PII，删除用户时只需删除 user_id 到 PII 的映射表）来简化。
- Legal hold 的实现：当某个用户/账户处于法律调查中，其数据不能按 TTL 删除。需要在数据上打 `legal_hold = true` 标记，deletion job 跳过这些记录。Legal hold 解除后才可以正常删除。
- **Backup 和 retention 的区别**：backup 是灾难恢复（DB crash 后恢复数据），retention 是业务需求（保留 N 天的数据供查询）。两者都需要，但策略不同：backup 保留 30 天（覆盖最长的灾难发现时间）；业务 retention 按 SLA 设置（审计日志保留 7 年）。

**面试表达**：
> "数据生命周期三层：hot 数据（SSD，近 30 天）→ warm（HDD，30 天-1 年）→ cold（S3/Glacier，1 年以上）。KV store 要讲 compaction：LSM-tree 的 compaction 合并 SSTable、清理 tombstone，生产环境要监控 compaction lag 防止影响在线性能。GDPR 用 pseudonymization 简化删除：PII 只存在映射表，删用户只删映射，其余数据用 user_id 无法关联。Legal hold 打标，deletion job 跳过。"

---

## Pattern 29: Approximate vs Exact 精度选择

**核心矛盾**：计算精度 vs 性能和扩展性

**适用设计题**：
- TopK / Trending Hashtags（允许近似）
- Ads Click Aggregation（计费要精确）
- Unique Visitor Count（UV 允许近似）
- Rate Limiter（允许误差）
- Monitoring（异常检测允许近似）

| 方案 | 机制 | 优点 | 缺点 |
|------|------|------|------|
| 精确统计 | 精确 count，full scan | 正确性强 | 成本高，扩展难 |
| 近似统计 | Count-Min Sketch, HyperLogLog, reservoir sampling | 省内存，高吞吐 | 有误差，不适合计费 |
| 实时近似 + 离线精确 | 实时 sketch 服务，离线 batch 校正 | 体验实时，最终可校正 | 两套结果需要 merge 规则 |

**关键细节与常见陷阱**：
- **先问业务需求，再决定精度**：UV（唯一访客数）用于展示给广告主看，0.8% 误差完全可接受，用 HyperLogLog 内存降低 1000 倍；广告计费必须精确，不能有误差。这个判断是 Staff+ 和普通候选人的分界线。
- HyperLogLog 的原理直觉：利用哈希值的前导零个数来估计集合大小。看到很多前导零，说明你可能遇到了很多不同的元素。Redis 内置 HyperLogLog，`PFADD` 添加，`PFCOUNT` 查询。
- **Count-Min Sketch 的误差特性**：只会高估，不会低估（因为 hash 碰撞只会让计数更大）。这对 TopK 是好的——错进了 TopK 的元素可以通过精确查询过滤掉，但漏掉的热门元素就永久丢失了。
- Reservoir sampling（水塘抽样）：从未知大小的流中均匀采样 K 个元素，内存只需要 O(K)。适合流量统计中的样本分析（比如采样 1% 的请求做详细分析）。
- **近似计算的组合使用**：实际系统往往多种近似技术组合：HyperLogLog 统计 UV（省内存）+ Count-Min Sketch 统计 TopK（省内存）+ reservoir sampling 采样详细数据 → 合并后既能估算整体，又能分析样本。

**面试表达**：
> "先问是否需要 exact。计费/库存精确；趋势/监控/UV 可近似。UV 用 HyperLogLog（Redis PFADD/PFCOUNT，12KB 内存，0.8% 误差）。流式 TopK 用 Count-Min Sketch + min-heap：CMS 估计频率，heap 维护当前 top N 候选，内存 O(width × depth + K)。Count-Min Sketch 只高估不低估——进了 TopK 的假阳性可以精确查询过滤，不会漏掉真正的热门 key。计费场景用实时近似 + T+1 离线精确对账，两套并行。"

---

## Pattern 30: Rate Limiter Deep Dive

**核心矛盾**：精度 vs 内存/性能，以及分布式环境下的一致性

**适用设计题**：
- Rate Limiter（最直接）
- API Gateway（通用限流）
- Ads Click Aggregation（防刷）
- Payment System（防重放）
- LLM Inference（请求配额）

| 算法 | 机制 | 优点 | 缺点 |
|------|------|------|------|
| Fixed window counter | 每个时间窗口一个计数器 | 实现简单，内存低 | 边界突刺：窗口末尾 + 下一窗口开头可以打双倍流量 |
| Sliding window log | 记录每个请求时间戳，滑动淘汰 | 精确 | 内存大，高 QPS 下每条请求都要存 |
| Sliding window counter | 当前 bucket + 上一 bucket 按比例估算 | 内存可控，精度够用 | 有误差（最多一个 bucket 量级） |
| Token bucket | 令牌以固定速率补充，请求消耗令牌 | 允许突发，控制平均速率 | 突发场景实现稍复杂 |
| Leaky bucket | 请求进队列，以固定速率出队 | 平滑输出速率 | 队列深度有上限，超出直接丢 |

**分布式实现**：
- Redis + Lua script：原子读写计数器，避免 race condition
- Redis `INCR` + `EXPIRE`：Fixed window，简单高效
- Redis sorted set：Sliding window log，`ZREMRANGEBYSCORE` 淘汰过期，`ZCARD` 计数
- 本地 + 全局混合：local cache 做粗粒度快速判断，减少 Redis 访问

**关键细节与常见陷阱**：
- **Rate limiter 在哪一层做**：API Gateway 做全局限流（防止整体 QPS 超限），Service 层做 per-user 限流（防止单个用户滥用），DB 层做连接池限流（防止 DB 被打崩）。三层分工不同，不是选一个，而是都要有。
- Token bucket 允许突发的量化：令牌桶最多积累 burst_size 个令牌（不是无限积累）。一个用户连续 10 秒没请求，积累了 burst_size 个令牌，然后瞬间发 burst_size 个请求全都放行。burst_size 要设置合理，不能无限大。
- **分布式环境下的误差来源**：多个 server 各自维护本地计数，合计可能超出限额（比如限 100 QPS，10 台 server 各自认为还有 10 个配额，同时放行 100 个）。解法：集中计数（Redis）牺牲一点延迟；或者给每台 server 分配 1/N 的配额（简单但不公平）；或者接受小幅误差（大多数场景可以）。
- **Lua script 的原子性**：Redis 的 `INCR` + `EXPIRE` 两条命令不是原子的，中间可能有并发插入。用 Lua script 把 check + increment 封装成原子操作，避免 race condition。Redis 6.2 以上也可以用 `INCR` + `SET PX NX`（Lua script 更通用）。
- 限流的用户体验设计：返回 429 时，response header 里加 `X-RateLimit-Limit`、`X-RateLimit-Remaining`、`X-RateLimit-Reset`，告诉客户端限额是多少、还剩多少、什么时候重置。`Retry-After` 告诉客户端多久后可以重试。

**面试表达**：
> "Rate limiter 三层：API Gateway 全局限流，Service 层 per-user 限流，DB 连接池限流。算法用 sliding window counter（两个 bucket，误差最多一个 bucket，对大多数场景可接受）。Redis Lua script 保证 check + increment 原子性。本地 cache 做 first-pass 降低 Redis 压力，允许小幅超量。返回 429 要带 Retry-After + X-RateLimit-* headers，客户端做 exponential backoff + jitter 防止 thundering herd。"

---

## Pattern 31: Distributed Lock 深入

**核心矛盾**：互斥保证 vs 锁过期/网络分区带来的安全性漏洞

**适用设计题**：
- Ticketmaster（防双重预订）
- Uber（防重复派单）
- Job Scheduler（防任务重复执行）
- Flash Sale（秒杀库存保护）

| 方案 | 机制 | 优点 | 缺点 |
|------|------|------|------|
| Redis SETNX + TTL | 单节点原子 set | 简单，延迟低 | 节点故障后锁丢失或残留，clock skew |
| Redlock（多节点） | 写入 N/2+1 个节点 | 可用性更高 | 仍有 clock drift 风险，Martin Kleppmann 有反驳 |
| DB advisory lock | `SELECT FOR UPDATE` | 强一致，依托 DB | 吞吐低，不适合高并发 |
| Fencing token | 锁带单调递增 token，写入时校验 | 防止 stale lock 写入 | 需要存储层支持校验 |

**关键 failure mode**：
- Client 拿到锁后 GC pause，锁 TTL 到期，另一 client 拿锁，两人同时执行 → **用 fencing token 解决**
- Redis 主库 crash，从库还没复制锁，新主库不知道锁 → **Redlock 部分缓解，但不完全解决**

**关键细节与常见陷阱**：
- **Fencing token 的实现**：锁服务（如 etcd）每次授予锁时返回一个单调递增的 token（如 epoch number）。Client 执行操作时把 token 带到存储层（如 `SET key value IF token > last_token`）。存储层保存最近见过的最大 token，拒绝过期 token 的写入。这需要存储层的配合，不是所有存储都支持。
- Redlock 的争议（Martin Kleppmann vs Antirez）：Kleppmann 认为 Redlock 在 clock drift 和进程暂停的场景下是不安全的；Antirez 认为 Kleppmann 设的假设过于严格。实践中的共识是：对安全性要求不高的场景（如防止缓存击穿）用 Redis 单节点锁足够；对安全性要求高的场景（如金融操作），用 Zookeeper 或 etcd，或者直接用 DB 事务。
- **锁的竞争压力**：分布式锁本身会成为热点。如果所有 worker 都争同一把锁，锁 server 成为瓶颈。解法：锁的粒度要细（per entity 而不是全局锁）；或者用 DB 的行级锁（`SELECT FOR UPDATE`）代替，利用 DB 本身的并发控制。
- **Lock and Load 反模式**：拿到锁 → 读取数据 → 修改数据 → 写回 → 释放锁。如果"修改数据"这步是远程调用或慢操作，锁会长时间持有，其他 waiter 被阻塞。锁应该只保护最小的临界区，不包含外部调用。

**面试表达**：
> "分布式锁无法提供完全安全的互斥——GC pause 导致锁过期，两个 client 同时在临界区，这是根本性的问题。正确用法：锁是性能优化层（减少 DB 竞争），正确性由 DB conditional write 保证。需要真正安全互斥时，用 fencing token + 存储层校验（存储层拒绝过期 token 的写入）。锁粒度要细（per entity），不用全局锁，避免锁 server 成为热点。临界区要小，不在锁内做外部调用。"

---

## Pattern 32: Consensus / Leader Election

**核心矛盾**：高可用 vs 强一致，Raft/Paxos 是基础但面试不需要实现细节

**适用设计题**：
- Key-Value Store（分布式共识）
- Job Scheduler（leader election）
- Distributed Lock Service（ZooKeeper/etcd 设计）
- Database Replication（primary election）

| 方案 | 机制 | 适用 |
|------|------|------|
| Single master + failover | 主从，主挂了手动或自动 promote | 大多数系统 |
| Raft consensus | 多数派写入，自动选主 | etcd, CockroachDB, TiKV |
| ZooKeeper ephemeral node | 临时节点 + watch | leader election, distributed coordination |
| External coordinator | 借用 etcd/ZooKeeper 做 leader election | Job Scheduler, Kafka controller |

**关键细节与常见陷阱**：
- **Raft 的面试处理方式**：面试官不期望你实现 Raft，但要知道核心思想：leader 选举需要多数派（quorum）同意，日志复制也需要多数派确认才能提交。这意味着 5 节点集群最多容忍 2 个节点故障，3 节点最多容忍 1 个。
- **Split-brain 问题**：网络分区时，集群可能出现两个"leader"（每个分区各自选了一个）。Raft 通过 quorum 防止这个问题——只有能联系到多数节点的那一侧才能选出 leader，另一侧 leader 失效。但对系统设计面试，说"用 etcd/ZooKeeper 避免 split-brain"就够了。
- etcd ephemeral lease 的工作原理：创建 lease（有 TTL），把 key-value 绑定到 lease，只要 lease 存活（定期 renew），key 就存在。进程 crash 后不再 renew，TTL 到期后 key 自动删除，其他候选者 watch 到 key 删除后竞选。
- **Leader election 的 failover 时间**：etcd 默认 session TTL 可以是几秒到几分钟。这段时间内没有 leader，系统要能优雅降级（不是崩溃，而是拒绝需要 leader 的操作，返回"leader election in progress"）。
- **不需要 leader election 的场景**：大多数无状态服务不需要 leader。只有需要单点协调的场景才需要（如 Job Scheduler 的任务分配、Kafka controller 的 partition leader）。分布式锁也是一种 leader election 的应用。

**面试表达**：
> "Job Scheduler 需要单 leader 保证任务不重复分配。用 etcd ephemeral lease 做 leader election：拿到 lease 的节点是 leader，定期 renew；crash 后 TTL 到期，其他节点 watch 到 key 删除后竞选。Failover 期间（TTL 时间内）拒绝任务分配请求，不让两个 leader 同时工作。核心原理：quorum 防止 split-brain，多数派决定谁是合法 leader。面试不需要实现 Raft，但要知道 5 节点容忍 2 个故障，3 节点容忍 1 个。"

---

## Pattern 33: Schema Evolution / Backward Compatibility

**核心矛盾**：快速迭代 vs 消费者兼容性，在异步系统里尤其重要

**适用设计题**：
- 几乎所有涉及 Kafka/事件流的设计
- Chat App（消息格式演化）
- YouTube（视频 metadata 演化）
- Payment System（事件溯源）

| 方案 | 机制 | 优点 | 缺点 |
|------|------|------|------|
| 直接改 schema | 全量更新 producer 和 consumer | 简单 | 停机或消费者不兼容 |
| Backward compatible schema | 新字段有 default，旧消费者可解析 | 滚动发布 | 需要纪律，不能删字段 |
| Schema Registry | Avro/Protobuf + Confluent Schema Registry | 版本化，兼容性检查自动化 | 额外基础设施 |
| Event versioning | 事件带 version 字段，消费者按版本分支处理 | 兼容新旧版本 | 消费者代码复杂 |

**关键细节与常见陷阱**：
- **三种兼容性方向**：(1) Backward compatible：新 producer，旧 consumer 能读（新字段有 default）；(2) Forward compatible：旧 producer，新 consumer 能读（消费者忽略未知字段）；(3) Full compatible：双向兼容。Schema Registry 的 compatibility mode 可以配置，生产推荐 BACKWARD_TRANSITIVE（所有历史版本都兼容）。
- **Avro vs Protobuf 的选择**：Avro 靠 schema 解析（schema 存在 registry，消息里不带 field 名），序列化后更紧凑；Protobuf 靠 field number（消息里带 tag），不依赖外部 registry，更适合 gRPC。Kafka 场景通常用 Avro + Schema Registry。
- **删字段的正确姿势**：不能直接删（旧 consumer 可能依赖），要先把字段标记为 deprecated（让 producer 停止写，consumer 忽略），等所有 consumer 都升级后再从 schema 里删除。这需要纪律和自动化检测。
- Event versioning 的实现：在事件里加 `event_version` 字段，consumer 按版本分支处理（v1 逻辑 vs v2 逻辑）。对重大变更（如字段含义改变），比 schema 扩展更安全，但消费者代码更复杂。
- **DB schema migration 的 backward compatibility**：加列要有 default（不然旧代码 insert 时 fail）；删列要先停止写入再删除（不然旧代码 select * 时多出不认识的列）；改列类型最危险，通常需要双写迁移。这和 Kafka schema evolution 的思路一致。

**面试表达**：
> "Kafka 事件不可变，schema 变更要向后兼容。用 Schema Registry（Avro）+ BACKWARD_TRANSITIVE 模式，新字段必须有 default，不能直接删字段——先 deprecated，等消费者升级后再删。Producer 先升级，consumer 后升级，滚动发布不停机。重大变更（字段含义改变）用新 topic + consumer migration，旧 topic 保留一段时间支持回滚。DB migration 同理：加列加 default，删列双阶段，改类型双写迁移。"

---

## Pattern 34: Capacity Estimation 思路

**核心矛盾**：面试里的容量估算不是精确计算，而是展示数量级直觉和 bottleneck 判断

**通用步骤**：
1. **DAU / MAU → QPS**：DAU × 操作次数 / 86400 = 平均 QPS，峰值 ×3-10
2. **存储估算**：每条记录大小 × 记录数 × 保留时间
3. **带宽估算**：QPS × 平均 payload 大小
4. **关键 bottleneck 判断**：是 CPU、内存、网络、磁盘 IO，还是 DB 连接数？

**常见数字记忆**：
- 1 DAU 用户每天 ~10 操作 → 100M DAU → 10B 操作/天 → 约 115K QPS，峰值 300K QPS
- 1 条 tweet/post ≈ 1KB，照片 ≈ 200KB，1 分钟视频 ≈ 10MB
- MySQL 单实例 ~5K-10K 写 QPS，读可更高
- Redis 单实例 ~100K-200K QPS
- Kafka 单 broker ~100K-200K 消息/秒

**关键细节与常见陷阱**：
- **先算 write QPS，再算 read QPS**：大多数系统读多写少（10:1 到 100:1）。先算写，确定存储需求和写入压力；再算读，确定 cache 和 read replica 需求。
- **峰值系数的选择**：平均 QPS × 3 是常见保守估算（日内 peak），突发性强的系统（如秒杀、演唱会开票）峰值可能是平均的 10-100 倍，需要特别说明并设计 admission control。
- **存储估算的维度**：(1) 每条记录大小（要算元数据、index 开销，不只是原始数据）；(2) 记录总数（DAU × 操作次数 × 保留天数）；(3) 副本数（通常 3 副本）；(4) 压缩比（文本可压缩 3-5 倍，视频不能压缩）。
- **带宽估算分 ingress 和 egress**：写入带宽（ingress） = 写 QPS × 平均消息大小；读取带宽（egress） = 读 QPS × 响应大小。视频系统 egress 是主要成本（CDN 费用）。
- **常用 bottleneck 数字**（面试必背）：
  - MySQL 单实例写：5K-10K QPS；加 read replica 可扩读到 ~100K QPS
  - Redis 单节点：100K-200K QPS；Redis Cluster 可线性扩展
  - Kafka 单 broker：100K-200K 消息/秒（取决于消息大小和副本数）
  - 一台 server 并发 WebSocket 连接：~100K（受文件描述符限制，可调整）
  - CDN 边缘节点带宽：数十 Gbps，吞吐远超 origin server
  - 单机 SSD 随机读：~500K IOPS；顺序读：~5 GB/s

**面试表达**：
> "容量估算的目的是找 bottleneck，不是精确数字。步骤：DAU → 写 QPS → 读 QPS（通常读是写的 10-100 倍）→ 存储（大小 × 数量 × 保留时间 × 副本数）→ 带宽（QPS × payload）→ 对照 bottleneck 数字（MySQL 单实例写 5K-10K QPS，Redis 100K-200K QPS）判断哪里会成为瓶颈，需要 sharding/caching/CDN。峰值系数 3-10 倍；突发型场景需要 admission control，不是单纯扩容能解决的。"

---

## 面试 Staff+ 总表达模板

```
1. 先定义 correctness boundary（开场必说）：
   "I would first define the invariants — what must never go wrong in this system:
    [inventory cannot oversell / no double charge / no unauthorized access]"

2. 分层处理：
   "The source-of-truth write path is protected with idempotency key + conditional update.
    Everything else — cache, index, feed, notifications, analytics —
    is eventually consistent, replayable, and rebuildable."

3. 承认 exactly-once 的局限：
   "I would not promise end-to-end exactly-once.
    At-least-once delivery + idempotent effects is the correct design for production systems."

4. 主动提热点（不要等面试官问）：
   "The average QPS is not the concern —
    the real risk is a single hot entity generating traffic orders of magnitude higher than average."

5. Multi-region 态度：
   "Default to home region + async replication.
    Only the narrowest set of critical invariants requires cross-region synchronous coordination."

6. Observability（设计收尾必说）：
   "Why was this request handled this way? Which version was used?
    Can we replay and fix derived data if something went wrong?
    DLQ + replay is the standard for any production async system."

7. Trade-off 表达公式：
   "Option A gives us [benefit] but costs us [downside].
    Option B gives us [benefit] but costs us [downside].
    Given [constraint], I would choose B, but we could revisit if [condition changes]."
```

---

## 常见题型 → Pattern 速查

| 设计题 | 核心 Pattern（按优先级） |
|--------|-------------------------|
| News Feed | fanout hybrid (#6), source of truth vs derived (#2), outbox/CDC (#3), hot key (#12) |
| Ticketmaster | oversell (#8), TTL/expiration (#15), search index correctness (#16), saga (#9) |
| Uber / Food Delivery | geo index (#13), saga (#9), worker failure (#10), realtime push (#14), hot partition (#12) |
| Payment System | idempotency (#4), exactly-once (#1), saga (#9), reconciliation (#18), distributed lock (#31) |
| Ads Click Aggregation | exactly-once (#1), hot key (#12), approximate vs exact (#29), reconciliation (#18), time window (#19) |
| LLM Inference | backpressure (#11), hot key (#12), versioning (#27), observability (#22), rate limiter (#30) |
| Short Video Recommendation | ML feature consistency (#20), exploration vs exploitation (#21), versioning (#27), read/write separation (#23) |
| Job Scheduler | worker failure (#10), workflow engine (#25), ordering (#5), idempotency (#4), leader election (#32) |
| Chat App | ordering (#5), realtime push (#14), retention (#28), security (#26), schema evolution (#33) |
| Rate Limiter | sliding window (#19), hot key (#12), approximate vs exact (#29), rate limiter deep dive (#30) |
| Key-Value Store | cache consistency (#7), retention/compaction (#28), multi-region (#17), consensus (#32) |
| Web Crawler | idempotency (#4), worker failure (#10), exact vs approximate (#29), schema evolution (#33) |
| S3 / File Storage | read/write separation (#23), retention (#28), security (#26), multi-region (#17) |
| Google Docs / Collaborative Editing | ordering (#5), multi-region (#17), realtime push (#14), conflict resolution (OT/CRDT) |
