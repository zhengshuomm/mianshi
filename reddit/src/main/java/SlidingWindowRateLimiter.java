import java.util.*;
import java.util.concurrent.locks.ReentrantLock;

/**
 * Simple Sliding Window Rate Limiter (Thread-safe with Lock)
 */
public class SlidingWindowRateLimiter {
    private final int maxRequests;
    private final long windowSizeMs;
    private final Queue<Long> timestamps;
    private final ReentrantLock lock;
    
    public SlidingWindowRateLimiter(int maxRequests, long windowSizeMs) {
        this.maxRequests = maxRequests;
        this.windowSizeMs = windowSizeMs;
        this.timestamps = new LinkedList<>();
        this.lock = new ReentrantLock();
    }
    
    public boolean allowRequest() {
        lock.lock();
        try {
            long now = System.currentTimeMillis();
            
            // Remove old timestamps outside the window
            while (!timestamps.isEmpty() && now - timestamps.peek() >= windowSizeMs) {
                timestamps.poll();
            }
            
            // Check limit
            if (timestamps.size() < maxRequests) {
                timestamps.offer(now);
                return true;
            }
            
            return false;
        } finally {
            lock.unlock();
        }
    }
}
