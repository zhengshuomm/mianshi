# Staff+ System Design Deep Dive Patterns

这份总结来自 `mianshi/design` 里的系统设计题库，重点不是背组件，而是在 deep dive 里展示 Staff Engineer 的判断：correctness boundary、trade-off、failure mode、scale path、operability。

## 1. Exactly Once / At-least-once

- 方案 A：At-least-once + 幂等 sink
  - 适用场景：大多数生产系统。
  - ✅ 优点：简单可靠，失败后 replay 不丢数据。
  - ❌ 缺点：sink 不幂等就会重复计数、重复写、重复通知。

- 方案 B：Kafka transactional producer
  - 适用场景：一个 Kafka pipeline 中同时写多个 topic，需要原子 commit/abort。
  - ✅ 优点：支持 transaction abort/commit，减少 Kafka 内部重复。
  - ❌ 缺点：只能解决 Kafka 内部事务，不自动解决外部 DB 或第三方系统写入 exactly-once。

- 方案 C：Flink checkpoint + transactional / idempotent sink
  - 适用场景：stream processing aggregation。
  - ✅ 优点：checkpoint 可以保存 offset 和 state，恢复时从一致位置继续。
  - ❌ 缺点：外部 sink 必须支持 2PC、事务、幂等 key 或 overwrite。

- 推荐：
  - 面试里不要轻易说端到端 exactly-once。
  - 更好的说法是：Kafka/Flink 内部可做到 exactly-once processing，但外部效果依赖幂等写入或事务 sink。
  - 对 Druid/ClickHouse/DB sink，可以用 deterministic key、window key、sequence id、Kafka offset tracking 降低重复 ingestion。

## 2. Source of Truth vs Derived Data

- 方案 A：所有读都查 source of truth
  - 适用场景：低 QPS、强一致、关键读。
  - ✅ 优点：一致性强，权限和状态最新。
  - ❌ 缺点：延迟高，扩展差，容易把核心 DB 打爆。

- 方案 B：Cache/Search/Read Model 直接服务用户
  - 适用场景：高 QPS 读、搜索、feed、推荐、实时列表。
  - ✅ 优点：低延迟，高吞吐，读模型可以按查询方式优化。
  - ❌ 缺点：stale data、权限泄露、库存错误、搜索结果过期。

- 方案 C：Derived data + read-time validation
  - 适用场景：读多但关键动作要正确，例如搜索后下单、抢票、播放授权。
  - ✅ 优点：普通读快，关键动作仍正确。
  - ❌ 缺点：多一次校验，链路复杂，依赖 source of truth 可用性。

- 推荐：
  - 搜索索引、feed inbox、cache、CDN、recommendation result 都是 derived data。
  - 真正影响钱、库存、权限、隐私的动作必须回到 source of truth 校验。

## 3. DB 和 MQ 一致性

- 方案 A：写 DB 后直接 publish MQ
  - 适用场景：demo 或低风险异步任务。
  - ✅ 优点：实现简单，低延迟。
  - ❌ 缺点：DB commit 后 publish 前 crash 会丢事件；publish 成功 DB 失败也会出现脏事件。

- 方案 B：2PC
  - 适用场景：多个内部资源都支持 XA/2PC，强一致要求极高。
  - ✅ 优点：理论强一致。
  - ❌ 缺点：外部系统通常不支持，阻塞复杂，可用性差。

- 方案 C：Outbox / CDC
  - 适用场景：生产系统里 DB 状态变更后触发消息、索引、通知。
  - ✅ 优点：DB commit 和 outbox 同事务，事件可 replay，可监控。
  - ❌ 缺点：异步延迟，CDC pipeline 需要运维。

- 推荐：
  - 优先讲 Outbox/CDC。
  - 正确表达：主写路径只写 source of truth 和 outbox，后续 indexing、notification、analytics 从 outbox/CDC 异步消费。

## 4. 幂等和去重

- 方案 A：客户端防重复点击
  - 适用场景：改善 UX。
  - ✅ 优点：减少重复请求。
  - ❌ 缺点：不能保证正确性，网络重试、客户端 crash、恶意请求都绕得过。

- 方案 B：Idempotency key / unique constraint
  - 适用场景：下单、支付、提交任务、发送消息。
  - ✅ 优点：同一业务请求只产生一次效果。
  - ❌ 缺点：需要保存 key 到结果的映射，并设计 TTL 和冲突语义。

- 方案 C：Stream dedup / event_id window
  - 适用场景：高吞吐事件流。
  - ✅ 优点：适合 Kafka/Flink 消费端去重。
  - ❌ 缺点：只在窗口内有效，窗口外重复无法识别。

- 推荐：
  - API、consumer、webhook、payment callback 都默认可能重试。
  - 所有业务副作用都要通过 idempotency key、unique constraint、dedup table 或 deterministic output key 保护。

## 5. Ordering Guarantee

- 方案 A：全局顺序
  - 适用场景：极少数全局账本类系统。
  - ✅ 优点：语义简单。
  - ❌ 缺点：吞吐和可用性差，跨 region 更难。

- 方案 B：per-entity ordering
  - 适用场景：订单、聊天线程、job、账户、ride。
  - ✅ 优点：按 order_id/thread_id/account_id 保序，足够满足 correctness。
  - ❌ 缺点：跨 entity 无全局顺序。

- 方案 C：client sequence / version check
  - 适用场景：客户端可能离线、重试、乱序发送。
  - ✅ 优点：能拒绝旧写、检测 gap、支持恢复。
  - ❌ 缺点：协议和状态管理复杂。

- 推荐：
  - 大多数系统只需要 per-entity ordering。
  - 面试里主动说“不需要全局顺序，只需要每个 order/thread/key 内部有序”，很像 Staff 视角。

## 6. Push vs Pull / Fanout

- 方案 A：fanout-on-write
  - 适用场景：普通用户、关注者少、读延迟敏感。
  - ✅ 优点：读快，feed/inbox 直接返回。
  - ❌ 缺点：写放大，大 V/大 channel 会造成热点。

- 方案 B：fanout-on-read
  - 适用场景：大 V、大 channel、超多 follower。
  - ✅ 优点：写轻，发布成本低。
  - ❌ 缺点：读慢，需要 scatter-gather 和 merge。

- 方案 C：hybrid
  - 适用场景：News Feed、Slack、Status、Live Comment。
  - ✅ 优点：普通用户写扩散，大用户读时聚合。
  - ❌ 缺点：读路径要 merge 两类候选，系统复杂。

- 推荐：
  - 默认讲 hybrid。
  - 面试句式：普通用户 fanout-on-write，大 V fanout-on-read，read path merge 并用 cache 承接热点。

## 7. Cache Consistency

- 方案 A：cache-aside
  - 适用场景：读多写少，允许短暂 stale。
  - ✅ 优点：简单，DB 仍是 source of truth。
  - ❌ 缺点：cache miss stampede、stale cache、失效策略复杂。

- 方案 B：write-through
  - 适用场景：希望写入后 cache 尽量新。
  - ✅ 优点：读缓存一致性较好。
  - ❌ 缺点：写延迟高，cache 故障可能影响写。

- 方案 C：write-behind
  - 适用场景：写非常多，允许数据短暂不一致。
  - ✅ 优点：写性能好，可批量落库。
  - ❌ 缺点：cache crash 可能丢数据，恢复复杂。

- 推荐：
  - 对 correctness-critical 数据，不要把 cache 当 source of truth。
  - 用 TTL、invalidation、version、read-time validation 和 reconciliation 控制风险。

## 8. Double Booking / Oversell

- 方案 A：distributed lock
  - 适用场景：短时间临界区、实现快速。
  - ✅ 优点：容易理解。
  - ❌ 缺点：lock 过期、client crash、clock skew、split-brain 都会带来风险。

- 方案 B：DB conditional update / optimistic lock
  - 适用场景：库存、座位、余额、最高价。
  - ✅ 优点：正确性落在 source of truth，语义清晰。
  - ❌ 缺点：热点行竞争严重，吞吐受限。

- 方案 C：single writer per partition
  - 适用场景：极热点库存或事件，例如抢票、秒杀、auction。
  - ✅ 优点：串行化最强，避免分布式并发冲突。
  - ❌ 缺点：分区、failover、扩容复杂。

- 推荐：
  - 库存/座位/rider assignment 优先用 conditional write 或 single writer。
  - 分布式锁可以作为保护层，但不要把最终正确性完全寄托在锁上。

## 9. Saga vs Distributed Transaction

- 方案 A：单体事务
  - 适用场景：同一个 DB 内部的短事务。
  - ✅ 优点：简单，一致性强。
  - ❌ 缺点：跨支付、商家、外部系统不可行。

- 方案 B：2PC
  - 适用场景：所有参与方都支持 2PC，且可接受阻塞。
  - ✅ 优点：强一致。
  - ❌ 缺点：可用性差，外部系统通常不支持。

- 方案 C：Saga + compensation
  - 适用场景：订单、支付、出票、配送、长流程。
  - ✅ 优点：每步本地提交，可重试、可补偿。
  - ❌ 缺点：状态机复杂，最终一致。

- 推荐：
  - 真实业务流程用 Saga。
  - 例子：支付成功但出票失败则退款；商家拒单则取消订单；rider 超时则重新派单。

## 10. Worker Failure / Task Ownership

- 方案 A：worker 拿任务后直接执行
  - 适用场景：低价值、可丢任务。
  - ✅ 优点：简单。
  - ❌ 缺点：worker crash 后任务丢失或永久卡住。

- 方案 B：lease / visibility timeout
  - 适用场景：queue worker、job scheduler、rider dispatch。
  - ✅ 优点：超时后可重新分配。
  - ❌ 缺点：任务可能重复执行，worker 必须幂等。

- 方案 C：durable workflow
  - 适用场景：长流程、多步骤、超时、人工介入。
  - ✅ 优点：retry、timeout、compensation、recovery 清晰。
  - ❌ 缺点：引入 Temporal/Step Functions 等复杂度。

- 推荐：
  - queue + lease + idempotent worker 是通用基础解。
  - 复杂订单/转码/job pipeline 可以升级 workflow engine。

## 11. Backpressure / Admission Control

- 方案 A：无限排队
  - 适用场景：几乎不推荐。
  - ✅ 优点：表面上不丢请求。
  - ❌ 缺点：tail latency 爆炸，过期请求浪费资源，系统雪崩。

- 方案 B：快速拒绝
  - 适用场景：系统过载、请求已超过 SLA。
  - ✅ 优点：保护核心服务。
  - ❌ 缺点：用户体验差，需要客户端重试策略。

- 方案 C：优先级队列 + 降级
  - 适用场景：LLM inference、秒杀、支付、监控告警。
  - ✅ 优点：高价值请求优先，低价值请求降级。
  - ❌ 缺点：策略复杂，可能带来公平性问题。

- 推荐：
  - 过载时要明确 queue limit、timeout、priority、drop policy。
  - Staff+ 说法：排队不是免费能力，admission control 是保护 tail latency 的核心。

## 12. Hot Partition / Hot Key

- 方案 A：普通 hash sharding
  - 适用场景：key 访问均匀。
  - ✅ 优点：简单，扩展方便。
  - ❌ 缺点：单个 hot key 仍会打爆一个 shard。

- 方案 B：hot key 拆分
  - 适用场景：Trending hashtag、热门视频、热门商品、热门 channel。
  - ✅ 优点：分摊写入和读流量。
  - ❌ 缺点：读时需要聚合，顺序和一致性更复杂。

- 方案 C：single writer + read replica/cache
  - 适用场景：热点但必须保持强一致的实体。
  - ✅ 优点：写入顺序清晰，读可扩展。
  - ❌ 缺点：写吞吐仍有限。

- 推荐：
  - 面试时不要只算平均 QPS，要主动说 hot entity。
  - 平均流量通常不可怕，真正风险是单个 event/channel/product/title 瞬间热点。

## 13. Geo Index

- 方案 A：PostGIS
  - 适用场景：复杂 polygon、配送范围、运营后台查询。
  - ✅ 优点：地理能力强，查询表达力好。
  - ❌ 缺点：高 QPS online 查询压力大，需要 cache/read replica。

- 方案 B：Geohash
  - 适用场景：简单附近搜索、容易按 prefix shard。
  - ✅ 优点：实现简单。
  - ❌ 缺点：边界问题，cell 大小固定，人口密度不均时不灵活。

- 方案 C：H3/S2
  - 适用场景：大规模 online geo service。
  - ✅ 优点：cell 层级清晰，适合邻居查询和分片。
  - ❌ 缺点：实现和调参复杂。

- 推荐：
  - Uber/proximity/food delivery 里，在线召回用 H3/S2，复杂范围和后台校验用 PostGIS。
  - 一定提 current cell + neighbor cells，否则边界会漏结果。

## 14. Realtime Push：Polling / SSE / WebSocket

- 方案 A：polling
  - 适用场景：低频状态查询、实现简单优先。
  - ✅ 优点：简单，服务端无长连接状态。
  - ❌ 缺点：实时性差，频繁 polling 浪费资源。

- 方案 B：SSE
  - 适用场景：服务端单向推送，如订单状态、rider 位置、job result。
  - ✅ 优点：比 WebSocket 简单，HTTP 友好。
  - ❌ 缺点：只适合单向，移动端/代理兼容性要考虑。

- 方案 C：WebSocket
  - 适用场景：双向实时，如 chat、live comment、协作编辑。
  - ✅ 优点：实时性好，双向通信。
  - ❌ 缺点：连接管理、扩容、重连恢复复杂。

- 推荐：
  - 协议不是重点，恢复机制才是重点。
  - WebSocket/SSE 只是 delivery channel，不是 source of truth；断线后用 cursor/inbox/DB 补齐。

## 15. TTL / Expiration

- 方案 A：DB TTL
  - 适用场景：自动清理过期数据。
  - ✅ 优点：实现简单，运维成本低。
  - ❌ 缺点：删除不精确，不能保证到点马上不可见。

- 方案 B：delay queue
  - 适用场景：需要到期触发动作，如 hold 释放、job 到点执行。
  - ✅ 优点：更接近实时。
  - ❌ 缺点：大规模 delayed message 成本高，worker failure 要处理。

- 方案 C：time bucket scanner
  - 适用场景：大量过期对象、可接受分钟级延迟。
  - ✅ 优点：稳定、可恢复、容易 backfill。
  - ❌ 缺点：不精确，需要扫描。

- 推荐：
  - correctness 由读路径判断 `expires_at` 保证。
  - TTL/scanner/delay queue 只是清理或触发机制，不能单独作为正确性保证。

## 16. Search Index Freshness vs Correctness

- 方案 A：同步写 index
  - 适用场景：低 QPS 且要求立即可搜。
  - ✅ 优点：freshness 好。
  - ❌ 缺点：index 故障会影响主写链路。

- 方案 B：异步 CDC 更新 index
  - 适用场景：高吞吐生产系统。
  - ✅ 优点：解耦主链路，索引可 replay、可重建。
  - ❌ 缺点：短暂不可搜或搜到旧结果。

- 方案 C：异步 index + 查询前校验
  - 适用场景：搜索结果涉及权限、库存、状态。
  - ✅ 优点：性能和正确性平衡。
  - ❌ 缺点：查询链路更复杂。

- 推荐：
  - 搜索 recall 可以短暂变差，但不能返回无权限、已删除、不可购买、已过期的结果。
  - 查询结果返回前做 permission/state/expiration validation。

## 17. Global Consistency / Multi-region

- 方案 A：全局同步写
  - 适用场景：少数强一致全球账本。
  - ✅ 优点：一致性强。
  - ❌ 缺点：延迟高，可用性差，region failure 影响大。

- 方案 B：home region 写入
  - 适用场景：用户、订单、文档、status、ride 等有自然 owner 的实体。
  - ✅ 优点：ownership 清晰，写入低延迟。
  - ❌ 缺点：跨 region 读取有复制延迟。

- 方案 C：active-active
  - 适用场景：全球低延迟和高可用极端重要。
  - ✅ 优点：区域故障影响小。
  - ❌ 缺点：冲突解决、权限撤销、一致性语义复杂。

- 推荐：
  - 默认 home region + async replication。
  - 只有少数关键 invariant 才做同步协调，不要让全系统背全局一致性的成本。

## 18. Reconciliation

- 方案 A：完全依赖实时链路
  - 适用场景：低风险 metrics 或临时展示。
  - ✅ 优点：实时。
  - ❌ 缺点：late event、duplicate、丢失会污染结果。

- 方案 B：离线 batch 重新计算
  - 适用场景：财务、计费、统计报表。
  - ✅ 优点：准确，可修正历史。
  - ❌ 缺点：延迟高，不能服务实时产品体验。

- 方案 C：实时 serving + 离线 reconciliation
  - 适用场景：ads、payment、inventory、TopK、monitoring。
  - ✅ 优点：用户看到实时结果，同时最终正确。
  - ❌ 缺点：双路径复杂，merge 规则要清楚。

- 推荐：
  - 实时链路服务用户体验，离线 reconciliation 负责最终正确性。
  - 财务和计费类系统一定要主动讲 reconciliation。

## 19. Sliding Window / Time Window

- 方案 A：精确 event list
  - 适用场景：窗口小、精度要求高。
  - ✅ 优点：准确。
  - ❌ 缺点：内存大，淘汰旧事件成本高。

- 方案 B：bucket aggregation
  - 适用场景：固定窗口，如过去 1 分钟、1 小时、1 天。
  - ✅ 优点：内存可控，更新快。
  - ❌ 缺点：边界有精度损失。

- 方案 C：sketch / decay count
  - 适用场景：超大规模、允许近似。
  - ✅ 优点：省内存，吞吐高。
  - ❌ 缺点：有误差，解释性差。

- 推荐：
  - 固定窗口用 bucket。
  - 任意 lookup window 用 Druid/ClickHouse/OLAP。
  - 超大规模 TopK/trending 可以考虑 sketch 或 decay score。

## 20. ML Feature Consistency

- 方案 A：离线和在线各写一套 feature logic
  - 适用场景：早期实验。
  - ✅ 优点：快。
  - ❌ 缺点：training-serving skew，线上效果不可预测。

- 方案 B：Feature Store 统一定义
  - 适用场景：生产 ML 系统。
  - ✅ 优点：特征复用，一致性好。
  - ❌ 缺点：平台复杂，治理成本高。

- 方案 C：point-in-time join + online decision logging
  - 适用场景：风控、推荐、广告等高风险 ML。
  - ✅ 优点：避免 data leakage，可 replay/debug。
  - ❌ 缺点：数据工程复杂，存储成本高。

- 推荐：
  - 推荐/风控题一定讲 feature freshness、point-in-time correctness、model version、feature vector logging。

## 21. Exploration vs Exploitation

- 方案 A：只推最高分
  - 适用场景：短期指标最大化。
  - ✅ 优点：短期 CTR/CVR 可能高。
  - ❌ 缺点：头部效应、冷启动差、反馈闭环偏置。

- 方案 B：固定 exploration slot
  - 适用场景：简单可控探索。
  - ✅ 优点：容易实现和解释。
  - ❌ 缺点：探索效率低，可能浪费流量。

- 方案 C：contextual bandit / uncertainty-based exploration
  - 适用场景：成熟推荐系统。
  - ✅ 优点：长期学习更好。
  - ❌ 缺点：实现、评估、guardrail 都复杂。

- 推荐：
  - 推荐系统不要只讲 ranking。
  - 要讲受控探索、新内容/新商家冷启动，以及 guardrail metrics。

## 22. Observability / Debuggability

- 方案 A：只看 error rate 和 latency
  - 适用场景：基础 API。
  - ✅ 优点：简单。
  - ❌ 缺点：无法解释业务错误和模型/策略错误。

- 方案 B：记录 decision reason / model version / policy version / feature vector
  - 适用场景：风控、推荐、订单、调度。
  - ✅ 优点：可 debug、可审计、可回放。
  - ❌ 缺点：存储和隐私成本高。

- 方案 C：trace + replay + DLQ
  - 适用场景：复杂异步系统。
  - ✅ 优点：故障恢复强，可以重放修复派生数据。
  - ❌ 缺点：工程复杂，需要事件 schema 和版本治理。

- 推荐：
  - Staff+ 要主动回答：这个请求为什么被这样处理？用了哪个版本？失败后能不能 replay 修复？

## 23. Read Path vs Write Path Optimization

- 方案 A：写优化
  - 适用场景：写入极多、读可以慢一点，如日志、事件流。
  - ✅ 优点：写吞吐高。
  - ❌ 缺点：查询需要聚合或异步构建 read model。

- 方案 B：读优化
  - 适用场景：feed、inbox、leaderboard、search result。
  - ✅ 优点：用户读低延迟。
  - ❌ 缺点：写放大、派生数据一致性复杂。

- 方案 C：分层读写模型
  - 适用场景：大多数互联网产品。
  - ✅ 优点：source of truth 写简单，read model 按查询优化。
  - ❌ 缺点：多套数据和异步 pipeline。

- 推荐：
  - 面试里要明确这是读多还是写多，以及哪些 query 需要低延迟。
  - 不要试图用一张表满足所有访问模式。

## 24. Strong Consistency 只保护关键 Invariant

- 方案 A：全系统强一致
  - 适用场景：小系统或极少数金融核心账本。
  - ✅ 优点：语义简单。
  - ❌ 缺点：性能、可用性、跨 region 延迟都很差。

- 方案 B：全系统最终一致
  - 适用场景：低风险读模型。
  - ✅ 优点：高可用，高吞吐。
  - ❌ 缺点：关键业务可能出错，如 oversell、重复扣款、权限泄露。

- 方案 C：关键 invariant 强一致，其余最终一致
  - 适用场景：绝大多数 Staff+ 系统设计。
  - ✅ 优点：成本和正确性平衡。
  - ❌ 缺点：需要清楚划分边界。

- 推荐：
  - 先说 invariant：不能 oversell、不能 double booking、不能重复扣款、不能越权读取。
  - 再说这些点强一致，其它 cache/index/feed/analytics 最终一致。

## 25. Workflow Engine vs Queue + Worker

- 方案 A：queue + worker
  - 适用场景：单步或短任务，如发通知、转码单个 segment。
  - ✅ 优点：简单，吞吐高。
  - ❌ 缺点：多步骤状态、timeout、补偿要自己写。

- 方案 B：cron / scanner
  - 适用场景：周期性扫描、低精度任务。
  - ✅ 优点：实现简单。
  - ❌ 缺点：准确性差，故障恢复和去重麻烦。

- 方案 C：durable workflow engine
  - 适用场景：长流程、多步骤、有人工介入、需要补偿。
  - ✅ 优点：状态清晰，retry/timeout/compensation 内建。
  - ❌ 缺点：引入平台依赖，学习和运维成本高。

- 推荐：
  - 简单异步任务用 queue worker。
  - 长订单、支付出票、视频处理、复杂 job scheduler 可以讲 Temporal/Step Functions。

## 26. Security / Privacy as Correctness

- 方案 A：只在前端隐藏
  - 适用场景：不可接受，只能作为 UX。
  - ✅ 优点：实现快。
  - ❌ 缺点：完全不安全。

- 方案 B：服务端统一鉴权
  - 适用场景：绝大多数系统。
  - ✅ 优点：权限边界清晰。
  - ❌ 缺点：每次读写都要带上下文，延迟增加。

- 方案 C：加密 / client-side search / signed URL
  - 适用场景：E2EE、媒体内容、隐私数据。
  - ✅ 优点：隐私强，权限泄露风险低。
  - ❌ 缺点：搜索、索引、调试、恢复更复杂。

- 推荐：
  - 权限、隐私、PII 不是附加项，是 correctness invariant。
  - Search index、cache、CDN 返回前都要考虑权限和过期。

## 27. Model / Policy / Config Versioning

- 方案 A：直接覆盖配置或模型
  - 适用场景：早期手动系统。
  - ✅ 优点：简单。
  - ❌ 缺点：事故后难定位，无法回滚。

- 方案 B：versioned config/model + canary
  - 适用场景：推荐、风控、搜索排序、rate limit。
  - ✅ 优点：可灰度、可回滚、可对比。
  - ❌ 缺点：发布系统和审计复杂。

- 方案 C：shadow traffic + A/B test
  - 适用场景：ML/策略系统。
  - ✅ 优点：上线前发现问题，在线量化效果。
  - ❌ 缺点：实验分析复杂，需要 guardrail。

- 推荐：
  - 涉及 ML、ranking、风控、调度策略时，主动说 version、canary、rollback、reason logging。

## 28. Data Retention / Compaction / GC

- 方案 A：永久保留所有数据
  - 适用场景：审计强要求且成本可接受。
  - ✅ 优点：可追溯。
  - ❌ 缺点：成本高，查询慢，隐私风险大。

- 方案 B：TTL 删除
  - 适用场景：短生命周期数据。
  - ✅ 优点：简单。
  - ❌ 缺点：不精确，派生数据可能残留。

- 方案 C：retention policy + compaction + legal hold
  - 适用场景：成熟系统。
  - ✅ 优点：成本、合规、恢复之间平衡。
  - ❌ 缺点：需要数据生命周期治理。

- 推荐：
  - 对 versioned KV、S3、logs、metrics、status、event stream，都要讲 retention 和 compaction。

## 29. Approximate vs Exact

- 方案 A：精确统计
  - 适用场景：计费、库存、财务、核心排名。
  - ✅ 优点：正确性强。
  - ❌ 缺点：成本高，扩展难。

- 方案 B：近似统计
  - 适用场景：trending、监控 top exceptions、推荐召回、粗略 analytics。
  - ✅ 优点：省内存，高吞吐。
  - ❌ 缺点：误差，不适合计费和强 correctness。

- 方案 C：实时近似 + 离线精确
  - 适用场景：大规模 analytics 和 TopK。
  - ✅ 优点：体验实时，最终可校正。
  - ❌ 缺点：两套结果需要解释和 merge。

- 推荐：
  - 面试先问结果是否需要 exact。
  - 计费/库存 exact，trending/monitoring 可以 approximate。

## 30. Staff+ 总表达

- `I would first define the correctness boundary.`
- `The source-of-truth write path must be protected with idempotency, conditional update, or state machine.`
- `Everything else, like cache, search index, notification, analytics, realtime push, and recommendation results, can be eventually consistent, replayable, and rebuildable.`
- `I would not promise end-to-end exactly-once. I would design for at-least-once delivery and idempotent effects.`
- `The real bottleneck is often hot entity traffic, not average QPS.`
- `For multi-region, I would avoid global strong consistency unless it protects a specific invariant.`
