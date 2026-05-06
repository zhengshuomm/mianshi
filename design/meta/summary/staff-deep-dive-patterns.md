# Staff+ System Design Deep Dive Patterns

这份总结来自 `mianshi/design` 里的系统设计题库。目标不是背组件名，而是在 deep dive 里展示 Staff Engineer 的判断力：

- 先定义 correctness invariant。
- 再区分 source of truth 和 derived data。
- 对每个关键选择讲清 trade-off、failure mode、scale path。
- 不轻易承诺 exactly-once、global strong consistency、实时强一致。
- 任何异步链路都要能 retry、replay、rebuild、observe。

面试时可以把下面每个 pattern 当成一个“深挖模块”。当面试官追问一致性、扩展性、失败恢复、实时性、缓存、搜索、推荐、队列时，直接套用。

## 34. 题目到 Deep Dive Pattern 速查表

| 题目 | 最适合讲的 Pattern |
|---|---|
| Ads Click Aggregation | Exactly-once vs effect-once；Reconciliation；Durable raw log；OLAP vs real-time serving |
| A/B Test System | Model/Policy versioning；Experiment guardrail；Read/write path separation；Observability |
| Calendar System | Strong invariant for event updates；Conflict handling；Notification outbox；Recurring event expansion |
| Chat App | Per-thread ordering；Inbox/delivery guarantee；Outbox/CDC；WebSocket reconnect recovery；Multi-region home region |
| Food Ordering + Delivery | Saga state machine；Payment idempotency；Geo index；Rider lease；Realtime tracking not source of truth |
| Flight System | Saga/compensation；Seat inventory conditional update；External API failure；Reconciliation |
| Game Leaderboard | Redis ZSET vs DB index；Hot leaderboard sharding；Exact vs approximate rank；Anti-cheat |
| Google Doc | OT vs CRDT；Version history；Offline edit conflict；Multi-region consistency |
| High Risk Account ML | Feature consistency；Label delay/noise；Decision logging；Fail open vs fail closed |
| Hotel Reservation | Double booking；Payment hold TTL；Saga/refund；Strong invariant only for inventory |
| In-memory KV Rollback | MVCC/version pointer；WAL + snapshot；Retention/GC；Global vs per-key version |
| Job Scheduler | Lease/visibility timeout；Delayed queue vs scanner；At-least-once + idempotent worker；Workflow engine |
| Key-Value Store | Consistent hashing；Replication/quorum；WAL recovery；Hinted handoff；LSM read path |
| LeetCode | Sandbox isolation；Queue worker idempotency；Polling vs SSE；Storage DB vs S3 |
| Live Comment | SSE/WebSocket；Hot video partition；Pub/Sub fanout；Cursor recovery；Multi-DC CDC |
| LLM Inference | Admission control；Priority scheduling；KV-cache-aware routing；Streaming protocol；Billing/audit |
| Monitoring / Logs | Push vs pull；Cardinality control；PII masking；Downsampling/retention；Durable log |
| Netflix / YouTube | CDN/edge cache；Manifest authorization；ABR；Transcoding workflow；QoE observability |
| News Feed | Fanout-on-write vs read；Active user cache；Privacy invalidation；Read model rebuild |
| Online Auction | Unique winner invariant；Highest bid rollback；Cache/DB consistency；Payment trigger |
| Price Drop Tracker | Scheduler due table；CDC/trigger；Notification idempotency；TTL not correctness |
| Proximity / Uber | H3/S2 vs Geohash/PostGIS；Geo sharding；High-frequency location updates；Driver/rider lease |
| RAG Chatbot | Ingestion pipeline；Chunk/index freshness；Metadata filtering；Evaluation metrics；Replay/reindex |
| Rate Limiter | Token bucket vs sliding window；Local vs global quota；Redis hot key；Fail open vs fail closed |
| Restaurant Recommendation | Geo-first recall；Feature consistency；Exploration vs exploitation；Availability read-time validation |
| Robinhood | Order state machine；External exchange failure；Idempotency；Live price updates；Reconciliation |
| RSS News Aggregator | Polling + webhook；Feed cache freshness；Pagination consistency；Personalized materialized feed |
| S3 / Object Store | Object ID vs name；Versioning/delete marker；Replication vs erasure coding；Metadata consistency；GC |
| 秒杀 / Ticketmaster | Virtual queue；Conditional inventory update；Hold TTL；Hot event sharding；Async result notification |
| Short Video Recommendation | Multi-stage ranking；Realtime features；Exploration；Guardrail metrics；Training-serving skew |
| Slack / WhatsApp Status | Hybrid fanout；Search permission validation；E2EE/client-side search；Expiration read check |
| TopK / Trending Hashtags | Fixed vs arbitrary window；Exact vs sketch；Late/duplicate events；Hot partition；OLAP fallback |
| Tracing Service | Sampling strategy；Context propagation；Raw span vs summary；High cardinality tags；Replay/debug |
| TypeAhead | Trie vs prefix hash；Realtime trending；Offline rebuild；Ranking metrics；Cache freshness |
| Web Crawler | Frontier queues；Politeness/rate limit；URL/content dedup；Retry + DLQ；Crawl trap detection |

面试前快速用法：

- 涉及钱、库存、权限：先讲 **Strong invariant + source of truth + idempotency**。
- 涉及搜索、feed、cache：先讲 **Derived data + read-time validation + rebuild**。
- 涉及 stream/pipeline：先讲 **At-least-once + idempotent sink + replay/reconciliation**。
- 涉及实时推送：先讲 **WebSocket/SSE is delivery channel, not source of truth**。
- 涉及 ML/推荐：先讲 **feature consistency + online/offline metrics + guardrails**。

## 35. Deep Dive 极简方案优缺点表

| Deep Dive 点 | 方案 A | 方案 B | 方案 C |
|---|---|---|---|
| Exactly-once / Effect-once | At-least-once + 幂等<br>✅最常用、可 replay<br>❌sink 必须幂等 | Transaction / 2PC<br>✅语义强<br>❌慢且复杂 | Flink checkpoint + 幂等 sink<br>✅stream 友好<br>❌外部 sink 仍要配合 |
| Source of Truth vs Derived Data | 只读 SOT<br>✅正确<br>❌慢 | Derived read model<br>✅快<br>❌stale | SOT + derived validation<br>✅快且守住正确性<br>❌链路更复杂 |
| DB 和 MQ 一致性 | 直接双写<br>✅简单<br>❌可能漏事件 | Outbox<br>✅DB+event 原子<br>❌多 relay | CDC<br>✅低侵入<br>❌延迟和 schema 治理 |
| Idempotency / Dedup | Idempotency key<br>✅防重试<br>❌要存状态 | Unique constraint<br>✅强约束<br>❌冲突处理麻烦 | Dedup window<br>✅适合 stream<br>❌窗口外重复挡不住 |
| Ordering Guarantee | Global order<br>✅心智简单<br>❌瓶颈 | Per-entity order<br>✅可扩展<br>❌跨实体无序 | Timestamp order<br>✅实现简单<br>❌并发/时钟不稳 |
| Push vs Pull / Fanout | Fanout-on-write<br>✅读快<br>❌写放大 | Fanout-on-read<br>✅写轻<br>❌读复杂 | Hybrid active push<br>✅平衡实时和成本<br>❌routing 复杂 |
| Cache Consistency | Write-through<br>✅读简单<br>❌写慢且仍可能乱序 | Cache-aside<br>✅简单<br>❌stale/cache miss | Event-driven cache<br>✅可重放<br>❌最终一致 |
| Double Booking / Unique Winner | DB conditional write<br>✅正确<br>❌热点冲突 | Distributed lock<br>✅直观<br>❌TTL/死锁风险 | Single writer<br>✅顺序强<br>❌单点吞吐 |
| Saga / Compensation | Big transaction<br>✅强一致<br>❌跨服务差 | Saga<br>✅可扩展<br>❌补偿复杂 | Workflow engine<br>✅可观测可恢复<br>❌重组件 |
| Worker Failure / Lease | Health check<br>✅简单<br>❌慢且不准 | Lease / visibility timeout<br>✅可恢复<br>❌要续租 | Idempotent replay<br>✅失败可重跑<br>❌业务幂等要设计 |
| Backpressure / Admission | Infinite queue<br>✅不拒绝<br>❌尾延迟爆炸 | Bounded queue<br>✅保护系统<br>❌会拒绝 | Priority / shedding<br>✅保 SLA<br>❌公平复杂 |
| Hot Key / Hot Entity | 普通 hash<br>✅简单<br>❌热点无解 | Shard / bucket hot key<br>✅抗热点<br>❌聚合复杂 | Single entity actor<br>✅顺序正确<br>❌单实体吞吐有限 |
| Geo Index | PostGIS<br>✅准确灵活<br>❌写扩展弱 | Geohash<br>✅简单<br>❌边界问题 | H3 / S2<br>✅工业级分层<br>❌理解和运维复杂 |
| Polling / SSE / WebSocket | Polling<br>✅简单兼容<br>❌浪费/延迟 | SSE<br>✅单向推送简单<br>❌只适合 server push | WebSocket<br>✅双向低延迟<br>❌连接运维复杂 |
| TTL / Expiration | DB TTL<br>✅省事<br>❌不准时 | Delayed queue<br>✅更准<br>❌复杂 | Read-time expires_at<br>✅保证正确性<br>❌每次读要校验 |
| Search Freshness / Correctness | Sync index<br>✅新鲜<br>❌阻塞主链路 | Async index<br>✅稳定可重试<br>❌搜索延迟 | Read-time ACL/delete check<br>✅防泄露<br>❌查询更慢 |
| Multi-region Consistency | Single home region<br>✅简单正确<br>❌跨区延迟 | Active-active<br>✅低延迟<br>❌冲突复杂 | Local write + async replication<br>✅性能好<br>❌最终一致 |
| Reconciliation | 无对账<br>✅简单<br>❌错账难修 | Offline reconciliation<br>✅可修复<br>❌滞后 | Raw log replay<br>✅可回放审计<br>❌存储成本 |
| Sliding Window / TopK | Exact count<br>✅准确<br>❌state 大 | Sketch / approximate<br>✅省内存<br>❌有误差 | OLAP window query<br>✅任意窗口<br>❌延迟高 |
| Feature Consistency | Offline feature<br>✅稳定<br>❌不新鲜 | Realtime feature<br>✅新鲜<br>❌复杂 | Feature store<br>✅训练/服务一致<br>❌成本高 |
| Exploration vs Exploitation | Pure exploit<br>✅短期指标好<br>❌信息茧房 | Random explore<br>✅简单<br>❌体验波动 | Bandit<br>✅动态探索<br>❌调参与归因难 |
| Observability / Replay | Metrics only<br>✅便宜<br>❌难 debug | Logs/traces<br>✅可定位<br>❌成本高 | Replayable event log<br>✅可重建<br>❌治理复杂 |
| Read vs Write Optimization | Write-optimized<br>✅吞吐高<br>❌读慢 | Read model / materialized view<br>✅读快<br>❌stale | CQRS<br>✅边界清楚<br>❌系统复杂 |
| Strong Consistency Boundary | Global strong consistency<br>✅简单正确<br>❌慢/贵 | Per-entity invariant<br>✅高性价比<br>❌要定义边界 | Eventual consistency elsewhere<br>✅可扩展<br>❌需要补偿/校验 |
| Workflow Engine vs Queue | Queue + worker<br>✅轻量<br>❌状态分散 | Workflow engine<br>✅可恢复可观测<br>❌重组件 | DB state machine<br>✅可控<br>❌要自建调度重试 |
| Security / Privacy | Client-side filter<br>✅简单<br>❌会泄露 | Server-side ACL<br>✅安全<br>❌延迟高 | Token/signed URL<br>✅边界清楚<br>❌过期/撤销复杂 |
| Versioning | In-place update<br>✅简单<br>❌难回滚 | Versioned config/model<br>✅可回滚审计<br>❌治理成本 | Canary/shadow<br>✅低风险发布<br>❌成本更高 |
| Retention / GC | Keep all<br>✅可回放<br>❌贵 | TTL / compaction<br>✅省钱<br>❌丢历史 | Tiered retention<br>✅成本平衡<br>❌策略复杂 |
| Approximate vs Exact | Exact<br>✅可信<br>❌贵 | Approximate<br>✅快/省<br>❌误差 | Hybrid<br>✅热路径快、离线修正<br>❌双链路 |
| Materialized View | Query-time compute<br>✅新鲜<br>❌慢 | Precompute<br>✅快<br>❌stale | Incremental materialization<br>✅低延迟<br>❌一致性复杂 |
| Local vs Global Quota | Global quota<br>✅准确<br>❌跨区慢 | Local quota<br>✅低延迟<br>❌会超发 | Local + global sync<br>✅平衡<br>❌短暂不准 |
| Durable Log / Replay | State only<br>✅便宜<br>❌难恢复 | Durable log<br>✅可 replay<br>❌存储成本 | Log + snapshot<br>✅恢复快<br>❌生命周期复杂 |

## 0. 怎么把优缺点讲得更像 Staff+

普通回答经常只说“优点是简单，缺点是复杂”。Staff+ 的表达要更具体：这个方案为什么简单，复杂在哪里，规模变大后会发生什么，失败时系统怎么表现。

讲优点时，尽量覆盖这些维度：

- Correctness：它保护了哪个不变量，例如不超卖、不重复扣款、不越权读取。
- Latency：它是否缩短同步主链路，是否改善 p95/p99。
- Scalability：它是否能水平扩展，hot entity 下是否仍成立。
- Operability：它是否容易监控、重放、回滚、debug。
- Cost：它是省机器、省存储，还是省人力运维。

讲缺点时，也不要只说“复杂”，而要说明复杂来自哪里：

- Consistency cost：是否引入 stale data、乱序、重复消费、异步延迟。
- Failure mode：哪个组件挂了会造成什么结果，是丢数据、重复写、卡住，还是降级。
- Hotspot risk：平均 QPS 下成立，但 hot key 下是否崩。
- Recovery burden：出错后能不能 replay、reconcile、backfill。
- Product impact：用户看到的是等待、旧数据、失败、重复通知，还是错误结果。

推荐表达模板：

```text
这个方案的好处不是“简单”而已，而是它把复杂性留在异步链路，让同步路径只保护核心 invariant。
它的主要代价是读模型/派生数据会短暂 stale，所以我会用 read-time validation 或 reconciliation 把 correctness 拉回来。
如果规模继续变大，风险会从平均吞吐变成 hot entity，因此需要额外的 sharding、single writer 或 backpressure。
```

## 1. Exactly Once / At-least-once / Effect-once

**适用题目**

- Ads Click Aggregation
- TopK / Trending Hashtags
- Job Scheduler
- Web Crawler
- Chat App / Slack
- Monitoring / Tracing
- Food Ordering / Payment
- Price Drop Tracker

**要解决的问题**

- Queue、stream processor、worker 都可能 retry。
- Producer 可能重复发，consumer 可能重复消费，sink 可能重复写。
- 面试里最危险的说法是“我们用 Kafka，所以 exactly once 了”。Kafka/Flink 的 exactly-once 通常只覆盖内部处理，不自动覆盖外部 DB、Druid、Redis、第三方 API。

**方案 A：At-least-once + 幂等 sink**

- 适用场景：大多数生产系统。
- 做法：
  - 每个 event 带 `event_id` 或 deterministic business key。
  - sink 使用 upsert、unique key、dedup table、idempotency key。
  - consumer 可以安全 retry。
- ✅ 优点：
  - 可靠性强：consumer crash、worker restart、rebalance 后都可以重新消费，不会因为“只投递一次”而丢失关键事件。
  - 工程可落地：不用把 broker、processor、sink、外部 API 全部放进一个分布式事务，复杂性集中在 sink 幂等上。
  - 恢复友好：保留 raw event 后可以 replay、backfill、rebuild 派生表，适合 ads、metrics、search index 这类可重算链路。
  - Broker 无关：Kafka、SQS、Pub/Sub 都能使用这个模式，不依赖某个系统特定的 exactly-once 能力。
- ❌ 缺点：
  - 正确性压力转移到 sink：如果 DB insert、Redis increment、notification send、payment capture 不是幂等的，重复消费会变成重复副作用。
  - 去重状态有成本：dedup table 或 event_id window 会占存储；窗口太短漏掉迟到重复事件，窗口太长成本高。
  - 外部系统最难处理：第三方支付、短信、邮件、webhook 不一定支持 request id，需要额外保存发送状态和补偿逻辑。
  - Debug 要看两层：看到重复 event 不代表 bug，真正要检查的是最终 effect 是否重复，这要求日志和审计更完整。

**方案 B：Kafka transactional producer**

- 适用场景：一个 Kafka pipeline 中需要同时写多个 topic，且希望原子 commit/abort。
- 做法：
  - transactional producer 绑定 input offset 和 output topic commit。
  - 出错时 abort transaction。
- ✅ 优点：
  - 能把 Kafka input offset 和 output topic 写入绑定到一个 transaction，减少“offset 提交了但 output 没写”或“output 写了但 offset 没提交”的不一致。
  - 对同时写多个 Kafka topic 的 pipeline 有价值，例如一个 topic 写实时聚合结果，另一个 topic 写 audit event。
  - abort 语义清晰：processor 失败时可以丢弃未完成 transaction，避免下游看到半成品 Kafka output。
- ❌ 缺点：
  - 边界有限：它不覆盖外部 DB、Redis、Druid、ClickHouse、HTTP API，所以不能直接宣称业务端到端 exactly-once。
  - 延迟和吞吐有代价：transaction commit/abort 增加协调成本，高吞吐 pipeline 要调 transaction size 和 timeout。
  - 运维复杂度更高：transactional.id、producer fencing、长事务超时都会变成排障点。
  - 仍然需要 sink 幂等：一旦 Kafka output 被外部 consumer 写到 DB，最终效果还是由外部写入语义决定。

**方案 C：Flink checkpoint + transactional / idempotent sink**

- 适用场景：stream aggregation、window count、TopK、ads metrics。
- 做法：
  - Flink checkpoint 保存 offset + operator state。
  - sink 支持 2PC、事务 commit，或使用 deterministic key overwrite。
- ✅ 优点：
  - 对 stateful computation 很强：offset、window state、operator state 一起 checkpoint，失败恢复后不会从一个不一致的窗口状态继续算。
  - 适合复杂聚合：TopK、sliding window、join、session window 都可以通过 checkpoint 恢复状态。
  - 可以把 replay 范围控制在 checkpoint 之后，恢复成本比从头重算低。
  - 如果 sink 支持 transaction/2PC，可以把计算结果和 checkpoint 对齐，降低重复写风险。
- ❌ 缺点：
  - 外部 sink 是瓶颈：没有 transactional sink 或 deterministic upsert key，checkpoint 只能保证 Flink 内部一致，不能保证外部效果一次。
  - checkpoint 本身有开销：interval 太短影响吞吐，太长恢复时 replay 多，state 很大时 checkpoint 也可能拖慢 pipeline。
  - 处理 late event 仍然复杂：checkpoint 不能替你定义迟到数据如何修正已输出结果。
  - schema/version 变更要谨慎：恢复旧 checkpoint 时如果 state schema 不兼容，会影响升级和 rollback。

**推荐话术**

- 不承诺端到端 exactly-once。
- 更稳的说法是：
  - `Kafka/Flink 内部可以做到 exactly-once processing，但业务效果要靠 idempotent sink 或 transactional sink。`
  - `我会把目标定义成 effect-once：event 可以重复处理，但最终外部状态只变化一次。`
- 对 Druid/ClickHouse/OLAP：
  - 用 `window_start + ad_id + shard_id` 作为 deterministic segment/sequence。
  - 或记录 Kafka offset，避免重复 ingestion。

**容易踩坑**

- 只说 Kafka exactly-once，不说 sink。
- 只在 producer 去重，不在 consumer/sink 去重。
- 对 payment/notification 这类外部副作用没有 idempotency key。

## 2. Source of Truth vs Derived Data

**适用题目**

- Ticketmaster
- Food Ordering + Delivery
- Slack / Chat App
- WhatsApp Status Search
- News Feed
- Restaurant Recommendation
- Netflix / YouTube
- S3
- RAG / TypeAhead
- Leaderboard

**要解决的问题**

- 高 QPS 系统常常会引入 cache、search index、read model、CDN、feed inbox。
- 这些读模型很快，但会过期。
- Staff+ 关键是说清楚：哪个存储是 source of truth，哪些只是 derived。

**方案 A：所有读都查 source of truth**

- 适用场景：低 QPS、强一致读、管理后台、关键校验。
- ✅ 优点：
  - 正确性最直观：所有读都经过 authoritative state，权限、库存、订单状态、删除状态都是最新判断。
  - 失败模式简单：如果 source of truth 不可用，请求失败，而不是返回错误数据；对关键动作更安全。
  - Debug 容易：不需要判断 cache/index/inbox 是否过期，排查路径短。
  - 适合强校验：checkout、payment capture、ticket hold、private document read 这类动作可以把正确性放在同一个边界里。
- ❌ 缺点：
  - 扩展性差：source DB 被所有读请求打满后，会影响写路径，最终影响核心业务。
  - 查询模型受限：DB 不适合全文搜索、复杂 ranking、feed merge、TopK、地理召回等读模式。
  - p99 不稳定：复杂 query 和热点 entity 会拖慢主库，读写混跑时更明显。
  - 成本高：为了读扩展加大量 read replica，但 replica lag 又会重新引入一致性问题。

**方案 B：Derived store 直接服务用户**

- 适用场景：搜索、feed、推荐、首页、实时列表、CDN 播放。
- ✅ 优点：
  - 性能好：读模型可以完全按产品 query 优化，例如 search index 查关键词、inbox 查 feed、ZSET 查 leaderboard。
  - 隔离主库压力：高 QPS 读不会直接打 source of truth，保护写路径。
  - 可水平扩展：不同 read model 可以独立 shard、cache、replicate。
  - 支持复杂体验：推荐、搜索、feed、实时列表通常都依赖派生视图，不适合每次从原始表实时计算。
- ❌ 缺点：
  - stale data 是默认状态：CDC lag、cache invalidation 失败、worker backlog 都会让用户看到旧结果。
  - 权限风险更高：如果 search index 或 cache 没带权限上下文，可能返回用户不该看的内容。
  - 派生链路要可恢复：如果 index 更新丢事件，需要 replay/rebuild，否则错误会长期存在。
  - 多版本问题复杂：source schema、event schema、index schema 不一致时，查询结果可能混合新旧语义。

**方案 C：Derived store + read-time validation**

- 适用场景：搜索后下单、抢票、播放授权、隐私内容搜索。
- 做法：
  - 搜索/缓存先返回候选。
  - 最终返回或关键动作前查 source of truth 校验。
- ✅ 优点：
  - 性能和正确性平衡：搜索/feed/recommendation 先用快的候选池，最终关键动作再查 authoritative state。
  - 容忍派生延迟：index/cache 短暂 stale 不会直接变成超卖、越权、播放未授权。
  - 降低 rebuild 风险：派生数据坏了可以重建，source of truth 仍然保护核心 invariant。
  - 产品体验好：大多数浏览请求低延迟，只有 checkout/playback/open-private-doc 等关键动作多一次校验。
- ❌ 缺点：
  - 链路更长：多一次 source lookup 会增加延迟，尤其是批量校验时要注意 fanout。
  - 部分结果会被过滤：用户可能先搜到再发现不可用，需要产品上处理“刚刚售罄/不可配送”。
  - source 服务成为关键依赖：read-time validation 服务不可用时，必须决定 fail open 还是 fail closed。
  - 实现要避免 N+1：搜索返回 100 个候选时，校验必须批量化或只校验 top N。

**推荐话术**

- `Search index / cache / inbox / CDN 都不是 source of truth。`
- `它们可以最终一致，但下单、支付、出票、播放授权、权限判断前必须 read-time validation。`
- `派生数据要可 replay、可 rebuild，不能成为唯一状态。`

**容易踩坑**

- 把 Elasticsearch 当最终库存状态。
- 把 Redis cache 当订单状态。
- CDN URL 长期有效，绕过权限和过期控制。

## 3. DB 和 MQ 一致性：Outbox / CDC

**适用题目**

- Chat App / Slack
- Food Ordering
- Online Auction
- Price Drop Tracker
- WhatsApp Status
- Notification 系统
- Search Index 更新
- Ticketmaster

**要解决的问题**

- 常见主链路：写 DB 后发 MQ，让 worker 更新 cache/index/notification。
- 问题是 crash window：
  - DB commit 成功，但 MQ publish 前服务 crash，事件丢。
  - MQ publish 成功，但 DB commit 失败，产生脏事件。

**方案 A：业务代码里写 DB 后直接 publish MQ**

- 适用场景：低风险异步副作用，或 demo。
- ✅ 优点：
  - 实现快：业务代码写完 DB 后直接 publish，适合原型或低风险异步副作用。
  - 延迟低：不需要 outbox relay 或 CDC，事件可以马上进入下游。
  - 组件少：不用维护 outbox table、CDC connector、relay worker。
- ❌ 缺点：
  - 有不可避免的 crash window：DB commit 后服务 crash，MQ event 永远不会发；MQ 发出后 DB rollback，又产生幽灵事件。
  - 派生数据会永久错误：search index、notification、cache invalidation 丢事件后不会自动修正。
  - 补偿困难：除非有 audit log 或定期 full scan，否则很难知道哪个事件没发。
  - scale 后更危险：低概率 crash window 在高 QPS 下会频繁发生，变成日常数据不一致。

**方案 B：2PC**

- 适用场景：内部系统都支持 XA/2PC，且强一致要求极高。
- ✅ 优点：
  - 一致性最强：DB 和 MQ 要么一起 commit，要么一起 abort，可以消除 DB/MQ 双写窗口。
  - 语义直观：下游不会看到没有 DB 状态支撑的事件。
- ❌ 缺点：
  - 生态支持差：很多 MQ、NoSQL、外部 API 不支持 XA/2PC。
  - 可用性差：coordinator 或 participant 故障可能导致 transaction in-doubt，阻塞资源。
  - 延迟高：每次写需要 prepare/commit 两阶段，主链路变重。
  - 运维和恢复复杂：手动处理 prepared transaction 是生产事故里很痛的部分。

**方案 C：Outbox / CDC**

- 适用场景：生产系统里的 DB -> event -> cache/index/notification。
- 做法：
  - 同一个 DB transaction 写 business table 和 outbox table。
  - CDC 或 outbox relay 异步把事件发到 Kafka。
  - consumer 幂等更新派生系统。
- ✅ 优点：
  - 正确性边界清晰：business row 和 outbox row 在同一个 DB transaction 里提交，不存在“业务成功但事件完全丢失”的窗口。
  - 事件可观测：outbox backlog、CDC lag、failed event 都可以监控和告警。
  - 可恢复：relay/CDC 挂了以后可以从 outbox 继续发送，不影响主业务写入。
  - 不要求 MQ 参与事务：比 2PC 更容易在真实系统落地。
- ❌ 缺点：
  - 最终一致：下游 index/cache/notification 会有延迟，用户可能短暂看不到新数据。
  - 运维组件增加：CDC connector、relay worker、offset 管理、schema evolution 都要维护。
  - consumer 仍要幂等：relay 可能重复发送 outbox event，下游必须能去重。
  - outbox 表也会膨胀：需要 retention、归档、清理策略。

**推荐话术**

- `核心写路径只写 source of truth 和 outbox。`
- `Search index、notification、cache invalidation、analytics 都从 outbox/CDC 消费。`
- `如果 CDC lag 变高，系统可能 stale，但不会丢业务事实。`

**容易踩坑**

- 只说“写完 DB 发 Kafka”，不说 crash window。
- Outbox event 没有 event_id，consumer 不幂等。
- CDC schema 没有版本，后续演进困难。

## 4. Idempotency / Deduplication

**适用题目**

- 秒杀
- Ticketmaster
- Food Ordering
- Payment / Robinhood
- Job Scheduler
- Web Crawler
- Ads Aggregation
- LeetCode Submission
- Notification

**要解决的问题**

- 用户重复点击。
- 网络超时后客户端重试。
- Queue at-least-once 重投递。
- PSP webhook 重复回调。
- Worker crash 后重新执行。

**方案 A：客户端防重复点击**

- 适用场景：改善用户体验。
- ✅ 优点：
  - 用户体验好：减少重复点击和重复 loading，让用户感觉系统响应更确定。
  - 成本低：前端改动简单，可以立刻减少一部分重复流量。
  - 对后端有减压作用：尤其秒杀/下单按钮重复点击场景。
- ❌ 缺点：
  - 不具备安全性：恶意请求、脚本、刷新、移动端重试都能绕过。
  - 网络层重试仍存在：client timeout 后可能自动 retry，服务端仍会收到重复请求。
  - 多端问题无法解决：同一用户在多个设备提交，前端按钮状态无法共享。

**方案 B：Idempotency key / unique constraint**

- 适用场景：创建订单、支付、提交任务、发送消息。
- 做法：
  - Client 或 server 生成 idempotency key。
  - Server 保存 `key -> result`。
  - 重复请求返回第一次结果。
- ✅ 优点：
  - 正确性强：同一个业务意图只产生一次订单、支付、任务或消息。
  - 对 retry 友好：客户端超时后用同一个 key 重试，可以拿到第一次结果，而不是创建新资源。
  - 审计清晰：可以查到某个 idempotency key 对应的请求、结果、状态。
  - 适合外部回调：payment webhook、notification provider callback 都可以用 external_id 去重。
- ❌ 缺点：
  - key 作用域容易设计错：全局 key、per user key、per endpoint key 的语义不同，错了会误拒绝或误放行。
  - 需要存储结果：只存 seen 不够，重试时应该返回第一次 response 或当前状态。
  - TTL 是 trade-off：太短挡不住慢重试，太长占存储且可能误判新请求。
  - 请求一致性要校验：同一个 key 携带不同 request body 时应返回冲突，而不是复用旧结果。

**方案 C：Event dedup table / window dedup**

- 适用场景：事件流、click aggregation、monitoring、crawler URL。
- ✅ 优点：
  - 适合流式系统：event_id/window state 可以在 Flink/Kafka Streams 里本地维护，吞吐高。
  - 成本可控：只保留一定窗口内的 id，适合 click、impression、metric event。
  - 可以按业务 key 去重：例如 `impression_id + ad_id`、`url_hash`、`client_event_id`。
- ❌ 缺点：
  - 无法无限去重：保留所有 event_id 成本过高，因此通常只能窗口内去重。
  - late duplicate 风险：非常晚到的重复事件可能被当成新事件。
  - state backend 压力大：高 cardinality event_id 会增加内存/磁盘状态。
  - 需要和业务语义对齐：同一用户两次真实点击和重复上报不能混淆。

**推荐话术**

- `幂等要覆盖所有副作用边界：API、queue consumer、payment webhook、notification sender。`
- `幂等不是只防用户重复点击，而是系统 retry 模型的一部分。`

**容易踩坑**

- idempotency key 不保存结果，只保存 seen，导致重试无法返回一致 response。
- payment 只用 order_id 幂等，没有 payment_attempt_id，取消/重试语义混乱。

## 5. Ordering Guarantee：Global Order vs Per-Entity Order

**适用题目**

- Chat App / Slack
- Order System
- Job Scheduler
- Kafka Pipeline
- Google Doc / Collaborative Editing
- In-memory KV Rollback
- Auction

**要解决的问题**

- 系统事件可能乱序到达。
- 多 partition 提升吞吐后无法保证全局顺序。
- 但很多业务并不需要全局顺序，只需要某个实体内有序。

**方案 A：全局顺序**

- 适用场景：全局账本、极小范围串行系统。
- ✅ 优点：
  - 语义最强：所有事件有唯一顺序，读写冲突和状态回放都容易理解。
  - Debug 和审计简单：系统可以用一个全局 log 重放出完全一致的状态。
  - 适合小范围强一致场景：例如单账本、单 auction item、单库存 partition。
- ❌ 缺点：
  - 扩展性差：全局 sequencer 或单 partition 会成为吞吐瓶颈。
  - 可用性差：sequencer 故障会影响整个系统写入。
  - 跨 region 成本高：每次写都要跨区域协调，p99 延迟不可控。
  - 过度设计：大多数业务只需要局部顺序，追求全局顺序会牺牲太多。

**方案 B：Per-entity ordering**

- 适用场景：订单状态按 order_id，聊天消息按 thread_id，账户事件按 account_id。
- 做法：
  - Kafka partition key 使用 entity id。
  - 单 entity 事件进入同一 partition。
- ✅ 优点：
  - 正好匹配业务 invariant：订单状态只需 order 内有序，聊天只需 thread 内有序，账户事件只需 account 内有序。
  - 扩展性好：不同 entity 可以分布到不同 partition 并行处理。
  - 成本更低：不用全局协调，也能避免同一实体状态倒退。
  - 容易和 Kafka partition key、DB shard key 对齐。
- ❌ 缺点：
  - hot entity 问题仍然存在：一个超大 channel 或热门 event 仍可能打爆单 partition。
  - 跨实体操作需要额外协调：例如转账、跨账户事务、跨 thread 搜索排序。
  - partition 数变化会影响 ordering/consumer rebalance，需要设计 migration。

**方案 C：Version / sequence check**

- 适用场景：客户端可离线、重试、乱序；或者状态机必须防止倒退。
- 做法：
  - 每个 event 带 sequence/version。
  - DB conditional update：只接受 `new_version > current_version`。
- ✅ 优点：
  - 防状态倒退：旧事件即使晚到，也会因为 version 低被拒绝。
  - 支持幂等：重复 event 带相同 sequence，可以安全忽略或返回已有结果。
  - 适合离线/弱网客户端：客户端恢复后按 sequence 补发，服务端能检测 gap。
  - Debug 明确：看到 version conflict 可以定位乱序来源。
- ❌ 缺点：
  - 协议更复杂：谁生成 sequence、如何处理 gap、是否允许重排都要定义。
  - 多 writer 难处理：同一实体如果多个客户端同时写，sequence 分配可能冲突。
  - 需要额外存储 current_version，并在写入时 conditional check。
  - 用户体验可能受影响：缺少某个 sequence 时，后续更新是等待、拒绝还是暂存，需要策略。

**推荐话术**

- `我不会追求全局顺序，只保证业务实体内顺序。`
- `订单只需要 order_id 内部状态有序；聊天只需要 thread 内有序；全局顺序会让系统不可扩展。`

**容易踩坑**

- 所有 event 都放一个 Kafka partition。
- 不做状态版本检查，导致 delivered 后又被旧事件改回 preparing。

## 6. Push vs Pull / Fanout Strategy

**适用题目**

- News Feed
- Slack
- Chat App
- WhatsApp Status
- Live Comment
- Notification
- RSS Aggregator

**要解决的问题**

- 用户发布内容后，应该立刻写进每个 follower inbox，还是读的时候聚合？
- 读延迟和写放大之间有天然 trade-off。

**方案 A：Fanout-on-write**

- 适用场景：普通用户、follower 少、读延迟敏感。
- ✅ 优点：
  - 读路径极快：用户打开 feed/inbox 时直接读自己的 materialized view，不需要实时聚合很多 source。
  - 用户状态好维护：unread、cursor、delivery status、per-user filtering 都可以直接存在 inbox。
  - 适合移动端：减少冷启动时的多服务 fanout，省电省流量。
  - 可以预先排序：写入时按时间或 rank 组织，读时分页简单。
- ❌ 缺点：
  - 写放大严重：一个作者发一次消息可能写入百万 follower inbox。
  - 热点作者危险：大 V、大 channel 发布会造成瞬时写峰值和 queue backlog。
  - 权限变化修正成本高：follow/unfollow、block、privacy change 可能需要更新大量 inbox。
  - 存储成本高：每个用户都存一份派生 entry，重复数据多。

**方案 B：Fanout-on-read**

- 适用场景：大 V、超大 channel、写多读少内容。
- ✅ 优点：
  - 写路径轻：发布只写作者/频道自己的 source log，不需要写所有 follower。
  - 热点作者更安全：celebrity 发帖不会产生巨大写扩散。
  - 权限变化更自然：读的时候基于最新关系过滤，避免大规模回填。
- ❌ 缺点：
  - 读延迟高：用户关注很多 source 时，需要多路读取、merge、排序。
  - 分页复杂：cursor 要能表达多个 source 的进度，不是单表 offset。
  - cache 难度高：每个用户关注集合不同，个性化组合降低缓存命中。
  - 高活跃读会反向打爆 source store，尤其首页频繁刷新。

**方案 C：Hybrid**

- 适用场景：真实社交/feed 系统。
- 做法：
  - 普通作者 fanout-on-write。
  - 大 V/大 channel 标记为 pull source。
  - 读路径 merge inbox + pull source。
- ✅ 优点：
  - 把两类瓶颈分开处理：普通用户读快，大 V 写轻。
  - 成本更均衡：大多数内容走 inbox，少数热点 source 走 pull，不让极端 case 决定全局架构。
  - 演进灵活：可以根据 follower 数、活跃度、写频率动态切换策略。
  - 适合真实社交系统：News Feed、Slack 大 channel、Status 都是类似形态。
- ❌ 缺点：
  - 读路径复杂：要 merge inbox entries 和 pull source entries，排序、去重、cursor 都更难。
  - 分类策略需要调参：多大算大 V，按 follower 数还是活跃读者数，不同产品答案不同。
  - 一致性更难解释：用户可能看到 inbox 内容已到，但 pull source 内容因 cache 还没更新。
  - 运维复杂：要监控 fanout backlog、pull source cache hit、merge latency。

**推荐话术**

- `普通用户 fanout-on-write，大 V fanout-on-read。`
- `read path merge 两类候选，热点 pull source 可以 cache 最近内容。`

**容易踩坑**

- 所有 channel 都 fanout-on-write，超大 channel 发布时写爆。
- 只说 fanout-on-read，不处理读路径分页和排序。

## 7. Cache Strategy and Consistency

**适用题目**

- Ticketmaster
- Food Ordering
- Restaurant Recommendation
- News Feed
- Price Drop Tracker
- Rate Limiter
- Leaderboard
- Netflix / YouTube CDN
- Search 系统

**要解决的问题**

- Cache 带来低延迟，但 stale data 会导致错误。
- Staff+ 要讲 cache 的写策略、失效策略、fallback、是否允许旧数据。

**方案 A：Cache-aside**

- 适用场景：读多写少，允许短暂 stale。
- 做法：
  - 读 miss 查 DB，再写 cache。
  - 写 DB 后 invalidate cache。
- ✅ 优点：
  - 架构边界清晰：DB 仍是 source of truth，cache 只是读优化，故障时可以绕过 cache 回源。
  - 实现成本低：不需要复杂写路径，适合大多数读多写少系统。
  - 可逐步引入：可以先缓存热点 query/key，不需要全量改造数据模型。
  - 运维直观：cache hit rate、miss rate、DB fallback 都容易监控。
- ❌ 缺点：
  - cache stampede 风险：热点 key 过期时大量请求同时回源，可能打爆 DB。
  - stale data 难完全避免：写 DB 成功但 invalidate 失败，用户会继续读旧 cache。
  - 一致性窗口难解释：不同用户可能读到不同版本，尤其 inventory/permission 场景风险高。
  - 需要额外保护：TTL jitter、singleflight、negative cache、read-time validation 都要配合。

**方案 B：Write-through**

- 适用场景：希望 cache 和 DB 更新同步。
- ✅ 优点：
  - 写后读一致性较好：写请求完成后 cache 已更新，后续读更可能命中新值。
  - 对读热点友好：热点 key 不需要等下一次 miss 才刷新。
  - 适合配置、用户 profile、商品详情等写频率较低但读频率高的数据。
- ❌ 缺点：
  - 写路径变重：每次写都要同步更新 cache，增加 p99 和依赖失败面。
  - cache 故障语义复杂：如果 DB 写成功但 cache 写失败，要回滚、重试，还是接受 stale？
  - 写多系统成本高：大量更新 cache 可能没有读收益，反而浪费资源。
  - 多 cache 层更难：本地 cache、Redis、CDN 同时存在时，write-through 只能覆盖其中一层。

**方案 C：Write-behind**

- 适用场景：写多、允许短暂不一致，如计数、非关键状态。
- ✅ 优点：
  - 写延迟低：先写 cache/内存队列即可返回，适合非关键计数或高频 telemetry。
  - 可批量落库：把大量小写合并成批量写，降低 DB 压力。
  - 对写峰值有缓冲：短时间 spike 可以先堆在 cache/queue，再慢慢 flush。
- ❌ 缺点：
  - durability 弱：cache crash 或 flush worker bug 可能丢已确认写。
  - 恢复复杂：需要 WAL、queue 或 replay log，否则无法知道哪些写尚未落库。
  - 不适合强正确性：订单、支付、库存不能把 cache 当唯一已提交状态。
  - 用户读写语义复杂：用户看到写成功，但 DB 尚未持久化，后续故障可能回滚。

**推荐话术**

- `cache 只能优化读，不能替代 source of truth。`
- `关键动作前做 read-time validation。`
- `热点 key 用 TTL jitter、singleflight、request coalescing 防止 stampede。`

**容易踩坑**

- 用 Redis 存库存但没有 DB reconciliation。
- cache 失效没版本，旧写覆盖新写。
- CDN URL 长 TTL，内容下架后仍可访问。

## 8. Double Booking / Oversell / Unique Winner

**适用题目**

- Ticketmaster
- 秒杀
- Hotel Reservation
- Online Auction
- Food Ordering
- Uber/Rider Assignment
- Robinhood Order

**要解决的问题**

- 多个用户同时抢同一个 seat/room/item。
- 多个订单同时分配同一个 rider。
- 拍卖最高价撤回后 winner 要一致。

**方案 A：Distributed lock**

- 适用场景：短临界区、快速实现、冲突不极端。
- ✅ 优点：
  - 接入快：不用改 DB schema 或数据模型，service 层就能先挡住一部分并发冲突。
  - 对短临界区有效：例如短时间 hold rider、seat、job ownership，配合 TTL 可以自动释放。
  - 能减少 DB 冲突：先在 cache/lock 层过滤一部分竞争，再进入 DB conditional update。
- ❌ 缺点：
  - 正确性不完整：lock 成功只说明拿到临界区，不代表最终 DB 状态一定成功更新。
  - TTL 很难选：太短会在业务未完成时释放，太长会降低恢复速度。
  - 分布式故障复杂：client pause、network partition、lock service failover 都可能导致双持有或误释放。
  - 仍需要 fencing token 或 DB 条件写，否则旧 lock holder 可能在 lock 过期后继续写。

**方案 B：DB conditional update / optimistic lock**

- 适用场景：库存、座位、rider 状态、balance。
- 做法：
  - `UPDATE seats SET status='held' WHERE seat_id=? AND status='available'`
  - DynamoDB conditional write。
- ✅ 优点：
  - 正确性落在 authoritative store：即使多个 service 并发请求，也只有一个 conditional update 成功。
  - 故障语义清楚：写成功就是状态改变，写失败就是没有拿到资源。
  - 不依赖额外 lock service，减少一个一致性组件。
  - 审计简单：DB 里能看到哪个 request/version 改变了状态。
- ❌ 缺点：
  - 热点竞争严重：热门座位、SKU、rider 状态会导致同一行/partition 大量条件写失败。
  - 重试会放大压力：失败请求如果立即 retry，会把热点 DB 进一步打爆。
  - 只解决单对象：跨多个 seat、多个房间、组合库存时，需要事务或更复杂 reservation model。
  - 用户体验可能是大量失败，需要 queue/waiting room/admission control 配合。

**方案 C：Single writer / partitioned command queue**

- 适用场景：极热点 entity，例如热门演唱会、秒杀 SKU、auction item。
- ✅ 优点：
  - 串行化最强：同一资源的所有命令按顺序处理，天然避免 double booking。
  - 状态机更简单：不需要到处加锁，冲突处理集中在一个 owner/partition。
  - 对极热点更可控：可以对某个 event/SKU 单独扩 worker queue、限流、监控 backlog。
  - replay 友好：命令 log 可以重放恢复出同样状态。
- ❌ 缺点：
  - 写吞吐受单 owner 限制：极端热点仍可能被单分区处理能力卡住。
  - failover 要谨慎：新 owner 必须从一致 log/state 接管，避免两个 writer 同时存在。
  - 分区策略影响大：资源迁移、扩容、rebalance 都可能影响 ordering。
  - 延迟可能增加：命令需要排队，尤其高峰抢票/秒杀时用户要等待结果。

**推荐话术**

- `分布式锁可以降低冲突，但最终正确性最好由 DB conditional update 或 single writer 保证。`
- `唯一 winner/不可超卖是 correctness invariant，不能依赖 cache 或异步 worker。`

**容易踩坑**

- lock 成功后写 DB 失败，但没有释放或补偿。
- lock TTL 到期后第一个请求还在执行，第二个请求也进入临界区。

## 9. Saga / Compensation for Long Business Flow

**适用题目**

- Food Ordering
- Ticketmaster
- Flight System
- Hotel Reservation
- Robinhood
- Job Scheduler
- Online Auction

**要解决的问题**

- 一个业务流程横跨订单、支付、库存、外部 API、人工审核。
- 不能把所有参与方放到一个 ACID transaction 里。

**方案 A：Local ACID transaction**

- 适用场景：同一个 DB 内部短流程。
- ✅ 优点：
  - 一致性强：同一个 DB transaction 内的状态要么全部提交，要么全部回滚。
  - 开发和 debug 简单：没有异步补偿、事件乱序、外部回调等复杂状态。
  - 适合短流程：例如单表库存扣减、单订单状态更新。
- ❌ 缺点：
  - 适用范围窄：外部支付、商家、rider、航空公司、交易所都不在你的 DB transaction 里。
  - 锁持有时间不能长：用户支付、人工确认、外部 API 调用不能放在 DB transaction 内等待。
  - 扩展到微服务后会变成分布式事务问题。

**方案 B：2PC**

- 适用场景：少数内部强一致资源。
- ✅ 优点：
  - 理论一致性强：所有参与方 prepare 成功后再 commit，可以避免部分提交。
  - 对少数内部强事务资源有价值，例如两个同构数据库之间的严格迁移。
- ❌ 缺点：
  - 外部服务很少支持：支付、商家、rider、第三方 API 基本不提供 2PC participant。
  - 阻塞风险高：coordinator 或 participant crash 后，资源可能长时间处于 prepared 状态。
  - 可用性差：为了强一致牺牲可用性，和互联网订单长流程不匹配。
  - 运维复杂：in-doubt transaction 需要人工或专门恢复流程。

**方案 C：Saga + 状态机 + 补偿**

- 适用场景：真实业务长流程。
- 做法：
  - 每步本地提交。
  - 失败后执行补偿，例如 refund、release hold、re-dispatch。
  - 状态机记录每一步结果。
- ✅ 优点：
  - 适合真实业务：每一步 local transaction，支付、库存、出票、派单、通知都可以独立重试。
  - 可处理长时间流程：用户支付、商家接单、rider 接单、人工审核都可以是 pending 状态。
  - 补偿语义清晰：失败后 refund、release hold、cancel order、re-dispatch。
  - 可观测性好：状态机记录每一步，便于客服、审计、debug。
- ❌ 缺点：
  - 最终一致：用户可能看到处理中，系统需要解释 pending、retrying、canceling 等状态。
  - 补偿不是总能完全恢复：比如用户已经收到通知、商家已经备餐、外部订单已经部分执行。
  - 状态机复杂：要防止状态倒退、重复补偿、超时和并发事件。
  - 测试成本高：需要覆盖大量失败组合，而不只是 happy path。

**推荐话术**

- `下单/支付/出票/派单不是一个分布式事务，而是一个可恢复的状态机。`
- `每个步骤 local transaction，失败靠 compensation。`

**容易踩坑**

- 支付成功但订单失败，没有退款流程。
- 状态机允许倒退。
- timeout 没有明确处理。

## 10. Worker Failure / Lease / Visibility Timeout

**适用题目**

- Job Scheduler
- Web Crawler
- LeetCode Runner
- Video Transcoding
- Food Delivery Dispatch
- Price Drop Scraper
- Notification Worker

**要解决的问题**

- Worker 拿到任务后可能 crash。
- 任务可能执行很久。
- 同一个任务不能永久卡住，也不能重复产生不可控副作用。

**方案 A：worker 直接消费并删除任务**

- 适用场景：可丢弃、低价值任务。
- ✅ 优点：
  - 实现最简单：worker 拿到任务后立刻删除或 ack，不需要维护 lease、heartbeat、重试状态。
  - 延迟低：没有额外的 visibility timeout 或 ownership 协议。
- ❌ 缺点：
  - worker crash 后任务丢失：任务已从 queue 删除，但实际 side effect 没完成。
  - 不适合重要任务：订单、支付、转码、爬虫进度都不能接受这种丢失。
  - 无法区分执行中和已完成，debug 和补偿困难。

**方案 B：Visibility timeout / lease**

- 适用场景：SQS、job scheduler、dispatch lease。
- 做法：
  - worker 获取任务后任务暂时不可见。
  - 成功后 ack/delete。
  - 超时未 ack 则重新可见。
- ✅ 优点：
  - crash recovery 好：worker 挂了以后 lease 到期，任务自动回到队列或可被其他 worker 抢占。
  - 不需要中心化健康检查：ownership 由 lease/visibility timeout 自然表达。
  - 适合水平扩展：增加 worker 就能消费更多任务，queue 负责分发。
  - 对长任务可支持 heartbeat extend lease，避免任务执行中被误抢。
- ❌ 缺点：
  - 任务可能重复执行：lease 到期但原 worker 仍在执行，另一个 worker 也可能开始执行。
  - heartbeat 逻辑复杂：extend 太频繁增加负载，太慢又可能误超时。
  - 需要幂等 side effect：写 DB、发通知、调用外部 API 都必须能处理重复。
  - 无法自动判断业务进度：heartbeat 活着不代表任务没有卡在某一步。

**方案 C：Durable workflow**

- 适用场景：多步骤、长时间任务，如转码、订单、支付、复杂 job。
- ✅ 优点：
  - 状态持久：每一步执行到哪里、下一步是什么、失败原因是什么，都可以查询。
  - retry/timeout/compensation 模型清晰：不用散落在多个 worker 和 cron 里手写。
  - 适合人机混合流程：人工审核、支付等待、商家确认、转码 QC 都能纳入 workflow。
  - 运维友好：可以 pause、resume、retry、cancel 某个 workflow instance。
- ❌ 缺点：
  - 平台复杂度高：需要引入 Temporal/Step Functions 等基础设施，团队要理解 workflow replay 模型。
  - 对简单任务过重：发一条通知或普通异步写索引用 workflow 可能得不偿失。
  - activity 仍要幂等：workflow 会 retry activity，外部 side effect 不能重复。
  - vendor/platform lock-in：状态格式、调度语义、成本模型需要长期维护。

**推荐话术**

- `worker 拿到任务只是 lease，不是永久 ownership。`
- `所有 worker side effect 必须幂等，因为 lease timeout 后可能被重新执行。`

**容易踩坑**

- 任务执行时间超过 visibility timeout，被两个 worker 同时执行。
- heartbeat 成功但 worker 实际卡死，没有 progress check。

## 11. Backpressure / Admission Control / Queue Limit

**适用题目**

- LLM Inference
- Ticketmaster / 秒杀
- Web Crawler
- Monitoring Ingestion
- Job Scheduler
- Ads Aggregation
- Rate Limiter

**要解决的问题**

- 流量高峰时，如果系统无限接收请求，会导致 queue 变长、请求过期、tail latency 爆炸。
- Staff+ 要主动保护系统，而不是“加机器”。

**方案 A：无限排队**

- 适用场景：几乎不推荐，只适合完全离线任务。
- ✅ 优点：
  - 表面上不丢请求：所有请求都被接收，适合完全离线且没有用户等待 SLA 的任务。
  - 实现简单：不用设计 reject policy、priority、drop 策略。
- ❌ 缺点：
  - 请求会排到失去意义：用户已断连、ticket 已售罄、LLM 请求已超时，但系统还在消耗资源处理。
  - tail latency 爆炸：平均延迟可能还行，但 p99/p999 会不可控。
  - 积压流量会二次冲击：下游恢复后 backlog 一起涌入，可能再次打爆系统。
  - 缺乏反馈：客户端不知道该等待、重试还是降级，用户体验很差。

**方案 B：快速拒绝 / fail fast**

- 适用场景：请求超过 SLA、系统过载。
- ✅ 优点：
  - 保护核心服务：在系统过载时明确拒绝，避免资源耗在注定超时的请求上。
  - 用户反馈明确：客户端可以根据 `retry-after`、错误码、排队位置做 UX。
  - 降低雪崩风险：快速失败能保住已有健康请求的 p99。
- ❌ 缺点：
  - 短期体验差：用户会看到失败或稍后重试，而不是排队等待。
  - 策略要细：哪些请求拒绝、哪些排队、哪些降级，需要按业务优先级设计。
  - 可能引发重试风暴：如果客户端没有 backoff，fail fast 会变成更高 QPS。

**方案 C：Priority queue + degradation**

- 适用场景：不同请求价值不同。
- 做法：
  - 高优先级请求保留容量。
  - 低优先级降级、采样或拒绝。
- ✅ 优点：
  - 保护高价值路径：支付、已登录用户、企业客户、低延迟 SLA 请求可以优先获得资源。
  - 资源利用更合理：低优先级任务可延迟、采样、降级，不影响核心体验。
  - 可表达业务策略：LLM 里 premium/user priority，crawler 里 politeness priority，监控里 critical alert priority。
- ❌ 缺点：
  - 策略复杂：priority 规则、quota、aging、防饥饿都要设计。
  - 公平性问题：低优先级用户可能长期被饿死，需要最小保障。
  - Debug 更难：用户为什么被拒绝或排得慢，需要暴露 reason 和指标。
  - 可能被滥用：如果 priority 可被用户控制，会被攻击者伪造高优先级。

**推荐话术**

- `Queue is not free. I would set max queue length, timeout, priority, and reject policy.`
- `过载时宁愿明确拒绝，也不要让请求在队列里排到超时。`

**容易踩坑**

- LLM inference 请求无限排队，最后用户断连还继续占 GPU。
- 秒杀请求全部进 DB，核心库存服务被打爆。

## 12. Hot Key / Hot Partition / Hot Entity

**适用题目**

- Trending Hashtags
- Live Comment
- Slack 大 channel
- Ticketmaster 热门 event
- Leaderboard
- Netflix 热门内容
- Price Drop 热门商品
- Rate Limiter 热门 API key

**要解决的问题**

- 平均 QPS 设计经常误导。
- 真实瓶颈是某个 hot entity：一个 event、channel、product、video、hashtag、API key。

**方案 A：普通 hash sharding**

- 适用场景：访问均匀。
- ✅ 优点：
  - 实现简单：一致性哈希或普通 hash 分片即可，适合 key 访问接近均匀的场景。
  - 扩容路径清楚：增加 shard 后迁移部分 key，整体吞吐随 shard 增长。
  - 运维成熟：监控、rebalance、replica 都有通用模式。
- ❌ 缺点：
  - 无法解决 hot key：一个热门 event/channel/product 仍会集中到单个 shard。
  - 平均 QPS 会误导容量规划：整体看很均匀，但热点 shard p99 已经爆炸。
  - 热点迁移困难：临时把一个 key 迁到新 shard 只能转移瓶颈，不能分摊单 key 写入。

**方案 B：Hot key splitting**

- 适用场景：计数、评论、实时流、热门商品 watch list。
- 做法：
  - 把一个 hot key 拆成多个 sub-key。
  - 写入分散，读取聚合。
- ✅ 优点：
  - 分摊写流量：把单个 hot key 拆成多个 sub-key，让多个 shard/worker 并行处理。
  - 可动态扩展：热点升高时增加 split 数，热点下降后再合并或过期。
  - 适合可聚合数据：计数、评论流、metrics、watcher list 都可以读时 merge。
- ❌ 缺点：
  - 读时聚合复杂：需要从多个 sub-key 拉取再 merge，p99 取决于最慢 shard。
  - 顺序难保证：评论、消息、竞价这类有顺序语义的数据拆分后需要额外排序。
  - 去重和分页更难：同一 item 可能跨 split 出现，cursor 要表达多个 shard 的进度。
  - 不适合强唯一状态：库存扣减、最高价 winner 不能随便拆成多个独立计数。

**方案 C：Single writer + read scaling**

- 适用场景：必须保持强顺序或唯一状态，如库存、拍卖最高价。
- ✅ 优点：
  - 写入顺序清晰：同一个 hot entity 的所有变更由一个 owner 串行处理，避免并发冲突。
  - 适合强 invariant：库存、最高价、唯一分配、状态机推进都更容易保证正确性。
  - 读扩展仍可做：当前状态可以异步复制到 cache/read replica，承接大量读。
- ❌ 缺点：
  - 写瓶颈仍存在：单 writer 的 CPU/IO/queue 能力决定上限。
  - failover 更关键：新 writer 接管前必须确认旧 writer 不再写，避免 split-brain。
  - backlog 影响用户体验：高峰期命令排队，用户看到 pending 或排队状态。
  - 分片迁移复杂：hot entity owner 迁移要保持顺序和状态一致。

**推荐话术**

- `我不会只按平均 QPS sizing，会单独分析 hot entity。`
- `如果是计数/评论可 split；如果是库存/最高价，写入要 single-writer 或 conditional update。`

**容易踩坑**

- 以为 consistent hashing 能解决 hot key。
- 热点拆分后忘记读聚合和去重。

## 13. Geo Index：PostGIS vs Geohash vs H3/S2

**适用题目**

- Uber
- Proximity Search
- Food Delivery
- Restaurant Recommendation
- Nearby Store
- Location-based Ads

**要解决的问题**

- 如何快速查附近实体。
- 如何处理边界。
- 如何分片和支持高频位置更新。

**方案 A：PostGIS**

- 适用场景：复杂 polygon、配送范围、后台运营查询。
- ✅ 优点：
  - 地理表达力强：可以处理 polygon、contains、intersects、distance、配送范围等复杂空间查询。
  - 适合 source/校验：餐馆配送边界、行政区域、商圈边界这类准确性要求高的数据很适合放 PostGIS。
  - 运维和查询语义成熟：SQL + spatial index 对后台、运营、debug 都友好。
- ❌ 缺点：
  - 高 QPS online 查询压力大：每次 nearby search 都打 PostGIS，热门城市/区域会成为瓶颈。
  - 分片复杂：空间数据按城市、cell、商家分片都会遇到边界查询和跨 shard 查询。
  - 动态实体更新成本高：司机/rider 这种秒级位置更新不适合直接频繁写复杂空间索引。
  - p99 不稳定：复杂 polygon 查询在热点区域可能拖慢在线路径。

**方案 B：Geohash**

- 适用场景：简单 nearby search，工程复杂度低。
- ✅ 优点：
  - 实现简单：经纬度编码成字符串，prefix 长度对应大致精度。
  - 容易分片：可以按 geohash prefix 把数据分到不同 shard。
  - 适合粗召回：附近商家、POI、低频位置对象可以快速先召回候选。
- ❌ 缺点：
  - 边界问题明显：两个很近的位置可能在不同 cell，只查当前 prefix 会漏结果。
  - cell 大小固定：人口密集区域一个 cell 里对象太多，偏远地区又太少。
  - 邻居查询必须补：需要 current cell + neighbor cells，再按真实距离过滤。
  - 动态精度调参麻烦：precision 太粗候选多，太细又要查更多邻居。

**方案 C：H3/S2**

- 适用场景：大规模 online geo search。
- ✅ 优点：
  - 层级结构清晰：不同 resolution 可以表达不同搜索半径，方便扩大范围。
  - neighbor 查询更系统化：天然支持 k-ring/邻居 cell，比手写 geohash 邻居更稳定。
  - 适合 geo-sharding：按 cell 分区，热门城市可细分，冷门区域可粗分。
  - 在线召回性能好：先 cell 召回，再真实距离/配送范围校验。
- ❌ 缺点：
  - 实现复杂度高于 geohash：团队要理解 resolution、cell covering、边界覆盖。
  - 仍不能省掉校验：cell 命中只是候选，最终仍要算真实距离、ETA 或 polygon。
  - 热点 cell 仍会出现：市中心/演唱会场馆附近需要进一步拆分或限流。
  - 数据迁移成本：从 geohash/Redis GEO 演进到 H3/S2，需要重建索引和调整查询服务。

**推荐话术**

- `在线 proximity 用 H3/S2 cell index，复杂配送 polygon 用 PostGIS 做 source/校验。`
- `查询不能只查 current cell，要查 neighbors；结果不够再扩大半径或降低 precision。`

**容易踩坑**

- 只查当前 geohash cell，边界附近漏结果。
- Redis GEO 高频更新未考虑 shard 和旧位置清理。

## 14. Realtime Delivery：Polling vs SSE vs WebSocket

**适用题目**

- Chat App
- Live Comment
- Food Delivery Tracking
- Ticketmaster Queue
- LeetCode Result
- Job Scheduler Result
- Robinhood Order Update

**要解决的问题**

- 用户想实时看到状态，但实时连接会带来连接管理、扩容和恢复问题。

**方案 A：Polling**

- 适用场景：低频状态、实现简单优先。
- ✅ 优点：
  - 服务端简单：每个请求都是普通 HTTP，不需要维护连接状态和订阅关系。
  - 兼容性好：浏览器、移动端、代理都支持，故障恢复就是下次请求。
  - 适合低频状态：job result、订单详情、非实时通知都可以用 polling。
- ❌ 缺点：
  - 实时性差：poll interval 决定最小延迟，想更实时就会显著增加 QPS。
  - 资源浪费：大部分 poll 可能没有新数据，但仍消耗网关、DB/cache、带宽。
  - 容易造成同步峰值：大量客户端固定间隔 polling，会形成周期性流量尖峰。
  - 移动端体验一般：频繁唤醒耗电，弱网下更明显。

**方案 B：SSE**

- 适用场景：服务端单向推送，如订单状态、runner result、排队进度。
- ✅ 优点：
  - 比 WebSocket 简单：服务端单向推送，协议和状态机更轻。
  - 适合状态流：订单状态、rider 位置、queue position、runner result 都是 server-to-client。
  - HTTP 友好：更容易穿过部分代理和负载均衡，自动重连模型也比较清晰。
  - 客户端实现简单：不需要设计双向消息协议。
- ❌ 缺点：
  - 单向限制：如果客户端也要频繁发消息，仍需要额外 HTTP 或改 WebSocket。
  - 连接资源仍存在：大量用户订阅时，gateway 要管理大量长连接。
  - 兼容性要验证：移动端后台、企业代理、部分网关对长连接支持不稳定。
  - 恢复仍要 cursor：断线期间的事件不能只靠推送，必须能补齐。

**方案 C：WebSocket**

- 适用场景：双向实时，如 chat、collaboration、live comment。
- ✅ 优点：
  - 低延迟双向通信：chat、live comment、collaboration、rider tracking 都可以在同一连接上收发。
  - 减少重复握手：频繁小消息不需要每次 HTTP request。
  - 适合 presence/typing/ack：这些天然是长连接语义。
- ❌ 缺点：
  - 长连接扩容复杂：连接数、内存、心跳、负载均衡、滚动发布都要处理。
  - Gateway failure 后必须恢复：连接断开不是数据丢失的理由，需要 cursor/inbox/DB 补齐。
  - 需要 connection registry：user_id/order_id/channel_id 到 gateway 的映射会频繁变化。
  - Backpressure 更难：客户端慢消费时要决定丢消息、断开还是降级。

**推荐话术**

- `协议选择取决于通信模型：低频 polling，单向 SSE，双向 WebSocket。`
- `更关键的是，push channel 不是 source of truth。断线后用 cursor/inbox/DB 补齐。`

**容易踩坑**

- WebSocket server 保存唯一状态，server 掉了状态丢失。
- 没有 cursor，断线期间消息无法补齐。

## 15. TTL / Expiration / Delayed Execution

**适用题目**

- WhatsApp Status
- Ticket Hold
- Hotel Reservation Payment Hold
- Job Scheduler
- Price Drop Tracker
- Cache
- S3 Lifecycle
- In-memory KV Retention

**要解决的问题**

- 某些对象需要过期：status 24h、ticket hold 10min、job 到点执行、cache TTL。
- TTL 删除通常不精确，不能单独作为 correctness 机制。

**方案 A：DB TTL**

- 适用场景：自动清理短生命周期数据。
- ✅ 优点：
  - 实现简单：只要写入 `expires_at/ttl` 字段，存储系统自动清理。
  - 成本控制好：短生命周期数据不会无限增长，适合 status、session、cache、临时 hold。
  - 运维负担低：不用为每类数据都写一套清理 worker。
- ❌ 缺点：
  - 删除时间不精确：很多 DB TTL 是 best-effort，可能延迟几分钟到几小时。
  - 不能作为正确性：ticket hold 到期、status 过期、权限撤销不能只靠 TTL 删除。
  - 派生数据残留：search index、cache、CDN、inbox 可能仍有旧 entry。
  - 缺少触发语义：TTL 删除未必可靠触发业务补偿或通知。

**方案 B：Delayed queue**

- 适用场景：到期需要触发动作，例如释放 hold、执行 job。
- ✅ 优点：
  - 触发更及时：到期后可以释放库存、取消订单、执行 job、发送提醒。
  - 业务语义清晰：每个 delayed message 对应一个未来动作，适合 workflow。
  - 比扫描更省无效读取：不用频繁扫没有到期的数据。
- ❌ 缺点：
  - 大规模成本高：海量 ticket hold、status、job 都放 delayed queue，会考验 queue 存储和调度能力。
  - 时间不一定精确：queue backlog、worker lag 仍会让执行延迟。
  - 需要幂等：到期消息可能重复投递，释放库存/取消订单不能重复副作用。
  - 取消和更新复杂：如果用户提前支付成功，需要取消或忽略原 delayed message。

**方案 C：Time bucket scanner**

- 适用场景：大量过期对象，可接受分钟级延迟。
- ✅ 优点：
  - 稳定可控：按分钟/小时 bucket 扫描，任务量可预估，也容易限流。
  - 可恢复：scanner 挂了可以从上一个 bucket 继续扫，也可以 backfill。
  - 适合海量对象：比为每个对象创建 delayed message 更便宜。
  - 分片友好：按 time bucket + shard id 并行扫描。
- ❌ 缺点：
  - 不精确：扫描粒度决定延迟，分钟级 bucket 就不可能秒级触发。
  - 会读到大量无效数据：如果 bucket 设计不好，扫描成本高。
  - 热点 bucket 风险：整点大量任务到期会造成 worker 峰值。
  - 仍要读路径兜底：scanner 延迟时，过期对象不能继续可见。

**推荐话术**

- `读路径必须判断 expires_at，保证过期后不可见。`
- `TTL/scanner/delay queue 只是清理或触发，不是 correctness guarantee。`

**容易踩坑**

- 依赖 DynamoDB TTL 精确释放票。
- Status 过期只靠清理任务，读路径不检查 expires_at。

## 16. Search Index Freshness vs Correctness

**适用题目**

- TypeAhead
- RAG Ingestion
- Restaurant Search
- Food Ordering Search
- Ticketmaster Search
- WhatsApp Status Search
- RSS Aggregator
- Slack Search

**要解决的问题**

- 搜索索引是异步派生的。
- 它可能不新鲜，但不能违反权限、库存、过期、删除语义。

**方案 A：同步写 search index**

- 适用场景：低 QPS、强 freshness。
- ✅ 优点：
  - Freshness 最好：用户创建/修改内容后立刻可搜，产品体验直接。
  - 语义简单：主写成功时 index 也成功，少了 CDC lag 和异步排查。
  - 适合低 QPS 管理后台或小规模搜索。
- ❌ 缺点：
  - 主写链路变重：index 写入慢或失败会拖慢甚至阻塞业务写。
  - 可用性耦合：搜索集群故障会影响发帖、下单、上传这类本不该依赖搜索的动作。
  - 高吞吐不友好：频繁更新会让 index refresh/merge 压力大，p99 变差。
  - 回滚复杂：DB 写成功、index 写失败时仍要补偿，否则两边不一致。

**方案 B：CDC / event 异步建索引**

- 适用场景：生产级搜索系统。
- ✅ 优点：
  - 主写链路稳定：DB 写成功即可返回，搜索更新不阻塞核心业务。
  - 可 replay/rebuild：索引坏了可以从 CDC/event log 重建，而不是依赖线上双写补洞。
  - 支持批量优化：indexer 可以 batch、限流、按优先级更新，降低搜索集群压力。
  - 便于演进：新增字段、新 analyzer、新 index version 可以异步 backfill。
- ❌ 缺点：
  - 索引延迟：新内容可能短暂搜不到，旧内容可能短暂还在。
  - 删除/权限风险高：如果删除、过期、权限撤销传播慢，可能出现隐私或合规问题。
  - CDC lag 需要监控：lag 高时搜索 freshness 降级，需要产品和告警策略。
  - 乱序事件要处理：update/delete/create 乱序可能导致 index 状态倒退。

**方案 C：异步索引 + read-time permission/state validation**

- 适用场景：涉及隐私、库存、可见性。
- ✅ 优点：
  - 性能和正确性平衡：index 负责快速召回，source service 负责最终 permission/state/availability 校验。
  - 容忍 stale index：索引延迟最多影响 recall 或多返回候选，不会直接造成越权或下单错误。
  - 适合复杂权限：Slack、Status、Google Doc、Ticketmaster、food search 都能复用。
  - 方便降级：校验失败的结果可以过滤，候选不够再补召回。
- ❌ 缺点：
  - 查询路径变复杂：需要批量校验 top N，避免每个结果一个 RPC。
  - 校验服务变成关键依赖：不可用时要决定 fail closed、返回少量结果，还是降级。
  - 可能影响 relevance：过滤后结果数量变少，排序分数也需要重新调整。
  - 成本更高：搜索集群和 source service 都要参与请求。

**推荐话术**

- `搜索可以短暂漏结果，但不能返回不该看的结果。`
- `返回前校验 permission、deleted、expires_at、availability。`

**容易踩坑**

- 删除内容后只等索引异步删除，期间仍可搜到。
- 私有数据进全局索引但查询时不做权限过滤。

## 17. Multi-region Consistency

**适用题目**

- Chat App
- Slack
- WhatsApp Status
- Google Doc
- Uber
- Netflix
- S3
- Rate Limiter
- Food Delivery

**要解决的问题**

- 全球用户需要低延迟。
- 但全局强一致代价高。
- 要定义哪些数据必须同步，哪些可以异步复制。

**方案 A：全局同步写**

- 适用场景：少数强一致全球账本。
- ✅ 优点：
  - 一致性强：所有 region 读到的关键状态一致，冲突少。
  - 适合少数强 invariant：例如全局唯一余额、严格账本、不可重复票据。
- ❌ 缺点：
  - 跨洲延迟高：每次写都需要跨 region 协调，用户 p99 明显变差。
  - 可用性差：任一参与 region 或网络链路故障都可能阻塞写入。
  - 成本高：共识/同步复制对带宽、存储和运维要求都高。
  - 过度使用会拖垮系统：很多 read model、feed、presence 根本不需要全球强一致。

**方案 B：Home region ownership**

- 适用场景：用户、订单、文档、ride、status 有自然 owner。
- ✅ 优点：
  - 写入低延迟：用户/订单/文档在自己的 home region 写入，不用每次跨洲协调。
  - ownership 清晰：每个 entity 有唯一写入权威，冲突显著减少。
  - 故障边界清楚：某 region 故障主要影响该 region 的 owner entity。
  - 适合异步复制：跨 region 读模型、通知、搜索可以最终一致。
- ❌ 缺点：
  - 跨 region 读取有延迟：远端用户可能看到旧状态，尤其 chat/status/doc 协作。
  - 用户迁移复杂：home region 变更需要迁移数据、cache、routing metadata。
  - 跨区交互要设计：两个用户不同 home region 时，消息、订单、权限由谁做 owner 要清楚。
  - 合规要求可能限制复制：PII、支付、医疗等数据不能随意跨区。

**方案 C：Active-active multi-master**

- 适用场景：协作编辑、全球低延迟写入、极高可用。
- ✅ 优点：
  - 写入延迟最低：用户可以写最近 region，协作体验好。
  - 可用性强：单 region 故障时，其他 region 仍可接受写。
  - 适合天然可合并数据：CRDT counter、presence、部分协作文档状态。
- ❌ 缺点：
  - 冲突解决复杂：同一实体多地并发写，需要 CRDT/OT/last-write-wins/业务 merge。
  - 删除和权限撤销很难：一地撤销权限，另一地可能已接受写或读。
  - 不适合金融/库存：余额、票、订单状态这类强 invariant 很难 active-active。
  - Debug 困难：用户看到的状态取决于复制顺序和冲突策略。

**推荐话术**

- `默认 home region + async replication。`
- `只有保护特定 invariant 时才做跨 region 同步。`
- `对于协作编辑可讨论 CRDT/OT；对于订单支付不要轻易 active-active。`

**容易踩坑**

- 所有服务都 global active-active，但不讲冲突。
- 跨 region 同步复制放进用户主链路导致延迟不可接受。

## 18. Reconciliation / Offline Correction

**适用题目**

- Ads Click Aggregation
- Payment / Robinhood
- Food Ordering
- Ticketmaster
- Monitoring Metrics
- TopK / Trending
- Inventory
- Billing

**要解决的问题**

- 实时链路为了低延迟，可能有重复、延迟、丢失、乱序。
- 财务、计费、库存、报表最终必须准确。

**方案 A：完全依赖实时链路**

- 适用场景：临时展示、低风险 metrics。
- ✅ 优点：
  - Freshness 好：用户能快速看到点击数、库存变化、TopK、监控指标。
  - 架构简单：少一条离线回算链路，实时结果直接 serving。
  - 适合低风险展示：临时 dashboard、近实时趋势、非计费指标。
- ❌ 缺点：
  - late event 和 duplicate event 会污染结果：迟到点击、重复消费、乱序更新都会影响聚合。
  - 无法修正历史：如果实时 job 有 bug，没有 raw log/batch 重算就无法纠正。
  - 不适合财务和计费：用户看到快不代表最终正确。
  - Debug 难：只有最终聚合值时，不知道错误来自哪些原始事件。

**方案 B：离线 batch 重新计算**

- 适用场景：财务结算、日报、最终账单。
- ✅ 优点：
  - 准确性高：可以基于完整 raw event、去重、迟到数据、业务规则重新计算。
  - 可修正历史：发现 bug 后能 backfill，重新产出正确报表或账单。
  - 逻辑更复杂：离线 job 可以做大 join、反作弊、异常修复，不受在线延迟限制。
  - 适合审计：财务、计费、广告结算都需要离线可复算链路。
- ❌ 缺点：
  - 延迟高：小时级/天级结果不能服务实时产品体验。
  - 资源成本高：大规模 batch scan 会消耗大量计算和存储 IO。
  - 双结果解释复杂：实时数字和离线最终数字可能不同，需要版本和口径说明。

**方案 C：实时 serving + 离线 reconciliation**

- 适用场景：成熟生产系统。
- ✅ 优点：
  - 用户体验和最终正确兼得：前台使用实时结果，后台离线修正最终事实。
  - 容错强：实时 pipeline 出错后，可以用离线结果覆盖或补偿。
  - 支持审计：raw log 保留后，任何时间都能解释结果来源。
  - 适合成熟系统：ads、payment、inventory、metrics 通常都需要这两条链路。
- ❌ 缺点：
  - 系统复杂：实时和离线两套 pipeline、两套餐表、两种延迟。
  - 结果冲突要定义：离线结果覆盖实时结果，还是生成 adjustment event，需要明确。
  - 用户沟通成本：dashboard 或账单可能出现修正，要有版本和 audit trail。
  - 数据模型要支持重算：没有 deterministic key/window/version，合并会很痛。

**推荐话术**

- `实时链路服务 freshness，离线 reconciliation 保证 correctness。`
- `对财务/计费/库存，我会保留 raw event log 支持 replay 和 backfill。`

**容易踩坑**

- 聚合后不保存 raw event，后续无法重算。
- 实时和离线结果冲突时没有版本/优先级规则。

## 19. Sliding Window / Time Window / TopK

**适用题目**

- TopK
- Trending Hashtags
- Rate Limiter
- Monitoring
- Ads Aggregation
- TypeAhead Trending
- Metrics Alert

**要解决的问题**

- 用户可能问过去 1 分钟、1 小时、7 天 TopK。
- 固定窗口和任意窗口是完全不同的复杂度。

**方案 A：精确 event list**

- 适用场景：窗口小、QPS 不高、精度要求高。
- ✅ 优点：
  - 结果精确：窗口内每个事件都可追溯，适合计费、审计、严格排行榜。
  - 修正能力强：late event、delete event、duplicate event 可以按单条事件精确处理。
  - Debug 友好：能解释某个 key 的 count 为什么是这个值。
- ❌ 缺点：
  - 内存/状态成本高：窗口越长、key cardinality 越高，event list 越不可控。
  - 淘汰旧事件贵：每次窗口滑动都要删除过期 event，并更新对应 key 的 count。
  - TopK 更新复杂：旧事件过期会导致 count 下降，heap 里可能有 stale entry。
  - 不适合任意大窗口：过去 7 天/30 天高 QPS event list 通常无法全放内存。

**方案 B：Bucket aggregation**

- 适用场景：固定窗口，如过去 1h，每分钟一个 bucket。
- ✅ 优点：
  - 内存可控：把事件聚合到分钟/小时 bucket，不需要保存每条 raw event 在在线状态里。
  - 查询简单：固定窗口只需要合并固定数量 bucket，例如 60 个分钟 bucket。
  - 容易并行：不同 bucket/key 可以分片聚合，适合 Flink/Redis/OLAP。
  - 成本和精度可调：bucket 越小越准但成本越高，越大成本越低但边界更粗。
- ❌ 缺点：
  - 边界精度损失：查询时间不对齐 bucket 时，需要接受误差或额外处理 partial bucket。
  - 任意窗口支持差：用户要任意 start/end 时，预聚合 bucket 不一定足够灵活。
  - 合并成本仍可能高：窗口长、维度多、K 大时，合并很多 bucket 会拖慢查询。
  - late event 修正复杂：迟到事件要回写旧 bucket，并考虑已输出结果是否修正。

**方案 C：OLAP store / Druid / ClickHouse**

- 适用场景：任意 lookup window、复杂 group by。
- ✅ 优点：
  - 查询灵活：任意时间窗口、group by、多维 filter 都可以支持。
  - 适合历史分析：不用把所有窗口都预先算好，适合 dashboard 和 ad-hoc query。
  - 存储/压缩成熟：Druid/ClickHouse 对 time-series rollup、列式扫描、分区剪枝优化好。
- ❌ 缺点：
  - 延迟高于内存 cache：OLAP 查询通常不能承接每个用户请求的超高 QPS 低延迟路径。
  - 成本更高：复杂 group by、长时间窗口会消耗大量 CPU/IO。
  - 数据 freshness 取决于 ingestion lag：实时性不如纯 streaming state。
  - 需要查询保护：不加 limit/timeout/resource group 容易被重查询拖垮。

**方案 D：Sketch / approximate**

- 适用场景：超大规模，允许近似。
- ✅ 优点：
  - 省内存：Count-Min Sketch、HyperLogLog、sampling 可以用固定空间处理超大 cardinality。
  - 吞吐高：更新通常是 O(1)，适合 trending、monitoring、推荐召回等大流量场景。
  - 成本低：不需要为每个 key 保存完整事件或精确集合。
- ❌ 缺点：
  - 有误差：可能 over-count 或估计偏差，不适合计费、库存、奖金排行榜。
  - 过期删除难：sliding window 下要维护多个 sketch 或 decay，否则旧数据影响长期存在。
  - 解释性差：面试官追问“为什么这个 key 是 Top1”时，sketch 很难给精确证据。
  - 参数敏感：误差率、hash 数、window 数都需要按数据规模调。

**推荐话术**

- `先问是否任意时间窗口。如果任意窗口，用 OLAP；如果固定窗口，可以用 streaming + bucket/topK cache。`
- `精确统计和近似统计要按业务正确性选择。`

**容易踩坑**

- 用 Redis ZSET 直接支持任意历史窗口。
- 不讨论 late event 和 duplicate event。

## 20. ML Feature Consistency / Training-Serving Skew

**适用题目**

- Restaurant Recommendation
- Short Video Recommendation
- High-risk Account ML Pipeline
- RAG Based Chatbot
- TypeAhead Ranking
- Ads Ranking

**要解决的问题**

- 离线训练看到的特征和在线 serving 用的特征不一致。
- 训练样本可能用了未来信息，导致离线效果虚高。

**方案 A：离线和在线各写一套 feature logic**

- 适用场景：早期实验。
- ✅ 优点：
  - 迭代快：data scientist 和 backend 可以各自推进，不需要先建设完整平台。
  - 适合 early stage：模型还在探索时，避免过度平台化。
- ❌ 缺点：
  - training-serving skew 高发：同名 feature 在线和离线计算口径不同，离线指标好但线上掉。
  - data leakage 风险高：训练可能用了预测时不可见的未来数据。
  - Debug 困难：线上错了很难判断是模型问题、特征问题还是数据延迟问题。
  - 复现困难：没有记录线上 feature vector 时，无法 replay 某次 prediction。

**方案 B：Feature Store 统一定义**

- 适用场景：生产 ML 系统。
- ✅ 优点：
  - 一致性更好：离线训练和在线 serving 共享 feature definition，减少 skew。
  - 特征复用：多个模型可以共享用户画像、实时行为、item 特征，减少重复开发。
  - 治理能力强：能管理 feature owner、schema、版本、freshness、质量监控。
  - 上线更安全：feature schema 变更可以和 model registry/checker 集成。
- ❌ 缺点：
  - 平台成本高：需要 online store、offline store、feature registry、计算 pipeline。
  - 治理复杂：特征生命周期、权限、PII、质量、backfill 都要有人负责。
  - 延迟和 freshness trade-off：实时特征成本高，离线特征可能不新鲜。
  - 不解决所有问题：仍需要 point-in-time join 防止训练用到未来信息。

**方案 C：Point-in-time join + online decision logging**

- 适用场景：风控、推荐、广告。
- 做法：
  - 训练时只使用 label 时间之前可见的特征。
  - 在线每次决策记录 feature vector、model version、policy version。
- ✅ 优点：
  - 避免 data leakage：训练样本只使用 label_time 之前可见的特征，离线评估更可信。
  - 可 replay/debug：线上每次 decision 记录 feature vector、model_version、policy_version，事故后能复现。
  - 支持模型对比：新旧模型可以在同一批历史请求上 replay，比较输出差异。
  - 适合高风险场景：风控、广告、推荐都需要解释为什么做了某个决策。
- ❌ 缺点：
  - 数据工程复杂：point-in-time join、late arriving feature、backfill 都很难。
  - 存储成本高：保存线上 feature vector 和 decision log 会产生大量数据。
  - 隐私治理更严格：feature vector 可能包含 PII 或敏感行为，需要脱敏和访问控制。
  - 训练链路更慢：构造高质量样本比直接 join 最新表成本更高。

**推荐话术**

- `我会重点防 training-serving skew 和 data leakage。`
- `每次线上 prediction 都记录 feature vector、model version、decision reason，用于 debug 和 retraining。`

**容易踩坑**

- 用用户下单之后才产生的特征训练下单预测。
- 模型上线后不知道用了哪个 feature schema。

## 21. Exploration vs Exploitation

**适用题目**

- Restaurant Recommendation
- Short Video Recommendation
- TypeAhead
- News Feed
- Ads
- RAG Retrieval Ranking

**要解决的问题**

- 如果只推模型最高分，系统会强化头部，冷启动差，长期效果变差。

**方案 A：Pure exploitation**

- 适用场景：短期转化最大化。
- ✅ 优点：
  - 短期指标强：模型总是选择预测分最高的 item，CTR/CVR/watch time 往往短期最好。
  - 体验稳定：不会因为探索把低质量候选突然推给用户。
  - 实现简单：不需要 bandit、uncertainty、exploration budget 等额外系统。
- ❌ 缺点：
  - 头部效应严重：已有高分 item 获得更多曝光，新 item 没数据就永远低分。
  - 反馈闭环偏置：模型只学习自己展示过的内容，position bias 和 selection bias 越来越强。
  - 冷启动差：新餐馆、新视频、新 query suggestion 没有探索流量就无法验证质量。
  - 长期指标可能下降：用户会看到同质化内容，多样性和新鲜感变差。

**方案 B：Fixed exploration slots**

- 适用场景：早期探索机制。
- ✅ 优点：
  - 简单可控：固定 1-2 个 slot 做探索，容易限制风险。
  - Guardrail 容易：可以只允许满足质量、安全、距离、库存等硬条件的候选进入探索池。
  - 数据收集稳定：新内容能获得基础曝光，帮助模型学习。
- ❌ 缺点：
  - 探索效率低：随机或固定 slot 可能把流量给不值得探索的候选。
  - 用户体验有损：slot 放太靠前会影响主指标，放太靠后又收集不到足够反馈。
  - 分群不精准：不同用户对探索容忍度不同，固定比例无法个性化。
  - 容易被业务滥用：探索位可能变成运营/广告插入位，污染学习目标。

**方案 C：Contextual bandit / uncertainty exploration**

- 适用场景：成熟推荐系统。
- ✅ 优点：
  - 学习效率高：把探索流量给模型不确定但潜在收益高的候选。
  - 个性化更强：不同用户、上下文、时间可以使用不同探索策略。
  - 长期收益好：能更快发现新内容、新商家、新兴趣。
- ❌ 缺点：
  - 实现复杂：需要在线学习或近实时反馈、uncertainty 估计、实验平台。
  - 评估困难：bandit 改变曝光分布，离线评估和 A/B attribution 更难。
  - Guardrail 必须强：不能为了探索推低质、不安全、不可用内容。
  - Debug 难：某个 item 被推是因为高分还是因为探索，需要 reason logging。

**推荐话术**

- `推荐系统不能只优化当前最高分，还要有受控探索和 guardrail。`
- `新内容/新商家进入 exploration pool，小流量验证后再扩大。`

**容易踩坑**

- 只讲 ranking model，不讲冷启动和探索。
- 探索没有安全、质量、距离、库存等 hard filter。

## 22. Observability / Debuggability / Replay

**适用题目**

- 所有复杂系统
- 特别适用：Risk ML、Recommendation、Order、Payment、Job Scheduler、Monitoring、Tracing、LLM Inference

**要解决的问题**

- Staff+ 不是只设计 happy path。
- 生产系统出问题后，要能回答：
  - 为什么这个请求被这样处理？
  - 用了哪个模型/配置/规则版本？
  - 哪个 event 丢了？
  - 能不能重放修复？

**方案 A：只监控 error rate / latency**

- 适用场景：基础 API。
- ✅ 优点：
  - 建设成本低：error rate、latency、QPS 是所有服务都能快速接入的基础指标。
  - 适合发现系统级故障：服务挂了、依赖超时、容量不足都能看到。
- ❌ 缺点：
  - 无法解释业务错误：订单为什么取消、用户为什么被风控、搜索为什么返回这个结果都看不出来。
  - 无法定位策略/模型问题：error rate 正常但转化率、误杀率、推荐质量可能已经坏了。
  - 异步链路不可见：outbox lag、DLQ、replay failure 不一定体现在 API error rate。

**方案 B：Decision logging**

- 适用场景：推荐、风控、调度、订单。
- 做法：
  - 记录 request_id、input、decision、reason、model_version、policy_version。
- ✅ 优点：
  - 可解释：每个 decision 都能看到输入、规则命中、模型分、最终动作。
  - 可审计：风控、支付、推荐、调度都能回答“为什么当时这么做”。
  - 支持 offline analysis：可以回放某段流量，比较新旧模型/策略差异。
  - 对用户申诉有帮助：尤其风控、封号、支付拒绝等场景。
- ❌ 缺点：
  - 存储成本高：高 QPS 服务记录完整 input/feature/reason 会产生大量日志。
  - PII 风险高：feature 和 request context 可能包含敏感数据，需要脱敏、权限和 retention。
  - Schema 演进复杂：decision log 要长期可读，否则历史 debug 失效。
  - 可能影响延迟：同步写审计日志要谨慎，通常需要异步化。

**方案 C：Trace + DLQ + replay**

- 适用场景：异步复杂系统。
- ✅ 优点：
  - 可恢复性强：派生数据坏了可以从 event log replay，而不是手工 patch。
  - DLQ 让坏消息显性化：schema 错误、业务校验失败、下游故障不会默默丢失。
  - Trace 能串联同步和异步链路：从 request_id 找到 DB write、outbox event、consumer、sink。
  - 支持事故复盘：知道哪一批事件失败、是否重放成功、影响范围多大。
- ❌ 缺点：
  - 重放边界复杂：哪些 event 可以重放，哪些外部 side effect 不能重放，需要明确。
  - 幂等要求更高：replay 会再次触发 consumer，所有 sink 必须保护重复效果。
  - Schema version 要长期维护：旧事件用新代码读可能失败。
  - DLQ 不是终点：没有处理工具和 owner，DLQ 只是把问题堆起来。

**推荐话术**

- `我会让每个关键决策可解释、可审计、可重放。`
- `出事故时，能从 request_id 找到 trace、event、model version、feature vector 和最终状态。`

**容易踩坑**

- 有 DLQ 但没有 replay 工具。
- log 里有 PII 但没有脱敏和访问控制。

## 23. Read Path vs Write Path Optimization

**适用题目**

- Leaderboard
- News Feed
- Slack
- Search
- Monitoring
- TypeAhead
- Restaurant Recommendation
- S3 Metadata

**要解决的问题**

- 一张表通常无法同时满足高写入、低延迟读、复杂查询。
- Staff+ 要按访问模式设计 read model。

**方案 A：写优化**

- 适用场景：日志、事件流、clickstream、trace spans。
- ✅ 优点：
  - 写吞吐高：append-only log、event table、trace spans 都可以顺序写，适合高 QPS ingestion。
  - 可靠性好：先记录事实，再异步构建查询视图，减少写路径复杂度。
  - 可 replay：原始事件保留后，后续能重建不同 read model。
- ❌ 缺点：
  - 查询体验差：用户要 feed/search/topK 时，从 raw log 实时扫会很慢。
  - 需要异步 read model：索引、聚合、缓存都要额外 pipeline。
  - 最终一致：写成功后，读模型可能还没更新。

**方案 B：读优化**

- 适用场景：feed inbox、leaderboard topK、search index。
- ✅ 优点：
  - 用户读低延迟：feed inbox、search index、leaderboard ZSET 都能直接服务产品查询。
  - 查询模型贴合体验：按用户、按 prefix、按 score、按 geo cell 建模，比主表灵活。
  - 可独立扩展：读模型可以用 Redis/ES/OLAP/CDN 等专用系统。
- ❌ 缺点：
  - 写放大：一次 source write 可能更新多个 read model、cache、index。
  - 一致性复杂：read model stale、更新失败、乱序事件都要处理。
  - 存储成本高：同一事实会以多种形态重复存储。
  - Rebuild 必须设计：read model 坏了要能从 source/event log 重建。

**方案 C：CQRS / source table + read model**

- 适用场景：成熟系统。
- ✅ 优点：
  - 职责清晰：write model 保护 source of truth，read model 优化用户查询。
  - 扩展灵活：不同读场景使用不同存储，不让主 DB 承担所有查询。
  - 可演进：新增搜索/推荐/报表时，可以从 event log 构建新 read model。
- ❌ 缺点：
  - 多套数据带来一致性问题：source 和 read model 之间有延迟和失败窗口。
  - 运维复杂：要监控 CDC lag、consumer failure、rebuild progress。
  - Debug 复杂：用户看到的结果可能来自 read model，不一定等于 source 当前状态。

**推荐话术**

- `我会把 source-of-truth write model 和 user-facing read model 分开。`
- `读模型可以最终一致，但必须可重建。`

**容易踩坑**

- 用主 DB 支持全文搜索、feed、analytics、topK 所有查询。
- read model 无法从 source event 重建。

## 24. Strong Consistency 只保护关键 Invariant

**适用题目**

- Ticketmaster
- 秒杀
- Payment
- Food Ordering
- Hotel Reservation
- Auction
- S3 Metadata
- Rate Limiter
- Privacy/Search

**要解决的问题**

- 全系统强一致成本太高。
- 全系统最终一致又会破坏关键业务。
- Staff+ 的做法是划边界。

**方案 A：全系统强一致**

- 适用场景：小系统，或极少数金融核心账本。
- ✅ 优点：
  - 语义简单：所有用户、所有服务看到同一个最新状态，业务逻辑容易写。
  - 对关键金融/账本类系统安全：减少并发冲突和补偿逻辑。
- ❌ 缺点：
  - 延迟高：所有读写都需要强协调，跨服务/跨 region 更明显。
  - 可用性差：强依赖不可用时，系统倾向 fail closed。
  - 扩展困难：全局锁、串行事务、同步复制都会限制吞吐。
  - 成本浪费：很多 feed/search/cache/analytics 不需要这一级别一致性。

**方案 B：全系统最终一致**

- 适用场景：低风险 read model。
- ✅ 优点：
  - 高可用：各个副本/region 可以独立服务，不必每次同步协调。
  - 高吞吐：写入可以异步复制，读模型也可以独立扩展。
  - 适合派生数据：cache、search、feed、analytics 都能接受短暂 stale。
- ❌ 缺点：
  - 会破坏关键 invariant：库存可能超卖、支付可能重复、权限撤销可能延迟生效。
  - 用户体验不确定：不同设备/region 看到不同状态。
  - Debug 难：需要判断是复制延迟、乱序、丢事件还是业务 bug。

**方案 C：关键 invariant 强一致，其余最终一致**

- 适用场景：绝大多数系统设计。
- ✅ 优点：
  - 成本和正确性平衡：只在库存、支付、权限、唯一 winner 等关键点付出强一致成本。
  - 系统可扩展：非关键 read model 可以最终一致、异步、可重建。
  - 面试表达高级：先定义 invariant，再决定一致性等级，而不是泛泛谈 CAP。
- ❌ 缺点：
  - 需要清楚定义 invariant：团队必须知道哪些路径不能降级，哪些可以 stale。
  - 工程复杂：同一系统里会同时有强一致写、最终一致 index、cache validation。
  - 测试复杂：要覆盖一致性边界，例如 cache stale 但 checkout 仍不能错。

**推荐话术**

- `我先定义 invariant：不能 oversell、不能 double booking、不能重复扣款、不能越权读取。`
- `这些点强一致，其它 cache/search/feed/analytics 最终一致。`

**容易踩坑**

- 面试官问 consistency 时只回答 CAP。
- 不知道自己系统里到底哪些 invariant 要强保护。

## 25. Workflow Engine vs Queue + Worker

**适用题目**

- Job Scheduler
- Video Processing / Netflix / YouTube
- Food Ordering
- Ticketmaster
- Flight Booking
- Web Crawler
- Price Drop Tracker

**要解决的问题**

- 很多流程不是单个 task，而是多步骤、长时间、有超时和补偿。

**方案 A：Queue + worker**

- 适用场景：单步任务，失败重试简单。
- ✅ 优点：
  - 简单直接：一个 queue 加一组 stateless workers 就能水平扩展，适合发通知、刷新索引、单步转码。
  - 吞吐高：worker 数量可以按 backlog 扩缩，任务之间相互独立时扩展效果好。
  - 运维成熟：queue depth、consumer lag、retry count、DLQ 都是标准指标。
  - 成本低：不用引入完整 workflow 平台，开发和学习成本小。
- ❌ 缺点：
  - 多步骤状态分散：任务 A 成功、任务 B 失败、任务 C pending 时，需要自己维护状态机。
  - timeout/compensation 要手写：支付等待、商家确认、转码 QC 这种流程会很快变复杂。
  - 失败恢复容易散落：retry、DLQ、manual fix、cron 补偿如果没有统一模型，事故排查困难。
  - 幂等仍是硬要求：worker retry 后外部 side effect 可能重复。

**方案 B：Cron / scanner**

- 适用场景：周期性低精度任务。
- ✅ 优点：
  - 实现简单：定时扫 due tasks 或异常状态，不需要复杂调度平台。
  - 容易补漏：可以作为 reconciliation/sweeper，修复漏发事件、过期 hold、卡住订单。
  - 运维直观：按时间桶扫描，失败后从上次进度继续。
- ❌ 缺点：
  - 调度精度低：poll interval 决定最小延迟，不适合秒级准确任务。
  - 扫描大表成本高：如果没有 due task 小表或 time bucket，扫描会拖垮 DB。
  - 容易重复处理：scanner 重启或多实例并发时，需要 lease/conditional update。
  - Failure recovery 仍要设计：扫到一半挂了、处理一半失败，都要有 checkpoint 和幂等。

**方案 C：Durable workflow engine**

- 适用场景：转码 pipeline、订单支付配送、复杂 job。
- ✅ 优点：
  - 状态持久化：每个 workflow instance 执行到哪一步、失败原因、下一步动作都可查询。
  - retry/timeout/compensation 清晰：不用把重试和补偿逻辑散落在 cron、queue、worker 里。
  - 适合长流程：订单、支付、派单、转码、人工审核都可能持续几分钟到几小时。
  - 运维友好：可以 pause、resume、retry、cancel 单个实例，事故处理更可控。
- ❌ 缺点：
  - 平台复杂度高：团队要理解 workflow replay、activity idempotency、版本升级。
  - 对简单任务过重：普通异步通知或单步 index update 用 workflow 可能增加不必要成本。
  - Activity 仍要幂等：workflow retry 会重复调用外部 API，不能以为 workflow 自动解决副作用。
  - 迁移和锁定风险：Temporal/Step Functions 等平台语义不同，长期维护要考虑。

**推荐话术**

- `单步异步任务用 queue worker。`
- `多步骤、长时间、有补偿的流程用 durable workflow。`

**容易踩坑**

- 用一堆 cron + queue 手写复杂 workflow，没有统一状态。
- Workflow step 不幂等，retry 后产生重复副作用。

## 26. Security / Privacy as Correctness

**适用题目**

- WhatsApp Status Search
- Slack / Chat
- News Feed Privacy
- Google Doc
- RAG Chatbot
- Monitoring PII
- Netflix DRM
- S3 Signed URL

**要解决的问题**

- 权限、隐私、PII、DRM 不是“附加功能”，而是 correctness invariant。
- Search/cache/CDN/read model 可能绕过权限。

**方案 A：只在前端隐藏**

- 适用场景：不可作为安全方案。
- ✅ 优点：
  - 实现快：前端隐藏按钮、菜单、入口即可，适合临时 UX 优化。
  - 可以减少误操作：用户界面上不展示无权限内容，降低普通用户误点。
- ❌ 缺点：
  - 完全不安全：用户可以直接调 API、改请求、使用旧客户端绕过前端。
  - 无法审计权限：服务端不知道某次访问是否应该被拒绝。
  - 派生数据仍可能泄露：搜索 index、CDN URL、cache API 可能直接暴露内容。

**方案 B：服务端统一鉴权**

- 适用场景：绝大多数系统。
- ✅ 优点：
  - 权限边界清晰：所有 API 在服务端检查 user、resource、action、context。
  - 可审计：每次拒绝/允许都能记录原因，适合企业、隐私、合规场景。
  - 易于统一策略：block list、workspace policy、subscription、region license 都可以统一处理。
- ❌ 缺点：
  - 增加延迟：每次读写都要鉴权，热点读路径可能需要缓存权限结果。
  - 派生系统复杂：search index、cache、CDN 都要考虑权限过滤或短 TTL token。
  - 权限变更传播问题：用户被移出 workspace 后，旧 cache/index/CDN URL 必须尽快失效。
  - 高可用取舍：鉴权服务挂了时，敏感资源通常要 fail closed。

**方案 C：加密 / client-side search / signed URL**

- 适用场景：E2EE、媒体、私有文件、DRM。
- ✅ 优点：
  - 隐私强：服务端无法读取明文内容，降低内部泄露和合规风险。
  - 适合媒体和私有文件：signed URL/DRM 可以把访问权限绑定到短期 token。
  - E2EE 场景正确：WhatsApp/私密文档类系统不能让服务端索引明文。
- ❌ 缺点：
  - 搜索能力受限：服务端不能做明文全文索引，只能做 metadata search 或 client-side search。
  - Debug 和恢复困难：服务端看不到明文，排查内容相关问题更难。
  - 多设备同步复杂：client-side encrypted index 要在设备间同步或重建。
  - Token 管理复杂：signed URL 太长会影响撤销，太短会影响播放/下载体验。

**推荐话术**

- `Search index/cache/CDN 返回前必须做 permission 和 expiration check。`
- `如果 E2EE，服务端不能做明文全文搜索，只能 metadata search 或 client-side index。`

**容易踩坑**

- 私有内容进入全局索引。
- CDN signed URL 太长，权限撤销后仍能访问。

## 27. Model / Policy / Config Versioning

**适用题目**

- High-risk Account ML
- Restaurant Recommendation
- Short Video Recommendation
- LLM Inference Routing
- Rate Limiter
- Search Ranking
- Dispatch / ETA
- A/B Test

**要解决的问题**

- 模型、规则、配置变更会影响线上行为。
- 如果不可版本化，事故后无法定位和回滚。

**方案 A：直接覆盖配置或模型**

- 适用场景：早期手动系统。
- ✅ 优点：
  - 简单：直接替换配置/模型，不需要发布平台和实验框架。
  - 适合早期低风险系统：人工调整少量参数时成本最低。
- ❌ 缺点：
  - 无法可靠回滚：新配置出问题后，旧配置可能已经丢失或无法恢复。
  - 历史不可解释：无法回答某个请求用了哪个模型、规则、阈值。
  - A/B test 困难：没有版本和流量分组，就无法比较效果。
  - 事故范围不可控：一次覆盖可能影响所有用户、所有 region。

**方案 B：Versioned config/model + canary**

- 适用场景：生产策略系统。
- ✅ 优点：
  - 可灰度：按 user、region、tenant、action_type 小流量发布，降低爆炸半径。
  - 可回滚：指标异常时快速切回上一个稳定版本。
  - 可解释：decision log 记录 policy/model version，历史请求可追溯。
  - 支持多场景策略：不同业务动作可以用不同阈值和模型版本。
- ❌ 缺点：
  - 发布系统复杂：需要 registry、approval、canary controller、rollback pipeline。
  - 配置兼容性要管理：新规则可能依赖新字段，新模型可能依赖新 feature schema。
  - 多版本同时在线会增加排障难度。
  - 需要治理权限：谁能改阈值、谁能发布模型，要有审计。

**方案 C：Shadow traffic + A/B test + guardrail**

- 适用场景：ML/ranking/风控。
- ✅ 优点：
  - 上线前评估风险：shadow traffic 不影响用户结果，但能看到新模型输出差异。
  - A/B 可量化业务指标：不仅看离线 AUC，还看线上转化、误杀、延迟、投诉等。
  - Guardrail 保护体验：主指标提升但错误率/延迟/安全指标变差时可以自动回滚。
- ❌ 缺点：
  - 实验设计复杂：样本量、分流、互斥实验、长期效应都要考虑。
  - Shadow 不等于真实效果：新模型没有真正影响用户行为，无法完全替代 A/B。
  - Guardrail 要提前定义：否则事故后才发现看错指标。
  - 成本增加：同时跑多套模型/策略会增加 serving 成本。

**推荐话术**

- `每个线上决策都记录 model_version、policy_version、feature_version。`
- `新模型先 shadow，再 canary，再 A/B，指标异常可快速 rollback。`

**容易踩坑**

- 模型上线后 feature schema 不兼容。
- 只看主指标，不看 guardrail。

## 28. Data Retention / Compaction / GC

**适用题目**

- S3
- In-memory KV Rollback
- Monitoring / Logs
- WhatsApp Status
- Tracing
- Kafka Event Log
- Price History
- Video Segments

**要解决的问题**

- 数据不能无限保留。
- 版本、日志、segments、metrics、trace、status 都需要生命周期策略。

**方案 A：永久保留所有数据**

- 适用场景：强审计且成本可接受。
- ✅ 优点：
  - 可追溯：任何历史版本、事件、对象都能查到，审计能力强。
  - 可重放：派生数据出错时可以从最早 raw data 重建。
  - Debug 方便：无需担心数据已被删除导致无法复盘。
- ❌ 缺点：
  - 成本高：存储、索引、备份、跨 region 复制都会持续增长。
  - 查询变慢：数据越来越多，分区、索引、压缩都要持续优化。
  - 隐私/合规风险增加：PII 和用户删除请求可能要求按期删除。
  - GC 变困难：没有 retention 边界时，系统长期运维压力越来越大。

**方案 B：TTL 删除**

- 适用场景：短生命周期数据。
- ✅ 优点：
  - 简单：按时间自动删除，适合短生命周期数据。
  - 成本可控：status、session、trace、cache 不会无限增长。
  - 运维自动化：减少手写清理任务。
- ❌ 缺点：
  - 删除不精确：TTL 通常是 best effort，不能保证到点删除。
  - 派生数据可能残留：search index、cache、CDN、materialized view 需要额外清理。
  - 审计和 legal hold 难处理：某些数据需要保留，某些又必须删除。
  - 不能作为可见性保证：读路径仍要检查 expires_at 或权限状态。

**方案 C：Retention policy + compaction + cold storage**

- 适用场景：成熟系统。
- ✅ 优点：
  - 成本和查询平衡：热数据保留高分辨率，冷数据压缩、降采样、归档。
  - 支持不同需求：线上查询看热数据，审计/回放去冷存储。
  - 合规更灵活：可以支持 retention、legal hold、delete request。
  - 对 versioned 系统重要：KV rollback、S3 versioning、logs 都需要 GC safe watermark。
- ❌ 缺点：
  - 生命周期治理复杂：哪些数据何时降采样、归档、删除，需要策略和 owner。
  - 历史查询变慢：冷数据在 object store/归档层，恢复成本高。
  - GC 容易误删：snapshot、legal hold、未消费 event 都可能仍引用旧数据。
  - 多层数据一致性复杂：热、温、冷之间要管理版本和索引。

**推荐话术**

- `热数据保留短期高精度，冷数据压缩、降采样或归档。`
- `GC 必须知道哪些 snapshot/version 仍被引用，不能误删。`

**容易踩坑**

- Versioned KV 保留所有版本导致内存无限增长。
- 删除 source 后 search index/cache/CDN 没清理。

## 29. Approximate vs Exact

**适用题目**

- TopK
- Trending Hashtags
- Monitoring Top Exceptions
- Ads Analytics
- Leaderboard
- Rate Limiter
- Recommendation Metrics

**要解决的问题**

- 精确统计成本高。
- 有些场景允许近似，有些绝对不允许。

**方案 A：Exact counting**

- 适用场景：计费、库存、支付、核心 leaderboard。
- ✅ 优点：
  - 正确性强：每个事件/分数/库存变化都可精确计算，适合业务承诺。
  - 可审计：能解释每个结果的来源，适合计费、财务、奖金排行榜。
  - 用户信任高：结果不因概率误差变化，争议少。
- ❌ 缺点：
  - 成本高：需要存储更多状态和事件，更新/查询都更重。
  - 高并发下难扩展：热门 key 精确计数会出现写热点。
  - 延迟可能更高：为了精确处理 late event、dedup、事务，pipeline 更复杂。
  - 不一定值得：trending、monitoring 这类场景精确性收益有限。

**方案 B：Approximate counting / sketch**

- 适用场景：trending、monitoring、推荐召回。
- ✅ 优点：
  - 省内存：sketch/HLL/sampling 用固定或近似固定空间处理大规模 key。
  - 高吞吐：更新通常很轻，适合实时趋势、监控、推荐粗召回。
  - 成本低：不需要保存完整集合或每个 key 的精确明细。
- ❌ 缺点：
  - 有误差：可能 overcount/undercount，结果不可用于财务、库存、奖金分配。
  - 解释性差：面试官追问某个数字为何如此，sketch 只能给估计。
  - 删除和窗口难：过期数据需要多个 sketch 或 decay，逻辑复杂。
  - 参数敏感：误差率、hash 数、内存大小需要根据规模调。

**方案 C：Realtime approximate + offline exact**

- 适用场景：大规模 analytics。
- ✅ 优点：
  - 实时体验好：用户先看到近似趋势或榜单，不用等离线 batch。
  - 最终可校正：离线 exact 结果用于账单、报表、最终排名。
  - 成本可控：在线层用便宜近似结构，离线层用重计算。
- ❌ 缺点：
  - 用户可能看到修正：实时榜单和最终榜单不一致时，需要产品解释。
  - merge 规则复杂：离线结果如何覆盖实时结果，历史窗口如何重新发布，要定义版本。
  - 双链路运维：实时近似和离线精确都要监控和校验。

**推荐话术**

- `我会先问这个结果是否需要 exact。`
- `计费/库存 exact，trending/monitoring 可以 approximate。`

**容易踩坑**

- 用 Count-Min Sketch 做广告计费。
- 用近似 TopK 做有奖排行榜。

## 30. Materialized View / Precompute vs Query-time Compute

**适用题目**

- Leaderboard
- News Feed
- Restaurant Recommendation
- RSS Aggregator
- TypeAhead
- Monitoring Dashboard
- Search Ranking

**要解决的问题**

- 查询时实时计算灵活但慢。
- 预计算快但不新鲜、写放大。

**方案 A：Query-time compute**

- 适用场景：低 QPS、强实时、查询维度多变。
- ✅ 优点：
  - 数据最新：每次查询直接基于 source 或最新状态计算。
  - 灵活性强：新 filter、新排序、新维度不需要提前预计算。
  - 数据模型简单：不需要维护额外 materialized view。
- ❌ 缺点：
  - 延迟高：复杂 join/aggregation/ranking 放在请求时做，p99 容易变差。
  - 高峰期成本不可控：每个用户请求都重复计算，热点 query 会放大资源消耗。
  - 难缓存：个性化、位置、实时状态会降低缓存命中。
  - 下游依赖多：query-time fanout 越多，失败面越大。

**方案 B：Precompute / materialized view**

- 适用场景：固定查询、高 QPS、首页/feed/topK。
- ✅ 优点：
  - 查询快：把复杂计算提前完成，用户请求只读结果。
  - 可缓存：固定榜单、首页候选、RSS feed、typeahead prefix 都适合缓存。
  - 峰值稳定：高峰期读预计算结果，不重复跑昂贵逻辑。
  - 适合固定查询：TopK、feed inbox、热门榜、prefix suggestions 都很典型。
- ❌ 缺点：
  - 数据可能 stale：source 改了以后 materialized view 有异步延迟。
  - 写放大：一次 source update 可能更新多个 view。
  - Backfill/rebuild 成本高：view schema 变更或 bug 修复时需要重算大量数据。
  - 灵活性差：用户临时问任意维度/窗口，预计算不一定支持。

**方案 C：Precompute candidates + query-time rerank**

- 适用场景：推荐、搜索、feed。
- ✅ 优点：
  - 召回成本低：离线/准实时预先准备候选，在线不用从全量 corpus 搜。
  - 保留实时性：最终 rerank 可以用位置、时间、session、库存、天气等实时上下文。
  - 性能可控：candidate 数量有限，ranking 服务负载稳定。
  - 适合推荐/搜索/feed：先 precompute candidates，再 query-time personalize。
- ❌ 缺点：
  - 系统复杂：候选生成、缓存、实时特征、rerank 都要配合。
  - Candidate freshness 要监控：候选过旧会影响 recall，尤其新闻、餐馆营业状态、库存。
  - Debug 更难：结果差可能是召回漏了，也可能是 rerank 排错。
  - 多版本管理：candidate model 和 ranking model 的版本要能对应。

**推荐话术**

- `高 QPS 固定查询预计算；实时上下文在 query-time rerank。`
- `预计算结果是 derived view，必须能从 source event 重建。`

**容易踩坑**

- 所有 feed 每次请求都从原始 graph 实时算。
- 预计算后没有 invalidation 和 rebuild。

## 31. Rate Limit / Quota：Local vs Global

**适用题目**

- Rate Limiter
- LLM Inference
- API Gateway
- Web Crawler Politeness
- Monitoring Ingestion
- Ads / Risk API

**要解决的问题**

- 单机 local counter 快但不全局准确。
- 全局 quota 准确但延迟高。
- 跨 DC rate limit 更复杂。

**方案 A：Local limiter**

- 适用场景：低风险 API，保护单节点。
- ✅ 优点：
  - 延迟最低：在本机内存判断，不需要远程 Redis/DB。
  - 高可用：外部 quota store 挂了也不影响本机基本保护。
  - 适合防止单节点过载：每个 gateway/worker 都能保护自己。
- ❌ 缺点：
  - 全局不准确：N 个节点各放行 limit，整体可能超出 N 倍。
  - 扩缩容影响语义：实例数变化时有效 quota 变化。
  - 用户请求分布不均：某些节点热，某些节点冷，local quota 利用不均。
  - 不适合强计费 quota：API key 全球额度不能只靠 local counter。

**方案 B：Central Redis / counter store**

- 适用场景：单 region 精确 quota。
- ✅ 优点：
  - 全局一致性较好：同一 API key/user 的计数集中到一个 store，语义清晰。
  - 实现直观：Redis INCR、sorted set、Lua script 很容易实现 token/sliding window。
  - 适合单 region 精确限流。
- ❌ 缺点：
  - Redis hot key：热门 API key 或匿名 IP 会集中打一个 shard。
  - 延迟增加：每个请求都要远程读写 quota store，p99 受 Redis 影响。
  - 可用性取舍：Redis 故障时 fail open 会放大风险，fail closed 会伤害可用性。
  - 跨 region 困难：全球都打同一个 Redis 不现实。

**方案 C：Local quota + global sync**

- 适用场景：跨 region / 高 QPS。
- ✅ 优点：
  - 延迟低：请求先在本地 region 判断，不需要跨 region 同步。
  - 可扩展：local service 承接高 QPS，global service 只做周期汇总和配额分配。
  - 适合跨 DC：每个 region 有预算，周期性向 global sync。
- ❌ 缺点：
  - 只能近似全局限制：sync interval 内多个 region 可能同时超用额度。
  - 短时间 overshoot：尤其流量突增时，global 还没来得及收回 quota。
  - 策略复杂：如何分配 local budget、如何处理 region 热点、如何惩罚超用都要设计。
  - 不适合强金融额度：严格余额/扣费不能用近似 quota 代替。

**推荐话术**

- `用户请求先走 local quota，local 周期性向 global quota service sync。`
- `高风险 API fail closed，低风险 API fail open 或降级。`

**容易踩坑**

- 所有请求都打同一个 Redis key。
- 跨 DC 强同步限流导致 API 延迟升高。

## 32. Durable Log / Replay as System Backbone

**适用题目**

- Ads Aggregation
- Monitoring
- Tracing
- TopK
- Event Sourcing
- RAG Ingestion
- Web Crawler
- CDC Pipeline

**要解决的问题**

- 派生数据会错、会丢、会需要重建。
- 没有 durable raw log，就无法 backfill 和 debug。

**方案 A：只存最终状态**

- 适用场景：简单 CRUD。
- ✅ 优点：
  - 存储少：只保留当前状态，不保存完整历史事件。
  - 查询简单：直接读当前值，适合普通 CRUD。
  - 开发快：不需要 event schema、log retention、replay 工具。
- ❌ 缺点：
  - 无法重放：派生数据错了，没有原始事件可以重新构建。
  - 无法修复历史：聚合 bug、漏消费、迟到事件都难以补偿。
  - Debug 困难：只知道最终状态，不知道它怎么变成这样。
  - 审计能力弱：金融、广告、风控、监控通常不能只存最终状态。

**方案 B：Raw event log**

- 适用场景：数据 pipeline、analytics、CDC。
- ✅ 优点：
  - 可 replay：search index、metrics、TopK、feature store 坏了都能从 log 重建。
  - 可 backfill：新增字段、新模型、新聚合逻辑可以回放历史数据。
  - 审计能力强：每个状态变化都有原始事实支撑。
  - 解耦系统：多个下游可以独立消费同一份事实流。
- ❌ 缺点：
  - 存储成本高：raw event 量大，保留时间越长成本越高。
  - Schema evolution 复杂：旧事件要能被新代码读取，或者有 migration。
  - 隐私和合规压力：raw log 可能包含 PII，需要脱敏、权限、retention。
  - 重放要谨慎：外部副作用不能随便 replay，必须区分可重建状态和不可重复动作。

**方案 C：Raw log + compacted state**

- 适用场景：同时需要恢复和快速查询。
- ✅ 优点：
  - 职责清晰：log 负责事实和 replay，compacted state 负责低延迟 serving。
  - 恢复速度更好：服务启动时加载 snapshot/compacted state，再 replay 增量 log。
  - 成本平衡：不需要每次查询都扫 raw log，也不失去重建能力。
- ❌ 缺点：
  - 两套数据一致性要治理：state 落后于 log 时，读到的可能不是最新事实。
  - Retention 更复杂：raw log、snapshot、compacted state 的生命周期要协调。
  - Rebuild 流程要演练：不能只理论上可 replay，实际工具和 runbook 要存在。
  - Schema 兼容更重要：log 和 state 都要支持版本演进。

**推荐话术**

- `所有 derived data 都应该能从 durable log/source of truth 重建。`
- `raw log 是 backfill、reconciliation、debug 的基础。`

**容易踩坑**

- 只保留聚合结果，不保留原始事件。
- schema 没有 version，旧事件无法 replay。

## 33. Staff+ 总表达模板

这些句子可以在很多系统设计里直接用：

- `I would first define the correctness boundary.`
- `The source-of-truth write path must be protected with idempotency, conditional update, or a state machine.`
- `Everything else, like cache, search index, notification, analytics, realtime push, recommendation result, and read model, can be eventually consistent, replayable, and rebuildable.`
- `I would not promise end-to-end exactly-once. I would design for at-least-once delivery and idempotent effects.`
- `The real bottleneck is often hot entity traffic, not average QPS.`
- `For multi-region, I would avoid global strong consistency unless it protects a specific invariant.`
- `Queue is not free. I would define queue limit, timeout, priority, and drop policy.`
- `Search freshness can be eventually consistent, but permission and deletion correctness cannot.`
- `TTL is cleanup, not correctness. The read path still checks expires_at.`
- `If this is a financial or inventory-related system, I would add reconciliation and raw event replay.`
