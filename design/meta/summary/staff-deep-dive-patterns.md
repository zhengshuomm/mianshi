# Staff+ System Design Deep Dive Patterns

这份总结来自 `mianshi/design` 里的系统设计题库。目标不是背组件名，而是在 deep dive 里展示 Staff Engineer 的判断力：

- 先定义 correctness invariant。
- 再区分 source of truth 和 derived data。
- 对每个关键选择讲清 trade-off、failure mode、scale path。
- 不轻易承诺 exactly-once、global strong consistency、实时强一致。
- 任何异步链路都要能 retry、replay、rebuild、observe。

面试时可以把下面每个 pattern 当成一个“深挖模块”。当面试官追问一致性、扩展性、失败恢复、实时性、缓存、搜索、推荐、队列时，直接套用。

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
  - 简单可靠，失败后 replay 不丢数据。
  - 对 Kafka/SQS/PubSub 都适用。
  - 运维和 debug 容易。
- ❌ 缺点：
  - sink 不幂等就会重复计数、重复扣款、重复通知。
  - dedup window 过短会漏掉旧重复事件。
  - 对外部 API 很难保证幂等，需要对方支持 request id。

**方案 B：Kafka transactional producer**

- 适用场景：一个 Kafka pipeline 中需要同时写多个 topic，且希望原子 commit/abort。
- 做法：
  - transactional producer 绑定 input offset 和 output topic commit。
  - 出错时 abort transaction。
- ✅ 优点：
  - Kafka 内部可以避免一部分重复 output。
  - 对 multi-topic pipeline 有帮助。
- ❌ 缺点：
  - 只能解决 Kafka 内部事务。
  - 不自动解决外部 DB、Redis、Druid、HTTP API 写入 exactly-once。
  - 增加 producer 协议复杂度和延迟。

**方案 C：Flink checkpoint + transactional / idempotent sink**

- 适用场景：stream aggregation、window count、TopK、ads metrics。
- 做法：
  - Flink checkpoint 保存 offset + operator state。
  - sink 支持 2PC、事务 commit，或使用 deterministic key overwrite。
- ✅ 优点：
  - 恢复时从一致状态继续。
  - 对 stateful aggregation 很强。
- ❌ 缺点：
  - 外部 sink 必须配合。
  - checkpoint interval 越短，overhead 越高。
  - late event、schema change、reprocessing 仍要额外处理。

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
  - 一致性强。
  - 权限、库存、状态最新。
- ❌ 缺点：
  - 延迟高。
  - 难以支持全文搜索、feed、推荐、TopK。
  - 容易把核心 DB 打爆。

**方案 B：Derived store 直接服务用户**

- 适用场景：搜索、feed、推荐、首页、实时列表、CDN 播放。
- ✅ 优点：
  - 查询低延迟。
  - 可以按访问模式建模，比如 inverted index、sorted set、user inbox。
  - 易扩展读流量。
- ❌ 缺点：
  - stale data。
  - 搜索结果可能包含已删除、已过期、无权限内容。
  - cache/index 和 DB 不一致时，用户体验或 correctness 出问题。

**方案 C：Derived store + read-time validation**

- 适用场景：搜索后下单、抢票、播放授权、隐私内容搜索。
- 做法：
  - 搜索/缓存先返回候选。
  - 最终返回或关键动作前查 source of truth 校验。
- ✅ 优点：
  - 读性能和 correctness 平衡。
  - 派生索引可以异步重建。
- ❌ 缺点：
  - 多一次服务调用。
  - source of truth 不可用时关键动作失败。

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
  - 简单。
  - 延迟低。
- ❌ 缺点：
  - crash window 导致 DB/MQ 不一致。
  - 事件丢失后派生索引永远不更新。
  - 很难人工补偿。

**方案 B：2PC**

- 适用场景：内部系统都支持 XA/2PC，且强一致要求极高。
- ✅ 优点：
  - 理论上 DB 和 MQ 原子提交。
- ❌ 缺点：
  - MQ、外部服务、第三方 API 通常不支持。
  - 阻塞、协调器故障、运维复杂。
  - 降低可用性。

**方案 C：Outbox / CDC**

- 适用场景：生产系统里的 DB -> event -> cache/index/notification。
- 做法：
  - 同一个 DB transaction 写 business table 和 outbox table。
  - CDC 或 outbox relay 异步把事件发到 Kafka。
  - consumer 幂等更新派生系统。
- ✅ 优点：
  - DB 状态和“待发送事件”原子提交。
  - 事件可重放、可监控、可补偿。
  - 不要求 MQ 和 DB 做 2PC。
- ❌ 缺点：
  - 有异步延迟。
  - CDC/relay 需要运维。
  - consumer 必须幂等。

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
  - 减少重复请求。
  - 简单。
- ❌ 缺点：
  - 不能作为 correctness guarantee。
  - 恶意请求、刷新、移动端重试都绕得过。

**方案 B：Idempotency key / unique constraint**

- 适用场景：创建订单、支付、提交任务、发送消息。
- 做法：
  - Client 或 server 生成 idempotency key。
  - Server 保存 `key -> result`。
  - 重复请求返回第一次结果。
- ✅ 优点：
  - 防重复副作用最直接。
  - 业务语义清晰。
- ❌ 缺点：
  - 需要 key TTL。
  - key scope 要清晰，例如 per user/per endpoint/per payment。
  - 请求 body 不同但 key 相同要拒绝。

**方案 C：Event dedup table / window dedup**

- 适用场景：事件流、click aggregation、monitoring、crawler URL。
- ✅ 优点：
  - 高吞吐场景可控。
  - 可按 event_id、hash、business key 去重。
- ❌ 缺点：
  - 全量 dedup 成本高。
  - window 外重复可能无法识别。

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
  - 语义简单。
  - Debug 容易。
- ❌ 缺点：
  - 单点瓶颈。
  - 跨 region 延迟高。
  - 很多业务不需要，成本浪费。

**方案 B：Per-entity ordering**

- 适用场景：订单状态按 order_id，聊天消息按 thread_id，账户事件按 account_id。
- 做法：
  - Kafka partition key 使用 entity id。
  - 单 entity 事件进入同一 partition。
- ✅ 优点：
  - 保证关键业务顺序。
  - 吞吐可通过不同 entity 分区扩展。
- ❌ 缺点：
  - hot entity 会打爆单 partition。
  - 跨 entity 没有全局顺序。

**方案 C：Version / sequence check**

- 适用场景：客户端可离线、重试、乱序；或者状态机必须防止倒退。
- 做法：
  - 每个 event 带 sequence/version。
  - DB conditional update：只接受 `new_version > current_version`。
- ✅ 优点：
  - 可以拒绝旧事件。
  - 能处理重试和乱序。
- ❌ 缺点：
  - 客户端和服务端协议复杂。
  - sequence gap 需要恢复逻辑。

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
  - 读 feed/inbox 很快。
  - unread、cursor、delivery state 容易维护。
- ❌ 缺点：
  - 写放大。
  - 大 V、大 channel、大 workspace 会造成瞬时写爆。
  - 更新 privacy/follow 关系时修正成本高。

**方案 B：Fanout-on-read**

- 适用场景：大 V、超大 channel、写多读少内容。
- ✅ 优点：
  - 发布成本低。
  - 避免大用户写爆系统。
- ❌ 缺点：
  - 读路径慢，需要查询多个 source。
  - 排序、去重、分页复杂。
  - 活跃用户读请求会有 scatter-gather。

**方案 C：Hybrid**

- 适用场景：真实社交/feed 系统。
- 做法：
  - 普通作者 fanout-on-write。
  - 大 V/大 channel 标记为 pull source。
  - 读路径 merge inbox + pull source。
- ✅ 优点：
  - 普通路径读快。
  - 热点作者写入可控。
- ❌ 缺点：
  - 系统复杂。
  - 需要分类标准、cache、merge pagination。

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
  - 简单。
  - DB 是 source of truth。
- ❌ 缺点：
  - cache stampede。
  - invalidate 失败会 stale。
  - 双删、延迟删除等细节复杂。

**方案 B：Write-through**

- 适用场景：希望 cache 和 DB 更新同步。
- ✅ 优点：
  - 读到旧值概率低。
  - 写后读一致性较好。
- ❌ 缺点：
  - 写延迟变高。
  - cache 故障可能影响写路径。
  - 对写多系统成本高。

**方案 C：Write-behind**

- 适用场景：写多、允许短暂不一致，如计数、非关键状态。
- ✅ 优点：
  - 写性能高。
  - 可批量落库。
- ❌ 缺点：
  - cache crash 可能丢数据。
  - 恢复和重放复杂。
  - 不适合订单、支付、库存 source of truth。

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
  - 容易理解。
  - 可以把并发控制放在 service 层。
- ❌ 缺点：
  - lock TTL 过短会提前释放。
  - client crash、GC pause、network partition 会破坏语义。
  - lock 成功不等于 DB 更新成功。

**方案 B：DB conditional update / optimistic lock**

- 适用场景：库存、座位、rider 状态、balance。
- 做法：
  - `UPDATE seats SET status='held' WHERE seat_id=? AND status='available'`
  - DynamoDB conditional write。
- ✅ 优点：
  - correctness 在 source of truth 层。
  - 无需额外分布式锁。
- ❌ 缺点：
  - 热点行竞争严重。
  - 高峰期失败重试多。

**方案 C：Single writer / partitioned command queue**

- 适用场景：极热点 entity，例如热门演唱会、秒杀 SKU、auction item。
- ✅ 优点：
  - 串行处理天然避免 double booking。
  - 状态机简单。
- ❌ 缺点：
  - 单 partition 写吞吐有限。
  - failover 和 replay 要设计好。

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
  - 强一致。
  - 简单。
- ❌ 缺点：
  - 不能覆盖外部支付、商家、rider、航空公司、交易所。

**方案 B：2PC**

- 适用场景：少数内部强一致资源。
- ✅ 优点：
  - 理论保证强。
- ❌ 缺点：
  - 外部服务很少支持。
  - 阻塞和恢复复杂。
  - 可用性差。

**方案 C：Saga + 状态机 + 补偿**

- 适用场景：真实业务长流程。
- 做法：
  - 每步本地提交。
  - 失败后执行补偿，例如 refund、release hold、re-dispatch。
  - 状态机记录每一步结果。
- ✅ 优点：
  - 扩展性和可用性好。
  - 适合异步、超时、人工介入。
- ❌ 缺点：
  - 最终一致。
  - 补偿逻辑复杂。
  - 用户可能看到 pending 状态。

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
  - 简单。
- ❌ 缺点：
  - worker crash 后任务丢。
  - 无法重试。

**方案 B：Visibility timeout / lease**

- 适用场景：SQS、job scheduler、dispatch lease。
- 做法：
  - worker 获取任务后任务暂时不可见。
  - 成功后 ack/delete。
  - 超时未 ack 则重新可见。
- ✅ 优点：
  - worker crash 后任务可恢复。
  - 不需要中心化 health check。
- ❌ 缺点：
  - 任务可能被重复执行。
  - 长任务要 heartbeat extend lease。

**方案 C：Durable workflow**

- 适用场景：多步骤、长时间任务，如转码、订单、支付、复杂 job。
- ✅ 优点：
  - retry、timeout、compensation 内建。
  - 状态可查询。
- ❌ 缺点：
  - 引入 Temporal/Step Functions 类复杂度。

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
  - 表面上不丢请求。
- ❌ 缺点：
  - 请求在队列里过期。
  - 用户等待不可控。
  - 下游恢复后被积压流量再次打爆。

**方案 B：快速拒绝 / fail fast**

- 适用场景：请求超过 SLA、系统过载。
- ✅ 优点：
  - 保护核心服务。
  - 用户可以快速重试或切换。
- ❌ 缺点：
  - 体验差。
  - 需要合理错误码和 retry-after。

**方案 C：Priority queue + degradation**

- 适用场景：不同请求价值不同。
- 做法：
  - 高优先级请求保留容量。
  - 低优先级降级、采样或拒绝。
- ✅ 优点：
  - 保护核心用户和关键路径。
  - 提升资源利用。
- ❌ 缺点：
  - 策略复杂。
  - 可能有公平性问题。

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
  - 简单。
  - 扩容容易。
- ❌ 缺点：
  - 单个 hot key 仍落在一个 shard。
  - 无法解决 celebrity/hot event。

**方案 B：Hot key splitting**

- 适用场景：计数、评论、实时流、热门商品 watch list。
- 做法：
  - 把一个 hot key 拆成多个 sub-key。
  - 写入分散，读取聚合。
- ✅ 优点：
  - 分摊写流量。
  - 可动态增加 split 数。
- ❌ 缺点：
  - 读时聚合复杂。
  - 顺序、一致性、分页更难。

**方案 C：Single writer + read scaling**

- 适用场景：必须保持强顺序或唯一状态，如库存、拍卖最高价。
- ✅ 优点：
  - 写入顺序清晰。
  - 读可以通过 cache/read replica 扩展。
- ❌ 缺点：
  - 写瓶颈仍存在。
  - failover 更关键。

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
  - 地理表达力强。
  - 支持复杂空间查询。
- ❌ 缺点：
  - 高 QPS online 查询压力大。
  - 分片和热点区域扩展复杂。

**方案 B：Geohash**

- 适用场景：简单 nearby search，工程复杂度低。
- ✅ 优点：
  - prefix 自然表示区域。
  - 容易按 cell 分片。
- ❌ 缺点：
  - 边界问题。
  - cell 大小固定，不适合人口密度差异。
  - 靠近但 geohash prefix 不一定相同。

**方案 C：H3/S2**

- 适用场景：大规模 online geo search。
- ✅ 优点：
  - 层级 cell，neighbor 查询清晰。
  - 更适合 geo-sharding。
  - 可以按 cell resolution 调整精度。
- ❌ 缺点：
  - 实现和调参复杂。
  - 仍要处理边界和扩大半径。

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
  - 服务端简单。
  - 无长连接。
- ❌ 缺点：
  - 实时性差。
  - 高频 polling 浪费资源。
  - 大量用户同时 polling 会放大 QPS。

**方案 B：SSE**

- 适用场景：服务端单向推送，如订单状态、runner result、排队进度。
- ✅ 优点：
  - 比 WebSocket 简单。
  - HTTP 友好，自动重连模型较简单。
- ❌ 缺点：
  - 单向通信。
  - 部分移动端/代理环境要验证。
  - 每个连接仍占资源。

**方案 C：WebSocket**

- 适用场景：双向实时，如 chat、collaboration、live comment。
- ✅ 优点：
  - 低延迟。
  - 支持双向消息。
- ❌ 缺点：
  - 长连接扩容复杂。
  - Gateway failure 后要重连恢复。
  - 需要 connection registry 和 routing。

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
  - 实现简单。
  - 存储自动回收。
- ❌ 缺点：
  - 删除时间不精确。
  - 不能保证到点马上不可见。
  - 派生索引/cache 可能残留。

**方案 B：Delayed queue**

- 适用场景：到期需要触发动作，例如释放 hold、执行 job。
- ✅ 优点：
  - 更接近实时。
  - 能驱动 workflow。
- ❌ 缺点：
  - 大规模 delayed message 成本高。
  - worker failure 和 retry 仍要处理。

**方案 C：Time bucket scanner**

- 适用场景：大量过期对象，可接受分钟级延迟。
- ✅ 优点：
  - 稳定，可恢复，可 backfill。
  - 容易做分片扫描。
- ❌ 缺点：
  - 不精确。
  - 扫描延迟。

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
  - 写后立刻可搜。
- ❌ 缺点：
  - Index 故障影响主写。
  - 写延迟高。
  - 不适合高吞吐变更。

**方案 B：CDC / event 异步建索引**

- 适用场景：生产级搜索系统。
- ✅ 优点：
  - 主写链路稳定。
  - 索引可 replay/rebuild。
  - 支持批量优化。
- ❌ 缺点：
  - 索引延迟。
  - 删除/权限变更传播慢会有风险。

**方案 C：异步索引 + read-time permission/state validation**

- 适用场景：涉及隐私、库存、可见性。
- ✅ 优点：
  - 性能和正确性平衡。
  - 不怕索引短暂 stale。
- ❌ 缺点：
  - 查询路径变复杂。
  - 校验服务不可用会影响返回。

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
  - 读写一致性强。
- ❌ 缺点：
  - 跨洲延迟高。
  - region failure 影响可用性。
  - 成本和复杂度高。

**方案 B：Home region ownership**

- 适用场景：用户、订单、文档、ride、status 有自然 owner。
- ✅ 优点：
  - 写入低延迟。
  - ownership 清晰。
  - 冲突少。
- ❌ 缺点：
  - 跨 region 读取有复制延迟。
  - 用户迁移和跨区协作复杂。

**方案 C：Active-active multi-master**

- 适用场景：协作编辑、全球低延迟写入、极高可用。
- ✅ 优点：
  - 任意 region 可写。
  - 局部故障影响小。
- ❌ 缺点：
  - 冲突解决复杂。
  - 权限撤销、删除、金融状态很难处理。
  - 需要 CRDT/OT/业务冲突策略。

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
  - 实时。
  - 简单。
- ❌ 缺点：
  - late event、duplicate event 会污染结果。
  - bug 后很难修正历史。

**方案 B：离线 batch 重新计算**

- 适用场景：财务结算、日报、最终账单。
- ✅ 优点：
  - 准确，可修正历史。
  - 可以用完整数据和更复杂逻辑。
- ❌ 缺点：
  - 延迟高。
  - 不能服务实时体验。

**方案 C：实时 serving + 离线 reconciliation**

- 适用场景：成熟生产系统。
- ✅ 优点：
  - 用户看到实时结果。
  - 后台最终修正。
- ❌ 缺点：
  - 两套结果可能短暂不同。
  - merge 和覆盖规则要清楚。

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
  - 精确。
  - 支持细粒度修正。
- ❌ 缺点：
  - 内存大。
  - 淘汰旧事件成本高。
  - TopK 更新复杂。

**方案 B：Bucket aggregation**

- 适用场景：固定窗口，如过去 1h，每分钟一个 bucket。
- ✅ 优点：
  - 内存可控。
  - 查询时合并固定数量 buckets。
- ❌ 缺点：
  - 边界精度损失。
  - 任意窗口支持差。
  - bucket 数多时合并成本高。

**方案 C：OLAP store / Druid / ClickHouse**

- 适用场景：任意 lookup window、复杂 group by。
- ✅ 优点：
  - 查询灵活。
  - 支持历史分析。
- ❌ 缺点：
  - 延迟和成本高于内存 TopK。
  - 不适合每个请求都超高 QPS。

**方案 D：Sketch / approximate**

- 适用场景：超大规模，允许近似。
- ✅ 优点：
  - 省内存，高吞吐。
- ❌ 缺点：
  - 有误差。
  - 删除过期数据和解释性差。

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
  - 快速迭代。
- ❌ 缺点：
  - training-serving skew。
  - 线上效果不可预测。
  - Debug 很困难。

**方案 B：Feature Store 统一定义**

- 适用场景：生产 ML 系统。
- ✅ 优点：
  - 离线/在线特征定义一致。
  - 特征复用。
- ❌ 缺点：
  - 平台建设复杂。
  - 特征版本、权限、质量都要治理。

**方案 C：Point-in-time join + online decision logging**

- 适用场景：风控、推荐、广告。
- 做法：
  - 训练时只使用 label 时间之前可见的特征。
  - 在线每次决策记录 feature vector、model version、policy version。
- ✅ 优点：
  - 避免 data leakage。
  - 可 replay/debug。
- ❌ 缺点：
  - 数据工程复杂。
  - 存储成本高。

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
  - 短期 CTR/CVR 高。
  - 用户体验稳定。
- ❌ 缺点：
  - 头部效应严重。
  - 新内容、新餐馆、新 creator 没机会。
  - 训练数据偏差越来越强。

**方案 B：Fixed exploration slots**

- 适用场景：早期探索机制。
- ✅ 优点：
  - 简单可控。
  - 容易设置 guardrail。
- ❌ 缺点：
  - 探索效率低。
  - slot 设计粗糙会伤害体验。

**方案 C：Contextual bandit / uncertainty exploration**

- 适用场景：成熟推荐系统。
- ✅ 优点：
  - 根据不确定性和上下文分配流量。
  - 长期学习效率高。
- ❌ 缺点：
  - 实现复杂。
  - 评估、归因、guardrail 难。

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
  - 简单。
- ❌ 缺点：
  - 无法解释业务错误。
  - 无法定位策略、模型、异步 pipeline 问题。

**方案 B：Decision logging**

- 适用场景：推荐、风控、调度、订单。
- 做法：
  - 记录 request_id、input、decision、reason、model_version、policy_version。
- ✅ 优点：
  - 可 debug。
  - 可审计。
  - 支持 offline analysis。
- ❌ 缺点：
  - 存储成本高。
  - PII 和权限治理复杂。

**方案 C：Trace + DLQ + replay**

- 适用场景：异步复杂系统。
- ✅ 优点：
  - 可以重放事件修复派生数据。
  - DLQ 支持人工处理坏消息。
- ❌ 缺点：
  - schema version、幂等、重放边界都要设计。

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
  - 写吞吐高。
  - append-only 简单可靠。
- ❌ 缺点：
  - 查询慢。
  - 需要异步聚合或索引。

**方案 B：读优化**

- 适用场景：feed inbox、leaderboard topK、search index。
- ✅ 优点：
  - 用户读低延迟。
  - 查询模型贴合产品体验。
- ❌ 缺点：
  - 写放大。
  - 派生数据一致性复杂。

**方案 C：CQRS / source table + read model**

- 适用场景：成熟系统。
- ✅ 优点：
  - 写路径简单可靠。
  - 读路径可按场景优化。
- ❌ 缺点：
  - 多套数据。
  - 异步同步和回放复杂。

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
  - 语义简单。
- ❌ 缺点：
  - 延迟高。
  - 可用性差。
  - 扩展困难。

**方案 B：全系统最终一致**

- 适用场景：低风险 read model。
- ✅ 优点：
  - 高可用，高吞吐。
- ❌ 缺点：
  - 可能 oversell、重复扣款、越权读取、状态倒退。

**方案 C：关键 invariant 强一致，其余最终一致**

- 适用场景：绝大多数系统设计。
- ✅ 优点：
  - 成本和正确性平衡。
  - 只把复杂性用在真正需要的地方。
- ❌ 缺点：
  - 需要清楚定义 invariant。
  - 工程上要维护不同一致性等级。

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
  - 简单。
  - 吞吐高。
- ❌ 缺点：
  - 多步骤状态需要自己管理。
  - timeout、compensation、human intervention 复杂。

**方案 B：Cron / scanner**

- 适用场景：周期性低精度任务。
- ✅ 优点：
  - 简单，容易运维。
- ❌ 缺点：
  - 调度精度低。
  - 扫描大表成本高。
  - failure recovery 需要额外设计。

**方案 C：Durable workflow engine**

- 适用场景：转码 pipeline、订单支付配送、复杂 job。
- ✅ 优点：
  - 状态持久化。
  - retry、timeout、compensation 清晰。
  - 可查询和恢复。
- ❌ 缺点：
  - 引入平台复杂度。
  - 对简单任务过重。

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
  - 实现快。
- ❌ 缺点：
  - 完全不安全。
  - API 仍可访问。

**方案 B：服务端统一鉴权**

- 适用场景：绝大多数系统。
- ✅ 优点：
  - 权限边界清晰。
  - 每次读写都可审计。
- ❌ 缺点：
  - 增加延迟。
  - 每个派生系统都要带权限上下文。

**方案 C：加密 / client-side search / signed URL**

- 适用场景：E2EE、媒体、私有文件、DRM。
- ✅ 优点：
  - 隐私强。
  - 减少服务端明文暴露。
- ❌ 缺点：
  - 搜索、索引、调试、恢复更复杂。
  - client-side index 覆盖有限。

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
  - 简单。
- ❌ 缺点：
  - 无法回滚。
  - 无法解释历史请求用了什么版本。
  - A/B test 困难。

**方案 B：Versioned config/model + canary**

- 适用场景：生产策略系统。
- ✅ 优点：
  - 可灰度、可回滚。
  - 可以按用户/region/action_type 放量。
- ❌ 缺点：
  - 发布系统更复杂。
  - 配置兼容性要管理。

**方案 C：Shadow traffic + A/B test + guardrail**

- 适用场景：ML/ranking/风控。
- ✅ 优点：
  - 上线前评估风险。
  - 可以比较业务指标。
- ❌ 缺点：
  - 实验设计和统计分析复杂。
  - guardrail 指标要提前定义。

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
  - 可追溯。
  - 可重放。
- ❌ 缺点：
  - 成本高。
  - 查询变慢。
  - 隐私/合规风险增加。

**方案 B：TTL 删除**

- 适用场景：短生命周期数据。
- ✅ 优点：
  - 简单。
  - 成本可控。
- ❌ 缺点：
  - 删除不精确。
  - 派生数据可能残留。
  - legal hold 或审计需求难处理。

**方案 C：Retention policy + compaction + cold storage**

- 适用场景：成熟系统。
- ✅ 优点：
  - 热数据保留高分辨率。
  - 冷数据降采样或归档。
  - 成本和合规平衡。
- ❌ 缺点：
  - 生命周期治理复杂。
  - 回滚/历史查询变慢。

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
  - 正确性强。
  - 可审计。
- ❌ 缺点：
  - 成本高。
  - 高并发下难扩展。

**方案 B：Approximate counting / sketch**

- 适用场景：trending、monitoring、推荐召回。
- ✅ 优点：
  - 省内存。
  - 高吞吐。
- ❌ 缺点：
  - 有误差。
  - 不适合财务和强业务承诺。

**方案 C：Realtime approximate + offline exact**

- 适用场景：大规模 analytics。
- ✅ 优点：
  - 实时体验好。
  - 最终可以校正。
- ❌ 缺点：
  - 用户可能看到修正。
  - 需要解释两个结果的关系。

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
  - 数据最新。
  - 不需要维护额外 read model。
- ❌ 缺点：
  - 延迟高。
  - 高峰期成本不可控。

**方案 B：Precompute / materialized view**

- 适用场景：固定查询、高 QPS、首页/feed/topK。
- ✅ 优点：
  - 查询快。
  - 可缓存。
- ❌ 缺点：
  - 数据可能 stale。
  - 写放大。
  - backfill 和 rebuild 成本高。

**方案 C：Precompute candidates + query-time rerank**

- 适用场景：推荐、搜索、feed。
- ✅ 优点：
  - 召回成本低。
  - 最终排序仍能用实时上下文。
- ❌ 缺点：
  - 系统复杂。
  - candidate freshness 要监控。

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
  - 延迟低。
  - 不依赖外部 store。
- ❌ 缺点：
  - 多节点加起来会超全局 quota。
  - 节点扩缩容影响 limit。

**方案 B：Central Redis / counter store**

- 适用场景：单 region 精确 quota。
- ✅ 优点：
  - 全局一致性较好。
  - 实现直观。
- ❌ 缺点：
  - Redis hot key。
  - 延迟增加。
  - Redis 故障时要 fail open/closed。

**方案 C：Local quota + global sync**

- 适用场景：跨 region / 高 QPS。
- ✅ 优点：
  - 延迟低。
  - 可扩展。
- ❌ 缺点：
  - 只能近似全局限制。
  - 短时间可能 overshoot。

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
  - 存储少。
  - 查询简单。
- ❌ 缺点：
  - 无法重放。
  - 无法修复历史聚合。
  - Debug 困难。

**方案 B：Raw event log**

- 适用场景：数据 pipeline、analytics、CDC。
- ✅ 优点：
  - 可 replay、backfill、重建 read model。
  - 保留审计。
- ❌ 缺点：
  - 存储成本高。
  - schema evolution 复杂。

**方案 C：Raw log + compacted state**

- 适用场景：同时需要恢复和快速查询。
- ✅ 优点：
  - log 负责 replay，state 负责 serving。
  - 恢复速度更好。
- ❌ 缺点：
  - 两套数据一致性和 retention 要治理。

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
