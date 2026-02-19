import java.util.concurrent.atomic.AtomicLong;

public class RateLimiter {

    private final long capacity;          // 最大 token 数
    private final long refillRate;         // 每秒生成 token 数
    private final AtomicLong tokens;
    private volatile long lastRefillTime;

    public RateLimiter(long capacity, long refillRate) {
        this.capacity = capacity;
        this.refillRate = refillRate;
        this.tokens = new AtomicLong(capacity);
        this.lastRefillTime = System.nanoTime();
    }

    public synchronized boolean allowRequest() {
        refill();
        if (tokens.get() > 0) {
            tokens.decrementAndGet();
            return true;
        }
        return false;
    }

    private void refill() {
        long now = System.nanoTime();
        long elapsedTime = now - lastRefillTime;
        long tokensToAdd = (elapsedTime * refillRate) / 1_000_000_000L;

        if (tokensToAdd > 0) {
            long newTokens = Math.min(capacity, tokens.get() + tokensToAdd);
            tokens.set(newTokens);
            lastRefillTime = now;
        }
    }
}

/**
 * Q1：How do you handle high throughput？
回答思路（3 点）

1️⃣ 减少锁竞争

当前是 synchronized

高并发下可以：

用 AtomicLong + CAS

或 分段 limiter（sharding）

“To reduce contention, I can shard the rate limiter by user ID hash.”

2️⃣ 本地限流 + 上游限流

API Gateway / Nginx 先挡一层

应用内部再限

3️⃣ 批量 refill

不每次请求 refill

定时 refill（ScheduledExecutor）

Q2：高并发环境下如何用？（多线程 / 多实例）
单机多线程

当前实现是 线程安全

但吞吐有限（锁）

👉 可优化：

每 CPU core 一个 limiter

或 ThreadLocal bucket

多实例（重点）

❗️单机 limiter 不适用于分布式

面试标准答案：

“For distributed environments, I would move the rate limiter to a shared store like Redis.”

Q3：分布式 Rate Limiter 怎么做？
Redis + Lua（面试加分）

思路：

token count + last refill time 存 Redis

Lua 保证原子性

你可以不用写 Lua，直接说：

“I’d use Redis with Lua scripts to ensure atomic token refill and consumption.”

Q4：How do you test it?
单元测试

mock 时间（Clock / TimeProvider）

测 allow / reject

@Test
public void testRateLimit() {
    RateLimiter limiter = new RateLimiter(5, 1);

    for (int i = 0; i < 5; i++) {
        assertTrue(limiter.allowRequest());
    }
    assertFalse(limiter.allowRequest());
}
 */